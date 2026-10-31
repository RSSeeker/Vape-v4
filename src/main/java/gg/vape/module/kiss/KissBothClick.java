package gg.vape.module.kiss;

import gg.vape.event.EventHandler;
import gg.vape.event.impl.EventPreTick;
import gg.vape.module.Category;
import gg.vape.module.Mod;
import gg.vape.module.none.ClientSettings;
import gg.vape.unmap.ModeOption;
import gg.vape.unmap.ModeSelection;
import gg.vape.value.BooleanValue;
import gg.vape.value.ModeValue;
import gg.vape.value.NumberValue;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.KeyBinding;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.RayTraceResult;
import gg.vape.wrapper.impl.World;
import java.util.Random;

/**
 * Kiss BothClick (双键连点) — clicks attack and use-item at the same time while held.
 * Ported from KissClient's world/BothClick.java.
 */
public class KissBothClick
extends Mod {
    private static final int MOD_ID = 1874720913;

    private final NumberValue minCps = NumberValue.create(this, "Min CPS", "#.#", "", 1.0, 8.0, 20.0, 0.5);
    private final NumberValue maxCps = NumberValue.create(this, "Max CPS", "#.#", "", 1.0, 12.0, 20.0, 0.5);
    private final ModeOption keyMode = new ModeOption("Key");
    private final ModeOption doubleMode = new ModeOption("Double");
    private final ModeValue clickMode = ModeValue.create(this, "Click Mode", "Key - click only the button you hold\nDouble - click both buttons together", this.keyMode, this.keyMode, this.doubleMode);
    private final BooleanValue breakBlock = BooleanValue.create(this, "Break Block", true,
            "Allow left clicks while looking at a block");
    private final BooleanValue eatCheck = BooleanValue.create(this, "Eat Check", true,
            "Do not spam use-item while already eating or blocking");

    private final Random random = new Random();
    private long nextAttackMillis;
    private long nextUseMillis;

    public KissBothClick() {
        super("Kiss BothClick", MOD_ID, Category.KISS,
                "Click attack and use-item simultaneously at a randomised CPS, ported from KissClient.");
    }

    @Override
    public void onEnable() {
        this.nextAttackMillis = 0L;
        this.nextUseMillis = 0L;
    }

    @EventHandler
    public void onTick(EventPreTick event) {
        EntityPlayerSP player = Minecraft.thePlayer();
        World world = Minecraft.theWorld();
        if (player == null || player.isNull() || world == null || world.isNull()) {
            return;
        }
        if (!ClientSettings.INSTANCE.inputEnabled) {
            return;
        }
        boolean attackHeld = gg.vape.config.ClientSettings.isAttackButtonDown();
        boolean useHeld = gg.vape.config.ClientSettings.isUseItemButtonDown();
        if (!attackHeld && !useHeld) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean doubleClick = this.clickMode.getSelectedIndex() == 1;
        boolean clickAttack = attackHeld && (doubleClick || !this.isEating(player));
        boolean clickUse = useHeld && (doubleClick || !this.isEating(player));

        if (clickAttack && !this.breakBlock.getEffectiveValue().booleanValue() && this.isLookingAtBlock()) {
            clickAttack = false;
        }
        if (clickUse && this.eatCheck.getEffectiveValue().booleanValue() && this.isEating(player)) {
            clickUse = false;
        }
        if (doubleClick && this.isEating(player)) {
            clickAttack = false;
            clickUse = false;
        }

        if (clickAttack && now >= this.nextAttackMillis) {
            this.press(Minecraft.gameSettings().F());
            this.nextAttackMillis = now + this.nextDelay();
        }
        if (clickUse && now >= this.nextUseMillis) {
            this.press(Minecraft.gameSettings().b$src$Lgg_vape_wrapper_impl_KeyBinding_$1yi3362());
            this.nextUseMillis = now + this.nextDelay();
        }
    }

    private long nextDelay() {
        double min = this.minCps.getValue();
        double max = this.maxCps.getValue();
        if (max < min) {
            max = min;
        }
        double cps = min + this.random.nextDouble() * (max - min);
        if (cps <= 0.0) {
            return 100L;
        }
        return Math.max(1L, (long)(1000.0 / cps));
    }

    private boolean isEating(EntityPlayerSP player) {
        try {
            return player.l$src$Z$1io4duf();
        }
        catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isLookingAtBlock() {
        try {
            RayTraceResult result = Minecraft.p$src$Lgg_vape_wrapper_impl_RayTraceResult_$5rw6n0();
            return result != null && !result.isNull() && result.isBlockHit();
        }
        catch (Throwable ignored) {
            return false;
        }
    }

    private void press(KeyBinding keyBinding) {
        if (keyBinding == null || keyBinding.isNull()) {
            return;
        }
        try {
            KeyBinding.setKeyBindState(keyBinding, true);
            KeyBinding.onTick(keyBinding);
            KeyBinding.setKeyBindState(keyBinding, false);
        }
        catch (Throwable ignored) {
        }
    }
}
