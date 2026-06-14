#pragma once

#include <jni.h>
#include <android/native_window.h>

#include <atomic>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

// Orchestrates one ADAM session: the ADAMEm core (run on a worker thread via
// adamem_main) plus the in-process FujiNet runtime, joined over AdamNet
// "Bus over IP" on loopback TCP 65216 (ADAMEm listens, FujiNet connects in).
class SessionRuntime {
public:
    static SessionRuntime& Get();

    // Paths: the FujiNet runtime root/config/SD/data. adam_args is the ADAMEm
    // command line (without argv[0]) built by the Kotlin layer: machine, ROM
    // paths, -fujinet, -palette, optional -cart, etc.
    void StartSession(const std::string& runtime_root,
                      const std::string& config_path,
                      const std::string& sd_path,
                      const std::string& data_path,
                      const std::vector<std::string>& adam_args);
    void StopSession();
    bool IsRunning() const { return running_.load(); }

    void AttachSurface(JNIEnv* env, jobject surface);
    void DetachSurface(JNIEnv* env);

    void InjectKey(int adam_char);
    void SetJoystick(int port, int adamnet_state);
    void RequestReset(int mode);

    // Called (on the emulator thread) by adam_host's PutImage via the frame sink.
    void OnFrame(const uint16_t* rgb565, int width, int height);

private:
    SessionRuntime() = default;
    SessionRuntime(const SessionRuntime&) = delete;
    SessionRuntime& operator=(const SessionRuntime&) = delete;

    void EmulatorThreadMain();

    static constexpr int kBoIpPort = 65216;

    mutable std::mutex surface_mutex_;
    ANativeWindow* window_ = nullptr;

    std::mutex lifecycle_mutex_;
    std::thread emulator_thread_;
    std::atomic<bool> running_{false};

    // Synthesized argv for adamem_main; strings must outlive the emulator (the
    // core stores argv[] pointers as its ROM/option paths).
    std::vector<std::string> arg_storage_;
    std::vector<char*> argv_;

    std::string runtime_root_;
    std::string config_path_;
    std::string sd_path_;
    std::string data_path_;
};
