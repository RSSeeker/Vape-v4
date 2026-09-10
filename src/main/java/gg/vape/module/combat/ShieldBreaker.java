package gg.vape.module.combat;

import gg.vape.event.Event;
import gg.vape.event.EventHandler;
import gg.vape.event.EventPriority;
import gg.vape.event.impl.EventKeyPress;
import gg.vape.event.impl.EventMouseButton;
import gg.vape.event.impl.EventPreTick;
import gg.vape.event.impl.SyntheticAttackRequestEvent;
import gg.vape.input.AttackKeyController;
import gg.vape.mapping.MappedClasses;
import gg.vape.module.Category;
import gg.vape.module.Mod;
import gg.vape.rotation.RotationManager;
import gg.vape.unmap.ItemLimitData;
import gg.vape.utils.ItemStackScoreUtil;
import gg.vape.utils.RotationUtil;
import gg.vape.value.BooleanValue;
import gg.vape.value.LimitValue;
import gg.vape.value.NumberValue;
import gg.vape.wrapper.impl.Entity;
import gg.vape.wrapper.impl.EntityOtherPlayerMP;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.InventoryPlayer;
import gg.vape.wrapper.impl.ItemStack;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.RayTraceResult;

public class ShieldBreaker extends Mod {
    private static final long MODULE_ID = -7666507152973844354L;

    private final NumberValue swapDelay;
    private final BooleanValue autoSwitchBack;
    private final NumberValue swapBackDelay;
    private final BooleanValue doubleClick;
    private final BooleanValue limitToItems;
    private final LimitValue allowedItems;
    private SwapState state = SwapState.IDLE;
    private int originalSlot = -1;
    private int ticksRemaining;
    private boolean attackReleasePending;

    public ShieldBreaker() {
        super("ShieldBreaker", (int)MODULE_ID, Category.COMBAT,
                "Swaps to an axe when attacking a player with a raised shield");
        this.swapDelay = NumberValue.create(
                this, "Swap delay", "#", "tick", 0.0, 5.0, 20.0, 1.0,
                "Delay between swapping to an axe and attacking");
        this.autoSwitchBack = BooleanValue.create(this, "Auto swap back", true, "Sweeping back to the original slot");
        this.swapBackDelay = NumberValue.create(
                this, "Swap back delay", "#", "tick", 0.0, 5.0, 20.0, 1.0,
                "Delay between attacking and sweeping back to the original slot");
        this.doubleClick = BooleanValue.create(this, "Double click", false,
                "Attacks again immediately after breaking the shield to knock the target back");
        this.limitToItems = BooleanValue.create(this, "Limit to items", false,
                "ShieldBreaker functions only while holding selected items");
        this.allowedItems = LimitValue.create(this, "shieldbreaker-alloweditems", "Allowed Items",
                LimitValue.ALLOW_LIST_COLOR, new ItemLimitData("swords"));
        this.swapDelay.setMaximumFractionDigits(0);
        this.swapBackDelay.setMaximumFractionDigits(0);
        this.autoSwitchBack.addDependentValues(this.swapBackDelay);
        this.limitToItems.addDependentValues(this.allowedItems);
        this.addValue(this.swapDelay, this.autoSwitchBack, this.swapBackDelay, this.doubleClick, this.limitToItems, this.allowedItems);
    }

    @EventHandler(priority = EventPriority.HIGH, skipCanceled = true)
    public void onMouseButton(EventMouseButton event) {
        if (event.isKeybinding(Minecraft.gameSettings().F()) && event.isDown()) {
            this.handleAttack(event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, skipCanceled = true)
    public void onKeyPress(EventKeyPress event) {
        if (event.isKeybinding(Minecraft.gameSettings().F()) && event.isDown()) {
            this.handleAttack(event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, skipCanceled = true)
    public void onSyntheticAttack(SyntheticAttackRequestEvent event) {
        Mod source = event.getSource();
        if (source == this || source instanceof HitSwap
                || source instanceof AutoMace && ((AutoMace)source).isSyntheticAttackInProgress()) {
            return;
        }
        this.handleAttack(event);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onTick(EventPreTick event) {
        EntityPlayerSP player = event.getThePlayer();
        if (player.isNull() || Minecraft.currentScreen().isNotNull()) {
            this.resetState(player, true);
            return;
        }

        if (this.attackReleasePending) {
            AttackKeyController.releaseAttackKey();
            this.attackReleasePending = false;
        }

        if (this.state == SwapState.IDLE) {
            return;
        }

        if (this.state == SwapState.WAITING_TO_SWAP_BACK
                && !this.autoSwitchBack.getEffectiveValue()) {
            this.resetState(player, false);
            return;
        }

        if (this.ticksRemaining > 0) {
            --this.ticksRemaining;
            if (this.ticksRemaining > 0) {
                return;
            }
        }

        if (this.state == SwapState.WAITING_TO_ATTACK) {
            this.attackWithAxe(player);
        } else if (this.state == SwapState.WAITING_TO_SWAP_BACK) {
            this.resetState(player, true);
        }
    }

    @Override
    public void onDisable() {
        this.resetState(Minecraft.thePlayer(), true);
    }

    private void handleAttack(Event event) {
        if (this.state != SwapState.IDLE || Minecraft.currentScreen().isNotNull()) {
            return;
        }

        EntityPlayerSP player = Minecraft.thePlayer();
        if (player.isNull() || !this.canUseHeldItem(player) || !this.isAttackingRaisedShield()) {
            return;
        }

        InventoryPlayer inventory = player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6();
        int selectedSlot = inventory.v();
        if (this.isAxe(inventory.c(selectedSlot))) {
            if (this.doubleClick.getEffectiveValue() && this.attackReleasePending) {
                AttackKeyController.releaseAttackKey();
                this.attackReleasePending = AttackKeyController.requestSyntheticAttack(this);
            }
            return;
        }

        int axeSlot = this.findAxeSlot(inventory);
        if (axeSlot == -1) {
            return;
        }

        this.originalSlot = selectedSlot;
        inventory.g(axeSlot);
        this.state = SwapState.WAITING_TO_ATTACK;
        this.ticksRemaining = this.getTickValue(this.swapDelay);
        event.setCancelled(true);

        if (this.ticksRemaining == 0) {
            this.attackWithAxe(player);
        }
    }

    private boolean canUseHeldItem(EntityPlayerSP player) {
        return !this.limitToItems.getEffectiveValue()
                || this.allowedItems.isValid(player.getHeldItemHand(), false);
    }

    private boolean isAttackingRaisedShield() {
        RayTraceResult rayTrace = RotationManager.INSTANCE.getExtendedReachRayTrace();
        if (rayTrace == null || !rayTrace.isEntityHit()) {
            return false;
        }

        Entity target = rayTrace.getEntity();
        if (target == null || target.isNull() || !target.isInstance(MappedClasses.lG)) {
            return false;
        }

        return RotationUtil.n(new EntityOtherPlayerMP(target.getObject()));
    }

    public boolean hasAxeInHotbar() {
        EntityPlayerSP player = Minecraft.thePlayer();
        return player.isNotNull() && this.findAxeSlot(player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6()) >= 0;
    }

    private void attackWithAxe(EntityPlayerSP player) {
        InventoryPlayer inventory = player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6();
        if (!this.isAxe(inventory.c(inventory.v()))) {
            int axeSlot = this.findAxeSlot(inventory);
            if (axeSlot == -1) {
                this.resetState(player, true);
                return;
            }
            inventory.g(axeSlot);
        }

        AttackKeyController.releaseAttackKey();
        this.attackReleasePending = AttackKeyController.requestSyntheticAttack(this);
        if (this.doubleClick.getEffectiveValue() && this.attackReleasePending) {
            AttackKeyController.releaseAttackKey();
            this.attackReleasePending = AttackKeyController.requestSyntheticAttack(this);
        }
        if (this.autoSwitchBack.getEffectiveValue()) {
            this.state = SwapState.WAITING_TO_SWAP_BACK;
            this.ticksRemaining = this.getTickValue(this.swapBackDelay);
        } else {
            this.originalSlot = -1;
            this.ticksRemaining = 0;
            this.state = SwapState.IDLE;
        }
    }

    private int findAxeSlot(InventoryPlayer inventory) {
        for (int slot = 0; slot < 9; ++slot) {
            if (this.isAxe(inventory.c(slot))) {
                return slot;
            }
        }
        return -1;
    }

    private boolean isAxe(ItemStack stack) {
        return stack != null && stack.isNotNull() && stack.getItem().isNotNull()
                && ItemStackScoreUtil.T(stack.getItem());
    }

    private int getTickValue(NumberValue value) {
        return Math.max(0, value.getValue().intValue());
    }

    private void resetState(EntityPlayerSP player, boolean restoreSlot) {
        if (this.attackReleasePending) {
            AttackKeyController.releaseAttackKey();
        }
        if (restoreSlot && this.originalSlot != -1 && player != null && player.isNotNull()) {
            player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6().g(this.originalSlot);
        }
        this.attackReleasePending = false;
        this.originalSlot = -1;
        this.ticksRemaining = 0;
        this.state = SwapState.IDLE;
    }

    private enum SwapState {
        IDLE,
        WAITING_TO_ATTACK,
        WAITING_TO_SWAP_BACK
    }
}