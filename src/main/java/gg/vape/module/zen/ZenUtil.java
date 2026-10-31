package gg.vape.module.zen;

import gg.vape.wrapper.impl.EntityLivingBase;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.World;

/** Small shared helpers for the Zen-ported modules. */
final class ZenUtil {
    private ZenUtil() {
    }

    static World world() {
        try {
            World world = Minecraft.theWorld();
            return world == null || world.isNull() ? null : world;
        }
        catch (Throwable ignored) {
            return null;
        }
    }

    static EntityPlayerSP player() {
        try {
            EntityPlayerSP player = Minecraft.thePlayer();
            return player == null || player.isNull() ? null : player;
        }
        catch (Throwable ignored) {
            return null;
        }
    }

    static int hurtTime(EntityLivingBase entity) {
        try {
            return entity.c$src$I$15a9iwo();
        }
        catch (Throwable ignored) {
            return 0;
        }
    }

    static float wrapAngle(float angle) {
        angle = angle % 360.0f;
        if (angle >= 180.0f) {
            angle -= 360.0f;
        }
        if (angle < -180.0f) {
            angle += 360.0f;
        }
        return angle;
    }
}
