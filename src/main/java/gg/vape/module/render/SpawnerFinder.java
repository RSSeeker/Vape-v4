package gg.vape.module.render;

import gg.vape.config.ClientSettings;
import gg.vape.event.EventHandler;
import gg.vape.event.impl.EventRender3D;
import gg.vape.mapping.MappedClasses;
import gg.vape.module.Category;
import gg.vape.notification.NotificationText;
import gg.vape.module.Mod;
import gg.vape.utils.RotationUtil;
import gg.vape.utils.render.OpenGlBackendHolder;
import gg.vape.utils.render.RenderUtil;
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
import java.awt.Color;
import org.lwjgl.opengl.GL11;

public class SpawnerFinder
extends Mod {
    private final OptionalLimitValue spawnerWhitelist;
    private final Color backgroundColor = new Color(20, 20, 20, 128);
    private final BooleanValue showDistance;
    private final NumberValue scale = NumberValue.create((Object)this, "Scale", "#.#", "", 0.1, 1.0, 1.5, 0.1);
    private static final long moduleId = -5031384321297466627L;

    private static String normalizeEntityName(String entityName) {
        if (entityName == null) {
            return "";
        }
        int colonIndex = entityName.lastIndexOf(':');
        if (colonIndex >= 0 && colonIndex < entityName.length() - 1) {
            return entityName.substring(colonIndex + 1);
        }
        return entityName;
    }

    private boolean matchesSpawner(String spawnerName) {
        if (spawnerName == null) {
            return false;
        }
        if (this.spawnerWhitelist.getValue().isEmpty()) {
            return true;
        }
        if (this.spawnerWhitelist.matches(spawnerName, false)) {
            return true;
        }
        String normalized = SpawnerFinder.normalizeEntityName(spawnerName);
        if (this.spawnerWhitelist.matches(normalized, false)) {
            return true;
        }
        for (String entry : this.spawnerWhitelist.getEnabledValues()) {
            if (entry == null || entry.isEmpty()) continue;
            String entryName = SpawnerFinder.normalizeEntityName(entry);
            String descriptionKey = "entity.minecraft." + entryName.toLowerCase();
            if (descriptionKey.equalsIgnoreCase(spawnerName) || descriptionKey.equalsIgnoreCase(normalized)) {
                return true;
            }
            if (("minecraft:" + entryName.toLowerCase()).equalsIgnoreCase(spawnerName)) {
                return true;
            }
            String localizedEntry = I18n.format(descriptionKey, new Object[0]);
            if (localizedEntry.equalsIgnoreCase(spawnerName) || localizedEntry.equalsIgnoreCase(normalized)) {
                return true;
            }
        }
        return false;
    }


    public SpawnerFinder() {
        super("SpawnerFinder", (int)moduleId, Category.RENDER);
        this.showDistance = BooleanValue.create(this, "Show distance", true);
        this.spawnerWhitelist = OptionalLimitValue.create(this, "Spawner names-whitelist", "Spawners", OptionalLimitValue.ALLOW_LIST_COLOR, "Zombie", "Skeleton", "Creeper", "Spider");
        this.addValue(this.scale, this.showDistance, this.spawnerWhitelist);
    }

    @EventHandler
    public void onRender3D(EventRender3D eventRender3D) {
        String spawnerLabel = NotificationText.localize("Spawner");
        boolean bl = GL11.glIsEnabled((int)3042);
        if (bl) {
            Minecraft.m$src$Lgg_vape_wrapper_impl_EntityRenderer_$13begmf().B(1.0);
            RenderUtil.d();
            GL11.glBlendFunc((int)770, (int)771);
            OpenGlBackendHolder.backend.setLineWidth(1.5f);
            OpenGlBackendHolder.backend.enableCapability(2848);
            OpenGlBackendHolder.backend.disableCapability(2929);
            GL11.glDepthMask((boolean)false);
            double d = RenderManager.getInterpolatedRenderPosX();
            double d2 = RenderManager.getInterpolatedRenderPosY();
            double d3 = RenderManager.getInterpolatedRenderPosZ();
            EntityPlayerSP entityPlayerSP = Minecraft.thePlayer();
            MatrixStack matrixStack = ForgeVersion.MC_1_16_5.d() ? MatrixStack.A() : null;
            java.util.List tileEntities = Minecraft.theWorld().R$src$Ljava_util_List_$1ycbpra();
            for (Object e : tileEntities) {
                TileEntityMobSpawner tileEntityMobSpawner;
                String string;
                if (!MappedClasses.MOB_SPAWNER_TILE_ENTITY.isInstance(e) || !this.matchesSpawner(string = (tileEntityMobSpawner = new TileEntityMobSpawner(e)).getSpawnerBaseLogic().getEntityName())) continue;
                String string2 = "";
                if (this.showDistance.getEffectiveValue().booleanValue()) {
                    String string3 = ClientSettings.FORMAT_CODE + "a[" + ClientSettings.FORMAT_CODE + "f" + (int)entityPlayerSP.i((double)tileEntityMobSpawner.getX(), (double)tileEntityMobSpawner.getY(), (double)tileEntityMobSpawner.getZ()) + ClientSettings.FORMAT_CODE + "a]" + ClientSettings.FORMAT_CODE + "r";
                    string2 = string2 + string3 + " ";
                }
                string2 = string2 + string + " " + spawnerLabel;
                RenderUtil.U(string2, (double)tileEntityMobSpawner.getX() - d + 0.5, (double)tileEntityMobSpawner.getY() - d2 - 1.0, (double)tileEntityMobSpawner.getZ() - d3 + 0.5, (Double)this.scale.getValue(), RotationUtil.p(entityPlayerSP, tileEntityMobSpawner.getX(), tileEntityMobSpawner.getY(), tileEntityMobSpawner.getZ()), -1, this.backgroundColor, 1.4, matrixStack);
            }
            GL11.glDepthMask((boolean)true);
            OpenGlBackendHolder.backend.enableCapability(2929);
            OpenGlBackendHolder.backend.enableCapability(3553);
            OpenGlBackendHolder.backend.disableCapability(2848);
            OpenGlBackendHolder.backend.popMatrix();
            Minecraft.m$src$Lgg_vape_wrapper_impl_EntityRenderer_$13begmf().O(1.0);
            return;
        }
        Minecraft.m$src$Lgg_vape_wrapper_impl_EntityRenderer_$13begmf().B(1.0);
        RenderUtil.d();
        OpenGlBackendHolder.backend.enableCapability(3042);
        GL11.glBlendFunc((int)770, (int)771);
        OpenGlBackendHolder.backend.setLineWidth(1.5f);
        OpenGlBackendHolder.backend.enableCapability(2848);
        OpenGlBackendHolder.backend.disableCapability(2929);
        GL11.glDepthMask((boolean)false);
        double d = RenderManager.getInterpolatedRenderPosX();
        double d4 = RenderManager.getInterpolatedRenderPosY();
        double d5 = RenderManager.getInterpolatedRenderPosZ();
        EntityPlayerSP entityPlayerSP = Minecraft.thePlayer();
        MatrixStack matrixStack = ForgeVersion.MC_1_16_5.d() ? MatrixStack.A() : null;
        for (Object e : Minecraft.theWorld().R$src$Ljava_util_List_$1ycbpra()) {
            TileEntityMobSpawner tileEntityMobSpawner;
            String string;
            if (!MappedClasses.MOB_SPAWNER_TILE_ENTITY.isInstance(e) || !this.matchesSpawner(string = (tileEntityMobSpawner = new TileEntityMobSpawner(e)).getSpawnerBaseLogic().getEntityName())) continue;
            String string4 = "";
            if (this.showDistance.getEffectiveValue().booleanValue()) {
                String string5 = ClientSettings.FORMAT_CODE + "a[" + ClientSettings.FORMAT_CODE + "f" + (int)entityPlayerSP.i((double)tileEntityMobSpawner.getX(), (double)tileEntityMobSpawner.getY(), (double)tileEntityMobSpawner.getZ()) + ClientSettings.FORMAT_CODE + "a]" + ClientSettings.FORMAT_CODE + "r";
                string4 = string4 + string5 + " ";
            }
            string4 = string4 + string + " " + spawnerLabel;
            RenderUtil.U(string4, (double)tileEntityMobSpawner.getX() - d + 0.5, (double)tileEntityMobSpawner.getY() - d4 - 1.0, (double)tileEntityMobSpawner.getZ() - d5 + 0.5, (Double)this.scale.getValue(), RotationUtil.p(entityPlayerSP, tileEntityMobSpawner.getX(), tileEntityMobSpawner.getY(), tileEntityMobSpawner.getZ()), -1, this.backgroundColor, 1.4, matrixStack);
        }
        GL11.glDepthMask((boolean)true);
        OpenGlBackendHolder.backend.enableCapability(2929);
        OpenGlBackendHolder.backend.enableCapability(3553);
        OpenGlBackendHolder.backend.disableCapability(2848);
        OpenGlBackendHolder.backend.disableCapability(3042);
        OpenGlBackendHolder.backend.popMatrix();
        Minecraft.m$src$Lgg_vape_wrapper_impl_EntityRenderer_$13begmf().O(1.0);
    }
}

