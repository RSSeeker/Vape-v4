package gg.vape.ui.click.frame.impl.inevitable;

import gg.vape.ui.click.GuiMouseEvent;
import gg.vape.ui.click.frame.Frame;
import gg.vape.ui.click.frame.FrameHeaderComponent;

public final class InevitablePanelHeader extends FrameHeaderComponent {
    private final String title;
    public InevitablePanelHeader(Frame frame, String title) { super(frame); this.title = title; }
    @Override public double C() { return 22.0; }
    @Override public void I() {}
    @Override public void F() {}
    @Override public void u() {}
    @Override public void g(GuiMouseEvent event) {}
    @Override public void H() {
        InevitablePanelStyle.header(this.title, "PROFILES".equals(this.title) ? "newprofiles" : "newsettings",
                this.G$src$D$1b2f02a(), this.n(), this.A(), this.L());
    }
}
