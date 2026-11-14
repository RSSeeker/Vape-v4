# KissClient AI 瞄准（LegitAura「AI」模式）逆向规格

> **分析对象**：KissClient（Minecraft 26.2 Fabric 作弊客户端），jar 经 Vineflower 反编译，混淆名保留。
> **源码根**：`C:\Users\Jiang\AppData\Local\Temp\kiss-src`
> **方法**：纯静态阅读 + 反编译常量解码（把 `new String(new char[]{...})` / `Double.longBitsToDouble(...)` 表达式用 JDK 实际执行求值），未运行客户端、未注入、未改任何目标文件。
> **性质**：本文只读分析文档；除本文件外未修改仓库任何文件。
> **行号说明**：行号对应上述反编译输出，重新反编译（不同 Vineflower 版本/选项）可能位移；请以「行号 + 邻近代码特征」双重定位。

---

## 0. 结论速览（TL;DR）

1. **Kiss 的 AI 不是「和传统瞄准按比例混合」**。`AI Blend Angle` 是一个**硬阈值门控**：当前误差角度 > 阈值时，该 tick 直接放弃 AI、交给 Kiss 自研平滑旋转引擎，并**清空 AI 全部内部状态**；误差回到阈值内才恢复 AI。所以观感上是「两个引擎轮流接管」，不是线性插值。
2. **AI 的输出量纲是「度 / tick 的旋转增量」**，先乘 yaw/pitch 倍率，再经可选的 `Correction` 残差修正，再叠加可选的积分项，然后**除以「每像素角度」（鼠标灵敏度换算）变成整像素**，做整数截断（可开启小数余量 carry），最后乘回角度并**双向 ±MaxStep 限幅**、俯仰限 ±90°，输出**绝对 yaw/pitch**（不是增量）。
3. **`AI Acquisition Boost` 是「热身 tick 数」**：目标切换后的前 N 个 tick 由普通旋转引擎抢锁（AI 强制让位并清状态），第 N+1 个 tick 起 AI 接管。
4. **6 维输入不做任何归一化/平滑**：`[yaw误差, pitch误差, yaw速度, pitch速度, 玩家+目标水平速度, 眼睛到瞄准点距离]`，前 4 维单位为度（速度是度/tick），第 5 维单位 blocks/tick，第 6 维单位 blocks。
5. 模型文件格式为自研 `VMLP`（魔数 `VMLP`、version=1、20 个张量），网络 `6 → 128 → 64 → 32 → 2`，每层 `Linear → BatchNorm(推理式) → ReLU`，输出层 `Linear` 无激活。**与 Vape 现有 `MlpModel` 完全同格式**，`21kc11kp.bin` / `19kc8kp.bin` 已在 Vape 资源里（各 49254 B，形状逐项一致）。
6. Kiss 的 `ModuleA → 21KC11KP`、`ModuleB → 19KC8KP` 只是**混淆显示名到模型文件名的映射**，没有额外语义。

---

## 1. 证据索引（混淆名 → 语义）

| 混淆名 | 文件 | 语义 |
| --- | --- | --- |
| `ie.c`（8738 行） | `ie/c.java` | LegitAura 模块本体（设置声明 + 每 tick 瞄准） |
| `ur.m.g`（430 行） | `ur/m/g.java` | **AI 旋转引擎**（MLP 调用 + 全部后处理），本文核心 |
| `bhx.h`（307 行） | `bhx/h.java` | **MLP 加载与推理**（`VMLP` 解析、`predict` / `predictSafe`） |
| `bhx.c`（81 行） | `bhx/c.java` | 目标角摆动振荡器（Kiss 引擎相关，见 §8.3） |
| `gp.rr.d`（1536 行） | `gp/rr/d.java` | Kiss 自研旋转控制器（`apply` / `syncTo` / `anchorYawToPlayer`） |
| `fv805` | `ie/c.java:75` | `ur.m.g` 实例（`ie/c.java:1339` new） |
| `fl856` | `ie/c.java:1712` | `gp.rr.d` 旋转控制器实例 |
| `fj182` | `ie/c.java:121` | `bhx.c` 摆动振荡器实例（`ie/c.java:1714` new） |

### 1.1 关键方法定位

| 方法 | 位置 | 作用 |
| --- | --- | --- |
| `mq928(Entity)` | `ie/c.java:4503-4560` | 每 tick 的瞄准总入口，AI 分支在此 |
| `mp855()` | `ie/c.java:3141-3163` | 把 10 个设置推送到引擎 |
| `mc383(String)` | `ie/c.java:~3300-3328` | 目标切换：日志 + `clear()` + `fw416=0` + `fv960=null`（尾部在 3325-3328） |
| `mi331()` | `ie/c.java:2430-2462` | 判定 `attackMode == "Silent"`（AI 只在 Silent 攻击模式启用） |
| `md81(yaw,pitch)` | `ie/c.java:2208-2229` | `hypot(|wrap(目标yaw-serverYaw)|, |目标pitch-serverPitch|)`（度） |
| `update(...)` | `ur/m/g.java:336-408` | 引擎主循环：输入构造 → 推理 → 后处理 → 绝对角度 |
| `mp871(...)` | `ur/m/g.java:309-334` | Correction（残差修正） |
| `ms321(...)` | `ur/m/g.java:294-299` | Correction 的每轴修正比例 |
| `aiMouseScale()` | `ur/m/g.java:246-254` | 鼠标灵敏度 → 每像素角度 |
| `mk297 / mw615 / mq993` | `ur/m/g.java:414-416 / 422-425 / 427-429` | clamp01 / 二次贝塞尔 / sigmoid |
| `mt852(String)` | `ur/m/g.java:90-169` | 模型名映射（LCG 加密字符串） |
| `update` 内的 `clear()` | `ur/m/g.java:218-227` | 状态复位 |
| `predict(float[6])` | `bhx/h.java:225-246` | 6 维输入，逐层 `mv625 → me589 → mh937` |
| `predictSafe` | `bhx/h.java:248-261` | 异常/非有限值兜底 → null |
| `load(String)` | `bhx/h.java:186-223` | `/aimodels/<小写名>.bin` 加载与缓存 |
| `mv625` / `me589` / `mh937` | `bhx/h.java:277-294 / 169-179 / 86-92` | 全连接 / BatchNorm 推理 / ReLU |

---

## 2. 十项 AI 设置总表

`ie/c.java` 中的声明位置与解码出的构造实参：

| # | Kiss 英文原名 | 中文名 | 类型 | 范围（step） | 默认值 | 声明位置 | 界面含义 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | `AI Model` | AI 模型 | Mode（2 项） | `ModuleA` / `ModuleB` | **ModuleA** | `ie/c.java:708` | 选择内置 MLP 权重。`ModuleA` 实际加载 `21KC11KP`，`ModuleB` 加载 `19KC8KP`（8/7 字符名） |
| 2 | `AI Yaw Multiplier` | AI 偏航倍率 | Number | 0.5 ~ 2.0（0.05） | **1.5** | `ie/c.java:863` | 模型 yaw 输出（度）乘以该倍率，>1 更凶 |
| 3 | `AI Pitch Multiplier` | AI 俯仰倍率 | Number | 0.5 ~ 2.0（0.05） | **1.0** | `ie/c.java:909` | 模型 pitch 输出（度）乘以该倍率 |
| 4 | `AI Blend Angle` | AI 混合角度（实为门控阈值） | Number | 0 ~ 180（1） | **45.0** | `ie/c.java:970` | 误差超过该角度就当 tick 弃用 AI，改由普通旋转接管；`0` = 关闭门控 |
| 5 | `AI Acquisition Boost` | AI 目标获取预热 | Number（当整数用） | 0 ~ 10（1） | **3.0** | `ie/c.java:1015` | 换目标后前 N 个 tick 强制使用普通旋转（值先 `intValue()` 再比较），`0` = 关闭 |
| 6 | `AI Yaw Only` | 仅 AI 偏航 = 只吃 AI 俯仰 | Boolean | 开 / 关 | **false** | `ie/c.java:1061` | 开：yaw 完全交给普通旋转引擎，只把 AI 的 pitch 应用到服务器旋转 |
| 7 | `AI Carry Pixels` | AI 像素余量继承 | Boolean | 开 / 关 | **true** | `ie/c.java:1087` | 开：整像素截断后的小数余量留到下一 tick（消除系统性欠转）；关：每 tick 直接丢弃余量 |
| 8 | `AI Integral Gain` | AI 积分增益 | Number | 0 ~ 1（0.01） | **0.0** | `ie/c.java:1139` | 误差积分项权重；`0` = 关闭并清空积分 |
| 9 | `AI Max Step` | AI 单 tick 最大步进 | Number | 10 ~ 180（1） | **90.0** | `ie/c.java:1184` | 相对「当前旋转」的每 tick 角度变化上限（yaw、pitch 各自独立），单位度 |
| 10 | `AI Correction` | AI 修正模式 | Mode（3 项） | `None` / `Interpolation` / `Linear` | **Interpolation** | `ie/c.java:1213` | AI 提议角之后的残差修正层：`None` 关闭；`Interpolation` 按误差比例（1.6%~46%）拉向真值；`Linear` 每 tick 固定最多 5° 拉向真值 |

**注意**：任务简报里写「Yaw/Pitch Multiplier（1.5/0.5）」是近似说法。实测解码结果是 **yaw 默认 1.5、pitch 默认 1.0**，两者范围都是 0.5 ~ 2.0（`0.5` 是两者的共同下限，不是 pitch 的默认值）。Vape 现有实现（`SilentAura.java:369-370`）默认值恰好也是 1.5 / 1.0，无需改动。

**声明实参原始形态**（`com/kiss/setting/settings/NumberSetting.java:12-16`，签名 `(name, defaultValue, min, max, pattern)`）：

- `ie/c.java:863` `aiYawMultiplier`：`1.5, 0.5, 2.0, "0.00"`
- `ie/c.java:909` `aiPitchMultiplier`：`1.0, 0.5, 2.0, "0.00"`
- `ie/c.java:970` `aiBlendAngle`：`45.0, 0.0, 180.0, "1"`
- `ie/c.java:1015` `aiAcquisitionBoost`：`3.0, 0.0, 10.0, "1"`
- `ie/c.java:1139` `aiIntegralGain`：`0.0, 0.0, 1.0, "0.01"`
- `ie/c.java:1184` `aiMaxStep`：`90.0, 10.0, 180.0, "1"`

### 2.1 设置 → 引擎的推送（`ie/c.java:3141-3163`）

```
mp855()：每 tick 在 AI 分支入口调用一次（ie/c.java:4521）
  setModelName(aiModel.getValue())                      // 名字变化才 clear()
  setYawMultiplier(aiYawMultiplier)                     // 无钳制
  setPitchMultiplier(aiPitchMultiplier)                  // 无钳制
  setCarryPixels(aiCarryPixels)                          // 变化时清像素余量
  setIntegralGain(aiIntegralGain)                        // clamp 0..1；<=0 时清积分
  setMaxStepDegrees(aiMaxStep)                           // clamp 1..180
  setCorrectionMode(correctionModeFromName(aiCorrection)) // None=0, 其余=1, Linear=2
```

**重要**：UI **从不调用** `setStepScale` / `setCorrectionSpeed` / `setCorrectionDirection`。因此引擎里这三个量永久保持构造默认值（见 §7），即：步进倍率 1.0、修正速度 0.02~0.05、修正方向 0.95~1.0、阈值 0.35、Linear 钳制 ±5°。

---

## 3. 逐项设置的数学作用

### 3.1 `AI Model`（`ie/c.java:708`）

只影响权重选择，不参与任何算术。链路：

```
ie/c.java:3142  setModelName(aiModel.getValue())          // "ModuleA" / "ModuleB"
ur/m/g.java:79-84  setModelName: 名字变了才 this.fl628 = name; this.clear();
ur/m/g.java:338  bhx.h.load( mt852(this.fl628) )          // 每 tick 都调用（有缓存）
ur/m/g.java:90-169  mt852: "ModuleA" -> "21KC11KP"(8字符)
                            "ModuleB" -> "19KC8KP"(7字符)
                            其它 -> 原样返回
bhx/h.java:200  路径 = "/aimodels/" + name.toLowerCase() + ".bin"
```

`ModuleA`/`ModuleB` 是 UI 上的混淆显示名，**真实模型文件名**才是 `21KC11KP` / `19KC8KP`（与 Vape 的 `aiModel` 枚举值同义）。字符串由 LCG 生成，已用等价脚本复现验证（`ModuleA`→`21KC11KP`、`ModuleB`→`19KC8KP`）。

### 3.2 `AI Yaw Multiplier` / `AI Pitch Multiplier`（`ie/c.java:863` / `909`）

纯倍率，**作用在模型输出的「度」上，且在所有后处理之前**：

```java
// ur/m/g.java:370-371
float var31 = var29[0] * this.fe48;   // fe48 = yawMultiplier，默认 1.5
float var32 = var29[1] * this.fj112;  // fj112 = pitchMultiplier，默认 1.0
```

setter 无钳制（`ur/m/g.java:265-267`、`305-307`），UI 范围 0.5~2.0。倍率作用后**立即**进入 `Correction`（见 §3.10），所以真实视角不是简单 ×1.5 的放大曲线。

### 3.3 `AI Blend Angle`（`ie/c.java:970`，默认 45）——不是插值，是门控

```java
// ie/c.java:4513-4514：误差角（相对 jitter 后的目标角）
float var11 = Mth.wrapDegrees(var7  - var9 );   // yaw 误差，度
float var12 = Mth.wrapDegrees(var8  - var10);   // pitch 误差，度
// ie/c.java:4527-4537
float var17 = Math.max(Math.abs(var11), Math.abs(var12));
boolean var18 = this.aiBlendAngle.getValue() > 0.0 && var17 > this.aiBlendAngle.getValue();
...
if (var18 || var19) {
   this.fv805.clear();                                  // ★ 清空全部 AI 状态
   this.fl856.apply(var14, var15, var1, var13);         // 普通平滑旋转接管本 tick
   return;
}
```

语义：

- 判据是 `max(|yawErr|, |pitchErr|) > BlendAngle`，是一个**单轴取最大**的角度阈值（不是合成角，也不是欧氏距离；对比 `md81()` 用的是 `hypot`，两者不同）。
- 触发时执行 **`fv805.clear()`**（`ur/m/g.java:218-227`）：`isActive=false`、`hasPrev=false`、`prevYaw=prevPitch=0`、积分清零、像素余量清零、`Correction` 的上一 tick 目标角 = NaN。
- 因此**每次大角度切换都会把 AI 复位一次**；误差要等普通旋转引擎把它压回 45° 以内，AI 才带着「零速度特征、零积分、零余量」重新接管。观感 = 大甩视角时由普通旋转完成，接近目标后 AI 收敛最后几度。
- `BlendAngle = 0` 时 `var18` 恒 false → 门控关闭（`AI Acquisition Boost` 仍生效）。
- 顺带：复位导致的「速度特征首帧为 0」是 `ur/m/g.java:350` 的 `fq48` 标志控制的（`clear()` 里置 false）。

### 3.4 `AI Acquisition Boost`（`ie/c.java:1015`，默认 3）——热身 tick 数

```java
// ie/c.java:4529-4537
boolean var19 = this.fw416 < this.aiAcquisitionBoost.getValue().intValue();
if (var19) {
   this.fw416++;
}
if (var18 || var19) { this.fv805.clear(); this.fl856.apply(...); return; }
```

- `fw416`（`ie/c.java:156`）是「当前目标已连续锁定的 tick 数」，**只在换目标时归零**：`mc383()` 尾部 `ie/c.java:3325-3328` → `this.fv805.clear(); this.fw416 = 0; this.fv960 = null;`；
- 比较发生在自增**之前**，所以 N=3 时：第 0/1/2 个 tick 走普通旋转（每个 tick 还伴随一次 `clear()`），第 3 个 tick 起（`fw416 == 3`）AI 接管；
- `0` = 关闭（`0 < 0` 为 false）；范围 0~10，`intValue()` 截断（小数部分无效，例如 3.9 → 3）；
- **副作用/边界**：`fv960` 只在 `mc383` 里置 null，所以「丢失目标后又重新锁到同一实体」不会重新热身，`fw416` 早已 ≥ N，AI 立刻接管（携带上一次的残余状态，因为期间没有 `clear()`）。若要做到「每次重新锁定都热身」，Vape 侧应在「目标从 null 变为非 null」处也重置。

### 3.5 `AI Yaw Only`（`ie/c.java:1061`，默认 false）

```java
// ie/c.java:4541-4548
if (var20 != null) {
   if (this.aiYawOnly.getValue()) {
      this.fl856.apply(var14, var15, var1, var13);      // 普通引擎先接管 yaw+pitch 的平滑目标
      this.fl856.syncTo(this.fl856.yaw(), var20[1]);    // ★ yaw 传「当前 serverYaw」（基本不变），pitch 用 AI
   } else {
      this.fl856.syncTo(var20[0], var20[1]);            // 两个轴都用 AI 的绝对角
   }
   this.fl856.anchorYawToPlayer();
   return;
}
```

- 关闭（默认）：本 tick 的服务器旋转被**直接覆盖**为 AI 返回的绝对 `(yaw, pitch)`；
- 开启：`apply(...)` 把普通引擎的平滑目标设好（后续 tick 由它推进 yaw），但本 tick **立即 `syncTo`** 只写入 AI 的 pitch，yaw 保持当前 `serverYaw`。效果是「yaw 用传统引擎的平滑手感，pitch 用模型」。

### 3.6 `AI Carry Pixels`（`ie/c.java:1087`，默认 true）——像素量化与余量

```java
// ur/m/g.java:378-393
float var41 = (var31 * this.fb110 + this.fb112 * this.fn110) / var30;   // 度 -> 像素
float var34 = (var32 * this.fb110 + this.fb112 * this.fy674) / var30;
if (this.fw11) { var41 += this.fc299; var34 += this.fd61; }   // 先加回上一 tick 的小数余量
int var35 = (int)var41;                                       // ★ 整像素截断（向零）
int var36 = (int)var34;
if (this.fw11) { this.fc299 = var41 - var35; this.fd61 = var34 - var36; }  // 保存小数余量
float var37 = var35 * var30;                                  // 像素 -> 度
float var38 = var36 * var30;
```

- `var30 = aiMouseScale() * 0.15`（`ur/m/g.java:369`），即**每 1 像素鼠标位移对应的角度（度/像素）**；
- `aiMouseScale()`（`ur/m/g.java:246-254`）：

  ```java
  double var0 = (Double) mc.options.sensitivity().get();   // 原版灵敏度滑块 0..1
  double var2 = var0 * 0.6 + 0.2;
  return (float)(var2 * var2 * var2 * 8.0);
  ```

  与 Vape `FixedRotationController.getMouseScale()`（`FixedRotationController.java:142-145`）**公式完全一致**；`mc` 为空时兜底 4.0（`var30 = 0.6`）。
- 于是「度 → 像素 → 度」的完整换算式：

  ```
  像素 = 度 / (mouseScale * 0.15)
  度   = int(像素 + carry) * mouseScale * 0.15
  ```

  常见灵敏度下的量化步长 `var30`：

  | 原版灵敏度 s | mouseScale = (0.6s+0.2)³·8 | 每像素角度 `var30`（度/px） | 90° 需要像素数 |
  | --- | --- | --- | --- |
  | 0.00 | 0.064 | 0.0096 | 9375 |
  | 0.25 | 0.343 | 0.0515 | 1749 |
  | 0.50 | 1.000 | 0.1500 | 600 |
  | 0.75 | 2.197 | 0.3296 | 273 |
  | 1.00 | 4.096 | 0.6144 | 146 |

- `CarryPixels=true`：小数部分逐 tick 累积，长期平均输出**精确等于请求角度**，只把误差推迟到后续 tick 消化；
- `CarryPixels=false`：每 tick 向零截断且余量丢弃 → **系统性欠转**。例：请求 3.33 px 只执行 3 px；在 s=1 时欠 0.2°/tick，在低灵敏度下（角度量子很小）几乎无感；
- **与 Vape 的关键差异**：Vape 的 `applyAiRotationDelta` 把「度 ÷ (mouseScale·0.15)」写进 `pendingYawDelta`，而 `MouseRotationController.applyPendingMovement`（`MouseRotationController.java:171-215`）里 `(int)` 截断后的余数虽写回 `pendingYawDelta`，**下一 tick 又被 `applyAiRotationDelta` 整体覆盖**（赋值不是 `+=`，见 `AdaptiveRotationController.java:455-462`），所以 Vape **实际没有 carry**。
- setter 在开关变化时清余量（`ur/m/g.java:287-292` → `mx507()`）。

### 3.7 `AI Integral Gain`（`ie/c.java:1139`，默认 0.0）——泄漏积分 + ±45° 钳制

```java
// ur/m/g.java:343-346  累计（只有 gain > 0 才执行）
if (this.fb112 > 0.0F) {
   this.fn110 = Mth.clamp((this.fn110 + var10) * 0.95F, -45.0F, 45.0F);   // yaw
   this.fy674 = Mth.clamp((this.fy674 + var11) * 0.95F, -45.0F, 45.0F);   // pitch
}
...
// ur/m/g.java:378-379  应用（单位：度）
float var41 = (var31 * this.fb110 + this.fb112 * this.fn110) / var30;
float var34 = (var32 * this.fb110 + this.fb112 * this.fy674) / var30;
```

- 积分状态 = **每 tick 误差（度）** 的泄漏累加：`I ← (I + err) × 0.95`，然后钳到 `±45°`；
- 等效时间常数约 `1/(1-0.95) = 20 tick`（≈1 秒），稳态增益 ≈ `1/(1-0.95) = 20` 倍误差——但因为最终又乘 `Integral Gain (0..1)` 并且结果被 `Max Step` 二次限幅，实际不会发散；
- 因为 `var10/var11` 是「目标 − 当前旋转」的**带符号**误差，积分会持续补足模型长期低估的部分（消除稳态偏差），代价是过冲；
- `gain = 0`（默认）时**完全不累加**；setter `setIntegralGain`（`ur/m/g.java:280-285`）在 `<= 0` 时调用 `mg380()` 清零（`ur/m/g.java:237-240`）；
- `clear()` 同样清零（`ur/m/g.java:223`）。

### 3.8 `AI Max Step`（`ie/c.java:1184`，默认 90）——单 tick 角度上限

```java
// ur/m/g.java:394-400
float var39 = var3 + var37;                                     // yaw 绝对角
float var40 = Mth.clamp(var4 + var38, -90.0F, 90.0F);           // pitch 先限到 ±90
var39 = var3 + Mth.clamp(var39 - var3, -this.fs256, this.fs256); // ★ yaw 相对当前旋转 ±MaxStep
var40 = var4 + Mth.clamp(var40 - var4, -this.fs256, this.fs256); // ★ pitch 同样
var40 = Mth.clamp(var40, -90.0F, 90.0F);                        // 二次俯仰限幅
this.fy322 = true;
return new float[]{var39, var40};                               // ★ 返回绝对角
```

- `fs256` 默认 90.0（构造函数 `ur/m/g.java:61`），UI 范围 10~180；内部 setter 钳 `1..180`（`ur/m/g.java:86-88`）；
- 两个轴**独立**限幅，且是相对**当前服务器旋转**（`var3/var4`）而不是相对目标；
- 极限情况：`MaxStep = 180` 时该项基本失效（±180 覆盖整个圆）；`MaxStep = 10` 时即使模型输出几百度的「甩枪」，每 tick 也只走 10°；
- 注意顺序：**先量化到整像素、再乘回角度、最后才限幅**，所以限幅后的值一定是「当前角 ± 整数像素的角度」的整数倍，量化误差无法靠限幅补偿；
- yaw 不做 `wrapDegrees`（绝对值可越界），由调用方 `anchorYawToPlayer()`（`gp/rr/d.java:882-901`）把 `serverYaw` 折回 `playerYaw ± 180`。

### 3.9 `AI Correction`（`ie/c.java:1213`，默认 Interpolation）——残差修正/阻尼层

模型名映射（`ur/m/g.java:171-202`）：`None → 0`，`Linear → 2`，其余（含 `Interpolation`）`→ 1`。

调用条件与位置：

```java
// ur/m/g.java:372-376
if (this.fb949 != 0) {
   float[] var33 = this.mp871(var3 + var31, var4 + var32, var5, var6); // (AI 提议角, 真目标角)
   var31 = var33[0] - var3;   // 换回「相对当前角」的增量
   var32 = var33[1] - var4;
}
```

核心 `mp871`（`ur/m/g.java:309-334`）：

```java
float var5 = wrapDegrees(targetYaw   - proposedYaw  );   // AI 提议角相对真目标的残差
float var6 = wrapDegrees(targetPitch - proposedPitch);
float var7 = 0.0F;                                       // assist（目标运动加成）
if (!Float.isNaN(this.fy354)) {                          // 上一 tick 的目标角
   float var8 = max(|wrap(targetYaw - fy354)|, |wrap(targetPitch - fx208)|); // 目标一 tick 内移动量（度）
   float var9 = fb517 + rand*(fz697 - fb517);             // 0.95 ~ 1.0 随机
   var7 = mk297(var8) * var9;                            // 移动量/180 clamp01 * 随机系数
}
fy354 = targetYaw; fx208 = targetPitch;                  // 记录本 tick 目标角

if (mode == LINEAR) {                                    // 2
   float var15 = clamp(var5, -5.0, 5.0);                 // ★ 固定 ±5° 上限
   float var17 = clamp(var6, -5.0, 5.0);
   return { proposedYaw + var15, proposedPitch + var17 };
} else {                                                 // INTERPOLATION == 1
   float var14 = fw0 + rand*(fn131 - fw0);               // yaw 速度 0.02~0.05
   float var16 = fv661 + rand*(fb27 - fv661);            // pitch 速度 0.02~0.05
   float var10 = ms321(|var5|, var14, var7, fa674);      // 0.35 阈值
   float var11 = ms321(|var6|, var16, var7, fa674);
   float var12 = signum(var5) * var10 * |var5|;          // ★ 残差 × 比例
   float var13 = signum(var6) * var11 * |var6|;
   return { proposedYaw + var12, proposedPitch + var13 };
}
```

`ms321`（`ur/m/g.java:294-299`）与三个整形函数：

```java
mk297(x) = clamp(x/180, 0, 1)                                  // 归一化角度
mw615(a,b,t) = (1-t)²·a + 2·(1-t)·t + t²·b                     // 二次贝塞尔；中间控制点恒为 1.0
mq993(x) = 1/(1+e^(-0.5(x-0.3)))                               // sigmoid

ms321(errAbs, speed, assist, threshold=0.35):
   var4 = mk297(errAbs)                       // errAbs/180
   var5 = mw615(0.05, 1.0, 1-var4)            // errAbs=0 → 1.0；errAbs=180 → 0.05
   var6 = mq993(var4)                          // errAbs=0 → 0.4626
   return var4 > threshold ? var5*speed : var6*min(1, speed+assist)
```

数值含义（这就是「Correction 到底修多少」）：

| 残差 \|err\| | 分支 | 每 tick 修正比例 | 备注 |
| --- | --- | --- | --- |
| 0° | sigmoid 分支 | `0.4626 × min(1, 0.02~0.05 + assist)` ≈ **1.0%~46%** | assist≈0（静止目标）→ ≈1.4%；assist≈1（目标高速移动）→ ≈46% |
| 30° | sigmoid 分支 | ≈ `0.5 × min(1, speed+assist)` | 残差小、目标动得快时修正最猛 |
| >63°（>0.35·180） | 贝塞尔分支 | `(1.0→0.05) × (0.02~0.05)` ≈ **0.1%~4.4%** | 大残差几乎不修，避免叠加过冲 |
| 任意 | Linear | `clamp(err, ±5°)` 固定 5° | 与残差大小无关 |

**结论**：`Correction` 不是主控制器，而是一层**朝真值方向的残差阻尼/助推**——AI 输出正确时它几乎不动；AI 输出略偏时它按 1%~46% 往回拉；AI 输出严重偏离时它反而几乎不介入（把大偏差留给 `Max Step` 与下一 tick 的模型）。这也解释了为什么「I 项默认 0、Correction 默认开」也能收敛。

**依赖的内部常量**（UI 未暴露，构造默认值，`ur/m/g.java:60-77`）：`fw0=0.02`、`fn131=0.05`、`fv661=0.02`、`fb27=0.05`（修正速度）、`fb517=0.95`、`fz697=1.0`、`fa674=0.35`（assist 系数/阈值）、`fj644=5.0`（Linear 钳制）。

### 3.10 未出现在 UI、但影响输出的引擎常量

| 字段 | 默认 | 语义 |
| --- | --- | --- |
| `fb110` stepScale | 1.0 | 模型增量整体倍率（`ur/m/g.java:378-379` 的 `var31*fb110`） |
| `fs256` maxStep | 90.0 | 单 tick 角度上限（§3.8） |
| `fw11` carryPixels | true | 像素余量（§3.6） |
| `fb112` integralGain | 0.0 | 积分权重（§3.7） |
| `fb949` correctionMode | 1 | 修正模式（§3.9） |
| `fe48` / `fj112` | 1.5 / 1.0 | yaw / pitch 倍率（§3.2） |
| `fl628` modelName | `"ModuleA"` | 模型名（§3.1） |

---

## 4. 六维模型输入的精确构造

```java
// ur/m/g.java:341-366
float var10 = Mth.wrapDegrees(var5 - var3);   // [0] yaw 误差（度）
float var11 = Mth.wrapDegrees(var6 - var4);   // [1] pitch 误差（度）

float var12 = 0.0F, var13 = 0.0F;
if (this.fq48) {                               // 有上一 tick 状态才给速度
   var12 = Mth.wrapDegrees(var3 - this.ft778); // [2] yaw 速度（度/tick）
   var13 = Mth.wrapDegrees(var4 - this.fa843); // [3] pitch 速度（度/tick）
}
this.ft778 = var3; this.fa843 = var4; this.fq48 = true;

double var14 = player.getX() - player.xo;      // 玩家水平位移
double var16 = player.getZ() - player.zo;
double var18 = sqrt(var14² + var16²);          // 玩家水平速度 blocks/tick
double var20 = target.getX() - target.xo;      // 目标水平位移
double var22 = target.getZ() - target.zo;
double var24 = sqrt(var20² + var22²);          // 目标水平速度 blocks/tick
float  var26 = (float)(var24 + var18);         // [4] 二者之和
float  var27 = (float) var7;                    // [5] 眼睛到瞄准点距离（blocks）

float[] var28 = new float[]{ var10, var11, var12, var13, var26, var27 };
float[] var29 = var9.predictSafe(var28);
```

| 下标 | 名称 | 单位 | 取值范围（实测语义） | 归一化 | 平滑 | 备注 |
| --- | --- | --- | --- | --- | --- | --- |
| 0 | yaw 误差 | 度 | `wrapDegrees` → (−180, 180] | 无 | 无 | 目标角 = 原始瞄准角 **+ Kiss 自身 jitter + 摆动振荡器**（见 §8.3） |
| 1 | pitch 误差 | 度 | 同上（实际目标 pitch 已限 ±90） | 无 | 无 | 同上 |
| 2 | yaw 速度 | 度/tick | 理论 ±180 | 无 | 无 | 上一 tick 的服务器 yaw 差；`clear()` 后首帧为 0 |
| 3 | pitch 速度 | 度/tick | 理论 ±180 | 无 | 无 | 同上 |
| 4 | 速度特征 | blocks/tick | 0 ~ 约 2（玩家 0.3 + 目标 1.4 量级） | 无 | 无 | 玩家 + 目标水平速度之和；**不含竖直分量** |
| 5 | 距离 | blocks | 1 ~ 6（aura 范围） | 无 | 无 | `Vec3.distanceTo` 三维距离 |

要点：

- **输入无任何归一化/裁剪**，全部是原始物理量；标准化由网络第一层 `BatchNorm` 完成（`bhx/h.java:169-179` 用 `mean/var` 推理式）。
- **输入无低通/EMA**。特征 2/3 的“速度”是硬差分；特征 0/1 之所以平滑，是因为目标角本身被 Kiss 引擎的 jitter/摆动/平滑链处理过。
- `predict` 严格要求长度 == 6 且全 `finite`，否则返回 `null`（`bhx/h.java:225-231`）；`predictSafe` 再兜一层 `Throwable`（`bhx/h.java:248-261`）。
- 调用方的“当前旋转”是 `fl856.yaw()/pitch()`（`gp/rr/d.java:1171`、`1477`）——即 **Kiss 维护的服务器旋转**，不是相机 `player.getYRot()`；Vape 侧对应 `RotationManager.getManagedYaw()/getManagedPitch()`。
- 距离用**眼睛位置**到瞄准点（`ie/c.java:4505` `getEyePosition(1.0F)` → `var13 = var2.distanceTo(var3)`），而 Vape 现用 `player.i(targetX,targetY,targetZ)`，量纲一致但基点可能略有差别（影响很小，模型已按 Kiss 训练，建议对齐）。

---

## 5. 模型文件与推理

### 5.1 格式（Kiss 与 Vape 同格式，已逐字节核对）

```
偏移 0   : magic "VMLP" (0x56 0x4D 0x4C 0x50)
偏移 4   : int32 version = 1
偏移 8   : int32 nParams = 20
每参数   : int32 nameLen + name(UTF-8) + int32 ndim + int32[ndim] shape + int32 dataLen + float32[dataLen] (小端)
```

Kiss 解析在 `bhx/h.java:134-160`（`ma728`），Vape 解析在 `MlpModel.parse(...)`（`MlpModel.java:90-163`），二者对参数顺序/形状要求一致：

| # | 形状 | 层 |
| --- | --- | --- |
| 0-4 | `[128,6] [128] [128] [128] [128]` | W1, b1, γ1, β1, mean1, var1 |
| 5-9 | `[64,128] [64] …` | W2, b2, γ2, β2, mean2, var2 |
| 10-14 | `[32,64] [32] …` | W3, b3, γ3, β3, mean3, var3 |
| 15-19 | `[2,32] [2]` | W4, b4 |

实测：Vape 仓库内 `src/main/resources/liquidbounce/models/21kc11kp.bin` 与 `19kc8kp.bin` 均为 **49254 字节**，`VMLP` v1、`nParams=20`、形状序列与上表完全一致；Kiss 侧 `/aimodels/*.bin` 结构相同（同一套 LiquidBounce 训练产物）。

### 5.2 前向

```java
// bhx/h.java:225-246
h1 = mv625(x,  W1, b1);  h1 = me589(h1, γ1, β1, mean1, var1);  mh937(h1);   // ReLU
h2 = mv625(h1, W2, b2);  h2 = me589(h2, γ2, β2, mean2, var2);  mh937(h2);
h3 = mv625(h2, W3, b3);  h3 = me589(h3, γ3, β3, mean3, var3);  mh937(h3);
y  = mv625(h3, W4, b4);                                                     // 无激活，2 维

// bhx/h.java:277-294   mv625: y[i] = b[i] + Σ_j W[i][j]·x[j]
// bhx/h.java:169-179   me589: y[i] = γ[i]·(x[i]-mean[i]) / sqrt(var[i] + 1e-5) + β[i]
// bhx/h.java:86-92     mh937: x[i] = max(0, x[i])
```

输出 2 维、无激活、**未乘任何常数**；引擎把它当「度」直接用（`ur/m/g.java:370-371`），据此可判定：**训练目标是「本 tick 的 yaw/pitch 角度增量（度）」**（此为由使用方式推断的量纲结论，非来自训练脚本）。

### 5.3 加载与失败语义

- `load(name)`：缓存 `Map<String,h>`（大小写不敏感，`toLowerCase(Locale.ENGLISH)`），失败名进 `fr978` 黑名单避免反复 IO；失败返回 null（`bhx/h.java:186-223`）。
- 引擎每 tick 都 `load(...)`（有缓存所以开销极小），`fs857 = (model != null)`；`isModelAvailable()` 返回它（`ur/m/g.java:339`、`410-412`）。
- 模型为 null 时 `update()` 直接走 `else` 分支把 `fq48=false` 并返回 null（`ur/m/g.java:404-407`）→ 调用方在 `isModelAvailable()==false` 时退回普通旋转（`ie/c.java:4553`、`4558`）。
- 若模型存在但本 tick 输出 null（输入非法/NaN），`ie/c.java:4553-4555` 命中 `isModelAvailable()` → **直接 return，本 tick 旋转完全不变**（不是退回普通旋转）。这是刻意的“宁可不转”策略。

---

## 6. AI 输出 → 最终 yaw/pitch 的完整伪代码

### 6.1 调用方（`ie/c.java:4503-4560`）

```text
每 tick（LegitAura 有合法目标、且 rotation claim 归本模块时）:

  eye        = player.getEyePosition(1.0)
  aimPoint   = mt168(target)                       // 瞄准点（中心/最近点）
  dist       = eye.distanceTo(aimPoint)
  rawYaw     = atan2(dz, dx)·180/π − 90
  rawPitch   = −atan2(dy, hypot(dx,dz))·180/π
  curYaw     = fl856.yaw()                          // 服务器旋转
  curPitch   = fl856.pitch()

  # 目标角 = 原始角 + Kiss 自身 jitter + 摆动振荡器（都在角度域）
  fj182.update(md81(rawYaw, rawPitch), 1.0, 10.0)   // 摆动器，>16° 时复位
  tgtYaw     = curYaw   + wrap(rawYaw   − curYaw)   + fl856.jitterYaw()   + fj182.getYaw()
  tgtPitch   = clamp(curPitch + wrap(rawPitch − curPitch) + fl856.jitterPitch() + fj182.getPitch(), −90, 90)

  yawErr     = wrap(tgtYaw   − curYaw)              // 度
  pitchErr   = wrap(tgtPitch − curPitch)            // 度

  if (!(attackMode == "Silent" && target is LivingEntity)):
      fl856.apply(tgtYaw, tgtPitch, target, dist)   // 普通旋转
      return

  pushSettings()                                    // mp855()
  if (target != lastAiTarget):                      // 目标切换
      mc383("target-change")                        //   clear(); fw416=0; lastAiTarget=null
      lastAiTarget = target

  # 门控 1：Blend Angle（大误差让位）
  if (aiBlendAngle > 0 && max(|yawErr|,|pitchErr|) > aiBlendAngle):
      engine.clear()                                // 清 prev/积分/carry/correction 状态
      fl856.apply(tgtYaw, tgtPitch, target, dist)
      return

  # 门控 2：Acquisition Boost（换目标后前 N tick 让位）
  if (fw416 < int(aiAcquisitionBoost)):
      fw416 += 1
      engine.clear()
      fl856.apply(tgtYaw, tgtPitch, target, dist)
      return

  out = engine.update(player, target, curYaw, curPitch, tgtYaw, tgtPitch, dist)   // §6.2
  if (out != null):
      if (aiYawOnly):
          fl856.apply(tgtYaw, tgtPitch, target, dist)      // yaw 交给普通引擎
          fl856.syncTo(fl856.yaw(), out[1])                  // 只写 AI 的 pitch
      else:
          fl856.syncTo(out[0], out[1])                       // 两个轴都覆盖
      fl856.anchorYawToPlayer()                              // 消除多圈
      return

  if (engine.isModelAvailable()): return                     // 模型在但输出 null → 本 tick 不动
  fl856.apply(tgtYaw, tgtPitch, target, dist)                // 模型缺失 → 普通旋转
```

### 6.2 引擎（`ur/m/g.java:336-408`）

```text
update(player, target, curYaw, curPitch, tgtYaw, tgtPitch, dist):
  active = false
  model  = load(mapName(modelName))            # ModuleA→21KC11KP, ModuleB→19KC8KP
  modelAvailable = (model != null)
  if (model == null || player == null || target == null):
      hasPrev = false
      return null

  # ① 误差
  yawErr   = wrap(tgtYaw   − curYaw)           # var10
  pitchErr = wrap(tgtPitch − curPitch)         # var11

  # ② 积分（仅 gain>0）
  if (integralGain > 0):
      Iyaw   = clamp((Iyaw   + yawErr)   · 0.95, −45, 45)
      Ipitch = clamp((Ipitch + pitchErr) · 0.95, −45, 45)

  # ③ 速度特征（clear 后首帧为 0）
  yawVel = hasPrev ? wrap(curYaw   − prevYaw)   : 0
  pitchVel= hasPrev ? wrap(curPitch − prevPitch) : 0
  prevYaw = curYaw; prevPitch = curPitch; hasPrev = true

  # ④ 输入（顺序固定，无归一化）
  speed = |ΔplayerXZ| + |ΔtargetXZ|            # blocks/tick
  x = [yawErr, pitchErr, yawVel, pitchVel, speed, dist]
  out = model.predictSafe(x)
  if (out == null || !finite(out[0]) || !finite(out[1])):
      return null                               # active 保持 false

  # ⑤ 每像素角度
  px = mouseScale() · 0.15                      # mouseScale=(0.6s+0.2)³·8，s=原版灵敏度
                                                 # mc 缺失时 mouseScale=4 → px=0.6

  # ⑥ 倍率
  dYaw   = out[0] · yawMultiplier
  dPitch = out[1] · pitchMultiplier

  # ⑦ Correction（残差修正，见 §3.9；mode 0 跳过）
  if (correctionMode != 0):
      (cYaw, cPitch) = correct(curYaw + dYaw, curPitch + dPitch, tgtYaw, tgtPitch)
      dYaw   = cYaw   − curYaw
      dPitch = cPitch − curPitch

  # ⑧ 度 → 像素（含步进倍率、积分、carry）
  pYaw   = (dYaw   · stepScale + integralGain · Iyaw)   / px
  pPitch = (dPitch · stepScale + integralGain · Ipitch) / px
  if (carryPixels):
      pYaw   += carryYaw
      pPitch += carryPitch
  nYaw   = int(pYaw)            # 向零截断
  nPitch = int(pPitch)
  if (carryPixels):
      carryYaw   = pYaw   − nYaw
      carryPitch = pPitch − nPitch

  # ⑨ 像素 → 度，限幅
  aYaw   = nYaw   · px
  aPitch = nPitch · px
  finalYaw   = clamp(curYaw   + aYaw  − curYaw,   −maxStep, +maxStep) + curYaw
  finalPitch = clamp(curPitch + aPitch − curPitch, −maxStep, +maxStep) + curPitch
  finalPitch = clamp(finalPitch, −90, 90)
  active = true
  return [finalYaw, finalPitch]                 # ★ 绝对角
```

### 6.3 关键性质

1. **输出是绝对角**，调用方 `syncTo` 直接覆盖服务器旋转；yaw 绝对值可能越界，由 `anchorYawToPlayer()` 折回。
2. **两次限幅**：先量化到整像素（角度分辨率 = 每像素角度），再 ±MaxStep；pitch 还有 ±90 的硬钳。
3. **无死区/无容差**：引擎不判断“是否已对上”，只要模型给数就输出，`active=true` 仅用于日志（`ie/c.java:3244`）。唯一的“死区”是量化：请求 < 1 像素的变化不会产生任何旋转（`carryPixels` 开启时会在后续 tick 补偿）。
4. **失败即静止**：模型可用但输出非法 → 调用方直接 return，本 tick 不写旋转；`maxStep` 不会“兜底继续转”。

---

## 7. 时序与数值示例

### 7.1 「换目标」时的 tick 序列（默认：Blend 45 / Boost 3 / Correction Interpolation）

| tick | fw416 | 判定 | 行为 |
| --- | --- | --- | --- |
| 0（锁定的第一个 AI tick） | 0 | `0 < 3` → 热身 | clear + 普通旋转 apply |
| 1 | 1 | 热身 | clear + 普通旋转 apply |
| 2 | 2 | 热身 | clear + 普通旋转 apply |
| 3 | 3 | 热身结束 | 若 `max(err) > 45` → 继续让位；否则 AI 接管 |
| 4+ | 3 | AI | 正常 AI 循环 |

### 7.2 大甩视角后的恢复

```
tick k   : err = 120° > 45° → clear() + 普通旋转（本 tick 由引擎平滑推进）
tick k+1 : err 由普通引擎逐步降低；AI 状态已清、速度特征首帧=0
...
tick k+m : err ≤ 45° → AI 接管（Correction 因 fy354=NaN 而 assist=0）
```

### 7.3 灵敏度对输出的影响（同一模型、同一误差）

| 灵敏度 s | 每像素角度 | 90° AI 请求 → 像素 | 实际执行（carry 开/关一致） | 量化残差 |
| --- | --- | --- | --- | --- |
| 0.5 | 0.15° | 600 px | 600 px = 90° | 0 |
| 1.0 | 0.6144° | 146.5 px | 146 px = 89.70° | −0.30°（下一 tick 补偿） |
| 0.0 | 0.0096° | 9375 px | 9375 px | 0 |

`carryPixels=false` 时，上表残差**永久丢失**，长跑会系统性偏慢（尤其在低灵敏度、小角度需求时，例如每 tick 请求 0.3 px 会被截成 0 → 完全不动）。

### 7.4 Correction 的一次数值演练

设 `curYaw = 0°`，`tgtYaw = 30°`，模型输出 `dYaw = 20°`（倍率 1.5 后），`correctionMode = Interpolation`，目标静止（assist ≈ 0）：

```
proposed = 0 + 20 = 20°
residual = wrap(30 − 20) = 10°           → var4 = 10/180 = 0.0556
var5 = mw615(0.05, 1.0, 1−0.0556=0.9444)≈0.987
var6 = sigmoid(0.5·(0.0556−0.3)) = sigmoid(−0.1222) ≈ 0.4695
var6 < 0.35 → 用 sigmoid 分支: frac = 0.4695 · min(1, 0.035 + 0) = 0.0164
修正 = 10° · 1.64% = 0.164°
finalYaw = 20.164°  （再进像素量化与 MaxStep）
```

即：模型少给 10° 时，Correction 每 tick 只补 0.16°，剩下的靠后续 tick 的模型输出与积分（默认关闭）——这是“AI 主导 + Correction 轻推”的设计取舍。

---

## 8. Kiss 与 Vape 现状的对照

### 8.1 已具备（Vape `SilentAura.java` + `deeplearn/*`）

| 能力 | Vape 位置 | 与 Kiss 的一致性 |
| --- | --- | --- |
| `AI Model` 两模型选择 | `SilentAura.java:363-365`、`771-778` | 一致（21KC11KP / 19KC8KP） |
| yaw / pitch 倍率（1.5 / 1.0，0.5~2.0） | `SilentAura.java:369-370` | 一致 |
| 6 维输入（同顺序/同单位） | `SilentAura.java:752-759` | 基本一致（见 §8.4 差异） |
| `VMLP` 解析 + `predict` | `MlpModel.java:90-232` | 一致（同格式/同 eps=1e-5） |
| 度 → 像素转换 | `AdaptiveRotationController.java:455-462` | 公式一致（`1/(mouseScale·0.15)`） |
| 灵敏度 → mouseScale | `FixedRotationController.java:142-145` | **公式完全一致** |

### 8.2 缺失（需新增）

`Blend Angle` 门控、`Acquisition Boost` 热身、`Yaw Only`、`Max Step` 限幅、`Integral`、`Correction`（3 模式）、像素余量 carry，以及**统一的状态复位**（`clear()` 等价物）。

### 8.3 Kiss 旋转引擎独有的、不可 1:1 复刻的部分

1. **目标角里的 `jitterYaw()/jitterPitch()`**（`gp/rr/d.java:1006`、`124`）：Kiss 在**角度域**叠加抖动，然后让 AI 去追一个抖动的目标，从而让输出的微动天然“像人”。Vape 的抖动加在**瞄准点（世界坐标）**上，角度域表现不同。
2. **摆动振荡器 `bhx.c`**（`ie/c.java:121`、`1714`，`ie/c.java:4517` 调用；实现 `bhx/c.java:22-50`）：两轴独立的二阶弹簧-阻尼系统，
   ```java
   // bhx/c.java:32-35  目标 −90·x、阻尼 7、步长 0.05
   this.ft301 += (−90·this.fg576 − 7·this.ft301) · 0.05;
   this.ff463 += (−90·this.fc835 − 7·this.ff463) · 0.05;
   this.fg576 += this.ft301 · 0.05;
   this.fc835 += this.ff463 · 0.05;
   // 每 4..16 tick 注入一次随机脉冲（幅度 1~3 倍 speed）
   // 钳制：yaw ±1.5·speed、pitch ±1.05·speed（speed=1.0 时即 ±1.5°/±1.05°）
   // 当 md81(目标角, 当前旋转) > 10·1.6 = 16° 时整体 reset()
   ```
   这是 Kiss 观感（瞄准时轻微“呼吸/游走”）的重要来源，Vape 无对应实现。
3. **`gp.rr.d.apply(...)` 的平滑推进曲线**：Kiss 自研的“目标 → 服务器旋转”逼近器，含速度/加速度/换目标策略（方法体被控制流混淆，未逐行还原；本文只把它当黑盒“普通旋转引擎”）。Vape 的 `SilentAuraRotationController`/`FixedRotationController` 是同类但参数化不同（tolerance/accel 模式/clampStepToRemaining），**不能假定等价**。
4. **`syncTo`/`anchorYawToPlayer` 的“虚拟 serverYaw”语义**（`gp/rr/d.java:1372-1383`、`882-901`）：Kiss 维护一个可越界的虚拟 yaw，靠 `anchorYawToPlayer` 折回；Vape 直接改玩家 yaw，无多圈问题，**这个函数不需要移植**。

### 8.4 Vape 现有 AI 路径与 Kiss 的细节差异（影响复刻精度）

| 项 | Kiss | Vape 现状 | 影响 |
| --- | --- | --- | --- |
| 输出后处理 | 倍率 → Correction → 积分 → 像素量化（可 carry）→ MaxStep | 倍率 → 像素量化（无 carry、无 MaxStep） | 有系统性欠转 + 无大角度保护 |
| 像素余量 | `carryPixels` 默认开 | `applyAiRotationDelta` 每 tick 覆盖 `pendingYawDelta`，余数丢失 | 小幅输出被 `(int)` 吃掉 |
| 目标切换 | `clear()` 全部状态 | 仅 `clearAiRotationMode()`（清 pending），无 prev/积分/余量状态 | 切换瞬间可能有陈旧速度特征 |
| 启用条件 | `attackMode == "Silent"` | `rotationMode == AI` | 语义位置不同，不影响数学 |
| 目标角 | 含 jitter + 摆动器 | 含瞄准点 jitter（`xJitter/pitchJitter/zJitter`） | 输入分布偏移（模型在 Kiss 分布上训练） |
| 距离 | `eye.distanceTo(aimPoint)` | `player.i(targetX,targetY,targetZ)` | 可能差一点基点/取整 |
| 失败语义 | 模型在但输出 null → 本 tick 不动 | `predictSafe` 返回 null → `clearAiRotationMode()` + `setTargetRotation(managedYaw, managedPitch)`（等价“不动”） | 等价 |

---

## 9. 移植到 Vape `SilentAura` AI 模式的最小改动清单

### A. 可在 Vape 侧独立实现（纯数学 + 状态机，不依赖 Kiss 旋转引擎）

按建议落地顺序（每步都可独立验证）：

1. **新增 7 项设置 + 1 个状态容器**
   - 设置：`aiBlendAngle`(Number 0~180, 默认 45)、`aiAcquisitionBoost`(Number 0~10, 默认 3，取整)、`aiYawOnly`(Boolean false)、`aiCarryPixels`(Boolean true)、`aiIntegralGain`(Number 0~1, 默认 0)、`aiMaxStep`(Number 10~180, 默认 90)、`aiCorrection`(Mode `None/Interpolation/Linear`，默认 Interpolation)。
   - 状态（建议独立类 `AiAimState`）：`hasPrev/prevYaw/prevPitch`、`integralYaw/integralPitch`、`carryYawPx/carryPitchPx`、`correctionPrevTargetYaw/Pitch`、`acquisitionTicks`、`lastTargetId`。
   - 注册到 `rotationMode` 的 AI 模式依赖列表（`SilentAura.java:374`）。

2. **Max Step 限幅**（最简单、收益最直接）：在 `updateAimAi` 把 `output[0]*yawMultiplier / output[1]*pitchMultiplier` 求出后，先转换成「相对当前 managed 旋转的增量」，再 `clamp(±aiMaxStep)`，然后才交给 `applyAiRotationDelta`。等价于 Kiss `ur/m/g.java:396-398`。

3. **像素量化 + carry（重建 Kiss 的整条像素路径）**：不要直接信任 `AdaptiveRotationController` 的 conversion + 隐式截断，在 `SilentAura` 内显式做：
   ```java
   float px = RotationManager.INSTANCE.getMouseSensitivity() * 0.6f + 0.2f;
   px = px * px * px * 8.0f * 0.15f;          // 每像素角度 = Kiss var30
   float pYaw = degYaw / px, pPitch = degPitch / px;
   if (aiCarryPixels) { pYaw += carryYaw; pPitch += carryPitch; }
   int nYaw = (int) pYaw, nPitch = (int) pPitch;
   if (aiCarryPixels) { carryYaw = pYaw - nYaw; carryPitch = pPitch - nPitch; }
   applyAiRotationDelta(nYaw * px, nPitch * px);   // 传回“量化后的度”
   ```
   （`AdaptiveRotationController` 内部会再除 `mouseScale·0.15`，由于除的正是同一个 `px`，最终 pending 恰好是整数像素，不会再产生二次截断。）

4. **Integral Gain**：在构造 6 维输入之前累加 `I = clamp((I+delta)*0.95, ±45)`，在像素换算前把 `gain*I` 加进 `degYaw/degPitch`（Kiss 的顺序：倍率 → Correction → 积分，见 `ur/m/g.java:370-379`）；`gain<=0` 与 `clear` 时清零。

5. **Correction（3 模式）**：纯函数搬运 `mp871/ms321/mk297/mw615/mq993`（`ur/m/g.java:294-334`、`414-429`），需要 `Random`、上一 tick 目标角状态（NaN 表示无）、以及 §7.4 的默认内部常量（speed 0.02~0.05、assist 0.95~1.0、阈值 0.35、Linear ±5°）。注意它作用在「度」上、介于倍率与积分之间。

6. **Blend Angle 门控**：在 `updateAimAi` 最前面（构造输入之前）比较 `max(|deltaYaw|,|deltaPitch|) > aiBlendAngle`；命中则 `aiAimState.reset()` + 走 PID 分支（Vape 已有独立 PID 代码路径，直接复用即可，见 `SilentAura.java:687-729` 的 yaw/pitch 公式），然后 return。

7. **Acquisition Boost**：在 `updateTarget()`（`SilentAura.java:565-570` 的 `targetChanged` 判定处）重置计数与 AI 状态；在 `updateAimAi` 里 `if (acquisitionTicks < (int)aiAcquisitionBoost) { acquisitionTicks++; resetState(); 走 PID; return; }`。建议额外在「target 从 null → 非 null」时也重置（Kiss 没有，但更符合直觉）。

8. **Yaw Only**：最小实现 = AI 只负责 pitch：`applyAiRotationDelta(0, aiPitchDeg)`，yaw 交给 PID 分支的 `setTargetRotation` 逻辑——但两者共享同一 controller，需注意 `applyAiRotationDelta` 会 `target=null` 并把 `aiRotationActive=true`（此时 `updateYaw` 直接 return true，PID 的 pending 不会被生成）。可行做法：在 `updateAimAi` 里自行计算 PID 的 yaw 增量（复用 `yawProportionalGain·yawError + yawIntegralGain·yawIntegral` 那段），把「PID yaw 度数」与「AI pitch 度数」一起传给 `applyAiRotationDelta`，一次写入。

9. **统一 `resetAiState()`**：等价 `ur/m/g.java:218-227`（isActive/hasPrev/prev/积分/carry/Correction 目标角）。调用点：目标切换、Blend 触发、Acquisition 触发、模型名变化、`IntegralGain` 设 0、`CarryPixels` 切换、目标丢失。

> 以上 1~9 全部只改 `SilentAura`（+ 新增一个状态类/工具类），**不动 `MlpModel` / `ModelManager` / 旋转控制器核心**，因此风险可控、可逐项 A/B 对比（Vape 已有 `Rotation Mode: PID/AI` 开关，天然支持对照）。

### B. 依赖 Kiss 旋转引擎、只能近似（不建议 1:1 复刻）

1. **`AI Blend Angle` 的“普通旋转接管”部分**：Kiss 交给 `fl856.apply()`（未知的平滑逼近曲线 + 目标切换策略）。Vape 侧只能映射到自己的 PID/`setTargetRotation`，手感必然不同；`BlendAngle` 数值（45）在两边的“等效观感”需要重新标定。
2. **`AI Yaw Only` 的“yaw 保持”**：Kiss 靠 `syncTo(fl856.yaw(), …)` 保持虚拟 yaw 不变；Vape 无虚拟 yaw 概念，只能靠 PID 增量近似。
3. **目标角抖动链**：`fl856.jitterYaw()/jitterPitch()` + `bhx.c` 摆动器（±1.5°/±1.05°、4~16 tick 随机脉冲、>16° 复位）。若要复刻观感，需**新写**一个等价的角度域抖动器（`bhx/c.java:22-50` 的公式很简单，可完整移植——但它是 Kiss 引擎的语义，不属于“AI 数学”）。
4. **`mt168` 瞄准点选择 + `md81` 的平滑判据**：Vape 用 `targetArea` + 世界坐标 jitter 近似，输入分布与训练分布已有偏移。
5. **`anchorYawToPlayer`**：Vape 不需要（直接改玩家 yaw，天然无多圈）。

### C. 建议的验收测试点

| 测试 | 期望 |
| --- | --- |
| 模型文件缺失 | 本 tick 不改变旋转（Kiss 行为），不抛异常 |
| 灵敏度 0 / 0.5 / 1.0 | 单 tick 最大角度变化 ≤ `aiMaxStep`；量化步长 = `(0.6s+0.2)³·8·0.15` |
| `carryPixels` 开/关 | 开：长期平均 = 请求角度；关：出现系统性欠转（可量化） |
| 大角度切换（>45°） | 前 3 tick（Boost）+ 大误差期（Blend）由 PID 接管；AI 恢复时速度特征首帧为 0 |
| `integralGain=0` 与 `=0.5` | 后者稳态误差更小、过冲更大；`=0` 时积分状态恒为 0 |
| `correction=None/Interpolation/Linear` | None = 纯模型；Interp 收敛更稳；Linear 每 tick 最多 5° 拉向真值 |
| 目标切换 | `prevYaw/prevPitch/积分/carry/Correction 目标角` 全部复位 |

---

## 附录 A：解码方法与可信度

- 设置名/默认值/范围、`ModuleA→21KC11KP`、`ModuleB→19KC8KP`、`mi331()=="Silent"` 等，都是把反编译源码里的**自包含常量表达式**（`new String(new char[]{...})`、`Double.longBitsToDouble(..)`、LCG 生成循环）抽出来，用 JDK 8 实际执行求值得到；LCG 映射另有独立 Python 复现交叉验证。**不依赖运行时**。
- 所有数学结论都直接对应到上文引用的行号；未还原的部分（`gp/rr/d.apply` 方法体、`mt168` 内部）已显式标注为“黑盒/未逐行还原”，未作猜测性描述。
- 模型输出量纲（度/tick）是由「引擎把它直接当度使用」反推，属于**推断结论**，已标注。

## 附录 B：混淆字段速查（`ur/m/g.java`）

| 字段 | 默认 | 语义 |
| --- | --- | --- |
| `fs256` | 90.0 | maxStepDegrees |
| `fb110` | 1.0 | stepScale |
| `fw11` | true | carryPixels |
| `fb112` | 0.0 | integralGain |
| `fb949` | 1 | correctionMode (0 None / 1 Interp / 2 Linear) |
| `fw0`,`fn131` | 0.02, 0.05 | yaw 修正速度 min/max |
| `fv661`,`fb27` | 0.02, 0.05 | pitch 修正速度 min/max |
| `fb517`,`fz697`,`fa674`,`fj644` | 0.95, 1.0, 0.35, 5.0 | assist min/max、阈值、Linear 钳制 |
| `fe48`,`fj112` | 1.5, 1.0 | yaw/pitch 倍率 |
| `fl628` | `"ModuleA"` | 模型名 |
| `fy354`,`fx208` | NaN | Correction 上一 tick 目标角 |
| `fn110`,`fy674` | 0.0 | yaw/pitch 积分 |
| `fc299`,`fd61` | 0.0 | yaw/pitch 像素余量 |
| `ft778`,`fa843`,`fq48` | 0/0/false | 上一 tick 服务器角 + 有效标志 |
| `fy322`,`fs857` | false/false | isActive / isModelAvailable |
