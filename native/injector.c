#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>
#include <tlhelp32.h>

#include <conio.h>
#include <fcntl.h>
#include <io.h>
#include <locale.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <wchar.h>

#include "embedded_dll_materializer.h"

#define MAX_CANDIDATES 256
#define WINDOW_TITLE_CAPACITY 256
#define REFRESH_INTERVAL_MS 750

typedef struct process_candidate {
    DWORD process_id;
    wchar_t executable[MAX_PATH];
    wchar_t title[WINDOW_TITLE_CAPACITY];
} process_candidate;

typedef struct window_search_context {
    process_candidate *candidates;
    size_t count;
} window_search_context;

static void print_last_error(const wchar_t *operation) {
    DWORD error = GetLastError();
    wchar_t *message = NULL;
    FormatMessageW(FORMAT_MESSAGE_ALLOCATE_BUFFER
                    | FORMAT_MESSAGE_FROM_SYSTEM
                    | FORMAT_MESSAGE_IGNORE_INSERTS,
            NULL, error, 0, (wchar_t *)&message, 0, NULL);
    fwprintf(stderr, L"%ls failed (%lu): %ls\n", operation,
            (unsigned long)error, message == NULL ? L"unknown error" : message);
    if (message != NULL) {
        LocalFree(message);
    }
}

static int is_java_process(const wchar_t *executable) {
    return _wcsicmp(executable, L"java.exe") == 0
            || _wcsicmp(executable, L"javaw.exe") == 0;
}

static BOOL CALLBACK capture_window_title(HWND window, LPARAM parameter) {
    window_search_context *context = (window_search_context *)parameter;
    wchar_t title[WINDOW_TITLE_CAPACITY];
    DWORD process_id = 0;
    size_t index;
    if (!IsWindowVisible(window) || GetWindowTextLengthW(window) == 0) {
        return TRUE;
    }
    GetWindowThreadProcessId(window, &process_id);
    if (process_id == 0
            || GetWindowTextW(window, title, WINDOW_TITLE_CAPACITY) == 0) {
        return TRUE;
    }
    for (index = 0; index < context->count; ++index) {
        process_candidate *candidate = &context->candidates[index];
        if (candidate->process_id == process_id && candidate->title[0] == L'\0') {
            wcscpy(candidate->title, title);
            break;
        }
    }
    return TRUE;
}

static int compare_candidates(const void *left, const void *right) {
    const process_candidate *a = (const process_candidate *)left;
    const process_candidate *b = (const process_candidate *)right;
    if (a->process_id < b->process_id) return -1;
    if (a->process_id > b->process_id) return 1;
    return 0;
}

static size_t enumerate_candidates(
        process_candidate *candidates, size_t capacity) {
    HANDLE snapshot;
    PROCESSENTRY32W entry;
    size_t count = 0;
    size_t read_index;
    size_t write_index = 0;
    window_search_context context;

    snapshot = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
    if (snapshot == INVALID_HANDLE_VALUE) {
        return 0;
    }
    memset(&entry, 0, sizeof(entry));
    entry.dwSize = sizeof(entry);
    if (Process32FirstW(snapshot, &entry)) {
        do {
            if (count < capacity && is_java_process(entry.szExeFile)) {
                process_candidate *candidate = &candidates[count++];
                memset(candidate, 0, sizeof(*candidate));
                candidate->process_id = entry.th32ProcessID;
                wcsncpy(candidate->executable, entry.szExeFile, MAX_PATH - 1);
            }
        } while (Process32NextW(snapshot, &entry));
    }
    CloseHandle(snapshot);

    context.candidates = candidates;
    context.count = count;
    EnumWindows(capture_window_title, (LPARAM)&context);

    /* The selector is for game windows, so omit background JVMs without one. */
    for (read_index = 0; read_index < count; ++read_index) {
        if (candidates[read_index].title[0] != L'\0') {
            if (write_index != read_index) {
                candidates[write_index] = candidates[read_index];
            }
            ++write_index;
        }
    }
    qsort(candidates, write_index, sizeof(*candidates), compare_candidates);
    return write_index;
}

static void clear_console_rows(HANDLE output, SHORT rows) {
    CONSOLE_SCREEN_BUFFER_INFO info;
    COORD start = {0, 0};
    DWORD cells;
    DWORD written;
    if (!GetConsoleScreenBufferInfo(output, &info)) return;
    if (rows > info.dwSize.Y) rows = info.dwSize.Y;
    cells = (DWORD)info.dwSize.X * (DWORD)rows;
    FillConsoleOutputCharacterW(output, L' ', cells, start, &written);
    FillConsoleOutputAttribute(output, info.wAttributes, cells, start, &written);
}

static void render_selector(const process_candidate *candidates, size_t count,
        size_t selected, const wchar_t *dll_path) {
    HANDLE output = GetStdHandle(STD_OUTPUT_HANDLE);
    CONSOLE_SCREEN_BUFFER_INFO info;
    COORD home = {0, 0};
    size_t index;
    static SHORT previous_rows = 0;
    if (GetConsoleScreenBufferInfo(output, &info)) {
        SHORT rows = (SHORT)(count + 5);
        if (count == 0) ++rows;
        clear_console_rows(output, rows > previous_rows ? rows : previous_rows);
        SetConsoleCursorPosition(output, home);
        previous_rows = rows;
    }
    wprintf(L"Soulbound injector\n");
    wprintf(L"DLL: %ls\n\n", dll_path);
    wprintf(L"Select a Java game window (Up/Down to select, Enter to inject, Esc to exit)\n\n");
    if (count == 0) {
        wprintf(L"  No visible java.exe/javaw.exe window found; waiting...\n");
    } else {
        for (index = 0; index < count; ++index) {
            wprintf(L"%lc [%5lu] %-9ls  %ls\n",
                    index == selected ? L'>' : L' ',
                    (unsigned long)candidates[index].process_id,
                    candidates[index].executable, candidates[index].title);
        }
    }
    fflush(stdout);
}

static DWORD select_process(const wchar_t *dll_path) {
    process_candidate candidates[MAX_CANDIDATES];
    size_t count = 0;
    size_t selected = 0;
    DWORD selected_process_id = 0;
    ULONGLONG next_refresh = 0;
    HANDLE output = GetStdHandle(STD_OUTPUT_HANDLE);
    CONSOLE_CURSOR_INFO original_cursor;
    CONSOLE_CURSOR_INFO hidden_cursor;
    int cursor_changed = 0;

    if (GetConsoleCursorInfo(output, &original_cursor)) {
        hidden_cursor = original_cursor;
        hidden_cursor.bVisible = FALSE;
        cursor_changed = SetConsoleCursorInfo(output, &hidden_cursor);
    }
    for (;;) {
        ULONGLONG now = GetTickCount64();
        if (now >= next_refresh) {
            DWORD previous_id = count == 0 ? 0 : candidates[selected].process_id;
            size_t index;
            count = enumerate_candidates(candidates, MAX_CANDIDATES);
            selected = 0;
            for (index = 0; index < count; ++index) {
                if (candidates[index].process_id == previous_id) {
                    selected = index;
                    break;
                }
            }
            render_selector(candidates, count, selected, dll_path);
            next_refresh = now + REFRESH_INTERVAL_MS;
        }
        if (_kbhit()) {
            int key = _getwch();
            if (key == 0 || key == 0xe0) {
                key = _getwch();
                if (key == 72 && count != 0) {
                    selected = selected == 0 ? count - 1 : selected - 1;
                    render_selector(candidates, count, selected, dll_path);
                } else if (key == 80 && count != 0) {
                    selected = (selected + 1) % count;
                    render_selector(candidates, count, selected, dll_path);
                }
            } else if (key == 13 && count != 0) {
                selected_process_id = candidates[selected].process_id;
                break;
            } else if (key == 27) {
                break;
            }
        }
        Sleep(25);
    }
    if (cursor_changed) SetConsoleCursorInfo(output, &original_cursor);
    wprintf(L"\n");
    return selected_process_id;
}

static uintptr_t remote_module_base(DWORD process_id, const wchar_t *module_name) {
    HANDLE snapshot;
    MODULEENTRY32W entry;
    uintptr_t result = 0;
    snapshot = CreateToolhelp32Snapshot(
            TH32CS_SNAPMODULE | TH32CS_SNAPMODULE32, process_id);
    if (snapshot == INVALID_HANDLE_VALUE) {
        return 0;
    }
    memset(&entry, 0, sizeof(entry));
    entry.dwSize = sizeof(entry);
    if (Module32FirstW(snapshot, &entry)) {
        do {
            if (_wcsicmp(entry.szModule, module_name) == 0) {
                result = (uintptr_t)entry.modBaseAddr;
                break;
            }
        } while (Module32NextW(snapshot, &entry));
    }
    CloseHandle(snapshot);
    return result;
}

static uintptr_t remote_module_by_path(
        DWORD process_id, const wchar_t *module_path) {
    HANDLE snapshot;
    MODULEENTRY32W entry;
    uintptr_t result = 0;
    snapshot = CreateToolhelp32Snapshot(
            TH32CS_SNAPMODULE | TH32CS_SNAPMODULE32, process_id);
    if (snapshot == INVALID_HANDLE_VALUE) {
        return 0;
    }
    memset(&entry, 0, sizeof(entry));
    entry.dwSize = sizeof(entry);
    if (Module32FirstW(snapshot, &entry)) {
        do {
            if (_wcsicmp(entry.szExePath, module_path) == 0) {
                result = (uintptr_t)entry.modBaseAddr;
                break;
            }
        } while (Module32NextW(snapshot, &entry));
    }
    CloseHandle(snapshot);
    return result;
}

static int require_x64_target(HANDLE process) {
    typedef BOOL (WINAPI *is_wow64_process2_fn)(HANDLE, USHORT *, USHORT *);
    is_wow64_process2_fn is_wow64_process2 =
            (is_wow64_process2_fn)GetProcAddress(
                    GetModuleHandleW(L"kernel32.dll"), "IsWow64Process2");
    if (is_wow64_process2 != NULL) {
        USHORT process_machine = IMAGE_FILE_MACHINE_UNKNOWN;
        USHORT native_machine = IMAGE_FILE_MACHINE_UNKNOWN;
        if (!is_wow64_process2(process, &process_machine, &native_machine)) {
            return 0;
        }
        return process_machine == IMAGE_FILE_MACHINE_UNKNOWN
                && native_machine == IMAGE_FILE_MACHINE_AMD64;
    }
    {
        BOOL wow64 = FALSE;
        if (!IsWow64Process(process, &wow64)) {
            return 0;
        }
        return !wow64 && sizeof(void *) == 8;
    }
}

static int inject_library(DWORD process_id, const wchar_t *dll_path) {
    HANDLE process = NULL;
    HANDLE thread = NULL;
    LPVOID remote_path = NULL;
    HMODULE local_kernel;
    FARPROC local_load_library;
    uintptr_t remote_kernel;
    uintptr_t load_library_offset;
    LPTHREAD_START_ROUTINE remote_load_library;
    SIZE_T path_bytes = (wcslen(dll_path) + 1) * sizeof(wchar_t);
    SIZE_T written = 0;
    DWORD wait_result;
    int attempt;
    int result = 0;
    void *verified_dll = vape421_lock_dll(dll_path);
    if (verified_dll == NULL) {
        fwprintf(stderr, L"Native DLL does not match the embedded resource; injection was refused.\n");
        return 0;
    }
    if (remote_module_by_path(process_id, dll_path) != 0) {
        vape421_unlock_dll(verified_dll);
        return 2;
    }

    process = OpenProcess(PROCESS_CREATE_THREAD | PROCESS_QUERY_INFORMATION
                    | PROCESS_VM_OPERATION | PROCESS_VM_WRITE | PROCESS_VM_READ,
            FALSE, process_id);
    if (process == NULL) {
        print_last_error(L"OpenProcess");
        goto cleanup;
    }
    if (!require_x64_target(process)) {
        fwprintf(stderr, L"The target process is not x64; injection was refused.\n");
        goto cleanup;
    }
    remote_path = VirtualAllocEx(process, NULL, path_bytes,
            MEM_COMMIT | MEM_RESERVE, PAGE_READWRITE);
    if (remote_path == NULL) {
        print_last_error(L"VirtualAllocEx");
        goto cleanup;
    }
    if (!WriteProcessMemory(process, remote_path, dll_path,
            path_bytes, &written) || written != path_bytes) {
        print_last_error(L"WriteProcessMemory");
        goto cleanup;
    }

    local_kernel = GetModuleHandleW(L"kernel32.dll");
    local_load_library = GetProcAddress(local_kernel, "LoadLibraryW");
    remote_kernel = remote_module_base(process_id, L"kernel32.dll");
    if (local_kernel == NULL || local_load_library == NULL || remote_kernel == 0) {
        fwprintf(stderr, L"Could not resolve remote kernel32!LoadLibraryW.\n");
        goto cleanup;
    }
    load_library_offset = (uintptr_t)local_load_library - (uintptr_t)local_kernel;
    remote_load_library = (LPTHREAD_START_ROUTINE)(
            remote_kernel + load_library_offset);
    thread = CreateRemoteThread(process, NULL, 0, remote_load_library,
            remote_path, 0, NULL);
    if (thread == NULL) {
        print_last_error(L"CreateRemoteThread");
        goto cleanup;
    }
    wait_result = WaitForSingleObject(thread, 30000);
    if (wait_result != WAIT_OBJECT_0) {
        fwprintf(stderr, L"Remote LoadLibraryW did not finish within 30 seconds.\n");
        goto cleanup;
    }
    for (attempt = 0; attempt < 100; ++attempt) {
        if (remote_module_by_path(process_id, dll_path) != 0) {
            result = 1;
            break;
        }
        Sleep(50);
    }
    if (result == 0) {
        fwprintf(stderr, L"LoadLibraryW returned, but the DLL was not mapped. "
                L"Check the selected client folder's log directory for bootstrap errors.\n");
        goto cleanup;
    }

cleanup:
    if (thread != NULL) CloseHandle(thread);
    if (remote_path != NULL && process != NULL) {
        VirtualFreeEx(process, remote_path, 0, MEM_RELEASE);
    }
    if (process != NULL) CloseHandle(process);
    vape421_unlock_dll(verified_dll);
    return result;
}

static void usage(void) {
    wchar_t executable[MAX_PATH];
    if (GetModuleFileNameW(NULL, executable, MAX_PATH) == 0) {
        wcsncpy_s(executable, MAX_PATH, L"Soulbound.exe", _TRUNCATE);
    }
    executable[MAX_PATH - 1] = L'\0';
    fwprintf(stderr,
            L"Usage: \"%ls\" -nogui [minecraft-pid]\n"
            L"Without a PID, an auto-refreshing Java window picker is shown.\n"
            L"An adjacent DLL is used only when it matches the embedded DLL.\n"
            L"Otherwise, the embedded DLL is extracted into the selected client folder.\n",
            executable);
}

int console_main(int argc, wchar_t **argv) {
    wchar_t dll_path[MAX_PATH];
    wchar_t *end = NULL;
    unsigned long process_id = 0;
    /* Render window titles (often non-ASCII) as UTF-8 in the selector. */
    SetConsoleOutputCP(CP_UTF8);
    _setmode(_fileno(stdout), _O_U8TEXT);
    _setmode(_fileno(stderr), _O_U8TEXT);
    setlocale(LC_ALL, ".UTF-8");
    if (argc > 2) {
        usage();
        return 2;
    }
    if (argc == 1) {
        process_id = wcstoul(argv[0], &end, 10);
        if (process_id == 0 || end == argv[0] || *end != L'\0') {
            fwprintf(stderr, L"Invalid process ID: %ls\n", argv[0]);
            return 2;
        }
    }
    /* Use the same checked, non-destructive materializer as GUI mode. */
    if (!vape421_materialize_dll(process_id, dll_path, MAX_PATH)) {
        fwprintf(stderr, L"Could not extract the embedded SoulboundNative.dll.\n");
        return 2;
    }
    if (argc != 1) {
        process_id = (unsigned long)select_process(dll_path);
        if (process_id == 0) {
            return 1;
        }
    }
    {
        int injection_result = inject_library((DWORD)process_id, dll_path);
        if (injection_result == 0) {
            return 3;
        }
        if (injection_result == 2) {
            wprintf(L"%ls is already loaded in PID %lu; no second bootstrap was requested.\n",
                    dll_path, process_id);
            return 0;
        }
    }
    wprintf(L"Injected %ls into PID %lu; Java bootstrap is running asynchronously.\n",
            dll_path, process_id);
    return 0;
}
