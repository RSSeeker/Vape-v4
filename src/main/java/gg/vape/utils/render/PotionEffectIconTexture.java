package gg.vape.utils.render;

import gg.vape.utils.render.BufferedGuiRenderPrimitives;
import gg.vape.utils.render.GlFramebuffer;
import gg.vape.utils.render.GlImageTexture;
import gg.vape.utils.render.GlScissorRect;
import gg.vape.utils.render.PotionEffectIconRenderBackend;
import gg.vape.utils.render.RenderBatchBuilder;
import gg.vape.utils.render.RenderBatchManager;
import gg.vape.utils.render.VertexCoordinateMode;
import gg.vape.wrapper.Wrapper;
import gg.vape.wrapper.impl.ForgeVersion;
import gg.vape.wrapper.impl.GlStateManager;
import gg.vape.wrapper.impl.Holder;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.PotionEffect;
import gg.vape.wrapper.impl.ResourceLocation;
import gg.vape.wrapper.impl.ResourceLocationConstantPair;
import gg.vape.wrapper.impl.Screen;
import gg.vape.wrapper.impl.StatusEffectSpriteUploader;
import gg.vape.wrapper.impl.TextureAtlas;
import gg.vape.wrapper.impl.TextureAtlasSprite;
import gg.vape.wrapper.impl.TextureManager;
import gg.vape.wrapper.impl.TextureObject;
import java.awt.Color;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

public class PotionEffectIconTexture
implements PotionEffectIconRenderBackend {
    private GlFramebuffer framebuffer;



    @Override
    public void renderQueued(float x, float y, int width, int height, float opacity, boolean worldSpace) {
        // A capture can legitimately leave framebuffer null (effect not resolvable
        // on this version, or capture threw). Guard here so the HUD call site never
        // NPEs on colorTextureId every frame and logs it (the ~7.7 FPS lag source).
        if (this.framebuffer == null || this.framebuffer.colorTextureId <= 0) {
            return;
        }
        RenderBatchBuilder batchBuilder = new RenderBatchBuilder(VertexCoordinateMode.DEFAULT, worldSpace).setTexture(new GlImageTexture(this.framebuffer.colorTextureId)).addTexturedRect(x, y, width, height, 18.0f, 18.0f, 0.0f, 1.0f, 1.0f, 0.0f, new Color(1.0f, 1.0f, 1.0f, opacity));
        if (worldSpace) {
            RenderBatchManager.getInstance().queueWorldBatch(batchBuilder);
        } else {
            RenderBatchManager.getInstance().queueGuiBatch(batchBuilder);
        }
    }

    private TextureAtlasSprite resolveEffectSprite(PotionEffect effect) {
        TextureAtlasSprite sprite;
        Holder effectHolder = effect.t();
        if (ForgeVersion.MC_1_21_10.d()) {
            TextureAtlas textureAtlas = Minecraft.x().getAtlas(ResourceLocationConstantPair.getGui());
            sprite = textureAtlas.getSprite(Screen.getMobEffectSprite(effectHolder));
        } else if (ForgeVersion.MC_1_21_6.d()) {
            sprite = Minecraft.T().getSprite(Screen.getMobEffectSprite(effectHolder));
        } else {
            StatusEffectSpriteUploader spriteUploader = StatusEffectSpriteUploader.getPotionSprites();
            sprite = spriteUploader.getSprite(effectHolder);
        }
        return sprite;
    }

    private static TextureObject getSpriteTexture(TextureAtlasSprite sprite) {
        try {
            Wrapper textureManager;
            ResourceLocation textureLocation;
            if (ForgeVersion.MC_1_20_6.d()) {
                textureLocation = sprite.getAtlasLocation();
            } else {
                // Pre-1.20.6 (1.16.5, 1.20.1): the atlas underneath the sprite can be
                // unresolvable (the legacy field no longer resolves on 1.20.1). Let the
                // caller's null-check skip the icon instead of throwing a per-frame NPE.
                Wrapper textureAtlas = new TextureAtlas(sprite.getContentsOrAtlasTexture());
                textureLocation = ((TextureAtlas)textureAtlas).getTextureLocation();
            }
            textureManager = Minecraft.getTextureManager();
            return ((TextureManager)textureManager).getTexture(textureLocation);
        }
        catch (Throwable throwable) {
            return null;
        }
    }

    @Override
    public void dispose() {
        // A capture can leave framebuffer null (sprite/texture not resolvable, or
        // capture threw after allocating). With always-cache-on-failure such a
        // renderer is now placed in the cache, so clear() -> dispose() must not NPE.
        if (this.framebuffer != null) {
            this.framebuffer.delete();
            this.framebuffer = null;
        }
    }

    @Override
    public void render(float x, float y, int width, int height, float opacity) {
        this.renderQueued(x, y, width, height, opacity, false);
    }

    @Override
    public void capture(PotionEffect effect) {
        int iconWidth = 18;
        int iconHeight = 18;
        // Resolve and guard the sprite, its texture and its texture coordinates
        // BEFORE mutating any GL or shared-batch state. On 1.21.11 these can be
        // null; resolving them here lets a missing effect return cleanly without
        // touching the framebuffer/viewport/scissor/matrix the render path uses.
        // This capture runs in the middle of the HUD/GUI pass, so an early return
        // must not leave the shared BufferedGuiRenderPrimitives matrices changed.
        TextureAtlasSprite effectSprite = this.resolveEffectSprite(effect);
        if (effectSprite == null) {
            return;
        }
        TextureObject texture = PotionEffectIconTexture.getSpriteTexture(effectSprite);
        float[] textureCoordinates = effectSprite.getTextureCoordinates();
        int textureId = texture != null ? texture.getId() : -1;
        if (texture == null || textureId <= 0 || textureCoordinates == null || textureCoordinates.length < 4) {
            return;
        }
        // Save the caller's shared GUI matrices before any flush/draw. Both
        // flushGuiBatches calls below would otherwise clobber projectionMatrix to
        // the world projection, which the GUI batch renderer reads at draw time:
        // that is exactly the legit-mode "设置页集体偏移" (settings page offset),
        // the truncated potion text and the mis-positioned 2D box. Mirror the
        // save/restore in Post117EntityModelFramebufferRenderer.
        RenderMatrix4f previousProjectionMatrix = BufferedGuiRenderPrimitives.projectionMatrix;
        RenderMatrix4f previousViewMatrix = BufferedGuiRenderPrimitives.viewMatrix;
        RenderMatrixStack previousMatrixStack = BufferedGuiRenderPrimitives.matrixStack;
        RenderBatchManager batchManager = RenderBatchManager.getInstance();
        // Flush any pending GUI batches WITHOUT resetting the shared projection so
        // the caller's GUI matrices survive the flush untouched.
        batchManager.flushGuiBatches(0.0f, false);
        int previousFramebufferId = GL11.glGetInteger((int)36006);
        int previousTextureId = GL11.glGetInteger((int)32873);
        boolean scissorEnabled = GL11.glIsEnabled((int)3089);
        ByteBuffer viewportBytes = ByteBuffer.allocateDirect(64);
        viewportBytes.order(ByteOrder.nativeOrder());
        IntBuffer viewport = viewportBytes.asIntBuffer();
        gg.vape.wrapper.impl.GL11.X(2978, viewport);
        GlScissorRect previousScissorRect = BufferedGuiRenderPrimitives.scissorRect;
        GlFramebuffer createdFramebuffer = null;
        boolean framebufferOverrideSet = false;
        boolean completed = false;
        try {
            if (scissorEnabled) {
                GL11.glDisable((int)3089);
            }
            createdFramebuffer = new GlFramebuffer(iconWidth, iconHeight, true);
            this.framebuffer = createdFramebuffer;
            createdFramebuffer.bind(true);
            GlStateManager.enableDepth();
            GlStateManager.enableBlend();
            GL11.glClearColor((float)0.0f, (float)0.0f, (float)0.0f, (float)0.0f);
            GL11.glClear((int)16384);
            GL11.glClear((int)256);
            // Mirror Post117ItemIconFramebufferRenderer.capture: put the batch under an
            // icon-space ortho projection (0..iconWidth x 0..iconHeight) so the sprite
            // quad fills the whole 18x18 framebuffer. Without this the batch renderer
            // (which reads BufferedGuiRenderPrimitives.projectionMatrix at flush time,
            // RenderBatchBuffer.bindResources) draws the quad under the caller's
            // full-window GUI/world projection and collapses it to a sliver -> empty ring.
            // The translate/scale on OpenGlBackendHolder.backend does NOT feed the batch
            // shader, so it never affected the icon (that was a no-op).
            BufferedGuiRenderPrimitives.projectionMatrix = new RenderMatrix4f().setIdentity().setOrthographic(0.0f, iconWidth, iconHeight, 0.0f, -21000.0f, 21000.0f);
            BufferedGuiRenderPrimitives.viewMatrix = new RenderMatrix4f().setIdentity();
            BufferedGuiRenderPrimitives.matrixStack = new RenderMatrixStack();
            BufferedGuiRenderPrimitives.scissorRect = null;
            RenderBatchBuilder batchBuilder = new RenderBatchBuilder().setTexture(new GlImageTexture(textureId)).addTexturedRect(0.0f, 0.0f, iconWidth, iconHeight, iconWidth, iconHeight, textureCoordinates[0], textureCoordinates[2], textureCoordinates[1], textureCoordinates[3], Color.WHITE);
            batchManager.queueGuiBatch(batchBuilder);
            batchManager.setFramebufferOverride(createdFramebuffer.framebufferId);
            framebufferOverrideSet = true;
            batchManager.flushGuiBatches(0.0f, false);
            createdFramebuffer.bindColorTexture();
            completed = true;
        }
        finally {
            BufferedGuiRenderPrimitives.scissorRect = previousScissorRect;
            if (scissorEnabled) {
                GL11.glEnable((int)3089);
            }
            if (framebufferOverrideSet) {
                batchManager.restoreFramebufferOverride();
            }
            GL30.glBindFramebuffer((int)36160, (int)previousFramebufferId);
            GlStateManager.bindTexture(previousTextureId);
            GL11.glViewport((int)viewport.get(0), (int)viewport.get(1), (int)viewport.get(2), (int)viewport.get(3));
            if (!completed && createdFramebuffer != null) {
                createdFramebuffer.delete();
                this.framebuffer = null;
            }
            // Restore the caller's shared GUI matrices so the rest of the GUI
            // (settings page, subsequent HUD text/elements) renders correctly and
            // is not left under the world projection.
            BufferedGuiRenderPrimitives.projectionMatrix = previousProjectionMatrix;
            BufferedGuiRenderPrimitives.viewMatrix = previousViewMatrix;
            BufferedGuiRenderPrimitives.matrixStack = previousMatrixStack;
        }
    }
}
