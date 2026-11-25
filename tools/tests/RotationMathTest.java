import gg.vape.rotation.RotationMath;
import java.util.Random;

/** Headless tests of the pure math used by the rotation controllers. */
public final class RotationMathTest {
    private static int checks;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void close(double actual, double expected, double tolerance, String message) {
        check(Double.isFinite(actual) && Math.abs(actual - expected) <= tolerance,
                message + ": expected " + expected + ", got " + actual);
    }

    private static void testTiming() {
        close(RotationMath.boundedElapsedMillis(3.5), 3.5, 0.0, "normal frame interval");
        close(RotationMath.boundedElapsedMillis(5000.0), 50.0, 0.0, "stall is capped");
        close(RotationMath.boundedElapsedMillis(-1.0), 0.0, 0.0, "negative interval");
        close(RotationMath.boundedElapsedMillis(Double.NaN), 0.0, 0.0, "NaN interval");
        close(RotationMath.boundedElapsedMillis(Double.POSITIVE_INFINITY), 0.0, 0.0, "infinite interval");
        long interval = 8333334L;
        check(RotationMath.isSampleDue(10L, 0L, interval), "first sample");
        check(!RotationMath.isSampleDue(1001000000L, 1000000000L, interval), "1 ms is too early");
        check(RotationMath.isSampleDue(1000000000L + interval, 1000000000L, interval), "interval boundary");
        check(RotationMath.isSampleDue(1L, 2L, interval), "backward clock resets sampling");
        check(!RotationMath.isStaleFrame(100L, 0L), "first frame is not stale");
        check(!RotationMath.isStaleFrame(1250000000L, 1000000000L), "frame gap boundary");
        check(RotationMath.isStaleFrame(1250000001L, 1000000000L), "long render stall is stale");
        check(!RotationMath.isStaleFrame(1L, 2L), "backward clock is not a long frame");
        int samples = 0;
        long last = 0L;
        for (long now = 1000000000L; now < 2000000000L; now += 1000000L) {
            if (RotationMath.isSampleDue(now, last, interval)) {
                last = now;
                samples++;
            }
        }
        check(samples >= 100 && samples <= 120, "1 ms worker should not integrate 1000 times: " + samples);
    }

    private static void testRelativeSteps() {
        float step = 0.15f;
        close(RotationMath.snapDelta(0.076f, step), step, 1.0E-6, "positive step");
        close(RotationMath.snapDelta(-0.076f, step), -step, 1.0E-6, "negative step");
        close(RotationMath.snapDelta(0.075f, step), step, 1.0E-6, "positive half step");
        close(RotationMath.snapDelta(-0.075f, step), -step, 1.0E-6, "negative half step");
        close(RotationMath.snapDelta(0.149f, step), step, 1.0E-6, "off-grid current view uses relative delta");
        close(RotationMath.snapDelta(Float.NaN, step), 0.0, 0.0, "invalid input");
        close(RotationMath.snapDelta(1.0f, Float.NaN), 0.0, 0.0, "invalid step");
        close(RotationMath.wrapDegrees(-179.0f - 179.0f), 2.0, 0.0, "short yaw across positive boundary");
        close(RotationMath.wrapDegrees(179.0f - -179.0f), -2.0, 0.0, "short yaw across negative boundary");
        close(RotationMath.wrapDegrees(180.0f), -180.0, 0.0, "180 degree boundary");

        RotationMath.StepQuantizer positive = new RotationMath.StepQuantizer();
        RotationMath.StepQuantizer negative = new RotationMath.StepQuantizer();
        float sum = 0.0f;
        float negativeSum = 0.0f;
        for (int i = 0; i < 1000; i++) {
            sum += positive.quantize(0.04f, step);
            negativeSum += negative.quantize(-0.04f, step);
        }
        close(sum, 40.0, step / 2 + 0.001, "small positive deltas accumulate");
        close(negativeSum, -40.0, step / 2 + 0.001, "small negative deltas accumulate");
        RotationMath.StepQuantizer reset = new RotationMath.StepQuantizer();
        close(reset.quantize(0.07f, step), 0.0, 0.0, "fraction retained");
        close(reset.quantize(-0.02f, step), 0.0, 0.0, "direction change clears old fraction");
        reset.reset();
        reset.quantize(0.07f, step);
        reset.quantize(0.0f, step);
        close(reset.quantize(0.02f, step), 0.0, 0.0, "zero request clears old fraction");
        reset.reset();
        reset.quantize(0.07f, step);
        close(reset.quantize(0.02f, 0.30f), 0.0, 0.0, "sensitivity change clears old fraction");
    }

    private static void testPendingCommands() {
        float pending = 0.0f;
        int applied = 0;
        for (int i = 0; i < 100; i++) {
            pending = RotationMath.replacePendingSteps(pending, 0.04f, 0.15f, i != 0);
            int whole = (int)pending;
            applied += whole;
            pending -= whole;
        }
        check(applied == 26, "positive per-tick residual retained: " + applied);
        close(pending, 2.0 / 3.0, 1.0E-4, "positive residual");
        pending = 0.0f;
        applied = 0;
        for (int i = 0; i < 100; i++) {
            pending = RotationMath.replacePendingSteps(pending, -0.04f, 0.15f, i != 0);
            int whole = (int)pending;
            applied += whole;
            pending -= whole;
        }
        check(applied == -26, "negative per-tick residual retained: " + applied);
        close(pending, -2.0 / 3.0, 1.0E-4, "negative residual");
        close(RotationMath.replacePendingSteps(13.75f, 0.0375f, 0.15f, true), 1.0, 1.0E-6,
                "unconsumed whole command not replayed");
        close(RotationMath.replacePendingSteps(0.75f, 0.0375f, 0.15f, false), 0.25, 1.0E-6,
                "mode switch clears fraction");
        close(RotationMath.replacePendingSteps(Float.NaN, 0.0375f, 0.15f, true), 0.25, 1.0E-6,
                "invalid previous remainder discarded");
        close(RotationMath.replacePendingSteps(0.5f, Float.NaN, 0.15f, true), 0.0, 0.0,
                "invalid command discarded");
    }

    private static void testPrediction() {
        RotationMath.TargetPredictor predictor = new RotationMath.TargetPredictor(2.0, 20.0, 1.0);
        long time = 1000000000L;
        close(predictor.predict(100.0, 5.0, -7.0, time, 0.05)[0], 100.0, 1.0E-9, "first raw sample");
        time += 50000000L;
        close(predictor.predict(100.1, 5.0, -7.0, time, 0.05)[0], 100.2, 1.0E-9, "moving target lead");
        time += 50000000L;
        close(predictor.predict(100.2, 5.0, -7.0, time, 0.05)[0], 100.3, 1.0E-9,
                "next lead uses previous raw sample, not previous prediction");
        time += 50000000L;
        close(predictor.predict(100.2, 5.0, -7.0, time, 0.05)[0], 100.2, 1.0E-9,
                "stationary target does not oscillate");
        time += 50000000L;
        close(predictor.predict(104.0, 5.0, -7.0, time, 0.05)[0], 104.0, 1.0E-9, "teleport does not extrapolate");
        time += 1L;
        double[] bounded = predictor.predict(104.1, 5.0, -7.0, time, 1.0);
        check(bounded[0] >= 104.1 && bounded[0] <= 105.100001, "short-interval prediction bounded");
        time += 300000000L;
        close(predictor.predict(104.2, 5.0, -7.0, time, 0.05)[0], 104.2, 1.0E-9, "long gap not extrapolated");
        predictor.reset();
        close(predictor.predict(-20.0, 1.0, 2.0, time, 0.05)[0], -20.0, 1.0E-9, "new target resets history");
        time += 50000000L;
        close(predictor.predict(-19.9, 1.0, 2.0, time, 0.0)[0], -19.9, 1.0E-9, "disabled prediction");
        time += 50000000L;
        close(predictor.predict(-19.8, 1.0, 2.0, time, 0.05)[0], -19.7, 1.0E-9,
                "disabled prediction still records raw position");
    }

    private static void testSpring() {
        double[] rates = {1.0, 12.0, 50.0};
        double[] intervals = {0.1, 0.05, 1.0 / 60.0, 1.0 / 240.0};
        for (double omega : rates) {
            for (double dt : intervals) {
                float position = 0.0f;
                float velocity = 0.0f;
                int count = (int)Math.ceil(10.0 / dt);
                for (int i = 0; i < count; i++) {
                    float[] result = RotationMath.springStep(90.0f - position, velocity, 0.0f, omega, dt);
                    float next = position + result[0];
                    check(Float.isFinite(next) && Float.isFinite(result[1]), "finite spring output");
                    check(next >= position - 0.0001f && next <= 90.001f, "static target must not overshoot");
                    position = next;
                    velocity = result[1];
                }
                close(position, 90.0, 0.06, "spring converges at different frame intervals");
            }
        }
        float[] single = RotationMath.springStep(60.0f, 3.0f, 8.0f, 12.0, 0.1);
        float[] first = RotationMath.springStep(60.0f, 3.0f, 8.0f, 12.0, 0.05);
        float[] second = RotationMath.springStep(60.0f - first[0], first[1], 8.0f, 12.0, 0.05);
        close(first[0] + second[0], single[0], 1.0E-4, "split frame displacement");
        close(second[1], single[1], 1.0E-4, "split frame velocity");
        float[] pause = RotationMath.springStep(10.0f, 2.0f, 0.0f, 12.0, 0.0);
        close(pause[0], 0.0, 0.0, "zero interval does not move");
        close(pause[1], 2.0, 0.0, "zero interval preserves velocity");
        float[] hitch = RotationMath.springStep(90.0f, 0.0f, 0.0f, 50.0, 0.1);
        check(hitch[0] > 0.0f && hitch[0] < 90.0f, "high response after hitch remains bounded");
    }

    private static void testOldVelocity() {
        double a = 0.0;
        double b = 0.0;
        for (int i = 0; i < 60; i++) a = RotationMath.smoothVelocity(a, 2.0 / 60.0, 1.0 / 60.0, 0.3, 20.0, 2.0);
        for (int i = 0; i < 120; i++) b = RotationMath.smoothVelocity(b, 2.0 / 120.0, 1.0 / 120.0, 0.3, 20.0, 2.0);
        close(a, b, 1.0E-10, "velocity smoothing is time-based");
        close(RotationMath.smoothVelocity(10.0, 3.0, 0.01, 0.3, 20.0, 2.0), 0.0, 0.0, "jump clears velocity");
        close(RotationMath.smoothVelocity(10.0, 0.01, 0.5, 0.3, 20.0, 2.0), 0.0, 0.0, "long gap clears velocity");
        check(RotationMath.smoothVelocity(0.0, 1.0, 0.00001, 0.3, 20.0, 2.0) <= 20.0,
                "velocity measurement bounded");
    }

    private static void testRandomInputs() {
        Random random = new Random(42158L);
        for (int i = 0; i < 10000; i++) {
            float delta = (random.nextFloat() - 0.5f) * 360.0f;
            float step = 0.0096f + random.nextFloat() * 0.61f;
            float snapped = RotationMath.snapDelta(delta, step);
            check(Float.isFinite(snapped), "finite random quantization");
            close(RotationMath.snapDelta(-delta, step), -snapped, 1.0E-4, "symmetric random quantization");
            check(Math.abs(snapped - delta) <= step * 0.5002f, "nearest random step");
        }
    }

    public static void main(String[] args) {
        testTiming();
        testRelativeSteps();
        testPendingCommands();
        testPrediction();
        testSpring();
        testOldVelocity();
        testRandomInputs();
        System.out.println("Rotation math: " + checks + " deterministic assertions passed");
    }
}
