# Vape 4.21.58 — English recovery build

This branch makes the recovered Windows loader and in-game client start in **English**. It is based on the existing Java and C/C++ recovery sources, not a translation of machine code into TypeScript. The project is independent research, not an official Vape product.

- [Full English documentation](README_EN.md) · [中文文档](README_ZH.md)
- [Source analysis and localization notes](docs/english-edition.md) · [Native build details](native/README_EN.md)
- [Upstream v4.21.58 release](https://github.com/RSSeeker/Vape-v4/releases/tag/v4.21.58)

Use only with a valid license and in environments where you are authorized to test. Respect the applicable software licenses, laws and server rules. The original release and this source-based rebuild are different binaries; neither feature parity nor vendor signing is implied.

## Language behavior

English is the default on a fresh install and when the language setting is absent or unreadable. The launcher, console messages, in-game settings and supported dynamic labels use English. A previously **explicitly saved Chinese** selection is respected. To change it, select **Settings → General → Language → English** in-game and restart the loader. Older configurations that omitted their default language will now use English.

The GUI launcher prefers an external `Vape-v4.21Native.dll` beside the EXE over its embedded DLL. Use the EXE in a clean directory, or use the matching DLL produced by this build; an older external DLL can restore the older Chinese client. Your settings are stored in `.vapeclient` beside the EXE.

## Build the English EXE on Windows

Requirements: Windows x64, Visual Studio 2022 C++ x64 and Windows SDK, CMake 3.21+, JDK 17 for Gradle, JDK 8 headers, and access to Gradle/Maven repositories. From PowerShell in the repository root:

```powershell
# Change this path to your JDK 8 installation. JAVA_HOME should point to JDK 17.
$jdk8 = 'C:\Program Files\Java\jdk1.8.0_301'
.\gradlew.bat clean build verifyInjectionPayload -PtargetRelease=8 -PnoObfuscation
.\gradlew.bat prepareInjectionBundle -PtargetRelease=8 -PnoObfuscation "-PnativeJavaHome=$jdk8"
ctest --test-dir build/native -C Release --output-on-failure
```

The expected result is `build\injection\Vape-v4.21.58.exe`, a single-file loader containing the rebuilt DLL and Java payload. Windows CI builds the bundle and runs native tests; it does not test gameplay. The bundle also includes the matching optional DLL. Keep it separate from the original release: the rebuilt EXE has a different hash and is unsigned unless you configure your own signing certificate.

Alternatively, push the branch or run **Actions → Build and test → Run workflow** on that branch. A successful Windows run uploads the **windows-x64-english-bundle** artifact for 14 days, including `SHA256SUMS.txt` and `MODULE-PARITY-REPORT.txt`. The report records source/build comparisons against the pinned v4.21.58 baseline and inspects the hash-verified upstream release. If its class layout prevents direct comparison, the report says so; it is not an in-game functionality guarantee. GitHub Actions may need to be enabled on your fork first.

To run the loader, start a supported 64-bit Minecraft instance, launch the EXE and select the process; Right Shift opens the in-game GUI by default. See the [compatibility notes](README_EN.md#minecraft-compatibility) and the [script security warning](README_EN.md#script-extensions) before use.

## Original release checksum

The supplied SHA-256 for the **original** `Vape-v4.21.58.exe` is:

```text
9b77e8ae1bddb6296e5d773d19ecada341fe93ac8ef39559bddcca7a4b945482
```

Verify a downloaded original with `Get-FileHash .\Vape-v4.21.58.exe -Algorithm SHA256`. This checksum does **not** apply to the rebuilt English EXE. The original binary is not an input to the source build. The Windows CI parity step downloads it only for hash verification and offline payload comparison; consult the report from a successful run for that run's results.
