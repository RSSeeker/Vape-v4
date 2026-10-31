package gg.vape.module.kiss;

import gg.vape.event.EventHandler;
import gg.vape.event.impl.EventPreTick;
import gg.vape.mapping.MappedClasses;
import gg.vape.module.Category;
import gg.vape.module.Mod;
import gg.vape.rotation.RotationManager;
import gg.vape.rotation.AdaptiveRotationController;
import gg.vape.unmap.ModeOption;
import gg.vape.value.BooleanValue;
import gg.vape.value.ModeValue;
import gg.vape.value.NumberValue;
import gg.vape.wrapper.impl.CPacketHeldItemChange;
import gg.vape.wrapper.impl.Entity;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.InventoryPlayer;
import gg.vape.wrapper.impl.ItemStack;
import gg.vape.wrapper.impl.KeyBinding;
import gg.vape.wrapper.impl.Minecraft;

/**
 * Kiss AutoThrow (自动投掷) — finds a target inside the configured band, switches to a projectile
 * and throws it with a low-arc solution. Ported from KissClient's world/AutoThrow.java.
 */
public class KissAutoThrow
extends Mod {
    private static final int MOD_ID = -1406180257;
    private static final int PHASE_IDLE = 0;
    private static final int PHASE_SWITCH = 1;
    private static final int PHASE_THROW = 2;
    private static final int PHASE_REPLACE = 3;
    private static final int SWITCH_DELAY = 1;
    private static final int REPLACE_DELAY = 2;
    private static final int SESSION_COOLDOWN = 5;
    private static final double CLOSE_STOP_DISTANCE = 3.1;
    private static final float PROJECTILE_VELOCITY = 0.6f;
    private static final float PROJECTILE_GRAVITY = 0.006f;

    private final ModeOption legitMode = new ModeOption("Legit");
    private final ModeOption silentMode = new ModeOption("Silent");
    private final ModeValue aimMode = ModeValue.create(this, "Aim Mode",
            "Legit - turns your camera\nSilent - keeps your camera and only changes outgoing rotation",
            this.legitMode, this.legitMode, this.silentMode);

    private final ModeOption singleMode = new ModeOption("Single");
    private final ModeOption switchMode = new ModeOption("Switch");
    private final ModeValue mode = ModeValue.create(this, "Mode",
            "Single - focus one target\nSwitch - throw at the closest target each time",
            this.singleMode, this.singleMode, this.switchMode);

    private final NumberValue fov = NumberValue.create(this, "FOV", "#", "\u00b0", 1.0, 180.0, 360.0, 1.0);
    private final NumberValue minDistance = NumberValue.create(this, "Min Distance", "#.#", "m", 3.0, 5.0, 30.0, 0.5);
    private final NumberValue maxDistance = NumberValue.create(this, "Max Distance", "#.#", "m", 3.0, 10.0, 30.0, 0.5);
    private final NumberValue throwDelay = NumberValue.create(this, "Delay", "#", "ms", 50.0, 400.0, 2000.0, 10.0);
    private final BooleanValue switchBack = BooleanValue.create(this, "Switch Back", true, "Return to the weapon slot after throwing");

    private int phase = PHASE_IDLE;
    private int originalSlot = -1;
    private int switchStartTick = -1;
    private int replaceStartTick = -1;
    private int nextSessionTick;
    private int tickCount;
    private long lastThrowMillis;
    private Entity lockedTarget;
    private AdaptiveRotationController controller;

    public KissAutoThrow() {
        super("Kiss AutoThrow", MOD_ID, Category.KISS,
                "Auto throw ported from KissClient: throws projectiles at targets inside a distance band.");
    }

    @Override
    public void onEnable() {
        this.resetState();
        this.tickCount = 0;
        this.lastThrowMillis = 0L;
        this.lockedTarget = null;
    }

    @Override
    public void onDisable() {
        this.abortMacro();
        this.releaseController();
        this.lockedTarget = null;
    }

    @EventHandler
    public void onTick(EventPreTick event) {
        EntityPlayerSP player = Minecraft.thePlayer();
        if (player == null || player.isNull() || Minecraft.theWorld() == null || Minecraft.theWorld().isNull()) {
            return;
        }
        if (Minecraft.currentScreen() != null && !Minecraft.currentScreen().isNull()) {
            this.abortMacro();
            return;
        }
        ++this.tickCount;
        switch (this.phase) {
            case PHASE_IDLE:
                this.doIdle(player);
                break;
            case PHASE_SWITCH:
                this.doSwitch(player);
                break;
            case PHASE_THROW:
                this.doThrow(player);
                break;
            case PHASE_REPLACE:
                this.doReplace(player);
                break;
            default:
                break;
        }
    }

    private void resetState() {
        this.phase = PHASE_IDLE;
        this.originalSlot = -1;
        this.switchStartTick = -1;
        this.replaceStartTick = -1;
        this.nextSessionTick = 0;
    }

    private void doIdle(EntityPlayerSP player) {
        if (this.tickCount < this.nextSessionTick) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - this.lastThrowMillis < this.throwDelay.getValue().longValue()) {
            return;
        }
        Entity target = this.resolveTarget(player);
        if (target == null) {
            this.lockedTarget = null;
            return;
        }
        int projectileSlot = this.findProjectileSlot(player);
        if (projectileSlot < 0) {
            return;
        }
        InventoryPlayer inventory = player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6();
        if (inventory == null || inventory.isNull()) {
            return;
        }
        this.lockedTarget = target;
        this.originalSlot = inventory.v();
        this.selectSlot(player, projectileSlot);
        this.switchStartTick = this.tickCount;
        this.phase = PHASE_SWITCH;
    }

    private void doSwitch(EntityPlayerSP player) {
        if (this.tickCount - this.switchStartTick < SWITCH_DELAY) {
            return;
        }
        if (!this.isProjectile(this.held(player))) {
            this.abortMacro();
            return;
        }
        this.phase = PHASE_THROW;
    }

    private void doThrow(EntityPlayerSP player) {
        Entity target = this.resolveTarget(player);
        if (target == null) {
            this.abortMacro();
            return;
        }
        this.aimAt(player, target);
        this.throwOne(player);
        this.lastThrowMillis = System.currentTimeMillis();
        this.replaceStartTick = this.tickCount;
        this.phase = PHASE_REPLACE;
        if (this.mode.getSelectedIndex() == 1) {
            this.lockedTarget = null;
        }
    }

    private void doReplace(EntityPlayerSP player) {
        if (this.tickCount - this.replaceStartTick < REPLACE_DELAY) {
            return;
        }
        int target = -1;
        if (this.switchBack.getEffectiveValue()) {
            int weaponSlot = this.findWeaponSlot(player);
            target = weaponSlot >= 0 ? weaponSlot : this.originalSlot;
        }
        if (target >= 0) {
            this.selectSlot(player, target);
        }
        this.releaseController();
        this.nextSessionTick = this.tickCount + SESSION_COOLDOWN;
        this.resetState();
    }

    private void abortMacro() {
        if (this.phase != PHASE_IDLE && this.originalSlot >= 0) {
            this.selectSlot(Minecraft.thePlayer(), this.originalSlot);
        }
        this.releaseController();
        this.resetState();
    }

    private Entity resolveTarget(EntityPlayerSP player) {
        if (this.lockedTarget != null && this.isLockedTargetValid(player, this.lockedTarget)) {
            return this.lockedTarget;
        }
        return this.findNearestTarget(player);
    }

    private boolean isLockedTargetValid(EntityPlayerSP player, Entity target) {
        if (target == null || target.isNull() || target.getObject() == player.getObject()) {
            return false;
        }
        if (target.M$src$Z$ff28xj() || !target.isInstance(MappedClasses.zm) || !target.isInstance(MappedClasses.Yl)) {
            return false;
        }
        double distance = player.getDistanceToEntity(target);
        return distance >= this.minDistance.getValue() && distance <= this.maxDistance.getValue();
    }

    private Entity findNearestTarget(EntityPlayerSP player) {
        Entity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Object handle : Minecraft.theWorld().z()) {
            Entity entity = new Entity(handle);
            if (entity.isNull() || entity.getObject() == player.getObject()) {
                continue;
            }
            if (entity.M$src$Z$ff28xj() || !entity.isInstance(MappedClasses.zm) || !entity.isInstance(MappedClasses.Yl)) {
                continue;
            }
            double distance = player.getDistanceToEntity(entity);
            if (distance < this.minDistance.getValue() || distance > this.maxDistance.getValue()) {
                continue;
            }
            if (distance <= CLOSE_STOP_DISTANCE) {
                continue;
            }
            if (!this.isInFov(player, entity)) {
                continue;
            }
            if (!player.canEntityBeSeen(entity)) {
                continue;
            }
            if (distance >= bestDistance) {
                continue;
            }
            bestDistance = distance;
            best = entity;
        }
        return best;
    }

    private boolean isInFov(EntityPlayerSP player, Entity entity) {
        double dx = entity.z() - player.z();
        double dz = entity.h() - player.h();
        float targetYaw = (float)Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float delta = wrapAngle(targetYaw - player.J());
        return Math.abs(delta) <= this.fov.getValue() / 2.0;
    }

    private void aimAt(EntityPlayerSP player, Entity target) {
        float[] rotation = this.calculateThrowRotation(player, target);
        if (rotation == null) {
            return;
        }
        if (this.aimMode.getSelectedIndex() == 1) {
            AdaptiveRotationController adaptive = this.ensureController();
            if (adaptive == null) {
                return;
            }
            float deltaYaw = wrapAngle(rotation[0] - RotationManager.INSTANCE.getManagedYaw());
            float deltaPitch = rotation[1] - RotationManager.INSTANCE.getManagedPitch();
            adaptive.applyAiRotationDelta(deltaYaw, deltaPitch);
            return;
        }
        player.z(rotation[0]);
        player.C(rotation[1]);
    }

    private AdaptiveRotationController ensureController() {
        if (this.controller != null) {
            return this.controller;
        }
        try {
            AdaptiveRotationController adaptive = new AdaptiveRotationController();
            adaptive.setNormalizeTargetYaw(false);
            adaptive.setRetainAfterCompletion(true);
            adaptive.setClampStepToRemaining(true);
            adaptive.setTolerance(0.0f);
            adaptive.setScaleAxesProportionally(false);
            RotationManager.INSTANCE.setController(adaptive);
            this.controller = adaptive;
            return adaptive;
        }
        catch (Throwable ignored) {
            return null;
        }
    }

    private void releaseController() {
        if (this.controller == null) {
            return;
        }
        try {
            this.controller.setComplete(true);
            this.controller.setRetainAfterCompletion(false);
            RotationManager.INSTANCE.releaseController(this.controller);
        }
        catch (Throwable ignored) {
        }
        this.controller = null;
    }

    private float[] calculateThrowRotation(EntityPlayerSP player, Entity target) {
        double targetX = target.z();
        double targetY = target.N() + target.Y() * 0.55;
        double targetZ = target.h();
        double motionX = target.z() - target.M();
        double motionZ = target.h() - target.m$src$D$fwnne5();
        double time = 0.0;
        for (int i = 0; i < 3; ++i) {
            double predictedX = targetX + motionX * time;
            double predictedZ = targetZ + motionZ * time;
            double dx = predictedX - player.z();
            double dz = predictedZ - player.h();
            time = Math.sqrt(dx * dx + dz * dz) / PROJECTILE_VELOCITY;
        }
        double predictedX = targetX + motionX * time;
        double predictedZ = targetZ + motionZ * time;
        double dx = predictedX - player.z();
        double dy = targetY - (player.N() + player.X());
        double dz = predictedZ - player.h();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < 0.001) {
            return null;
        }
        float yaw = (float)Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float pitch = -this.getLowArcPitch((float)horizontal, (float)dy, PROJECTILE_VELOCITY, PROJECTILE_GRAVITY);
        pitch = Math.max(-90.0f, Math.min(90.0f, pitch));
        return new float[]{yaw, pitch};
    }

    private float getLowArcPitch(float distance, float height, float velocity, float gravity) {
        float velocitySq = velocity * velocity;
        float root = velocitySq * velocitySq - gravity * (gravity * distance * distance + 2.0f * height * velocitySq);
        if (root <= 0.0f) {
            return (float)Math.toDegrees(Math.atan2(height, distance));
        }
        return (float)Math.toDegrees(Math.atan((velocitySq - (float)Math.sqrt(root)) / (gravity * distance)));
    }

    private void throwOne(EntityPlayerSP player) {
        if (!this.isProjectile(this.held(player))) {
            return;
        }
        try {
            KeyBinding useItemKey = Minecraft.gameSettings().b$src$Lgg_vape_wrapper_impl_KeyBinding_$1yi3362();
            KeyBinding.setKeyBindState(useItemKey, true);
            KeyBinding.onTick(useItemKey);
            KeyBinding.setKeyBindState(useItemKey, false);
        }
        catch (Throwable ignored) {
        }
    }

    private ItemStack held(EntityPlayerSP player) {
        try {
            return player.getHeldItemHand();
        }
        catch (Throwable ignored) {
            return null;
        }
    }

    private int findProjectileSlot(EntityPlayerSP player) {
        InventoryPlayer inventory = player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6();
        if (inventory == null || inventory.isNull()) {
            return -1;
        }
        if (this.isProjectile(inventory.c(inventory.v()))) {
            return inventory.v();
        }
        for (int slot = 0; slot < 9; ++slot) {
            if (this.isProjectile(inventory.c(slot))) {
                return slot;
            }
        }
        return -1;
    }

    private int findWeaponSlot(EntityPlayerSP player) {
        InventoryPlayer inventory = player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6();
        if (inventory == null || inventory.isNull()) {
            return -1;
        }
        int bestSlot = -1;
        double bestDamage = 0.0;
        for (int slot = 0; slot < 9; ++slot) {
            ItemStack stack = inventory.c(slot);
            if (stack == null || stack.isNull()) {
                continue;
            }
            String name = this.itemName(stack);
            double damage;
            if (name.contains("sword")) {
                damage = 7.0;
            }
            else if (name.contains("axe")) {
                damage = 6.0;
            }
            else {
                continue;
            }
            if (damage <= bestDamage) {
                continue;
            }
            bestDamage = damage;
            bestSlot = slot;
        }
        return bestSlot;
    }

    private boolean isProjectile(ItemStack stack) {
        String name = this.itemName(stack);
        return name.contains("snowball") || name.contains("egg") || name.contains("ender_pearl");
    }

    private String itemName(ItemStack stack) {
        if (stack == null || stack.isNull()) {
            return "";
        }
        try {
            String name = stack.getItem().A();
            return name == null ? "" : name.toLowerCase();
        }
        catch (Throwable ignored) {
            return "";
        }
    }

    private void selectSlot(EntityPlayerSP player, int slot) {
        if (player == null || player.isNull()) {
            return;
        }
        InventoryPlayer inventory = player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6();
        if (inventory == null || inventory.isNull()) {
            return;
        }
        inventory.g(slot);
        try {
            player.sendQueue().addToSendQueue(CPacketHeldItemChange.create(slot));
        }
        catch (Throwable ignored) {
        }
    }

    private static float wrapAngle(float angle) {
        angle = angle % 360.0f;
        if (angle >= 180.0f) {
            angle -= 360.0f;
        }
        if (angle < -180.0f) {
            angle += 360.0f;
        }
        return angle;
    }

    @Override
    public String getDetailedSuffix() {
        return this.mode.getSelectedIndex() == 1 ? "Switch" : "Single";
    }
}
