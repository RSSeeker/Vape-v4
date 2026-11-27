# Soulbound V1 — 产品恢复

> **语言 / Language:** 中文 | [English](README.md)
>
> Soulbound 发布页：[Releases](https://github.com/arsnexc/Vape-v421/releases) · [上游参考](https://github.com/RSSeeker/Vape-v4/releases)
>
> 源代码来源：[OpenVapeCN/OpenVape](https://github.com/OpenVapeCN/OpenVape)
> （本项目基于该公开仓库的源代码进行恢复、整理与本地化）。

Soulbound 是基于 Vape 4.21 恢复源码的独立研究性重命名版本，包含 Java 层、Windows x64 原生桥接层及中英文界面。

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

请核验下载来源、签名和哈希；任何发布渠道都不能保证安全。旧构建曾被报告出现 VirusTotal 检测，但未提供报告与检测名称；单次 ClamAV 未检出并不能证明安全。详见[安全调查](docs/security-investigation.md)。在独立调查之前请隔离被标记的文件，不要添加杀毒软件排除项。

## 数据路径与版本

新安装默认使用 exe 旁的 `.soulbound`。若只有旧目录 `.vapeclient`，则继续使用旧目录；两者同时存在时优先 `.soulbound`。不会移动或删除旧数据。所选目录中，若不存在 `soulbound-service.json`，服务会复用 `vape-service.json`；若不存在 `texture-cache`，纹理缓存会复用 `vapeTextures`。无原生启动器提供 `vape.directory` 时，配置回退到工作目录，服务与纹理缓存保留原来的 `user.home` 回退。测试前请备份。

以下示例以 V1 和 `.soulbound` 为例。版本由 `gradle.properties` 的 `soulboundVersion` 控制；具体构建及发布方法见[主文档](README.md#versioning-and-releases)。为兼容旧数据，`gg.vape.*`、JNI、配置档中的 `4.21` 等标识不会改名。

## 主要产物

| 文件 | 说明 |
| --- | --- |
| `SoulboundV1.exe` | GUI 单文件加载器（内嵌完整 DLL 与 Java 载荷 + 图标） |

**可选外部 DLL**：GUI 和控制台只接受与 exe 内嵌 DLL 逐字节一致的 `SoulboundNative.dll`；否则使用内嵌版本。此检查不能证明 exe 或 DLL 安全。

**使用方式**：

- 双击运行 → GUI 界面（窗口标题「Soulbound V1」），直接选择 Minecraft 进程注入，无需登录
- 命令行注入器：`SoulboundV1.exe -nogui [pid]` —— 不带 pid 弹出进程选择器，带 pid 直接注入
- 注入完成后游戏中按 右Shift（默认）打开功能界面

## 特性

**GUI 加载器**

- 集成上游 VapeLoader 图形界面（GDI+ 自绘，支持中英文）：进程选择 / 注入进度 / 加载完成
- 去掉登录页与缓存询问页：启动即选进程注入，本地生成 token
- **DLL 完整性检查**：外部 DLL 必须与内嵌资源一致；否则解压内嵌版本，不覆盖已有恢复文件
- 窗口标题「Soulbound V1」，图标与产品一致
- 文件落盘全部收拢进 exe 旁隐藏的 `.soulbound` 目录（解压的 DLL/JAR、日志、配置、服务数据、纹理缓存）

**注入方式**

- GUI 优先尝试反射手工映射和线程劫持；控制台使用 `LoadLibraryW`。手工映射不建立普通模块记录，但这不代表不可见或可以逃避检测。
- **失败自动回退**：GUI 手工映射失败时可能回退到 `CreateRemoteThread` + `LoadLibraryW`；两条路径的实际兼容性尚未证明完全一致。
- 编译时会随机化产品类名，但这不能保证任何检测或反作弊结果。

**动态模糊（MotionBlur）**

- **HUD 模块「动态模糊」**（显示/Game 分组，与方块彩色边框同区域）：帧混合后处理，开启后根据视角移动产生残影拖尾效果
- 可调项：模糊强度（默认 5，×2 强度，上限 0.95）、速度自适应（随镜头移动速度增强）、柔和模糊、帧率调制、灰度拖影、菜单 / 游戏菜单中是否生效
- 支持 **1.17+ 至 26.1** 的 Vanilla / Forge / NeoForge / Fabric 运行时（26.2 的渲染管线无帧末钩子暂不支持）
- 渲染时机与 GL 状态经专门适配：在 `RenderTarget.blitToScreen` 出口（画面已呈现到屏幕、swap 前）执行，并同步游戏 `GlStateManager` 缓存，避免字体/贴图采样错乱

**功能集成**

- 内嵌 **VapeService**（HTTP 8080 + Zeus TCP 8091 配套服务），游戏内自动后台启动：
  - 账号 / 设置 / 配置档 / 好友 / 小队 / 位置共享等在线功能本地跑通
  - 服务数据存于 exe 旁 `.soulbound\soulbound-service.json`，与本地配置（`.soulbound\config.json`）分离存储，互不冲突
  - 端口占用自动向上探测空闲端口；启动失败静默降级，不影响游戏
  - 支持环境变量配置（见下文「内嵌服务配置」）

**本地化**

- 语言包扩充至 2600+ 键，模块名、值名、提示、教程、确认框、药水/物品名全覆盖
- 默认语言为 English；语言选项仍为「中文 / English」，显式保存的中文设置会保留
- 模块搜索同时匹配英文名与中文翻译名，中文可直接搜到模块
- 分类导航显示「其他」分类，Other 分类模块（如不释放物品）可直接浏览

**字体与显示**

- `noto.ttf` 为覆盖全部翻译字符的 Noto Sans SC 静态子集（SemiBold 600 字重）
- 注入器控制台使用英文并启用 UTF-8 输出

**工程与稳定性**

- **26.2 图形后端**：26.2 引入 Vulkan 后端，Vape 基于 OpenGL，需将图形 API 切换为 OpenGL 后使用（详见下方兼容性说明）
- 新增 1.21.0+ / 26.x 渲染管线适配（`blitToScreen` 帧末钩子），动态模糊等后处理可跨版本工作
- 版本探测增强：区分 Vanilla / Forge / NeoForge / Fabric 运行时，避免旧版 Fabric 误判（1.20.1-Fabric 除外，见兼容性表）
- 配置本地持久化：模块设置、配置档、好友、框架位置保存至 `.soulbound\config.json`，自动保存 + 退出兜底
- 原生日志与 Java 日志统一到 `.soulbound\log\`，每次注入生成独立日志文件
- 单文件注入器：`SoulboundV1.exe` 内嵌完整 DLL 与 Java 载荷

### 它不是 Vape 官方源码、原始发布包或厂商签名产物，也不保证具备与原产品完全一致的行为。

> 本项目用于软件恢复、兼容性分析和自有环境测试。仅应在你拥有并获准测试的隔离实例中
> 使用，并自行确认当地法律、软件许可和服务器规则。

## 脚本扩展

客户端内嵌 Mozilla Rhino 1.7.15（重定位到 `gg.vape.shaded.org.mozilla`，解释执行、不生成字节码），可从 `.soulbound\scripts\` 加载自定义模块脚本：

```text
.soulbound/
  scripts/
    example.json      模块描述（必需）
    example.js        脚本入口（默认与 json 同名，可用 main 指定）
    mylib.js          其他脚本，用 load("mylib.js") 引入
    data/             storage 持久化数据（每个脚本一个 json）
  extension.log       脚本加载 / 运行 / 报错日志
```

- 首次启动自动生成 `example.json` + `example.js` 示例，脚本模板随版本升级自动重写
- 脚本能力：读写模块与设置项（含颜色、快捷键）、玩家与实体查询、屏幕绘制，以及
  `onEnable` / `onDisable` / `onTick` / `onRender2D` / `onKeyPress` / `onChat` / `onAttack` /
  `onMove` / `onWorldChange` / `onPacketSend` / `onPacketReceive` 回调、`storage` 持久化、
  `after()` 定时器、`load()` 多文件
- 「重新加载脚本」模块可热重载：重新编译已有脚本、注册新增 json、禁用已删除 json 对应的模块
- 脚本加载或运行出错会在游戏内弹提示，并写入 `.soulbound\extension.log`

完整开发文档（JSON 字段、全部 API、示例、版本白名单、限制与安全）见
[`docs/scripting.md`](docs/scripting.md)。

> ⚠️ **安全警告：脚本没有沙箱。** 当前版本使用 Rhino 的完整标准对象集加载脚本，脚本可以访问
> `Packages.java.*`，等价于在 Minecraft 进程（即你当前 Windows 用户权限）里执行任意 Java 代码——
> 起进程、读写任意文件、加载原生库等。**只运行你自己写的或完全信任的脚本**；从网络下载的第三方
> 脚本等同于运行来源不明的程序。

## Minecraft 兼容性

下表是恢复工程的预期范围，并不表示 Soulbound V1 已经实机测试；已知映射和显示问题仍然存在。

| Minecraft | Vanilla | Forge | Fabric |
| --- | :---: | :---: | :---: |
| 1.7.10 | ✓ | ✓ | - |
| 1.8.9 | ✓ | ✓ | - |
| 1.12.2 | ✓ | ✓ | - |
| 1.16.5 | △ 实验性 | - | - |
| 1.20.1 | △ 实验性 | △ 实验性 | - |
| 1.21.1 | △ 实验性 | △ 实验性 | - |
| 1.21.10 | △ 实验性 | △ 实验性 | - |
| 1.21.11 | ✓ | ✓ | ✓ |
| 26.1.2 | ✓ | ✓ | ✓ |
| 26.2 | △ 实验性 | △ 实验性 | △ 实验性 |

**1.21.10 的 Forge 60.x / NeoForge / 原版均为实验性适配，功能可能不完整（NeoForge 与 Forge 同属一行）；Fabric 不支持**。

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

请等待游戏进入主菜单，确认使用 64 位 JVM，并核对所选 PID、游戏版本与运行环境。支持 1.7.10、1.8.9、1.12.2、1.21.11 和 26.2 的 Vanilla，1.21.10 的 Forge / NeoForge（该版本各加载器均为实验性支持），以及 1.21.11 / 26.2 的 Fabric。不要向标题相似的启动器辅助 JVM 或其他 Java 程序注入。

### 2. Loader 选择游戏进程后卡死

若该 Minecraft 进程已被注入过 Vape，无论第一次 Vape 是否加载成功，第二次注入同一进程时 Loader 都会卡死。

若第一次注入失败，请重启 Minecraft 后重新注入。

### 3. Loader 提示 SoulboundNative.dll was not found beside the Loader

此提示表示 DLL 无法打开或校验。外部 DLL 并非必需，但必须与内嵌资源一致；请检查解压和恢复目录权限，不要为了加载而绕过安全警告。

### 4. Loader 提示 Product DLL did not acknowledge… 或 Unexpected…

Vape 4.21 支持 1.7.10、1.8.9、1.12.2、1.21.11 和 26.2 的 Vanilla，1.21.10 的 Forge / NeoForge（该版本各加载器均为实验性支持），以及 1.21.11 / 26.2 的 Fabric；其他 Fabric 版本、以及 1.21.10 的 Fabric 不在当前支持范围。

出现该提示说明 Vape 注入失败：请确认 Minecraft 是否为受支持的版本，重启游戏后重新注入。若问题依旧，请检查 每次注入的 `soulbound-native-*.log` 并反馈问题。

### 5. Loader 显示 Finished Loading，但游戏中没有出现 Finished Loading 提示

说明 Vape 注入失败：请确认 Minecraft 是否为受支持的版本，重启游戏后重新注入。若问题依旧，请检查 每次注入的 `soulbound-native-*.log` 并反馈问题。

### 6. 出现 injection error code，或字段 / 方法映射失败、客户端崩溃

请确认 Minecraft 是否为受支持的版本，重启游戏后重新注入。若问题依旧，请检查 每次注入的 `soulbound-native-*.log` 并反馈问题。

### 7. ClickGUI 能打开，但无法点击、鼠标位置错误或按键卡住

请提交 Issue 反馈，并详细描述问题出现的时机与复现步骤。

### 8. Render 模块导致 OpenGL 1283、HUD 消失、画面损坏或崩溃

Minecraft 1.16.5 及其他未列出的 1.16+ 版本兼容性仍然不佳。上游列出过下列组合，但 Soulbound 版本尚未实机验证：

- 1.21.11 Forge：Forge 61.0.8
- 1.21.11 Fabric：Fabric Loader 0.19.3
- 26.2 Forge：Forge 65.1.0
- 26.2 Fabric：Fabric Loader 0.19.3

### 9. 配置消失、保存失败、好友或小队（Party）状态异常

内嵌服务在所选客户端目录中保存账户数据；独立服务默认使用相对于工作目录的 `data/vape-service.json`，换目录启动可能看起来像创建了全新账户。文件损坏、无写权限、磁盘已满、同时运行多个 Service 实例或异常退出都可能影响保存。同一账户的新 Zeus 连接会替换旧连接；Service 重启、系统休眠或网络切换后，高频在线状态也不会自动恢复。

建议始终使用明确的 `--data-file` 路径并定期备份，同时避免两个 Service 实例写同一文件。当前 Service 是实验性兼容层：部分公共配置、评论、举报、标签和分页行为为简化实现，连接成功不等于所有原版 Online 功能完整可用。

### 10. 产品类名是否随机化？

本分支的 CI 发布构建默认随机化产品类名；native 硬编码的三个类保留原名。`-PnoObfuscation` 可用于诊断构建。CI 会在名称还原后比较内嵌模块类，但这不能证明运行时行为一致或避免检测。

## 内嵌服务配置

VapeService 在游戏内自动启动，默认监听 `127.0.0.1:8080`（HTTP）与 `127.0.0.1:8091`（Zeus TCP）。
可通过环境变量调整：

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `VAPE_BIND_ADDRESS` | `127.0.0.1` | 绑定地址；设为 `0.0.0.0` 可允许局域网访问 |
| `VAPE_HTTP_PORT` | `8080` | HTTP 端口 |
| `VAPE_ZEUS_PORT` | `8091` | Zeus TCP 端口 |
| `VAPE_DATA_FILE` | `<exe>/.soulbound/soulbound-service.json` | 服务数据文件路径 |

客户端侧已有 `VAPE_ONLINE_BASE_URL` / `VAPE_ZEUS_ADDRESS` 覆盖服务地址，与上述变量配合
可实现局域网多端互联。

## 环境要求

仅编译和校验 Java 层需要：

- JDK 17，用作 Gradle toolchain；`gradle.properties` 默认使用 Java 8 兼容字节码，CI 也明确传入 `-PtargetRelease=8`
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

完整测试包输出到 `build/injection/`（exe 文件名随项目版本号变化）：

```text
SoulboundV1.exe   GUI 单文件加载器（内嵌 DLL 与全部资源）
SoulboundNative.dll   可选且必须匹配的 DLL
README.md / README_EN.md
```

DLL 将 Java injection JAR 作为 `RCDATA` 嵌入，不要求另行放置 payload。原生桥接层恢复
样本的 `RegisterNatives` 接口表，并保留另行记录的 `gat()` 兼容方法；其他未实现的声明不会凭空注册。更多细节见 [`native/README.md`](native/README.md)。

## 隔离环境运行

启动使用 64 位 JVM 的受支持 Minecraft 实例（包括 1.21.11、26.1.2、26.2 Fabric）后，直接运行 `SoulboundV1.exe` 打开 GUI，选择 Minecraft 进程点击注入（无登录、
无外部 DLL）。

也可用命令行方式注入：

```powershell
# 指定进程 ID 注入
.\SoulboundV1.exe -nogui <pid>
# 不带 pid：弹出自动刷新的 Java 窗口选择器（↑/↓ 选择，回车注入，Esc 退出）
.\SoulboundV1.exe -nogui
```

GUI 首先尝试反射映射，失败时可能回退到 `LoadLibraryW`；控制台使用 `LoadLibraryW`。DLL 加载后会等待 JVM 与 Minecraft `Client thread`，通过其
上下文 ClassLoader 加载内嵌 JAR；Fabric 实例会通过 Fabric Launcher API 将载荷加入 Knot
ClassLoader。随后 DLL 注册原生方法，并调用
`gg.vape.runtime.NativeBridge.start()`。每次注入的日志位于注入器 EXE 同目录的
`.soulbound\log\soulbound-native-<pid>-<时间戳>.log`。

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
