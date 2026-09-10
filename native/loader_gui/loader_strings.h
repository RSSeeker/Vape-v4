#pragma once

#include <string>

// Loader GUI localisation.
//
// The loader is a native Win32 app whose UI text used to be hardcoded Chinese,
// so it could never follow the language chosen inside the product. The product
// stores that choice in
//
//     <exe>\.vapeclient\config.json
//     -> settings.otherData[] -> { "id": "Language", "value": "<name>" }
//
// where <name> is the font option name ("English" / "Chinese" / "Spanish" /
// "Portuguese" / "French"). The product's default is Chinese, and default
// values are deliberately omitted from that array, so a Chinese user simply has
// no Language entry at all.
//
// We therefore read that file once, before any UI is built:
//
//     "English"                        -> English
//     "Spanish"/"Portuguese"/"French"  -> English (nearest bundled set)
//     "Chinese"                        -> Chinese
//     missing / malformed / unknown    -> Chinese (the product default)
//
// Only Chinese and English tables exist in the loader; the loader never writes
// the setting back, the product remains the single source of truth.
enum class Ls {
    // login page
    UsernameEmail,
    Password,
    Login,
    Or,
    BrowserLogin,
    LoggingIn,
    FollowBrowser,
    Reopen,
    Cancel,

    // minecraft selection page
    MinecraftNotFound,
    OpenMinecraftFirst,
    SelectMinecraft,
    EnsureGameLoaded,
    Injected,

    // loading page
    Stage,
    StageTakingLong,

    // cache prompt
    CachePrompt,
    FilesStoredIn,
    Yes,
    No,

    // loading complete page
    LoadedComplete,
    OpenGuiHint,
    CloseWindow,

    // outdated launcher page
    LauncherOutdated,
    RedownloadFromSite,

    // error page
    LoadErrorStage0,
    CopyError,

    // model status messages
    EnterUsername,
    CannotLoginStartMinecraft,
    CannotLoginLocalService,
    CannotCreateControlSocket,
    CannotExtractNativeDll,
    InjectNativeDllFailed,
    CannotOpenBrowserLogin,
    BrowserLoginTimeout,
    NativeConnectionClosed,
    NativeLoadTimeout,

    // injection coordinator status messages
    ControlPortUnavailable,
    OnlineBaseUrlTooLong,
    ZeusAddressFormat,
    NativeDllNotFoundBesideLoader,
    BootstrapBlockExists,
    CannotCreateBootstrapObject,
    CannotOpenMinecraftProcess,
    CannotWriteProductDllPath,
    CannotResolveLoadLibraryW,
    ProductDllLoadStartFailed,
    ProductDllLoadTimeout,
    ProductDllNoAck,
    ProductDllRejected,
    VapeV4DllNotFound,
    ReflectionExportNotFound,
    CannotWriteReflectionImage,
    ReflectionLoaderStartFailed,
    ReflectionLoaderFailed,

    Count
};

// Reads the saved language from <vapeClientDirectory>\config.json and selects
// the string table. Must be called before the UI is created. Any failure leaves
// the loader on Chinese, the product default.
void loaderStringsInitialize(const std::wstring& vapeClientDirectory);

// Returns the active string for id. Never null.
const wchar_t* ls(Ls id);

// True when the English table is active (used where text length matters).
bool loaderStringsEnglish();
