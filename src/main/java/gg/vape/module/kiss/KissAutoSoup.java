package gg.vape.module.kiss;

import gg.vape.event.EventHandler;
import gg.vape.event.impl.EventPreTick;
import gg.vape.module.Category;
import gg.vape.module.Mod;
import gg.vape.value.NumberValue;
import gg.vape.wrapper.impl.CPacketHeldItemChange;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.InventoryPlayer;
import gg.vape.wrapper.impl.ItemStack;
import gg.vape.wrapper.impl.KeyBinding;
import gg.vape.wrapper.impl.Minecraft;

/**
 * Kiss AutoSoup (自动喝汤) — eats mushroom stew when health drops below the threshold, then
 * switches back to the best weapon (or the original slot). Ported from KissClient's AutoSoup.java.
 */
public class KissAutoSoup
extends Mod {
    private static final int MOD_ID = -1374350669;
    private static final int PHASE_IDLE = 0;
    private static final int PHASE_SWITCH = 1;
    private static final int PHASE_EAT = 2;
    private static final int PHASE_DONE = 3;
    private static final int REPLACE_DELAY = 2;
    private static final int EAT_TIMEOUT = 45;
    private static final int RETRIGGER_COOLDOWN = 5;
    private static final String SOUP_NAME = "mushroom_stew";

    private final NumberValue health = NumberValue.create(this, "Health", "#.#", "", 1.0, 8.0, 20.0, 0.5);

    private int phase = PHASE_IDLE;
    private int originalSlot = -1;
    private int switchStartTick = -1;
    private int useDoneTick = -1;
    private int nextTriggerTick;
    private int tickCount;

    public KissAutoSoup() {
        super("Kiss AutoSoup", MOD_ID, Category.KISS,
                "Auto soup ported from KissClient: eats mushroom stew under the health threshold and swaps back.");
    }

    @Override
    public void onEnable() {
        this.resetState();
        this.tickCount = 0;
    }

    @Override
    public void onDisable() {
        if (this.originalSlot >= 0) {
            this.selectSlot(this.originalSlot);
        }
        this.resetState();
    }

    @EventHandler
    public void onTick(EventPreTick event) {
        EntityPlayerSP player = Minecraft.thePlayer();
        if (player == null || player.isNull() || Minecraft.theWorld() == null || Minecraft.theWorld().isNull()) {
            return;
        }
        if (Minecraft.currentScreen() != null && !Minecraft.currentScreen().isNull()) {
            this.abortMacro();
            return;
        }
        ++this.tickCount;
        switch (this.phase) {
            case PHASE_IDLE:
                if (this.shouldTrigger(player)) {
                    this.startMacro(player);
                }
                break;
            case PHASE_SWITCH:
                this.doSwitch(player);
                break;
            case PHASE_EAT:
                this.doEat(player);
                break;
            case PHASE_DONE:
                this.doDone(player);
                break;
            default:
                break;
        }
    }

    private void resetState() {
        this.phase = PHASE_IDLE;
        this.originalSlot = -1;
        this.switchStartTick = -1;
        this.useDoneTick = -1;
        this.nextTriggerTick = 0;
    }

    private boolean shouldTrigger(EntityPlayerSP player) {
        if (player.w$src$F$15l9epb() >= this.health.getValue().doubleValue()) {
            return false;
        }
        if (this.tickCount < this.nextTriggerTick) {
            return false;
        }
        return this.findSoupSlot(player) >= 0;
    }

    private void startMacro(EntityPlayerSP player) {
        int slot = this.findSoupSlot(player);
        if (slot < 0) {
            return;
        }
        InventoryPlayer inventory = player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6();
        if (inventory == null || inventory.isNull()) {
            return;
        }
        this.originalSlot = inventory.v();
        this.selectSlot(slot);
        this.switchStartTick = this.tickCount;
        this.phase = PHASE_SWITCH;
    }

    private void doSwitch(EntityPlayerSP player) {
        if (this.tickCount - this.switchStartTick < 1) {
            return;
        }
        InventoryPlayer inventory = player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6();
        if (inventory == null || inventory.isNull() || !this.isSoup(inventory.c(inventory.v()))) {
            this.abortMacro();
            return;
        }
        if (!player.l$src$Z$1io4duf()) {
            this.useItem();
        }
        this.useDoneTick = this.tickCount;
        this.phase = PHASE_EAT;
    }

    private void doEat(EntityPlayerSP player) {
        boolean eatingDone = !player.l$src$Z$1io4duf() && this.tickCount - this.useDoneTick >= 1;
        boolean timedOut = this.tickCount - this.useDoneTick > EAT_TIMEOUT;
        if (eatingDone || timedOut) {
            this.phase = PHASE_DONE;
        }
    }

    private void doDone(EntityPlayerSP player) {
        if (this.tickCount - this.useDoneTick < REPLACE_DELAY) {
            return;
        }
        int weaponSlot = this.findBestWeaponSlot(player);
        int target = weaponSlot >= 0 ? weaponSlot : this.originalSlot;
        if (target >= 0) {
            this.selectSlot(target);
        }
        this.nextTriggerTick = this.tickCount + RETRIGGER_COOLDOWN;
        this.resetState();
    }

    private void abortMacro() {
        if (this.phase != PHASE_IDLE && this.originalSlot >= 0) {
            this.selectSlot(this.originalSlot);
        }
        this.resetState();
    }

    private void selectSlot(int slot) {
        EntityPlayerSP player = Minecraft.thePlayer();
        if (player == null || player.isNull()) {
            return;
        }
        InventoryPlayer inventory = player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6();
        if (inventory == null || inventory.isNull()) {
            return;
        }
        inventory.g(slot);
        try {
            player.sendQueue().addToSendQueue(CPacketHeldItemChange.create(slot));
        }
        catch (Throwable ignored) {
        }
    }

    private void useItem() {
        try {
            KeyBinding useItemKey = Minecraft.gameSettings().b$src$Lgg_vape_wrapper_impl_KeyBinding_$1yi3362();
            KeyBinding.setKeyBindState(useItemKey, true);
            KeyBinding.onTick(useItemKey);
            KeyBinding.setKeyBindState(useItemKey, false);
        }
        catch (Throwable ignored) {
        }
    }

    private int findSoupSlot(EntityPlayerSP player) {
        InventoryPlayer inventory = player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6();
        if (inventory == null || inventory.isNull()) {
            return -1;
        }
        if (this.isSoup(inventory.c(inventory.v()))) {
            return inventory.v();
        }
        for (int slot = 0; slot < 9; ++slot) {
            if (this.isSoup(inventory.c(slot))) {
                return slot;
            }
        }
        return -1;
    }

    private int findBestWeaponSlot(EntityPlayerSP player) {
        InventoryPlayer inventory = player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6();
        if (inventory == null || inventory.isNull()) {
            return -1;
        }
        int bestSlot = -1;
        double bestDamage = 0.0;
        for (int slot = 0; slot < 9; ++slot) {
            ItemStack stack = inventory.c(slot);
            if (stack == null || stack.isNull()) {
                continue;
            }
            String name = this.itemName(stack);
            double damage;
            if (name.contains("sword")) {
                damage = 7.0;
            }
            else if (name.contains("axe")) {
                damage = 6.0;
            }
            else {
                continue;
            }
            if (damage > bestDamage) {
                bestDamage = damage;
                bestSlot = slot;
            }
        }
        return bestSlot;
    }

    private boolean isSoup(ItemStack stack) {
        return stack != null && !stack.isNull() && this.itemName(stack).contains(SOUP_NAME);
    }

    private String itemName(ItemStack stack) {
        try {
            String name = stack.getItem().A();
            return name == null ? "" : name.toLowerCase();
        }
        catch (Throwable ignored) {
            return "";
        }
    }
}
