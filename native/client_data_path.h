#ifndef INEVITABLE_CLIENT_DATA_PATH_H
#define INEVITABLE_CLIENT_DATA_PATH_H
#include <stddef.h>
#include <wchar.h>

/* Keep in sync with InevitablePaths; never rename or copy a legacy tree. */
static const wchar_t *inevitable_client_folder(int current_exists, int previous_exists, int legacy_exists) {
    if (current_exists) return L".inevitable";
    if (previous_exists) return L".soulbound";
    return legacy_exists ? L".vapeclient" : L".inevitable";
}
#ifdef _WIN32
#include <windows.h>
#include <stdio.h>
/* output may alias base; candidate paths are built first. */
static int inevitable_client_directory(const wchar_t *base, wchar_t *output, size_t capacity) {
    wchar_t current[MAX_PATH], previous[MAX_PATH], legacy[MAX_PATH];
    DWORD ca, pa, la;
    const wchar_t *chosen;
    if (!base || !output || !capacity) return 0;
    if (_snwprintf_s(current, MAX_PATH, _TRUNCATE, L"%ls\\.inevitable", base) < 0 ||
            _snwprintf_s(previous, MAX_PATH, _TRUNCATE, L"%ls\\.soulbound", base) < 0 ||
            _snwprintf_s(legacy, MAX_PATH, _TRUNCATE, L"%ls\\.vapeclient", base) < 0) return 0;
    ca = GetFileAttributesW(current);
    pa = GetFileAttributesW(previous);
    la = GetFileAttributesW(legacy);
    chosen = inevitable_client_folder(
            ca != INVALID_FILE_ATTRIBUTES && (ca & FILE_ATTRIBUTE_DIRECTORY),
            pa != INVALID_FILE_ATTRIBUTES && (pa & FILE_ATTRIBUTE_DIRECTORY),
            la != INVALID_FILE_ATTRIBUTES && (la & FILE_ATTRIBUTE_DIRECTORY));
    return wcsncpy_s(output, capacity,
            wcscmp(chosen, L".inevitable") == 0 ? current :
            (wcscmp(chosen, L".soulbound") == 0 ? previous : legacy), _TRUNCATE) == 0;
}
#endif
#endif
