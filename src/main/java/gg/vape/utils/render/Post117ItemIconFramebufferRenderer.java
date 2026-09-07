package gg.vape.utils.render;

import gg.vape.Vape;
import gg.vape.utils.render.BufferedGuiRenderPrimitives;
import gg.vape.utils.render.GlFramebuffer;
import gg.vape.utils.render.GlImageTexture;
import gg.vape.utils.render.GlScissorRect;
import gg.vape.utils.render.ItemIconRenderBackend;
import gg.vape.utils.render.ItemStackRenderUtils;
import gg.vape.utils.render.OpenGlBackendHolder;
import gg.vape.utils.render.Post117RenderPhaseCompat;
import gg.vape.utils.render.RenderBatchBuilder;
import gg.vape.utils.render.RenderBatchManager;
import gg.vape.utils.render.RenderMatrix4f;
import gg.vape.utils.render.VertexCoordinateMode;
import gg.vape.wrapper.Wrapper;
import gg.vape.wrapper.impl.BlockStateContainerBridge;
import gg.vape.wrapper.impl.ForgeVersion;
import gg.vape.wrapper.impl.GlStateManager;
import gg.vape.wrapper.impl.ItemStack;
import gg.vape.wrapper.impl.MainWindow;
import gg.vape.wrapper.impl.Matrix4fHandle;
import gg.vape.wrapper.impl.MatrixStack;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.RenderBufferBridge;
import gg.vape.wrapper.impl.RenderItem;
import gg.vape.wrapper.impl.RenderItemTextBridge;
import gg.vape.wrapper.impl.StringTextComponent;
import java.awt.Color;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.List;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

public class Post117ItemIconFramebufferRenderer
implements ItemIconRenderBackend {
    GlFramebuffer framebuffer;

    @Override
    public void capture(ItemStack itemStack, float scale) {
        RenderBatchManager.getInstance().flushGuiBatches(0.0f);
        int iconWidth = 32;
        int iconHeight = 32;
        int previousFramebufferId = GL11.glGetInteger((int)36006);
        int previousTextureId = GL11.glGetInteger((int)32873);
        boolean scissorEnabled = GL11.glIsEnabled((int)3089);
        if (scissorEnabled) {
            OpenGlBackendHolder.backend.disableCapability(3089);
            ByteBuffer viewportBytes = ByteBuffer.allocateDirect(64);
            viewportBytes.order(ByteOrder.nativeOrder());
            IntBuffer viewport = viewportBytes.asIntBuffer();
            gg.vape.wrapper.impl.GL11.X(2978, viewport);
            this.framebuffer = new GlFramebuffer(iconWidth, iconHeight, true);
            this.framebuffer.bind(true);
            GL11.glClearColor((float)0.0f, (float)0.0f, (float)0.0f, (float)0.0f);
            GL11.glClear((int)16384);
            GL11.glClear((int)256);
            GlStateManager.enableDepth();
            GlStateManager.enableBlend();
            MatrixStack renderMatrixStack = MatrixStack.A();
            renderMatrixStack.H();
            float guiScale = Minecraft.p().k(Minecraft.gameSettings().T(), false);
            float inverseGuiScale = 1.0f / guiScale;
            renderMatrixStack.S(inverseGuiScale, inverseGuiScale, inverseGuiScale);
            renderMatrixStack.S((float)Minecraft.J() / 32.0f, (float)Minecraft.h() / 32.0f, 0.0f);
            OpenGlBackendHolder.backend.pushMatrix();
            if (ForgeVersion.MC_1_20_6.d()) {
                RenderItemTextBridge itemRenderState;
                Object matrixHandle;
                if (ForgeVersion.MC_1_21_6.d()) {
                    matrixHandle = Matrix4fHandle.b(16);
                    itemRenderState = RenderItemTextBridge.l((Matrix4fHandle)matrixHandle);
                } else {
                    itemRenderState = RenderItemTextBridge.t(renderMatrixStack);
                }
                if (ForgeVersion.MC_1_21_6.d()) {
                    itemRenderState.S().reset();
                }
                ItemStackRenderUtils.renderItemOverlay(itemRenderState, itemStack, 0, -100);
                if (ForgeVersion.MC_1_21_10.d()) {
                    float maxV;
                    float maxU;
                    int renderTargetTextureId;
                    Object renderTarget;
                    Wrapper renderTargetHandle;
                    List<String> fogEntries = itemRenderState.S().getFogMode().getItemStates();
                    if (fogEntries == null || fogEntries.isEmpty()) {
                        OpenGlBackendHolder.backend.popMatrix();
                        return;
                    }
                    StringTextComponent renderTargetName = new StringTextComponent(fogEntries.get(fogEntries.size() - 1));
                    RenderBufferBridge renderBuffer = Minecraft.m$src$Lgg_vape_wrapper_impl_EntityRenderer_$13begmf().V();
                    renderBuffer.j();
                    Post117RenderPhaseCompat.applyRenderPhaseCompat();
                    if (ForgeVersion.MC_26_1.d()) {
                        renderTargetHandle = renderBuffer.P();
                        renderTarget = renderTargetHandle.isNull() ? null : ((BlockStateContainerBridge)renderTargetHandle)
                                .getOrUpdate(renderTargetName.getItemStackRenderState());
                        renderTargetTextureId = renderTargetHandle.isNull() ? -1 : ((BlockStateContainerBridge)renderTargetHandle).getTextureId();
                    } else {
                        renderTarget = renderBuffer.p().get(
                                renderTargetName.getItemStackRenderState().getModelIdentity());
                        int resolvedTextureId = renderTargetTextureId = renderBuffer.F().isNull() ? -1 : renderBuffer.F().getId();
                    }
                    if (renderTarget == null || renderTargetTextureId == -1) {
                        OpenGlBackendHolder.backend.popMatrix();
                        return;
                    }
                    renderTargetHandle = new MainWindow(renderTarget);
                    float minU = ((MainWindow)renderTargetHandle).q();
                    float minV = ((MainWindow)renderTargetHandle).r();
                    if (ForgeVersion.MC_26_1.d()) {
                        maxU = ((MainWindow)renderTargetHandle).i();
                        maxV = ((MainWindow)renderTargetHandle).A();
                    } else {
                        int guiScaleFactor = Minecraft.p().P();
                        int pixelOffset = 16 * guiScaleFactor;
                        int renderTargetSize = renderBuffer.L(pixelOffset);
                        maxU = minU + (float)pixelOffset / (float)renderTargetSize;
                        maxV = minV + (float)(-pixelOffset) / (float)renderTargetSize;
                    }
                    this.framebuffer.bind(true);
                    GL11.glColorMask((boolean)true, (boolean)true, (boolean)true, (boolean)true);
                    GlScissorRect previousScissorRect = BufferedGuiRenderPrimitives.scissorRect;
                    BufferedGuiRenderPrimitives.scissorRect = null;
                    RenderBatchBuilder batchBuilder = new RenderBatchBuilder().setTexture(new GlImageTexture(renderTargetTextureId)).addTexturedRect(0.0f, 0.0f, iconWidth, iconHeight, iconWidth, iconHeight, minU, minV, maxU, maxV, Color.WHITE);
                    BufferedGuiRenderPrimitives.projectionMatrix = new RenderMatrix4f().setIdentity().setOrthographic(0.0f, iconWidth, iconHeight, 0.0f, -21000.0f, 21000.0f);
                    RenderBatchManager batchManager = RenderBatchManager.getInstance();
                    batchManager.queueGuiBatch(batchBuilder);
                    batchManager.setFramebufferOverride(this.framebuffer.framebufferId);
                    batchManager.flushGuiBatches(0.0f, false);
                    batchManager.restoreFramebufferOverride();
                    BufferedGuiRenderPrimitives.scissorRect = previousScissorRect;
                    OpenGlBackendHolder.backend.popMatrix();
                }
            } else {
                // Pre-1.20.6 (1.16.5, 1.20.1) path: RenderItem.a can throw on 1.20.1
                // (getBakedModel/legacy zLevel mappings not wired). Swallow so the
                // framebuffer/viewport/texture restore below always runs.
                try {
                    RenderItem renderItem = Minecraft.v();
                    renderItem.a(itemStack, 0, 0, renderMatrixStack);
                }
                catch (Throwable throwable) {
                    Vape.logThrowable(throwable);
                }
            }
            this.framebuffer.bindColorTexture();
            this.framebuffer.unbind();
            GL11.glViewport((int)viewport.get(0), (int)viewport.get(1), (int)viewport.get(2), (int)viewport.get(3));
            GL30.glBindFramebuffer((int)36160, (int)previousFramebufferId);
            GlStateManager.bindTexture(previousTextureId);
            OpenGlBackendHolder.backend.enableCapability(3089);
            return;
        }
        ByteBuffer viewportBytes = ByteBuffer.allocateDirect(64);
        viewportBytes.order(ByteOrder.nativeOrder());
        IntBuffer viewport = viewportBytes.asIntBuffer();
        gg.vape.wrapper.impl.GL11.X(2978, viewport);
        this.framebuffer = new GlFramebuffer(iconWidth, iconHeight, true);
        this.framebuffer.bind(true);
        GL11.glClearColor((float)0.0f, (float)0.0f, (float)0.0f, (float)0.0f);
        GL11.glClear((int)16384);
        GL11.glClear((int)256);
        GlStateManager.enableDepth();
        GlStateManager.enableBlend();
        MatrixStack renderMatrixStack = MatrixStack.A();
        renderMatrixStack.H();
        float guiScale = Minecraft.p().k(Minecraft.gameSettings().T(), false);
        float inverseGuiScale = 1.0f / guiScale;
        renderMatrixStack.S(inverseGuiScale, inverseGuiScale, inverseGuiScale);
        renderMatrixStack.S((float)Minecraft.J() / 32.0f, (float)Minecraft.h() / 32.0f, 0.0f);
        OpenGlBackendHolder.backend.pushMatrix();
        if (ForgeVersion.MC_1_20_6.d()) {
            RenderItemTextBridge itemRenderState;
            Object matrixHandle;
            if (ForgeVersion.MC_1_21_6.d()) {
                matrixHandle = Matrix4fHandle.b(16);
                itemRenderState = RenderItemTextBridge.l((Matrix4fHandle)matrixHandle);
            } else {
                itemRenderState = RenderItemTextBridge.t(renderMatrixStack);
            }
            if (ForgeVersion.MC_1_21_6.d()) {
                itemRenderState.S().reset();
            }
            ItemStackRenderUtils.renderItemOverlay(itemRenderState, itemStack, 0, -100);
            if (ForgeVersion.MC_1_21_10.d()) {
                float maxV;
                float maxU;
                int renderTargetTextureId;
                Object renderTarget;
                Wrapper renderTargetHandle;
                List<String> fogEntries = itemRenderState.S().getFogMode().getItemStates();
                if (fogEntries == null || fogEntries.isEmpty()) {
                    OpenGlBackendHolder.backend.popMatrix();
                    return;
                }
                StringTextComponent renderTargetName = new StringTextComponent(fogEntries.get(fogEntries.size() - 1));
                RenderBufferBridge renderBuffer = Minecraft.m$src$Lgg_vape_wrapper_impl_EntityRenderer_$13begmf().V();
                renderBuffer.j();
                Post117RenderPhaseCompat.applyRenderPhaseCompat();
                if (ForgeVersion.MC_26_1.d()) {
                    renderTargetHandle = renderBuffer.P();
                    renderTarget = renderTargetHandle.isNull() ? null : ((BlockStateContainerBridge)renderTargetHandle)
                            .getOrUpdate(renderTargetName.getItemStackRenderState());
                    renderTargetTextureId = renderTargetHandle.isNull() ? -1 : ((BlockStateContainerBridge)renderTargetHandle).getTextureId();
                } else {
                    renderTarget = renderBuffer.p().get(
                            renderTargetName.getItemStackRenderState().getModelIdentity());
                    int resolvedTextureId = renderTargetTextureId = renderBuffer.F().isNull() ? -1 : renderBuffer.F().getId();
                }
                if (renderTarget == null || renderTargetTextureId == -1) {
                    OpenGlBackendHolder.backend.popMatrix();
                    return;
                }
                renderTargetHandle = new MainWindow(renderTarget);
                float minU = ((MainWindow)renderTargetHandle).q();
                float minV = ((MainWindow)renderTargetHandle).r();
                if (ForgeVersion.MC_26_1.d()) {
                    maxU = ((MainWindow)renderTargetHandle).i();
                    maxV = ((MainWindow)renderTargetHandle).A();
                } else {
                    int guiScaleFactor = Minecraft.p().P();
                    int pixelOffset = 16 * guiScaleFactor;
                    int renderTargetSize = renderBuffer.L(pixelOffset);
                    maxU = minU + (float)pixelOffset / (float)renderTargetSize;
                    maxV = minV + (float)(-pixelOffset) / (float)renderTargetSize;
                }
                this.framebuffer.bind(true);
                GL11.glColorMask((boolean)true, (boolean)true, (boolean)true, (boolean)true);
                GlScissorRect previousScissorRect = BufferedGuiRenderPrimitives.scissorRect;
                BufferedGuiRenderPrimitives.scissorRect = null;
                RenderBatchBuilder batchBuilder = new RenderBatchBuilder().setTexture(new GlImageTexture(renderTargetTextureId)).addTexturedRect(0.0f, 0.0f, iconWidth, iconHeight, iconWidth, iconHeight, minU, minV, maxU, maxV, Color.WHITE);
                BufferedGuiRenderPrimitives.projectionMatrix = new RenderMatrix4f().setIdentity().setOrthographic(0.0f, iconWidth, iconHeight, 0.0f, -21000.0f, 21000.0f);
                RenderBatchManager batchManager = RenderBatchManager.getInstance();
                batchManager.queueGuiBatch(batchBuilder);
                batchManager.setFramebufferOverride(this.framebuffer.framebufferId);
                batchManager.flushGuiBatches(0.0f, false);
                batchManager.restoreFramebufferOverride();
                BufferedGuiRenderPrimitives.scissorRect = previousScissorRect;
                OpenGlBackendHolder.backend.popMatrix();
            }
        } else {
            // Pre-1.20.6 (1.16.5, 1.20.1) path: RenderItem.a can throw on 1.20.1.
            // Swallow so the framebuffer/viewport/texture restore below always runs.
            try {
                RenderItem renderItem = Minecraft.v();
                renderItem.a(itemStack, 0, 0, renderMatrixStack);
            }
            catch (Throwable throwable) {
                Vape.logThrowable(throwable);
            }
        }
        this.framebuffer.bindColorTexture();
        this.framebuffer.unbind();
        GL11.glViewport((int)viewport.get(0), (int)viewport.get(1), (int)viewport.get(2), (int)viewport.get(3));
        GL30.glBindFramebuffer((int)36160, (int)previousFramebufferId);
        GlStateManager.bindTexture(previousTextureId);
    }


    @Override
    public void renderQueued(float x, float y, int width, int height, float opacity, boolean worldSpace) {
        // A capture can legitimately leave framebuffer null/empty (mapping failure, or an
        // early return). Guard so the HUD call site never NPEs on colorTextureId every frame
        // (the per-frame lag source).
        if (this.framebuffer == null || this.framebuffer.colorTextureId <= 0) {
            return;
        }
        RenderBatchBuilder batchBuilder = new RenderBatchBuilder(VertexCoordinateMode.DEFAULT, worldSpace).setTexture(new GlImageTexture(this.framebuffer.colorTextureId)).addTexturedRect(x, y, width, height, 64.0f, 64.0f, 0.0f, 1.0f, 1.0f, 0.0f, new Color(1.0f, 1.0f, 1.0f, opacity));
        if (worldSpace) {
            RenderBatchManager.getInstance().queueWorldBatch(batchBuilder);
        } else {
            RenderBatchManager.getInstance().queueGuiBatch(batchBuilder);
        }
    }

    @Override
    public void render(float x, float y, int width, int height, float opacity) {
        this.renderQueued(x, y, width, height, opacity, false);
    }


    @Override
    public void dispose() {
        this.framebuffer.delete();
        this.framebuffer = null;
    }
}
