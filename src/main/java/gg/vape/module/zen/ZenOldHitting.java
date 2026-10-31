package gg.vape.module.zen;

import gg.vape.event.EventHandler;
import gg.vape.event.impl.EventRenderItemInFirstPerson;
import gg.vape.module.Category;
import gg.vape.module.Mod;
import gg.vape.unmap.ModeOption;
import gg.vape.value.ModeValue;
import gg.vape.value.NumberValue;
import gg.vape.utils.MathUtil;
import gg.vape.utils.render.OpenGlBackendHolder;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.GlStateManager;
import gg.vape.wrapper.impl.ItemRenderer;
import gg.vape.wrapper.impl.ItemRendererBridge;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.RenderHelper;
import org.lwjgl.opengl.GL11;

/**
 * Zen OldHitting (1.7 挥击动画) — replaces the first person swing transform with the old
 * vanilla / "leaked" / "slide" style animations, ported from OpenZen's render/OldHitting.java.
 */
public class ZenOldHitting
extends Mod {
    private static final int MOD_ID = 1148293305;

    private final ModeOption vanillaMode = new ModeOption("Vanilla");
    private final ModeOption leakedMode = new ModeOption("Leaked");
    private final ModeOption slideMode = new ModeOption("Slide");
    private final ModeValue animation = ModeValue.create(this, "Animation",
            "Vanilla - 1.7 vanilla swing\nLeaked - leaked client swing\nSlide - slide swing",
            this.leakedMode, this.vanillaMode, this.leakedMode, this.slideMode);
    private final NumberValue size = NumberValue.create(this, "Size", "#.##", "x", 0.1, 1.0, 3.0, 0.05);
    private final NumberValue speed = NumberValue.create(this, "Speed", "#.##", "x", 0.1, 1.0, 5.0, 0.05);
    private final NumberValue yOffset = NumberValue.create(this, "Y-Offset", "#.##", "", -1.0, 0.0, 1.0, 0.05);

    public ZenOldHitting() {
        super("Zen OldHitting", MOD_ID, Category.ZEN,
                "1.7 style swing animations, ported from OpenZen's OldHitting.");
    }

    @EventHandler
    public void onRenderItemInFirstPerson(EventRenderItemInFirstPerson event) {
        EntityPlayerSP player = Minecraft.thePlayer();
        if (player == null || player.isNull() || Minecraft.currentScreen().isNotNull()) {
            return;
        }
        ItemRenderer itemRenderer = event.getItemRenderer();
        if (itemRenderer.k().isNull()) {
            return;
        }
        float swingProgress = player.L(event.partialTicks);
        if (swingProgress <= 0.0f || swingProgress >= 1.0f) {
            return;
        }
        this.renderSwing(event, itemRenderer, player, swingProgress);
        event.setCancelled(true);
    }

    private void renderSwing(EventRenderItemInFirstPerson event, ItemRenderer itemRenderer, EntityPlayerSP player, float swingProgress) {
        float equipProgress = 1.0f - (itemRenderer.e() + (itemRenderer.R() - itemRenderer.e()) * event.partialTicks);
        float pitch = player.D() + (player.V() - player.D()) * event.partialTicks;
        float yaw = player.j() + (player.J() - player.j()) * event.partialTicks;
        this.applyViewRotation(pitch, yaw);
        itemRenderer.X(player);
        this.applyCameraLag(player, event.partialTicks);
        OpenGlBackendHolder.backend.enableCapability(32826);
        GL11.glPushMatrix();
        this.applyItemTransform(equipProgress, swingProgress);
        itemRenderer.g(player, itemRenderer.k(), ItemRendererBridge.firstPerson());
        GL11.glPopMatrix();
        OpenGlBackendHolder.backend.disableCapability(32826);
        RenderHelper.s();
    }

    private void applyViewRotation(float pitch, float yaw) {
        GL11.glPushMatrix();
        GlStateManager.d(pitch, 1.0f, 0.0f, 0.0f);
        GlStateManager.d(yaw, 0.0f, 1.0f, 0.0f);
        RenderHelper.e();
        GL11.glPopMatrix();
    }

    private void applyCameraLag(EntityPlayerSP player, float partialTicks) {
        float prevPitch = player.n$src$F$1u53eru() + (player.t$src$F$1u8e6c0() - player.n$src$F$1u53eru()) * partialTicks;
        float prevYaw = player.x$src$F$1ualcpg() + (player.q$src$F$1u6qsjx() - player.x$src$F$1ualcpg()) * partialTicks;
        GlStateManager.d((player.V() - prevPitch) * 0.1f, 1.0f, 0.0f, 0.0f);
        GlStateManager.d((player.J() - prevYaw) * 0.1f, 0.0f, 1.0f, 0.0f);
    }

    private void applyItemTransform(float equipProgress, float swingProgress) {
        float scaledProgress = swingProgress * this.speed.getValue().floatValue();
        float scale = this.size.getValue().floatValue();
        GL11.glTranslatef(0.0f, this.yOffset.getValue().floatValue(), 0.0f);
        if (this.animation.getSelectedIndex() == 0) {
            this.applyVanilla(equipProgress, scaledProgress, scale);
        } else if (this.animation.getSelectedIndex() == 2) {
            this.applySlide(scaledProgress, scale);
        } else {
            this.applyLeaked(equipProgress, scaledProgress, scale);
        }
    }

    private void applyVanilla(float equipProgress, float progress, float scale) {
        GL11.glTranslatef(0.56f, -0.52f + equipProgress * -0.6f, -0.72f);
        GL11.glTranslatef(-0.1414214f, 0.08f, 0.1414213925600052f);
        rot(1.0f, 0.0f, 0.0f, -102.25f);
        rot(0.0f, 1.0f, 0.0f, 13.365f);
        rot(0.0f, 0.0f, 1.0f, 78.05f);
        double squared = Math.sin(progress * progress * Math.PI);
        double rooted = Math.sin(Math.sqrt(progress) * Math.PI);
        rot(0.0f, 1.0f, 0.0f, (float)(squared * -20.0));
        rot(0.0f, 0.0f, 1.0f, (float)(rooted * -20.0));
        rot(1.0f, 0.0f, 0.0f, (float)(rooted * -80.0));
        GL11.glScalef(scale, scale, scale);
    }

    private void applyLeaked(float equipProgress, float progress, float scale) {
        GL11.glTranslatef(0.56f, -0.52f, -0.72f);
        rot(0.0f, 1.0f, 0.0f, 45.0f);
        float squared = MathUtil.sin(progress * progress * (float)Math.PI);
        float rooted = MathUtil.sin(MathUtil.sqrt(progress) * (float)Math.PI);
        rot(0.0f, 1.0f, 0.0f, squared * -20.0f);
        rot(0.0f, 0.0f, 1.0f, rooted * -20.0f);
        rot(1.0f, 0.0f, 0.0f, rooted * -80.0f);
        GL11.glScalef(0.4f * scale, 0.4f * scale, 0.4f * scale);
        GL11.glTranslatef(-0.5f, 0.2f, 0.0f);
        rot(0.0f, 1.0f, 0.0f, 30.0f);
        rot(1.0f, 0.0f, 0.0f, -80.0f);
        rot(0.0f, 1.0f, 0.0f, 60.0f);
        float pulse = MathUtil.sin(MathUtil.sqrt(progress) * (float)Math.PI) / 8.0f;
        GL11.glTranslatef(0.008f, 0.24f, 0.03f);
        GL11.glTranslatef(-0.16f, -0.25f, 0.0f);
        float pulseScale = (0.8f + pulse) * scale;
        GL11.glScalef(pulseScale, pulseScale, pulseScale);
        rot(0.0f, 1.2f, -0.8f, -MathUtil.sin(MathUtil.sqrt(progress) * (float)Math.PI) * 20.0f);
        rot(1.0f, 0.0f, 0.0f, -MathUtil.sin(MathUtil.sqrt(progress) * (float)Math.PI) * 30.0f);
        GL11.glScalef(2.4f * scale, 2.4f * scale, 2.4f * scale);
        rot(0.0f, 1.0f, 0.0f, -38.4f);
        GL11.glScalef(scale, scale, scale);
    }

    private void applySlide(float progress, float scale) {
        float wave = MathUtil.sin(MathUtil.sqrt(progress) * (float)Math.PI);
        GL11.glTranslatef(0.648f, -0.55f, -0.72f);
        rot(0.0f, 1.0f, 0.0f, 77.0f);
        rot(0.0f, 0.0f, 1.0f, -10.0f);
        rot(1.0f, 0.0f, 0.0f, -80.0f);
        rot(1.0f, 0.0f, 0.0f, -wave * 20.0f);
        GL11.glScalef(1.2f * scale, 1.2f * scale, 1.2f * scale);
        GL11.glScalef(scale, scale, scale);
    }

    private static void rot(float ax, float ay, float az, float angle) {
        GlStateManager.d(angle, ax, ay, az);
    }
}
