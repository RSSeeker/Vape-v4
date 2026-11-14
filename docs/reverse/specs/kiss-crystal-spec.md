# KissClient 1.7.3 · CrystalAura 逆向规格

> 目标：把 KissClient 的 CrystalAura 行为完整、可执行地记录下来，供移植进 Vape V4.21。
> 本文只描述 **Kiss 原实现**，第 6 节才给出到 Vape 现有能力的映射。**不改任何 Vape 源码。**
> 所有行号均指 `bc/ysg/a.java`（除显式标注的其它文件）。

## 0. 元信息与证据链

| 项 | 值 |
| --- | --- |
| 源码根 | `C:\Users\Jiang\AppData\Local\Temp\kiss-src`（Vineflower 反编译） |
| 主类 | `bc/ysg/a.java`，5853 行 / 303408 字节，`public class a extends AbstractModule implements IMinecraft` |
| 注解 | `@ModuleInfo` 在 `a.java:63-73`；`category = Category.COMBAT`（**不是**任务书里猜的 RISKY），`key = 0`；显示名是两条混淆 `@Text`，未解 |
| 同包邻居 | `b.java`(3877 行, 伤害/工具)、`c/d/e/f…m.java` |
| 工具 | `D:\vscode\VapeV4.21\build\kiss-tools\{deobf.py, consts.py, jvm.py}`（未修改） |

字符串还原（可复现）：

```
cd /d/vscode/VapeV4.21/build/kiss-tools
PYTHONIOENCODING=utf-8 python deobf.py "C:/Users/Jiang/AppData/Local/Temp/kiss-src/bc/ysg/a.java"
```

`deobf.py` 复用 `jvm.py` 的表达式求值器，逐语句求值 `StringBuilder` 链与 `new String(char[])`，
并把**每条**语句求出的串都打印，所以输出里既有片段（`'Aim '`、`'Mode'`）也有完整串（`'Aim Mode'`）。
判定"哪个是最终串"：同一 `this.xxx = ...` 语句块里**最后**出现的那条。

数值常量都是 `Double.longBitsToDouble(a ^ b)` / `Float.intBitsToFloat(a ^ b)`，用 python 折叠：

| 表达式（`a.java` 内字面） | 折叠值 | 含义 |
| --- | --- | --- |
| `Double.longBitsToDouble(-6289629024393965545 ^ -2929239412833439768)` | `1.7976931348623157e308` | `Double.MAX_VALUE`（`mn254` 最近面初值） |
| `Float.intBitsToFloat(1870440156 ^ -1862490404)` | `-Infinity` | `my134` 最优差初值 |
| `2139916436 ^ 2139916420` | `16` | 候选精化上限 `var83` |
| `-1976992064 ^ -1976992054` | `10` | 单水晶攻击冷却（tick） |
| `2011198337 ^ 2011198377` | `40` | 方块黑名单时长（tick） |
| `-1307629784 ^ -1307629790` | `10` | `WAIT_BASE` 超时（`mn392`） |
| `−741479548 ^ −741479538` | `10` | 等水晶出生超时（`mq130`） |
| `1489597061 ^ 1489597073` | `12` | 单阶段总超时（`mb673`） |
| `207796691 ^ 207796698` | `9` | hotbar 槽位数 |
| `-806898887 ^ -806898823` | `64` | 黑名单 Map 上限 |

> 混淆器把常量内联成了字面量，因此 `a.java:74-141` 里的 16 个 `static final` 字段
> （`fn700=0.75`、`fe512=6.0F`、`fz558=0.3F`、`fr99=0.1F`、`fb129=7.0`、`fy792=12.0`、
> `fk759=1000L`、`ft188=40`、`fo465=10`、`fa434=10`、`fu914=10`、`fw146=5`、`fz551=4`、
> `fc839=16`、`fv816=2`、`fy154=2.0`）**全是死字段**：除声明外无任何引用
> （`grep -n "fn700" a.java` 只命中声明行）。它们恰好等于上面这些魔数，说明"常量内联 + 字段残留"。
> 移植时按注释理解，不要去找引用。

内部类型（文件末尾）：

| 类型 | 位置 | 字段 | 用途 |
| --- | --- | --- | --- |
| `record c` | `5711-5762` | `cell, existingBase, supportPos, supportFace, enemyEstimate, selfEstimate`（访问器 `fa712/fe799/fb580n/fj263/fg329/fd371`） | 放置候选 |
| `record d` | `5764-5821` | `armor, toughness, resistanceMul, blastMul` + `float apply(float)` + `static a.d of(LivingEntity)` | 受伤者护甲画像 |
| `enum b` | `5692-5709` | `CRYSTAL(6.0F)` + `float diameter() { return power*2.0F; }` | 爆炸源（直径 12.0） |
| `enum e` | `5823-5834` | `IDLE, PLACE_OBBY, PLACE_CRYSTAL, ATTACK, WAIT_BASE` | 状态机 |
| `record f` | `5836-5853` | `pos, face` | 可交互方块面 |

---

## 1. 设置清单

注册在 `1060-1078`：`Setting[] var10001 = new Setting[981356735 ^ 981356719]`（=16），
按下列顺序填充后逐个 `registerSetting(...)`。**共 16 项**，名字全部由 `deobf.py` 还原。

| # | 行号 | 字段 | 类型 | 名称 | 默认 | min / max / step | 作用（引用） |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | 277 | `aimMode` | ModeSetting | `Aim Mode` | `Silent` | `Silent` / `LockView` | 旋转落地方式：`onPrePlace:5652-5662`、`my575:3529`、`mq887:2378/2398/2435`、`ms591:5406` |
| 2 | 400 | `attackMode` | ModeSetting | `Attack Mode` | `Auto` | `Auto` / `RightClick` | 每 tick 走 `mb871()` 还是 `mx164()`（`onPrePlace:5646-5649`） |
| 3 | 401 | `fov` | NumberSetting | `FOV` | `180.0` | `1.0` / `360.0` / `5` | 视野角，`ma636:4549` 用 `fov/2` |
| 4 | 434 | `range` | NumberSetting | `Range` | `4.5` | `1.0` / `6.0` / `0.1` | 目标最大距离 + 搜索半径（`mn196` 的 `var26=ceil(range)`） |
| 5 | 489 | `obbyDelay` | NumberSetting | `Obby Delay` | `0.0` | `0.0` / `500.0` / `5` | 放黑曜石最小间隔 ms（`ml535:2716`；仅 Auto 可见） |
| 6 | 554 | `crystalDelay` | NumberSetting | `Crystal Delay` | `0.0` | `0.0` / `500.0` / `5` | 放水晶最小间隔 ms（`mu347` 内 `mi762(crystalDelay)`） |
| 7 | 603 | `attackDelay` | NumberSetting | `Attack Delay` | `0.0` | `0.0` / `500.0` / `5` | 引爆最小间隔 ms（`mq130` 内 `mi762(attackDelay)`） |
| 8 | 660 | `minEnemyDamage` | NumberSetting | `Min enemy damage` | `3.0` | `0.0` / `20.0` / `1` | 敌方伤害下限：粗筛 `×0.3`（`4878`），精检 `×1.0`（`mn196`），选现成水晶（`my134:3835`） |
| 9 | 749 | `maxSelfDamage` | NumberSetting | `Max self damage` | `20.0` | `0.0` / `20.0` / `1` | 自身伤害上限（`ma792:5676`） |
| 10 | 766 | `efficient` | BooleanSetting | `Efficient` | `true` | — | 要求敌方伤害 > 自身伤害（`5178`） |
| 11 | 835 | `fastPlace` | BooleanSetting | `Fast Place` | `false` | — | 每 tick 最多推进 2 个阶段（`mb871:5521`；仅 Auto 可见） |
| 12 | 888 | `breakCrystals` | BooleanSetting | `Break Crystals` | `true` | — | 优先打现成水晶（`mn196:4670`） |
| 13 | 901 | `predict` | NumberSetting | `Predict` | `2.0` | `0.0` / `5.0` / `1` | 预测点数 N（`mx974:2583`，`(int)` 截断） |
| 14 | 942 | `animals` | BooleanSetting | `Animals` | `true` | — | 非玩家目标可选（`mh61:3192`） |
| 15 | 976 | `deathCheck` | BooleanSetting | `Death Check` | `true` | — | 自己死亡时自动关模块（`onPrePlace:5630-5638`、`onWorldChange:4556`） |
| 16 | 1022 | `debug` | BooleanSetting | `Debug` | `false` | — | `getMode()` 回显 `fp554`（`2495`） |

可见性（`1079-1094`）：`setVisibleWhen(...)` 只给两项加了条件 —— `obbyDelay` 与 `fastPlace`
**仅在 `attackMode.is("Auto")` 时可见**（它们只在 Auto 主循环里用）。

`ModeSetting` 的 4 参构造是 `(name, default, firstMode, String...rest)`，`rest` 由
`String[] var287 = new String[1]` 现填。求值结果：`aimMode = ['Silent','LockView']`、
`attackMode = ['Auto','RightClick']`。**没有别的模式项。**

---

## 2. 模式 / 子模式

### 2.1 `aimMode`

| 值 | 语义 | 代码路径 |
| --- | --- | --- |
| `Silent` | 旋转只进**发包旋转**，客户端视角不动 | `my575:3529-3533` → `ur.m.h.setTargetRotation(rot, this)` + `isRotating=true` + `ms591()`；`onPrePlace:5657-5662` 再补一次 + `touchTickLatch()`；`ms591:5406-5416` 在 Silent 下 `syncRotationNow()` + `syncRotationWhileIdle()` |
| `LockView` | 直接改本地 yaw/pitch，每 tick 最多 30° | `onPrePlace:5652-5654`：`setYRot(cur + clamp(wrapDegrees(target-cur), -30, 30))`，pitch 同理；`my575:3529` 改为分支 `release(this)`；`mq887:2378/2398/2435` 走"视角已对准"判定 |

LockView 还有外部渲染路径：若 `ur.m.a`（Kiss 瞄准辅助模块）存在，`onPrePlace:5652` 的
`ur.m.a.INSTANCE == null` 为假，则由 `onRender2D:2287-2307` 把目标和瞄点喂给
`ur.m.a.lockViewAim(...)`；`lockViewExternal = isEnable() && (aimMode=="LockView" || state!=IDLE)`。

### 2.2 `attackMode`

| 值 | 语义 | 代码路径 |
| --- | --- | --- |
| `Auto` | 自己找目标、搜基座、放黑曜石/水晶 | `mb871:5421-5560`（含 `mn196` 规划器） |
| `RightClick` | 只处理"玩家自己右键放下的黑曜石" | `mx164:1344-1457`，输入来自 `onBlockPlaced:3345-3351` |

`RightClick` 细节：
- `onBlockPlaced`（`3345`）：`isEnable() && attackMode.is("RightClick") && !fj17` 且
  `e.block()==Blocks.OBSIDIAN` → `fe961 = e.position()`、`fa854 = tickCount`。
- `mx164`（`1344`）：① `mo315()` 选目标（与 Auto 共用）；② `PLACE_OBBY`/`WAIT_BASE` 直接
  `mz450()`（这两阶段只在 Auto 有意义），`state != IDLE && mb673()` → `mz450()`；
  ③ `IDLE` 时：`fe961==null` → 返回；`tickCount-fa854>40` → 丢弃；`!mp605(fe961)` → 返回；
  `!mn755(Items.END_CRYSTAL)` → 返回；否则 `fx935=fe961; fe961=null; mn259(); mx938(PLACE_CRYSTAL)`；
  ④ 分派 `ATTACK → mu347()`、`WAIT_BASE → mq130()`，其它返回。
- `onUseItem:5584-5587`：`state != IDLE && fc255 != -1 && !fj17` 时**取消玩家右键**，
  防止打断放置序列。

### 2.3 内部模式（非设置项）

- 状态机 `enum e`（`5823`）：`IDLE / PLACE_OBBY / PLACE_CRYSTAL / ATTACK / WAIT_BASE`。
- 爆炸源 `enum b`（`5692`）：只有 `CRYSTAL`，`power=6.0F`，`diameter()=12.0`（原版水晶半径 6、伤害用直径 12）。
- `debug` 打开时 `getMode()`（`2495`）把 `fp554` 当后缀，于是 `fp554` 就是整个模块的**决策轨迹**（§4.4）。

---

## 3. 主流程

### 3.0 入口与每 tick 顺序

唯一 tick 入口 `onPrePlace(EventPrePlace)`（`5626-5668`），顺序严格如下：

```
5627  player/level/gameMode 空 或 screen != null           → mi41()
5629  player.isDeadOrDying()                              → mi41(); deathCheck && isEnable() → setEnable(false)+toast
5640  ie.d.INSTANCE 开启（Telly Bridge）或 bc.ysg.f.t.isActive() → mi41()
5642  attackMode 值变化                                    → mi41(); fv431 = 新值
5645  fv908 = null                                        // 每 tick 清空"本 tick 想要的旋转"
5646  attackMode == Auto ? mb871() : mx164()
5652  aimMode == LockView && ur.m.a == null && fv908 != null → 直接改 yaw/pitch（±30°，5653/5654）
5657  fv908 != null → fk762 = tickCount; if aimMode == Silent → setTargetRotation + isRotating + touchTickLatch（5658-5662）
5664  else if tickCount - fk762 > 10 → qs.ysn.d.release(this)
```

| 事件 | 行号 | 行为 |
| --- | --- | --- |
| `onEnable` | 5619 | `mz450()`、`fv364=null`、`fp554="on"` |
| `onDisable` | 1336 | `mi41()` |
| `onWorldChange` | 4554 | `mi41()`；`deathCheck && isEnable()` → `setEnable(false)` |
| `onRender2D` | 2287 | 把 `mg628()` 的瞄点/目标喂给 `ur.m.a` |
| `onStrafe` | 2528 | 仅 `Silent`：`qs.ysn.d.resolveTickMoveYaw()` + `a.je.kd.f.computeFixedInput(forward, strafe, yaw, targetYaw)` 重投影 WASD |
| `onBlockPlaced` | 3345 | RightClick 输入 |
| `onUseItem` | 5584 | 序列中吞玩家右键 |

`mi41()`（`3631-3668`）硬清理：`my930()`、`fe961=null`、`fg722.clear()`、`ft66.clear()`、
`fc50=-1`、`fa644=0`、`mz450()`、`fv364=null`、`ur.m.a.INSTANCE.lockViewExternal=false`（判空）、
`qs.ysn.d.release(this)`、`fv908=null`。

`mz450()`（`1171-1256`）状态清理：`fb521=IDLE`、`fx935=fw299=fv558=ff514=null`、`fc127=0`、
`fn2=0`、`fh79=true`、`ff969=Direction.UP`；若 `attackMode.is("RightClick")` 再 `my930()` +
`release(this)` + `fv908=null`。

### 3.1 目标选择

**候选池** `mo315()`（`3882-3920`）：

```
3883  AABB box = player.getBoundingBox().inflate(range + 2.0)
3884  收集 box 内 LivingEntity，过滤 ma636()
3888  空 → fv364 = null
3891  粘滞：当前 fv364 仍在列表内且 distanceTo(fv364) <= 最近距离 + 0.75 → 保留
3900  否则按 distanceTo(player) 升序取第一个 → fv364
```

**过滤** `ma636(LivingEntity)`（`4541-4551`）：

```
4542  entity == mc.player || !alive || isRemoved || health<=0 || entity == player.getVehicle() → false
4546  player.distanceTo(entity) > range → false
4549  mo45(entity) > fov / 2.0 → false
4550  return mh61(entity)
```

`mo45`（`3609-3629`）= `getLookAngle()` 与"眼→目标眼"夹角（`deg(acos(clamp(dot,-1,1)))`，
距离 < 1e-6 返回 0）。**是 3D 夹角，不是纯 yaw。**
`mh61`（`3165-3196`）：Player → `isFriend` / `shouldSkip` 否决；其它实体只接受
`Animal / Monster / AbstractVillager / IronGolem`，最后 `return animals.getValue()`。

**明确没有做的事**：没有隐身过滤；没有无敌帧（`hurtTime`/`invulnerable`）过滤；
没有护甲预筛（护甲只进伤害 `a.d.of`）；没有队伍/scoreboard 判断（队伍语义只经由
`isFriend`/`shouldSkip`）；没有目标锁定超时，只有 0.75 粘滞；目标为空时 `mb871:5456`
直接 `mi41()`。

### 3.2 基座搜索与规划

规划器 `mn196(boolean obbyAllowed)`（`4591-5345`），唯一调用点 `mb871:5533`（`mn196(true)`），
只在 `fb521 == IDLE` 时跑。**候选格围绕"玩家"生成，不是围绕目标**（`var27..var29` 取 `floor(player)`）。

**阶段 A：环境快照**

```
4593  eye = player.getEyePosition(1.0F)
4594  var3 = fv364.position();  var4 = range
4595  var6 = (blockInteractionRange + 1)^2        // 放置/命中距离上限（平方）
4596  var8 = range + 2.0
4597  AABB(player±var8 ∪ target±var8) → ft952 = getEntitiesOfClass(Entity, box, !isSpectator)
4600  fc561 = ft952 中存活 EndCrystal
4608  var79 = a.d.of(player); var80 = a.d.of(target)
4670  breakCrystals && !fc561.isEmpty() → my134(var79,var80)
4703    非空 → ff514=水晶; fx935=ms560(水晶); fp554="break existing"; mx938(ATTACK); return true
4712  !mn755(Items.END_CRYSTAL) → fp554="crystal: no item"（拼接）; my930(); return false
4718  var1 = var1 && mn755(Items.OBSIDIAN)       // obbyAllowed 且包里有黑曜石
```

**阶段 B：候选生成**

```
4720  mx974(var3)                                 // 预测点列表 fx793
4721  var82 = player.position(); fl800.clear(); 计数器清零
4725  var26 = ceil(range); var27/28/29 = floor(player.x/y/z)
4729  var30 = floor(target bbox minY)
4730  var31 = min(var28,var30) - 3                // Y 下界
4731  var32 = max(var28,var30) + 2                // Y 上界
4732  var33 = 144.0                               // 目标距离平方上限 = 12²
4734  for y in [var31,var32] × x in [var27±var26] × z in [var29±var26]:
4738    mt319(pos.asLong()) == false → skip       // 40 tick 方块黑名单
4740    centre = (x+0.5, y+1.0, z+0.5)
4741    eye 到 centre 的 dist² > var6 → skip
4746    target 到 centre 的 dist² > var33 → skip
4750    var56 = new BlockPos; var57 = state(var56); var58 = mp605(var57)
4752    候选:
          (var58 || (var1 && mi539(var57)))       // 已基座，或可替换且允许放黑曜石
          && state(var56.above()).isAir()
          && !mo556(var56)                        // 水晶 AABB(y+1..y+3) 内无实体
          && (var58 || !mc676(var56))             // 非基座时格子本身必须无方块
4780    若 !var58: var59/var60 = mn254(var56)     // 找"可见邻面"当黑曜石贴靠面；null → skip
          target 到 mo738(var59,var60) 的 dist² > var6 → skip
          eye.y <= var56.getY() + 1.0 → skip      // 眼位不得低于基座上一格
4806    var100 = max over fx793 of mq910(centre, p, var80, 1.0F)   // 粗估敌方（exposure=1）
4816    var100 <= 0 → skip
4817    var101 = mq910(centre, var82, var79, 1.0F)                 // 粗估自身
4818    fl800.add(new a.c(var56, var58, var59, var60, var100, var101))
4827  ft821 = fl800.size(); 空 → fp554="no plan cand"(片段); return false
```

**阶段 C：排序与精化**

```
4837  fl800.sort: me36 降序 → existingBase 优先 → mu544(cell).distanceTo(var3) 升序
4848  me36(a.c) = enemyEstimate - selfEstimate + (existingBase ? 0.5F : 0)      // a.java:2349
4860  for (var83 = 16; var83 > 0 && 未取完; var83--):          // 最多 16 个
4865    var88 = existingBase ? cell : supportPos
4866    var39 = existingBase ? Direction.UP : supportFace
4867    md915(var88, var39) == false → skip                    // 面必须真的可见
4869    var91 = mu544(cell); var41 = existingBase ? null : cell
4872    var94 = mb747(var91, var3, var41)                     // 12 点竖直采样见光率(0..1)
4874    var43 = max over fx793 of mq910(var91, p, var80, var94)   // 见光率修正后的敌方伤害
4878    var43 < minEnemyDamage * 0.3 → skip                    // 粗筛只放 30% 门槛
4880    ff870.add(new a.c(cell, existingBase, var88, var39, var43, selfEstimate))
4959  ff870 空 → fp554="no plan occluded"+计数; return false
4962  ff870.sort(me36 降序)
```

**阶段 D：精确伤害复检**

```
4965  var89 = 4                                   // 最多试 4 个
4970  fz213++
4976  var96 = mh644(player, mu544(cell), var79, existingBase?null:cell)     // 精确自身伤害
4980  var96 < 0 → continue
4984  !ma792(var96) → fi113++; continue                          // 自伤不安全
4996  var98 = mh644(fv364, mu544(cell), var80, existingBase?null:cell)      // 精确敌方伤害
5004  var98 < minEnemyDamage → fa932++; continue
5178  !efficient || var98 > var96                                 // 效率闸门
5196  → 接受: ff922++; 记录第一个接受项; break
5202  成功时 fp554 = "place obby dmg/" + 数值（拼接片段）
5233  var85 == null → fp554="no plan cand low self eff exact"+计数; return false
5334  fx935 = var85.fa712
5335  fw299 = var85.fe799 ? null : var85.fb580n
5336  fv558 = var85.fe799 ? null : var85.fj263
5337  ff514 = null;  5338 mn259()
5339  mx938(var85.fe799 ? PLACE_CRYSTAL : PLACE_OBBY); return true
```

要点：评分 = `敌方估算 − 自身估算 + 0.5`（已有基座加分）；估算阶段用"只按距离"的
`mq910(..., exposure=1.0F)`，精化阶段用 `mb747` 的 12 点见光率当 exposure，最终决策用完整
`mh644`（含方块密度 `ms9`）。`minEnemyDamage` 三级门槛：粗估 `>0`、粗筛 `≥0.3×`、精检 `≥1.0×`。
已有基座（`existingBase=true`）不需要黑曜石，也不需要 `supportPos/supportFace`，直接进 `PLACE_CRYSTAL`。

**辅助谓词**

| 方法 | 行号 | 语义 |
| --- | --- | --- |
| `mn254(BlockPos, boolean needLos)` | 1125 | 6 方向取邻块 `pos.relative(dir.getOpposite())`，要求非空气、`!mi539`、`!du.f.isInteractable`；交互点 `mo738(neighbor,dir)`；要求 `eye.subtract(point)·dirVec > 0`；距离² ≤ `min(range, blockInteractionRange)²`；`needLos` 时还要 `md915`；返回**最近**的 `record f` 或 null |
| `mn254(BlockPos)` | 2268 | 上面 `needLos=false` 的重载 |
| `mo556` | 1282 | `AABB mv189(pos)` 与 `ft952` 任一实体相交 → true |
| `mv189` | 2473 | `new AABB(x, y+1, z, x+1, y+3, z+1)`（水晶碰撞箱） |
| `mc676` | 3306 | 同上但 `new AABB(pos)`（精确格子） |
| `mw944` | 1340 | `state(pos.above()).isAir() && getEntities(null, mv189(pos)).isEmpty()` |
| `mp605(BlockState/BlockPos)` | 3424 / 2999 | `is(Blocks.OBSIDIAN) || is(Blocks.BEDROCK)` |
| `mi539` | 3466 | `isAir() || canBeReplaced()` |
| `mq488` | 2313 | `mi539(state) && !mp605(state)` |
| `mk10(pos, face)` | 3357 | 参数非空且该位置是实心、不可替换方块 |
| `md915(pos, face)` | 3670 | 眼到 `mo738(pos,face)` 的视线检查 |
| `mz587(pos, from, to)` | 3428 | 基座方块对射线的遮挡（slab eps 1e-4 / 0.9999） |
| `mp702(pos)` | 2683 | 可见面选择：优先 `ff969`，否则第一个 `md915` 可见方向，再否则 null |
| `mo738(pos, face)` | 3383 | 面中心点（见下表） |

`mo738` 面中心（eps = 0.001，侧面高 0.75）：UP `(x+0.5, y+1-eps, z+0.5)`、DOWN `(x+0.5, y+eps, z+0.5)`、
NORTH `(x+0.5, y+0.75, z+eps)`、SOUTH `(x+0.5, y+0.75, z+1-eps)`、WEST `(x+eps, y+0.75, z+0.5)`、
EAST `(x+1-eps, y+0.75, z+0.5)`。

### 3.3 放置流程（黑曜石 / 水晶共用"瞄准 → 校验 → 发包"链）

`my575(BlockPos pos, Direction face)`（`3485-3539`）：

```
3486  var3 = p.ibr.zq.g.rotationTo(mc.player.getEyePosition(1.0F), mo738(pos, face))
3487  fv908 = var3                                 // 绝对角，登记为本 tick 目标旋转
3529  aimMode.is("LockView") ? qs.ysn.d.release(this)
                            : ur.m.h.setTargetRotation(var3, this); isRotating = true; ms591()
3536  var4 = mj248()                               // 当前发包旋转（null 则取玩家 yaw/pitch）
3537  return mk776(var4, pos, face) != null
```

`mk776(p.ibr.zq.h rot, BlockPos pos, Direction face)`（`4561-4587`）：

```
4564  var5 = eye + directionFromRotation(rot) * min(range, blockInteractionRange)
4565  var6 = level.clip(ClipContext(eye, var5, Block.OUTLINE, NONE, player))
4568  var6.type == BLOCK && getBlockPos()==pos && getDirection()==face → 返回 var6
     否则走备用 clip 分支（仍可能 null）；参数任一 null → null
```

`mw116(BlockPos, Direction)`（`3857-3880`）——**真正发包**：

```
3858  var3 = mk776(mj248(), pos, face); null → false
3862  fj17 = true; try { var4 = mc.gameMode.useItemOn(mc.player, fo56, var3) } finally { fj17 = false }
3871  !var4.consumesAction() → false
3874  mc.player.swing(fo56)
3875  fl606 = System.currentTimeMillis();  3876 fh79 = false;  3877 return true
```

**黑曜石** `ml535()`（`2702-2997`）：

```
2703  fw299/fv558/fx935 任一为空 → mz450(); return false
2706  mp605(fx935) → mx938(PLACE_CRYSTAL); return true           // 基座已在，跳过
2714  !mi762(obbyDelay) → return false                            // 毫秒闸门
2716  !mq488(fx935) || !mk10(fw299,fv558) → mr857(fx935); fp554="obby: …"; mz450(); return false
2746  !mo754r(Items.OBSIDIAN) → fp554="obby: no item"(2805); mz450(); return false
2855  !my575(fw299, fv558) → fp554="obby: waiting aim"; return false
2921  !mw116(fw299, fv558) → mr857(fx935); fp554="obby: refused"; mz450(); return false
2983  fp554="placed obby"; fl606=now; fh79=false
2994  mx938(mp605(fx935) ? PLACE_CRYSTAL : WAIT_BASE); return true
```

**等基座** `mn392()`（`3054-3162`）：`fx935 != null && mp605(fx935)` → `mx938(PLACE_CRYSTAL)`，
返回 true；否则 `fp554="obby: waiting update"`，`tickCount-fn2 > 10` 时 `mr857(fx935)` + `mz450()`。

**水晶** `mu347()`（`1461-2243`）：

```
1462  fx935 == null → mz450(); return false
1466  var1 = ma102(fx935)                              // 基座上方 0.5 格内是否有水晶
1479  var1 != null → ff514=var1; mx938(ATTACK); return true
1481  !mp605(fx935) → mr857(fx935); fp554="crystal: no base"(1557); mz450(); return false
1571  !mw944(fx935) → fp554="crystal: slot busy"(1640); return false   // 不 reset
1673  !mi762(crystalDelay) → return false
1675  !mo754r(Items.END_CRYSTAL) → fp554="crystal: no item"(1750); mz450(); return false
1754  var2 = mp702(fx935); null → fp554="crystal: no visible face"(1892); return false
1896  ff969 = var2; var3 = max(0, mh644(player, mu544(fx935), a.d.of(player), null))
1942  !ma792(var3) → fp554="crystal: self risk"; mz450(); return false
2083  !my575(fx935, var2) → fp554="crystal: waiting aim"; return false
2158  !mw116(fx935, var2) → mr857(fx935); fp554="crystal: refused"; mz450(); return false
2226  ff514=null; fp554="placed crystal"; mx938(ATTACK); return true
```

**切槽 / 手持物**：`mn259()`（`1254`）在 `fc255==-1` 时保存当前槽；`mb160(slot)`（`3198`）切 0..8 槽；
`my930()`（`2654`）还原并清空 `fc255`；`me978(Item)`（`1325`）返回首个持有该物品的热键槽；
`mn755(Item)`（`3353`）额外查副手；`mo754r(Item)`（`3576-3606`）决定手持：热键槽有 →
`fo56=MAIN_HAND`；否则副手有 → `fo56=OFF_HAND`；否则 `mn259()`+`mb160`；都没有 → false。
**没有静默切槽**（不发 swap 包），只用 `setSelectedSlot` + `useItemOn` —— 这是与 Vape 实现最大的差异之一。

**delay 闸门** `mi762(NumberSetting)`（`2309`）：`fh79 ? true : now - fl606 >= value`。
`fh79` 是"首次动作豁免"（`mz450` 置 true，任何成功发包置 false），`fl606` 是上次成功动作时间戳。

**失败重试** `mr857(BlockPos)`（`2556`）：写 `fg722[pos.asLong] = tickCount + 40`
（`size() > 64` 先清空）；`mt319(long)`（`3245`）在未到期时返回 false。`mr857` 共 9 处调用，
失败格会同时从候选与"已有基座"里排除。

### 3.4 破坏流程（引爆）

入口两条：① 规划器直接选中现成水晶（`4703`）；② 放完水晶进 `ATTACK` 态 → `mq130()`（`3945-4540`）。

```
3945  若 ff514 空/移除/死:  ff514 = fx935!=null ? ma102(fx935) : null
3960    仍空 && tickCount-fn2 > 10 → mr857(fx935); fp554="crystal: never spawned"; mz450(); return
4193    否则 fp554="crystal: waiting spawn"; return
3972  var2 = max(0, mw522(player, a.b.CRYSTAL, ff514.position(), a.d.of(player)))   // 自身伤害
4254  !ma792(var2) → fp554="attack: self risk"; mz450(); return
4293  !ms894(ff514) → fp554="attack: out of reach"; (fx935 非空则 mr857); mz450(); return
4360  !mi762(attackDelay) → return
4374  fv908 = p.ibr.zq.g.rotationTo(eye, ml96(ff514))
4463  !mq887(ff514) → fp554="attack: waiting aim"; return
4479  if (var3 != fc50) { fc50 = var3; fa644 = 1 } else fa644++        // var3 = ff514.getId()
4483  mc.player.swing(InteractionHand.MAIN_HAND)
4484  mc.gameMode.attack(mc.player, ff514)        // 攻击包，不是 interact
4485  ml751(var3)                                  // 该水晶 10 tick 内不再打
4486  fl606 = now; fh79 = false
4488  fa644 >= 5 && ff514.isAlive() && fx935 != null → mr857(fx935)   // 同水晶 5 次没死 → 拉黑基座
4514  fp554 = "detonated"；随后 mz450()
```

判定细节：
- **`mq887(EndCrystal)`（`2353-2471`）= 能不能打**：① `fv908==null` → false；② 按 `aimMode`
  分支（`2378/2398/2435`，`LockView` 走 `release` 路径，`Silent` 走 `setTargetRotation + isRotating + ms591`）；
  ③ `mj248()` 为空 → false；④ **发包旋转必须已对准**：`|Δyaw|>eps || |Δpitch|>eps` → false
  （eps = `1e-6`，`2466`），即本 tick 先转向、下一 tick 才打；⑤ 眼到水晶 AABB
  做 `AABB.clip(...)` + `level.clip(ClipContext(..., COLLIDER, ...))`，必须无方块阻挡。
- **距离** `ms894(Entity)`（`3292`）：`entity AABB distanceToSqr(eye) <= (entityInteractionRange + 0.5)²`。
- **单水晶冷却** `ml751(id)`（`3755`）：`ft66.put(id, tickCount+10)`；`mr264(crystal)`（`3007`）在未到期时 false。
- **预测只用于伤害估算**（`mx974`），不预测攻击点；攻击点是水晶实时 AABB（`ml96` 把眼位夹进 AABB）。

`my134(a.d self, a.d enemy)`（`3798-3855`）选现成水晶：过滤 `!mr264 && !mt319(ms560) && ms894 &&
player.distanceTo <= range+1.0` → 按 `distanceToSqr(fv364)` 升序 → **只看最近 2 颗** →
`selfDmg = mh644(player, pos, self, null)`，`<0` 或 `!ma792` 跳过 → `enemyDmg = mh644(target, pos, enemy, null)`，
`< minEnemyDamage` 跳过 → `diff = enemy - self`，取最大。

### 3.5 伤害计算

**受伤者画像** `record d`（`5764-5821`）：

```
of(e): armor = ARMOR; toughness = ARMOR_TOUGHNESS
       resistanceMul = RESISTANCE ? clamp((25-(amp+1)*5)/25, ≥0) : 1
       blastMul = 1 - clamp(mi72(e), 0, 20)/25
apply(dmg): dmg<=0→0;  s = 2.0 + toughness/4.0
            ea = clamp(armor - dmg/s, armor*0.2, 20.0)
            return max(dmg*(1-ea/25) * resistanceMul * blastMul, 0)
```

`mi72`（`5590-5617`）：四件护甲 `PROTECTION + 2×BLAST_PROTECTION` 求和，异常返回 0。

**三件套**

```
mr41(diameter, ratio, exposure)       3556-3573
  f = (1-ratio)*exposure
  return (f*f + f)/2 * 7.0 * diameter + 1.0        // 原版公式，系数恒 7.0

ms9(center, entity, base)             2605-2651 = 原版 getSeenPercent
  每轴 step = 1/((max-min)*2+1)，采样点 = AABB lerp + 半格抖动
  clip(point → center, COLLIDER, FLUID_NONE, entity)
    非 MISS → 挡住
    否则 base != null && mz587(base, point, center) → 也算挡住     // ★ Kiss 额外项
    否则 → 可见
  return 可见/总数（总数 0 时 0.0）

mw522(e, a.b bomb, pos, armor)        2244-2266
  d = pos.distanceTo(e.position())/bomb.diameter(); d>1 → -1.0F
  armor.apply(mr41(diameter, d, ms9(pos, e, null)))

mh644(e, pos, armor, base)            2499-2521
  d = pos.distanceTo(e.position())/12.0; d>1 → -1.0F
  armor.apply(mr41(12.0F, d, ms9(pos, e, base)))
```

`mh644` 与 `mw522` 唯一区别：`mh644` 把 `base` 传给 `ms9`，即"基座方块自身也算遮挡"。
这正是 Vape `calculateBlockDensity` 缺少的一项。

`mb747(crystalCentre, targetPos, base)`（`3765-3796`）12 点竖直见光率：

```
3767  fv364 == null → 1.0F
3769  bb = target.getBoundingBox(); pos = target.position()
3771  cx = targetPos.x + ((bb.minX+bb.maxX)*0.5 - pos.x)
3773  cz = targetPos.z + ((bb.minZ+bb.maxZ)*0.5 - pos.z)
3775  y0 = targetPos.y + (bb.minY-pos.y);  y1 = targetPos.y + (bb.maxY-pos.y)
3779  step = 1.0/((y1-y0)*2.0 + 1.0)
3781  for t in 0..1 step step，最多 12 次:
3783    p = (cx, lerp(t, y0, y1), cz)
3785    base != null && mz587(base, p, centre) → 跳过（既不计可见也不计遮挡）
3787    else level.clip(centre → p, COLLIDER, NONE, target).type != BLOCK → 可见++
3790  return 0 次采样 ? 1.0F : 可见/采样数
```

**防自杀** `ma792(float dmg)`（`5672-5677`）：

```
mc.player.getAbilities().invulnerable → true
j.getEffectiveHealth(player) <= dmg → false            // 必死
return dmg <= maxSelfDamage.getValue()
```

`j.getEffectiveHealth`（`bc/ysg/j.java:39`）= `max(0, health) + max(0, absorption)`。

| 阈值 | 使用点 |
| --- | --- |
| `maxSelfDamage` + 必死判定 | `ma792`：`1942`（放水晶前）、`4254`（引爆前）、`4984`（候选精检）、`3826`（选现成水晶） |
| `minEnemyDamage` | `4878`（×0.3）、`5004`（×1.0）、`3835` |
| `efficient` | `5178`：要求 `enemyDmg > selfDmg` |
| `predict` | `mx974`：预测点数，只影响估算 |

### 3.6 与旋转引擎的接口

| 对象 | 角色 |
| --- | --- |
| `p.ibr.zq.g` | 纯数学：`rotationTo(Vec3,Vec3)`（`g.java:391`）、`exactRotation`(263)、`normalizeRotation`(277)、`getSensitivitySnappedRotation`(282)、`rotateTowards`(295)、`rotationToBlock`(563)、`isLookingAt`(237)、`getHitDistanceLenient`(225) |
| `p.ibr.zq.h` | 旋转值对象：`yaw/pitch` + `apply()` + `snapToSensitivity` + `smoothYaw/smoothPitch`（鼠标量化）+ `subtract/clone/distanceTo` |
| `ur.m.h` | 静态包旋转写入器：`static h prevRotation/targetRotation`、`static boolean isRotating`、`static Object rotationOwner`、`setTargetRotation(h[, owner])`、`reset()` |
| `qs.ysn.d` | 静默旋转管理器（单例 `getInstance()`，`d.java:509`）：`syncRotationNow()`(292)、`syncRotationWhileIdle()`(352)、`touchTickLatch()`(1791)、`release(Object)`(137)、`getSendRotation()`(1878，返回 `ff666`)、`getModelRotation()`(149)、`resolveTickMoveYaw()`(155)、`applyRotation(Packet)`(194)、`toDetectionSafeYaw(float)`、`effectiveOnGround(boolean)` |

Kiss 的调用点：
1. **算角**：`my575:3486`、`mq130:4374` 都用 `p.ibr.zq.g.rotationTo(eye, point)` —— **绝对角**，非增量。
2. **登记**：`fv908` 字段，`onPrePlace:5645` 每 tick 清空，只有"本 tick 决定要瞄"的分支才赋值。
3. **落地**：`Silent` → `setTargetRotation(fv908, this)` + `isRotating=true` + `ms591()`，
   并在 `onPrePlace:5657-5662` 再补 + `touchTickLatch()`；`ms591:5406-5416` 仅在 Silent 下
   `syncRotationNow()` + `syncRotationWhileIdle()`。
   `LockView` → `my575:3529` 只 `release(this)`，实际视角由 `onPrePlace:5652-5654` 直接
   `setYRot/setXRot`（增量 ≤30°/tick）或 `ur.m.a.lockViewAim` 渲染驱动。
4. **校验**：`mj248()`（`3922`）取 `getSendRotation()`，null 时退化成 `new h(player.getYRot(), getXRot())`；
   `mk776` 与 `mq887` 都用它判断"这 tick 是否已对准"。
5. **移动修正**：`onStrafe:2528` 用 `resolveTickMoveYaw()` + `computeFixedInput` 重投影 WASD。
6. **还权**：连续 10 tick 无 `fv908` → `release(this)`（`5664`）。

---

## 4. 状态机与计时器

### 4.1 状态转移（`enum e`，`5823`）

| 从 | 到 | 位置 | 条件 |
| --- | --- | --- | --- |
| IDLE | PLACE_OBBY | 5339 | 规划器选中"需要放黑曜石"的候选（`existingBase=false`） |
| IDLE | PLACE_CRYSTAL | 5339 | 规划器选中已有基座 |
| IDLE | ATTACK | 4703 | `breakCrystals` 且 `my134` 找到现成水晶 |
| IDLE | PLACE_CRYSTAL | 1399 | RightClick：玩家放下黑曜石后 `mx164` 推进 |
| PLACE_OBBY | PLACE_CRYSTAL | 2712 | 进 `ml535` 时基座已存在 |
| PLACE_OBBY | WAIT_BASE | 2994 | 黑曜石包发了但方块还没变 |
| PLACE_OBBY | PLACE_CRYSTAL | 2994 | 发了包且 `mp605(fx935)` 已为真 |
| WAIT_BASE | PLACE_CRYSTAL | 3061 | 基座出现 |
| WAIT_BASE | IDLE | 3155 | 10 tick 超时 |
| PLACE_CRYSTAL | ATTACK | 1479 | 基座上方已有水晶 |
| PLACE_CRYSTAL | ATTACK | 2236 | 水晶发包成功 |
| ATTACK | IDLE | 4514 之后 | `detonated`(4514) 随后 `mz450()` |
| 任意 | IDLE | 1172 | `mz450()` |

> **命名与字面 `switch` 不符（重要）**：按反编译出的 `switch` 字面量，`PLACE_CRYSTAL` 分支
> 调 `mn392()`（等基座），`ATTACK` 分支调 `mu347()`（放水晶），`WAIT_BASE` 分支调 `mq130()`（引爆）。
> 但所有转移点与 `fp554` 调试串（`"obby: waiting update"` 在 `mn392`、`"crystal: never spawned"`
> 在 `mq130`）都指向语义顺序 `IDLE→PLACE_OBBY→(等方块)→PLACE_CRYSTAL 放水晶→ATTACK 引爆`。
> 这与混淆器打乱 `switch` 的手法一致（文件里布满 `if (a*b != c)` 死分支）。
> **结论**：语义顺序按上表实现；字面 case→方法绑定列入 §7 U-1（未解明）。

### 4.2 字段语义（`74-141`）

| 字段 | 类型 | 含义 | 写入点 |
| --- | --- | --- | --- |
| `fb521` | `a.e` | 当前状态 | `mx938:5564`、`mz450:1172` |
| `fc127` | `long` | 进入状态的时间（ms） | `mx938:5565` |
| `fn2` | `int` | 进入状态的 tick | `mx938:5566` |
| `fh79` | `boolean` | 首次动作豁免；true 时 `mi762` 直接放行 | `mz450` 置 true；发包后置 false |
| `fl606` | `long` | 上次成功动作的 ms 时间戳 | `mw116:3875`、`ml535:2983`、`mq130:4486` |
| `fk762` | `int` | 上次有 `fv908` 的 tick（10 tick 还权） | `onPrePlace:5658` |
| `fx935` | `BlockPos` | 当前基座格（水晶放其上） | `5334`、`4703`、`1399` |
| `fw299` | `BlockPos` | 黑曜石贴靠的邻块 | `5335` |
| `fv558` | `Direction` | 黑曜石贴靠面 | `5336` |
| `ff969` | `Direction` | 上次成功的水晶放置面（`mp702` 优先复用） | `1892`；`mz450` 置 UP |
| `ff514` | `EndCrystal` | 待引爆水晶 | `1474`、`2226`、`4703`、`3956` |
| `fv908` | `p.ibr.zq.h` | 本 tick 目标旋转 | `3487`、`4374`；每 tick 清空 |
| `fo56` | `InteractionHand` | 发包用手 | `mo754r:3576-3606` |
| `fc255` | `int` | 保存的玩家热键槽（-1 未保存） | `mn259:1254`、`my930:2654` |
| `fe961` | `BlockPos` | RightClick：玩家刚放下的黑曜石 | `onBlockPlaced:3345` |
| `fa854` | `int` | `fe961` 的记录 tick（40 tick 有效） | `onBlockPlaced:3346` |
| `fg722` | `Map<Long,Integer>` | 方块黑名单 `posLong → tickCount+40` | `mr857:2569` |
| `ft66` | `Map<Integer,Integer>` | 水晶冷却 `entityId → tickCount+10` | `ml751:3759` |
| `fc50` / `fa644` | `int` | 上次打的水晶 id / 连续攻击次数 | `mq130:4480` |
| `ft952` / `fc561` | `List` | 规划器实体快照 / 其中存活水晶 | `mn196` |
| `fx793` | `List<Vec3>` | 目标预测位置 | `mx974` |
| `fl800` / `ff870` | `List<a.c>` | 阶段 1 / 阶段 2 候选 | `4818` / `4880` |
| `ft821` | `int` | 阶段 1 候选数 | `4827` |
| `fz213/fa932/fi113/ff922` | `int` | 精检计数：尝试 / 敌伤不足 / 自伤不安全 / 接受 | `mn196` |
| `fj17` | `boolean` | 发包重入闩锁 | `mw116:3862` |
| `fp554` | `String` | 调试状态串（§4.4） | 30 处 |
| `fv431` | `String` | 上次 `attackMode` 值（变更检测） | `onPrePlace:5642` |

### 4.3 计时器一览

| 计时器 | 值 | 语义 | 位置 |
| --- | --- | --- | --- |
| 动作延迟 `mi762` | 设置值 ms | `now - fl606 >= v`，`fh79` 豁免 | 2309 |
| 单阶段总超时 | 12 tick | `tickCount - fn2 > 12` → `mz450` | `mb673:3462` |
| 等基座超时 | 10 tick | `> 10` → 拉黑 + 重置 | `mn392:3155` |
| 等水晶出生超时 | 10 tick | `> 10` → 拉黑 + 重置 | `mq130:3960` |
| 方块黑名单 | 40 tick | `fg722`，`mt319` 判 | `mr857:2569` |
| 水晶攻击冷却 | 10 tick | `ft66`，`mr264` 判 | `ml751:3759` |
| RightClick 基座有效期 | 40 tick | `tickCount-fa854 > 40` → 丢弃 | `mx164` |
| 目标粘滞 | 0.75 格 | 不换目标 | `mo315:3891` |
| 瞄准还权 | 10 tick | 无 `fv908` → `release` | `onPrePlace:5666` |
| 同水晶攻击上限 | 5 次 | `fa644 >= 5` → 拉黑基座 | `mq130:4510` |

### 4.4 `fp554` 状态串 → 分支（`debug` 时可见）

| 行 | 串 | 含义 |
| --- | --- | --- |
| 1057 | `idle` | 初始 |
| 1557 | `crystal: no base` | `mu347`：该格不是黑曜石/基岩 |
| 1640 | `crystal: slot busy` | `mu347`：水晶占用格被占 |
| 1750 | `crystal: no item` | `mu347`：没有末地水晶 |
| 1892 | `crystal: no visible face` | `mu347`：`mp702` 无可见面 |
| 1942 | `crystal: self risk` | `mu347`：`ma792` 拒绝 |
| 2083 | `crystal: waiting aim` | `mu347`：`my575` 未对准 |
| 2158 | `crystal: refused` | `mu347`：发包被拒 |
| 2226 | `placed crystal` | 水晶放成功 |
| 2718 | `obby: …`（片段） | `ml535`：格不可放 / 贴靠面不实心 |
| 2805 | `obby: no item` | 没有黑曜石 |
| 2855 | `obby: waiting aim` | `ml535` 未对准 |
| 2921 | `obby: refused` | 发包被拒 |
| 2983 | `placed obby` | 黑曜石放成功 |
| 3149 | `obby: waiting update` | `mn392` 等服务端方块更新 |
| 4057 | `crystal: never spawned` | `mq130`：10 tick 没出现 |
| 4193 | `crystal: waiting spawn` | 等待水晶实体 |
| 4254 | `attack: self risk` | 引爆自伤不可接受 |
| 4369 | `attack: out of reach` | `ms894` 距离不够 |
| 4463 | `attack: waiting aim` | `mq887` 未对准 |
| 4514 | `detonated` | 引爆成功 |
| 4703 | `break existing` | 选中现成水晶 |
| 4719 | `crystal: …`（片段 `al`） | `mn196`：没有末地水晶 |
| 4830 | `…pot`（片段，推断 `no spot`） | `mn196`：候选为空 |
| 4959 | `no plan occluded` | 精化后无候选 |
| 5202 | `place obby dmg/` | 接受候选并记录伤害 |
| 5255 | `no plan cand low self eff exact` | 精确复检全否 |
| 5456 | `no target` | 没目标 |
| 5511 | `stage timeout` | 阶段超时重置 |
| 5622 | `on` | `onEnable` |

> 4719 / 4830 是多段拼接，`deobf.py` 只解出片段，补全属推断（§7 U-2）。

### 4.5 每 tick 推进（Auto）

```
EventPrePlace → 前置闸门 → mb871
  ├─ mo315 选目标（→ ma636 → mo45 / mh61）
  ├─ 目标空 && state != ATTACK → "no target" + mi41
  ├─ state != IDLE && mb673（12 tick）→ "stage timeout" + mz450
  ├─ state == ATTACK && fx935 != null → mr857(fx935)      // 见 §7 U-3
  ├─ mz450
  ├─ iterations = fastPlace ? 2 : 1
  └─ for i in [0, iterations):
       ├─ state == IDLE 且 (目标空 || !mn196(true)) → return
       └─ switch(state): IDLE→(由 mn196 推进) / PLACE_OBBY→ml535 / PLACE_CRYSTAL→mn392 /
                          ATTACK→mu347 / WAIT_BASE→mq130
→ aimMode 落地（§3.6）
```

---

## 5. 依赖的外部 Kiss 类

| 依赖 | 签名 | 作用 | 调用点 |
| --- | --- | --- | --- |
| `p.ibr.zq.g` | `static h rotationTo(Vec3, Vec3)` | 绝对 yaw/pitch | `3486`、`4374` |
| `p.ibr.zq.g` | `exactRotation`(263)、`normalizeRotation`(277)、`getSensitivitySnappedRotation`(282)、`rotateTowards`(295)、`rotationToBlock`(563)、`isLookingAt`(237)、`getHitDistanceLenient`(225) | CrystalAura 不直接调（供其它模块） | — |
| `p.ibr.zq.h` | `float yaw/pitch`、`void apply()`、`h snapToSensitivity(Float)`、`smoothYaw/smoothPitch`、`subtract`、`distanceTo` | 旋转值对象；`apply()` 直接改本地视角 | `onPrePlace:5653-5654` 读 `getYaw/getPitch` |
| `ur.m.h` | `static h prevRotation/targetRotation`、`static boolean isRotating`、`static Object rotationOwner`、`setTargetRotation(h[, Object])` | 静默旋转目标 | `my575:3532`、`onPrePlace:5660` |
| `qs.ysn.d` | `static d getInstance()`(509)、`syncRotationNow()`(292)、`syncRotationWhileIdle()`(352) | 静默旋转管理器 / 落地 | 多处；`ms591:5414-5415` |
| `qs.ysn.d` | `touchTickLatch()`(1791)、`release(Object)`(137)、`getSendRotation()`(1878)、`resolveTickMoveYaw()`(155) | 占 tick / 还权 / 读发包旋转 / 移动朝向 | `5662`、`3529`、`3924`、`onStrafe:2537` |
| `qs.ysn.d` | `applyRotation(Packet)`(194)、`static toDetectionSafeYaw(float)`、`static effectiveOnGround(boolean)` | 包改写/反检测（间接生效） | — |
| `ur.m.a` | `INSTANCE`、`boolean lockViewExternal`(84)、`lockViewAim(LivingEntity, Vec3)`(1802)、`lockViewAimPoint(Vec3)`(2095)、`lockViewAim(...)`(1615/1978) | LockView 渲染/瞄准 | `onRender2D:2287-2307` |
| `qs.ysn.e.a` | `void show(String, boolean)` | 关模块提示 | `onPrePlace:5633` |
| `ie.d` | `INSTANCE.isEnable()`（RISKY，Telly Bridge） | 开启时让路 | `onPrePlace:5640` |
| `bc.ysg.f.t` | `static void publish(...)`、`isActive()`、`yaw()` | 朝向占用闩锁 | `5640` |
| `bc.ysg.f.s` | `publish(forward,strafe,jump,sprint)`、`isActive()`、`clear()` | 移动状态发布 | 间接 |
| `j` | `static float getEffectiveHealth(LivingEntity)`（`bc/ysg/j.java:39`） | 有效血量 | `ma792:5674` |
| `a.je.kd.f` | `static float[] computeFixedInput(fwd, strafe, yaw, targetYaw)`(1009)、`computeFixedInputKeepForward`(1435) | 静默 yaw 下重投影 WASD | `onStrafe:2540` |
| `msb.j.b` | `boolean isFriend(Player)` | 目标过滤 | `mh61:3168` |
| `ss.x.ga.a` | `boolean shouldSkip(Entity)` | 目标过滤（RISKY 模块） | `mh61:3170` |
| `du.f` | `boolean isInteractable(BlockState)` | 找贴靠面时排除可交互方块 | `mn254:1133` |

---

## 6. 移植建议（映射到 Vape 现有能力）

Vape 侧现状：`src/main/java/gg/vape/module/combat/CrystalAura.java`（310 行，已提供大部分数学与判定）；
`src/main/java/gg/vape/module/combat/crystalaura/KissCrystalAuraSubModule.java`（839 行，已是
一份 Kiss 移植，`Mode = Kiss`），但与本规格差异较多（6.3）。

### 6.1 可直接复用

| Kiss | Vape | 说明 |
| --- | --- | --- |
| `a.d.apply`（`5764`） | `CrystalAura.reduceDamageByArmor`（`CrystalAura.java:63-67`） | 公式逐项相同 |
| `a.d.of` 的 resistance/blast 乘子 | `applyPotionAndEnchantmentReductions`（`76-90`）+ `reduceDamageByBlastProtection`（`268-271`）+ `getBlastProtectionLevel`（`287-298`） | Vape 先护甲再药水/附魔，Kiss 先护甲再 `×(resist×blast)`，数值等价 |
| `mi72`（`5590`） | `getBlastProtectionLevel`（`287-298`） | `PROTECTION + 2×BLAST_PROTECTION`，同 |
| `mr41`（`3556`） | `calculateRawExplosionDamage`（`187-191`） | 同式；**差异**：Vape 对 1.12.2 用 7.0、其它 8.0，Kiss 恒 7.0 → 目标版本要传 7.0 |
| `ms9`（`2605`） | `calculateBlockDensity(Vec3, Entity, World)`（`227-260`） | 采样/抖动/`world.K(...)` 一致；**缺** `mz587` 基座遮挡项 |
| `mw522`/`mh644` | `calculateExplosionDamage(player, ExplosionType, Vec3, World)`（`193-218`） | 可直接替代；需要"传 base"时自行组合密度变体 |
| `ma792`（`5672`） | `isSelfDamageSafe(...preventSuicide=true, maxSelfDamage)`（`126-136`） | `preventSuicide` 对应必死分支，`maximumDamage` 对应 `maxSelfDamage` |
| `mp605`（`3424`） | `isCrystalBaseBlock`（`279-285`） | 同（obsidian/bedrock 子串） |
| `mn755`/`me978` | `findCrystalSlot`/`findObsidianSlot`/`isEndCrystal`/`isObsidian`（`54-61`、`106-124`、`300-309`） | 同；Kiss 多查副手 |
| `mo738`（`3383`） | `getInteractionPoint(DirectionalPosition)`（`138-160`） | 几何一致（UP `y+1`、侧面 `y+0.75`）；Kiss 多 0.001 eps |
| `mr857`/`mt319`（40 tick） | 需自写 | 现有已用 `blacklist` + `BLACKLIST_TICKS=40`（`:84`、`:61`） |
| `mr264`/`ml751`（10 tick） | **缺失** | 现有用 `crystalHits` + `MAX_HITS_PER_CRYSTAL=5`，无单水晶 10 tick 节流 |
| `my134` | `findBestCrystalToBreak`（`:543`） | 现有按分数而非"最近 2 颗 + 差最大" |

### 6.2 必须新写

1. **两阶段规划器**：现有 `search()`（`:288-389`）只取"单一最优"，缺
   "粗估 `mq910(...,1.0)` → 排序 → 16 个 `mb747` 修正 → 按 `me36` 重排 → 4 个精确 `mh644` 复检"，
   以及 `minEnemyDamage×0.3` 粗筛（`4878`）与 `existingBase ? 0.5 : 0` 加分（`me36:2349`，现有写 0.3）。
2. **`mb747` 12 点竖直见光率**（`3765`）：Vape 无等价物，按 §3.5 实现，含 `mz587` 额外遮挡分支。
3. **`mn254` 邻面搜索**（`1125`）：现有 `placeObsidian` 硬编码 `facing=1`、贴靠块固定 `y-1`。
   Kiss 是 6 方向最近可见面，还要 `du.f.isInteractable` 排除与 `eye.subtract(point)·dirVec > 0` 前向性。
4. **`mp702` 可见面记忆**（`2683`）+ `ff969` 字段。
5. **`mq887` "已对准才能打"**（`2353`）：现有用 `getNormalReachRayTrace()` 命中实体即打；
   Kiss 要求"发包旋转 ≈ 目标旋转（1e-6）"且"眼到 AABB 无方块阻挡"。
6. **`fa644 >= 5` 拉黑基座**（`4510`）+ `fc50` 记录上次水晶 id。
7. **两套模式**：`aimMode`（Silent/LockView）与 `attackMode`（Auto/RightClick）。
   Silent 映射到 `RotationManager` + `AdaptiveRotationController`/`FixedRotationController` +
   `SharedModuleControlClaims.rotation`（`CrystalAura.java:47`、`224`）；
   `setTargetRotation(rot, owner)`+`isRotating`+`syncRotationNow()` ≙ "提交绝对 `RotationAngles` 并接管本 tick 发包"。
   LockView 用"会真转视角"的控制器（`RotationManager.onMotionUpdate:374` 一类）+ 30°/tick 钳制。
   RightClick 需新写"`EventBlockPlaced` 记录 + 40 tick 窗口 + 序列中取消玩家右键"。
8. **副手回退**（`mo754r`）：Kiss 允许 `fo56=OFF_HAND`，现有只 `switchToSlot`。
9. **移动重投影**：直接用 `RotationManager.adjustMovementYaw`（`:537`）/
   `adjustMovementYawFromBindings`（`:109`）/`suppressForwardMovement`（`:263`），不要重写 `computeFixedInput`。
10. **`fj17` 重入闩锁**：发包期间屏蔽 `onBlockPlaced`/`onUseItem` 副作用。

### 6.3 现有 `KissCrystalAuraSubModule` 与本节规格的差异

| # | 项 | 现有实现 | Kiss 原版 |
| --- | --- | --- | --- |
| 1 | 搜索半径 | 硬编码 `25.0`（`:329`） | `(blockInteractionRange+1)²`（`4595`） |
| 2 | 目标距离上限 | 无 | `144`（`4732`） |
| 3 | 候选精化 | 单最优 + `verifyCandidate`（`:288-406`） | 16 见光率 + 4 精确（`4860-5220`） |
| 4 | 贴靠面 | 固定 `y-1`、`facing=1`（`:340-346`） | `mn254` 6 方向（`1125`） |
| 5 | 放水晶的面 | `aimAt(cell, 1)`（`:489`） | `mp702` 优先 `ff969`（`1850`） |
| 6 | 攻击节流 | `crystalHits`/5（`:533-539`） | `ft66` 10 tick（`3755`）+ 5 次（`4510`） |
| 7 | 攻击判定 | `getNormalReachRayTrace()`（`:523-530`） | `mq887` 双条件（`2353`） |
| 8 | 放前检查 | `hasBlockingEntity`（`:480`） | `mw944`（`1340`） |
| 9 | 密度基座遮挡 | 无 | `mz587`（`2605`） |
| 10 | `predict` | 只加水平点（`:619`） | `±0.6`/`±0.5` 夹取 + 静止跳过（`2578`） |
| 11 | 目标过滤 | yaw-only vs `getManagedYaw()`（`:257-278`） | 3D 夹角 `mo45` + `fov/2`（`4549`） |
| 12 | `Animals` 默认 | `false`（`:120`） | `true`（`942`） |
| 13 | 步进 | Delay 10、Min dmg 0.5 | Delay 5、Min dmg 1 |
| 14 | `Auto Obsidian` | 额外开关（`:122`） | 无；由 `attackMode==Auto` + `mn755(OBSIDIAN)` 隐含（`4718`） |
| 15 | 模式 | 仅 Auto | Auto / RightClick |
| 16 | 视角 | 仅静默 | Silent / LockView |
| 17 | 副手 | 无 | `OFF_HAND`（`3576-3606`） |
| 18 | 重入守卫 | 无 | `fj17` |

### 6.4 事件挂载

- 每 tick：`EventPreTick`（现有 `:150`）≙ Kiss `onPrePlace`（每 tick 一次）。
- 放置：`CrystalAura.placeItem()`（`:714`）+ `rotationManager.getNormalReachRayTrace()`（`:631`）；
  若要 1:1 复刻 Kiss，改成"先设旋转 → 下 tick 用发包旋转校验 → 再 `useItemOn`"的两拍节奏。
- 玩家放方块：`EventBlockPlaced`（现有未监听）→ RightClick 输入。
- 世界切换/禁用：`EventWorldChange` + `onDisable` ≙ `mi41`（`3631`）。
- 旋转落地：不要自己写包，挂 `RotationManager`（`rotationClaim.setPriority(this, 6)` 已有）。

---

## 7. 未解明项

- **U-1 `switch` 标签语义**（`5537-5555`）：`case` 与 `mn392/mu347/mq130` 的绑定与全部转移点/
  调试串矛盾。判定为混淆器打乱；语义顺序按转移点实现，但"运行时哪个 case 调哪个方法"无法静态确定。
  验证：`debug=true` 观察 `fp554` 序列，或在 `mx938` 下断点看 `fb521`。
- **U-2 `fp554` 片段串**：`4719`（`al`）与 `4830`（`pot`）只解出片段，补全（推断
  `crystal: no item` / `no spot`）未证实。
- **U-3 `mb871:5514` 的 `mr857`**：`ATTACK` 阶段每 tick 都把**正在使用的基座**写进 40 tick 黑名单，
  与"引爆后 `mz450`"配合看起来会自锁。可能是故意的"一基座只用一轮"，也可能是死代码；
  现有 Vape 实现没有这一行。
- **U-4 `mn196:4780` 的 `eye.y <= cell.y + 1.0`**：只对"需要放黑曜石"的分支生效，
  为何不对 existingBase 分支生效未解明。
- **U-5 `me36` 加分常量**：反编译为 `0.5F`（`2349`），现有 Vape 写 `0.3`；二者不能同时正确，
  本文以反编译值为准，但未运行时验证。
- **U-6 `mz587` 精确几何**（`3428-3460`）：只确认"slab/AABB 射线 + eps 1e-4/0.9999"，
  是"基座方块整体"还是"基座的水晶碰撞箱"未逐行读完。
- **U-7 `mq887` 的 `LockView` 三条分支**（`2378/2398/2435`）：只确认存在与阈值 `1e-6`，
  三者各跳过哪一步未完全展开。
- **U-8 `mo45` 是否吃 `predict`**：`mo45`（`3609`）只用实时位置，`fx793` 只进伤害估算；
  若原版 FOV 也吃预测则此处不符 —— 未发现证据，按"不吃"记录。
- **U-9 死字段用途**：§0 的 16 个 `static final` 全部无引用，是否曾服务已删分支不可判。
- **U-10 `mv194`**（`5569`）：私有方法、0 调用点（死代码）；按代码读是"按主轴把方向离散成
  Direction"，但无法确认服务过哪个分支。
- **U-11 显示名**：`@Text` 中英文标签仍是混淆串，未解（不影响行为）。

---

## 附录：关键行号速查

| 内容 | 行号 |
| --- | --- |
| `@ModuleInfo` / 字段区 / 设置注册 / 可见性 | 63-73 / 74-141 / 1060-1078 / 1079-1094 |
| 16 个设置声明 | 277, 400, 401, 434, 489, 554, 603, 660, 749, 766, 835, 888, 901, 942, 976, 1022 |
| `onPrePlace` / `mb871` / `mx164` | 5626-5668 / 5421-5560 / 1344-1457 |
| `mn196`（规划器） | 4591-5345 |
| `mu347` / `ml535` / `mn392` / `mq130` | 1461-2243 / 2702-2997 / 3054-3162 / 3945-4540 |
| `my134` / `mq887` / `mw116` / `mo315` | 3798 / 2353 / 3857 / 3882 |
| `ma636` / `mh61` / `mo45` / `mk776` | 4541 / 3165 / 3609 / 4561 |
| `mw522` / `mh644` / `mr41` / `ms9` / `mb747` | 2244 / 2499 / 3556 / 2605 / 3765 |
| `ma792` / `mi41` / `mz450` / `mx938` / `ms591` | 5672 / 3631 / 1171 / 5563 / 5347 |
| `mo738` / `mp702` / `mn254` / `mp605` | 3383 / 2683 / 1125 / 3424 |
| `mr857` / `mt319` / `ml751` / `mr264` / `mi762` | 2556 / 3245 / 3755 / 3007 / 2309 |
| `record c` / `record d` / `enum b` / `enum e` / `record f` | 5711 / 5764 / 5692 / 5823 / 5836 |

核对命令：

```
grep -n "^   \(private\|public\|protected\)" "C:/Users/Jiang/AppData/Local/Temp/kiss-src/bc/ysg/a.java"
grep -n "mx938(a\.e\." "C:/Users/Jiang/AppData/Local/Temp/kiss-src/bc/ysg/a.java"
grep -n "this.fp554 = " "C:/Users/Jiang/AppData/Local/Temp/kiss-src/bc/ysg/a.java"
```
