# InevitableClient V1

InevitableClient is an independent, source-based rebrand of the recovered Vape 4.21.58 Windows client. It is not the original release or an official Vape product. English is the default; Chinese and legacy settings remain supported. Use it only with a valid license, in environments where you have permission, and in accordance with applicable laws and server rules.

- [Detailed English guide](README_EN.md) · [中文文档](README_ZH.md) · [Native build](native/README_EN.md)
- [InevitableClient V1 notes](docs/releases/InevitableClientV1.md) · [Earlier changes and test limits](docs/english-edition.md) · [Security investigation](docs/security-investigation.md)

## Interface

The main click GUI uses six dark panels: Combat, Movement, Visual, Utility, Profiles and Settings. Left-click a module to toggle it, use its settings control to expand its actual values, and drag a panel by its header. Ctrl-F opens the classic search temporarily; Settings provides classic tools and the HUD editor. Profiles and module values use the existing configuration system. The new interface has not yet been visually or functionally verified in Minecraft.

## Files and existing data

The Windows x64 bundle contains `InevitableClientV1.exe` and the optional matching `InevitableClientNative.dll`. The EXE embeds the DLL, which embeds the Java payload. An adjacent DLL is used only if it matches the embedded copy byte-for-byte; otherwise a copy is extracted without overwriting existing recovery files. This check does **not** establish trust in either file.

Next to the EXE, an existing `.inevitable` folder wins; otherwise an existing `.soulbound` folder is used, then an existing `.vapeclient` folder. A fresh install uses `.inevitable`. No tree is moved or deleted. Inside the selected folder, service data prefers `inevitable-service.json`, then `soulbound-service.json`, then `vape-service.json`; the image cache reuses `vapeTextures` when `texture-cache` is absent. Back up your data before testing. Legacy identifiers such as `gg.vape.*`, `VAPE_*`, profile `4.21` values and JNI/resource IDs are intentionally preserved.

English is used when no valid language selection is saved; an explicitly saved Chinese selection remains Chinese. Change it through **Settings → General → Language** and restart the loader.

## Build and verify

Requirements: Windows x64, Visual Studio 2022 C++ x64 and Windows SDK, CMake 3.21+, JDK 17 for Gradle, JDK 8 headers, and access to Gradle/Maven repositories. In PowerShell:

```powershell
$jdk8 = 'C:\Program Files\Java\jdk1.8.0_301'  # adjust to your installation
.\gradlew.bat clean build verifyInjectionPayload -PtargetRelease=8 -PnoObfuscation
.\gradlew.bat prepareInjectionBundle -PtargetRelease=8 "-PnativeJavaHome=$jdk8"
ctest --test-dir build/native -C Release --output-on-failure
```

The default bundle is in `build\injection\`. The release build randomizes product class names; CI compares embedded classes with the source build after name normalization. Builds are unsigned unless a certificate is supplied. Run only in an authorized, isolated environment; the [compatibility notes](README_EN.md#minecraft-compatibility) include known mapping and display issues.

**Actions → Build and test** builds the Windows bundle, runs Java/native and parity checks, then scans that run's EXE and DLL as data with ClamAV. Only a completed successful run provides the `windows-x64-inevitable-bundle` and `rebuilt-bundle-scan-report` artifacts for 14 days. Review `SHA256SUMS.txt` and `MODULE-PARITY-REPORT.txt`; a single scan is not proof of safety. No live gameplay or anti-cheat testing has been performed for this rebrand.

## Versioning and releases

`gradle.properties` contains the shared `inevitableVersion`: `1`, `1.1`, `1.2`, etc. It controls the visible version, JAR metadata, executable name and tag. To prepare a later version:

```powershell
python tools/bump_inevitable_version.py 1.1
# Add docs/releases/InevitableClientV1.1.md, review changes, commit and run full CI.
```

Tag the **exact commit** only after its entire CI run, including the scan, succeeds. Pushing a product tag such as `InevitableClientV1.1` starts **Publish tested InevitableClient release**, which locates a successful CI run for that commit. Manual dispatch may select a specific successful run. The release workflow publishes those already-built artifacts after checking versions, embedded files, checksums and scanned hashes; it does not rebuild or overwrite an existing release. See the [release notes](docs/releases/InevitableClientV1.md) for limitations.

## Security and original checksum

An earlier build was reported to VirusTotal; the report and detection labels remain unavailable. A separate ClamAV scan found no signatures in that earlier sample, but did not explain the report or establish safety. Keep flagged files quarantined and do not add antivirus exclusions pending independent investigation. See the [security investigation](docs/security-investigation.md); scripts also have [no security sandbox](README_EN.md#script-extensions).

The supplied SHA-256 for the **original**, different `Vape-v4.21.58.exe` is `9b77e8ae1bddb6296e5d773d19ecada341fe93ac8ef39559bddcca7a4b945482`. CI downloads it only for hash verification and offline parity inspection; it is not a source-build input. Its hash does not apply to InevitableClient.
