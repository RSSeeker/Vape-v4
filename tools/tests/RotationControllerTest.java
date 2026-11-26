import gg.vape.event.impl.EventPreRenderTick;
import gg.vape.module.combat.AimAssist;
import gg.vape.module.combat.aimassist.AimAssistKissSubModule;
import gg.vape.module.combat.aimassist.AimAssistRotationSubModule;
import gg.vape.module.combat.aimassist.AimAssistTargetingSubModule;
import gg.vape.module.combat.automace.AutoMaceRotationController;
import gg.vape.module.combat.automace.AimSelectionRules;
import gg.vape.rotation.AdaptiveRotationController;
import gg.vape.unmap.ModeSelection;
import gg.vape.utils.RotationUtil;
import gg.vape.value.ModeValue;
import gg.vape.wrapper.impl.EntityLivingBase;
import gg.vape.wrapper.impl.Minecraft;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

/** Exercises real controller classes against deliberately small headless stubs. */
public final class RotationControllerTest {
    private static int checks;

    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }

    private static void close(double actual, double expected, double tolerance, String message) {
        check(Double.isFinite(actual) && Math.abs(actual - expected) <= tolerance,
                message + ": expected " + expected + ", got " + actual);
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void testAdaptivePending() {
        Minecraft.player.yaw = 0.0f;
        Minecraft.player.pitch = 0.0f;
        Minecraft.screen.open = false;
        AdaptiveRotationController controller = new AdaptiveRotationController(0.0f, 0.0f);
        for (int tick = 0; tick < 100; tick++) {
            controller.applyAiRotationDelta(0.04f, -0.04f);
            controller.update(Minecraft.thePlayer(), Minecraft.currentScreen());
            controller.applyPendingMovement(Minecraft.currentScreen());
        }
        close(controller.getCurrentYaw(), 3.9, 1.0E-4, "adaptive yaw fractional steps survive tick updates");
        close(controller.getCurrentPitch(), -3.9, 1.0E-4, "adaptive pitch fractional steps survive tick updates");
        check(!controller.isComplete(), "AI mode remains active");
        controller.pendingYawDelta = 13.75f;
        controller.applyAiRotationDelta(0.0375f, 0.0f);
        close(controller.pendingYawDelta, 1.0, 1.0E-6, "old whole commands are not replayed");
        controller.clearAiRotationMode();
        close(controller.pendingYawDelta, 0.0, 0.0, "clear discards pending yaw");
        controller.pendingYawDelta = 0.75f;
        controller.applyAiRotationDelta(0.0375f, 0.0f);
        close(controller.pendingYawDelta, 0.25, 1.0E-6, "new AI mode does not inherit PID remainder");
        controller.clearAiRotationMode();
        controller.setCurrentPitch(89.9f);
        controller.applyAiRotationDelta(0.0f, 1.5f);
        controller.applyPendingMovement(Minecraft.currentScreen());
        close(controller.getCurrentPitch(), 90.0, 1.0E-6, "adaptive pitch clamped");
    }

    private static void testSimpleTargetLifecycle() throws Exception {
        Minecraft.world.present = true;
        Minecraft.screen.open = false;
        Minecraft.player.yaw = 0.0f;
        Minecraft.player.pitch = 0.0f;
        AimAssist parent = new AimAssist();
        EntityLivingBase first = new EntityLivingBase();
        first.x = 1000000.0;
        first.y = 64.0;
        first.z = -2000000.0;
        parent.selected = first;
        AimAssistRotationSubModule simple = new AimAssistRotationSubModule(parent, "Simple");
        AimAssistRotationSubModule.runWorkerTick(simple);
        close(RotationUtil.predictedX, first.x, 1.0E-9, "first sample does not predict from world origin");
        close(RotationUtil.predictedZ, first.z, 1.0E-9, "first Z sample does not predict from origin");
        first.x += 0.25;
        first.z += 0.5;
        AimAssistRotationSubModule.runWorkerTick(simple);
        close(RotationUtil.predictedX, first.x + 0.25 * 1.7, 1.0E-8, "subsequent X movement is predicted");
        close(RotationUtil.predictedZ, first.z + 0.5 * 1.7, 1.0E-8, "subsequent Z movement is predicted");
        EntityLivingBase second = new EntityLivingBase();
        second.x = -3000000.0;
        second.y = 64.0;
        second.z = 4000000.0;
        parent.selected = second;
        AimAssistRotationSubModule.runWorkerTick(simple);
        close(RotationUtil.predictedX, second.x, 1.0E-9, "target switch clears previous X history");
        close(RotationUtil.predictedZ, second.z, 1.0E-9, "target switch clears previous Z history");
        parent.allowed = false;
        AimAssistRotationSubModule.runWorkerTick(simple);
        check(simple.getTarget() == null, "disabled aiming does not expose a stale target");
        second.x += 3.0;
        parent.allowed = true;
        AimAssistRotationSubModule.runWorkerTick(simple);
        close(RotationUtil.predictedX, second.x, 1.0E-9, "disabled aiming clears old motion history");
        field(AimAssistRotationSubModule.class, "horizontalMouseAccumulator").setFloat(simple, 4.0f);
        Minecraft.screen.open = true;
        float before = Minecraft.player.yaw;
        simple.onPreRenderTick(new EventPreRenderTick());
        close(Minecraft.player.yaw, before, 0.0, "no movement while screen open");
        AimAssistRotationSubModule.runWorkerTick(simple);
        Minecraft.screen.open = false;
        simple.onPreRenderTick(new EventPreRenderTick());
        close(Minecraft.player.yaw, before, 0.0, "screen pause clears queued movement");
        field(AimAssistRotationSubModule.class, "horizontalMouseAccumulator").setFloat(simple, 10.0f);
        field(AimAssistRotationSubModule.class, "lastRenderNanos").setLong(simple, System.nanoTime() - 300000000L);
        simple.onPreRenderTick(new EventPreRenderTick());
        close(Minecraft.player.yaw, before, 0.0, "render stall discards accumulated simple movement");
        close(field(AimAssistRotationSubModule.class, "horizontalMouseAccumulator").getFloat(simple), 0.0, 0.0,
                "render stall clears simple pending movement");
        Minecraft.world.present = false;
        AimAssistRotationSubModule.runWorkerTick(simple);
        check(simple.getTarget() == null, "world loss clears target");
        Minecraft.world.present = true;
        AimAssistRotationSubModule.runWorkerTick(simple);
        close(RotationUtil.predictedX, second.x, 1.0E-9, "world re-entry has no stale target position");
        simple.onDisable();
        check(simple.getTarget() == null, "disable clears target");
        close(field(AimAssistRotationSubModule.class, "horizontalVelocity").getFloat(simple), 0.0, 0.0,
                "disable clears velocity");
    }

    private static void testAdaptiveSnap() throws Exception {
        AimAssist parent = new AimAssist();
        parent.horizontalSpeed.value = 25.0;
        parent.verticalSpeed.value = 25.0;
        parent.vertical.value = true;
        parent.selected = new EntityLivingBase();
        parent.selected.x = 4.0;
        parent.selected.z = 5.0;
        AimAssistTargetingSubModule adaptive = new AimAssistTargetingSubModule(parent, "Adaptive");
        field(AimAssistTargetingSubModule.class, "target").set(adaptive, parent.selected);
        field(AimAssistTargetingSubModule.class, "yawSnapped").setBoolean(adaptive, true);
        field(AimAssistTargetingSubModule.class, "pitchSnapped").setBoolean(adaptive, true);
        Minecraft.player.x = 0.0;
        Minecraft.player.y = 0.0;
        Minecraft.player.z = 0.0;
        Minecraft.player.yaw = 0.13f;
        Minecraft.player.pitch = 0.07f;
        adaptive.onPreRenderTick(new EventPreRenderTick());
        float yawSteps = (Minecraft.player.yaw - 0.13f) / 0.15f;
        float pitchSteps = (Minecraft.player.pitch - 0.07f) / 0.15f;
        check(Math.abs(yawSteps) > 0.5f, "adaptive snap moves toward the target");
        close(yawSteps, Math.round(yawSteps), 1.0E-4, "adaptive snap uses relative yaw steps");
        close(pitchSteps, Math.round(pitchSteps), 1.0E-4, "adaptive snap uses relative pitch steps");
        float before = Minecraft.player.yaw;
        field(AimAssistTargetingSubModule.class, "pendingYaw").setFloat(adaptive, 10.0f);
        field(AimAssistTargetingSubModule.class, "lastRenderNanos").setLong(adaptive, System.nanoTime() - 300000000L);
        adaptive.onPreRenderTick(new EventPreRenderTick());
        close(Minecraft.player.yaw, before, 0.0, "render stall discards accumulated adaptive movement");
        close(field(AimAssistTargetingSubModule.class, "pendingYaw").getFloat(adaptive), 0.0, 0.0,
                "render stall clears adaptive pending movement");
        Minecraft.screen.open = true;
        adaptive.onPreRenderTick(new EventPreRenderTick());
        close(Minecraft.player.yaw, before, 0.0, "adaptive snap stops while a screen is open");
        Minecraft.screen.open = false;
        adaptive.onDisable();
        check(adaptive.getTarget() == null, "adaptive disable clears target");
    }

    private static void testKissOutput() throws Exception {
        AimAssist parent = new AimAssist();
        AimAssistKissSubModule kiss = new AimAssistKissSubModule(parent, "Kiss");
        Method apply = AimAssistKissSubModule.class.getDeclaredMethod("applyRotation", float.class, float.class);
        apply.setAccessible(true);
        Minecraft.player.yaw = 0.13f;
        Minecraft.player.pitch = 10.07f;
        apply.invoke(kiss, 0.28f, 10.22f);
        close(Minecraft.player.yaw, 0.28, 1.0E-5, "off-grid current yaw gets a relative mouse step");
        close(Minecraft.player.pitch, 10.22, 1.0E-5, "off-grid current pitch gets a relative mouse step");
        Minecraft.player.yaw = 179.97f;
        Minecraft.player.pitch = 0.0f;
        apply.invoke(kiss, -179.88f, 0.0f);
        close(Minecraft.player.yaw, 180.12, 1.0E-4, "yaw crosses boundary along the short path");
        Minecraft.player.yaw = 0.0f;
        Minecraft.player.pitch = 89.89f;
        apply.invoke(kiss, 0.0f, 92.0f);
        close(Minecraft.player.pitch, 90.0, 1.0E-5, "pitch limit applied after quantization");
        field(AimAssistKissSubModule.class, "newInit").setBoolean(kiss, true);
        field(AimAssistKissSubModule.class, "newYawVel").setFloat(kiss, 20.0f);
        Minecraft.world.entities.clear();
        kiss.onPreRenderTick(new EventPreRenderTick());
        check(!field(AimAssistKissSubModule.class, "newInit").getBoolean(kiss), "target loss resets spring state");
        close(field(AimAssistKissSubModule.class, "newYawVel").getFloat(kiss), 0.0, 0.0,
                "target loss clears spring velocity");
        ModeValue mode = (ModeValue)field(AimAssistKissSubModule.class, "aimMode").get(kiss);
        ModeSelection old = (ModeSelection)field(AimAssistKissSubModule.class, "oldMode").get(kiss);
        mode.setValue(old);
        Minecraft.player.yaw = 0.0f;
        Minecraft.player.pitch = 0.0f;
        for (int i = 0; i < 10; i++) {
            apply.invoke(kiss, Minecraft.player.yaw + 0.04f, 0.0f);
        }
        close(Minecraft.player.yaw, 0.45, 1.0E-5, "old mode accumulates small incremental requests");
        Method reset = AimAssistKissSubModule.class.getDeclaredMethod("resetState");
        reset.setAccessible(true);
        reset.invoke(kiss);
        Minecraft.player.yaw = 0.0f;
        apply.invoke(kiss, 0.04f, 0.0f);
        close(Minecraft.player.yaw, 0.0, 0.0, "new tracking state discards old remainder");
    }

    private static void testKissDistanceRanking() {
        Minecraft.world.present = true;
        Minecraft.screen.open = false;
        Minecraft.world.entities.clear();
        Minecraft.player.x = 0.0;
        Minecraft.player.y = 0.0;
        Minecraft.player.z = 0.0;
        Minecraft.player.yaw = 0.0f;
        Minecraft.player.pitch = 0.0f;
        EntityLivingBase centeredButFar = new EntityLivingBase();
        centeredButFar.z = 4.0;
        EntityLivingBase closeButOffCenter = new EntityLivingBase();
        closeButOffCenter.x = 2.0;
        Minecraft.world.entities.add(centeredButFar);
        Minecraft.world.entities.add(closeButOffCenter);
        AimAssistKissSubModule kiss = new AimAssistKissSubModule(new AimAssist(), "Kiss");
        kiss.onPreRenderTick(new EventPreRenderTick());
        check(kiss.getTarget() != null && kiss.getTarget().getObject() == closeButOffCenter,
                "Kiss Distance mode selects the closest target, not the smallest yaw error");
        Minecraft.world.entities.clear();
        kiss.onPreRenderTick(new EventPreRenderTick());
        check(kiss.getTarget() == null, "Kiss target is cleared when no candidates remain");
    }

    private static void testAutoMaceSelectionRules() {
        double best = Double.MAX_VALUE;
        check(!AimSelectionRules.isBetterCandidate(2.0, best, false, false, 0, 6),
                "unreachable nearest candidate is rejected without taking the best rank");
        check(AimSelectionRules.isBetterCandidate(3.0, best, false, true, 4, 6),
                "farther reachable candidate is still eligible");
        best = 3.0;
        check(!AimSelectionRules.isBetterCandidate(4.0, best, true, false, 0, 6),
                "farther direct candidate does not replace a nearer reachable candidate");
        check(AimSelectionRules.isBetterCandidate(1.0, best, true, false, 0, 6),
                "nearer directly reachable candidate wins");
        check(!AimSelectionRules.isBetterCandidate(1.0, best, false, true, 7, 6),
                "impact beyond the aiming horizon is rejected");
        check(!AimSelectionRules.isBetterCandidate(Double.NaN, best, true, true, 1, 6),
                "non-finite distances are rejected");
    }

    private static void testAutoMaceControllerLifecycle() {
        Minecraft.player.yaw = 0.0f;
        Minecraft.player.pitch = 0.0f;
        AutoMaceRotationController controller = new AutoMaceRotationController(false);
        check(!controller.isSilent(), "AutoMace exposes the constructed visible mode");
        check(new AutoMaceRotationController(true).isSilent(), "AutoMace exposes the constructed silent mode");
        EntityLivingBase first = new EntityLivingBase();
        controller.setTargetEntity(first);
        controller.setPrediction(true, 1, 0, 0, 0, 4, 0, 4, true);
        controller.refreshTargetRotation();
        check(controller.isCommittedToAim(), "directly reachable target commits aim");
        controller.pendingYawDelta = 3.0f;
        controller.pendingPitchDelta = -2.0f;
        controller.setTargetEntity(new EntityLivingBase(first.getObject()));
        check(controller.isCommittedToAim(), "a new wrapper for the same entity preserves commitment");
        EntityLivingBase second = new EntityLivingBase();
        controller.setTargetEntity(second);
        check(!controller.isCommittedToAim(), "new entity clears the previous commitment");
        close(controller.pendingYawDelta, 0.0, 0.0, "new entity clears pending yaw");
        close(controller.pendingPitchDelta, 0.0, 0.0, "new entity clears pending pitch");
        controller.setPrediction(true, 6, 0, 0, 0, 4, 0, 4, false);
        controller.refreshTargetRotation();
        check(!controller.isCommittedToAim(), "far impact does not inherit immediate commitment");
        controller.pendingYawDelta = 4.0f;
        controller.clearTarget();
        close(controller.pendingYawDelta, 0.0, 0.0, "clearing target drops pending movement");
    }

    private static void testWorkerRenderConcurrency() throws Exception {
        final AimAssist parent = new AimAssist();
        parent.selected = new EntityLivingBase();
        parent.selected.x = 100.0;
        parent.selected.z = 10.0;
        final AimAssistRotationSubModule simple = new AimAssistRotationSubModule(parent, "Simple");
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        Thread worker = new Thread(new Runnable() { public void run() {
            try { for (int i = 0; i < 2000; i++) AimAssistRotationSubModule.runWorkerTick(simple); }
            catch (Throwable error) { failure.set(error); }
        }});
        Thread render = new Thread(new Runnable() { public void run() {
            try { for (int i = 0; i < 2000; i++) simple.onPreRenderTick(new EventPreRenderTick()); }
            catch (Throwable error) { failure.set(error); }
        }});
        worker.setDaemon(true);
        render.setDaemon(true);
        worker.start();
        render.start();
        worker.join(10000L);
        render.join(10000L);
        check(!worker.isAlive() && !render.isAlive(), "worker/render complete without deadlock");
        check(failure.get() == null, "worker/render raised " + failure.get());
        check(Float.isFinite(Minecraft.player.yaw) && Float.isFinite(Minecraft.player.pitch), "render remains finite");
    }

    public static void main(String[] args) throws Exception {
        testAdaptivePending();
        testSimpleTargetLifecycle();
        testAdaptiveSnap();
        testKissOutput();
        testKissDistanceRanking();
        testAutoMaceSelectionRules();
        testAutoMaceControllerLifecycle();
        testWorkerRenderConcurrency();
        System.out.println("Rotation controllers: " + checks + " headless integration assertions passed");
    }
}
