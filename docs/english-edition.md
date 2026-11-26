# English edition: source analysis

The supplied release is `Vape-v4.21.58.exe` (the supplied SHA-256 is recorded in the [README](../README.md)). This checkout's `build.gradle` also identifies version 4.21.58, and the upstream tag points to source commit `36aa9fb4890b2845de3452f008a334d0d08b774f`. The local sandbox could not download the release binary; Windows CI downloads it for hash verification and offline inspection. Consult the report from a successful run for the results. These changes are based on recovered source, not a claim that the English build is byte-for-byte identical to the release.

## How the project is assembled

| Component | Relevant source | Role |
| --- | --- | --- |
| Windows GUI EXE | `native/loader_gui/`, `native/injector.c` | Selects a game process, displays progress and supports the `-nogui` console. |
| Native DLL | `native/dllmain.c`, `native/native_bridge.c` | Bridges the loader and the recovered Java client. |
| Java payload | `src/main/java/`, `src/main/resources/` | Implements the in-game UI, modules, settings and translations. |
| Build | `build.gradle`, `native/CMakeLists.txt` | Builds the payload and embeds it in the DLL, then embeds the DLL in the EXE. |

The DLL carries the Java JAR as resource **421** (`native/payload.rc.in`); the EXE carries the DLL as resource **422** (`native/embed_dll.cmake`). GUI and console modes use an adjacent DLL only when it matches resource 422, and reverify it under a read lock before injection. The remaining trust and filesystem limitations are documented in the [security investigation](security-investigation.md).

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

## Module changes

The rotation updates are enabled by default and intentionally differ from the original. No setting names, identifiers or saved values were removed or renamed. Existing profiles still control their configured modes and speeds; the optional off-grid jitter settings remain available and default to zero.

- The shared rotation controller limits catch-up work after a long frame, reducing the chance of replaying a pause as one large turn. This affects modules that use that controller, not every module in the client.
- AimAssist Simple resets motion history on a target switch, synchronizes worker/render access and discards stale input after a long render pause. Adaptive limits worker integration frequency and rounds its high-speed snap relative to the current view.
- AimAssist Kiss uses interpolated positions, bounded prediction from actual previous samples, a stable spring step and sensitivity-sized **relative** changes. Target loss or mode changes reset the relevant tracking state; Old mode retains fractional movement.
- SilentAura retains fractional AI/Kiss movement between ticks and resets it on target or mode changes. Kiss target jitter now tracks the underlying entity rather than short-lived wrapper objects.
- AutoMace ranks reachable targets, resets its rotation controller on target or silent-mode changes, and revalidates queued attacks and slot state. A stun/slam follow-up now waits for a local axe post-attack event; that event does not establish a server-side hit or shield break. AutoMace-owned synthetic swaps are guarded against re-swapping by HitSwap and ShieldBreaker. AimAssist exposes and clears its current target more consistently.
- Triggerbot tracks crosshair changes while cooldown gates are active. HitSwap respects already-canceled inputs, revalidates its target, player, world and selected item, restores the prior slot when appropriate, and does not advance a stun/slam sequence on a rejected synthetic attack. On 26.2, all three revalidate deferred attack requests when the tick executor actually processes them.
- Modern Search shares a coordinate index between nearby and full-range scans, invalidates stale worlds and cave filters, floors negative coordinates and avoids recycling published results. Older chunk scanning also avoids recycling results still visible to renderers.

These changes can alter aim timing and accuracy, especially during lag, at very low frame rates or when settings use high speeds. Back up `.vapeclient` before testing, and first try the desired settings in a single-player world or other authorized test environment. Minecraft 26.2/Fabric is marked experimental in the [compatibility notes](../README_EN.md#minecraft-compatibility): the project requires the OpenGL backend and injection after entering a world. This branch has **not** been tested in Minecraft 26.2/Fabric, against a server anti-cheat, or against visual observation. No detection or ban avoidance is guaranteed.

## Verification and limits

`Vape421LoaderLanguageTest` covers the portable JSON selection logic, and `Vape421LoaderStringsTest` covers the Win32 loader's file-based selection and table contents. Native tests also cover resource comparison, locking and non-destructive materialization. `verifyLocalization` validates the English resource strings and paired dynamic translations; `verifyFontCoverage` checks the Chinese font. Windows CI builds the Java 8-compatible payload and native bundle and runs CTest. Deterministic rotation, attack-controller, combat and Search tests use small Minecraft stubs. AutoMace tests exercise its event handlers, deferred attacks and target selection in a synthetic world, as well as its rotation and attack gate. These simplified stubs do not exercise actual Minecraft physics, mappings, rendering, packets or injection.

The module-parity audit compares the pinned source with this branch, including all 460 baseline Java files in the module tree. It reverses the two localization-only changes in `ClientSettings` and `SpawnerFinder`, and pins reviewed module changes, supporting changes and added classes by content. Other module sources must match the baseline. CI rebuilds the baseline with the same build script, JDK and flags, checks class inventories and embedded resources, and inspects the hash-verified upstream EXE as data. An offline auditor reverses randomized class names and compares normalized class data. CI fails on incomplete mapping, an original/baseline mismatch, unreviewed changes or a randomized embedded module that differs from the current source build. Results are placed in `MODULE-PARITY-REPORT.txt`.

These are static and headless checks, not proof of identical runtime behavior. No Minecraft game session, end-to-end injection or module-by-module gameplay test has been performed in this environment. An authorized runtime test on the intended Minecraft version is still required. Rebuilt binaries will not have the upstream release's hash or signature.
