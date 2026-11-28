package gg.vape.ui.click.frame.impl.inevitable;

import gg.vape.module.none.ClientSettings;
import gg.vape.ui.click.frame.Frame;

/** Escape hatch from the preserved classic tools to the new primary GUI. */
public final class InevitableReturnFrame extends Frame {
    public InevitableReturnFrame() {
        this.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M(false);
        this.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("wrap");
        this.setDisabledOverlayColor(InevitablePanelStyle.BLACK);
        this.Y(false);
        this.L(false, true);
        this.K(12.0);
        this.S(8.0);
        InevitableActionRow back = new InevitableActionRow(gg.vape.brand.InevitableBrand::wordmark, () -> false,
                () -> ClientSettings.INSTANCE.switchFrameStack(ClientSettings.inevitableStack), null);
        back.w("Return to the Inevitable click GUI");
        this.h(back, new Object[0]);
    }
    @Override public String getName() { return "Inevitable.ClassicReturn"; }
    @Override public void z(boolean scrolling) {
        InevitablePanelStyle.panel(this.G$src$D$1b2f02a(), this.n(), this.A(), this.getComponentHeight());
    }
}
