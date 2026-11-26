#include "safe_file_materialization.h"

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
bool hasBytes(const wchar_t* path, const unsigned char* data, DWORD size) {
    HANDLE file = CreateFileW(path, GENERIC_READ, FILE_SHARE_READ, nullptr, OPEN_EXISTING,
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
    wchar_t temp[MAX_PATH]{};
    if (GetTempPathW(MAX_PATH, temp) == 0) return 1;
    const std::wstring directory = std::wstring(temp) + L"vape421-safe-test-" +
            std::to_wstring(GetCurrentProcessId()) + L"-" + std::to_wstring(GetTickCount64());
    if (!CreateDirectoryW(directory.c_str(), nullptr)) return 1;
    const std::wstring base = directory + L"\\product";
    const std::wstring old = base + L".jar";
    const std::wstring sentinel = directory + L"\\sentinel";
    const unsigned char stale[] = {1, 2, 3, 4};
    const unsigned char payload[] = {4, 3, 2, 1};
    if (!putNew(old, stale, sizeof(stale)) || !putNew(sentinel, stale, sizeof(stale))) return 1;
    wchar_t first[MAX_PATH]{};
    wchar_t second[MAX_PATH]{};
    check(vape421_write_unique_file(base.c_str(), L".jar", payload, sizeof(payload), first,
            std::size(first)) != 0, "write alternative without replacing old JAR");
    check(std::wstring(first) != old && hasBytes(first, payload, sizeof(payload)), "new payload has expected bytes");
    check(hasBytes(old.c_str(), stale, sizeof(stale)), "existing JAR unchanged");
    check(hasBytes(sentinel.c_str(), stale, sizeof(stale)), "unrelated file unchanged");
    check(vape421_write_unique_file(base.c_str(), L".jar", payload, sizeof(payload), second,
            std::size(second)) != 0 && std::wstring(second) != first, "each write uses an unused path");
    wchar_t tooSmall[2]{};
    check(!vape421_write_unique_file(base.c_str(), L".jar", payload, sizeof(payload), tooSmall,
            std::size(tooSmall)), "truncated path refused");
    if (first[0]) DeleteFileW(first);
    if (second[0]) DeleteFileW(second);
    DeleteFileW(old.c_str());
    DeleteFileW(sentinel.c_str());
    RemoveDirectoryW(directory.c_str());
    std::printf("%s (%d failure(s))\n", failures ? "FAIL" : "PASS", failures);
    return failures ? 1 : 0;
}
