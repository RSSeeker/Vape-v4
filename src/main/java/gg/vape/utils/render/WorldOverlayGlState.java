package gg.vape.utils.render;

import gg.vape.wrapper.impl.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/** Saves the immediate and buffered state touched by the base-hunting overlays. */
public final class WorldOverlayGlState {
    private final boolean buffered;
    private final boolean blend, depth, cull, smooth, depthWrite;
    private final boolean texture, lighting;
    private final boolean bufferedBlend, bufferedDepth, bufferedCull, bufferedDepthWrite;
    private final int blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha;
    private final float lineWidth;

    private WorldOverlayGlState() {
        this.buffered = GuiRenderPrimitives.d();
        this.blend = GL11.glIsEnabled(3042);
        this.depth = GL11.glIsEnabled(2929);
        this.cull = GL11.glIsEnabled(2884);
        this.smooth = GL11.glIsEnabled(2848);
        this.depthWrite = GL11.glGetBoolean(2930);
        // Fixed-function texture and lighting capabilities do not exist in the
        // modern core profile. The buffered backend ignores both of them.
        this.texture = !this.buffered && GL11.glIsEnabled(3553);
        this.lighting = !this.buffered && GL11.glIsEnabled(2896);
        GlCapabilityState logical = BufferedGuiRenderPrimitives.capabilityState;
        this.bufferedBlend = logical.blendEnabled;
        this.bufferedDepth = logical.depthTestEnabled;
        this.bufferedCull = logical.cullFaceEnabled;
        this.bufferedDepthWrite = logical.depthWriteEnabled;
        this.blendSrcRgb = GL11.glGetInteger(32969);
        this.blendDstRgb = GL11.glGetInteger(32968);
        this.blendSrcAlpha = GL11.glGetInteger(32971);
        this.blendDstAlpha = GL11.glGetInteger(32970);
        this.lineWidth = GL11.glGetFloat(2849);
    }

    public static WorldOverlayGlState capture() {
        return new WorldOverlayGlState();
    }

    private static void setRawCapability(int capability, boolean enabled) {
        if (enabled) GL11.glEnable(capability);
        else GL11.glDisable(capability);
    }

    /**
     * A label also changes Minecraft's cached state through GlStateManager.
     * Restore that cache first, then restore both the raw GL values and the
     * separately tracked state captured by the buffered batch builder. The
     * batch flush sets its own blending after listeners have returned.
     */
    public void restore(boolean labelAttempted) {
        try {
            if (labelAttempted) {
                GlStateManager.r(2929, this.depth);
                GlStateManager.depthMask(this.depthWrite);
                GlStateManager.r(3042, this.blend);
                GlStateManager.r(2884, this.cull);
                if (!this.buffered) {
                    GlStateManager.r(3553, this.texture);
                    GlStateManager.r(2896, this.lighting);
                }
                GlStateManager.tryBlendFuncSeparate(this.blendSrcRgb, this.blendDstRgb,
                        this.blendSrcAlpha, this.blendDstAlpha);
            }
        } finally {
            if (this.buffered) {
                GlCapabilityState logical = BufferedGuiRenderPrimitives.capabilityState;
                logical.blendEnabled = this.bufferedBlend;
                logical.depthTestEnabled = this.bufferedDepth;
                logical.cullFaceEnabled = this.bufferedCull;
                logical.depthWriteEnabled = this.bufferedDepthWrite;
            }
            setRawCapability(3042, this.blend);
            setRawCapability(2929, this.depth);
            setRawCapability(2884, this.cull);
            setRawCapability(2848, this.smooth);
            if (!this.buffered) {
                setRawCapability(3553, this.texture);
                setRawCapability(2896, this.lighting);
            }
            GL11.glDepthMask(this.depthWrite);
            GL14.glBlendFuncSeparate(this.blendSrcRgb, this.blendDstRgb,
                    this.blendSrcAlpha, this.blendDstAlpha);
            GL11.glLineWidth(this.lineWidth);
        }
    }
}
