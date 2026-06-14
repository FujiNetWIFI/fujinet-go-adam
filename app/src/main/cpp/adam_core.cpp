#include <jni.h>
#include <string>
#include <vector>

#include "session_runtime.h"

// ADAMEm PSG sample generator (AdamSDLSound_2.c). Fills `len` bytes of mono
// signed-16 samples at 44100 Hz from the PSG state. soundData reads its state
// from `userdata`, which must be the emulator's PSG state (file-static in
// AdamSDLSound_2.c; exposed via adamsound_get_state()).
extern "C" void soundData(void* userdata, unsigned char* stream, int len);
extern "C" void* adamsound_get_state(void);

namespace {
std::string JStr(JNIEnv* env, jstring s) {
    if (!s) return {};
    const char* c = env->GetStringUTFChars(s, nullptr);
    std::string out(c ? c : "");
    if (c) env->ReleaseStringUTFChars(s, c);
    return out;
}
}  // namespace

extern "C" {

JNIEXPORT void JNICALL
Java_com_mantismoonlabs_fujinetgoadam_core_EmulatorNative_nativeStartSession(
        JNIEnv* env, jobject /*thiz*/,
        jstring runtimeRoot, jstring configPath, jstring sdPath, jstring dataPath,
        jobjectArray adamArgs) {
    std::vector<std::string> args;
    if (adamArgs != nullptr) {
        const jsize n = env->GetArrayLength(adamArgs);
        args.reserve(static_cast<size_t>(n));
        for (jsize i = 0; i < n; ++i) {
            auto s = reinterpret_cast<jstring>(env->GetObjectArrayElement(adamArgs, i));
            args.push_back(JStr(env, s));
            if (s) env->DeleteLocalRef(s);
        }
    }
    SessionRuntime::Get().StartSession(
            JStr(env, runtimeRoot), JStr(env, configPath), JStr(env, sdPath),
            JStr(env, dataPath), args);
}

JNIEXPORT void JNICALL
Java_com_mantismoonlabs_fujinetgoadam_core_EmulatorNative_nativeStopSession(
        JNIEnv* /*env*/, jobject /*thiz*/) {
    SessionRuntime::Get().StopSession();
}

JNIEXPORT jboolean JNICALL
Java_com_mantismoonlabs_fujinetgoadam_core_EmulatorNative_nativeIsRunning(
        JNIEnv* /*env*/, jobject /*thiz*/) {
    return SessionRuntime::Get().IsRunning() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_mantismoonlabs_fujinetgoadam_core_EmulatorNative_nativeAttachSurface(
        JNIEnv* env, jobject /*thiz*/, jobject surface) {
    SessionRuntime::Get().AttachSurface(env, surface);
}

JNIEXPORT void JNICALL
Java_com_mantismoonlabs_fujinetgoadam_core_EmulatorNative_nativeDetachSurface(
        JNIEnv* env, jobject /*thiz*/) {
    SessionRuntime::Get().DetachSurface(env);
}

JNIEXPORT void JNICALL
Java_com_mantismoonlabs_fujinetgoadam_core_EmulatorNative_nativeInjectKey(
        JNIEnv* /*env*/, jobject /*thiz*/, jint adamChar) {
    SessionRuntime::Get().InjectKey(adamChar);
}

JNIEXPORT void JNICALL
Java_com_mantismoonlabs_fujinetgoadam_core_EmulatorNative_nativeSetJoystick(
        JNIEnv* /*env*/, jobject /*thiz*/, jint port, jint adamnetState) {
    SessionRuntime::Get().SetJoystick(port, adamnetState);
}

JNIEXPORT void JNICALL
Java_com_mantismoonlabs_fujinetgoadam_core_EmulatorNative_nativeRequestReset(
        JNIEnv* /*env*/, jobject /*thiz*/, jint mode) {
    SessionRuntime::Get().RequestReset(mode);
}

// Pulls PSG samples into `out` (mono signed-16). Returns the sample count.
JNIEXPORT jint JNICALL
Java_com_mantismoonlabs_fujinetgoadam_core_EmulatorNative_nativeRenderAudio(
        JNIEnv* env, jobject /*thiz*/, jshortArray out) {
    if (out == nullptr) return 0;
    const jsize n = env->GetArrayLength(out);
    if (n <= 0) return 0;
    jshort* buf = env->GetShortArrayElements(out, nullptr);
    if (buf == nullptr) return 0;
    soundData(adamsound_get_state(), reinterpret_cast<unsigned char*>(buf), static_cast<int>(n) * 2);
    env->ReleaseShortArrayElements(out, buf, 0);
    return n;
}

}  // extern "C"
