#include "locked_resource_file.h"

#include <cstdio>
#include <cstring>

namespace {
int failures = 0;
void check(bool ok, const char* name) {
    std::printf("%s %s\n", ok ? "ok  " : "FAIL", name);
    if (!ok) ++failures;
}
}

int main() {
    wchar_t directory[MAX_PATH]{};
    wchar_t path[MAX_PATH]{};
    if (GetTempPathW(MAX_PATH, directory) == 0 || GetTempFileNameW(directory, L"v42", 0, path) == 0) {
        std::puts("FAIL could not create test file");
        return 1;
    }
    const unsigned char expected[] = {0x4d, 0x5a, 0x00, 0xff, 0x25, 0x10};
    HANDLE file = CreateFileW(path, GENERIC_WRITE, 0, nullptr, CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, nullptr);
    DWORD written = 0;
    check(file != INVALID_HANDLE_VALUE, "open fixture");
    if (file == INVALID_HANDLE_VALUE) return 1;
    check(WriteFile(file, expected, sizeof(expected), &written, nullptr) && written == sizeof(expected), "write fixture");
    CloseHandle(file);

    embedded_resource_integrity::LockedResourceFile guard;
    check(guard.open(path, expected, sizeof(expected)), "matching file accepted");
    HANDLE writer = CreateFileW(path, GENERIC_WRITE, FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE,
            nullptr, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, nullptr);
    check(writer == INVALID_HANDLE_VALUE, "write denied while verified handle is held");
    if (writer != INVALID_HANDLE_VALUE) CloseHandle(writer);
    check(!DeleteFileW(path), "delete denied while verified handle is held");
    HANDLE reader = CreateFileW(path, GENERIC_READ, FILE_SHARE_READ, nullptr,
            OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, nullptr);
    check(reader != INVALID_HANDLE_VALUE, "other read handles are allowed");
    if (reader != INVALID_HANDLE_VALUE) CloseHandle(reader);
    guard.close();

    unsigned char wrong[sizeof(expected)]{};
    std::memcpy(wrong, expected, sizeof(expected));
    wrong[2] ^= 1;
    check(!guard.open(path, wrong, sizeof(wrong)), "same-size mismatch rejected");
    check(!guard.open(path, expected, sizeof(expected) - 1), "wrong size rejected");
    check(!guard.open(path, nullptr, sizeof(expected)), "null resource rejected");
    check(DeleteFileW(path) != 0, "fixture can be deleted after closing");
    check(!guard.open(path, expected, sizeof(expected)), "missing file rejected");
    std::printf("%s (%d failure(s))\n", failures ? "FAIL" : "PASS", failures);
    return failures ? 1 : 0;
}
