package gg.vape.ui.click.frame.impl.inevitable;

import gg.vape.Vape;
import gg.vape.ui.font.SmoothFontRenderer;
import gg.vape.utils.render.GuiRenderPrimitives;
import java.awt.Color;

/** The click GUI's neutral palette; configured HUD colors are not changed. */
public final class InevitablePanelStyle {
    public static final Color BLACK = new Color(18, 19, 22);
    public static final Color WHITE = Color.WHITE;
    public static final Color BORDER = new Color(44, 44, 44);
    public static final Color DISABLED_TEXT = new Color(112, 112, 112);
    public static final Color HOVER_TEXT = new Color(190, 190, 190);
    public static final Color ACTIVE_BACKGROUND = new Color(245, 241, 228);
    public static final Color ACTIVE_TEXT = new Color(25, 25, 25);
    public static final Color HOVER_BACKGROUND = new Color(29, 30, 33);
    public static final Color VALUE_BACKGROUND = new Color(14, 14, 14);
    public static final Color VALUE_CHILD_BACKGROUND = new Color(20, 20, 20);
    public static final Color SEPARATOR = new Color(255, 255, 255, 28);
    private InevitablePanelStyle() {}

    public static void panel(double x, double y, double width, double height) {
        GuiRenderPrimitives.p(x, y, width, height, BLACK, false, 4.0f, 1.0f, 0.0f, BLACK, 15);
        GuiRenderPrimitives.P(x, y, width, height, BORDER, 4.0f, 0.65f, 1.0f);
    }

    public static void header(String title, String icon, double x, double y, double width, double height) {
        SmoothFontRenderer font = Vape.INSTANCE.getFontManager().W(0.78, true);
        GuiRenderPrimitives.F(icon, x + 10.0, y + height / 2.0, 7.0, 7.0, new Color(145, 145, 145));
        font.d(title, x + 18.0, y + height / 2.0 - font.d(title) / 2.0, WHITE);
        for (int row = 0; row < 3; row++) {
            GuiRenderPrimitives.C(x + width - 9.0, y + height / 2.0 - 3.0 + row * 2.0, 0.8, 0.8, DISABLED_TEXT);
            GuiRenderPrimitives.C(x + width - 6.5, y + height / 2.0 - 3.0 + row * 2.0, 0.8, 0.8, DISABLED_TEXT);
        }
        GuiRenderPrimitives.C(x + 4.0, y + height - 0.5, width - 8.0, 0.35, SEPARATOR);
    }

    public static void row(double x, double y, double width, double height, boolean active, boolean hovered) {
        if (active || hovered) {
            GuiRenderPrimitives.B(x + 4.0, y + 1.5, width - 8.0, height - 3.0,
                    active ? ACTIVE_BACKGROUND : HOVER_BACKGROUND, 3.0f);
        }
    }

    public static void wordmark(double x, double y) {
        SmoothFontRenderer font = Vape.INSTANCE.getFontManager().W(1.05, true);
        font.d(gg.vape.brand.InevitableBrand.wordmark(), x, y, WHITE);
    }
}
