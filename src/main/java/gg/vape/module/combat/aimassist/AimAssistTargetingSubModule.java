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
import gg.vape.utils.Vec3d;
import gg.vape.wrapper.impl.EntityLivingBase;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.Minecraft;
import java.util.Random;
import org.jetbrains.annotations.Nullable;

public class AimAssistTargetingSubModule
extends SubModule<AimAssist> {
    private float pitchVelocity;
    private float lastYawDiff;
    private double predictedZ;
    private float pendingPitch;
    private boolean aimPointInitialized;
    private float airFactor;
    private boolean predictionInitialized;
    private static final float SNAP_ENTER_THRESHOLD = 5.0f;
    private static final long MIN_SAMPLE_NANOS = 8333334L;
    private long lastFrameNanos;
    private long lastRenderNanos;
    private double lastGroundEyeY;
    private float lastPlayerPitch;
    private float pitchBias;
    private float pendingYaw;
    private float overshoot;
    private float driftPos;
    private double leadY;
    private double leadX;
    private boolean yawSnapped;
    private static final float SNAP_MAX_ANGLE = 20.0f;
    private long driftNextNanos;
    private double leadZ;
    private float pitchFlickTicks;
    private static final float SNAP_EXIT_THRESHOLD = 7.0f;
    private float lastPitchSign;
    private float aimStrength;
    private double aimX;
    private float driftVelocity;
    private float yawAccel;
    private double predictedY;
    private float verticalVelocity;
    private float yawBias;
    private long noiseStartNanos;
    private float lastTargetPitch;
    private double lastEyeY;
    private float driftTarget;
    @Nullable
    private EntityLivingBase target;
    private float lastYawSign;
    private float driftNoise;
    private double predictedX;
    private float lastPlayerYaw;
    private float yawFlickTicks;
    private int targetSwitchTicks;
    private float pitchAccel;
    private boolean pitchSnapped;
    private float lastTargetYaw;
    private float yawVelocity;
    private final Random random = new Random();
    private boolean workerStarted;
    private double aimY;
    private float lastPitchDiff;
    private boolean initialized;
    private double aimZ;

    public static void runWorkerTick(AimAssistTargetingSubModule targetingModule) {
        targetingModule.runTargetLoop();
    }

    private double[] resolveSnapPoint(EntityPlayerSP player, EntityLivingBase target) {
        AimAssist aimAssist = (AimAssist)this.getParent();
        double playerEyeY = player.A() + (double)player.X();
        double targetMinY = target.R$src$Lgg_vape_wrapper_impl_AxisAlignedBB_$r19dfl().getMinY();
        double targetMaxY = target.R$src$Lgg_vape_wrapper_impl_AxisAlignedBB_$r19dfl().getMaxY();
        double targetHeight = targetMaxY - targetMinY;
        double targetCenterY = targetMinY + targetHeight * 0.65;
        double airborneOffset = Math.min(0.85, (double)this.airFactor);
        double eyeOffset = 0.1 + airborneOffset;
        double eyeAlignedY = Math.max(targetMinY + 0.01, Math.min(targetMaxY - 0.01, playerEyeY - eyeOffset));
        double centerBlend = Math.max(0.0, Math.min(1.0, airborneOffset / 0.55));
        double targetY = eyeAlignedY + (targetCenterY - eyeAlignedY) * centerBlend;
        targetY = Math.max(targetMinY + 0.01, Math.min(targetMaxY - 0.01, targetY));
        if (aimAssist.targetArea.getValue() == aimAssist.closestAreaMode) {
            Vec3d closestPoint = RotationUtil.T(player, target.R$src$Lgg_vape_wrapper_impl_AxisAlignedBB_$r19dfl(), 0.0, 0.0, 0.0);
            double closestY = Math.max(targetMinY + 0.01, Math.min(targetMaxY - 0.01, closestPoint.getY() - eyeOffset));
            closestY += (targetCenterY - closestY) * centerBlend;
            closestY = Math.max(targetMinY + 0.01, Math.min(targetMaxY - 0.01, closestY));
            return new double[]{closestPoint.getX(), closestY, closestPoint.getZ()};
        }
        return new double[]{target.c(), targetY, target.Z()};
    }

    private void updateAim() {
        AimAssist aimAssist = (AimAssist)this.getParent();
        boolean yawSnapEnabled = ((Double)aimAssist.getHorizontalSpeed().getValue()).floatValue() > 20.0f;
        boolean pitchSnapEnabled = aimAssist.getAimVertically().getEffectiveValue() != false && ((Double)aimAssist.getVerticalSpeed().getValue()).floatValue() > 20.0f;
        boolean snapYaw = yawSnapEnabled && this.yawSnapped;
        boolean snapPitch = pitchSnapEnabled && this.pitchSnapped;
        if (this.target == null || this.target.isNull()) {
            this.reset();
            return;
        }
        EntityPlayerSP player = Minecraft.thePlayer();
        if (player.isNull()) {
            this.reset();
            return;
        }
        long nowNanos = System.nanoTime();
        // The worker wakes roughly every millisecond. Integrating a minimum
        // 1/120 s on every wake exaggerates movement; sample at most 120 Hz.
        if (this.initialized && !RotationMath.isSampleDue(nowNanos, this.lastFrameNanos, MIN_SAMPLE_NANOS)) {
            return;
        }
        float deltaTime;
        if (!this.initialized || this.lastFrameNanos == 0L || nowNanos < this.lastFrameNanos) {
            deltaTime = 0.016666668f;
        } else {
            deltaTime = (float)(nowNanos - this.lastFrameNanos) / 1.0E9f;
            deltaTime = Math.max(0.008333334f, Math.min(0.05f, deltaTime));
        }
        this.lastFrameNanos = nowNanos;

        double[] resolvedAimPoint = this.resolveAimTarget(player, this.target);
        double resolvedAimX = resolvedAimPoint[0];
        double resolvedAimY = resolvedAimPoint[1];
        double resolvedAimZ = resolvedAimPoint[2];
        double targetMotionX = this.target.t();
        double targetMotionY = this.target.q();
        double targetMotionZ = this.target.T();
        double relativeMotionX = targetMotionX - player.t();
        double relativeMotionY = targetMotionY - player.q();
        double relativeMotionZ = targetMotionZ - player.T();
        double targetLeadX = targetMotionX + relativeMotionX * 0.08;
        double targetLeadY = targetMotionY + relativeMotionY * 0.1;
        double targetLeadZ = targetMotionZ + relativeMotionZ * 0.08;
        if (!this.aimPointInitialized) {
            this.aimX = resolvedAimX;
            this.aimY = resolvedAimY;
            this.aimZ = resolvedAimZ;
            this.leadX = targetLeadX;
            this.leadY = targetLeadY;
            this.leadZ = targetLeadZ;
            this.aimPointInitialized = true;
        }
        double targetHorizontalSpeed = Math.sqrt(targetMotionX * targetMotionX + targetMotionZ * targetMotionZ);
        double relativeHorizontalSpeed = Math.sqrt(relativeMotionX * relativeMotionX + relativeMotionZ * relativeMotionZ);
        double targetDistance = player.getDistanceToEntity(this.target);
        double baseAimSmoothing = aimAssist.targetArea.getValue() == aimAssist.closestAreaMode ? 0.3 : 0.28;
        double aimSmoothing = Math.max(0.08, Math.min(0.75, baseAimSmoothing + Math.min(0.35, relativeHorizontalSpeed * 0.5)));
        double leadMotionScale = targetHorizontalSpeed + relativeHorizontalSpeed * 0.25;
        double leadSmoothing = Math.max(0.12, Math.min(0.68, 0.18 + Math.min(0.42, leadMotionScale * 0.72)));
        double frameScale = Math.max(0.45, Math.min(2.4, (double)deltaTime * 60.0));
        double aimBlend = 1.0 - Math.pow(1.0 - aimSmoothing, frameScale);
        double leadBlend = 1.0 - Math.pow(1.0 - leadSmoothing, frameScale);
        this.aimX += (resolvedAimX - this.aimX) * aimBlend;
        this.aimY += (resolvedAimY - this.aimY) * aimBlend;
        this.aimZ += (resolvedAimZ - this.aimZ) * aimBlend;
        this.leadX += (targetLeadX - this.leadX) * leadBlend;
        this.leadY += (targetLeadY - this.leadY) * leadBlend;
        this.leadZ += (targetLeadZ - this.leadZ) * leadBlend;

        double aimLagX = resolvedAimX - this.aimX;
        double aimLagZ = resolvedAimZ - this.aimZ;
        double horizontalAimLag = Math.sqrt(aimLagX * aimLagX + aimLagZ * aimLagZ);
        double maximumAimLag = 0.3 + targetHorizontalSpeed * 1.3 + relativeHorizontalSpeed * 0.4;
        if (horizontalAimLag > maximumAimLag) {
            this.aimX = resolvedAimX;
            this.aimY = resolvedAimY;
            this.aimZ = resolvedAimZ;
            this.leadX = targetLeadX;
            this.leadY = targetLeadY;
            this.leadZ = targetLeadZ;
        }

        double leadAmount = 0.35 + targetDistance * 0.045 + targetHorizontalSpeed * 0.95 + Math.min(0.18, relativeHorizontalSpeed * 0.12);
        leadAmount = Math.max(0.1, Math.min(1.05, leadAmount));
        double distanceLeadScale = Math.max(0.0, Math.min(1.0, (targetDistance - 0.8) / 2.5));
        leadAmount *= 0.3 + 0.7 * distanceLeadScale;
        leadAmount = Math.max(0.05, leadAmount);
        double playerX = player.c();
        double playerZ = player.Z();
        double playerEyeY = player.A() + (double)player.X();
        float measuredVerticalVelocity = 0.0f;
        if (this.initialized && this.lastEyeY != 0.0) {
            measuredVerticalVelocity = (float)((playerEyeY - this.lastEyeY) / (double)deltaTime);
        }
        this.verticalVelocity += (measuredVerticalVelocity - this.verticalVelocity) * 0.65f;
        this.lastEyeY = playerEyeY;
        if (player.b$src$Z$fqlxe4()) {
            this.lastGroundEyeY = playerEyeY;
            this.airFactor *= 0.35f;
        } else {
            if (this.lastGroundEyeY == 0.0) {
                this.lastGroundEyeY = playerEyeY;
            }
            double airborneHeight = Math.max(0.0, playerEyeY - this.lastGroundEyeY);
            float velocityAirFactor = (float)Math.min(0.7, Math.max(0.0, (double)this.verticalVelocity) * 0.34);
            float heightAirFactor = (float)Math.min(0.82, airborneHeight * 0.58);
            this.airFactor = Math.max(this.airFactor * 0.92f, Math.max(velocityAirFactor, heightAirFactor));
        }

        double adjustedAimX = this.aimX;
        double adjustedAimZ = this.aimZ;
        double verticalAimDelta = this.aimY - playerEyeY;
        double verticalOffsetScale = Math.max(0.0, Math.min(1.0, Math.abs(verticalAimDelta) / 0.7));
        double verticalLeadAmount = Math.min(leadAmount, 0.7 + targetDistance * 0.04);
        double verticalLeadScale = 0.28 + 0.52 * verticalOffsetScale;
        double verticalLead = this.leadY * verticalLeadAmount * verticalLeadScale;
        double verticalLeadLimit = 0.16 + targetDistance * 0.055;
        verticalLead = Math.max(-verticalLeadLimit, Math.min(verticalLeadLimit, verticalLead));
        double adjustedAimY = this.aimY + verticalLead;
        if (snapYaw) {
            adjustedAimX = resolvedAimX;
            adjustedAimZ = resolvedAimZ;
        }
        if (snapPitch) {
            adjustedAimY = resolvedAimY;
        }
        double aimDeltaX = adjustedAimX - playerX;
        double aimDeltaZ = adjustedAimZ - playerZ;
        double aimDeltaY = adjustedAimY - playerEyeY;
        double horizontalAimDistance = Math.sqrt(aimDeltaX * aimDeltaX + aimDeltaZ * aimDeltaZ);
        float targetYaw = (float)(Math.toDegrees(Math.atan2(aimDeltaZ, aimDeltaX)) - 90.0);
        float targetPitch = (float)(-Math.toDegrees(Math.atan2(aimDeltaY, Math.max(horizontalAimDistance, 1.0E-4))));
        if (this.initialized) {
            float yawTargetChange = Math.abs(MathUtil.wrapAngleTo180(targetYaw - this.lastTargetYaw));
            float pitchTargetChange = Math.abs(MathUtil.wrapAngleTo180(targetPitch - this.lastTargetPitch));
            float abruptChangeThreshold = aimAssist.targetArea.getValue() == aimAssist.closestAreaMode ? 20.0f : 12.0f;
            float maximumTargetChange = Math.max(yawTargetChange, pitchTargetChange);
            float abruptChangeFactor = Math.max(0.0f, Math.min(1.0f, (maximumTargetChange - abruptChangeThreshold) / 50.0f));
            this.overshoot = Math.max(this.overshoot, abruptChangeFactor);
            if (abruptChangeFactor > 0.3f) {
                this.aimStrength *= 0.3f;
                this.aimX = resolvedAimX;
                this.aimY = resolvedAimY;
                this.aimZ = resolvedAimZ;
            }
            float yawRateScale = 1.0f + Math.min(2.0f, yawTargetChange / 60.0f);
            float pitchRateScale = 1.0f + Math.min(2.0f, pitchTargetChange / 60.0f);
            float maximumYawRate = (120.0f + (float)(relativeHorizontalSpeed * 700.0)) * yawRateScale;
            float maximumPitchRate = (95.0f + (float)(Math.abs(this.leadY) * 550.0)) * pitchRateScale;
            float viewYawDifference = Math.abs(MathUtil.wrapAngleTo180(targetYaw - RotationManager.getViewYaw(player)));
            float wideTurnScale = AimAssistTargetingSubModule.smoothStep(10.0f, 35.0f, viewYawDifference);
            maximumYawRate *= 1.0f + wideTurnScale * 1.5f;
            maximumYawRate = Math.max(90.0f, Math.min(1080.0f, maximumYawRate));
            maximumPitchRate = Math.max(70.0f, Math.min(500.0f, maximumPitchRate));
            float yawStep = MathUtil.wrapAngleTo180(targetYaw - this.lastTargetYaw);
            float pitchStep = MathUtil.wrapAngleTo180(targetPitch - this.lastTargetPitch);
            if (!snapYaw) {
                yawStep = Math.max(-maximumYawRate * deltaTime, Math.min(maximumYawRate * deltaTime, yawStep));
            }
            if (!snapPitch) {
                pitchStep = Math.max(-maximumPitchRate * deltaTime, Math.min(maximumPitchRate * deltaTime, pitchStep));
            }
            targetYaw = this.lastTargetYaw + yawStep;
            targetPitch = this.lastTargetPitch + pitchStep;
        }
        float overshootDecay = (float)Math.pow(0.02, deltaTime);
        this.overshoot *= overshootDecay;
        if (this.overshoot < 0.01f) {
            this.overshoot = 0.0f;
        }
        float overshootMultiplier = 1.0f + 3.0f * this.overshoot;
        float playerYaw = RotationManager.getViewYaw(player);
        float playerPitch = RotationManager.getViewPitch(player);
        float yawError = MathUtil.wrapAngleTo180(targetYaw - playerYaw);
        float pitchError = MathUtil.wrapAngleTo180(targetPitch - playerPitch);
        if (!aimAssist.getAimVertically().getEffectiveValue().booleanValue()) {
            pitchError = 0.0f;
        }
        float absoluteYawError = Math.abs(yawError);
        float absolutePitchError = Math.abs(pitchError);
        float combinedAngleError = (float)Math.sqrt(absoluteYawError * absoluteYawError + absolutePitchError * absolutePitchError);
        this.yawSnapped = AimAssistTargetingSubModule.shouldSnap(yawSnapEnabled, this.yawSnapped, absoluteYawError);
        this.pitchSnapped = AimAssistTargetingSubModule.shouldSnap(pitchSnapEnabled, this.pitchSnapped, absolutePitchError);
        snapYaw = yawSnapEnabled && this.yawSnapped;
        snapPitch = pitchSnapEnabled && this.pitchSnapped;
        float desiredAimStrength = 1.0f - AimAssistTargetingSubModule.smoothStep(1.5f, 8.0f, combinedAngleError);
        if (this.initialized) {
            float yawClosingSpeed = -(absoluteYawError - Math.abs(this.lastYawDiff)) / deltaTime;
            float closingAdjustment = Math.max(-0.3f, Math.min(0.3f, yawClosingSpeed / 20.0f));
            desiredAimStrength += closingAdjustment;
            desiredAimStrength = Math.max(0.0f, Math.min(1.0f, desiredAimStrength));
        }
        float aimStrengthBlend = desiredAimStrength > this.aimStrength
                ? Math.max(0.01f, Math.min(0.25f, deltaTime * 3.0f))
                : Math.max(0.05f, Math.min(0.8f, deltaTime * 20.0f));
        this.aimStrength += (desiredAimStrength - this.aimStrength) * aimStrengthBlend;
        float currentAimStrength = this.aimStrength;

        float smoothedTargetYawRate = 0.0f;
        float smoothedTargetPitchRate = 0.0f;
        float playerYawRate = 0.0f;
        float playerPitchRate = 0.0f;
        if (this.initialized) {
            float targetYawRate = MathUtil.wrapAngleTo180(targetYaw - this.lastTargetYaw) / deltaTime;
            float targetPitchRate = MathUtil.wrapAngleTo180(targetPitch - this.lastTargetPitch) / deltaTime;
            playerYawRate = MathUtil.wrapAngleTo180(playerYaw - this.lastPlayerYaw) / deltaTime;
            playerPitchRate = MathUtil.wrapAngleTo180(playerPitch - this.lastPlayerPitch) / deltaTime;
            float accelerationBlend = Math.max(0.05f, Math.min(0.45f, deltaTime * 12.0f));
            this.yawAccel += (targetYawRate - this.yawAccel) * accelerationBlend;
            this.pitchAccel += (targetPitchRate - this.pitchAccel) * accelerationBlend;
            smoothedTargetYawRate = this.yawAccel;
            smoothedTargetPitchRate = this.pitchAccel;
        }
        float horizontalSpeed = ((Double)aimAssist.getHorizontalSpeed().getValue()).floatValue() * 0.75f;
        float verticalSpeed = ((Double)aimAssist.getVerticalSpeed().getValue()).floatValue() * 0.75f;
        float horizontalSpeedFactor = Math.max(0.0f, Math.min(1.0f, (horizontalSpeed - 10.0f) / 90.0f));
        float verticalSpeedFactor = Math.max(0.0f, Math.min(1.0f, (verticalSpeed - 10.0f) / 90.0f));
        if (Math.signum(yawError) != Math.signum(this.lastYawDiff) && Math.abs(yawError) > 0.1f && Math.abs(this.lastYawDiff) > 0.1f) {
            this.yawBias *= 0.3f;
        }
        if (Math.signum(pitchError) != Math.signum(this.lastPitchDiff) && Math.abs(pitchError) > 0.1f && Math.abs(this.lastPitchDiff) > 0.1f) {
            this.pitchBias *= 0.3f;
        }
        float squaredAimStrength = currentAimStrength * currentAimStrength;
        this.yawBias += yawError * deltaTime * squaredAimStrength * (1.0f - horizontalSpeedFactor);
        this.pitchBias += pitchError * deltaTime * squaredAimStrength * (1.0f - verticalSpeedFactor);
        float biasRetention = 1.0f - (1.0f - currentAimStrength) * Math.max(0.0f, Math.min(0.5f, deltaTime * 5.0f));
        this.yawBias *= biasRetention;
        this.pitchBias *= biasRetention;
        float yawBiasLimit = 15.0f * (1.0f - horizontalSpeedFactor * 0.9f);
        float pitchBiasLimit = 10.0f * (1.0f - verticalSpeedFactor * 0.9f);
        this.yawBias = Math.max(-yawBiasLimit, Math.min(yawBiasLimit, this.yawBias));
        this.pitchBias = Math.max(-pitchBiasLimit, Math.min(pitchBiasLimit, this.pitchBias));

        float yawErrorVelocity = this.initialized ? (yawError - this.lastYawDiff) / deltaTime : 0.0f;
        float pitchErrorVelocity = this.initialized ? (pitchError - this.lastPitchDiff) / deltaTime : 0.0f;
        float velocityBlend = 0.15f;
        this.yawVelocity = this.yawVelocity * (1.0f - velocityBlend) + yawErrorVelocity * velocityBlend;
        this.pitchVelocity = this.pitchVelocity * (1.0f - velocityBlend) + pitchErrorVelocity * velocityBlend;

        float flickThreshold = aimAssist.targetArea.getValue() == aimAssist.closestAreaMode ? 1.5f : 0.5f;
        float yawSign = Math.signum(yawError);
        this.yawFlickTicks = yawSign != this.lastYawSign && Math.abs(yawError) > flickThreshold
                ? Math.min(this.yawFlickTicks + 1.0f, 8.0f)
                : Math.max(0.0f, this.yawFlickTicks - deltaTime * 3.0f);
        this.lastYawSign = yawSign;
        float pitchSign = Math.signum(pitchError);
        this.pitchFlickTicks = pitchSign != this.lastPitchSign && Math.abs(pitchError) > flickThreshold
                ? Math.min(this.pitchFlickTicks + 1.0f, 8.0f)
                : Math.max(0.0f, this.pitchFlickTicks - deltaTime * 3.0f);
        this.lastPitchSign = pitchSign;
        float yawFlickFactor = Math.max(0.0f, Math.min(1.0f, this.yawFlickTicks / 5.0f));
        float pitchFlickFactor = Math.max(0.0f, Math.min(1.0f, this.pitchFlickTicks / 5.0f));

        float limitedHorizontalSpeed = Math.min(horizontalSpeed, 10.0f);
        float limitedVerticalSpeed = Math.min(verticalSpeed, 10.0f);
        float horizontalGainInput = (limitedHorizontalSpeed - 1.0f) / 9.0f;
        float verticalGainInput = (limitedVerticalSpeed - 1.0f) / 9.0f;
        horizontalGainInput *= horizontalGainInput;
        verticalGainInput *= verticalGainInput;
        float horizontalGainScale = 0.15f + 0.85f * Math.max(0.0f, Math.min(1.0f, horizontalGainInput));
        float verticalGainScale = 0.15f + 0.85f * Math.max(0.0f, Math.min(1.0f, verticalGainInput));
        float yawErrorGain = AimAssistTargetingSubModule.lerp(currentAimStrength, 8.0f, 2.5f + limitedHorizontalSpeed * 0.15f) * horizontalGainScale;
        float pitchErrorGain = AimAssistTargetingSubModule.lerp(currentAimStrength, 7.0f, 2.2f + limitedVerticalSpeed * 0.13f) * verticalGainScale;
        float yawBiasGain = AimAssistTargetingSubModule.lerp(currentAimStrength, 0.15f, 0.8f + limitedHorizontalSpeed * 0.04f) * horizontalGainScale;
        float pitchBiasGain = AimAssistTargetingSubModule.lerp(currentAimStrength, 0.12f, 0.65f + limitedVerticalSpeed * 0.035f) * verticalGainScale;
        float yawVelocityGain = AimAssistTargetingSubModule.lerp(currentAimStrength, 0.08f, 0.25f) * horizontalGainScale;
        float pitchVelocityGain = AimAssistTargetingSubModule.lerp(currentAimStrength, 0.06f, 0.2f) * verticalGainScale;
        float targetYawRateGain = (0.85f + limitedHorizontalSpeed * 0.015f) * horizontalGainScale;
        float targetPitchRateGain = (0.82f + limitedVerticalSpeed * 0.013f) * verticalGainScale;
        float playerYawCompensation = AimAssistTargetingSubModule.lerp(currentAimStrength, 0.1f, 0.3f);
        float playerPitchCompensation = AimAssistTargetingSubModule.lerp(currentAimStrength, 0.08f, 0.25f);
        yawErrorGain *= 1.0f - 0.6f * yawFlickFactor;
        yawVelocityGain *= 1.0f + 2.0f * yawFlickFactor;
        pitchErrorGain *= 1.0f - 0.6f * pitchFlickFactor;
        pitchVelocityGain *= 1.0f + 2.0f * pitchFlickFactor;

        float yawRate = yawErrorGain * yawError + yawBiasGain * this.yawBias + yawVelocityGain * this.yawVelocity
                + targetYawRateGain * smoothedTargetYawRate - playerYawCompensation * playerYawRate;
        float pitchRate = 0.0f;
        if (aimAssist.getAimVertically().getEffectiveValue().booleanValue()) {
            pitchRate = pitchErrorGain * pitchError + pitchBiasGain * this.pitchBias + pitchVelocityGain * this.pitchVelocity
                    + targetPitchRateGain * smoothedTargetPitchRate - playerPitchCompensation * playerPitchRate;
        } else {
            this.pitchBias = 0.0f;
            this.pitchVelocity = 0.0f;
        }
        if (!snapPitch) {
            pitchRate += this.computePitchDrift(playerPitch, deltaTime, nowNanos);
        }
        float strafeMultiplier = 1.0f;
        float strafeInput = player.movementInput().T();
        if (aimAssist.getStrafeIncrease().getEffectiveValue().booleanValue() && Math.abs(strafeInput) > 0.01f) {
            boolean targetToRight = yawError > 0.0f;
            boolean strafingAway = targetToRight && strafeInput < 0.0f || !targetToRight && strafeInput > 0.0f;
            if (strafingAway) {
                strafeMultiplier = 1.15f;
            }
        }
        yawRate *= strafeMultiplier;

        float wideYawScale = AimAssistTargetingSubModule.smoothStep(8.0f, 40.0f, absoluteYawError);
        float wideTurnMultiplier = 1.0f + wideYawScale * 2.5f;
        float yawBaseLimit = (22.0f + limitedHorizontalSpeed * 15.0f) * horizontalGainScale * overshootMultiplier * wideTurnMultiplier;
        float pitchBaseLimit = (18.0f + limitedVerticalSpeed * 13.0f) * verticalGainScale * overshootMultiplier;
        float yawMotionLimit = (Math.abs(smoothedTargetYawRate) * 0.4f + 18.0f) * (0.35f + horizontalGainScale * 0.65f) * wideTurnMultiplier;
        float pitchMotionLimit = (Math.abs(smoothedTargetPitchRate) * 0.38f + 14.0f) * (0.35f + verticalGainScale * 0.65f);
        float maximumYawOutput = Math.min(400.0f * overshootMultiplier * wideTurnMultiplier, Math.max(yawBaseLimit, yawMotionLimit));
        float maximumPitchOutput = Math.min(300.0f * overshootMultiplier, Math.max(pitchBaseLimit, pitchMotionLimit));
        yawRate = Math.max(-maximumYawOutput, Math.min(maximumYawOutput, yawRate));
        pitchRate = Math.max(-maximumPitchOutput, Math.min(maximumPitchOutput, pitchRate));

        float distanceScale = AimAssistTargetingSubModule.smoothStep(0.5f, 3.0f, (float)targetDistance);
        float wideTurnBaseScale = AimAssistTargetingSubModule.lerp(wideYawScale, 0.15f, 0.65f);
        float yawDistanceMultiplier = AimAssistTargetingSubModule.lerp(currentAimStrength,
                wideTurnBaseScale + (1.0f - wideTurnBaseScale) * distanceScale, 0.4f + 0.6f * distanceScale);
        float pitchDistanceMultiplier = AimAssistTargetingSubModule.lerp(currentAimStrength,
                0.2f + 0.8f * distanceScale, 0.45f + 0.55f * distanceScale);
        yawRate *= yawDistanceMultiplier;
        pitchRate *= pitchDistanceMultiplier;
        float noiseElapsedSeconds = (float)(nowNanos - this.noiseStartNanos) / 1.0E9f;
        float highFrequencyYawNoise = (float)(Math.sin((double)noiseElapsedSeconds * 62.83) * 0.4
                + Math.sin((double)noiseElapsedSeconds * 47.12) * 0.25
                + Math.sin((double)noiseElapsedSeconds * 78.54) * 0.15);
        float highFrequencyPitchNoise = (float)(Math.sin((double)noiseElapsedSeconds * 56.55 + 1.3) * 0.35
                + Math.sin((double)noiseElapsedSeconds * 43.98 + 0.7) * 0.2
                + Math.sin((double)noiseElapsedSeconds * 72.26 + 2.1) * 0.12);
        float mediumFrequencyYawNoise = (float)(Math.sin((double)noiseElapsedSeconds * 12.57) * 0.8
                + Math.sin((double)noiseElapsedSeconds * 7.85) * 0.5);
        float mediumFrequencyPitchNoise = (float)(Math.sin((double)noiseElapsedSeconds * 10.47 + 0.9) * 0.6
                + Math.sin((double)noiseElapsedSeconds * 5.65 + 1.8) * 0.4);
        float lowFrequencyYawNoise = (float)(Math.sin((double)noiseElapsedSeconds * 1.26) * 0.3);
        float lowFrequencyPitchNoise = (float)(Math.sin((double)noiseElapsedSeconds * 0.94 + 0.5) * 0.2);
        float noiseStrength = 0.15f + 0.85f * currentAimStrength;
        float speedNoiseScale = 0.5f + 0.5f * (1.0f - horizontalGainInput);
        float yawNoise = (highFrequencyYawNoise + mediumFrequencyYawNoise + lowFrequencyYawNoise)
                * noiseStrength * speedNoiseScale * 1.5f;
        float pitchNoise = (highFrequencyPitchNoise + mediumFrequencyPitchNoise + lowFrequencyPitchNoise)
                * noiseStrength * speedNoiseScale;
        if (!snapYaw) {
            yawRate += yawNoise;
        }
        if (aimAssist.getAimVertically().getEffectiveValue().booleanValue() && !snapPitch) {
            pitchRate += pitchNoise;
        }

        float yawFrameDelta = yawRate * deltaTime;
        float pitchFrameDelta = pitchRate * deltaTime;
        float sensitivity = Minecraft.gameSettings().y();
        float sensitivityBase = sensitivity * 0.6f + 0.2f;
        float sensitivityScale = sensitivityBase * sensitivityBase * sensitivityBase * 8.0f;
        float mouseAngleUnit = sensitivityScale * 0.15f;
        if (mouseAngleUnit > 1.0E-5f) {
            float yawVelocityUnits = yawFrameDelta / mouseAngleUnit;
            float pitchVelocityUnits = pitchFrameDelta / mouseAngleUnit;
            float pitchBlendFactor = aimAssist.getAimVertically().getEffectiveValue() != false ? verticalSpeedFactor : 0.0f;
            if (snapYaw) {
                this.pendingYaw = 0.0f;
            }
            if (snapPitch) {
                this.pendingPitch = 0.0f;
            }
            if (!snapYaw) {
                if (horizontalSpeedFactor > 0.0f) {
                    float targetYawUnits = yawError / mouseAngleUnit;
                    float yawCorrection = targetYawUnits - this.pendingYaw;
                    float maximumYawCorrection = horizontalSpeed * 2.0f;
                    yawCorrection = Math.max(-maximumYawCorrection, Math.min(maximumYawCorrection, yawCorrection));
                    this.pendingYaw += yawVelocityUnits * (1.0f - horizontalSpeedFactor) + yawCorrection * horizontalSpeedFactor;
                } else {
                    this.pendingYaw += yawVelocityUnits;
                }
            }
            if (!snapPitch) {
                if (pitchBlendFactor > 0.0f) {
                    float targetPitchUnits = aimAssist.getAimVertically().getEffectiveValue() != false ? pitchError / mouseAngleUnit : 0.0f;
                    float pitchCorrection = targetPitchUnits - this.pendingPitch;
                    float maximumPitchCorrection = verticalSpeed * 2.0f;
                    pitchCorrection = Math.max(-maximumPitchCorrection, Math.min(maximumPitchCorrection, pitchCorrection));
                    this.pendingPitch += pitchVelocityUnits * (1.0f - pitchBlendFactor) + pitchCorrection * pitchBlendFactor;
                } else {
                    this.pendingPitch += pitchVelocityUnits;
                }
            }
        }
        if (!this.initialized) {
            this.initialized = true;
        }
        this.lastYawDiff = yawError;
        this.lastPitchDiff = pitchError;
        this.lastTargetYaw = targetYaw;
        this.lastTargetPitch = targetPitch;
        this.lastPlayerYaw = playerYaw;
        this.lastPlayerPitch = playerPitch;
    }

    @Override
    public synchronized void onEnable() {
        this.reset();
        this.lastRenderNanos = System.nanoTime();
        if (!this.workerStarted) {
            this.workerStarted = true;
            new AimAssistWorkerThread(this).start();
        }
    }

    private static float lerp(float factor, float start, float end) {
        return start + factor * (end - start);
    }

    private synchronized void runTargetLoop() {
        AimAssist aimAssist = (AimAssist)this.getParent();
        if (Minecraft.theWorld().isNull() || Minecraft.thePlayer().isNull()) {
            this.target = null;
            this.reset();
            return;
        }
        if (!aimAssist.canAim()) {
            this.reset();
            this.target = null;
            return;
        }
        if (this.target != null && this.target.isNull()) {
            this.target = null;
        }
        if (aimAssist.getRequireMouseDown().getEffectiveValue().booleanValue() && !gg.vape.config.ClientSettings.isAttackButtonDown()) {
            this.target = null;
            this.reset();
            return;
        }
        if (this.target != null && (RotationUtil.C(this.target) || (double)Minecraft.thePlayer().getDistanceToEntity(this.target) > (Double)aimAssist.getDistance().getValue())) {
            this.reset();
            this.target = null;
        }
        if (aimAssist.getRequireMouseDown().getEffectiveValue().booleanValue() && gg.vape.config.ClientSettings.isAttackButtonDown() && this.target == null || !aimAssist.getRequireMouseDown().getEffectiveValue().booleanValue()) {
            EntityLivingBase candidateTarget = aimAssist.findBestTarget();
            if (!aimAssist.getRequireMouseDown().getEffectiveValue().booleanValue()) {
                ++this.targetSwitchTicks;
                if (this.targetSwitchTicks > 700 || this.target == null) {
                    if (this.target == null || !this.target.equals(candidateTarget)) {
                        this.reset();
                    }
                    this.target = candidateTarget;
                    this.targetSwitchTicks = 0;
                }
            } else {
                if (this.target == null || !this.target.equals(candidateTarget)) {
                    this.reset();
                }
                this.target = candidateTarget;
            }
        }
        if (Minecraft.theWorld().getObject() == null) {
            return;
        }
        if (this.target != null && Minecraft.currentScreen().getObject() == null && ClientSettings.INSTANCE.inputEnabled) {
            this.updateAim();
        } else {
            this.target = null;
            this.reset();
        }
    }

    private void reset() {
        this.pendingYaw = 0.0f;
        this.pendingPitch = 0.0f;
        this.yawSnapped = false;
        this.pitchSnapped = false;
        this.yawBias = 0.0f;
        this.pitchBias = 0.0f;
        this.yawVelocity = 0.0f;
        this.pitchVelocity = 0.0f;
        this.lastYawDiff = 0.0f;
        this.lastPitchDiff = 0.0f;
        this.lastTargetYaw = 0.0f;
        this.lastTargetPitch = 0.0f;
        this.yawAccel = 0.0f;
        this.pitchAccel = 0.0f;
        this.lastPlayerYaw = 0.0f;
        this.lastPlayerPitch = 0.0f;
        this.initialized = false;
        this.lastFrameNanos = 0L;
        this.aimPointInitialized = false;
        this.aimX = 0.0;
        this.aimY = 0.0;
        this.aimZ = 0.0;
        this.leadX = 0.0;
        this.leadY = 0.0;
        this.leadZ = 0.0;
        this.aimStrength = 0.0f;
        this.yawFlickTicks = 0.0f;
        this.pitchFlickTicks = 0.0f;
        this.lastYawSign = 0.0f;
        this.lastPitchSign = 0.0f;
        this.overshoot = 0.0f;
        this.lastEyeY = 0.0;
        this.lastGroundEyeY = 0.0;
        this.verticalVelocity = 0.0f;
        this.airFactor = 0.0f;
        this.predictionInitialized = false;
        this.predictedX = 0.0;
        this.predictedY = 0.0;
        this.predictedZ = 0.0;
        this.noiseStartNanos = System.nanoTime();
        this.driftPos = 0.0f;
        this.driftVelocity = 0.0f;
        this.driftTarget = 0.0f;
        this.driftNoise = 0.0f;
        this.driftNextNanos = 0L;
    }

    @EventHandler
    public synchronized void onPreRenderTick(EventPreRenderTick eventPreRenderTick) {
        long now = System.nanoTime();
        boolean staleFrame = RotationMath.isStaleFrame(now, this.lastRenderNanos);
        this.lastRenderNanos = now;
        if (staleFrame || eventPreRenderTick.getWorld().isNull() || this.target == null
                || Minecraft.currentScreen().isNotNull() || !ClientSettings.INSTANCE.inputEnabled) {
            this.reset();
            return;
        }
        AimAssist aimAssist = (AimAssist)this.getParent();
        boolean yawSnapEnabled = ((Double)aimAssist.getHorizontalSpeed().getValue()).floatValue() > 20.0f;
        boolean pitchSnapEnabled = aimAssist.getAimVertically().getEffectiveValue() != false && ((Double)aimAssist.getVerticalSpeed().getValue()).floatValue() > 20.0f;
        boolean snapYaw = yawSnapEnabled && this.yawSnapped;
        boolean snapPitch = pitchSnapEnabled && this.pitchSnapped;
        int yawSteps = snapYaw ? Math.round(this.pendingYaw) : (int)this.pendingYaw;
        int pitchSteps = snapPitch ? Math.round(this.pendingPitch) : (int)this.pendingPitch;
        float remainingYaw = snapYaw ? 0.0f : this.pendingYaw - (float)yawSteps;
        float remainingPitch = snapPitch ? 0.0f : this.pendingPitch - (float)pitchSteps;
        if (Math.abs(yawSteps) == 0) {
            yawSteps = 0;
        }
        if (Math.abs(pitchSteps) == 0) {
            pitchSteps = 0;
        }
        float sensitivity = Minecraft.gameSettings().y();
        float sensitivityBase = sensitivity * 0.6f + 0.2f;
        float sensitivityScale = sensitivityBase * sensitivityBase * sensitivityBase * 8.0f;
        float mouseDeltaX = (float)yawSteps * sensitivityScale;
        float mouseDeltaY = -(float)pitchSteps * sensitivityScale;
        if ((snapYaw || snapPitch) && Minecraft.thePlayer().isNotNull()) {
            EntityPlayerSP player = Minecraft.thePlayer();
            double[] snapPoint = this.resolveSnapPoint(player, this.target);
            double deltaX = snapPoint[0] - player.c();
            double deltaZ = snapPoint[2] - player.Z();
            double deltaY = snapPoint[1] - (player.A() + (double)player.X());
            double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
            float targetYaw = (float)(Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0);
            float targetPitch = (float)(-Math.toDegrees(Math.atan2(deltaY, Math.max(horizontalDistance, 1.0E-4))));
            float step = sensitivityScale * 0.15f;
            if (snapYaw) {
                float delta = MathUtil.wrapAngleTo180(targetYaw - RotationManager.getViewYaw(player));
                mouseDeltaX = RotationMath.snapDelta(delta, step) / 0.15f;
            }
            if (snapPitch) {
                float delta = targetPitch - RotationManager.getViewPitch(player);
                mouseDeltaY = -RotationMath.snapDelta(delta, step) / 0.15f;
            }
        }
        PlayerMouseRotationApplier.applyTrackedMouseDelta(mouseDeltaX, mouseDeltaY);
        this.pendingYaw = remainingYaw;
        this.pendingPitch = remainingPitch;
    }

    private double[] resolveAimTarget(EntityPlayerSP player, EntityLivingBase target) {
        AimAssist aimAssist = (AimAssist)this.getParent();
        double playerEyeY = player.A() + (double)player.X();
        double targetMinY = target.R$src$Lgg_vape_wrapper_impl_AxisAlignedBB_$r19dfl().getMinY();
        double targetMaxY = target.R$src$Lgg_vape_wrapper_impl_AxisAlignedBB_$r19dfl().getMaxY();
        double targetHeight = targetMaxY - targetMinY;
        double targetCenterY = targetMinY + targetHeight * 0.65;
        double airborneOffset = Math.min(0.85, (double)this.airFactor);
        double eyeOffset = 0.1 + airborneOffset;
        double eyeAlignedY = Math.max(targetMinY + 0.01, Math.min(targetMaxY - 0.01, playerEyeY - eyeOffset));
        double centerBlend = Math.max(0.0, Math.min(1.0, airborneOffset / 0.55));
        double targetY = eyeAlignedY + (targetCenterY - eyeAlignedY) * centerBlend;
        targetY = Math.max(targetMinY + 0.01, Math.min(targetMaxY - 0.01, targetY));
        if (aimAssist.targetArea.getValue() == aimAssist.closestAreaMode) {
            Vec3d closestPoint = RotationUtil.T(player, target.R$src$Lgg_vape_wrapper_impl_AxisAlignedBB_$r19dfl(), 0.0, 0.0, 0.0);
            double closestY = Math.max(targetMinY + 0.01, Math.min(targetMaxY - 0.01, closestPoint.getY() - eyeOffset));
            closestY += (targetCenterY - closestY) * centerBlend;
            closestY = Math.max(targetMinY + 0.01, Math.min(targetMaxY - 0.01, closestY));
            double resolvedX = closestPoint.getX();
            double resolvedY = closestY;
            double resolvedZ = closestPoint.getZ();
            if (this.predictionInitialized) {
                double smoothingFactor = 0.35;
                resolvedX = this.predictedX + (resolvedX - this.predictedX) * smoothingFactor;
                resolvedY = this.predictedY + (resolvedY - this.predictedY) * smoothingFactor;
                resolvedZ = this.predictedZ + (resolvedZ - this.predictedZ) * smoothingFactor;
            }
            this.predictedX = resolvedX;
            this.predictedY = resolvedY;
            this.predictedZ = resolvedZ;
            this.predictionInitialized = true;
            return new double[]{resolvedX, resolvedY, resolvedZ};
        }
        return new double[]{target.c(), targetY, target.Z()};
    }

    private static float smoothStep(float edgeStart, float edgeEnd, float value) {
        float normalized = Math.max(0.0f, Math.min(1.0f, (value - edgeStart) / (edgeEnd - edgeStart)));
        return normalized * normalized * (3.0f - 2.0f * normalized);
    }


    @Nullable
    public synchronized EntityLivingBase getTarget() {
        return this.target;
    }

    public AimAssistTargetingSubModule(Mod parent, String name) {
        super(parent, name);
    }

    private float computePitchDrift(float playerPitch, float deltaTime, long nowNanos) {
        float strength = 0.65f + 0.35f * this.aimStrength;
        if (this.driftNextNanos == 0L || nowNanos >= this.driftNextNanos) {
            float randomAmplitude = AimAssistTargetingSubModule.lerp(strength, 0.05f, 0.15f);
            float pitchCorrection = playerPitch > 22.0f ? -0.18f : (playerPitch < -22.0f ? 0.18f : 0.0f);
            this.driftTarget = (this.random.nextFloat() * 2.0f - 1.0f) * randomAmplitude + pitchCorrection;
            this.driftTarget = Math.max(-0.5f, Math.min(0.5f, this.driftTarget));
            long intervalMillis = 300L + (long)this.random.nextInt(420);
            this.driftNextNanos = nowNanos + intervalMillis * 1000000L;
        }
        float springStrength = AimAssistTargetingSubModule.lerp(strength, 2.0f, 5.0f);
        float damping = (float)Math.pow(0.04f, deltaTime);
        this.driftVelocity += (this.driftTarget - this.driftPos) * springStrength * deltaTime;
        this.driftVelocity *= damping;
        this.driftPos += this.driftVelocity * deltaTime;
        float positionLimit = AimAssistTargetingSubModule.lerp(strength, 0.45f, 0.8f);
        if (this.driftPos > positionLimit) {
            this.driftPos = positionLimit;
            this.driftVelocity = Math.min(0.0f, this.driftVelocity);
        } else if (this.driftPos < -positionLimit) {
            this.driftPos = -positionLimit;
            this.driftVelocity = Math.max(0.0f, this.driftVelocity);
        }
        this.driftNoise += (this.random.nextFloat() * 2.0f - 1.0f - this.driftNoise) * Math.max(0.02f, Math.min(0.18f, deltaTime * 5.0f));
        float elapsedSeconds = (float)(nowNanos - this.noiseStartNanos) / 1.0E9f;
        float noise = (float)(Math.sin((double)elapsedSeconds * 8.7 + 0.4) * (double)0.35f + Math.sin((double)elapsedSeconds * 13.1 + 2.2) * (double)0.22f + Math.sin((double)elapsedSeconds * 19.6 + 1.1) * (double)0.12f + (double)(this.driftNoise * 0.3f));
        float restoringForce = -this.driftPos * AimAssistTargetingSubModule.lerp(strength, 1.4f, 3.0f);
        float output = this.driftVelocity * 0.25f + restoringForce + noise * AimAssistTargetingSubModule.lerp(strength, 0.35f, 0.9f);
        float pitchLimitScale = 1.0f - 0.85f * AimAssistTargetingSubModule.smoothStep(72.0f, 88.0f, Math.abs(playerPitch));
        float outputLimit = AimAssistTargetingSubModule.lerp(strength, 0.25f, 0.55f) * pitchLimitScale;
        return Math.max(-outputLimit, Math.min(outputLimit, output));
    }

    private static boolean shouldSnap(boolean enabled, boolean currentlySnapped, float angleDifference) {
        if (!enabled) {
            return false;
        }
        return currentlySnapped ? angleDifference <= SNAP_EXIT_THRESHOLD : angleDifference <= SNAP_ENTER_THRESHOLD;
    }

    @Override
    public synchronized void onDisable() {
        this.target = null;
        this.reset();
    }
}
