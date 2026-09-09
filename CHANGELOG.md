# 更新日志

## v4.21.33 (2026-09-09)

**SilentAura 集成上游 AI 瞄准（Rotation Mode：PID / AI）+ 移植 LiquidBounce MLP 模型**

- **SilentAura 新增「瞄准模式 / Rotation Mode」**：顶部下拉，选项 **PID**（默认，原 Vape 旋转引擎）/ **AI**（LiquidBounce MLP 战斗回归模型）。
- **AI 模式**：由捆绑的 MLP 模型（`21KC11KP` / `19KC8KP`）驱动静默瞄准的 yaw/pitch 增量，含 `AI Model`、`AI Yaw/Pitch Multiplier` 设置（仅选 AI 时显示）。
- **移植 `gg.vape.deeplearn`**：`ModelManager` + `MlpModel`（纯 Java MLP 前向推理）+ `DeepLearningEngine`，并**捆绑模型权重** `liquidbounce/models/{21kc11kp,19kc8kp}.bin`。
- **`AdaptiveRotationController` 加 AI 支持**：`applyAiRotationDelta` / `clearAiRotationMode` / `aiRotationActive`；AI 模式下 `updateYaw/updatePitch` 跳过 PID（避免覆盖 AI 增量，保证收敛）。
- **带回正**：无目标/模块关闭时 `clearAiRotationMode()`，托管旋转与真实视角对齐，不残留旋转视角。
- 新增值及说明**中文汉化**（瞄准模式/AI模型/AI偏航乘数/AI俯仰乘数 + tooltip）。
- 注：上游 AI 面向 LiquidBounce 旋转体系，集成后若命中不稳定，可调 AI 乘数或改用 PID；README 未同步（按需）。

## v4.21.32 (2026-09-08)

**新增小且实用的功能（来自 OpenVape4.21 fork）+ 汉化**

- **RightClicker「Sneak Only」**：新增「仅潜行」开关——开启后**只在潜行时右击**（`player.P()`）。
- **AutoTool「Not during combat / Not during use」**：新增两开关（默认关）——**战斗中不切换**（用 `InventoryActionGuard` 检测受伤/瞄准目标）、**使用物品时不切换**（喝药/吃/格挡/拉弓，`isUsingItem()`），避免换槽中断动作。
- **SilentAura「Random aim」**：新增随机瞄准——在目标包围盒指定百分比范围内随机化瞄准点（Random size/速度、每 100–300ms 换点平滑插值），用于**防作弊**（瞄准点不固定，更难被判为锁头）。
- 新增值名/说明的**中文汉化**（`chinese.properties.txt` / `english.properties.txt`），SilentAura 随机瞄准、RightClicker 仅潜行、AutoTool 不切换均汉化。
- 未加入：SilentAura AI 模式（LiquidBounce MLP，需整套 deeplearn 子系统 + 模型权重，工程量大未验证）；通知 augustus piano 开关音（偏外观，需捆绑音效资源）。
- 跨版本门控审查通过、注入 payload 验证通过。

## v4.21.31 (2026-09-08)

**宏全面修复（命令宏/物品宏）+ 26.1 EventPreTick 注入修复 + 原生诊断文件收敛**

- **26.1 EventPreTick 注入修复**（`MinecraftTickEventMappingTask`）：26.1 的每 tick 为 `tick(Z)V`，原 `runTick()V` 回退查不到 → `EventPreTick` 从不触发。改为把 Pre/PostTick 注入必可解析的游戏主循环（`U.v`），解决命令/物品宏执行链（`MacroEventListener.onTick`）不运行的问题。
- **命令宏发送指令包**（`MEntityPlayerSP` 注册 `NetHandlerPlayClient.sendCommand`；`EntityPlayerSP` 新增 `sendCommandMessage`；`CommandMacroAction`）：`/` 开头的命令在 ≥1.20.6 走 `sendCommand`（`ServerboundChatCommandPacket`，去掉前导 `/`），真正作为指令执行而非聊天消息；<1.20.6 保持旧 `sendChatMessage`。
- **`sendChatMessage` 接收者按版本修正**（`EntityPlayerSP`）：≥1.20.6 用网络连接（`NetHandlerPlayClient`），<1.20.6 用玩家对象，避免旧版本聊天/消息发送回归。
- **物品宏匹配修复**（`ItemMacro.findHotbarSlot`）：注册名改用物品对象自身 `toString()`（避免 `item.O()` 在 26.1 因 `MItem.S` 未注册抛 NPE）；并支持完整注册名（`minecraft:snowball`）与去掉命名空间前缀的短名（`snowball`）。
- **移除注入器诊断文件**（`native/dllmain.c`）：`injector_diag()` 改为空操作，不再生成 `vape_injector_diag.txt`。
- 移除临时宏诊断日志（`EventPreTickCallback`/`MacroEventListener`/`ItemMacro` 的 debugLog）。
- 跨版本（1.7.10 / 1.8.9 / 1.12.2 / 1.14.4 / 1.16.5 / 1.20.x / 1.21.x / 26.x）门控审查通过、无回归；运行时验证 26.1.2。

## v4.21.30 (2026-09-07)

**集成上游（4.21-recovered）新功能/修复 + 此前遗留修复**

- **26.2 队伍颜色映射**（`MappedClasses.l5` 增加 `MC_26_2.d() ? TeamColor : ...`；`MScorePlayerTeam` 的 `color` 字段类型改为 `MC_26_2.d() ? Optional.class : ...`）
- **26.2 攻击按键 tick 线程门控**（`AttackKeyController` 新增 `shouldRunOnClientTick()`，在 26.2 上把 release/press/synthetic-attack 经 `PRE_TICK_EXECUTOR` 延迟到客户端 tick）
- **左键连点器窗口失焦**（`LeftClicker` 新增 `isMinecraftFocused()` = 输入分发焦点 && `Minecraft.a()`（窗口焦点），替代仅检查输入分发焦点）
- **宏按键按下触发**（`MacroEventListener` 由按键释放改为按键按下触发）
- **GUI 分类 frame 网格预排**（`ClientSettings.initializeFrames` 首开即非重叠排列）+ 26.2 通知条件 + try/finally popMatrix
- 跨版本（1.7.10 / 1.12.2 / 1.21.10 / 1.21.11 / 26.x）审查通过、无回归
- （说明：上游 Search 1.21.11 的 Fabric 专用门控不适用本仓库；本仓库既有门控已等效覆盖 1.21.11）

## v4.21.29 (2026-08-25)

**v4.21.28 基线 + 1.20.1 注入支持与卡顿修复（发布版）；其余 1.20.1 改动回退**

- 在 **v4.21.28 基线**上仅保留两项 1.20.1 修复（详见 v4.21.28 条目补注）：
  1. **1.20.1 Forge 注入支持**（`MBlock.U` 的 `BLOCK` 字段 owner 门控 `MC_1_20_6 → MC_1_20_1`）
  2. **1.20.1 关闭菜单后游戏 GUI 消失 + 卡顿**（`ClientSettings` try/finally + 图标 NPE 守卫）
- **回退了 1.20.1 的 ESP/射线/箭头/图标显示等改动**（经大量排查仍未解决，属深层映射错位）
- **README 新增「已知难以修复的问题」**：记录 1.20.1 的 ESP/射线/箭头/图标、宏、自动重锤等
  暂未解决的问题；1.20.1 作为**实验性支持**，推荐主力使用 1.21.11 / 26.x
- 跨版本（1.7.10 / 1.12.2 / 1.14.4 / 1.16.5 / 1.20.x / 1.21.10 / 1.21.11 / 26.x）审查通过、无回归

## v4.21.28 (2026-08-25)

**26.x 装备名牌旋转 + 2D 框随镜头修复**

> 补注（在 v4.21.28 基线上额外应用的 1.20.1 修复）：
> - **1.20.1 Forge 注入支持**：`MBlock.U` 的 `BLOCK` 字段 owner 门控从 `MC_1_20_6.d()` 改为 `MC_1_20_1.d()`，
>   使 1.20.1+ 把 `BLOCK` 注册到 `BuiltInRegistries`（`MappedClasses.R`）而非 `Registry` 接口（无 `BLOCK` 字段），
>   修复 `Block.t` 遍历块注册表 NPE、初始化中断（无法注入）
> - **1.20.1 关闭 Vape 菜单后游戏 GUI 消失 + 卡顿**：1.20.1 整个 2D/GUI 通道在 `GameRenderer.render` 内运行，
>   图标捕获路径的未捕获 NPE 会中断整帧渲染（GUI 消失/卡顿）。`ClientSettings.renderHudFrames` push/pop 包
>   try/finally、`renderHudOverlay` 包 try/catch + 保证 flush 仍执行；`Post117ItemIconFramebufferRenderer`
>   pre-1.20.6 分支 `renderItem.a` 包 try/catch + renderQueued 空守卫；`PotionEffectIconTexture.getSpriteTexture`
>   空图集 try/catch 返 null；`ItemIconRenderer.createRenderer` 改为总是缓存——组件错误不再从
>   `EventRender2D.create()` 泄漏到 `GameRenderer.render`（不再黑屏/消失、不再每帧 NPE 卡顿）
> 
> 其余 1.20.1 的 ESP/射线/箭头/图标显示等改动已回退，不包含在此版本。


- **26.x 装备名牌不旋转（回归）**：v4.21.27 为"防止 1.16.5/1.20.x/26.x 回归"而把两个旋转按基线条件重新应用，
  但 **26.x 的 billboard 已自带相机基线**（`RenderUtil.d()/Y()`），再叠加这两个旋转会双重旋转，导致装备图标
  不再面向相机（26.1.2 实测）。现改为**全程移除**这两个旋转（与 1.21.10/1.21.11 一致）：26.x 与
  1.21.x 的 billboard 都自带相机基线，叠加旋转都是有害的
- **26.x 2D ESP 框不随镜头/只朝一个方向**：26.x 不注入 `modelViewMatrix`，共享投影是裸的（无相机旋转），
  框的世界坐标经 `RenderUtil.W` 投影后停在固定方向。现按 ESP3D/ItemESP/名牌的做法，在 `onRender3D` 的
  bounds 循环外加 `RenderUtil.d()/Y()`（26.x 通过 `RenderUtil.p→f` 把相机偏航/俯仰注入模型矩阵，使
  `W()` = 投影×相机×点 随镜头转动）；1.21.10-25.x 的 `RenderUtil.p()` 是 no-op，不影响 1.21.11
- 该修复仅动 `ESP2D.java`，不触碰共享 `updateProjectionMatrix`（避免影响 26.x 上正常工作的名牌/ESP/射线）
- 若遇崩溃请反馈日志

## v4.21.27 (2026-08-25)

**1.21.11 药水图标 / 2D 框 / 名牌 / 中文字体 全面修复**

- **药水图标空圆环 + 卡顿（~7.7 FPS）+ 文字截断**：
  - 空圆环根因：`capture` 只调了 `OpenGlBackendHolder.backend.translate/scale`（批处理着色器根本不读它，无效），
    精灵在调用方全窗口 GUI/WORLD 投影下缩成细条。现改为在 capture 内**设 icon-space 正交投影**
    （`ortho(0,18,18,0)`）+ 单位 view + 新建 matrixStack，精灵画在 `(0,0,18,18)`，正确填满 18×18 帧缓冲。
  - 卡顿根因：capture 失败 → 每帧重采集+日志；`renderQueued` 空 framebuffer 每帧 NPE。已加
    `createRenderer` 总是缓存 + `renderQueued`/`render` 空守卫。
  - 截断根因：capture 污染共享 `projectionMatrix/viewMatrix/matrixStack`。已加保存/恢复 + icon-space 正交隔离，
    修复合法模式设置页集体偏移与药水文字截断。
- **2D ESP 框压在游戏 GUI 之上**：根因是 box 经 `fillRect` 延迟进 `guiBatches`，只有帧尾
  `EventPostRenderTick` 才 flush（在 HUD 之后）→ 恒盖 HUD。现把 box 绘制+flush 移到世界阶段
  `onRender3D`（先于原版 HUD），用手动 GUI 正交投影 + `flushGuiBatches(0.0f,false)`，并把投影/矩阵恢复，
  保住 `modelViewMatrix`（顺带修复名牌集体偏移），box 沉到 HUD 之下。
- **名牌装备图标不正视镜头**：移除两次多余旋转，让图标继承外层 billboard 变换正视镜头（1.21.10/1.21.11）；
  并在 1.16.5/1.20.x/26.x 按基线 `RenderUtil.f` 生效条件重新应用其逆旋转，避免这些版本回归。
- **中文字形缺失（"迅捷"只显"捷"、物品/生物名空白）**：字体图集原只加载 `chinese.properties.txt`
  （约 1053 个 CJK）+ ASCII，游戏中文文本大量字形缺失回退成空格。现解码 GBK（GB2312 超集）双字节区，
  把全部 CJK 表意字（约 6763 字）加入图集，覆盖所有游戏简体中文文本。
- **跨版本/跨模块回归审查**：经子代理逐版本（1.16.5/1.20.x/1.21.10/1.21.11/26.x）与跨模块审查确认
  无受支持版本回归；对 `dispose()` 空 framebuffer、pre-1.17 box 消失等边界加了防御。
- 若遇崩溃请反馈日志

## v4.21.26 (2026-08-25)

**彩色循环（Rainbow）颜色修复 —— 不再依赖打开颜色设置页**

- **根因**：rainbow 颜色的 hue 推进方法 `ColorValue.advanceRainbowHue()` 之前只被颜色设置页的
  "颜色通道滑块"组件调用（`ColorChannelSliderComponent.u()`）。所以重启注入后，没有任何驱动推进 hue，
  方块彩色边框等 rainbow 颜色停在保存的静态值、不循环；只有打开颜色设置页才开始循环（且此时滑块每帧
  无节流推进，与读色推进叠加导致速度翻倍）
- **修复**：让 rainbow 颜色的 hue 由**模块实际读色时推进** —— 在 `ColorValue.getMutableColor()` 里，
  当彩虹启用时按时间节流（≥50ms 步进一次）调用 `advanceRainbowHue()`。这样任何 rainbow 颜色（方块
  彩色边框、射线、ESP 等）在渲染读色时持续循环，不再依赖打开颜色设置页
- **速度稳定统一**：同时移除颜色设置页滑块组件里的无节流推进，消除"打开设置页后循环变快"的双源叠加。
  现在只有一个推进源（约 20 步/秒），设置页开合速度一致，HUE 滑条随循环实时滑动，其余通道滑条不动
- **安全性**：经审查无递归、无持久化风暴、不破坏 GUI 主题色（`getAccentColor` 用独立 rainbowHue 机制）、
  不污染配置序列化；是通用修复，一并解决所有带 rainbow 颜色选项的模块
- 若遇崩溃请反馈日志

## v4.21.25 (2026-08-24)

**1.21.11 / 26.1.2 名牌、射线与 ESP 世界投影修复**

- **1.21.11 名牌与射线对齐修复**：世界批次渲染让射线起点相对准星、名牌文字相对背景框在非 -Z 方向偏移，
  根因是 `drawInBatch` 走 MC 原版 `RenderSystem` 投影/模型视图，缺失这批画面的 `viewRotation`。现将 1.21.10+
  的世界文字改走 Vape 世界批次字形路径（与背景框共用 `u_Projection/u_View/u_Model`），文字与背景对齐；同时
  删除名牌 billboard 多余的 `RenderUtil.f` 前置旋转（其 `+180` 恰好抵消了相机依赖），并让 `updateProjectionMatrix`
  在 1.21.10-25.x 使用纯旋转 `viewRotation`
- **26.1.2 名牌翻滚修复（重要）**：26.x 的世界批次投影内嵌相机旋转（`P = Proj * R_cam`），面向相机必须由
  模型矩阵提供。名牌 billboard 此前用 `pushMatrix()`（纯 identity 基底）构建，缺失相机旋转基线 → 名牌绕
  偏离头部的中心翻滚。改为 `RenderUtil.d()/Y()`（与 ItemESP 一致），26.x 注入相机基线后名牌正确面向相机、
  不再翻滚；1.21.10-25.x 的 `RenderUtil.p()` 为 no-op，行为不变
- **26.1.2 ESP/射线投影回归修复**：26.x 不重注入 `modelViewMatrix`（恢复 `!MC_26_1.d()` 门控），ESP/射线
  恢复 identity 投影下的正常渲染
- **发布附件版本化**：`release.yml` 的 `files` 改用通配符匹配带版本号的产物（`Vape-v4.21.25.exe`），附件名
  随版本号变化，避免每次发布手动维护文件名
- 若遇崩溃请反馈日志

## v4.21.24 (2026-08-24)

**26.x 药水图标/zC 修复 + 版本化附件命名**

- **26.x 药水图标 / GUI 页不显示修复**：根因是 26.x 把 `ResourceLocation` 改名为
  `net.minecraft.resources.Identifier`。此前 `MappedClasses.zC` 用 `resources/ResourceLocation`，在 26.x
  无对应 `Identifier` 重映射而解析为 null，导致 `TextureAtlasSprite.atlasLocation` 字段（型 Identifier）
  注册失败 → `getAtlasLocation` NPE → 药水效果图标 / 用到它的 GUI 页不渲染。现按版本用正确源名：
  **26.x 用 `net.minecraft.resources.Identifier`**，1.14.4-25.x 用 `resources/ResourceLocation`，<1.13 用
  `util/ResourceLocation`。顺带修复 60+ 处使用 zC 的映射在现代版本的解析
- **药水图标坐标回退**：`MTextureAtlasSprite.getTextureCoordinates` 在 `u0/u1/v0/v1` 字段为 null 时回退到
  `getU0()/getU1()/getV0()/getV1()` 方法，全部失败用默认 UV，避免 NPE
- **版本化附件命名**：构建产物 exe 现在带完整版本号（如 `Vape-v4.21.24.exe`），DLL 仍为
  `Vape-v4.21Native.dll`（injector 依赖其名，保持不变）
- 若遇崩溃请反馈日志

## v4.21.23 (2026-08-24)

**ItemESP 26.x 文字对齐 + ResourceLocation 现代版解析修复**

- **ItemESP 26.1.2 名字标签文字对齐修复**：世界空间标注的物品名文字此前相对背景框偏移（box 走世界投影
  shader 批量、文字走 MC 字体回调，投影不一致）。改为用 Vape 平滑字体（`StbSmoothFontRenderer`）经
  `BufferedRenderPrimitives.queueWorldBatch` 渲染——与背景框共用同一世界投影，文字与背景对齐；功能与
  其他版本不受影响
- **ResourceLocation 现代版解析修复**：`MappedClasses.zC`（ResourceLocation 类）此前写死旧源名
  `net/minecraft/util/ResourceLocation`，在现代版本（1.20.1 起 mojmap）上是
  `net/minecraft/resources/ResourceLocation`，导致 zC 解析为 null，进而在
  `registerStaticField("GUI")` 等处触发 `owner=null` NPE（破坏物品图标/GUI 渲染）。现按版本使用正确源名。
  顺带修复 60+ 处使用 zC 的映射（getTextureLocation/getSprite/getLocationSkin 等）在现代版本的可能为 null
- **已知外观问题**：1.21.11 的 Tracers 射线中心相对准星有偏移动（仅外观、功能正常，见 README 兼容性表）；
  26.1.2 的 ItemESP 文字偏移已修复，不再记录
- 若遇崩溃请反馈日志

## v4.21.22 (2026-08-23)

**1.16.5 实验性支持 + NeoForge 1.21.11 注入修复 + 兼容性调整**

- **新增 1.16.5 原版支持（实验性）**：
  - 新增 `Vanilla1165Mappings` 独立映射集（1.16.5 专用 Mojang 混淆命名，Minecraft=djz）与重新生成的
    `vanilla1165/joined.srg`（CL/FD/MD 全量，含无前缀方法行）；探测锚点 djz/C/F/dwt/dzm/dzj/brx → 映射版本 36
  - 补齐 1.16.5 缺失映射：`MStatusEffect` 无条件注册（修复 PotionRegistry 初始化 NPE）；`MGlStateManager._blendFunc`
    在两个分支注册（修复 HUD 渲染 NPE）；`MMatrix4f.store(FloatBuffer)`；`MMinecraft.gameSettings` 字段
  - 修复 1.16.5 GUI 缩放异常（物品栏/准星缩到左上角）：`GuiRenderPrimitives.d()` 判定扩展到 1.16.5（与 1.17+
    同为 LWJGL3 着色器管线，不走固定管线 glOrtho 路径）
  - ⚠️ 实验性：部分映射、渲染和模块功能可能无法正常工作（详见 README 兼容性表）
- **NeoForge 1.21.11 注入修复（重要）**：
  - 根因：NeoForge 1.21.11 运行时加载官方可读名的 patched jar（`net.minecraft.client.Minecraft` / `instance` /
    `getInstance`），同时"已认领"的混淆 jar 仍对 AppClassLoader 可见；此前类解析先试混淆名 `gfj`，命中静态单例
    为 null 的错误拷贝 → `Minecraft.getInstance()` 返回 null、渲染钩子不触发、注入卡在帧等待
  - 修复：`VanillaSrgMappings.resolveClass` 改为先试规范名（SRG/mojmap）再试混淆名——NeoForge 直接命中游戏
    真类；原版混淆运行时规范名不存在自动回退，行为不变
  - 注入时机：等待渲染/客户端线程出现（游戏主循环就绪）后再初始化，避免 ModLauncher 早期注入时类加载器与
    类身份不稳定
  - `EnchantmentUtil` 注册表引导容错：NeoForge 启动早期 "Not bootstrapped" 时返回空结果并不缓存，待引导完成
    后重试，避免类初始化被异常污染
  - `Minecraft.i()` 不再缓存 null 的 getInstance 结果
- **1.20.1 / 1.21.1 Fabric 明确不支持**：Fabric Knot 类加载隔离 + 双份 slf4j 冲突为环境级问题，注入时直接
  中止并提示，避免半可用状态误导（兼容表标注 -）
- **退出修复**：VapeService 的 Netty 事件循环线程改为守护线程（命名线程工厂），不再阻塞游戏进程退出
- **错误可见性**：`EventRenderWorldPassExecutorDrain.fire()` 不再吞掉 TBE 任务异常，改用 `Vape.logThrowable`
  记录（帧初始化失败等会写入日志，便于定位注入挂起）

## v4.21.21 (2026-08-23)

**动态模糊全版本支持 + 26.2 修复 + 跨版本稳定性**

- **动态模糊（MotionBlur）改为 HUD 模块**：从「其他」分类移到 HUD 模块区（Game 分组，与方块彩色边框同区域），名称/设置不变；注册保留版本约束（1.17+ 且 <26.2）
- **动态模糊全版本支持**：
  - 新增 `EventFramePresent` 帧呈现事件，注入到 `RenderTarget.blitToScreen()` 出口（画面已 blit 到屏幕 framebuffer 0、swap 前）——1.20.1 / 1.21.1 / 1.21.11 / 26.1 的 Vanilla、Forge、NeoForge 运行时均可用
  - 1.20.1 / 1.21.0-1.21.3 的 `blitToScreen(int,int)` 与 1.21.4+ 的无参 `blitToScreen()` 按版本分别注册；Forge 用 mojmap 类名解析，Vanilla 混淆运行时用 srg 映射
  - 修复 26.1 上启用即黑屏：26.1.2 用 sampler 对象管理采样，解绑 sampler + 生成 mipmap 后纹理可正常采样
  - 修复启用后游戏 GUI 字体变彩色乱码：所有 GL 状态修改（纹理绑定、active 单元、sampler、framebuffer）走游戏 `GlStateManager` 路径，同步其缓存，避免后续渲染采样错乱
  - **模糊强度 ×2**（上限 0.95 防过曝）
- **26.2 修复（Vulkan 后端）**：26.2 首次引入 Vulkan 图形后端，Vape 基于 OpenGL，需将图形 API 切换为 OpenGL（或 `options.txt` 中 `preferredGraphicsBackend:"opengl"`）；保留 GL 就绪检测并在未就绪时提示
- **移除注入超时降级**：不再虚假报告"注入完成"，GL 未就绪时注入保持"进行中"
- **版本探测增强**：Fabric 1.21.11 探测增加 `GpuBuffer`/`CommandEncoder` 独有锚点，避免旧版 Fabric（如 1.20.1-Fabric）误判导致"Unable to determine Minecraft/Forge version"
- **1.20.1 Forge 注入修复**：`RenderTarget` 类解析在 Forge（mojmap 类名）与 Vanilla（srg 混淆）间正确切换
- ⚠️ **已知限制**：1.20.1-Fabric 的 Knot ClassLoader 存在 SLF4J 服务冲突，无法注入（兼容表标注不支持）；26.2 渲染管线无帧末钩子，动态模糊不支持

## v4.21.20 (2026-08-22)

**新增"动态模糊"模块（开发中）与渲染性能修复**

- **新增模块：动态模糊（MotionBlur，其他分类）**：帧混合后处理，开启后根据视角移动产生残影拖尾效果；可调模糊强度、速度自适应、柔和模糊、帧率调制、灰度拖影、菜单/游戏菜单中是否生效
  - ⚠️ **开发中**：目前仅在 1.17+（含 1.20.1/1.21.x/26.1）注册；26.2 的渲染管线无帧末钩子暂不支持；在部分版本上开启后可能出现黑屏或显示异常，请谨慎使用并及时反馈日志
- **性能修复（重要）**：移除渲染线程每帧高频调试日志（`ThreadBoundExecutor` 的 runPending/execute、世界渲染事件排空等），此前每次渲染帧都会触发一次同步磁盘日志写入（打开/关闭文件），导致"游戏显示 100+ FPS 但肉眼只有约 30 FPS"，偶发掉到 1 帧；现在帧率应与实际显示一致
- 修复 1.20.1 / 1.21.1 / 1.21.11 注入后的卡顿问题（同上）

## v4.21.19 (2026-08-22)

**26.1 / 26.2 渲染适配修复与版本兼容性核查**

- **26.1.2**：修复 `GameRenderer.render` 映射——26.1 保持 `render(DeltaTracker, boolean)`（此前误注册 `render(float,long,boolean)` 导致阶段 23 渲染钩子失败）
- **26.2**：修复 `GameRenderer.update(DeltaTracker)` 映射（26.2 将 `render` 改名为 `update`）；修复 GUI 不显示——ClickGUI/HUD 改为在 `GuiRenderer.render()` 前后注入（HUD 在游戏 HUD 之下、ClickGUI 在游戏 HUD 之上），每帧一次，绘制到实际参与合成的渲染目标；不再依赖 `RenderBuffers.drawFromBuffers`（每帧数百次调用的低层命令点，会造成闪烁/叠层/贴图污染）
- **1.21.4-1.21.11**：修正 `GameRenderer.render` 签名——1.21.0+ 全部为 `render(DeltaTracker, boolean)`（此前 1.21.4+ 误用 `render(float,long,boolean)`，可能导致映射注册失败/阶段 23 卡死）
- **跨版本回归修复**：恢复非 26.2 版本的游戏内通知渲染（26.2 的通知在 GUI pass 后绘制以避免字体方块）；26.2 消除 HUD 双重绘制；`flushGuiBatches` 不再改写世界渲染的 framebuffer 绑定状态；字体未就绪时的跳过逻辑仅限 26.x
- **已知限制（实验性适配，可能存在问题）**：
  - 26.2 注入后首条通知（"按右Shift打开GUI"）文字可能显示为方块，打开 ClickGUI 后字体正常——26.x 的 Minecraft 字体桥（FontSet）在 GUI 渲染前未完全就绪
  - 26.x 的 GUI/HUD 层级与官方客户端存在差异（HUD 模块在游戏 HUD 之下，ClickGUI 覆盖游戏 HUD）
  - 1.20.1 / 1.21.1 仍为实验性：部分 HUD 覆盖层定位可能异常、平滑字体初始化失败时回退 legacy 字体
  - 若遇崩溃请反馈日志

## v4.21.18 (2026-08-21)

**1.20.1 / 1.21.1 实验性适配补强（修复剩余映射任务）**

- **1.20.1 Forge**：修复剩余 2 个映射任务通知（鼠标点击方法映射 id=521、实体加入事件 id=659）——`Minecraft.startAttack()` 1.19.3+ 返回 `boolean`（方法映射按正确描述符注册；鼠标点击事件因 boolean 返回方法无法安全注入而跳过，对应功能不受影响）；`ClientLevel.addEntity(int, Entity)` 名称（替代 1.16 的 `addEntityImpl`）
- **原版 1.21.1**：修复 3 个映射任务通知（`MinecraftTickEventMappingTask`、`WorldEntityJoinEventMappingTask`、`EntityRenderPreEventMappingTask`）——
  - V50/V51 表未带参数的方法在混淆名翻译时使用调用方参数描述符（`runTick` 按 `(Z)` 而非 `()` 查询）
  - 1.21.0+ `ClientLevel.addEntity(Entity)` 为单参数（1.20.x 为 `addEntity(int, Entity)`），按版本注册正确签名
  - 1.21.0-1.21.3 的 9 参数 `render` 属于 `EntityRenderDispatcher`（此前注册在 `EntityRenderer` 上导致失败）
- 验证：1.20.1 Forge 与 1.21.1 原版启动日志均无映射任务失败，`OK initializeManagers` + `injection is active`，渲染钩子正常触发
- **已知限制（实验性适配，可能存在问题）**：1.20.1 / 1.21.1 仍为实验性适配，可能存在以下问题：
  - 部分 HUD 覆盖层（如生命值覆盖层）定位可能异常（渲染位置偏移，如出现在右下角）
  - 平滑字体初始化失败时回退 legacy 字体渲染，偶发 GUI 边缘黑边
  - 部分映射任务（鼠标点击事件等）按设计跳过注入，对应功能不可用
  - 若遇崩溃请反馈日志

## v4.21.17 (2026-08-21)

**新增 1.20.1 与 1.21.1 实验性适配**

- 新增 **1.20.1** 与 **1.21.1** 的 Vanilla / Forge / NeoForge 运行时适配（实验性）：版本检测、映射加载、注入、GUI、HUD 与大部分模块可正常工作
- 1.21.1：修复原版（混淆名）运行时成员名翻译（obfmembers 映射按混淆 owner + 混淆参数描述符生成，V50/V51 表未覆盖成员回退 ObfMap）、NeoForge 1.21.1 渲染钩子（`render(DeltaTracker, boolean)`）、`GameRenderer` 9 参数实体渲染注入
- 1.20.1 Forge（mojmap 类名 + SRG 成员名）：修复启动阶段 23 死锁（`GameRenderer.render(float,long,boolean)` 方法名）、Item `getId`/`byId`、ITEM 注册表迁移到 `BuiltInRegistries`、`OptionInstance` 包裹的设置项（gamma/guiScale/mouseSensitivity/fov 等）读写、字体 `drawInBatch`（Matrix4f/MultiBufferSource/DisplayMode）适配、`GlStateManager$BooleanState` 字段 SRG 名
- 版本检测修正：26.1.2/26.2 不再被 NeoForge 1.20.1/1.21.1 探针误判（`matching=2` → 注入中断）
- **已知限制**：1.20.1 / 1.21.1 上部分映射任务（鼠标点击事件、实体加入事件、3D 渲染事件、网络包事件、计分板渲染）可能提示「注入出错」，仅影响对应功能；若遇崩溃请反馈日志

**配置持久化**

- 修复选中配置档（profile）重启后未恢复：内置 profile（Classic/Modern PVP）无 online id，改为持久化稳定 local id，重启后按 local id 恢复选中

## v4.21.16 (2026-08-20)

**配置保存修复（1.7.10 / 1.8.9 / 1.12.2 / 1.16.5 等老版本）**

- 修复老版本（1.8.9/1.12.2 等）配置无法保存、第二次注入设置重置：`LocalConfigStore` 用了 Gson 2.8.6+ 才有的 `JsonParser.parseReader`，而 1.7.10/1.8.9（Gson 2.2.4）、1.12.2（2.8.0）、1.16.5（2.8.5）自带的 Gson 均无此方法 → 每次读配置都抛 `NoSuchMethodError`，配置读不出也存不回。改为全版本兼容的 `new JsonParser().parse(...)`
- 同类问题一并修复：`JsonParser.parseString`（LegacyHttpServer）、`JsonPrimitive.isNumber`（ConfigJsonUtils）、`JsonElement.deepCopy`（FileStore/LegacyHttpServer，改为自实现递归深拷贝），覆盖所有旧 Gson 环境
- 修复离线/全新环境下配置无法保存：`SyncThread.loadConfig` 对 `accountInfo` 为 null（本地服务连不上）做保护并走 standalone 配置加载；无存储 profiles 时创建内置默认 profile，保证至少有一个 profile 可持久化模块设置

**加载提示**

- 原生加载超时 / 阶段加载异常长的提示末尾增加"注：26+版本请在打开世界后注入"（现代版本建议进入世界后再注入，避免初始化阶段等待游戏世界导致卡顿/超时）

**Badlion 1.8.9 键盘队列与模块协作**

- 恢复 Badlion 1.8.9 键盘事件队列接线（clicker 线程经队列投递按键事件，不再直接改键位状态），ClickerWorker/KeyBindingHelper/Badlion189InputQueueMappingTask 恢复
- ShieldBreaker 恢复对 HitSwap/AutoMace 合成攻击的排除，新增 Double click、Limit to items、Allowed Items 选项（含汉化）与 `hasAxeInHotbar()`
- Triggerbot / SilentAura 恢复盾检豁免与重锤冷却跳过；SilentAura 恢复 Shield check 选项（含汉化）
- HitSwap 恢复排除 ShieldBreaker 与 AutoMace 进行中的合成攻击

**其他修复**

- 中文长文本换行丢末字：`WrappedTextComponent` 拆词时 `substring` 结束索引丢最后一个字符（"建议避免使用"→"建议避免使"），改为保留末字并加边界保护
- ArmorStatus HUD 空指针保护（player/container/slot 三重兜底）
- 解压文件改名：产品 JAR → `Vape-v4.21-product-<pid>.jar`、目录 → `Vape-v4.21Recovery`
- 清理机制修复：`.vapeclient\Vape-v4.21Recovery` 每次注入自动清空旧文件（递归删除 + 解压前 sweep），不再随注入次数堆积 DLL/JAR
- 同进程（PID）重复注入时，若 DLL 被先前会话占用，自动复用已有文件
- 构建增量检测修复：native 源码递归跟踪 `**/*.cpp`，子目录改动不再误判 UP-TO-DATE

## v4.21.15 (2026-08-20)

**外部 DLL 优先 + 双文件发布**

- exe 同目录存在 `Vape-v4.21Native.dll` 时，优先直接使用外部 DLL，不再解压内嵌副本（GUI 与 `-nogui` 两种模式均支持；便于单独替换/更新原生库，无需重发整个 exe）
- 外部 DLL 不存在时自动回退：解压内嵌 DLL 到 `<exe>\.vapeclient\Vape421Recovery\`（原有行为不变，仍不写 `%TEMP%`）
- 自动构建产物与 GitHub Release 现在同时附带 `Vape-v4.21.exe` 和 `Vape-v4.21Native.dll`（双文件发布）

## v4.21.14 (2026-08-20)

**生命值 HUD 修复**

- 修复生命值在所有现代版本（1.21.11 / 26.1.2 等）完全不显示：根因是 `ActiveModuleStackFrame` 用 Minecraft 原生 `FontRenderer.drawStringWithShadow`，而 1.20.6+ 该旧签名不存在（基于 GuiGraphics）→ 静默不绘制。改为统一使用 Vape 自绘字体（SmoothFontRenderer），与 Keystrokes 等正常 HUD 帧一致
- 生命值居中坐标对齐其他 HUD 帧的实际坐标系（`窗口/(2×缩放)`），字号调大至 1.5 倍更清晰

**寻找方块（Search）修复**

- 修复现代版本搜不到方块：`ClientChunkProvider.chunkListing` 字段在现代 `ClientChunkCache` 不存在，改为按玩家周围区块坐标扫描；`Block.t` 现代版增加物品注册表 fallback
- 现代版扫描改由 SearchProcessor 单路径负责（避免双扫描并发清空结果）
- 翻译：寻找矿物 → **寻找方块**（Search 为通用方块搜索器，可搜任意方块）

**翻译与界面**

- 输入框不再自动翻译用户输入（输入 Diamond 不再变钻石，仅 placeholder 翻译）
- 天神搭路 → 神桥（godbridge）

**渲染修复**

- 补交 3D 渲染修复（26.1.2/1.21.10 事件钩子矩阵、实例化渲染器矩阵、视口/FBO 状态），修正矿物边框错位

## v4.21.13 (2026-08-20)

**1.7.10 兼容性修复（SilentAura / XRay）**

- SilentAura（1.7.10）：攻击直接锁定目标实体（`PlayerControllerMP.attackEntity`），不再依赖准星 rayTrace——"右键打准星所指而非目标"修复
- SilentAura（1.7.10）：移除发包视角改写——此前改写服务器视角导致服务器频繁发 S08 位置/视角同步包，把本地视角拉向目标、移动被拉回，并触发实体 tick NPE 崩溃
- SilentAura（1.7.10）：攻击时显式 `swingItem()` 播放本地挥动动画并广播挥动包（其他玩家可见），本地视角完全不转动（全静默）
- SilentAura（1.7.10）：隐藏无用的"瞄准速度"设置（该版本不发包旋转，设置不生效；1.12.2+ 仍可用）
- XRay（1.7.10）：修复开启闪退（Tessellator isDrawing 字段缺失保护）、非目标方块完全隐藏（稳定跨 chunk 重建）、矿洞模式（六方向相邻判断暴露矿块）、矿洞模式切换即刷新、隐藏无效透明度滑块
- 翻译：比较器→计数器、后视镜 Level view→平视、生物名（牛/猪/狗/熊/虎等）字库全覆盖、TargetInfo tooltip 补译、按键绑定/徽章文案修复

## v4.21.12 (2026-08-19)

**通知文案翻译修复**

- 修复自救/搭梯失败通知（"Server rejected block placement!" 等）仍显示英文：根因是通知文本经 WrappedTextComponent 按空格拆行后再做整句翻译匹配，拆行后的碎片永远匹配不到完整句子键
- `WrappedTextComponent.getWrappedLines()` 改为在拆行前先对完整文本翻译一次，未知文本（玩家名/动态消息）原样返回不受影响
- 顺带补译 server_rejected_block_placement 键（服务器拒绝了方块放置！），并核查全部救援失败通知（Clutch Failed / AutoLadder Failed / Server teleported you! / No ladder available! / No support block available! / Could not find a valid laddering solution!）均已有翻译

## v4.21.11 (2026-08-19)

**翻译与界面完善**

- 修复按键绑定文案仍显示英文：`Properties` 键不允许空格，改用下划线键（`UI_PRESS_A_KEY_TO_BIND` 等），JVM 级验证全部按键提示（按任意键绑定 / 绑定已移除 / 已绑定 / 必须先绑定 / 通过按键使用 / 设置按键 / 输入物品名称 / 正在编辑收藏）映射正确
- 补译界面徽章与占位文案：New!→新！、UNSAFE→不安全、INDEV→开发中、Beta/BETA→测试版、Type message...→输入消息...、User is offline→用户已离线、Click to remove bind→点击移除绑定（补全全大写 BETA 键）
- 术语统一：锚点→重生锚、救场→自救、上帝视角→自由视角（Freecam）、围堵→围墙（BlockIn）、变速齿轮→变速（Timer）、距离→攻击距离（Reach）、自由视角→自由旋转视角（Freelook）
- 部分翻译中文化：Combo→连击、Kite→风筝、overlay→覆盖层、nametags→名称标签、Bot→机器人、Post→后置；CPS / GUI / FPS / ESP / WTap / 甩枪 保留原文（社区通用）
- 修复中文字体过细：noto.ttf 重建时固定字重为 SemiBold(600)（NotoSansSC-VF 默认 Thin(100) 导致文字发虚），新增 `tools/rebuild_noto.py` 一键重建脚本
- 修复退出世界闪退：`CoordinatesHudFrame.getBiomeName` 空世界保护（退出世界后 HUD 坐标框多渲染一帧导致 NPE）

## v4.21.10 (2026-08-18)

**文件落盘收拢（不再写 %TEMP%）**

- 所有运行产物全部收进 exe 旁隐藏的 `.vapeclient` 目录，不再向 `%TEMP%` 写入任何文件：
  - 内嵌 DLL / 产品 JAR 解压到 `<exe>\.vapeclient\Vape421Recovery\`（原 `%TEMP%\Vape421Recovery\`）
  - 注入目录不再经 `%TEMP%\injector_dir.txt` 传递：DLL 从自身模块路径（固定位于 `.vapeclient\Vape421Recovery`）推断 exe 目录，彻底废弃跨进程 TEMP 标记
  - 注入诊断改为写到 DLL 模块目录（`.vapeclient\Vape421Recovery\vape_injector_diag.txt`），不再写 `%TEMP%\vape_injector_diag.txt`
  - Java 侧映射失败转储改到 `.vapeclient\Vape421Recovery\`（原 `java.io.tmpdir`）
  - 在线纹理缓存从 `~/vapeTextures` 移入 `.vapeclient\vapeTextures`
  - 服务数据 `vape-service.json` 与本地配置统一到 exe 旁 `.vapeclient`（跟随 DLL 注入的 `vape.directory`）
- `.vapeclient` 目录自动设为隐藏属性（GUI / 命令行 / DLL 三处创建时均设置），Explorer 中不再显眼
- 启动时顺手清理旧版本遗留的 `%TEMP%\injector_dir.txt` / `vape_injector_diag.txt`

**修复**

- 修复 `.vapeclient` 落点与 exe 目录不一致：GUI 注入不再依赖可能过期的 TEMP 标记，路径始终由注入链路内的模块位置推导

## v4.21.9 (2026-08-17)

**GUI 单文件加载器**

- 上游 VapeLoader（GDI+ 图形界面）集成进主产物：登录 / 浏览器授权 / 进程选择 / 注入进度界面，全中文化
- 去掉登录页：启动即进入 Minecraft 进程选择，本地生成 token，无需外部服务
- 去掉缓存询问页：注入完成直接显示加载完成页
- 窗口标题「Vape v4」；图标与产品一致（vape-v4.21.ico 嵌入）
- 移除缓存询问与外部 DLL 加载：GUI 与命令行模式均从内嵌资源（ID 422 RCDATA）解压注入，不加载外部 DLL
- 支持命令行模式：`Vape-v4.21.exe -nogui [pid]` 启动命令行注入器（进程选择器 / 指定 PID）
- 错误页去掉「联系支持」按钮，「复制错误」居中

**产物精简**

- 移除 Vape421Injector / Vape421InjectorStandalone 目标与单独 DLL 产物
- 最终构建产物仅保留一个单文件：`Vape-v4.21.exe`（内嵌 DLL + 图标 + 全部资源）

**修复**

- 修复 AutoTotem 创造模式无法装备副手图腾：根因是物品栏打开判断未识别创造物品栏（GuiContainerCreative），导致每 tick 重复开背包、点击永不执行；现识别创造物品栏并改用 PICKUP 拿起→放下（ClickType.SWAP 在创造模式被新版拒绝）
- 修复进程选择器长标题显示不全：加宽标题绘制区域，超长标题用 GDI+ 实测宽度二分截断并追加省略号（中英文混排精确）
- 修复注入报「产品 DLL 拒绝套接字引导块」：消除登录死锁（token 本地生成）
- 兼容性审计：AutoTotem 等 ≥1.21.4 模块在老版本自动跳过，不会误加载或崩溃

## v4.21.8 (2026-08-17)

**上游新功能集成**

- 合并上游 VapeV4.21 新模块：AutoMace（自动重锤，含重锤选择 / 眩晕猛击 / 瞄准范围 / 自动卸下鞘翅 / 仅猛击 / 显示快捷栏）、NoItemRelease（不释放物品）、PearlCatch（接住珍珠）
- 合并 InventoryOverlay（物品栏覆盖显示）组件与其设置页，可在 HUD 设置页开启
- 合并 Badlion 旧版按键事件队列（BadlionKeyBindingEventQueue / Badlion189InputQueueMappingTask），Badlion 客户端按键兼容
- 移除与现有 Animations 模块重复的上游 BlockHit 模式文件（保留原 Animations 的 Manual / Predict / Auto / Lag 模式）

**内嵌 VapeService（加载器配套服务）**

- 将上游 VapeService（HTTP 8080 + Zeus TCP 8091）整体集成进单文件注入包，游戏内自动后台启动，无需单独运行服务 jar
- VapeService 全部 14 个 Java 文件降级为 Java 8 语法（record / `Set.copyOf` / `.toList` / `String.isBlank` / `Optional.stream` / `Optional.isEmpty` / `HexFormat` / `Files.readString` / netty 4.1 API 等），通过 `verifyInjectionPayload`（major ≤ 52）检查
- 服务数据存于 `~/.vapeclient/vape-service.json`；端口被占用时自动向上探测空闲端口，启动失败静默降级不影响游戏

**字库修复（重要）**

- 修复中文界面缺字：原 noto.ttf 为子集字体，缺少 释 / 猛 / 卸 / 鞘 / 翅 / 观 / 晋 / 房 / 址 / 订 / 资 / 料 / 钥 共 13 个字形，其中「不释放物品」「自动卸下鞘翅」「重新装备鞘翅」等新翻译会显示空白/方框
- 使用系统 NotoSansSC 重新子集化生成静态 TTF（去除可变字体表），覆盖全部 1250 个翻译字符，经 stb（游戏实际渲染引擎）验证 0 缺失；旧字体备份于 `noto.old.ttf.backup`

**本地化与界面（正式版补充）**

- 服务启动可配置：`VAPE_BIND_ADDRESS` / `VAPE_HTTP_PORT` / `VAPE_ZEUS_PORT` / `VAPE_DATA_FILE` 环境变量（默认 127.0.0.1 / 8080 / 8091，绑定 0.0.0.0 可局域网访问）
- 中文字体加粗：noto.ttf 由 Thin(100) 字重改为 SemiBold(600) 字重子集（322KB），界面文字更清晰
- 补全翻译：PearlCatch（瞄准模式/蓄力延迟/向上/当前瞄准）、AutoMace（密度/破甲模式名、概率、目标设置）、InventoryOverlay（物品栏覆盖标题与提示）、KillAura 完美挥击提示、「完美挥击」tooltip 等
- 修正翻译：「修改方块人（我的世界）游戏时间。」→「修改游戏时间。」
- 模块搜索同时匹配英文名与中文翻译名（中文可直接搜到模块）
- 分类导航放开「其他」分类入口：新版模块页与旧版 GUI 均显示，可直接查看 Other 分类模块（如不释放物品）

**标准译名与字库同步**

- mace 改用官方译名「重锤」（原误译「狼牙棒」），涉及 AutoMace 模块名、设置项、物品名与全部 tooltip 共 17 处
- 修正翻译：投掷器颜色（原错位为「投掷器潜影贝」）、smash（重击）与 breach（破甲）混淆、删除重复的 smash_only 死值
- 字库按更新后的翻译字符集重新子集化，补入「锤」等新字形（stb 验证 1248 字符 0 缺失）

## v4.21.7 (2026-08-17)

**本地化与界面修复**

- 修复中文界面下模块设置值名（如完美挥击、需要鼠标按下等）显示英文：语言初始化提前到启动阶段，不再受 GUI 渲染时序影响
- 修复切换到 English 语言后模块子选项文字不渲染：值行换行缓存随语言切换失效重建
- 语言选项精简为「中文 / English」两种
- 大量补全翻译：SilentAura/各类模块值名与提示、AntiDebuff、MLG 水桶、BlockIn 黑名单、CrystalAura 效率/防自杀、完整性检查、NBT 标签、使用好友、重置角度、在线状态页（Error establishing / Registration offline / 重连倒计时）等
- 换行提示文本整串翻译（`WrappingTextLabelComponent` 先翻译再换行）
- 修复 Frame 设置导航标题（「Profiles settings / Friends settings」→「配置 设置 / 好友 设置」）与 Overlay 标题翻译
- 列表 / 白名单条目不再经过 GUI 翻译表，显示配置原文（默认例子显示英文 Zombie / Skeleton / Creeper / Spider，输入什么显示什么）
- 注入完成通知、在线重连文案中文化

**按键显示（Keystrokes）**

- WASD / LMB / RMB 等按键文字与图标在按键格内居中显示

## v4.21.6 (2026-08-15)

**渲染修复**

- 修复 26.1.2 查找矿物（Search）3D 方块轮廓不渲染：`EventRender3D` 钩子改用 `modelViewMatrix` 参数，实例化渲染器矩阵正确；修复 `RenderBatchState` 字段初始化顺序、1×1 视口与 FBO 绑定问题
- 修复 26.1.2 相机矩阵来源（改用 `GameRenderer` 主相机四元数），Search 方块轮廓不再错位/漂移

**刷怪笼查找（SpawnerFinder）**

- 修复中文客户端（26.1.2）白名单无法匹配：白名单条目同时支持本地化名称（僵尸）、资源键（`minecraft:zombie`）与英文名（Zombie）匹配
- 渲染标签本地化：「僵尸 刷怪笼」（实体名 + 距离）
- 移除调试诊断日志

**本地化与界面**

- HUD 设置翻译「抬头显示」→「显示」
- 列表 / 白名单条目不再经过 GUI 翻译表，一律显示配置原文（默认例子显示英文 Zombie / Skeleton / Creeper / Spider，输入什么显示什么）
- 内置配置文件档按钮恢复英文：Classic PVP / Modern PVP

## v4.21.5

- 时钟（Clock）HUD：位置夹取屏幕内、模块列表打开时隐藏
- 后视镜（Rearview）：离屏渲染 HUD 重叠抑制、UV 镜像修复；26.1.2 上隐藏（<1.16.5 约束）
- 配置目录定位：`.vapeclient` 优先位于注入器 EXE 同目录
- 注入器控制台中文横幅

## v4.21.4 及更早

- 配置本地持久化（`.vapeclient\config.json`）
- 完整中文本地化（2600+ 键）、默认中文
- 26.1.x / 26.2.x 运行时版本探测
- 单文件注入器、统一日志目录
