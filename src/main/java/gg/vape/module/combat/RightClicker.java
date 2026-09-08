package gg.vape.module.combat;

import gg.vape.click.ClickButton;
import gg.vape.click.ClickEngine;
import gg.vape.module.combat.ClickerMod;
import gg.vape.module.control.SharedModuleControlClaims;
import gg.vape.unmap.ItemLimitData;
import gg.vape.unmap.ModeOption;
import gg.vape.value.BooleanValue;
import gg.vape.value.LimitValue;
import gg.vape.value.ModeValue;
import gg.vape.value.NumberValue;
import gg.vape.value.RandomValue;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.EnumHand;
import gg.vape.wrapper.impl.ForgeVersion;
import gg.vape.wrapper.impl.ItemStack;

public class RightClicker
extends ClickerMod {
    private final NumberValue startDelay;
    private final ModeOption modeExtraPlus;
    private final LimitValue itemWhitelist;
    private final BooleanValue useItemWhitelist;
    private final BooleanValue jitter;
    private final ModeOption modeNormal;
    private final BooleanValue holdToClick;
    private final BooleanValue sneakOnly;
    private final ModeOption modeExtra;
    private final ModeValue randomization;
    private final RandomValue cps = RandomValue.create(this, "CPS", "#.#", "", 1.0, 7.0, 13.0, 20.0);

    @Override
    public boolean shouldBlockClick(EntityPlayerSP player) {
        if (SharedModuleControlClaims.rightClickUse.isClaimed()) {
            return true;
        }
        if (this.sneakOnly.getEffectiveValue() && !player.P()) {
            return true;
        }
        if (ForgeVersion.MC_1_12_2.d()) {
            ItemStack mainHandItem = player.i(EnumHand.mainHand());
            ItemStack offHandItem = player.i(EnumHand.offHand());
            if (!this.itemWhitelist.matches(mainHandItem) && this.itemWhitelist.matches(offHandItem)
                    && this.isUsableItem(mainHandItem, player)) {
                return true;
            }
        }
        return false;
    }


    @Override
    public double getStartDelayMillis() {
        return (Double)this.startDelay.getValue();
    }

    private boolean isUsableItem(ItemStack itemStack, EntityPlayerSP player) {
        return itemStack.isNotNull() && itemStack.getItem().isNotNull()
                && itemStack.getItem().I(itemStack, player) > 0;
    }

    @Override
    public String getDetailedSuffix() {
        return this.cps.getDisplayValue() + "cps";
    }

    public RightClicker() {
        super("RightClicker");
        this.holdToClick = BooleanValue.create(this, "Hold to Click", true);
        this.sneakOnly = BooleanValue.create(this, "Sneak Only", false, "Only right clicks while sneaking");
        this.itemWhitelist = LimitValue.create(this, "autoclicker-allowed-items", "Item whitelist", LimitValue.ALLOW_LIST_COLOR, new ItemLimitData("blocks")).setIncludeOffhand(true);
        this.jitter = BooleanValue.create(this, "Jitter", false);
        this.useItemWhitelist = BooleanValue.create(this, "Use item whitelist", false);
        this.modeExtra = new ModeOption("Extra");
        this.modeExtraPlus = new ModeOption("Extra+");
        this.modeNormal = new ModeOption("Normal");
        this.randomization = ModeValue.create((Object)this, "Randomization", this.modeExtraPlus, this.modeNormal, this.modeExtra, this.modeExtraPlus);
        this.startDelay = NumberValue.create(this, "Start Delay", "#.#", "", 0.0, 0.0, 1000.0);
        this.useItemWhitelist.addDependentValues(this.itemWhitelist);
        this.addValue(this.cps, this.startDelay, this.randomization, this.jitter, this.useItemWhitelist, this.itemWhitelist, this.sneakOnly);
        ClickEngine clickEngine = new ClickEngine(ClickButton.RIGHT, this.cps, this.useItemWhitelist,
                this.itemWhitelist, this.holdToClick, this.randomization, this.jitter, this);
        this.setClickEngine(clickEngine);
        this.cps.setMaximumFractionDigits(0);
    }
}

