# Vape 4.21 原生测试桥

> **语言 / Language:** 中文 | [English](README_EN.md)

本目录包含一套 x64 Windows JNI/JVMTI 桥接层，依据 `sample.dll` 中那张九方法
`RegisterNatives` 表重建。它支持以下隔离测试实例：Minecraft 1.7.10
Forge/Vanilla、1.8.9 Forge/Vanilla、1.12.2 Forge/Vanilla、1.21.11
Forge/Vanilla/Fabric、26.2 Forge/Vanilla/Fabric，并支持启用了 Forge 的 Lunar
Client 注入。Minecraft 1.21.11 与 26.2 的 Fabric 目标为 Fabric Loader 0.19.3，
其它 Fabric 版本不在当前支持范围内。
Minecraft 1.16.5 的支持不完整，可能存在映射、渲染与模块兼容性问题。

Badlion Client 1.8.9 会在 JVMTI 类重定义之后重跑它自己的运行时转换器。在 JVMTI
初始化阶段，桥接层通过已加载的 `ave` Minecraft 类与 `net/badlion` 类识别出该
运行时，随后保留那些包含 `gg/vape` 回调的成功类定义，并在同一个类被重新转换时
由最终的 `ClassFileLoadHook` 再次提供。若用不含回调的原始字节码重定义某个类，
其保留定义会被移除，因此正常回滚依然有效。`trs(int)` 仍然专用于加载器进度上报
与窗口集成。

权威的桥接接口如下：

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

恢复出的 Java 类里目前还带着的其它 native 声明，`sample.dll` 并未注册，而且该 PE
既没有导出表也没有第二条注册路径 —— 因此这里有意不去臆造它们。

## 加载器 token 交接的设计差异

本地服务集成有意新增了一个 Product 兼容 native：`gat()Ljava/lang/String;`，同时
保持它在 Java 侧可见的名字严格为 `gat`。它没有新增 `native_gat()` 这样的 Java
方法，也没有改动现有的 Java 在线、Zeus、好友、Party 或设置同步实现。
这第十条注册并不属于「九方法 `sample.dll`」这个权威范围；旧版官方 DLL 对 native
`gat()` 提供了独立的证据，但它的实现是通过一条常驻 EXE socket 上的控制器命令
`0x269` 完成的。

Product 的设计里有两种明确的启动模式：

- 直接用 `Vape-v4.21Injector.exe` 注入时没有 Loader 引导，因此 native `gat()`
  返回字符串 `"0"`。
- 加载器启动时会按用户名从回环 Service 取得一个长期 token，并通过临时回环控制器
  socket 把它交给 `Vape-v4.21Native.dll`。DLL 用命令 `0x269` 请求该 token、缓存
  起来供 `gat()` 使用，用 `0x25c` 上报 `trs(step)`，用 `0x25e` 上报完成。加载器
  会一直停留在「加载完成」页面。

Service 不会创建 token 为 `"0"` 的开发者账号、不做 HWID 校验，并且在用户名大小写
不敏感匹配时复用既有的长期 token。由于当前 Java 初始化会把 `gat()` 用于
`/api/v1/{token}/authenticated`，直接模式只能保证返回独立哨兵值 `"0"`；在不改动
Java 初始化的前提下，一旦该 token 被拒绝或 Service 不存在，它可能会停下来。

带版本号的命名内存块在 DLL 注入之前就已创建，其中只携带控制器端口与 Service
端点，**从不包含 token**。token 与加载状态都通过反编译可支持的控制器命令走回环。
完整设计文档见
`../../native_method_research/loader_product_token_handoff_design.md`。

## 构建

使用 `product` 里的 Gradle 8.8 构建 Java 8 载荷、内嵌全部远程托管的运行时依赖、
编译原生目标并组装整个包：

```powershell
.\gradlew.bat prepareInjectionBundle -PtargetRelease=8 `
  -PnativeJavaHome="C:\Program Files\Java\jdk1.8.0_301"
```

只想开发原生层时，可以直接用 CMake 并传入注入 JAR：

```powershell
cmake -S . -B build -A x64 `
  -DVAPE421_JAVA_HOME="C:\Program Files\Java\jdk1.8.0_301" `
  -DVAPE421_PRODUCT_JAR="..\build\libs\vape421-product-recovery-4.21-recovered-injection.jar"
cmake --build build --config Release
```

产物写入 `build/dist`：

- `Vape-v4.21Native.dll`
- `Vape-v4.21.exe`（单文件注入器，内嵌 DLL）
- `Vape-v4.21Injector.exe`（独立注入器，不内嵌 DLL）

## 直接注入

`Vape-v4.21Native.dll` 以 `RCDATA` 资源形式内含恢复出的 Java 产品。先以 64 位 JVM
启动一个受支持的 Minecraft 实例（包括 Minecraft 1.21.11 或 26.2 Fabric），或一个
启用了 Forge 的 Lunar Client 实例，然后在包目录里运行注入器：

```powershell
Vape-v4.21.exe
```

注入器每 750 ms 刷新一次可见 `java.exe` / `javaw.exe` 窗口列表，并显示它们的窗口
标题（例如 `Minecraft` 或 `Lunar Client`）。用上/下键选择进程、按 Enter 注入，
按 Esc 退出。若 DLL 不在旁边，可以把它作为唯一参数传入。原有的非交互形式仍然
保留，便于脚本调用：

```powershell
Vape-v4.21Injector.exe <pid> Vape-v4.21Native.dll
```

`Vape-v4.21.exe` 把 `Vape-v4.21Native.dll` 作为 `RCDATA` 资源内嵌。当可执行文件旁
边没有 `Vape-v4.21Native.dll` 时，它会把内嵌副本解压到
`<exe>\.vapeclient\Vape-v4.21Recovery\Vape-v4.21Native-<pid>.dll` 并注入该副本，
因此整个包可以只带一个文件。

注入器只做 `LoadLibraryW`。加载之后，DLL 工作线程等待 JVM 与 Minecraft 的
`Client thread`，把内嵌的产品 JAR 落到进程临时目录，再通过上下文 ClassLoader
加载它。在 Fabric 上，工作线程使用 Fabric Launcher API 把该 JAR 加入 Knot 目标
ClassLoader，使被转换的游戏类与载荷回调共享同一个类标识。随后它注册那九个权威
方法以及 Product 的 `gat()` 兼容 native，并自动调用 `NativeBridge.start()` ——
不需要第二条命令或启动开关。具体结果请查看包旁
`.vapeclient\log\vape421-native-<pid>-<timestamp>.log` 里本次注入的日志。

注入载荷以 `--release 8` 编译，其项目类使用 class-file major version 52。运行时
依赖从 Gradle 中声明的仓库解析并合并进该载荷，而不是以 vendored 源码目录的形式
恢复。注入器会拒绝非 x64 进程。
