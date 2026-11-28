package gg.vape.ui.click.frame.impl.inevitable;

import gg.vape.Vape;
import gg.vape.module.none.ClientSettings;
import gg.vape.ui.click.component.DropdownSelectComponent;
import gg.vape.ui.click.component.GuiComponent;
import gg.vape.ui.click.component.PopupMenuButtonComponent;
import gg.vape.ui.click.component.module.ModuleComponent;
import gg.vape.ui.click.component.input.BindableInputComponent;
import gg.vape.ui.click.component.value.AbstractListValueComponent;
import gg.vape.ui.click.component.value.FloatingValueDropdownLayer;
import gg.vape.ui.click.frame.Frame;
import gg.vape.ui.click.frame.FrameStackManager;
import gg.vape.ui.click.frame.PopupFrame;
import gg.vape.wrapper.impl.Minecraft;
import java.util.ArrayList;
import java.util.List;

/** Primary stack: fixed panel order is independent of click z-order. */
public final class InevitableFrameStack extends FrameStackManager {
    private final List<Frame> panels = new ArrayList<Frame>();
    private int lastWidth = -1, lastHeight = -1;
    private double lastScale = -1.0;
    public void addPanel(Frame frame) { this.panels.add(frame); this.q(frame); }
    public void clearPanels() {
        this.panels.clear(); this.Y().clear();
        this.lastWidth = this.lastHeight = -1; this.lastScale = -1.0;
    }
    public void resetLayout() {
        for (Frame frame : this.panels) {
            if (frame instanceof InevitableModuleFrame) ((InevitableModuleFrame)frame).clearLayout();
            if (frame instanceof InevitablePanelFrame) ((InevitablePanelFrame)frame).clearLayout();
            frame.W(0.0);
        }
        this.lastWidth = this.lastHeight = -1; this.lastScale = -1.0;
    }
    private boolean hasLayout(Frame frame) {
        if (frame instanceof InevitableModuleFrame) return ((InevitableModuleFrame)frame).hasLayout();
        return ((InevitablePanelFrame)frame).hasLayout();
    }
    private void markLayout(Frame frame) {
        if (frame instanceof InevitableModuleFrame) ((InevitableModuleFrame)frame).markLayoutAssigned();
        else ((InevitablePanelFrame)frame).markLayoutAssigned();
    }
    public static boolean isPanel(Frame frame) {
        return frame instanceof InevitableModuleFrame || frame instanceof InevitablePanelFrame;
    }
    public static boolean isPanelOrPopup(Frame frame) {
        if (frame instanceof PopupFrame) {
            Frame owner = ((PopupFrame)frame).X$src$Lgg_vape_ui_click_frame_Frame_$1aw5qf9();
            return owner != frame && isPanelOrPopup(owner);
        }
        if (frame instanceof FloatingValueDropdownLayer) {
            GuiComponent source = ((FloatingValueDropdownLayer<?>)frame).getSourceComponent();
            if (source == null || source.getParentFrameComponent() == null) return false;
            Frame owner = source.L$src$Lgg_vape_ui_click_frame_Frame_$1djx6sa();
            return owner != frame && isPanelOrPopup(owner);
        }
        return isPanel(frame);
    }
    public static boolean isUnpositionedPanel(Frame frame) {
        return frame instanceof InevitableModuleFrame && !((InevitableModuleFrame)frame).hasLayout()
                || frame instanceof InevitablePanelFrame && !((InevitablePanelFrame)frame).hasLayout();
    }
    public void syncModules() {
        for (Frame frame : this.panels) if (frame instanceof InevitableModuleFrame) ((InevitableModuleFrame)frame).syncModules();
    }
    /** Remove a popup and its descendants from their owner and this stack. */
    private void closePopup(PopupFrame popup) {
        this.closePanelPopups(popup);
        this.clearFocusInTree(popup);
        GuiComponent source = popup.V$src$Lgg_vape_ui_click_component_GuiComponent_$jpbobc();
        if (source instanceof DropdownSelectComponent
                && ((DropdownSelectComponent<?>)source).getPopupFrame() == popup) {
            ((DropdownSelectComponent<?>)source).closePopup();
        } else if (source instanceof PopupMenuButtonComponent
                && ((PopupMenuButtonComponent)source).getPopupFrame() == popup) {
            ((PopupMenuButtonComponent)source).closePopup();
        }
        // The legacy close helpers target the active stack. The Inevitable
        // stack can instead be inactive during a script-driven refresh.
        this.m(popup);
        popup.Q$src$Lgg_vape_ui_click_frame_Frame_$1y8ivjg()
                .s$src$Ljava_util_ArrayList_$1a2240q().remove(popup);
    }

    public void closePanelPopups(Frame owner) {
        for (PopupFrame popup : new ArrayList<PopupFrame>(owner.s$src$Ljava_util_ArrayList_$1a2240q())) {
            this.closePopup(popup);
        }
    }

    private void clearFocusInTree(GuiComponent component) {
        if (component instanceof BindableInputComponent) ((BindableInputComponent)component).getCaptureTask().cancelCapture();
        if (ClientSettings.activeComponent == component) ClientSettings.activeComponent = null;
        for (GuiComponent child : new ArrayList<GuiComponent>(component.f())) this.clearFocusInTree(child);
    }

    /** Close only the detached/hidden editor subtree, including floating lists. */
    public void closeEditorTree(GuiComponent component) {
        if (component instanceof BindableInputComponent) {
            ((BindableInputComponent)component).getCaptureTask().cancelCapture();
        }
        for (Frame frame : new ArrayList<Frame>(this.Y())) {
            if (frame instanceof FloatingValueDropdownLayer
                    && ((FloatingValueDropdownLayer<?>)frame).getSourceComponent() == component) {
                this.closePanelPopups(frame);
                this.clearFocusInTree(frame);
                this.m(frame);
            }
        }
        if (component instanceof DropdownSelectComponent) {
            PopupFrame popup = ((DropdownSelectComponent<?>)component).getPopupFrame();
            if (popup != null) this.closePopup(popup);
        } else if (component instanceof PopupMenuButtonComponent) {
            PopupFrame popup = ((PopupMenuButtonComponent)component).getPopupFrame();
            if (popup != null) this.closePopup(popup);
        }
        if (component instanceof AbstractListValueComponent) {
            ((AbstractListValueComponent)component).setExpanded(false);
        }
        if (ClientSettings.activeComponent == component) ClientSettings.activeComponent = null;
        for (GuiComponent child : new ArrayList<GuiComponent>(component.f())) this.closeEditorTree(child);
    }

    /** Close transient editors on navigation, without collapsing module rows. */
    public void closeTransients() {
        for (Frame panel : this.panels) {
            BindableInputComponent.cancelCapturesInTree(panel);
            this.closePanelPopups(panel);
            for (GuiComponent child : new ArrayList<GuiComponent>(panel.f())) {
                if (!(child instanceof ModuleComponent)) continue;
                for (GuiComponent editor : ((ModuleComponent)child).getValueComponents()) this.closeEditorTree(editor);
            }
        }
    }

    @Override public void A() {
        int width = Minecraft.J(), height = Minecraft.h();
        double scale = Vape.INSTANCE.getClientSettings().getGuiScaleFactor();
        if (width <= 0 || height <= 0 || scale <= 0.0) return;
        double viewportWidth = width / (2.0 * scale), viewportHeight = height / (2.0 * scale);
        boolean changed = width != this.lastWidth || height != this.lastHeight || scale != this.lastScale;
        boolean placed = false;
        for (int i = 0; i < this.panels.size(); i++) {
            Frame frame = this.panels.get(i);
            if (!this.hasLayout(frame)) {
                frame.M(InevitableLayout.panelX(viewportWidth, i), InevitableLayout.panelY(viewportHeight));
                this.markLayout(frame); placed = true;
            } else if (changed) {
                double x = InevitableLayout.clampX(frame.G$src$D$1b2f02a(), viewportWidth);
                double y = InevitableLayout.clampY(frame.n(), viewportHeight);
                if (x != frame.G$src$D$1b2f02a() || y != frame.n()) frame.M(x, y);
            }
            double limit = InevitableLayout.maxPanelHeight(viewportHeight, frame.n());
            if (Math.abs(frame.d$src$D$ibccpu() - limit) > 0.01) {
                frame.t(limit); frame.l$src$V$1mibm4x();
            }
        }
        this.lastWidth = width; this.lastHeight = height; this.lastScale = scale;
        if (placed && Vape.INSTANCE.getSyncThread() != null) Vape.INSTANCE.saveAndStop();
    }
}
