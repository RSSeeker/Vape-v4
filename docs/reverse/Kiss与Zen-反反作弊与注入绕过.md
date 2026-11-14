# Kiss / Zen：反反作弊（anti-anti-cheat）与注入绕过分析

> 重点：**网易（NetEase）客户端**——只有 Kiss 做了针对性对抗。
> 材料：可读源码 `kiss/源码/com/kiss/**`（KissV1.4，1.20.1 Forge，非混淆）+ `OpenZen/**`（Zen 完整源码）+ 本文引用的 1.7.3 混淆 jar 逆向结论（`docs/reverse/KissFabric-1.7.3-逆向分析.md`）。
> 说明：Kiss 1.7.3 jar 已混淆，本报告中"网易绕过"部分来自 V1.4 可读源码；1.7.3 的类名/字符串形态见原报告。

---

## 0. 摘要

| 维度 | KissClient | OpenZen (Zen) | 我们 Vape 4.21.49 |
|---|---|---|---|
| 注入方式 | ① Forge mod 正常加载 ② 外部器 `VirtualMachine.attach().loadAgentPath(DLL)` ③ 本地 9999 端口与 launcher 通信 | ① loader 手动映射 DLL（不进 PEB 模块表）② DLL 找 `instrument.dll` 调 `Agent_OnAttach` ③ `Instrumentation` retransform | 手动映射 + 反射加载器（`native/manual_map.h`、`reflective_loader.c`），失败回退 `LoadLibraryW` |
| 字节码装载 | native `KissCore.dll` 用 JVMTI `DefineClass`/`RedefineClasses`；`Unsafe` 改 `Class.module` 绕 Java 9+ 模块封装 | `ClassFileTransformer`（ASM）在加载/重转换时改类 | 自研映射层 + Mixin 风格事件（无 JVMTI 重定义） |
| 注入痕迹 | `KissCore.dll` 由 `System.load` 加载（模块表可见）；本地 9999 端口 | 手动映射无模块记录；但 `%TEMP%\openzen-<pid>.jar` 落盘 | DLL 手动映射无记录；产品 JAR 释放到 `.vapeclient\Recovery\` |
| 发布包混淆 | 是（单字母包名、控制流平坦化、`~` 字符串加密、`char[]` 构造） | 是（每次构建随机类名，native 侧联动 `generated_names.h`） | **可选**（`-PnoObfuscation` 时 Release 不混淆，见 README FAQ） |
| 网易专项对抗 | **有**：`NeteaseBypass` | 无 | **无** |
| 服务端反反作弊 | `RotationEngine`（抖动+量化）、`Velocity`(JumpReset)、`MoreKB`、`HitSelect`、`BackTrack` | `Disabler`（Grim/ACA/Themis）、`LagManager`(KeepAlive 队列)、`Rotation.snapToSensitivity` | 已有 `Velocity`/`JumpReset`/`WTap`/`HitSelect`/`BackTrack`/`Blink`/`FakeLag`/`Scaffold` 等，但无 GCD 统一收口、无 NetEase 对抗 |

---

## 1. 网易客户端有哪些"可被对抗"的检测面

从 Kiss 反射访问的类名可以反推出网易 Java 版客户端（`com.netease.mc.mod.*`）的几条能力：

| 类 / 字段 | 作用 | Kiss 的处理 |
|---|---|---|
| `com.netease.mc.mod.encryption.EncryptionEnableWrapper#NetworkEncrypt` | 网易客户端网络加密开关 | 静态字段置 `false` |
| `com.netease.mc.mod.filter.ChatBanHelper#chatBan` / `#banChatExpireAt` | 本地禁言状态与到期时间 | 置 `false` / `0`（并先去掉 `final`） |
| `com.netease.mc.mod.filter.ItemBanHelper#itemBanlock` | 物品封禁等待锁 | 在 `onReply(4614)` 里 `notifyAll()` 解锁 |
| `com.netease.mc.mod.network.socket.NetworkHandler#replyHashMap` | **SMID → 指令处理类** 的分发表（服务端下发指令） | 用运行时生成的子类替换 4 个 SMID 的处理类 |
| `com.netease.mc.mod.screenshot.ScreenShotHandler#step` | 客户端截图流水线状态机 | 看门狗线程每秒把 `step` 归 0 |

SMID（Kiss 代码里的常量）：
- `1  = RemoteClose`（服务端远程关闭客户端）
- `4612 = Screenshot`（服务端请求截图）
- `2305 = OpScreenshot`（管理员触发的截图）
- `4614 = ItemBan`（物品封禁指令）

> 这说明网易的"反作弊"在客户端侧不是驱动扫描，而是**服务端下发指令 + 客户端模块执行**（关游戏、截屏上传、封物品、禁言），外加网络层加密。Kiss 的对抗点全部落在"把服务端意志的执行器换掉/关掉"这一层。

---

## 2. Kiss 的网易绕过（`com/kiss/bypass/NeteaseBypass.java`，167 行）

### 2.1 调用时机

```java
// com/kiss/KissMod.java —— Forge 模组构造函数里（越早越好）
@Mod("kissclient")
public KissMod() {
    System.out.println("[KissClient] Forge Mod loading...");
    NeteaseBypass.applyEarly();   // 关网络加密 + 清禁言
    Preloader.init();
}
```
```java
// com/kiss/Main.java:54 —— 主初始化完成后
NeteaseBypass.applyLate();        // 中和 4 个 SMID + 起截图看门狗
```

拆成 early/late 的原因：`EncryptionEnableWrapper`/`ChatBanHelper` 在 MC 启动早期就可能被调用，必须提前改静态字段；`NetworkHandler.replyHashMap` 与 `ScreenShotHandler` 要等网易模块初始化注册完才有内容，晚一点替换才有效。

### 2.2 关网络加密

```java
Class<?> clazz = Class.forName("com.netease.mc.mod.encryption.EncryptionEnableWrapper");
Field f = clazz.getDeclaredField("NetworkEncrypt");
f.set(null, Boolean.FALSE);
```
效果：客户端不再走网易自研的加密通道，普通封包抓取/注入工具可以直接读写。（副作用：明文流量更容易被第三方分析。）

### 2.3 解禁言

```java
Class<?> clazz = Class.forName("com.netease.mc.mod.filter.ChatBanHelper");
Field chatBan = clazz.getDeclaredField("chatBan");
chatBan.setAccessible(true);
removeFinal(chatBan);                       // 反射去 final：Field.modifiers &= ~0x10
chatBan.setBoolean(null, false);
Field expireAt = clazz.getDeclaredField("banChatExpireAt");
expireAt.setAccessible(true);
expireAt.setLong(null, 0L);
```
`removeFinal` 用 `Field.class.getDeclaredField("modifiers")` 的经典写法（Java 12+ 会被封，但 1.8.x 运行时有效）。

### 2.4 中和服务端指令（核心）

```java
private static void neutralizeMessage(int smid, String label) {
    Class<?> nhClass = Class.forName("com.netease.mc.mod.network.socket.NetworkHandler");
    Field mapField = nhClass.getDeclaredField("replyHashMap");
    mapField.setAccessible(true);
    Object mapObj = mapField.get(null);           // 静态 map，或回退到实例字段
    ...
    Object original = map.get(smid);              // 原处理类（如 ScreenshotReply）
    Object neutral  = ReplyFactory.createNeutralized(smid, original.getClass());
    map.put(smid, neutral);
}
```
`ReplyFactory` 用 ASM 现场生成原处理类的子类，只覆写消息入口 `handMessage([B)V`：

```java
// ReplyFactory.generate(...)
String superName = superClass.getName().replace('.', '/');
cw.visit(52, 33, "com/kiss/bypass/NeutralizedReply_" + smid, null, superName, null);
// <init>()V → super()
mv = cw.visitMethod(1, "handMessage", "([B)V", null, null);
mv.visitLdcInsn(smid);
mv.visitMethodInsn(184, "com/kiss/bypass/NeteaseBypass", "onReply", "(I)V", false);
mv.visitInsn(177);
```
定义方式：
```java
byte[] bytes = generate(smid, superClass);
Class<?> clazz = MethodHandles.lookup().defineClass(bytes);   // Java 9+ 的私有 defineClass
return clazz.getDeclaredConstructor().newInstance();
```
`onReply` 只做记录/解锁：
- `1` → 打印 `BLOCKED: remote close attempt`
- `2305/4612` → 打印 `BLOCKED: screenshot attempt`
- `4614` → `ItemBanHelper.itemBanlock` 上 `synchronized { notifyAll(); }`，避免客户端线程一直卡在封禁等待里

要点：
1. **不删 map 项**，而是换成子类实例——避免网易代码 `get(smid)` 拿到 null 引发 NPE 暴露。
2. 子类构造仍调 `super()`，保留原处理类的字段初始化，避免序列化/反射路径崩。
3. `MethodHandles.lookup().defineClass` 定义在当前类同一包/类加载器下（`com/kiss/bypass`），不会被网易的 ClassLoader 校验拦住。

### 2.5 截图看门狗

```java
Field stepField = clazz.getDeclaredField("step");   // ScreenShotHandler.step
Thread watchdog = new Thread(() -> {
    while (true) {
        if (stepField.getInt(null) != 0) { stepField.setInt(null, 0); ... }
        Thread.sleep(1000L);
    }
});
watchdog.setDaemon(true);
watchdog.setName("KissScreenshotWatchdog");
```
每秒轮询并清零，等于把网易截图状态机按在起点：即使指令到达、`replyHashMap` 被绕过（比如走了别的入口），截图流程也无法推进。线程名 `KissScreenshotWatchdog` 是明显的检测特征（见 §6）。

### 2.6 效果边界（客观）

- **只覆盖客户端可见执行器**：加密开关、禁言、4 个 SMID、截图状态机。服务端换 SMID、换加密协议、把执行改到 native/驱动层时全部失效。
- 不改服务端判定逻辑（服务端仍然能看见你的移动/攻击行为），所以它属于"抗检测/抗处置"，不是"抗判定"。
- 打印大量 `System.out`（`[KissClient] Bypass: ...`）：如果网易做本地日志/控制台扫描，这些字符串本身就是证据；1.7.3 发布版做了字符串加密（`~` 前缀 + 294 处），正好掩盖这类特征。

---

## 3. Kiss 的注入链（"绕过注入检测"）

### 3.1 入口 A：Forge 模组（最"白"的路径）

`KissMod` 用 `@Mod("kissclient")` 注册，等同于普通 Forge 模组——服务端/客户端看不到任何"注入"行为，只看到 mod 列表里多一个（这也是为什么 Kiss 做 Forge 版时"注入绕过"问题最小：它根本不注入）。网易/服务器若只看 mod 列表，可见 `kissclient`；若做 mod 黑名单，这条路径就暴露。

### 3.2 入口 B：JDK attach（`com/kiss/attach/KissAttach.java`）

```java
VirtualMachine vm = VirtualMachine.attach(pid);
vm.loadAgentPath(dllPath);       // 直接加载 agent DLL（不走 -javaagent 命令行）
vm.detach();
```
- 用 JDK 自带 attach 机制，**不改启动命令行**、不写 `-javaagent`，因此启动参数检查工具/日志看不到。
- 需要目标进程开启 attach（默认开），Windows 上走 `%TEMP%\.attach_pid<pid>` 命名管道；这段"临时文件"是痕迹（Kiss 没清理）。
- 要求目标 JVM 与 launcher 同 JDK 架构（64 位）。

### 3.3 入口 C：external launcher + 本地端口

`Preloader`（`com/kiss/preload/Preloader.java`）：
```java
boolean connected = connect("127.0.0.1", 9999);
if (connected) {
    MAIN_PATH = sendAndWait("run!");          // launcher 告诉它安装目录
    CORE_DLL  = sendAndWait("ask_dll_name");  // launcher 告诉它 core DLL 文件名
}
int mc_ver = detectMcEnvironment();           // 0 非混淆 / 1 vanilla / 2 forge obf / 3 fabric obf
System.load(MAIN_PATH + "\\" + CORE_DLL);     // 加载 KissCore.dll
byte[][] classes = getClassByte(mc_ver);      // native 返回全部作弊类字节
for (byte[] b : classes) defineClass(b);      // native JVMTI DefineClass
mainClazz.getDeclaredMethod("attach").invoke(null);
```
关键技巧：
1. **类字节放在 native DLL 里**（`getClassByte`），内存中定义，磁盘上没有 `.class` / `.jar` 可扫描；`Class.forName("com.kiss.Main")` 只在 MOD 模式下命中。
2. **Unsafe 改 Class.module**：
   ```java
   Unsafe unsafe = ...; 
   Module baseModule = Object.class.getModule();     // java.base
   long addr = unsafe.objectFieldOffset(Class.class.getDeclaredField("module"));
   unsafe.getAndSetObject(Preloader.class, addr, baseModule);   // 让 Preloader 属于 java.base
   setContextClassLoader(classLoader);
   ```
   绕开 Java 9+ 的模块可见性限制，使作弊类能访问 `java.base` 内部 API（`sun.misc.Unsafe` 之外，还能直接拿 JVMTI 能力）。
3. **native 导出**（`com/kiss/natives/CoreNative` + `Preloader` 的 native 方法）：`defineClass`、`redefineClasses`、`getClassBytes`、`getClassLoader`、`registerNatives`、`setModMode`、`log` —— 即一个自带的 JVMTI agent 能力集。
4. 检测面：`KissCore.dll` 通过 `System.load` 加载 → **出现在进程模块表**（`EnumProcessModules`/`GetModuleHandle` 可见），路径在 launcher 安装目录；9999 端口本机监听；native 线程/回调名可见。Kiss 1.7.3 用类名/字符串混淆降低了"按名字扫描"的命中率，但模块表这一层没有藏（要藏就得像 Zen 那样手动映射）。

### 3.4 构建期隐藏（1.7.3 jar 的逆向结论，摘要）

- 包名/类名全部单字母（`a/a.java` 之类），与"作弊语义"无关；
- 294 处字符串用 `~` 前缀 + 位置相关异或（key `1597681457`）加密，运行时解密；
- 5807 处 `new char[]{...}` 常量拼装，避免常量池里出现明文；
- 362 个控制流平坦化调度器 + 不透明谓词，静态分析成本高；
- 未混淆的 1.4 源码与 1.7.3 的对应关系靠行为比对，不靠名字。

---

## 4. Zen 的注入链（对照）

### 4.1 手动映射（`OpenZen/native/loader/src/manual_map.cpp`、`injector.cpp`）

- `injector.cpp` 读取 DLL 文件字节，`manual_map.cpp` 在目标进程里分配内存、按节映射、处理重定位/导入表，然后创建远程线程执行 mapped image 的入口；
- `remote_load_library()`（`LoadLibraryW` 注入）只是兜底函数；
- 效果：DLL **不进入 PEB 模块表**，`EnumProcessModules`/`GetModuleHandle` 看不到，也没有 image-load 事件；代价是卸载/崩溃转储困难。
- 与我们工程里的 `manual_map.h` + `reflective_loader.c` 同构（我们是自己实现的，调 `Vape421ReflectiveLoader`）。

### 4.2 无 `-javaagent` 的 agent 挂载（`OpenZen/native/dll/src/jvm_attach.cpp`）

```cpp
HMODULE javaDll = GetModuleHandleW(L"java.dll");
// 取 java.dll 路径 → 同目录 instrument.dll
HMODULE inst = LoadLibraryW(instrument_path.c_str());
auto fn = (Agent_OnAttach_t)GetProcAddress(inst, "Agent_OnAttach");
fn(vm, options /* = 内嵌 jar 路径 */, nullptr);
```
```cpp
JNI_GetCreatedJavaVMs(&vms, 1, &count);   // 运行时解析，DLL 不链接 jvm.lib
```
- 直接调 JDK 的 `instrument.dll!Agent_OnAttach`：**命令行没有 `-javaagent`、没有 attach 端口、没有 `VirtualMachine.attach` 的临时文件**（比 Kiss 的 attach 路径更隐蔽）。
- jar 由 `jar_extract.cpp` 释放到 `%TEMP%\openzen-<pid>.jar`（落盘，可被文件扫描）。

### 4.3 找游戏 ClassLoader（`OpenZen/native/dll/src/class_loader.cpp`）

```cpp
jvmti->GetLoadedClasses(&count, &classes);
for (...) { GetClassSignature(c, &sig, ...); if (sig == "Lnet/minecraft/client/Minecraft;") ... }
```
用 JVMTI 遍历已加载类，按签名找到 Minecraft 的 ClassLoader，再 `load_dll_bootstrap`（`class_loader.cpp`）加载桥接类。比 `Thread.currentThread().getContextClassLoader()` 稳。

### 4.4 字节码补丁（`OpenZen/src/main/java/asm/patchify/**`）

- `PatchAgent.premain/agentmain` 把 `Instrumentation` 塞进 `System.getProperties()`（键 `oz.instrumentation`），跨 ClassLoader 取用（dev 环境 agent jar 与 mod jar 是两个 loader）；
- `PatchClassFileTransformer` 实现 `ClassFileTransformer`，按 `@Patch/@Inject/@Overwrite` 用 ASM 改写目标类；`installPatchesAndRetransform()` 对已加载类 `retransformClasses`；
- **完全不用 Mixin、不以 Forge mod 身份注册**：类里没有 `mixins.json`，也没有 `@Mod`，因此"按 mixin 配置/Mod 列表"的检测拿不到东西；
- 副作用：`Instrumentation` 在 System properties 里（`oz.instrumentation`）——这是个可被反射扫描到的特征；jar 释放路径 `%TEMP%` 同理。

### 4.5 构建期随机类名（`OpenZen/build.gradle` `ext.obfuscateJar`）

- 每次构建把自有类（`shit.zen.*` / `asm.patchify.*`）改成随机 16 位包名+类名，重写所有引用；
- native 侧通过生成的 `generated_names.h`（`OZ_BRIDGE_FQCN`）联动，所以也改了 native 里的 `Class.forName` 目标；
- 映射写 `build/rename-mapping.txt`（反混淆崩溃日志唯一依据）；CI 产物固定名字，README 建议自行编译。

---

## 5. 服务端反反作弊（服务器能看到的层面）

### 5.1 旋转：不发"完美角"

- Kiss `RotationEngine`（`kiss/源码/com/kiss/module/modules/risky/killaura/RotationEngine.java`）：速度呼吸 `smoothNoise = 0.5+0.5*(0.62cos x + 0.38cos(2.3x+0.71))`；三角抖动 `0.15*(1+0.75t²)`、pitch ×0.65、每 3~7 tick 重抽、α=0.25；最后按灵敏度量化（`gcd = (sens*0.6+0.2)^3 * 8 * 0.15`）。
- Zen `Rotation.snapToSensitivity`（`OpenZen/src/main/java/shit/zen/utils/rotation/Rotation.java:91`）：`scaled = sens*0.6+0.2; gcd = scaled^3 * 8`（另一处 `RotationUtil.getSensitivitySnappedRotation` 用 `*1.2`），并把 `sensitivity==0.5` 特判成 `0.47887325`；`steps = round(...)` 后 `snapped = steps*gcd`。
- 目的：让封包里的角度差**恰好是鼠标能产生的整数步长**，避免"非人角度"（ACA/Themis/Grim 的 aim 检查通常就查这个）。

### 5.2 击退：不改判定，改输入

Kiss `Velocity`（257 行）不是取消 `ClientboundSetEntityMotionPacket`，而是 **jump reset**：
- 受击当 tick 记录 `hurtCycle`、KB 方向（`kbDirX/Z`，TTL 12 tick）；
- 在 `EventMoveInput` 里按方向覆盖前/侧输入（counter-strafe）、强制 sprint 重置、必要时再来一次跳跃（double jump）；
- 落地冷却 14 tick，`projectileReset` 处理弹射物击退。
服务端看到的是"玩家被击退时自己跳了一下"——这是合法输入序列，无法用单一包特征判定。

Kiss `MoreKB`（185 行）：攻击前 **sprint reset**（sprint off → on），让服务端把这一下算成"非疾跑命中"，从而给满击退；`HitSelect`（201 行）用 `hurtTime`/逼近角度决定是否放行这次攻击，避免连续命中暴露 APS。

### 5.3 延迟与回滚

- Kiss `BackTrack`（328 行）：缓存入站位置包，用旧位置判定命中，再按时间/距离批量放行——服务端看到的是"你打中了他当时的位置"。
- Zen `LagManager`（`OpenZen/src/main/java/shit/zen/manager/LagManager.java`）：blink 期间扣下 `ClientboundKeepAlivePacket`，`lagTime`（默认 500ms）后按时序放行，避免服务端读超时踢人。
- Vape 已有 `BackTrack`/`Blink`/`FakeLag`，实现思路接近，但还没有这种"KeepAlive 队列 + 到期放行"的细节保护。

### 5.4 指令序列：修"不可能"的包

Zen `Disabler`（`OpenZen/src/main/java/shit/zen/modules/impl/exploit/Disabler.java`，319 行）按反作弊逐个开关：
- `Grim BadPacketsA`：同一个 slot 的 `ServerboundSetCarriedItemPacket` 重复出现 → 取消；
- `ACA Fast Switch`：快速切槽时补发中间 slot（`sendIntermediateSlots`），服务端看到逐格切换；
- `ACA Inventory Frequency`：开背包 150ms 内关 → 暂存 `ServerboundContainerClosePacket`，到期再发；
- `ACA Aim Step`：某轴"卡住"（delta<1e-5 而另一轴 >1）时加高斯微扰；
- `ACA Perfect Rotation`：角度差是已知网格（`perfectRotSteps = 5.625° 的整数倍`，从 0 到 90）时加 0.005 高斯；
- `Grim Duplicate RotPlace`：两次放置的旋转增量完全相同 → 抖动 0.001~0.01°；
- `Themis Blink`：200ms 内没有 `StatusOnly`/`Pong` 就补发 `ServerboundPongPacket(0)`。
Zen `PacketUtil.sendPredictive` 还直接复用原版预测序列（`BlockStatePredictionHandler.startPredicting()` + `predict(sequence)`）发包，保证 `sequence` 与客户端预测一致。

---

## 6. 检测面清单（双方都没完全藏住的地方）

| 痕迹 | Kiss | Zen | 说明 |
|---|---|---|---|
| 进程模块表新增 DLL | 有（`KissCore.dll`，`System.load`） | 无（手动映射） | 手动映射才藏得住 |
| 磁盘落盘文件 | 安装目录（jar/DLL/映射） | `%TEMP%\openzen-<pid>.jar` | Zen 把 jar 写到 TEMP；Kiss 靠 launcher 目录 |
| 本地端口/管道 | `127.0.0.1:9999` | 无 | Kiss 用于 launcher 通信 |
| 进程内字符串/类名 | 混淆（1.7.3：单字母包/加密字符串） | 随机类名 + native 联动 | 混淆是防"扫描"的关键 |
| attach 痕迹 | JDK attach 临时文件 `%TEMP%\.attach_pid*` | 无（直接 `Agent_OnAttach`） | Zen 更隐蔽 |
| System property | 无 | `oz.instrumentation` | 可被反射枚举 |
| 线程名 | `KissScreenshotWatchdog` | 无特殊线程名 | 明显的特征 |
| 控制台输出 | `[KissClient] ...` 大量打印 | `logger` 写文件 | 发布版应关闭/降级 |

---

## 7. 对我们 Vape 的对照（能做 / 不建议做）

| 能力 | 现状 | 结论 |
|---|---|---|
| 手动映射注入 | 已有（`native/manual_map.h` + `reflective_loader.c`，4.21.49 验证） | 已达到 Zen 水平；可补 `LoadLibrary` 兜底之外的"映射失败原因码"，便于排查 |
| 无 `-javaagent` 挂载 | 已有（`Agent_OnAttach` 等价的 JVMTI 桥 `/ RetransformClasses`） | 无需搬 |
| 构建期混淆 | **可选**（`-PnoObfuscation`；Release 默认不混淆） | 若担心"按类名/字符串扫描"，建议 Release 也默认混淆，或至少发布 `rename-mapping.txt`；当前 Release 是明文 `gg.vape.*`，是最容易命中的特征 |
| 旋转 GCD 量化 | Kiss 模式已实现（`KissRotationEngine`）；其他模式各自为政 | 可把 `snapToSensitivity` 收口成一个公共方法，SilentAura/AimAssist 共用 |
| Jump-reset 击退 | 有 `Velocity`/`JumpReset`/`WTap`，但没有 Kiss 的"方向记忆 + double jump + 落地冷却"组合 | 可借鉴（中风险，需实测） |
| KeepAlive 队列 | 无 | 可借鉴到 `FakeLag`/`Blink`（低风险，纯本地时序） |
| 网易专项绕过 | 无 | **不建议**：依赖网易内部类名/字段（版本一变就废），且属于绕过平台处置机制，与 Vape 离线定位/合规风险不符；仅作情报留档 |
| Disabler 类（Grim/ACA/Themis） | 无 | 若目标服是这些反作弊，需按服务端逐项实测；风险高，不建议默认开启 |

---

## 8. 方法与局限

- Kiss：1.7.3 发布 jar 混淆（静态只能看形态）；行为细节用 KissV1.4 可读源码（`kiss/源码`，1.20.1 Forge）——两者映射关系靠行为比对，不保证 1.7.3 与 1.4 完全一致。
- NetEase 客户端模块本身未随附，只能看到 Kiss 反射访问的类/字段名与调用顺序，**无法确认**这些字段在目标版本的真实语义（例如 `replyHashMap` 是静态还是实例字段，Kiss 用"先静态再实例"的兜底）。
- Zen：源码完整（`OpenZen/`），但 `OpenZen-master.zip` 版本与 README 描述的构建混淆实现可能略有差异，以 `build.gradle` 实际内容为准。
- 未做运行验证：本报告是静态代码分析，未在网易客户端/目标反作弊服务器上实测；`NeteaseBypass` 在非网易客户端会打印 `absent (non-NetEase)` 并安全跳过。

## 9. 关键文件索引

- Kiss 网易绕过：`kiss/源码/com/kiss/bypass/NeteaseBypass.java`（167 行）、`com/kiss/bypass/ReplyFactory.java`（62 行）
- Kiss 注入：`com/kiss/attach/KissAttach.java`、`com/kiss/preload/Preloader.java`（305 行）、`com/kiss/natives/CoreNative.java`、`com/kiss/KissMod.java`、`com/kiss/Main.java`
- Kiss 反反作弊模块：`risky/killaura/RotationEngine.java`、`risky/Velocity.java`、`risky/MoreKB.java`、`risky/HitSelect.java`、`world/BackTrack.java`、`network/PacketBlinkHandler.java`、`network/PacketManager.java`
- Zen 注入：`OpenZen/native/loader/src/manual_map.cpp`、`injector.cpp`；`OpenZen/native/dll/src/{jvm_attach,class_loader,jar_extract,main}.cpp`
- Zen 补丁框架：`OpenZen/src/main/java/asm/patchify/**`（`PatchAgent`、`PatchClassFileTransformer`、`PatchTransformer`）
- Zen 反反作弊：`OpenZen/src/main/java/shit/zen/modules/impl/exploit/Disabler.java`、`manager/LagManager.java`、`utils/rotation/Rotation.java`、`utils/game/RotationUtil.java`、`utils/misc/PacketUtil.java`、`modules/impl/combat/AntiKB.java`
