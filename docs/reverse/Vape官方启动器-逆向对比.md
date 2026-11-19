# Vape V4 官方启动器逆向分析（与我们的 4.21 重建版对比）

分析对象：`C:\Users\Jiang\Downloads\34d76634.exe`
分析方式：静态逆向（PE 解析 + 字符串提取 + Capstone 指令级扫描 + 关键函数人工还原）。未运行该程序，也未取得运行时下载的载荷。

---

## 1. 识别信息

| 项 | 值 |
| --- | --- |
| 文件大小 | 7,630,800 字节 |
| SHA-256 | `49ed78325573b1778507ad88912beda4534a2e7817d10ad165b613a9b5947289` |
| 格式 | PE32+ x86-64 GUI，ImageBase `0x140000000`，8 个节 |
| 节表 | `.text` `0x301c00` / `.rdata` `0xd0c00` / `.data` `0x6ce00` / `.pdata` `0x32a00` / `.fptable` / `.rsrc` / `.reloc` / **`.vlizer`（原始大小 0x2c76a5，熵 8.00 —— 加密/虚拟化保护段）** |
| 数字签名 | **有效**：`CN=Manthe Industries LLC, O=Manthe Industries LLC, L=De Pere, S=Wisconsin, C=US`；签发者 `DigiCert Trusted G4 Code Signing RSA4096 SHA384 2021 CA1`；有效期 2026-04-10 → 2027-04-12；指纹 `74B8606287303D055A3ABAF7CC2990E23FFC5E1D` |
| 代号/UA | 字符串 `Vape V4 Launcher`、`User-Agent: Vape4/Launcher`、`Vape4/Launcher`、`Vape4/DNSCheck` |
| 第三方库 | GLFW + OpenGL32（自绘 GUI/窗口）、Dear ImGui（`Dear ImGui is licensed under the MIT License`）、**Boost.Asio + WebSocket**、**OpenSSL 1.0.x**（静态，`ssl3_digest_cached_records` 等符号）、CryptoPP、zlib、LodePNG、WinHTTP |
| 导入特征 | `VirtualAllocEx` / `WriteProcessMemory` / `VirtualProtectEx` / `CreateRemoteThread` / `OpenProcess` / `CreateNamedPipeA` / `AdjustTokenPrivileges` / `CreateToolhelp32Snapshot` / `IsDebuggerPresent`；`WINHTTP.dll` 全套；`WS2_32.dll` |

结论：这是 **Vape V4 官方启动器（native C++）**，不是注入后的游戏内客户端本体。它自己用 GLFW/OpenGL 画启动器界面，负责登录、下载、找 Minecraft 进程、注入 agent，并通过命名管道与 agent 通信。

---

## 2. 启动器主流程（由状态字符串还原）

界面状态机字符串（`0x140064be9` 一带）：

```
no_minecraft / Vape V4 launcher
Launcher update required / 检查更新 (update.php?edition=V4)
Authentication required → Authenticating（浏览器授权 / app-auth）
Waiting for Minecraft（等待游戏启动）
Choose a Minecraft window（选择窗口）→ 检测 "Minecraft 1.x" / "Minecraft* 1.x" 窗口标题
Checking version → 版本判定（Incompatible Minecraft / Minecraft Forge is required to run on this version）
Downloading files（从 CDN 下载，带 "Download appears stalled" 提示）
Injecting → Loading
Vape has finished loading → 提示 Press RIGHT SHIFT 打开游戏内 GUI
```

配套提示还有：`Already Injected [%i]`、`Select a Minecraft window`、`Up/Down: select   Enter: inject`、`No Minecraft found`、`Make sure game is fully loaded first`、`Failed to load %i`、`Stage %i/%i`、`It is taking abnormally long to load this stage`、`Game crash detected`。

版本探测相关：`Minecraft 1.` / `Minecraft* 1.`（窗口标题前缀匹配）、`1.8.9m`、`Incompatible Minecraft`、`Minecraft Forge is required to run on this version`、`Use a supported Minecraft version and client listed on the FAQ of the website`。

---

## 3. 认证与授权（在线）

| 环节 | 证据 |
| --- | --- |
| 浏览器授权 | `/api/v1/app-auth/generate` + `&hwid=`，打开 `https://www.vape.gg/app-auth/proceed/<...>`，轮询 `/api/v1/app-auth/status`；错误文案 `No token was received from vape.gg`、`Browser login completed, but no auth token was returned`、`The login token is missing`、`token=` |
| 旧式登录 | `/auth.php`，提示 `Use the same credentials you use on vape.gg/login` |
| 设备绑定 | `&hwid=`、`Your HWID or IP update timer is on cooldown`、`Your IP can not be updated to a proxy/VPN` |
| 云端设置 | `/api/v1/%s/settings/load/global`、`/api/v1/%s/settings/save/global`（`%s` 为账号/token 标识）、域名 `online.vape.gg` |
| 崩溃上报 | `/api/v1/%s/crashreports/create` |
| 更新检查 | `http://www.vape.gg/update.php?edition=V4`、`A launcher update is available`、`Skip update and continue` |
| 网络容错 | 严格 TLS 校验与「中间人检测」文案：`Possible HTTPS interception detected (issuer: …)`、`DETECTED: Server returned HTTP instead of TLS!`、`DETECTED: Server returned an HTML page`、`WARNING: Certificate appears to be from a PROXY or SECURITY PRODUCT!`、`DETECTED: Server returned HTTP instead of TLS!`、`mitmproxy` 字符串；DNS 劫持检测 `DNS results match - no hijacking detected` |

即：**启动器本身是在线授权的商业软件**，token/HWID 由启动器获取后交给注入到游戏里的 agent。

---

## 4. 注入链（关键）

### 4.1 IPC：命名管道 + JSON

- 启动器侧创建命名管道：`CreateNamedPipeA`（`0x1400b3f0a`），管道名模式 `\\.\pipe\vapeclient.v4.<...>`（字符串 `0x39a838`），失败文案 `Failed to create pipe`、`Failed to create pipe ` + `name %s` + ` Error: `。
- 握手数据是 JSON：`0x1400b4c23` 处先后取 `metadata` → `game_version` 两个键并赋值（把检测到的 Minecraft 版本告诉 agent）。
- `Agent_%s`（5 处引用）用于拼 agent 相关名称（管道/互斥体/日志文件名一类）。

### 4.2 注入实现（`0x14000dc40`，唯一注入函数）

反汇编还原（节选，地址为 VA）：

```
0x14000dc9d  GetModuleHandleW("NtWriteVirtualMemory" 所在模块 = ntdll)
0x14000dcb4  GetProcAddress(ntdll, "NtWriteVirtualMemory") -> 全局函数指针
0x14000dcf7  若 0x1400be900(payload, ...) == 0 -> "e1 %i"（payload 解析/校验失败）
0x14000dd3a  VirtualAllocEx(hProcess, NULL, imageSize, MEM_COMMIT|MEM_RESERVE(0x3000), PAGE_READWRITE(4))
0x14000dd8f  WriteProcessMemory(hProcess, remote, payloadBytes, imageSize, NULL)
0x14000dde2  VirtualProtectEx(hProcess, remote, imageSize, PAGE_EXECUTE_READWRITE(0x40), &old)
0x14000de4b  CreateRemoteThread(hProcess, NULL, 0x100000, remote + entryOffset, param, 0, &tid)
```

要点：
1. **载荷是整块 PE 字节**，先写入目标进程（`VirtualAllocEx` + `WriteProcessMemory`）；
2. **入口不是映射后的 RVA，而是"文件偏移"**：`0x1400be900` 是一个 PE 解析器（校验 MZ/PE、`0x10b/0x20b`、节表、资源/版本信息），`0x1400be7e0` 负责 `RVA → 文件偏移`；`CreateRemoteThread` 的起点是 `远端分配基址 + 入口文件偏移`。也就是说 **载荷必须以"文件布局"就地执行**（自带装载逻辑的自映射 stub），这正是反射式 DLL 注入的标准形态；
3. 线程参数块（第 5 个参数）由调用方传入，`0x14000ed10` 是薄包装；栈保留 `0x100000`（1 MB）预留标志；
4. 额外解析 `NtWriteVirtualMemory` 到全局（后续可用于绕过/直写，非首注入路径必需）。

### 4.3 载荷来源

- 载荷不内嵌：入口流程有 `Downloading files` / `Download appears stalled` / `Would you like to cache local files for faster loading time?`；
- 下载域名出现 `chink2.vape.gg`（`0x1400db815`，与 `vape.gg` 二选一，判断走国内/海外 CDN）与 `vape.gg`；
- 本地目录模板 `%s\.vapeclient\`（`0x1400e360` 引用处）与 `%scache`，即 `<启动器目录>\.vapeclient\` + `cache` 子目录。

### 4.4 注入后的会话

- 启动器 ↔ agent ↔ 游戏：管道 + WebSocket（Boost.Asio，`wss://`/`ws://` 字符串齐全）；
- 启动器把 `token`、`game_version` 等 JSON 字段经管道交给 agent，agent 再进入 JVM 完成真正的客户端装载（agent 内部实现不在本 exe 中）。

---

## 5. 防护与反分析

| 机制 | 证据 |
| --- | --- |
| 代码保护 | `.vlizer` 节熵 8.00（2.9 MB 密文/虚拟化代码），`0x1400de90` 起大量非法指令/花指令区段 |
| 数字签名 | DigiCert OV/EV 签名（Manthe Industries LLC），可被 SmartScreen/企业策略信任 |
| 调试器检测 | `IsDebuggerPresent` 2 处（`0x1402a6993`、`0x1402b5a73`）；`NtWriteVirtualMemory` 动态解析 |
| GL 哨兵 | `sentinel draw` / `sentinel readback` / `expected magenta at %d,%d got rgba(%u,%u,%u,%u) framebuffer %dx%d` / `UI_INIT_SENTINEL_DRAW`：自绘品红像素→回读校验，判断帧缓冲/GL 上下文是否被拦截或篡改 |
| 网络中间人检测 | 见第 3 节 TLS/DNS 文案 |
| 权限提升 | `AdjustTokenPrivileges`（`0x1400bab5e`，失败文案 `Failed to adjust token privileges`）+ `OpenProcessToken` |
| 崩溃上报 | `/api/v1/%s/crashreports/create` |

---

## 6. 与我们的 Vape 4.21 重建版（v4.21.49）逐项对比

| 维度 | 官方启动器（本文件） | 我们的重建版 |
| --- | --- | --- |
| **形态** | 独立 native 启动器（7.6 MB，C++/ImGui/GLFW/OpenGL），客户端载荷运行时下载 | 单文件自包含 exe（约 39 MB，内嵌 38 MB native DLL + 混淆后的产品 JAR），自绘 Win32 GUI loader |
| **授权** | 在线账号 + 浏览器 app-auth + HWID + IP 限制 + 服务端设置同步 | 完全离线，无授权校验（已被本地化改造），配置只写 `.vapeclient\config.json` |
| **载荷来源** | CDN 下载（`chink2.vape.gg` / `vape.gg`），本地 `cache` | 内嵌 RCDATA，启动器自己释放 JAR/DLL |
| **注入方式** | `OpenProcess` → `VirtualAllocEx` → `WriteProcessMemory` → `VirtualProtectEx(RWX)` → `CreateRemoteThread(远端基址 + 入口文件偏移)`，载荷自带 loader（反射式） | `OpenProcess` → 写入 DLL 字节 → 反射加载器在目标内自映射（manual map）→ 调 `DllMain`；**失败自动回退 `CreateRemoteThread + LoadLibraryW`** |
| **JVM 挂载** | 启动器只注入 agent；agent 负责进 JVM（实现不在本文件） | DLL 自己 `GetModuleHandleW("jvm.dll")` + `JNI_GetCreatedJavaVMs` + `AttachCurrentThreadAsDaemon`，JVMTI `RetransformClasses` 打补丁 |
| **启动器↔客户端通信** | 命名管道 `\\.\pipe\vapeclient.v4.*` + WebSocket（Boost.Asio），JSON 字段 `token`/`metadata.game_version` 等 | 单进程内直接调用（无 IPC），版本号在 DLL 内部检测 |
| **版本探测** | 窗口标题 `Minecraft 1.` / `Minecraft* 1.`，结合 `game_version`；Forge 必需提示 | `NativeBridge.gmv()` 读 Forge/NeoForge/Fabric 元数据 + 映射表锚点结构校验，覆盖 1.7.10–26.2 |
| **防篡改** | `.vlizer` 加壳、GL 哨兵回读、TLS 中间人检测、`IsDebuggerPresent`、官方签名 | 编译时随机类名混淆（可选，Release 不混淆）、manual map（DLL 不进 PEB 模块表）、自签名证书 |
| **签名** | DigiCert OV/EV：`CN=Manthe Industries LLC`（受信任） | 自签名 `CN=OpenVape`（需用户先装 `OpenVape-Cert.exe` 才显示有效） |
| **模块/配置** | 服务端全局设置 + 本地缓存 | 本地 JSON；Kiss/原版 Kick 等模块自研移植 |
| **日志** | `Stage %i/%i`、错误码 `e1 %i`…`e4 %i`、`Read timed out at stage %i` | `vape421-native-<pid>-<ts>.log` + `manual-map-ok.txt`/`manual-map-fallback.txt` |
| **代码规模** | 启动器 7.6 MB（其中 2.9 MB 是加密段） | exe 39 MB（38 MB 为 DLL + JAR） |

### 结论性对照

1. **注入层级相同**：双方都是「外部进程 → 远程分配 + 写入 PE 字节 → 远程线程执行自映射 stub」，都不走 `LoadLibraryW` 常规加载；我们额外提供 `LoadLibraryW` 兜底，官方没有（它只走反射式一条路）。
2. **职责划分不同**：官方是「启动器（授权/下载/注入 agent） + agent（进 JVM） + 游戏内客户端」三层；我们是「loader（内嵌载荷/manual map） + DLL（进 JVM/打补丁/加载 JAR） + Java 客户端」两层半。
3. **在线 vs 离线**：官方全部依赖 `vape.gg`（授权、设置、崩溃上报、更新、CDN）；我们已完全离线化，这也是 4.21 重建版能独立分发的原因。
4. **防篡改思路**：官方靠加壳 + 签名 + GL 哨兵 + TLS 检测；我们靠（可选的）类名混淆 + manual map + 自签名。**GL 哨兵**与 **TLS 中间人检测** 是我们没有的两项；前者对其自绘启动器有意义，后者对离线版无必要。

---

## 7. 可借鉴 / 不建议借鉴

**值得借鉴**
- 反射式注入的载荷入口按「文件偏移」计算，并与远端分配基址相加 —— 与我们 `reflective_loader.c` 的做法一致，可作为我们实现的旁证（说明该做法是业界标准形态，而非野路子）。
- 命名管道传 JSON 控制字段（`game_version`、token）—— 若将来需要把 loader 与 DLL 解耦（比如支持热替换载荷），可参考其握手方式。
- GL 哨兵自检（画点回读）作为「渲染上下文是否被劫持」的廉价探针，可用于排查用户侧覆盖层冲突（可选）。

**不建议借鉴**
- 在线授权/HWID/IP 绑定：与我们的离线定位冲突，也带来隐私与可用性问题。
- `.vlizer` 加壳 + 大量花指令：可维护性极差，且会显著增加杀软误报；我们已有可选的类名混淆，够用。
- TLS 中间人检测文案/严格校验：离线版不发外网请求，无收益。
- 反调试（`IsDebuggerPresent`）等：对用户无感，只增加排障成本。

---

## 8. 方法与局限

- **方法**：PE 头/节表/导入表解析；全文件 ASCII/UTF-16 字符串提取；Capstone 全 `.text` 线性反汇编（848,959 条指令），建立 IAT 调用点与 RIP 相对字符串引用表（28,885 个引用、8,216 个命中字符串）；对关键函数（`0x14000dc40` 注入、`0x1400be900` PE 解析、`0x1400b4c23` 管道握手、`0x1400b3ec0` 管道创建）逐指令还原。
- **局限**：
  1. `.vlizer` 段为加密/虚拟化代码（熵 8.00），其中逻辑只能从字符串与导入反推，无法静态完整还原；
  2. 真正的「注入载荷」（agent DLL / 客户端 JAR）是运行时从 CDN 下载的，本 exe 内不含，因此 agent 内部如何进 JVM、如何装载 Java 客户端无法在本轮确认；
  3. 未实际运行样本（避免账号/网络副作用），故行为结论均来自静态证据，未做动态抓包验证。
