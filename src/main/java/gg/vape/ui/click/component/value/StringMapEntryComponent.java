package gg.vape.ui.click.component.value;

import gg.vape.ui.click.GuiMouseEvent;
import gg.vape.ui.click.component.GuiClickListener;
import gg.vape.ui.click.component.GuiComponent;
import gg.vape.ui.click.component.SquareIconButtonComponent;
import gg.vape.ui.font.FontOption;
import gg.vape.ui.font.SmoothFontRenderer;
import gg.vape.utils.render.GuiRenderPrimitives;
import java.awt.Color;

public class StringMapEntryComponent
extends GuiComponent {
    private int legacyIndex = (int)LEGACY_INDEX_SEED;
    private boolean selected = true;
    private static final String REMOVE_ICON = "newclose";
    private SquareIconButtonComponent removeButton = new SquareIconButtonComponent(REMOVE_ICON);
    private String valueText;
    private boolean hovered;
    private String keyText;
    private static final long LEGACY_INDEX_SEED = -3947651445599240187L;

    public StringMapEntryComponent setRemoveClickListener(GuiClickListener guiClickListener) {
        this.removeButton.addClickListener(guiClickListener);
        return this;
    }

    @Override
    public double C() {
        return 17.5;
    }

    @Override
    public double x() {
        return 110.0;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    @Override
    public void c() {
        // Map keys and values are user data, not translation keys.
        boolean previous = FontOption.isTranslationDisabled();
        FontOption.setTranslationDisabled(true);
        try {
            super.c();
        } finally {
            FontOption.setTranslationDisabled(previous);
        }
    }

    @Override
    public void H() {
        SmoothFontRenderer smoothFontRenderer = this.getFontRenderer(0.8);
        Color color = this.hovered ? StringMapEntryComponent.J.A : StringMapEntryComponent.J.Z;
        GuiRenderPrimitives.d(this.G$src$D$1b2f02a() + 2.0, this.n() + 1.0, this.A() - 10.0, this.L() - 2.0, StringMapEntryComponent.J.m);
        if (this.hovered) {
            GuiRenderPrimitives.d(this.G$src$D$1b2f02a() + 2.0, this.n() + 1.5, this.A() - 10.0 - 1.0, this.L() - 3.0, StringMapEntryComponent.J.i);
        }
        smoothFontRenderer.d(this.keyText, this.G$src$D$1b2f02a() + 8.0, this.n() + 3.0, color);
        smoothFontRenderer.d(this.valueText, this.G$src$D$1b2f02a() + 8.0, this.n() + 9.0, color.darker());
        this.removeButton.K(this.G$src$D$1b2f02a() + this.A() - 22.0);
        this.removeButton.S(this.n());
        this.removeButton.Y(this.L());
    }

    @Override
    public void I() {
    }

    @Override
    public void u() {
        if (this.hovered && !this.w$src$Z$e457mb()) {
            this.hovered = false;
        }
    }

    public SquareIconButtonComponent getRemoveButton() {
        return this.removeButton;
    }

    @Override
    public void F() {
        this.hovered = true;
    }

    public String getKeyText() {
        return this.keyText;
    }

    public boolean isHovered() {
        return this.hovered;
    }

    @Override
    public void g(GuiMouseEvent guiMouseEvent) {
        this.selected = !this.selected;
    }

    public StringMapEntryComponent(String keyText, String valueText) {
        this.keyText = keyText;
        this.valueText = valueText;
        this.addChildren(this.removeButton);
    }

}

