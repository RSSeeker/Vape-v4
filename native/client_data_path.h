#ifndef SOULBOUND_CLIENT_DATA_PATH_H
#define SOULBOUND_CLIENT_DATA_PATH_H
#include <stddef.h>
#include <wchar.h>

/* Keep in sync with SoulboundPaths; never rename or copy a legacy tree. */
static const wchar_t *soulbound_client_folder(int current_exists, int legacy_exists) {
    return current_exists || !legacy_exists ? L".soulbound" : L".vapeclient";
}
#ifdef _WIN32
#include <windows.h>
#include <stdio.h>
/* output may alias base; candidate paths are built first. */
static int soulbound_client_directory(const wchar_t *base, wchar_t *output, size_t capacity) {
    wchar_t current[MAX_PATH], legacy[MAX_PATH];
    DWORD current_attributes, legacy_attributes;
    const wchar_t *chosen;
    if (!base || !output || !capacity) return 0;
    if (_snwprintf_s(current, MAX_PATH, _TRUNCATE, L"%ls\\.soulbound", base) < 0 ||
            _snwprintf_s(legacy, MAX_PATH, _TRUNCATE, L"%ls\\.vapeclient", base) < 0) return 0;
    current_attributes = GetFileAttributesW(current);
    legacy_attributes = GetFileAttributesW(legacy);
    chosen = soulbound_client_folder(
            current_attributes != INVALID_FILE_ATTRIBUTES && (current_attributes & FILE_ATTRIBUTE_DIRECTORY),
            legacy_attributes != INVALID_FILE_ATTRIBUTES && (legacy_attributes & FILE_ATTRIBUTE_DIRECTORY));
    return wcsncpy_s(output, capacity, wcscmp(chosen, L".soulbound") == 0 ? current : legacy, _TRUNCATE) == 0;
}
#endif
#endif
