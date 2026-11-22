#include "controller_model.h"
#include "injection_coordinator.h"
#include "loader_strings.h"

#include <windows.h>
#include <tlhelp32.h>
#include <winhttp.h>

#include <algorithm>
#include <chrono>
#include <cstdlib>
#include <fstream>
#include <random>
#include <sstream>
#include <thread>
#include <unordered_map>

namespace {
// Maps a native bootstrap failure ("Native bootstrap failed with code N") to a
// user facing explanation. The codes come from native/dllmain.c where the
// bootstrap thread reports its failure point:
//   2  jvm.dll never appeared (game not started / not a Java process)
//   3  JNI_GetCreatedJavaVMs export missing
//   4  JNI_GetCreatedJavaVMs returned no VM (injected too early)
//   5  AttachCurrentThreadAsDaemon failed
//   6  launcher/game token handshake failed
//   7  this game process already has a Vape DLL loaded
//   8+ attached to the JVM but a later bootstrap step failed
Ls friendlyNativeFailure(const std::string& detail) {
    const std::size_t marker = detail.find("code ");
    if (marker == std::string::npos) return Ls::Count;
    const int code = std::atoi(detail.c_str() + marker + 5);
    switch (code) {
        case 2: return Ls::ErrorJvmNotLoaded;
        case 3: return Ls::ErrorJvmExportMissing;
        case 4: return Ls::ErrorNoJavaVm;
        case 5: return Ls::ErrorAttachThread;
        case 6: return Ls::ErrorLoaderToken;
        case 7: return Ls::ErrorAlreadyInjected;
        case 8: case 9: case 10: case 11: case 12:
        case 13: case 14: case 15: case 16:
            return Ls::ErrorJavaStage;
        default:
            return Ls::ErrorUnknownStage;
    }
}

std::wstring onlineBaseUrl() {
    wchar_t value[2048]{};
    const DWORD length = GetEnvironmentVariableW(L"VAPE_ONLINE_BASE_URL", value,
        static_cast<DWORD>(std::size(value)));
    std::wstring result = length > 0 && length < std::size(value)
        ? std::wstring(value, length) : L"http://127.0.0.1:8080";
    while (!result.empty() && result.back() == L'/') result.pop_back();
    return result;
}

std::string utf8(const std::wstring& value) {
    if (value.empty()) return {};
    const int size = WideCharToMultiByte(CP_UTF8, WC_ERR_INVALID_CHARS, value.data(),
        static_cast<int>(value.size()), nullptr, 0, nullptr, nullptr);
    if (size <= 0) return {};
    std::string result(static_cast<std::size_t>(size), '\0');
    WideCharToMultiByte(CP_UTF8, WC_ERR_INVALID_CHARS, value.data(),
        static_cast<int>(value.size()), result.data(), size, nullptr, nullptr);
    return result;
}

std::string jsonEscape(const std::string& value) {
    std::string result;
    result.reserve(value.size());
    for (const unsigned char character : value) {
        switch (character) {
        case '\\': result += "\\\\"; break;
        case '"': result += "\\\""; break;
        case '\b': result += "\\b"; break;
        case '\f': result += "\\f"; break;
        case '\n': result += "\\n"; break;
        case '\r': result += "\\r"; break;
        case '\t': result += "\\t"; break;
        default:
            if (character >= 0x20) result.push_back(static_cast<char>(character));
            break;
        }
    }
    return result;
}

// Deletes every entry under directory (recursively) but keeps the directory
// itself, so the recovery folder only ever holds the current session's files.
void sweepDirectory(const std::wstring& directory) {
    WIN32_FIND_DATAW data{};
    const std::wstring pattern = directory + L"\\*";
    HANDLE found = FindFirstFileW(pattern.c_str(), &data);
    if (found == INVALID_HANDLE_VALUE) return;
    do {
        if (wcscmp(data.cFileName, L".") == 0 ||
                wcscmp(data.cFileName, L"..") == 0) {
            continue;
        }
        const std::wstring child = directory + L"\\" + data.cFileName;
        if ((data.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) != 0) {
            sweepDirectory(child);
            RemoveDirectoryW(child.c_str());
        } else {
            SetFileAttributesW(child.c_str(), FILE_ATTRIBUTE_NORMAL);
            DeleteFileW(child.c_str());
        }
    } while (FindNextFileW(found, &data));
    FindClose(found);
}

void captureWindowTitles(std::vector<MinecraftProcess>& candidates) {
    EnumWindows([](HWND window, LPARAM value) -> BOOL {
        auto* candidates = reinterpret_cast<std::vector<MinecraftProcess>*>(value);
        if (!IsWindowVisible(window) || GetWindowTextLengthW(window) == 0) {
            return TRUE;
        }

        DWORD processId = 0;
        GetWindowThreadProcessId(window, &processId);
        wchar_t title[256]{};
        if (processId == 0 ||
                GetWindowTextW(window, title, static_cast<int>(std::size(title))) == 0) {
            return TRUE;
        }

        const auto candidate = std::find_if(candidates->begin(), candidates->end(),
            [processId](const MinecraftProcess& process) {
                return process.pid == processId && process.title.empty();
            });
        if (candidate != candidates->end()) candidate->title = title;
        return TRUE;
    }, reinterpret_cast<LPARAM>(&candidates));
}

std::wstring executableDirectory();

// Diagnostic trail for the automatic-injection watchdog. Kept next to the
// preferences so a user report can include it when auto injection misbehaves.
void appendAutoInjectLog(const std::wstring& line) {
    const std::wstring directory = executableDirectory() + L"\\.vapeclient\\";
    CreateDirectoryW(directory.c_str(), nullptr);
    SYSTEMTIME now{};
    GetLocalTime(&now);
    wchar_t stamp[40]{};
    swprintf_s(stamp, L"[%02u:%02u:%02u.%03u] ",
        now.wHour, now.wMinute, now.wSecond, now.wMilliseconds);
    std::wofstream output(directory + L"auto-inject.log", std::ios::app);
    output << stamp << line << L"\r\n";
}

// True when a Java process already hosts a visible Minecraft window. Java-based
// launchers (HMCL, PCL in Java mode, ...) also run as javaw.exe, so counting
// Java processes alone would wrongly suppress automatic injection at startup.
bool hasRunningMinecraftGame() {
    std::vector<DWORD> javaPids;
    HANDLE snapshot = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
    if (snapshot == INVALID_HANDLE_VALUE) return false;
    PROCESSENTRY32W entry{sizeof(entry)};
    if (Process32FirstW(snapshot, &entry)) {
        do {
            if (_wcsicmp(entry.szExeFile, L"java.exe") == 0 ||
                    _wcsicmp(entry.szExeFile, L"javaw.exe") == 0) {
                javaPids.push_back(entry.th32ProcessID);
            }
        } while (Process32NextW(snapshot, &entry));
    }
    CloseHandle(snapshot);
    if (javaPids.empty()) return false;

    struct Context {
        const std::vector<DWORD>* pids;
        bool found;
    };
    Context context{&javaPids, false};
    EnumWindows([](HWND window, LPARAM value) -> BOOL {
        auto* context = reinterpret_cast<Context*>(value);
        if (!IsWindowVisible(window) || GetWindowTextLengthW(window) == 0) return TRUE;
        wchar_t title[256]{};
        if (GetWindowTextW(window, title, static_cast<int>(std::size(title))) == 0) {
            return TRUE;
        }
        if (std::wstring(title).find(L"Minecraft") == std::wstring::npos) return TRUE;
        DWORD processId = 0;
        GetWindowThreadProcessId(window, &processId);
        const auto match = std::find(context->pids->begin(), context->pids->end(), processId);
        if (match == context->pids->end()) return TRUE;
        context->found = true;
        return FALSE;
    }, reinterpret_cast<LPARAM>(&context));
    return context.found;
}

std::wstring executableDirectory() {
    wchar_t path[MAX_PATH]{};
    GetModuleFileNameW(nullptr, path, MAX_PATH);
    std::wstring result(path);
    const auto separator = result.find_last_of(L"\\/");
    return separator == std::wstring::npos ? L"." : result.substr(0, separator);
}

// Ensures <exe>\.vapeclient exists and is hidden. The injected DLL derives
// the client directory from its own module path (<exe>\.vapeclient\Vape-v4.21Recovery),
// so no TEMP marker file is needed; this just makes the folder visible early
// and removes the obsolete TEMP marker/diagnostics left by older builds.
void ensureClientDirectoryHidden() {
    const std::wstring directory = executableDirectory() + L"\\.vapeclient";
    if (CreateDirectoryW(directory.c_str(), nullptr) ||
            GetLastError() == ERROR_ALREADY_EXISTS) {
        const DWORD attributes = GetFileAttributesW(directory.c_str());
        if (attributes != INVALID_FILE_ATTRIBUTES &&
                (attributes & FILE_ATTRIBUTE_HIDDEN) == 0) {
            SetFileAttributesW(directory.c_str(),
                attributes | FILE_ATTRIBUTE_HIDDEN);
        }
    }
    // Best-effort cleanup of the legacy TEMP-based directory handshake.
    wchar_t tempRoot[MAX_PATH]{};
    if (GetTempPathW(static_cast<DWORD>(std::size(tempRoot)), tempRoot) != 0 &&
            tempRoot[0] != L'\0') {
        std::wstring root = tempRoot;
        if (!root.empty() && root.back() != L'\\') root.push_back(L'\\');
        DeleteFileW((root + L"injector_dir.txt").c_str());
        DeleteFileW((root + L"vape_injector_diag.txt").c_str());
    }
}
}

// Extracts the embedded product DLL (resource 422, RCDATA) to
// <exe>\.vapeclient\Vape-v4.21Recovery\ so it can be injected via LoadLibraryW.
// The injected DLL derives the client directory from this fixed module path,
// keeping every artifact inside the .vapeclient tree (no %TEMP% writes).
// If a Vape-v4.21Native.dll sits next to the exe, it is used directly and the
// embedded copy is not extracted.
bool ControllerModel::materializeEmbeddedDll(std::uint32_t processId,
        std::wstring& output) {
    // Prefer an external DLL placed beside the exe (e.g. the published bundle).
    const std::wstring external = executableDirectory() + L"\\Vape-v4.21Native.dll";
    if (GetFileAttributesW(external.c_str()) != INVALID_FILE_ATTRIBUTES) {
        output = external;
        return true;
    }
    const HRSRC resource = FindResourceW(nullptr,
        MAKEINTRESOURCEW(422), MAKEINTRESOURCEW(10));
    if (resource == nullptr) return false;
    const DWORD size = SizeofResource(nullptr, resource);
    const HGLOBAL loaded = LoadResource(nullptr, resource);
    const auto* bytes = loaded == nullptr ? nullptr
        : static_cast<const unsigned char*>(LockResource(loaded));
    if (bytes == nullptr || size < 4) return false;

    ensureClientDirectoryHidden();
    const std::wstring clientRoot = executableDirectory() + L"\\.vapeclient";
    wchar_t directory[MAX_PATH]{};
    _snwprintf_s(directory, std::size(directory), _TRUNCATE,
        L"%ls\\Vape-v4.21Recovery", clientRoot.c_str());
    if (!CreateDirectoryW(directory, nullptr)
            && GetLastError() != ERROR_ALREADY_EXISTS) {
        return false;
    }
    // Sweep stale per-pid artifacts from previous injections so the recovery
    // directory only ever holds the current session's files.
    sweepDirectory(directory);
    wchar_t target[MAX_PATH]{};
    _snwprintf_s(target, std::size(target), _TRUNCATE,
        L"%ls\\Vape-v4.21Native-%lu.dll", directory,
        static_cast<unsigned long>(processId));

    HANDLE file = CreateFileW(target, GENERIC_WRITE,
        FILE_SHARE_READ | FILE_SHARE_DELETE, nullptr, CREATE_ALWAYS,
        FILE_ATTRIBUTE_NORMAL, nullptr);
    if (file == INVALID_HANDLE_VALUE) {
        // The same-pid artifact may still be mapped by a live process from a
        // previous injection (same content). Reuse it instead of failing.
        const DWORD createError = GetLastError();
        if (createError == ERROR_SHARING_VIOLATION ||
                createError == ERROR_ACCESS_DENIED) {
            WIN32_FILE_ATTRIBUTE_DATA existing{};
            if (GetFileAttributesExW(target, GetFileExInfoStandard, &existing) &&
                    existing.nFileSizeLow == size &&
                    existing.nFileSizeHigh == 0) {
                output = target;
                return true;
            }
        }
        return false;
    }

    DWORD offset = 0;
    bool ok = true;
    while (offset < size) {
        DWORD written = 0;
        const DWORD remaining = size - offset;
        if (!WriteFile(file, bytes + offset, remaining, &written, nullptr)
                || written == 0) {
            ok = false;
            break;
        }
        offset += written;
    }
    CloseHandle(file);
    if (!ok) {
        DeleteFileW(target);
        return false;
    }
    output = target;
    return true;
}

ControllerModel::ControllerModel() {
    serviceHttpBase_ = onlineBaseUrl();
    const std::wstring setting = cacheDirectory() + L"cache.preference";
    std::wifstream input(setting);
    int enabled = 0;
    if (input >> enabled) {
        cachePreference_ = enabled != 0;
    }
    // Skip the login page: authenticate against the local loopback Service
    // with an anonymous username and go straight to Minecraft selection.
    autoLoginToService();
    initializeAutoInject();
}

void ControllerModel::initializeAutoInject() {
    const std::wstring setting = cacheDirectory() + L"auto.preference";
    std::wifstream input(setting);
    int enabled = 1;
    if (input >> enabled) {
        autoInjectEnabled_ = enabled != 0;
    }
    autoInjectArmed_ = false;
    autoInjectDone_ = false;
    autoInjectTarget_ = 0;
    autoInjectAttempts_ = 0;
    autoInjectIgnoreIds_.clear();
    if (!autoInjectEnabled_) {
        appendAutoInjectLog(L"auto inject disabled by preference");
        return;
    }
    // Always arm while the switch is on. Java processes that already exist are
    // frozen and never injected behind the user's back, but the watchdog keeps
    // running so every game started afterwards is injected automatically.
    const std::size_t javaProcesses = countJavaProcesses();
    const bool minecraftRunning = hasRunningMinecraftGame();
    {
        std::wostringstream line;
        line << L"init java=" << javaProcesses << L" minecraftWindow="
             << (minecraftRunning ? 1 : 0);
        appendAutoInjectLog(line.str());
    }
    autoInjectIgnoreIds_ = listJavaProcessIds();
    autoInjectArmed_ = true;
    autoInjectArmedAt_ = std::chrono::steady_clock::now();
    setStatus(ls(Ls::AutoInjectWaiting));
    appendAutoInjectLog(L"armed: waiting for a newly started game");
}

std::vector<std::uint32_t> ControllerModel::listJavaProcessIds() {
    std::vector<std::uint32_t> pids;
    HANDLE snapshot = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
    if (snapshot == INVALID_HANDLE_VALUE) {
        return pids;
    }
    PROCESSENTRY32W entry{sizeof(entry)};
    if (Process32FirstW(snapshot, &entry)) {
        do {
            if (_wcsicmp(entry.szExeFile, L"java.exe") == 0 ||
                    _wcsicmp(entry.szExeFile, L"javaw.exe") == 0) {
                pids.push_back(entry.th32ProcessID);
            }
        } while (Process32NextW(snapshot, &entry));
    }
    CloseHandle(snapshot);
    return pids;
}

std::size_t ControllerModel::countJavaProcesses() {
    return listJavaProcessIds().size();
}

bool ControllerModel::autoInjectEnabled() const { return autoInjectEnabled_; }

bool ControllerModel::autoInjectArmed() const { return autoInjectArmed_; }

void ControllerModel::setAutoInjectEnabled(bool enabled) {
    if (autoInjectEnabled_ == enabled) {
        return;
    }
    autoInjectEnabled_ = enabled;
    persistAutoInjectPreference(enabled);
    appendAutoInjectLog(enabled ? L"preference enabled" : L"preference disabled");
    if (!enabled) {
        autoInjectArmed_ = false;
        autoInjectDone_ = false;
        autoInjectTarget_ = 0;
        autoInjectAttempts_ = 0;
        autoInjectIgnoreIds_.clear();
        return;
    }
    // Enabling at runtime always arms the watchdog. Sessions that are already
    // alive are frozen: they are not injected automatically, but every game
    // started afterwards is.
    autoInjectIgnoreIds_ = listJavaProcessIds();
    autoInjectArmed_ = true;
    autoInjectDone_ = false;
    autoInjectTarget_ = 0;
    autoInjectAttempts_ = 0;
    autoInjectArmedAt_ = std::chrono::steady_clock::now();
    setStatus(ls(Ls::AutoInjectWaiting));
    appendAutoInjectLog(L"armed at runtime: waiting for a newly started game");
}

void ControllerModel::persistAutoInjectPreference(bool enabled) {
    const auto directory = cacheDirectory();
    if (CreateDirectoryW(directory.c_str(), nullptr) ||
            GetLastError() == ERROR_ALREADY_EXISTS) {
        const std::wstring clientRoot = executableDirectory() + L"\\.vapeclient";
        const DWORD attributes = GetFileAttributesW(clientRoot.c_str());
        if (attributes != INVALID_FILE_ATTRIBUTES &&
                (attributes & FILE_ATTRIBUTE_HIDDEN) == 0) {
            SetFileAttributesW(clientRoot.c_str(),
                attributes | FILE_ATTRIBUTE_HIDDEN);
        }
    }
    std::wofstream output(directory + L"auto.preference", std::ios::trunc);
    output << (enabled ? 1 : 0);
}

void ControllerModel::tickAutoInject() {
    if (!autoInjectArmed_ || autoInjectDone_) {
        return;
    }
    const ControllerPage currentPage = page();
    if (currentPage != ControllerPage::MinecraftSelection) {
        return;
    }
    const auto now = std::chrono::steady_clock::now();
    if (lastAutoInjectTick_.time_since_epoch().count() != 0 &&
            now - lastAutoInjectTick_ < std::chrono::seconds(1)) {
        return;
    }
    lastAutoInjectTick_ = now;

    std::uint32_t target = 0;
    std::wstring targetTitle;
    for (const auto& process : minecraftProcesses()) {
        if (process.alreadyInjected) {
            continue;
        }
        // Skip sessions that were already alive when the watchdog armed.
        if (std::find(autoInjectIgnoreIds_.begin(), autoInjectIgnoreIds_.end(),
                process.pid) != autoInjectIgnoreIds_.end()) {
            continue;
        }
        target = process.pid;
        targetTitle = process.title;
        break;
    }
    if (target == 0) {
        resetAutoInjectTarget();
        return;
    }
    if (autoInjectTarget_ != target) {
        autoInjectTarget_ = target;
        autoInjectAttempts_ = 0;
        autoInjectArmedAt_ = now;
        std::wostringstream line;
        line << L"target pid=" << target << L" title=" << targetTitle;
        appendAutoInjectLog(line.str());
        return;
    }
    // Give the JVM a moment to finish booting: injecting the instant the window
    // exists reliably hits "jvm.dll never appeared" (code 2). The retry loop
    // covers the cases where one second is still too early.
    if (autoInjectAttempts_ == 0 &&
            now - autoInjectArmedAt_ < std::chrono::seconds(1)) {
        return;
    }
    if (autoInjectAttempts_ > 0 &&
            now - lastAutoInjectAttempt_ < std::chrono::seconds(3)) {
        return;
    }
    if (autoInjectAttempts_ >= 12) {
        // Keep the watchdog alive: remember this session as failed so the next
        // game started while the switch is on still gets injected.
        if (std::find(autoInjectIgnoreIds_.begin(), autoInjectIgnoreIds_.end(),
                autoInjectTarget_) == autoInjectIgnoreIds_.end()) {
            autoInjectIgnoreIds_.push_back(autoInjectTarget_);
        }
        {
            std::wostringstream line;
            line << L"giving up on pid=" << autoInjectTarget_
                 << L" after 12 attempts";
            appendAutoInjectLog(line.str());
        }
        setStatus(ls(Ls::AutoInjectFailed));
        resetAutoInjectTarget();
        return;
    }
    lastAutoInjectAttempt_ = now;
    ++autoInjectAttempts_;
    {
        std::wostringstream line;
        line << L"attempt " << autoInjectAttempts_ << L" pid=" << autoInjectTarget_;
        appendAutoInjectLog(line.str());
    }
    if (startInjection(autoInjectTarget_, false)) {
        // Injection has started: stop the watchdog so later game sessions are
        // not touched. A manual selection is still available if needed.
        resetAutoInjectTarget();
        autoInjectArmed_ = false;
        autoInjectDone_ = true;
        autoInjectIgnoreIds_.clear();
        appendAutoInjectLog(L"injection started; auto inject stopped");
    } else {
        {
            std::lock_guard lock(mutex_);
            std::wostringstream line;
            line << L"attempt failed: " << autoInjectError_;
            appendAutoInjectLog(line.str());
        }
        if (autoInjectAttempts_ >= 12) {
            if (std::find(autoInjectIgnoreIds_.begin(), autoInjectIgnoreIds_.end(),
                    autoInjectTarget_) == autoInjectIgnoreIds_.end()) {
                autoInjectIgnoreIds_.push_back(autoInjectTarget_);
            }
            {
                std::wostringstream line;
                line << L"giving up on pid=" << autoInjectTarget_
                     << L" after 12 attempts";
                appendAutoInjectLog(line.str());
            }
            setStatus(ls(Ls::AutoInjectFailed));
            resetAutoInjectTarget();
        }
    }
}

void ControllerModel::resetAutoInjectTarget() {
    autoInjectTarget_ = 0;
    autoInjectAttempts_ = 0;
    autoInjectArmedAt_ = std::chrono::steady_clock::now();
}

ControllerModel::~ControllerModel() {
    if (injectionThread_.joinable()) {
        injectionThread_.join();
    }
}

bool ControllerModel::injectionRunning() const {
    return injectionRunning_.load();
}

bool ControllerModel::startInjection(std::uint32_t processId, bool reportFailure) {
    bool idle = false;
    if (!injectionRunning_.compare_exchange_strong(idle, true)) {
        return false;
    }
    if (injectionThread_.joinable()) {
        injectionThread_.join();
    }
    injectionSucceeded_.store(false);
    if (reportFailure) {
        // Manual injection: show the loading page immediately so the click has
        // visible feedback while the worker waits for the target process.
        std::lock_guard lock(mutex_);
        loadingStage_ = 0;
        loadingStarted_ = std::chrono::steady_clock::now();
        stageStarted_ = loadingStarted_;
        page_ = ControllerPage::Loading;
        status_.clear();
    }
    injectionThread_ = std::thread([this, processId, reportFailure]() {
        const bool injected = injectMinecraft(processId, reportFailure);
        injectionSucceeded_.store(injected);
        injectionRunning_.store(false);
    });
    return true;
}

ControllerPage ControllerModel::page() const {
    std::lock_guard lock(mutex_);
    return page_;
}

void ControllerModel::setPage(ControllerPage value) {
    std::lock_guard lock(mutex_);
    page_ = value;
}

std::wstring ControllerModel::status() const {
    std::lock_guard lock(mutex_);
    return status_;
}

std::wstring ControllerModel::errorDetail() const {
    std::lock_guard lock(mutex_);
    return errorDetail_;
}

void ControllerModel::setStatus(std::wstring value) {
    std::lock_guard lock(mutex_);
    status_ = std::move(value);
}

std::wstring& ControllerModel::username() { return username_; }
std::wstring& ControllerModel::password() { return password_; }

std::vector<MinecraftProcess> ControllerModel::minecraftProcesses() const {
    std::lock_guard lock(mutex_);
    return minecraftProcesses_;
}

void ControllerModel::refreshMinecraftProcesses() {
    std::vector<MinecraftProcess> found;
    std::vector<std::uint32_t> injected;
    {
        std::lock_guard lock(mutex_);
        injected = injectedProcesses_;
    }
    HANDLE snapshot = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
    if (snapshot != INVALID_HANDLE_VALUE) {
        PROCESSENTRY32W entry{sizeof(entry)};
        if (Process32FirstW(snapshot, &entry)) {
            do {
                if (_wcsicmp(entry.szExeFile, L"java.exe") != 0 &&
                        _wcsicmp(entry.szExeFile, L"javaw.exe") != 0) {
                    continue;
                }
                const bool wasInjected = std::find(injected.begin(), injected.end(),
                    entry.th32ProcessID) != injected.end();
                found.push_back({entry.th32ProcessID, {}, wasInjected});
            } while (Process32NextW(snapshot, &entry));
        }
        CloseHandle(snapshot);
    }

    captureWindowTitles(found);
    found.erase(std::remove_if(found.begin(), found.end(),
        [](const MinecraftProcess& process) { return process.title.empty(); }), found.end());
    std::sort(found.begin(), found.end(),
        [](const MinecraftProcess& left, const MinecraftProcess& right) {
            return left.pid < right.pid;
        });

    std::lock_guard lock(mutex_);
    minecraftProcesses_ = std::move(found);
    lastMinecraftRefresh_ = std::chrono::steady_clock::now();
}

bool ControllerModel::injectMinecraft(std::uint32_t processId, bool reportFailure) {
    autoInjectError_.clear();
    std::string token;
    std::string serviceHttpBase;
    {
        std::lock_guard lock(mutex_);
        token = accessToken_;
        serviceHttpBase = utf8(serviceHttpBase_);
    }
    // The local Service may have been unavailable when the Loader started
    // (Minecraft not running yet). Re-authenticate lazily so the token handed
    // to the injected DLL is always valid.
    if (token.empty() && !loginToService()) {
        autoInjectError_ = L"service login failed";
        if (reportFailure) {
            setStatus(ls(Ls::CannotLoginStartMinecraft));
            setPage(ControllerPage::Error);
        }
        return false;
    }
    {
        std::lock_guard lock(mutex_);
        token = accessToken_;
        if (token.empty()) {
            autoInjectError_ = L"service token is empty";
            if (reportFailure) {
                setStatus(ls(Ls::CannotLoginStartMinecraft));
                setPage(ControllerPage::Error);
            }
            return false;
        }
    }
    if (!service_.start(token, cachePreference_, true)) {
        autoInjectError_ = L"control socket start failed";
        if (reportFailure) {
            setStatus(ls(Ls::CannotCreateControlSocket));
            setPage(ControllerPage::Error);
        }
        return false;
    }
    std::wstring error;
    // Always use the embedded DLL resource; never load an external copy.
    std::wstring dllPath;
    if (!materializeEmbeddedDll(processId, dllPath)) {
        autoInjectError_ = L"extract native dll failed";
        if (reportFailure) {
            setStatus(ls(Ls::CannotExtractNativeDll));
            setPage(ControllerPage::Error);
        }
        return false;
    }
    // The injected DLL derives the client directory from its own module path
    // (<exe>\.vapeclient\Vape-v4.21Recovery); ensure the hidden folder exists.
    ensureClientDirectoryHidden();
    if (!InjectionCoordinator::injectProductDll(processId, dllPath, service_.port(),
            serviceHttpBase, error)) {
        service_.stop();
        autoInjectError_ = error.empty() ? std::wstring(ls(Ls::InjectNativeDllFailed))
                                         : error;
        if (reportFailure) {
            setStatus(error.empty() ? ls(Ls::InjectNativeDllFailed) : error);
            setPage(ControllerPage::Error);
        }
        return false;
    }
    {
        std::lock_guard lock(mutex_);
        if (std::find(injectedProcesses_.begin(), injectedProcesses_.end(), processId) ==
            injectedProcesses_.end()) {
            injectedProcesses_.push_back(processId);
        }
        if (!accessToken_.empty()) {
            SecureZeroMemory(accessToken_.data(), accessToken_.size());
            accessToken_.clear();
        }
        loadingStage_ = service_.stage();
        loadingStarted_ = std::chrono::steady_clock::now();
        stageStarted_ = loadingStarted_;
        page_ = ControllerPage::Loading;
        status_.clear();
    }
    if (!token.empty()) SecureZeroMemory(token.data(), token.size());
    return true;
}

std::string ControllerModel::httpPost(const wchar_t* host, const wchar_t* path,
                                      const std::string& body) {
    HINTERNET session = WinHttpOpen(L"Vape4/Launcher",
        WINHTTP_ACCESS_TYPE_AUTOMATIC_PROXY, WINHTTP_NO_PROXY_NAME,
        WINHTTP_NO_PROXY_BYPASS, 0);
    if (!session) return {};
    WinHttpSetTimeouts(session, 5000, 5000, 5000, 5000);
    HINTERNET connection = WinHttpConnect(session, host, INTERNET_DEFAULT_HTTPS_PORT, 0);
    HINTERNET request = connection ? WinHttpOpenRequest(connection, L"POST", path,
        nullptr, WINHTTP_NO_REFERER, WINHTTP_DEFAULT_ACCEPT_TYPES, WINHTTP_FLAG_SECURE) : nullptr;
    std::string response;
    const wchar_t* headers = L"Content-Type: application/x-www-form-urlencoded";
    if (request && WinHttpSendRequest(request, headers, static_cast<DWORD>(-1L),
            const_cast<char*>(body.data()), static_cast<DWORD>(body.size()),
            static_cast<DWORD>(body.size()), 0) && WinHttpReceiveResponse(request, nullptr)) {
        for (;;) {
            DWORD available = 0;
            if (!WinHttpQueryDataAvailable(request, &available) || available == 0) break;
            const auto offset = response.size();
            response.resize(offset + available);
            DWORD read = 0;
            if (!WinHttpReadData(request, response.data() + offset, available, &read)) break;
            response.resize(offset + read);
        }
    }
    if (request) WinHttpCloseHandle(request);
    if (connection) WinHttpCloseHandle(connection);
    WinHttpCloseHandle(session);
    return response;
}

std::string ControllerModel::httpPostJson(const std::wstring& baseUrl,
                                          const wchar_t* path,
                                          const std::string& body) {
    URL_COMPONENTS components{};
    components.dwStructSize = sizeof(components);
    components.dwSchemeLength = static_cast<DWORD>(-1);
    components.dwHostNameLength = static_cast<DWORD>(-1);
    components.dwUrlPathLength = static_cast<DWORD>(-1);
    if (!WinHttpCrackUrl(baseUrl.c_str(), 0, 0, &components) ||
            components.dwHostNameLength == 0) {
        return {};
    }
    const std::wstring host(components.lpszHostName, components.dwHostNameLength);
    HINTERNET session = WinHttpOpen(L"Vape4/Loader",
        WINHTTP_ACCESS_TYPE_NO_PROXY, WINHTTP_NO_PROXY_NAME,
        WINHTTP_NO_PROXY_BYPASS, 0);
    if (!session) return {};
    WinHttpSetTimeouts(session, 5000, 5000, 5000, 5000);
    HINTERNET connection = WinHttpConnect(session, host.c_str(), components.nPort, 0);
    const DWORD flags = components.nScheme == INTERNET_SCHEME_HTTPS
        ? WINHTTP_FLAG_SECURE : 0;
    HINTERNET request = connection ? WinHttpOpenRequest(connection, L"POST", path,
        nullptr, WINHTTP_NO_REFERER, WINHTTP_DEFAULT_ACCEPT_TYPES, flags) : nullptr;
    std::string response;
    const wchar_t* headers = L"Content-Type: application/json; charset=utf-8";
    if (request && WinHttpSendRequest(request, headers, static_cast<DWORD>(-1L),
            const_cast<char*>(body.data()), static_cast<DWORD>(body.size()),
            static_cast<DWORD>(body.size()), 0) && WinHttpReceiveResponse(request, nullptr)) {
        DWORD statusCode = 0;
        DWORD statusSize = sizeof(statusCode);
        if (WinHttpQueryHeaders(request, WINHTTP_QUERY_STATUS_CODE |
                WINHTTP_QUERY_FLAG_NUMBER, WINHTTP_HEADER_NAME_BY_INDEX,
                &statusCode, &statusSize, WINHTTP_NO_HEADER_INDEX) && statusCode == 200) {
            for (;;) {
                DWORD available = 0;
                if (!WinHttpQueryDataAvailable(request, &available) || available == 0) break;
                const auto offset = response.size();
                response.resize(offset + available);
                DWORD read = 0;
                if (!WinHttpReadData(request, response.data() + offset, available, &read)) break;
                response.resize(offset + read);
            }
        }
    }
    if (request) WinHttpCloseHandle(request);
    if (connection) WinHttpCloseHandle(connection);
    WinHttpCloseHandle(session);
    return response;
}

std::string ControllerModel::jsonString(const std::string& json, const char* key) {
    const std::string needle = std::string("\"") + key + "\"";
    auto position = json.find(needle);
    if (position == std::string::npos) return {};
    position = json.find(':', position + needle.size());
    position = json.find('"', position == std::string::npos ? position : position + 1);
    if (position == std::string::npos) return {};
    const auto end = json.find('"', position + 1);
    return end == std::string::npos ? std::string{} : json.substr(position + 1, end - position - 1);
}

void ControllerModel::beginBrowserAuthentication(void* /*windowHandle*/) {
    // The upstream browser flow POSTed to www.vape.gg/api/v1/app-auth/generate,
    // opened https://www.vape.gg/app-auth/proceed/<challenge> through
    // ShellExecuteW and then polled www.vape.gg/api/v1/app-auth/status. Those
    // remote endpoints are gone, and the flow was unreachable anyway: only the
    // login page could enter ControllerPage::BrowserAuth, which itself only led
    // back to the login page. The button therefore now fails locally and issues
    // no network request at all. The loopback Service login
    // (autoLoginToService / submitCredentialAuthentication) plus
    // ControllerPage::MinecraftSelection is the only supported path.
    setStatus(ls(Ls::CannotOpenBrowserLogin));
    setPage(ControllerPage::Login);
}

void ControllerModel::reopenBrowserAuthentication() {
    // No browser URL exists any more (see beginBrowserAuthentication). Kept as a
    // no-op so the login page's hit targets stay wired to the model.
}

void ControllerModel::cancelBrowserAuthentication() {
    setPage(ControllerPage::Login);
}

std::wstring ControllerModel::cacheDirectory() const {
    const std::wstring directory = executableDirectory() + L"\\.vapeclient\\";
    return directory;
}

void ControllerModel::persistCachePreference(bool enabled) {
    cachePreference_ = enabled;
    const auto directory = cacheDirectory();
    if (CreateDirectoryW(directory.c_str(), nullptr) ||
            GetLastError() == ERROR_ALREADY_EXISTS) {
        const std::wstring clientRoot = executableDirectory() + L"\\.vapeclient";
        const DWORD attributes = GetFileAttributesW(clientRoot.c_str());
        if (attributes != INVALID_FILE_ATTRIBUTES &&
                (attributes & FILE_ATTRIBUTE_HIDDEN) == 0) {
            SetFileAttributesW(clientRoot.c_str(),
                attributes | FILE_ATTRIBUTE_HIDDEN);
        }
    }
    std::wofstream output(directory + L"cache.preference", std::ios::trunc);
    output << (enabled ? 1 : 0);
}

bool ControllerModel::cachePreference() const { return cachePreference_; }

void ControllerModel::tick() {
    const auto now = std::chrono::steady_clock::now();
    const auto currentPage = page();
    // The auto-inject watchdog only runs while the main selection page is
    // visible: as soon as injection starts (loading/error page) polling stops,
    // whether the last attempt succeeded or failed.
    if (currentPage == ControllerPage::MinecraftSelection) {
        bool refresh = false;
        {
            std::lock_guard lock(mutex_);
            refresh = lastMinecraftRefresh_.time_since_epoch().count() == 0 ||
                now - lastMinecraftRefresh_ >= std::chrono::seconds(1);
        }
        if (refresh) refreshMinecraftProcesses();
        tickAutoInject();
        return;
    }
    if (currentPage != ControllerPage::Loading) return;

    const int serviceStage = service_.stage();
    {
        std::lock_guard lock(mutex_);
        if (serviceStage != loadingStage_) {
            loadingStage_ = serviceStage;
            stageStarted_ = now;
        }
    }
    if (service_.completed()) {
        setPage(ControllerPage::LoadingComplete);
    } else if (service_.failed()) {
        const std::string detail = service_.error();
        std::wstring raw = detail.empty()
            ? std::wstring()
            : std::wstring(detail.begin(), detail.end());
        std::wstring message = raw.empty() ? std::wstring(ls(Ls::NativeConnectionClosed)) : raw;
        const Ls friendly = friendlyNativeFailure(detail);
        if (friendly != Ls::Count) {
            // Raw technical line first (kept for support reports), then the
            // localized explanation below it.
            message = raw.empty()
                ? std::wstring(ls(friendly))
                : raw + L"\r\n" + ls(friendly);
        }
        {
            std::lock_guard lock(mutex_);
            errorDetail_ = std::move(raw);
        }
        setStatus(std::move(message));
        setPage(ControllerPage::Error);
    } else if (loadingElapsedSeconds() >= 90.0) {
        setStatus(ls(Ls::NativeLoadTimeout));
        setPage(ControllerPage::Error);
    }
}

int ControllerModel::loadingStage() const {
    std::lock_guard lock(mutex_);
    return loadingStage_;
}

double ControllerModel::loadingElapsedSeconds() const {
    std::lock_guard lock(mutex_);
    if (loadingStarted_.time_since_epoch().count() == 0) return 0.0;
    return std::chrono::duration<double>(std::chrono::steady_clock::now() - loadingStarted_).count();
}

double ControllerModel::stageElapsedSeconds() const {
    std::lock_guard lock(mutex_);
    if (stageStarted_.time_since_epoch().count() == 0) return 0.0;
    return std::chrono::duration<double>(std::chrono::steady_clock::now() - stageStarted_).count();
}

void ControllerModel::submitCredentialAuthentication() {
    const std::wstring usernameValue = username_;
    const std::string usernameUtf8 = utf8(usernameValue);
    if (usernameUtf8.empty()) {
        setStatus(ls(Ls::EnterUsername));
        return;
    }
    const std::string response = httpPostJson(serviceHttpBase_, L"/loader/login",
        "{\"username\":\"" + jsonEscape(usernameUtf8) + "\"}");
    const std::string token = jsonString(response, "token");
    if (token.empty()) {
        setStatus(ls(Ls::CannotLoginLocalService));
        return;
    }
    {
        std::lock_guard lock(mutex_);
        accessToken_ = token;
        status_.clear();
    }
    refreshMinecraftProcesses();
    setPage(ControllerPage::MinecraftSelection);
}

bool ControllerModel::loginToService() {
    // Generate a local access token directly. The in-game Service used to be
    // the token source, but it only exists after injection (which needs the
    // token first), so depending on it created a chicken-and-egg deadlock.
    // The bootstrap only requires a non-empty token; online features inside
    // the game register their own account with the loopback Service.
    std::string token;
    token.reserve(48);
    std::mt19937 generator(static_cast<unsigned int>(
        std::chrono::steady_clock::now().time_since_epoch().count())
        ^ static_cast<unsigned int>(GetCurrentProcessId()));
    const char alphabet[] = "0123456789abcdef";
    for (int i = 0; i < 48; ++i) {
        token.push_back(alphabet[generator() % 16]);
    }
    {
        std::lock_guard lock(mutex_);
        accessToken_ = token;
        status_.clear();
    }
    return true;
}

void ControllerModel::autoLoginToService() {
    if (loginToService()) {
        refreshMinecraftProcesses();
    }
    setPage(ControllerPage::MinecraftSelection);
}
