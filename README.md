[简体中文](README.md) | [English](README_EN.md)

# Vape 4.21 Product Recovery

Vape 4.21 的 Java 层与 Windows x64 原生桥接层研究性恢复工程，附带完整中文本地化。

> GitHub 仓库：[RSSeeker/Vape-v4.21](https://github.com/RSSeeker/Vape-v4.21) ·
> 发布页：[Releases](https://github.com/RSSeeker/Vape-v4.21/releases)
>
> 源代码来源：[OpenVapeCN/OpenVape](https://github.com/OpenVapeCN/OpenVape)
> （本项目基于该公开仓库的源代码进行恢复、整理与本地化）。

## 主要产物

| 文件 | 说明 |
| --- | --- |
| `Vape-v4.21.31.exe` | GUI 单文件加载器（内嵌完整 DLL 与 Java 载荷 + 图标），文件名随版本号变化（如 `Vape-v4.21.31.exe`） |

**可选外部 DLL**：exe 旁放置 `Vape-v4.21Native.dll` 时**优先加载外部 DLL**（便于自行替换/更新原生层）；无外部 DLL 时自动解压内嵌版本，免附带文件。

**使用方式**：

- 双击运行 → GUI 界面（窗口标题「Vape v4」），直接选择 Minecraft 进程注入，无需登录
- 命令行注入器：`Vape-v4.21.31.exe -nogui [pid]` —— 不带 pid 弹出进程选择器，带 pid 直接注入
- 注入完成后游戏中按 右Shift（默认）打开功能界面

## 特性

**GUI 加载器（v4.21.9+）**

- 集成上游 VapeLoader 图形界面（GDI+ 自绘，全中文）：进程选择 / 注入进度 / 加载完成
- 去掉登录页与缓存询问页：启动即选进程注入，本地生成 token
- **支持加载外部 DLL**：exe 旁存在 `Vape-v4.21Native.dll` 时优先注入外部 DLL；否则从 exe 内嵌资源解压注入，两种模式均免附带文件
- 窗口标题「Vape v4」，图标与产品一致
- 文件落盘全部收拢进 exe 旁隐藏的 `.vapeclient` 目录（解压的 DLL/JAR、日志、配置、服务数据、纹理缓存），**不向 `%TEMP%` 写任何文件**

**动态模糊（MotionBlur，v4.21.20+）**

- **HUD 模块「动态模糊」**（显示/Game 分组，与方块彩色边框同区域）：帧混合后处理，开启后根据视角移动产生残影拖尾效果
- 可调项：模糊强度（默认 5，×2 强度，上限 0.95）、速度自适应（随镜头移动速度增强）、柔和模糊、帧率调制、灰度拖影、菜单 / 游戏菜单中是否生效
- 支持 **1.17+ 至 26.1** 的 Vanilla / Forge / NeoForge / Fabric 运行时（26.2 的渲染管线无帧末钩子暂不支持）
- 渲染时机与 GL 状态经专门适配：在 `RenderTarget.blitToScreen` 出口（画面已呈现到屏幕、swap 前）执行，并同步游戏 `GlStateManager` 缓存，避免字体/贴图采样错乱

**功能集成（v4.21.8）**

- 合并上游新模块：**AutoMace**（自动重锤，含重锤选择 / 眩晕猛击 / 瞄准范围 / 自动卸下鞘翅 / 仅猛击 / 显示快捷栏）、**NoItemRelease**（不释放物品）、**PearlCatch**（接住珍珠）、**InventoryOverlay**（物品栏覆盖显示）
- 合并 Badlion 旧版按键事件队列，Badlion 客户端按键兼容
- 内嵌 **VapeService**（HTTP 8080 + Zeus TCP 8091 配套服务），游戏内自动后台启动：
  - 账号 / 设置 / 配置档 / 好友 / 小队 / 位置共享等在线功能本地跑通
  - 服务数据存于 exe 旁 `.vapeclient\vape-service.json`，与本地配置（`.vapeclient\config.json`）分离存储，互不冲突
  - 端口占用自动向上探测空闲端口；启动失败静默降级，不影响游戏
  - 支持环境变量配置（见下文「内嵌服务配置」）

**本地化**

- 语言包扩充至 2600+ 键，模块名、值名、提示、教程、确认框、药水/物品名全覆盖
- 默认语言为中文；语言选项精简为「中文 / English」
- 修复多行提示换行被压平、颜色码 `§` 丢失导致的翻译不匹配
- 下拉框 / 目标过滤器等运行时拼串的翻译（先查整串、未命中再逐段翻译）
- 模块搜索同时匹配英文名与中文翻译名，中文可直接搜到模块
- 分类导航显示「其他」分类，Other 分类模块（如不释放物品）可直接浏览
- mace 使用官方译名「重锤」；字库按更新后的翻译字符集重新子集化（含「锤」等新字形）

**字体与显示**

- `noto.ttf` 为覆盖全部翻译字符的 Noto Sans SC 静态子集（SemiBold 600 字重），经 stb（游戏实际渲染引擎）验证 0 缺字
- 自定义圆角图标嵌入 `Vape-v4.21.exe`
- 注入器控制台中文化并启用 UTF-8 输出

**工程与稳定性**

- **26.2 图形后端**：26.2 引入 Vulkan 后端，Vape 基于 OpenGL，需将图形 API 切换为 OpenGL 后使用（详见下方兼容性说明）
- 新增 1.21.0+ / 26.x 渲染管线适配（`blitToScreen` 帧末钩子），动态模糊等后处理可跨版本工作
- 版本探测增强：区分 Vanilla / Forge / NeoForge / Fabric 运行时，避免旧版 Fabric 误判（1.20.1-Fabric 除外，见兼容性表）
- 配置本地持久化：模块设置、配置档、好友、框架位置保存至 `.vapeclient\config.json`，自动保存 + 退出兜底
- 原生日志与 Java 日志统一到 `.vapeclient\log\`，每次注入生成独立日志文件
- 单文件注入器：`Vape-v4.21.exe` 内嵌完整 DLL 与 Java 载荷

### 它不是 Vape 官方源码、原始发布包或厂商签名产物，也不保证具备与原产品完全一致的行为。

> 本项目用于软件恢复、兼容性分析和自有环境测试。仅应在你拥有并获准测试的隔离实例中
> 使用，并自行确认当地法律、软件许可和服务器规则。

## Minecraft 兼容性

| Minecraft | Vanilla | Forge | Fabric |
| --- | :---: | :---: | :---: |
| 1.7.10 | ✓ | ✓ | - |
| 1.8.9 | ✓ | ✓ | - |
| 1.12.2 | ✓ | ✓ | - |
| 1.16.5 | △ 实验性 | - | - |
| 1.20.1 | △ 实验性 | △ 实验性 | - |
| 1.21.1 | △ 实验性 | △ 实验性 | - |
| 1.21.11 | ✓ | ✓ | ✓ |
| 26.1.2 | ✓ | ✓ | ✓ |
| 26.2 | △ 实验性 | △ 实验性 | △ 实验性 |

也支持 Lunar Client 与 Badlion Client 1.8.9 实例注入。

**1.16.5 为实验性适配，可能存在以下问题**：

- 部分映射、渲染和模块功能可能无法正常工作
- 1.16.5 原版仅暴露 getInstance() 单例（无静态 instance 字段），部分静态字段映射可能缺失
- 若遇崩溃请反馈日志

**1.20.1 / 1.21.1 为实验性适配，可能存在以下问题**：

- 1.20.1 / 1.21.1 的 **Fabric 运行时不受支持**（Fabric Knot 类加载隔离 + slf4j 冲突），
  请使用对应版本的 Forge / NeoForge
- 部分 HUD 覆盖层（如生命值覆盖层）定位可能异常（渲染位置偏移，如出现在右下角）
- 平滑字体初始化失败时会回退 legacy 字体渲染，偶发 GUI 边缘出现黑边
- 若遇崩溃请反馈日志

## 已知难以修复的问题

以下问题经大量排查，根因主要是 **1.20.1 的渲染/映射与代码目标版本（1.21.x / 26.x）差异较大**，
涉及多个核心映射错位（实体坐标、包围盒、相机旋转、PoseStack、投影 getter 等），**修复难度高、暂未解决**：

**1.20.1（Forge / NeoForge）**
- **ESP（2D/3D 方框、名字、骨架）不显示**：实体坐标/包围盒或投影在 1.20.1 取值异常，导致
  `ProjectedEntityBounds` 角点投影暴增、`onScreen` 恒为 false；已尝试旋转修正、包围盒方法访问器、
  `entity.b()` clamp、回退透视等多项修复，仍未彻底解决
- **射线（Tracers）不显示 / 仅 +Z 方向对齐**：起点/终点坐标的相机相对约定与 1.20.1 投影错位
- **箭头（Arrows）方向指示有偏差**（身前实体左右镜像）：投影旋转的 handedness 在 1.20.1 存在翻转
- **药水图标 / 盔甲（装备）图标不显示**：`getBakedModel`/`getSpriteTexture` 等映射在 1.20.1 失效，
  当前以优雅跳过处理（不崩、不卡，但不显示）

**其它模块**
- **宏功能（Macro）**：存在缺陷（具体表现待复现定位）
- **自动重锤（AutoMace）**：存在缺陷（具体表现待复现定位）

> 说明：上述 1.20.1 相关改动已在 v4.21.29 中**回退**，仅保留 1.20.1 的**注入支持**与**关闭菜单后 GUI
> 消失/卡顿**修复。1.20.1 目前作为**实验性支持**，推荐主力使用 **1.21.11 / 26.x**。


**26.2 为实验性适配，可能存在以下问题**：

- 注入后首条通知（"按右Shift打开GUI"）文字可能显示为方块，打开 ClickGUI 后字体恢复正常
- 26.2（Fabric）使用 RT 光线追踪渲染管线时，部分渲染钩子行为可能不稳定
- 部分 HUD 覆盖层 / 渲染元素位置可能略有偏差
- 若遇崩溃请反馈日志

**26.2 必须使用 OpenGL 图形后端**：

- 26.2 首次引入 Vulkan 图形后端。Vape 基于 OpenGL 渲染管线，**在 Vulkan 后端下无法工作**：
  注入时 GL 初始化会触发 JVM 致命错误（"No context is current"），或注入完成后 GUI 无法打开
- 请在视频设置中把「图形 API」切换为 **OpenGL**（或编辑版本目录下 `options.txt`，将
  `preferredGraphicsBackend` 改为 `"opengl"`），然后重启游戏再注入
- 26.1.2 及更早版本无此选项，不受影响

**对于 26.1.2 与 26.2 版本，请在进入服务器或单人世界后注入。**

所有目标实例均须使用 64 位 JVM。

## 内嵌服务配置

VapeService 在游戏内自动启动，默认监听 `127.0.0.1:8080`（HTTP）与 `127.0.0.1:8091`（Zeus TCP）。
可通过环境变量调整：

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `VAPE_BIND_ADDRESS` | `127.0.0.1` | 绑定地址；设为 `0.0.0.0` 可允许局域网访问 |
| `VAPE_HTTP_PORT` | `8080` | HTTP 端口 |
| `VAPE_ZEUS_PORT` | `8091` | Zeus TCP 端口 |
| `VAPE_DATA_FILE` | `<exe>/.vapeclient/vape-service.json` | 服务数据文件路径 |

客户端侧已有 `VAPE_ONLINE_BASE_URL` / `VAPE_ZEUS_ADDRESS` 覆盖服务地址，与上述变量配合
可实现局域网多端互联。

## 环境要求

仅编译和校验 Java 层需要：

- JDK 17，用作 Gradle toolchain；输出默认编译为 Java 17 字节码，传
  `-PtargetRelease=8` 可输出 Java 8 字节码（CI 构建即采用该参数）
- 项目自带的 Gradle Wrapper；构建脚本固定要求 Gradle 8.8
- 可访问 Maven Central 和 Gradle Plugin Portal 的网络连接

构建 native bundle 还需要：

- Windows x64
- Visual Studio 2022 C++ x64 工具链及 Windows SDK
- CMake 3.21 或更高版本
- 一套包含 JNI/JVMTI 头文件的 JDK；面向 1.7.10、1.8.9 和 1.12.2 测试时建议使用 JDK 8

## 快速开始

在 PowerShell 中进入仓库根目录：

```powershell
.\gradlew.bat clean build verifyInjectionPayload
```

该命令会完成以下工作：

1. 编译恢复源码并处理全部资源。
2. 检查源码数量以及残留的致命 CFR 反编译标记。
3. 生成包含运行时依赖的 injection JAR。
4. 确认载荷包含必要包，且所有 class 均可由 Java 8 加载。

主要 Java 产物位于 `build/libs/`。如需生成 IntelliJ IDEA 工程配置，可运行：

```powershell
.\gradlew.bat idea
```

## 构建原生测试包

```powershell
.\gradlew.bat prepareInjectionBundle -PtargetRelease=8 `
  -PnativeJavaHome="C:\Program Files\Java\jdk1.8.0_301"
```

完整测试包输出到 `build/injection/`（文件名随项目版本号变化，如 `Vape-v4.21.31.exe`）：

```text
Vape-v4.21.31.exe   GUI 单文件加载器（内嵌 DLL 与全部资源）
README.md
```

DLL 将 Java injection JAR 作为 `RCDATA` 嵌入，不要求另行放置 payload。原生桥接层恢复
样本的 `RegisterNatives` 接口表，另将样本未实现声明的 native 方法注册为安全占位桩，
避免 `UnsatisfiedLinkError`。更多细节见 [`native/README.md`](native/README.md)。

## 隔离环境运行

启动使用 64 位 JVM 的受支持 Minecraft 实例（包括 1.21.11、26.1.2、26.2 Fabric）或 Lunar
Client 实例后，直接运行 `Vape-v4.21.exe` 打开 GUI，选择 Minecraft 进程点击注入（无登录、
无外部 DLL）。

也可用命令行方式注入：

```powershell
# 指定进程 ID 注入
.\Vape-v4.21.exe -nogui <pid>
# 不带 pid：弹出自动刷新的 Java 窗口选择器（↑/↓ 选择，回车注入，Esc 退出）
.\Vape-v4.21.exe -nogui
```

注入器仅执行 `LoadLibraryW`。DLL 加载后会等待 JVM 与 Minecraft `Client thread`，通过其
上下文 ClassLoader 加载内嵌 JAR；Fabric 实例会通过 Fabric Launcher API 将载荷加入 Knot
ClassLoader。随后 DLL 注册原生方法，并调用
`gg.vape.runtime.NativeBridge.start()`。每次注入的日志位于注入器 EXE 同目录的
`.vapeclient\log\vape421-native-<pid>-<时间戳>.log`。

## 常用校验任务

| 命令 | 用途 |
| --- | --- |
| `.\gradlew.bat check` | 编译、源码覆盖与恢复质量检查 |
| `.\gradlew.bat injectionJar` | 构建自包含 Java 注入载荷 |
| `.\gradlew.bat verifyInjectionPayload` | 检查依赖完整性与 Java 8 字节码版本 |
| `.\gradlew.bat buildNative` | 构建 x64 DLL 和注入器 |
| `.\gradlew.bat prepareInjectionBundle` | 汇总可供隔离测试的 native bundle |

## 许可证

本仓库以 [CC0 1.0 Universal](LICENSE) 方式提供。在适用范围内，CC0 仅覆盖仓库贡献者
有权作出处分的内容；第三方库、商标、字体、纹理以及其他既有材料仍受其各自权利约束。

## 版本更新日志

详见 [CHANGELOG.md](CHANGELOG.md)。
