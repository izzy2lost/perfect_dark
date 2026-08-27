#ifndef _IN_TOUCH_H
#define _IN_TOUCH_H

#include <PR/ultratypes.h>

#ifdef ANDROID

// State of the on-screen controller overlay.
//
// The overlay mirrors a real N64 pad: one analog stick plus buttons. Looking and sidestepping
// are the C buttons, exactly as on hardware, so there is no second stick here.
//
// The overlay itself lives in Java (com.perfectdark.port.TouchControls) and runs on the
// Android UI thread, while inputReadController() runs on the SDL thread, so everything
// here is stored atomically. Sticks are kept as fixed point in TOUCH_STICK_RANGE units
// rather than floats so a partially written value can never be observed.

#define TOUCH_STICK_RANGE 1024

// called from the Java overlay
// contMask is a bitmask of CONT_ values, ie the same layout as OSContPad.button
void touchSetButtons(u32 contMask);
// The single analog stick, as on the N64 pad. x and y are -1 .. 1 in N64 convention:
// +x is right, +y is UP. The overlay flips Android's y-down screen coordinates.
void touchSetStick(f32 x, f32 y);
void touchSetActive(s32 active);

// called from inputReadController()
u32 touchGetButtons(void);
void touchGetStick(f32 *outX, f32 *outY);

// true while the overlay is visible and driving input
s32 touchIsActive(void);

#endif // ANDROID

#endif
