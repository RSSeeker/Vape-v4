package gg.vape.module.kiss;

import gg.vape.Vape;
import gg.vape.event.EventHandler;
import gg.vape.event.impl.EventPreAttack;
import gg.vape.event.impl.EventPreTick;
import gg.vape.module.Category;
import gg.vape.module.Mod;
import gg.vape.unmap.ModeOption;
import gg.vape.unmap.ModeSelection;
import gg.vape.value.ModeValue;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.KeyBinding;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.MovementInput;
import gg.vape.wrapper.impl.Packet;

/**
 * Kiss Critical (刀爆) — Legit holds the movement input until the fall gives a critical hit,
 * Risky sends a fake position hop right before the attack so the server sees the crit.
 * Ported from KissClient's risky/Critical.java.
 */
public class KissCritical
extends Mod {
    private static final int MOD_ID = -447130265;

    private final ModeOption legitMode = new ModeOption("Legit");
    private final ModeOption riskyMode = new ModeOption("Risky");
    private final ModeValue mode = ModeValue.create(this, "Mode",
            "Legit - stop moving in mid air after a hit (client side only)\nRisky - send two position packets before the hit (server side crit)",
            this.legitMode, this.legitMode, this.riskyMode);

    private boolean critActive;
    private boolean hitKnocked;

    public KissCritical() {
        super("Kiss Critical", MOD_ID, Category.KISS,
                "Critical hits ported from KissClient: Legit movement freeze or Risky position packet hop.");
    }

    @Override
    public void onEnable() {
        this.reset();
    }

    @Override
    public void onDisable() {
        this.reset();
    }

    private void reset() {
        this.critActive = false;
        this.hitKnocked = false;
    }

    @EventHandler
    public void onTick(EventPreTick event) {
        EntityPlayerSP player = Minecraft.thePlayer();
        if (player == null || player.isNull() || Minecraft.theWorld() == null || Minecraft.theWorld().isNull()) {
            return;
        }
        if (this.mode.getSelectedIndex() != 0) {
            return;
        }
        boolean onGround = player.h$src$Z$ftwoya();
        if (player.i() > 0) {
            this.hitKnocked = true;
        }
        else if (onGround) {
            this.hitKnocked = false;
        }
        boolean falling = !onGround && player.q() < 0.0 && player.getFallDistance() > 0.0f;
        if (this.hitKnocked && falling) {
            this.critActive = true;
            try {
                this.setSprinting(player, false);
                MovementInput input = player.movementInput();
                if (input != null && !input.isNull()) {
                    input.B(0.0f);
                }
            }
            catch (Throwable ignored) {
            }
        }
        else if (onGround && this.critActive) {
            this.critActive = false;
        }
    }

    @EventHandler
    public void onPreAttack(EventPreAttack event) {
        EntityPlayerSP player = Minecraft.thePlayer();
        if (player == null || player.isNull() || Minecraft.theWorld() == null || Minecraft.theWorld().isNull()) {
            return;
        }
        if (this.mode.getSelectedIndex() != 1) {
            return;
        }
        if (!player.h$src$Z$ftwoya()) {
            return;
        }
        try {
            double x = player.z();
            double y = player.N();
            double z = player.h();
            this.sendPosition(player, x, y + 0.42, z);
            this.sendPosition(player, x, y, z);
        }
        catch (Throwable ignored) {
        }
    }

    private void sendPosition(EntityPlayerSP player, double x, double y, double z) {
        try {
            Object handle = Vape.INSTANCE.getMappings().R5.createPositionPacket(x, y, z, false);
            if (handle != null) {
                player.sendQueue().addToSendQueue(new Packet(handle));
            }
        }
        catch (Throwable ignored) {
        }
    }

    private void setSprinting(EntityPlayerSP player, boolean sprinting) {
        KeyBinding sprintKey = Minecraft.gameSettings().r();
        KeyBinding.setKeyBindState(sprintKey, sprinting);
    }

    @Override
    public String getDetailedSuffix() {
        return this.mode.getSelectedIndex() == 1 ? "Risky" : "Legit";
    }
}
