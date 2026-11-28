package gg.vape.ui.click.component.value;

import gg.vape.Vape;
import gg.vape.module.none.ClientSettings;
import gg.vape.ui.click.component.GuiComponent;
import gg.vape.ui.click.component.value.AbstractListValueComponent;
import gg.vape.ui.click.frame.Frame;
import gg.vape.ui.click.frame.FrameComponent;
import gg.vape.ui.click.frame.impl.inevitable.InevitableFrameStack;
import gg.vape.ui.click.frame.impl.inevitable.InevitableLayout;
import gg.vape.wrapper.impl.Minecraft;

public abstract class FloatingValueDropdownLayer<T extends AbstractListValueComponent>
extends Frame {
    private double lastSourceX;
    private boolean visibleLastTick;
    private T sourceComponent;
    private double lastSourceY;

    @Override
    public boolean V$src$Z$1xhop3l() {
        return this.sourceComponent != null && ((GuiComponent)this.sourceComponent).V$src$Z$1xhop3l() && ((GuiComponent)this.sourceComponent).getParentFrameComponent() != null && ((GuiComponent)this.sourceComponent).getParentFrameComponent().V$src$Z$1xhop3l() && ((AbstractListValueComponent)this.sourceComponent).isExpanded();
    }

    @Override
    public void u() {
        super.u();
        boolean visible = this.V$src$Z$1xhop3l();
        if (visible && !this.visibleLastTick) {
            this.refreshContents();
        }
        this.visibleLastTick = visible;
    }

    @Override
    public void v() {
    }

    public void updatePosition() {
        if (this.sourceComponent == null || ((GuiComponent)this.sourceComponent).getParentFrameComponent() == null) {
            return;
        }
        Frame sourceFrame = this.sourceComponent.L$src$Lgg_vape_ui_click_frame_Frame_$1djx6sa();
        if (sourceFrame == null) return;
        if (InevitableFrameStack.isPanelOrPopup(sourceFrame)) {
            double scale = Vape.INSTANCE.getClientSettings().getGuiScaleFactor();
            if (scale <= 0.0) return;
            double viewportWidth = Minecraft.J() / (2.0 * scale);
            double viewportHeight = Minecraft.h() / (2.0 * scale);
            double maxHeight = Math.max(22.0, Math.min(300.0, viewportHeight - 2.0 * InevitableLayout.MARGIN));
            if (Math.abs(this.d$src$D$ibccpu() - maxHeight) > 0.01) {
                this.t(maxHeight);
                this.l$src$V$1mibm4x();
            }
            double x = InevitableLayout.overlayX(sourceFrame.G$src$D$1b2f02a(), sourceFrame.A(), this.A(), viewportWidth);
            double y = InevitableLayout.overlayY(this.sourceComponent.n(), Math.min(this.L(), maxHeight), viewportHeight);
            if (x != this.G$src$D$1b2f02a() || y != this.n()) this.M(x, y);
            return;
        }
        double preferredX = sourceFrame.G$src$D$1b2f02a() + sourceFrame.A() + 1.0;
        if (preferredX != this.lastSourceX || ((GuiComponent)this.sourceComponent).n() != this.lastSourceY) {
            double targetY = ((GuiComponent)this.sourceComponent).n();
            FrameComponent frameComponent = ((GuiComponent)this.sourceComponent).getParentFrameComponent();
            if (frameComponent.k$src$Z$if6xeb()) {
                targetY = Math.min(targetY, frameComponent.n() + frameComponent.d$src$D$ibccpu() - ((GuiComponent)this.sourceComponent).L());
                targetY = Math.max(targetY, frameComponent.n() + (frameComponent.j$src$Lgg_vape_ui_click_frame_FrameHeaderComponent_$175vsfc() != null ? frameComponent.j$src$Lgg_vape_ui_click_frame_FrameHeaderComponent_$175vsfc().L() : 0.0));
            }
            double screenCenterX = Minecraft.J() / 2;
            if (preferredX + this.A() / 2.0 > screenCenterX) {
                this.M(preferredX - ((GuiComponent)this.sourceComponent).L$src$Lgg_vape_ui_click_frame_Frame_$1djx6sa().A() - this.A() - 2.0, targetY);
            } else {
                this.M(preferredX, targetY);
            }
            this.lastSourceX = ((GuiComponent)this.sourceComponent).G$src$D$1b2f02a();
            this.lastSourceY = ((GuiComponent)this.sourceComponent).n();
        }
    }

    @Override
    public void Y() {
        if (this.sourceComponent == null || ((GuiComponent)this.sourceComponent).getParentFrameComponent() == null) {
            return;
        }
        ClientSettings.INSTANCE.replaceFrame(((GuiComponent)this.sourceComponent).getParentFrameComponent().L$src$Lgg_vape_ui_click_frame_Frame_$1djx6sa(), this);
    }


    public abstract void refreshContents();

    public T getSourceComponent() {
        return this.sourceComponent;
    }

    @Override
    public String getName() {
        if (this.sourceComponent == null) {
            return "sidecar_" + this.hashCode();
        }
        return "sidecar_" + this.sourceComponent.hashCode();
    }

    public FloatingValueDropdownLayer(T sourceComponent) {
        this.setDisabledOverlayColor(FloatingValueDropdownLayer.J.i);
        this.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M(false);
        this.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("wrap");
        this.K(300.0);
        this.S(100.0);
        this.sourceComponent = sourceComponent;
        this.Y(false);
        this.L(false, false);
    }
}

