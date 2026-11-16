#pragma once

#include <atomic>
#include <chrono>
#include <cstdint>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#include "local_controller_service.h"

enum class ControllerPage {
    Login,
    BrowserAuth,
    MinecraftSelection,
    Loading,
    CachePrompt,
    LoadingComplete,
    OutdatedLauncher,
    Error
};

struct MinecraftProcess {
    std::uint32_t pid{};
    std::wstring title;
    bool alreadyInjected{};
};

class ControllerModel {
public:
    ControllerModel();
    ~ControllerModel();

    ControllerPage page() const;
    void setPage(ControllerPage value);
    std::wstring status() const;
    void setStatus(std::wstring value);
    // Raw, untranslated failure text reported by the native bootstrap (for
    // example "Native bootstrap failed with code 7"). The UI shows only the
    // localized explanation, but the copy button still includes this line so
    // support reports keep the technical detail.
    std::wstring errorDetail() const;

    std::wstring& username();
    std::wstring& password();
    std::vector<MinecraftProcess> minecraftProcesses() const;

    void refreshMinecraftProcesses();
    bool injectMinecraft(std::uint32_t processId);
    // Browser-based authentication was removed together with its remote
    // www.vape.gg endpoints. These entry points are kept so the UI keeps
    // compiling; none of them issues a network request. The begin call reports a
    // local failure and returns to ControllerPage::Login.
    void beginBrowserAuthentication(void* windowHandle);
    void reopenBrowserAuthentication();
    void cancelBrowserAuthentication();
    void persistCachePreference(bool enabled);
    bool cachePreference() const;
    std::wstring cacheDirectory() const;
    void tick();
    int loadingStage() const;
    double loadingElapsedSeconds() const;
    double stageElapsedSeconds() const;
    void submitCredentialAuthentication();
    void autoLoginToService();
    bool loginToService();

private:
    static std::string httpPost(const wchar_t* host, const wchar_t* path,
        const std::string& body);
    static std::string httpPostJson(const std::wstring& baseUrl,
        const wchar_t* path, const std::string& body);
    static std::string jsonString(const std::string& json, const char* key);
    bool materializeEmbeddedDll(std::uint32_t processId, std::wstring& output);

    mutable std::mutex mutex_;
    ControllerPage page_{ControllerPage::Login};
    std::wstring status_;
    std::wstring errorDetail_;
    std::wstring username_;
    std::wstring password_;
    std::vector<MinecraftProcess> minecraftProcesses_;
    std::vector<std::uint32_t> injectedProcesses_;
    std::string accessToken_;
    std::wstring serviceHttpBase_;
    LocalControllerService service_;
    bool cachePreference_{};
    int loadingStage_{};
    std::chrono::steady_clock::time_point loadingStarted_{};
    std::chrono::steady_clock::time_point stageStarted_{};
    std::chrono::steady_clock::time_point lastMinecraftRefresh_{};
};
