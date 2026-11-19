package gg.vape.script;

import gg.vape.event.impl.EventRender2D;
import gg.vape.utils.render.GuiRenderPrimitives;
import gg.vape.wrapper.impl.FontRenderer;
import java.awt.Color;

/**
 * 一次 {@code onRender2D} 回调期间的绘制上下文。
 *
 * <p>脚本里的 {@code draw.text/rect/hline} 只有在渲染回调中被调用才有意义，所以这里用
 * {@link ThreadLocal} 保存「当前正在渲染」的上下文：回调开始时 {@link #begin} 设置，
 * 结束时 {@link #end} 清除。不在回调里调用时 {@link #current()} 返回 {@code null}，
 * {@link ScriptApi} 会记一条错误日志而不是把绘制命令塞进别的线程。</p>
 */
public final class ScriptDrawContext {
    private static final ThreadLocal<ScriptDrawContext> CURRENT = new ThreadLocal<ScriptDrawContext>();

    private final int displayWidth;
    private final int displayHeight;
    private final FontRenderer fontRenderer;

    private ScriptDrawContext(EventRender2D event) {
        this.displayWidth = event.getDisplayWidth();
        this.displayHeight = event.getDisplayHeight();
        this.fontRenderer = event.getFontRenderer();
    }

    public static ScriptDrawContext begin(EventRender2D event) {
        ScriptDrawContext context = new ScriptDrawContext(event);
        CURRENT.set(context);
        return context;
    }

    public static void end() {
        CURRENT.remove();
    }

    public static ScriptDrawContext current() {
        return CURRENT.get();
    }

    public int getDisplayWidth() {
        return this.displayWidth;
    }

    public int getDisplayHeight() {
        return this.displayHeight;
    }

    public void drawText(double x, double y, String text, int argb) {
        if (text == null || text.isEmpty() || this.fontRenderer == null) {
            return;
        }
        this.fontRenderer.drawStringWithShadow(text, x, y, argb);
    }

    public void drawRect(double x, double y, double width, double height, int argb) {
        if (width <= 0.0 || height <= 0.0) {
            return;
        }
        GuiRenderPrimitives.V(x, y, width, height, new Color(argb, true));
    }

    /**
     * 水平线：{@code GuiRenderPrimitives.L} 的语义是「从 {@code (x, y)} 起画 length 像素」。
     */
    public void drawHorizontalLine(double x, double y, double length, int argb) {
        if (length <= 0.0) {
            return;
        }
        GuiRenderPrimitives.L(x, y, length, new Color(argb, true));
    }

    public double measureText(String text) {
        if (text == null || text.isEmpty() || this.fontRenderer == null) {
            return 0.0;
        }
        return this.fontRenderer.getStringWidth(text);
    }

    public double fontHeight() {
        if (this.fontRenderer == null) {
            return 0.0;
        }
        return 2.0 * this.fontRenderer.getHalfFontHeight("A");
    }
}
