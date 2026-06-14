/*
 * Minimal SDL_stdinc.h shim: the fixed-width type aliases ADAMEm's sound code
 * (AdamSDLSound_2.h) expects from SDL, without depending on SDL.
 */
#ifndef FUJI_ADAM_SDL_STDINC_SHIM_H
#define FUJI_ADAM_SDL_STDINC_SHIM_H

#include <stdint.h>

typedef uint8_t  Uint8;
typedef int8_t   Sint8;
typedef uint16_t Uint16;
typedef int16_t  Sint16;
typedef uint32_t Uint32;
typedef int32_t  Sint32;
typedef uint64_t Uint64;
typedef int64_t  Sint64;

#endif /* FUJI_ADAM_SDL_STDINC_SHIM_H */
