# KissClient 1.7.3 vs Vape 4.21 — 自瞄（Aura / AimAssist）实现对比（逆向只读）

> 分析方式：Kiss 为反编译混淆源码（只读），Vape 为本仓库源码（只读）。
> 证据引用格式：`文件:行号`。Kiss 路径相对 `C:\Users\Jiang\AppData\Local\Temp\kiss-src\`（下文简写 `kiss/`），Vape 路径相对仓库 `src/main/java/gg/vape/`。
> Kiss 的字符串（设置名、模式名）为 `new String(new char[]{(char)(A^B^…)})` 常量表达式，本文所有名称均由 `build/kiss-tools/deobf.py` 求值还原，数值由 `Double.longBitsToDouble(a^b)` 位运算还原，非猜测。
> 无法从代码/数据表确认的结论一律标注「未发现」，不做推测。

---

## 1 概览

### 1.1 分析对象与规模

| 项 | KissClient 1.7.3 | Vape 4.21 |
|---|---|---|
| 自瞄主体 | `kiss/g/pbd/b.java` KillAura **10988 行**（RISKY）<br>`kiss/ie/c.java` LegitAura **8738 行**（RISKY） | `module/combat/SilentAura.java` **876 行** |
| 辅助自瞄 | `kiss/ur/m/a.java` AutoAim **2466 行**、`kiss/ur/m/c.java` BowAim、`kiss/msb/j/s.java` FlickAim | `module/combat/AimAssist.java` **259 行** + `aimassist/` 4 文件 **1174 行** |
| 旋转引擎 | `kiss/p/ibr/zq/b.java` **1272 行**（KillAura 持有 `fe591`）+ `kiss/bhx/c.java` 81 行二级噪声 | `rotation/` 包 13 文件 **2717 行**（`RotationManager` 942、`AdaptiveRotationController` 504、`FixedRotationController` 226、`MouseRotationController` 226） |
| 封包注入 | `kiss/qs/ysn/d.java` **1996 行** | `RotationManager` 内（`onMotionUpdate` 374-390、`onPacketSend` 221-227） |
| 联动模块 | `kiss/ur/m/b.java` HitSelect 370 行、`kiss/a/je/kd/m.java` AimTimer 376 行 | `module/combat/HitSelect.java` 48 行、`silentaura/SilentAuraClicker.java` 44 行 |

Kiss 的「自瞄」不是单模块，而是**一套旋转状态机 + 三个消费方**：KillAura（自动攻击）、LegitAura（真实点击）、AutoAim（纯锁视角）。三者共享同一批诊断字符串（`HOLD-START`/`EASE-START`/`ATK-GATE`/`HITBOX-GUARD` 等），说明 LegitAura 是 KillAura 的「人工点击版」派生（`kiss/ie/c.java:2826,4877,5985,7264` 与 `kiss/g/pbd/b.java:6301,6135,10510,8378` 完全同名）。

Vape 同样是一套旋转底座 + 两个消费方：`RotationManager`/`AdaptiveRotationController`（旋转底座）、`SilentAura`（静默自瞄）、`AimAssist`（合法辅助）。

### 1.2 一句话结论

- **Kiss**：把力气全花在「**旋转看起来像人**」上——4 条随机曲线族 + Perlin/fBm 噪声 + 三角分布抖动 + **GCD 量化** + 目标切换随机化 + LOS 3 tick 迟滞 + 移动修正；攻击侧用 APS 节流 + 命中箱宽容 + 射线守卫。代价是代码臃肿（10988 行）、每 tick 构造丢弃的调试字符串。
- **Vape**：把力气花在「**结构与可维护性**」上——旋转被抽象成 `MouseRotationController` 家族（像素步进 PID），静默通过 `EventMotion` 旋转覆盖 + 临时改写玩家朝向 + S08 重写实现；参数面大（21 项）、按版本分支（1.7.10 完全不改写出站包）、AimAssist 另有工作线程。曲线简洁（PID + 正弦抖动），没有 Kiss 那种多曲线族随机化。

### 1.3 关键数字（默认值）

| 量 | Kiss（KillAura） | Vape（SilentAura） |
|---|---|---|
| 攻击距离 | Range 3.0（1..6）；Swing Range 6.0（1..8，判定用 ×1.1×1.1） | `3.0 + Extra swing distance(1.0)` = 4.0（`SilentAura.java:847-849`） |
| 视场角 | FOV 180（= ±90°，`g/pbd/b.java:652,660,662` + `7663`） | Max angle 120（= ±60°，`SilentAura.java:333`） |
| 点击速率 | Min APS 8 / Max APS 12（1.9 分支随机，`g/pbd/b.java:596-649`）；1.8 分支按武器攻速 1.6 → 12.5 tick | Attacks per Second 6.0–13.0（`SilentAura.java:332`） |
| 瞄准点 | Aim X/Y/Z = 0 / **0.9** / 0（`g/pbd/b.java:1370,1421,1465`） | Target area = Center（另有 Closest，`SilentAura.java:345`） |
| 平滑 | New Response 12、Feedforward 0.6、Predict 3.0、Deadzone 0.1（`g/pbd/b.java:1591-1790`） | Aim speed 7.0（1..10，`SilentAura.java:110`） |
| 预测 | Predict 1.0（0..5）+ Reference Ping 50 / Limit Ping 200 | 无独立预测开关；PID 用目标位移，AimAssist 用 `predictionFactor = 1.7`（`aimassist/AimAssistRotationSubModule.java:257`） |

---

## 2 功能矩阵

图例：✅ 实现并生效　◐ 部分/受限　✗ 未实现　— 未发现

| 能力 | Kiss 1.7.3 | Vape 4.21 | 证据 |
|---|---|---|---|
| 距离筛选 | ✅ 双重（攻击距离 / 挥击距离 ×1.21） | ✅ 单一（3.0+额外距离） | Kiss `g/pbd/b.java:2546-2574,5925-5960`；Vape `SilentAura.java:265-269,847-849` |
| 角度(FOV)筛选 | ✅ FOV/2 比较，360 视为全向 | ✅ Max angle/2 比较 | Kiss `g/pbd/b.java:7663-7666`；Vape `SilentAura.java:233` |
| 可见性射线 | ✅ `hasLineOfSight` + **3 tick 迟滞** | ◐ 仅当 `Ignore behind walls`（默认关） | Kiss `g/pbd/b.java:6508-6518`；Vape `value/EntityTargetFilterValue.java:80,221` |
| 穿墙攻击 | ✅ 默认（LOS 3 tick 后放行） | ✅ 默认（无 LOS 检查） | 同上 |
| 队伍/好友 | ✅ Team Check + `msb.j.b.isFriend` | ✅ FriendManager | Kiss `g/pbd/b.java:764,5450-5453`；Vape `SilentAura.java:236,224` |
| 无敌帧/死亡 | ✅ hp/死亡判定 + Death Check 开关 | ✅ `hp<=0 \|\| isDead` | Kiss `g/pbd/b.java:2013,5450`；Vape `SilentAura.java:227` |
| 假人/AntiBot | ✅ 独立 AntiBot 模块（7 项设置） | ◐ 仅 `isReservedEntity` | Kiss `kiss/ss/x/ga/a.java:1250-1290`；Vape `EntityTargetFilterValue.java:198` |
| 实体类型开关 | ✅ Players / Animals（含 Villager/IronGolem） | ✅ Players/Mobs/Peaceful/Neutral/Ignore naked/Ignore invisible | Kiss `g/pbd/b.java:671,711,5450-5464`；Vape `EntityTargetFilterValue.java:21-80` |
| 优先级排序 | ✅ Distance/Health/Armor + Switch Speed | ✅ Distance/Yaw/Armor/Threat/Health + Switch | Kiss `g/pbd/b.java:903-1032,1078`；Vape `SilentAura.java:341,549-562` |
| 瞄准点=眼/身/头 | ✅ 近身瞄身 0.5、远距离按 `eyeHeight/bbHeight` 插值，y 夹 0.1–0.95 | ✅ Center（实体中心）/ Closest（贴命中箱最近点） | Kiss `g/pbd/b.java:10818-10847,3078`；Vape `SilentAura.java:593-607` |
| 随机瞄准偏移 | ✅ Aim X/Z ±0.1、Y ±0.06，按距离缩放（`(dist-1.2)/1.6`） | ✅ `Random aim` + 百分比尺寸（默认 30%）+ 100–300ms 重掷 + 插值 | Kiss `g/pbd/b.java:6524-6559,10818-10847`；Vape `SilentAura.java:358-360,646-668` |
| 平滑曲线 | ✅ 4 曲线族随机抽取（smoothstep/easeOutCubic/easeOutExpo/cubic-Bezier/3t²-2t³）+ 双曲线混合 | ✅ 像素步进 PID（P/I 双轴）+ 正弦抖动 | Kiss `p/ibr/zq/b.java:776-820,1157-1205,606-614`；Vape `SilentAura.java:686-730`、`AdaptiveRotationController.java:188-207` |
| 随机抖动 | ✅ 三角分布 + Perlin/fBm 三层叠加 | ✅ 正弦相位抖动（200–500ms 重掷） + 三轴 `SilentAuraAimJitter` | Kiss `p/ibr/zq/b.java:436-460,831-853`；Vape `AdaptiveRotationController.java:188-207`、`silentaura/SilentAuraAimJitter.java:20-40` |
| GCD 一致性 | ✅ 灵敏度→GCD 量化（`round(Δ/gcd)*gcd`） | ✅ 整数鼠标像素步进（`(int)pendingDelta × scale × 0.15`） | Kiss `p/ibr/zq/b.java:203-214,586-596,1263-1266`；Vape `AdaptiveRotationController.java:72-73`、`FixedRotationController.java:143-149` |
| 改本地视角？（合法） | ✅ Attack Mode = `LockView`（默认）时走 AutoAim 写 `setYRot/setXRot` | ✗ 静默为主；AimAssist 走真实鼠标增量 | Kiss `g/pbd/b.java:1249-1359,9342-9345`、`ur/m/a.java:1658-1680`；Vape `aimassist/AimAssistRotationSubModule.java:99-129` |
| 只改出站包？（静默） | ✅ Attack Mode = `Silent` 时重写 `ServerboundMovePlayerPacket` | ✅ `EventMotion` 旋转覆盖 + 临时 `applyManagedRotation` + 1.21.4 重写 S08 | Kiss `qs/ysn/d.java:194-…`、`g/pbd/b.java:9345-9359`；Vape `RotationManager.java:189-202,221-227,374-390` |
| 移动预判 | ✅ ping 缩放外推（位移 ×0.5×clamp(ping,5,200)/50） | ✅ PID 用目标/自身位移特征；AimAssist `1.7×` 直推 | Kiss `g/pbd/b.java:5884-5923`；Vape `SilentAura.java:636-638,757`、`AimAssistRotationSubModule.java:257-259` |
| 击退/受伤适配 | ✅ `hurtTime` 参与门槛；Release/Hold 状态机 | ◐ 仅 `Switch`（目标在冷却时换人） | Kiss `g/pbd/b.java:9322,10510`；Vape `SilentAura.java:326` |
| 自动点击 | ✅ 内建 APS 节流 + `gameMode.attack` 直调 | ✅ `ClickEngine`（RandomClickDelayValue）+ 可选 Perfect swing | Kiss `g/pbd/b.java:10905-10987,8397`；Vape `silentaura/SilentAuraClicker.java:31-37`、`SilentAura.java:278-295` |
| 切目标节流 | ✅ Switch Speed 5.0 + `fe794` 24 tick 静默冷却 | ✅ `readyToAttack` 角差 <3°，换目标清积分 | Kiss `g/pbd/b.java:1078,9352-9359`；Vape `SilentAura.java:566-569,582-585` |
| HitSelect 联动 | ✅ 独立模块（Second / WTap，取消攻击包） | ✅ 独立模块（Right-click use cancel / Normal，Chance 90%） | Kiss `ur/m/b.java:146-181,237-267`；Vape `HitSelect.java:14-39` |
| AI/ML 旋转 | ✅ 推理 `bhx/h.java:181,225,248`（`/aimodels/*.bin`），LegitAura 有 `AI Model` 家族 10 项 | ✅ `Rotation Mode = AI`（`deeplearn/models/MlpModel.java:69`） | Kiss `ie/c.java:708-1213`、`bhx/h.java`；Vape `SilentAura.java:363-374,744-769`；模型二进制同源见 §3.7.1 |
| 工作线程 | ✗ 未发现（全部主线程 tick） | ✅ AimAssist 两条 `Thread`（1ms 轮询） | Vape `aimassist/AimAssistWorkerThread.java:8-18` |

---

## 3 Kiss 实现细节

### 3.1 目标筛选（`kiss/g/pbd/b.java`）

实体白名单在 `my358(LivingEntity)` `5450-5464`：

- Player：先 `msb.j.b.isFriend(var2)`（好友→false），再 `ss.x.ga.a.shouldSkip(var2)`（AntiBot 假人→false），最后返回 `this.players.getValue()`；
- `Animal || Monster` → `this.animals.getValue()`；`AbstractVillager || IronGolem` → 同一 `animals` 开关；
- 其余类型一律 false。
- 因此 Kiss 的实体类型只有 **2 个开关**（Players / Animals），比 Vape 的 6 项少。

距离/角度在 `mq580` `2546-2574`（挥击判定）与 `my967` `10788-10816`（候选筛选）里各写一遍：

```
this.ms692(var1) > this.swingRange^2 * 1.1 * 1.1   → false   // 2546-2558，1.21 倍宽松
Math.abs(Mth.wrapDegrees(yawTarget - mc.player.getYRot())) > FOV/2 → false
```

`mt843(float,double)` `7663-7666`：`fov >= 360.0 || angle <= fov / 2.0`，即 FOV 语义是**总视场**，默认 180 → ±90°。

**可见性（穿墙）**在 `ml377(Entity)` `6508-6518`：

```java
if (mc.player.hasLineOfSight(var1)) { this.fi527 = 0; return true; }
this.fi527++;
return this.fi527 >= 3;          // 连续 3 tick 无视线后反而放行
```

这是一个**刻意设计的 3 tick 视觉迟滞**：目标刚被墙挡住时继续瞄/打，超过 3 tick 才放弃。等价于「短暂穿墙」而无需独立 NoWall 开关，且不会像纯穿墙一样长期锁墙后目标（对 Grim/Verus 的 `EntityReach`/`Post` 检查更温和）。

假人处理在 `kiss/ss/x/ga/a.java:1250-1290`（按 tick 缓存结果），设置项 `Spawn Window`(137)、`New Player Timeout`=100(214)、`Static Ticks`(288)、`Tab Check`(337)、`Score Check`(363)、`Format Name Check`(407)、`Naked Ticks`=20(484)——比 Vape 的单一 `isReservedEntity` 强得多。

**优先级**：`mi322()` `10853-10874` 先用攻击距离收候选，空则回落到挥击距离；`mt914(Comparator)` `3054-3076` 取列表 sort 后第 0 个；排序器由 `Target Priority`（`903`，选项 Distance/Health/Armor）与 `Mode`（`791`：Single / Single Switch，`835`）决定；`Switch Speed` 默认 5.0（`1078,1091,1093`）。

### 3.2 瞄准点

`mv794(Entity)` `10818-10847` 是唯一瞄准点生成器：

```java
var4 = clamp((dist - 1.2) / 1.6, 0, 1);                      // 距离归一化
var5 = lerp(var4, 0.5, mg555(target).y) + ft82 * var4;       // 近身 0.5(身)，远处按眼高比例
offset = new Vec3(fz945 * var4, clamp(var5, 0.1, 0.95), fa523 * var4);  // fz945/fa523 ∈ ±0.1
fr540 = fr540.lerp(fd402, 0.3);                              // 0.3 平滑，避免逐 tick 跳动
point = (x + off.x*bbWidth, y + off.y*bbHeight, z + off.z*bbWidth)
→ mr939(point, target)
```

`mg555` `3078-3080` = `(0, clamp(eyeHeight/bbHeight, 0, 1), 0)`。`mr939` `2410-2487` 做**命中箱射线夹取**：从眼睛到该点连线与 AABB 求交，命中则用原点，未命中则回退到 `closestPoint`（贴住箱子表面），保证瞄点永远在可命中的几何体内。

设置项 `Aim X/Y/Z`（`1370/1421/1465`，默认 0 / **0.9** / 0，Y 上限 1.0）是**静态偏移**，与上面的随机偏移叠加。

### 3.3 旋转算法（重点）

Kiss 的旋转核心是 `kiss/p/ibr/zq/b.java`（1272 行），KillAura 字段 `private final p.ibr.zq.b fe591;`（`g/pbd/b.java:80-84`），每 tick 由 `ml502(Entity)` `7681-7740` 驱动：

```java
var7  = atan2(dz,dx)*180/π - 90;  var8 = -(atan2(dy,horiz)*180/π);      // 目标角
var11 = wrapDegrees(var7 - fe591.yaw());   var12 = wrapDegrees(var8 - fe591.pitch());
fe591.updateJitter(var1, var13);                                        // 7721
fg28.update(mp759(var7,var8), 1.0F, 10.0F);                             // 7722 二级噪声
var14 = yaw + Δyaw + jitterYaw() + fg28.getYaw();                       // 7723
var15 = clamp(pitch + Δpitch + jitterPitch() + fg28.getPitch(), -90, 90);// 7724
fe591.apply(var14, var15, var1, var13);                                 // 7730
```

`apply()`（`854-1271`）内部按一个字符串模式分 5~6 条曲线分支，且**分支在运行时随机选择**：

- `1115-1127`：`this.fc487 = var43.nextInt(4)` 选曲线号；`this.fd507 = rand<0.35 ? rand : 0` 决定是否与第二条曲线混合；`fu436` 是第二条曲线号（避开第一条）。
- `1142`：`var35 = this.mt847(this.fy99)` → `mt847` `606-614` 用 `mr132(fc487,t)` 与 `mr132(fu436,t)` 按 `fd507` 混合（EMA）。
- `mr132` `813-820`：`case 0 → mt937`（easeInOutCubic）、`case 1 → my656`（easeOutCubic）、`case 2 → mu103`（easeOutExpo，`1 - 2^(-10t)`）、`default → t*t*(3-2t)`（3t²-2t³）。
- `1157-1181`：**三次贝塞尔**分支，`mg932(p0,p1,p2,p3,t)`（`563-585`，Bezier 基函数常数 3.0）用 `mc607`（`370-404`）产出的两个随机中间控制点：
  ```java
  var8 = 0.65 + rand*0.7;  var9 = 1 - (0.6 + rand*0.6);
  var13 = 0.12 * min(len, 90) * clamp(len/12, 0, 1);   // 抖动幅度 ∝ 角差
  ```
  即**每次转向的弧线路径都是从命中箱内随机采样的样条**，不是固定插值；`var21 = this.mq859(fy3)` 是自研的 S 形时间映射。
- `1182-1189`：线性限速分支（步长 ∝ 角差）。
- `1190-1205`：**fBm 噪声控速**分支，`mg791(f)` `831-853` 做 3 octave 分形 Perlin（`mw998` `225-232`，`mg986` `518-525` 用 4096 周期置换表 + `x²(3-2x)` fade），输出 `1 + amp*noise + random`。
- `1253-1260`：抖动分支，`serverYaw += (rand-0.5)*2*amp`。

抖动参数在 `updateJitter(Entity,double)` `436-460`：

```java
var6 = 0.15F * (1 + 0.75F*d*d);                 // d = 距离/range
fz783 = mc763(rand, var6);  fw648 = mc763(rand, var6*0.65);
fq999 += (fz783 - fq999) * 0.25F;               // EMA 平滑
```

`mc763(rand,amp)` `406-412` = `(rand+rand-1)*amp` → **三角分布**（比均匀分布更像手抖）。重掷周期 `nextInt(3,8)` tick（`436-442`）。

**GCD 量化**（`203-214` + `586-596` + `1263-1266`）：

```java
mu379(): f = sens*0.6 + 0.2;  return f*f*f*8.0*0.15;      // 标准 MC 灵敏度→度/像素
mv194(v, gcd): return Math.round(v/gcd)*gcd;
// apply() 末尾：
serverYaw   = var11 + mv194(serverYaw   - var11, gcd);    // var11 = 上一 tick 的 serverYaw
serverPitch = var12 + mv194(serverPitch - var12, gcd);
```

即**只有增量被量化到 GCD 网格**，绝对值保留 → 出站 yaw/pitch 与真实鼠标的量化格完全一致（Grim 的灵敏度/量化检查、以及 `rotationDelta` 统计都看不出规律）。这是 Kiss 最硬的检测面工程。

另有 `setSpeedOverride(60F|85F)`（`g/pbd/b.java:10117`，随是否移动切换）与 `anchorYawToPlayer()`（`348-368`，把 server yaw 锚回玩家当前朝向，避免移动修正抖动）。

### 3.4 静默 vs 合法

`Attack Mode`（`g/pbd/b.java:1249`，默认 **LockView**，选项 `LockView`/`Silent`）：

- `9342`：`ur.m.a.INSTANCE.lockViewExternal = this.attackMode.is("LockView")` —— LockView 时把「锁视角」职责交给 AutoAim；
- `9345-9351`：`if (attackMode.is("LockView")) { ur.m.h.reset(); qs.ysn.d.getInstance().clear(); mt521(); }`；
- `9352-9359`：Silent 分支在移动/输入中/刚发位置包时丢弃目标，并置 `fe794 = tickCount + 24`（24 tick 重入冷却）。

静默通道是 `kiss/qs/ysn/d.java`（1996 行单例）：

- `me759()` `126-135`：每 tick 一次性闩锁；
- `applyRotation(Packet)` `194-…`：只处理 `ServerboundMovePlayerPacket`，用 `resolveTickMoveYaw()`（`155-166`，本 tick 缓存）重写 yaw/pitch —— 这是**注入点**；
- `release(Object)` `137-147`：清 `ur.m.h.setTargetRotation(null)/rotationOwner/isRotating`。

合法通道：`ur/m/a.java` `lockViewAim(...)` `1615-1637` → `mu936` `1658-1680`：

```java
mc.player.setYRot(getYRot() + wrapDegrees(a.yaw - getYRot()));  // 顺滑切到目标
mc.player.setXRot(clamp(a.pitch, -90, 90));
if (!重入) { 同时写 yRotO/xRotO }                                 // 消第三人称抖动
```

`onRender2D` 里 `else if (!this.lockViewExternal)`（`1727`）才跑自己的锁视角，避免与 KillAura 抢。

### 3.5 预测

`mu31(LivingEntity)` `5884-5923`：

```java
if (predict <= 0) return 原始距离;
scale = Mth.clamp(ping, 5.0, 200.0) / 50.0;      // ping 由 mk960(uuid) 取，Reference Ping 50/Limit Ping 200
future = pos + (pos - prevPos) * scale * 0.5;     // 目标与自身都外推
return Math.max(0, dist - r1 - r2);               // 命中箱半径之和抵扣
```

`md581` `3033-3035`：`hitableCheck ? mu31(预测距离) : mk863x(原始距离)`，即 `HitAble Check` 开关切换两套距离语义。

### 3.6 攻击联动

`ms739()` `10905-10987` 是唯一节流器：

- `Version == "1.8"`：`ms81()`（`2194`，Method=Sword 返回 **1.6** 攻击速度）→ 间隔 `max(1, 20/1.6)` = 12.5 tick；若 `ur.vz.c.earlyHitActive()` 取更短间隔；
- 否则（1.9+）：`target = MinAPS + rand*(MaxAPS-MinAPS)`，`interval = max(1, 20/target)`；
- 累加器 `fb69`，`fu221 = ceil(fb69)`，落后则重置。

实际出手只有一处：`mc.gameMode.attack(mc.player, var1)` `8397`。前置守卫 `ATK-GATE`（`10510`）检查 `moving/angleErr/rayHit/inSwing/inRange/pkgBlock/canAtk/hurtT`；`HITBOX-GUARD`（`8378`）在射线未命中时跳过；`mg448` `7745-7763` 用 `box.inflate(0.031 + 3.0)` + `attackRange + 3.0 + 1.0` 的宽松距离复核，异常返回 true（宁可打）。

HitSelect `kiss/ur/m/b.java`（独立 RISKY 模块）：默认 `Second`（`60,81,141`），在 `onPacketSend` 拦 `ServerboundAttackPacket`（`146-181`），`mp326` `237-267` 只在「对方未受伤 && 自己已受伤 && 贴近 && 正对」时取消这一击（抢第二刀）；`WTap` 模式 `mo496` `333-369` 用 `setSprinting(true)` 重置冲刺（`214-235`），`mq129`(312-331) 事后恢复。

### 3.7 LegitAura 的 AI 旋转（`kiss/ie/c.java`）

LegitAura 是唯一**字段名未混淆**的模块（`moveFix`/`range`/`minCps`/`maxCps`/`fov`/`players`/`animals`/`teamCheck`/`mode`/`targetPriority`/`switchSpeed`/`version`/`method`/`attackMode`），并多出一整族 `AI` 设置：

| 行号 | 名称 | 默认（字面量） |
|---|---|---|
| 708 | `AI Model` | 模式选择 |
| 863 | `AI Yaw Multiplier` | 1.5 |
| 909 | `AI Pitch Multiplier` | 0.5 |
| 970 | `AI Blend Angle` | 45 |
| 1015 | `AI Acquisition Boost` | 3.0 |
| 1061 | `AI Yaw Only` | boolean |
| 1087 | `AI Carry Pixels` | boolean |
| 1139 | `AI Integral Gain` | 0.0 |
| 1184 | `AI Max Step` | 90 |
| 1213 | `AI Correction` | 模式选择 |

（这组设置字面量顺序不一致，min/max 未逐项确认。）`AI Carry Pixels` 与 AutoAim 的 `fp166`（`ur/m/a.java:1682-1707`，`var9 = cur.yaw + (var8 + fp166)/var6`）语义一致：**把不足一个鼠标像素的角度余量留存到下一 tick**，避免小角差永远收敛不了（典型 Verus 小角度检查）。

LegitAura 的点击链路留有中文诊断（未混淆）：`REALCLICK skip：准星不在目标身上（等瞄准收敛），本 tick 不出手`(7546)、`REALCLICK skip：充能未达 0.9，此击拿不到冲刺加成`(7794)、`REALCLICK：玩家正物理按住左键，交由他的真实点击完成`(7965)。→ **LegitAura 不用 `gameMode.attack`，而是驱动真实左键**（与 KillAura 的 `8397` 形成明确分工）。

#### 3.7.1 AI 模型资源与 Vape 同源（本次核对的新发现）

Kiss 的 MLP 推理实现在 `kiss/bhx/h.java`（307 行）：`predictSafe(String,float[])` `181` → `load()` `187` 从 classpath `/aimodels/<name>.bin` 反序列化，`predict()` `225` 先校验 6 维输入，再走 3 层全连接（`mv625` 线性层 / `me589` 激活 / `mh937`）。

| 文件 | Kiss（jar 内） | Vape（`src/main/resources`） | sha256 |
|---|---|---|---|
| 19kc8kp.bin | `aimodels/19kc8kp.bin`，49,254 B | `liquidbounce/models/19kc8kp.bin`，49,254 B | `b0e79df3c7c36b0186346b3645a26549df8ad17812b4f35f513191f6fa559dc5` |
| 21kc11kp.bin | `aimodels/21kc11kp.bin`，49,254 B | `liquidbounce/models/21kc11kp.bin`，49,254 B | `1edfcbcf11dd1b5a55422314eb01a3f7918d410cc96223a103500b3607a3f2eb` |

三个哈希逐字节相同 → **双方用的是同一份公开 LiquidBounceNG 战斗回归模型**，AI 层没有技术代差；差异只在接线与参数：Kiss 在 LegitAura 暴露 10 项 AI 旋钮（含 `AI Carry Pixels`、`AI Integral Gain`、`AI Max Step`），Vape 只暴露模型选择 + `AI yaw/pitch multiplier`。

### 3.8 性能

- 全 tick 主线程，未发现任何 worker thread。
- 但 `g/pbd/b.java:7369-7598`：`onPacketSend` 处理 `ServerboundMovePlayerPacket` 时，**每 10 tick 用 6 次 `String.format` + 数十个 `char[]` 拼接**构造 `SIM-DIAG ...` 诊断串，然后交给 `mn65(String)` —— 而 `mn65` `7742-7743` 是**空方法**。也就是说这份格式化开销在生产包里 100% 被浪费。
- 更重的是 `apply()` 里大量 `new String(char[]{(char)(a^b^c^d)})` 常量拼接（如 `1157-1205` 每个 case 都现场拼字符串再 `switch(var13)/equals()`），每 tick 多次分配。这是反混淆器留下的原样结构（原始代码应为字符串常量 + switch），但仍构成实际开销。

---

## 4 Vape 实现细节

### 4.1 目标筛选（`module/combat/SilentAura.java`）

`isValidTarget(EntityLivingBase)` `220-263` 依次检查：非空 → 非自己 → `hp > 0 && !isDead` → `isInRange`（到瞄准点的距离 ≤ `3.0 + extraSwingDistance`）→ `RotationUtil.a(player,target) > maxAngle/2` → 好友 → 坐骑 → `Shield check`（举盾则跳过，除非 ShieldBreaker/HitSwap/AutoMace 能处理，`242-261`）→ `passesItemFilter`（`Limit to items` + 白名单）。

**没有 LOS 检查**；可见性完全交给 `EntityTargetFilterValue` 的 `Ignore behind walls`（`value/EntityTargetFilterValue.java:80`，默认 **false**，`221` 用 `canEntityBeSeen`）→ 默认行为是**穿墙锁人**（比 Kiss 的 3 tick 迟滞更激进）。

同文件另有一层筛选：`194-229` 的类型（`zm` = EntityLivingBase，1.7.10 排除 `FT`）、`isReservedEntity`(198)、`Peaceful`(201)、`Ignore invisible`(218)、好友(224)、`Mobs/Peaceful/Neutral` 分类(227-229)。

排序 `549-562`：`Target mode`（`341`，默认 **Distance**，可选 Yaw/Armor/Threat/Health）分别对应 `EntityAngleComparator`/`EntityDistanceComparator`/`EntityArmorValueComparator`/`EntityEquipmentValueComparator`/`EntityHealthComparator`；`Switch`（`326`）打开时再用 `SilentAuraEntityIdComparator`（`silentaura/SilentAuraEntityIdComparator.java`，13 行）。

`updateTarget` `533-591` 每次重排都重新取第 0 个；目标变化时 `randomizeGainFactors()`（`271-276`）：

```java
pitchProportionalScale = 0.85 + rand*0.3;
pitchIntegralScale     = 0.85 + rand*0.3;
yawProportionalScale   = 0.8  + rand*0.4;
yawIntegralScale       = 0.85 + rand*0.3;
```

即**每个新目标的 PID 增益都不同**——与 Kiss 的「每目标重掷抖动参数」（`g/pbd/b.java:6524-6559`）是同一思路。

### 4.2 瞄准点（`SilentAura.java:593-668`）

`computeAimCoords` `593-607`：`Center` → `(x, y+N(), z)` 附上一 tick 位置；`Closest` → `RotationUtil.T(player, bb, 0,0,0)` 贴命中箱最近点，再用目标位移反推上一 tick 点。

抖动/偏移 `636-668`：

```java
jitteredX = targetX + xJitter.offset * (1 + horizontalTargetMotion);   // xJitter ∈ ±0.15
jitteredZ = targetZ + zJitter.offset * (1 + horizontalTargetMotion);   // zJitter ∈ ±0.15
y = playerEyeY < targetY ? targetY + pitchJitter*0.5
                         : min(playerEyeY, targetY + height) - 0.275 + pitchJitter;  // pitchJitter ∈ [-0.3,0.25]
```

注意 `-0.275`：当目标比自己低时瞄的是「头顶下方」（头颈），高于自己时瞄身体上沿 —— 与 Kiss 的「按眼高比例插值」思路一致，但更简单、更贴命中箱。

`Random aim`（`358`，默认 false）：以**玩家自身包围盒**为基准（`647-654`）取 `randomSize%`（默认 30%）的一半作为最大偏移，100–300ms 重掷目标偏移（`655-660`），用 `randomAimSpeed*0.08` 插值逼近（`661-664`）。

### 4.3 旋转算法

两条路径，由 `Rotation Mode`（`368`）选择：

**（a）PID（默认）** `686-730`：

```java
desired = controller.calculateRotation(target);            // 角度解算
yawError = wrap(desired.yaw - managedYaw);  pitchError = wrap(targetPitch - managedPitch);
prevYawStep = managedYaw - prevManagedYaw;  prevPitchStep = managedPitch - prevManagedPitch;
integrationStep = 0.05f;
pitchP = 0.45 * pitchProportionalScale;  pitchI = 0.91 * pitchIntegralScale;
yawP   = (onTarget ? 0.05 : 0.1) * yawProportionalScale;  yawI = 0.33 * yawIntegralScale;
if (垂直速度 > 0.1)        pitchError *= 1 + rand*0.32;
if (同向 && |yawError| < 20) { yawP *= 2.5; prevYawStep *= 1 + min(motionSum, 0.25); }
if (dist < 0.8)            { pitchError *= (d/0.8)^2; yawError *= d/0.8; }
pitchControlError = pitchError - prevPitchStep + prevYawStep*0.05*±1;   // 交叉耦合 + 随机符号
yawControlError   = yawError - prevYawStep;
integrals += controlError * 0.05;
setTargetRotation(managedYaw + yawError + yawAdjustment/3, managedPitch + pitchAdjustment);
if (|yawError| > 120) { yawIntegral = 0; yawAdjustment = 0; }           // 大跳变时开环
```

特点：P+I 双轴、**交叉耦合**（pitch 控制误差里掺 yaw 步长）、大角差降级为「直接怼」、近距离按平方衰减 pitch。

**（b）AI** `744-769`：`ModelManager.predictSafe` 输入 6 维 `[deltaYaw, deltaPitch, velDeltaYaw, velDeltaPitch, speedFeature, distanceFeature]`，输出 yaw/pitch 增量 × `AI yaw/pitch multiplier`，再 `applyAiRotationDelta`；异常/NaN 时回退固定 `managedYaw/managedPitch`。模型为 `21KC11KP` / `19KC8KP` 两个（`363-365`，描述写 "Bundled LiquidBounce combat regression model"）。

**像素级落点**在 `rotation/` 里完成（这是 Vape 相对 Kiss 更「工程化」的部分）：

- `AdaptiveRotationController.updatePitch()` `58-92`：`pendingPitchDelta`（int）→ `(int)pendingPitchDelta * mouseScale * 0.15f`；
- `mouseScale` 来自灵敏度：`FixedRotationController.java:143-149`
  ```java
  float sensitivityBase = RotationManager.INSTANCE.getMouseSensitivity() * 0.6f + 0.2f;
  float rotationPerStep  = this.getMouseScale() * 0.15f;
  return Math.round(absoluteError / rotationPerStep) > max(round(tolerance/rotationPerStep), 0);
  ```
- 即**旋转量始终是「整数鼠标像素 × 每像素角度」**，与真实鼠标输入同构（等效 GCD 一致）。
- 更新频率：`RotationManager.onTick` `795-800` 每 tick 跑 `round(50 * sensitivityTimeScale)` 次控制器迭代；`getSensitivityTimeScale()` `529-535` = `referenceScale/currentScale`（referenceBase = 0.5），即低灵敏度时每 tick 迭代更多次（最多约 100 次），高灵敏度时更少 —— 保证「同一 tick 内的像素步数恒定」，手感与检测特征都稳定。
- 抖动 `AdaptiveRotationController.updateJitter()` `188-207`：200–500 ms 重掷 `yawJitterSpeed 0.05–0.2`、`pitchJitterSpeed 0.04–0.16`、`yawJitterAmplitude 0.15–0.4`、`pitchJitterAmplitude 0.1–0.3`，正弦相位推进。

### 4.4 静默 vs 合法

静默（`SilentAura`）：

- `RotationManager.onMotionUpdate` `374-390`：`EventMotion.setRotationYaw/Pitch(managedYaw/managedPitch)` —— 旋转只进「运动事件」，由位置/朝向包写出，**不改相机**；
- `RotationManager.applyManagedRotation(true/false)` `189-202`：需要在 `attackEntity` / 交互时临时把 `player.setYRot/setXRot` 改成目标角，动作完立刻还原；
- `onPacketSend` `221-227`：仅 1.21.4 且使用自适应控制器时，重写 `S08PacketPlayerPosLook` 的 yaw/pitch（防止服务端回正）；
- 第三人称显示用 `applyRenderRotation` `164-177` + `renderBodyYaw`（`122-137`），只改渲染不改逻辑；
- `SilentAura.java:776-784` 注释明确：**1.7.10 不改写出站包**（会触发服务端 S08 回正并 NPE），改为 `player.swingItem()` + `controller.attackEntity(player, target)` 直接打目标（`417-445` 的 `onSyntheticAttack`），`aimSpeed` 在 1.7.10 直接隐藏（`378-380`）。

合法（`AimAssist`）：走**真实鼠标增量** `PlayerMouseRotationApplier.applyTrackedMouseDelta(yawDelta, pitchDelta)`（`aimassist/AimAssistRotationSubModule.java:124`），且 `*0.15` 换算与 MC 自身一致（`rotation/PlayerMouseRotationApplier.java:51-66`）。`AimAssist` 不会改出站包，只推动相机。

### 4.5 预测

- `SilentAura`：PID 用 `targetMotionX/Z`（`636-637`）与玩家速度（`696`）作为特征，但**不做时间外推**（瞄准点就是目标当前位置）；`Closest` 模式用「上一 tick 最近点」差分（`596-604`）。
- `AimAssist`：`predictionFactor = 1.7`（`aimassist/AimAssistRotationSubModule.java:257-259`），把目标水平位移外推 1.7 帧；`proximityBoost = max(0, (9 - dist)/2.5 - 2)`（`275`）；角差变化 >6° 时额外加速度（`272-274`）。
- 无 ping/延迟补偿（未发现类似 Kiss `Reference Ping`/`Limit Ping` 的参数）。

### 4.6 攻击联动

`SilentAuraClicker extends ClickerMod`（`silentaura/SilentAuraClicker.java`）：内部建 `ClickEngine(LEFT, attackRate, limitToItems, allowedItems, requireMouseDown, …)`，`isClickCycleBlocked()` 返回 `!silentAura.canClickAttack()`；`shouldSimulateBlockHit` 交给 `BlockHit` 模块。

`SilentAura.canAttack()` `397-411` = 世界非空 + `isAttackCooldownReady()` + `readyToAttack` + 无 GUI + 不在挖方块；`isAttackCooldownReady()` `278-295`：`Perfect swing` 且 1.12.2 时用 `getCooledAttackStrength(0)==1.0`（并对 mace/剑特判），否则 `attackRate.hasClickDelayElapsed()`。

`readyToAttack` 的门槛是**角差 < 3°**（`582-585`，Perfect swing 时改为「扩展射线已命中目标」，`576-580`）—— 这是「先收敛再出手」的核心，也是与 Kiss `ATK-GATE` 等价的设计。

`HitSelect`（`module/combat/HitSelect.java`）48 行：`Mode`（`Right-click use cancel` / `Normal` 冲刺重置）+ `Chance` 默认 **90%**（`34`）；触发判定 `shouldTrigger()` `37-39` 用 `Math.random()*100` 比较。

### 4.7 性能与线程

- `SilentAura`/`RotationManager`：全主线程；但每 tick 的控制器迭代是 `round(50*timeScale)` 次（`795-800`），比 Kiss 的「每 tick 一次 apply()」更贵，换来的是像素级平滑。
- `AimAssist` 有两条常驻线程：`aimassist/AimAssistWorkerThread.java:8-18` 与 `aimassist/AimAssistRotationWorkerThread.java:13-24`，都是 `while (!Vape.INSTANCE.isEnabled()) { sleep(1); if (selected) runWorkerTick(); }` —— 1 ms 轮询，把目标选择/旋转计算搬出主线程（`AimAssistRotationSubModule.java:131-133`），主线程只做 `onPreRenderTick` 的像素落地（`99-129`）。
- 无 Kiss 那种「构造即丢弃」的字符串开销。

---

## 5 流程对照

| 阶段 | Kiss（KillAura） | Vape（SilentAura） |
|---|---|---|
| 1 入口 | tick 事件 → `mk586()` 巨型状态机 `g/pbd/b.java:3145-5431` | `EventPreTick` → `onTick` `SilentAura.java:464-526` |
| 2 候选 | `mf275(distance)` → `mi322()`（攻击距离空则回落挥击距离）`10853-10874` | `updateTarget()` 遍历 `world.z()` `533-548` |
| 3 过滤 | 好友 → AntiBot → 类型开关 → 距离×1.21 → FOV/2 → LOS(3 tick) | 类型/无敌/隐身/墙 → hp → 距离 → FOV/2 → 好友 → 盾 → 物品 |
| 4 排序 | `Target Priority` 比较器 + `Switch Speed` | `Target mode` 比较器 + 可选 `EntityIdComparator` |
| 5 瞄准点 | `mv794`（距离插值 + ±0.1 随机）→ `mr939` 射线夹取 | `computeAimCoords`（Center/Closest）→ 三轴 jitter → 可选 Random aim |
| 6 角度 | `ml502` 算目标角 → `fe591.apply()`（随机曲线族 + 噪声 + GCD） | PID/AI → `setTargetRotation` → `AdaptiveRotationController` 像素步进 |
| 7 出口 | `Silent`: 重写 `ServerboundMovePlayerPacket`（`qs/ysn/d.java:194`）<br>`LockView`: AutoAim `setYRot/setXRot` | `EventMotion` 旋转覆盖（`RotationManager.java:374-390`）+ 动作期临时 `applyManagedRotation` |
| 8 出手 | `ms739` APS 节流 → ATK-GATE/HITBOX-GUARD → `gameMode.attack` `8397` | `ClickEngine`（CPS）→ `canAttack` + 角差<3° → 左键点击 |
| 9 释放 | IDLE/ACQUIRE/HOLD/EASE/RELEASE 状态机 + 24 tick 冷却 | `resetTargeting()`：清积分、释放控制器与 claim `826-845` |

---

## 6 参数对照

Kiss 名称来自 deobf 还原；默认值/范围来自位运算还原（`Double.longBitsToDouble` / `Float.intBitsToFloat`）。

| Kiss 设置（行号） | 默认 / 范围 | Vape 对应（行号） | 默认 / 范围 |
|---|---|---|---|
| `Range`(322) | 3.0 / 1.0–6.0 | `Distance` 由攻击距离隐式决定 | 3.0 + `Extra swing distance` |
| `Extra swing distance`(—) | — | `SilentAura.java:119` | 1.0 / 0.0–3.0 |
| `Swing Range`(228) | 6.0 / 1.0–8.0 | — | 未发现 |
| `FOV`(652) | 180 / 1–360 | `Max angle`(333) | 120 / 1–360 |
| `Predict`(421) | 1.0 / 0.0–5.0 | （无；AimAssist `1.7` 硬编码） | — |
| `Reference Ping`(504) | 50 / 0–1000 | — | 未发现 |
| `Limit Ping`(560) | 200 / 0–1000 | — | 未发现 |
| `Min APS`(596) / `Max APS`(635) | 8 / 12，上限 20 | `Attacks per Second`(332) | 6–13，允许 1–20 |
| `HitAble Check`(398) | boolean | （预测距离始终启用） | — |
| `Mode`(791,812,835) | Single / Single Switch | `Switch`(326) | false |
| `Target Priority`(903) | Distance/Health/Armor | `Target mode`(341) | Distance/Yaw/Armor/Threat/Health |
| `Switch Speed`(1078) | 5.0 / 0–10 | （无节流参数） | — |
| `Version`(1116) | 1.8 / 1.9 | 由 `ForgeVersion` 自动 | — |
| `Method`(1170) | Sword… | — | 未发现 |
| `Attack Mode`(1249) | **LockView** / Silent | `Rotation Mode`(368) | PID / AI（另一维度） |
| `Aim X/Y/Z`(1370/1421/1465) | 0 / **0.9** / 0 | `Target area`(345) + jitter 区间 | Center；±0.15 / ±0.15 / [-0.3,0.25] |
| `Optimal Lock`(1550) | true | — | 未发现 |
| `New Response`(1591) | 12 / 1–50 | `Aim speed`(110) | 7.0 / 1–10 |
| `New Feedforward`(1637) | 0.6 / 0–1 | （PID 的 I 项等价） | 0.91 / 0.33 |
| `New Predict`(1701) | 3.0 / ≥1 | （无） | — |
| `New Deadzone`(1788) | 0.1 / 0–2.0 | 就绪阈值角差 | 3.0°（硬编码） |
| `BlockHit`(1857) | Off / Fake / Risky | `BlockHit` 模块（独立） | — |
| `Chance`(1936) | 70 / 0–100 | `HitSelect.Chance` | 90 / 0–100 |
| `BlockHit Count`(1987) | 3 / 1–20 | — | 未发现 |
| `Death Check`(2013) | boolean | `Disable on death`(324) | false |
| `Players`(671) / `Animals`(711) | boolean | `Players`(filter) / `Mobs` | true / false |
| `Team Check`(764) | boolean | FriendManager（无队伍检查） | — |
| `Target Info`(1811) | boolean | `Show target`(325) | false |
| — | — | `Shield check`(350) | false |
| — | — | `Perfect swing`(349) | false |
| — | — | `Random aim`(358) + `Random size`(359) + `Random aim speed`(360) | false / 30% / 2.0 |
| — | — | `AI Model`(365) / `AI yaw multiplier`(369) / `AI pitch multiplier`(370) | 21KC11KP / 1.5 / 1.0 |
| `Move Fix`(301) | true | （由 `RotationManager.adjustMovementYaw` 统一处理） | — |

LegitAura 额外参数（`kiss/ie/c.java`）：`Min CPS` 8（216）、`Max CPS` 12（237）、`FOV` 180（258）、`Switch Speed` 5（502）、`Aim Y` 0.9（1362）、`New Response` 12（1433）、`New Feedforward` 0.6（1478）、`New Predict` 3.0（1522）、`New Deadzone` 0.1（1566）、`AI Yaw Multiplier` 1.5（863）、`AI Max Step` 90（1184）、`AI Integral Gain` 0.0（1139）、`FakeBlock`（1647）。与 KillAura 相比**没有 `Method`/`BlockHit`/`Chance`**，多出 `FakeBlock` 与 AI 家族。

AimAssist 参数（`module/combat/AimAssist.java:169-196`）：`Require mouse down` **true**、`Aim vertically` false、`Vertical speed` 5（1–10）、`Horizontal speed` 5（1–10）、`Max angle` 180（1–360）、`Distance` 5（1–8）、`Target mode` **Yaw**、`Target area` **Center**、`Strafe increase` false、`Check block break` false；另有 `AimAssistTargetingSubModule`（Adaptive，748 行）作为第二模式。

---

## 7 检测面与绕过

| 检测点 | Kiss | Vape | 证据 |
|---|---|---|---|
| GCD/灵敏度量化 | ✅ 增量 `round(Δ/gcd)*gcd`，`gcd = (sens*0.6+0.2)³*8*0.15` | ✅ 「整数像素 × mouseScale × 0.15」天然落在 GCD 网格 | Kiss `p/ibr/zq/b.java:203-214,586-596,1263-1266`；Vape `AdaptiveRotationController.java:72-73`、`FixedRotationController.java:143-149` |
| 角度随机化 | ✅ 三层（三角分布 0.15·(1+0.75d²)、Perlin、fBm），3–8 tick 重掷 | ✅ 正弦抖动 + 三轴 jitter + 每目标随机 PID 增益 | Kiss `p/ibr/zq/b.java:436-460,831-853`；Vape `AdaptiveRotationController.java:188-207`、`SilentAura.java:271-276` |
| 曲线自然度 | ✅ 4 曲线族随机 + 贝塞尔随机控制点（每次转向路径不同） | ◐ 单一 PID + 加速度调制，周期性特征更明显 | Kiss `p/ibr/zq/b.java:1115-1205,370-404`；Vape `SilentAura.java:686-730` |
| 旋转速率上限 | ✅ `setSpeedOverride(60/85)` + 分轴限速 | ✅ 像素步数上限 + `tolerance` + 大角差开环 | Kiss `g/pbd/b.java:10117`、`p/ibr/zq/b.java:1137-1141`；Vape `AdaptiveRotationController.java:58-92`、`SilentAura.java:721-724` |
| 移动修正 | ✅ `Move Fix`（`301`）+ `anchorYawToPlayer()` 把 server yaw 锚回移动朝向 | ✅ `adjustMovementYawFromBindings` 按键重映射 + `movementKeysRemapped` | Kiss `g/pbd/b.java:301`、`p/ibr/zq/b.java:348-368`；Vape `RotationManager.java:109-120,147-150` |
| 穿墙/超距 | ✅ LOS 3 tick 迟滞后放行；命中箱 `inflate(0.031+3.0)` 宽松复核 | ✅ 默认无 LOS（`Ignore behind walls` 关）；射线用扩展 reach | Kiss `g/pbd/b.java:6508-6518,7745-7763`；Vape `EntityTargetFilterValue.java:221`、`RotationManager.java:400-411` |
| 视角-封包一致性 | ✅ Silent 时只改包、相机不动；LockView 时相机与人同动 | ✅ `EventMotion` 覆盖 + 动作期临时改写（`applyManagedRotation`） | Kiss `qs/ysn/d.java:194`、`g/pbd/b.java:9342-9345`；Vape `RotationManager.java:189-202,374-390` |
| S08 回正处理 | ✅ `applyRotation` 里对「自己刚放行的包」直接跳过并记 tick（`fp820`） | ✅ 1.21.4 主动重写 S08 的 yaw/pitch | Kiss `qs/ysn/d.java:194-…`；Vape `RotationManager.java:221-227` |
| 1.7.10 特化 | — 未发现（Kiss 为 26.x Fabric） | ✅ 完全不改出站包，改 `attackEntity` 直打 | Vape `SilentAura.java:417-445,776-784` |
| 具体 AC 适配（Grim/Verus/Vulcan） | 未发现字面证据；`Version`(1116) 只区分 1.8/1.9 攻击间隔 | 未发现字面证据（仅 `Perfect swing`/`SilentBlockBreakingItems` 等通用项） | — |

补充：Kiss 的 `Version`（`1116`，1.8/1.9）影响的是**攻击间隔模型**（1.8 用武器攻速 1.6，1.9 用 APS 随机），不是反作弊适配；Vape 的等价分支走 `ForgeVersion`（`SilentAura.java:210,378,427,449,491,522,576`），且对 1.12.2 的 `Perfect swing` 单独处理。

---

## 8 结论与评分

### 8.1 各自优势

**Kiss**
1. 旋转自然度是全场最强项：4 条曲线族**运行时随机抽取**（`p/ibr/zq/b.java:1115-1127`），再叠加贝塞尔随机控制点（`370-404`）与 fBm 噪声（`831-853`），同一个人连续两次转向的曲线形态都不同，这是最难被统计类检测（rotation delta 分布、曲线拟合）抓到的结构。
2. GCD 量化做得最「正确」：量化的是**增量**而非绝对值（`1263-1266`），不会累积漂移，也不会出现「角度永远是 0.15 的整数倍」这种绝对特征。
3. 目标筛选有真正的 AntiBot 体系（7 项设置）与 LOS 3 tick 迟滞，穿墙是「短时容忍」而不是「无脑穿墙」。
4. 状态机（IDLE/ACQUIRE/HOLD/EASE/RELEASE + FASTROT + ATK-GATE + HITBOX-GUARD）覆盖了「贴身、被击退、格挡、移动」等边界，`Sim-DIAG` 注释显示作者是拿这段代码反复调参的。

**Vape**
1. 架构最干净：旋转被抽象为控制器家族，`SilentAura`/`AimAssist`/`AutoMace`/`BowAimbot` 共享 `RotationManager` + `RotationControlClaim` 仲裁（`rotation/RotationControlClaim.java`），不会互相抢视角。
2. 像素步进 + 灵敏度时间缩放（`RotationManager.java:529-535,795-800`）让「每 tick 像素步数」在任何灵敏度下恒定，手感与检测特征都稳定；这是 Kiss 用 GCD 量化达到的同一目标，Vape 用「物理上就是鼠标」达成。
3. 参数面大且语义清晰（21 项），版本分支完整（1.7.10 的静默不可用被显式识别并降级，而不是硬套）。
4. AI 旋转（MLP）与 PID 可切换，`AimAssist` 另有工作线程，工程化程度高。

### 8.2 各自劣势

**Kiss**
1. 性能最差：`onPacketSend` 每 10 tick 构造 6 次 `String.format` 的诊断串后交给**空方法** `mn65`（`g/pbd/b.java:7369-7598`、`7742-7743`）；`apply()` 每 tick 现场拼 `char[]` 字符串再 `switch`（`p/ibr/zq/b.java:1157-1205`）。
2. 单文件 10988 行 + 巨型方法 `mk586()`（3145–5431，2286 行），可维护性极差；同一逻辑（距离/角度过滤）在 `mq580`/`ms767`/`my967` 各写一遍。
3. 无 AI/ML 之外的预测手段，`Predict` 只按 ping 线性外推，对高速横移/跳动的补正有限。
4. 参数语义重复：`Range` 与 `Swing Range`、`New Response/Feedforward/Predict/Deadzone` 与 `Predict` 并存，普通用户难以理解。

**Vape**
1. 旋转曲线单一：PID 收敛的「指数逼近」形态比 Kiss 的多曲线随机更容易被曲线拟合类检测建模；正弦抖动周期固定（相位推进），长期可被频谱分析。
2. 默认无 LOS（`Ignore behind walls` 默认关）→ 会在墙后锁人，`EntityReach`/`Post` 类检查更容易命中；Kiss 的 3 tick 迟滞更保守。
3. 每次 tick 跑 50–100 次控制器迭代（`RotationManager.java:795-800`），CPU 成本高于 Kiss 的单次 `apply()`。
4. `attackEntity` 直调路径（1.7.10）与静默包路径（1.8+）分叉，行为不一致，跨版本体验差异大。

### 8.3 评分（1–5，5 最强）

| 维度 | Kiss | Vape | 理由（证据见上） |
|---|---|---|---|
| 1 目标筛选 | **4** | **4** | Kiss 有 AntiBot + LOS 迟滞但类型开关少；Vape 参数多但无 LOS 默认、无 AntiBot |
| 2 瞄准点 | **3** | **4** | Kiss 只有「身/眼插值 + ±0.1 随机」；Vape 有 Center/Closest + 百分比随机瞄准 + 三轴 jitter |
| 3 旋转算法 | **5** | **4** | Kiss 多曲线族 + 贝塞尔随机 + fBm + GCD 增量量化；Vape PID 干净但形态单一 |
| 4 静默/合法分离 | **4** | **5** | 双方都能静默，但 Vape 的 EventMotion 覆盖 + claim 仲裁 + S08 重写更系统 |
| 5 预测 | **3** | **3** | Kiss 有 ping 缩放但线性；Vape 有特征与 1.7× 直推，双方都无真正的延迟补偿 |
| 6 攻击联动 | **3** | **4** | Kiss APS + ATK-GATE 可靠但无 perfect swing；Vape 有 ClickEngine + Perfect swing + Shield 联动 |
| 7 参数可配置 | **3** | **5** | Kiss 参数多但语义重叠、命名混淆；Vape 21 项 + 描述 + 依赖显隐 |
| 8 检测面工程 | **5** | **4** | Kiss GCD 增量量化 + 多曲线随机更强；Vape 像素步进同等但曲线可预测、默认穿墙 |
| 9 性能/线程 | **2** | **4** | Kiss 诊断字符串与现场拼串是实打实的浪费；Vape 偏重但有工作线程 |
| 10 实战取向 | **4** | **4** | Kiss 适合「怕被录屏/统计」的保守场景；Vape 适合「要手感、要参数、要稳定」的场景 |
| **合计** | **36/50** | **41/50** | |

### 8.4 一句话总结

Kiss 是「**把旋转藏进噪声里**」的偏执实现——它愿意用 10988 行、300 行无效字符串开销，换取一条「每次都不一样」的瞄准曲线；Vape 是「**把旋转做成产品**」的工程实现——像素级步进、控制器仲裁、参数与版本矩阵，代价是曲线形态更容易被长期统计。

若只看「反检测上限」，Kiss 的旋转层更高（多曲线族 + GCD 增量量化）；若看「整体可用性与维护性」，Vape 明显更强。

---

## 附：本文使用的还原方法

| 用途 | 工具 | 说明 |
|---|---|---|
| 设置名/模式名还原 | `build/kiss-tools/deobf.py`（`Runner.eval_expr`） | 求值 `new String(new char[]{(char)(A^B^…)})` 常量表达式 |
| 数值还原 | `Double.longBitsToDouble(aL^bL)` / `Float.intBitsToFloat(a^b)` 位运算 | 例：`g/pbd/b.java:660` → 180.0（FOV 默认） |
| 字符串转储 | `C:\Users\Jiang\AppData\Local\Temp\s_<path>.txt` | 逐语句求值并映射回行号 |
| 设置批量提取 | `C:\Users\Jiang\AppData\Local\Temp\settings.py` | 输出 `行号 \t 类型 \t 名称 \t 数值表`（数值表前 3 项为 default/min/max） |
