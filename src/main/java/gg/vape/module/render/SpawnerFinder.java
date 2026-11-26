package gg.vape.module.render;

import gg.vape.config.ClientSettings;
import gg.vape.event.EventHandler;
import gg.vape.event.impl.EventRender3D;
import gg.vape.mapping.MappedClasses;
import gg.vape.module.Category;
import gg.vape.module.Mod;
import gg.vape.module.render.spawnerfinder.SpawnerNameMatcher;
import gg.vape.notification.NotificationText;
import gg.vape.utils.RotationUtil;
import gg.vape.utils.render.OpenGlBackendHolder;
import gg.vape.utils.render.RenderUtil;
import gg.vape.utils.render.WorldOverlayGlState;
import gg.vape.value.BooleanValue;
import gg.vape.value.NumberValue;
import gg.vape.value.OptionalLimitValue;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.ForgeVersion;
import gg.vape.wrapper.impl.I18n;
import gg.vape.wrapper.impl.MatrixStack;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.RenderManager;
import gg.vape.wrapper.impl.TileEntityMobSpawner;
import gg.vape.wrapper.impl.WorldClient;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.opengl.GL11;

public class SpawnerFinder extends Mod {
    private static final long MODULE_ID = -5031384321297466627L;
    private final OptionalLimitValue spawnerWhitelist;
    private final Color backgroundColor = new Color(20, 20, 20, 128);
    private final BooleanValue showDistance, showCoordinates, showUnknown;
    private final NumberValue scale, range;

    public SpawnerFinder() {
        super("SpawnerFinder", (int)MODULE_ID, Category.RENDER);
        this.scale = NumberValue.create(this, "Scale", "#.#", "", 0.1, 1.0, 1.5, 0.1);
        this.range = NumberValue.create(this, "Range", "#", "", 1.0, 512.0, 1024.0, 1.0,
                "Maximum distance to loaded spawners");
        this.showDistance = BooleanValue.create(this, "Show distance", true);
        this.showCoordinates = BooleanValue.create(this, "Show coordinates", false, "Show the spawner's block coordinates");
        this.showUnknown = BooleanValue.create(this, "Show unknown", true,
                "Show spawners without a known mob name even when a whitelist is set");
        this.spawnerWhitelist = OptionalLimitValue.create(this, "Spawner names-whitelist", "Spawners",
                OptionalLimitValue.ALLOW_LIST_COLOR, "Zombie", "Skeleton", "Creeper", "Spider");
        this.addValue(this.scale, this.range, this.showDistance, this.showCoordinates, this.showUnknown, this.spawnerWhitelist);
    }

    private List<Target> collectTargets(WorldClient world, EntityPlayerSP player) {
        List<Target> targets = new ArrayList<Target>();
        if (MappedClasses.MOB_SPAWNER_TILE_ENTITY == null) return targets;
        List<String> enabledNames = this.spawnerWhitelist.getEnabledValues();
        boolean emptyList = this.spawnerWhitelist.getValue().isEmpty();
        double configuredRange = this.range.getValue();
        double maxDistance = Double.isNaN(configuredRange) ? 512.0 : Math.max(1.0, Math.min(1024.0, configuredRange));
        for (Object handle : world.R$src$Ljava_util_List_$1ycbpra()) {
            if (!MappedClasses.MOB_SPAWNER_TILE_ENTITY.isInstance(handle)) continue;
            try {
                TileEntityMobSpawner tile = new TileEntityMobSpawner(handle);
                int x = tile.getX(), y = tile.getY(), z = tile.getZ();
                double distance = RotationUtil.p(player, x, y, z);
                if (distance > maxDistance) continue;
                String name;
                try {
                    name = tile.getSpawnerBaseLogic().getEntityName();
                } catch (Exception ignored) {
                    name = "";
                }
                boolean unknown = name == null || name.trim().isEmpty();
                if (unknown) {
                    if (!this.showUnknown.getEffectiveValue()) continue;
                    name = NotificationText.localize("Unknown");
                } else if (!SpawnerNameMatcher.matches(name, emptyList, enabledNames,
                        key -> I18n.format(key, new Object[0]))) {
                    continue;
                }
                targets.add(new Target(x, y, z, distance, name));
            } catch (Exception ignored) {
                // A single invalid block entity should not suppress other spawners.
            }
        }
        return targets;
    }

    @EventHandler
    public void onRender3D(EventRender3D event) {
        WorldClient world = event.getWorld();
        EntityPlayerSP player = event.getThePlayer();
        if (world == null || world.isNull() || player == null || player.isNull()) return;
        List<Target> targets = this.collectTargets(world, player);
        if (targets.isEmpty()) return;
        String spawnerLabel = NotificationText.localize("Spawner");
        WorldOverlayGlState glState = WorldOverlayGlState.capture();
        boolean rendererStarted = false;
        boolean labelAttempted = false;
        OpenGlBackendHolder.backend.pushMatrix();
        try {
            if (ForgeVersion.MC_1_16_5.d()) RenderUtil.p();
            Minecraft.m$src$Lgg_vape_wrapper_impl_EntityRenderer_$13begmf().B(1.0);
            rendererStarted = true;
            GL11.glBlendFunc(770, 771);
            OpenGlBackendHolder.backend.setLineWidth(1.5f);
            OpenGlBackendHolder.backend.enableCapability(2848);
            double renderX = RenderManager.getInterpolatedRenderPosX();
            double renderY = RenderManager.getInterpolatedRenderPosY();
            double renderZ = RenderManager.getInterpolatedRenderPosZ();
            MatrixStack matrix = ForgeVersion.MC_1_16_5.d() ? MatrixStack.A() : null;
            for (Target target : targets) {
                // RenderUtil.U changes these states as it draws each label.
                OpenGlBackendHolder.backend.enableCapability(3042);
                OpenGlBackendHolder.backend.disableCapability(2929);
                OpenGlBackendHolder.backend.setDepthMask(false);
                String label = "";
                if (this.showDistance.getEffectiveValue()) {
                    label += ClientSettings.FORMAT_CODE + "a[" + ClientSettings.FORMAT_CODE + "f"
                            + (int)target.distance + ClientSettings.FORMAT_CODE + "a]" + ClientSettings.FORMAT_CODE + "r ";
                }
                label += target.name + " " + spawnerLabel;
                if (this.showCoordinates.getEffectiveValue()) {
                    label += " (" + target.x + ", " + target.y + ", " + target.z + ")";
                }
                labelAttempted = true;
                RenderUtil.U(label, target.x - renderX + 0.5, target.y - renderY - 1.0,
                        target.z - renderZ + 0.5, this.scale.getValue(), target.distance,
                        -1, this.backgroundColor, 1.4, matrix);
            }
        } finally {
            try {
                if (rendererStarted) Minecraft.m$src$Lgg_vape_wrapper_impl_EntityRenderer_$13begmf().O(1.0);
            } finally {
                try {
                    RenderUtil.Y();
                } finally {
                    glState.restore(labelAttempted);
                }
            }
        }
    }

    private static final class Target {
        private final int x, y, z;
        private final double distance;
        private final String name;
        private Target(int x, int y, int z, double distance, String name) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.distance = distance;
            this.name = name;
        }
    }
}
