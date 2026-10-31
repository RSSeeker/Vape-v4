package gg.vape.module.zen;

import gg.vape.Vape;
import gg.vape.event.EventHandler;
import gg.vape.event.impl.EventPreTick;
import gg.vape.input.MouseInput;
import gg.vape.mapping.MappedClasses;
import gg.vape.module.Category;
import gg.vape.module.Mod;
import gg.vape.module.combat.SilentAura;
import gg.vape.unmap.ModeOption;
import gg.vape.utils.BlockUtil;
import gg.vape.value.BooleanValue;
import gg.vape.value.ModeValue;
import gg.vape.wrapper.impl.BlockPos;
import gg.vape.wrapper.impl.Entity;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.InventoryPlayer;
import gg.vape.wrapper.impl.ItemStack;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.Vec3;
import gg.vape.wrapper.impl.World;
import java.util.ArrayList;

/**
 * Zen MidPearl — full port of OpenZen's player/MidPearl.java.
 *
 * Normal mode: hold the button to swap to an ender pearl (the aura is paused while
 * the pearl is held), release to throw it and restore the previous slot.
 * Intercept mode: predict the flight path of an enemy ender pearl and throw our
 * own pearl at its landing point.
 */
public class ZenMidPearl
extends Mod {
    private static final int MOD_ID = -1607740031;
    private static final double PEARL_VELOCITY = 1.5;
    private static final double PEARL_GRAVITY = 0.03;
    private static final int PEARL_PREDICT_TICKS = 80;
    private static final int PRE_THROW_TICKS = 2;
    private static final int POST_THROW_TICKS = 2;
    private static final int DISABLE_DELAY = 2;

    private final ModeOption middleButton = new ModeOption("Middle");
    private final ModeOption mouse4Button = new ModeOption("Mouse 4");
    private final ModeOption mouse5Button = new ModeOption("Mouse 5");
    private final ModeValue button = ModeValue.create(this, "Button",
            "Mouse button used to hold / throw the pearl",
            this.middleButton, this.middleButton, this.mouse4Button, this.mouse5Button);
    private final BooleanValue intercept = BooleanValue.create(this, "Intercept", false,
            "Throw a pearl at the landing point of an enemy ender pearl");
    private final ModeOption interceptMouse4 = new ModeOption("Mouse 4");
    private final ModeOption interceptMouse5 = new ModeOption("Mouse 5");
    private final ModeValue interceptButton = ModeValue.create(this, "Intercept Button",
            "Mouse button used for interception",
            this.interceptMouse4, this.interceptMouse4, this.interceptMouse5);
    private final BooleanValue onRelease = BooleanValue.create(this, "On Release", false,
            "Throw the interception pearl when the button is released");
    private final BooleanValue blockCheck = BooleanValue.create(this, "Block Check", true,
            "Only intercept when the target point is not occluded");

    private boolean holdingIntercept;
    private boolean pearlSlotSelected;
    private boolean throwPhaseActive;
    private int pearlSavedSlot = -1;
    private int savedOriginalSlot = -1;
    private int throwCountdown;
    private int disableDelay;
    private int preThrowTicks;
    private int postThrowTicks;
    private boolean auraPaused;
    private float savedYaw;
    private float savedPitch;
    private boolean rotationSaved;

    public ZenMidPearl() {
        super("Zen MidPearl", MOD_ID, Category.ZEN,
                "Ender pearl thrower / interceptor, ported from OpenZen's MidPearl.");
    }

    @Override
    public void onEnable() {
        this.reset();
    }

    @Override
    public void onDisable() {
        EntityPlayerSP player = Minecraft.thePlayer();
        if (player != null && !player.isNull()) {
            this.restoreSlot(player);
            this.restoreRotation(player);
        }
        this.resumeAura();
        this.reset();
    }

    private void reset() {
        this.holdingIntercept = false;
        this.pearlSlotSelected = false;
        this.throwPhaseActive = false;
        this.pearlSavedSlot = -1;
        this.savedOriginalSlot = -1;
        this.throwCountdown = 0;
        this.disableDelay = 0;
        this.preThrowTicks = 0;
        this.postThrowTicks = 0;
        this.rotationSaved = false;
    }

    private int buttonIndex(ModeValue value) {
        int index = value.getSelectedIndex();
        if (index == 1) {
            return 3;
        }
        if (index == 2) {
            return 4;
        }
        return 2;
    }

    private boolean isButtonDown(int index) {
        try {
            return MouseInput.isButtonDown(index);
        }
        catch (Throwable ignored) {
            return false;
        }
    }

    @EventHandler
    public void onTick(EventPreTick event) {
        EntityPlayerSP player = Minecraft.thePlayer();
        World world = Minecraft.theWorld();
        if (player == null || player.isNull() || world == null || world.isNull()) {
            if (this.throwPhaseActive || this.rotationSaved) {
                this.reset();
            }
            return;
        }
        if (this.postThrowTicks > 0) {
            --this.postThrowTicks;
            if (this.postThrowTicks == 0) {
                this.restoreSlot(player);
                this.restoreRotation(player);
                this.disableDelay = DISABLE_DELAY;
            }
        }
        if (this.disableDelay > 0) {
            --this.disableDelay;
            if (this.disableDelay == 0) {
                this.resumeAura();
                this.reset();
            }
            return;
        }
        if (this.throwPhaseActive) {
            if (this.preThrowTicks > 0) {
                --this.preThrowTicks;
            }
            if (this.preThrowTicks <= 0) {
                this.throwPearl(player, world);
            }
            return;
        }
        if (this.intercept.getEffectiveValue()) {
            int interceptIndex = this.buttonIndex(this.interceptButton);
            boolean pressed = this.isButtonDown(interceptIndex);
            int normalIndex = this.buttonIndex(this.button);
            if (this.onRelease.getEffectiveValue()) {
                if (pressed) {
                    this.holdingIntercept = true;
                }
                else if (this.holdingIntercept) {
                    this.holdingIntercept = false;
                    if (this.beginIntercept(player)) {
                        return;
                    }
                }
            }
            else if (pressed && this.beginIntercept(player)) {
                return;
            }
            if (interceptIndex == normalIndex) {
                return;
            }
        }
        this.tickNormal(player);
    }

    private void tickNormal(EntityPlayerSP player) {
        boolean pressed = this.isButtonDown(this.buttonIndex(this.button));
        if (this.throwCountdown > 0) {
            --this.throwCountdown;
            if (this.throwCountdown == 0) {
                this.throwPearl(player, Minecraft.theWorld());
                this.disableDelay = DISABLE_DELAY;
            }
            return;
        }
        if (pressed) {
            if (!this.pearlSlotSelected) {
                int slot = this.findPearlSlot(player);
                if (slot >= 0) {
                    this.pearlSlotSelected = true;
                    this.pearlSavedSlot = this.currentSlot(player);
                    this.pauseAura();
                    this.switchSlot(player, slot);
                }
            }
        }
        else if (this.pearlSlotSelected) {
            this.pearlSlotSelected = false;
            this.throwCountdown = 1;
        }
    }

    private boolean beginIntercept(EntityPlayerSP player) {
        float[] angles = this.calculateInterceptAngles(player);
        if (angles == null) {
            return false;
        }
        int slot = this.findPearlSlot(player);
        if (slot < 0) {
            return false;
        }
        this.saveRotation(player);
        player.z(angles[0]);
        player.C(angles[1]);
        this.savedOriginalSlot = this.currentSlot(player);
        this.pauseAura();
        this.switchSlot(player, slot);
        this.throwPhaseActive = true;
        this.preThrowTicks = PRE_THROW_TICKS;
        return true;
    }

    private void throwPearl(EntityPlayerSP player, World world) {
        try {
            if (this.isPearlHeld(player)) {
                ItemStack held = player.getHeldItemHand();
                Minecraft.playerController().sendUseItem(player, world, held);
                player.swingItem();
            }
        }
        catch (Throwable ignored) {
        }
        this.throwPhaseActive = false;
        this.preThrowTicks = 0;
        this.postThrowTicks = POST_THROW_TICKS;
    }

    private float[] calculateInterceptAngles(EntityPlayerSP player) {
        World world = Minecraft.theWorld();
        if (world == null || world.isNull()) {
            return null;
        }
        Entity best = null;
        double bestDistance = Double.MAX_VALUE;
        Object self = player.getObject();
        for (Object handle : new ArrayList<Object>(world.z())) {
            Entity entity = new Entity(handle);
            if (entity.isNull() || !entity.isInstance(MappedClasses.Zg)) {
                continue;
            }
            if (this.isOwnPearl(handle, self)) {
                continue;
            }
            double distance = player.getDistanceToEntity(entity);
            if (distance > 30.0 || distance >= bestDistance) {
                continue;
            }
            best = entity;
            bestDistance = distance;
        }
        if (best == null) {
            return null;
        }
        double vx = best.z() - best.M();
        double vy = best.q();
        double vz = best.h() - best.m$src$D$fwnne5();
        try {
            Vec3 motion = best.C$src$Lgg_vape_wrapper_impl_Vec3_$1q93kwi();
            if (motion != null && !motion.isNull()) {
                vx = motion.getX();
                vy = motion.getY();
                vz = motion.getZ();
            }
        }
        catch (Throwable ignored) {
        }
        double eyeY = player.N() + player.X();
        double targetX = best.z();
        double targetY = best.N();
        double targetZ = best.h();
        double motionY = vy;
        for (int tick = 0; tick < PEARL_PREDICT_TICKS; ++tick) {
            vx *= 0.99;
            vz *= 0.99;
            motionY = motionY * 0.99 - PEARL_GRAVITY;
            targetX += vx;
            targetY += motionY;
            targetZ += vz;
            if (targetY <= eyeY - 1.0) {
                break;
            }
        }
        double dx = targetX - player.z();
        double dy = targetY - eyeY;
        double dz = targetZ - player.h();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < 0.4) {
            return null;
        }
        if (this.blockCheck.getEffectiveValue() && this.isOccluded(player, targetX, targetY, targetZ)) {
            return null;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float pitch = -this.lowArcPitch((float) horizontal, (float) dy);
        pitch = Math.max(-90.0f, Math.min(90.0f, pitch));
        return new float[]{yaw, pitch};
    }

    private boolean isOwnPearl(Object handle, Object self) {
        try {
            Object owner = Vape.INSTANCE.getMappings().enderPearlProjectileBridge.getOwner(handle);
            return owner != null && self != null && owner == self;
        }
        catch (Throwable ignored) {
            return false;
        }
    }

    private float lowArcPitch(float distance, float height) {
        double velocitySq = PEARL_VELOCITY * PEARL_VELOCITY;
        double root = velocitySq * velocitySq
                - PEARL_GRAVITY * (PEARL_GRAVITY * distance * distance + 2.0 * height * velocitySq);
        if (root <= 0.0) {
            return (float) Math.toDegrees(Math.atan2(height, distance));
        }
        return (float) Math.toDegrees(Math.atan(
                (velocitySq - Math.sqrt(root)) / (PEARL_GRAVITY * distance)));
    }

    private boolean isOccluded(EntityPlayerSP player, double x, double y, double z) {
        try {
            World world = Minecraft.theWorld();
            if (world == null || world.isNull()) {
                return false;
            }
            double eyeX = player.z();
            double eyeY = player.N() + player.X();
            double eyeZ = player.h();
            for (int step = 1; step < 12; ++step) {
                double t = step / 12.0;
                double sx = eyeX + (x - eyeX) * t;
                double sy = eyeY + (y - eyeY) * t;
                double sz = eyeZ + (z - eyeZ) * t;
                BlockPos pos = BlockPos.create((int) Math.floor(sx), (int) Math.floor(sy), (int) Math.floor(sz));
                if (BlockUtil.u(world.getBlockState(pos).getBlock())) {
                    continue;
                }
                return true;
            }
        }
        catch (Throwable ignored) {
        }
        return false;
    }

    private boolean isPearlHeld(EntityPlayerSP player) {
        ItemStack held = player.getHeldItemHand();
        return held != null && !held.isNull() && "ender_pearl".equals(this.itemName(held));
    }

    private int findPearlSlot(EntityPlayerSP player) {
        InventoryPlayer inventory = this.inventory(player);
        if (inventory == null || inventory.isNull()) {
            return -1;
        }
        for (int slot = 0; slot < 9; ++slot) {
            if ("ender_pearl".equals(this.itemName(inventory.c(slot)))) {
                return slot;
            }
        }
        return -1;
    }

    private String itemName(ItemStack stack) {
        try {
            if (stack == null || stack.isNull()) {
                return "";
            }
            String name = stack.getItem().A();
            return name == null ? "" : name.toLowerCase();
        }
        catch (Throwable ignored) {
            return "";
        }
    }

    private InventoryPlayer inventory(EntityPlayerSP player) {
        try {
            return player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6();
        }
        catch (Throwable ignored) {
            return null;
        }
    }

    private int currentSlot(EntityPlayerSP player) {
        InventoryPlayer inventory = this.inventory(player);
        return inventory == null || inventory.isNull() ? -1 : inventory.v();
    }

    private void switchSlot(EntityPlayerSP player, int slot) {
        try {
            InventoryPlayer inventory = this.inventory(player);
            if (inventory != null && !inventory.isNull()) {
                inventory.g(slot);
            }
        }
        catch (Throwable ignored) {
        }
    }

    private void restoreSlot(EntityPlayerSP player) {
        int slot = this.savedOriginalSlot >= 0 ? this.savedOriginalSlot : this.pearlSavedSlot;
        if (slot >= 0) {
            this.switchSlot(player, slot);
        }
        this.savedOriginalSlot = -1;
        this.pearlSavedSlot = -1;
    }

    private void pauseAura() {
        try {
            if (this.auraPaused) {
                return;
            }
            SilentAura aura = Vape.INSTANCE.getModManager().getMod(SilentAura.class);
            if (aura != null && aura.isEnabled()) {
                aura.setEnabled(false);
                this.auraPaused = true;
            }
        }
        catch (Throwable ignored) {
        }
    }

    private void resumeAura() {
        if (!this.auraPaused) {
            return;
        }
        try {
            SilentAura aura = Vape.INSTANCE.getModManager().getMod(SilentAura.class);
            if (aura != null) {
                aura.setEnabled(true);
            }
        }
        catch (Throwable ignored) {
        }
        this.auraPaused = false;
    }

    private void saveRotation(EntityPlayerSP player) {
        if (!this.rotationSaved) {
            this.savedYaw = player.J();
            this.savedPitch = player.V();
            this.rotationSaved = true;
        }
    }

    private void restoreRotation(EntityPlayerSP player) {
        if (this.rotationSaved) {
            player.z(this.savedYaw);
            player.C(this.savedPitch);
            this.rotationSaved = false;
        }
    }
}
