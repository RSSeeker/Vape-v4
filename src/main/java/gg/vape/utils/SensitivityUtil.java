package gg.vape.utils;

import gg.vape.wrapper.impl.Minecraft;

/**
 * 鼠标灵敏度相关的角度量化工具（"GCD 对齐"）。
 *
 * <p>原版把鼠标位移转换成视角的逻辑是：</p>
 * <pre>
 *   base  = sensitivity * 0.6 + 0.2
 *   scale = base * base * base * 8.0     // 每个鼠标像素对应的角度
 *   yaw  += mouseDx * scale * 0.15
 * </pre>
 * <p>也就是说，玩家每移动 1 个鼠标像素，视角只能变化
 * {@code gcd = scale * 0.15} 度，所有合法旋转的差值必然是 {@code gcd} 的整数倍。</p>
 *
 * <p>ACA / Themis / Grim 一类的 aim 检查会校验封包里的旋转差是否落在这个网格上：
 * 直接写入"任意角度"的静默旋转（无量化）会被判定为非人类输入。因此所有代替玩家转视角的
 * 模块（静默杀戮的 Kiss 引擎、自瞄的 Kiss 模式等）在写回朝向或封包前都应先经过
 * {@link #snapAngle(float)} / {@link #snapDelta(float)} 量化。</p>
 */
public final class SensitivityUtil {
    /** 原版灵敏度基数的三次方系数（鼠标像素 → 角度）。 */
    private static final float PIXEL_SCALE = 8.0f;
    /** 原版每个鼠标像素之外的 0.15 系数（像素角度 → 视角角度）。 */
    private static final float DEGREES_PER_PIXEL = 0.15f;

    private SensitivityUtil() {
    }

    /** 当前客户端的鼠标灵敏度（0.0 ~ 1.0，原版默认 0.5）。 */
    public static float currentSensitivity() {
        return Minecraft.gameSettings().y();
    }

    /** 每个鼠标像素对应的角度（未乘 0.15 的灵敏度步长）。 */
    public static float degreesPerMouseStep() {
        return degreesPerMouseStep(currentSensitivity());
    }

    /** 每个鼠标像素对应的角度（未乘 0.15 的灵敏度步长）。 */
    public static float degreesPerMouseStep(float sensitivity) {
        float base = sensitivity * 0.6f + 0.2f;
        return base * base * base * PIXEL_SCALE;
    }

    /** 服务端可见的旋转网格大小：一个鼠标像素对应的视角角度（度）。 */
    public static float gcdDegrees() {
        return gcdDegrees(currentSensitivity());
    }

    /** 服务端可见的旋转网格大小：一个鼠标像素对应的视角角度（度）。 */
    public static float gcdDegrees(float sensitivity) {
        return degreesPerMouseStep(sensitivity) * DEGREES_PER_PIXEL;
    }

    /** 把绝对角度吸附到最近的灵敏度网格上（用于静默旋转写回前）。 */
    public static float snapAngle(float degrees) {
        return snapAngle(degrees, gcdDegrees());
    }

    /** 把绝对角度吸附到最近的灵敏度网格上。 */
    public static float snapAngle(float degrees, float gcd) {
        if (gcd <= 0.0f || Float.isNaN(degrees) || Float.isInfinite(degrees)) {
            return degrees;
        }
        return Math.round(degrees / gcd) * gcd;
    }

    /** 把角度增量吸附到灵敏度网格上（用于按 delta 施加的旋转）。 */
    public static float snapDelta(float delta) {
        return snapDelta(delta, gcdDegrees());
    }

    /**
     * 吸附网格之外的随机偏移：加大约 {@code ±factor*gcd} 的偏移，用于打破
     * "每一步都精确落在网格上"的特征（ACA Aim Step / Grim AimModulo360 类检查）。
     * {@code factor <= 0} 时返回 0，保持原行为。
     */
    public static float gcdJitter(float gcd, double factor) {
        if (factor <= 0.0 || gcd <= 0.0f || Float.isNaN(gcd) || Float.isInfinite(gcd)) {
            return 0.0f;
        }
        double spread = factor * gcd;
        return (float)((java.util.concurrent.ThreadLocalRandom.current().nextDouble() * 2.0 - 1.0) * spread);
    }

    /** 把角度增量吸附到灵敏度网格上。 */
    public static float snapDelta(float delta, float gcd) {
        if (gcd <= 0.0f || delta == 0.0f || Float.isNaN(delta) || Float.isInfinite(delta)) {
            return delta;
        }
        return Math.round(delta / gcd) * gcd;
    }

    /** 视角角度 → 鼠标像素位移（配合 {@code PlayerMouseRotationApplier.applyTrackedMouseDelta}）。 */
    public static float degreesToMouseDelta(float degrees) {
        return degrees / DEGREES_PER_PIXEL;
    }

    /** 鼠标像素位移 → 视角角度。 */
    public static float mouseDeltaToDegrees(float delta) {
        return delta * DEGREES_PER_PIXEL;
    }
}
