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
// Only Chinese and English are bundled in the product. English is its default,
// and default values are omitted from the array. We therefore use English for
// an absent, unreadable or malformed configuration, and Chinese only when it
// was explicitly selected and saved. Unsupported entries are ignored, as in
// the product. Legacy English settings work too.
// The loader reads this before the UI is built and never writes it back.
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

    // auto inject (minecraft selection page)
    AutoInjectLabel,
    AutoInjectWaiting,
    AutoInjectSkipped,
    AutoInjectFailed,
    AutoInjectWaitingTitle,
    AutoInjectWaitingDetail,
    AutoInjectActiveHint,

    // settings flyout (slides in from the left edge)
    SettingsLabel,
    AutoInjectHint,

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
    ErrorAlreadyInjected,
    ErrorLoaderToken,
    ErrorJvmNotLoaded,
    ErrorJvmExportMissing,
    ErrorNoJavaVm,
    ErrorAttachThread,
    ErrorJavaStage,
    ErrorUnknownStage,
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
    NativeDllIntegrityFailed,
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
// the loader on English, the product default.
void loaderStringsInitialize(const std::wstring& vapeClientDirectory);

// Returns the active string for id. Never null.
const wchar_t* ls(Ls id);

// True when the English table is active (used where text length matters).
bool loaderStringsEnglish();
