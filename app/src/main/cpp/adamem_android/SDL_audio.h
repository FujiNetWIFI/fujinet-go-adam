/*
 * Minimal SDL_audio.h shim. ADAMEm's PSG driver (AdamSDLSound_2.c) opens an SDL
 * audio device and registers a callback. On Android there is no SDL device: we
 * neutralise the device open/pause and instead pull samples from the callback
 * (soundData) ourselves, feeding an AudioTrack. See adam_core.cpp's
 * nativeRenderAudio and the Kotlin AudioOutput.
 */
#ifndef FUJI_ADAM_SDL_AUDIO_SHIM_H
#define FUJI_ADAM_SDL_AUDIO_SHIM_H

#include "SDL_stdinc.h"

#ifdef __cplusplus
extern "C" {
#endif

#define AUDIO_S16SYS 0x8010

typedef Uint32 SDL_AudioDeviceID;
typedef void (*SDL_AudioCallback)(void *userdata, Uint8 *stream, int len);

typedef struct SDL_AudioSpec {
    int freq;
    Uint16 format;
    Uint8 channels;
    Uint8 silence;
    Uint16 samples;
    Uint32 size;
    SDL_AudioCallback callback;
    void *userdata;
} SDL_AudioSpec;

/* The ADAM driver only reads back nothing from `obtained`; a fake non-zero
   device id keeps its success path. No real device is opened. */
static inline SDL_AudioDeviceID SDL_OpenAudioDevice(
        const char *device, int iscapture,
        const SDL_AudioSpec *desired, SDL_AudioSpec *obtained, int allowed_changes) {
    (void)device; (void)iscapture; (void)allowed_changes;
    if (obtained != 0 && desired != 0) *obtained = *desired;
    return (SDL_AudioDeviceID)1;
}

static inline void SDL_PauseAudioDevice(SDL_AudioDeviceID dev, int pause_on) {
    (void)dev; (void)pause_on;
}

#ifdef __cplusplus
}
#endif

#endif /* FUJI_ADAM_SDL_AUDIO_SHIM_H */
