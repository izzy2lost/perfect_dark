#ifndef ANDROID_SYSTEM_H
#define ANDROID_SYSTEM_H

#ifdef ANDROID

#include <jni.h>
#include <android/log.h>

// Path handed to us by MainActivity.nativeInit(); this is where the ROM and saves live.
const char* sysGetDataPath(void);

// port/src/main.c -- startup handshake with MainActivity
JNIEXPORT void JNICALL Java_com_perfectdark_port_MainActivity_nativeInit(JNIEnv* env, jobject thiz, jstring dataPath);
JNIEXPORT void JNICALL Java_com_perfectdark_port_MainActivity_nativeDestroy(JNIEnv* env, jobject thiz);

// port/src/touch.c -- on-screen controller; see touch.h for the state it feeds
JNIEXPORT void JNICALL Java_com_perfectdark_port_TouchControls_nativeSetButtons(JNIEnv* env, jclass cls, jint contMask);
JNIEXPORT void JNICALL Java_com_perfectdark_port_TouchControls_nativeSetStick(JNIEnv* env, jclass cls, jint stick, jfloat x, jfloat y);
JNIEXPORT void JNICALL Java_com_perfectdark_port_TouchControls_nativeSetActive(JNIEnv* env, jclass cls, jboolean active);

#endif // ANDROID

#endif // ANDROID_SYSTEM_H
