package gg.vape.ui.click.frame.impl.inevitable;

import gg.vape.module.none.ClientSettings;
import gg.vape.ui.click.component.GuiComponent;
import gg.vape.ui.click.component.module.ModuleComponent;
import gg.vape.ui.click.frame.FrameHeaderComponent;
import gg.vape.ui.click.frame.impl.ModuleCategoryFrameHeader;

/** Retains frame dragging and right-click collapse without legacy controls. */
public final class InevitableModuleHeader extends ModuleCategoryFrameHeader {
    private final InevitableModuleFrame panel;
    public InevitableModuleHeader(InevitableModuleFrame panel) {
        super(panel, panel.group().icon(), panel.group().title());
        this.panel = panel;
        for (GuiComponent child : this.f()) child.setVisible(false);
    }
    @Override public double C() { return 22.0; }
    @Override public boolean D(int x, int y) {
        // The inherited header has hidden legacy buttons whose stale bounds
        // must not prevent dragging the new, control-free header.
        return this.getBounds().J(x, y);
    }
    @Override public void H() {
        InevitablePanelStyle.header(this.panel.group().title(), this.panel.group().icon(),
                this.G$src$D$1b2f02a(), this.n(), this.A(), this.L());
    }
    @Override public void h() {
        for (GuiComponent child : this.panel.f()) {
            if (child instanceof FrameHeaderComponent) continue;
            if (child instanceof ModuleComponent) {
                ModuleComponent row = (ModuleComponent)child;
                row.collapseValueComponents();
                child.setVisible(!this.panel.q() && (ClientSettings.moduleSearchActive || row.getModule().isVisible()));
            } else child.setVisible(false);
        }
        this.panel.l$src$V$1mibm4x();
    }
}
