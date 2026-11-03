package gg.vape.utils.render;

import gg.vape.Vape;
import gg.vape.mapping.MappedClasses;
import gg.vape.module.render.hud.FreeLookHudModule;
import gg.vape.runtime.NativeBridge;
import gg.vape.unmap.GLUtils;
import gg.vape.utils.datas.HSBAData;
import gg.vape.utils.datas.HSBData;
import gg.vape.utils.datas.SearchResultData;
import gg.vape.utils.render.BufferedGuiRenderPrimitives;
import gg.vape.utils.render.BufferedRenderPrimitives;
import gg.vape.utils.render.GuiRenderPrimitives;
import gg.vape.utils.render.OpenGlBackendHolder;
import gg.vape.utils.render.RenderMatrix4f;
import gg.vape.utils.render.RenderUtils;
import gg.vape.utils.render.RenderVector4f;
import gg.vape.wrapper.impl.ActiveRenderInfo;
import gg.vape.wrapper.impl.Entity;
import gg.vape.wrapper.impl.FontRenderer;
import gg.vape.wrapper.impl.ForgeVersion;
import gg.vape.wrapper.impl.GlStateManager;
import gg.vape.wrapper.impl.Matrix4f;
import gg.vape.wrapper.impl.MatrixStack;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.Quaternion;
import gg.vape.wrapper.impl.RenderItemFontBridge;
import gg.vape.wrapper.impl.RenderManager;
import gg.vape.wrapper.impl.ScorePlayerTeamTextComponent;
import gg.vape.wrapper.impl.SharedMonsterAttributes;
import gg.vape.wrapper.impl.Tessellator;
import java.awt.Color;
import org.lwjgl.opengl.GL11;

public class RenderUtil {
    private static final GLUtils boxOutlineBuffer;
    private static final GLUtils boxFillBuffer;
    private static final GLUtils boxTriangleBuffer;
    private static final GLUtils globalOutlineBuffer;

    public static void m(SearchResultData searchResultData, double d, double d2, double d3) {
        RenderUtil.u(searchResultData.F, searchResultData.O, searchResultData.D, 1.0, 1.0, 1.0, 0.1, searchResultData.O(), null, d, d2, d3);
    }

    static {
        globalOutlineBuffer = Vape.INSTANCE.getGlUtils();
        boxFillBuffer = new GLUtils();
        boxFillBuffer.initializeVertexBuffer(48, 7);
        boxOutlineBuffer = new GLUtils();
        boxOutlineBuffer.initializeVertexBuffer(24, 1);
        boxTriangleBuffer = new GLUtils();
        boxTriangleBuffer.initializeVertexBuffer(36, 4);
    }

    public static void L(double d, double d2, double d3, HSBData hSBData) {
        float f = 0.0f;
        if (hSBData instanceof HSBAData) {
            HSBAData hSBAData = (HSBAData)hSBData;
            f = hSBAData.n();
        }
        RenderUtil.q(d, d2, d3, hSBData.F, hSBData.O, hSBData.D, new Color(255 - hSBData.U.getRed(), 255 - hSBData.U.getGreen(), 255 - hSBData.U.getBlue(), hSBData.U.getAlpha()), hSBData.U, f);
    }

    public static void D(HSBData hSBData, double d, double d2, double d3) {
        RenderUtil.u(hSBData.F, hSBData.O, hSBData.D, 1.0, 1.0, 1.0, 0.1, hSBData.U, null, d, d2, d3);
    }

    public static void d() {
        OpenGlBackendHolder.backend.pushMatrix();
        if (ForgeVersion.MC_1_16_5.d()) {
            RenderUtil.p();
        }
    }

    public static void q(double d, double d2, double d3, double d4, double d5, double d6, Color color, Color color2, float f) {
        if (GuiRenderPrimitives.d()) {
            double d7 = d4 + 1.0 - d;
            double d8 = d4 - d;
            double d9 = d5 + 1.0 - d2;
            double d10 = d5 - d2;
            double d11 = d6 + 1.0 - d3;
            double d12 = d6 - d3;
            BufferedRenderPrimitives.fillQuad(d8, d10, d12, d8, d9, d12, d7, d10, d12, d7, d9, d12, color2);
            BufferedRenderPrimitives.fillQuad(d7, d10, d11, d7, d9, d11, d8, d10, d11, d8, d9, d11, color2);
            BufferedRenderPrimitives.fillQuad(d7, d9, d12, d7, d10, d12, d8, d9, d12, d8, d10, d12, color2);
            BufferedRenderPrimitives.fillQuad(d8, d9, d11, d8, d10, d11, d7, d9, d11, d7, d10, d11, color2);
            BufferedRenderPrimitives.fillQuad(d8, d9, d12, d7, d9, d12, d7, d9, d11, d8, d9, d11, color2);
            BufferedRenderPrimitives.fillQuad(d8, d9, d12, d8, d9, d11, d7, d9, d11, d7, d9, d12, color2);
            BufferedRenderPrimitives.fillQuad(d8, d10, d12, d7, d10, d12, d7, d10, d11, d8, d10, d11, color2);
            BufferedRenderPrimitives.fillQuad(d8, d10, d12, d8, d10, d11, d7, d10, d11, d7, d10, d12, color2);
            BufferedRenderPrimitives.fillQuad(d8, d10, d12, d8, d9, d12, d8, d10, d11, d8, d9, d11, color2);
            BufferedRenderPrimitives.fillQuad(d7, d10, d11, d7, d9, d11, d7, d10, d12, d7, d9, d12, color2);
            BufferedRenderPrimitives.fillQuad(d8, d9, d11, d8, d10, d11, d8, d9, d12, d8, d10, d12, color2);
            BufferedRenderPrimitives.fillQuad(d7, d9, d12, d7, d10, d12, d7, d9, d11, d7, d10, d11, color2);
            if ((double)f > 0.1) {
                BufferedRenderPrimitives.drawLine3D(d8, d10, d12, d8, d9, d12, 3.0f, color);
                BufferedRenderPrimitives.drawLine3D(d7, d10, d12, d7, d9, d12, 3.0f, color);
                BufferedRenderPrimitives.drawLine3D(d8, d10, d11, d8, d9, d11, 3.0f, color);
                BufferedRenderPrimitives.drawLine3D(d7, d10, d11, d7, d9, d11, 3.0f, color);
                BufferedRenderPrimitives.drawLine3D(d7, d10, d12, d7, d10, d11, 3.0f, color);
                BufferedRenderPrimitives.drawLine3D(d7, d9, d12, d7, d9, d11, 3.0f, color);
                BufferedRenderPrimitives.drawLine3D(d8, d10, d12, d8, d10, d11, 3.0f, color);
                BufferedRenderPrimitives.drawLine3D(d8, d9, d12, d8, d9, d11, 3.0f, color);
                BufferedRenderPrimitives.drawLine3D(d8, d10, d12, d7, d10, d12, 3.0f, color);
                BufferedRenderPrimitives.drawLine3D(d8, d9, d12, d7, d9, d12, 3.0f, color);
                BufferedRenderPrimitives.drawLine3D(d8, d10, d11, d7, d10, d11, 3.0f, color);
                BufferedRenderPrimitives.drawLine3D(d8, d9, d11, d7, d9, d11, 3.0f, color);
            }
            return;
        }
        GL11.glColor4d((double)((float)color2.getRed() / 255.0f), (double)((float)color2.getGreen() / 255.0f), (double)((float)color2.getBlue() / 255.0f), (double)((float)color2.getAlpha() / 255.0f));
        boxFillBuffer.reset();
        double d13 = d4 + 1.0 - d;
        double d14 = d4 - d;
        double d15 = d5 + 1.0 - d2;
        double d16 = d5 - d2;
        double d17 = d6 + 1.0 - d3;
        double d18 = d6 - d3;
        boxFillBuffer.addVertex(d14, d16, d18);
        boxFillBuffer.addVertex(d14, d15, d18);
        boxFillBuffer.addVertex(d13, d16, d18);
        boxFillBuffer.addVertex(d13, d15, d18);
        boxFillBuffer.addVertex(d13, d16, d17);
        boxFillBuffer.addVertex(d13, d15, d17);
        boxFillBuffer.addVertex(d14, d16, d17);
        boxFillBuffer.addVertex(d14, d15, d17);
        boxFillBuffer.addVertex(d13, d15, d18);
        boxFillBuffer.addVertex(d13, d16, d18);
        boxFillBuffer.addVertex(d14, d15, d18);
        boxFillBuffer.addVertex(d14, d16, d18);
        boxFillBuffer.addVertex(d14, d15, d17);
        boxFillBuffer.addVertex(d14, d16, d17);
        boxFillBuffer.addVertex(d13, d15, d17);
        boxFillBuffer.addVertex(d13, d16, d17);
        boxFillBuffer.addVertex(d14, d15, d18);
        boxFillBuffer.addVertex(d13, d15, d18);
        boxFillBuffer.addVertex(d13, d15, d17);
        boxFillBuffer.addVertex(d14, d15, d17);
        boxFillBuffer.addVertex(d14, d15, d18);
        boxFillBuffer.addVertex(d14, d15, d17);
        boxFillBuffer.addVertex(d13, d15, d17);
        boxFillBuffer.addVertex(d13, d15, d18);
        boxFillBuffer.addVertex(d14, d16, d18);
        boxFillBuffer.addVertex(d13, d16, d18);
        boxFillBuffer.addVertex(d13, d16, d17);
        boxFillBuffer.addVertex(d14, d16, d17);
        boxFillBuffer.addVertex(d14, d16, d18);
        boxFillBuffer.addVertex(d14, d16, d17);
        boxFillBuffer.addVertex(d13, d16, d17);
        boxFillBuffer.addVertex(d13, d16, d18);
        boxFillBuffer.addVertex(d14, d16, d18);
        boxFillBuffer.addVertex(d14, d15, d18);
        boxFillBuffer.addVertex(d14, d16, d17);
        boxFillBuffer.addVertex(d14, d15, d17);
        boxFillBuffer.addVertex(d13, d16, d17);
        boxFillBuffer.addVertex(d13, d15, d17);
        boxFillBuffer.addVertex(d13, d16, d18);
        boxFillBuffer.addVertex(d13, d15, d18);
        boxFillBuffer.addVertex(d14, d15, d17);
        boxFillBuffer.addVertex(d14, d16, d17);
        boxFillBuffer.addVertex(d14, d15, d18);
        boxFillBuffer.addVertex(d14, d16, d18);
        boxFillBuffer.addVertex(d13, d15, d18);
        boxFillBuffer.addVertex(d13, d16, d18);
        boxFillBuffer.addVertex(d13, d15, d17);
        boxFillBuffer.addVertex(d13, d16, d17);
        boxFillBuffer.uploadAndDraw();
        if (f > 0.0f) {
            GL11.glColor4d((double)((double)color.getRed() / 255.0), (double)((double)color.getGreen() / 255.0), (double)((double)color.getBlue() / 255.0), (double)0.7);
            GL11.glLineWidth((float)3.0f);
            globalOutlineBuffer.reset();
            globalOutlineBuffer.addVertex(d14, d16, d18);
            globalOutlineBuffer.addVertex(d14, d15, d18);
            globalOutlineBuffer.addVertex(d13, d16, d18);
            globalOutlineBuffer.addVertex(d13, d15, d18);
            globalOutlineBuffer.addVertex(d14, d16, d17);
            globalOutlineBuffer.addVertex(d14, d15, d17);
            globalOutlineBuffer.addVertex(d13, d16, d17);
            globalOutlineBuffer.addVertex(d13, d15, d17);
            globalOutlineBuffer.addVertex(d13, d16, d18);
            globalOutlineBuffer.addVertex(d13, d16, d17);
            globalOutlineBuffer.addVertex(d13, d15, d18);
            globalOutlineBuffer.addVertex(d13, d15, d17);
            globalOutlineBuffer.addVertex(d14, d16, d18);
            globalOutlineBuffer.addVertex(d14, d16, d17);
            globalOutlineBuffer.addVertex(d14, d15, d18);
            globalOutlineBuffer.addVertex(d14, d15, d17);
            globalOutlineBuffer.addVertex(d14, d16, d18);
            globalOutlineBuffer.addVertex(d13, d16, d18);
            globalOutlineBuffer.addVertex(d14, d15, d18);
            globalOutlineBuffer.addVertex(d13, d15, d18);
            globalOutlineBuffer.addVertex(d14, d16, d17);
            globalOutlineBuffer.addVertex(d13, d16, d17);
            globalOutlineBuffer.addVertex(d14, d15, d17);
            globalOutlineBuffer.addVertex(d13, d15, d17);
            globalOutlineBuffer.uploadAndDraw();
        }
    }

    public static void x(double d, double d2, double d3, double d4, double d5, double d6, Color color, double d7, double d8, double d9) {
        double d10 = d + d4 - d7;
        double d11 = d - d7;
        double d12 = d2 + d5 - d8;
        double d13 = d2 - d8;
        double d14 = d3 + d6 - d9;
        double d15 = d3 - d9;
        boxTriangleBuffer.addVertex(d11, d13, d15);
        boxTriangleBuffer.addVertex(d10, d13, d15);
        boxTriangleBuffer.addVertex(d11, d12, d15);
        boxTriangleBuffer.addVertex(d10, d13, d15);
        boxTriangleBuffer.addVertex(d10, d12, d15);
        boxTriangleBuffer.addVertex(d11, d12, d15);
        boxTriangleBuffer.addVertex(d11, d13, d14);
        boxTriangleBuffer.addVertex(d11, d12, d14);
        boxTriangleBuffer.addVertex(d10, d13, d14);
        boxTriangleBuffer.addVertex(d10, d13, d14);
        boxTriangleBuffer.addVertex(d11, d12, d14);
        boxTriangleBuffer.addVertex(d10, d12, d14);
        boxTriangleBuffer.addVertex(d11, d13, d15);
        boxTriangleBuffer.addVertex(d11, d12, d14);
        boxTriangleBuffer.addVertex(d11, d13, d14);
        boxTriangleBuffer.addVertex(d11, d13, d15);
        boxTriangleBuffer.addVertex(d11, d12, d15);
        boxTriangleBuffer.addVertex(d11, d12, d14);
        boxTriangleBuffer.addVertex(d10, d13, d15);
        boxTriangleBuffer.addVertex(d10, d13, d14);
        boxTriangleBuffer.addVertex(d10, d12, d14);
        boxTriangleBuffer.addVertex(d10, d13, d15);
        boxTriangleBuffer.addVertex(d10, d12, d14);
        boxTriangleBuffer.addVertex(d10, d12, d15);
        boxTriangleBuffer.addVertex(d11, d12, d15);
        boxTriangleBuffer.addVertex(d10, d12, d14);
        boxTriangleBuffer.addVertex(d11, d12, d14);
        boxTriangleBuffer.addVertex(d11, d12, d15);
        boxTriangleBuffer.addVertex(d10, d12, d15);
        boxTriangleBuffer.addVertex(d10, d12, d14);
        boxTriangleBuffer.addVertex(d11, d13, d15);
        boxTriangleBuffer.addVertex(d11, d13, d14);
        boxTriangleBuffer.addVertex(d10, d13, d14);
        boxTriangleBuffer.addVertex(d11, d13, d15);
        boxTriangleBuffer.addVertex(d10, d13, d14);
        boxTriangleBuffer.addVertex(d10, d13, d15);
        if (color != null) {
            GL11.glColor4d((double)((float)color.getRed() / 255.0f), (double)((float)color.getGreen() / 255.0f), (double)((float)color.getBlue() / 255.0f), (double)((float)color.getAlpha() / 255.0f));
            boxTriangleBuffer.uploadAndDraw();
        }
    }


    public static Color N(Color color) {
        int n = color.getRGB();
        n = 0xFFFFFF - n;
        return new Color(n);
    }

    public static void U(String string, double d, double d2, double d3, double d4, double d5, int n, Color color, double d6, MatrixStack matrixStack) {
        float f;
        float f2;
        GuiRenderPrimitives.U = true;
        FontRenderer fontRenderer = Minecraft.getFontRenderer();
        int n2 = n;
        float f3 = d5 / 5.0 <= 2.0 ? 2.0f : (float)(d5 / 5.0);
        float f4 = f2 = (float)(0.01666666753590107 * ((double)f3 * d4));
        RenderUtil.d();
        float f5 = FreeLookHudModule.isActive() ? FreeLookHudModule.getRenderPitch() : Minecraft.D().getPlayerViewX();
        float f6 = f = FreeLookHudModule.isActive() ? FreeLookHudModule.getRenderYaw() : Minecraft.D().getPlayerViewY();
        if (ForgeVersion.MC_1_16_5.d()) {
            if (Minecraft.gameSettings().x() == 0) {
                OpenGlBackendHolder.backend.translate(d + 0.0, d2 + 0.25 + 0.5, d3);
                OpenGlBackendHolder.backend.setNormal(0.0f, 1.0f, 0.0f);
                OpenGlBackendHolder.backend.rotate(-f5, 0.0f, 1.0f, 0.0f);
                OpenGlBackendHolder.backend.rotate(-f, -1.0f, 0.0f, 0.0f);
            } else {
                ActiveRenderInfo activeRenderInfo = Minecraft.m$src$Lgg_vape_wrapper_impl_EntityRenderer_$13begmf().l();
                double d7 = GuiRenderPrimitives.d() ? 0.0 : RenderManager.getInterpolatedRenderPosX() - activeRenderInfo.o().getX();
                double d8 = GuiRenderPrimitives.d() ? 0.0 : RenderManager.getInterpolatedRenderPosY() - activeRenderInfo.o().getY();
                double d9 = GuiRenderPrimitives.d() ? 0.0 : RenderManager.getInterpolatedRenderPosZ() - activeRenderInfo.o().getZ();
                OpenGlBackendHolder.backend.translate(d + d7, d2 + d8 + 0.25 + 0.5, d3 + d9);
                OpenGlBackendHolder.backend.setNormal(0.0f, 1.0f, 0.0f);
                OpenGlBackendHolder.backend.rotate(-f5, 0.0f, 1.0f, 0.0f);
                OpenGlBackendHolder.backend.rotate(f, 1.0f, 0.0f, 0.0f);
            }
        } else {
            OpenGlBackendHolder.backend.translate(d + 0.0, d2 + 0.25 + 0.5, d3);
            OpenGlBackendHolder.backend.setNormal(0.0f, 1.0f, 0.0f);
            if (Minecraft.gameSettings().x() == 2) {
                OpenGlBackendHolder.backend.rotate(-f5, 0.0f, 1.0f, 0.0f);
                OpenGlBackendHolder.backend.rotate(f, -1.0f, 0.0f, 0.0f);
            } else {
                OpenGlBackendHolder.backend.rotate(-f5, 0.0f, 1.0f, 0.0f);
                OpenGlBackendHolder.backend.rotate(f, 1.0f, 0.0f, 0.0f);
            }
        }
        OpenGlBackendHolder.backend.scale(-f4, -f4, f4);
        int n3 = fontRenderer.getStringWidth(string) / 2;
        int n4 = -(fontRenderer.FONT_HEIGHT(string) - 1);
        int n5 = fontRenderer.getFontHeight();
        int n6 = n3;
        int n7 = -(n5 - 1);
        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.Y();
        GlStateManager.disableTexture2D();
        RenderUtils.M((double)(-n6) - 2.0, n7, (double)n6 + 2.0, 2.0, 0.0, color, color);
        GlStateManager.enableTexture2D();
        GlStateManager.disableDepth();
        OpenGlBackendHolder.backend.setColor(1.0f, 1.0f, 1.0f);
        if (ForgeVersion.MC_1_16_5.d() && (matrixStack == null || matrixStack.isNull() || !matrixStack.isInstance(MappedClasses.DQ))) {
            matrixStack = MatrixStack.A();
        }
        boolean bl = false;
        if (GuiRenderPrimitives.d()) {
            RenderMatrix4f renderMatrix4f = new RenderMatrix4f().setIdentity();
            renderMatrix4f.multiply(BufferedGuiRenderPrimitives.viewMatrix);
            renderMatrix4f.multiply(BufferedGuiRenderPrimitives.matrixStack.peek());
            if (ForgeVersion.MC_1_20_6.d()) {
                fontRenderer.h(string, -n3, -n5 + 3, n2, false, renderMatrix4f, SharedMonsterAttributes.V());
            } else {
                int n8 = 0xF000F0;
                RenderItemFontBridge renderItemFontBridge = Minecraft.H$src$Lgg_vape_wrapper_impl_VoxelShape_$1dlcquv().getBufferSource();
                ScorePlayerTeamTextComponent scorePlayerTeamTextComponent = ScorePlayerTeamTextComponent.B(string);
                fontRenderer.Z(scorePlayerTeamTextComponent, -n3, -n5 + 3, n2, false, renderMatrix4f.toMinecraftMatrix(), renderItemFontBridge, true, 0, n8);
                renderItemFontBridge.q();
            }
        } else if (ForgeVersion.MC_1_16_5.d()) {
            matrixStack.H();
            bl = true;
            Quaternion quaternion = Minecraft.D().getCameraOrientation();
            matrixStack.i(quaternion);
            matrixStack.i(Quaternion.fromEulerAngles(180.0f, 0.0f, 180.0f, true));
            ScorePlayerTeamTextComponent scorePlayerTeamTextComponent = ScorePlayerTeamTextComponent.B(string);
            RenderItemFontBridge renderItemFontBridge = RenderItemFontBridge.V(Tessellator.getInstance().getWorldRenderer());
            int n9 = 0xF000F0;
            Matrix4f matrix4f = matrixStack.F().getMatrix();
            fontRenderer.Z(scorePlayerTeamTextComponent, -n3, -n5 + 3, n2, false, matrix4f, renderItemFontBridge, true, 0, n9);
            renderItemFontBridge.q();
        } else {
            fontRenderer.drawString(string, (double)(-n3), (double)(-n5 + 3), n2);
        }
        if (bl) {
            matrixStack.U();
        }
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        RenderUtil.Y();
        GuiRenderPrimitives.U = false;
    }

    public static void u(double d, double d2, double d3, double d4, double d5, double d6, double d7, Color color, Color color2, double d8, double d9, double d10) {
        double d11;
        double d12;
        double d13;
        double d14;
        double d15;
        double d16;
        if (GuiRenderPrimitives.d()) {
            double d17 = d + d4 - d8;
            double d18 = d - d8;
            double d19 = d2 + d5 - d9;
            double d20 = d2 - d9;
            double d21 = d3 + d6 - d10;
            double d22 = d3 - d10;
            if (color2 != null) {
                BufferedRenderPrimitives.fillBox(d18, d20, d22, d17, d19, d21, color2);
            }
            if (color != null) {
                BufferedRenderPrimitives.drawBoxOutline(d18, d20, d22, d17, d19, d21, color);
            }
            return;
        }
        if (color2 != null) {
            GL11.glColor4d((double)((float)color2.getRed() / 255.0f), (double)((float)color2.getGreen() / 255.0f), (double)((float)color2.getBlue() / 255.0f), (double)((float)color2.getAlpha() / 255.0f));
            boxFillBuffer.reset();
            d16 = d + d4 - d8;
            d15 = d - d8;
            d14 = d2 + d5 - d9;
            d13 = d2 - d9;
            d12 = d3 + d6 - d10;
            d11 = d3 - d10;
            boxFillBuffer.addVertex(d15, d13, d11);
            boxFillBuffer.addVertex(d15, d14, d11);
            boxFillBuffer.addVertex(d16, d13, d11);
            boxFillBuffer.addVertex(d16, d14, d11);
            boxFillBuffer.addVertex(d16, d13, d12);
            boxFillBuffer.addVertex(d16, d14, d12);
            boxFillBuffer.addVertex(d15, d13, d12);
            boxFillBuffer.addVertex(d15, d14, d12);
            boxFillBuffer.addVertex(d16, d14, d11);
            boxFillBuffer.addVertex(d16, d13, d11);
            boxFillBuffer.addVertex(d15, d14, d11);
            boxFillBuffer.addVertex(d15, d13, d11);
            boxFillBuffer.addVertex(d15, d14, d12);
            boxFillBuffer.addVertex(d15, d13, d12);
            boxFillBuffer.addVertex(d16, d14, d12);
            boxFillBuffer.addVertex(d16, d13, d12);
            boxFillBuffer.addVertex(d15, d14, d11);
            boxFillBuffer.addVertex(d16, d14, d11);
            boxFillBuffer.addVertex(d16, d14, d12);
            boxFillBuffer.addVertex(d15, d14, d12);
            boxFillBuffer.addVertex(d15, d14, d11);
            boxFillBuffer.addVertex(d15, d14, d12);
            boxFillBuffer.addVertex(d16, d14, d12);
            boxFillBuffer.addVertex(d16, d14, d11);
            boxFillBuffer.addVertex(d15, d13, d11);
            boxFillBuffer.addVertex(d16, d13, d11);
            boxFillBuffer.addVertex(d16, d13, d12);
            boxFillBuffer.addVertex(d15, d13, d12);
            boxFillBuffer.addVertex(d15, d13, d11);
            boxFillBuffer.addVertex(d15, d13, d12);
            boxFillBuffer.addVertex(d16, d13, d12);
            boxFillBuffer.addVertex(d16, d13, d11);
            boxFillBuffer.addVertex(d15, d13, d11);
            boxFillBuffer.addVertex(d15, d14, d11);
            boxFillBuffer.addVertex(d15, d13, d12);
            boxFillBuffer.addVertex(d15, d14, d12);
            boxFillBuffer.addVertex(d16, d13, d12);
            boxFillBuffer.addVertex(d16, d14, d12);
            boxFillBuffer.addVertex(d16, d13, d11);
            boxFillBuffer.addVertex(d16, d14, d11);
            boxFillBuffer.addVertex(d15, d14, d12);
            boxFillBuffer.addVertex(d15, d13, d12);
            boxFillBuffer.addVertex(d15, d14, d11);
            boxFillBuffer.addVertex(d15, d13, d11);
            boxFillBuffer.addVertex(d16, d14, d11);
            boxFillBuffer.addVertex(d16, d13, d11);
            boxFillBuffer.addVertex(d16, d14, d12);
            boxFillBuffer.addVertex(d16, d13, d12);
            boxFillBuffer.uploadAndDraw();
        }
        if (color != null) {
            GL11.glColor4d((double)((float)color.getRed() / 255.0f), (double)((float)color.getGreen() / 255.0f), (double)((float)color.getBlue() / 255.0f), (double)((float)color.getAlpha() / 255.0f));
            boxOutlineBuffer.reset();
            d16 = d + d4 - d8;
            d15 = d - d8;
            d14 = d2 + d5 - d9;
            d13 = d2 - d9;
            d12 = d3 + d6 - d10;
            d11 = d3 - d10;
            boxOutlineBuffer.addVertex(d15, d13, d11);
            boxOutlineBuffer.addVertex(d16, d13, d11);
            boxOutlineBuffer.addVertex(d16, d13, d11);
            boxOutlineBuffer.addVertex(d16, d13, d12);
            boxOutlineBuffer.addVertex(d16, d13, d12);
            boxOutlineBuffer.addVertex(d15, d13, d12);
            boxOutlineBuffer.addVertex(d15, d13, d12);
            boxOutlineBuffer.addVertex(d15, d13, d11);
            boxOutlineBuffer.addVertex(d15, d14, d11);
            boxOutlineBuffer.addVertex(d16, d14, d11);
            boxOutlineBuffer.addVertex(d16, d14, d11);
            boxOutlineBuffer.addVertex(d16, d14, d12);
            boxOutlineBuffer.addVertex(d16, d14, d12);
            boxOutlineBuffer.addVertex(d15, d14, d12);
            boxOutlineBuffer.addVertex(d15, d14, d12);
            boxOutlineBuffer.addVertex(d15, d14, d11);
            boxOutlineBuffer.addVertex(d15, d13, d11);
            boxOutlineBuffer.addVertex(d15, d14, d11);
            boxOutlineBuffer.addVertex(d16, d13, d11);
            boxOutlineBuffer.addVertex(d16, d14, d11);
            boxOutlineBuffer.addVertex(d16, d13, d12);
            boxOutlineBuffer.addVertex(d16, d14, d12);
            boxOutlineBuffer.addVertex(d15, d13, d12);
            boxOutlineBuffer.addVertex(d15, d14, d12);
            boxOutlineBuffer.uploadAndDraw();
        }
    }

    public static void p() {
        if (ForgeVersion.MC_1_21_10.d() && !ForgeVersion.MC_26_1.d()) {
            // 1.21.10 <= 版本 < 26.1：相机旋转由 LocalPlayerRotationUtil 的投影路径提供
            // （那份视图矩阵的平移被清零），所以这里不能重复施加旋转；但第三人称的
            // (renderPos - cameraPos) 平移必须补上。否则所有沿用「renderPos 相对坐标 +
            // RenderUtil.d()」约定的模块（ESP2D/ESP3D、ItemESP、NameTags、BedPlates、
            // Search、Tracers、Arrows、StorageESP ...）在第三人称下都会整体偏一个后拉距离。
            RenderUtil.f(Minecraft.D(), false);
            return;
        }
        RenderUtil.f(Minecraft.D());
    }

    public static void Y() {
        OpenGlBackendHolder.backend.popMatrix();
    }

    public static void f(RenderManager renderManager) {
        RenderUtil.f(renderManager, true);
    }

    public static void f(RenderManager renderManager, boolean applyCameraRotation) {
        if (ForgeVersion.MC_1_16_5.d()) {
            if (applyCameraRotation) {
                OpenGlBackendHolder.backend.rotate(renderManager.getPlayerViewY(), 1.0f, 0.0f, 0.0f);
                OpenGlBackendHolder.backend.rotate(renderManager.getPlayerViewX() + 180.0f, 0.0f, 1.0f, 0.0f);
            }
            if (GuiRenderPrimitives.d()) {
                // 参考点换算：各模块传入的是「renderPos 相对」坐标，而世界是按相机渲染的，
                // 必须把 (renderPos - cameraPos) 补进模型矩阵才是「相机相对」坐标。
                //   * 第三人称：该偏移 = 后拉距离（原本只在这一种情况下补偿）；
                //   * 自由视角(Freecam)：FreecamModernController.updateRenderViewPosition 直接
                //     改 ActiveRenderInfo 的相机位置，玩家实体不动、游戏仍是第一人称，于是该
                //     偏移 = 整段相机位移 —— 以前不补偿，所以开自由视角时所有世界叠加物
                //     （ESP / 存储ESP / 箭 / 射线 …）整体平移。现在统一按相机作参考。
                //   * 普通第一人称：cameraPos == renderPos（Y 已被 updateInterpolatedRenderPosition
                //     强制成相机 Y），偏移≈0，行为不变。
                ActiveRenderInfo activeRenderInfo = Minecraft.m$src$Lgg_vape_wrapper_impl_EntityRenderer_$13begmf().l();
                OpenGlBackendHolder.backend.translate(RenderManager.getInterpolatedRenderPosX() - activeRenderInfo.o().getX(), RenderManager.getInterpolatedRenderPosY() - activeRenderInfo.o().getY(), RenderManager.getInterpolatedRenderPosZ() - activeRenderInfo.o().getZ());
                // 这里原来还有一句第三人称下的 FreeLook yaw 增量补偿
                //   rotate(-(FreeLookHudModule.getRenderPitch() - thePlayer().J()), 0, 1, 0)
                // 但上面两个 rotate 用的就是 Camera 对象里的 yaw/pitch，而 Camera 在实体渲染
                // 阶段之前就已建立（FreeLook 在此阶段的还原只作用于玩家实体、不作用于相机），
                // 也就是说相机里存的已经是自由视角朝向 —— 那句增量等于把「自由视角 yaw - 身体
                // yaw」再叠一次，第三人称 + 自由旋转视角下会让所有世界叠加物多转一个 yaw。
                // 自由视角未开启时该增量为 0，故删除它不改变任何其它情况的行为。
            }
        }
    }

    public static void w(double d, double d2, double d3, double d4, double d5, double d6, Color color) {
        if (GuiRenderPrimitives.d()) {
            double d7 = d4 + 1.0 - d;
            double d8 = d4 - d;
            double d9 = d5 + 1.0 - d2;
            double d10 = d5 - d2;
            double d11 = d6 + 1.0 - d3;
            double d12 = d6 - d3;
            BufferedRenderPrimitives.fillQuad(d8, d10, d12, d8, d9, d12, d7, d10, d12, d7, d9, d12, color);
            BufferedRenderPrimitives.fillQuad(d7, d10, d11, d7, d9, d11, d8, d10, d11, d8, d9, d11, color);
            BufferedRenderPrimitives.fillQuad(d7, d9, d12, d7, d10, d12, d8, d9, d12, d8, d10, d12, color);
            BufferedRenderPrimitives.fillQuad(d8, d9, d11, d8, d10, d11, d7, d9, d11, d7, d10, d11, color);
            BufferedRenderPrimitives.fillQuad(d8, d9, d12, d7, d9, d12, d7, d9, d11, d8, d9, d11, color);
            BufferedRenderPrimitives.fillQuad(d8, d9, d12, d8, d9, d11, d7, d9, d11, d7, d9, d12, color);
            BufferedRenderPrimitives.fillQuad(d8, d10, d12, d7, d10, d12, d7, d10, d11, d8, d10, d11, color);
            BufferedRenderPrimitives.fillQuad(d8, d10, d12, d8, d10, d11, d7, d10, d11, d7, d10, d12, color);
            BufferedRenderPrimitives.fillQuad(d8, d10, d12, d8, d9, d12, d8, d10, d11, d8, d9, d11, color);
            BufferedRenderPrimitives.fillQuad(d7, d10, d11, d7, d9, d11, d7, d10, d12, d7, d9, d12, color);
            BufferedRenderPrimitives.fillQuad(d8, d9, d11, d8, d10, d11, d8, d9, d12, d8, d10, d12, color);
            BufferedRenderPrimitives.fillQuad(d7, d9, d12, d7, d10, d12, d7, d9, d11, d7, d10, d11, color);
            return;
        }
        GL11.glColor4d((double)((float)color.getRed() / 255.0f), (double)((float)color.getGreen() / 255.0f), (double)((float)color.getBlue() / 255.0f), (double)((float)color.getAlpha() / 255.0f));
        boxFillBuffer.reset();
        double d13 = d4 + 1.0 - d;
        double d14 = d4 - d;
        double d15 = d5 + 1.0 - d2;
        double d16 = d5 - d2;
        double d17 = d6 + 1.0 - d3;
        double d18 = d6 - d3;
        boxFillBuffer.addVertex(d14, d16, d18);
        boxFillBuffer.addVertex(d14, d15, d18);
        boxFillBuffer.addVertex(d13, d16, d18);
        boxFillBuffer.addVertex(d13, d15, d18);
        boxFillBuffer.addVertex(d13, d16, d17);
        boxFillBuffer.addVertex(d13, d15, d17);
        boxFillBuffer.addVertex(d14, d16, d17);
        boxFillBuffer.addVertex(d14, d15, d17);
        boxFillBuffer.addVertex(d13, d15, d18);
        boxFillBuffer.addVertex(d13, d16, d18);
        boxFillBuffer.addVertex(d14, d15, d18);
        boxFillBuffer.addVertex(d14, d16, d18);
        boxFillBuffer.addVertex(d14, d15, d17);
        boxFillBuffer.addVertex(d14, d16, d17);
        boxFillBuffer.addVertex(d13, d15, d17);
        boxFillBuffer.addVertex(d13, d16, d17);
        boxFillBuffer.addVertex(d14, d15, d18);
        boxFillBuffer.addVertex(d13, d15, d18);
        boxFillBuffer.addVertex(d13, d15, d17);
        boxFillBuffer.addVertex(d14, d15, d17);
        boxFillBuffer.addVertex(d14, d15, d18);
        boxFillBuffer.addVertex(d14, d15, d17);
        boxFillBuffer.addVertex(d13, d15, d17);
        boxFillBuffer.addVertex(d13, d15, d18);
        boxFillBuffer.addVertex(d14, d16, d18);
        boxFillBuffer.addVertex(d13, d16, d18);
        boxFillBuffer.addVertex(d13, d16, d17);
        boxFillBuffer.addVertex(d14, d16, d17);
        boxFillBuffer.addVertex(d14, d16, d18);
        boxFillBuffer.addVertex(d14, d16, d17);
        boxFillBuffer.addVertex(d13, d16, d17);
        boxFillBuffer.addVertex(d13, d16, d18);
        boxFillBuffer.addVertex(d14, d16, d18);
        boxFillBuffer.addVertex(d14, d15, d18);
        boxFillBuffer.addVertex(d14, d16, d17);
        boxFillBuffer.addVertex(d14, d15, d17);
        boxFillBuffer.addVertex(d13, d16, d17);
        boxFillBuffer.addVertex(d13, d15, d17);
        boxFillBuffer.addVertex(d13, d16, d18);
        boxFillBuffer.addVertex(d13, d15, d18);
        boxFillBuffer.addVertex(d14, d15, d17);
        boxFillBuffer.addVertex(d14, d16, d17);
        boxFillBuffer.addVertex(d14, d15, d18);
        boxFillBuffer.addVertex(d14, d16, d18);
        boxFillBuffer.addVertex(d13, d15, d18);
        boxFillBuffer.addVertex(d13, d16, d18);
        boxFillBuffer.addVertex(d13, d15, d17);
        boxFillBuffer.addVertex(d13, d16, d17);
        boxFillBuffer.uploadAndDraw();
    }

    public static void k(Entity entity, double d, Color color, Color color2, float f) {
        double d2 = RenderManager.getInterpolatedRenderPosX();
        double d3 = RenderManager.getInterpolatedRenderPosY();
        double d4 = RenderManager.getInterpolatedRenderPosZ();
        double d5 = entity.M() + (entity.z() - entity.M()) * (double)f;
        double d6 = entity.W() + (entity.N() - entity.W()) * (double)f;
        double d7 = entity.m$src$D$fwnne5() + (entity.h() - entity.m$src$D$fwnne5()) * (double)f;
        RenderUtil.d();
        Minecraft.m$src$Lgg_vape_wrapper_impl_EntityRenderer_$13begmf().B(1.0);
        boolean bl = OpenGlBackendHolder.backend.isCapabilityEnabled(3042);
        boolean bl2 = OpenGlBackendHolder.backend.isCapabilityEnabled(2929);
        boolean bl3 = OpenGlBackendHolder.backend.isCapabilityEnabled(3553);
        boolean bl4 = GL11.glGetBoolean((int)2930);
        if (!bl) {
            OpenGlBackendHolder.backend.enableCapability(3042);
        }
        if (bl2) {
            OpenGlBackendHolder.backend.disableCapability(2929);
        }
        if (bl3) {
            OpenGlBackendHolder.backend.disableCapability(3553);
        }
        if (bl4) {
            OpenGlBackendHolder.backend.setDepthMask(false);
        }
        GL11.glBlendFunc((int)770, (int)771);
        OpenGlBackendHolder.backend.setLineWidth(1.5f);
        OpenGlBackendHolder.backend.enableCapability(2848);
        double d8 = 0.15;
        RenderUtil.u(d5 - (double)(entity.f$src$F$fst3ac() / 2.0f) - d8, d6, d7 - (double)(entity.f$src$F$fst3ac() / 2.0f) - d8, (double)entity.f$src$F$fst3ac() + d8 * 2.0, entity.Y(), (double)entity.f$src$F$fst3ac() + d8 * 2.0, d, color, color2, d2, d3, d4);
        if (bl4) {
            OpenGlBackendHolder.backend.setDepthMask(true);
        }
        if (bl2) {
            OpenGlBackendHolder.backend.enableCapability(2929);
        }
        if (bl3) {
            OpenGlBackendHolder.backend.enableCapability(3553);
        }
        if (bl) {
            OpenGlBackendHolder.backend.disableCapability(3042);
        }
        OpenGlBackendHolder.backend.disableCapability(2848);
        Minecraft.m$src$Lgg_vape_wrapper_impl_EntityRenderer_$13begmf().O(1.0);
        RenderUtil.Y();
    }

    public static void c(SearchResultData searchResultData, double d, double d2, double d3) {
        RenderUtil.x(searchResultData.F, searchResultData.O, searchResultData.D, 1.0, 1.0, 1.0, searchResultData.O(), d, d2, d3);
    }

    /**
     * Same as {@link #W(double, double, double)} but returns {@code null} when the point is
     * behind the camera (clip-space w <= 0). Behind-camera points would otherwise be divided by
     * a negative w, which mirrors them through the screen centre and makes ESP boxes appear on
     * the opposite side of the world.
     */
    public static double[] WChecked(double d, double d2, double d3) {
        if (GuiRenderPrimitives.d()) {
            RenderVector4f renderVector4f = new RenderVector4f(d, d2, d3, 1.0);
            RenderVector4f renderVector4f2 = new RenderVector4f(0.0f, 0.0f, 0.0f, 0.0f);
            RenderMatrix4f renderMatrix4f = BufferedGuiRenderPrimitives.matrixStack.peek();
            RenderMatrix4f renderMatrix4f2 = BufferedGuiRenderPrimitives.viewMatrix;
            RenderMatrix4f renderMatrix4f3 = BufferedGuiRenderPrimitives.projectionMatrix;
            renderMatrix4f.transform(renderVector4f, renderVector4f2);
            renderMatrix4f2.transform(renderVector4f2, renderVector4f);
            renderMatrix4f3.transform(renderVector4f, renderVector4f2);
            if ((double)renderVector4f2.w <= 1.0E-6) {
                return null;
            }
            renderVector4f2.w = 1.0f / renderVector4f2.w * 0.5f;
            renderVector4f2.x = renderVector4f2.x * renderVector4f2.w + 0.5f;
            renderVector4f2.y = renderVector4f2.y * renderVector4f2.w + 0.5f;
            renderVector4f2.z = renderVector4f2.z * renderVector4f2.w + 0.5f;
            return new double[]{renderVector4f2.x * (float)Minecraft.J(), renderVector4f2.y * (float)Minecraft.h(), renderVector4f2.z};
        }
        return NativeBridge.trn(d, d2, d3);
    }

    public static double[] W(double d, double d2, double d3) {
        if (GuiRenderPrimitives.d()) {
            double[] dArray = new double[3];
            RenderVector4f renderVector4f = new RenderVector4f(d, d2, d3, 1.0);
            RenderVector4f renderVector4f2 = new RenderVector4f(0.0f, 0.0f, 0.0f, 0.0f);
            RenderMatrix4f renderMatrix4f = BufferedGuiRenderPrimitives.matrixStack.peek();
            RenderMatrix4f renderMatrix4f2 = BufferedGuiRenderPrimitives.viewMatrix;
            RenderMatrix4f renderMatrix4f3 = BufferedGuiRenderPrimitives.projectionMatrix;
            renderMatrix4f.transform(renderVector4f, renderVector4f2);
            renderMatrix4f2.transform(renderVector4f2, renderVector4f);
            renderMatrix4f3.transform(renderVector4f, renderVector4f2);
            if ((double)renderVector4f2.w == 0.0) {
                return dArray;
            }
            renderVector4f2.w = 1.0f / renderVector4f2.w * 0.5f;
            renderVector4f2.x = renderVector4f2.x * renderVector4f2.w + 0.5f;
            renderVector4f2.y = renderVector4f2.y * renderVector4f2.w + 0.5f;
            renderVector4f2.z = renderVector4f2.z * renderVector4f2.w + 0.5f;
            dArray[0] = renderVector4f2.x * (float)Minecraft.J();
            dArray[1] = renderVector4f2.y * (float)Minecraft.h();
            dArray[2] = renderVector4f2.z;
            return dArray;
        }
        return NativeBridge.trn(d, d2, d3);
    }
}
