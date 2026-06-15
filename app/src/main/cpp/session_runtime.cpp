#include "session_runtime.h"

#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>

#include <cstring>

#define LOG_TAG "AdamSession"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// --- ADAMEm core (C) -------------------------------------------------------
extern "C" {
int  adamem_main(int argc, char* argv[]);
extern int Z80_Running;  // defined in Z80.c; set 0 to terminate the emulation loop
void adamhost_set_frame_sink(void (*sink)(const uint16_t*, int, int, void*), void* ud);
void adamhost_inject_key(int adam_char);
void adamhost_set_joystick(int port, int adamnet_state);
void adamhost_request_reset(int mode);
}

// --- FujiNet runtime dlopen wrapper (fujinet_android.cpp) -------------------
extern "C" {
bool        FujiNetAndroid_StartRuntime(const char* runtimeRootPath,
                                        const char* configPath,
                                        const char* sdPath,
                                        const char* dataPath,
                                        int listenPort);
void        FujiNetAndroid_StopRuntime();
const char* FujiNetAndroid_LastErrorMessage();
bool        FujiNetAndroid_IsRuntimeRunning();
}

namespace {
void frame_sink_trampoline(const uint16_t* rgb565, int w, int h, void* ud) {
    static_cast<SessionRuntime*>(ud)->OnFrame(rgb565, w, h);
}
}  // namespace

SessionRuntime& SessionRuntime::Get() {
    static SessionRuntime instance;
    return instance;
}

void SessionRuntime::StartSession(const std::string& runtime_root,
                                  const std::string& config_path,
                                  const std::string& sd_path,
                                  const std::string& data_path,
                                  const std::vector<std::string>& adam_args) {
    std::lock_guard<std::mutex> lock(lifecycle_mutex_);
    if (running_.load()) {
        LOGW("StartSession ignored; session already running");
        return;
    }

    runtime_root_ = runtime_root;
    config_path_ = config_path;
    sd_path_ = sd_path;
    data_path_ = data_path;

    // argv[0] + the ADAMEm command line built by the Kotlin layer.
    arg_storage_.clear();
    arg_storage_.push_back("adamem");
    for (const std::string& a : adam_args) arg_storage_.push_back(a);
    argv_.clear();
    argv_.reserve(arg_storage_.size());
    for (std::string& a : arg_storage_) argv_.push_back(a.data());

    adamhost_set_frame_sink(&frame_sink_trampoline, this);

    // Start the FujiNet runtime first; NetAdamNet retries connecting to the
    // emulator's listener, so ordering is forgiving.
    if (!FujiNetAndroid_StartRuntime(runtime_root_.c_str(), config_path_.c_str(),
                                     sd_path_.c_str(), data_path_.c_str(), kBoIpPort)) {
        const char* err = FujiNetAndroid_LastErrorMessage();
        LOGE("FujiNet runtime failed to start: %s", err ? err : "(unknown)");
        // Continue anyway: ADAMEm still boots, just without the FujiNet drive.
    }

    Z80_Running = 1;
    running_.store(true);
    emulator_thread_ = std::thread(&SessionRuntime::EmulatorThreadMain, this);
    LOGI("Session started (BoIP %d)", kBoIpPort);
}

void SessionRuntime::EmulatorThreadMain() {
    LOGI("Emulator thread: entering adamem_main with %zu args", argv_.size());
    int rc = adamem_main(static_cast<int>(argv_.size()), argv_.data());
    LOGI("Emulator thread: adamem_main returned %d", rc);
    running_.store(false);
}

void SessionRuntime::StopSession() {
    std::lock_guard<std::mutex> lock(lifecycle_mutex_);
    if (!running_.load() && !emulator_thread_.joinable()) {
        return;
    }

    Z80_Running = 0;  // break the core's execution loop
    if (emulator_thread_.joinable()) {
        emulator_thread_.join();
    }
    FujiNetAndroid_StopRuntime();
    running_.store(false);
    adamhost_set_frame_sink(nullptr, nullptr);
    LOGI("Session stopped");
}

void SessionRuntime::AttachSurface(JNIEnv* env, jobject surface) {
    std::lock_guard<std::mutex> lock(surface_mutex_);
    if (window_) {
        ANativeWindow_release(window_);
        window_ = nullptr;
    }
    if (surface) {
        window_ = ANativeWindow_fromSurface(env, surface);
        LOGI("AttachSurface: window=%p", static_cast<void*>(window_));
        // Repaint the last frame so a surface recreated by a UI toggle isn't
        // blank until the core next decides the screen changed.
        if (!last_frame_.empty()) {
            PresentLocked(last_frame_.data(), last_frame_w_, last_frame_h_);
        }
    }
}

void SessionRuntime::DetachSurface(JNIEnv* env) {
    (void)env;
    std::lock_guard<std::mutex> lock(surface_mutex_);
    if (window_) {
        ANativeWindow_release(window_);
        window_ = nullptr;
    }
}

void SessionRuntime::OnFrame(const uint16_t* rgb565, int width, int height) {
    std::lock_guard<std::mutex> lock(surface_mutex_);
    static bool first_frame = true;
    if (first_frame) {
        first_frame = false;
        LOGI("First frame: %dx%d window=%p", width, height, static_cast<void*>(window_));
    }
    if (!rgb565 || width <= 0 || height <= 0) return;

    // Cache so a surface (re)attached later can be repainted without the core.
    const size_t pixels = static_cast<size_t>(width) * height;
    if (last_frame_.size() != pixels) last_frame_.resize(pixels);
    std::memcpy(last_frame_.data(), rgb565, pixels * sizeof(uint16_t));
    last_frame_w_ = width;
    last_frame_h_ = height;

    PresentLocked(rgb565, width, height);
}

void SessionRuntime::PresentLocked(const uint16_t* rgb565, int width, int height) {
    if (!window_ || !rgb565) return;

    ANativeWindow_setBuffersGeometry(window_, width, height, WINDOW_FORMAT_RGB_565);

    ANativeWindow_Buffer buffer;
    if (ANativeWindow_lock(window_, &buffer, nullptr) != 0) {
        return;
    }
    const int copy_w = buffer.width < width ? buffer.width : width;
    const int copy_h = buffer.height < height ? buffer.height : height;
    auto* dst = static_cast<uint8_t*>(buffer.bits);
    for (int y = 0; y < copy_h; ++y) {
        std::memcpy(dst + static_cast<size_t>(y) * buffer.stride * 2,
                    rgb565 + static_cast<size_t>(y) * width,
                    static_cast<size_t>(copy_w) * 2);
    }
    ANativeWindow_unlockAndPost(window_);
}

void SessionRuntime::InjectKey(int adam_char) { adamhost_inject_key(adam_char); }
void SessionRuntime::SetJoystick(int port, int state) { adamhost_set_joystick(port, state); }
void SessionRuntime::RequestReset(int mode) { adamhost_request_reset(mode); }
