# Vape 4.21 Product Recovery

> **语言 / Language:** 中文 | [English](README_EN.md)
>
> 发布页：[Releases](https://github.com/RSSeeker/Vape-v4/releases)
>
> 源代码来源：[OpenVapeCN/OpenVape](https://github.com/OpenVapeCN/OpenVape)
> （本项目基于该公开仓库的源代码进行恢复、整理与本地化）。

Vape 4.21 的 Java 层与 Windows x64 原生桥接层研究性恢复工程，附带完整中文本地化。

## 免责声明与版权说明

本项目为**教育研究性质**的逆向工程恢复项目，旨在进行软件兼容性分析与技术研究。

- 仅限教育、研究与自有授权环境测试用途
- 使用本项目前，**必须已拥有 Vape 的合法授权**
- 使用者需自行确保遵守当地法律、软件许可协议及服务器规则
- 项目作者不对使用者的违规行为承担任何责任

## 独立性声明

本项目为个人自发进行的独立研究工作：

- 未接受任何形式的外部资助、赞助或商业合作
- 不代表、不隶属于任何组织、团体或个人
- 与任何政治立场、意识形态或商业利益无关
- 项目内容仅反映作者个人的技术研究兴趣

项目由个人独立发起并维护，所有观点与成果仅代表技术研究层面，不涉及任何其他立场。

## 安全性声明

本项目仅通过本 GitHub 仓库与 B站账号 [RS_Seeker](https://space.bilibili.com/649086235) 视频附带资源发布源码与构建产物，以上渠道发布的文件可保证安全性。
任何在其他 GitHub 仓库、Discord、QQ群、Telegram群、第三方网站或网盘等渠道发布的 "Vape" 相关程序安全性均无法保证。本项目不对这些程序及其可能造成的损失承担任何责任。

## 主要产物

| 文件 | 说明 |
| --- | --- |
| `Vape-v4.21.*.exe` | GUI 单文件加载器（内嵌完整 DLL 与 Java 载荷 + 图标） |

**可选外部 DLL**：exe 旁放置 `Vape-v4.21Native.dll` 时**优先加载外部 DLL**（便于自行替换/更新原生层）；无外部 DLL 时自动解压内嵌版本，免附带文件。

**使用方式**：

- 双击运行 → GUI 界面（窗口标题「Vape v4」），直接选择 Minecraft 进程注入，无需登录
- 命令行注入器：`Vape-v4.21.*.exe -nogui [pid]` —— 不带 pid 弹出进程选择器，带 pid 直接注入
- 注入完成后游戏中按 右Shift（默认）打开功能界面

## 特性

**GUI 加载器**

- 集成上游 VapeLoader 图形界面（GDI+ 自绘，全中文）：进程选择 / 注入进度 / 加载完成
- 去掉登录页与缓存询问页：启动即选进程注入，本地生成 token
- **支持加载外部 DLL**：exe 旁存在 `Vape-v4.21Native.dll` 时优先注入外部 DLL；否则从 exe 内嵌资源解压注入，两种模式均免附带文件
- 窗口标题「Vape v4」，图标与产品一致
- 文件落盘全部收拢进 exe 旁隐藏的 `.vapeclient` 目录（解压的 DLL/JAR、日志、配置、服务数据、纹理缓存）

**注入方式**

- 默认使用 **manual map（内存手工映射）** 注入：注入器把 DLL 内容写入目标进程并自行完成节映射、重定位、导入解析与节权限设置，再调用入口点，**不经过 `LoadLibrary`**——因此在 Minecraft 进程的模块表里看不到本 DLL（Process Explorer、`EnumProcessModules` 均枚举不到），也不会产生 image-load 类加载痕迹
- **失败自动回退**：手工映射任一步失败即退回传统 `CreateRemoteThread` + `LoadLibraryW` 方式，可用性优先；两种方式加载的是同一份 DLL，游戏内功能、性能与版本兼容性完全一致
- 与「编译时类名混淆」叠加使用（自行编译时默认开启）：注入痕迹 + 随机类名，降低按特征匹配的概率；发布包不混淆，详见常见问题第 10 条

**动态模糊（MotionBlur）**

- **HUD 模块「动态模糊」**（显示/Game 分组，与方块彩色边框同区域）：帧混合后处理，开启后根据视角移动产生残影拖尾效果
- 可调项：模糊强度（默认 5，×2 强度，上限 0.95）、速度自适应（随镜头移动速度增强）、柔和模糊、帧率调制、灰度拖影、菜单 / 游戏菜单中是否生效
- 支持 **1.17+ 至 26.1** 的 Vanilla / Forge / NeoForge / Fabric 运行时（26.2 的渲染管线无帧末钩子暂不支持）
- 渲染时机与 GL 状态经专门适配：在 `RenderTarget.blitToScreen` 出口（画面已呈现到屏幕、swap 前）执行，并同步游戏 `GlStateManager` 缓存，避免字体/贴图采样错乱

**功能集成**

- 内嵌 **VapeService**（HTTP 8080 + Zeus TCP 8091 配套服务），游戏内自动后台启动：
  - 账号 / 设置 / 配置档 / 好友 / 小队 / 位置共享等在线功能本地跑通
  - 服务数据存于 exe 旁 `.vapeclient\vape-service.json`，与本地配置（`.vapeclient\config.json`）分离存储，互不冲突
  - 端口占用自动向上探测空闲端口；启动失败静默降级，不影响游戏
  - 支持环境变量配置（见下文「内嵌服务配置」）

**本地化**

- 语言包扩充至 2600+ 键，模块名、值名、提示、教程、确认框、药水/物品名全覆盖
- 默认语言为中文；语言选项精简为「中文 / English」
- 模块搜索同时匹配英文名与中文翻译名，中文可直接搜到模块
- 分类导航显示「其他」分类，Other 分类模块（如不释放物品）可直接浏览

**字体与显示**

- `noto.ttf` 为覆盖全部翻译字符的 Noto Sans SC 静态子集（SemiBold 600 字重）
- 注入器控制台中文化并启用 UTF-8 输出

**工程与稳定性**

- **26.2 图形后端**：26.2 引入 Vulkan 后端，Vape 基于 OpenGL，需将图形 API 切换为 OpenGL 后使用（详见下方兼容性说明）
- 新增 1.21.0+ / 26.x 渲染管线适配（`blitToScreen` 帧末钩子），动态模糊等后处理可跨版本工作
- 版本探测增强：区分 Vanilla / Forge / NeoForge / Fabric 运行时，避免旧版 Fabric 误判（1.20.1-Fabric 除外，见兼容性表）
- 配置本地持久化：模块设置、配置档、好友、框架位置保存至 `.vapeclient\config.json`，自动保存 + 退出兜底
- 原生日志与 Java 日志统一到 `.vapeclient\log\`，每次注入生成独立日志文件
- 单文件注入器：`Vape-v4.21.*.exe` 内嵌完整 DLL 与 Java 载荷

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
| 1.21.10 | - | ✓ | - |
| 1.21.11 | ✓ | ✓ | ✓ |
| 26.1.2 | ✓ | ✓ | ✓ |
| 26.2 | △ 实验性 | △ 实验性 | △ 实验性 |

**1.21.10 仅支持 Forge 60.x 与 NeoForge（NeoForge 与 Forge 同属一行），原版与 Fabric 不支持**。

**1.16.5 为实验性适配，可能存在以下问题**：

- 部分映射、渲染和模块功能可能无法正常工作
- 1.16.5 原版仅暴露 getInstance() 单例（无静态 instance 字段），部分静态字段映射可能缺失
- 若遇崩溃请反馈日志

**1.20.1 / 1.21.1 为实验性适配，可能存在以下问题**：

- 1.20.1 / 1.21.1 的 **Fabric 运行时不受支持**（Fabric Knot 类加载隔离 + slf4j 冲突），
  请使用对应版本的 Forge / NeoForge
- 部分 HUD 覆盖层（如生命值覆盖层）定位可能异常（渲染位置偏移，如出现在右下角）
- 若遇崩溃请反馈日志

## 已知难以修复的问题

4.21.41 已修掉 1.20.1 的接线问题（投影矩阵映射、混合状态、绘制顺序）：**ESP 2D/3D 方框、射线（Tracers）、箭头方向、GUI 黑底均已恢复**。
下列问题仍然存在，根因主要是 **1.20.1 的映射与代码目标版本（1.21.x / 26.x）差异较大**（文字组件构造、方块实体表、模型 getter 等），**修复难度高、暂未解决**：

**1.20.1（Forge / NeoForge）**
- **世界文字不显示**：物品标签框内文字、NameTags（名牌）、Indicators 与箭头的距离文字。文字组件构造桥在 1.20.1 取不到，当前改为「取不到就不画字、不抛异常」，后续按屏幕空间 2D 绘制补齐
- **箱子ESP（StorageESP）与寻找方块（Search）不显示**：容器 ESP 依赖的方块实体表映射在 1.20.1 取不到，渲染异常被静默吞掉
- **药水图标 / 盔甲（装备）图标不显示**：`getBakedModel` / `getSpriteTexture` 等映射在 1.20.1 失效，当前以优雅跳过处理（不崩、不卡，但不显示）

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

## 常见问题

### 1. Loader 看不到游戏

Loader 依赖 Java 进程和可见窗口标题来发现游戏，只有窗口标题包含 `Minecraft`、`Lunar` 或 `Feather` 的进程才会被列出。

请等待游戏进入主菜单，确认使用 64 位 JVM，并核对所选 PID、游戏版本与运行环境。支持 1.7.10、1.8.9、1.12.2、1.21.11 和 26.2 的 Vanilla，1.21.10 的 Forge / NeoForge，以及 1.21.11 / 26.2 的 Fabric。不要向标题相似的启动器辅助 JVM 或其他 Java 程序注入。

### 2. Loader 选择游戏进程后卡死

若该 Minecraft 进程已被注入过 Vape，无论第一次 Vape 是否加载成功，第二次注入同一进程时 Loader 都会卡死。

若第一次注入失败，请重启 Minecraft 后重新注入。

### 3. Loader 提示 Vape421Native.dll was not found beside the Loader

该提示指的是 Loader 同目录下的 `Vape-v4.21Native.dll`（即「主要产物」一节所述的可选外部 DLL）：DLL 必须与 Loader 放在同一目录下。

### 4. Loader 提示 Product DLL did not acknowledge… 或 Unexpected…

Vape 4.21 支持 1.7.10、1.8.9、1.12.2、1.21.11 和 26.2 的 Vanilla，1.21.10 的 Forge / NeoForge，以及 1.21.11 / 26.2 的 Fabric；其他 Fabric 版本、以及 1.21.10 的原版 / Fabric 不在当前支持范围。

出现该提示说明 Vape 注入失败：请确认 Minecraft 是否为受支持的版本，重启游戏后重新注入。若问题依旧，请检查 `vape421-native.log` 并反馈问题。

### 5. Loader 显示 Finished Loading，但游戏中没有出现 Finished Loading 提示

说明 Vape 注入失败：请确认 Minecraft 是否为受支持的版本，重启游戏后重新注入。若问题依旧，请检查 `vape421-native.log` 并反馈问题。

### 6. 出现 injection error code，或字段 / 方法映射失败、客户端崩溃

请确认 Minecraft 是否为受支持的版本，重启游戏后重新注入。若问题依旧，请检查 `vape421-native.log` 并反馈问题。

### 7. ClickGUI 能打开，但无法点击、鼠标位置错误或按键卡住

请提交 Issue 反馈，并详细描述问题出现的时机与复现步骤。

### 8. Render 模块导致 OpenGL 1283、HUD 消失、画面损坏或崩溃

Minecraft 1.16.5 及其他未列出的 1.16+ 版本兼容性仍然不佳。请使用下列已验证的组合：

- 1.21.11 Forge：Forge 61.0.8（已验证）
- 1.21.11 Fabric：Fabric Loader 0.19.3
- 26.2 Forge：Forge 65.1.0（已验证）
- 26.2 Fabric：Fabric Loader 0.19.3

### 10. Release 里的类名为什么不是随机的？

Release 发布包按原类名编译，**不做类名混淆**，方便核对与排查。想要每次构建都得到随机的包名与类名，请自行编译：本地执行 `gradlew.bat build verifyInjectionPayload prepareInjectionBundle` 时默认开启编译时混淆（所有自有的 `gg.vape.*` 类会重命名为随机 16 位名字，被 native 硬编码的三个类保留原名），每次构建结果都不同，映射表写入 `build/rename-mapping.txt`，用于反混淆崩溃日志。做法与 Zen（OpenZen）README 的「编译时类名混淆」一节一致。

### 9. 配置消失、保存失败、好友或小队（Party）状态异常

VapeService 使用单个本地 JSON 文件保存账户与配置，数据路径相对于启动目录 —— 换目录启动会看起来像创建了全新账户。文件损坏、无写权限、磁盘已满、同时运行多个 Service 实例或异常退出都可能影响保存。同一账户的新 Zeus 连接会替换旧连接；Service 重启、系统休眠或网络切换后，高频在线状态也不会自动恢复。

建议始终使用明确的 `--data-file` 路径并定期备份，同时避免两个 Service 实例写同一文件。当前 Service 是实验性兼容层：部分公共配置、评论、举报、标签和分页行为为简化实现，连接成功不等于所有原版 Online 功能完整可用。

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

完整测试包输出到 `build/injection/`（文件名随项目版本号变化，如 `Vape-v4.21.*.exe`）：

```text
Vape-v4.21.*.exe   GUI 单文件加载器（内嵌 DLL 与全部资源）
README.md
```

DLL 将 Java injection JAR 作为 `RCDATA` 嵌入，不要求另行放置 payload。原生桥接层恢复
样本的 `RegisterNatives` 接口表，另将样本未实现声明的 native 方法注册为安全占位桩，
避免 `UnsatisfiedLinkError`。更多细节见 [`native/README.md`](native/README.md)。

## 隔离环境运行

启动使用 64 位 JVM 的受支持 Minecraft 实例（包括 1.21.11、26.1.2、26.2 Fabric）后，直接运行 `Vape-v4.21.*.exe` 打开 GUI，选择 Minecraft 进程点击注入（无登录、
无外部 DLL）。

也可用命令行方式注入：

```powershell
# 指定进程 ID 注入
.\Vape-v4.21.*.exe -nogui <pid>
# 不带 pid：弹出自动刷新的 Java 窗口选择器（↑/↓ 选择，回车注入，Esc 退出）
.\Vape-v4.21.*.exe -nogui
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
