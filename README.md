# Soulbound V1

Soulbound is an independent, source-based rebrand of the recovered Vape 4.21.58 Windows client. English is the default; Chinese and saved legacy settings remain supported. It is not the original release or an official Vape product. Use it only with a valid license, in environments where you have permission, and in accordance with applicable laws and server rules.

- [Detailed English guide](README_EN.md) · [中文文档](README_ZH.md) · [Native build](native/README_EN.md)
- [Changes and test limits](docs/english-edition.md) · [Security investigation](docs/security-investigation.md) · [V1 release notes](docs/releases/SoulboundV1.md)

## Files and existing data

The Windows x64 bundle contains `SoulboundV1.exe` and the optional matching `SoulboundNative.dll`. The EXE embeds the DLL, which embeds the Java payload. An adjacent DLL is used only if it matches the embedded copy byte-for-byte; otherwise a copy is extracted without overwriting existing recovery files. This check does **not** establish trust in either file.

Fresh installs use `.soulbound` next to the EXE. If only `.vapeclient` exists, Soulbound keeps using it; if both exist, `.soulbound` wins. No existing tree is moved or deleted. Inside the chosen folder, the embedded service reuses `vape-service.json` if no `soulbound-service.json` exists, and the image cache reuses `vapeTextures` if no `texture-cache` exists. Back up your data before testing. Legacy identifiers such as `gg.vape.*`, `VAPE_*`, profile `4.21` values and JNI/resource IDs are intentionally preserved for compatibility.

English is used when no valid language selection is saved; an explicitly saved Chinese selection remains Chinese. Change it through **Settings → General → Language** and restart the loader.

## Build and verify

Requirements: Windows x64, Visual Studio 2022 C++ x64 and Windows SDK, CMake 3.21+, JDK 17 for Gradle, JDK 8 headers, and access to Gradle/Maven repositories. In PowerShell:

```powershell
$jdk8 = 'C:\Program Files\Java\jdk1.8.0_301'  # adjust to your installation
.\gradlew.bat clean build verifyInjectionPayload -PtargetRelease=8 -PnoObfuscation
.\gradlew.bat prepareInjectionBundle -PtargetRelease=8 "-PnativeJavaHome=$jdk8"
ctest --test-dir build/native -C Release --output-on-failure
```

The default bundle is in `build\injection\`. The release build randomizes product class names; CI compares the embedded classes with the source build after name normalization. Builds are unsigned unless a certificate is supplied. Run only in an authorized, isolated environment; the [compatibility notes](README_EN.md#minecraft-compatibility) include known 1.20.1 mapping and display issues.

**Actions → Build and test** builds the Windows bundle, runs Java/native and parity checks, then scans that run's EXE and DLL as data with ClamAV. A completed successful run provides the `windows-x64-soulbound-bundle` and `rebuilt-bundle-scan-report` artifacts for 14 days. Review `SHA256SUMS.txt` and `MODULE-PARITY-REPORT.txt`; a single scan is not proof of safety. No live gameplay or anti-cheat testing has been performed for this rebrand.

## Versioning and releases

`gradle.properties` contains the shared `soulboundVersion`: `1`, `1.1`, `1.2`, etc. It controls the visible version, JAR metadata, executable name and tag. To prepare a later version:

```powershell
python tools/bump_soulbound_version.py 1.1
# Add docs/releases/SoulboundV1.1.md, review changes, commit and run full CI.
```

Tag the **exact commit** only after its entire CI run (including the scan) succeeds. Pushing a product tag (for example `SoulboundV1.1`) starts **Publish tested Soulbound release**, which finds a successful CI run for that commit. Alternatively, dispatch it manually with the tag and an explicit successful run ID. In either case the workflow downloads that run's already-built files, checks their version, embedded DLL/JAR, checksums and scanned hashes, and publishes those exact files without rebuilding or overwriting an existing release. See [release notes](docs/releases/SoulboundV1.md) for limitations.

## Security and original checksum

An earlier build was reported to VirusTotal; the report and detection labels remain unavailable. A separate ClamAV scan found no signatures in that earlier sample, but did not explain the report or establish safety. Keep flagged files quarantined and do not add antivirus exclusions pending independent investigation. See the [security investigation](docs/security-investigation.md); scripts also have [no security sandbox](README_EN.md#script-extensions).

The supplied SHA-256 for the **original**, different `Vape-v4.21.58.exe` is `9b77e8ae1bddb6296e5d773d19ecada341fe93ac8ef39559bddcca7a4b945482`. CI downloads it only for hash verification and offline parity inspection; it is not a source-build input. Its hash does not apply to Soulbound.
