package gg.vape.module.combat.blockhit;

import gg.vape.Vape;
import gg.vape.config.ClientSettings;
import gg.vape.event.EventHandler;
import gg.vape.event.impl.EventMouseButton;
import gg.vape.event.impl.EventPreTick;
import gg.vape.module.Mod;
import gg.vape.combat.AttackPacketTimingTracker;
import gg.vape.module.combat.LeftClicker;
import gg.vape.module.combat.SilentAura;
import gg.vape.module.combat.BlockHit;
import gg.vape.value.RandomValue;
import gg.vape.wrapper.impl.Minecraft;

public class ManualBlockHitMode
extends BlockHitMode {
    private boolean blocking = false;
    private long releaseTime;
    private final RandomValue chance = RandomValue.createWithDescription(this, "Chance", "#", "%", 0.0, 70.0, 90.0, 100.0, 1.0, "Chance that a click will blockhit\n(Blocks per second = Your CPS * Chance)");

    public ManualBlockHitMode(Mod parent, String name) {
        super(parent, name);
        this.addValue(this.chance);
    }

    @Override
    public boolean shouldBlock() {
        if (!((BlockHit)this.getParent()).isHoldingSword()) {
            return false;
        }
        if (((BlockHit)this.getParent()).requiresMouseDown() && !ClientSettings.isUseItemButtonDown()) {
            return false;
        }
        return this.chance.getRandomValue() >= Math.random() * 100.0;
    }

    public void setBlocking(boolean blocking) {
        if (this.blocking != blocking) {
            this.blocking = blocking;
            this.releaseTime = 0L;
            Minecraft.gameSettings().b$src$Lgg_vape_wrapper_impl_KeyBinding_$1yi3362().setPressed(blocking);
        }
    }

    @Override
    public String getDetailedSuffix() {
        return this.chance.getDisplayValue();
    }

    @Override
    public boolean isBlocking() {
        return this.blocking;
    }

    @EventHandler
    public void onTick(EventPreTick event) {
        if (this.isAutoClickerActive()) {
            return;
        }
        if (Minecraft.thePlayer().isNull()) {
            return;
        }
        boolean hurtTimeExpired = event.getThePlayer().c$src$I$15a9iwo() > AttackPacketTimingTracker.INSTANCE.getExpectedHurtTimeTicks() + 1;
        boolean releaseTimeReached = this.releaseTime > 0L && System.currentTimeMillis() >= this.releaseTime;
        if (hurtTimeExpired || releaseTimeReached) {
            this.setBlocking(false);
            return;
        }
    }


    @EventHandler
    public void onMouseButton(EventMouseButton event) {
        if (!event.getButtonState()) {
            return;
        }
        if (this.isAutoClickerActive()) {
            return;
        }
        int buttonBinding = -100 + event.getButton();
        if (event.getButtonState() && buttonBinding == Minecraft.gameSettings().F().getKeyCode()) {
            if (!this.shouldBlock()) {
                return;
            }
            if (!this.blocking && !event.getThePlayer().o$src$Z$1iprrmi()) {
                this.setBlocking(true);
                this.releaseTime = System.currentTimeMillis() + 50L;
            }
        }
    }

    private boolean isAutoClickerActive() {
        LeftClicker leftClicker = Vape.INSTANCE.getModManager().getMod(LeftClicker.class);
        if (leftClicker.isEnabled()) {
            return true;
        }
        SilentAura silentAura = Vape.INSTANCE.getModManager().getMod(SilentAura.class);
        return silentAura.isEnabled();
    }
}
