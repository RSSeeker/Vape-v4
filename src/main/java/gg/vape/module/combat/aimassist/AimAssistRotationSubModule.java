package gg.vape.module.combat.aimassist;

import gg.vape.event.EventHandler;
import gg.vape.event.impl.EventPreRenderTick;
import gg.vape.module.Mod;
import gg.vape.module.SubModule;
import gg.vape.module.combat.AimAssist;
import gg.vape.module.none.ClientSettings;
import gg.vape.rotation.PlayerMouseRotationApplier;
import gg.vape.rotation.RotationManager;
import gg.vape.rotation.RotationMath;
import gg.vape.utils.MathUtil;
import gg.vape.utils.RotationUtil;
import gg.vape.utils.SensitivityUtil;
import gg.vape.utils.Vec3d;
import gg.vape.wrapper.impl.EntityLivingBase;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.ForgeVersion;
import gg.vape.wrapper.impl.Minecraft;
import java.util.Random;
import org.jetbrains.annotations.Nullable;

public class AimAssistRotationSubModule
extends SubModule<AimAssist> {
    private int randomOffsetX;
    private int swapTickCounter;
    private boolean farFromTarget;
    private float pitchBoost = 0.0f;
    private int randomOffsetY;
    private float verticalMouseAccumulator;
    private float horizontalVelocity;
    private double prevTargetZ;
    private float horizontalMouseAccumulator;
    @Nullable
    private EntityLivingBase target = null;
    private double targetY;
    private boolean prevOnLeft;
    private double lastAngleDiff;
    private boolean prevAbove;
    private boolean threadStarted;
    private float verticalVelocity;
    private float verticalVelocityBuffer;
    private int retargetCounter;
    private float yawBoost = 0.0f;
    private final Random random;
    private double driftTimer;
    private double targetZ;
    private int driftY;
    private float horizontalVelocityBuffer;
    private int sampleCounter;
    private int driftX;
    private double targetX;
    private final Random sharedRandom = new Random();
    private double prevTargetX;
    private boolean previousTargetPositionInitialized;
    private Object motionTargetObject;
    private long lastRenderNanos;

    private void computeTargetOffset() {
        Vec3d closestPoint = RotationUtil.T(Minecraft.thePlayer(), this.target.R$src$Lgg_vape_wrapper_impl_AxisAlignedBB_$r19dfl(), 0.0, 0.0, 0.0);
        double targetMotionX = this.target.z() - this.target.f();
        double targetMotionZ = this.target.h() - this.target.R();
        double previousClosestX = closestPoint.getX() - targetMotionX;
        double previousClosestZ = closestPoint.getZ() - targetMotionZ;
        float partialTicks = Minecraft.getTimer().renderPartialTicks();
        this.targetX = previousClosestX + (closestPoint.getX() - previousClosestX) * (double)partialTicks;
        this.targetZ = previousClosestZ + (closestPoint.getZ() - previousClosestZ) * (double)partialTicks;
    }

    private void updateDrift() {
        this.driftTimer += 1.0;
        if (this.driftTimer >= (double)(250 + this.random.nextInt(50))) {
            this.driftTimer = MathUtil.randomExclusiveUpper(this.random, -100, -50);
            this.randomOffsetX = MathUtil.randomExclusiveUpper(this.random, -1, 2);
            this.randomOffsetY = MathUtil.randomExclusiveUpper(this.random, -1, 2);
        }
        int horizontalStep = this.randomOffsetX;
        int verticalStep = this.randomOffsetY;
        if (this.random.nextInt(10) < 2) {
            // empty if block
        }
        if (this.random.nextInt(10) < 2) {
            // empty if block
        }
        if (this.random.nextInt(10) < 2) {
            horizontalStep = 0;
        }
        if (this.random.nextInt(10) < 2) {
            verticalStep = 0;
        }
        if (this.driftTimer < 0.0) {
            horizontalStep = 0;
            verticalStep = 0;
        }
        if (this.random.nextInt(20) == 1) {
            this.driftX += horizontalStep;
            this.driftY += verticalStep;
        }
        if (this.horizontalMouseAccumulator > 0.0f && this.driftX < 0 || this.horizontalMouseAccumulator < 0.0f && this.driftX > 0) {
            this.driftX = 0;
        }
    }

    @EventHandler
    public synchronized void onPreRenderTick(EventPreRenderTick eventPreRenderTick) {
        long now = System.nanoTime();
        boolean staleFrame = RotationMath.isStaleFrame(now, this.lastRenderNanos);
        this.lastRenderNanos = now;
        if (staleFrame || eventPreRenderTick.getWorld().isNull() || this.target == null
                || Minecraft.currentScreen().isNotNull() || !ClientSettings.INSTANCE.inputEnabled) {
            this.resetRotationState();
            return;
        }
        this.horizontalMouseAccumulator += (float)this.driftX;
        this.verticalMouseAccumulator += (float)this.driftY;
        int horizontalMouseSteps = (int)this.horizontalMouseAccumulator;
        int verticalMouseSteps = (int)this.verticalMouseAccumulator;
        float remainingHorizontalDelta = this.horizontalMouseAccumulator - (float)horizontalMouseSteps;
        float remainingVerticalDelta = this.verticalMouseAccumulator - (float)verticalMouseSteps;
        if (Math.abs(horizontalMouseSteps) == 0) {
            horizontalMouseSteps = 0;
        }
        if (Math.abs(verticalMouseSteps) == 0) {
            verticalMouseSteps = 0;
        }
        float sensitivityScale = SensitivityUtil.degreesPerMouseStep();
        float horizontalDelta = (float)horizontalMouseSteps * sensitivityScale;
        float verticalDelta = (float)verticalMouseSteps * sensitivityScale;
        PlayerMouseRotationApplier.applyTrackedMouseDelta(horizontalDelta, -verticalDelta);
        this.horizontalMouseAccumulator = remainingHorizontalDelta;
        this.verticalMouseAccumulator = remainingVerticalDelta;
        this.driftX = 0;
        this.driftY = 0;
    }

    public static void runWorkerTick(AimAssistRotationSubModule rotationModule) {
        rotationModule.tick();
    }

    void queueVerticalAdjustment(float adjustment, float angleDifference) {
        AimAssist aimAssist = (AimAssist)this.getParent();
        if (adjustment != 0.0f) {
            adjustment *= 5.0f;
            float speed = ((Double)aimAssist.getVerticalSpeed().getValue()).floatValue();
            float absoluteAngleDifference = Math.abs(angleDifference);
            if (absoluteAngleDifference <= 10.0f) {
                this.yawBoost = speed;
            }
            if (this.yawBoost > 0.0f) {
                speed -= this.yawBoost / 3.0f;
                this.yawBoost -= absoluteAngleDifference / 200.0f;
            }
            this.verticalMouseAccumulator += speed * adjustment;
        } else {
            this.verticalMouseAccumulator = 0.0f;
        }
    }

    void queueHorizontalAdjustment(float adjustment) {
        AimAssist aimAssist = (AimAssist)this.getParent();
        if (adjustment != 0.0f) {
            adjustment *= 5.0f;
            float speed = ((Double)aimAssist.getHorizontalSpeed().getValue()).floatValue();
            float angleDifference = RotationUtil.a(Minecraft.thePlayer(), this.target);
            if (angleDifference <= 10.0f) {
                this.pitchBoost = speed;
            }
            if (this.pitchBoost > 0.0f) {
                speed -= this.pitchBoost / 3.0f;
                this.pitchBoost -= angleDifference / 200.0f;
            }
            this.horizontalMouseAccumulator += speed * adjustment;
        } else {
            this.horizontalMouseAccumulator = 0.0f;
        }
    }

    public AimAssistRotationSubModule(Mod parent, String name) {
        super(parent, name);
        this.random = new Random();
    }

    void resetRotationState() {
        this.horizontalMouseAccumulator = 0.0f;
        this.verticalMouseAccumulator = 0.0f;
        this.horizontalVelocity = 0.0f;
        this.horizontalVelocityBuffer = 0.0f;
        this.verticalVelocity = 0.0f;
        this.verticalVelocityBuffer = 0.0f;
        this.pitchBoost = 0.0f;
        this.yawBoost = 0.0f;
        this.swapTickCounter = 0;
        this.sampleCounter = 0;
        this.lastAngleDiff = 0.0;
        this.prevOnLeft = false;
        this.prevAbove = false;
        this.previousTargetPositionInitialized = false;
        this.motionTargetObject = null;
        this.randomOffsetX = 0;
        this.randomOffsetY = 0;
        this.driftTimer = 0.0;
        this.driftX = 0;
        this.driftY = 0;
    }

    private synchronized void tick() {
        AimAssist aimAssist = (AimAssist)this.getParent();
        if (Minecraft.theWorld().isNull() || Minecraft.thePlayer().isNull()) {
            this.target = null;
            this.resetRotationState();
            return;
        }
        if (!aimAssist.canAim()) {
            this.target = null;
            this.resetRotationState();
            return;
        }
        if (this.target != null && this.target.isNull()) {
            this.target = null;
        }
        if (aimAssist.getRequireMouseDown().getEffectiveValue().booleanValue() && !gg.vape.config.ClientSettings.isAttackButtonDown()) {
            this.target = null;
            this.resetRotationState();
            return;
        }
        if (this.target != null && (RotationUtil.C(this.target) || (double)Minecraft.thePlayer().getDistanceToEntity(this.target) > (Double)aimAssist.getDistance().getValue())) {
            this.resetRotationState();
            this.target = null;
        }
        if (aimAssist.getRequireMouseDown().getEffectiveValue().booleanValue() && gg.vape.config.ClientSettings.isAttackButtonDown() && this.target == null || !aimAssist.getRequireMouseDown().getEffectiveValue().booleanValue()) {
            EntityLivingBase candidateTarget = aimAssist.findBestTarget();
            if (!aimAssist.getRequireMouseDown().getEffectiveValue().booleanValue()) {
                ++this.retargetCounter;
                if (this.retargetCounter > 700 || this.target == null || !aimAssist.isValidTarget(this.target)) {
                    this.target = candidateTarget;
                    this.retargetCounter = 0;
                }
            } else {
                this.target = candidateTarget;
            }
        }
        if (Minecraft.theWorld().getObject() == null) {
            return;
        }
        if (this.target != null && Minecraft.currentScreen().getObject() == null && ClientSettings.INSTANCE.inputEnabled) {
            if (this.motionTargetObject != this.target.getObject()) {
                this.resetRotationState();
                this.motionTargetObject = this.target.getObject();
            }
            this.updateVelocityBuffers();
            this.applyRotation();
        } else {
            this.target = null;
            this.resetRotationState();
        }
    }

    @Override
    public synchronized void onDisable() {
        this.target = null;
        this.resetRotationState();
    }

    private void applyRotation() {
        AimAssist aimAssist = (AimAssist)this.getParent();
        this.updateDrift();
        this.targetX = this.target.c();
        this.targetY = this.target.A();
        this.targetZ = this.target.Z();
        if (ForgeVersion.MC_1_7_10.L()) {
            this.targetY += (double)this.target.X();
        }
        if (aimAssist.targetArea.getValue() == aimAssist.closestAreaMode) {
            this.computeTargetOffset();
        }

        // The first sample for a target has no prior position. Treating (0, 0)
        // or the previous target as its origin can produce a huge false lead.
        double targetMotionX = this.previousTargetPositionInitialized ? this.targetX - this.prevTargetX : 0.0;
        double targetMotionZ = this.previousTargetPositionInitialized ? this.targetZ - this.prevTargetZ : 0.0;
        this.prevTargetX = this.targetX;
        this.prevTargetZ = this.targetZ;
        this.previousTargetPositionInitialized = true;

        EntityPlayerSP player = Minecraft.thePlayer();
        float viewYaw = RotationManager.getViewYaw(player);
        double predictionFactor = 1.7;
        double predictedTargetX = this.targetX + targetMotionX * predictionFactor;
        double predictedTargetZ = this.targetZ + targetMotionZ * predictionFactor;
        double horizontalAngleDifference = RotationUtil.C(player.c(), player.Z(), viewYaw, predictedTargetX, predictedTargetZ);
        boolean targetOnLeft = RotationUtil.p(player.c(), player.Z(), viewYaw, predictedTargetX, predictedTargetZ);
        int verticalAngleDifference = RotationUtil.H(player, this.targetX, this.targetY, this.targetZ);
        boolean targetAbove = verticalAngleDifference < 0;
        int verticalDeadZoneDistance = Math.abs(verticalAngleDifference) - 10;

        float horizontalForce = 1.0f;
        float verticalForce = 1.0f;
        horizontalForce = (float)((double)horizontalForce + MathUtil.randomRange(this.sharedRandom, 0.0, 2.0));
        horizontalForce = (float)((double)horizontalForce + horizontalAngleDifference / 50.0);
        verticalForce = (float)((double)verticalForce + MathUtil.randomRange(this.sharedRandom, 0.0, 2.0));
        verticalForce += (float)Math.abs(verticalDeadZoneDistance) / 50.0f;
        if (Math.abs(horizontalAngleDifference - this.lastAngleDiff) > 6.0) {
            horizontalForce = (float)((double)horizontalForce + horizontalAngleDifference / 35.0);
        }
        double proximityBoost = Math.max(0.0, (9.0f - player.getDistanceToEntity(this.target)) / 2.5f - 2.0f);
        horizontalForce = (float)((double)horizontalForce + proximityBoost);

        float strafeInput = player.movementInput().T();
        boolean strafingAway = targetOnLeft ? strafeInput < 0.0f : strafeInput > 0.0f;
        if (aimAssist.getStrafeIncrease().getEffectiveValue().booleanValue() && strafingAway) {
            horizontalForce = (float)((double)horizontalForce * 1.6);
        }
        if (player.getDistanceToEntity(this.target) < 0.5f) {
            horizontalForce /= 5.0f;
        }

        float horizontalAcceleration = horizontalForce / 90.0f * (targetOnLeft ? -1.0f : 1.0f);
        float verticalAcceleration = verticalForce / 90.0f * (targetAbove ? 1.0f : -1.0f);
        if (horizontalAngleDifference < 5.0) {
            horizontalAcceleration = 0.0f;
            this.horizontalVelocity *= 0.7f;
            boolean strafingToward = targetOnLeft ? strafeInput > 0.0f : strafeInput < 0.0f;
            if (strafingToward) {
                this.horizontalVelocity *= 0.5f;
            }
        }
        if (targetOnLeft != this.prevOnLeft) {
            this.horizontalVelocity = -this.horizontalVelocity;
            this.horizontalVelocityBuffer = -this.horizontalVelocityBuffer;
            this.horizontalMouseAccumulator = 0.0f;
        }
        if (targetAbove != this.prevAbove) {
            this.verticalVelocityBuffer = -this.verticalVelocityBuffer;
            this.verticalVelocity = -this.verticalVelocity;
            this.verticalMouseAccumulator = 0.0f;
        }
        if (verticalDeadZoneDistance < 5) {
            verticalAcceleration = 0.0f;
            this.verticalVelocityBuffer *= 0.7f;
        }

        this.horizontalVelocityBuffer += horizontalAcceleration;
        this.verticalVelocity += verticalAcceleration;
        float smoothedHorizontalVelocity = this.horizontalVelocity;
        float smoothedVerticalVelocity = this.verticalVelocityBuffer;
        if (Math.abs(smoothedHorizontalVelocity) > 10.0f) {
            this.horizontalVelocityBuffer = 0.0f;
            this.horizontalVelocity = 0.0f;
            return;
        }

        float horizontalAdjustment = smoothedHorizontalVelocity * 0.15f;
        if (horizontalAngleDifference <= 9.0) {
            horizontalAdjustment = (float)((double)horizontalAdjustment / (10.0 - horizontalAngleDifference));
        }
        this.farFromTarget = horizontalAngleDifference > 5.0;
        if (Float.isNaN(horizontalAdjustment)) {
            this.horizontalVelocityBuffer = 0.0f;
            this.horizontalVelocity = 0.0f;
            return;
        }
        this.queueHorizontalAdjustment(horizontalAdjustment);

        if (aimAssist.getAimVertically().getEffectiveValue().booleanValue()) {
            float verticalAdjustment = (float)((double)smoothedVerticalVelocity * 0.15);
            if (Float.isNaN(verticalAdjustment)) {
                this.verticalVelocity = 0.0f;
                this.verticalVelocityBuffer = 0.0f;
                return;
            }
            this.queueVerticalAdjustment(verticalAdjustment, verticalAngleDifference);
        }
        this.prevAbove = targetAbove;
        this.prevOnLeft = targetOnLeft;
        ++this.sampleCounter;
        if (this.sampleCounter > 10) {
            this.lastAngleDiff = horizontalAngleDifference;
            this.sampleCounter = 0;
        }
    }

    @Nullable
    public synchronized EntityLivingBase getTarget() {
        return this.target;
    }

    void updateVelocityBuffers() {
        ++this.swapTickCounter;
        if (this.swapTickCounter > 10) {
            this.verticalVelocityBuffer = this.verticalVelocity;
            this.horizontalVelocity = this.horizontalVelocityBuffer;
            this.horizontalVelocityBuffer = 0.0f;
            this.verticalVelocity = 0.0f;
            this.swapTickCounter = 0;
        }
    }

    @Override
    public synchronized void onEnable() {
        this.lastRenderNanos = System.nanoTime();
        if (!this.threadStarted) {
            this.threadStarted = true;
            new AimAssistRotationWorkerThread(this).start();
        }
    }

}
