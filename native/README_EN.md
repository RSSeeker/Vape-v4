# Vape 4.21 native test bridge

> **Language:** English | [中文](README.md)

This directory contains an x64 Windows JNI/JVMTI bridge reconstructed from
the nine-method `RegisterNatives` table in `sample.dll`. The authoritative
per-version/per-runtime support matrix lives in the root
[README.md](../README.md) ("Minecraft compatibility" table): Forge and Vanilla
for 1.7.10 / 1.8.9 / 1.12.2, and Forge/Vanilla/Fabric for 1.21.11 and 26.1.2 are
supported; 1.16.5, 1.20.1, 1.21.1 and 26.2 are experimental. Minecraft 1.21.11
and 26.2 Fabric target Fabric Loader 0.19.3; other Fabric versions are outside
the current support scope.
Minecraft 1.16.5 support is incomplete and may have mapping, rendering, and
module compatibility problems.

The authoritative bridge surface is:

```text
scb(Class, byte[]) : int
smd(int, int) : void
gks(int) : short
gkn(long) : String
mvk(int, int) : int
gcb(Class) : byte[]
gfb(String) : byte[]
trs(int) : void
inv(Method, Object, Object[]) : Object
```

The additional native declarations currently present in the recovered Java
class are not registered by `sample.dll`, and the PE has no export table or
second registration path. They are intentionally not invented here. `trs(int)`
remains dedicated to loader progress reporting and window integration.

## Loader token handoff design difference

The local-service integration deliberately adds `gat()Ljava/lang/String;`
as a Product compatibility native while keeping its Java-visible name exactly
`gat`. It does not add a `native_gat()` Java method and does not change the
existing Java online, Zeus, friend, Party, or settings-sync implementations.
This tenth registration is not part of the nine-method `sample.dll` authority;
the legacy official DLL provides separate evidence for native `gat()`, but its
implementation used controller command `0x269` over a persistent EXE socket.

The Product design has two explicit launch modes:

- Direct `Vape-v4.21.<version>.exe -nogui` injection has no Loader bootstrap, so
  native `gat()` returns the string `"0"`.
- Loader startup obtains a long-lived token from the loopback Service by
  username and exposes it to `Vape-v4.21Native.dll` through the temporary
  loopback controller socket. The DLL requests it with command `0x269`, caches
  it for `gat()`, reports `trs(step)` with `0x25c`, and reports completion with
  `0x25e`. The Loader remains open through the Finished Loading page.

The Service does not create a token-`"0"` developer account, performs no HWID
check, and reuses the existing long-lived token for a case-insensitive username
match. Because current Java initialization uses `gat()` for
`/api/v1/{token}/authenticated`, direct mode is only guaranteed to return the
standalone sentinel `"0"`; without changes to Java initialization it may stop
when that token is rejected or the Service is absent.

The versioned named-memory block is created before DLL injection and carries
only the controller port and Service endpoints; it never contains the token.
The token and loading state use the decomp-supported controller commands over
loopback. The full design is documented at
`../../native_method_research/loader_product_token_handoff_design.md`.

## Build

Use Gradle to build the Java 8 payload, embed all remotely managed runtime
dependencies, compile the native targets, and assemble the single-file bundle
(this is exactly what `.github/workflows/release.yml` runs in CI):

```powershell
.\gradlew.bat prepareInjectionBundle -PtargetRelease=8 `
  -PnativeJavaHome="C:\Program Files\Java\jdk1.8.0_301"
```

For native-only development, invoke CMake directly — these are the two steps the
Gradle `configureNative` / `buildNative` tasks perform (`nativeJavaHome` is passed
as `VAPE421_JAVA_HOME`, and `VAPE421_VERSION` comes from the version in
`build.gradle`):

```powershell
cmake -S native -B build/native -A x64 `
  -DVAPE421_JAVA_HOME="C:\Program Files\Java\jdk1.8.0_301" `
  -DVAPE421_PRODUCT_JAR="build\libs\vape421-product-recovery-4.21.39-injection.jar" `
  -DVAPE421_VERSION=4.21.39
cmake --build build/native --config Release
```

Native component tests (CI runs these too):

```powershell
ctest --test-dir build/native -C Release --output-on-failure
```

Outputs are written to `build/native/dist`:

- `Vape-v4.21Native.dll` — carries the recovered product JAR as an `RCDATA`
  resource
- `Vape-v4.21.<version>.exe` — single-file GUI loader (embeds the DLL above)

> There is no separate `Vape-v4.21Injector.exe` any more: console injection is
> the `-nogui` mode of the same exe (`injector.c` is compiled into it).

`prepareInjectionBundle` copies those two artifacts plus this README into
`build/injection/`, producing a single-file bundle ready to hand out.

## Direct injection

`Vape-v4.21Native.dll` contains the recovered Java product as an `RCDATA`
resource. Start a supported Minecraft instance with a 64-bit JVM, then run the
loader from the bundle directory:

```powershell
Vape-v4.21.<version>.exe
```

With no arguments this opens the GUI loader (window title "Vape v4"): it
auto-refreshes the visible Java windows, shows their window titles (for example
`Minecraft`), and injects into the process you pick.
In GUI mode an external `Vape-v4.21Native.dll` **next to the exe is preferred**;
the embedded copy is extracted only when it is absent, so the bundle can be
carried as a single file.

Console mode requires `-nogui` as the first argument:

```powershell
Vape-v4.21.<version>.exe -nogui <minecraft-pid>
```

Without a pid it opens the auto-refreshing Java window picker (Up/Down to select,
Enter to inject, Esc to quit). Console mode **always uses the embedded
`Vape-v4.21Native.dll` and never loads an external DLL**; the embedded copy is
extracted to `<exe>\.vapeclient\Vape-v4.21Recovery\Vape-v4.21Native-<pid>.dll`
before injection.

The injector only performs `LoadLibraryW`. Once loaded, the DLL worker waits
for the JVM and Minecraft `Client thread`, extracts its embedded product JAR to
`<exe>\.vapeclient\Vape-v4.21Recovery\Vape-v4.21-product-<pid>.jar` (every
artifact stays inside the `.vapeclient` tree; nothing is written to `%TEMP%`),
and then attaches it per runtime:

- Vanilla: appended to the system ClassLoader search path
- Forge / NeoForge: payload package routes installed on the ModLauncher
  `ModuleClassLoader`
- Fabric: the JAR is added to the Knot target ClassLoader through the Fabric
  Launcher API, so transformed game classes and payload callbacks share one
  class identity

It then registers the nine authoritative methods plus the Product `gat()`
compatibility native, and calls `NativeBridge.start()` automatically. No second
command or start flag is required. Inspect the per-injection log under
`.vapeclient\log\vape421-native-<pid>-<timestamp>.log` next to the bundle for
the exact result.

The injection payload is compiled with `--release 8`; its project classes use
class-file major version 52. Runtime dependencies are resolved from the
repositories declared in Gradle and merged into that payload, not restored as
vendored source directories. The injector rejects non-x64 processes.
