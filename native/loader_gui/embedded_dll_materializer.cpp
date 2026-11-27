#include "embedded_dll_materializer.h"
#include "embedded_resource_integrity.h"
#include "locked_resource_file.h"
#include "client_data_path.h"

#include <windows.h>

#include <iterator>
#include <new>
#include <string>

namespace {
bool embeddedDll(const unsigned char*& bytes, DWORD& size) {
    const HRSRC resource = FindResourceW(nullptr, MAKEINTRESOURCEW(422), MAKEINTRESOURCEW(10));
    if (resource == nullptr) return false;
    size = SizeofResource(nullptr, resource);
    const HGLOBAL loaded = LoadResource(nullptr, resource);
    bytes = loaded == nullptr ? nullptr : static_cast<const unsigned char*>(LockResource(loaded));
    return bytes != nullptr && size >= 4;
}

bool executableDirectory(std::wstring& result) {
    wchar_t path[MAX_PATH]{};
    const DWORD length = GetModuleFileNameW(nullptr, path, MAX_PATH);
    if (length == 0 || length >= MAX_PATH) return false;
    result.assign(path, length);
    const auto separator = result.find_last_of(L"\\/");
    if (separator == std::wstring::npos) return false;
    result.resize(separator);
    return true;
}

bool copyResult(const std::wstring& path, wchar_t* output, size_t capacity) {
    return output != nullptr && capacity > path.size()
            && wcsncpy_s(output, capacity, path.c_str(), _TRUNCATE) == 0;
}
}

extern "C" int vape421_materialize_dll(unsigned long processId, wchar_t* output, size_t capacity) {
    if (output == nullptr || capacity == 0) return 0;
    output[0] = L'\0';
    const unsigned char* bytes = nullptr;
    DWORD size = 0;
    std::wstring exeDir;
    if (!embeddedDll(bytes, size) || !executableDirectory(exeDir)) return 0;

    // An optional bundled copy is usable only if it is byte-for-byte identical.
    const std::wstring external = exeDir + L"\\SoulboundNative.dll";
    if (embedded_resource_integrity::matchesFile(external, bytes, size)) {
        return copyResult(external, output, capacity) ? 1 : 0;
    }
    wchar_t rootBuffer[MAX_PATH]{};
    if (!soulbound_client_directory(exeDir.c_str(), rootBuffer, std::size(rootBuffer))) return 0;
    const std::wstring root(rootBuffer);
    if (!CreateDirectoryW(root.c_str(), nullptr) && GetLastError() != ERROR_ALREADY_EXISTS) return 0;
    const DWORD attributes = GetFileAttributesW(root.c_str());
    if (attributes != INVALID_FILE_ATTRIBUTES && (attributes & FILE_ATTRIBUTE_HIDDEN) == 0) {
        SetFileAttributesW(root.c_str(), attributes | FILE_ATTRIBUTE_HIDDEN);
    }
    const std::wstring directory = root + L"\\SoulboundRecovery";
    if (!CreateDirectoryW(directory.c_str(), nullptr) && GetLastError() != ERROR_ALREADY_EXISTS) return 0;

    // Do not sweep the directory or truncate any existing file. A reused file
    // must match and is checked again under a read lock before injection.
    for (unsigned int attempt = 0; attempt < 32; ++attempt) {
        wchar_t target[MAX_PATH]{};
        const int length = attempt == 0
            ? _snwprintf_s(target, std::size(target), _TRUNCATE,
                L"%ls\\SoulboundNative-%lu.dll", directory.c_str(), processId)
            : _snwprintf_s(target, std::size(target), _TRUNCATE,
                L"%ls\\SoulboundNative-%lu-%lu-%u.dll", directory.c_str(), processId,
                static_cast<unsigned long>(GetCurrentProcessId()), attempt);
        if (length < 0 || static_cast<size_t>(length) >= capacity) return 0;
        HANDLE file = CreateFileW(target, GENERIC_WRITE, FILE_SHARE_READ, nullptr,
            CREATE_NEW, FILE_ATTRIBUTE_NORMAL, nullptr);
        if (file == INVALID_HANDLE_VALUE) {
            if (embedded_resource_integrity::matchesFile(target, bytes, size)) {
                return copyResult(target, output, capacity) ? 1 : 0;
            }
            continue;
        }
        DWORD offset = 0;
        bool ok = true;
        while (offset < size) {
            DWORD written = 0;
            if (!WriteFile(file, bytes + offset, size - offset, &written, nullptr) || written == 0) {
                ok = false;
                break;
            }
            offset += written;
        }
        CloseHandle(file);
        // A partial file is intentionally left alone: deleting a path after
        // closing its handle could remove a file replaced by another process.
        return ok && copyResult(target, output, capacity) ? 1 : 0;
    }
    return 0;
}

extern "C" void* vape421_lock_dll(const wchar_t* path) {
    const unsigned char* bytes = nullptr;
    DWORD size = 0;
    if (path == nullptr || !embeddedDll(bytes, size)) return nullptr;
    auto* guard = new (std::nothrow) embedded_resource_integrity::LockedResourceFile();
    if (guard == nullptr) return nullptr;
    if (!guard->open(path, bytes, size)) {
        delete guard;
        return nullptr;
    }
    return guard;
}

extern "C" void vape421_unlock_dll(void* guard) {
    delete static_cast<embedded_resource_integrity::LockedResourceFile*>(guard);
}
