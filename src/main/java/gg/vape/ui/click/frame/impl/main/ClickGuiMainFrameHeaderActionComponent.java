package gg.vape.ui.click.frame.impl.main;

import gg.vape.ui.click.component.GuiComponent;
import gg.vape.utils.render.ImageRenderer;
import java.awt.Color;

public class ClickGuiMainFrameHeaderActionComponent
extends GuiComponent {
    private float a = 8.0f;

    public float a$src$F$1db460d() {
        return this.a;
    }

    public ClickGuiMainFrameHeaderActionComponent(float f) {
        this.a = f;
    }

    public void y(float f) {
        this.a = f;
    }

    public ClickGuiMainFrameHeaderActionComponent() {
    }

    @Override
    public void H() {
        float f = (float)ImageRenderer.getImageWidth("vapelogo") / (this.a * 0.62f);
        float f2 = (float)ImageRenderer.getImageHeight("vapelogo") / (this.a * 0.62f);
        float f3 = (float)ImageRenderer.getImageWidth("v4") / this.a;
        float f4 = (float)ImageRenderer.getImageHeight("v4") / this.a;
        ImageRenderer.drawImage(Color.WHITE, (float)this.G$src$D$1b2f02a() + f + 1.0f, (float)this.n(), "v4", f3, f4, false);
        ImageRenderer.drawImage(Color.WHITE, (float)this.G$src$D$1b2f02a(), (float)this.n(), "vapelogo", f, f2, false);
    }
}

