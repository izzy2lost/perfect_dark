#ifndef _IN_TOUCH_H
#define _IN_TOUCH_H

#include <PR/ultratypes.h>
#include <PR/os_thread.h>
#include <PR/os_cont.h>

// Called by inputReadController() once per poll so the virtual pad can
// contribute to player 0's OSContPad. On platforms without a touchscreen
// this is a no-op.
void touchApplyToPad(OSContPad *npad);

// Returns nonzero if the virtual pad has produced input in the last frame.
// Used by input.c so real-controller input can still trump touch when both
// exist without forcing the touch overlay to always show.
s32 touchIsActive(void);

// Called by inputUpdateMouse() to drain any pending look delta that was
// produced by a drag on the touch LOOK_PAD since last frame. The returned
// values are added to mouseDX/mouseDY so the game's mouselook code rotates
// the camera by the drag amount and stops when the finger stops. Reading
// is destructive: the internal accumulator resets to zero after each call.
void touchConsumeLookDelta(s32 *dx, s32 *dy);

#endif
