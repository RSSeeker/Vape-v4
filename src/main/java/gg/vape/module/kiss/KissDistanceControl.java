package gg.vape.module.kiss;

import gg.vape.config.ClientSettings;
import gg.vape.event.EventHandler;
import gg.vape.event.impl.EventPreAttack;
import gg.vape.event.impl.EventPreTick;
import gg.vape.mapping.MappedClasses;
import gg.vape.module.Category;
import gg.vape.module.Mod;
import gg.vape.rotation.RotationManager;
import gg.vape.unmap.ModeOption;
import gg.vape.unmap.ModeSelection;
import gg.vape.value.BooleanValue;
import gg.vape.value.ModeValue;
import gg.vape.value.NumberValue;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.KeyBinding;
import gg.vape.wrapper.impl.Minecraft;

/**
 * Kiss DistanceControl (自动控距) — briefly taps the forward key right after an attack so the
 * outgoing movement never lines up with the hit, which makes the server see a distance mismatch.
 * Ported from KissClient's combat/DistanceControl.java (WTAP = release forward, STAP = hold forward).
 */
public class KissDistanceControl
extends Mod {
    private static final int MOD_ID = 1593482731;

    private final ModeOption wtapMode = new ModeOption("WTAP");
    private final ModeOption stapMode = new ModeOption("STAP");
    private final ModeValue controlMode = ModeValue.create(this, "Control Mode",
            "WTAP - release forward for a few ticks after the hit\nSTAP - hold forward for a few ticks after the hit",
            this.wtapMode, this.wtapMode, this.stapMode);
    private final NumberValue wtapDuration = NumberValue.create(this, "WTap Duration", "#", " ticks", 1.0, 3.0, 10.0, 1.0);
    private final NumberValue stapDuration = NumberValue.create(this, "STap Duration", "#", " ticks", 1.0, 1.0, 10.0, 1.0);
    private final NumberValue cooldown = NumberValue.create(this, "Cooldown", "#", " ticks", 0.0, 2.0, 10.0, 1.0);
    private final BooleanValue onAuraLockView = BooleanValue.create(this, "On Aura LockView", false,
            "Also tap when an aura is aiming instead of the vanilla attack");

    private boolean tapping;
    private int tapTicksLeft;
    private int nextTriggerTick;
    private int tickCount;

    public KissDistanceControl() {
        super("Kiss DistanceControl", MOD_ID, Category.KISS,
                "Distance control ported from KissClient: taps the forward key after a hit (WTAP / STAP).");
    }

    @Override
    public void onEnable() {
        this.tapping = false;
        this.tapTicksLeft = 0;
        this.nextTriggerTick = 0;
        this.tickCount = 0;
    }

    @Override
    public void onDisable() {
        if (this.tapping) {
            this.restoreKeys();
        }
        this.tapping = false;
        this.tapTicksLeft = 0;
    }

    @EventHandler
    public void onPreAttack(EventPreAttack event) {
        EntityPlayerSP player = Minecraft.thePlayer();
        if (player == null || player.isNull() || Minecraft.theWorld() == null || Minecraft.theWorld().isNull()) {
            return;
        }
        if (event.getTarget() == null || event.getTarget().isNull()
                || !event.getTarget().isInstance(MappedClasses.zm)
                || event.getTarget().getObject() == player.getObject()) {
            return;
        }
        if (this.tapping || this.tickCount < this.nextTriggerTick) {
            return;
        }
        if (this.onAuraLockView.getEffectiveValue().booleanValue()
                && !RotationManager.INSTANCE.hasAdaptiveController()) {
            return;
        }
        this.startTap();
    }

    @EventHandler
    public void onTick(EventPreTick event) {
        EntityPlayerSP player = Minecraft.thePlayer();
        if (player == null || player.isNull()) {
            return;
        }
        ++this.tickCount;
        if (!this.tapping) {
            return;
        }
        if (this.tapTicksLeft > 0) {
            KeyBinding forwardKey = Minecraft.gameSettings().Y();
            KeyBinding.setKeyBindState(forwardKey, this.isStapMode());
            --this.tapTicksLeft;
            return;
        }
        this.restoreKeys();
        this.tapping = false;
    }

    private void startTap() {
        this.tapping = true;
        this.tapTicksLeft = this.isStapMode()
                ? this.stapDuration.getValue().intValue()
                : this.wtapDuration.getValue().intValue();
        this.nextTriggerTick = this.tickCount + this.cooldown.getValue().intValue();
    }

    private boolean isStapMode() {
        return this.controlMode.getSelectedIndex() == 1;
    }

    private void restoreKeys() {
        try {
            KeyBinding forwardKey = Minecraft.gameSettings().Y();
            KeyBinding.setKeyBindState(forwardKey, ClientSettings.isPhysicalKeyDown(forwardKey));
        }
        catch (Throwable ignored) {
        }
    }
}
