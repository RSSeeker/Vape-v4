package gg.vape.ui.click.frame.impl.inevitable;

import gg.vape.ui.click.component.GuiClickListener;
import gg.vape.ui.click.component.gui.InteractiveComponent;
import gg.vape.ui.font.FontOption;
import gg.vape.ui.font.SmoothFontRenderer;
import gg.vape.utils.render.GuiRenderPrimitives;
import gg.vape.utils.render.RenderUtils;
import java.awt.Color;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Interactive profile selection or settings shortcut. */
public final class InevitableActionRow extends InteractiveComponent {
    private final Supplier<String> label;
    private final BooleanSupplier selected;
    private final boolean literalLabel;

    public InevitableActionRow(Supplier<String> label, BooleanSupplier selected, Runnable action, Runnable secondary) {
        this(label, selected, action, secondary, false);
    }

    public InevitableActionRow(Supplier<String> label, BooleanSupplier selected, Runnable action, Runnable secondary, boolean literalLabel) {
        this.label = label;
        this.selected = selected;
        this.literalLabel = literalLabel;
        this.addClickListener(new GuiClickListener() {
            @Override public void onPrimaryClick() { action.run(); }
            @Override public void onSecondaryClick() { if (secondary != null) secondary.run(); }
        });
    }

    @Override public double x() { return InevitableLayout.PANEL_WIDTH; }
    @Override public double C() { return 20.0; }
    @Override public void I() {}
    @Override public void F() {}
    @Override public void u() {}

    @Override public void H() {
        boolean active = this.selected.getAsBoolean(), hovered = this.w$src$Z$e457mb();
        double x = this.G$src$D$1b2f02a(), y = this.n();
        InevitablePanelStyle.row(x, y, this.A(), this.L(), active, hovered);
        SmoothFontRenderer font = this.getFontRenderer(0.85);
        String text = this.label.get();
        if (text == null) text = "";
        Color color = active ? InevitablePanelStyle.ACTIVE_TEXT : (hovered ? InevitablePanelStyle.HOVER_TEXT : InevitablePanelStyle.DISABLED_TEXT);
        RenderUtils.m(x + 8.0, y, Math.max(0.0, this.A() - 25.0), this.L());
        boolean previousTranslation = FontOption.isTranslationDisabled();
        if (this.literalLabel) FontOption.setTranslationDisabled(true);
        try {
            font.d(text, x + 8.0, y + this.L() / 2.0 - font.d(text) / 2.0, color);
        } finally {
            FontOption.setTranslationDisabled(previousTranslation);
            RenderUtils.T();
        }
        Color arrow = active ? InevitablePanelStyle.ACTIVE_TEXT : new Color(92, 92, 92);
        double ax = x + this.A() - 10.0, ay = y + this.L() / 2.0;
        GuiRenderPrimitives.u(ax - 2.0, ay - 2.0, ax, ay, 0.7f, arrow);
        GuiRenderPrimitives.u(ax, ay, ax - 2.0, ay + 2.0, 0.7f, arrow);
    }
}
