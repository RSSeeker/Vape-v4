package gg.vape.ui.click.animation;

import gg.vape.Vape;
import gg.vape.ui.click.animation.ColorAnimation;
import java.awt.Color;

public class ThemeColorAnimation
extends ColorAnimation {
    public ThemeColorAnimation(double d, Color color) {
        super(d, color, Vape.INSTANCE.getClientSettings().guiColor.getMutableColor());
    }

    @Override
    public Color getInterpolatedColor() {
        super.setEndColor(gg.vape.ui.click.frame.impl.inevitable.InevitableRenderScope.isActive()
                ? Color.WHITE : Vape.INSTANCE.getClientSettings().guiColor.getMutableColor());
        return super.getInterpolatedColor();
    }
}
