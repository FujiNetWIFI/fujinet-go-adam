/*
 * Minimal SDL_timer.h shim for the headless Android build of the ADAMEm core.
 *
 * The staged ADAMEm sources are compiled with -DSDL (so ADAMEm.c picks up the
 * AdamemSDL.h option globals), which drags in two trivial SDL timer calls --
 * Z80.c's Z80_Delay() and Sound.c -- without needing real SDL. This shim maps
 * them onto POSIX so no SDL dependency is required on device.
 */
#ifndef FUJI_ADAM_SDL_TIMER_SHIM_H
#define FUJI_ADAM_SDL_TIMER_SHIM_H

#include <stdint.h>
#include <time.h>

#ifdef __cplusplus
extern "C" {
#endif

static inline void SDL_Delay(uint32_t ms)
{
    struct timespec ts;
    ts.tv_sec = (time_t)(ms / 1000u);
    ts.tv_nsec = (long)((ms % 1000u) * 1000000L);
    nanosleep(&ts, NULL);
}

static inline uint32_t SDL_GetTicks(void)
{
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (uint32_t)(ts.tv_sec * 1000u + ts.tv_nsec / 1000000u);
}

#ifdef __cplusplus
}
#endif

#endif /* FUJI_ADAM_SDL_TIMER_SHIM_H */
