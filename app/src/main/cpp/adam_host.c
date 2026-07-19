/*
 * adam_host.c -- Android host layer driving the adamcore emulator library.
 *
 * Owns the emulation loop on the session's emulator thread: parses the
 * session argv into an adamcore_config, creates the core, and paces one
 * emulated frame per display vsync (AChoreographer phase lock) with a
 * wall-clock sleeper as fallback. The session runtime registers a frame
 * sink and feeds input through the adamhost_* entry points; audio is
 * pulled by the Kotlin AudioTrack feeder via adamhost_render_audio.
 *
 * Copyright (C) 2026 Thomas Cherryhomes
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

#include <errno.h>
#include <pthread.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#include <android/choreographer.h>
#include <android/log.h>
#include <android/looper.h>

#include "adamcore.h"

#define LOG_TAG "AdamHost"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

/****************************************************************************/
/** Session-facing surface                                                 **/
/****************************************************************************/
typedef void (*adam_frame_sink_t)(const uint16_t *rgb565, int w, int h, void *ud);

static adam_frame_sink_t g_frame_sink = NULL;
static void             *g_frame_sink_ud = NULL;

static adamcore *volatile g_core = NULL;
static volatile int g_stop = 0;

void adamhost_set_frame_sink(adam_frame_sink_t sink, void *ud)
{
    g_frame_sink = sink;
    g_frame_sink_ud = ud;
}

void adamhost_inject_key(int adam_char)
{
    adamcore *c = g_core;
    if (c) adamcore_inject_key(c, (uint8_t)adam_char);
}

void adamhost_set_joystick(int port, int adamnet_state)
{
    adamcore *c = g_core;
    if (c) adamcore_set_joystick(c, port, (uint16_t)adamnet_state);
}

void adamhost_request_reset(int mode)
{
    adamcore *c = g_core;
    if (c) adamcore_request_reset(c, mode);
}

void adamhost_request_stop(void)
{
    g_stop = 1;
}

int adamhost_render_audio(int16_t *out, int nsamples)
{
    adamcore *c = g_core;
    if (!c) {
        memset(out, 0, (size_t)nsamples * sizeof(int16_t));
        return nsamples;
    }
    return adamcore_render_audio(c, out, nsamples);
}

/****************************************************************************/
/** Timing (microseconds, monotonic)                                       **/
/****************************************************************************/
static long ReadTimer(void)
{
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (long)(ts.tv_sec * 1000000L + ts.tv_nsec / 1000L);
}

/* Sleep (don't spin) until an absolute CLOCK_MONOTONIC time, in microseconds.
   A busy-wait pegs a core, which on a phone means heat -> thermal throttling
   -> an unsteady frame rate (audible as a "gallop"). */
static void SleepUntilUs(long target_us)
{
    struct timespec ts;
    ts.tv_sec  = target_us / 1000000L;
    ts.tv_nsec = (target_us % 1000000L) * 1000L;
    while (clock_nanosleep(CLOCK_MONOTONIC, TIMER_ABSTIME, &ts, NULL) == EINTR)
        ; /* re-sleep if interrupted by a signal */
}

/****************************************************************************/
/** Vsync phase-lock                                                       **/
/** Pace frames to the display's hardware vsync (AChoreographer) instead of **/
/** a wall-clock timer, so the 60Hz frame clock -- and thus music tempo --  **/
/** is the panel's refresh and can't drift/jitter against it. The display   **/
/** is pinned to 60Hz by the app's Surface.setFrameRate(60). Engages only   **/
/** while ~60Hz vsyncs are actually arriving; otherwise (screen off, or a   **/
/** non-60Hz panel) the wall-clock sleeper takes over.                      **/
/****************************************************************************/
static volatile int    g_vsync_run = 0;
static pthread_t       g_vsync_tid;
static pthread_mutex_t g_vsync_mtx = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t  g_vsync_cv;              /* MONOTONIC-clocked          */
static long            g_vsync_ns = 0;          /* latest vsync time (ns)     */
static long            g_vsync_iv = 0;          /* recent vsync interval (us) */

#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wdeprecated-declarations"
static void vsync_cb(long frameTimeNanos, void *data)
{
    static long prev_ns = 0;                    /* vsync thread only          */
    pthread_mutex_lock(&g_vsync_mtx);
    if (prev_ns) g_vsync_iv = (frameTimeNanos - prev_ns) / 1000;
    prev_ns = frameTimeNanos;
    g_vsync_ns = frameTimeNanos;
    pthread_cond_broadcast(&g_vsync_cv);
    pthread_mutex_unlock(&g_vsync_mtx);
    if (g_vsync_run)
        AChoreographer_postFrameCallback((AChoreographer *)data, vsync_cb, data);
}

static void *vsync_main(void *arg)
{
    (void)arg;
    pthread_setname_np(pthread_self(), "adam-vsync");
    ALooper *looper = ALooper_prepare(ALOOPER_PREPARE_ALLOW_NON_CALLBACKS);
    AChoreographer *ch = looper ? AChoreographer_getInstance() : NULL;
    if (!ch) { g_vsync_run = 0; return NULL; }
    AChoreographer_postFrameCallback(ch, vsync_cb, ch);
    while (g_vsync_run)
        ALooper_pollOnce(200, NULL, NULL, NULL);
    return NULL;
}
#pragma clang diagnostic pop

static void StartVsync(void)
{
    pthread_condattr_t a;
    if (g_vsync_run) return;
    pthread_condattr_init(&a);
    pthread_condattr_setclock(&a, CLOCK_MONOTONIC);
    pthread_cond_init(&g_vsync_cv, &a);
    pthread_condattr_destroy(&a);
    g_vsync_run = 1;
    if (pthread_create(&g_vsync_tid, NULL, vsync_main, NULL) != 0)
        g_vsync_run = 0;
}

static void StopVsync(void)
{
    if (!g_vsync_run) return;
    g_vsync_run = 0;
    pthread_mutex_lock(&g_vsync_mtx);
    pthread_cond_broadcast(&g_vsync_cv);
    pthread_mutex_unlock(&g_vsync_mtx);
    pthread_join(g_vsync_tid, NULL);
}

/* True while ~60Hz vsyncs are arriving (display attached, on, at 60Hz). */
static int VsyncFresh(long now_us)
{
    int fresh;
    if (!g_vsync_run) return 0;
    pthread_mutex_lock(&g_vsync_mtx);
    fresh = g_vsync_ns != 0 &&
            (now_us - g_vsync_ns / 1000) < 100000 &&     /* fresh (<100ms)     */
            g_vsync_iv >= 15000 && g_vsync_iv <= 18500;  /* ~55-66Hz           */
    pthread_mutex_unlock(&g_vsync_mtx);
    return fresh;
}

/* Block until the next display vsync after the one we last presented on and
   return its time (us). Self-correcting: if a frame's work overruns a vsync,
   the next call returns at once. Bails on a short timeout so it can never
   hang if vsyncs stop mid-wait. */
static long WaitNextVsync(void)
{
    static long last_ns = 0;
    long v;
    pthread_mutex_lock(&g_vsync_mtx);
    while (g_vsync_run && g_vsync_ns <= last_ns) {
        struct timespec ts;
        clock_gettime(CLOCK_MONOTONIC, &ts);
        ts.tv_nsec += 40000000L;                /* 40ms safety timeout        */
        if (ts.tv_nsec >= 1000000000L) { ts.tv_sec++; ts.tv_nsec -= 1000000000L; }
        if (pthread_cond_timedwait(&g_vsync_cv, &g_vsync_mtx, &ts) == ETIMEDOUT)
            break;
    }
    last_ns = g_vsync_ns;
    v = g_vsync_ns / 1000;
    pthread_mutex_unlock(&g_vsync_mtx);
    return v;
}

/****************************************************************************/
/** argv -> adamcore_config                                                **/
/****************************************************************************/
static int parse_args(int argc, char **argv, adamcore_config *cfg)
{
    int i;
    memset(cfg, 0, sizeof(*cfg));
    cfg->start_machine = ADAMCORE_MACHINE_CV;
    cfg->audio_rate = 44100;
    cfg->joystick_mode = 1;

    for (i = 1; i < argc; i++) {
        const char *a = argv[i];
        if (!strcmp(a, "-adam")) cfg->start_machine = ADAMCORE_MACHINE_ADAM;
        else if (!strcmp(a, "-cvision")) cfg->start_machine = ADAMCORE_MACHINE_CV;
        else if (!strcmp(a, "-os7") && i + 1 < argc) cfg->os7_rom_path = argv[++i];
        else if (!strcmp(a, "-eos") && i + 1 < argc) cfg->eos_rom_path = argv[++i];
        else if (!strcmp(a, "-wp") && i + 1 < argc) cfg->wp_rom_path = argv[++i];
        else if (!strcmp(a, "-cart") && i + 1 < argc) cfg->cart_path = argv[++i];
        else if (!strcmp(a, "-fujinet") && i + 1 < argc) cfg->boip_listen_port = atoi(argv[++i]);
        else if (!strcmp(a, "-palette") && i + 1 < argc) cfg->palette = atoi(argv[++i]);
        else if (!strcmp(a, "-expansion") && i + 1 < argc) cfg->expansion = atoi(argv[++i]);
        else if (!strcmp(a, "-joystick") && i + 1 < argc) cfg->joystick_mode = atoi(argv[++i]);
        else if (!strcmp(a, "-swapbuttons") && i + 1 < argc) cfg->swap_buttons = atoi(argv[++i]);
        else if (!strcmp(a, "-keypad") && i + 1 < argc) cfg->reverse_keypad = atoi(argv[++i]);
        else LOGW("parse_args: ignoring unknown option \"%s\"", a);
    }
    if (!cfg->os7_rom_path) {
        LOGW("parse_args: missing -os7");
        return -1;
    }
    return 0;
}

/****************************************************************************/
/** Emulation loop entry (runs on the session's emulator thread)           **/
/****************************************************************************/
int adamhost_main(int argc, char **argv)
{
    adamcore_config cfg;
    adamcore *core;
    long next_us;

    if (parse_args(argc, argv, &cfg) != 0)
        return 1;

    core = adamcore_create(&cfg);
    if (!core) {
        LOGW("adamcore_create failed (ROM paths? BoIP port?)");
        return 1;
    }
    g_stop = 0;
    g_core = core;

    StartVsync();
    LOGI("adamhost: core up (machine=%d, boip=%d, palette=%d)",
         (int)cfg.start_machine, cfg.boip_listen_port, cfg.palette);

    next_us = ReadTimer();
    while (!g_stop) {
        int changed = adamcore_run_frame(core);

        if (changed && g_frame_sink) {
            int w, h;
            const uint16_t *fb = adamcore_framebuffer(core, &w, &h);
            g_frame_sink(fb, w, h, g_frame_sink_ud);
        }

        /* Pacing diagnostics: once per real second, report achieved frame
           rate, frames the emulator couldn't keep up with ("behind"), and
           how many paced on the vsync phase-lock vs the wall-clock. */
        {
            long now = ReadTimer();
            int vfresh = VsyncFresh(now);
            static long dbg_t0 = 0;
            static int  dbg_frames = 0, dbg_behind = 0, dbg_vsync = 0;
            if (dbg_t0 == 0) dbg_t0 = now;
            dbg_frames++;
            if (vfresh) dbg_vsync++;

            next_us += 16688;                    /* 59.922 Hz NTSC frame */
            if (now >= next_us) dbg_behind++;

            if (now - dbg_t0 >= 1000000L) {
                LOGI("pace: %d fps over %ld ms (%d behind, %d on vsync)",
                     dbg_frames, (now - dbg_t0) / 1000, dbg_behind, dbg_vsync);
                dbg_t0 = now; dbg_frames = 0; dbg_behind = 0; dbg_vsync = 0;
            }

            if (vfresh) {
                /* One emulated frame per display vsync; snap the wall
                   clock so a later fallback resumes from real time. */
                next_us = WaitNextVsync();
            } else if (now < next_us) {
                SleepUntilUs(next_us);
            } else {
                next_us = now;                   /* behind: don't accumulate */
            }
        }
    }

    StopVsync();
    g_core = NULL;
    adamcore_destroy(core);
    LOGI("adamhost: loop ended");
    return 0;
}
