package gg.vape.module.combat;

import gg.vape.config.ClientSettings;
import gg.vape.event.EventHandler;
import gg.vape.event.impl.EventPreAttack;
import gg.vape.event.impl.EventPreTick;
import gg.vape.mapping.MappedClasses;
import gg.vape.module.Category;
import gg.vape.module.Mod;
import gg.vape.utils.TimerUtil;
import gg.vape.value.BooleanValue;
import gg.vape.value.NumberValue;
import gg.vape.wrapper.impl.EntityLivingBase;
import gg.vape.wrapper.impl.ForgeVersion;
import gg.vape.wrapper.impl.KeyBinding;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.Packet;

public class WTap
extends Mod {
    private final NumberValue chance = NumberValue.createWithDescription(this, "Chance", "#", "%", 0.0, 90.0, 100.0, "Chance of WTapping when hitting a target");
    private final TimerUtil rePressTimer;
    private final NumberValue releaseDelay = NumberValue.create(this, "Release delay", "#", "", 0.0, 0.0, 500.0, 50.0, "Delay before releasing W key after hitting a target");
    private boolean releasePending;
    private final TimerUtil releaseTimer;
    private final NumberValue rePressDelay = NumberValue.create(this, "Re-press delay", "#", "", 0.0, 0.0, 500.0, 50.0, "Delay before re-pressing W key after releasing it");
    private final BooleanValue selectHits = BooleanValue.create(this, "Select hits", true, "Only WTap when the target is vulnerable");
    private boolean rePressPending;
    private static final long MODULE_ID = -5147998889622254014L;

    private void handleRePress() {
        if (this.rePressTimer.hasTimeElapsed(((Double)this.rePressDelay.getValue()).longValue())) {
            KeyBinding forwardKey = Minecraft.gameSettings().Y();
            if (ClientSettings.isPhysicalKeyDown(forwardKey)) {
                KeyBinding.setKeyBindState(forwardKey, true);
            }
            this.rePressPending = false;
        }
    }

    private boolean isTargetVulnerable(gg.vape.wrapper.impl.Entity target) {
        if (!this.selectHits.getEffectiveValue()) {
            return true;
        }
        if (ForgeVersion.MC_1_16_5.d()) {
            return new EntityLivingBase(target.getObject()).c$src$I$15a9iwo() <= 0;
        }
        return target.V$src$I$fk0dv5() <= 14;
    }

    private void restoreForwardKey() {
        KeyBinding forwardKey = Minecraft.gameSettings().Y();
        KeyBinding.setKeyBindState(forwardKey, ClientSettings.isPhysicalKeyDown(forwardKey));
        this.releasePending = false;
        this.rePressPending = false;
    }


    @EventHandler
    public void onPreAttack(EventPreAttack event) {
        if (Packet.h()) {
            if (!event.getTarget().isInstance(MappedClasses.lG)) {
                return;
            }
            if (this.releasePending || this.rePressPending) {
                return;
            }
            if (!this.isTargetVulnerable(event.getTarget())) {
                return;
            }
            if (this.shouldTrigger()) {
                this.releasePending = true;
                this.releaseTimer.reset();
                this.handleRelease();
            }
            return;
        }
        if (event.getTarget().isInstance(MappedClasses.lG)) {
            this.releasePending = true;
            this.releaseTimer.reset();
            this.handleRelease();
        }
    }

    public WTap() {
        super("WTap", (int)MODULE_ID, Category.COMBAT);
        this.releaseTimer = new TimerUtil();
        this.rePressTimer = new TimerUtil();
        this.addValue(this.chance, this.releaseDelay, this.rePressDelay, this.selectHits);
        this.releaseDelay.setMaximumFractionDigits(0);
    }

    @EventHandler
    public void onTick(EventPreTick event) {
        if (Packet.A()) {
            if (Minecraft.currentScreen().isNull()) {
                this.handleRePress();
            } else if (this.releasePending || this.rePressPending) {
                this.restoreForwardKey();
            }
            return;
        }
        if (Minecraft.currentScreen().isNotNull()) {
            if (this.releasePending || this.rePressPending) {
                this.restoreForwardKey();
            }
            return;
        }
        if (this.releasePending) {
            this.handleRelease();
            return;
        }
        if (this.rePressPending) {
            this.handleRePress();
            return;
        }
    }

    private void handleRelease() {
        if (this.releaseTimer.hasTimeElapsed(((Double)this.releaseDelay.getValue()).longValue())) {
            KeyBinding forwardKey = Minecraft.gameSettings().Y();
            KeyBinding.setKeyBindState(forwardKey, false);
            this.releasePending = false;
            this.rePressTimer.reset();
            this.rePressPending = true;
        }
    }

    @Override
    public void onDisable() {
        this.restoreForwardKey();
    }

    @Override
    public String getDetailedSuffix() {
        return this.releaseDelay.getDisplayValue();
    }

    public boolean shouldTrigger() {
        return (Double)this.chance.getValue() >= Math.random() * 100.0;
    }
}
