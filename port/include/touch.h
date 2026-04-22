#ifndef _IN_TOUCH_H
#define _IN_TOUCH_H

#include <PR/ultratypes.h>
#include <PR/os_cont.h>

// Called by inputReadController() once per poll so the virtual pad can
// contribute to player 0's OSContPad. On platforms without a touchscreen
// this is a no-op.
void touchApplyToPad(OSContPad *npad);

// Returns nonzero if the virtual pad has produced input in the last frame.
// Used by input.c so real-controller input can still trump touch when both
// exist without forcing the touch overlay to always show.
s32 touchIsActive(void);

#endif
