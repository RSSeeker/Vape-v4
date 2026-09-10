package gg.vape.module.combat.automace;

import gg.vape.rotation.AdaptiveRotationController;
import gg.vape.rotation.PlayerMouseRotationApplier;
import gg.vape.utils.MathUtil;
import gg.vape.wrapper.impl.AxisAlignedBB;
import gg.vape.wrapper.impl.EntityLivingBase;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.GuiScreen;
import gg.vape.wrapper.impl.Minecraft;

public class AutoMaceRotationController extends AdaptiveRotationController {
    private static final float MAX_ANGLE_PER_TICK = 12.0f;
    private static final float ROTATION_UPDATES_PER_TICK = 1.875f;
    private static final double MIN_HORIZONTAL_DISTANCE = 0.35;

    private final boolean silent;
    private EntityLivingBase target;
    private boolean predictionValid;
    private int impactTick;
    private double sourceX;
    private double sourceY;
    private double sourceZ;
    private double aimX;
    private double aimY;
    private double aimZ;
    private boolean directlyReachable;
    private boolean committedToAim;
    private boolean retainVisibleRotation = true;
    private float currentSpeed = 1.0f;

    public AutoMaceRotationController(boolean silent) {
        this.silent = silent;
        this.setClampStepToRemaining(true);
        this.setTolerance(0.0f);
        this.setRetainAfterCompletion(true);
    }

    public void setTargetEntity(EntityLivingBase target) {
        this.target = target;
    }

    public void setPrediction(boolean predictionValid, int impactTick,
                              double sourceX, double sourceY, double sourceZ,
                              double aimX, double aimY, double aimZ,
                              boolean directlyReachable) {
        this.predictionValid = predictionValid;
        this.impactTick = impactTick;
        this.sourceX = sourceX;
        this.sourceY = sourceY;
        this.sourceZ = sourceZ;
        this.aimX = aimX;
        this.aimY = aimY;
        this.aimZ = aimZ;
        this.directlyReachable = directlyReachable;
    }

    public boolean isDirectlyReachable() {
        return this.directlyReachable;
    }

    public boolean isCommittedToAim() {
        return this.committedToAim;
    }

    public void clearTarget() {
        this.predictionValid = false;
        this.directlyReachable = false;
        this.committedToAim = false;
        this.target = null;
        if (!this.silent) {
            this.retainVisibleRotation = false;
            super.setRetainAfterCompletion(false);
            this.setComplete(true);
        }
    }

    @Override
    public void update(EntityPlayerSP player, GuiScreen screen) {
        if (!this.silent) {
            if (player.isNull()) {
                return;
            }
            if (!this.retainVisibleRotation) {
                this.setComplete(true);
                return;
            }
            this.refreshTargetRotation();
            boolean yawComplete = this.updateYaw();
            boolean pitchComplete = this.updatePitch();
            this.setComplete(yawComplete && pitchComplete
                    && Math.abs(this.pendingYawDelta) < 1.0f
                    && Math.abs(this.pendingPitchDelta) < 1.0f);
            return;
        }
        super.update(player, screen);
    }

    @Override
    public void refreshTargetRotation() {
        if (this.isRelativeMode()) {
            this.currentSpeed = 7.0f;
            this.setSpeed(this.currentSpeed);
            super.refreshTargetRotation();
            return;
        }

        EntityPlayerSP player = Minecraft.thePlayer();
        if (player.isNull() || this.target == null || this.target.isNull()) {
            return;
        }

        if (this.directlyReachable) {
            this.committedToAim = true;
            this.currentSpeed = this.speedForAngle(this.angleToCurrentTarget(player), 1);
            this.setSpeed(this.currentSpeed);
            this.aimAtCurrentTarget(player);
            return;
        }

        if (!this.predictionValid) {
            this.committedToAim = false;
            this.freezeRotation();
            return;
        }

        float[] predictedAngles = this.calculateAngles(
                this.sourceX, this.sourceY, this.sourceZ, this.aimX, this.aimY, this.aimZ);
        float yawError = Math.abs(MathUtil.wrapAngleTo180(predictedAngles[0] - this.getCurrentYaw()));
        float pitchError = Math.abs(MathUtil.wrapAngleTo180(predictedAngles[1] - this.getCurrentPitch()));
        float maxError = Math.max(yawError, pitchError);
        int requiredTicks = this.requiredTicks(maxError);
        if (this.committedToAim || this.impactTick <= requiredTicks) {
            this.committedToAim = true;
            this.currentSpeed = this.speedForAngle(maxError, this.impactTick - 1);
            this.setSpeed(this.currentSpeed);
            this.setTargetRotation(predictedAngles[0], predictedAngles[1]);
            return;
        }

        this.committedToAim = false;
        this.freezeRotation();
    }

    @Override
    public void applyMouseDelta(float yawDelta, float pitchDelta) {
        if (this.silent) {
            super.applyMouseDelta(yawDelta, pitchDelta);
            return;
        }
        PlayerMouseRotationApplier.applyTrackedMouseDelta(yawDelta, pitchDelta);
    }

    @Override
    public float getCurrentYaw() {
        if (!this.silent && !this.isRelativeMode()) {
            return Minecraft.F().J();
        }
        return super.getCurrentYaw();
    }

    @Override
    public float getCurrentPitch() {
        if (!this.silent && !this.isRelativeMode()) {
            return Minecraft.F().V();
        }
        return super.getCurrentPitch();
    }

    @Override
    public float getRenderedYaw() {
        if (!this.silent && !this.isRelativeMode()) {
            return Minecraft.F().J();
        }
        return super.getRenderedYaw();
    }

    @Override
    public float getRenderedPitch() {
        if (!this.silent && !this.isRelativeMode()) {
            return Minecraft.F().V();
        }
        return super.getRenderedPitch();
    }

    @Override
    public float getSpeed() {
        return this.currentSpeed;
    }

    @Override
    public boolean shouldRetainAfterCompletion() {
        if (this.silent) {
            return super.shouldRetainAfterCompletion();
        }
        return this.retainVisibleRotation;
    }

    @Override
    public void setRetainAfterCompletion(boolean retainAfterCompletion) {
        super.setRetainAfterCompletion(retainAfterCompletion);
        this.retainVisibleRotation = retainAfterCompletion;
    }

    private void freezeRotation() {
        this.currentSpeed = 1.0f;
        this.setSpeed(this.currentSpeed);
        this.setTargetRotation(this.getCurrentYaw(), this.getCurrentPitch());
    }

    private void aimAtCurrentTarget(EntityPlayerSP player) {
        AxisAlignedBB bounds = this.target.u$src$Lgg_vape_wrapper_impl_AxisAlignedBB_$kogbsu();
        double x = (bounds.getMinX() + bounds.getMaxX()) * 0.5;
        double y = bounds.getMinY() + (bounds.getMaxY() - bounds.getMinY()) * 0.75;
        double z = (bounds.getMinZ() + bounds.getMaxZ()) * 0.5;
        float[] angles = this.calculateAngles(player.z(), player.N() + player.X(), player.h(), x, y, z);
        this.setTargetRotation(angles[0], angles[1]);
    }

    private float angleToCurrentTarget(EntityPlayerSP player) {
        AxisAlignedBB bounds = this.target.u$src$Lgg_vape_wrapper_impl_AxisAlignedBB_$kogbsu();
        double x = (bounds.getMinX() + bounds.getMaxX()) * 0.5;
        double y = bounds.getMinY() + (bounds.getMaxY() - bounds.getMinY()) * 0.75;
        double z = (bounds.getMinZ() + bounds.getMaxZ()) * 0.5;
        float[] angles = this.calculateAngles(player.z(), player.N() + player.X(), player.h(), x, y, z);
        float yawError = Math.abs(MathUtil.wrapAngleTo180(angles[0] - this.getCurrentYaw()));
        float pitchError = Math.abs(MathUtil.wrapAngleTo180(angles[1] - this.getCurrentPitch()));
        return Math.max(yawError, pitchError);
    }

    private float speedForAngle(float angle, int ticks) {
        float speed = angle / Math.max(ROTATION_UPDATES_PER_TICK * Math.max(ticks, 1), 0.01f);
        return Math.max(1.0f, Math.min(this.maximumSpeed(), speed));
    }

    private float maximumSpeed() {
        EntityPlayerSP player = Minecraft.thePlayer();
        if (player.isNull()) {
            return 75.0f;
        }
        return 75.0f + (float)((int)(player.N() * 100.0) % 5);
    }

    private int requiredTicks(float angle) {
        int ticks = (int)Math.ceil(angle / MAX_ANGLE_PER_TICK) + 1;
        return Math.max(2, Math.min(4, ticks));
    }

    private float[] calculateAngles(double fromX, double fromY, double fromZ,
                                    double toX, double toY, double toZ) {
        double deltaX = toX - fromX;
        double deltaY = toY - fromY;
        double deltaZ = toZ - fromZ;
        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        float yaw = horizontalDistance < MIN_HORIZONTAL_DISTANCE
                ? this.getCurrentYaw()
                : (float)(Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0);
        float pitch = (float)(-Math.toDegrees(Math.atan2(deltaY, Math.max(horizontalDistance, 1.0E-4))));
        return new float[]{yaw, MathUtil.clamp(pitch, -90.0f, 90.0f)};
    }
}
