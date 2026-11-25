# English edition: source analysis

The supplied release is `Vape-v4.21.58.exe` (the supplied SHA-256 is recorded in the [README](../README.md)). This checkout's `build.gradle` also identifies version 4.21.58, and the upstream tag points to source commit `36aa9fb4890b2845de3452f008a334d0d08b774f`. The local sandbox could not download the release binary; Windows CI downloads it for hash verification and offline inspection. Consult the report from a successful run for the results. These changes are based on recovered source, not a claim that the English build is byte-for-byte identical to the release.

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
- `Vape.initializeManagers` retains the original initialization order; a saved language is applied at the same point as in the 4.21.58 source.
- `english.properties.txt` supplies English strings; paired Chinese strings are retained for switched-language use. Previously hard-coded Chinese cache, reconnection and spawner labels now use the localization mechanism. Console messages and renderer diagnostics were translated separately.

An explicit non-default selection is serialized in the existing format, for example:

```json
{"settings":{"otherData":[{"id":"Language","value":"Chinese"}]}}
```

Do not replace an existing configuration with this example: it would omit your other settings and profiles. Change the language through the in-game settings instead. The loader sees the change after restart. For the original release, selecting **设置 → 通用 → 语言 → English** also switches its existing language table, but its Chinese-only console and a few hard-coded labels remain unchanged; those are addressed by rebuilding this branch.

## Verification and limits

`Vape421LoaderLanguageTest` covers the portable JSON selection logic, and `Vape421LoaderStringsTest` covers the Win32 loader's file-based selection and table contents. `verifyLocalization` validates the English resource strings and paired dynamic translations; `verifyFontCoverage` checks the Chinese font. The Windows CI workflow builds the Java 8-compatible payload and native bundle and runs the CTest suite.

The module-parity audit compares the pinned source with this branch, including all 460 Java files in the module tree. The only module source exceptions are the GUI language default in `ClientSettings` and the displayed label in `SpawnerFinder`; the audit reverses those exact edits and requires the originals to match. It also checks supporting Java sources, native code and resources against a reviewed list of localization changes. CI rebuilds the baseline with the same build script, JDK and flags, compares class and module inventories, checks the rebuilt EXE's embedded DLL and payload, and inspects the upstream EXE after verifying the supplied SHA-256. Results are placed in `MODULE-PARITY-REPORT.txt` in a successful bundle.

These are static source and build checks, not proof of identical runtime behavior. No Minecraft game session, end-to-end injection or module-by-module gameplay test has been performed in this environment. An authorized runtime test on the intended Minecraft version is still required. Rebuilt binaries will not have the upstream release's hash or signature.
