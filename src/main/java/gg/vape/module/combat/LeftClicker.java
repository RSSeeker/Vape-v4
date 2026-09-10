package gg.vape.module.combat;

import gg.vape.Vape;
import gg.vape.click.ClickButton;
import gg.vape.click.ClickEngine;
import gg.vape.event.EventHandler;
import gg.vape.event.EventPriority;
import gg.vape.event.impl.EventPreTick;
import gg.vape.event.impl.EventSendClickBlockToController;
import gg.vape.input.InputEventDispatcher;
import gg.vape.mapping.MappedClasses;
import gg.vape.module.control.SharedModuleControlClaims;
import gg.vape.module.none.ClientSettings;
import gg.vape.rotation.RotationManager;
import gg.vape.ui.click.component.GuiComponent;
import gg.vape.unmap.ItemLimitData;
import gg.vape.unmap.ModeOption;
import gg.vape.utils.TimerUtil;
import gg.vape.value.BooleanValue;
import gg.vape.value.LimitValue;
import gg.vape.value.ModeValue;
import gg.vape.value.RandomValue;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.ForgeVersion;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.Packet;
import gg.vape.wrapper.impl.PlayerControllerMP;
import gg.vape.wrapper.impl.RayTraceResult;
import gg.vape.wrapper.impl.RayTraceResult_type;
import java.util.Arrays;

public class LeftClicker
extends ClickerMod {
    private final BooleanValue jitter;
    private final ModeOption normalMode;
    private final ModeOption extraPlusMode;
    private final LimitValue blockBreakItems;
    private final ModeOption extraMode;
    private final RandomValue cps;
    private final BooleanValue breakBlocks;
    private final BooleanValue triggerMode;
    private final LimitValue itemWhitelist;
    private final BooleanValue breakBlocksWhitelist;
    private final BooleanValue limitItems;
    private final BooleanValue holdToClick = BooleanValue.create(this, "Hold to click", true);
    private final TimerUtil breakBlockTimer;
    private volatile boolean blocked = false;
    private volatile boolean breakingBlock = false;
    private volatile boolean blockBreakAttackHeld = false;
    private final RandomValue breakBlocksDelay;
    private final ModeValue randomization;

    @Override
    public boolean shouldSimulateBlockHit(ClickEngine clickEngine, EntityPlayerSP player) {
        BlockHit blockHit = Vape.INSTANCE.getModManager().getMod(BlockHit.class);
        if (Packet.A()) {
            boolean animationsEnabled = blockHit.shouldBlock();
            GuiComponent.setLegacyComponentState(new GuiComponent[2]);
            return animationsEnabled;
        }
        return blockHit != null && blockHit.shouldBlock();
    }

    @Override
    public boolean isTriggerModeEnabled() {
        return this.triggerMode.getEffectiveValue();
    }

    private boolean isMinecraftFocused() {
        return InputEventDispatcher.getInstance().getFocusState().isFocused() && Minecraft.a();
    }

    private void stopBreakingBlock() {
        this.breakingBlock = false;
        if (!this.blockBreakAttackHeld) {
            return;
        }
        this.blockBreakAttackHeld = false;
        this.getClickEngine().releaseClickButton();
    }

    private boolean computeBlocked() {
        if (!ClientSettings.INSTANCE.isInputEnabled()) {
            this.stopBreakingBlock();
            return true;
        }
        if (!this.isMinecraftFocused()) {
            this.stopBreakingBlock();
            return true;
        }
        if (SharedModuleControlClaims.mouseButtons.isLocked()) {
            this.stopBreakingBlock();
            return true;
        }
        EntityPlayerSP player = Minecraft.thePlayer();
        if (player.isNull()) {
            this.stopBreakingBlock();
            return true;
        }
        boolean shouldBreakBlock = !this.shouldAllowClick(player);
        if (shouldBreakBlock) {
            this.breakingBlock = true;
        } else {
            this.stopBreakingBlock();
        }
        return shouldBreakBlock;
    }

    @Override
    public boolean isHitSelectActive() {
        HitSelect hitSelect = Vape.INSTANCE.getModManager().getMod(HitSelect.class);
        if (hitSelect == null) {
            return false;
        }
        return hitSelect.isEnabled() && hitSelect.isRightClickCancelActive();
    }

    public boolean shouldAllowClick(EntityPlayerSP player) {
        if (!gg.vape.config.ClientSettings.isAttackButtonDown()) {
            this.breakBlockTimer.reset();
            return true;
        }
        if (this.breakBlocks.getEffectiveValue().booleanValue() && this.breakBlockTimer.hasTimeElapsed((long)this.breakBlocksDelay.getRandomValue())) {
            if (Minecraft.currentScreen().isInstance(MappedClasses.Ft)) {
                return true;
            }
            if (this.breakBlocksWhitelist.getEffectiveValue().booleanValue() && !this.blockBreakItems.matches(player.getHeldItemHand())) {
                return true;
            }
            PlayerControllerMP playerController = Minecraft.playerController();
            if (playerController.isNull()) {
                return true;
            }
            RayTraceResult rayTraceResult = RotationManager.INSTANCE.rayTraceUsingManagedRotation(
                    playerController.N(), 0.0f, false);
            if (rayTraceResult.isNotNull() && rayTraceResult.getTypeOfHit().equals(RayTraceResult_type.block())) {
                return false;
            }
            this.breakBlockTimer.reset();
        }
        return true;
    }


    @Override
    public boolean isClickCycleBlocked() {
        return this.blocked;
    }

    @Override
    protected boolean shouldSuppressClickRelease() {
        if (!this.breakingBlock || !Minecraft.gameSettings().F().isKeyDown()) {
            return false;
        }
        this.blockBreakAttackHeld = true;
        return true;
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void keepLegacyBlockBreakingResponsive(EventSendClickBlockToController event) {
        if (ForgeVersion.c() == ForgeVersion.MC_1_8_9.i() && this.breakingBlock
                && this.isMinecraftFocused() && gg.vape.config.ClientSettings.isAttackButtonDown()) {
            // A transient miss between blocks sets this to 10, pausing mining for half a second.
            Minecraft.r(0);
        }
    }

    @EventHandler
    public void updateBlockedState(EventPreTick eventPreTick) {
        this.blocked = this.computeBlocked();
        ClickEngine clickEngine = this.getClickEngine();
        if (this.breakingBlock && this.isMinecraftFocused() && ClientSettings.INSTANCE.inputEnabled && Minecraft.currentScreen().isNull() && clickEngine.isActivationHeld() && !Minecraft.gameSettings().F().isKeyDown()) {
            clickEngine.pressClickButton();
            this.blockBreakAttackHeld = Minecraft.gameSettings().F().isKeyDown();
        }
    }

    @Override
    public void onDisable() {
        this.blocked = true;
        this.breakingBlock = false;
        super.onDisable();
        this.stopBreakingBlock();
    }

    @Override
    public String getDetailedSuffix() {
        return this.cps.getDisplayValue() + "cps";
    }

    public LeftClicker() {
        super("AutoClicker");
        this.normalMode = new ModeOption("Normal");
        this.extraMode = new ModeOption("Extra");
        this.extraPlusMode = new ModeOption("Extra+");
        this.randomization = ModeValue.create((Object)this, "Randomization", this.extraMode, this.normalMode, this.extraMode, this.extraPlusMode);
        this.jitter = BooleanValue.create(this, "Jitter", false);
        this.cps = RandomValue.create(this, "CPS", "#.#", "", 1.0, 6.0, 13.0, 20.0);
        this.limitItems = BooleanValue.create(this, "Limit items", false);
        this.itemWhitelist = LimitValue.create(this, "autoclicker-allowed-items", "Item whitelist", LimitValue.ALLOW_LIST_COLOR, new ItemLimitData("swords"));
        this.triggerMode = BooleanValue.create(this, "Trigger mode", false, "Only clicks while hovering an entity");
        this.breakBlocks = BooleanValue.create(this, "Break blocks", false);
        this.breakBlocksDelay = RandomValue.create(this, "Break blocks delay", "#", "", 0.0, 0.0, 10.0, 2000.0);
        this.breakBlocksWhitelist = BooleanValue.create(this, "Break blocks whitelist", false);
        this.blockBreakItems = LimitValue.create(this, "autoclicker-blockbreak-items", "Items", LimitValue.ALLOW_LIST_COLOR, Arrays.asList(new ItemLimitData("pickaxes"), new ItemLimitData("shovels")));
        this.breakBlockTimer = new TimerUtil();
        this.limitItems.addDependentValues(this.itemWhitelist);
        this.limitItems.setCompactListValue(this.itemWhitelist);
        this.breakBlocks.addDependentValues(this.breakBlocksDelay, this.breakBlocksWhitelist);
        this.breakBlocksWhitelist.setCompactListValue(this.blockBreakItems);
        this.breakBlocksWhitelist.addDependentValues(this.blockBreakItems);
        this.addValue(this.holdToClick, this.triggerMode, this.breakBlocks, this.breakBlocksDelay, this.breakBlocksWhitelist, this.blockBreakItems, this.cps, this.randomization, this.jitter, this.limitItems, this.itemWhitelist);
        ClickEngine clickEngine = new ClickEngine(ClickButton.LEFT, this.cps, this.limitItems,
                this.itemWhitelist, this.holdToClick, this.randomization, this.jitter, this);
        this.setClickEngine(clickEngine);
        this.cps.setMaximumFractionDigits(0);
    }
}
