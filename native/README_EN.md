# Vape 4.21 native test bridge

> **Language:** English | [中文](README.md)

This directory contains an x64 Windows JNI/JVMTI bridge reconstructed from
the nine-method `RegisterNatives` table in `sample.dll`. It supports isolated
Minecraft 1.7.10 Forge/Vanilla, 1.8.9 Forge/Vanilla, 1.12.2
Forge/Vanilla, 1.21.11 Forge/Vanilla/Fabric, and 26.2 Forge/Vanilla/Fabric test
instances, including Forge-enabled Lunar Client
injection. Minecraft 1.21.11 and 26.2 Fabric target Fabric Loader 0.19.3; other Fabric
versions are outside the current support scope.
Minecraft 1.16.5 support is incomplete and may have mapping, rendering, and
module compatibility problems.

Badlion Client 1.8.9 can rerun its runtime transformer after a JVMTI class
redefinition. During JVMTI initialization, the bridge identifies that runtime
from the loaded `ave` Minecraft class and `net/badlion` classes. It then
retains successful class definitions containing `gg/vape` callbacks and
supplies them again from the final `ClassFileLoadHook` when the same class is
retransformed. Redefining a class with callback-free original bytecode removes
its retained definition, so normal rollback still works. `trs(int)` remains
dedicated to loader progress reporting and window integration.

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
second registration path. They are intentionally not invented here.

## Loader token handoff design difference

The local-service integration deliberately adds `gat()Ljava/lang/String;`
as a Product compatibility native while keeping its Java-visible name exactly
`gat`. It does not add a `native_gat()` Java method and does not change the
existing Java online, Zeus, friend, Party, or settings-sync implementations.
This tenth registration is not part of the nine-method `sample.dll` authority;
the legacy official DLL provides separate evidence for native `gat()`, but its
implementation used controller command `0x269` over a persistent EXE socket.

The Product design has two explicit launch modes:

- Direct `Vape-v4.21Injector.exe` injection has no Loader bootstrap, so native
  `gat()` returns the string `"0"`.
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

Use Gradle 8.8 from `product` to build the Java 8 payload, embed all remotely
managed runtime dependencies, compile the native targets, and assemble the
bundle:

```powershell
.\gradlew.bat prepareInjectionBundle -PtargetRelease=8 `
  -PnativeJavaHome="C:\Program Files\Java\jdk1.8.0_301"
```

For native-only development, invoke CMake directly with the injection JAR:

```powershell
cmake -S . -B build -A x64 `
  -DVAPE421_JAVA_HOME="C:\Program Files\Java\jdk1.8.0_301" `
  -DVAPE421_PRODUCT_JAR="..\build\libs\vape421-product-recovery-4.21-recovered-injection.jar"
cmake --build build --config Release
```

Outputs are written to `build/dist`:

- `Vape-v4.21Native.dll`
- `Vape-v4.21.exe` (single-file injector, embeds the DLL)
- `Vape-v4.21Injector.exe` (standalone injector, does not embed the DLL)

## Direct injection

`Vape-v4.21Native.dll` contains the recovered Java product as an `RCDATA`
resource. Start a supported Minecraft instance (including Minecraft 1.21.11
or 26.2 Fabric), or a Forge-enabled Lunar Client instance, with a 64-bit JVM,
then run the injector from the bundle directory:

```powershell
Vape-v4.21.exe
```

The injector refreshes its list of visible `java.exe` and `javaw.exe` windows
every 750 ms and displays their window titles (for example, `Minecraft` or
`Lunar Client`). Select a process with Up/Down and press Enter to inject;
press Esc to quit. If the DLL is elsewhere, pass its path as the only
argument. The original non-interactive form remains available for scripts:

```powershell
Vape-v4.21Injector.exe <pid> Vape-v4.21Native.dll
```

`Vape-v4.21.exe` embeds `Vape-v4.21Native.dll` as an `RCDATA` resource. When
no `Vape-v4.21Native.dll` sits beside the executable, it extracts the embedded
copy to `<exe>\.vapeclient\Vape-v4.21Recovery\Vape-v4.21Native-<pid>.dll` and
injects that, so the bundle can be carried as a single file.

The injector only performs `LoadLibraryW`. Once loaded, the DLL worker waits
for the JVM and Minecraft `Client thread`, materializes its embedded product
JAR into the process temp directory, and loads it through the context
ClassLoader. On Fabric, the worker uses the Fabric Launcher API to add the JAR
to the Knot target ClassLoader so transformed game classes and payload callbacks
share one class identity. It then
registers the nine authoritative methods plus the Product `gat()` compatibility
native, and calls
`NativeBridge.start()` automatically. No second command or start flag is
required. Inspect the per-injection log under
`.vapeclient\log\vape421-native-<pid>-<timestamp>.log` next to the bundle for
the exact result.

The injection payload is compiled with `--release 8`; its project classes use
class-file major version 52. Runtime dependencies are resolved from the
repositories declared in Gradle and merged into that payload, not restored as
vendored source directories. The injector rejects non-x64 processes.
