package gg.vape.ui.click.frame.impl.profile;

import gg.vape.config.ProfileModuleSnapshot;
import gg.vape.config.ProfileSnapshot;
import gg.vape.module.none.ClientSettings;
import gg.vape.ui.click.component.ConfirmationDialogComponent;
import gg.vape.ui.click.component.IconButtonComponent;
import gg.vape.ui.click.component.PanelComponent;
import gg.vape.ui.click.component.SpacerComponent;
import gg.vape.ui.click.component.SquareIconButtonComponent;
import gg.vape.ui.click.component.layout.PaddedComponent;
import gg.vape.ui.click.frame.Frame;
import gg.vape.ui.click.frame.FrameStackManager;
import gg.vape.ui.click.frame.impl.main.ClickGuiFrameManager;
import gg.vape.ui.click.frame.impl.profile.ProfileSnapshotModuleDetailsPanel;
import gg.vape.ui.click.frame.impl.profile.ProfileSnapshotModuleListPanel;
import gg.vape.utils.render.GuiRenderPrimitives;
import java.awt.Color;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

public class ProfileSnapshotFrame
extends Frame {
    private ProfileSnapshot snapshot;
    @Nullable
    private Consumer<ProfileSnapshot> applyCallback;
    private final ProfileSnapshotModuleDetailsPanel moduleDetailsPanel;
    @Nullable
    private FrameStackManager returnStack;
    @Nullable
    private Frame returnSidecar;
    private final PanelComponent detailsContainer;
    private final ProfileSnapshotModuleListPanel moduleListPanel;
    private final PanelComponent rootPanel = new PanelComponent(332.0, 182.0);
    private final IconButtonComponent closeButton;


    @Override
    public void v() {
    }

    private void applyChangesAndClose() {
        this.applyChanges();
        this.closeEditor();
    }

    public void setSnapshot(ProfileSnapshot snapshot, @Nullable Consumer<ProfileSnapshot> applyCallback) {
        // Parent panels keep their staged state when this editor is discarded.
        this.snapshot = snapshot.fork();
        this.applyCallback = applyCallback;
        this.moduleDetailsPanel.setSnapshot(this.snapshot);
        this.moduleListPanel.setSnapshot(this.snapshot);
        this.moduleListPanel.selectInitialModule();
    }

    public ProfileSnapshotFrame() {
        this.detailsContainer = new PanelComponent(206.0, 182.0);
        this.closeButton = new SquareIconButtonComponent("newclose", 1.0, new Color(255, 255, 255, 0), new Color(255, 255, 255, 25), 10.0, 10.0);
        this.moduleListPanel = new ProfileSnapshotModuleListPanel();
        this.moduleDetailsPanel = new ProfileSnapshotModuleDetailsPanel();
        this.g(true);
        PaddedComponent paddedComponent = new PaddedComponent(4.0, 4.0, 4.0, 0.0, this.rootPanel);
        this.h(paddedComponent, new Object[0]);
        PaddedComponent paddedComponent2 = new PaddedComponent(8.0, this.moduleListPanel);
        PanelComponent panelComponent = new PanelComponent(14.0, 10.0);
        panelComponent.h(this.closeButton, new Object[0]);
        this.rootPanel.h(paddedComponent2, new Object[0]);
        this.rootPanel.h(new SpacerComponent(2.0, 0.0), new Object[0]);
        this.rootPanel.h(this.detailsContainer, new Object[0]);
        this.detailsContainer.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("wrap");
        this.detailsContainer.h(panelComponent, "alignright");
        this.detailsContainer.h(this.moduleDetailsPanel, new Object[0]);
        this.closeButton.addClickListener(this::showCloseConfirmation);
    }

    private void applyChanges() {
        this.moduleDetailsPanel.cancelCaptures();
        if (this.snapshot != null) {
            this.snapshot.applyAndSave();
            if (this.applyCallback != null) this.applyCallback.accept(this.snapshot);
        }
    }

    private void closeEditor() {
        this.moduleDetailsPanel.cancelCaptures();
        this.applyCallback = null;
        FrameStackManager frameStackManager = this.returnStack;
        if (frameStackManager != null) {
            if (frameStackManager instanceof ClickGuiFrameManager) {
                ClickGuiFrameManager clickGuiFrameManager = (ClickGuiFrameManager)frameStackManager;
                if (this.returnSidecar != null) clickGuiFrameManager.setSidecarFrame(this.returnSidecar);
                else clickGuiFrameManager.closeSidecar();
            } else {
                ClientSettings.INSTANCE.switchFrameStack(frameStackManager);
            }
            this.returnStack = null;
            this.returnSidecar = null;
        } else {
            ClientSettings.INSTANCE.switchFrameStack(ClientSettings.mainStack);
        }
    }

    @Override
    public String getName() {
        return "profileEditor";
    }

    @Override
    public void c() {
        this.H(true);
        GuiRenderPrimitives.P(this.G$src$D$1b2f02a() - 0.5, this.n() - 0.5, this.A() + 1.0, this.L() + 1.0 + 2.0, ProfileSnapshotFrame.J.y, 2.0f, 0.8f, 1.0f);
        super.c();
    }

    @Nullable
    public FrameStackManager getReturnStack() {
        return this.returnStack;
    }

    private void discardChangesAndClose() {
        this.closeEditor();
    }

    public void selectModule(ProfileModuleSnapshot moduleSnapshot) {
        if (this.snapshot == null || moduleSnapshot == null) return;
        for (ProfileModuleSnapshot candidate : this.snapshot.getAllModules()) {
            if (candidate.getModule() != moduleSnapshot.getModule()) continue;
            this.moduleDetailsPanel.setSelectedModule(candidate);
            this.moduleListPanel.selectModuleRow(candidate);
            return;
        }
    }

    public ProfileSnapshot getSnapshot() {
        return this.snapshot;
    }

    public void setReturnStack(@Nullable FrameStackManager returnStack) {
        this.returnStack = returnStack;
        Frame sidecar = returnStack instanceof ClickGuiFrameManager
                ? ((ClickGuiFrameManager)returnStack).getSidecarFrame() : null;
        this.returnSidecar = sidecar == this ? null : sidecar;
    }

    private void showCloseConfirmation() {
        ConfirmationDialogComponent.show(this.L$src$Lgg_vape_ui_click_frame_Frame_$1djx6sa(), "Do you want to apply changes to profile?", "Apply", null, this::applyChangesAndClose, 80.0, "Discard", this::discardChangesAndClose);
    }

    public void resetAllSettings() {
        if (this.snapshot == null) {
            return;
        }
        this.moduleDetailsPanel.cancelCaptures();
        this.snapshot.getGuiBuilder().resetAllModules();
        this.moduleListPanel.rebuildModuleRows();
    }
}

