#include "embedded_dll_materializer.h"
#include "client_data_path.h"

#include <windows.h>
#include <cstdio>
#include <cstring>
#include <iterator>
#include <string>

namespace {
int failures = 0;
void check(bool ok, const char* name) {
    std::printf("%s %s\n", ok ? "ok  " : "FAIL", name);
    if (!ok) ++failures;
}
bool putNew(const std::wstring& path, const unsigned char* data, DWORD size) {
    HANDLE file = CreateFileW(path.c_str(), GENERIC_WRITE, 0, nullptr, CREATE_NEW,
            FILE_ATTRIBUTE_NORMAL, nullptr);
    if (file == INVALID_HANDLE_VALUE) return false;
    DWORD written = 0;
    const bool ok = WriteFile(file, data, size, &written, nullptr) && written == size;
    CloseHandle(file);
    return ok;
}
bool hasBytes(const std::wstring& path, const unsigned char* data, DWORD size) {
    HANDLE file = CreateFileW(path.c_str(), GENERIC_READ, FILE_SHARE_READ, nullptr, OPEN_EXISTING,
            FILE_ATTRIBUTE_NORMAL, nullptr);
    if (file == INVALID_HANDLE_VALUE) return false;
    unsigned char buffer[16]{};
    DWORD read = 0;
    const bool ok = size <= sizeof(buffer) && ReadFile(file, buffer, size, &read, nullptr)
            && read == size && std::memcmp(buffer, data, size) == 0;
    CloseHandle(file);
    return ok;
}
}

int main() {
    wchar_t exe[MAX_PATH]{};
    DWORD length = GetModuleFileNameW(nullptr, exe, MAX_PATH);
    if (length == 0 || length >= MAX_PATH) return 1;
    std::wstring dir(exe, length);
    const auto slash = dir.find_last_of(L"\\/");
    if (slash == std::wstring::npos) return 1;
    dir.resize(slash);
    wchar_t rootBuffer[MAX_PATH]{};
    if (!soulbound_client_directory(dir.c_str(), rootBuffer, std::size(rootBuffer))) return 1;
    const std::wstring root(rootBuffer);
    const std::wstring recovery = root + L"\\SoulboundRecovery";
    CreateDirectoryW(root.c_str(), nullptr);
    CreateDirectoryW(recovery.c_str(), nullptr);
    const unsigned long pid = GetCurrentProcessId();
    const std::wstring external = dir + L"\\SoulboundNative.dll";
    const std::wstring stale = recovery + L"\\SoulboundNative-" + std::to_wstring(pid) + L".dll";
    const std::wstring sentinel = recovery + L"\\materializer-sentinel-" + std::to_wstring(pid);
    const unsigned char wrong[] = {0x4d, 0x5a, 0x00, 0x11};
    const unsigned char expected[] = {0x4d, 0x5a, 0x00, 0x10};
    // Never overwrite fixtures someone else may own.
    const bool madeExternal = putNew(external, wrong, sizeof(wrong));
    const bool madeStale = putNew(stale, wrong, sizeof(wrong));
    const bool madeSentinel = putNew(sentinel, wrong, sizeof(wrong));
    if (!madeExternal || !madeStale || !madeSentinel) {
        std::puts("FAIL could not safely create isolated fixtures");
        if (madeExternal) DeleteFileW(external.c_str());
        if (madeStale) DeleteFileW(stale.c_str());
        if (madeSentinel) DeleteFileW(sentinel.c_str());
        return 1;
    }
    wchar_t output[MAX_PATH]{};
    check(vape421_materialize_dll(pid, output, std::size(output)) != 0, "materialize embedded resource");
    check(std::wstring(output) != external && std::wstring(output) != stale, "reject same-size foreign DLLs");
    check(hasBytes(output, expected, sizeof(expected)), "materialized bytes equal resource");
    check(hasBytes(external, wrong, sizeof(wrong)), "adjacent file was not overwritten");
    check(hasBytes(stale, wrong, sizeof(wrong)), "stale file was not overwritten");
    check(hasBytes(sentinel, wrong, sizeof(wrong)), "recovery contents were not swept");
    void* guard = vape421_lock_dll(output);
    check(guard != nullptr, "materialized resource can be locked");
    void* foreign = vape421_lock_dll(external.c_str());
    check(foreign == nullptr, "foreign file fails locked verification");
    vape421_unlock_dll(foreign);
    check(!DeleteFileW(output), "locked DLL cannot be deleted");
    vape421_unlock_dll(guard);
    if (output[0] != L'\0' && std::wstring(output) != external && std::wstring(output) != stale) {
        check(DeleteFileW(output) != 0, "test output can be removed after unlock");
    }
    DeleteFileW(external.c_str());
    DeleteFileW(stale.c_str());
    DeleteFileW(sentinel.c_str());
    std::printf("%s (%d failure(s))\n", failures ? "FAIL" : "PASS", failures);
    return failures ? 1 : 0;
}
