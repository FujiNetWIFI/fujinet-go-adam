/*
 * Minimal SDL.h shim for the headless Android build of the ADAMEm core.
 *
 * ADAMEm is compiled with -DSDL so ADAMEm.c sees the option globals, which
 * makes a couple of core files (e.g. Z80.c) include "SDL.h" for SDL_Delay.
 * No real SDL is linked on device; this forwards to the POSIX timer shim.
 */
#ifndef FUJI_ADAM_SDL_SHIM_H
#define FUJI_ADAM_SDL_SHIM_H

/* The real SDL.h transitively provides the C stdlib; ADAMEm relies on that. */
#include <stdlib.h>
#include <stdio.h>
#include <string.h>

#include "SDL_stdinc.h"
#include "SDL_timer.h"
#include "SDL_audio.h"

#endif /* FUJI_ADAM_SDL_SHIM_H */
