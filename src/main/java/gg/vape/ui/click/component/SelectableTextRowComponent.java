package gg.vape.ui.click.component;

import gg.vape.ui.click.GuiMouseEvent;
import gg.vape.ui.click.component.GuiClickListener;
import gg.vape.ui.click.component.GuiComponent;
import gg.vape.ui.click.component.SquareIconButtonComponent;
import gg.vape.ui.click.component.TruncatedTextComponent;
import gg.vape.ui.font.FontOption;
import gg.vape.ui.font.SmoothFontRenderer;
import gg.vape.utils.render.GuiRenderPrimitives;
import gg.vape.value.ToggleableListEntry;
import java.awt.Color;

public class SelectableTextRowComponent
extends GuiComponent {
    private float rowHorizontalInset = 5.0f;
    private float indicatorOffsetY = 0.0f;
    private boolean locallySelected = true;
    private TruncatedTextComponent rowTextLabel;
    private static final long DEFAULT_INDICATOR_SIZE_SEED = 5225434907995537413L;
    private ToggleableListEntry selectionTarget;
    private String indicatorIcon;
    private String rowText;
    private int legacyIndex;
    private SquareIconButtonComponent deleteButton;
    private Color selectedIndicatorColor;
    private int indicatorSize = (int)DEFAULT_INDICATOR_SIZE_SEED;
    private Color rowBackgroundColor;

    public void setSelected(boolean selected) {
        this.locallySelected = selected;
    }

    public SelectableTextRowComponent setIndicatorOffsetY(float indicatorOffsetY) {
        this.indicatorOffsetY = indicatorOffsetY;
        return this;
    }

    public SelectableTextRowComponent setDeleteActionListener(GuiClickListener deleteActionListener) {
        this.deleteButton.addClickListener(deleteActionListener);
        return this;
    }

    public SelectableTextRowComponent setIndicatorSize(int indicatorSize) {
        this.indicatorSize = indicatorSize;
        return this;
    }

    public boolean isSelected() {
        return this.selectionTarget != null ? this.selectionTarget.isEnabled() : this.locallySelected;
    }

    public SquareIconButtonComponent getDeleteButton() {
        return this.deleteButton;
    }

    public SelectableTextRowComponent(Color selectedColor, String text) {
        this(selectedColor, text, -1);
    }

    public SelectableTextRowComponent setSelectionTarget(ToggleableListEntry selectionTarget) {
        this.selectionTarget = selectionTarget;
        return this;
    }

    public String getText() {
        return this.rowText;
    }


    public SelectableTextRowComponent(Color selectedColor, String text, String indicatorIcon) {
        this(selectedColor, text, -1);
        this.indicatorIcon = indicatorIcon;
    }

    public boolean isHovered() {
        return this.w$src$Z$e457mb();
    }

    @Override
    public void g(GuiMouseEvent mouseEvent) {
        this.locallySelected = !this.locallySelected;
        if (this.selectionTarget != null) {
            this.selectionTarget.toggleEnabled();
        }
    }

    public SelectableTextRowComponent(Color selectedColor, String text, int legacyIndex) {
        this.deleteButton = new SquareIconButtonComponent("newclose");
        this.rowBackgroundColor = SelectableTextRowComponent.J.m;
        this.selectedIndicatorColor = selectedColor;
        this.rowText = text;
        this.legacyIndex = legacyIndex;
        this.rowTextLabel = new TruncatedTextComponent(this.getText(), "...", this.A() - 30.0, 0.9, SelectableTextRowComponent.J.Z, false);
        this.rowTextLabel.setAcceptsMouseInput(false);
        this.addChildren(this.rowTextLabel, this.deleteButton);
    }

    @Override
    public void c() {
        // List entries are user-configured values; render them verbatim
        // instead of passing them through the GUI translation table.
        boolean previous = FontOption.isTranslationDisabled();
        FontOption.setTranslationDisabled(true);
        try {
            super.c();
        }
        finally {
            FontOption.setTranslationDisabled(previous);
        }
    }

    @Override
    public void H() {
        SmoothFontRenderer smoothFontRenderer = this.getFontRenderer(0.9);
        double textHeight = smoothFontRenderer.d(this.getText());
        Color resolvedTextColor;
        if (this.isSelected()) {
            resolvedTextColor = this.isHovered() ? SelectableTextRowComponent.J.A : SelectableTextRowComponent.J.Z;
        } else {
            resolvedTextColor = this.isHovered() ? SelectableTextRowComponent.J.Z : SelectableTextRowComponent.J.h;
        }
        Color hoverOverlayColor = SelectableTextRowComponent.J.i;
        Color inactiveIndicatorColor = SelectableTextRowComponent.J.W;
        GuiRenderPrimitives.d(this.G$src$D$1b2f02a() + this.getRowHorizontalInset(), this.n() + 1.0, this.A() - this.getRowHorizontalInset() * 2.0, this.L() - 2.0, this.rowBackgroundColor);
        if (this.isHovered()) {
            GuiRenderPrimitives.d(this.G$src$D$1b2f02a() + this.getRowHorizontalInset() + 0.5, this.n() + 1.5, this.A() - this.getRowHorizontalInset() * 2.0 - 1.0, this.L() - 3.0, hoverOverlayColor);
        }
        if (this.isSelected()) {
            if (this.indicatorIcon != null) {
                GuiRenderPrimitives.F(this.indicatorIcon, this.G$src$D$1b2f02a() + this.getRowHorizontalInset() + 5.0 + (double)(this.indicatorSize / 2), this.n() + this.L() / 2.0 - (double)(this.indicatorSize / 2) + (double)(this.indicatorSize / 2) + (double)this.indicatorOffsetY, (double)this.indicatorSize, this.indicatorSize, this.selectedIndicatorColor);
            } else {
                GuiRenderPrimitives.V((float)(this.G$src$D$1b2f02a() + this.getRowHorizontalInset() + 5.0), (float)(this.n() + this.L() / 2.0 - (double)(this.indicatorSize / 2) + (double)this.indicatorOffsetY), this.indicatorSize, 0.5, this.selectedIndicatorColor);
            }
        } else if (this.indicatorIcon != null) {
            GuiRenderPrimitives.F(this.indicatorIcon, this.G$src$D$1b2f02a() + this.getRowHorizontalInset() + 5.0 + (double)(this.indicatorSize / 2), this.n() + this.L() / 2.0 - (double)(this.indicatorSize / 2) + (double)(this.indicatorSize / 2) + (double)this.indicatorOffsetY, (double)this.indicatorSize, this.indicatorSize, inactiveIndicatorColor);
        } else {
            GuiRenderPrimitives.m((float)(this.G$src$D$1b2f02a() + this.getRowHorizontalInset() + 5.0), (float)(this.n() + this.L() / 2.0 - (double)(this.indicatorSize / 2) + (double)this.indicatorOffsetY), this.indicatorSize, 1.0f, 0.5f, inactiveIndicatorColor);
        }
        this.rowTextLabel.setTextColor(resolvedTextColor);
        this.rowTextLabel.setMaxWidth(this.A() - 30.0);
        this.rowTextLabel.K(this.G$src$D$1b2f02a() + this.getRowHorizontalInset() + 15.0);
        this.rowTextLabel.S(this.n() + this.L() / 2.0 - textHeight / 2.0);
        this.deleteButton.K(this.G$src$D$1b2f02a() + this.A() - this.getRowHorizontalInset() - 5.0 - 8.0);
        this.deleteButton.S(this.n());
        this.deleteButton.Y(this.L());
    }

    public SelectableTextRowComponent setBackgroundColor(Color backgroundColor) {
        this.rowBackgroundColor = backgroundColor;
        return this;
    }

    @Override
    public double C() {
        return 17.5;
    }

    public void setHorizontalInset(float horizontalInset) {
        this.rowHorizontalInset = horizontalInset;
    }

    public void setText(String text) {
        this.rowText = text;
        this.rowTextLabel.setText(text);
    }

    public void setIndicatorIcon(String indicatorIcon) {
        this.indicatorIcon = indicatorIcon;
    }

    @Override
    public double x() {
        return 110.0;
    }

    public void setSelectedIndicatorColor(Color selectedIndicatorColor) {
        this.selectedIndicatorColor = selectedIndicatorColor;
    }

    private double getRowHorizontalInset() {
        return this.rowHorizontalInset;
    }
}

