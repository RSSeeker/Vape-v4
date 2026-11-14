# KissClient 1.7.3 vs Vape 4.21 — ESP / 渲染类功能对比（逆向只读）

> 分析方式：Kiss 为反编译混淆源码（只读），Vape 为本仓库源码（只读）。
> 证据引用格式：`文件:行号`。Kiss 路径均相对 `C:\Users\Jiang\AppData\Local\Temp\kiss-src\`（下文简写为 `kiss/`），Vape 路径相对仓库 `src/main/java/gg/vape/`。
> 凡无法从代码/数据表中确认的结论，一律标注「未发现」，不做推测。

---

## 1 概览

### 1.1 分析对象

| 项 | KissClient 1.7.3 | Vape 4.21 |
|---|---|---|
| 平台 | Fabric（`kiss/kf.mixins.json`，42 个 client mixin，包 `gdp.g`） | 源码工程，MCP/Forge 多版本（`ForgeVersion.MC_1_7_10` … `MC_26_2`） |
| 代码形态 | 反编译 + 混淆（类名 `ss/x/ga/d.java`、方法 `mg137`、字段 `fs152`） | 可读源码，语义化命名 |
| 模块总数 | 147（`build/kiss-tools/modules.json`），其中 `RENDER` 26 个 | `module/render` 包 9 个主模块 + 4 个 ESP 子模式 + 5 个子包 |
| 渲染主体 | Skia（`com.kiss.ui.system.*`），2D 画布叠加 | OpenGL（`utils/render/*`，批次缓冲 + 立即模式双路径） |
| 字符串可读性 | 已清空，仅 `build/kiss-tools/strings_full.json` 保留 token 列表 | 完整可读 |

### 1.2 版本线索（Kiss，推断）

- `kiss/gdp/g/ai.java:16` 注入 `shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z`；
- `kiss/gdp/g/aj.java:21` 注入 `submitNameDisplay(...Lnet/minecraft/client/renderer/state/level/CameraRenderState;I)V`；
- `kiss/gdp/g/q.java:17` 注入 `GpuSurface.blitFromTexture`；
- `kiss/com/kiss/ui/system/SkiaManager.java:35` 使用 `mc.getDeltaTracker().getGameTimeDeltaPartialTick(false)`。

这组 API 组合（RenderState/submit 渲染管线 + `GpuSurface` + `DeltaTracker`）对应 1.21.9/1.21.10 级别的渲染重构。另据 jar 根 `fabric.mod.json` 原文（`id=kissclient`）显式声明 `"minecraft": "~26.2"`、`"fabricloader": ">=0.19.3"`、`"java": ">=25"`，即目标版本为 26.2；`META-INF/jars/types-0.2.0.jar` 内另有 `id=io_github_humbleui_types` 的同名文件，解包到同目录时会覆盖根文件，注意区分。

### 1.3 一句话结论

Kiss 的 ESP 家族是「**小而精的 2D 投影叠层**」：ESP、NameTags、BedESP 三个模块共享一套 Skia 投影管线，视觉完成度高但参数极少、无颜色自定义、无 Tracers/StorageESP，且 Item/WorldItem/GuardFilter 是**纯空壳**。
Vape 的 ESP 家族是「**四模式 + 大参数面 + 多版本分支的 GL 管线**」：2D/3D/Outline/Skeleton 四种子模式、13 项 ESP 参数、模板缓冲描边、批次渲染器，并配套 Tracers/StorageESP/BedPlates/Indicators/Arrows 完整矩阵。

### 1.4 规模数字

- Kiss `RENDER` 类模块 26 个，其中**真正含 2D 绘制调用的仅 6 个**：ESP `ss/x/ga/d.java`(477 行)、NameTags `du/a.java`(1319 行)、BedESP `p/ibr/zq/a.java`(394 行)、HUD `gt/z/b.java`、List `ocg/c.java`、WaterMark `qs/ysn/b.java`；Chams 走 Mixin（`i/ur/vz/b.java` 仅 64 行判定逻辑）。
- Vape `module/render` 主模块合计 3270 行，`esp/*` 子包 871 行，`utils/render/*` 60+ 文件合计 **20862 行**。
- Kiss 全库 `EntityItem` 命中 **0 次**（`grep -rl EntityItem kiss/` 无输出）→ 掉落物相关功能无实现。

---

## 2 功能矩阵

图例：✅ 实现并生效　◐ 部分/受限　✗ 未实现　— 未发现

| 能力 | Kiss 1.7.3 | Vape 4.21 | 证据 |
|---|---|---|---|
| 2D 屏幕角框 | ✅ 圆角 3px + 双层描边 + 12% 白填充 | ✅ 无圆角、黑双层 + 彩色内描边 | Kiss `ss/x/ga/d.java:256-280`；Vape `esp/ESP2D.java:89-136` |
| 3D 世界方框 | ✅ 但实为 2D 投影 12 条线 | ✅ 真 GL 线（world pass，关深度） | Kiss `ss/x/ga/d.java:346-368`；Vape `esp/ESP3D.java:25-76` |
| Outline（模型描边） | — | ✅ 模板缓冲 + display list 5 轮 | Vape `esp/ESPOutline.java:143-259` |
| Skeleton（骨骼） | — | ✅ ModelBiped 旋转角逐段划线 | Vape `esp/ESPSkeleton.java:71-171` |
| 血条 | ✗ ESP 无；NameTags 有血量数字 | ✅ ESP 2D 竖向血条（5 段阈值色） | Vape `esp/ESP2D.java:140-190` |
| 名字文字（ESP 内） | ✗ | ✅ 可开关 + 背景框 + 高亮红框 | Vape `esp/ESP2D.java:191-219` |
| 距离显示 | ✗（仅做距离剔除） | ✅ ESP 无；NameTags/ItemESP/Tracers/Arrows 有 | Vape `NameTags.java:426`、`ItemESP.java:152` |
| 自定义主色 | ✗ ESP 颜色写死 | ✅ `Player Color` + 主题/好友/敌人色 | Vape `ESP.java:57,81-114` |
| 不透明度 | ◐ 固定 alpha 常量 | ✅ NameTags `Opacity` 0–1 | Vape `NameTags.java:443` |
| 渐变 | ✗ | ◐ 批次渲染器支持渐变胶囊，ESP 未用 | Vape `utils/render/RenderBatchBuilder.java:254` |
| Chams（穿墙着色） | ✅ RenderType 换成恒过深度测试的 shader | ✅ 双通道重绘 + 双色 | Kiss `gdp/g/d.java:39-51`、`g/pbd/j.java:31-37`；Vape `Chams.java:111-197` |
| Chams 颜色 | ✗（无参数） | ✅ 可见色/隔墙色 + `Color Behind Walls` | Vape `Chams.java:201-205` |
| Chams 影响范围 | 玩家/怪物/动物/自己 4 开关 | 仅玩家（`MappedClasses.Yl` 且非 `z5`） | Kiss `i/ur/vz/b.java:49-63`；Vape `Chams.java:67,90,123` |
| Chams 方块/物品 | ✗ | ✗ | 双方均无 |
| ItemESP / 掉落物 | ✗ 空壳（无绘制、全库无 `EntityItem`） | ✅ 3D billboard 文字标签（名/数量/距离） | Kiss `p/ibr/zq/l.java:263-272`；Vape `ItemESP.java:61,160-268` |
| 物品图标渲染 | ✗ | ✗（只有文字，无 icon） | Vape `ItemESP.java:143-159` 只拼字符串 |
| 物品分组/白名单 | ✗ | ✅ `Group Items` 合并 + `Whitelist Only` | Vape `ItemESP.java:100-118,122-125` |
| Tracers | — 未发现 | ✅ 视线起点 + 黑底描边 + 距离色 + 聚焦 3× | Vape `Tracers.java:146-220,287-` |
| StorageESP | — 未发现 | ✅ 8 类容器 + 开箱检测 | Vape `StorageESP.java:47-120,183-198` |
| BedESP（床框） | ✅ 分帧预算扫描 + 床头尾合并 + 6 色 | ✗（无同功能模块） | Kiss `p/ibr/zq/a.java:150-167,199-260,327-343` |
| BedPlates（床防御清单） | — 未发现 | ✅ 分 3 层统计床周方块并渲染清单 | Vape `BedPlates.java:66-180,420` |
| Indicators / Arrows（屏外指示） | — 未发现 | ✅ 椭圆半径定位箭头 + 6 类投射物 | Vape `Indicators.java:131-256,522-531`、`Arrows.java:50-133` |
| NameTags 替换原版 | ✅ 3 个 Mixin 三重接管 | ✅ 取消 `EventPreRenderLiving` | Kiss `gdp/g/ai.java:16-40`、`ak.java:20-49`、`aj.java:21-52`；Vape `NameTags.java:223-234` |
| NameTags 头像/队伍/装备 | ✅ 皮肤头像 + 队伍前缀 + 手持物图标 | ◐ 装备图标 + 队友前缀，无头像 | Kiss `du/a.java:870-1000+`；Vape `NameTags.java:131-215,367,379` |
| 中文/CJK | ✅ MiSans（7.94 MB，含 CJK） | ◐ `noto.ttf` 1.09 MB，CJK 覆盖未确认 | Kiss `assets/kf/fonts/misans.ttf`、`FontManager.getFont`；Vape `bin/main/resources/noto.ttf` |
| 反截图/录屏屏蔽 | — 未发现 | — 未发现 | 双方 `grep -ri screenshot` 均 0 命中 |

---

## 3 Kiss 实现细节

### 3.1 渲染基础设施（三个类 + 四个 Mixin）

**投影与屏幕坐标 — `kiss/com/kiss/ui/system/RenderState.java`（212 行）**

- 静态缓存 `viewProjMat / camPos / camRot / fov / guiScale / projReady`（`:15-25`）。
- `update(float partialTick)` `:41-90`：取 `mc.getEntityRenderDispatcher().camera`，`camera.getViewRotationProjectionMatrix(viewProjMat)` `:70`，`guiScale = mc.getWindow().getGuiScale()` `:71`；仅当 `mc.gui.screen()==null || ChatScreen` 才继续 `:49-51`。
- `isValid()` `:99-107`：窗口存在非 Chat 界面时返回 `true`（绕过超时）；否则 `System.currentTimeMillis()-timestamp < 100L`。这是「100 ms 内本帧投影可用」的时效闸门。
- `project(Vec3)` `:133-153`：`viewProjMat.transform(world - camPos)` → `clip.w <= 0` 返回零点（`out[2]=0`）→ NDC → `(ndcX+1)/2 * (window.getWidth()/guiScale)`，即 **GuiScaled 坐标**；`out[2]=1` 表示有效。
- `projectAabb(AABB)` `:155-158` 仅投影中心点。
- `fovMultiplier(LivingEntity)` `:187-211`：`AbstractClientPlayer.getFieldOfViewModifier(true,1.0F)` + 120 ms 平滑。

**Skia 生命周期 — `kiss/com/kiss/ui/system/SkiaManager.java`**

- `render()` `:27-`：`RenderState.update(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false))` `:35` → `skia.initSkia()` / `checkAndUpdateSurface()` / `beginFrame()`；`isWarmedUp()` 为假时本帧 `clear(0)` 后直接 `return`（预热帧）→ `Canvas mainStack = Skia.canvas` → `asf.e.rqc.b.INSTANCE.renderOverlay(mainStack)` → `uiSurface.clear(0)`。
- `markFrameStart()` 置 `frameRendered=false`，由 `kiss/gdp/g/o.java:18-26` 在 `GameRenderer.render` HEAD 每帧调用；`:28-38` 同 Mixin 内取消 `bobHurt`（NoHurtCam）。
- 2D 叠层的进入点不是 HUD 事件，而是 **GPU 提交钩子**：`kiss/gdp/g/q.java:17-21` 在 `GpuSurface.blitFromTexture` HEAD 调用 `Main.INSTANCE.skiaManager.render()`。
- 3D 世界阶段钩子：`kiss/gdp/g/t.java:23-31` 注入 `LevelRenderer.render(GraphicsResourceAllocator,DeltaTracker,Z,CameraRenderState,Matrix4fc,GpuBufferSlice,Vector4f,Z)` HEAD。

**绘制封装 — `kiss/com/kiss/ui/system/Render2D.java`**

`drawRect / drawRoundedRect / drawCircle / drawLine / drawRectBlur / drawRectGlow(scissorRect, ClipMode)`，内部 `BlurUtil` / `GlowUtil` / `FilterCache`；`drawRect` 在 `alpha==0` 时直接 return（省一次 GPU 命令）。

**字体 — `kiss/com/kiss/ui/system/font/FontManager.java`**

`getFont(int)` → `get("misans.ttf", size, false)`；`getBoldFont(int)` → `get("misans.ttf", size, true)`；`getIconFont` → `icon.ttf`；`getProductFont` → `product.ttf`；缓存 `ConcurrentHashMap<String,SkiaFont>`，键 `path:size:bold`。
实际字体文件：`kiss/assets/kf/fonts/misans.ttf`（7,943,504 B）。MiSans 为小米开源字体、含完整 CJK —— Kiss 的 NameTags 直接用 Skia 绘制 `[凶手]` 等中文串（`du/a.java:616-622`），中文显示无缺字风险。

### 3.2 ESP — `kiss/ss/x/ga/d.java`（477 行）

**注册与参数**

- `@ModuleInfo(category=Category.RENDER, key=0)` `:37-47`，无默认键位。
- 参数只有 5 项（`:60-77, :111, :163, :172`）：
  - `ModeSetting("Draw Mode", "2D", {"2D","3D"})`，默认 **2D**；
  - `BooleanSetting("Players", true)` / `("NPCs", true)` / `("Mobs", true)`；
  - `NumberSetting("Max Distance", 128.0, 16.0, 512.0)`。
- 参数名由 `build/kiss-tools/strings_full.json` 的 `ss/x/ga/d.java` 条目恢复：`Draw Mode`、`Max Distance`、`ESP getFov not resolved, using fallback sprint-FOV math`。

**事件与门控**

- `@EventTarget onRender(EventRender2D)` `:296-344`；只在 `mc.gui.screen()==null` 或 `ChatScreen` 时绘制 `:303`；先 `RenderState.isValid()` `:315`；画布 `Canvas var6 = var1.getCanvasStack()` `:320`。
- 距离剔除：`var10 = range*range`，`var13.distanceToSqr(mc.player) > var10` 跳过 `:321-337`。

**实体分类（含反作弊式 NPC 判定）**

- `my933(Entity)` `:398-408`：`AntiBots.shouldSkip(entity)` → 1（NPC）`ss/x/ga/a.java:1250`；`Player` → 需 `mf879(player)` 为真才算 0；`ArmorStand` / `AbstractVillager` → 1；其它 `LivingEntity` → 2；其余 → -1。
- `mf879(Player)` `:175-189`：`id >= 1000000000` 或 `id <= -1` 判 NPC；名字为空判 NPC；**`getPlayerInfo().getLatency() <= 0` 判 NPC（延迟 0 = bot）**。

**2D 角框 `mg137` `:256-280`**

1. 屏幕外或宽高 `< 2px` 直接丢弃 `:257-258`；
2. `RRect.makeXYWH(..., 3.0F)` —— **圆角 3px**；
3. 填充 `536870911` = `0x1FFFFFFF`（白 12.2% α）`:261`；
4. 描边 `1191182336` = `0x47000000`（黑 27.8% α），宽 **1.6px** `:265-270`；
5. 再描边 `-419430401` = `0xE6FFFFFF`（白 90% α），宽 **0.7px** `:272-277`。
   无渐变、无逐实体配色。

**3D 盒 `mj78` `:346-368`**

8 角点构造 `:216`：中心 `getPosition(partialTick)` ± `getBbWidth()/2`（x、z）与 `getBbHeight()`（y），静态索引表 `fs152 / fl814 / fb416` `:67,72,76`；**逐点 `RenderState.project(...)`，任一点 `var15[2] <= 0` 放弃整框** `:217-220`；再按 `ft863` 的 12 条棱 `:75` 用 `drawLine` 画两遍（1.6px + 0.7px），`PaintStrokeCap.BUTT`。
→ 架构含义：Kiss 的「3D 模式」并没有在世界渲染阶段画几何体，而是把 3D 盒投影成 2D 线段，**永远不会有深度遮挡/遮挡关系**（见 §5）。

**两处值得注意的实现细节**

- FOV 反射 `mz220 / mf371 / mc944` `:191-201, 291-294, 370-388`：反射 `GameRenderer.getFov(Camera,float,boolean)` 失败后走 fallback（字符串 `ESP getFov not resolved, using fallback sprint-FOV math`）；但 `RenderState.resolveGetFovMethod()` **恒返回 null**，该分支实为死代码。
- `mb356` `:282-289` 打印 `[KissESP]` 并 `Preloader.send("ESP " + msg)` —— 发布版残留的调试/上报路径。

### 3.3 Chams — 模块只做判定，真逻辑在 Mixin

**模块 `kiss/i/ur/vz/b.java`（64 行，无任何绘制）**

- 参数：`players(true)` `:28`、`mobs(false)` `:32`、`animals(false)` `:35`、`self(false)` `:36`；单例 `INSTANCE` `:29`。
- `public static boolean shouldRender(LivingEntity e, Minecraft mc)` `:49-63`：模块未启用 / 实体 null / 死亡 → `false`；`e == mc.player` → `self`；`Player` → `players`；`Monster` → `mobs`；`Animal` → `animals`；其余 `false`。

**Mixin `kiss/gdp/g/d.java`（52 行）**

- `@Mixin({LivingEntityRenderer.class})` `:17`；`@Unique private static final Map<LivingEntityRenderState,Boolean> CHAMS = new WeakHashMap<>()` `:19-20`。
- `:25-37` `@Inject(method="extractRenderState(...)", at=@At("TAIL"))`：`i.ur.vz.b.INSTANCE.isEnable() && i.ur.vz.b.shouldRender(entity, mc)` → `CHAMS.put(state, TRUE)`，否则 `remove`。
- `:39-51` `@Inject(method="getRenderType(LivingEntityRenderState;ZZZ)RenderType;", at=@At("RETURN"), cancellable=true)`：`original != null && !original.isOutline() && CHAMS.getOrDefault(state, FALSE)` → `cir.setReturnValue(g.pbd.j.get(original))`。

**着色管线 `kiss/g/pbd/j.java`（591 行）**

- 缓存 `Map<RenderType,RenderType> fb930` 与 `Map<RenderPipeline,RenderPipeline> fg21`（ConcurrentHashMap）。
- `mg683(RenderPipeline)` `:31-37`：`withLocation(Identifier.fromNamespaceAndPath("kf","chams/"+ns+"/"+path))`，逐项复制 vertex/fragment shader、polygonMode、cull、topology、bind group layouts、shader defines、color target states、vertex bindings；**核心在 `:36` —— `var1.withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))`：深度比较恒通过且不写深度**（这就是「穿墙」的全部秘密，不是重绘两遍）。
- `get(RenderType)` `:588`：`fb930.computeIfAbsent(var0, j::md23)`，缓存键 `"kiss_chams:" + var0`；异常时回退返回原 RenderType。

**能力边界**：只作用于 `LivingEntity`（玩家/怪物/动物）；**无方块/物品 Chams**；**无颜色/透明度参数**（`strings_full.json` 中 `i/ur/vz/b.java`、`gdp/g/d.java` 均无字符串条目）；`self` 打开时本地玩家也会被套用同一 RenderType。

### 3.4 NameTags — `kiss/du/a.java`（1319 行）+ 3 个 Mixin

**参数**

- `ModeSetting("Npc Check", "STRICT", {"STRICT","AUTO"})` `:170`（配合 `lfh.ty.g.isConfirmedPlayer(player, mode)` 做 NPC 过滤）；
- `BooleanSetting("Self", false)` `:171`；
- `NumberSetting("Max Distance", 128.0, 16.0, 512.0)` `:172-190`；
- `NumberSetting("Scale", 1.0, 0.5, 2.0)` `:186`；
- 样式 `ModeSetting` `:170-190` 区间内：`"Vanilla"` / `"Simple"` / 其它（完整款）；背景 `ModeSetting`：`"Frosted"` / `"Solid"` / 其它（描边）；
- 由 `strings_full.json`（`du/a.java`）恢复的其余设置名：`Show Health`、`Background`、`NameSize`、`Show Held Item`、`Simple Scale`、`Simple Health Format`、`Simple Style`、`Lightbox`、`Player`。

**主绘制 `onRender(EventRender2D)` `:634-`**

- `mc.gui.screen() != null && !ChatScreen` → return `:641-643`；`RenderState.isValid()` `:653`；`Canvas var6 = var1.getCanvasStack()` `:655`。
- 距离剔除 `distanceToSqr > MaxDistance²` `:~672`。
- **距离自动放大** `:~693-696`：`var23 = sqrt(distSq) < 32 ? 1.0 : min(2.2, 1 + (dist-32)/80)`，`var24 = Scale * var23`。
- **淡入 alpha 平滑** `:~690`：`var11 = 1 - 0.01^(deltaMs/160)`，`alpha += (1-alpha)*var11`，`alpha < 0.005` 不画（避免闪烁/瞬现）。

**完整款 `mr684(...)` `:870-1000+`**

- 皮肤头像 14×14：`clipRRect(..., 3*scale)` + `drawImageRect` 取 UV `8,8,8,8`；
- 队伍前缀 `mv130(team)`：带色 `drawShadowString`；
- 图标 `mk117(player)`（10px）；名字 `FontManager.getBoldFont(12)`；
- 血量 `h.displayHealthReal()` → `♥ + ceil(hp)`（`Show Health` 开关）；
- 字体：`getBoldFont(12)` / `getFont(10)`（`:618, :878-879, :1143-1144`）。

**背景三态 `:~905-925`**

- 阴影：`Render2D.drawDropShadowOnlySmooth(..., 7.0F, 637534208(0x26000000) * 0.45 * alpha)`；
- `"Frosted"`：`drawSmoothRect` 两次 —— `-436207616`（`0xE6000000`，黑 90%）×0.92 + `234881023`（`0x0DFFFFFF`，白 5%）×alpha；
- `"Solid"`：`-14935010`（`0xFF1C1C1E`）×0.96 + `234881023`；
- 默认：双层描边 —— 外 `234881023`，内 `-436207616`×0.55，边宽 `max(1, 1*scale)`。
- `mg273(int color, float f)` `:608-612` 按比例缩放 alpha 通道，供上述透明度叠加使用。

**凶手标记**：`mj92(...)` `:616-622`，`lfh.ty.d.INSTANCE.isMarkedMurderer(uuid)` 为真时在名字左侧画 `[凶手]`（`mg273(-50384, alpha)` = `0xFF3B30` 红）。

**手持物图标 `mx350(...)` `:853-875`**：`new ItemStackRenderState()` + `mc.getItemModelResolver().updateForLiving(v7, stack, ItemDisplayContext.GUI, entity)` + `pickParticleMaterial(RandomSource.create(42L)).sprite()`，按 `identityHashCode(item)*31 + damage` 缓存 sprite。

**vanilla 缩放回填**：`vanillaNameScale()` `:814-821` —— 非 vanilla 模式返回 `1.0`，否则 `clamp(ft77/100, 0.1, 5.0)`（`NameSize` 设置以百分数表示）。

**三个 Mixin 构成「三重接管」**

| Mixin | 目标方法 | 行为 |
|---|---|---|
| `kiss/gdp/g/ai.java:16-40` | `shouldShowName(LivingEntity;D)Z` HEAD，cancellable | vanilla 模式按 `isVanillaTagTarget` 决定 `true`；**非 vanilla 模式且 `Player` → `setReturnValue(false)`**（彻底关掉原版名牌） |
| `kiss/gdp/g/ak.java:20-49` | `getNameTag` RETURN，cancellable | 前缀 `[凶手] `（`TextColor.fromRgb(-50384)`，bold）→ 原名 → bold → 若 LivingEntity 追加 `" " + ceil(displayHealthReal)`（`ChatFormatting.RED, BOLD`） |
| `kiss/gdp/g/aj.java:21-52` | `submitNameDisplay(...)` HEAD/RETURN 压栈出栈 | 其间把 `nameScale()` 交给 `du.a.INSTANCE.vanillaNameScale()`，即 `NameSize/100` 直接缩放原版名牌 |

> 这是 Kiss 与 Vape 在 NameTags 上最本质的差别：Kiss 同时改写了「是否显示」「文本内容」「缩放」三处原版行为，Vape 只取消了渲染事件。

### 3.5 BedESP — `kiss/p/ibr/zq/a.java`（394 行，真实功能）

**参数**：`NumberSetting("Range", 64.0, 16.0, 256.0)` `:67`；`ModeSetting("Color", "Red", {"Red","Green","Yellow","Blue","Purple","White"})` `:76`；注册 `:94`。

**分帧预算扫描（性能设计，这是 Kiss 明文唯一的性能优化）**

- `mz47()` `:294-323`：以玩家为中心、`fx219 = floor(range)` 为半径、`fc313/fb282` 限高，生成螺旋偏移列表 `fj101`；
- `mw288` 分帧消费，`:150-167` 每帧预算 `var10 = clamp(1 + deltaMs/40, 1, 12)`、`var13 = 150000 / 体积`，取 `min(var10, var13)`。

**缓存与合并**

- `fa80: Map<BlockPos,AABB>` 存已发现床的包围盒；`fx340` 为本 tick 命中集；
- `mv15()` `:~380` 按 `Range²` 剔除远处条目；
- `mq706()` `:~370` 用 `BedBlock.getConnectedDirection(state)` 把床头/床尾合成一个 AABB（`mw167` 求并集），key 取 `min(pos1, pos2)` —— **一个床只画一个框**。

**绘制 `mr565(Canvas, AABB)` `:199-260`**

8 角点手工填 `fi717 / fc536 / fv212` 数组 → `RenderState.project(new Vec3(...))`，任一点 `var4[2] <= 0` 直接 return；按 `fq700` 的 12 条棱 `drawLine` 两遍：先 `855638016`（`0x33000000`，黑 20%）宽 **2.0px**，再 `mp253()` 宽 **1.0px**，`PaintStrokeCap.BUTT`。

**颜色表 `mp253()` `:327-343`**：Green `-432748711`、Yellow `-419441142`、Blue `-432898818`、Purple `-424717602`、White `-419430401`、default(Red) `-419480784`。

事件 `onRender(EventRender2D)` `:362-387`：`RenderState.isValid()` + `mu218()` / `mv15()` / `mu750(canvas)`。

### 3.6 空壳模块（重要发现）

任务说明把 `Item / WorldItem / GuardFilter` 列为待比对象，实测三者**均无任何绘制**：

| 文件 | 行数 | 实测行为 |
|---|---|---|
| `kiss/p/ibr/zq/l.java`（Item） | 284 | `onRender2D(EventRender2D)` `:263-272` 只调用 `mg835 / mh337 / mw988 / mx322 / mc188("cache") / ml411(new ArrayList())` 六个纯计算函数并**丢弃返回值**（`long var10000 = ...` 也未使用）→ 无绘制 |
| `kiss/p/ibr/zq/r.java`（WorldItem） | 392 | 同样模式，`onRender2D` `:381-391` 全是死计算（`mo347("frame")` 等）→ 无绘制 |
| `kiss/ss/x/ga/i.java`（GuardFilter） | 283 | `onPreMotion` `:197` / `onTick` `:275` / `onRender2D` `:286` 同一套死计算重复三遍 → 无功能 |

判定方法（可复现）：统计每个文件 `Canvas|Render2D\.|RenderState\.project|drawString|SkiaFont` 的出现次数 —— ESP `ss/x/ga/d.java` 6 次、NameTags `du/a.java` 22 次、BedESP `p/ibr/zq/a.java` 5 次、HUD `gt/z/b.java` 82 次、List `ocg/c.java` 12 次、WaterMark `qs/ysn/b.java` 52 次；而 `p/ibr/zq/{l,r}.java`、`ss/x/ga/i.java`、ClickGui `bhx/b.java`（832 行）等均为 **0 次**。
佐证：`grep -rl "EntityItem" kiss/` → **无任何文件命中**，即 Kiss 连掉落物实体都未引用。

`build/kiss-tools/strings_full.json` 中这些文件仅剩 `Range`、`Limit`、`Radius`、`Speed`、`Amount`、`Ticks`、`Chance`、`Mode` 等参数名 —— 属于「保留 UI 骨架但移除功能」的典型发布版裁剪痕迹。

### 3.7 数据表实况（与任务描述不一致处，如实记录）

- `build/kiss-tools/strings_full.json`：**键是相对路径**（如 `du/a.java`）→ 值是该文件的字符串 token 数组，共 637 键。**不存在**「相对路径|偏移」形式的键。
- `build/kiss-tools/module_strings.json`：全部为空的骨架对象（`{"m":[],"a":[],...}`），无有效内容。
- `build/kiss-tools/mixin_table.json`：48 项，字段 `file / target / anns / methods`；渲染相关项：`ai → LivingEntityRenderer`、`aj → EntityRenderer`、`ak → EntityRenderer`、`as → AvatarRenderer`、`d → LivingEntityRenderer`、`o → GameRenderer`、`p → Hud`、`q → GpuSurface`、`r → ItemInHandRenderer`、`t → LevelRenderer`。
- 注意 `kf.mixins.json` 列出 42 个 client mixin，比 mixin_table.json 的 48 项少（表内含非 client 或已合并项），两者不完全等价。

---

## 4 Vape 实现细节

### 4.1 基础设施

- **批处理渲染器** `utils/render/RenderBatchManager.java`：双队列 `worldBatches` / `guiBatches` `:63-65`；`queueWorldBatch` `:351`、`queueGuiBatch` `:389`；`flushGuiBatches(partialTicks, resetProjection)` `:168-230`（重入保护 `:169-172`、GL 绑定捕获 `:180`、逐批 scissor `:198-208`、standalone 回调隔离 `:209-219`、脏标记 `rasterStateDirty` 减少裸 GL 调用）；`flushWorldBatches` `:420`。
- **顶点格式** `utils/render/RenderBatchBuilder.java:362`：单顶点 31 个 float，含 `shaderMode / position / uv / color / inner / feather / center / middleAngle / sweepAngle / screenDimensions / origin / innerRect / radius(vec4) / spread / corners(vec4)` —— 圆角、羽化、圆弧、渐变都是 shader 参数（`addGradientPill` `:254`）。
- **字体** `ui/font/FontManager.java`：`FontFamily` 枚举 = `ARIAL / PROXIMA / NOTO / POPPINS / MINECRAFT`（`ui/font/FontFamily.java:2-8`）；`W(double,boolean)` `:79` 取当前选择的平滑字体；实现类有 `StbSmoothFontRenderer` / `LegacySmoothFontRenderer` / `BufferedSmoothFontRenderer`。
- **离屏渲染上下文** `render/OffscreenRenderContext.java`：自持 framebuffer（`modernFramebuffer` / `legacyFramebuffer`）与 `renderingOffscreen` 标志，用于后视镜（Rearview）离屏渲染；各 ESP 入口用它跳过离屏绘制（`ESP2D.java:48`、`ESPOutline.java:44`、`Chams` 未用但 `ESP` 的 `resolveEntityColor` `:81-84` 用了）。

### 4.2 ESP — `module/render/ESP.java`（120 行）+ 4 个子模式

**模式矩阵按版本拼装** `ESP.java:71`：1.17+ 仅 `{3D, 2D}`；1.12.2 追加 `Skeleton`；其它版本再加 `Outline`。子模块实例化 `:52-56`。

**参数（13 项）** `:57-69` + 依赖关系 `:72-76`：

| 参数 | 默认 | 作用域 |
|---|---|---|
| `Player Color` | `new Color(-14368924)` | 全局 |
| `Invisibles` / `Enemy Only` / `Priority Only` / `Enemies List Only` / `Hide Bots` | 全 false | 全局 |
| `Bounding Box` | true | 2D |
| `Health Bar` / `Name` / `Use Displayname` / `Show Background` | false | 2D（`showName` 派生依赖 `useDisplayName`、`showNameBackground`） |
| `Hitbox` / `Show Normal` | — | 3D only |

**颜色解析 `resolveEntityColor(viewer, entityHandle)` `:81-114`**（这是 Vape ESP 的颜色中枢）：离屏 → null；非 `MappedClasses.zm` → null；`entityHandle.equals(viewer)` → **null（不画自己）**；`isSyntheticEntity()` → null；`hideBots && isBot()` → null；`!showInvisibles && isInvisibleWithoutEquipment()` → null；玩家走 `ClientSettings.resolveEntityColor(context)`（主题/好友/敌人色），为空回退 `playerColor`，返回带 alpha 的 `MutableColor`。

**2D 子模式 `esp/ESP2D.java`（338 行）**

- 双路径：`GuiRenderPrimitives.d()` 为真（≥1.16.5 批次后端）时**在世界阶段画框**（`onRender2D` 直接 `return` `:57`），为假时退回立即模式 `:112-135`。原因写在注释 `:51-56`：延迟的 guiBatches 在帧末冲刷，会盖在 HUD 上。
- 投影**每帧一次**：`onRender3D` `:255-332` 里 `LocalPlayerRotationUtil.updateProjectionMatrix(event.getTicks())` `:267` + `RenderUtil.d()` 压相机基线 `:278`；注释 `:240-244, :257-262` 明确记录了两个历史 bug：「`onPreRenderLiving` 中 modelViewMatrix 为 null → 退化成 identity → 框只在一个方向对齐」与「逐实体投影是掉帧主因」。
- 世界空间构建：`renderX/Y/Z` 为插值坐标减相机 `:290-292`；`worldBounds = entity.R$src$Lgg_vape_wrapper_impl_AxisAlignedBB_$r19dfl().expand(expansion, expansion, expansion)` `:294`（`expansion = entity.b()`，即碰撞箱膨胀值）；`ProjectedEntityBounds(...)` 且 `onScreen` 才入列 `:297-300`。
- 批次后端下**自建 GUI 正交投影**再画：`new RenderMatrix4f().setIdentity().setOrthographic(0, width/2, height/2, 0, 21000, -21000)` `:322`，`drawPendingBounds` + `flushGuiBatches(0.0f, false)` `:325-326`，然后还原矩阵（注释 `:307-314` 解释为何不能用 `resetProjection=true`）。
- 方框三段式 `:89-136`：外层 1px `(0,0,0,0.4*alpha)` 四边 → 内缩 1px 再画同色 → 内缩 0.5 再画实体色 1px（共 3 层 × 4 边，**无圆角**）。
- 血条 `:140-190`：底槽 `(0,0,0,0.4)` 宽 2px；填充按 5 段阈值 `≥0.9 绿 / ≥0.75 (0.9,1,0) / ≥0.5 黄 / ≥0.25 橙(1,0.5,0) / ≥0 红`，宽 1px，自下而上。
- 名字 `:191-219`：`useDisplayName` 时 `context.getTypeName()` 并 `replaceAll("[^\u00a7^\x00-\x7F]", "")`（`NON_ASCII_PATTERN` `:44`，**非 ASCII 被剔除**）；好友覆盖显示名 `:197-199`；背景框 `priorityTarget ? 实体色 : (0,0,0,95)`，被注视（`entityLivingBase.P()`）时边框 1.5px 红 `(255,0,0,200)`，否则 0.5px `(0,0,0,102)`；文字用 `SmoothFontRenderer`（`W(0.9, true)` `:68`）。
- 取消原版名牌：`onPreRenderLiving` `:235-252`，仅当 `showName` 开启且 `resolveEntityColor != null` 时 `setCancelled(true)`。

**3D 子模式 `esp/ESP3D.java`（82 行）**：`B(1.0)` `:26`、`RenderUtil.d()` `:27`、blend `SRC_ALPHA/ONE_MINUS_SRC_ALPHA` `:32`、`setLineWidth(1.5f)` `:33`、关纹理 `:34`、开 `GL_LINE_SMOOTH` `:35`、**关深度测试 `:36` + `setDepthMask(false)` `:37`**；`RenderUtil.u(...)` 画 3D 盒 `:59`；`Hitbox` 叠加时用反色 `(255-r,255-g,255-b,70)` 画真实碰撞箱 `:60-65`。

**Outline 子模式 `esp/ESPOutline.java`（260 行）**：模板缓冲五轮法 —— `StencilUtil.ensureFramebufferStencilBuffer()` `:152`、`glGenLists(1)` `:153`、`pushLayer()` `:154`、`configureLayerMask(true)` `:164`；把每个 `MappedClasses.Yl` 实体（临时 `entity.q(false)` 解除隐身 `:193-194`）`doRender` 录进 display list `:181-230`；随后 `glPolygonMode(GL_FRONT_AND_BACK, LINE/FILL)` 依次 LINE→FILL→LINE→FILL 调 5 次 display list `:231-243`，中间切 `configureLayerMask(false)` 与 `configureEqualLayerTest()` `:235-238`，`popLayer()` `:244`。`glLineWidth(3.0f)` `:196`。开启时 `onPreRenderLiving` / `onPreRenderPlayerSpec` / `onSetArmorModel` 全部 cancel（`:61-63, :137-139, :93-96`），避免原模型与描边叠影。fast render 开启时强制回退 3D 并提示（`onTick` `:67-72`）。

**Skeleton 子模式 `esp/ESPSkeleton.java`（191 行）**：`onRenderPlayerPost` `:30-184`；`getEntityRenderer().B(0.0)` + 关深度 `:34-35`；**线宽随距离反比** `:68-70`：`lineWidth = max(4.0 * ((100 - min(distance,100)) / 100), 0.1)`；从 `ModelBiped` 取四肢 `getRotateAngleX/Y/Z()*57.29578f` 逐段 `glRotatef` 后画 `GL_LINES`（腿 `:78-111`、躯干 0.65 + 肩线 ±0.35 `:115-123`、手 0.6 `:132-150`、头 0.4 + 朝向线 0.25 `:158-168`）；潜行时 `legOffset 0.6`、偏移 `(0,-0.2,0)` `:73-77`；攻击目标（`renderEntityContext.isAttackable()`）强制蓝色 `:44-46`。

### 4.3 Chams — `module/render/Chams.java`（218 行）

- 常量 `PASS_NONE/-1`、`PASS_OCCLUDED/1`、`PASS_VISIBLE/2`、`PASS_LEGACY/3` `:36-39`。
- 参数 `:201-205`：`Colored`(false)、`Visible Color`(红)、`Color Behind Walls`(true)、`Invisible Color`(黄)、`Hide Bots`(false)；依赖 `colored → visibleColor、colorBehindWalls → occludedColor`。
- **未开 Colored（默认）**：只用多边形偏移 `enableCapability(32823)` + `glPolygonOffset(1.0f, -2500000.0f)` `:124-126`，靠深度偏移把原模型「顶」到墙前；`onRenderPlayerPost` 用 `glPolygonOffset(1.0f, 2500000.0f)` 反向复位 `:91-92`。
- **开 Colored**：`onRenderPlayerPre` `:127-196` 三重 doRender —— ① 关深度/纹理/光照 + `occludedColor`（或 `Color Behind Walls=false` 时的 `visibleColor`）→ `PASS_OCCLUDED` `:138-150`；② 开深度/纹理 `:156-157`；③ `visibleColor` + `PASS_VISIBLE` 再画一次 `:167-172`；1.7.10 额外 `PASS_LEGACY` + `glDepthMask(false)` `:158-166`；结束后若玩家原本非隐身则 `player.q(true)` 临时隐身 `:189-192`（防止原版在可见通道再叠一层）。
- 期间 `onPreRenderLiving` / `onPreRenderPlayerSpec` / `onSetArmorModel` cancel（`:50-63, :97-108, :208-217`）；`onPreRenderEntity` `:66-75` 按 pass 换色。
- 互斥：`renderingChamsPass || renderPass == PASS_LEGACY || esp.isEnabled() && esp.isOutlineModeActive()` → 直接 return `:119-121`（避免与 Outline 抢渲染）。

### 4.4 NameTags — `module/render/NameTags.java`（803 行）

- **世界空间 3D 文字**（不是 2D 叠层）：`onRender3D` `:523-` 按实体类别分派 `renderNameTag(..., playersHealth/playersDistance/equipment/strengthIndicator/playersEffects, matrixStack)` `:572-576`。
- 参数 22 项 `:421-443`：玩家/动物/怪物三组各带 `Health` / `Distance` / `Effects` / `Max Distance`(0–250，0=不限 `:429`)；全局 `Ignore Invisibles`、`Auto Scale`(true)、`Hide bots`(true)、`Equipment`、`Strength Indicator`(a+/e=/c- `:430`)、`Calculate Effects`、`Scale`(0.1–1.5)、`Opacity`(0–1)。
- `renderNameTag(...)` `:242-411`：背景色 `isSneaking() ? enemyBackgroundColor : backgroundColor` `:300`（`(20,20,20,128)` `:91` / `(115,0,4,128)` `:103`）；`opacity` 乘进 alpha `:253, :303-306`；**Auto Scale** `:309-313`：`f4 = 0.0333*scale`，开启时改为 `0.016667 * (dist/5 或 2.0) * scale`（与 Kiss 的 `1+(dist-32)/80` 是不同曲线）；文字居中 `n2 = getStringWidth/2` `:314`；绘制 `fontRenderer.drawString(..., -n2, n3+2, n)` `:383`；队伍前缀走 `fontRenderer.Z(scorePlayerTeamTextComponent, ...)` `:367, 379`；药水效果罗马数字 `:398`；装备 `renderEquipment(...)` `:411`。
- 装备图标 `renderEquipment(...)` `:131-215`：数量 `drawStringWithShadow(String.valueOf(itemStack.t()), n+8, n2+8, -1)` `:190`；耐久条 `BufferedRenderPrimitives.fillRect(n+2, n2+13, 13.0f, 2.0f, Color.BLACK)` `:652`。
- 取消原版：`@EventHandler(skipCanceled=true) S(EventPreRenderLiving)` `:223-234`，`shouldRender(...)` 为真即 `setCancelled(true)`。
- 字体：MC `FontRenderer`（位图字体），与 ESP2D 使用 `SmoothFontRenderer` 不同 —— 同一客户端内两套字体并存。

### 4.5 ItemESP — `module/render/ItemESP.java`（343 行）

- 3D billboard 文案：`onRender3D` `:61`、`RenderUtil.d()` `:168`、`RenderUtil.Y()` `:261`。
- 文本内容 `:143-159`：`stack.x()`（显示名）+ 可选距离前缀 `§a[§f<dist>§a]§r ` `:152` + 数量 `§r x<t()>` `:157`；**只画文字，不画物品图标**（无 `IconRenderer` 调用）。
- 背景 `RenderUtils.M(-maxHalfWidth-2, top, maxHalfWidth+2, 2.0, 0.0, backgroundColor, backgroundColor)`，`backgroundColor = new Color(20,20,20,64)` `:127`。
- 缩放 `labelScale = 0.0333 * scale`，`Auto Scale` 时 `0.016667 * distanceScale * scale` `:161-166`。
- 分组：`Group Items` 开时按距离阈值 `mergeRadius = max(1.5, averageDistance/5)` 合并邻近 `EntityItem` `:100-118`。
- 过滤：`Whitelist Only` + `Allowed Items`(LimitValue) `:122-125, :287, :315`。
- 字体：`Minecraft.getFontRenderer()`（MC 位图字体）；26.x 分支改用 `StbSmoothFontRenderer.c(...)` 并走 `queueWorldBatch` 共用投影 `:230-241`。

### 4.6 Tracers — `module/render/Tracers.java`（310 行）

- 参数 15 项 `:121-135`：三类实体开关 + 三个 `Distance Check` + `Invisibles` + `Color by distance` + `Highlight if focusing` + 三个 `RandomValue` 距离(0–256，默认 32) + 三色。
- 起点 = **视线方向单位向量** `:162-196`：`Vec3d(0,0,1)` 依次 `rotateAroundXAxis(-toRadians(viewY))`、`rotateAroundYAxis(-toRadians(viewX))`；`startY += viewDirection.getY() - player.X()`（1.16.5+）；批次后端下 `startX/Y/Z -= cameraOffset`（= renderPos − 相机位置），使射线汇聚屏幕中心 `:181-189`。
- 双描边 `:202-204`：先 `drawLine3D(..., lineWidth + lineWidth*0.5, Color.black)`，再 `drawLine3D(..., lineWidth, color)`；旧路径等价 `glLineWidth(lineWidth*1.5)` 黑线 + `glLineWidth(lineWidth)` 彩线 `:207-219`。
- **线宽来自 scale**：`applyFocusHighlight` `:235-250` —— 命中 `focusedEntities` 时 `setScale(3.0)`，其余 `setScale(0.75)`，传给 `drawTracer(..., (float)entry.getScale(), ...)` `:275`。
- 距离配色 `applyDistanceColors` `:287-`：`hue = 0.35 * (distanceOffset/medianDistance)`，超中位数固定 `0.35`（黄绿端）；`alpha = clamp(255/(dist/median), 150, 255)`。
- 入口 `onRender(EventRenderTracers3D)` `:255-283`：`RenderUtil.d()`、关深度 `:262`、`B(0.0)`、`startY = 1.7.10 ? player.X() : 0.0` `:263`、三点过滤快照 `updateMedianDistance/applyDistanceColors/applyFocusHighlight`、按距离反序绘制 `:277-283`。

### 4.7 StorageESP — `module/render/StorageESP.java`（213 行）

- 8 类容器 + 每类独立颜色 `:183-198`；`Outline open`(true) `:44`。
- 遍历**方块实体列表** `Minecraft.theWorld().R$src$Ljava_util_List_$1ycbpra()` `:62`，按 `MappedClasses` 逐个 `isInstance` 分类 `:66-88`：箱子/陷阱箱共用 `DZ`，用 `getNumPlayersUsing()` 区分 —— `n == 0` → 箱子色，`n == 1` → 陷阱箱色 `:69-76`；末影箱 `u0`、漏斗 `Dx`、熔炉 `li`、投掷器 `YI`、发射器 `lI`、潜影盒 `Dh`（仅 1.12.2+）。
- 绘制 `RenderUtil.L(d, d2, d3, renderData)` `:105`，`renderData` 为 `HSBData`（普通）或 `HSBAData`（带 `TileEntityOpenedChest.Y()` / `TileEntityChest.getLidOpenness()` `:98, :101` —— **开箱程度直接进渲染参数**，配合 `Outline open` 用对比色描出开启状态）。
- GL 状态：`setLineWidth(1.5f)` `:54`、关纹理 `:55`、开 `LINE_SMOOTH` `:56`、**关深度 `:57`、`setDepthMask(false)` `:58`**；有 `isCapabilityEnabled(3042)` 双分支（`:51` 已开启走分支一 / `:116` 主动开启走分支二）各自独立的完整状态保存-恢复。

### 4.8 BedPlates — `module/render/BedPlates.java`（444 行，`Category.MINIGAMES` `:420`）

> 注意：这不是 BedESP。它统计**床周围防御方块清单**并按层渲染（床战用途），Kiss 的 BedESP 是画床框。两者不同功能，不能直接对等比较。

- 扫描 `onScheduledAction` `:183-231`：以玩家为中心逐 chunk 扫描，垂直 ±20 格找床 `:195-208`；`BlockBed.isFoot(...)` 为真则跳过（只留一个位置）`:205-207`；距离 > 100 剔除 `:230-231`。
- 清理 `:78-83`：床方块消失且距离 > 100 时移除缓存条目。
- 渲染 `onRender3D` `:66-180`：批次后端直接 `renderPlate` `:97-99`（26.2 前后按需关深度 `:92-112`）；非批次后端先 `ItemIconRenderer.precache(itemId, metadata)` 预热图标 `:114-117`，再统一 GL 状态（关光照、`depthMask(false)`、`disableDepth`、`tryBlendFuncSeparate(770,771,1,0)`、关剔除）`:121-140`，`GuiRenderPrimitives.G.bind()` 后逐个 `renderPlate` `:143-146`，完毕逐项恢复 `:147-165`。
- 数据模型：`bedplates/BedPlateBlockStateKey.java`（52 行，方块 id + metadata 的排序键）、`bedplates/BedPlateCountState.java`（54 行，`getSortedLayer(layer)` 提供 3 层清单）。

### 4.9 Indicators / Arrows（屏外指示器）

- `Indicators.java`（535 行）：`onRender2D` `:131-256` 对投射物计算屏外角度，`radiusX = centerX * radiusScale` `:172-176`，椭圆边界求交 `sqrt(1/(1/rx² + tan²(angle)/ry²))` `:174-176`；三档 `Alert Type`（threat / always / hitOnly）`:522`；6 类投射物开关 `:526-531`；图标 `ImageRenderer.drawResWithShadow(color, -16, 0, "exo", 1.0, false)` `:252`；`hitOnly` 时 `directHit` 分支带命中预测 `:141, :185`。
- `Arrows.java`（284 行）：`onRender2D` `:50-133` 同一套椭圆求交 `:67-70`；`projectedPosition[2] > 1.0` 时角度 +180（背面）`:66`；`Scale Opacity` 让远处箭头更透明 `:127-128`；`onRender3D` 里预投影存 `projectedPositions` 供 2D 阶段使用。

---

## 5 渲染管线对照

| 阶段 | Kiss | Vape |
|---|---|---|
| 2D 叠层入口 | `GpuSurface.blitFromTexture` HEAD → `skiaManager.render()`（`gdp/g/q.java:17-21`） | `EventRender2D` 事件（各模块 `@EventHandler onRender2D`），批次在帧末 `flushGuiBatches` 冲刷（`RenderBatchManager.java:168`） |
| 3D 世界阶段 | `LevelRenderer.render` HEAD（`gdp/g/t.java:23-31`），但 ESP/BedESP **不用它**，仍走 2D 投影 | `EventRender3D` / `EventRenderTracers3D` / `EventRenderPlayerPost`，真 GL 几何体 |
| 批次缓冲 | ✗ 逐 draw 直接下发 Skia 命令 | ✅ `queueGuiBatch` / `queueWorldBatch` + VBO 合批（31 float/顶点，`RenderBatchBuilder.java:362`） |
| 投影计算频率 | 每帧一次（`SkiaManager.java:35` → `RenderState.update`） | 每帧一次（`ESP2D.java:267`；注释 `:257-262` 说明逐实体投影曾导致掉帧） |
| 坐标空间 | GuiScaled（`RenderState.java:133-153`） | 像素/2（`coordinateScale = 2.0f`，`ESP2D.java:71`）+ `getRenderResolutionMultiplier` |
| 深度测试 | 2D 阶段不涉及；Chams 用 `CompareOp.ALWAYS_PASS` + 不写深度（`g/pbd/j.java:36`） | ESP3D / Tracers / StorageESP / Skeleton 显式 `disableCapability(2929)`；Chams 分通道开/关；Outline 用模板缓冲 |
| 描边实现 | Skia 双遍 stroke（1.6px + 0.7px） | ① 2D：3 层 fillRect；② 3D：双遍 GL 线（黑 1.5×线宽 + 彩色）；③ Outline：模板 + `glPolygonMode` 五轮 |
| 圆角 | ✅ `RRect.makeXYWH(...,3.0F)`（`ss/x/ga/d.java:256-280`） | ✗ ESP 方框无圆角；渲染器层支持（`radius`/`corners` 顶点属性） |
| 渐变 | ✗ | 渲染器支持（`addGradientPill` `:254`），ESP 未调用 |
| 文字渲染 | Skia + MiSans TTF（CJK 完整） | ESP2D/NameTags 用 MC 位图字体；ItemESP 26.x 用 StbSmoothFontRenderer；FontManager 另有 4 套 TTF |
| 状态保存/恢复 | Skia 自管（Canvas 级 scissor/clip） | 显式逐项存取（如 `ESPOutline.java:157-162, 245-255`；`StorageESP.java:51-60`） |

---

## 6 参数对照

### 6.1 ESP

| 参数 | Kiss | Vape |
|---|---|---|
| 模式 | `Draw Mode`: 2D(default) / 3D | 3D / 2D / Outline / Skeleton（按版本裁剪，`ESP.java:71`） |
| 距离 | `Max Distance` 16–512，默认 128 | 无（全实体，靠 `Hide Bots`/`Enemy Only` 等过滤） |
| 实体开关 | `Players` / `NPCs` / `Mobs` 各一个布尔 | `Player`(隐式) / `Invisibles` / `Hide Bots` / `Enemy Only` / `Priority Only` / `Enemies List Only` |
| 颜色 | 无（写死） | `Player Color` + 主题色 + 好友/敌人色（`ESP.java:81-114`） |
| 2D 附加 | 无 | `Bounding Box` / `Health Bar` / `Name` / `Use Displayname` / `Show Background` |
| 3D 附加 | 无 | `Hitbox` / `Show Normal`（反色叠加真实碰撞箱） |
| 参数总数 | **5** | **13** |

### 6.2 Chams

| 参数 | Kiss | Vape |
|---|---|---|
| 目标开关 | `Players`(true) / `Mobs`(false) / `Animals`(false) / `Self`(false) | 仅玩家 + `Hide Bots`(false) |
| 颜色 | 无 | `Colored`(false) / `Visible Color` / `Color Behind Walls`(true) / `Invisible Color` |
| 实现 | RenderType → 恒过深度的副本 shader | 默认 polygonOffset(-2500000)；Colored 时双通道 doRender |
| 方块/物品 | ✗ | ✗ |

### 6.3 NameTags

| 参数 | Kiss | Vape |
|---|---|---|
| 目标 | `Player` / `Self` 等（strings 恢复） | `Render Players`(true) / `Render Animals`(false) / `Render Mobs`(false) |
| 距离 | `Max Distance` 16–512，默认 128 | 三组独立 `Max Distance` 0–250，**0 = 不限** |
| 缩放 | `Scale` 0.5–2.0 + 距离放大 `1+(dist-32)/80`（上限 2.2） | `Scale` 0.1–1.5 + `Auto Scale`（`dist/5`，下限 2.0） |
| 透明度 | 无（alpha 仅做淡入） | `Opacity` 0–1 |
| 背景 | `Frosted` / `Solid` / 描边 三态 + 投影 | 单色 `(20,20,20,128)`，潜行时 `(115,0,4,128)` |
| 内容 | 头像、队伍前缀、血量(`♥N`)、手持物图标、`[凶手]` | 血量、距离、装备、药水效果、强度指示(a+/e=/c-) |
| 替换原版 | 3 个 Mixin（显示/文本/缩放） | 1 个事件 cancel |
| 字体 | MiSans TTF（Skia） | MC 位图 `FontRenderer` |

### 6.4 其他

| 功能 | Kiss | Vape |
|---|---|---|
| ItemESP | 空壳（无 EntityItem 引用） | 名/数量/距离 + 分组 + 白名单 + Auto Scale |
| Tracers | 未发现 | 视线起点、黑底描边、三色、距离渐变、聚焦 3× |
| StorageESP | 未发现 | 8 类 × 独立色 + 开箱程度 + `Outline open` |
| BedESP | ✅ 6 色 + `Range` 16–256 + 分帧扫描 + 床头尾合并 | 无同类模块 |
| BedPlates | 未发现 | `Show distance` + 3 层方块清单（MINIGAMES 分类） |
| Indicators | 未发现 | 6 类投射物 + 3 档告警 + `Radius Scale` |
| Arrows | 未发现 | 屏外实体箭头 + `Scale Opacity` + `Radius Scale` |

---

## 7 性能、版本兼容与反检测

### 7.1 性能设计

**Kiss**

- **BedESP 分帧预算**：`p/ibr/zq/a.java:150-167`，每帧最多消费 `clamp(1 + deltaMs/40, 1, 12)` 个候选偏移，且受 `150000/体积` 上限约束 —— 唯一一处明文的扫描节流。
- **投影每帧一次**：`SkiaManager.java:35` 统一 `RenderState.update`，所有 2D 模块共享 `viewProjMat`，无重复矩阵运算。
- **纹理/精灵缓存**：NameTags `mx350` 按 `identityHashCode(item)*31+damage` 缓存 `ItemStackRenderState` 的 sprite（`du/a.java:853-875`）；FontManager 按 `path:size:bold` 缓存 `SkiaFont`。
- **早退**：`Render2D.drawRect` 在 `alpha==0` 时不发命令；ESP 2D 框宽高 `< 2px` 提前丢弃（`ss/x/ga/d.java:257-258`）。
- **无批次**：每个矩形/线段都是一次 Skia 调用，实体多时 CPU→GPU 提交次数线性上升（与 Vape 的合批形成对比）。

**Vape**

- **合批**：`RenderBatchManager` 把同状态图元并成一次 draw（`stageBatch`/`batchBuffer.draw()` `:228-230`）；脏标记避免逐批重复 5 次裸 GL 调用（`:186-190` 注释）。
- **投影单次**：`ESP2D.java:257-262` 注释明确记录「逐实体投影是掉帧主因」，现改为每帧一次 `updateProjectionMatrix` + `RenderUtil.d()`。
- **重入保护**：`flushGuiBatches` 的 `withinGuiFlush` 闸门 `:169-172`，防止 Arrows/ESP2D 在别的绘制流程里触发递归冲刷。
- **模板缓冲复用**：`StencilUtil.ensureFramebufferStencilBuffer()` `ESPOutline.java:152`。
- **每帧 display list**：`ESPOutline.java:153, 181, 232-243` 每帧 `glGenLists`/`glEndList`/`glDeleteLists` 并调 5 次 display list —— Outline 模式开销明显高于 2D（与官方 `fast render` 冲突时的强制回退 `:67-72` 也印证了这点）。
- **无预算扫描**：`StorageESP` 每帧遍历整张 tile-entity 列表（`:62`），无分帧、无空间剪枝；`BedPlates` 用 `onScheduledAction` 低频扫描（`:183`）来规避。

### 7.2 版本兼容

| 项 | Kiss | Vape |
|---|---|---|
| 支持跨度 | 单目标版本（推断 1.21.9/1.21.10，见 §1.2） | 1.7.10 → 26.x 多分支 |
| 分支证据 | `ForgeVersion` 无对应物；Mixin 直接写 26.x 的 RenderState/Submit API | `ForgeVersion.MC_1_7_10`（`Chams.java:128,143,158,180,193`）、`MC_1_16_5`、`MC_1_17`（`ESPOutline.java:172`）、`MC_1_20_6`（`ItemESP.java:230`）、`MC_26_2`（`BedPlates.java:92`） |
| 版本相关降级 | 无 | Outline 在 fast render 下强制回退 3D（`ESPOutline.java:67-72`）；1.7.10 前 10 次渲染跳过（`:147-149`） |

### 7.3 反检测 / 录屏截图可见性

- **Kiss：未发现**。对 `kiss/` 全库 `grep -ri "screenshot\|offscreen"` **0 命中**；唯一的「隐藏」行为是 NameTags 通过 `shouldShowName` 返回 false 关闭原版名牌（`gdp/g/ai.java:16-40`），那是视觉替换，不是反截图。
- **Vape：未发现**。`grep -rln "Screenshot" src/main/java` **0 命中**。`OffscreenRenderContext` 是后视镜（Rearview）离屏 framebuffer，各 ESP 用它**跳过**离屏绘制（`ESP2D.java:48`、`ESPOutline.java:44`），目的是避免 ESP 出现在后视镜画面里污染自身渲染，而不是针对外部截图工具。
- 双方都存在的「视觉层规避」仅有：不画自己（Vape `ESP.java:92-94` 显式 `entityHandle.equals(viewer) → null`；Kiss ESP 靠 `distanceToSqr` + 分类，理论上会包含自己，但 `mf879` 对本地玩家无特判 —— 需注意 Kiss ESP 可能对本地玩家也画框）。
- 值得一提：Kiss `ss/x/ga/d.java:282-289` 的 `Preloader.send("ESP " + msg)` 是向外部发送字符串的残留路径，与反检测无关，但属于发布版不应存在的调试代码。

---

## 8 结论与评分

### 8.1 评分（1–5，5 = 该维度实现最完整）

| 维度 | Kiss | Vape | 评分依据 |
|---|---|---|---|
| 1 渲染通道 / 架构 | **4** | **5** | Kiss：Skia 单一 2D 管线 + 统一投影，干净但无批次、无世界阶段几何；Vape：批次 + 立即模式双路径、模板、多事件阶段，架构更完整但复杂度高 |
| 2 实体方框 | **3** | **4** | Kiss：圆角 3px + 12% 填充 + 双层描边，观感精致；Vape：3 层描边、血条/名字/Hitbox 叠加，但无圆角、无填充 |
| 3 颜色 / 不透明度 / 自定义色 | **2** | **5** | Kiss：ESP 颜色全写死，仅 BedESP 有 6 色枚举；Vape：主题色 + 好友/敌人色 + 每功能独立 ColorValue |
| 4 信息文字 | **4** | **4** | Kiss：MiSans 平滑字体 + 头像 + 队伍 + 血量 + 手持物，CJK 无缺字；Vape：信息项更多（距离/效果/强度），但 NameTags 用 MC 位图字体、CJK 依赖 `noto.ttf`（1.09 MB，覆盖未确认） |
| 5 Tracers | **1** | **5** | Kiss：未发现（全库无 tracer 命中）；Vape：起点/描边/距离色/聚焦高亮齐全 |
| 6 Chams | **3** | **4** | Kiss：RenderType 换 shader + 恒过深度，支持 4 类实体，但无颜色、无分通道；Vape：双通道 + 双色 + polygonOffset 回退，仅玩家 |
| 7 ItemESP / 掉落物 | **1** | **4** | Kiss：空壳（`p/ibr/zq/l.java` 无绘制，全库无 `EntityItem`）；Vape：名/数量/距离 + 分组 + 白名单，但无图标 |
| 8 NameTags | **5** | **4** | Kiss：3 个 Mixin 改写显示/文本/缩放 + 头像 + 背景三态 + 距离自动放大 + 淡入；Vape：内容更全（装备/效果/强度）但字形与缩放策略更朴素 |
| 9 StorageESP / BedESP | **3** | **4** | Kiss：仅 BedESP（含分帧预算与床头尾合并），无 StorageESP；Vape：StorageESP 8 类 + 开箱检测 + BedPlates，但 StorageESP 每帧全量遍历 |
| 10 性能 / 兼容 / 反检测 | **3** | **4** | Kiss：单版本、无批次但有分帧预算；Vape：合批 + 投影单次 + 多版本分支，但 Outline 每帧 display list、StorageESP 无预算。反检测双方均未发现 |
| **平均** | **3.1** | **4.3** | — |

### 8.2 结论

1. **功能广度上 Vape 明显占优**：Vape 有 9 个主模块 + 4 个 ESP 子模式 + 5 个子包；Kiss 的 26 个 `RENDER` 模块中只有 6 个真正绘制，`Item / WorldItem / GuardFilter` 等是发布版裁剪后的空壳（`p/ibr/zq/l.java:263-272`、`r.java:381-391`、`ss/x/ga/i.java:197/275/286`），且全库无 `EntityItem` 引用。
2. **Kiss 在单点观感与「原版行为接管」上更强**：ESP 2D 框有 3px 圆角 + 12% 白填充（`ss/x/ga/d.java:256-280`），NameTags 用 MiSans 平滑字体渲染中文、头像、队伍前缀，并通过 `shouldShowName`/`getNameTag`/`submitNameDisplay` 三个 Mixin 同时改掉原版的「显示与否 / 文本内容 / 缩放」（`gdp/g/ai.java:16-40`、`ak.java:20-49`、`aj.java:21-52`）——这种深度 Vape 没有对应实现（Vape 只 cancel 渲染事件）。
3. **Vape 在颜色与可配置性上是量级差距**：Kiss ESP 只有 5 个参数且颜色写死；Vape ESP 13 个参数 + 主题色解析（`ESP.java:81-114`），Chams 4 个颜色开关，StorageESP 8 套配色，Tracers 三色 + 距离渐变。
4. **两者的「3D」含义不同**：Kiss 的 3D 模式是把 12 条棱投影成 2D 线段（`ss/x/ga/d.java:346-368`），永远无遮挡关系；Vape 的 3D 是真 GL 线框并显式关深度（`ESP3D.java:36`），另有模板描边（`ESPOutline.java:143-259`）与骨骼（`ESPSkeleton.java:30-184`）两种世界阶段模式。
5. **反检测维度双方均无建树**：`screenshot` 类关键字在两侧均 0 命中，无法给出「有/无录屏屏蔽」的能力对比，只能标注「未发现」。

---

## 9 未发现 / 无法确认清单

| 项 | 结论 | 说明 |
|---|---|---|
| Kiss StorageESP | **未发现** | 147 个模块无 storage/chest/barrel/shulker 命名；全库 grep 亦无 |
| Kiss Tracers | **未发现** | 同上（`Bow & Arrows`、`Arrow` 属物品栏设置项） |
| Kiss Indicators / Arrows / ESPOutline / ESPSkeleton | **未发现** | 模块表无对应项；ESP 仅 2D/3D 两模式（`ss/x/ga/d.java:60-77`） |
| Kiss 精确 MC 版本 | **26.2（已确认）** | jar 根 `fabric.mod.json` 声明 `"minecraft": "~26.2"`、`"java": ">=25"`；`submitNameDisplay`/`GpuSurface`/`DeltaTracker` 的 API 组合与 26.x 渲染重构一致（§1.2） |
| Kiss Chams 颜色参数 | **未发现** | `i/ur/vz/b.java` 无颜色字段，`strings_full.json` 该文件无条目 |
| Kiss ItemESP 是否曾存在 | **无法确认** | 现存 `l.java` 仅有参数名残留（`Range/Limit/Radius`），无绘制 |
| Vape 掉落物图标渲染 | **未发现** | `ItemESP.java` 只拼字符串并 `drawString`，无 `IconRenderer` 调用 |
| Vape `noto.ttf` 是否含 CJK | **无法确认** | 文件 1,139,428 B（Latin 子集量级），源码无字形覆盖声明 |
| Vape 反截图 | **未发现** | `Screenshot` 关键字 0 命中；`OffscreenRenderContext` 属后视镜用途 |
| `strings_full.json` 的「路径\|偏移」键 | **不存在** | 实际为「相对路径 → token 数组」，637 键（§3.7） |
| `module_strings.json` 内容 | **为空** | 全部空数组骨架 |

---

*报告完成时间：2026-10-30。全部结论均可由上述 `文件:行号` 复现；Kiss 侧行号以反编译源码为准，混淆名（`mg137`/`fs152` 等）保留原样以便比对。*
