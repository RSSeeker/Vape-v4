#include "client_data_path.h"
#include <stdio.h>
#include <wchar.h>
static int failures;
static void check(int ok, const char *name) {
    printf("%s %s\n", ok ? "ok" : "FAIL", name);
    if (!ok) ++failures;
}
int main(void) {
    check(wcscmp(soulbound_client_folder(0,0), L".soulbound") == 0, "new install");
    check(wcscmp(soulbound_client_folder(0,1), L".vapeclient") == 0, "legacy only");
    check(wcscmp(soulbound_client_folder(1,1), L".soulbound") == 0, "both folders");
    check(wcscmp(soulbound_client_folder(1,0), L".soulbound") == 0, "current only");
#ifdef _WIN32
    {
        wchar_t root[MAX_PATH], current[MAX_PATH], legacy[MAX_PATH], result[MAX_PATH];
        DWORD size = GetTempPathW(MAX_PATH, root);
        if (!size || size >= MAX_PATH || _snwprintf_s(root + size, MAX_PATH - size, _TRUNCATE,
                L"SoulboundPathTest-%lu", (unsigned long)GetCurrentProcessId()) < 0 || !CreateDirectoryW(root, NULL)) return 1;
        _snwprintf_s(current, MAX_PATH, _TRUNCATE, L"%ls\\.soulbound", root);
        _snwprintf_s(legacy, MAX_PATH, _TRUNCATE, L"%ls\\.vapeclient", root);
        check(soulbound_client_directory(root, result, MAX_PATH) && wcscmp(result, current) == 0, "fresh path");
        check(CreateDirectoryW(legacy, NULL) != 0, "create legacy fixture");
        check(soulbound_client_directory(root, result, MAX_PATH) && wcscmp(result, legacy) == 0, "legacy path");
        check(CreateDirectoryW(current, NULL) != 0, "create current fixture");
        check(soulbound_client_directory(root, result, MAX_PATH) && wcscmp(result, current) == 0, "current path wins");
        check(!soulbound_client_directory(root, result, 2), "short buffer rejected");
        RemoveDirectoryW(current); RemoveDirectoryW(legacy); RemoveDirectoryW(root);
    }
#endif
    return failures ? 1 : 0;
}
