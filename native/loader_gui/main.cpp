#include "controller_model.h"
#include "controller_ui.h"
#include "loader_strings.h"

#include <windows.h>
#include <shellapi.h>

// Declared in injector.c (renamed from wmain so the WIN32 GUI entry point can
// dispatch to it): console-mode injection with process selection or a pid.
// injector.c compiles as C, so the symbol is unmangled.
extern "C" int console_main(int argc, wchar_t **argv);

// Builds a stripped argv (without the exe name and -nogui) for console_main
// from the Windows command line. Strings are copied into caller-owned buffers
// because CommandLineToArgvW's memory is freed before console_main runs.
// Returns the number of remaining arguments, or 0 when there is nothing.
static int console_argv(wchar_t **outArgv, wchar_t (*outBuf)[128],
        size_t outCount) {
    int argc = 0;
    wchar_t **argv = CommandLineToArgvW(GetCommandLineW(), &argc);
    if (argv == nullptr || argc < 3) {
        if (argv != nullptr) LocalFree(argv);
        return 0;
    }
    // argv[0] = exe, argv[1] = -nogui; pass argv[2..] through.
    const int stripped = argc - 2;
    const size_t count = static_cast<size_t>(stripped) < outCount
            ? static_cast<size_t>(stripped) : outCount;
    for (size_t i = 0; i < count; ++i) {
        wcsncpy_s(outBuf[i], 128, argv[i + 2], _TRUNCATE);
        outArgv[i] = outBuf[i];
    }
    LocalFree(argv);
    return static_cast<int>(count);
}

int APIENTRY wWinMain(HINSTANCE instance, HINSTANCE, wchar_t*, int showCommand) {
    // Console mode is opt-in: only when the first argument is exactly
    // `-nogui` do we run the console injector. Everything else (no arguments,
    // or unknown flags) launches the GUI loader.
    int argc = 0;
    wchar_t **argv = CommandLineToArgvW(GetCommandLineW(), &argc);
    const bool noGui = argv != nullptr && argc >= 2
            && _wcsicmp(argv[1], L"-nogui") == 0;
    if (argv != nullptr) {
        LocalFree(argv);
    }

    if (noGui) {
        // The GUI subsystem has no console attached; create one so the
        // console injector's output and interactive picker work. The console
        // is intentionally not freed here: calling FreeConsole() from a GUI
        // subsystem process triggers STATUS_CONTROL_C_EXIT (0xC000013A), so we
        // let the OS reap the console when the process exits.
        AllocConsole();
        FILE *dummy = nullptr;
        freopen_s(&dummy, "CONOUT$", "w", stdout);
        freopen_s(&dummy, "CONOUT$", "w", stderr);
        freopen_s(&dummy, "CONIN$", "r", stdin);
        wchar_t *consoleArgv[8]{};
        wchar_t consoleArgBuf[8][128]{};
        const int consoleArgc = console_argv(consoleArgv, consoleArgBuf,
                sizeof(consoleArgv) / sizeof(consoleArgv[0]));
        return console_main(consoleArgc, consoleArgv);
    }

    ControllerModel model;
    // Follow the language the product saved in <exe>\.vapeclient\config.json so
    // the very first screen already matches the in-game setting. Read before the
    // UI is constructed; any failure keeps the loader on Chinese.
    loaderStringsInitialize(model.cacheDirectory());
    ControllerUi ui(instance, model);
    return ui.run(showCommand);
}
