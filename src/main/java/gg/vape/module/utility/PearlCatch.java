package gg.vape.module.utility;

import gg.vape.event.EventHandler;
import gg.vape.event.impl.EventPreTick;
import gg.vape.module.UtilityMod;
import gg.vape.module.control.SharedModuleControlClaims;
import gg.vape.rotation.AdaptiveRotationController;
import gg.vape.rotation.FixedRotationController;
import gg.vape.rotation.RotationControlClaim;
import gg.vape.rotation.RotationManager;
import gg.vape.unmap.ModeOption;
import gg.vape.value.BooleanValue;
import gg.vape.value.ModeValue;
import gg.vape.value.NumberValue;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.InventoryPlayer;
import gg.vape.wrapper.impl.Item;
import gg.vape.wrapper.impl.ItemStack;
import gg.vape.wrapper.impl.KeyBinding;
import gg.vape.wrapper.impl.Minecraft;

/**
 * Throws an ender pearl and then intersects it with a wind charge.
 *
 * <p>The trajectory constants and search ranges mirror the 4.21 beta module:
 * pearl drag is 0.99, pearl gravity is 0.03 and both projectiles start at
 * 1.5 blocks per tick.</p>
 */
public class PearlCatch extends UtilityMod {
    private static final double PROJECTILE_SPEED = 1.5;
    private static final double PEARL_DRAG = 0.99;
    private static final double PEARL_GRAVITY = 0.03;
    private static final double MAX_INTERCEPT_DISTANCE_SQUARED = 0.36;
    private static final int MAX_FLIGHT_TICKS = 30;
    private static final int MAX_AIM_TICKS = 40;

    private final ModeOption upwardMode = new ModeOption("Upward");
    private final ModeOption currentAimMode = new ModeOption("Current aim");
    private final ModeValue aimMode = ModeValue.create(
            this,
            "Aim mode",
            "Upward - throws the pearl straight up\nCurrent aim - throws the wind charge where you were looking",
            this.upwardMode,
            this.upwardMode,
            this.currentAimMode);
    private final NumberValue aimSpeed = NumberValue.create(
            this, "Aim speed", "#.#", "", 1.0, 10.0, 20.0);
    private final BooleanValue silentAim = BooleanValue.create(this, "Silent aim", true, null);
    private final NumberValue chargeDelay = NumberValue.create(
            this,
            "Charge delay",
            "#",
            " ticks",
            0.0,
            0.0,
            10.0,
            1.0,
            "Ticks to wait after the pearl before throwing the wind charge");

    private final RotationControlClaim rotationClaim = SharedModuleControlClaims.rotation;

    private State state = State.IDLE;
    private FixedRotationController rotationController;
    private InterceptPlan plan;
    private int pearlSlot = -1;
    private int chargeSlot = -1;
    private int savedSlot = -1;
    private int stateTicks;
    private int ticksSincePearl;

    public PearlCatch() {
        super("PearlCatch", "Throws a pearl, then throws a wind charge to catch it");
        this.addValue(this.aimMode, this.aimSpeed, this.silentAim, this.chargeDelay);
        this.rotationClaim.setPriority(this, 7);
    }

    @Override
    public void onEnable() {
        EntityPlayerSP player = Minecraft.thePlayer();
        if (player.isNull() || Minecraft.theWorld().isNull()) {
            this.abort();
            return;
        }

        InventoryPlayer inventory = player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6();
        this.pearlSlot = this.findHotbarSlot(inventory, "minecraft:ender_pearl", "Ender Pearl");
        this.chargeSlot = this.findHotbarSlot(inventory, "minecraft:wind_charge", "Wind Charge", "Windcharge");
        if (this.pearlSlot == -1 || this.chargeSlot == -1) {
            this.abort();
            return;
        }

        this.savedSlot = inventory.v();
        int configuredDelay = this.chargeDelay.getValue().intValue();
        this.plan = this.currentAimMode.isSelected()
                ? this.findCurrentAimPlan(player, Math.max(2, configuredDelay))
                : this.findUpwardPlan(player, configuredDelay);
        if (this.plan == null) {
            this.abort();
            return;
        }

        this.state = State.AIMING_PEARL;
        this.stateTicks = 0;
        this.ticksSincePearl = 0;
        this.setRotation(this.plan.pearlYaw, this.plan.pearlPitch);
    }

    @Override
    public void onDisable() {
        this.releaseRotation();
        EntityPlayerSP player = Minecraft.thePlayer();
        if (player.isNotNull() && this.savedSlot != -1) {
            player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6().g(this.savedSlot);
        }
        this.state = State.IDLE;
        this.rotationController = null;
        this.plan = null;
        this.pearlSlot = -1;
        this.chargeSlot = -1;
        this.savedSlot = -1;
        this.stateTicks = 0;
        this.ticksSincePearl = 0;
    }

    @EventHandler
    public void onTick(EventPreTick event) {
        EntityPlayerSP player = event.getThePlayer();
        if (player.isNull() || Minecraft.theWorld().isNull() || this.plan == null) {
            this.abort();
            return;
        }

        ++this.stateTicks;
        if (this.state == State.AIMING_CHARGE || this.state == State.WAITING_FOR_CHARGE) {
            ++this.ticksSincePearl;
        }
        if (this.stateTicks > MAX_AIM_TICKS && this.state != State.WAITING_FOR_CHARGE) {
            this.abort();
            return;
        }

        switch (this.state) {
            case AIMING_PEARL:
                if (!this.hasRotationControl()) {
                    return;
                }
                if (this.isRotationReady()) {
                    this.state = State.THROWING_PEARL;
                }
                break;
            case THROWING_PEARL:
                this.throwFromSlot(this.pearlSlot);
                this.state = State.AIMING_CHARGE;
                this.stateTicks = 0;
                this.ticksSincePearl = 0;
                this.setRotation(this.plan.chargeYaw, this.plan.chargePitch);
                break;
            case AIMING_CHARGE:
                if (!this.hasRotationControl()) {
                    return;
                }
                if (this.isRotationReady()) {
                    this.state = State.WAITING_FOR_CHARGE;
                    this.stateTicks = 0;
                } else if (this.plan.absoluteChargeTiming
                        && this.ticksSincePearl >= this.plan.chargeTick) {
                    this.abort();
                }
                break;
            case WAITING_FOR_CHARGE:
                boolean readyToThrow = this.plan.absoluteChargeTiming
                        ? this.ticksSincePearl >= this.plan.chargeTick
                        : this.stateTicks >= this.plan.chargeTick;
                if (readyToThrow) {
                    this.throwFromSlot(this.chargeSlot);
                    this.state = State.FINISHED;
                }
                break;
            case FINISHED:
                this.setEnabled(false, true);
                break;
            default:
                break;
        }
    }

    private boolean hasRotationControl() {
        return this.rotationClaim.isOwnedBy(this)
                || this.rotationClaim.acquire(this, this.silentAim.getEffectiveValue());
    }

    private boolean isRotationReady() {
        if (this.rotationController == null || !this.rotationClaim.isOwnedBy(this)) {
            return false;
        }
        if (RotationManager.INSTANCE.getActiveController() != this.rotationController) {
            RotationManager.INSTANCE.setController(this.rotationController);
        }
        return this.rotationController.isComplete();
    }

    private void setRotation(float yaw, float pitch) {
        FixedRotationController controller = this.silentAim.getEffectiveValue()
                ? new AdaptiveRotationController(yaw, pitch)
                : new FixedRotationController(yaw, pitch);
        controller.setTargetRotation(yaw, pitch);
        controller.setSpeed(this.aimSpeed.getValue().floatValue());
        controller.setTolerance(0.35f);
        controller.setScaleAxesProportionally(true);
        controller.setLinearAcceleration(true);
        controller.setClampStepToRemaining(true);
        controller.setRetainAfterCompletion(true);
        this.rotationController = controller;
        if (this.rotationClaim.isOwnedBy(this)) {
            RotationManager.INSTANCE.setController(controller);
        }
    }

    private void releaseRotation() {
        if (this.rotationController != null
                && RotationManager.INSTANCE.getActiveController() == this.rotationController) {
            RotationManager.INSTANCE.releaseController(this.rotationController);
        }
        this.rotationClaim.release(this);
    }

    private void throwFromSlot(int slot) {
        EntityPlayerSP player = Minecraft.thePlayer();
        if (player.isNull()) {
            return;
        }
        player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6().g(slot);
        KeyBinding useKey = Minecraft.gameSettings().b$src$Lgg_vape_wrapper_impl_KeyBinding_$1yi3362();
        SharedModuleControlClaims.rightClickUse.blockUse();
        try {
            KeyBinding.setKeyBindState(useKey, true);
            KeyBinding.onTick(useKey);
        } finally {
            KeyBinding.setKeyBindState(useKey, false);
            SharedModuleControlClaims.rightClickUse.clearClaimed();
        }
    }

    private int findHotbarSlot(InventoryPlayer inventory, String itemId, String... displayNames) {
        Item expected = Item.L(itemId);
        for (int slot = 0; slot < 9; ++slot) {
            ItemStack stack = inventory.c(slot);
            if (stack.isNull() || stack.getItem().isNull()) {
                continue;
            }
            Item item = stack.getItem();
            if (expected != null && expected.isNotNull() && item.equals(expected)) {
                return slot;
            }
            String text = item.toString();
            for (String displayName : displayNames) {
                if (text != null && text.contains(displayName)) {
                    return slot;
                }
            }
        }
        return -1;
    }

    private void abort() {
        if (this.isEnabled()) {
            this.setEnabled(false, true);
        } else {
            this.onDisable();
        }
    }

    private InterceptPlan findUpwardPlan(EntityPlayerSP player, int minimumDelay) {
        float referenceYaw = player.J();
        double horizontalMotionSquared = player.t() * player.t() + player.T() * player.T();
        float[] pearlYaws;
        if (horizontalMotionSquared < 0.003) {
            pearlYaws = new float[]{referenceYaw};
        } else {
            float movementYaw = (float)Math.toDegrees(Math.atan2(-player.t(), player.T()));
            pearlYaws = Math.abs(wrapAngle(movementYaw - referenceYaw)) < 20.0f
                    ? new float[]{referenceYaw}
                    : new float[]{referenceYaw, movementYaw};
        }

        InterceptPlan best = null;
        double bestScore = Double.MAX_VALUE;
        for (float pearlPitch = -90.0f; pearlPitch <= -76.0f; pearlPitch += 0.5f) {
            for (float pearlBaseYaw : pearlYaws) {
                for (float pearlYawOffset = -20.0f; pearlYawOffset <= 20.0f; pearlYawOffset += 2.5f) {
                    float pearlYaw = pearlBaseYaw + pearlYawOffset;
                    for (float chargePitch = -90.0f; chargePitch <= -86.0f; chargePitch += 1.0f) {
                        for (float chargeYawOffset = -20.0f; chargeYawOffset <= 20.0f; chargeYawOffset += 5.0f) {
                            float chargeYaw = referenceYaw + chargeYawOffset;
                            InterceptPlan candidate = this.evaluatePlan(
                                    player, pearlYaw, pearlPitch, chargeYaw, chargePitch,
                                    minimumDelay, minimumDelay, false);
                            if (candidate == null) {
                                continue;
                            }
                            double score = candidate.missDistanceSquared
                                    + Math.abs(wrapAngle(chargeYaw - referenceYaw)) * 0.01
                                    + Math.abs(wrapAngle(pearlYaw - referenceYaw)) * 0.003
                                    + Math.abs(chargePitch + 90.0f) * 0.03;
                            if (score < bestScore) {
                                best = candidate;
                                bestScore = score;
                            }
                        }
                    }
                }
            }
        }
        return best;
    }

    private InterceptPlan findCurrentAimPlan(EntityPlayerSP player, int minimumDelay) {
        float chargeYaw = player.J();
        float chargePitch = player.V();
        InterceptPlan best = null;
        double bestScore = Double.MAX_VALUE;

        for (int delay = Math.max(3, minimumDelay); delay <= Math.max(3, minimumDelay) + 12; ++delay) {
            for (float pitch = -90.0f; pitch <= 60.0f; pitch += 2.5f) {
                for (float yawOffset = -180.0f; yawOffset < 180.0f; yawOffset += 5.0f) {
                    float pearlYaw = chargeYaw + yawOffset;
                    InterceptPlan candidate = this.evaluatePlan(
                            player, pearlYaw, pitch, chargeYaw, chargePitch, delay, delay, true);
                    if (candidate == null) {
                        continue;
                    }
                    double score = candidate.missDistanceSquared
                            + Math.abs(wrapAngle(pearlYaw - chargeYaw)) * 0.0005
                            + delay * 0.0001;
                    if (score < bestScore) {
                        best = candidate;
                        bestScore = score;
                    }
                }
            }
        }
        return best;
    }

    private InterceptPlan evaluatePlan(EntityPlayerSP player,
                                       float pearlYaw, float pearlPitch,
                                       float chargeYaw, float chargePitch,
                                       int chargeTick, int minimumDelay,
                                       boolean absoluteChargeTiming) {
        Vec pearlDirection = direction(pearlYaw, pearlPitch);
        Vec chargeDirection = direction(chargeYaw, chargePitch);
        double playerMotionX = player.t();
        double playerMotionY = player.b$src$Z$fqlxe4() ? 0.0 : player.q();
        double playerMotionZ = player.T();
        double pearlX = player.z();
        double pearlY = player.N() + player.Y() - 0.1;
        double pearlZ = player.h();
        double pearlMotionX = pearlDirection.x * PROJECTILE_SPEED + playerMotionX;
        double pearlMotionY = pearlDirection.y * PROJECTILE_SPEED + playerMotionY;
        double pearlMotionZ = pearlDirection.z * PROJECTILE_SPEED + playerMotionZ;
        double chargeX = player.z() + playerMotionX * chargeTick;
        double chargeY = player.N() + player.Y() + playerMotionY * chargeTick;
        double chargeZ = player.h() + playerMotionZ * chargeTick;
        double chargeMotionX = chargeDirection.x * PROJECTILE_SPEED + playerMotionX;
        double chargeMotionY = chargeDirection.y * PROJECTILE_SPEED + playerMotionY;
        double chargeMotionZ = chargeDirection.z * PROJECTILE_SPEED + playerMotionZ;

        double closest = Double.MAX_VALUE;
        for (int tick = 1; tick <= chargeTick + MAX_FLIGHT_TICKS; ++tick) {
            pearlMotionX *= PEARL_DRAG;
            pearlMotionY = (pearlMotionY - PEARL_GRAVITY) * PEARL_DRAG;
            pearlMotionZ *= PEARL_DRAG;
            pearlX += pearlMotionX;
            pearlY += pearlMotionY;
            pearlZ += pearlMotionZ;
            if (tick <= chargeTick) {
                continue;
            }
            chargeX += chargeMotionX;
            chargeY += chargeMotionY;
            chargeZ += chargeMotionZ;
            double dx = pearlX - chargeX;
            double dy = pearlY - chargeY;
            double dz = pearlZ - chargeZ;
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance < closest) {
                closest = distance;
            }
        }

        if (chargeTick < minimumDelay || closest > MAX_INTERCEPT_DISTANCE_SQUARED) {
            return null;
        }
        return new InterceptPlan(
                pearlYaw, pearlPitch, chargeYaw, chargePitch,
                chargeTick, closest, absoluteChargeTiming);
    }

    private static Vec direction(float yaw, float pitch) {
        double yawRadians = Math.toRadians(yaw);
        double pitchRadians = Math.toRadians(pitch);
        return new Vec(
                -Math.sin(yawRadians) * Math.cos(pitchRadians),
                -Math.sin(pitchRadians),
                Math.cos(yawRadians) * Math.cos(pitchRadians));
    }

    private static float wrapAngle(float angle) {
        angle %= 360.0f;
        if (angle >= 180.0f) {
            angle -= 360.0f;
        }
        if (angle < -180.0f) {
            angle += 360.0f;
        }
        return angle;
    }

    private enum State {
        IDLE,
        AIMING_PEARL,
        THROWING_PEARL,
        AIMING_CHARGE,
        WAITING_FOR_CHARGE,
        FINISHED
    }

    private static final class InterceptPlan {
        private final float pearlYaw;
        private final float pearlPitch;
        private final float chargeYaw;
        private final float chargePitch;
        private final int chargeTick;
        private final double missDistanceSquared;
        private final boolean absoluteChargeTiming;

        private InterceptPlan(float pearlYaw, float pearlPitch,
                              float chargeYaw, float chargePitch,
                              int chargeTick,
                              double missDistanceSquared,
                              boolean absoluteChargeTiming) {
            this.pearlYaw = pearlYaw;
            this.pearlPitch = pearlPitch;
            this.chargeYaw = chargeYaw;
            this.chargePitch = chargePitch;
            this.chargeTick = chargeTick;
            this.missDistanceSquared = missDistanceSquared;
            this.absoluteChargeTiming = absoluteChargeTiming;
        }
    }

    private static final class Vec {
        private final double x;
        private final double y;
        private final double z;

        private Vec(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

    }
}
