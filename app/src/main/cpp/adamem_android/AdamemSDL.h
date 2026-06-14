/*
 * Android replacement for ADAMEm's AdamemSDL.h.
 *
 * The upstream header pulls in <SDL_stdinc.h> and SDL scancode macros. The
 * staged ADAMEm core is compiled with -DSDL only so ADAMEm.c can see the option
 * globals declared here -- it does not use any SDL scancodes. This header keeps
 * those declarations but drops every SDL dependency. The implementations live
 * in app/src/main/cpp/adam_host.c (the Android replacement for AdamemSDL.c).
 */
#ifndef _ADAMEMSDL_H
#define _ADAMEMSDL_H

#include "sms_ntsc.h"

/* ADAM VDP frame buffer geometry (TMS9928 active area). */
#define WIDTH  256
#define HEIGHT 212

/* Number of host key slots tracked by the keyboard handler. */
#ifndef NR_KEYS
#define NR_KEYS 512
#endif

/* 16-bit (RGB565) screen buffer written by the Common.h refresh drivers. */
extern SMS_NTSC_IN_T *DisplayBuf;

extern char szJoystickFileName[]; /* File holding joystick information      */
extern char szBitmapFile[];       /* Next screen shot file                  */
extern char szSnapshotFile[];     /* Next snapshot file                     */
extern char *szKeys;              /* Key scancodes                          */
extern int  mouse_sens;           /* Mouse/Joystick sensitivity             */
extern int  keypadmode;           /* 1 if keypad should be reversed         */
extern int  joystick;             /* Joystick support                       */
extern int  calibrate;            /* Set to 1 to force joystick calibration */
extern int  swapbuttons;          /* 1=joystick, 2=keyboard, 4=mouse        */
extern int  expansionmode;        /* Expansion module emulated              */
extern int  syncemu;              /* 0 if emulation shouldn't be synced     */
extern int  SaveCPU;              /* If 1, CPU is saved when focus is out   */
extern int  videomode;            /* 0=1x1  1=2x1                           */
extern int  PausePressed;
extern int  AutoText80;           /* 1 if auto-switch to Text80 mode on     */

void setFullScreen(int on);
int  WindowManagerSetPause(int pauseVal);

#endif /* _ADAMEMSDL_H */
