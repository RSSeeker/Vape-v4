package gg.vape.module.render;

import com.google.common.collect.Lists;
import gg.vape.Vape;
import gg.vape.config.ClientSettings;
import gg.vape.event.EventHandler;
import gg.vape.event.impl.EventPreRenderEntity;
import gg.vape.event.impl.EventPreRenderLiving;
import gg.vape.event.impl.EventPreRenderPlayerSpec;
import gg.vape.event.impl.EventRenderPlayerPost;
import gg.vape.event.impl.EventRenderPlayerPre;
import gg.vape.event.impl.EventSetArmorModel;
import gg.vape.mapping.MappedClasses;
import gg.vape.module.Category;
import gg.vape.module.Mod;
import gg.vape.utils.render.OpenGlBackendHolder;
import gg.vape.utils.render.RenderUtil;
import gg.vape.utils.render.RenderUtils;
import gg.vape.value.BooleanValue;
import gg.vape.value.ColorValue;
import gg.vape.value.Value;
import gg.vape.wrapper.impl.Entity;
import gg.vape.wrapper.impl.EntityLivingBase;
import gg.vape.wrapper.impl.EntityPlayer;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.ForgeVersion;
import gg.vape.wrapper.impl.GlStateManager;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.Tessellator;
import java.awt.Color;
import java.util.ArrayList;
import org.lwjgl.opengl.GL11;

public class Chams
extends Mod {
    private static final int PASS_NONE = -1;
    private static final int PASS_OCCLUDED = 1;
    private static final int PASS_VISIBLE = 2;
    private static final int PASS_LEGACY = 3;
    private boolean renderingChamsPass;
    private EntityLivingBase temporarilyInvisiblePlayer;
    private final ColorValue occludedColor;
    private final ColorValue visibleColor;
    private final BooleanValue colored;
    private final BooleanValue colorBehindWalls;
    private final BooleanValue hideBots = BooleanValue.create(this, "Hide Bots", false, "Doesn't apply chams on bots.");
    private int renderPass = PASS_NONE;

    @Override
    public void onEnable() {
        if (ForgeVersion.MC_1_21_11.d()) {
            gg.vape.debug.ChamsRenderHooks.warmUp();
            gg.vape.debug.ChamsRenderHooks.enabled = true;
            gg.vape.debug.ChamsModernProbe.runOnce();
        }
    }

    @Override
    public void onDisable() {
        gg.vape.debug.ChamsRenderHooks.enabled = false;
        gg.vape.debug.ChamsRenderHooks.restorePipelines();
    }

    @EventHandler
    public void onPreRenderLiving(EventPreRenderLiving event) {
        if (!this.renderingChamsPass) {
            return;
        }
        if (event.getWorld().isNull()) {
            return;
        }
        EntityPlayerSP viewer = event.getThePlayer();
        Entity entity = event.getEntity();
        if (entity.equals(viewer)) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onPreRenderEntity(EventPreRenderEntity event) {
        if (event.getEntity().isInstance(MappedClasses.Yl) && !event.getEntity().isInstance(MappedClasses.z5) && this.colored.getEffectiveValue().booleanValue() && this.renderingChamsPass) {
            if (this.renderPass == PASS_OCCLUDED) {
                RenderUtils.w(this.colorBehindWalls.getEffectiveValue() != false ? this.occludedColor.getMutableColor() : this.visibleColor.getMutableColor());
            }
            if (this.renderPass == PASS_VISIBLE) {
                RenderUtils.w(this.visibleColor.getMutableColor());
            }
        }
    }

    @EventHandler
    public void onRenderPlayerPost(EventRenderPlayerPost event) {
        if (Vape.INSTANCE.getClientSettings().isBot(event.getEntityPlayer()) && this.hideBots.getEffectiveValue().booleanValue()) {
            return;
        }
        if (ForgeVersion.MC_1_16_5.d()) {
            // 1.17+：只走 polygon offset 穿墙，没有自己重绘模型的 pass，直接复位。
            EntityPlayer player = event.getEntityPlayer();
            if (player.isInstance(MappedClasses.Yl) && !player.isInstance(MappedClasses.z5)) {
                OpenGlBackendHolder.backend.disableCapability(32823);
                GL11.glPolygonOffset((float)1.0f, (float)2500000.0f);
            }
            return;
        }
        if (this.colored.getEffectiveValue().booleanValue()) {
            if (this.temporarilyInvisiblePlayer != null) {
                EntityPlayer player = event.getEntityPlayer();
                player.q(false);
                this.temporarilyInvisiblePlayer = null;
            }
            return;
        }
        if (event.getEntityPlayer().isInstance(MappedClasses.Yl) && !event.getEntityPlayer().isInstance(MappedClasses.z5)) {
            OpenGlBackendHolder.backend.disableCapability(32823);
            GL11.glPolygonOffset((float)1.0f, (float)2500000.0f);
        }
    }

    @EventHandler
    public void onPreRenderPlayerSpec(EventPreRenderPlayerSpec event) {
        if (event.getClientPlayer().isInstance(MappedClasses.z5)) {
            return;
        }
        if (!this.renderingChamsPass) {
            return;
        }
        if (Minecraft.theWorld().isNull()) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onRenderPlayerPre(EventRenderPlayerPre event) {
        if (this.hideBots.getEffectiveValue().booleanValue() && Vape.INSTANCE.getClientSettings().isBot(event.getEntityPlayer())) {
            return;
        }
        if (ClientSettings.isReservedEntity(event.getEntityPlayer())) {
            return;
        }
        if (ForgeVersion.MC_1_16_5.d()) {
            // 1.17+：实体走 RenderSystem / 批量顶点，彩色模式的“关深度测试后手动重绘”会
            // 打乱状态，所以高版本只做 polygon offset：把墙后玩家的深度整体前移，让它
            // 通过深度测试，显示的是原版皮肤（Colored 选项在高版本不生效）。
            EntityPlayer modernPlayer = event.getEntityPlayer();
            if (modernPlayer.isNull() || modernPlayer.isInstance(MappedClasses.z5)) {
                return;
            }
            ESP outlineEsp = Vape.INSTANCE.getModManager().getMod(ESP.class);
            if (outlineEsp != null && outlineEsp.isEnabled() && outlineEsp.isOutlineModeActive()) {
                return;
            }
            OpenGlBackendHolder.backend.enableCapability(32823);
            GL11.glPolygonOffset((float)1.0f, (float)-2500000.0f);
            return;
        }
        ESP esp = Vape.INSTANCE.getModManager().getMod(ESP.class);
        if (this.renderingChamsPass || this.renderPass == PASS_LEGACY || esp.isEnabled() && esp.isOutlineModeActive()) {
            return;
        }
        if (!this.colored.getEffectiveValue().booleanValue()) {
            if (event.getEntityPlayer().isInstance(MappedClasses.Yl) && !event.getEntityPlayer().isInstance(MappedClasses.z5)) {
                OpenGlBackendHolder.backend.enableCapability(32823);
                GL11.glPolygonOffset((float)1.0f, (float)-2500000.0f);
            }
        } else if (event.getEntityPlayer().isNotNull() && event.getRenderer().isNotNull() && !event.getEntityPlayer().isInstance(MappedClasses.z5)) {
            if (ForgeVersion.MC_1_7_10.L()) {
                event.setCancelled(true);
            }
            EntityPlayer player = event.getEntityPlayer();
            double renderX = event.getX();
            double renderY = event.getY();
            double renderZ = event.getZ();
            float interpolatedYaw = player.j() + (player.J() - player.j()) * event.getPartialTicks();
            RenderUtil.d();
            RenderUtils.g();
            OpenGlBackendHolder.backend.disableCapability(2929);
            OpenGlBackendHolder.backend.disableCapability(3553);
            GlStateManager.disableLighting();
            RenderUtils.w(this.colorBehindWalls.getEffectiveValue() != false ? this.occludedColor.getMutableColor() : this.visibleColor.getMutableColor());
            ArrayList<Object> savedLayerRenderers = null;
            if (ForgeVersion.MC_1_7_10.Y()) {
                savedLayerRenderers = Lists.newArrayList(event.getRenderer().getLayerRenderers());
                event.getRenderer().getLayerRenderers().clear();
            }
            try {
                this.renderingChamsPass = true;
                this.renderPass = PASS_OCCLUDED;
                event.getRenderer().doRender(player, renderX, renderY, renderZ, interpolatedYaw, event.getPartialTicks());
            }
            catch (Exception exception) {
                // empty catch block
            }
            this.renderingChamsPass = false;
            OpenGlBackendHolder.backend.enableCapability(2929);
            OpenGlBackendHolder.backend.enableCapability(3553);
            if (ForgeVersion.MC_1_7_10.L()) {
                this.renderPass = PASS_LEGACY;
                GL11.glPushMatrix();
                OpenGlBackendHolder.backend.enableCapability(2896);
                event.getRenderer().doRender(player, renderX, renderY, renderZ, interpolatedYaw, event.getPartialTicks());
                GL11.glDepthMask((boolean)false);
                OpenGlBackendHolder.backend.disableCapability(2896);
                GL11.glPopMatrix();
            }
            this.renderingChamsPass = true;
            RenderUtils.w(this.visibleColor.getMutableColor());
            OpenGlBackendHolder.backend.disableCapability(3553);
            try {
                this.renderPass = PASS_VISIBLE;
                event.getRenderer().doRender(player, renderX, renderY, renderZ, interpolatedYaw, event.getPartialTicks());
            }
            catch (Exception exception) {
                // empty catch block
            }
            finally {
                this.renderingChamsPass = false;
            }
            if (ForgeVersion.MC_1_7_10.Y()) {
                event.getRenderer().setLayerRenderers(savedLayerRenderers);
            }
            OpenGlBackendHolder.backend.enableCapability(3553);
            GL11.glColor4f((float)1.0f, (float)1.0f, (float)1.0f, (float)1.0f);
            GlStateManager.enableLighting();
            RenderUtils.f();
            GL11.glPopMatrix();
            this.renderPass = PASS_NONE;
            if (!player.J$src$Z$fdev5g()) {
                this.temporarilyInvisiblePlayer = player;
                player.q(true);
            }
            if (ForgeVersion.MC_1_16_5.d()) {
                Tessellator.getInstance().getWorldRenderer().Q(false);
            }
        }
    }

    public Chams() {
        super("Chams", -16711936, Category.RENDER, "Render players through walls.");
        this.colored = BooleanValue.create(this, "Colored", false, "Colors entities.\nOnly works below 1.17; newer versions render the original skin through walls.");
        this.visibleColor = ColorValue.create(this, "Visible Color", new Color(255, 0, 0));
        this.colorBehindWalls = BooleanValue.create(this, "Color Behind Walls", true, "Renders a different color when\nplayers are behind walls.");
        this.occludedColor = ColorValue.create(this, "Invisible Color", new Color(255, 255, 0));
        this.addValue(new Value[]{this.hideBots, this.colored.addDependentValues(new Value[]{this.visibleColor, this.colorBehindWalls.addDependentValues(this.occludedColor)}), this.visibleColor, this.colorBehindWalls, this.occludedColor});
    }

    @EventHandler
    public void onSetArmorModel(EventSetArmorModel event) {
        if (this.renderingChamsPass) {
            if (Minecraft.theWorld().isNull()) {
                return;
            }
            event.setResult(0);
            event.setCancelled(true);
        }
    }
}
