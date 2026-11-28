package gg.vape.ui.click.component.value;

import gg.vape.ui.click.GuiMouseEvent;
import gg.vape.ui.click.component.GuiComponent;
import gg.vape.ui.click.component.value.ColorPaletteSliderComponent;
import gg.vape.ui.click.component.value.ColorValueDropdownHueSliderComponent;
import gg.vape.ui.font.SmoothFontRenderer;
import gg.vape.unmap.ModeSelection;
import gg.vape.value.ColorValue;
import gg.vape.value.ModeValue;
import java.awt.Color;

public class ColorValueDropdownComponent
extends GuiComponent {
    private final ColorPaletteSliderComponent colorSlider;
    private final ModeValue modeValue;
    private ModeSelection previousSelection;
    private final ColorValue teamColorValue;

    @Override
    public double C() {
        return 25.0;
    }

    @Override
    public void F() {
    }

    public ColorValueDropdownComponent(ModeValue modeValue) {
        this.modeValue = modeValue;
        this.teamColorValue = ColorValue.create(null, "Team Color", new Color(189, 0, 1));
        // This color belongs to the slider, not to the live configuration.
        this.teamColorValue.setPersistenceSuppressed(true);
        this.bindValue(modeValue);
        Color[] palette = new Color[]{new Color(189, 0, 1), new Color(253, 63, 63), new Color(215, 162, 50), new Color(254, 254, 62), new Color(0, 191, 4), new Color(64, 253, 62), new Color(65, 255, 254), new Color(0, 190, 189), new Color(1, 1, 187), new Color(61, 64, 255), new Color(254, 63, 255), new Color(190, 0, 190), new Color(255, 255, 255), new Color(190, 190, 190), new Color(63, 63, 63), new Color(17, 17, 17)};
        this.colorSlider = new ColorValueDropdownHueSliderComponent(this, "Team color", this.teamColorValue, palette);
        this.colorSlider.setDisabledOverlayColor(this.getDisabledOverlayColor());
        this.colorSlider.setSeparatedSegments(true);
        this.colorSlider.setToolTips(null);
        this.addChildren(this.colorSlider);
    }

    @Override
    public void I() {
    }

    @Override
    public void H() {
        this.onDisable();
        this.colorSlider.K(this.G$src$D$1b2f02a());
        this.colorSlider.S(this.n());
        SmoothFontRenderer smoothFontRenderer = this.getFontRenderer(0.7);
        String selectionName = ((ModeSelection)this.modeValue.getValue()).getName().substring(2);
        smoothFontRenderer.d(selectionName, this.G$src$D$1b2f02a() + this.A() - 5.0 - smoothFontRenderer.N(selectionName), this.n() + 5.0, ColorValueDropdownComponent.J.Z);
    }

    @Override
    public double x() {
        return 110.0;
    }

    private void synchronizeSliderSelection() {
        ModeSelection currentSelection = (ModeSelection)this.modeValue.getValue();
        if (this.previousSelection == null || !this.previousSelection.equals(currentSelection)) {
            this.colorSlider.selectPaletteIndex(this.modeValue.getSelectedIndex());
            this.colorSlider.synchronizeSelectionFromValue();
            this.previousSelection = currentSelection;
        }
    }

    @Override
    public void u() {
        this.synchronizeSliderSelection();
        if (this.modeValue.getSelectedIndex() != this.colorSlider.getSelectedIndex()) {
            // A user selection should save for a live value, but a detached
            // snapshot remains suppressed throughout the editor's lifetime.
            this.modeValue.setSelectedIndex(this.colorSlider.getSelectedIndex());
        }
        this.previousSelection = (ModeSelection)this.modeValue.getValue();
    }


    @Override
    public void g(GuiMouseEvent guiMouseEvent) {
    }
}

