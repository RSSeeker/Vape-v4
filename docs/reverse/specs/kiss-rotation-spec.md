# KissClient 26.2 Fabric — 自瞄旋转引擎逆向规格

> 目标文件：`C:\Users\Jiang\AppData\Local\Temp\kiss-src\p\ibr\zq\b.java`（1272 行，Vineflower 反编译，混淆名保留）
> 原始 jar：`KissFabric-1.7.3.jar`（19,416,638 字节）
> 证据来源：反编译源码 + `javap -p -c -constants` 字节码交叉验证（字节码 dump：`/tmp/kissbc/b.bc.txt`，8484 行；`apply` 自 4715 行起）
> 本文只做静态阅读记录，未修改 Vape 源码，未构建。

---

## 0. TL;DR（先看这 9 条）

1. 引擎类 `p.ibr.zq.b implements IMinecraft` 是一个**纯状态机**：它只维护两个绝对角 `serverYaw` / `serverPitch`，**从不直接写 `mc.player`**（全文件 0 处 `setYRot`/`setXRot`）。
2. 每 tick 入口是 `apply(targetYaw, targetPitch, entity, dist)`（`b.java:854`）；最终把 `serverYaw/serverPitch` 通过**增量 GCD 量化**折回玩家朝向（`b.java:1263-1270`）。
3. 速度基常数是 **`30.0F`**（`b.java:857`），不是 12.0。`12.0` 只出现在贝塞尔控制点的距离归一化 `h/12.0`（`b.java:391`）。
4. 本 jar 的 `apply()` 里模式 `switch` 的操作数是**被 javac 内联的编译期常量 `"Linear"`**（字节码 1778-1791：`StringBuilder.toString → astore 13 → aload 13 → hashCode → lookupswitch`），因此 **本构建永远只走 Linear 分支**；`Smooth / Exponential / Bezier / Adaptive / Perlin` 五个分支是**不可达代码**，但其公式完整保留在源码中（第 5 节）。
5. 同理 `if ("Linear".equals("Smooth"))`（历史环形缓冲，`b.java:858-904`）与 `if ("Linear".equals(<6字符常量>))`（尾部三角抖动，`b.java:1207-1261`）恒假 —— 死代码。
6. Linear 分支的实际数学只有 6 行（`b.java:1019-1030`）：速度上限 × 误差比例钳制 + `copySign` 逐轴推进。**没有**贝塞尔、没有 fBm、没有抖动进旋转（抖动是"加法混合在绝对角上"，见第 6 节）。
7. 真正带"人味"的量来自三处**外部叠加**（不在引擎里）：`updateJitter()` 的一阶低通三角抖动（`0.15°` 基值）、`bhx.c` 弹簧手抖、KillAura 的瞄准点抖动 `mt729()`。
8. GCD 量化用**原版公式**：`f = s*0.6 + 0.2; gcd = f³ * 8 * 0.15`（`b.java:203-214`），且量化的对象是**本 tick 的增量**（`serverYaw - 本tick起始值`），不是绝对角。
9. KillAura 会覆盖速度：`setSpeedOverride(ms294() ? 60.0F : 85.0F)`（`g/pbd/b.java:10117`）。

---

## 1. 混淆形态与阅读方法（复现要点）

整文件所有**数值常量**都被写成二目异或表达式：

```java
float var7 = Float.intBitsToFloat(921197093 ^ 1998084645);   // = 30.0F   (b.java:857)
double var3 = Double.longBitsToDouble(-3177798021860750072L ^ -1432241366811503470L); // = 1.6
int[] a = new int[-25111466 ^ -25111458];                    // = new int[8]
```

**所有字符串字面量**都被换成运行期 char[] 拼装，用两条 LCG 交叉取位（`var36`/`var37` 或 `var4`/`var5`）：

```java
var36 = var36 * 1103515245 + C;   var37 = var37 * 1664525 + C;
ch = (char)((var36 >>> 8 ^ var37 >>> 13) & 0xFFFF ^ K1 ^ K2);
```

结论：

- **不要试图肉眼读数字**，必须脚本求值（我用的是 `C:\Users\Jiang\AppData\Local\Temp\kissdec\decode.py`，一个 Java int/long 表达式求值器：`^ & | >>> >> << ~ * + -`、`(char)`/`(int)` cast、32/64 位回绕）。
- 字符串名可以只用 `javap -p -c -constants` 看 `lookupswitch` 的 **hashCode 键**，再用 Java 字符串 hash 规则（`h = h*31 + c`，32 位回绕）反查，比解 char[] 更快。
- Vineflower 对这些二目表达式不会折叠，所以源码"看起来"全是 `A ^ B`，但字节码里就是 `ldc / ldc / ixor`，确认没有失真。

---

## 2. 类结构与状态字段

```java
public final class b implements IMinecraft {
   public float serverYaw;         // b.java:27  绝对 yaw（度）
   public float serverPitch;       // b.java:29  绝对 pitch（度）
   private final g.pbd.b fb955;    // b.java:51  构造参数 = KillAura 模块（只用来读 range 设置）
}
```

### 2.1 状态字段分组（全部 `private`，名称为混淆名）

| 组 | 字段 | 含义 |
|---|---|---|
| 绝对角 | `serverYaw` / `serverPitch` | 当前引擎朝向（度），唯一对外可见状态 |
| 速度覆盖 | `fg557` | `setSpeedOverride()` 写入，`>0` 时覆盖速度上限 |
| 抖动 | `fq999` / `fu115` | yaw / pitch 抖动当前值（已是一阶低通后的**度数**） |
| 抖动原始 | `fz783` / `fw648` / `fn393` | 每 3~7 tick 重抽的三角分布样本 / 倒计时 |
| 抖动目标 | `fg435` | 当前绑定的实体（换目标即清零） |
| 噪声相位 | `fz428` | `mc45()` 的相位累加器，按 `(0.45+rnd*0.7) % 2π` 前进 |
| Perlin 相位 | `fw913` / `fv458` / `fn109` / `fr853` | fBm 累加器（Perlin 模式用，本 jar 死代码） |
| 梯度表 | `fh321` / `fx188` | 两张 512 长梯度表的相位偏移 |
| 表/缓冲 | `fi248`/`fd546`(int[256]) `fe118`/`fb455`(int[512]) `fv533`(int[256]) `fh828`(int[512]) `fb498`/`fv809`(float[8]) | 洗牌源表、梯度表、历史环形缓冲（8 长） |
| 洗牌标志 | `fg64`（梯度表已洗）/ `fk133`（Perlin 表已洗） | 懒初始化标记 |
| Bezier 状态 | `fy3` `fj87` `fp581` `fn772` `fk968` `fk354` `fu277` `fw766` `fn778` `fc527` | 起点/终点/两控制点/进度/绑定实体 |
| Smooth 状态 | `fy99` `fv856` `fr542` `fy341` `fc287` `fy550` `fc487` `fu436` `fd507` `fp597` `fw704` `fu346` `fa621` `fq825` `fs72` `fz409` `fr858` | 进度/起点/目标快照/曲线索引/混合权重/重抽计时器/历史环形指针 |
| 其它 | `fk601` | Smooth 模式的"停顿"倒计时 |

### 2.2 构造（`b.java:118-127`）

```java
this.fb498 = new float[8];
this.fv809 = new float[8];
this.fi248 = new int[256];
this.fe118 = new int[512];
this.fd546 = new int[256];
this.fb455 = new int[512];
this.fv533 = new int[256];
this.fh828 = new int[512];
this.fb955 = var1;            // KillAura 模块
```

---

## 3. 常量与配置（默认值 = 原文数值）

### 3.1 `static final` 常量（Vineflower 已折叠，`b.java:9-103`）

| 字段 | 值 | 行 | 字段 | 值 | 行 |
|---|---|---|---|---|---|
| `fp564` | `90.0F` | 9 | `fy534` | `1.0F` | 94 |
| `fg464` | `7` | 14 | `fe760` | `0.3F` | 95 |
| `fs181` | `0.5F` | 15 | `fc209` | `0.2F` | 99 |
| `fx353` | `3` | 18 | `fr548` | `1.0F` | 101 |
| `ff337` | `90` | 19 | `fs780` | `"Linear"` | 102 |
| `fo369` | `3.0F` | 21 | `fq855` | `4` | 103 |
| `fp703` | `40` | 26 | `fk859` | `"Smoothstep"` | 37 |
| `fk638` | `0.12F` | 35 | `fa666` | `0.7F` | 41 |
| `fx470` | `100` | 38 | `fk315` | `0.75F` | 48 |
| `fc661` | `3` | 42 | `ff435` | `0.12F` | 49 |
| `fk235` | `0.4F` | 53 | `ft891` | `3.0F` | 56 |
| `fy876` | `0.35F` | 57 | `fs244` | `0.35F` | 58 |
| `fp841` | `0.025F` | 61 | `fq885` | `0.04F` | 63 |
| `fe849` | `0.25F` | 64 | `ff674` | `0.0F` | 65 |
| `fd803` | `0.26F` | 67 | `fl583` | `true` | 68 |
| `fz398` | `0.5F` | 69 | `fc137` | `1.5F` | 73 |
| `fl678` | `2.0F` | 74 | `fx471` | `8` | 77 |
| `fw888` | `30.0F` | 80 | `fg281` | `0.15F` | 83 |
| `fl574` | `0.1F` | 84 | `fg393` | `0.35F` | 88 |
| `fd438` | `12.0F` | 89 | `ff552` | `30.0F` | 92 |

### 3.2 关键内联常量（均经脚本解码，行号为原文件）

| 行 | 值 | 出现处 / 用途 |
|---|---|---|
| 133 / 134 | `0.5` / `1.0001` | fBm 幅度衰减 / 频率倍增 |
| 148 | `1e-4` | 除零保护 |
| 156 / 161 / 172 | `0.35` / `0.225` / `0.75` | `mc45()` 抖动幅度三件套 |
| 205 / 206 / 211 / 212 | `0.6` / `0.2` / `8.0` / `0.15` | **GCD 公式** |
| 228 | `3.0` | 曲线 `1-(1-t)³` |
| 241 / 242 / 246 | `0.5` / `4.0` / `-2.0`,`3.0` | easeInOutCubic |
| 343 | `3.0` | smoothstep `t²(3-2t)` |
| 375 / 380 | `±90` / `0.001` | Bezier 控制点 pitch 钳制 / 退化阈值 |
| 387 / 388 | `0.2`,`0.2` | 控制点沿弦参数 `k1` 的随机基 |
| **391** | **`12.0`** | `s = clamp(h/12.0,0,1)` —— 唯一的 "12" |
| 392 | `0.12` / `90.0` | `amp = 0.12*min(h,90)*s` |
| 397 / 401 | `±90` | 控制点 pitch 钳制 |
| 420 | `1.70158` | easeOutBack 系数（`mg562`，未被调用） |
| 427 / 428 | `3.0` / `2.0` | easeOutBack 的幂 |
| 464-493 | `0.3`,`1.7`,`0.7`,`0.6`,`0.04`,`0.1`,`4096` | `mu161()` Perlin 相位 |
| 503-514 | `0.3`,`1.7`,`0.05`,`0.12`,`0.7`,`0.6`,`0.85`,`0.3`,`4096` | `mr430()` 梯度表相位 |
| 581 / 582 | `3.0` | 三次贝塞尔系数 |
| 701 | `-10.0` / `2.0` | `mu103` 指数衰减 |
| 730 | `3.7` | `fn109` 初值（Perlin 模式） |
| 782-786 | `0.5`,`0.5`,`0.62`,`0.38`,`2.3`,`0.71` | `mh925` 双余弦 |
| 804-809 | `0.70710677`,`0.35355338`,`0.1767767` | 8 向梯度表 |
| 818 | `3.0` | 曲线指数 |
| 827 | `0.45` / `0.7` | `mx337` 相位步长 |
| 828 | `6.2831855` | 2π |
| 832 / 834 / 835 / 849 | `0.35`,`0.5`,`1.0001`,`1e-4` | `mg791` fBm |
| 850 | `0.5` / `0.06` | `mg791` 抖动 |
| **857** | **`30.0`** | **速度基常数 `var7`** |
| 901 | `0.6` | 历史缓冲刷新周期基数 |
| 908 / 909 | `0.37` / `0.19` | `mc45()` 相位偏移 |
| 1022 / 1025 | `45.0` | Linear 速度拐点 |
| 1024 / 1027 | `0.5` / `4.0` | Linear 速度斜率/截距 |
| 1042-1043 | `180.0` / `0.99` | Exponential 分支 |
| 1060 | `0.04` | Smooth 模式停顿概率 |
| 1072-1081 | `0.65`,`0.35`,`0.7`,`0.3`,`0.001`,`0.5`,`0.9`,`0.2` | Smooth 曲线系数 |
| 1085-1096 | `0.175`,`0.175`,`0.5`,`0.16`,`1.5` | Smooth fBm 幅度/抖动/加速 |
| 1114-1140 | `3.0`,`0.35`,`0.5` | Smooth 阈值与混合概率 |
| 1159-1171 | `3.0`,`0.5` | Bezier 模式阈值 |
| 1185-1186 | `0.5` / `90.0` | Adaptive 分支 |
| 1201-1202 | `0.05` | Perlin 分支下限 |
| 1255-1260 | `0.025`,`0.7` | 尾部三角抖动（死代码） |
| 1269 | `-90.0` / `90.0` | 最终 pitch 钳制 |
| `-1741282306 ^ -1741282326` | **`20`** | 历史缓冲刷新周期 `fz409` |
| `-1815010626 ^ -1815010631` | **`7`** | 历史索引抖动上限 `fs72` |
| `435486359 ^ 435486399` 与 `-979679428 ^ -979679385` | **`40` / `91`** | `fp597 = rng.nextInt(40, 91)`（即 40~90） |

### 3.3 从"参数容器"读的配置

引擎**只读一个设置**：

```java
this.fb955.range.getValue().floatValue()      // b.java:446 (updateJitter) 与 b.java:1075 (Smooth)
```

即 KillAura 的**攻击距离**设置，用作抖动/速度的距离归一化分母。除此之外引擎没有任何 `Settings` 读取 —— 所有行为都是硬编码常量。

---

## 4. Helper 逐个解剖

> 行号为 `p/ibr/zq/b.java` 原文件行号。所有数值已解码。

### 4.1 `mv465()` — 清 Bezier 状态（`b.java:105-116`）

```java
this.fc527 = null;
this.fy3 = this.fj87 = this.fp581 = this.fn772 = this.fk968
        = this.fk354 = this.fu277 = this.fw766 = this.fn778 = 0.0F;
```

### 4.2 `mc193(boolean alt)` — 梯度表 fBm（`b.java:129-149`）

3 个倍频的梯度噪声和，用洗牌过的 int[512] 表当"哈希"：

```java
int[] tbl = alt ? this.fb455 : this.fe118;
float off = alt ? this.fx188 : this.fh321;
float oct = Math.max(3, 1);                     // = 3
float amp = 1.0F, sum = 0.0F, sumAmp = 0.0F, f = 1.0F;
for (int i = 0; i < oct; i++) {
   sum    += mg986(tbl, off) * amp;
   sumAmp += amp;
   amp *= 0.5F;                                  // 0.5        (b.java:134)
   f   *= 2.0001F;                               // 1.0001     (b.java:133)
   off *= f;
}
return sum / Math.max(sumAmp, 1e-4F);
```

作用：输出 ≈ `[-1,1]` 的平滑伪随机，用于给速度加 ±17.5% 的有机波动（Smooth 分支）。

### 4.3 `mc45(float t)` — 平滑随机乘子（`b.java:151-175`）

**这是引擎里唯一进入"当前构建实际执行路径"的随机项**（每 tick 被 `apply` 调 3 次）。

```java
float A = 0.35F;                        // 156
if (A <= 0.0F) return 1.0F;
float v = mh925(this.fz428 + t);        // 值域 [0,1] 的双余弦
float B = 0.225F;                       // 161
if (B > 0.0F) v = v * (1.0F - B) + B * ThreadLocalRandom.current().nextFloat();
float C = A * 0.75F;                    // = 0.2625   (172)
return 1.0F + C * (2.0F * v - 1.0F);
```

数学：`1 ± 0.2625`，即速度在 `[0.7375, 1.2625]` 之间平滑游走，叠加 22.5% 白噪声。

### 4.4 `mv790()` — 洗牌梯度表（`b.java:177-201`）

```java
for (int i = 0; i < 256; i++) this.fi248[i] = i;         // 恒等
for (int i = 255; i > 0; i--) {                          // Fisher-Yates
   int j = rnd.nextInt(i + 1);
   swap(fi248[i], fi248[j]);
}
// fd546 同理
for (int i = 0; i < 512; i++) {
   this.fe118[i] = this.fi248[i & 255];
   this.fb455[i] = this.fd546[i & 255];
}
this.fg64 = true;
```

### 4.5 `mu379()` — 原版 GCD（`b.java:203-214`）

```java
double s = mc.options.sensitivity().get();
double f = s * 0.6 + 0.2;          // 205 / 206
return (float)(f * f * f * 8.0 * 0.15);   // 211 / 212
```

- `s = 0.5`（默认）→ `f = 0.5` → **`gcd = 0.15°`**
- 这就是 Minecraft 的鼠标灵敏度→角度步长公式，直接复刻即可。

### 4.6 `ms537(a,b,c)` — lerp（`b.java:216-223`）

```java
return b + a * (c - b);
```

### 4.7 `mw998(float x)` / `mg986(int[] tbl,float x)` — 一维 value noise（`b.java:225-232` / `518-525`）

```java
int   i = (int)Math.floor(x);
float fr = x - i;
float u  = fr * fr * (3.0F - 2.0F * fr);          // smoothstep 插值权重
int   a  = tbl[i & 255];
int   b  = tbl[(i + 1) & 255];
return ms537(u, mp349(a, fr), mp349(b, fr - 1.0F));
```

### 4.8 `mp349(int i, double t)` — 8 向梯度（`b.java:800-811`）

```java
switch (i & 7) {
   case 0 ->  t;            case 1 -> -t;
   case 2 ->  t*0.70710677; case 3 -> -t*0.70710677;
   case 4 ->  t*0.35355338; case 5 -> -t*0.35355338;
   case 6 ->  t*0.1767767;  default -> -t*0.1767767;
}
```

### 4.9 缓动曲线族

| 函数 | 行 | 公式 |
|---|---|---|
| `mt937(t)` | 234-255 | `t<0.5 ? 4t³ : 1 - (-2t+2)³/2`（easeInOutCubic） |
| `my656(t)` | 527-534 | `1 - (1-t)³` |
| `mu103(t)` | 690-709 | `t>=1 ? 1 : 1 - 2^(-10t)` |
| `mr132(i,t)` | 813-820 | `0→mt937`、`1→my656`、`2→mu103`、默认 `t²(3-2t)` |
| `mq859(t)` | 257-346 | 按字符串选择曲线；**本 jar 恒等于 `"Smoothstep"` 分支 → `t²(3-2t)`** |
| `mt847(t)` | 606-613 | 曲线族**随机混合**：`v = mr132(fc487,t); if (fd507>0) v += (mr132(fu436,t)-v)*fd507` |
| `mg562(t)` | 414-434 | easeOutBack：`1 + 2.70158(t-1)³ + 1.70158(t-1)²`（**未被任何地方调用**） |
| `mg932(p0,p1,p2,p3,t)` | 563-584 | 三次贝塞尔 `u³p0 + 3u²t·p1 + 3ut²·p2 + t³p3`（`u=1-t`），系数 `3.0` 在 581/582 |

### 4.10 `mh925(float x)` — 双余弦平滑噪声（`b.java:776-798`）

```java
return 0.5F + 0.5F * (0.62F * cos(x) + 0.38F * cos(2.3F*x + 0.71F));
```

值域 `[0,1]`，周期 ≈ 2π/1 与 2π/2.3 的拍频 —— 这是"呼吸感"的来源。

### 4.11 `mx337()` — 相位推进（`b.java:826-829`）

```java
this.fz428 = (this.fz428 + (0.45F + rnd.nextFloat()*0.7F)) % 6.2831855F;
```

每 tick 相位随机前进 `0.45~1.15` 弧度，**mod 2π**；因此 `mc45()` 的"呼吸"周期是 6~14 tick。

### 4.12 `mc763(rng, amp)` — 三角分布（`b.java:406-412`）

```java
return (rng.nextFloat() + rng.nextFloat() - 1.0F) * amp;
```

两个均匀分布相加减一 = 三角分布，值域 `[-amp, amp]`，中心概率最高。

### 4.13 `updateJitter(Entity e, double dist)` — 抖动（`b.java:436-460`）

```java
float A = 0.15F;                                  // 436
if (A <= 0.0F) { ms106(); return; }
if (e != fg435) { ms106(); fg435 = e; }

float t = Mth.clamp((float)(dist / Math.max(fb955.range.getValue().floatValue(), 0.001F)), 0.0F, 1.0F);
float g = A * (1.0F + 0.75F * t * t);             // 距离越近抖动越大（t 小时小）

if (--fn393 <= 0) {
   this.fz783 = mc763(rng, g);
   this.fw648 = mc763(rng, g * 0.65F);
   this.fn393 = rng.nextInt(3, 8);                // 3~7 tick 重抽
}
this.fz783 = Mth.clamp(this.fz783, -g, g);
this.fw648 = Mth.clamp(this.fw648, -g*0.65F, g*0.65F);
this.fq999 += (this.fz783 - this.fq999) * 0.25F;   // 一阶低通 (α=0.25)
this.fu115 += (this.fw648 - this.fu115) * 0.25F;
```

输出单位是**度**，值域约 `±0.15°`（近距离），调用方**直接加到目标绝对角上**。

### 4.14 Perlin 相位推进（`mu161` 462-495 / `mr430` 497-516）

```java
// mu161(): 单表，供 Perlin 模式
float amp = 0.3F + rnd*1.7F;
if (!fk133) mz18();
float rate = 0.7F + 0.6F * mw998(fr853);
fr853 += 0.04F + rnd*0.1F;
fw913 += amp * rate;                      // wrap 到 [0,4096)

// mr430(): 双表，供 Smooth 模式
if (!fg64) mv790();
float amp = 0.3F + rnd*1.7F;
fr853 += 0.05F + rnd*0.12F;
float rate = 0.7F + 0.6F * mg986(fe118, fr853);
float d = amp * rate;
fh321 += d;
fx188 += d * (0.85F + 0.3F * rnd);
// 两者 wrap 到 [0,4096)
```

### 4.15 `mg791(float x)` — fBm 乘子（`b.java:831-852`）

```java
float A = 0.35F, oct = Math.max(3,1), decay = 0.5F, grow = Math.max(2.0F, 1.0001F) /*=2.0*/;
float amp = 1, sumAmp = 0, f = 1, sum = 0;
for (i < 3) { sum += mw998(fw913 + x) * amp; sumAmp += amp;
              amp *= decay; f *= grow; x *= f; }
sum /= Math.max(sumAmp, 1e-4F);
return 1.0F + A * sum + (rnd - 0.5F) * 0.06F;
```

### 4.16 `mz18()`（540-561）/ `reseedPerlin()`（756-774）

```java
// mz18: 洗 fv533 → 写 fh828[i] = fv533[i & 255] (i<512); fk133 = true
// reseedPerlin: mz18(); mv790(); mv743(); mt249();
```

### 4.17 `mv743()`（727-733）/ `mt249()`（711-716）/ `ms106()`（735-754）

```java
mv743: fw913=0; fv458=0; fn109=3.7F; fk133=false; fr853=0;
mt249: fh321=0; fx188=0; fr853=0; fg64=false;
ms106: fg435=null; fq999=0; fu115=0; fz783=0; fw648=0; fn393=0;
```

### 4.18 `mv194(float delta, float gcd)` — **增量 GCD 量化**（`b.java:586-604`）

```java
return delta == 0.0F ? 0.0F : Math.round(delta / gcd) * gcd;
```

- 输入是**增量**（`serverYaw - 本tick起始serverYaw`）。
- `delta == 0` 特判保留，避免 `round(0)*gcd` 产生 `-0.0` 或让"完全对齐"被破坏。

### 4.19 `anchorYawToPlayer()`（`b.java:348-368`）

```java
if (mc.player != null)
   this.serverYaw = mc.player.getYRot() + Mth.wrapDegrees(this.serverYaw - mc.player.getYRot());
```

把累积的 yaw 折回玩家 yaw 的 ±180° 邻域，防止 `serverYaw` 无限增长。

### 4.20 `mc607(...)` — 贝塞尔控制点（`b.java:370-404`）

```java
fc527 = e; fj87 = serverYaw; fp581 = serverPitch;
float dYaw   = Mth.wrapDegrees(yaw - serverYaw);
float dPitch = Mth.clamp(pitch, -90, 90) - serverPitch;
fw766 = serverYaw + dYaw;      // 终点
fn778 = serverPitch + dPitch;
fy3 = 0;
float h = (float)Math.hypot(dYaw, dPitch);
if (h <= 0.001F) {                             // 380
   fn772 = fw766; fk968 = fn778; fk354 = fw766; fu277 = fn778;
} else {
   float k1 = 0.2F + rnd*0.2F;                 // 387  0.2~0.4
   float k2 = 1.0F - (0.2F + rnd*0.2F);        // 388  0.6~0.8
   float ux = -dPitch / h, uy = dYaw / h;      // 弦的垂直单位向量
   float s  = Mth.clamp(h / 12.0F, 0.0F, 1.0F);          // 391 ← 唯一的 12
   float amp = 0.12F * Math.min(h, 90.0F) * s;           // 392
   float j1 = mc763(rnd, amp), j2 = mc763(rnd, amp);     // 三角分布
   fn772 = fj87 + dYaw*k1   + ux*j1;
   fk968 = clamp(fp581 + dPitch*k1 + uy*j1, -90, 90);     // 397
   fk354 = fj87 + dYaw*k2   + ux*j2;
   fu277 = clamp(fp581 + dPitch*k2 + uy*j2, -90, 90);     // 401
}
```

数学：在三维（yaw,pitch）平面上做**带垂直偏移的三次贝塞尔**，两个控制点分别位于弦的 20~40% 与 60~80% 处，偏移量 = 三角分布，幅度随角度差增长（最多 `0.12*90 = 10.8°`）。这就是"弧线甩枪"的几何。

---

## 5. `apply()` 的六个模式（公式完整保留，但本 jar 只走 Linear）

### 5.0 公共头（`b.java:854-971`）

```java
float var5  = Mth.wrapDegrees(yaw  - this.serverYaw);   // 854  yaw 增量（-180..180）
float var6  = pitch - this.serverPitch;                 // 856  pitch 增量
float var7  = 30.0F;                                    // 857  速度基常数

// 858-904  if ("Linear".equals("Smooth")) { 历史缓冲填充 }   ← 死代码
this.mx337();                                           // 906  相位推进
float var38 = var7 * this.mc45(0.0F);                   // 907  yaw 速度上限
float var9  = var7 * this.mc45(0.37F);                  // 908  pitch 速度上限
float var10 = this.mc45(0.19F);                         // 909  全局速率乘子
if (!"Linear".equals("Bezier")) this.mv465();           // 910  恒真 → 清贝塞尔态
float var11 = this.serverYaw;                           // 970  本 tick 起始角（供量化）
float var12 = this.serverPitch;                         // 971
```

### 5.1 Linear（`b.java:1019-1030`）—— **本 jar 唯一执行的模式**

```java
float var45 = this.fg557 > 0.0F ? this.fg557 : var38;    // 速度覆盖（KillAura 设 60/85）
float var50 = Math.abs(var5), var53 = Math.abs(var6);
float var56 = var50 > 45.0F ? var45 : Math.min(var45, var50 * 0.5F + 4.0F);
float var58 = var53 > 45.0F ? var45 : Math.min(var45, var53 * 0.5F + 4.0F);
this.serverYaw   += Math.copySign(Math.min(var50, var56), var5);
this.serverPitch += Math.copySign(Math.min(var53, var58), var6);
```

拆解：
- 误差 > 45°：全速（`speed` 上限）冲；
- 误差 ≤ 45°：线性**减速**到 `Δ*0.5 + 4`（度/tick），4° 是底部偏置 —— 这保证最后不会龟速爬行；
- 最后再用 `min(|Δ|, step)` 防过冲，`copySign` 保方向。

`Δ→0` 时 `step→4`，所以每 tick 至少走 `min(|Δ|,4)`，误差 ≤4° 时一 tick 到位（**无死区**，是"硬收敛"）。这就是默认自瞄"贴脸准"的原因。

### 5.2 Exponential（`b.java:1042-1045`）

```java
float var44 = Math.min(var38 / 180.0F, 0.99F);
float var49 = Math.min(var9  / 180.0F, 0.99F);
this.serverYaw   += var5 * var44;
this.serverPitch += var6 * var49;
```

每 tick 收敛 `speed/180` 的比例（≈ `30/180 = 16.7%`），指数逼近、永远不过冲。

### 5.3 Smooth（`b.java:1057-1146`）—— 历史快照 + 曲线随机混合 + retarget

```java
if (fk601 > 0) fk601--;                                    // 停顿期
else if (rnd.nextFloat() < 0.04F) { fk601 = 2 + rnd.nextInt(6); break; }  // 4% 概率停 2~7 tick

int idx = (fq825 - 1 - fs72 + 8) % 8;                      // 取 fs72 tick 前的历史目标
if (fr858 > 0) idx = (fq825 - 1 + 8) % 8;
float srcYaw = fb498[idx], srcPitch = fv809[idx];

float k    = 0.65F + 0.35F * (0.7F + 0.3F*rnd);            // 0.895~1.0
float near = clamp(dist / max(range, 0.001), 0, 1);
float damp = clamp(1 - 0.5F*(1-near)*(0.9F + 0.2F*rnd), 0.5F, 1.0F);   // 远=1，近=0.5~1

this.mr430();                                              // 梯度表相位推进
float m1 = 1.0F + 0.175F * this.mc193(false);
float m2 = 1.0F + 0.175F * this.mc193(true);

if (--fp597 <= 0) {
   fp597 = rnd.nextInt(40, 91);          // 40~90 tick
   fw704 = rnd.nextBoolean();
   fu346 = 1.5F + rnd.nextFloat()*1.5F;  // 1.5~3.0
   fa621 = 1 + rnd.nextInt(2);           // 1~2 tick 加速窗口
}
float b1 = 1.0F, b2 = 1.0F;
if (fa621 > 0) { fa621--; float v = 1.0F + (fu346 - 1.0F); if (fw704) b1 = v; else b2 = v; }

float errY = Math.abs(wrapDegrees(srcYaw  - fy341));
float errP = Math.abs(srcPitch - fc287);
if (e != fy550 || fy99 >= 1.0F || errY + errP > 3.0F) {    // retarget
   fy550 = e; fv856 = serverYaw; fr542 = serverPitch;
   fy341 = srcYaw; fc287 = srcPitch; fy99 = 0;
   fc487 = rnd.nextInt(4);                                 // 主曲线
   fd507 = rnd.nextFloat() < 0.35F ? rnd.nextFloat() : 0.0F;  // 35% 概率混合
   fu436 = fd507 > 0 ? rnd.nextInt(3) : 0;
   if (fu436 >= fc487) fu436++;                            // 保证两条曲线不同
}
float dYaw = wrapDegrees(fy341 - fv856), dPitch = fc287 - fr542;
float err  = Math.abs(dYaw) + Math.abs(dPitch);
if (err <= 0.5F) { serverYaw = fy341; serverPitch = fc287; fy99 = 1; }   // 收敛吸附
else {
   float span = Math.max(err / var7, 3.0F);
   float rate = k * damp * (0.5F + 0.5F*(m1*b1 + m2*b2));
   fy99 = Math.min(fy99 + var10 * rate / span, 1.0F);       // 进度推进
   float curve = mt847(fy99);                               // 曲线族随机混合
   serverYaw   = fv856 + dYaw   * curve;
   serverPitch = fr542 + dPitch * curve;
}
```

### 5.4 Bezier（`b.java:1157-1181`）

```java
float errY = Math.abs(wrapDegrees(yaw - fw766));
float errP = Math.abs(pitch - fn778);
if (e != fc527 || fy3 >= 1.0F || errY + errP > 3.0F) this.mc607(yaw, pitch, e);
float dx = fw766 - fj87, dy = fn778 - fp581;
float h  = (float)Math.hypot(dx, dy);
if (h <= 0.5F) { serverYaw = fw766; serverPitch = fn778; fy3 = 1; }
else {
   float span = Math.max(h / var7, 3.0F);
   fy3 = Math.min(fy3 + var10 / span, 1.0F);                // 注意：无 rate 系数
   float t = mq859(fy3);                                    // 本 jar = t²(3-2t)
   serverYaw   = mg932(fj87, fn772, fk354, fw766, t);
   serverPitch = mg932(fp581, fk968, fu277, fn778, t);
}
```

### 5.5 Adaptive（`b.java:1183-1189`）

```java
float var17 = var38 * (0.5F + Math.abs(var5) / 90.0F);
float var18 = var9  * (0.5F + Math.abs(var6) / 90.0F);
serverYaw   += copySign(min(|var5|, var17), var5);
serverPitch += copySign(min(|var6|, var18), var6);
```

误差越大步长越大（`0.5x ~ 1.5x` 速度）；误差小时降到 `0.5x` —— 平滑收敛。

### 5.6 Perlin（`b.java:1200-1204`）

```java
this.mu161();
float var15 = var7 * Math.max(this.mg791(this.fv458), 0.05F);
float var16 = var7 * Math.max(this.mg791(this.fn109), 0.05F);
serverYaw   += copySign(min(|var5|, var15), var5);
serverPitch += copySign(min(|var6|, var16), var6);
```

`mg791` 返回值被钳到 `≥0.05`，因此速度在 `1.5 ~ 37.5+` 之间 fBm 游走 —— 这是"手抖式"追枪。

> 注：模式名 ↔ 体块的对应关系**由字节码确定，不是猜测**：`lookupswitch`（`b.bc.txt:4889`）的 6 组 (hashCode → 分支偏移) 为
> `-2018804923→1848`(Linear)、`-97545947→2056`(Exponential)、`-1814666802→2652`(Smooth)、`1986538239→3098`(Bezier)、`-1241367914→3536`(Adaptive)、`-1907856268→4270`(Perlin)；
> 1848/2056/2652/3098/3536/4270 处是 `iconst_N; istore 14`（N=0..5），再由 `tableswitch`（`b.bc.txt:7166`）分派到源码顺序的 6 个体，即 body1..body6 依次 = Linear..Perlin。
> 交叉验证：常量碎片 `'ial'`@1034（Exponential 结尾）、`'th'`@1050（Smooth 结尾）、`'er'`@1150（Bezier 结尾）、`'in'`@1193（Perlin 结尾）。

### 5.7 尾部（`b.java:1207-1271`）—— **恒执行的部分**

```java
// 1207-1261: if ("Linear".equals(<6字常量>)) { ... }   ← 死代码
//   yaw   += (rnd-0.5)*2*0.025
//   pitch += (rnd-0.5)*2*0.025*0.7

float gcd = this.mu379();                                 // 1263
if (gcd > 0.0F) {                                         // 1264
   this.serverYaw   = var11 + mv194(this.serverYaw   - var11, gcd);   // 1265
   this.serverPitch = var12 + mv194(this.serverPitch - var12, gcd);   // 1266
}
this.serverPitch = Mth.clamp(this.serverPitch, -90.0F, 90.0F);        // 1269
this.anchorYawToPlayer();                                             // 1270
```

---

## 6. 每 tick 主流程（可实现的伪代码）

### 6.1 调用方（KillAura `g/pbd/b.java`）

`ml502(Entity)`（`g/pbd/b.java:7681-7731`）是唯一的旋转 tick：

```java
// ---- 1. 瞄准点（"Aim Y" 的来源） ----
Vec3 eye   = mc.player.getEyePosition(1.0F);
Vec3 point = this.mv794(target);              // g/pbd/b.java:10818
Vec3 d     = point.subtract(eye);
float horiz = (float)Math.sqrt(d.x*d.x + d.z*d.z);
float targetYaw   = (float)(Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0);
float targetPitch = (float)(-Math.toDegrees(Math.atan2(d.y, horiz)));
float dist = (float)eye.distanceTo(point);

// ---- 2. 外部人味层 ----
this.fe591.updateJitter(target, dist);                       // 引擎：0.15° 低通三角抖动
this.fg28.update(this.mp759(targetYaw, targetPitch), 1.0F, 10.0F);  // bhx.c 弹簧手抖

// ---- 3. 合成目标绝对角（抖动是"加在绝对角上"的） ----
float curYaw   = fe591.yaw();
float curPitch = fe591.pitch();
float dYaw   = Mth.wrapDegrees(targetYaw - curYaw);
float dPitch = Mth.wrapDegrees(targetPitch - curPitch);
float finalYaw   = curYaw   + dYaw   + fe591.jitterYaw()   + fg28.getYaw();
float finalPitch = Mth.clamp(curPitch + dPitch + fe591.jitterPitch() + fg28.getPitch(), -90.0F, 90.0F);

// 速度覆盖（g/pbd/b.java:10117）
this.fe591.setSpeedOverride(this.ms294() ? 60.0F : 85.0F);

// ---- 4. 引擎推进 ----
this.fe591.apply(finalYaw, finalPitch, target, dist);        // g/pbd/b.java:7730
```

### 6.2 引擎 `apply()` 逐 tick 完整伪代码

```java
void apply(float aimYaw, float aimPitch, Entity target, float dist) {
    // (A) 计算绝对增量
    float dYaw   = wrapDegrees(aimYaw   - serverYaw);      // -180..180
    float dPitch = aimPitch - serverPitch;
    final float V = 30.0F;                                  // 速度基常数

    // (B) 每 tick 必跑的随机人味（进入速度）
    phase = (phase + 0.45 + rnd*0.7) % (2π);                // mx337
    float vYaw   = V * mc45(0.00);                          // 1±0.2625
    float vPitch = V * mc45(0.37);
    float rate   = mc45(0.19);

    // (C) 起点快照（供最终量化）
    float yaw0 = serverYaw, pitch0 = serverPitch;

    // (D) 模式分支（本 jar 只有 Linear 可达）
    if (speedOverride > 0) vYaw = speedOverride;            // 60 / 85
    float stepY = |dYaw|  > 45 ? vYaw : min(vYaw, |dYaw|*0.5 + 4.0);
    float stepP = |dPitch|> 45 ? vYaw : min(vYaw, |dPitch|*0.5 + 4.0);
    serverYaw   += copySign(min(|dYaw|,  stepY), dYaw);
    serverPitch += copySign(min(|dPitch|, stepP), dPitch);

    // (E) 增量 GCD 量化（只量化本 tick 走了多少）
    float gcd = (s = sensitivity) -> (f=s*0.6+0.2; f*f*f*8*0.15);   // 默认 0.15°
    if (gcd > 0) {
        serverYaw   = yaw0   + quantizeDelta(serverYaw   - yaw0,   gcd);
        serverPitch = pitch0 + quantizeDelta(serverPitch - pitch0, gcd);
    }

    // (F) 收尾
    serverPitch = clamp(serverPitch, -90, +90);
    serverYaw   = player.getYRot() + wrapDegrees(serverYaw - player.getYRot());  // anchor
}
```

### 6.3 先后顺序（谁在谁之前生效）

| 顺序 | 环节 | 是否在本 jar 生效 |
|---|---|---|
| 1 | 目标角计算（`atan2` + Aim Y） | ✅（KillAura 内，引擎外） |
| 2 | 瞄准点抖动 `mt729()`（±0.1 格、每 4~8 tick 重抽） | ✅（`mv794` 内） |
| 3 | 抖动 `updateJitter`（±0.15°，低通 α=0.25） | ✅ 加在**绝对角**上 |
| 4 | 弹簧手抖 `bhx.c`（±1.5°/±1.05°，每 4~16 tick 脉冲） | ✅ 加在**绝对角**上 |
| 5 | 速度常数 `30.0` → `setSpeedOverride` 覆盖 60/85 | ✅ |
| 6 | `mc45` 速度乘子（±26.25%） | ✅ |
| 7 | Linear 递减 + 4° 底速 + 防过冲 | ✅ |
| 8 | 增量 GCD 量化 | ✅ |
| 9 | pitch clamp ±90 + yaw 折回 | ✅ |
| 10 | （死代码）历史缓冲 / 平滑曲线族 / 贝塞尔 / fBm 速度 / 尾部三角抖动 | ❌ |

---

## 7. 增量（delta）vs 绝对值（absolute）

| 量 | 类型 | 说明 |
|---|---|---|
| `serverYaw` / `serverPitch` | **绝对**（度） | 引擎状态，对外 `yaw()`/`pitch()` |
| `apply()` 的第 1/2 参数 | **绝对**（度） | 目标朝向，不是增量 |
| `var5` / `var6`（`wrapDegrees(aim - server)`） | **增量** | 每 tick 重算，不保存 |
| `var38` / `var9` / `var10` | 速度（度/tick） | 绝对速率 |
| `jitterYaw()` / `jitterPitch()` | **增量**（度偏移） | 由调用方**加**到绝对角 |
| `mv194(delta, gcd)` 的输入 | **增量** | 量化后才重新加回 `yaw0` |
| `bhx.c` 的 `fg576`/`fc835` | **增量**（度偏移） | `getYaw()/getPitch()` |
| `mt729` 的 `fz945`/`fa523`/`ft82` | **偏移量**（格） | 乘 `bbWidth/bbHeight` 后加到世界坐标 |
| `fy99` / `fy3` | 进度 `[0,1]` | 死代码分支用 |

**写回**：引擎自身**不写** `mc.player`（`b.java` 全文件 0 处 `setYRot`）。写回有两条路径：

1. `p.ibr.zq.h.apply()`（`p/ibr/zq/h.java:96-100`）
   ```java
   mc.player.setYRot(this.yaw);
   mc.player.setXRot(this.pitch);
   ```
2. 影子旋转：`ur.m.h.setTargetRotation(new p.ibr.zq.h(yaw, pitch))`（`ur/m/h.java`），由 mixin 在发包/移动包时替换 yaw/pitch；`ur.m.h.isRotating` 标志与 `r/d.java`、`gdp/g/*.java` 协作。
3. 可选"鼠标路径仿真"：`h.snapToSensitivity()` / `h.smoothYaw()` / `h.smoothPitch()`，用 `6.667 = 1/0.15` 把角度差换算成鼠标计数再乘回 `0.15`，模拟真实鼠标轨迹（`p/ibr/zq/h.java:118-160`）。

---

## 8. 移植到另一个客户端：最小状态 + 最小参数集

### 8.1 最小状态（10 个字段）

```java
float serverYaw, serverPitch;   // 绝对角（度）
float speedOverride;            // 0 = 用默认 30；KillAura 设 60/85
float phase;                    // mc45 相位，每 tick 前进 0.45~1.15 rad mod 2π
float jitterYaw, jitterPitch;   // 低通后的抖动（度）
float jitterTargetYaw, jitterTargetPitch;  // 三角分布样本
int   jitterTicks;              // 3~7 tick 重抽倒计时
Object jitterOwner;             // 换目标即清零
```

可选（要还原 Smooth/Bezier 的话再补）：`progress`、`startYaw/startPitch`、`curveIndex`、`curveMix`、两套 Perlin 相位、`int[512]` 梯度表 ×2。

### 8.2 最小参数集（照抄即可）

| 参数 | 值 | 用途 |
|---|---|---|
| 速度基常数 | `30.0F` | `speed = 30 * mc45()`；KillAura 覆盖成 `60/85` |
| 速度乘子 | `mc45`：`1 + 0.2625*(2v-1)`，`v` 混入 `22.5%` 白噪声 | 速度呼吸 |
| 相位步长 | `(0.45 + rnd*0.7) % (2π)` | 呼吸周期 6~14 tick |
| 双余弦 | `0.5 + 0.5*(0.62cos(x) + 0.38cos(2.3x+0.71))` | 呼吸波形 |
| 减速拐点 | `45.0`（度） | 远距离全速 |
| 减速公式 | `step = min(speed, |Δ|*0.5 + 4.0)` | 4° 底速 |
| 抖动基值 | `0.15`（度） | `updateJitter` |
| 抖动距离因子 | `g = 0.15 * (1 + 0.75*t²)`，`t = clamp(dist/range,0,1)` | 近距抖更大 |
| 抖动 pitch 缩比 | `0.65` | pitch 抖动更小 |
| 抖动重抽 | `nextInt(3,8)`（3~7 tick），三角分布 `mc763` | |
| 抖动低通 | `α = 0.25` | 一阶 IIR |
| GCD | `f = s*0.6+0.2; gcd = f³*8*0.15`（s=0.5 → **0.15°**） | 反作弊对齐 |
| pitch 钳制 | `[-90, +90]` | |
| 瞄准点距离模型 | `t = clamp((dist - 1.2)/1.6, 0, 1)` | `mv794` |
| 瞄准 Y | `lerp(t, 0.5, eyeHeight/bbHeight) + 0.06*t`，clamp `[0.1, 0.95]` | 玩家 `eyeHeight/bbHeight = 1.62/1.8 = 0.9` → 即"**Aim Y 0.9**"的来源 |
| 瞄准 X/Z 抖动 | `(rnd*2-1)*0.1` 格，每 4~8 tick 重抽；y 抖动 `rnd*0.06` | `mt729` |
| 瞄准点平滑 | `lerp(prev, cur, 0.3)` | 防抖 |
| 弹簧手抖 | `k=90, c=7, dt=0.05`，脉冲 `amp*(1+rnd*2)`，yaw ±1.5°、pitch ±1.05°，每 4~16 tick | `bhx.c` |

### 8.3 移植难点（按难度排序）

1. **"模式被钉死"是构建特性，不是设计**。源码里 6 个模式都在，但本 jar 的 `switch` 是编译期常量 —— 移植时若想要贝塞尔/Perlin，必须把对应分支完整实现（第 5 节给了公式），**不能靠调设置**。
2. **GCD 量化必须作用在增量上**，且要对 `delta == 0` 特判；直接量化绝对角会产生 0.15° 级的"台阶噪声"并破坏 `anchorYawToPlayer` 的连续性。
3. **抖动是"绝对角上的加法"**，不是引擎内部状态机的一部分 —— 移植时必须由调用方在 `apply()` 之前叠加，否则会被 Linear 的 `min(|Δ|, step)` 吃掉（误差 ≤4° 时一 tick 到位，抖动等于没加）。
4. **`anchorYawToPlayer()` 不可省**：它把 `serverYaw` 折回玩家的 ±180°，否则长时间自瞄后 `serverYaw` 会漂移到千度级，量化与 `wrapDegrees` 都会失真。
5. **`updateJitter` 的 `range` 分母**来自模块设置且被 `max(...,0.001)` 保护；移植时若 range 为 0，`t` 会瞬间变 1，抖动幅度翻倍 —— 必须保留这个保护。
6. **`mc45` 的相位必须每 tick 前进**（`mx337`），否则三次 `mc45` 调用会得到完全相同的乘子，速度变成固定值。
7. **60/85 的速度覆盖**是 KillAura 的模块逻辑（`ms294()` 决定），不是引擎默认；引擎默认 `30.0` 对 180° 转身需要 ~6 tick，覆盖成 85 后 2~3 tick —— 手感差异巨大，移植时要单独暴露。
8. **`mr939`（瞄准点射线裁剪）**：`mv794` 最后对 `eye→aimPoint` 射线做 AABB clip 修正（`g/pbd/b.java:2410` 起，switch 展开的长方法），细节未完全精读 —— 这是"穿墙/近身"修正层，移植时可先省略。

---

## 9. 残余未知（诚实标注）

- `mr939` 的裁剪逻辑（`g/pbd/b.java:2410-2500+`，字节码巨大）：只确认是 `eye→point` 的 AABB clip，命中点比目标点更近时用于修正；完整分支未逐条精读。
- `mq859` 的另外三个曲线名（hashCode `-1089088822` / `-681804913` / `1766046322`）未能反推出字符串；但对应数值函数已确定（见 4.9）。
- Smooth 分支的 `fy341/fc287` 与历史缓冲 `fs72` 的完整交互（因为该分支不可达，未逐行验证边界）。
- `ie/c.java`（LegitAura）的 AI 旋转模型 `fv805`（`update(player, target, curYaw, curPitch, finalYaw, finalPitch, dist) → float[2]`）是独立子系统，本文未展开。

---

## 10. 快速索引

| 想找什么 | 去哪 |
|---|---|
| 目标角公式 | `g/pbd/b.java:7713-7716`（`atan2(z,x)-90` / `-atan2(y,h)`）|
| 瞄准点 | `g/pbd/b.java:10818-10847`（`mv794`）+ `mt729`(6524) + `mg555`(3078) |
| 抖动 | `p/ibr/zq/b.java:436-460`（`updateJitter`）|
| 速度 | `p/ibr/zq/b.java:857/907-909` + `1019-1030` |
| GCD | `p/ibr/zq/b.java:203-214` + `586-604`（量化）+ `1263-1267` |
| 6 个模式 | `p/ibr/zq/b.java:1004-1205` |
| 贝塞尔控制点 | `p/ibr/zq/b.java:370-404`（`mc607`）|
| 写回 | `p/ibr/zq/h.java:96-100`（`apply()`）+ `ur/m/h.java`（`setTargetRotation`）|
| KillAura 设置覆盖 | `g/pbd/b.java:10117`（`setSpeedOverride(60/85)`）|
| 弹簧手抖 | `bhx/c.java`（81 行，`update(err, amp, thr)`，`amp=1.0, thr=10.0`）|
