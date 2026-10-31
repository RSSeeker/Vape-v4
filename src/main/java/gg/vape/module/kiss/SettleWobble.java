package gg.vape.module.kiss;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Port of KissClient's {@code SettleWobble} (risky/killaura/SettleWobble.java).
 *
 * <p>Once the crosshair is inside {@code enterRange} degrees of the target a damped
 * spring is fed random flicks, so the last few degrees of the aim drift like a hand
 * instead of snapping dead-on. The spring is {@code k=90, damping=7, dt=0.05} and the
 * flicks are re-rolled every 4-16 ticks with a {@code strength .. strength*3} magnitude.</p>
 */
public final class SettleWobble {
    private static final float EXIT_ERROR_SCALE = 1.6f;
    private static final float SPRING_K = 90.0f;
    private static final float SPRING_DAMPING = 7.0f;
    private static final int FLICK_MIN_TICKS = 4;
    private static final int FLICK_MAX_TICKS = 16;
    private static final float DT = 0.05f;

    private float yaw;
    private float pitch;
    private float velYaw;
    private float velPitch;
    private int flickTicks;
    private boolean active;

    public void update(float errorDegrees, float strength, float enterRange) {
        if (strength <= 0.0f || errorDegrees > enterRange * EXIT_ERROR_SCALE) {
            this.reset();
            return;
        }
        if (!this.active) {
            if (errorDegrees >= enterRange) {
                return;
            }
            this.active = true;
        }
        this.velYaw += (-SPRING_K * this.yaw - SPRING_DAMPING * this.velYaw) * DT;
        this.velPitch += (-SPRING_K * this.pitch - SPRING_DAMPING * this.velPitch) * DT;
        this.yaw += this.velYaw * DT;
        this.pitch += this.velPitch * DT;
        if (--this.flickTicks <= 0) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            float flick = strength * (1.0f + random.nextFloat() * 2.0f);
            this.velYaw += random.nextBoolean() ? flick : -flick;
            this.velPitch += random.nextBoolean() ? flick * 0.6f : -flick * 0.6f;
            this.flickTicks = FLICK_MIN_TICKS + random.nextInt(FLICK_MAX_TICKS - FLICK_MIN_TICKS + 1);
        }
        float maxWobble = strength * 1.5f;
        this.yaw = Math.max(-maxWobble, Math.min(maxWobble, this.yaw));
        this.pitch = Math.max(-maxWobble * 0.7f, Math.min(maxWobble * 0.7f, this.pitch));
    }

    public void reset() {
        this.active = false;
        this.yaw = 0.0f;
        this.pitch = 0.0f;
        this.velYaw = 0.0f;
        this.velPitch = 0.0f;
        this.flickTicks = 0;
    }

    public float getYaw() {
        return this.yaw;
    }

    public float getPitch() {
        return this.pitch;
    }
}
