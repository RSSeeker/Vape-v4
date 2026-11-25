package gg.vape.rotation;

/** Pure helpers for rotation timing, quantization and target tracking. */
public final class RotationMath {
    /** Do not replay an entire pause as a single frame of controller movement. */
    public static final double MAX_CONTROLLER_ELAPSED_MILLIS = 50.0;
    /** Discard direct worker input accumulated while rendering was paused. */
    public static final long MAX_DIRECT_FRAME_GAP_NANOS = 250000000L;

    private RotationMath() {
    }

    public static double boundedElapsedMillis(double elapsedMillis) {
        if (!Double.isFinite(elapsedMillis) || elapsedMillis <= 0.0) {
            return 0.0;
        }
        return Math.min(elapsedMillis, MAX_CONTROLLER_ELAPSED_MILLIS);
    }

    public static boolean isStaleFrame(long nowNanos, long previousNanos) {
        return previousNanos != 0L && nowNanos > previousNanos
                && nowNanos - previousNanos > MAX_DIRECT_FRAME_GAP_NANOS;
    }

    /** A worker may wake more frequently than its controller should integrate. */
    public static boolean isSampleDue(long nowNanos, long previousNanos, long intervalNanos) {
        long elapsed = nowNanos - previousNanos;
        return previousNanos == 0L || elapsed < 0L || elapsed >= intervalNanos;
    }

    public static float wrapDegrees(float degrees) {
        if (!Float.isFinite(degrees)) {
            return 0.0f;
        }
        float wrapped = degrees % 360.0f;
        if (wrapped >= 180.0f) {
            wrapped -= 360.0f;
        }
        if (wrapped < -180.0f) {
            wrapped += 360.0f;
        }
        return wrapped;
    }

    /** Round a relative angular change, not an absolute view angle, to mouse steps. */
    public static float snapDelta(float delta, float step) {
        if (!Float.isFinite(delta) || !Float.isFinite(step) || step <= 0.0f) {
            return 0.0f;
        }
        double units = (double)delta / (double)step;
        double rounded = Math.copySign(Math.floor(Math.abs(units) + 0.5), units);
        double result = rounded * (double)step;
        return Double.isFinite(result) && Math.abs(result) <= Float.MAX_VALUE ? (float)result : 0.0f;
    }

    /**
     * Replace an old per-tick command, retaining only its sub-step remainder.
     * Whole steps that were not consumed are deliberately not queued again.
     */
    public static float replacePendingSteps(float previous, float degrees, float step, boolean continuing) {
        if (!Float.isFinite(degrees) || !Float.isFinite(step) || step <= 0.0f) {
            return 0.0f;
        }
        float remainder = continuing && Float.isFinite(previous) ? previous % 1.0f : 0.0f;
        float result = degrees / step + remainder;
        return Float.isFinite(result) ? result : 0.0f;
    }

    /** Time-scaled low-pass velocity estimate; invalid intervals or jumps reset it. */
    public static double smoothVelocity(double previous, double displacement, double seconds,
                                        double alphaPerTick, double maxSpeed, double maxJump) {
        if (!Double.isFinite(previous) || !Double.isFinite(displacement) || !Double.isFinite(seconds)
                || seconds <= 0.0 || seconds > 0.25 || Math.abs(displacement) > maxJump
                || !Double.isFinite(alphaPerTick) || alphaPerTick < 0.0 || alphaPerTick > 1.0
                || !Double.isFinite(maxSpeed) || maxSpeed <= 0.0) {
            return 0.0;
        }
        double measured = Math.max(-maxSpeed, Math.min(maxSpeed, displacement / seconds));
        double alpha = 1.0 - Math.pow(1.0 - alphaPerTick, seconds * 20.0);
        return previous + (measured - previous) * alpha;
    }

    /**
     * Exact critical-damping step with constant target error and feedforward
     * during this frame. Returns {angular displacement, new angular velocity}.
     */
    public static float[] springStep(float error, float velocity, float feedVelocity,
                                     double omega, double seconds) {
        if (!Float.isFinite(error) || !Float.isFinite(velocity) || !Float.isFinite(feedVelocity)
                || !Double.isFinite(omega) || omega <= 0.0
                || !Double.isFinite(seconds) || seconds <= 0.0) {
            return new float[]{0.0f, Float.isFinite(velocity) ? velocity : 0.0f};
        }
        double equilibrium = (double)error + 2.0 * (double)feedVelocity / omega;
        double coefficient = (double)velocity - omega * equilibrium;
        double decay = Math.exp(-omega * seconds);
        double displacement = equilibrium + (-equilibrium + coefficient * seconds) * decay;
        double nextVelocity = ((double)velocity - omega * coefficient * seconds) * decay;
        if (!Double.isFinite(displacement) || !Double.isFinite(nextVelocity)
                || Math.abs(displacement) > Float.MAX_VALUE || Math.abs(nextVelocity) > Float.MAX_VALUE) {
            return new float[]{0.0f, 0.0f};
        }
        return new float[]{(float)displacement, (float)nextVelocity};
    }

    /** Quantizes requested relative movement while carrying sub-step motion. */
    public static final class StepQuantizer {
        private float remainder;
        private float direction;
        private float lastStep;

        public float quantize(float delta, float step) {
            if (!Float.isFinite(delta) || !Float.isFinite(step) || step <= 0.0f || delta == 0.0f) {
                this.reset();
                return 0.0f;
            }
            float nextDirection = Math.signum(delta);
            if (this.direction != nextDirection || this.lastStep != step) {
                this.remainder = 0.0f;
            }
            float combined = delta + this.remainder;
            float snapped = RotationMath.snapDelta(combined, step);
            this.remainder = combined - snapped;
            this.direction = nextDirection;
            this.lastStep = step;
            return snapped;
        }

        public void reset() {
            this.remainder = 0.0f;
            this.direction = 0.0f;
            this.lastStep = 0.0f;
        }
    }

    /** Predicts from the previous raw sample, never from its own predicted output. */
    public static final class TargetPredictor {
        private final double maxJump;
        private final double maxSpeed;
        private final double maxLead;
        private boolean initialized;
        private double x;
        private double y;
        private double z;
        private long lastNanos;

        public TargetPredictor(double maxJump, double maxSpeed, double maxLead) {
            this.maxJump = maxJump;
            this.maxSpeed = maxSpeed;
            this.maxLead = maxLead;
        }

        public double[] predict(double x, double y, double z, long nowNanos, double leadSeconds) {
            double[] result = new double[]{x, y, z};
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                this.reset();
                return result;
            }
            if (this.initialized && nowNanos > this.lastNanos && Double.isFinite(leadSeconds)
                    && leadSeconds > 0.0) {
                double elapsed = (nowNanos - this.lastNanos) / 1.0E9;
                double dx = x - this.x;
                double dy = y - this.y;
                double dz = z - this.z;
                double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (elapsed <= 0.25 && distance > 0.0 && distance <= this.maxJump) {
                    double predictedDistance = Math.min(distance / elapsed, this.maxSpeed) * leadSeconds;
                    predictedDistance = Math.min(predictedDistance, this.maxLead);
                    double scale = predictedDistance / distance;
                    result[0] += dx * scale;
                    result[1] += dy * scale;
                    result[2] += dz * scale;
                }
            }
            this.x = x;
            this.y = y;
            this.z = z;
            this.lastNanos = nowNanos;
            this.initialized = true;
            return result;
        }

        public void reset() {
            this.initialized = false;
            this.lastNanos = 0L;
        }
    }
}
