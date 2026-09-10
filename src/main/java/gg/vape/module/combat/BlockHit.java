package gg.vape.module.combat;

import gg.vape.module.Category;
import gg.vape.module.Mod;
import gg.vape.module.combat.blockhit.BlockHitMode;
import gg.vape.module.combat.blockhit.ManualBlockHitMode;
import gg.vape.module.combat.blockhit.PredictBlockHitMode;
import gg.vape.module.combat.blockhit.LagBlockHitMode;
import gg.vape.module.combat.blockhit.AutoBlockHitMode;
import gg.vape.utils.ItemStackScoreUtil;
import gg.vape.utils.RotationUtil;
import gg.vape.value.BooleanValue;
import gg.vape.value.ModeValue;
import gg.vape.value.NumberValue;
import gg.vape.value.SubModuleValue;
import gg.vape.wrapper.impl.EntityLivingBase;
import gg.vape.wrapper.impl.Minecraft;

public class BlockHit
extends Mod {
    private static final long MODULE_COLOR = -1033271949203766542L;
    private final ModeValue mode;
    private final SubModuleValue<PredictBlockHitMode> predictiveMode;
    private final SubModuleValue<ManualBlockHitMode> manualMode = new ManualBlockHitMode(this, "Manual").getSelectionValue();
    private final SubModuleValue<LagBlockHitMode> lagMode;
    public final BooleanValue requireMouseDown;
    public final BooleanValue ignoreManualBlock;
    public final NumberValue targetDistance;
    private final SubModuleValue<BlockHitMode> automaticMode;
    public final NumberValue targetAngle;

    public boolean isHoldingSword() {
        if (Minecraft.currentScreen().isNotNull()) {
            return false;
        }
        return Minecraft.thePlayer().getHeldItemHand().isNotNull() && ItemStackScoreUtil.h(Minecraft.thePlayer().getHeldItemHand().getItem());
    }

    public boolean shouldBlock() {
        return this.isEnabled() && this.getActiveMode().shouldBlock();
    }


    public boolean requiresMouseDown() {
        return this.requireMouseDown.getEffectiveValue();
    }

    @Override
    public String getDetailedSuffix() {
        if (this.mode.getValue() == this.manualMode) {
            return this.manualMode.getInstance().getDetailedSuffix();
        }
        if (this.mode.getValue() == this.lagMode) {
            return this.lagMode.getInstance().getDetailedSuffix();
        }
        return this.getSimpleSuffix();
    }

    public EntityLivingBase findTarget(double angle, double distance) {
        EntityLivingBase target = RotationUtil.u(distance, angle / 2.0);
        if (target == null) {
            return null;
        }
        if (RotationUtil.o(Minecraft.thePlayer(), target, distance, angle / 2.0, true)) {
            return target;
        }
        return null;
    }

    public BlockHit() {
        super("BlockHit", (int)MODULE_COLOR, Category.COMBAT, "Automatically blockhit");
        this.predictiveMode = new PredictBlockHitMode(this, "Predict").getSelectionValue();
        this.automaticMode = new AutoBlockHitMode(this, "Auto").getSelectionValue();
        this.lagMode = new LagBlockHitMode(this, "Lag").getSelectionValue();
        this.requireMouseDown = BooleanValue.create(this, "Require mouse down", true, "Require block to be pressed to blockhit");
        this.ignoreManualBlock = BooleanValue.create(this, "Ignore manual block", true, "Prevents manually blocking, useful for holding right click to activate");
        this.targetAngle = NumberValue.createWithDescription(this, "Angle", "#", "", 0.0, 90.0, 360.0, "Max target angle to blockhit");
        this.targetDistance = NumberValue.createWithDescription(this, "Distance", "#.#", "", 0.0, 5.0, 6.0, "Max target distance to blockhit");
        this.mode = ModeValue.create((Object)this, "Mode", this.manualMode, this.manualMode, this.predictiveMode, this.automaticMode, this.lagMode);
        this.mode.setDescription("Manual: Blockhit based on your CPS\nPredict: Predicts when a player can hit you and blocks ahead of time\nAuto: Legacy auto mode from AutoClicker\nLag: Lags you after blocking to maximize server side block time\n");
        this.requireMouseDown.addDependentValues(this.ignoreManualBlock);
        this.mode.addModeDependentValues(this.lagMode, this.ignoreManualBlock);
        this.mode.addModeDependentValues(this.lagMode, this.targetAngle);
        this.mode.addModeDependentValues(this.lagMode, this.targetDistance);
        this.addValue(this.mode, this.requireMouseDown, this.ignoreManualBlock, this.targetAngle, this.targetDistance);
    }

    public BlockHitMode getActiveMode() {
        return (BlockHitMode)((SubModuleValue)this.mode.getValue()).getInstance();
    }

    public boolean isBlocking() {
        return this.getActiveMode().isBlocking();
    }

    @Override
    public String getSimpleSuffix() {
        return this.mode.getDisplayValue();
    }

    public EntityLivingBase findDefaultTarget() {
        return this.findTarget(90.0, 5.0);
    }
}

