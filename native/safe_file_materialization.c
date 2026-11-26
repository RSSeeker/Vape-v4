#include "safe_file_materialization.h"

#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>
#include <wchar.h>

int vape421_write_unique_file(const wchar_t *base, const wchar_t *extension,
        const unsigned char *bytes, size_t size, wchar_t *output, size_t capacity) {
    unsigned int attempt;
    if (base == NULL || extension == NULL || bytes == NULL || size == 0
            || output == NULL || capacity == 0) return 0;
    output[0] = L'\0';
    for (attempt = 0; attempt < 32; ++attempt) {
        wchar_t path[MAX_PATH];
        int length;
        HANDLE file;
        size_t offset = 0;
        int ok = 1;
        if (attempt == 0) {
            length = _snwprintf_s(path, MAX_PATH, _TRUNCATE, L"%ls%ls", base, extension);
        } else {
            length = _snwprintf_s(path, MAX_PATH, _TRUNCATE, L"%ls-%lu-%u%ls",
                    base, (unsigned long)GetCurrentProcessId(), attempt, extension);
        }
        if (length < 0 || (size_t)length >= capacity) return 0;
        file = CreateFileW(path, GENERIC_WRITE, FILE_SHARE_READ, NULL, CREATE_NEW,
                FILE_ATTRIBUTE_NORMAL, NULL);
        if (file == INVALID_HANDLE_VALUE) {
            DWORD error = GetLastError();
            if (error == ERROR_FILE_EXISTS || error == ERROR_ALREADY_EXISTS) continue;
            return 0;
        }
        while (offset < size) {
            DWORD written = 0;
            DWORD remaining = size - offset > 0xffffffffu
                    ? 0xffffffffu : (DWORD)(size - offset);
            if (!WriteFile(file, bytes + offset, remaining, &written, NULL) || written == 0) {
                ok = 0;
                break;
            }
            offset += written;
        }
        if (ok && !FlushFileBuffers(file)) ok = 0;
        CloseHandle(file);
        if (!ok) return 0; /* leave partial file rather than delete a replaced path */
        return wcsncpy_s(output, capacity, path, _TRUNCATE) == 0;
    }
    return 0;
}
