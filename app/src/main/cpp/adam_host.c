/*
 * adam_host.c -- Android host layer for the ADAMEm core.
 *
 * This is the headless replacement for ADAMEm's SDL driver (AdamemSDL.c). It
 * provides the same host contract the core expects -- InitMachine/TrashMachine,
 * PutImage, Keyboard, Joysticks, CheckScreenRefresh, the option globals -- but
 * renders into a shared RGB565 frame buffer and consumes injected input instead
 * of touching SDL. The session runtime (session_runtime.cpp) registers a frame
 * sink and feeds input; the core's StartColeco() loop drives everything.
 *
 * Layout mirrors AdamemSDL.c: globals + PutPixel + PutImage are declared before
 * "Common.h" is included at the bottom, because Common.h is a mix-in that emits
 * the screen-refresh drivers (RefreshScreen*) which call PutPixel/PutImage.
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

#include "Coleco.h"
#include "AdamemSDL.h"

/* ADAMEm sound entry points (Sound.c / AdamSDLSound_2.c), compiled with SOUND. */
extern int  InitSound(void);
extern void StopSound(void);

#define LOG_TAG "AdamHost"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

/* Skip Common.h's modem/RS232 mix-in drivers; the ADAM boots and reaches
   FujiNet without them, and they pull in host serial plumbing we do not need. */
#define NO_COMMON_MODEM_DRIVERS

/****************************************************************************/
/** Host globals (the symbols AdamemSDL.c used to own)                     **/
/****************************************************************************/
char Title[] = "FujiNet Go Adam";

SMS_NTSC_IN_T *DisplayBuf = NULL;        /* RGB565 frame buffer, WIDTH*HEIGHT */
unsigned int   RGBcolors[16];            /* palette index -> RGB565           */
static byte    VGA_Palette[16 * 3];      /* Coleco palette (screenshot use)   */
static int     PalBuf[16], Pal0;         /* palette index buffer              */
sms_ntsc_t    *ntsc = NULL;              /* Blargg NTSC filter (unused here)  */

static int width, height;                /* frame buffer dimensions           */
static int keyboardmode;                 /* 0=joystick, 1=keyboard            */
static long OldTimer = 0, NewTimer = 0;  /* frame pacing (microseconds)       */
static char fullscreen = 0;

/* Option globals declared extern in AdamemSDL.h. */
char szJoystickFileName[256] = "";
char szBitmapFile[256] = "adam000.bmp";
char szSnapshotFile[256] = "adam000.sna";
char *szKeys = "05004F05205101900601B01D";
int  mouse_sens = 200;
int  keypadmode = 0;
int  joystick = 1;
int  calibrate = 0;
int  swapbuttons = 0;
int  expansionmode = 0;
int  syncemu = 1;
int  SaveCPU = 1;
int  videomode = 0;
int  PausePressed = 0;
int  AutoText80 = 0;
int  Text80 = 0;
int  Text80Colour = 0;

/* Joystick direction/button scancodes parsed from szKeys (host-side in the
   original AdamemSDL.c). Retained so InitMachine's sscanf has somewhere to go. */
static int KEY_LEFT, KEY_RIGHT, KEY_UP, KEY_DOWN;
static int KEY_BUTTONA, KEY_BUTTONB, KEY_BUTTONC, KEY_BUTTOND;

/****************************************************************************/
/** Frame sink + input bridge to the session runtime                       **/
/****************************************************************************/
typedef void (*adam_frame_sink_t)(const uint16_t *rgb565, int w, int h, void *ud);

static adam_frame_sink_t g_frame_sink = NULL;
static void             *g_frame_sink_ud = NULL;

/* Injected key events: a tiny lock-guarded ring of ADAM character codes. */
#define ADAM_KEY_QUEUE 256
static pthread_mutex_t g_input_mutex = PTHREAD_MUTEX_INITIALIZER;
static byte g_key_queue[ADAM_KEY_QUEUE];
static int  g_key_head = 0, g_key_tail = 0;
static int  g_pending_reset = -1;        /* -1 none, else ResetColeco(mode)   */

void adamhost_set_frame_sink(adam_frame_sink_t sink, void *ud)
{
    g_frame_sink = sink;
    g_frame_sink_ud = ud;
}

void adamhost_inject_key(int adam_char)
{
    pthread_mutex_lock(&g_input_mutex);
    int next = (g_key_tail + 1) % ADAM_KEY_QUEUE;
    if (next != g_key_head) {
        g_key_queue[g_key_tail] = (byte)adam_char;
        g_key_tail = next;
    }
    pthread_mutex_unlock(&g_input_mutex);
}

void adamhost_set_joystick(int port, int adamnet_state)
{
    if (port < 0 || port > 1) return;
    JoyState[port] = adamnet_state;
}

void adamhost_request_reset(int mode)
{
    pthread_mutex_lock(&g_input_mutex);
    g_pending_reset = mode;
    pthread_mutex_unlock(&g_input_mutex);
}

/****************************************************************************/
/** Index -> RGB565 pixel store (mirrors AdamemSDL.c's SDLPutPixel)         **/
/****************************************************************************/
#define PutPixel(P, C)  (DisplayBuf[(P)] = (SMS_NTSC_IN_T)RGBcolors[(C)])

static inline unsigned int pack_rgb565(int r, int g, int b)
{
    return (unsigned int)(((r & 0xF8) << 8) | ((g & 0xFC) << 3) | ((b & 0xF8) >> 3));
}

/* Push the finished frame to the session's surface. Called once per refresh. */
static void PutImage(void)
{
    if (g_frame_sink && DisplayBuf) {
        g_frame_sink((const uint16_t *)DisplayBuf, width, height, g_frame_sink_ud);
    }
}

/****************************************************************************/
/** Timing (microseconds, monotonic) -- replaces SDL ReadTimer             **/
/****************************************************************************/
static long ReadTimer(void)
{
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (long)(ts.tv_sec * 1000000L + ts.tv_nsec / 1000L);
}

/****************************************************************************/
/** Vsync phase-lock                                                       **/
/** Pace frames to the display's hardware vsync (AChoreographer) instead of **/
/** a wall-clock timer, so the 60Hz frame clock -- and thus the music tempo **/
/** driven by the per-frame VDP interrupt -- is the panel's refresh and     **/
/** can't drift/jitter against it. The display is pinned to 60Hz by the     **/
/** app's Surface.setFrameRate(60), so one vsync == one frame. Engages only **/
/** while ~60Hz vsyncs are actually arriving; otherwise (screen off, or a    **/
/** non-60Hz panel) it falls back to the wall-clock sleeper below.          **/
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

/* Block until the *next* display vsync after the one we last presented on, and
   return its time (us). Pacing one emulated frame per vsync locks the machine to
   the panel refresh (~60Hz here, gated by VsyncFresh) with nothing to overshoot.

   The previous version waited for a vsync at/after a wall-clock target that
   advanced by a fixed 1000000/60 = 16666us per frame. On any panel whose true
   period is below that -- i.e. running even a hair above 60.00Hz, which "60Hz"
   panels routinely do -- the on-time vsync fell just short of the target and was
   rejected, so every frame waited two vsync intervals: a rock-steady 30fps (the
   "31 fps, 0 behind, on vsync" we measured). Waiting for the vsync counter to
   simply advance removes the target entirely and is self-correcting: if a frame's
   work overruns a vsync, the next call returns at once instead of stalling.
   Bails on a short timeout so it can never hang if vsyncs stop mid-wait. */
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
/** Lifecycle                                                              **/
/****************************************************************************/
int InitMachine(void)
{
    int i;

    width = WIDTH;
    height = HEIGHT;

    DisplayBuf = (SMS_NTSC_IN_T *)calloc((size_t)width * height, sizeof(SMS_NTSC_IN_T));
    if (!DisplayBuf) {
        LOGW("InitMachine: out of memory allocating frame buffer");
        return 0;
    }

    memset(VGA_Palette, 0, sizeof(VGA_Palette));
    memcpy(VGA_Palette, Coleco_Palette, 16 * 3);

    for (i = 0; i < 16; i++) {
        RGBcolors[i] = pack_rgb565(Palettes[PalNum][i * 3 + 0],
                                   Palettes[PalNum][i * 3 + 1],
                                   Palettes[PalNum][i * 3 + 2]);
        PalBuf[i] = i;
    }
    Pal0 = 0;

    JoyState[0] = JoyState[1] = 0x7F7F;

    /* Parse the default key-mapping string for joystick directions/buttons. */
    sscanf(szKeys, "%03X%03X%03X%03X%03X%03X%03X%03X",
           &KEY_LEFT, &KEY_RIGHT, &KEY_UP, &KEY_DOWN,
           &KEY_BUTTONA, &KEY_BUTTONB, &KEY_BUTTONC, &KEY_BUTTOND);

    keyboardmode = (EmuMode) ? 1 : 0;

    InitSound();

    if (syncemu)
        OldTimer = ReadTimer();

    StartVsync();   /* phase-lock frame pacing to the display refresh */

    LOGI("InitMachine: %dx%d frame buffer, EmuMode=%d palette=%d", width, height, EmuMode, PalNum);
    return 1;
}

void TrashMachine(void)
{
    StopVsync();
    if (DisplayBuf) {
        free(DisplayBuf);
        DisplayBuf = NULL;
    }
}

/****************************************************************************/
/** Per-frame input + pacing (called from the core's VDP interrupt)        **/
/****************************************************************************/
void Keyboard(void)
{
    int reset_mode = -1;
    byte ch;

    /* Drain injected ADAM key codes into the core keyboard buffer. */
    pthread_mutex_lock(&g_input_mutex);
    while (g_key_head != g_key_tail) {
        ch = g_key_queue[g_key_head];
        g_key_head = (g_key_head + 1) % ADAM_KEY_QUEUE;
        pthread_mutex_unlock(&g_input_mutex);
        AddToKeyboardBuffer(ch);
        pthread_mutex_lock(&g_input_mutex);
    }
    reset_mode = g_pending_reset;
    g_pending_reset = -1;
    pthread_mutex_unlock(&g_input_mutex);

    if (reset_mode >= 0)
        ResetColeco(reset_mode);
}

void Joysticks(void)
{
    /* JoyState[] is updated directly by adamhost_set_joystick(); nothing to
       poll here in the headless host. */
}

/* Sleep (don't spin) until an absolute CLOCK_MONOTONIC time, in microseconds.
   Frees the CPU between frames -- a busy-wait pegs a core, which on a phone
   means heat -> thermal throttling -> an unsteady frame rate (audible as a
   "gallop"). clock_nanosleep(TIMER_ABSTIME) wakes precisely via an hrtimer. */
static void SleepUntilUs(long target_us)
{
    struct timespec ts;
    ts.tv_sec  = target_us / 1000000L;
    ts.tv_nsec = (target_us % 1000000L) * 1000L;
    while (clock_nanosleep(CLOCK_MONOTONIC, TIMER_ABSTIME, &ts, NULL) == EINTR)
        ; /* re-sleep if interrupted by a signal */
}

int CheckScreenRefresh(void)
{
    static int skipped = 0;
    if (!syncemu) return 2;

    NewTimer = ReadTimer();
    OldTimer += 1000000L / (IFreq ? IFreq : 60);

    /* Pacing diagnostics: once per real second, report the achieved emulated
       frame rate, how many frames the emulator couldn't keep up with ("behind"),
       and how many used the vsync phase-lock vs the wall-clock fallback. Reading:
         ~60 fps, 0 behind        -> emulation is real-time; any perceived slowness
                                     is render/display or thermal, not the core.
         ~30 fps, mostly behind   -> compute-bound (can't finish a frame in 16.7ms).
         ~30 fps, 0 behind        -> the pacer itself is over-throttling (e.g. a
                                     90Hz panel forcing a 2-vsync wait per frame). */
    int vfresh = VsyncFresh(NewTimer);
    {
        static long dbg_t0 = 0;
        static int  dbg_frames = 0, dbg_behind = 0, dbg_vsync = 0;
        if (dbg_t0 == 0) dbg_t0 = NewTimer;
        dbg_frames++;
        if (vfresh) dbg_vsync++;
        if ((OldTimer - NewTimer) <= 0) dbg_behind++;
        if (NewTimer - dbg_t0 >= 1000000L) {
            LOGI("pace: %d fps over %ld ms (%d behind, %d on vsync)",
                 dbg_frames, (NewTimer - dbg_t0) / 1000, dbg_behind, dbg_vsync);
            dbg_t0 = NewTimer; dbg_frames = 0; dbg_behind = 0; dbg_vsync = 0;
        }
    }

    if (vfresh) {
        /* Locked to the display: one emulated frame per vsync == the panel
           refresh (~60Hz here). Snap OldTimer to the vsync so a later fall back
           to the wall-clock sleeper picks up from real time. */
        OldTimer = WaitNextVsync();
        skipped = 0;
        return 1;
    }

    /* No usable display vsync (screen off / backgrounded / non-60Hz panel):
       fall back to the wall-clock sleeper. */
    if ((OldTimer - NewTimer) > 0) {
        SleepUntilUs(OldTimer);
        skipped = 0;
        return 1;
    } else if (++skipped >= (UPeriod ? UPeriod : 2)) {
        OldTimer = NewTimer;
        skipped = 0;
        return 1;
    }
    return 0;
}

/****************************************************************************/
/** Misc host hooks the core/option parser reference                       **/
/****************************************************************************/
void setFullScreen(int on)        { fullscreen = on ? 1 : 0; }
int  WindowManagerSetPause(int p) { (void)p; return 0; }

/* ADAM serial/modem port (6850 ACIA). Not used for FujiNet-over-AdamNet, so the
   Common.h modem mix-in is skipped (NO_COMMON_MODEM_DRIVERS) and these are stubs. */
int  ModemIn(int reg)             { (void)reg; return 0xFF; }
void ModemOut(int reg, int value) { (void)reg; (void)value; }
void InitText80(void)             { Text80 = 1; RefreshScreen(1); }
void ResetText80(void)            { Text80 = 0; RefreshScreen(1); }

/****************************************************************************/
/** Screen-refresh drivers (RefreshScreen0..3, RefreshScreen) live here     **/
/****************************************************************************/
#include "Common.h"
