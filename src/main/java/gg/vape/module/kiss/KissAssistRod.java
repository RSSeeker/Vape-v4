package gg.vape.module.kiss;

import gg.vape.event.EventHandler;
import gg.vape.event.impl.EventPreTick;
import gg.vape.mapping.MappedClasses;
import gg.vape.module.Category;
import gg.vape.module.Mod;
import gg.vape.module.utility.inventory.ItemStackActionPredicate;
import gg.vape.module.utility.mlg.MLGImpactState;
import gg.vape.value.BooleanValue;
import gg.vape.value.NumberValue;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.InventoryPlayer;
import gg.vape.wrapper.impl.ItemStack;
import gg.vape.wrapper.impl.KeyBinding;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.Slot;
import gg.vape.wrapper.impl.World;

/**
 * Kiss AssistRod (辅助鱼竿) — one key runs a whole fishing cycle:
 * switch to the rod, cast, wait {@code Retract Delay}, reel in, switch back.
 * Ported from KissClient's world/AssistRod.java.
 */
public class KissAssistRod
extends Mod {
    private static final int MOD_ID = -1207788554;
    private static final int PHASE_IDLE = 0;
    private static final int PHASE_SWITCH = 1;
    private static final int PHASE_USE = 2;
    private static final int PHASE_RETRACT = 3;
    private static final int PHASE_DONE = 4;

    private final NumberValue switchDelay = NumberValue.create(this, "Switch Delay", "#", " ticks", 1.0, 2.0, 10.0, 1.0);
    private final NumberValue retractDelay = NumberValue.create(this, "Retract Delay", "#", " ticks", 0.0, 20.0, 40.0, 1.0);
    private final BooleanValue switchBack = BooleanValue.create(this, "Switch Back", true,
            "Switch back to the original hotbar slot after reeling in");
    private int phase = PHASE_IDLE;
    private int originalSlot = -1;
    private int tickCount;
    private int phaseTick;

    public KissAssistRod() {
        super("Kiss AssistRod", MOD_ID, Category.KISS,
                "Fishing rod macro ported from KissClient: switch to the rod, cast, wait, reel in and switch back.");
    }

    @Override
    public void onEnable() {
        this.resetState();
        this.startCycle();
    }

    @Override
    public void onDisable() {
        this.finishCycle();
        this.resetState();
    }

    @EventHandler
    public void onTick(EventPreTick event) {
        EntityPlayerSP player = Minecraft.thePlayer();
        World world = Minecraft.theWorld();
        if (player == null || player.isNull() || world == null || world.isNull()) {
            this.resetState();
            return;
        }
        ++this.tickCount;
        switch (this.phase) {
            case PHASE_SWITCH:
                if (this.tickCount - this.phaseTick >= this.switchDelay.getValue().intValue()) {
                    this.phase = PHASE_USE;
                }
                break;
            case PHASE_USE:
                this.useItem();
                this.phaseTick = this.tickCount;
                this.phase = PHASE_RETRACT;
                break;
            case PHASE_RETRACT:
                if (this.tickCount - this.phaseTick >= this.retractDelay.getValue().intValue()) {
                    this.useItem();
                    this.phaseTick = this.tickCount;
                    this.phase = PHASE_DONE;
                }
                break;
            case PHASE_DONE:
                if (this.tickCount - this.phaseTick >= 2) {
                    this.finishCycle();
                    this.resetState();
                    this.setEnabled(false);
                }
                break;
            default:
                break;
        }
    }

    private void startCycle() {
        EntityPlayerSP player = Minecraft.thePlayer();
        if (player == null || player.isNull()) {
            return;
        }
        InventoryPlayer inventory = player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6();
        if (inventory == null || inventory.isNull()) {
            return;
        }
        int slot = this.findRodSlot(player);
        if (slot < 0) {
            this.setEnabled(false);
            return;
        }
        this.originalSlot = inventory.v();
        this.switchSlot(inventory, slot);
        this.phase = PHASE_SWITCH;
        this.phaseTick = this.tickCount;
    }

    private void finishCycle() {
        if (!this.switchBack.getEffectiveValue().booleanValue() || this.originalSlot < 0) {
            return;
        }
        EntityPlayerSP player = Minecraft.thePlayer();
        if (player == null || player.isNull()) {
            return;
        }
        InventoryPlayer inventory = player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6();
        if (inventory == null || inventory.isNull()) {
            return;
        }
        this.switchSlot(inventory, this.originalSlot);
    }

    private void switchSlot(InventoryPlayer inventory, int slot) {
        if (inventory.v() == slot) {
            return;
        }
        inventory.g(slot);
    }

    private void useItem() {
        try {
            KeyBinding useItemKey = Minecraft.gameSettings().b$src$Lgg_vape_wrapper_impl_KeyBinding_$1yi3362();
            if (useItemKey.u() || useItemKey.isPressed()) {
                KeyBinding.setKeyBindState(useItemKey, false);
            }
            KeyBinding.setKeyBindState(useItemKey, true);
            KeyBinding.onTick(useItemKey);
            KeyBinding.setKeyBindState(useItemKey, false);
        } catch (Throwable ignored) {
        }
    }

    private int findRodSlot(EntityPlayerSP player) {
        ItemStack held = player.getHeldItemHand();
        if (held != null && !held.isNull() && held.getItem().isInstance(MappedClasses.Yi)) {
            return player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6().v();
        }
        Slot slot = ItemStackActionPredicate.findSlotByItemClass(MappedClasses.Yi, MLGImpactState.HOTBAR);
        if (slot == null || slot.isNull()) {
            return -1;
        }
        int hotbarIndex = slot.getSlotNumber() - 36;
        return hotbarIndex < 0 || hotbarIndex > 8 ? -1 : hotbarIndex;
    }

    private void resetState() {
        this.phase = PHASE_IDLE;
        this.originalSlot = -1;
        this.phaseTick = 0;
    }
}
