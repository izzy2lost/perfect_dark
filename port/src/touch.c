#ifdef ANDROID

#include <stdatomic.h>
#include <jni.h>
#include <PR/ultratypes.h>
#include "touch.h"

static atomic_uint touchButtons;
// Bits pressed since the last read. inputReadController() samples us once a frame, so a tap
// that goes down and back up inside 16ms would otherwise never be seen at all.
static atomic_uint touchLatched;
static atomic_int touchStick[2];
static atomic_int touchActive;

static inline s32 touchClampStick(f32 v)
{
	if (v < -1.f) {
		v = -1.f;
	} else if (v > 1.f) {
		v = 1.f;
	}
	return (s32)(v * TOUCH_STICK_RANGE);
}

void touchSetButtons(u32 contMask)
{
	const u32 prev = atomic_exchange_explicit(&touchButtons, contMask, memory_order_relaxed);
	const u32 pressed = contMask & ~prev;
	if (pressed) {
		atomic_fetch_or_explicit(&touchLatched, pressed, memory_order_relaxed);
	}
}

void touchSetStick(f32 x, f32 y)
{
	atomic_store_explicit(&touchStick[0], touchClampStick(x), memory_order_relaxed);
	atomic_store_explicit(&touchStick[1], touchClampStick(y), memory_order_relaxed);
}

void touchSetActive(s32 active)
{
	if (!active) {
		// make sure nothing stays held down when the overlay goes away
		atomic_store_explicit(&touchButtons, 0u, memory_order_relaxed);
		atomic_store_explicit(&touchLatched, 0u, memory_order_relaxed);
		atomic_store_explicit(&touchStick[0], 0, memory_order_relaxed);
		atomic_store_explicit(&touchStick[1], 0, memory_order_relaxed);
	}
	atomic_store_explicit(&touchActive, active ? 1 : 0, memory_order_relaxed);
}

u32 touchGetButtons(void)
{
	// Anything pressed since the last call is reported for at least this one frame, then
	// cleared, so a very quick tap still produces a full press and release for the game.
	const u32 held = atomic_load_explicit(&touchButtons, memory_order_relaxed);
	const u32 latched = atomic_exchange_explicit(&touchLatched, 0u, memory_order_relaxed);
	return held | latched;
}

void touchGetStick(f32 *outX, f32 *outY)
{
	*outX = (f32)atomic_load_explicit(&touchStick[0], memory_order_relaxed) / TOUCH_STICK_RANGE;
	*outY = (f32)atomic_load_explicit(&touchStick[1], memory_order_relaxed) / TOUCH_STICK_RANGE;
}

s32 touchIsActive(void)
{
	return atomic_load_explicit(&touchActive, memory_order_relaxed);
}

/* JNI entry points -- see com.perfectdark.port.TouchControls */

JNIEXPORT void JNICALL
Java_com_perfectdark_port_TouchControls_nativeSetButtons(JNIEnv *env, jclass cls, jint contMask)
{
	touchSetButtons((u32)contMask);
}

JNIEXPORT void JNICALL
Java_com_perfectdark_port_TouchControls_nativeSetStick(JNIEnv *env, jclass cls, jfloat x, jfloat y)
{
	touchSetStick((f32)x, (f32)y);
}

JNIEXPORT void JNICALL
Java_com_perfectdark_port_TouchControls_nativeSetActive(JNIEnv *env, jclass cls, jboolean active)
{
	touchSetActive(active ? 1 : 0);
}

#else

typedef int touch_translation_unit_not_empty;

#endif // ANDROID
