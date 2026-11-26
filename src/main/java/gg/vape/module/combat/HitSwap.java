package gg.vape.module.combat;

import gg.vape.event.EventHandler;
import gg.vape.event.EventPriority;
import gg.vape.event.impl.EventKeyPress;
import gg.vape.event.impl.EventMouseButton;
import gg.vape.event.impl.EventPreTick;
import gg.vape.event.impl.SyntheticAttackRequestEvent;
import gg.vape.input.AttackKeyController;
import gg.vape.input.InputEventDispatcher;
import gg.vape.mapping.MappedClasses;
import gg.vape.module.Category;
import gg.vape.module.Mod;
import gg.vape.module.control.AttackCancellationAdapter;
import gg.vape.module.control.PhysicalAttackCancellationAdapter;
import gg.vape.module.control.SyntheticAttackCancellationAdapter;
import gg.vape.rotation.RotationManager;
import gg.vape.unmap.ItemLimitData;
import gg.vape.utils.ItemStackScoreUtil;
import gg.vape.utils.RotationUtil;
import gg.vape.value.BooleanValue;
import gg.vape.value.LimitValue;
import gg.vape.wrapper.impl.EnchantmentHelper;
import gg.vape.wrapper.impl.Entity;
import gg.vape.wrapper.impl.EntityOtherPlayerMP;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.EnumHand;
import gg.vape.wrapper.impl.InventoryPlayer;
import gg.vape.wrapper.impl.Item;
import gg.vape.wrapper.impl.ItemStack;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.RayTraceResult;
import gg.vape.wrapper.impl.RayTraceResult_type;
import gg.vape.wrapper.impl.WorldClient;
import java.util.Arrays;

public class HitSwap
extends Mod {
    public final BooleanValue breachMaces;
    private volatile boolean swingReleasePending = false;
    private final Object swapRequestLock = new Object();
    private long swapRequestEpoch;
    private volatile boolean swapAttackInFlight;
    private int swapAttackOutcome;
    private int queuedAttackTicks;
    private boolean pendingStunTransition;
    public final BooleanValue stunSlam;
    private int tickCounter;
    public final BooleanValue axes;
    private boolean stunSlamActive = false;
    private boolean stunSlamSecondPhase = false;
    private boolean breachSwapPending = false;
    public final LimitValue allowedItems;
    private boolean swapAttackPending = false;
    public final BooleanValue limitToItems = BooleanValue.create(this, "Limit to items", false);
    private volatile boolean swapActive;
    public final BooleanValue smashOnly;
    public final BooleanValue densityMaces;
    private int originalSlot = -1;
    private volatile int swappedSlot = -1;
    private volatile Object swapPlayerObject;
    private volatile Object swapWorldObject;
    private volatile Object swapTargetObject;
    private static final long MODULE_ID = -7666507152973844357L;
    public final BooleanValue maces = BooleanValue.create(this, "Maces", true);
    public final BooleanValue swords;

    private boolean shouldFilterByEnchant() {
        return (this.densityMaces.getEffectiveValue() != false || this.breachMaces.getEffectiveValue() != false) && this.hasMaceInHotbar();
    }

    private boolean hasMaceInHotbar() {
        InventoryPlayer inventory = Minecraft.thePlayer().V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6();
        for (int slot = 0; slot < 9; ++slot) {
            if (this.isValidMace(inventory.c(slot), true)) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(priority=EventPriority.HIGH)
    public void onKeyPress(EventKeyPress event) {
        if (event.isCanceled() || !event.isKeybinding(Minecraft.gameSettings().F()) || !event.isDown()) {
            return;
        }
        this.handleAttack(new PhysicalAttackCancellationAdapter(event));
    }

    @Override
    public void onDisable() {
        this.finishSwap(Minecraft.thePlayer());
    }

    private boolean isTargetSmashReady(EntityOtherPlayerMP targetPlayer) {
        EnumHand activeHand = RotationUtil.q(targetPlayer);
        if (activeHand == null) {
            return false;
        }
        ItemStack activeItem = targetPlayer.i(activeHand);
        if (activeItem.isNull() || activeItem.getItem().isNull()) return false;
        float useDuration = targetPlayer.d(activeHand);
        if (useDuration <= 0.0f) {
            return false;
        }
        int maximumUseDuration = activeItem.getItem().I(activeItem, targetPlayer);
        return maximumUseDuration - useDuration > 5.0f;
    }

    public boolean hasAlternateAxe() {
        if (this.axes.getEffectiveValue().booleanValue()) {
            InventoryPlayer inventory = Minecraft.thePlayer().V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6();
            int selectedSlot = inventory.v();
            for (int slot = 0; slot < 9; ++slot) {
                ItemStack stack = inventory.c(slot);
                if (slot != selectedSlot && stack.isNotNull() && stack.getItem().isNotNull()
                        && stack.getItem().isInstance(MappedClasses.YP)) {
                    return true;
                }
            }
        }
        return false;
    }

    private EntityOtherPlayerMP getTargetPlayer() {
        RayTraceResult hit = RotationManager.INSTANCE.getExtendedReachRayTrace();
        if (hit == null || hit.isNull() || !hit.getTypeOfHit().equals(RayTraceResult_type.entity())) return null;
        Entity target = hit.getEntity();
        return target.isNotNull() && target.isInstance(MappedClasses.lG)
                ? new EntityOtherPlayerMP(target.getObject()) : null;
    }

    private boolean isOriginalTargetUnderCrosshair() {
        RayTraceResult hit = RotationManager.INSTANCE.getExtendedReachRayTrace();
        return this.swapTargetObject != null && hit != null && hit.isNotNull()
                && hit.getTypeOfHit().equals(RayTraceResult_type.entity())
                && hit.getEntity().isNotNull() && hit.getEntity().getObject() == this.swapTargetObject;
    }

    private void finishSwap(EntityPlayerSP player) {
        int restoreSlot;
        int expectedSlot;
        Object restorePlayer;
        boolean release;
        synchronized (this.swapRequestLock) {
            restoreSlot = this.swapActive ? this.originalSlot : -1;
            expectedSlot = this.swappedSlot;
            restorePlayer = this.swapPlayerObject;
            ++this.swapRequestEpoch;
            release = this.swingReleasePending;
            this.swingReleasePending = false;
            this.swapAttackInFlight = false;
            this.swapAttackOutcome = 0;
            this.queuedAttackTicks = 0;
            this.pendingStunTransition = false;
            this.swapActive = false;
            this.originalSlot = -1;
            this.swappedSlot = -1;
            this.swapPlayerObject = null;
            this.swapWorldObject = null;
            this.swapTargetObject = null;
            this.tickCounter = 0;
            this.swapAttackPending = false;
            this.breachSwapPending = false;
            this.stunSlamActive = false;
            this.stunSlamSecondPhase = false;
        }
        if (player != null && player.isNotNull() && player.getObject() == restorePlayer && restoreSlot >= 0) {
            InventoryPlayer inventory = player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6();
            // Do not undo a manual slot change made while the swap was pending.
            if (inventory.v() == expectedSlot && restoreSlot != expectedSlot) inventory.g(restoreSlot);
        }
        if (release) AttackKeyController.releaseAttackKey();
    }

    private void releasePendingSwing() {
        boolean release;
        synchronized (this.swapRequestLock) {
            release = this.swingReleasePending;
            this.swingReleasePending = false;
        }
        if (release) AttackKeyController.releaseAttackKey();
    }

    private void queueSwapAttack(boolean firstStunPhase) {
        final long epoch;
        final int slot;
        final Object target;
        final Object playerObject;
        final Object worldObject;
        synchronized (this.swapRequestLock) {
            if (!this.swapActive) return;
            epoch = this.swapRequestEpoch;
            slot = this.swappedSlot;
            target = this.swapTargetObject;
            playerObject = this.swapPlayerObject;
            worldObject = this.swapWorldObject;
            this.swapAttackInFlight = true;
            this.swapAttackOutcome = 0;
            this.queuedAttackTicks = 0;
            this.pendingStunTransition = firstStunPhase;
        }
        EntityPlayerSP queuedPlayer = Minecraft.thePlayer();
        if (queuedPlayer.isNull() || queuedPlayer.getObject() != playerObject || slot < 0 || slot >= 9) {
            this.finishSwap(queuedPlayer);
            return;
        }
        ItemStack stack = queuedPlayer.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6().c(slot);
        if (stack == null || stack.isNull()) {
            this.finishSwap(queuedPlayer);
            return;
        }
        final Object stackObject = stack.getObject();
        AttackKeyController.releaseAttackKey();
        AttackKeyController.requestValidatedSyntheticAttack(this, () -> {
            synchronized (this.swapRequestLock) {
                if (epoch != this.swapRequestEpoch || !this.swapActive
                        || slot != this.swappedSlot || target != this.swapTargetObject) return false;
            }
            EntityPlayerSP player = Minecraft.thePlayer();
            WorldClient world = Minecraft.theWorld();
            if (!this.isEnabled() || player == null || player.isNull() || player.getObject() != playerObject
                    || world.isNull() || world.getObject() != worldObject || Minecraft.currentScreen().isNotNull()
                    || !InputEventDispatcher.getInstance().getFocusState().isFocused()) return false;
            InventoryPlayer inventory = player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6();
            if (inventory.v() != slot) return false;
            ItemStack currentStack = inventory.c(slot);
            return currentStack != null && currentStack.isNotNull() && currentStack.getObject() == stackObject
                    && this.isOriginalTargetUnderCrosshair();
        }, pressed -> {
            boolean releaseLate = false;
            synchronized (this.swapRequestLock) {
                if (epoch != this.swapRequestEpoch || !this.swapActive) {
                    releaseLate = pressed;
                } else {
                    this.swingReleasePending = pressed;
                    this.swapAttackOutcome = pressed ? 1 : -1;
                }
            }
            if (releaseLate) AttackKeyController.releaseAttackKey();
        });
    }

    // False means still queued or rejected. A successful completion is the
    // only event that may advance the stun/slam state machine.
    private boolean settleSwapAttack(EntityPlayerSP player) {
        int outcome;
        boolean firstStunPhase;
        synchronized (this.swapRequestLock) {
            if (!this.swapAttackInFlight) return true;
            outcome = this.swapAttackOutcome;
            if (outcome == 0) return false;
            firstStunPhase = this.pendingStunTransition;
            this.swapAttackInFlight = false;
            this.swapAttackOutcome = 0;
            this.queuedAttackTicks = 0;
            this.pendingStunTransition = false;
        }
        if (outcome < 0) {
            this.finishSwap(player);
            return false;
        }
        if (firstStunPhase && this.stunSlamActive) this.stunSlamSecondPhase = true;
        return true;
    }

    private boolean canHandleAttack() {
        if (Minecraft.thePlayer().isNull() || Minecraft.theWorld().isNull()
                || Minecraft.currentScreen().isNotNull()
                || !InputEventDispatcher.getInstance().getFocusState().isFocused()) {
            return false;
        }
        ItemStack heldItem = Minecraft.thePlayer().getHeldItemHand();
        return !this.limitToItems.getEffectiveValue() || this.allowedItems.isValid(heldItem, false);
    }

    @EventHandler(priority=EventPriority.HIGH)
    public void onSyntheticAttack(SyntheticAttackRequestEvent event) {
        if (event.isCanceled()) return;
        Mod source = event.getSource();
        if (source == this || source instanceof ShieldBreaker
                || source instanceof AutoMace && ((AutoMace)source).isSyntheticAttackInProgress()) {
            return;
        }
        this.handleAttack(new SyntheticAttackCancellationAdapter(event));
    }

    @EventHandler(priority=EventPriority.HIGH)
    public void onMouseButton(EventMouseButton event) {
        if (event.isCanceled() || !event.isKeybinding(Minecraft.gameSettings().F()) || !event.isDown()) {
            return;
        }
        this.handleAttack(new PhysicalAttackCancellationAdapter(event));
    }

    private int findAxeSlot() {
        InventoryPlayer inventory = Minecraft.thePlayer().V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6();
        int selectedSlot = inventory.v();
        for (int slot = 0; slot < 9; ++slot) {
            ItemStack stack = inventory.c(slot);
            if (slot != selectedSlot && stack.isNotNull() && stack.getItem().isNotNull()
                    && stack.getItem().isInstance(MappedClasses.YP)) {
                return slot;
            }
        }
        return -1;
    }

    public boolean hasValidWeaponSwap() {
        int weaponSlot = this.findWeaponSlot();
        if (weaponSlot < 0) {
            return false;
        }
        InventoryPlayer inventory = Minecraft.thePlayer().V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6();
        return this.isValidMace(inventory.c(weaponSlot), this.shouldFilterByEnchant());
    }

    private int findMaceSlot() {
        InventoryPlayer inventory = Minecraft.thePlayer().V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6();
        for (int slot = 0; slot < 9; ++slot) {
            if (this.isValidMace(inventory.c(slot), this.shouldFilterByEnchant())) {
                return slot;
            }
        }
        return -1;
    }

    @EventHandler(priority=EventPriority.HIGH)
    public void onTick(EventPreTick eventPreTick) {
        EntityPlayerSP player = eventPreTick.getThePlayer();
        WorldClient world = Minecraft.theWorld();
        if (player.isNull() || world.isNull()) {
            this.finishSwap(player);
            return;
        }
        this.releasePendingSwing();
        if (this.swapActive) {
            InventoryPlayer inventory = player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6();
            if (Minecraft.currentScreen().isNotNull()
                    || !InputEventDispatcher.getInstance().getFocusState().isFocused()
                    || player.getObject() != this.swapPlayerObject || world.getObject() != this.swapWorldObject
                    || inventory.v() != this.swappedSlot
                    || ((this.breachSwapPending || this.swapAttackPending || this.stunSlamSecondPhase
                         || this.swapAttackInFlight) && !this.isOriginalTargetUnderCrosshair())) {
                this.finishSwap(player);
                return;
            }
        }
        if (this.swapAttackInFlight && !this.settleSwapAttack(player)) {
            // A stalled tick executor must not leave the player's slot swapped
            // indefinitely. Invalidation also rejects a later queued request.
            if (this.swapAttackInFlight && ++this.queuedAttackTicks > 4) this.finishSwap(player);
            return;
        }
        if (this.breachSwapPending && this.swapActive && this.tickCounter >= 1) {
            int maceSlot = this.findMaceSlot();
            if (maceSlot == -1) {
                this.finishSwap(player);
                return;
            }
            player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6().g(maceSlot);
            this.swappedSlot = maceSlot;
            this.breachSwapPending = false;
            this.queueSwapAttack(false);
            this.settleSwapAttack(player);
            return;
        }
        if (this.swapAttackPending && this.swapActive) {
            this.swapAttackPending = false;
            this.queueSwapAttack(this.stunSlamActive);
            if (this.settleSwapAttack(player)) ++this.tickCounter;
            return;
        }
        if (this.stunSlamActive && this.swapActive && this.stunSlamSecondPhase && this.tickCounter >= 1) {
            int maceSlot = this.findMaceSlot();
            if (maceSlot == -1) {
                this.finishSwap(player);
                return;
            }
            player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6().g(maceSlot);
            this.swappedSlot = maceSlot;
            this.stunSlamSecondPhase = false;
            this.stunSlamActive = false;
            this.queueSwapAttack(false);
            this.settleSwapAttack(player);
            return;
        }
        if (this.swapActive) {
            int restoreDelayTicks = this.stunSlamActive || this.stunSlamSecondPhase ? 2 : 1;
            if (this.tickCounter++ > restoreDelayTicks) {
                this.finishSwap(player);
            }
        }
    }

    private void handleAttack(AttackCancellationAdapter cancellation) {
        if (this.swapActive) {
            return;
        }
        if (!this.canHandleAttack()) {
            return;
        }
        EntityPlayerSP player = Minecraft.thePlayer();
        WorldClient world = Minecraft.theWorld();
        if (player.isNull() || world.isNull()) {
            return;
        }
        if (player.l$src$Z$1io4duf()) {
            return;
        }
        RayTraceResult crosshairHit = RotationManager.INSTANCE.getExtendedReachRayTrace();
        if (crosshairHit == null || crosshairHit.isNull()
                || !crosshairHit.getTypeOfHit().equals(RayTraceResult_type.entity())) {
            return;
        }
        Entity target = crosshairHit.getEntity();
        if (target.isNull()) {
            return;
        }
        ItemStack heldItem = player.getHeldItemHand();
        EntityOtherPlayerMP targetPlayer = target.isInstance(MappedClasses.lG)
                ? new EntityOtherPlayerMP(target.getObject()) : null;
        int maceSlot = this.findMaceSlot();
        if (this.stunSlam.getEffectiveValue().booleanValue() && this.maces.getEffectiveValue().booleanValue()
                && heldItem.isNotNull() && heldItem.getItem().isNotNull()
                && targetPlayer != null && this.isTargetSmashReady(targetPlayer) && maceSlot >= 0) {
            if (heldItem.getItem().isInstance(MappedClasses.YP)) {
                this.originalSlot = player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6().v();
                this.swappedSlot = this.originalSlot;
                this.swapPlayerObject = player.getObject();
                this.swapWorldObject = world.getObject();
                this.swapTargetObject = target.getObject();
                this.breachSwapPending = true;
                this.swapActive = true;
                this.tickCounter = 0;
                return;
            }
            int axeSlot = this.findAxeSlot();
            if (axeSlot >= 0) {
                cancellation.setCancelled(true);
                this.originalSlot = player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6().v();
                this.swappedSlot = axeSlot;
                this.swapPlayerObject = player.getObject();
                this.swapWorldObject = world.getObject();
                this.swapTargetObject = target.getObject();
                player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6().g(axeSlot);
                this.stunSlamActive = true;
                this.swapActive = true;
                this.tickCounter = 0;
                this.swapAttackPending = true;
                return;
            }
        }
        int weaponSlot = this.findWeaponSlot();
        if (weaponSlot >= 0) {
            cancellation.setCancelled(true);
            this.originalSlot = player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6().v();
            this.swappedSlot = weaponSlot;
            this.swapPlayerObject = player.getObject();
            this.swapWorldObject = world.getObject();
            this.swapTargetObject = target.getObject();
            player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6().g(weaponSlot);
            this.swapActive = true;
            this.tickCounter = 0;
            this.swapAttackPending = true;
        }
    }

    private boolean isValidMace(ItemStack itemStack, boolean requireConfiguredEnchantment) {
        if (this.smashOnly.getEffectiveValue().booleanValue() && !RotationUtil.u(Minecraft.thePlayer())) {
            return false;
        }
        if (itemStack.isNull()) {
            return false;
        }
        if (itemStack.getItem().isNull()) {
            return false;
        }
        Item item = itemStack.getItem();
        if (item.isInstance(MappedClasses.zx)) {
            if (!requireConfiguredEnchantment) {
                return true;
            }
            if (this.densityMaces.getEffectiveValue().booleanValue() && EnchantmentHelper.e("density", itemStack) > 0) {
                return true;
            }
            if (this.breachMaces.getEffectiveValue().booleanValue() && EnchantmentHelper.e("breach", itemStack) > 0) {
                return true;
            }
            return this.densityMaces.getEffectiveValue() == false && this.breachMaces.getEffectiveValue() == false;
        }
        return false;
    }

    public HitSwap() {
        super("HitSwap", (int)MODULE_ID, Category.UTILITY, "Swaps into another weapon on attack, copying its attributes\nAKA BreachSwap, ZeroTick");
        this.smashOnly = BooleanValue.create(this, "Smash only", true, "Only swap to mace if will smash");
        this.breachMaces = BooleanValue.create(this, "Breach maces", false, "Will use Maces with Breach enchantment");
        this.densityMaces = BooleanValue.create(this, "Density maces", true, "Will use Maces with Breach enchantment");
        this.stunSlam = BooleanValue.create(this, "Stun slam", false, "When holding an axe and attacking a shielded player:\nHits with axe first (breaks shield), then swaps to mace for a follow-up slam");
        this.axes = BooleanValue.create(this, "Axes", true);
        this.swords = BooleanValue.create(this, "Swords", false);
        this.allowedItems = LimitValue.create(this, "bs-alloweditems", "Allowed Items", LimitValue.ALLOW_LIST_COLOR, Arrays.asList(new ItemLimitData("swords")));
        this.addValue(this.maces, this.smashOnly, this.breachMaces, this.densityMaces, this.stunSlam, this.axes, this.swords, this.limitToItems, this.allowedItems);
        this.limitToItems.addDependentValues(this.allowedItems);
        this.maces.addDependentValues(this.smashOnly, this.breachMaces, this.densityMaces, this.stunSlam);
    }

    private int findWeaponSlot() {
        InventoryPlayer inventory = Minecraft.thePlayer().V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6();
        int selectedSlot = inventory.v();
        for (int slot = 0; slot < 9; ++slot) {
            ItemStack stack = inventory.c(slot);
            if (slot == selectedSlot || stack.isNull() || stack.getItem().isNull()) {
                continue;
            }
            Item item = stack.getItem();
            EntityOtherPlayerMP targetPlayer = this.getTargetPlayer();
            EnumHand activeHand = targetPlayer != null ? RotationUtil.q(targetPlayer) : null;
            if (this.axes.getEffectiveValue().booleanValue() && item.isInstance(MappedClasses.YP) && activeHand != null) {
                ItemStack activeItem = targetPlayer.i(activeHand);
                float useDuration = targetPlayer.d(activeHand);
                if (activeItem.isNull() || activeItem.getItem().isNull()) continue;
                int maximumUseDuration = activeItem.getItem().I(activeItem, targetPlayer);
                if (useDuration > 0.0f) {
                    if (maximumUseDuration - useDuration > 5.0f) {
                        return slot;
                    }
                    return -2;
                }
            }
            if (this.maces.getEffectiveValue().booleanValue() && this.isValidMace(stack, this.shouldFilterByEnchant())) {
                return slot;
            }
            if (this.swords.getEffectiveValue().booleanValue() && ItemStackScoreUtil.h(item)) {
                return slot;
            }
        }
        return -1;
    }
}
