#ifdef ANDROID

#include <stdatomic.h>
#include <jni.h>
#include <PR/ultratypes.h>
#include "touch.h"

static atomic_uint touchButtons;
static atomic_int touchStick[TOUCH_STICK_COUNT][2];
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
	atomic_store_explicit(&touchButtons, contMask, memory_order_relaxed);
}

void touchSetStick(s32 stick, f32 x, f32 y)
{
	if (stick < 0 || stick >= TOUCH_STICK_COUNT) {
		return;
	}
	atomic_store_explicit(&touchStick[stick][0], touchClampStick(x), memory_order_relaxed);
	atomic_store_explicit(&touchStick[stick][1], touchClampStick(y), memory_order_relaxed);
}

void touchSetActive(s32 active)
{
	if (!active) {
		// make sure nothing stays held down when the overlay goes away
		atomic_store_explicit(&touchButtons, 0u, memory_order_relaxed);
		for (s32 i = 0; i < TOUCH_STICK_COUNT; ++i) {
			atomic_store_explicit(&touchStick[i][0], 0, memory_order_relaxed);
			atomic_store_explicit(&touchStick[i][1], 0, memory_order_relaxed);
		}
	}
	atomic_store_explicit(&touchActive, active ? 1 : 0, memory_order_relaxed);
}

u32 touchGetButtons(void)
{
	return atomic_load_explicit(&touchButtons, memory_order_relaxed);
}

void touchGetStick(s32 stick, f32 *outX, f32 *outY)
{
	if (stick < 0 || stick >= TOUCH_STICK_COUNT) {
		*outX = 0.f;
		*outY = 0.f;
		return;
	}
	*outX = (f32)atomic_load_explicit(&touchStick[stick][0], memory_order_relaxed) / TOUCH_STICK_RANGE;
	*outY = (f32)atomic_load_explicit(&touchStick[stick][1], memory_order_relaxed) / TOUCH_STICK_RANGE;
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
Java_com_perfectdark_port_TouchControls_nativeSetStick(JNIEnv *env, jclass cls, jint stick, jfloat x, jfloat y)
{
	touchSetStick((s32)stick, (f32)x, (f32)y);
}

JNIEXPORT void JNICALL
Java_com_perfectdark_port_TouchControls_nativeSetActive(JNIEnv *env, jclass cls, jboolean active)
{
	touchSetActive(active ? 1 : 0);
}

#else

typedef int touch_translation_unit_not_empty;

#endif // ANDROID
