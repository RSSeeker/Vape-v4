package gg.vape.module.kiss;

import gg.vape.event.EventHandler;
import gg.vape.event.impl.EventPreAttack;
import gg.vape.mapping.MappedClasses;
import gg.vape.module.Category;
import gg.vape.module.Mod;
import gg.vape.unmap.ModeOption;
import gg.vape.unmap.ModeSelection;
import gg.vape.value.ModeValue;
import gg.vape.wrapper.impl.Entity;
import gg.vape.wrapper.impl.EntityLivingBase;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.Minecraft;

/**
 * Kiss HitSelect (攻击筛选) — cancels a hit when the server would not give it the intended
 * knockback window. Ported from KissClient's risky/HitSelect.java.
 */
public class KissHitSelect
extends Mod {
    private static final int MOD_ID = 1988426057;

    private final ModeOption secondMode = new ModeOption("Second");
    private final ModeOption wtapMode = new ModeOption("WTap");
    private final ModeValue mode = ModeValue.create(this, "Mode",
            "Second - only hit right after the target is hittable again\nWTap - only hit while your forward key is up or you are sprinting",
            this.secondMode, this.secondMode, this.wtapMode);

    private int blockedHits;
    private int allowedHits;

    public KissHitSelect() {
        super("Kiss HitSelect", MOD_ID, Category.KISS,
                "Hit selection ported from KissClient: skips hits that would land in a bad window.");
    }

    @Override
    public void onEnable() {
        this.blockedHits = 0;
        this.allowedHits = 0;
    }

    @Override
    public void onDisable() {
        this.blockedHits = 0;
        this.allowedHits = 0;
    }

    @EventHandler
    public void onPreAttack(EventPreAttack event) {
        EntityPlayerSP player = Minecraft.thePlayer();
        if (player == null || player.isNull() || Minecraft.theWorld() == null || Minecraft.theWorld().isNull()) {
            return;
        }
        Entity hit = event.getTarget();
        if (hit == null || hit.isNull() || hit.getObject() == player.getObject()) {
            return;
        }
        if (!hit.isInstance(MappedClasses.zm)) {
            return;
        }
        EntityLivingBase target = new EntityLivingBase(hit.getObject());
        boolean allow = this.mode.getSelectedIndex() == 1
                ? this.shouldHitWTap(player)
                : this.shouldHitSecond(player, target);
        if (allow) {
            ++this.allowedHits;
            return;
        }
        ++this.blockedHits;
        event.setCancelled(true);
    }

    private boolean shouldHitSecond(EntityPlayerSP player, EntityLivingBase target) {
        if (this.hurtTime(target) != 0) {
            return true;
        }
        if (this.hurtTime(player) <= 9) {
            return true;
        }
        double distance = player.getDistanceToEntity(target);
        if (distance < 2.5) {
            return true;
        }
        return !this.isMovingTowards(target, player, 60.0) || !this.isMovingTowards(player, target, 60.0);
    }

    private boolean shouldHitWTap(EntityPlayerSP player) {
        try {
            boolean forwardDown = Minecraft.gameSettings().Y().isKeyDown();
            return !forwardDown || player.r();
        }
        catch (Throwable ignored) {
            return true;
        }
    }

    private int hurtTime(EntityLivingBase entity) {
        try {
            return entity.c$src$I$15a9iwo();
        }
        catch (Throwable ignored) {
            return 0;
        }
    }

    private boolean isMovingTowards(EntityLivingBase source, EntityLivingBase target, double maxAngle) {
        double moveX = source.z() - source.M();
        double moveZ = source.h() - source.m$src$D$fwnne5();
        double moveLength = Math.sqrt(moveX * moveX + moveZ * moveZ);
        if (moveLength == 0.0) {
            return false;
        }
        double targetX = target.z() - source.z();
        double targetZ = target.h() - source.h();
        double targetLength = Math.sqrt(targetX * targetX + targetZ * targetZ);
        if (targetLength == 0.0) {
            return false;
        }
        double dot = (moveX / moveLength) * (targetX / targetLength)
                + (moveZ / moveLength) * (targetZ / targetLength);
        return dot >= Math.cos(Math.toRadians(maxAngle));
    }

    @Override
    public String getDetailedSuffix() {
        return "B" + this.blockedHits + "/A" + this.allowedHits;
    }
}
