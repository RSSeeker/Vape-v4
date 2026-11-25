package gg.vape.compat;

import gg.vape.Vape;
import gg.vape.event.EventHandler;
import gg.vape.event.EventListener;
import gg.vape.event.impl.EventFramePresent;
import gg.vape.event.impl.EventRender2D;
import gg.vape.utils.render.GuiRenderPrimitives;
import gg.vape.utils.render.RenderBatchManager;
import gg.vape.wrapper.impl.ForgeVersion;
import gg.vape.wrapper.impl.Minecraft;
import org.lwjgl.opengl.GL11;

/**
 * WalksyLib 兼容层。
 *
 * WalksyLib 在 GuiRenderer.render() 返回时注入自己的收尾逻辑（
 * MsdfGuiRendererMixin#walksylib$flushMsdfText -> WLRenderSystem.flushText /
 * finishGuiFrame）：它用自建管线把顶部玻璃层和 MSDF 文字重新合成一帧，
 * 结果是把我们在同一个 render() 里画好的 ClickGUI 整块覆盖掉——表现为
 * 右 Shift 后鼠标已释放但界面看不见。
 *
 * 处理方式：检测到 WalksyLib 时不再在 GuiRenderer.render 阶段绘制，
 * 改为等原版把主渲染目标 blit 到屏幕 framebuffer（EventFramePresent）
 * 之后再补画一次 HUD 与 GUI，此时画面已定稿，不会再被它清掉。
 */
public final class WalksyLibCompat
implements EventListener {
    private static final String WALKSY_LIB_MARKER = "main.walksy.lib.core.render.WLRenderSystem";
    private static boolean detectionDone;
    private static boolean detected;
    private static boolean deferredPass;

    /**
     * 检测到 WalksyLib，且当前版本落在它的帧末注入生效区间（1.21.6 起、26.2 之前）。
     */
    public static boolean needsDeferredGui() {
        if (!ForgeVersion.MC_1_21_6.d() || ForgeVersion.MC_26_2.d()) {
            return false;
        }
        return isPresent();
    }

    public static boolean isPresent() {
        if (!detectionDone) {
            detectionDone = true;
            try {
                Class.forName(WALKSY_LIB_MARKER, false, WalksyLibCompat.class.getClassLoader());
                detected = true;
                Vape.debugLog("[WalksyLib] detected, deferred GUI drawing enabled");
            }
            catch (Throwable ignored) {
                detected = false;
            }
        }
        return detected;
    }

    public static boolean isDeferredPass() {
        return deferredPass;
    }

    @EventHandler
    public void onFramePresent(EventFramePresent event) {
        if (!needsDeferredGui() || deferredPass) {
            return;
        }
        RenderBatchManager batchManager = null;
        deferredPass = true;
        try {
            batchManager = RenderBatchManager.getInstance();
            // blitToScreen 之后默认 framebuffer 0 上就是最终画面；批次默认会把
            // 绘制重新绑到主渲染目标，那样补画的内容又会被留在纹理里看不见。
            batchManager.setFramebufferOverride(0);
            GL11.glViewport(0, 0, Minecraft.J(), Minecraft.h());
            GL11.glDisable(2929);
            GL11.glDisable(2884);
            EventRender2D.createAtFramePresent();
        }
        catch (Throwable throwable) {
            Vape.logThrowable(throwable);
        }
        finally {
            deferredPass = false;
            if (batchManager != null) {
                try {
                    batchManager.restoreFramebufferOverride();
                }
                catch (Throwable ignored) {
                }
            }
        }
    }

    /** 便于诊断：当前是否走在帧末补画路径上。 */
    public static String describe() {
        return "present=" + isPresent() + " needsDeferred=" + needsDeferredGui() + " guiPrimitives=" + GuiRenderPrimitives.d();
    }
}
