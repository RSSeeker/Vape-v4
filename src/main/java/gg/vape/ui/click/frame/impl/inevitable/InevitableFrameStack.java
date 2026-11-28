package gg.vape.ui.click.frame.impl.inevitable;

import gg.vape.Vape;
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
        return frame instanceof PopupFrame ? isPanel(((PopupFrame)frame).X$src$Lgg_vape_ui_click_frame_Frame_$1aw5qf9()) : isPanel(frame);
    }
    public static boolean isUnpositionedPanel(Frame frame) {
        return frame instanceof InevitableModuleFrame && !((InevitableModuleFrame)frame).hasLayout()
                || frame instanceof InevitablePanelFrame && !((InevitablePanelFrame)frame).hasLayout();
    }
    public void syncModules() {
        for (Frame frame : this.panels) if (frame instanceof InevitableModuleFrame) ((InevitableModuleFrame)frame).syncModules();
    }
    /** Remove owned popups from this stack, even when a different stack is active. */
    public void closePanelPopups(Frame owner) {
        for (PopupFrame popup : new ArrayList<PopupFrame>(owner.s$src$Ljava_util_ArrayList_$1a2240q())) {
            this.closePanelPopups(popup);
            this.m(popup);
            owner.s$src$Ljava_util_ArrayList_$1a2240q().remove(popup);
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
