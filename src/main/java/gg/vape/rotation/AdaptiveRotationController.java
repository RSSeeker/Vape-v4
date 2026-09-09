package gg.vape.rotation;

import gg.vape.event.impl.EventPostRenderTick;
import gg.vape.event.impl.EventPreEntityRendererMouseUpdate;
import gg.vape.mapping.MappedClasses;
import gg.vape.module.render.hud.FreeLookHudModule;
import gg.vape.rotation.FixedRotationController;
import gg.vape.rotation.RotationAngles;
import gg.vape.rotation.RotationManager;
import gg.vape.rotation.WorldPointRotationTarget;
import gg.vape.utils.MathUtil;
import gg.vape.utils.MutableColor;
import gg.vape.utils.RotationVectorMath;
import gg.vape.wrapper.impl.EntityLivingBase;
import gg.vape.wrapper.impl.EntityPlayer;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.ForgeVersion;
import gg.vape.wrapper.impl.GuiScreen;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.Vec3;
import java.util.Random;

public class AdaptiveRotationController
extends FixedRotationController
implements WorldPointRotationTarget {
    private boolean blockWhenScreenOpen;
    private long lastJitterUpdateTime;
    private boolean normalizeTargetYaw;
    private double renderLineWidth;
    private RotationAngles fixedTargetRotation;
    private float yawOffset;
    private float pitchJitterPhase;
    private float yawJitterAmplitude;
    private MutableColor primaryColor;
    private boolean relativeMode;
    private MutableColor secondaryColor;
    private float yawJitterPhase;
    private final Random random;
    private Float referenceYawOverride;
    private Vec3 target;
    private float currentYaw;
    private float currentPitch;
    private float pitchJitterAmplitude;
    private boolean useSecondaryColor;
    private float yawJitterSpeed;
    private EntityLivingBase referenceEntity;
    private static final RotationAngles ZERO_ROTATION;
    private float pitchJitterSpeed;
    private boolean aiRotationActive;

    public float getReferenceYaw() {
        EntityLivingBase reference = this.referenceEntity != null ? this.referenceEntity : Minecraft.F();
        return this.referenceYawOverride != null
                ? this.referenceYawOverride.floatValue()
                : (FreeLookHudModule.isActive() ? FreeLookHudModule.getSavedPitch() : reference.J());
    }

    public float getYawOffset() {
        return this.yawOffset;
    }

    @Override
    public boolean updatePitch() {
        if (this.targetPitch == UNSET_ROTATION) {
            return true;
        }
        if (this.aiRotationActive) {
            return true;
        }
        float currentPitch = this.getCurrentPitch() == -90.0f ? -89.99f : this.getCurrentPitch();
        float mouseScale = this.getMouseScale();
        float predictedYaw = this.getCurrentYaw() + (float)(int)this.pendingYawDelta * mouseScale * 0.15f;
        float predictedPitch = currentPitch - (float)(int)(-this.pendingPitchDelta) * mouseScale * 0.15f;
        double yawError = MathUtil.wrapAngleTo180((this.targetYaw - predictedYaw) % 360.0f);
        double pitchError = MathUtil.wrapAngleTo180((this.targetPitch - predictedPitch) % 360.0f);
        double absolutePitchError = Math.abs(pitchError);
        if (!this.isOutsideTolerance(absolutePitchError)) {
            return true;
        }
        double step = this.getSpeed() * 0.25;
        double axisRatio = absolutePitchError / Math.abs(yawError);
        if (this.isAxisScalingEnabled() && axisRatio < 1.0) {
            step *= axisRatio;
        }
        step = this.applyPitchAcceleration(step, absolutePitchError);
        this.pendingPitchDelta = this.addPendingStep(this.pendingPitchDelta, pitchError, step);
        return false;
    }

    public void setSecondaryColor(MutableColor color) {
        this.secondaryColor = color;
    }

    public void setRenderLineWidth(double renderLineWidth) {
        this.renderLineWidth = renderLineWidth;
    }

    @Override
    public void onPreMouseUpdate(EventPreEntityRendererMouseUpdate event) {
    }

    public AdaptiveRotationController(EntityPlayer referencePlayer) {
        super(referencePlayer.J(), referencePlayer.V());
        this.blockWhenScreenOpen = false;
        this.normalizeTargetYaw = true;
        this.referenceYawOverride = null;
        this.primaryColor = new MutableColor(0xFFFFFF);
        this.secondaryColor = new MutableColor(16756275);
        this.yawOffset = 0.0f;
        this.random = new Random();
        this.yawJitterPhase = 0.0f;
        this.pitchJitterPhase = 0.0f;
        this.yawJitterSpeed = 0.0f;
        this.pitchJitterSpeed = 0.0f;
        this.yawJitterAmplitude = 0.0f;
        this.pitchJitterAmplitude = 0.0f;
        this.lastJitterUpdateTime = 0L;
        this.referenceEntity = referencePlayer;
        this.currentYaw = this.targetYaw;
        this.currentPitch = this.targetPitch;
        this.renderLineWidth = 3.0;
    }

    public boolean isRelativeMode() {
        return this.relativeMode;
    }

    private float calculateYawJitter() {
        float primaryWave = (float)(Math.sin(this.yawJitterPhase) * (double)this.yawJitterAmplitude);
        float secondaryWave = (float)(Math.sin(this.yawJitterPhase * 2.7f + 1.3f)
                * (double)this.yawJitterAmplitude * 0.3);
        return primaryWave + secondaryWave;
    }

    public double getRenderLineWidth() {
        return this.renderLineWidth;
    }

    public void refreshTargetRotation() {
        if (this.relativeMode) {
            this.setTargetRotation(ZERO_ROTATION);
        } else {
            RotationAngles targetRotation = null;
            if (this.target != null) {
                targetRotation = this.calculateRotation(this.target);
            } else if (this.fixedTargetRotation != null) {
                targetRotation = this.fixedTargetRotation;
            }
            if (targetRotation != null) {
                if (this.yawOffset != 0.0f) {
                    this.setTargetRotation(targetRotation.getYaw() + this.yawOffset, targetRotation.getPitch());
                } else {
                    this.setTargetRotation(targetRotation);
                }
            }
        }
    }

    public AdaptiveRotationController() {
        super(RotationManager.INSTANCE.getManagedYaw(), RotationManager.INSTANCE.getManagedPitch());
        this.blockWhenScreenOpen = false;
        this.normalizeTargetYaw = true;
        this.referenceYawOverride = null;
        this.primaryColor = new MutableColor(0xFFFFFF);
        this.secondaryColor = new MutableColor(16756275);
        this.yawOffset = 0.0f;
        this.random = new Random();
        this.yawJitterPhase = 0.0f;
        this.pitchJitterPhase = 0.0f;
        this.yawJitterSpeed = 0.0f;
        this.pitchJitterSpeed = 0.0f;
        this.yawJitterAmplitude = 0.0f;
        this.pitchJitterAmplitude = 0.0f;
        this.lastJitterUpdateTime = 0L;
        this.currentYaw = this.targetYaw;
        this.currentPitch = this.targetPitch;
        this.renderLineWidth = 3.0;
    }

    public RotationAngles calculateRotation(Vec3 target) {
        EntityLivingBase reference = this.referenceEntity != null ? this.referenceEntity : Minecraft.F();
        double eyeHeight = ForgeVersion.MC_1_7_10.Y() ? (double)reference.X() : 0.0;
        Vec3 targetPosition = Vec3.create(target.getX(), target.getY(), target.getZ());
        Vec3 eyePosition = Vec3.create(reference.c(), reference.A() + eyeHeight, reference.Z());
        return RotationVectorMath.H(eyePosition, targetPosition, this.getCurrentYaw(), false);
    }

    private void updateJitter() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - this.lastJitterUpdateTime > (long)(200 + this.random.nextInt(300))) {
            this.lastJitterUpdateTime = currentTime;
            this.yawJitterSpeed = 0.05f + this.random.nextFloat() * 0.15f;
            this.pitchJitterSpeed = 0.04f + this.random.nextFloat() * 0.12f;
            this.yawJitterAmplitude = 0.15f + this.random.nextFloat() * 0.25f;
            this.pitchJitterAmplitude = 0.1f + this.random.nextFloat() * 0.2f;
        }
        this.yawJitterPhase += this.yawJitterSpeed;
        this.pitchJitterPhase += this.pitchJitterSpeed;
        if ((double)this.yawJitterPhase > Math.PI * 2) {
            this.yawJitterPhase -= (float)Math.PI * 2;
        }
        if ((double)this.pitchJitterPhase > Math.PI * 2) {
            this.pitchJitterPhase -= (float)Math.PI * 2;
        }
    }

    public void setNormalizeTargetYaw(boolean normalizeTargetYaw) {
        this.normalizeTargetYaw = normalizeTargetYaw;
    }

    public void setBlockWhenScreenOpen(boolean blockWhenScreenOpen) {
        this.blockWhenScreenOpen = blockWhenScreenOpen;
    }

    public void copyFrom(AdaptiveRotationController source) {
        this.currentYaw = source.currentYaw;
        this.currentPitch = source.currentPitch;
        this.targetYaw = source.targetYaw;
        this.targetPitch = source.targetPitch;
        this.renderLineWidth = source.renderLineWidth;
        this.pendingYawDelta = source.pendingYawDelta;
        this.pendingPitchDelta = source.pendingPitchDelta;
        this.yawDistance = source.yawDistance;
        this.pitchDistance = source.pitchDistance;
        this.setRestoreCapturedRotation(source.shouldRestoreCapturedRotation());
        this.setClampStepToRemaining(source.isStepClampedToRemaining());
        this.setScaleAxesProportionally(source.isAxisScalingEnabled());
        this.setAngleBasedAcceleration(source.isAngleBasedAccelerationEnabled());
        this.setLinearAcceleration(source.isLinearAccelerationEnabled());
        this.setCubicAcceleration(source.isCubicAccelerationEnabled());
        this.setRetainAfterCompletion(source.shouldRetainAfterCompletion());
        this.setComplete(source.isComplete());
        this.speed = source.speed;
        this.tolerance = source.tolerance;
        this.relativeMode = source.relativeMode;
        this.referenceYawOverride = source.referenceYawOverride;
    }


    public AdaptiveRotationController(float yaw, float pitch) {
        super(yaw, pitch);
        this.blockWhenScreenOpen = false;
        this.normalizeTargetYaw = true;
        this.referenceYawOverride = null;
        this.primaryColor = new MutableColor(0xFFFFFF);
        this.secondaryColor = new MutableColor(16756275);
        this.yawOffset = 0.0f;
        this.random = new Random();
        this.yawJitterPhase = 0.0f;
        this.pitchJitterPhase = 0.0f;
        this.yawJitterSpeed = 0.0f;
        this.pitchJitterSpeed = 0.0f;
        this.yawJitterAmplitude = 0.0f;
        this.pitchJitterAmplitude = 0.0f;
        this.lastJitterUpdateTime = 0L;
        this.currentYaw = Minecraft.F().J();
        this.currentPitch = Minecraft.F().V();
        this.renderLineWidth = 3.0;
    }

    public MutableColor getPrimaryColor() {
        return this.primaryColor;
    }

    @Override
    public void applyMouseDelta(float yawDelta, float pitchDelta) {
        this.currentYaw = (float)((double)this.currentYaw + (double)yawDelta * 0.15);
        this.currentPitch = (float)((double)this.currentPitch - (double)pitchDelta * 0.15);
        this.currentPitch = MathUtil.clamp(this.currentPitch, -90.0f, 90.0f);
    }

    public void setReferenceEntity(EntityLivingBase referenceEntity) {
        this.referenceEntity = referenceEntity;
    }

    public void setYawOffset(float yawOffset) {
        this.yawOffset = yawOffset;
    }

    static {
        ZERO_ROTATION = new RotationAngles(0.0f, 0.0f);
    }

    @Override
    public void setTarget(double x, double y, double z) {
        this.setTarget(Vec3.create(x, y, z));
    }

    @Override
    public boolean shouldRetainAfterCompletion() {
        return !this.relativeMode;
    }

    @Override
    public float getCurrentPitch() {
        return this.currentPitch;
    }

    public void setPrimaryColor(MutableColor color) {
        this.primaryColor = color;
    }

    public MutableColor getSecondaryColor() {
        return this.secondaryColor;
    }

    @Override
    public void update(EntityPlayerSP player, GuiScreen screen) {
        if (player.isNull() || this.blockWhenScreenOpen && screen.isNotNull()) {
            return;
        }
        this.refreshTargetRotation();
        boolean yawComplete = this.updateYaw();
        boolean pitchComplete = this.updatePitch();
        boolean rotationComplete = yawComplete && pitchComplete
                && Math.abs(this.pendingYawDelta) < 1.0f
                && Math.abs(this.pendingPitchDelta) < 1.0f;
        if (this.aiRotationActive) {
            this.setComplete(false);
            return;
        }
        if (!this.relativeMode && rotationComplete && !this.shouldRetainAfterCompletion()) {
            this.setRelativeMode(true);
        } else {
            this.setComplete(rotationComplete);
        }
    }

    private float calculatePitchJitter() {
        float primaryWave = (float)(Math.sin(this.pitchJitterPhase) * (double)this.pitchJitterAmplitude);
        float secondaryWave = (float)(Math.sin(this.pitchJitterPhase * 3.1f + 0.7f)
                * (double)this.pitchJitterAmplitude * 0.25);
        return primaryWave + secondaryWave;
    }

    public AdaptiveRotationController(Vec3 target) {
        this(0.0f, 0.0f);
        EntityPlayerSP player = Minecraft.thePlayer();
        double eyeHeight = ForgeVersion.MC_1_7_10.Y() ? (double)player.X() : 0.0;
        Vec3 targetPosition = Vec3.create(target.getX(), target.getY(), target.getZ());
        Vec3 eyePosition = Vec3.create(player.c(), player.A() + eyeHeight, player.Z());
        RotationAngles rotationAngles = RotationVectorMath.H(
                eyePosition, targetPosition, this.getCurrentYaw(), this.isNormalizeTargetYaw());
        this.targetYaw = rotationAngles.getYaw();
        this.targetPitch = rotationAngles.getPitch();
    }

    @Override
    public void setTarget(Vec3 target) {
        this.fixedTargetRotation = null;
        this.target = target;
    }

    @Override
    public boolean updateYaw() {
        if (this.targetYaw == UNSET_ROTATION) {
            return true;
        }
        if (this.aiRotationActive) {
            return true;
        }
        float mouseScale = this.getMouseScale();
        float predictedYaw = this.getCurrentYaw() + (float)(int)this.pendingYawDelta * mouseScale * 0.15f;
        float predictedPitch = this.getCurrentPitch() - (float)(int)(-this.pendingPitchDelta) * mouseScale * 0.15f;
        double yawError = MathUtil.wrapAngleTo180((this.targetYaw - predictedYaw) % 360.0f);
        double pitchError = MathUtil.wrapAngleTo180((this.targetPitch - predictedPitch) % 360.0f);
        double absoluteYawError = Math.abs(yawError);
        if (!this.isOutsideTolerance(absoluteYawError)) {
            return true;
        }
        double step = this.getSpeed() * 0.25;
        double axisRatio = absoluteYawError / Math.abs(pitchError);
        if (this.isAxisScalingEnabled() && axisRatio < 1.0) {
            step *= axisRatio;
        }
        step = this.applyYawAcceleration(step, absoluteYawError);
        this.pendingYawDelta = this.addPendingStep(this.pendingYawDelta, yawError, step);
        return false;
    }

    public float getRenderedPitch() {
        EntityLivingBase reference = this.referenceEntity != null ? this.referenceEntity : Minecraft.F();
        float renderedPitch = this.relativeMode ? reference.V() + this.currentPitch : this.currentPitch;
        return MathUtil.clamp(renderedPitch, -90.0f, 90.0f);
    }

    public void setUseSecondaryColor(boolean useSecondaryColor) {
        this.useSecondaryColor = useSecondaryColor;
    }

    public boolean blocksWhenScreenOpen() {
        return this.blockWhenScreenOpen;
    }

    public void setReferenceYawOverride(Float yawOverride) {
        this.referenceYawOverride = yawOverride;
    }

    @Override
    public void onPostRenderTick(EventPostRenderTick event) {
        if (this.relativeMode && this.referenceEntity == null) {
            float playerYawDelta = event.getThePlayer().J() - this.savedYaw;
            float playerPitchDelta = event.getThePlayer().V() - this.savedPitch;
            float remainingYaw = Math.abs(this.currentYaw - playerYawDelta);
            if (remainingYaw < Math.abs(this.currentYaw)) {
                this.currentYaw -= playerYawDelta;
            }
            float remainingPitch = Math.abs(this.currentPitch - playerPitchDelta);
            if (remainingPitch < Math.abs(this.currentPitch)) {
                this.currentPitch -= playerPitchDelta;
            }
        }
    }

    @Override
    public Vec3 getTarget() {
        return this.target;
    }

    public float getRenderedYaw() {
        EntityLivingBase reference = this.referenceEntity != null ? this.referenceEntity : Minecraft.F();
        if (this.relativeMode) {
            return reference.J() + this.currentYaw;
        }
        return this.currentYaw;
    }

    @Override
    public float getCurrentYaw() {
        return this.currentYaw;
    }

    public boolean usesSecondaryColor() {
        return this.useSecondaryColor;
    }

    @Override
    public void setTargetRotation(float yaw, float pitch) {
        this.target = null;
        super.setTargetRotation(yaw, pitch);
    }

    public void setCurrentPitch(float pitch) {
        this.currentPitch = pitch;
    }

    public boolean isNormalizeTargetYaw() {
        return this.normalizeTargetYaw;
    }

    public void setCurrentYaw(float yaw) {
        this.currentYaw = yaw;
    }

    public void applyAiRotationDelta(float yawDelta, float pitchDelta) {
        this.aiRotationActive = true;
        this.target = null;
        float conversion = 1.0f / (this.getMouseScale() * 0.15f);
        this.pendingYawDelta = yawDelta * conversion;
        this.pendingPitchDelta = pitchDelta * conversion;
        this.setComplete(false);
    }

    public void clearAiRotationMode() {
        this.aiRotationActive = false;
        this.pendingYawDelta = 0.0f;
        this.pendingPitchDelta = 0.0f;
    }

    public void setRelativeMode(boolean relativeMode) {
        if (this.relativeMode != relativeMode) {
            EntityLivingBase reference = this.referenceEntity != null ? this.referenceEntity : Minecraft.F();
            if (relativeMode) {
                this.setReferenceYawOverride(null);
                float yawDifference = reference.J() - this.currentYaw;
                float pitchDifference = reference.V() - this.currentPitch;
                float yawNormalizationOffset = 0.0f;
                while (yawDifference + yawNormalizationOffset > 180.0f) {
                    yawNormalizationOffset -= 360.0f;
                }
                while (yawDifference + yawNormalizationOffset < -180.0f) {
                    yawNormalizationOffset += 360.0f;
                }
                if (yawNormalizationOffset != 0.0f) {
                    reference.H(reference.J() + yawNormalizationOffset);
                    reference.z(reference.s() + yawNormalizationOffset);
                    reference.D(reference.j() + yawNormalizationOffset);
                    if (reference.isInstance(MappedClasses.z5)) {
                        EntityPlayerSP player = new EntityPlayerSP(reference);
                        player.F(player.q$src$F$1u6qsjx() + yawNormalizationOffset);
                    }
                }
                this.currentYaw = MathUtil.wrapAngleTo180(-yawDifference);
                this.currentPitch = MathUtil.clamp(-pitchDifference, -90.0f, 90.0f);
            } else {
                this.currentYaw += reference.J();
                this.currentPitch += reference.V();
                this.currentPitch = MathUtil.clamp(this.currentPitch, -90.0f, 90.0f);
            }
            this.setComplete(false);
        }
        this.relativeMode = relativeMode;
    }
}
