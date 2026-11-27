package gg.vape.module.utility;

import gg.vape.Vape;
import gg.vape.event.EventHandler;
import gg.vape.event.impl.EventPreTick;
import gg.vape.input.AttackKeyController;
import gg.vape.input.BindSet;
import gg.vape.input.KeyboardInput;
import gg.vape.input.MouseInput;
import gg.vape.module.Category;
import gg.vape.module.Mod;
import gg.vape.utils.AttackCooldownUtil;
import gg.vape.value.BindValue;
import gg.vape.value.BooleanValue;
import gg.vape.value.NumberValue;
import gg.vape.wrapper.impl.EnchantmentHelper;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.FoodStats;
import gg.vape.wrapper.impl.InventoryPlayer;
import gg.vape.wrapper.impl.ItemStack;
import gg.vape.wrapper.impl.Minecraft;
import java.util.List;

/**
 * One key = spear lunge swap: hotbar -> spear -> jab -> back.
 *
 * <p>Ported from the Auto Lunge Swap mod (autolungeswap 1.2.0). The mod swaps the
 * selected hotbar slot to a spear carrying the Lunge enchantment, performs a
 * synthetic attack on the same tick, and swaps back to the previous item either
 * immediately or after a short delay.</p>
 */
public class AutoLungeSwap
extends Mod {
    private static final long MODULE_ID = 2754812098374561281L;
    private static final int FLASH_TICKS = 6;

    public static final int STATUS_OFF = 0;
    public static final int STATUS_NO_SPEAR = 1;
    public static final int STATUS_HUNGRY = 2;
    public static final int STATUS_COOLDOWN = 3;
    public static final int STATUS_READY = 4;
    public static final int STATUS_LUNGED = 5;

    private final BindValue triggerKey;
    private final BooleanValue waitForCooldown;
    private final BooleanValue holdToRepeat;
    private final BooleanValue returnToOriginalSlot;
    private final NumberValue minHunger;
    private final NumberValue swapBackDelay;

    private boolean sessionActive;
    private boolean swingReleasePending;
    private int originalSlot = -1;
    private int spearSlot = -1;
    private int lungeLevel;
    private int pendingRevertSlot = -1;
    private int pendingRevertTicks;
    private int ticksSinceLunge;
    private int statusFlashTicks;
    private int status = STATUS_OFF;
    private boolean triggerWasDown;

    public AutoLungeSwap() {
        super("AutoLungeSwap", (int)MODULE_ID, Category.COMBAT,
                "Auto-swaps to a spear for a lunge attack, then back.");
        this.triggerKey = BindValue.createEmpty(this, "Trigger key");
        this.waitForCooldown = BooleanValue.create(this, "Wait for cooldown", true,
                "Do not lunge while the attack cooldown is still recovering");
        this.holdToRepeat = BooleanValue.create(this, "Hold to repeat", true,
                "Keep lunging while the key is held down");
        this.returnToOriginalSlot = BooleanValue.create(this, "Return to original slot", true,
                "Swap back to the item that was held before the lunge");
        this.minHunger = NumberValue.create(this, "Min hunger", "0", "", 0.0, 7.0, 20.0);
        this.swapBackDelay = NumberValue.create(this, "Swap back delay", "0", " ticks", 0.0, 1.0, 10.0);
        this.addValue(this.triggerKey, this.waitForCooldown, this.holdToRepeat,
                this.returnToOriginalSlot, this.minHunger, this.swapBackDelay);
    }

    @EventHandler
    public void onTick(EventPreTick eventPreTick) {
        EntityPlayerSP player = eventPreTick.getThePlayer();
        if (player.isNull() || Minecraft.theWorld().isNull()) {
            this.resetSession();
            this.status = STATUS_OFF;
            return;
        }
        InventoryPlayer inventory = player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6();

        if (this.swingReleasePending) {
            AttackKeyController.releaseAttackKey();
            this.swingReleasePending = false;
        }
        if (this.ticksSinceLunge < 10000) {
            ++this.ticksSinceLunge;
        }
        if (this.statusFlashTicks > 0) {
            --this.statusFlashTicks;
        }
        this.handlePendingRevert(inventory);
        this.refreshSpear(inventory);

        if (Minecraft.currentScreen().isNotNull()) {
            this.endSession(inventory);
            this.status = STATUS_OFF;
            return;
        }

        boolean keyDown = this.isTriggerDown();
        boolean pressedThisTick = keyDown && !this.triggerWasDown;
        this.triggerWasDown = keyDown;
        if (!keyDown) {
            this.endSession(inventory);
            this.status = this.statusFlashTicks > 0 ? STATUS_LUNGED : STATUS_OFF;
            return;
        }
        if (!this.sessionActive) {
            this.startSession(inventory);
        }
        this.status = this.evaluate(player);
        boolean wantLunge = this.holdToRepeat.getEffectiveValue()
                ? keyDown
                : pressedThisTick;
        if (wantLunge && this.status == STATUS_READY && this.ticksSinceLunge > 0 && this.performLunge(inventory)) {
            this.ticksSinceLunge = 0;
            this.statusFlashTicks = FLASH_TICKS;
            this.status = STATUS_LUNGED;
        } else if (this.statusFlashTicks > 0) {
            this.status = STATUS_LUNGED;
        }
    }

    @Override
    public void onDisable() {
        if (Minecraft.thePlayer().isNotNull()) {
            InventoryPlayer inventory = Minecraft.thePlayer().V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6();
            this.endSession(inventory);
        }
        this.resetSession();
        this.status = STATUS_OFF;
    }

    public int getStatus() {
        return this.status;
    }

    private boolean isTriggerDown() {
        BindSet bindSet = this.triggerKey.getValue();
        List<Integer> boundInputs = bindSet.getBoundInputs();
        for (int i = 0; i < boundInputs.size(); ++i) {
            int code = boundInputs.get(i);
            if (code < 0) {
                if (MouseInput.isButtonDown(100 + code)) {
                    return true;
                }
            } else if (KeyboardInput.isKeyDown(code)) {
                return true;
            }
        }
        return false;
    }


    private void startSession(InventoryPlayer inventory) {
        this.sessionActive = true;
        this.originalSlot = inventory.v();
        this.pendingRevertSlot = -1;
        this.pendingRevertTicks = 0;
    }

    private void endSession(InventoryPlayer inventory) {
        if (this.sessionActive && this.pendingRevertSlot < 0 && this.returnToOriginalSlot.getEffectiveValue()
                && this.originalSlot >= 0 && inventory.v() != this.originalSlot) {
            inventory.g(this.originalSlot);
        }
        this.sessionActive = false;
        this.originalSlot = -1;
    }

    private void resetSession() {
        this.sessionActive = false;
        this.originalSlot = -1;
        this.spearSlot = -1;
        this.lungeLevel = 0;
        this.pendingRevertSlot = -1;
        this.pendingRevertTicks = 0;
        this.ticksSinceLunge = 0;
        this.statusFlashTicks = 0;
    }

    private void handlePendingRevert(InventoryPlayer inventory) {
        if (this.pendingRevertSlot < 0) {
            return;
        }
        if (--this.pendingRevertTicks <= 0) {
            inventory.g(this.pendingRevertSlot);
            this.pendingRevertSlot = -1;
        }
    }

    private void refreshSpear(InventoryPlayer inventory) {
        this.spearSlot = -1;
        this.lungeLevel = 0;
        for (int slot = 0; slot < 9; ++slot) {
            ItemStack stack = inventory.c(slot);
            if (!this.isSpear(stack)) {
                continue;
            }
            int level = this.lungeLevel(stack);
            // A lunge swap is only meaningful with the Lunge enchantment, so it is always required.
            if (level <= 0) {
                continue;
            }
            if (level > this.lungeLevel) {
                this.lungeLevel = level;
                this.spearSlot = slot;
            }
        }
    }

    private boolean isSpear(ItemStack stack) {
        if (stack == null || stack.isNull()) {
            return false;
        }
        String id = stack.f();
        return id != null && id.toLowerCase().contains("spear");
    }

    private int lungeLevel(ItemStack stack) {
        return EnchantmentHelper.e("lunge", stack);
    }

    private int evaluate(EntityPlayerSP player) {
        if (this.spearSlot < 0) {
            return STATUS_NO_SPEAR;
        }
        FoodStats foodStats = player.Y$src$Lgg_vape_wrapper_impl_FoodStats_$fakh1z();
        int food = foodStats == null ? 20 : foodStats.getFoodLevel();
        if (food < (int)Math.round(this.minHunger.getValue())) {
            return STATUS_HUNGRY;
        }
        if (this.waitForCooldown.getEffectiveValue() && !AttackCooldownUtil.isAttackReady(0.0f)) {
            return STATUS_COOLDOWN;
        }
        return STATUS_READY;
    }

    private boolean performLunge(InventoryPlayer inventory) {
        if (this.pendingRevertSlot >= 0) {
            return false;
        }
        int selected = inventory.v();
        int spear = this.spearSlot;
        if (spear < 0 || spear > 8) {
            return false;
        }
        if (this.originalSlot < 0) {
            this.originalSlot = selected;
        }
        if (spear != selected) {
            inventory.g(spear);
        }
        AttackKeyController.releaseAttackKey();
        boolean requested = AttackKeyController.requestSyntheticAttack(this);
        this.swingReleasePending = true;
        int delay = (int)Math.round(this.swapBackDelay.getValue());
        if (spear != selected && this.returnToOriginalSlot.getEffectiveValue()) {
            if (delay <= 0) {
                inventory.g(selected);
            } else {
                this.pendingRevertSlot = selected;
                this.pendingRevertTicks = delay;
            }
        }
        return true;
    }
}
