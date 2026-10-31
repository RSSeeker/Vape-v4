package gg.vape.module.combat.silentaura;

import gg.vape.rotation.RotationManager;
import gg.vape.utils.MathUtil;
import java.util.Random;

/**
 * KissClient 26.2 自瞄旋转引擎（{@code p/ibr/zq/b.java}，Linear 模式）的等价移植。
 *
 * <p>Kiss 的引擎内部只保存 {@code serverYaw}/{@code serverPitch} 两个绝对角，每 tick 拿"目标绝对角"
 * 与它们做差后推进一步；本 jar 的构建把模式 switch 内联成了编译期常量 {@code "Linear"}，
 * 因此实际运行的数学只有四步：</p>
 *
 * <ol>
 *   <li>速度基准 {@code 30 * mc45()}（KillAura 会覆盖成 60/85）；</li>
 *   <li>{@code |Δ| > 45°} 时全速冲，否则线性减速到 {@code |Δ| * 0.5 + 4}（4°/tick 底速，无死区）；</li>
 *   <li>用 {@code copySign(min(|Δ|, step))} 防过冲；</li>
 *   <li>把本 tick <b>增量</b> 按原版鼠标灵敏度 GCD（{@code (s*0.6+0.2)³*8*0.15}，默认 0.15°）量化。</li>
 * </ol>
 *
 * <p>另外移植了两层"人味"：{@code mc45()} 的双余弦速度呼吸（相位每 tick 前进 0.45~1.15 rad），
 * 以及 {@code updateJitter()} 的三角分布 ±0.15° 低频抖动（距离越近幅度越大，3~7 tick 重抽，α=0.25 低通）。
 * 抖动按 Kiss 的原始做法加在<b>目标绝对角</b>上，否则会被 Linear 的 {@code min} 吃掉。</p>
 *
 * <p>本类不持有绝对角状态：Vape 的 {@link gg.vape.rotation.AdaptiveRotationController} 已经维护当前旋转，
 * 因此只需输出每 tick 的增量，避免两套绝对角互相漂移。Kiss 的 {@code anchorYawToPlayer()} 对应的
 * 折回逻辑由调用方传入的当前 yaw 天然满足。</p>
 */
public final class KissRotationEngine {
    /** Kiss {@code b.java:857}，未被 KillAura 覆盖时使用。 */
    private static final float BASE_SPEED = 30.0f;
    /** Linear 分支的减速拐点（度）。 */
    private static final float FAST_ANGLE = 45.0f;
    /** Linear 分支的底部偏置（度/tick）。 */
    private static final float MIN_STEP = 4.0f;
    /** Linear 分支的比例系数。 */
    private static final float STEP_SCALE = 0.5f;
    /** {@code mc45()} 的振幅：0.35 * 0.75。 */
    private static final float SPEED_AMPLITUDE = 0.35f * 0.75f;
    /** {@code mc45()} 混入的白噪声比例。 */
    private static final float NOISE_MIX = 0.225f;
    private static final float TWO_PI = 6.2831855f;
    /** {@code updateJitter()} 的抖动基值（度）。 */
    private static final float JITTER_BASE = 0.15f;

    private final Random random = new Random();

    /** {@code mc45()} 相位。 */
    private float phase;
    /** 低通后的抖动偏移（度）。 */
    private float jitterYaw;
    private float jitterPitch;
    /** 抖动幅度倍率（"Kiss jitter" 设置，1.0 = Kiss 原版 0.15° 基值）。 */
    private float jitterScale = 1.0f;
    /** 三角分布样本（度）。 */
    private float targetJitterYaw;
    private float targetJitterPitch;
    private int jitterTicks;
    private Object jitterOwner;

    /** "Kiss jitter" 设置：直接缩放抖动幅度（0 表示关掉抖动）。 */
    public void setJitterScale(float scale) {
        this.jitterScale = Math.max(0.0f, scale);
    }

    public void reset() {
        this.phase = 0.0f;
        this.jitterYaw = 0.0f;
        this.jitterPitch = 0.0f;
        this.targetJitterYaw = 0.0f;
        this.targetJitterPitch = 0.0f;
        this.jitterTicks = 0;
        this.jitterOwner = null;
    }

    /** 换目标时清零抖动状态（Kiss {@code b.java:436-460}）。 */
    public void setTarget(Object target) {
        if (this.jitterOwner != target) {
            this.jitterOwner = target;
            this.jitterYaw = 0.0f;
            this.jitterPitch = 0.0f;
            this.targetJitterYaw = 0.0f;
            this.targetJitterPitch = 0.0f;
            this.jitterTicks = 0;
        }
    }

    /**
     * 计算本 tick 的旋转增量。
     *
     * @param aimYaw        目标绝对 yaw（度）
     * @param aimPitch      目标绝对 pitch（度）
     * @param currentYaw    当前旋转 yaw（度）
     * @param currentPitch  当前旋转 pitch（度）
     * @param speedOverride Kiss 的速度覆盖（度/tick），{@code <= 0} 时退回 {@code 30 * mc45()}
     * @param jitterEnabled 是否叠加 {@code updateJitter()} 的低频抖动
     * @param distance      与目标的距离
     * @param range         抖动距离归一化用的射程
     * @return {@code {deltaYaw, deltaPitch}}（度，已按 GCD 量化）
     */
    public float[] compute(float aimYaw, float aimPitch, float currentYaw, float currentPitch,
                           float speedOverride, boolean jitterEnabled, double distance, double range) {
        this.advancePhase();
        if (jitterEnabled) {
            this.advanceJitter(distance, range);
            aimYaw += this.jitterYaw;
            aimPitch = MathUtil.clamp(aimPitch + this.jitterPitch, -90.0f, 90.0f);
        }

        float deltaYaw = MathUtil.wrapAngleTo180(aimYaw - currentYaw);
        float deltaPitch = aimPitch - currentPitch;

        // Kiss RotationEngine.apply(): Linear 分支取 baseStep = speedOverride > 0 ? override : speedYaw，
        // 且 yaw / pitch 两根轴共用这个 baseStep（speedPitch 只被 Exponential 分支使用）。
        float baseStep = speedOverride > 0.0f ? speedOverride : BASE_SPEED * this.smoothMultiplier(0.0f);

        float stepYaw = Math.abs(deltaYaw) > FAST_ANGLE
                ? baseStep
                : Math.min(baseStep, Math.abs(deltaYaw) * STEP_SCALE + MIN_STEP);
        float stepPitch = Math.abs(deltaPitch) > FAST_ANGLE
                ? baseStep
                : Math.min(baseStep, Math.abs(deltaPitch) * STEP_SCALE + MIN_STEP);

        float appliedYaw = Math.copySign(Math.min(Math.abs(deltaYaw), stepYaw), deltaYaw);
        float appliedPitch = Math.copySign(Math.min(Math.abs(deltaPitch), stepPitch), deltaPitch);

        // Kiss b.java:1263-1267 / mv194:586-604 —— 量化对象是本 tick 增量，delta == 0 有特判。
        float gcd = perPixelAngle();
        if (gcd > 0.0f) {
            appliedYaw = quantiseDelta(appliedYaw, gcd);
            appliedPitch = quantiseDelta(appliedPitch, gcd);
        }
        return new float[]{appliedYaw, appliedPitch};
    }

    /** Kiss {@code mu379()} / {@code mv194()}：原版鼠标灵敏度对应的每像素角度，默认 0.15°。 */
    public static float perPixelAngle() {
        float sensitivity = RotationManager.INSTANCE.getMouseSensitivity();
        float base = sensitivity * 0.6f + 0.2f;
        return base * base * base * 8.0f * 0.15f;
    }

    private static float quantiseDelta(float delta, float gcd) {
        return delta == 0.0f ? 0.0f : Math.round(delta / gcd) * gcd;
    }

    /** Kiss {@code mx337()}：每 tick 让 {@code mc45()} 的呼吸相位随机前进 0.45~1.15 rad（mod 2π）。 */
    private void advancePhase() {
        this.phase = (this.phase + 0.45f + this.random.nextFloat() * 0.7f) % TWO_PI;
    }

    /**
     * Kiss {@code mc45(float)}：{@code 1 + 0.2625 * (2v - 1)}，其中
     * {@code v = 0.5 + 0.5 * (0.62 cos x + 0.38 cos(2.3x + 0.71))} 再混入 22.5% 白噪声。
     */
    private float smoothMultiplier(float offset) {
        float x = this.phase + offset;
        float wave = 0.5f + 0.5f * (0.62f * (float)Math.cos(x) + 0.38f * (float)Math.cos(2.3f * x + 0.71f));
        wave = wave * (1.0f - NOISE_MIX) + NOISE_MIX * this.random.nextFloat();
        return 1.0f + SPEED_AMPLITUDE * (2.0f * wave - 1.0f);
    }

    /**
     * Kiss {@code updateJitter(Entity, double)}：三角分布重抽 + 一阶低通。抖动幅度随距离变化
     * {@code 0.15 * (1 + 0.75 t²)}，pitch 缩比 0.65。
     */
    private void advanceJitter(double distance, double range) {
        float t = MathUtil.clamp((float)(distance / Math.max(range, 0.001)), 0.0f, 1.0f);
        float amplitude = JITTER_BASE * this.jitterScale * (1.0f + 0.75f * t * t);
        if (--this.jitterTicks <= 0) {
            this.targetJitterYaw = triangle(amplitude);
            this.targetJitterPitch = triangle(amplitude * 0.65f);
            this.jitterTicks = 3 + this.random.nextInt(5);
        }
        this.targetJitterYaw = MathUtil.clamp(this.targetJitterYaw, -amplitude, amplitude);
        this.targetJitterPitch = MathUtil.clamp(this.targetJitterPitch, -amplitude * 0.65f, amplitude * 0.65f);
        this.jitterYaw += (this.targetJitterYaw - this.jitterYaw) * 0.25f;
        this.jitterPitch += (this.targetJitterPitch - this.jitterPitch) * 0.25f;
    }

    /** Kiss {@code mc763}：两个均匀分布相减得到三角分布，值域 {@code [-amp, amp]}。 */
    private float triangle(float amplitude) {
        return (this.random.nextFloat() + this.random.nextFloat() - 1.0f) * amplitude;
    }
}
