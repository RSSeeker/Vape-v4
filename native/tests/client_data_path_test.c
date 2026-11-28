#include "client_data_path.h"
#include <stdio.h>
#include <wchar.h>

static int failures = 0;
static void check(int condition, const char *message) {
    if (!condition) { fprintf(stderr, "FAIL: %s\n", message); ++failures; }
}

int main(void) {
    int current, previous, legacy;
    for (current = 0; current <= 1; ++current) {
        for (previous = 0; previous <= 1; ++previous) {
            for (legacy = 0; legacy <= 1; ++legacy) {
                const wchar_t *expected = current ? L".inevitable" :
                        previous ? L".soulbound" : legacy ? L".vapeclient" : L".inevitable";
                check(wcscmp(inevitable_client_folder(current, previous, legacy), expected) == 0, "folder precedence");
            }
        }
    }
#ifdef _WIN32
    {
        wchar_t temp[MAX_PATH], root[MAX_PATH], newest[MAX_PATH], previousPath[MAX_PATH], legacyPath[MAX_PATH], result[MAX_PATH];
        if (!GetTempPathW(MAX_PATH, temp) || _snwprintf_s(root, MAX_PATH, _TRUNCATE,
                L"%lsInevitablePathTest-%lu", temp, (unsigned long)GetCurrentProcessId()) < 0 || !CreateDirectoryW(root, NULL)) return 1;
        _snwprintf_s(newest, MAX_PATH, _TRUNCATE, L"%ls\\.inevitable", root);
        _snwprintf_s(previousPath, MAX_PATH, _TRUNCATE, L"%ls\\.soulbound", root);
        _snwprintf_s(legacyPath, MAX_PATH, _TRUNCATE, L"%ls\\.vapeclient", root);
        check(inevitable_client_directory(root, result, MAX_PATH) && wcscmp(result, newest) == 0, "fresh path");
        check(CreateDirectoryW(legacyPath, NULL) != 0, "create legacy");
        check(inevitable_client_directory(root, result, MAX_PATH) && wcscmp(result, legacyPath) == 0, "legacy path");
        check(CreateDirectoryW(previousPath, NULL) != 0, "create previous");
        check(inevitable_client_directory(root, result, MAX_PATH) && wcscmp(result, previousPath) == 0, "previous path wins");
        check(CreateDirectoryW(newest, NULL) != 0, "create current");
        check(inevitable_client_directory(root, result, MAX_PATH) && wcscmp(result, newest) == 0, "current path wins");
        check(!inevitable_client_directory(root, result, 2), "short buffer rejected");
        RemoveDirectoryW(newest); RemoveDirectoryW(previousPath); RemoveDirectoryW(legacyPath); RemoveDirectoryW(root);
    }
#endif
    if (failures) return 1;
    puts("Inevitable client data path OK");
    return 0;
}
