package gg.vape.module.utility;

import gg.vape.config.ClientSettings;
import gg.vape.event.EventHandler;
import gg.vape.event.impl.EventPrePlayerTick;
import gg.vape.module.Category;
import gg.vape.module.Mod;
import gg.vape.module.utility.inventory.InventoryActionGuard;
import gg.vape.rotation.RotationManager;
import gg.vape.utils.ItemStackScoreUtil;
import gg.vape.utils.TimerUtil;
import gg.vape.value.BooleanValue;
import gg.vape.value.NumberValue;
import gg.vape.wrapper.impl.InventoryPlayer;
import gg.vape.wrapper.impl.Item;
import gg.vape.wrapper.impl.ItemStack;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.RayTraceResult;

public class AutoTool
extends Mod {
    private final BooleanValue instantSwap;
    private int originalSlot = -1;
    private final BooleanValue swapBack;
    private boolean swapPending;
    private static final long MAGIC_ID = -983995720679623614L;
    private boolean swapBackPending;
    private boolean swapped;
    private TimerUtil swapBackTimer;
    private TimerUtil swapTimer;
    private final BooleanValue swapWeapon = BooleanValue.create(this, "Swap weapon", true, "Swaps to the strongest weapon on your hotbar");
    private final NumberValue swapBackDelay;
    private final BooleanValue requireMouseDown;
    private final NumberValue swapToDelay;
    private final BooleanValue onlyWhileSneaking;
    private final BooleanValue notDuringCombat = BooleanValue.create(this, "Not during combat", false, "Don't swap tools while in combat");
    private final BooleanValue notDuringUse = BooleanValue.create(this, "Not during use", false, "Don't swap tools while using an item (drinking, eating, blocking, drawing bow)");
    private final InventoryActionGuard combatGuard;

    @EventHandler
    public void onTick(EventPrePlayerTick eventPrePlayerTick) {
        if (eventPrePlayerTick.getThePlayer().isNull() || Minecraft.currentScreen().isNotNull()) {
            return;
        }
        if (this.notDuringCombat.getEffectiveValue().booleanValue()) {
            this.combatGuard.update(eventPrePlayerTick.getPlayer());
            if (this.combatGuard.isBlocked()) {
                return;
            }
        }
        if (this.notDuringUse.getEffectiveValue().booleanValue() && eventPrePlayerTick.getPlayer().l$src$Z$1io4duf()) {
            return;
        }
        RayTraceResult rayTraceResult = RotationManager.INSTANCE.getExtendedReachRayTrace();
        boolean mouseDown = ClientSettings.isAttackButtonDown();
        int currentSlot = eventPrePlayerTick.getThePlayer().V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6().v();
        int bestSlot = this.findBestToolSlot(this.swapWeapon.getEffectiveValue());
        if (this.requireMouseDown.getEffectiveValue().booleanValue() && !mouseDown) {
            bestSlot = -1;
        }
        if (this.onlyWhileSneaking.getEffectiveValue().booleanValue() && !eventPrePlayerTick.getThePlayer().P()) {
            bestSlot = -1;
        }
        if (this.swapped && this.swapBack.getEffectiveValue().booleanValue() && !this.swapBackPending && bestSlot == -1 && this.originalSlot != -1) {
            this.swapBackPending = true;
            this.swapBackTimer.reset();
        }
        if (this.swapBackPending) {
            if (this.swapped && this.swapBackTimer.hasTimeElapsed(((Double)this.swapBackDelay.getValue()).longValue())) {
                if (this.originalSlot != -1) {
                    eventPrePlayerTick.getThePlayer().V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6().g(this.originalSlot);
                }
                this.swapped = false;
                this.swapBackPending = false;
                this.originalSlot = -1;
            }
            return;
        }
        if (!this.swapPending && bestSlot != -1 && bestSlot != currentSlot) {
            this.swapPending = true;
            this.swapTimer.reset();
        }
        if (this.swapPending) {
            boolean shouldSwap = this.swapTimer.hasTimeElapsed(((Double)this.swapToDelay.getValue()).longValue());
            if (this.swapWeapon.getEffectiveValue().booleanValue() && this.instantSwap.getEffectiveValue().booleanValue() && rayTraceResult.isNotNull() && rayTraceResult.isEntityHit()) {
                shouldSwap = true;
            }
            if (shouldSwap) {
                int savedSlot = this.originalSlot = this.originalSlot == -1 ? currentSlot : this.originalSlot;
                if (bestSlot != -1 && bestSlot != currentSlot) {
                    eventPrePlayerTick.getThePlayer().V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6().g(bestSlot);
                    this.swapped = true;
                }
                this.swapPending = false;
            }
            return;
        }
    }

    private int findBestToolSlot(boolean includeWeapons) {
        int bestSlot;
        block6: {
            RayTraceResult rayTraceResult;
            block5: {
                rayTraceResult = RotationManager.INSTANCE.getExtendedReachRayTrace();
                if (rayTraceResult.isNull()) {
                    return -1;
                }
                bestSlot = -1;
                if (!rayTraceResult.isBlockHit()) break block5;
                float bestScore = 1.0f;
                InventoryPlayer inventory = Minecraft.thePlayer().V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6();
                for (int i = 0; i < 9; ++i) {
                    ItemStack itemStack = inventory.c(i);
                    if (itemStack.isNull()) continue;
                    float score = itemStack.V(rayTraceResult.g(), rayTraceResult.T(), rayTraceResult.a$src$I$8nuo9d());
                    Item item = itemStack.getItem();
                    if (item.isNotNull() && ItemStackScoreUtil.I(item) && ItemStackScoreUtil.K(itemStack)) {
                        score /= 2.0f;
                    }
                    if (!(score > bestScore)) continue;
                    bestScore = score;
                    bestSlot = i;
                }
                break block6;
            }
            if (!rayTraceResult.isEntityHit() || !includeWeapons) break block6;
            float bestScore = 1.0f;
            InventoryPlayer inventory = Minecraft.thePlayer().V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6();
            for (int i = 0; i < 9; ++i) {
                ItemStack itemStack = inventory.c(i);
                if (itemStack.isNull() || itemStack.getItem().isNull() || !ItemStackScoreUtil.h(itemStack.getItem()) && !ItemStackScoreUtil.I(itemStack.getItem())) continue;
                float score = (float)ClientSettings.getWeaponDamageScore(itemStack);
                if (ItemStackScoreUtil.h(itemStack.getItem())) {
                    score = (float)((double)score + 0.01);
                }
                if (!(score > bestScore)) continue;
                bestScore = score;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    public AutoTool() {
        super("AutoTool", (int)MAGIC_ID, Category.WORLD, "Automatically swaps your hand to the appropriate tool");
        this.instantSwap = BooleanValue.create(this, "Instant swap", true, "Swaps to weapon without swap delay");
        this.swapToDelay = NumberValue.create(this, "Swap to delay", "#", "ms", 0.0, 50.0, 500.0, 50.0, "How long to wait before swapping to tool");
        this.swapBack = BooleanValue.create(this, "Swap back", false, "Swaps back to your original item when not hovering over blocks");
        this.swapBackDelay = NumberValue.create(this, "Swap back delay", "#", "ms", 50.0, 350.0, 1000.0, 50.0, "How long to wait before swapping back");
        this.requireMouseDown = BooleanValue.create(this, "Require mouse down", true, "Only swaps tools while holding left click");
        this.onlyWhileSneaking = BooleanValue.create(this, "Only while sneaking", false, "Only swaps tools while sneaking");
        this.swapBackTimer = new TimerUtil();
        this.swapTimer = new TimerUtil();
        this.combatGuard = new InventoryActionGuard(20);
        this.swapWeapon.addDependentValues(this.instantSwap);
        this.swapBack.addDependentValues(this.swapBackDelay);
        this.addValue(this.swapToDelay, this.swapWeapon, this.instantSwap, this.swapBack, this.swapBackDelay, this.requireMouseDown, this.onlyWhileSneaking, this.notDuringCombat, this.notDuringUse);
    }

}
