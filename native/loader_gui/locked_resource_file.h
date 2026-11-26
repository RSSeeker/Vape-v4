#pragma once

#include <windows.h>

#include <array>
#include <cstddef>
#include <cstring>
#include <string>

namespace embedded_resource_integrity {

// Keep a read handle that denies write/delete sharing while the verified DLL is
// passed to the injection paths. This closes the usual compare-then-replace
// window, including replacement between the JAR extraction and DLL reads.
class LockedResourceFile {
public:
    LockedResourceFile() = default;
    LockedResourceFile(const LockedResourceFile&) = delete;
    LockedResourceFile& operator=(const LockedResourceFile&) = delete;
    ~LockedResourceFile() { close(); }

    bool open(const std::wstring& path, const unsigned char* expected, std::size_t size) {
        close();
        if (expected == nullptr || size == 0) return false;
        handle_ = CreateFileW(path.c_str(), GENERIC_READ, FILE_SHARE_READ, nullptr,
                OPEN_EXISTING, FILE_FLAG_OPEN_REPARSE_POINT, nullptr);
        if (handle_ == INVALID_HANDLE_VALUE) return false;
        BY_HANDLE_FILE_INFORMATION info{};
        if (!GetFileInformationByHandle(handle_, &info)
                || (info.dwFileAttributes & (FILE_ATTRIBUTE_REPARSE_POINT | FILE_ATTRIBUTE_DIRECTORY)) != 0
                || ((static_cast<unsigned long long>(info.nFileSizeHigh) << 32) | info.nFileSizeLow) != size) {
            close();
            return false;
        }
        std::array<unsigned char, 16384> buffer{};
        std::size_t offset = 0;
        while (offset < size) {
            const DWORD count = static_cast<DWORD>((size - offset < buffer.size())
                    ? size - offset : buffer.size());
            DWORD read = 0;
            if (!ReadFile(handle_, buffer.data(), count, &read, nullptr) || read != count
                    || std::memcmp(buffer.data(), expected + offset, count) != 0) {
                close();
                return false;
            }
            offset += count;
        }
        return true;
    }

    void close() {
        if (handle_ != INVALID_HANDLE_VALUE) {
            CloseHandle(handle_);
            handle_ = INVALID_HANDLE_VALUE;
        }
    }

private:
    HANDLE handle_ = INVALID_HANDLE_VALUE;
};

} // namespace embedded_resource_integrity
