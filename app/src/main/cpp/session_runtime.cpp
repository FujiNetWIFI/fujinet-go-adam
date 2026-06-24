#include "session_runtime.h"

#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>

#include <pthread.h>
#include <sys/resource.h>

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
    render_running_.store(true);
    render_thread_ = std::thread(&SessionRuntime::RenderThreadMain, this);
    emulator_thread_ = std::thread(&SessionRuntime::EmulatorThreadMain, this);
    LOGI("Session started (BoIP %d)", kBoIpPort);
}

void SessionRuntime::EmulatorThreadMain() {
    // Name the thread so a native tombstone identifies it. Without this, every
    // native worker inherits the comm name of its creator -- the Kotlin
    // "adam-bootstrap" thread that calls nativeStartSession -- so unrelated
    // crashes in the emulator, render, vsync, or FujiNet threads all show up as
    // "adam-bootstrap" and can't be told apart.
    pthread_setname_np(pthread_self(), "adam-emu");
    // Run the emulator thread at a raised priority (Android THREAD_PRIORITY_
    // URGENT_DISPLAY = -8) so it isn't preempted off its 60Hz frame schedule by
    // background/UI work -- jittery frame timing is what makes the audio tempo
    // (driven by the per-frame VDP interrupt) sound uneven. Still below the
    // audio feeder (URGENT_AUDIO = -19), which must never underrun.
    setpriority(PRIO_PROCESS, 0, -8);
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
    // Stop the render thread (wake it if it's waiting for a frame).
    render_running_.store(false);
    {
        std::lock_guard<std::mutex> lock(frame_mutex_);
        frame_dirty_ = true;
    }
    frame_cv_.notify_all();
    if (render_thread_.joinable()) {
        render_thread_.join();
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
    }
    // Repaint the last frame (via the render thread) so a surface recreated by a
    // UI toggle isn't blank until the core next decides the screen changed.
    SignalRepaint();
}

void SessionRuntime::DetachSurface(JNIEnv* env) {
    (void)env;
    std::lock_guard<std::mutex> lock(surface_mutex_);
    if (window_) {
        ANativeWindow_release(window_);
        window_ = nullptr;
    }
}

// Producer: runs on the emulator thread. Only caches the frame and wakes the
// render thread -- never touches the surface, so a stalled blit (e.g. during a
// screen recording) can't freeze the emulator and thus AdamNet/FujiNet traffic.
void SessionRuntime::OnFrame(const uint16_t* rgb565, int width, int height) {
    if (!rgb565 || width <= 0 || height <= 0) return;
    const size_t pixels = static_cast<size_t>(width) * height;
    {
        std::lock_guard<std::mutex> lock(frame_mutex_);
        if (last_frame_.size() != pixels) last_frame_.resize(pixels);
        std::memcpy(last_frame_.data(), rgb565, pixels * sizeof(uint16_t));
        last_frame_w_ = width;
        last_frame_h_ = height;
        frame_dirty_ = true;
    }
    frame_cv_.notify_one();
}

void SessionRuntime::SignalRepaint() {
    {
        std::lock_guard<std::mutex> lock(frame_mutex_);
        frame_dirty_ = true;
    }
    frame_cv_.notify_one();
}

void SessionRuntime::RenderThreadMain() {
    pthread_setname_np(pthread_self(), "adam-render");
    std::vector<uint16_t> scratch;
    int w = 0, h = 0;
    while (render_running_.load()) {
        {
            std::unique_lock<std::mutex> lock(frame_mutex_);
            frame_cv_.wait(lock, [this] { return frame_dirty_ || !render_running_.load(); });
            if (!render_running_.load()) break;
            frame_dirty_ = false;
            if (last_frame_.empty()) continue;
            scratch = last_frame_;  // copy out; release frame_mutex_ before blitting
            w = last_frame_w_;
            h = last_frame_h_;
        }
        // Grab a ref to the current window without holding it across the blit, so
        // attach/detach never block behind a stalled ANativeWindow_lock.
        ANativeWindow* w_local = nullptr;
        {
            std::lock_guard<std::mutex> lock(surface_mutex_);
            if (window_) {
                w_local = window_;
                ANativeWindow_acquire(w_local);
            }
        }
        if (w_local) {
            PresentTo(w_local, scratch.data(), w, h);
            ANativeWindow_release(w_local);
        }
    }
}

void SessionRuntime::PresentTo(ANativeWindow* w, const uint16_t* rgb565, int width, int height) {
    if (!w || !rgb565) return;

    ANativeWindow_setBuffersGeometry(w, width, height, WINDOW_FORMAT_RGB_565);

    ANativeWindow_Buffer buffer;
    if (ANativeWindow_lock(w, &buffer, nullptr) != 0) {
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
    ANativeWindow_unlockAndPost(w);
}

void SessionRuntime::InjectKey(int adam_char) { adamhost_inject_key(adam_char); }
void SessionRuntime::SetJoystick(int port, int state) { adamhost_set_joystick(port, state); }
void SessionRuntime::RequestReset(int mode) { adamhost_request_reset(mode); }
