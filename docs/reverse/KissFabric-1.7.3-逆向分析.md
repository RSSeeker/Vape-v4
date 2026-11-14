# KissFabric 1.7.3 逆向分析报告

- 分析对象：`KissFabric-1.7.3.jar`（工作区根目录，另有 `C:\Users\Jiang\Downloads\KissFabric-1.7.3.jar` 同名副本）
- 文件大小：19,416,638 字节
- SHA-256：`d7e2c6158bc70ab970cfce135d14483b88d03ecd60036f94b72c451e9fb54716`
- Jar 内 `fabric.mod.json` 版本号：`1.0-SNAPSHOT`（文件名里的 1.7.3 是发行时的外部标记）
- 分析方式：**纯静态逆向**（未运行该 Mod）。反编译 + 自写字符串反混淆。
- 分析日期：2026-10-30

---

## 1. 结论摘要

1. 这是一个面向 **Minecraft 26.2 / Fabric Loader 0.19.3 / Java 25** 的客户端作弊 Mod（"KissClient"），作者署名 `B站LastKissss`，MIT 协议，jar **未签名**。
2. 功能规模较大：**147 个功能模块**（6 大类）、**810 处设置项构造**、**31 种事件**、**47 个 Mixin 目标类、约 63 个被 Hook 的方法**，反编译后非 Skia 部分约 **19.3 万行** Java。
3. 核心业务是 **PvP/移动类作弊**：KillAura、LegitAura、CrystalAura、AutoMace、Scaffold、Velocity、BackTrack、Blink、Disabler、ChestStealer 等；渲染类包含 ESP/Chams/NameTags/Cape、ClickGui 与 HUD。
4. 网络层通过 `Connection.send/channelRead0` 的 Mixin 实现 **静默旋转（silent rotation）**、**发包取消重写**、**Blink 缓存延迟发包**，并对 `AcceptTeleportation/KeepAlive/Pong` 等协议关键包做白名单，避免被服务器踢出。
5. 混淆较专业：标识符重命名 + 控制流平坦化（362 处/124 文件）+ 不透明谓词（839 处）+ **三套字符串加密方案**（`~` 前缀 XOR 逐位解密、LCG 伪随机生成、常量折叠 `char[]`）。本报告已完整还原解密算法，并用自写解释器还原 **5122 条** 运行时拼接字符串（去重后 2753 条完整串）。
6. **无任何联网、授权、HWID 或遥测代码**；配置全部保存在本地 `C:\ProgramData\KissConfig`。唯一"网络"用法是脚本引擎的本地 `URLClassLoader`。
7. **安全提示**：内置脚本引擎会在运行时用 JDK 编译器**编译任意 `.java`**（`/kiss script reload`），脚本目录位于 `C:\ProgramData\KissConfig\Script`（共享路径），相当于一个"可落地的任意代码执行入口"，请只从可信来源获取本 Mod 及其脚本。

---

## 2. 样本识别信息

### 2.1 `fabric.mod.json`

```json
{
  "schemaVersion": 1,
  "id": "kissclient",
  "version": "1.0-SNAPSHOT",
  "name": "KissClient",
  "description": "Hello",
  "authors": ["B站LastKissss"],
  "license": "MIT",
  "environment": "client",
  "entrypoints": { "client": ["r.a"] },
  "mixins": ["kf.mixins.json"],
  "depends": {
    "fabricloader": ">=0.19.3",
    "fabric-api": "*",
    "minecraft": "~26.2",
    "java": ">=25"
  },
  "jars": [
    { "file": "META-INF/jars/skija-shared-0.143.17.jar" },
    { "file": "META-INF/jars/skija-windows-x64-0.143.17.jar" },
    { "file": "META-INF/jars/types-0.2.0.jar" }
  ]
}
```

### 2.2 `META-INF/MANIFEST.MF` 关键字段

| 字段 | 值 |
| --- | --- |
| Fabric-Mapping-Namespace | official |
| Fabric-Gradle-Version | 9.5.1 |
| Fabric-Loom-Version | 1.17.19 |
| Fabric-Minecraft-Version | 26.2 |
| Fabric-Loader-Version | 0.19.3 |
| Fabric-Mixin-Version | 0.17.3+mixin.0.8.7 |
| Fabric-Mixin-Compile-Extensions | 0.6.0 |
| Fabric-Tiny-Remapper | 0.14.0 |

- **没有** `META-INF/*.SF` / `*.RSA` / `*.DSA`：jar 未做代码签名。
- `META-INF/jars/` 内嵌 3 个 jar：Skija（Skia 的 Java 绑定）shared + `windows-x64` 原生库 + `types`。也就是说界面层依赖内嵌的 Windows 原生 Skia `.dll`。

### 2.3 规模统计（反编译后）

| 指标 | 数值 |
| --- | --- |
| class 文件数 | 508 |
| 反编译 `.java` 文件数 | 637 |
| 总行数 | 221,876 |
| 其中 Skija 绑定 | 28,918 行（224 文件） |
| 业务/框架代码 | **192,958 行** |
| 功能模块 | **147** |
| 设置项构造 | 810（Boolean 398 / Number 333 / Mode 65 / Text 11 / Keybind 3） |
| 事件类 | 31 |
| Mixin 类 | 47（42 个 client 端 + 5 个 common），分布在 **30 个目标类**；约 63 个方法 Hook 条目（另有模块类 `gdp/g/av.java` 位于同包但非 Mixin） |
| 被 Hook 方法 | 约 63 |
| 反混淆还原字符串 | 5122 条（去重完整串 2753） |

### 2.4 包结构

| 包 | class 数 | 作用 |
| --- | --- | --- |
| `com.kiss` | 框架 | 入口、模块管理、事件总线、设置、配置、命令、脚本引擎、UI |
| `io.github.humbleui.skija` | 224 文件 | Skija/Skia Java API（内嵌依赖） |
| `gdp.g` | 48 | **Mixin 包**（`kf.mixins.json` 的 package），多数 Hook 在此 |
| `bc` | 54 | 战斗类模块及其支撑（CrystalAura、LegitAura 一部分等） |
| `dh`, `qs`, `ur`, `ss`, `asf`, `gp`, `ie`, `msb`, `w`, `ocg`, `bhx` … | 11–21 各自 | 混淆后的各功能模块/工具类 |
| `r`, `lfh.ty`, `in`, `i.ur.vz` | 少量 | 命令实现（help/toggle/bind/config） |

> 说明：混淆把包名压成 1–3 个字母，例如 `g/pbd/b.java`（KillAura，10,988 行）、`ie/c.java`（LegitAura，8,738 行）、`bc/ysg/a.java`（CrystalAura，5,853 行）、`asf/e/rqc/c.java`（Disabler，4,532 行）、`bhx/e.java`（ChestStealer，4,433 行）。

---

## 3. 启动与生命周期

### 3.1 入口

```java
// r/a.java
package r;
public class a implements ClientModInitializer {
   public void onInitializeClient() {
      Main.INSTANCE.modRun();
      try {
         ScriptCommand.register();
      } catch (Throwable var2) {
         System.out.println("[KissScript] command init failed: " + var2);
      }
   }
}
```

### 3.2 `com/kiss/Main.java`

```java
public void modRun() {
   this.moduleManager = new ModuleManager();
   this.moduleManager.getModule(b.class).setEnable(false);   // ClickGui 强制关闭
   if (!...isEnable()) {
      ConfigManager.loadInitial(moduleList, (a, b) -> {});
      mcEnvironment = Main.McEnvironment.FABRIC_OBF;
      this.commandManager = new CommandManager();
      this.skiaManager = new SkiaManager();
      qs.ysn.d.getInstance();                                // 旋转/封包管理器单例
      System.out.println(<加密 banner>);
   }
}
```

`detach()`（Minecraft 关闭时由 `gdp/g/ab.java` 调用）依次：禁用所有模块、`ConfigManager.suspendSave(true)`、`moduleManager.cleanup()`、`skiaManager.cleanup()`、清空封包管理器、`INSTANCE = null`。

`enum McEnvironment { VANILLA_OBF, FORGE_OBF, NON_OBF, FABRIC_OBF }` 说明代码库原本是一套跨加载器/跨版本的客户端框架，当前以 `FABRIC_OBF` 模式运行。

---

## 4. 模块系统

### 4.1 注解与分类

```java
@ModuleInfo(
   name = { @Text(label = "<加密串>", language = Language.English),
            @Text(label = "<加密串>", language = Language.Chinese) },
   category = Category.MISC,
   key = 0
)
public class e extends AbstractModule { ... }
```

`Category` 枚举：`PERMIT("Permit") / MISC("World") / RENDER("Render") / COMBAT("Legit") / RISKY("Risky") / MOVEMENT("Move")`，界面显示顺序为 COMBAT → MOVEMENT → RENDER → MISC → RISKY → PERMIT。

分类分布：

| 枚举 | 显示名 | 数量 |
| --- | --- | --- |
| COMBAT | Legit（战斗） | 25 |
| MISC | World（杂项） | 28 |
| MOVEMENT | Move（移动） | 18 |
| PERMIT | Permit（辅助） | 27 |
| RENDER | Render（渲染） | 26 |
| RISKY | Risky（风险） | 23 |
| **合计** | | **147** |

完整清单（含中英文名、类文件、设置项数量）见 **[`KissFabric-1.7.3-模块清单.md`](./KissFabric-1.7.3-模块清单.md)**。

### 4.2 代表模块

| 模块 | 中文 | 类 | 说明（静态代码观察） |
| --- | --- | --- | --- |
| KillAura | 杀戮光环 | `g/pbd/b.java` | 最大类（10,988 行），目标筛选/攻击节奏/多模式 |
| LegitAura | 合法光环 | `ie/c.java` | 8,738 行，平滑瞄准与点击 |
| CrystalAura | 水晶光环 | `bc/ysg/a.java` | 末地水晶放置/引爆，5,853 行 |
| AutoMace | 自动重锤 | `qs/ysn/a.java` | 依赖 "Mace Keywords" 判定目标 |
| AutoAim | 自动瞄准 | `ur/m/a.java` | 21 个设置项：Target Mode/Aim Mode/Aim X-Y/Range/Smooth… |
| Disabler | 反误判 | `asf/e/rqc/c.java` | Grim/ACA 反作弊针对性绕过 |
| ChestStealer | 箱子窃取 | `bhx/e.java` | 按物品类别过滤（Sword/Throw/Rod/Axe/Pickaxe/Gapple/Totem/Blocks/Bow & Arrows） |
| Scaffold | 搭路 | `ie/d.java` | Normal/Telly Bridge/Berry Bridge 模式 |
| Velocity | 反制 | `msb/j/c.java` | JumpReset/NoXZ/Reduce，可调延迟百分比 |
| BackTrack / Blink / FakeLag | 回溯/闪现/假延迟 | `asf`、`ss`、`gp` 等 | 封包缓存与延迟发送 |
| Test | 行为记录 | `w/mf/r/d.java` | 把移动/采样数据按行写入文件（Scope: Moving Only/All） |
| ClickGui | 点击界面 | `bhx/b.java` | key=260，两套界面 `ClickGuiScreen`/`ClickGui2Screen` |

### 4.3 设置系统

`com/kiss/setting/Setting<T>` 抽象基类（name/value/level/display/children/visibleSupplier），`SettingManager` 用静态 `Map<AbstractModule, List<Setting<?>>>` 保存，构造时扫描 `@SettingInfo` 注解字段并通过 `Language.getLabel(...)` 取名。

具体类型：`BooleanSetting / NumberSetting / ModeSetting / ColorSetting / TextSetting / KeybindSetting`。其中 `NumberSetting(String name, double value, double min, double max, String precisePattern, SettingAttribute<Double>...)` 带精度模板（如 `"0.00"`）。

---

## 5. 事件总线

`com/kiss/eventapi` 提供 `EventManager / EventTarget / Cancellable / EventPriority / EventType` 等；`com.kiss.event.events` 共 **31** 个事件：

```
EventTick, EventRender2D, EventRender3D, EventKey, EventMouseButton,
EventPacketSend, EventPacketReceive, EventClientPacket,
EventPreMotion, EventPostMotion, EventPreMove, EventPrePlayerInput, EventPostPlayerInput,
EventMoveInput, EventStrafe, EventSlowdown, EventVelocityUpdate, EventJump,
EventBlockPlaced, EventBlockUse, EventPlayerAction, EventUseItem, EventUpdateHeldItem,
EventPrePick, EventPrePlace, EventDisconnect, EventWorldChange, EventEntityRemove,
EventHitSlowDown, EventBeforePlayerTravel, EventAddDynamicIsland
```

主循环挂接点（见 §6）：

- `Minecraft.tick` HEAD → 旋转管理器 `tick()` + `EventTick` + 恢复使用键状态
- `LocalPlayer.sendPosition` HEAD → `EventPreMotion` + 发包前处理（BridgeAssist 等）
- `Connection.send` HEAD → `EventPacketSend`（可取消）+ 静默旋转 + Blink 缓存
- `Connection.channelRead0` HEAD → `EventPacketReceive`（可取消）+ 观察器
- `LevelRenderer.render` / `Hud.extractRenderState` / `GameRenderer.render` → `EventRender3D` / `EventRender2D`

---

## 6. Mixin Hook 全景

`kf.mixins.json`：`required: true`、`package: gdp.g`、`compatibilityLevel: JAVA_25`、`defaultRequire: 1`，**42 个 client Mixin + 5 个 common Mixin = 47 个 Mixin 类，覆盖 30 个目标类**。

### 6.1 网络/封包

| Mixin | 目标 | 方式 | 方法 |
| --- | --- | --- | --- |
| `gdp/g/al.java` | `Connection` | `@Inject` HEAD, cancellable | `send(Packet)` |
| `gdp/g/i.java` | `Connection` | `@ModifyVariable` HEAD, argsOnly | `send` |
| `gdp/g/at.java` | `Connection` | `@Inject` HEAD, cancellable | `channelRead0` |
| `gdp/g/g.java` | `PacketProcessor$ListenerAndPacket` | `@Redirect` | `handle` |
| `gdp/g/h.java` | `ClientPacketListener` | `@Redirect` | `handleSetEntityMotion` |
| `gdp/g/j.java` | `ClientCommonPacketListenerImpl` | `@Inject` | `onDisconnect` |

关键代码（静默旋转，直接把出站包里的 yaw/pitch 换成伪造值）：

```java
// gdp/g/i.java
@ModifyVariable(method = {"send"}, at = @At("HEAD"), argsOnly = true)
private Packet<?> kissclient$applySilentRotation(Packet<?> packet) {
    return qs.ysn.d.getInstance().applyRotation(packet);
}
```

发包主 Hook（可取消 + 协议关键包白名单 + Blink 缓存）：

```java
// gdp/g/al.java（要点摘录）
@Inject(method = {"send(Lnet/minecraft/network/protocol/Packet;)V"},
        at = {@At("HEAD")}, cancellable = true)
private void kiss$onSend(Packet<?> packet, CallbackInfo ci) {
    EventPacketSend event = new EventPacketSend(packet);
    EventManager.call(event);
    if (event.isCancelled() && !qs.ysn.c.isQueued(packet)) {
        boolean protocolCritical =
               packet instanceof ServerboundAcceptTeleportationPacket
            || packet instanceof ServerboundKeepAlivePacket
            || packet instanceof ServerboundPongPacket;
        if (!protocolCritical) { ci.cancel(); }
    }
    // Blink：缓存一段时间内的包，延迟统一发送
    if (asf.e.rqc.e.getInstance().isBlinking()) {
        asf.e.rqc.e.getInstance().cacheBlinkPacket(packet, ci);
        return;
    }
    qs.ysn.d.getInstance().applyRotation(packet);
    r.d.getInstance().applyRotation(packet);
    qs.ysn.d.getInstance().prepareOutgoingMovement(packet);
    // 若内容被改写 → 重新发送并取消原包
}
```

收包侧：

```java
// gdp/g/at.java
@Inject(method = {"channelRead0"}, at = {@At("HEAD")}, cancellable = true)
private void kiss$onReceive(Packet<?> packet, CallbackInfo ci) {
    gp.rr.h.onPacketReceive(packet);
    ss.x.ga.a.observe(packet);          // AntiBots / 观察器
    EventPacketReceive ev = new EventPacketReceive(packet);
    EventManager.call(ev);
    if (ev.isCancelled()) { ci.cancel(); }
}
```

### 6.2 玩家/移动

| Mixin | 目标 | 方法 | 用途 |
| --- | --- | --- | --- |
| `gdp/g/y.java` | `LocalPlayer` | `sendPosition`, `raycastHitResult`, `aiStep`, `drop`, `onSyncedDataUpdated` | 发包前事件、BridgeAssist 射线重定向、静默使用物品 |
| `gdp/g/z.java` | `LocalPlayer` | `isSlowDueToUsingItem`, `itemUseSpeedMultiplier` | NoSlow |
| `gdp/g/k.java` | `Entity` | `moveRelative` | 移动输入修改 |
| `gdp/g/l.java` | `Entity` | `move(MoverType, Vec3)` | 位移修改/碰撞 |
| `gdp/g/m.java` | `Entity` | `shouldRenderAtSqrDistance` | 渲染距离（ESP） |
| `gdp/g/n.java` | `Entity` | `turn(double,double)` | 视角旋转拦截 |
| `gdp/g/v.java` | `LivingEntity` | `updateFallFlyingMovement` | 鞘翅/飞行移动修改 |
| `gdp/g/w.java` | `LivingEntity` | `jumpFromGround` | 跳跃修改（JumpReset 等） |
| `gdp/g/s.java` | `KeyboardInput` | `tick` | 输入注入 |
| `gdp/g/ag/ah` | `MultiPlayerGameMode` | `attack`, `useItemOn`, `releaseUsingItem` | 攻击/放置/使用拦截 |
| `gdp/g/aa` | `Minecraft` | `startAttack`, `continueAttack` | 攻击流程拦截 |
| `gdp/g/ab.java` | `Minecraft` | `tick`, `runTick`, `renderFrame`, `handleKeybinds`, `startUseItem`, `setLevel`, `close/run/stop`, `pauseGame` | 总生命周期 + ClickGui/键位 |

### 6.3 渲染

| Mixin | 目标 | 方法 | 用途 |
| --- | --- | --- | --- |
| `gdp/g/aj` / `ak` | `EntityRenderer` | `submitNameDisplay`, `getNameTag` | NameTags/昵称伪装 |
| `gdp/g/ai` / `d` | `LivingEntityRenderer` | `shouldShowName`, `extractRenderState`, `getRenderType` | 名牌、Chams、发光 |
| `gdp/g/b` / `c` | `CapeLayer` | Accessor + `submit` | 自定义披风 |
| `gdp/g/as` | `AvatarRenderer` | `extractRenderState` | 皮肤/披风渲染状态 |
| `gdp/g/o` | `GameRenderer` | `bobHurt`, `render` | NoHurtCam、世界渲染入口 |
| `gdp/g/t` | `LevelRenderer` | `render(...)` | 世界渲染事件 |
| `gdp/g/p` | `Hud` | `extractRenderState` | HUD/2D 渲染事件 |
| `gdp/g/q` | `GpuSurface` | `blitFromTexture` | GUI 纹理上传（Skia 合成） |
| `gdp/g/r` | `ItemInHandRenderer` | `submitArmWithItem` | 手部渲染（动画/透明） |
| `gdp/g/a` | `Camera` | `setRotation` | Freecam/第三人称相机 |
| `gdp/g/aq` | `Player` | `attack`, `causeExtraKnockback` | 击退增强/刀爆 |
| `gdp/g/ap` | `Player` | `getAttackStrengthScale` | 取消攻击冷却（NoHitDelay） |
| `gdp/g/am` | `ChatComponent` | `addClientSystemMessage` 等 | 聊天拦截/替换 |
| `gdp/g/an` / `ao` | `PlayerTeam` / `PlayerTabOverlay` | `formatNameForTeam`, `getNameForDisplay` | 队伍/列表名伪装 |
| `gdp/g/ae` / `af` | `KeyboardHandler` / `MouseHandler` | `keyPress`, `onButton` | 键鼠事件注入 |
| `gdp/g/ad` | `CommandSuggestions` | `updateCommandInfo` | 命令补全 |
| `gdp/g/e` / `f` | `ClientInput` / `ClientLevel` | Accessor/Invoker | 输入/世界访问 |

> 注：`gdp/g/av.java` 位于 Mixin 包内，但它不是 Mixin，而是 RISKY 分类的 **Swing（挥击）** 模块类。

---

## 7. 静默旋转与封包引擎

`qs/ysn/d.java`（单例，1,962 行）是核心：

- 字段：`public static boolean DIAG`、`fc871 = 25`、`fn496 = 2` 等；
- 方法：`applyRotation(Packet)`（改写包内 yaw/pitch）、`prepareOutgoingMovement(Packet)`（重写移动包）、`tick()`（每 tick 更新）、队列判定 `isQueued(Packet)`；
- 配套：旋转对 `p.ibr.zq.h`（`getYaw()/getPitch()`）、目标旋转 `ur/m/h.targetRotation / setTargetRotation / isRotating`。

行为链条：模块设置 target rotation → 每 tick 计算出"服务端应看到的"角度 → 通过 `@ModifyVariable` + `prepareOutgoingMovement` 把该角度写进出站 `ServerboundMovePlayerPacket`/`ServerboundUseItemPacket` 等 → 客户端本地画面不改（"静默"），仅服务端视角改变。

Blink（`asf.e.rqc.e`）：`isBlinking()` 为真时把包塞进缓存，等关闭 Blink 再统一发送；`protocolCritical` 白名单保证 `AcceptTeleportation/KeepAlive/Pong` 永不被延迟或取消，避免被服务器判定超时。

---

## 8. 配置系统

`com/kiss/config/ConfigManager.java` 关键常量：

```java
private static final Path CONFIG_DIR = Paths.get("C:\\ProgramData\\KissConfig");
private static final Path CONFIG_FILE = CONFIG_DIR.resolve("kiss_config.json");
private static final int  CONFIG_VERSION = 1;
private static final Path ACTIVE_FILE = CONFIG_DIR.resolve("_active.txt");
private static final long AUTO_SAVE_INTERVAL_MS = 3000L;
private static final Map<String,String> LEGACY_KEY_ALIASES =
        Map.of("HUD","Hud","List","ListModule","Block-In","BlockIn");
```

- 配置 JSON 形状：`{ "version": 1, "modules": { "<模块键>": { "enabled": bool, "keybind": int, "settings": { ... } } } }`。
- 支持多配置：`CONFIG_DIR` 下任意 `*.json` 都是配置档；`_active.txt` 记录当前档；原子写入（临时文件 + move）。
- 每 3 秒自动保存；关闭时 `suspendSave(true)`。
- 路径固定在 `C:\ProgramData\KissConfig`（**共享目录**，见 §12 安全提示）。

---

## 9. 命令与脚本引擎

### 9.1 点命令（聊天栏，以 `.` 开头）

`com/kiss/command/CommandManager` 拦截 `ServerboundChatPacket`：消息以 `.` 开头则取消原包，按空格切分后匹配注册命令；未命中输出 `Unknown command! Try .help if you're lost`。

已注册命令（别名由加密串还原）：

| 命令 | 用法 | 说明 |
| --- | --- | --- |
| `.help` | — | 列出所有命令：`<名称> §8» §7<usage>` |
| `.toggle <module>` | `.toggle <module>` | 开关模块，返回 `Toggled %s %s`（ON/OFF） |
| `.bind <module> <key/none>` | `.bind <module> <key/none>` | 绑定按键，`Bound %s to %s.` / `Removed bind for %s.` |
| `.config <save/load/list/remove> <name>` | `.config <save/load/list/remove> <name>` | 配置档管理（保存/加载/列表/删除） |

### 9.2 脚本引擎（重点）

- 客户端命令：`/kiss script reload|list|dir|auto`（Fabric 客户端命令，独立于 `.` 命令）。
- 脚本目录：`<ConfigManager 配置目录>/Script`，即 `C:\ProgramData\KissConfig\Script`；编译输出 `Script/buildScript`。
- 首次运行会把 resources 里的示例脚本释放到脚本目录（`.examples_released` 标记）。
- 实现：`ScriptManager`（563 行）用 `javax.tools.JavaCompiler` + `ToolProvider` 现场编译 `.java`，用 `ScriptClassLoader extends URLClassLoader` 加载 `script.*` 类，支持按源码 SHA 热重载；消息包括：
  - `当前 JVM 没有 Java 编译器（需要 JDK 而不是 JRE 运行游戏）`
  - `热重载：新增 %d / 修改 %d / 卸载 %d / 失败 %d`
- 示例脚本 `assets/kf/script/Fly.java`：

```java
package script;
@ModuleInfo(name = {@Text(label = "ScriptFly", language = Language.English),
                    @Text(label = "[示例脚本]飞行", language = Language.Chinese)},
            category = Category.MOVEMENT, key = GLFW.GLFW_KEY_P)
public class Fly extends AbstractModule implements ScriptModule {
   // NumberSetting speed 0.6 (0.05–3.0), verticalSpeed 0.4, glide, glideSpeed, stopOnDisable
   @EventTarget
   public void onPreMove(EventPreMove event) {
      // 基于 player.input.getMoveVector() 的飞行/滑翔逻辑
   }
}
```

**含义**：脚本 = 在游戏进程内编译并执行的任意 Java 代码（Mod 自身设计如此）。这既是扩展能力，也是供应链风险点。

---

## 10. 界面（Skia）

- `com/kiss/ui/system`：`Skia.java`（267 行，初始化/帧管理）、`SkiaManager`、`Render2D`（443 行，矩形/圆角/阴影/渐变/文字）、`RenderState`、`UIAdaptive`。
- 字体：`assets/kf/fonts/misans.ttf`（MiSans），`FontManager` + `SkiaFont/VanillaFont`。
- 效果：`BlurUtil`、`GlowUtil`、`FilterCache`；`GLState/GLStateStack` 管理 GL 状态。
- ClickGui：`bhx/b.java`（key=260），两套界面 `ClickGuiScreen` / `ClickGui2Screen`，主题项（`Theme Accent`、`Theme Gradient A/B`、`Theme Flow`、`Gui Style`、`Language`）。
- 资源：18 张披风 PNG（`birthday`、`blueprint`、`cobalt`、`dannybstyle`、`millionth-customer`、`minecon-2011/2012/2013/2015/2016`、`oxeye`、`prismarine`、`scrolls-champion`、`snowman`、`spade`、`turtle`、`valentine`），Cape 模块使用。
- `SkiaManager.render()` 会打印 `skia inited`，通过 Skija 把 UI 画到 GL 纹理再合成（`GpuSurface.blitFromTexture` 有 Mixin）。

---

## 11. 混淆与字符串还原（核心技术部分）

### 11.1 混淆手段

| 手段 | 观察 | 规模 |
| --- | --- | --- |
| 标识符重命名 | 包名 1–3 字母；字段如 `fp300/fc804`；类名 `a/b/c`；但覆写方法保留真实名（`onEnable`、`render`、`getInstance`） | 全量 |
| 控制流平坦化 | `int var1 = -1450080666; while (true) { switch (var1) { case -1450080666: ... var1 = 794769152; ... } }` | 362 处 / 124 文件 |
| 不透明谓词 | `if (-1002577890 * 4 != 284655736) { ... }`（恒真） | 839 处 |
| 字符串加密 A：`~` 前缀 XOR | 见 11.2 | 294 处 |
| 字符串加密 B：LCG 伪随机 | 见 11.3 | 命令/别名等 |
| 字符串加密 C：常量折叠 `char[]` | 见 11.4，配合 StringBuilder 分片拼接 | 5,807 个 `new char[]` |
| 字符串 switch 变形 | `switch` 以 hashCode 整数分发（如通知模式 Click/Note/Pickup/Toast） | 多模块 |

### 11.2 方案 A：`~` 前缀 XOR（名称类字符串）

```java
// ur/m/i.java
public static String mr999(String var0) {
   if (var0 != null && !var0.isEmpty() && var0.charAt(0) == (175034371 ^ 175034493)) { // == 126 '~'
      char[] var1 = var0.toCharArray();
      for (int var2 = 1; var2 < var1.length; var2++) {
         var1[var2] = (char)(var1[var2] ^ (-425956204 ^ -1180253275) + (var2 - 1));
      }
      return new String(var1, 1, var1.length - 1);
   }
   return var0;
}
```

- 前缀：`175034371 ^ 175034493 = 126`（`'~'`）
- 密钥：`-425956204 ^ -1180253275 = 1597681457`（`0x5F3AAF31`）
- 解密：对第 i 个字符（i≥1）执行 `c[i] ^= (1597681457 + (i-1))`，再丢弃首字符。
- 已验证：`~꽰꽇꽇꽛꽡꽙꽘꽔꽊` → `AutoTools`。

逆向时需注意：源码里的这类字面量是先经 javac 写成 `\uXXXX` 转义的（含孤立代理项），必须**先做 Java unescape 再解密**，否则中文会解出乱码（这是第一版脚本只有 1/10 标签解对的原因）。

### 11.3 方案 B：LCG 双流伪随机生成（命令/别名）

```java
// r/b.java 构造 "help"
int var1 = 1625065458;
int var2 = 274116845;
char[] var10007 = new char[]{
   (char)((var1 >>> 8 ^ var2 >>> 13) & 65535 ^ 24154),
   '\u0000','\u0000','\u0000'
};
var1 = var1 * 1103515245 + 24154;
var2 = var2 * 1664525 + 24154;
var10007[1] = (char)((var1 >>> 8 ^ var2 >>> 13) & 65535 ^ 19919);
// ... 每字符推进一次
super("", new String[]{ new String(var10007) });
```

特征：常量 `1103515245`（glibc LCG 乘子）与 `1664525`（Numerical Recipes LCG 乘子），每字符一个 XOR 常数；与方案 C 一样可用常量折叠直接算出。

### 11.4 方案 C：`new char[]` 常量折叠 + StringBuilder 分片

最普遍的一种：每个字符串被拆成若干"分片"，每片用 `(char)(<整数表达式>)` 构造，表达式里混着 `^ & >>> + -` 与 `'\u0000'` 占位；再用 `StringBuilder.append(new String(array))` 拼接，最后 `.toString()`。例如：

```java
var10006 = new char[]{
   (char)((var1 >>> (-1465514367 ^ -1465514359) ^ var2 >>> (-1448656793 ^ -1448656790)) & (-1824092185 ^ -1824100328) ^ -1937911373 ^ -1937938985),
   '\u0000','\u0000','\u0000'
};
var1 = var1 * (-1100425144 ^ -5335515) + (-981612108 ^ -981631536);
var2 = var2 * (-1274779060 ^ -1273164735) + (-549531879 ^ -549553283);
var10006[1] = (char)( ... );
```

因为要精确模拟 **Java 的 int/long 溢出、移位掩码（0x1f/0x3f）、char 截断（&0xFFFF）**，用 javac 批量编译评估是最稳的（本报告对 2,903 条"纯常量"表达式直接 javac 求值，全部成功），但变量相关的分片（约 2,904 条）必须模拟执行语句序列。

### 11.5 本报告的反混淆流水线

| 步骤 | 脚本 | 结果 |
| --- | --- | --- |
| 反编译 | `vineflower 1.11.1`（`-dgs=1 -hes=1 -hdc=0 -bsm=1 -jrt=1`） | 637 java / 22.2 万行 |
| 扫描纯常量 `char[]` 表达式 | `pure1.py` | 2,903 条 |
| javac 批量求值 | `eval.py`（分批 60 个表达式编译执行） | 2,903/2,903 成功 |
| 自写 Java 表达式/语句解释器 | `jvm.py`（词法/类型化求值：int32/int64 回绕、Java 优先级、cast、append/toString、数组下标） | 支持变量、循环外语句、字段赋值 |
| 语句切分 + 执行 | `deobf.py` | 处理声明/赋值/`this.x=`/`case ->`/数组元素写入/`throw new RuntimeException(...)` |
| 全量运行 | `run_all.py` | **5,122 条**字符串（完整串 2,753） |

覆盖度自评：与 `Zh.java` 里 641 个已知英文名（模块/设置/模式/UI 词典）对账，精确命中 **395 个（61.6%）**；剩余多数是被拆散在"依赖字段/局部变量"的表达式里，或本就不在词典中。产物见：

- `build/kiss-tools/strings_raw.json`：每个文件的全部还原串（含中间分片）
- `build/kiss-tools/strings_full.json`：剔除分片后的完整串
- `build/kiss-tools/modules.json` / `mixins.json` / `mixin_table.json` / `zh.json`
- 本报告附带的 `KissFabric-1.7.3-字符串汇编.md`（按文件分组的高价值串）

---

## 12. 反检测 / 针对性绕过

从还原的设置名与类结构可见明显的"反反作弊"取向：

- **Disabler**（`asf/e/rqc/c.java`，4,532 行）设置项包括：
  - `Grim Bad Packets`（含 A/F/G/D/Y/X 等变体）
  - `Grim Duplicate RotPlace`
  - `Grim AimModulo360`
  - `Grim Transaction Order`
  - `Grim Packet Order`
  - `ACA Fast Switch` / `ACA Inventory Frequency` / `ACA Aim Step`
  （Grim / ACA 均为常见服务端反作弊；这些选项直接针对其检测规则）
- **静默旋转**：出站包角度伪造而本地视角不变，绕过基于"视角-攻击方向一致性"的检测（§7）。
- **协议关键包白名单**：`AcceptTeleportation/KeepAlive/Pong` 永不被 Blink 缓存或取消，避免超时踢出。
- **BackTrack / FakeLag / Blink**：延迟或缓存移动包，制造位置回溯窗口。
- **Velocity / NoSlow / KeepSprint / HitSelect / NoHitDelay**：修改击退、减速、攻击冷却与攻击目标选择。
- 渲染类 **Chams / ESP** 通过 `shouldRenderAtSqrDistance`、`extractRenderState`、`getRenderType` 等 Hook 实现穿墙/发光。

---

## 13. 安全与合规评估

| 项目 | 结论 |
| --- | --- |
| 联网/上报 | **无**。全库检索 `http(s)://`、`HttpClient`、`Socket`、`WebSocket`、`InetAddress` 仅命中 Skija 自带注释与脚本引擎的本地 `URLClassLoader`；无遥测、无崩溃上报。 |
| 授权/授权服务器 | **无**。无 License/HWID/机器码/到期时间/登录逻辑，"注册"类代码不存在。 |
| 数据落地 | 配置：`C:\ProgramData\KissConfig\kiss_config.json`；脚本：同目录 `Script\`；Test 模块会按设置把行为日志写入文件。 |
| Jar 签名 | **未签名**（无 `META-INF/*.SF`），无法校验来源/完整性；建议核对 SHA-256（本报告头）。 |
| 原生组件 | 内嵌 `skija-windows-x64-0.143.17.jar`（Windows 原生 Skia），会增加攻击面并绑定平台。 |
| 任意代码执行面 | **有**：脚本引擎在游戏进程内编译并执行 `.java`；脚本目录位于共享的 ProgramData 路径（默认 ACL 允许普通用户写入），任何能写该目录的程序都可借 `/kiss script reload` 在游戏内执行代码。 |
| 合规 | 该类客户端在多数服务器属于违规作弊工具，可能触发封禁；本报告仅作静态分析记录。 |

---

## 14. 复现步骤

```bash
# 1. 解包
mkdir -p /tmp/kiss && cd /tmp/kiss && unzip -q /d/vscode/VapeV4.21/KissFabric-1.7.3.jar

# 2. 反编译（Java 17 可反编译 Java 25 字节码）
curl -sL -k --ssl-no-revoke -o /tmp/vineflower.jar \
  https://repo1.maven.org/maven2/org/vineflower/vineflower/1.11.1/vineflower-1.11.1.jar
"/c/Program Files/Java/jdk-17/bin/java" -jar /tmp/vineflower.jar \
  -dgs=1 -hes=1 -hdc=0 -bsm=1 -jrt=1 -log=WARN /tmp/kiss /tmp/kiss-src

# 3. 反混淆字符串（本仓库脚本）
cd /d/vscode/VapeV4.21/build/kiss-tools
python run_all.py      # → strings_raw.json / strings_full.json
python report_data.py  # → module_strings.json + 覆盖率
python gen_docs.py     # → docs/reverse/*.md
```

脚本清单：`mods.py`（模块表）、`zh.py`（中词典）、`mixins.py`/`mixin_table.py`（Mixin 表）、`pure1.py`/`eval.py`（javac 求值）、`jvm.py`/`deobf.py`（解释器）、`run_all.py`、`coverage.py`、`stats.py`、`report_data.py`、`gen_docs.py`。

---

## 15. 局限与后续可做

1. 本次为**纯静态**分析，未实际运行 Mod，行为结论均来自代码路径（尤其反作弊绕过的实际效果未做动态验证）。
2. 标识符仍为混淆名（类/字段），本报告只建立了"模块→类文件"映射，未做批量重命名。
3. 字符串还原覆盖率 61.6%（相对内置中词典），仍有一部分依赖字段/局部变量的表达式未求值；若要 100% 还原，可扩展 `jvm.py` 支持字段初始器上下文、方法内控制流（if/loop 常量条件）与 `StringBuilder` 容量/对象别名。
4. 控制流平坦化未做反平坦化还原（不影响功能结论，但影响逐行阅读）。
5. 未分析 Skija 内嵌原生 DLL 的安全性（仅确认它是 Skia 的官方 prebuilt 版本号 0.143.17）。

---

## 附录

- [模块清单（147）](./KissFabric-1.7.3-模块清单.md)
- [反混淆字符串汇编](./KissFabric-1.7.3-字符串汇编.md)
- 原始数据：`build/kiss-tools/{strings_full,strings_raw,modules,mixins,mixin_table,zh,module_strings}.json`
