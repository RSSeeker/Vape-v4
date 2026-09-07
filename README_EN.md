[English](README_EN.md) | [简体中文](README.md)

# Vape 4.21 Product Recovery

A research-oriented recovery project for the Vape 4.21 Java layer and Windows x64 native bridge layer, with full Chinese localization.

> GitHub repository: [RSSeeker/Vape-v4.21](https://github.com/RSSeeker/Vape-v4.21) ·
> Releases: [Releases](https://github.com/RSSeeker/Vape-v4.21/releases)
>
> Source code origin: [OpenVapeCN/OpenVape](https://github.com/OpenVapeCN/OpenVape)
> (This project recovers, reorganizes and localizes the source code from that public repository).

## Main Artifacts

| File | Description |
| --- | --- |
| `Vape-v4.21.30.exe` | GUI single-file loader (embeds the complete DLL, Java payload and icon); filename varies with the version (e.g. `Vape-v4.21.30.exe`) |

**Optional external DLL**: if `Vape-v4.21Native.dll` sits next to the exe, it is loaded preferentially (handy for replacing/updating the native layer yourself); otherwise the embedded copy is extracted, so no extra files are required.

**Usage**:

- Double-click to run the GUI (window title "Vape v4"): pick a Minecraft process and inject directly, no login needed
- Command-line injector: `Vape-v4.21.30.exe -nogui [pid]` — without a pid it opens the process picker; with a pid it injects directly
- After injection, press RIGHT SHIFT (default) in-game to open the module GUI

## Features

**GUI loader (v4.21.9+)**

- Integrated the upstream VapeLoader graphical UI (GDI+, fully Chinese): process selection / injection progress / loading complete
- Removed the login page and cache prompt: start directly into process selection, token generated locally
- **External DLL support**: if `Vape-v4.21Native.dll` exists next to the exe it is injected preferentially; otherwise the DLL is extracted from the exe resource — both modes need no extra files
- Window title "Vape v4", icon matches the product
- All runtime artifacts are kept inside the hidden `<exe>\.vapeclient` folder (extracted DLL/JAR, logs, config, service data, texture cache); **nothing is ever written to %TEMP%**

**Motion Blur (v4.21.20+)**

- **HUD module "Motion Blur"** (Game group, same area as the block overlay): frame-blending post-processing that leaves a motion trail as the camera moves
- Options: blur strength (default 5, doubled, capped at 0.95), velocity adaptive (scales with camera movement), smooth blur, FPS modulate, faded grayscale trail, apply on Vape menu / game menu
- Works on **1.17+ up to 26.1** across Vanilla / Forge / NeoForge / Fabric runtimes (26.2's render pipeline has no end-of-frame hook, so it is unsupported there)
- Rendering timing and GL state are specially adapted: it runs at the `RenderTarget.blitToScreen` exit (after the frame is presented to the screen, before swap) and synchronizes the game's `GlStateManager` cache so font/texture sampling is not corrupted

**Feature integration (v4.21.8)**

- Merged upstream modules: **AutoMace** (mace selection / stun slam / aim range / auto unequip Elytra / smash only / show hotbar), **NoItemRelease**, **PearlCatch**, **InventoryOverlay**
- Merged the Badlion legacy keybinding event queue for Badlion client compatibility
- Embedded **VapeService** (HTTP 8080 + Zeus TCP 8091 companion service), auto-started in-game in the background:
  - Account / settings / profiles / friends / party / location sharing online features run locally
  - Service data stored in `<exe>\.vapeclient\vape-service.json`, separate from the local config (`.vapeclient\config.json`), no conflicts
  - Port conflicts are resolved by probing upward for a free port; startup failures degrade silently without affecting the game
  - Configurable via environment variables (see "Embedded Service Configuration" below)

**Localization**

- Language pack expanded to 2600+ keys covering module names, value names, tooltips, tutorials, dialogs, potion/item names, etc.
- Chinese is the default language; the language option is trimmed to "中文 / English"
- Fixed multi-line tooltip newline flattening and lost `§` color codes that broke exact matching
- Fixed runtime-composed strings in dropdowns and the target filter (whole-string lookup first, per-part translation fallback)
- Module search matches both the English name and the translated name, so modules can be found by Chinese directly
- The "Other" category is shown in the category navigation, so Other-category modules (e.g. NoItemRelease) can be browsed directly

**Fonts & visuals**

- `noto.ttf` is a static Noto Sans SC subset (SemiBold 600) covering every translated character, verified with zero missing glyphs via stb (the actual in-game rendering engine)
- Custom rounded-corner icon embedded into `Vape-v4.21.exe`
- Chinese-localized injector console with UTF-8 output

**Engineering & stability**

- **26.2 graphics backend**: 26.2 introduces the Vulkan backend; Vape is OpenGL-based, so switch the graphics API to OpenGL before use (see compatibility notes below)
- Render-pipeline adaptation for 1.21.0+ / 26.x (end-of-frame `blitToScreen` hook) so post-processing like Motion Blur works across versions
- Enhanced runtime detection distinguishing Vanilla / Forge / NeoForge / Fabric, avoiding false matches on older Fabric runtimes (except 1.20.1-Fabric, see the compatibility table)
- Local configuration persistence: module settings, profiles, friends and frame positions saved to `.vapeclient\config.json`, auto-saved plus shutdown fallback
- Native and Java logs unified under `.vapeclient\log\`, with a new log file per injection
- Single-file injector: `Vape-v4.21.exe` embeds the complete DLL and Java payload

### It is NOT official Vape source code, an original release package, or a vendor-signed artifact, and it does not guarantee behavior identical to the original product.

> This project is intended for software recovery, compatibility analysis, and testing in self-owned environments. It should only be used in isolated instances that you own and are authorized to test, and you are responsible for verifying local laws, software licenses, and server rules.

## Minecraft Compatibility

| Minecraft | Vanilla | Forge | Fabric |
| --- | :---: | :---: | :---: |
| 1.7.10 | ✓ | ✓ | - |
| 1.8.9 | ✓ | ✓ | - |
| 1.12.2 | ✓ | ✓ | - |
| 1.16.5 | △ Experimental | - | - |
| 1.20.1 | △ Experimental | △ Experimental | - |
| 1.21.1 | △ Experimental | △ Experimental | - |
| 1.21.11 | ✓ | ✓ | ✓ |
| 26.1.2 | ✓ | ✓ | ✓ |
| 26.2 | △ Experimental | △ Experimental | △ Experimental |

Injection into Lunar Client and Badlion Client 1.8.9 instances is also supported.

Support for Minecraft 1.16.5 is poor; certain mappings, rendering, and module features may not function properly.

**1.20.1 and 1.21.1 are experimental adaptations and may have the following issues**:

- Some HUD overlays (e.g. the health overlay) may be positioned incorrectly
  (rendered offset, such as at the bottom-right corner)
- If smooth font initialization fails, rendering falls back to the legacy font,
  which can occasionally cause black edges around the GUI
- If you hit a crash, please report the log.

**26.2 is an experimental adaptation and may have the following issues**:

- The first notification after injection ("Press RSHIFT to open GUI") may render
  as boxes until the ClickGUI is opened (the Minecraft font bridge is not fully
  ready before the GUI pass on 26.x)
- GUI/HUD layering differs from the official client: HUD modules render below
  the game HUD, the ClickGUI covers the game HUD
- On 26.2 (Fabric) with the RT ray-tracing pipeline, some render hooks may be
  unstable
- If you hit a crash, please report the log.

**26.2 requires the OpenGL graphics backend**:

- 26.2 is the first version to introduce the Vulkan graphics backend. Vape is
  built on the OpenGL rendering pipeline and **cannot work under the Vulkan
  backend**: GL initialization during injection triggers a JVM fatal error
  ("No context is current"), or the GUI fails to open after injection
- In Video Settings, switch the "Graphics API" to **OpenGL** (or edit
  `options.txt` in the version folder and set `preferredGraphicsBackend` to
  `"opengl"`), then restart the game before injecting
- 26.1.2 and earlier are unaffected (no such option)

**For versions 26.1.2 and 26.2, please inject after joining a server or singleplayer world.**

All target instances must use a 64-bit JVM.

## Embedded Service Configuration

VapeService auto-starts in-game, listening on `127.0.0.1:8080` (HTTP) and `127.0.0.1:8091` (Zeus TCP) by default. It can be adjusted via environment variables:

| Environment variable | Default | Description |
| --- | --- | --- |
| `VAPE_BIND_ADDRESS` | `127.0.0.1` | Bind address; set to `0.0.0.0` to allow LAN access |
| `VAPE_HTTP_PORT` | `8080` | HTTP port |
| `VAPE_ZEUS_PORT` | `8091` | Zeus TCP port |
| `VAPE_DATA_FILE` | `<exe>/.vapeclient/vape-service.json` | Service data file path |

On the client side, `VAPE_ONLINE_BASE_URL` / `VAPE_ZEUS_ADDRESS` already override the service address; combined with the variables above, this enables LAN multi-client interoperation.

## Requirements

Required only for compiling and verifying the Java layer:

- JDK 17, used as the Gradle toolchain; the output defaults to Java 17 bytecode, and passing `-PtargetRelease=8` produces Java 8 bytecode (as the CI build does)
- Project-bundled Gradle Wrapper; build script strictly requires Gradle 8.8
- Internet connection with access to Maven Central and Gradle Plugin Portal

Required for building the native bundle:

- Windows x64
- Visual Studio 2022 C++ x64 toolchain and Windows SDK
- CMake 3.21 or higher
- A JDK containing JNI/JVMTI headers; JDK 8 is recommended when testing against 1.7.10, 1.8.9, and 1.12.2

## Quick Start

In PowerShell, navigate to the repository root directory:

```powershell
.\gradlew.bat clean build verifyInjectionPayload
```

This command performs the following tasks:

1. Compiles the recovered source code and processes all resources.
2. Checks source count and remaining fatal CFR decompilation markers.
3. Generates the injection JAR containing runtime dependencies.
4. Confirms that the payload includes necessary packages and that all classes can be loaded by Java 8.

Main Java artifacts are located in `build/libs/`. To generate IntelliJ IDEA project configurations, run:

```powershell
.\gradlew.bat idea
```

## Building Native Test Bundle

```powershell
.\gradlew.bat prepareInjectionBundle -PtargetRelease=8 `
  -PnativeJavaHome="C:\Program Files\Java\jdk1.8.0_301"
```

The complete test bundle outputs to `build/injection/` (the filename varies with the project version, e.g. `Vape-v4.21.30.exe`):

```text
Vape-v4.21.30.exe   GUI single-file loader (embeds the DLL and all resources)
README.md
```

The DLL embeds the Java injection JAR as an `RCDATA` resource, so placing a payload separately is not required. The native bridge layer recovers the sample's `RegisterNatives` interface table and additionally registers the sample's unimplemented native declarations as safe stubs to avoid `UnsatisfiedLinkError`. For more details, see [`native/README.md`](native/README.md).

## Running in an Isolated Environment

After launching a supported Minecraft instance (including 1.21.11, 26.1.2, and 26.2 Fabric) or Lunar Client instance using a 64-bit JVM, run `Vape-v4.21.exe` to open the GUI, pick the Minecraft process and click to inject (no login, no external DLL).

You can also inject from the command line:

```powershell
# Inject a specific process ID
.\Vape-v4.21.exe -nogui <pid>
# Without a pid: opens the auto-refreshing Java window picker (↑/↓ to select, Enter to inject, Esc to quit)
.\Vape-v4.21.exe -nogui
```

The injector only performs `LoadLibraryW`. Once loaded, the DLL waits for the JVM and Minecraft `Client thread`, and loads the embedded JAR via its context ClassLoader. Fabric instances add the payload to the Knot ClassLoader via the Fabric Launcher API. Subsequently, the DLL registers the native bridge methods and calls `gg.vape.runtime.NativeBridge.start()`. A new per-injection `vape421-native-<pid>-<timestamp>.log` is written under `.vapeclient\log\`.

## Common Verification Tasks

| Command | Purpose |
| --- | --- |
| `.\gradlew.bat check` | Compile, source coverage, and recovery quality verification |
| `.\gradlew.bat injectionJar` | Build self-contained Java injection payload |
| `.\gradlew.bat verifyInjectionPayload` | Verify dependency integrity and Java 8 bytecode version |
| `.\gradlew.bat buildNative` | Build x64 DLL and injector |
| `.\gradlew.bat prepareInjectionBundle` | Assemble native bundle ready for isolated testing |

## License

This repository is provided under [CC0 1.0 Universal](LICENSE). To the extent applicable, CC0 covers only content that repository contributors have the right to dispose of; third-party libraries, trademarks, fonts, textures, and other existing materials remain subject to their respective rights.

## Changelog

See [CHANGELOG.md](CHANGELOG.md).
