#ifndef _IN_TOUCH_H
#define _IN_TOUCH_H

#include <PR/ultratypes.h>

#ifdef ANDROID

// State of the on-screen controller overlay.
//
// The overlay itself lives in Java (com.perfectdark.port.TouchControls) and runs on the
// Android UI thread, while inputReadController() runs on the SDL thread, so everything
// here is stored atomically. Sticks are kept as fixed point in TOUCH_STICK_RANGE units
// rather than floats so a partially written value can never be observed.

#define TOUCH_STICK_RANGE 1024

enum touchstick {
	TOUCH_STICK_LEFT = 0,  // movement -> N64 analog stick
	TOUCH_STICK_RIGHT = 1, // look     -> N64 C buttons or right stick, depending on config
	TOUCH_STICK_COUNT
};

// called from the Java overlay
// contMask is a bitmask of CONT_ values, ie the same layout as OSContPad.button
void touchSetButtons(u32 contMask);
// x and y are -1 .. 1 in N64 stick convention: +x is right, +y is UP.
// The overlay is responsible for flipping Android's y-down screen coordinates.
void touchSetStick(s32 stick, f32 x, f32 y);
void touchSetActive(s32 active);

// called from inputReadController()
u32 touchGetButtons(void);
void touchGetStick(s32 stick, f32 *outX, f32 *outY);

// true while the overlay is visible and driving input
s32 touchIsActive(void);

#endif // ANDROID

#endif
