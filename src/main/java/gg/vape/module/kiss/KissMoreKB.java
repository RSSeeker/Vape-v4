package gg.vape.module.kiss;

import gg.vape.config.ClientSettings;
import gg.vape.event.EventHandler;
import gg.vape.event.impl.EventPreAttack;
import gg.vape.event.impl.EventPreTick;
import gg.vape.mapping.MappedClasses;
import gg.vape.module.Category;
import gg.vape.module.Mod;
import gg.vape.unmap.ModeOption;
import gg.vape.unmap.ModeSelection;
import gg.vape.utils.MathUtil;
import gg.vape.value.BooleanValue;
import gg.vape.value.ModeValue;
import gg.vape.wrapper.impl.Entity;
import gg.vape.wrapper.impl.EntityLivingBase;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.KeyBinding;
import gg.vape.wrapper.impl.Minecraft;

/**
 * Kiss MoreKB (击退增强) — resets the sprint right after a hit so the server computes more knockback.
 * Ported from KissClient's risky/MoreKB.java (LEGIT / LEGIT_FAST / WTAP).
 */
public class KissMoreKB
extends Mod {
    private static final int MOD_ID = -1803765047;

    private final ModeOption legitMode = new ModeOption("Legit");
    private final ModeOption legitFastMode = new ModeOption("Legit Fast");
    private final ModeOption wtapMode = new ModeOption("WTap");
    private final ModeValue mode = ModeValue.create(this, "Mode",
            "Legit - sprint reset on every hit\nLegit Fast - sprint reset every tick while moving\nWTap - sprint reset and keep the forward key up for two ticks",
            this.legitMode, this.legitMode, this.legitFastMode, this.wtapMode);
    private final BooleanValue intelligent = BooleanValue.create(this, "Intelligent", false,
            "Skip the sprint reset when the target is not in front of you");
    private final BooleanValue onlyGround = BooleanValue.create(this, "Only Ground", true,
            "Only reset the sprint while standing on the ground");

    private EntityLivingBase target;
    private boolean rePressPending;
    private int wtapTicksLeft;

    public KissMoreKB() {
        super("Kiss MoreKB", MOD_ID, Category.KISS,
                "Knockback boost ported from KissClient: sprint resets on hit (Legit / Legit Fast / WTap).");
    }

    @Override
    public void onEnable() {
        this.target = null;
        this.rePressPending = false;
        this.wtapTicksLeft = 0;
    }

    @Override
    public void onDisable() {
        this.target = null;
        this.rePressPending = false;
        this.wtapTicksLeft = 0;
        this.restoreSprintKey();
    }

    @EventHandler
    public void onPreAttack(EventPreAttack event) {
        if (this.mode.getSelectedIndex() == 1) {
            return;
        }
        EntityPlayerSP player = Minecraft.thePlayer();
        if (player == null || player.isNull() || Minecraft.theWorld() == null || Minecraft.theWorld().isNull()) {
            return;
        }
        Entity hit = event.getTarget();
        if (hit == null || hit.isNull() || !hit.isInstance(MappedClasses.zm) || hit.getObject() == player.getObject()) {
            return;
        }
        if (this.intelligent.getEffectiveValue().booleanValue()
                && this.yawTooLarge(player, hit)) {
            return;
        }
        this.target = new EntityLivingBase(hit.getObject());
        if (this.mode.getSelectedIndex() == 2) {
            this.wtapTicksLeft = 2;
        }
        this.resetSprint(player);
    }

    @EventHandler
    public void onTick(EventPreTick event) {
        EntityPlayerSP player = Minecraft.thePlayer();
        if (player == null || player.isNull()) {
            return;
        }
        if (this.rePressPending) {
            KeyBinding sprintKey = Minecraft.gameSettings().r();
            if (ClientSettings.isPhysicalKeyDown(sprintKey)) {
                KeyBinding.setKeyBindState(sprintKey, true);
            }
            this.rePressPending = false;
        }
        if (this.wtapTicksLeft > 0) {
            --this.wtapTicksLeft;
            if (this.wtapTicksLeft == 0) {
                this.restoreSprintKey();
            }
            return;
        }
        if (this.mode.getSelectedIndex() != 1 || this.target == null || this.target.isNull()) {
            return;
        }
        boolean onGround = player.h$src$Z$ftwoya();
        if (this.onlyGround.getEffectiveValue().booleanValue() && !onGround) {
            return;
        }
        if (player.r()) {
            this.resetSprint(player);
        }
        this.target = null;
    }

    private void resetSprint(EntityPlayerSP player) {
        try {
            KeyBinding sprintKey = Minecraft.gameSettings().r();
            if (sprintKey.isKeyDown()) {
                KeyBinding.setKeyBindState(sprintKey, false);
                this.rePressPending = true;
            }
        }
        catch (Throwable ignored) {
        }
    }

    private void restoreSprintKey() {
        try {
            KeyBinding sprintKey = Minecraft.gameSettings().r();
            KeyBinding.setKeyBindState(sprintKey, ClientSettings.isPhysicalKeyDown(sprintKey));
        }
        catch (Throwable ignored) {
        }
    }

    private boolean yawTooLarge(EntityPlayerSP player, Entity entity) {
        double dx = player.z() - entity.z();
        double dz = player.h() - entity.h();
        float calcYaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float diff = Math.abs(MathUtil.wrapAngleTo180(calcYaw - entity.J()));
        return diff > 120.0f;
    }

    @Override
    public String getDetailedSuffix() {
        int index = this.mode.getSelectedIndex();
        return index == 1 ? "Legit Fast" : index == 2 ? "WTap" : "Legit";
    }
}
