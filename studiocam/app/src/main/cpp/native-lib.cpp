#include <jni.h>
#include <memory>
#include "AudioEngine.h"

namespace {
std::unique_ptr<AudioEngine> g_engine;
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_aviad_studiocam_audio_NativeAudioEngine_nativeStart(
    JNIEnv *env, jobject, jint inputDeviceId
) {
    g_engine = std::make_unique<AudioEngine>();
    return g_engine->start(inputDeviceId) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_aviad_studiocam_audio_NativeAudioEngine_nativeStop(JNIEnv *env, jobject) {
    if (g_engine) {
        g_engine->stop();
        g_engine.reset();
    }
}

JNIEXPORT void JNICALL
Java_com_aviad_studiocam_audio_NativeAudioEngine_nativeSetChannelParams(
    JNIEnv *env, jobject,
    jint channel, jint physicalIndex, jfloat volume, jfloat pan,
    jboolean reverbOn, jfloat reverbMix, jfloat reverbSize,
    jboolean delayOn, jfloat delayMix, jfloat delayFeedback
) {
    if (g_engine) {
        g_engine->setChannelParams(
            channel, physicalIndex, volume, pan,
            reverbOn == JNI_TRUE, reverbMix, reverbSize,
            delayOn == JNI_TRUE, delayMix, delayFeedback
        );
    }
}

JNIEXPORT jfloat JNICALL
Java_com_aviad_studiocam_audio_NativeAudioEngine_nativeGetLevel(
    JNIEnv *env, jobject, jint channel
) {
    if (g_engine) {
        return g_engine->getLevel(channel);
    }
    return 0.0f;
}

JNIEXPORT void JNICALL
Java_com_aviad_studiocam_audio_NativeAudioEngine_nativeStartFileRecording(
    JNIEnv *env, jobject, jstring path
) {
    if (!g_engine) return;
    const char *pathChars = env->GetStringUTFChars(path, nullptr);
    g_engine->startFileRecording(pathChars);
    env->ReleaseStringUTFChars(path, pathChars);
}

JNIEXPORT void JNICALL
Java_com_aviad_studiocam_audio_NativeAudioEngine_nativeStopFileRecording(JNIEnv *env, jobject) {
    if (g_engine) {
        g_engine->stopFileRecording();
    }
}

} // extern "C"
