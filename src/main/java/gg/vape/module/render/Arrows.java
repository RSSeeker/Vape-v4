package gg.vape.module.render;

import gg.vape.rotation.LocalPlayerRotationUtil;
import gg.vape.Vape;
import gg.vape.event.EventHandler;
import gg.vape.event.impl.EventRender2D;
import gg.vape.event.impl.EventRender3D;
import gg.vape.mapping.MappedClasses;
import gg.vape.module.Category;
import gg.vape.module.Mod;
import gg.vape.render.OffscreenRenderContext;
import gg.vape.unmap.ColorUtil;
import gg.vape.utils.MathUtil;
import gg.vape.utils.render.BufferedGuiRenderPrimitives;
import gg.vape.utils.render.GuiRenderPrimitives;
import gg.vape.utils.render.ImageRenderer;
import gg.vape.utils.render.OpenGlBackendHolder;
import gg.vape.utils.render.RenderBatchManager;
import gg.vape.utils.render.RenderMatrix4f;
import gg.vape.utils.render.RenderMatrixStack;
import gg.vape.utils.render.RenderUtil;
import gg.vape.utils.render.RenderUtils;
import gg.vape.value.BooleanValue;
import gg.vape.value.ColorValue;
import gg.vape.value.NumberValue;
import gg.vape.wrapper.impl.Entity;
import gg.vape.wrapper.impl.EntityPlayer;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.FontRenderer;
import gg.vape.wrapper.impl.ForgeVersion;
import gg.vape.wrapper.impl.GlStateManager;
import gg.vape.wrapper.impl.MatrixStack;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.RenderManager;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

public class Arrows
extends Mod {
    private final BooleanValue showDistance;
    private final NumberValue radiusScale;
    private final ColorValue color = ColorValue.create(this, "Color", new Color(255, 0, 0));
    private static final long MODULE_COLOR = 6320396365828000318L;
    private final BooleanValue scaleOpacity;
    private final Map<Entity, double[]> projectedPositions;
    // 本帧图标是否已在世界阶段画掉（决定 2D 阶段是否还要画）。
    private boolean iconsDrawnInWorldPhase;

    @EventHandler
    public void onRender2D(EventRender2D event) {
        EntityPlayerSP player = event.getThePlayer();
        if (player.isNull() || event.getWorld().isNull()) {
            return;
        }
        int displayWidth = Minecraft.J();
        int displayHeight = Minecraft.h();
        float centerX = (float)displayWidth / 4.0f;
        float centerY = (float)displayHeight / 4.0f;
        GuiRenderPrimitives.u(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, Color.WHITE);
        FontRenderer fontRenderer = event.getFontRenderer();
        for (Entity entity : this.projectedPositions.keySet()) {
            double[] projectedPosition = this.projectedPositions.get(entity);
            double screenX = projectedPosition[0];
            double screenY = (double)Minecraft.h() - projectedPosition[1];
            if (projectedPosition[2] < 1.0 && this.isOnScreen(screenX / 2.0, screenY / 2.0, displayWidth, displayHeight)) continue;
            float angle = this.getAngle(centerX, screenX / 2.0, centerY, screenY / 2.0) + (float)(projectedPosition[2] > 1.0 ? 180 : 0);
            double horizontalRadius = (double)centerX * (Double)this.radiusScale.getValue();
            double verticalRadius = (double)centerY * (Double)this.radiusScale.getValue();
            double arrowX = Math.sqrt(1.0 / (1.0 / (horizontalRadius * horizontalRadius) + Math.pow(Math.tan(Math.toRadians(angle)), 2.0) / (verticalRadius * verticalRadius)));
            double arrowY = Math.tan(Math.toRadians(angle)) * arrowX;
            float wrappedAngle = MathUtil.wrapAngleTo180(angle + 90.0f);
            if (wrappedAngle < 0.0f) {
                arrowX = -arrowX;
                if (wrappedAngle > -180.0f) {
                    arrowY = -arrowY;
                }
            }
            int distanceAlpha = (int)((double)player.getDistanceToEntity(entity) * 1.5);
            if (distanceAlpha > 255) {
                distanceAlpha = 255;
            }
            if (this.showDistance.getEffectiveValue().booleanValue() && distanceAlpha < 255) {
                OpenGlBackendHolder.backend.pushMatrix();
                RenderUtils.g();
                String distanceText = (int)player.getDistanceToEntity(entity) + "m";
                OpenGlBackendHolder.backend.translate(arrowX + (double)centerX, arrowY + (double)centerY, 0.0);
                OpenGlBackendHolder.backend.scale(0.5, 0.5, 0.0);
                boolean blendEnabled = OpenGlBackendHolder.backend.isCapabilityEnabled(3042);
                GlStateManager.enableBlend();
                Color textColor = new Color(255, 255, 255, 255 - (this.scaleOpacity.getEffectiveValue() != false ? distanceAlpha : 0));
                if (GuiRenderPrimitives.d()) {
                    MatrixStack matrixStack = MatrixStack.A();
                    matrixStack.H();
                    float guiScale = (float)Minecraft.p().k(Minecraft.gameSettings().T(), false) / 2.0f;
                    float renderScale = 1.0f;
                    float inverseGuiScale = 1.0f / guiScale;
                    arrowX = Math.ceil(arrowX);
                    arrowY = Math.ceil(arrowY);
                    arrowX /= (double)renderScale;
                    arrowY /= (double)renderScale;
                    matrixStack.S(inverseGuiScale, inverseGuiScale, inverseGuiScale);
                    matrixStack.S(renderScale, renderScale, renderScale);
                    matrixStack.i(BufferedGuiRenderPrimitives.matrixStack.peek().toMinecraftMatrix());
                    fontRenderer.V(distanceText, (float)(-fontRenderer.getStringWidth(distanceText)) / 2.0f, -fontRenderer.getHalfFontHeight(distanceText), ColorUtil.packRgba(textColor.getRed(), textColor.getGreen(), textColor.getBlue(), textColor.getAlpha()), matrixStack);
                } else {
                    fontRenderer.drawStringWithShadow(distanceText, (double)((float)(-fontRenderer.getStringWidth(distanceText)) / 2.0f), -fontRenderer.getHalfFontHeight(distanceText), ColorUtil.packRgba(textColor.getRed(), textColor.getGreen(), textColor.getBlue(), textColor.getAlpha()));
                }
                if (!blendEnabled) {
                    GlStateManager.disableBlend();
                }
                RenderUtils.f();
                OpenGlBackendHolder.backend.popMatrix();
            }
            // 1.20.1：图标优先在世界阶段画完并立即冲刷（见 drawArrowIconsBeforeHud），
            // 这里只保留距离文字；若世界阶段因队列非空/异常而没画，则退回这里绘制。
            if (!this.iconsDrawnInWorldPhase) {
                OpenGlBackendHolder.backend.pushMatrix();
                RenderUtils.g();
                OpenGlBackendHolder.backend.translate(arrowX + (double)centerX, arrowY + (double)centerY, 0.0);
                OpenGlBackendHolder.backend.rotate(angle - 90.0f, 0.0f, 0.0f, 1.0f);
                OpenGlBackendHolder.backend.scale(0.375, 0.5, 0.0);
                Color configuredColor = this.color.getMutableColor();
                int alpha = configuredColor.getAlpha();
                if (this.scaleOpacity.getEffectiveValue().booleanValue()) {
                    alpha = 255 - distanceAlpha;
                }
                Color arrowColor = new Color(configuredColor.getRed(), configuredColor.getGreen(), configuredColor.getBlue(), alpha);
                ImageRenderer.drawResWithShadow(arrowColor, -16.0f, 0.0f, "exo", 1.0f, false);
                RenderUtils.f();
                OpenGlBackendHolder.backend.popMatrix();
            }
        }
        this.projectedPositions.clear();
    }

    // 箭头图标在 1.20.1 上改为"世界阶段画完并立即冲刷"：2D 阶段的 GUI 批次要到帧末才冲刷，
    // 那时游戏 HUD 已经画完 ⇒ 箭头会压在 HUD/其它 UI 之上。其它版本保持原有 2D 阶段绘制。
    private boolean shouldDrawIconsInWorldPhase() {
        return ForgeVersion.MC_1_20_1.d() && !ForgeVersion.MC_1_20_6.d() && GuiRenderPrimitives.d();
    }

    private Color getArrowColor(int distanceAlpha) {
        Color configuredColor = this.color.getMutableColor();
        int alpha = configuredColor.getAlpha();
        if (this.scaleOpacity.getEffectiveValue().booleanValue()) {
            alpha = 255 - distanceAlpha;
        }
        return new Color(configuredColor.getRed(), configuredColor.getGreen(), configuredColor.getBlue(), alpha);
    }

    // 与 onRender2D 里同一套角度/椭圆半径算法，只是把图标放到世界阶段画。
    private void drawArrowIconBeforeHud(double[] projectedPosition, int displayWidth, int displayHeight,
            float centerX, float centerY) {
        double screenX = projectedPosition[0];
        double screenY = (double)Minecraft.h() - projectedPosition[1];
        if (projectedPosition[2] < 1.0 && this.isOnScreen(screenX / 2.0, screenY / 2.0, displayWidth, displayHeight)) {
            return;
        }
        float angle = this.getAngle(centerX, screenX / 2.0, centerY, screenY / 2.0) + (float)(projectedPosition[2] > 1.0 ? 180 : 0);
        double horizontalRadius = (double)centerX * (Double)this.radiusScale.getValue();
        double verticalRadius = (double)centerY * (Double)this.radiusScale.getValue();
        double arrowX = Math.sqrt(1.0 / (1.0 / (horizontalRadius * horizontalRadius)
                + Math.pow(Math.tan(Math.toRadians(angle)), 2.0) / (verticalRadius * verticalRadius)));
        double arrowY = Math.tan(Math.toRadians(angle)) * arrowX;
        float wrappedAngle = MathUtil.wrapAngleTo180(angle + 90.0f);
        if (wrappedAngle < 0.0f) {
            arrowX = -arrowX;
            if (wrappedAngle > -180.0f) {
                arrowY = -arrowY;
            }
        }
        OpenGlBackendHolder.backend.pushMatrix();
        RenderUtils.g();
        OpenGlBackendHolder.backend.translate(arrowX + (double)centerX, arrowY + (double)centerY, 0.0);
        OpenGlBackendHolder.backend.rotate(angle - 90.0f, 0.0f, 0.0f, 1.0f);
        OpenGlBackendHolder.backend.scale(0.375, 0.5, 0.0);
        ImageRenderer.drawResWithShadow(this.getArrowColor(0), -16.0f, 0.0f, "exo", 1.0f, false);
        RenderUtils.f();
        OpenGlBackendHolder.backend.popMatrix();
    }

    // 用 GUI 正交投影把箭头图标画完并立即冲刷（与 ESP2D 相同的做法），随后还原
    // 投影/视图/矩阵栈，不影响随后 ESP/Tracers/NameTags 的世界批次。
    private void drawArrowIconsBeforeHud() {
        RenderBatchManager batchManager = RenderBatchManager.getInstance();
        // 只有"队列里没有别人的批次"时才提前冲刷：否则会把别的模块排队的 GUI 批次
        // （可能带 standalone 回调，内含 MC 原生绘制）拉到世界阶段执行，容易造成卡顿/崩溃。
        // 这种情况下返回 false，让图标退回 2D 阶段绘制（安全路径）。
        if (batchManager.getGuiBatchCount() != 0 || batchManager.isWithinGuiFlush()) {
            this.iconsDrawnInWorldPhase = false;
            return;
        }
        int displayWidth = Minecraft.J();
        int displayHeight = Minecraft.h();
        float centerX = (float)displayWidth / 4.0f;
        float centerY = (float)displayHeight / 4.0f;
        RenderMatrix4f savedProjection = BufferedGuiRenderPrimitives.projectionMatrix;
        RenderMatrix4f savedView = BufferedGuiRenderPrimitives.viewMatrix;
        RenderMatrixStack savedMatrixStack = BufferedGuiRenderPrimitives.matrixStack;
        BufferedGuiRenderPrimitives.projectionMatrix = new RenderMatrix4f().setIdentity()
                .setOrthographic(0.0f, (float)displayWidth / 2.0f, (float)displayHeight / 2.0f, 0.0f, 21000.0f, -21000.0f);
        BufferedGuiRenderPrimitives.viewMatrix = new RenderMatrix4f().setIdentity();
        BufferedGuiRenderPrimitives.matrixStack = new RenderMatrixStack();
        try {
            for (Map.Entry<Entity, double[]> entry : this.projectedPositions.entrySet()) {
                this.drawArrowIconBeforeHud(entry.getValue(), displayWidth, displayHeight, centerX, centerY);
            }
            batchManager.flushGuiBatches(0.0f, false);
            this.iconsDrawnInWorldPhase = true;
        }
        catch (Throwable drawFailure) {
            // 世界阶段绘制失败时退回 2D 阶段，绝不影响主渲染流程。
            this.iconsDrawnInWorldPhase = false;
        }
        finally {
            BufferedGuiRenderPrimitives.projectionMatrix = savedProjection;
            BufferedGuiRenderPrimitives.viewMatrix = savedView;
            BufferedGuiRenderPrimitives.matrixStack = savedMatrixStack;
        }
    }


    private boolean isOnScreen(double x, double y, int displayWidth, int displayHeight) {
        return x > 0.0 && y > 0.0 && x < (double)(displayWidth / 2) && y < (double)(displayHeight / 2);
    }

    @EventHandler
    public void onRender3D(EventRender3D event) {
        this.iconsDrawnInWorldPhase = false;
        this.projectedPositions.clear();
        RenderUtil.d();
        // 1.21.10-25.x: GUI batch matrices do not follow the camera; arrows would lag/stick.
        if (ForgeVersion.MC_1_21_10.d() && !ForgeVersion.MC_26_1.d()) {
            LocalPlayerRotationUtil.updateProjectionMatrix(event.getTicks());
        }
        for (Object entityHandle : Minecraft.theWorld().z()) {
            Entity entity = new Entity(entityHandle);
            if (!this.shouldTrack(entity)) continue;
            double renderX = entity.M() + (entity.z() - entity.M()) * (double)event.getTicks() - RenderManager.getInterpolatedRenderPosX();
            double renderY = entity.W() + (entity.N() - entity.W()) * (double)event.getTicks() - RenderManager.getInterpolatedRenderPosY();
            double renderZ = entity.m$src$D$fwnne5() + (entity.h() - entity.m$src$D$fwnne5()) * (double)event.getTicks() - RenderManager.getInterpolatedRenderPosZ();
            double[] projectedPosition = RenderUtil.W(renderX, renderY, renderZ);
            this.projectedPositions.put(entity, projectedPosition);
        }
        RenderUtil.Y();
        // 1.20.1：把箭头图标提前到世界阶段画完并立即冲刷，使其排在游戏 HUD 之前。
        // 只在"确实有箭头要画"且当前不在 GUI 冲刷内部时执行，避免每帧空冲刷/递归冲刷。
        if (this.shouldDrawIconsInWorldPhase()
                && !this.projectedPositions.isEmpty()
                && !OffscreenRenderContext.isRenderingOffscreen()
                && !RenderBatchManager.getInstance().isWithinGuiFlush()) {
            this.drawArrowIconsBeforeHud();
        }
    }

    public boolean shouldTrack(Entity entity) {
        if (OffscreenRenderContext.isRenderingOffscreen()) {
            return false;
        }
        if (!entity.isInstance(MappedClasses.Yl)) {
            return false;
        }
        if (entity.isInstance(MappedClasses.z5)) {
            return false;
        }
        EntityPlayer player = new EntityPlayer(entity.getObject());
        if (Vape.INSTANCE.getClientSettings().isBot(player)) {
            return false;
        }
        if (Vape.INSTANCE.getClientSettings().isTeammate(player)) {
            return false;
        }
        return !Vape.INSTANCE.getFriendManager().isFriend(player.getName());
    }

    public Arrows() {
        super("Arrows", (int)MODULE_COLOR, Category.RENDER, "Draws arrows on screen when entities\nare out of your field of view.");
        this.showDistance = BooleanValue.create(this, "Show Distance", false, "Renders the distance next to the arrow.");
        this.scaleOpacity = BooleanValue.create(this, "Scale Opacity", false, "Lowers the opacity the farther they are.");
        this.radiusScale = NumberValue.create((Object)this, "Radius Scale", "#.##", "x", 0.0, 0.5, 1.0, 0.05);
        this.projectedPositions = new HashMap<Entity, double[]>();
        this.addValue(this.color, this.radiusScale, this.showDistance, this.scaleOpacity);
    }

    private float getAngle(double centerX, double x, double centerY, double y) {
        return (float)Math.toDegrees(Math.atan2(y - centerY, x - centerX));
    }
}
