# Vape 4.21 Product Recovery

> **Language:** English | [中文](README_ZH.md)
>
> Releases: [Releases](https://github.com/RSSeeker/Vape-v4/releases)
>
> Source code: [OpenVapeCN/OpenVape](https://github.com/OpenVapeCN/OpenVape)
> (This project recovers, cleans up and localizes the public source from that repository.)

A research/recovery project for Vape 4.21's Java layer and Windows x64 native bridge layer. This branch defaults to English while retaining Chinese.

## Disclaimer and Copyright

This is an **educational/research** reverse-engineering recovery project, intended for software compatibility analysis and technical study.

- For educational, research and authorized-environment testing use only
- You **must already hold a legitimate Vape license** before using this project
- Users must make sure they comply with local laws, software license agreements and server rules
- The project author accepts no responsibility for any violation committed by users

## Independence Statement

This project is independent research carried out on the author's own initiative:

- It accepts no external funding, sponsorship or commercial cooperation of any kind
- It does not represent, and is not affiliated with, any organization, group or individual
- It has no connection to any political stance, ideology or commercial interest
- Its content reflects only the author's personal technical research interest

The project is started and maintained independently by one individual; all views and results are technical research only and imply no other position.

## Security Notice

Verify the origin, signatures and hashes of downloaded programs before running them. A locally rebuilt binary differs from the upstream release and is unsigned unless you supply a code-signing certificate. No distribution channel can guarantee safety.

## Main artifact

| File | Description |
| --- | --- |
| `Vape-v4.21.*.exe` | Single-file GUI loader (embeds the complete DLL, Java payload and icon) |

**Optional external DLL**: if `Vape-v4.21Native.dll` sits next to the exe it is loaded preferentially (handy for replacing/updating the native layer yourself); otherwise the embedded copy is extracted, so no extra files are required.

**Language and compatibility:** The loader reads `.vapeclient\config.json` next
to the executable. An explicitly saved Chinese selection remains Chinese; choose
**Settings → General → Language → English** in-game and restart the loader to
change it. Configurations from older versions that omitted their default language
now start in English. The console messages are English regardless of this setting.

Run the rebuilt EXE on its own or next to the **matching rebuilt DLL**. A stale
external `Vape-v4.21Native.dll` takes precedence over the embedded payload and
can otherwise bring back the older Chinese in-game UI.

**Usage**:

- Double-click to run the GUI (window title "Vape v4"): pick a Minecraft process and inject directly, no login needed
- Command-line injector: `Vape-v4.21.*.exe -nogui [pid]` — without a pid it opens the process picker; with a pid it injects directly
- After injection, press RIGHT SHIFT (default) in-game to open the module GUI

## Features

**GUI loader**

- Integrated VapeLoader GUI (GDI+ self-drawn, English by default): process selection / injection progress / load complete
- Removed the login page and cache prompt: it starts straight to process selection and injection, generating a token locally
- **External DLL support**: if `Vape-v4.21Native.dll` sits next to the exe it is injected preferentially; otherwise the embedded copy is extracted and injected — both modes need no extra files
- Window title "Vape v4", icon consistent with the product
- All dropped files are kept inside the hidden `.vapeclient` directory next to the exe (extracted DLL/JAR, logs, config, service data, texture cache)

**Injection**

- Uses **manual map** injection by default: the injector writes the DLL image into the target process and performs section mapping, relocations, import resolution and section protection itself before calling the entry point. The DLL is **never loaded through `LoadLibrary`**, so it does not appear in the Minecraft process module list (Process Explorer and `EnumProcessModules` cannot see it) and leaves no image-load trace
- **Automatic fallback**: if any manual-map step fails, injection falls back to the classic `CreateRemoteThread` + `LoadLibraryW` path so availability comes first. Both paths load the same DLL, so in-game behavior, performance and version compatibility are identical
- The verified upstream EXE and this branch's CI bundle use build-time randomized product class names. This does not establish any particular detection or anti-cheat outcome — see FAQ 10.

**Motion Blur (MotionBlur)**

- **HUD module "Motion Blur"** (Display/Game group, same area as block colored borders): frame-blend post-processing that produces a motion-trail afterimage based on view movement
- Tunable: blur strength (default 5, ×2 strength, cap 0.95), speed-adaptive (stronger with camera speed), soft blur, frame-rate modulation, grayscale trail, apply on menus / game menus
- Supports **1.17+ up to 26.1** Vanilla / Forge / NeoForge / Fabric runtimes (26.2's render pipeline has no end-of-frame hook, not yet supported)
- Render timing and GL state specially adapted: executed at the `RenderTarget.blitToScreen` exit (after the frame is presented, before swap) and syncs the game `GlStateManager` cache to avoid font/texture sampling glitches

**Feature integration**

- Embeds **VapeService** (HTTP 8080 + Zeus TCP 8091 companion service), auto-started in the background in-game:
  - Account / settings / profiles / friends / party / location sharing etc. run locally
  - Service data stored at `.vapeclient\vape-service.json` next to the exe, separated from the local config (`.vapeclient\config.json`) so they don't conflict
  - Port conflict auto-probes for a free port further up; silent fallback on startup failure, doesn't affect the game
  - Supports environment-variable configuration (see "Embedded service config" below)

**Localization**

- Language pack expanded to 2600+ keys, covering module names, value names, tooltips, tutorials, confirmation dialogs, potion/item names
- English is the default for a fresh install and for missing or invalid language settings. Explicitly saved Chinese remains supported; restart the loader after changing the in-game language.
- Module search matches both the English name and the Chinese translated name, so Chinese can find modules directly
- Category navigation shows the "Other" category, so Other-category modules (e.g. NoItemRelease) are browsable directly

**Fonts & display**

- `noto.ttf` is a static subset of Noto Sans SC covering all translated glyphs (SemiBold 600 weight)
- The injector console uses English and outputs UTF-8

**Engineering & stability**

- **26.2 graphics backend**: 26.2 introduces a Vulkan backend; Vape is based on OpenGL, so you must switch the graphics API to OpenGL to use it (see the compatibility notes below)
- Added 1.21.0+/26.x render-pipeline adaptation (`blitToScreen` end-of-frame hook) so post-processing like Motion Blur works across versions
- Enhanced runtime detection: distinguishes Vanilla / Forge / NeoForge / Fabric, avoiding old-Fabric misdetection (except 1.20.1-Fabric, see the compatibility table)
- Local config persistence: module settings, profiles, friends, frame positions saved to `.vapeclient\config.json`, autosave + exit fallback
- Native and Java logs unified under `.vapeclient\log\`, one log file per injection
- Single-file injector: `Vape-v4.21.*.exe` embeds the full DLL and Java payload

### This is not Vape's official source, the original release package, or a vendor-signed artifact, and does not guarantee behavior identical to the original product.

> This project is for software recovery, compatibility analysis and testing in your own environment. Only use it in an isolated instance you own and are permitted to test, and confirm local laws, licenses and server rules yourself.

## Script extensions

The client embeds Mozilla Rhino 1.7.15 (relocated to `gg.vape.shaded.org.mozilla`, interpreted, no bytecode generation) and loads custom module scripts from `.vapeclient\scripts\`:

```text
.vapeclient/
  scripts/
    example.json      module descriptor (required)
    example.js        script entry (defaults to the json name; override with main)
    mylib.js          extra scripts, pulled in with load("mylib.js")
    data/             storage persistence (one json per script)
  extension.log       script load / run / error log
```

- `example.json` + `example.js` are generated on first launch; the example template is rewritten when a newer version ships
- Script capabilities: read/write modules and settings (including color and keybind), player and entity queries, screen drawing, callbacks `onEnable` / `onDisable` / `onTick` / `onRender2D` / `onKeyPress` / `onChat` / `onAttack` / `onMove` / `onWorldChange` / `onPacketSend` / `onPacketReceive`, `storage` persistence, `after()` timers and multi-file `load()`
- The "Reload Scripts" module hot-reloads: recompiles existing scripts, registers newly added json files and disables modules whose json was removed
- Load or runtime errors show an in-game notification and are written to `.vapeclient\extension.log`

Full developer documentation (json fields, every API, examples, version whitelists, limitations and security) lives in [`docs/scripting.md`](docs/scripting.md).

> ⚠️ **Security warning: scripts are not sandboxed.** The current build loads scripts with Rhino's full standard object set, so a script can reach `Packages.java.*` — equivalent to running arbitrary Java code inside the Minecraft process (i.e. with your current Windows user privileges): spawning processes, reading or writing any file, loading native libraries. **Only run scripts you wrote yourself or fully trust**; a third-party script downloaded from the internet is the same as running a program of unknown origin.

## Minecraft compatibility

| Minecraft | Vanilla | Forge | Fabric |
| --- | :---: | :---: | :---: |
| 1.7.10 | ✓ | ✓ | - |
| 1.8.9 | ✓ | ✓ | - |
| 1.12.2 | ✓ | ✓ | - |
| 1.16.5 | △ Experimental | - | - |
| 1.20.1 | △ Experimental | △ Experimental | - |
| 1.21.1 | △ Experimental | △ Experimental | - |
| 1.21.10 | △ Experimental | △ Experimental | - |
| 1.21.11 | ✓ | ✓ | ✓ |
| 26.1.2 | ✓ | ✓ | ✓ |
| 26.2 | △ Experimental | △ Experimental | △ Experimental |

**1.21.10 is an experimental port on Forge 60.x, NeoForge and Vanilla alike - features may be incomplete (NeoForge shares the Forge row); Fabric is not supported**.

**1.16.5 is an experimental adaptation and may have the following issues**:

- Some mappings, rendering and module functionality may not work correctly
- Vanilla 1.16.5 only exposes the getInstance() singleton (no static instance field); some static field mappings may be missing
- If it crashes, please report the log

**1.20.1 / 1.21.1 are experimental adaptations and may have the following issues**:

- **Fabric runtimes for 1.20.1 / 1.21.1 are unsupported** (Fabric Knot class-loading isolation + slf4j conflict); use the corresponding Forge / NeoForge versions
- Some HUD overlays (e.g. health overlay) may be positioned incorrectly (offset, e.g. appear bottom-right)
- If it crashes, please report the log

## Known hard-to-fix issues

4.21.41 already fixed the 1.20.1 wiring problems (projection-matrix mapping, blend state, draw order): **ESP 2D/3D boxes, Tracers, arrow direction and the GUI black background are back**.
The issues below remain; they are mainly caused by **1.20.1's mappings differing greatly from the code's target versions (1.21.x / 26.x)** (text-component construction, block-entity tables, model getters etc.), so they are **hard to fix and not yet resolved**:

**1.20.1 (Forge / NeoForge)**
- **World text is missing**: text inside item label boxes, NameTags, and the distance text of Indicators and Arrows. The text-component construction bridge cannot be resolved on 1.20.1, so the code now simply skips the text (no exception thrown) until it is rebuilt as screen-space 2D drawing
- **StorageESP (chest ESP) and Search do not show**: the block-entity table that container ESP relies on cannot be resolved on 1.20.1, and the render exception is silently swallowed
- **Potion icons / armor (equipment) icons not showing**: `getBakedModel` / `getSpriteTexture` mappings fail on 1.20.1; currently handled by a graceful skip (no crash, no lag, but no display)

**26.2 is an experimental adaptation and may have the following issues**:

- The first notification after injection ("Press RIGHT SHIFT to open the GUI") text may render as boxes; the font recovers after opening the ClickGUI
- On 26.2 (Fabric) RT ray-tracing render pipeline, some render hooks may be unstable
- Some HUD overlays / render elements may be slightly offset
- If it crashes, please report the log

**26.2 must use the OpenGL graphics backend**:

- 26.2 introduces the Vulkan backend for the first time. Vape is based on OpenGL rendering, **it does not work under the Vulkan backend**: GL initialization triggers a fatal JVM error ("No context is current") at injection, or the GUI cannot open after injection
- Switch "Graphics API" to **OpenGL** in video settings (or edit `options.txt` in the version directory, setting `preferredGraphicsBackend` to `"opengl"`), then restart the game and inject
- 26.1.2 and earlier have no such option and are unaffected

**For 26.1.2 and 26.2, inject after entering a server or single-player world.**

All target instances must use a 64-bit JVM.

## FAQ

### 1. The loader does not list the game

The loader finds the game through the Java process and its visible window title, so only processes whose window title contains `Minecraft`, `Lunar` or `Feather` are listed.

Wait until the game reaches the main menu, make sure it runs on a 64-bit JVM, and double-check the selected PID, game version and runtime. Supported: 1.7.10, 1.8.9, 1.12.2, 1.21.11 and 26.2 Vanilla, 1.21.10 Forge / NeoForge (all loaders on this version are experimental), plus 1.21.11 / 26.2 Fabric. Do not inject into similarly titled launcher helper JVMs or other Java programs.

### 2. The loader hangs after the game process is picked

If that Minecraft process has already been injected with Vape, injecting into the same process a second time always hangs, whether or not the first Vape load succeeded.

If the first injection failed, restart Minecraft and inject again.

### 3. The loader reports "Vape421Native.dll was not found beside the Loader"

That message refers to `Vape-v4.21Native.dll` in the loader's directory (the optional external DLL described under "Main artifact"): the DLL must sit in the same directory as the loader.

### 4. The loader reports "Product DLL did not acknowledge…" or "Unexpected…"

Vape 4.21 supports 1.7.10, 1.8.9, 1.12.2, 1.21.11 and 26.2 Vanilla, 1.21.10 Forge / NeoForge (all loaders on this version are experimental), plus 1.21.11 / 26.2 Fabric; other Fabric versions, and 1.21.10 Fabric, are outside the current support scope.

This status means the injection failed: check that Minecraft is a supported version, restart the game and inject again. If the problem persists, inspect `vape421-native.log` and report the issue.

### 5. The loader shows "Finished Loading", but the in-game "Finished Loading" notification never appears

This means the injection failed: check that Minecraft is a supported version, restart the game and inject again. If the problem persists, inspect `vape421-native.log` and report the issue.

### 6. An injection error code appears, or field / method mappings fail or the client crashes

Check that Minecraft is a supported version, restart the game and inject again. If the problem persists, inspect `vape421-native.log` and report the issue.

### 7. The ClickGUI opens but cannot be clicked, the cursor is misplaced or keys get stuck

Open an issue and describe in detail when the problem appears and how to reproduce it.

### 8. A Render module causes OpenGL 1283, a vanishing HUD, corrupted visuals or a crash

Compatibility for Minecraft 1.16.5 and other unlisted 1.16+ versions is still poor. Use these verified combinations:

- 1.21.11 Forge: Forge 61.0.8 (verified)
- 1.21.11 Fabric: Fabric Loader 0.19.3
- 26.2 Forge: Forge 65.1.0 (verified)
- 26.2 Fabric: Fabric Loader 0.19.3

### 9. Configs disappear, saving fails, or friend / party state is wrong

VapeService stores accounts and configs in a single local JSON file, and the data path is relative to the working directory — starting from another directory looks like a brand-new account. A corrupted file, missing write permission, a full disk, several Service instances running at once or an abnormal exit can all affect saving. A new Zeus connection for the same account replaces the old one, and high-frequency online state does not recover by itself after a Service restart, system sleep or network switch.

Always pass an explicit `--data-file` path and keep regular backups, and never let two Service instances write the same file. The Service is an experimental compatibility layer: some public config, comment, report, tag and pagination behaviour is simplified, so a successful connection does not mean every original Online feature works.

### 10. Are the product class names randomized?

The supplied v4.21.58 EXE has randomized product class names, and this branch's CI bundle preserves that packaging. The build renames owned `gg.vape.*` classes to random 16-character names, except the three classes hard-coded by the native layer. Each build differs; `build/rename-mapping.txt` maps the names for crash-log analysis. Passing `-PnoObfuscation` disables this for a diagnostic build. The CI parity report compares classes after reversing the randomized names; it does not establish identical runtime behavior or detection characteristics.

## Embedded service config

VapeService auto-starts in-game, listening on `127.0.0.1:8080` (HTTP) and `127.0.0.1:8091` (Zeus TCP) by default. Configurable via environment variables:

| Env var | Default | Description |
| --- | --- | --- |
| `VAPE_BIND_ADDRESS` | `127.0.0.1` | Bind address; set to `0.0.0.0` to allow LAN access |
| `VAPE_HTTP_PORT` | `8080` | HTTP port |
| `VAPE_ZEUS_PORT` | `8091` | Zeus TCP port |
| `VAPE_DATA_FILE` | `<exe>/.vapeclient/vape-service.json` | Service data file path |

The client side already has `VAPE_ONLINE_BASE_URL` / `VAPE_ZEUS_ADDRESS` to override the service address, which combine with the variables above for LAN multi-end interop.

## Requirements

Only compiling and validating the Java layer needs:

- JDK 17, used as the Gradle toolchain; output is compiled to Java 17 bytecode by default, pass `-PtargetRelease=8` for Java 8 bytecode (used by CI builds)
- The project's bundled Gradle Wrapper; the build scripts require Gradle 8.8
- Network access to Maven Central and the Gradle Plugin Portal

Building the native bundle additionally needs:

- Windows x64
- Visual Studio 2022 C++ x64 toolchain and Windows SDK
- CMake 3.21 or later
- A JDK with JNI/JVMTI headers; JDK 8 is recommended when testing against 1.7.10, 1.8.9 and 1.12.2

## Quick start

From the repository root in PowerShell:

```powershell
.\gradlew.bat clean build verifyInjectionPayload -PtargetRelease=8
```

This command:

1. Compiles the recovered source and processes all resources.
2. Checks the source count and residual fatal CFR decompilation markers.
3. Builds the injection JAR with runtime dependencies.
4. Checks for necessary packages and class-file versions compatible with Java 8; this does not prove runtime loading.

Main Java artifacts are in `build/libs/`. To generate IntelliJ IDEA project config, run:

```powershell
.\gradlew.bat idea
```

## Build the native test bundle

```powershell
.\gradlew.bat prepareInjectionBundle -PtargetRelease=8 `
  -PnativeJavaHome="C:\Program Files\Java\jdk1.8.0_301"
```

The complete test bundle outputs to `build/injection/` (the filename varies with the project version, e.g. `Vape-v4.21.*.exe`):

```text
Vape-v4.21.*.exe   GUI single-file loader (embeds the DLL and all resources)
Vape-v4.21Native.dll   optional matching external DLL
README.md / README_EN.md   native bundle documentation
```

The DLL embeds the Java injection JAR as an `RCDATA` resource, so a separate payload is not required. The native bridge layer recovers the sample's `RegisterNatives` interface table and additionally registers the sample's unimplemented native declarations as safe stubs to avoid `UnsatisfiedLinkError`. See [`native/README_EN.md`](native/README_EN.md) for details.

## GitHub Actions build

Once these changes are pushed to the working branch, open **Actions → Build and
test → Run workflow**, select that branch and run it (enable Actions on the fork
if needed). The successful run publishes `windows-x64-english-bundle` as an
artifact for 14 days. It contains the rebuilt EXE, a matching optional DLL and
`SHA256SUMS.txt`; this is not the original signed release. The workflow also runs on pull requests
and pushes to `arena/**`.

## Running in an isolated environment

Start a supported Minecraft instance using a 64-bit JVM (including 1.21.11, 26.1.2, 26.2 Fabric), then run `Vape-v4.21.*.exe` to open the GUI, select the Minecraft process, and inject (no login, no external DLL).

You can also inject from the command line:

```powershell
# Inject a specific process ID
.\Vape-v4.21.*.exe -nogui <pid>
# Without a pid: opens an auto-refreshing Java window picker (↑/↓ to select, Enter to inject, Esc to quit)
.\Vape-v4.21.*.exe -nogui
```

The GUI tries manual mapping first and falls back to `LoadLibraryW`; console mode uses `LoadLibraryW`. Once loaded the DLL waits for the JVM and Minecraft `Client thread`, then loads the embedded JAR through its context ClassLoader; on Fabric it adds the payload to the Knot ClassLoader via the Fabric Launcher API. The DLL then registers the native methods and calls `gg.vape.runtime.NativeBridge.start()`. Each injection log is at `.vapeclient\log\vape421-native-<pid>-<timestamp>.log` next to the injector EXE.

## Common validation tasks

| Command | Purpose |
| --- | --- |
| `.\gradlew.bat check` | Compile, source-coverage and recovery-quality checks |
| `.\gradlew.bat injectionJar` | Build the self-contained Java injection payload |
| `.\gradlew.bat verifyInjectionPayload -PtargetRelease=8` | Check dependency completeness, localization and Java 8 bytecode version |
| `.\gradlew.bat buildNative` | Build the x64 DLL and injector |
| `.\gradlew.bat prepareInjectionBundle` | Produce the native bundle for isolated testing |

## License

This repository is provided under [CC0 1.0 Universal](LICENSE). To the extent applicable, CC0 only covers what the repository contributors have the right to dispose of; third-party libraries, trademarks, fonts, textures and other pre-existing material remain subject to their respective rights.

## Changelog

See [CHANGELOG.md](CHANGELOG.md).
