// Android virtual touch pad bridge.
//
// State is written from the Java overlay (TouchOverlayView) via JNI and read
// from the game thread through touchApplyToPad(). A single atomic snapshot is
// copied into the controller so the reader never sees a torn state.

#include <string.h>
#include <PR/ultratypes.h>
#include <PR/os_thread.h>
#include <PR/os_cont.h>
#include "platform.h"
#include "touch.h"

#ifdef ANDROID

#include <jni.h>
#include <stdatomic.h>

struct touchSnap {
	f32 lx, ly;    // left stick axes, [-1, 1]
	f32 rx, ry;    // right stick axes, [-1, 1]
	u32 buttons;   // CONT_* mask
};

// Double-buffered snapshot; Java writes `next`, we swap into `live` under a
// seqlock-style guard so we never copy a partially-written struct.
static struct touchSnap snaps[2];
static atomic_int writeSlot = 0;   // slot Java is filling
static atomic_int liveSeq = 0;     // bumped after each publish
static atomic_int active = 0;      // nonzero if any finger down

// Look-pad drag deltas. Written from Java (UI thread), consumed from the
// game thread once per frame. Simple atomic fetch_add / exchange: we do not
// care about precise per-event ordering, only that the running sum does not
// tear or leak motion.
static atomic_int lookDeltaX = 0;
static atomic_int lookDeltaY = 0;

static inline s8 axisToS8(f32 v)
{
	if (v > 1.f) v = 1.f;
	if (v < -1.f) v = -1.f;
	s32 r = (s32)(v * 127.f);
	if (r > 127) r = 127;
	if (r < -128) r = -128;
	return (s8)r;
}

void touchApplyToPad(OSContPad *npad)
{
	if (!atomic_load_explicit(&active, memory_order_acquire)) {
		return;
	}

	// Read the most-recently published slot. Java's publish uses
	// seqlock semantics: liveSeq goes even -> odd -> even; reading
	// even values means "stable".
	int seq;
	struct touchSnap s;
	do {
		seq = atomic_load_explicit(&liveSeq, memory_order_acquire);
		if (seq & 1) { continue; }
		int slot = 1 - atomic_load_explicit(&writeSlot, memory_order_acquire);
		s = snaps[slot];
	} while (seq != atomic_load_explicit(&liveSeq, memory_order_acquire));

	npad->button |= s.buttons;

	// Only override axes if the digital bindings didn't already push them.
	if (!npad->stick_x) {
		npad->stick_x = axisToS8(s.lx);
	}
	if (!npad->stick_y) {
		// Screen Y is down-positive; N64 stick_y is up-positive.
		npad->stick_y = axisToS8(-s.ly);
	}
	if (!npad->rstick_x) {
		npad->rstick_x = axisToS8(s.rx);
	}
	if (!npad->rstick_y) {
		npad->rstick_y = axisToS8(-s.ry);
	}
}

s32 touchIsActive(void)
{
	return atomic_load_explicit(&active, memory_order_acquire);
}

void touchConsumeLookDelta(s32 *dx, s32 *dy)
{
	s32 rx = atomic_exchange_explicit(&lookDeltaX, 0, memory_order_acq_rel);
	s32 ry = atomic_exchange_explicit(&lookDeltaY, 0, memory_order_acq_rel);
	if (dx) *dx = rx;
	if (dy) *dy = ry;
}

// ---------------- JNI entry points ----------------

JNIEXPORT void JNICALL
Java_com_perfectdark_port_TouchOverlayView_nativeSetState(
	JNIEnv *env, jobject thiz,
	jfloat lx, jfloat ly, jfloat rx, jfloat ry,
	jint buttons, jboolean anyDown)
{
	(void)env; (void)thiz;

	int slot = atomic_load_explicit(&writeSlot, memory_order_relaxed);
	// publish begin (odd seq)
	int seq = atomic_load_explicit(&liveSeq, memory_order_relaxed);
	atomic_store_explicit(&liveSeq, seq + 1, memory_order_release);

	snaps[slot].lx = lx;
	snaps[slot].ly = ly;
	snaps[slot].rx = rx;
	snaps[slot].ry = ry;
	snaps[slot].buttons = (u32)buttons;

	// publish end (even seq); flip slot for next writer.
	atomic_store_explicit(&writeSlot, 1 - slot, memory_order_release);
	atomic_store_explicit(&liveSeq, seq + 2, memory_order_release);
	atomic_store_explicit(&active, anyDown ? 1 : 0, memory_order_release);
}

JNIEXPORT void JNICALL
Java_com_perfectdark_port_TouchOverlayView_nativeAddLookDelta(
	JNIEnv *env, jobject thiz, jint dx, jint dy)
{
	(void)env; (void)thiz;
	atomic_fetch_add_explicit(&lookDeltaX, dx, memory_order_release);
	atomic_fetch_add_explicit(&lookDeltaY, dy, memory_order_release);
}

#else // !ANDROID

void touchApplyToPad(OSContPad *npad)
{
	(void)npad;
}

s32 touchIsActive(void)
{
	return 0;
}

void touchConsumeLookDelta(s32 *dx, s32 *dy)
{
	if (dx) *dx = 0;
	if (dy) *dy = 0;
}

#endif
