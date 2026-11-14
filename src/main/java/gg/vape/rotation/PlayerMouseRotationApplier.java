package gg.vape.rotation;

import gg.vape.Vape;
import gg.vape.module.render.hud.FreeLookHudModule;
import gg.vape.rotation.RotationManager;
import gg.vape.wrapper.impl.EntityPlayer;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.Minecraft;

public class PlayerMouseRotationApplier {
    public static void applyMouseDelta(EntityPlayer player, float yawDelta, float pitchDelta) {
        float previousPitch = player.V();
        float previousYaw = player.J();
        player.H((float)((double)player.J() + (double)yawDelta * 0.15));
        player.C((float)((double)player.V() - (double)pitchDelta * 0.15));
        if (player.V() < -90.0f) {
            player.C(-90.0f);
        }
        if (player.V() > 90.0f) {
            player.C(90.0f);
        }
        player.l(player.D() + player.V() - previousPitch);
        player.D(player.j() + player.J() - previousYaw);
        player.z(player.J());
        player.o(player.s());
        PlayerMouseRotationApplier.syncFreeLookRotation();
    }

    private static void syncFreeLookRotation() {
        if (FreeLookHudModule.isActive()) {
            return;
        }
        FreeLookHudModule freeLook = Vape.INSTANCE.getModManager().getMod(FreeLookHudModule.class);
        EntityPlayerSP player = Minecraft.thePlayer();
        freeLook.capturePlayerRotation(player.J(), player.V());
    }

    public static void setLocalPlayerRotation(float yaw, float pitch) {
        EntityPlayerSP player = Minecraft.thePlayer();
        float previousPitch = player.V();
        float previousYaw = player.J();
        player.H(yaw);
        player.C(pitch);
        if (player.V() < -90.0f) {
            player.C(-90.0f);
        }
        if (player.V() > 90.0f) {
            player.C(90.0f);
        }
        player.l(player.D() + player.V() - previousPitch);
        player.D(player.j() + player.J() - previousYaw);
    }

    public static void applyTrackedMouseDelta(float yawDelta, float pitchDelta) {
        EntityPlayerSP player = Minecraft.thePlayer();
        float trackedPitch = RotationManager.getViewPitch(player);
        float trackedYaw = RotationManager.getViewYaw(player);
        player.H((float)((double)trackedYaw + (double)yawDelta * 0.15));
        player.C((float)((double)trackedPitch - (double)pitchDelta * 0.15));
        if (player.V() < -90.0f) {
            player.C(-90.0f);
        }
        if (player.V() > 90.0f) {
            player.C(90.0f);
        }
        player.l(player.D() + player.V() - trackedPitch);
        player.D(player.j() + player.J() - trackedYaw);
        PlayerMouseRotationApplier.syncFreeLookRotation();
    }

    public static void applyLocalMouseDelta(float yawDelta, float pitchDelta) {
        EntityPlayerSP player = Minecraft.thePlayer();
        float previousPitch = player.V();
        float previousYaw = player.J();
        player.H((float)((double)player.J() + (double)yawDelta * 0.15));
        player.C((float)((double)player.V() - (double)pitchDelta * 0.15));
        if (player.V() < -90.0f) {
            player.C(-90.0f);
        }
        if (player.V() > 90.0f) {
            player.C(90.0f);
        }
        player.l(player.D() + player.V() - previousPitch);
        player.D(player.j() + player.J() - previousYaw);
    }

}
