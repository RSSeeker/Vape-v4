package gg.vape.ui.click.frame.impl.main;

import gg.vape.Vape;
import gg.vape.module.none.ClientSettings;
import gg.vape.ui.click.component.input.BindableInputComponent;
import gg.vape.ui.click.frame.Frame;
import gg.vape.ui.click.frame.FrameStackManager;
import gg.vape.ui.click.frame.impl.hud.HudModuleConfigFrameBase;
import gg.vape.ui.click.frame.impl.hud.HudModuleFrameBase;
import gg.vape.ui.click.frame.impl.hud.HudOverlaySelectorFrame;
import gg.vape.ui.click.frame.impl.main.ClickGuiLayer;
import gg.vape.ui.click.frame.impl.main.ClickGuiMainFrame;
import gg.vape.wrapper.impl.Minecraft;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

public class ClickGuiFrameManager
extends FrameStackManager {
    private final Set<HudModuleFrameBase> overlayLayerHudFrames = new HashSet<HudModuleFrameBase>();
    private Frame sidecarFrame;
    private final Set<HudModuleFrameBase> mainLayerHudFrames = new HashSet<HudModuleFrameBase>();
    private HudOverlaySelectorFrame overlaySelector;
    private ClickGuiMainFrame mainFrame;

    private void clearMainLayerHudFrames() {
        if (this.mainLayerHudFrames.isEmpty()) {
            return;
        }
        for (HudModuleFrameBase hudModuleFrameBase : this.mainLayerHudFrames) {
            hudModuleFrameBase.setFrontmostOverlay(false);
            this.m(hudModuleFrameBase);
        }
        this.mainLayerHudFrames.clear();
    }


    public Frame getSidecarFrame() {
        return this.sidecarFrame;
    }

    public HudOverlaySelectorFrame getOverlaySelector() {
        return this.overlaySelector;
    }

    public void setSidecarFrame(@Nullable Frame frame) {
        if (this.sidecarFrame != null && this.sidecarFrame != frame) {
            BindableInputComponent.cancelCapturesInTree(this.sidecarFrame);
        }
        if (frame != null) {
            if (this.sidecarFrame != null && this.sidecarFrame != frame) {
                this.m(this.sidecarFrame);
            }
            this.sidecarFrame = frame;
            if (!this.Y().contains(frame)) {
                this.q(frame);
            }
            this.R(this.mainFrame, frame);
            frame.t(true, false);
            this.mainFrame.getTransitionOverlay().setActive(true);
        } else {
            if (this.sidecarFrame != null) {
                this.m(this.sidecarFrame);
            }
            this.sidecarFrame = null;
            this.mainFrame.getTransitionOverlay().setActive(false);
        }
    }

    @Override
    public void A() {
        if (this.mainFrame == null) {
            this.mainFrame = new ClickGuiMainFrame();
            this.q(this.mainFrame);
            this.overlaySelector = new HudOverlaySelectorFrame();
            this.q(this.overlaySelector);
            this.showLayer(ClickGuiLayer.MAIN);
        }
        if (this.overlaySelector.V$src$Z$1xhop3l()) {
            this.clearMainLayerHudFrames();
            this.overlaySelector.K((double)Minecraft.J() / 4.0 / Vape.INSTANCE.getClientSettings().getGuiScaleFactor() - this.overlaySelector.A() / 2.0);
            this.overlaySelector.S((double)Minecraft.h() / 2.0 / Vape.INSTANCE.getClientSettings().getGuiScaleFactor() - this.overlaySelector.L() - 5.0);
            this.syncOverlayLayerHudFrames();
        } else {
            this.clearOverlayLayerHudFrames();
            this.syncMainLayerHudFrames();
        }
    }

    public ClickGuiMainFrame getMainFrame() {
        return this.mainFrame;
    }

    private void clearOverlayLayerHudFrames() {
        if (this.overlayLayerHudFrames.isEmpty()) {
            return;
        }
        for (HudModuleFrameBase hudModuleFrameBase : this.overlayLayerHudFrames) {
            this.m(hudModuleFrameBase);
        }
        this.overlayLayerHudFrames.clear();
    }

    public void showLayer(ClickGuiLayer clickGuiLayer) {
        switch (clickGuiLayer) {
            case MAIN: {
                this.mainFrame.setVisible(true);
                this.overlaySelector.setVisible(false);
                HudModuleConfigFrameBase.closeAllHudSettings();
                break;
            }
            case OVERLAYS: {
                this.overlaySelector.setVisible(true);
                this.mainFrame.setVisible(false);
            }
        }
    }

    private void syncMainLayerHudFrames() {
        HashSet<HudModuleFrameBase> visibleFrames = new HashSet<HudModuleFrameBase>();
        for (Frame frame : ClientSettings.getAllFrames()) {
            HudModuleFrameBase hudModuleFrameBase;
            if (!(frame instanceof HudModuleFrameBase) || !(hudModuleFrameBase = (HudModuleFrameBase)frame).V$src$Z$1xhop3l()) continue;
            visibleFrames.add(hudModuleFrameBase);
            if (this.Y().contains(hudModuleFrameBase)) continue;
            int n = this.Y().indexOf(this.mainFrame);
            if (n >= 0) {
                this.Y().add(n, hudModuleFrameBase);
                continue;
            }
            this.q(hudModuleFrameBase);
        }
        Iterator<HudModuleFrameBase> iterator = this.mainLayerHudFrames.iterator();
        while (iterator.hasNext()) {
            HudModuleFrameBase frame = iterator.next();
            if (visibleFrames.contains(frame) && frame.V$src$Z$1xhop3l()) continue;
            this.m(frame);
            iterator.remove();
        }
        this.mainLayerHudFrames.addAll(visibleFrames);
    }

    public void closeSidecar() {
        this.setSidecarFrame(null);
    }

    public boolean isMainLayerHudFrame(Frame frame) {
        return this.mainLayerHudFrames.contains(frame);
    }

    private void syncOverlayLayerHudFrames() {
        HashSet<HudModuleFrameBase> visibleFrames = new HashSet<HudModuleFrameBase>();
        for (Frame frame : ClientSettings.getAllFrames()) {
            HudModuleFrameBase hudModuleFrameBase;
            if (!(frame instanceof HudModuleFrameBase) || !(hudModuleFrameBase = (HudModuleFrameBase)frame).V$src$Z$1xhop3l()) continue;
            visibleFrames.add(hudModuleFrameBase);
            if (this.Y().contains(hudModuleFrameBase)) continue;
            this.q(hudModuleFrameBase);
        }
        Iterator<HudModuleFrameBase> iterator = this.overlayLayerHudFrames.iterator();
        while (iterator.hasNext()) {
            HudModuleFrameBase frame = iterator.next();
            if (visibleFrames.contains(frame) && frame.V$src$Z$1xhop3l()) continue;
            this.m(frame);
            iterator.remove();
        }
        this.overlayLayerHudFrames.addAll(visibleFrames);
        this.v(this.overlaySelector);
    }
}
