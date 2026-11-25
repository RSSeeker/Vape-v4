# English edition: source analysis

The supplied release is `Vape-v4.21.58.exe` (the supplied SHA-256 is recorded in the [README](../README.md)). This checkout's `build.gradle` also identifies version 4.21.58. The linked release redirected to a binary host unavailable in this environment, so its bytes and checksum were **not** verified here. The changes below are based on the existing recovered source, not a claim that this build is byte-for-byte identical to the release.

## How the project is assembled

| Component | Relevant source | Role |
| --- | --- | --- |
| Windows GUI EXE | `native/loader_gui/`, `native/injector.c` | Selects a game process, displays progress and supports the `-nogui` console. |
| Native DLL | `native/dllmain.c`, `native/native_bridge.c` | Bridges the loader and the recovered Java client. |
| Java payload | `src/main/java/`, `src/main/resources/` | Implements the in-game UI, modules, settings and translations. |
| Build | `build.gradle`, `native/CMakeLists.txt` | Builds the payload and embeds it in the DLL, then embeds the DLL in the EXE. |

The DLL carries the Java JAR as resource **421** (`native/payload.rc.in`); the EXE carries the DLL as resource **422** (`native/embed_dll.cmake`). In GUI mode an external `Vape-v4.21Native.dll` is preferred if present, so running beside an older DLL can bypass the new English Java payload. Console mode uses the embedded DLL.

## Where language is chosen

- `PublicProfileSettings` and the GUI module's `ClientSettings` now default to `FontSelector.j` (English); Chinese remains available as `FontSelector.c`.
- `IndependentSettingsManager` omits default settings when saving. Consequently an absent Language entry now means English; an explicitly saved Chinese entry remains Chinese.
- The launcher reads only `settings.otherData` from `<exe>/.vapeclient/config.json`. Its initial and failure fallback is English. It honors a saved Chinese entry and never changes the file itself. The bounded parser rejects malformed documents and ignores unrelated lookalike entries in profiles.
- `Vape.initializeManagers` applies a saved language immediately after loading configuration, before the startup notification.
- `english.properties.txt` supplies English strings; paired Chinese strings are retained for switched-language use. Previously hard-coded Chinese cache, reconnection and spawner labels now use the localization mechanism. Console messages and renderer diagnostics were translated separately.

An explicit non-default selection is serialized in the existing format, for example:

```json
{"settings":{"otherData":[{"id":"Language","value":"Chinese"}]}}
```

Do not replace an existing configuration with this example: it would omit your other settings and profiles. Change the language through the in-game settings instead. The loader sees the change after restart. For the original release, selecting **设置 → 通用 → 语言 → English** also switches its existing language table, but its Chinese-only console and a few hard-coded labels remain unchanged; those are addressed by rebuilding this branch.

## Verification and limits

`Vape421LoaderLanguageTest` covers the portable JSON selection logic, and `Vape421LoaderStringsTest` covers the Win32 loader's file-based selection and table contents. `verifyLocalization` validates the English resource strings and paired dynamic translations; `verifyFontCoverage` checks the Chinese font. The Windows CI workflow builds the Java 8-compatible payload and native bundle and runs the CTest suite.

No Windows game session or end-to-end native injection was run in this environment, and no release binary was produced here. A Windows MSVC build and authorized runtime test are still required. Locally rebuilt binaries will not have the upstream release's hash or signature.
