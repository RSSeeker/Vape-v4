package gg.vape.module.combat.aimassist;

import gg.vape.event.EventHandler;
import gg.vape.event.impl.EventPreRenderTick;
import gg.vape.mapping.MappedClasses;
import gg.vape.module.Mod;
import gg.vape.module.SubModule;
import gg.vape.module.combat.AimAssist;
import gg.vape.module.none.ClientSettings;
import gg.vape.rotation.PlayerMouseRotationApplier;
import gg.vape.Vape;
import gg.vape.rotation.RotationManager;
import gg.vape.rotation.RotationMath;
import gg.vape.unmap.ModeOption;
import gg.vape.unmap.ModeSelection;
import gg.vape.utils.MathUtil;
import gg.vape.utils.SensitivityUtil;
import gg.vape.utils.Vec3d;
import gg.vape.value.BooleanValue;
import gg.vape.value.ModeValue;
import gg.vape.value.NumberValue;
import gg.vape.wrapper.impl.Entity;
import gg.vape.wrapper.impl.EntityLivingBase;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.Minecraft;
import java.util.ArrayList;
import java.util.List;

/**
 * AimAssist's Kiss-inspired New and Old controllers. New uses a
 * time-stepped spring and bounded target prediction; Old uses incremental
 * smoothing and carries fractional mouse movement. Both apply relative
 * sensitivity-sized changes unless the optional off-grid jitter is enabled.
 * Target filtering still uses the parent module's existing settings.
 */
public class AimAssistKissSubModule
extends SubModule<AimAssist> {
    private static final int MAX_LOST_TICKS = 20;
    private static final float MAX_TARGET_ANG_VEL = 540.0f;
    private static final double MAX_TARGET_JUMP = 2.0;
    private static final double MAX_FRAME_DT = 0.1;
    private static final double Y_PREDICT_ALPHA = 0.3;
    private static final double NATURAL_OFFSET = 0.01;

    private final ModeSelection distanceMode = new ModeOption("Distance");
    private final ModeSelection healthMode = new ModeOption("Health");
    private final ModeSelection singleMode = new ModeOption("Single");
    private final ModeSelection newMode = new ModeOption("New");
    private final ModeSelection oldMode = new ModeOption("Old");

    private final ModeValue targetMode;
    private final ModeValue aimMode;
    private final NumberValue aimX;
    private final NumberValue aimY;
    private final NumberValue aimZ;
    private final NumberValue range;
    private final BooleanValue optimalRange;
    private final NumberValue fov;
    private final NumberValue smooth;
    private final NumberValue pitchSmooth;
    private final NumberValue yPredict;
    private final NumberValue yEmaAlpha;
    private final NumberValue yDeadzone;
    private final NumberValue newResponse;
    private final NumberValue newFeedforward;
    private final NumberValue newPredict;
    private final NumberValue newDeadzone;
    private final NumberValue offGridJitter;

    private EntityLivingBase target;
    private Object lockedTargetObject;
    private boolean wasAttackDown;

    private boolean yStateInitialized;
    private double lastTargetY;
    private long lastOldTargetNanos;
    private double targetVelocityY;
    private float smoothedPitchTarget = Float.NaN;
    private int targetLostTicks;
    private double lastKnownVelocityY;
    private double curOffYaw;
    private double curOffPitch;
    private double tgtOffYaw;
    private double tgtOffPitch;
    private int offTimer;

    private boolean newInit;
    private Object trackedTargetObject;
    private ModeSelection trackedAimMode;
    private final RotationMath.TargetPredictor newPredictor = new RotationMath.TargetPredictor(MAX_TARGET_JUMP, 20.0, 1.0);
    private final RotationMath.StepQuantizer oldYawQuantizer = new RotationMath.StepQuantizer();
    private final RotationMath.StepQuantizer oldPitchQuantizer = new RotationMath.StepQuantizer();
    private long lastNewDesiredNanos;
    private float lastNewDesiredYaw;
    private float lastNewDesiredPitch;
    private float newYaw;
    private float newPitch;
    private float newYawVel;
    private float newPitchVel;
    private long lastNewFrameNanos;
    private float lastNewFrameDt;

    public AimAssistKissSubModule(Mod parent, String name) {
        super(parent, name);
        this.targetMode = ModeValue.create(this, "Target mode", "How Kiss AutoAim prioritizes targets", this.distanceMode, this.healthMode, this.distanceMode, this.singleMode);
        this.aimMode = ModeValue.create(this, "Aim mode", "New: spring-mass tracking with feedforward and prediction.\nOld: per-frame smoothing with adaptive pitch.", this.newMode, this.newMode, this.oldMode);
        this.aimX = NumberValue.create(this, "Aim X", "#.##", "", -0.5, 0.0, 0.5, 0.01);
        this.aimY = NumberValue.create(this, "Aim Y", "#.##", "", 0.0, 0.85, 1.0, 0.01);
        this.aimZ = NumberValue.create(this, "Aim Z", "#.##", "", -0.5, 0.0, 0.5, 0.01);
        this.range = NumberValue.create(this, "Range", "#.#", "m", 1.0, 5.0, 30.0, 0.5);
        this.optimalRange = BooleanValue.create(this, "Optimal range", true);
        this.fov = NumberValue.create(this, "FOV", "#", "°", 1.0, 120.0, 360.0, 1.0);
        this.smooth = NumberValue.create(this, "Smooth", "#.#", "", 1.0, 20.0, 90.0, 1.0);
        this.pitchSmooth = NumberValue.create(this, "Pitch smooth", "#.#", "", 1.0, 12.0, 60.0, 1.0);
        this.yPredict = NumberValue.create(this, "Y predict", "#.##", "", 0.0, 0.6, 1.0, 0.05);
        this.yEmaAlpha = NumberValue.create(this, "Y EMA", "#.##", "", 0.05, 0.25, 1.0, 0.05);
        this.yDeadzone = NumberValue.create(this, "Y deadzone", "#.##", "°", 0.0, 0.5, 3.0, 0.05);
        this.newResponse = NumberValue.create(this, "New response", "#.#", "", 1.0, 12.0, 50.0, 0.5);
        this.newFeedforward = NumberValue.create(this, "New feedforward", "#.##", "", 0.0, 0.6, 1.0, 0.05);
        this.newPredict = NumberValue.create(this, "New predict", "#.#", "", 0.0, 1.0, 3.0, 0.1);
        this.newDeadzone = NumberValue.create(this, "New deadzone", "#.##", "°", 0.0, 0.1, 2.0, 0.05);
        // Kiss AutoAim：New / Old 两套控制器各自只显示自己的调参项。
        this.aimMode.addModeDependentValues((ModeOption)this.newMode, this.newResponse, this.newFeedforward,
                this.newPredict, this.newDeadzone);
        this.aimMode.addModeDependentValues((ModeOption)this.oldMode, this.smooth, this.pitchSmooth,
                this.yPredict, this.yEmaAlpha, this.yDeadzone);
        this.offGridJitter = NumberValue.create(this, "Off-grid jitter", "#.##", "", 0.0, 0.0, 1.0, 0.05,
                "Adds a small random offset on top of the GCD grid so aim steps are not perfectly aligned. 0 = off.");
        this.addValue(this.targetMode, this.aimMode, this.aimX, this.aimY, this.aimZ, this.range, this.optimalRange,
                this.fov, this.smooth, this.pitchSmooth, this.yPredict, this.yEmaAlpha, this.yDeadzone,
                this.newResponse, this.newFeedforward, this.newPredict, this.newDeadzone, this.offGridJitter);
    }

    @Override
    public void onEnable() {
        this.resetState();
    }

    @Override
    public void onDisable() {
        this.target = null;
        this.lockedTargetObject = null;
        this.resetState();
    }

    private void resetState() {
        this.yStateInitialized = false;
        this.lastOldTargetNanos = 0L;
        this.lastTargetY = 0.0;
        this.targetVelocityY = 0.0;
        this.smoothedPitchTarget = Float.NaN;
        this.targetLostTicks = 0;
        this.lastKnownVelocityY = 0.0;
        this.newInit = false;
        this.newPredictor.reset();
        this.oldYawQuantizer.reset();
        this.oldPitchQuantizer.reset();
        this.trackedTargetObject = null;
        this.trackedAimMode = null;
        this.lastNewDesiredNanos = 0L;
        this.newYawVel = 0.0f;
        this.newPitchVel = 0.0f;
        this.lastNewFrameNanos = 0L;
        this.lastNewFrameDt = 0.0f;
    }

    @EventHandler
    public void onPreRenderTick(EventPreRenderTick event) {
        if (event.getWorld().isNull()) {
            this.target = null;
            this.resetState();
            return;
        }
        EntityPlayerSP player = Minecraft.thePlayer();
        if (player.isNull() || Minecraft.currentScreen().getObject() != null) {
            this.target = null;
            this.resetState();
            return;
        }
        AimAssist aimAssist = this.getParent();
        if (!aimAssist.canAim() || !ClientSettings.INSTANCE.inputEnabled) {
            this.target = null;
            this.resetState();
            return;
        }
        boolean attackDown = gg.vape.config.ClientSettings.isAttackButtonDown();
        if (this.getParent().getRequireMouseDown().getEffectiveValue() && !attackDown) {
            this.wasAttackDown = false;
            this.target = null;
            this.resetState();
            return;
        }

        boolean single = this.targetMode.getValue() == this.singleMode;
        if (single && attackDown && !this.wasAttackDown) {
            this.lockedTargetObject = null;
            this.resetState();
        }
        this.wasAttackDown = attackDown;

        EntityLivingBase found = this.findTarget(this.range.getValue(), this.fov.getValue());
        if (found == null) {
            if (this.aimMode.getValue() == this.oldMode) {
                this.handleTargetLost();
            } else {
                this.handleNewTargetLost();
            }
            this.target = null;
            return;
        }
        ModeSelection selectedAimMode = (ModeSelection)this.aimMode.getValue();
        if (this.trackedTargetObject != found.getObject() || this.trackedAimMode != selectedAimMode) {
            this.resetState();
            this.trackedTargetObject = found.getObject();
            this.trackedAimMode = selectedAimMode;
        }
        this.target = found;
        this.targetLostTicks = 0;

        Vec3d eye = this.eyePosition(player);
        Vec3d targetPoint = this.getTargetPoint(found);
        float currentYaw = RotationManager.getViewYaw(player);
        float currentPitch = RotationManager.getViewPitch(player);
        if (selectedAimMode == this.newMode) {
            this.runNewAim(targetPoint, currentYaw, currentPitch);
        } else {
            this.runOldAim(found, targetPoint, eye, currentYaw, currentPitch);
        }
    }

    private EntityLivingBase findTarget(double maxRange, double fovDegrees) {
        EntityPlayerSP player = Minecraft.thePlayer();
        AimAssist parent = this.getParent();
        List<EntityLivingBase> candidates = new ArrayList<EntityLivingBase>();
        for (Object object : new ArrayList<Object>(Minecraft.theWorld().z())) {
            Entity wrapper = new Entity(object);
            if (wrapper.isNull()) {
                continue;
            }
            if (gg.vape.config.ClientSettings.IS_LEGACY_1_7 && wrapper.isInstance(MappedClasses.FT)) {
                continue;
            }
            if (!wrapper.isInstance(MappedClasses.zm)) {
                continue;
            }
            EntityLivingBase living = new EntityLivingBase(object);
            if (living.isNull() || living.getObject() == player.getObject()) {
                continue;
            }
            if (living.w$src$F$15l9epb() <= 0.0f || living.M$src$Z$ff28xj()) {
                continue;
            }
            if (parent.isFriendTarget(living) || !parent.isTargetFiltered(living)) {
                continue;
            }
            if (this.distanceTo(living, player) > maxRange) {
                continue;
            }
            candidates.add(living);
        }
        if (candidates.isEmpty()) {
            return null;
        }
        if (this.targetMode.getValue() == this.singleMode && this.lockedTargetObject != null) {
            for (EntityLivingBase candidate : candidates) {
                if (this.lockedTargetObject == candidate.getObject()
                        && this.yawToTarget(candidate, player) <= fovDegrees) {
                    return candidate;
                }
            }
            this.lockedTargetObject = null;
        }
        EntityLivingBase best = null;
        double bestValue = Double.MAX_VALUE;
        for (EntityLivingBase candidate : candidates) {
            double yawDiff = this.yawToTarget(candidate, player);
            if (yawDiff > fovDegrees) {
                continue;
            }
            double compare;
            if (this.targetMode.getValue() == this.healthMode) {
                compare = candidate.w$src$F$15l9epb();
            } else if (this.targetMode.getValue() == this.singleMode) {
                compare = this.distanceTo(candidate, player);
            } else {
                compare = yawDiff;
            }
            if (compare < bestValue) {
                bestValue = compare;
                best = candidate;
            }
        }
        if (this.targetMode.getValue() == this.singleMode && best != null) {
            this.lockedTargetObject = best.getObject();
        }
        return best;
    }

    private boolean isValidTarget(EntityLivingBase candidate, double maxRange) {
        if (candidate.isNull() || candidate.M$src$Z$ff28xj() || candidate.w$src$F$15l9epb() <= 0.0f) {
            return false;
        }
        if (candidate.equals(Minecraft.thePlayer())) {
            return false;
        }
        return this.distanceTo(candidate, Minecraft.thePlayer()) <= maxRange;
    }

    private double distanceTo(EntityLivingBase candidate, EntityPlayerSP player) {
        boolean optimal = this.optimalRange.getEffectiveValue() && this.aimMode.getValue() == this.newMode;
        if (optimal) {
            Vec3d eye = this.eyePosition(player);
            double dx = candidate.z() - eye.getX();
            double dy = candidate.N() - eye.getY();
            double dz = candidate.h() - eye.getZ();
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
        return player.getDistanceToEntity(candidate);
    }

    private double yawToTarget(EntityLivingBase candidate, EntityPlayerSP player) {
        Vec3d eye = this.eyePosition(player);
        Vec3d point = this.getTargetPoint(candidate);
        float yaw = (float)(Math.toDegrees(Math.atan2(point.getZ() - eye.getZ(), point.getX() - eye.getX())) - 90.0);
        return Math.abs(wrapDegrees(yaw - RotationManager.getViewYaw(player)));
    }

    private Vec3d eyePosition(EntityPlayerSP player) {
        return new Vec3d(player.c(), player.A() + player.X(), player.Z());
    }

    private Vec3d getTargetPoint(EntityLivingBase candidate) {
        double height = candidate.Y();
        double width = candidate.R$src$Lgg_vape_wrapper_impl_AxisAlignedBB_$r19dfl().getMaxX()
                - candidate.R$src$Lgg_vape_wrapper_impl_AxisAlignedBB_$r19dfl().getMinX();
        return new Vec3d(candidate.c() + this.aimX.getValue() * width,
                candidate.A() + this.aimY.getValue() * height,
                candidate.Z() + this.aimZ.getValue() * width);
    }

    // ------------------------------------------------------------------ Old mode

    private void runOldAim(EntityLivingBase living, Vec3d targetPoint, Vec3d eye, float currentYaw, float currentPitch) {
        float[] rot = this.rotationTo(eye, targetPoint);
        float yawDelta = wrapDegrees(rot[0] - currentYaw);
        this.updateNaturalOffset();
        float targetYaw = currentYaw + (float)((yawDelta + this.curOffYaw) / Math.max(this.smooth.getValue(), 1.0));
        float targetPitch = this.computeOptimizedPitch(living, targetPoint, eye, currentPitch);
        this.applyRotation(targetYaw, targetPitch);
    }

    private float computeOptimizedPitch(EntityLivingBase living, Vec3d targetPoint, Vec3d eye, float currentPitch) {
        double targetY = targetPoint.getY();
        long now = System.nanoTime();
        if (this.yStateInitialized) {
            double elapsed = (now - this.lastOldTargetNanos) / 1.0E9;
            this.targetVelocityY = RotationMath.smoothVelocity(this.targetVelocityY, targetY - this.lastTargetY,
                    elapsed, Y_PREDICT_ALPHA, 20.0, MAX_TARGET_JUMP);
        } else {
            this.yStateInitialized = true;
        }
        this.lastTargetY = targetY;
        this.lastOldTargetNanos = now;
        double predictedY = targetY + this.targetVelocityY * this.yPredict.getValue() / 20.0;
        float rawPitchTarget = this.rotationTo(eye, new Vec3d(targetPoint.getX(), predictedY, targetPoint.getZ()))[1];
        double dynamicSmooth = this.computeDynamicPitchSmooth(living);
        float smoothPitch = currentPitch + (float)((rawPitchTarget - currentPitch) / Math.max(dynamicSmooth, 1.0));
        if (Float.isNaN(this.smoothedPitchTarget)) {
            this.smoothedPitchTarget = currentPitch;
        }
        double alpha = this.yEmaAlpha.getValue();
        this.smoothedPitchTarget = (float)(this.smoothedPitchTarget * (1.0 - alpha) + smoothPitch * alpha);
        double deadzone = this.yDeadzone.getValue();
        float pitchDiff = this.smoothedPitchTarget - currentPitch;
        return Math.abs(pitchDiff) < deadzone ? currentPitch : this.smoothedPitchTarget + (float)this.curOffPitch;
    }

    private double computeDynamicPitchSmooth(EntityLivingBase living) {
        double base = this.pitchSmooth.getValue();
        double distance = Minecraft.thePlayer().getDistanceToEntity(living);
        double distanceFactor;
        if (distance < 5.0) {
            distanceFactor = 0.8;
        } else if (distance > 20.0) {
            distanceFactor = 1.2;
        } else {
            distanceFactor = 0.8 + (distance - 5.0) / 15.0 * 0.4;
        }
        double absVelY = Math.abs(this.targetVelocityY);
        double speedFactor = 1.0;
        if (absVelY > 1.0) {
            speedFactor = Math.max(0.6, 1.0 - (absVelY - 1.0) * 0.1);
        }
        return base * distanceFactor * speedFactor;
    }

    private void updateNaturalOffset() {
        if (--this.offTimer <= 0) {
            this.tgtOffYaw = -NATURAL_OFFSET + Math.random() * 2.0 * NATURAL_OFFSET;
            this.tgtOffPitch = -NATURAL_OFFSET + Math.random() * 2.0 * NATURAL_OFFSET;
            this.offTimer = 20 + (int)(Math.random() * 15.0);
        }
        this.curOffYaw += (this.tgtOffYaw - this.curOffYaw) * 0.1;
        this.curOffPitch += (this.tgtOffPitch - this.curOffPitch) * 0.1;
    }

    private void handleTargetLost() {
        this.oldYawQuantizer.reset();
        this.oldPitchQuantizer.reset();
        EntityPlayerSP player = Minecraft.thePlayer();
        if (++this.targetLostTicks == 1) {
            this.lastKnownVelocityY = this.targetVelocityY;
            this.smoothedPitchTarget = RotationManager.getViewPitch(player);
        } else if (this.targetLostTicks > MAX_LOST_TICKS) {
            this.resetState();
        } else {
            this.smoothedPitchTarget = (float)(this.smoothedPitchTarget * 0.95 + RotationManager.getViewPitch(player) * 0.05);
        }
    }

    // ------------------------------------------------------------------ New mode

    private void handleNewTargetLost() {
        // Reacquire from the current view rather than replaying a stale spring
        // position after the player has moved during a target-loss frame.
        this.newInit = false;
        this.newPredictor.reset();
        this.oldYawQuantizer.reset();
        this.oldPitchQuantizer.reset();
        this.lastNewDesiredNanos = 0L;
        this.lastNewFrameNanos = 0L;
        this.lastNewFrameDt = 0.0f;
        this.newYawVel = 0.0f;
        this.newPitchVel = 0.0f;
    }

    private void runNewAim(Vec3d targetPoint, float currentYaw, float currentPitch) {
        long now = System.nanoTime();
        double elapsed = Math.max((now - this.lastNewFrameNanos) / 1.0E9, 0.0);
        boolean gapFrame = elapsed > 0.05 && this.lastNewFrameDt < 0.05;
        this.lastNewFrameDt = (float)elapsed;
        if (gapFrame) {
            this.newYaw = currentYaw;
            this.newPitch = currentPitch;
            this.newYawVel = 0.0f;
            this.newPitchVel = 0.0f;
            this.newPredictor.reset();
        }
        double dt = this.newInit ? (gapFrame ? 1.0 / 60.0 : Math.min(elapsed, MAX_FRAME_DT)) : 1.0 / 60.0;
        this.lastNewFrameNanos = now;
        Vec3d eye = this.eyePosition(Minecraft.thePlayer());
        double[] predicted = this.newPredictor.predict(targetPoint.getX(), targetPoint.getY(), targetPoint.getZ(),
                now, this.newPredict.getValue() * 0.05);
        Vec3d predictedTarget = new Vec3d(predicted[0], predicted[1], predicted[2]);

        float[] desired = this.rotationTo(eye, predictedTarget);
        if (!this.newInit) {
            this.newYaw = currentYaw;
            this.newPitch = currentPitch;
            this.newYawVel = 0.0f;
            this.newPitchVel = 0.0f;
            this.newInit = true;
            this.lastNewDesiredYaw = desired[0];
            this.lastNewDesiredPitch = desired[1];
            this.lastNewDesiredNanos = now;
            return;
        }
        float targetAngVelYaw = 0.0f;
        float targetAngVelPitch = 0.0f;
        if (this.lastNewDesiredNanos != 0L) {
            double desiredDt = Math.max((now - this.lastNewDesiredNanos) / 1.0E9, 1.0E-6);
            targetAngVelYaw = MathUtil.clamp(wrapDegrees(desired[0] - this.lastNewDesiredYaw) / (float)desiredDt,
                    -MAX_TARGET_ANG_VEL, MAX_TARGET_ANG_VEL);
            targetAngVelPitch = MathUtil.clamp((desired[1] - this.lastNewDesiredPitch) / (float)desiredDt,
                    -MAX_TARGET_ANG_VEL, MAX_TARGET_ANG_VEL);
        }
        this.lastNewDesiredYaw = desired[0];
        this.lastNewDesiredPitch = desired[1];
        this.lastNewDesiredNanos = now;

        float deadzone = this.newDeadzone.getValue().floatValue();
        float deltaYaw = wrapDegrees(desired[0] - this.newYaw);
        float deltaPitch = desired[1] - this.newPitch;
        if (Math.abs(deltaYaw) < deadzone) {
            deltaYaw = 0.0f;
        }
        if (Math.abs(deltaPitch) < deadzone) {
            deltaPitch = 0.0f;
        }
        double omega = Math.max(0.5, this.newResponse.getValue());
        float feedforward = this.newFeedforward.getValue().floatValue();
        float[] yawStep = RotationMath.springStep(deltaYaw, this.newYawVel, targetAngVelYaw * feedforward, omega, dt);
        float[] pitchStep = RotationMath.springStep(deltaPitch, this.newPitchVel, targetAngVelPitch * feedforward, omega, dt);
        this.newYawVel = yawStep[1];
        this.newPitchVel = pitchStep[1];
        this.newYaw = wrapDegrees(this.newYaw + yawStep[0]);
        float unclampedPitch = this.newPitch + pitchStep[0];
        this.newPitch = MathUtil.clamp(unclampedPitch, -90.0f, 90.0f);
        if (unclampedPitch != this.newPitch && Math.signum(this.newPitchVel) == Math.signum(unclampedPitch)) {
            this.newPitchVel = 0.0f;
        }
        this.applyRotation(this.newYaw, this.newPitch);
    }

    // ------------------------------------------------------------------ helpers

    /** Kiss {@code rotationTo}: yaw/pitch from eye to point (yaw first). */
    private float[] rotationTo(Vec3d from, Vec3d to) {
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        double distXZ = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float)(-Math.toDegrees(Math.atan2(dy, distXZ)));
        return new float[]{yaw, pitch};
    }

    private void applyRotation(float absoluteYaw, float absolutePitch) {
        EntityPlayerSP player = Minecraft.thePlayer();
        if (player == null || player.isNull()) {
            return;
        }
        float gcd = SensitivityUtil.gcdDegrees();
        float currentYaw = RotationManager.getViewYaw(player);
        float currentPitch = RotationManager.getViewPitch(player);
        float requestedYaw = wrapDegrees(absoluteYaw - currentYaw);
        float requestedPitch = MathUtil.clamp(absolutePitch, -90.0f, 90.0f) - currentPitch;
        boolean old = this.aimMode.getValue() == this.oldMode;
        // New mode already integrates a continuous absolute angle. Old mode
        // requests an incremental movement, so it must retain sub-step deltas.
        float snappedYaw = old ? this.oldYawQuantizer.quantize(requestedYaw, gcd)
                : RotationMath.snapDelta(requestedYaw, gcd);
        float snappedPitch = old ? this.oldPitchQuantizer.quantize(requestedPitch, gcd)
                : RotationMath.snapDelta(requestedPitch, gcd);
        double jitterFactor = this.offGridJitter == null ? 0.0 : (double)this.offGridJitter.getValue();
        if (snappedYaw != 0.0f) {
            snappedYaw += SensitivityUtil.gcdJitter(gcd, jitterFactor);
        }
        if (snappedPitch != 0.0f) {
            snappedPitch += SensitivityUtil.gcdJitter(gcd, jitterFactor);
        }
        float unclampedPitch = currentPitch + snappedPitch;
        float finalPitch = MathUtil.clamp(unclampedPitch, -90.0f, 90.0f);
        if (unclampedPitch != finalPitch) {
            this.oldPitchQuantizer.reset();
        }
        if (snappedYaw != 0.0f || finalPitch != currentPitch) {
            float yawDelta = SensitivityUtil.degreesToMouseDelta(snappedYaw);
            float pitchDelta = SensitivityUtil.degreesToMouseDelta(currentPitch - finalPitch);
            PlayerMouseRotationApplier.applyTrackedMouseDelta(yawDelta, pitchDelta);
        }
    }

    private static float wrapDegrees(float degrees) {
        return RotationMath.wrapDegrees(degrees);
    }
}
