package gg.vape.ui.click.frame.impl.profile;

import gg.vape.Vape;
import gg.vape.config.Profile;
import gg.vape.config.ProfileSnapshot;
import gg.vape.module.none.ClientSettings;
import gg.vape.ui.click.component.ConfirmationDialogComponent;
import gg.vape.ui.click.component.FlowLayoutComponent;
import gg.vape.ui.click.component.PanelComponent;
import gg.vape.ui.click.component.gui.TextLabel;
import gg.vape.ui.click.component.layout.PaddedComponent;
import gg.vape.ui.click.frame.FrameStackManager;
import gg.vape.ui.click.frame.impl.main.ClickGuiFrameManager;
import gg.vape.ui.click.frame.impl.profile.ProfileSnapshotFrame;
import gg.vape.ui.font.SmoothFontRenderer;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

public class ProfileSnapshotApplyBarComponent
extends FlowLayoutComponent {
    private final TextLabel actionLabel;
    @Nullable
    private FrameStackManager returnStack;
    private int affectedModuleCount;
    private float leftPadding = 6.0f;
    private boolean countAllModules;
    private ProfileSnapshot snapshot;
    @Nullable
    private Consumer<ProfileSnapshot> applyCallback;

    public void setApplyCallback(@Nullable Consumer<ProfileSnapshot> applyCallback) {
        this.applyCallback = applyCallback;
    }

    public ProfileSnapshot getSnapshot() {
        return this.snapshot;
    }

    public void setSnapshot(ProfileSnapshot snapshot) {
        this.snapshot = snapshot;
        this.refreshModuleCount();
        this.actionLabel.setVisible(snapshot != null);
        this.l$src$V$1mibm4x();
    }

    @Nullable
    public FrameStackManager getReturnStack() {
        return this.returnStack;
    }

    private void refreshModuleCount() {
        if (this.snapshot != null) {
            this.affectedModuleCount = this.snapshot.getModules(this.countAllModules).size();
        }
    }

    private void handleResetConfirmed(ProfileSnapshotFrame snapshotFrame) {
        snapshotFrame.resetAllSettings();
        this.refreshModuleCount();
    }

    public void setCountAllModules(boolean countAllModules) {
        this.countAllModules = countAllModules;
        this.refreshModuleCount();
    }

    public ProfileSnapshotApplyBarComponent(ProfileSnapshot snapshot, double width, boolean resetAction) {
        super(110.0);
        this.setShowDisabledOverlay(false);
        PanelComponent panelComponent = new PanelComponent(width, 12.0);
        this.addChildren(new PaddedComponent(2.0, panelComponent));
        this.actionLabel = new TextLabel(resetAction ? "Reset all" : "edit all", 0.75, false);
        this.actionLabel.o(20.0);
        this.actionLabel.Y(14.0);
        panelComponent.h(this.actionLabel, "alignright");
        panelComponent.setShowDisabledOverlay(false);
        this.actionLabel.setTextColor(null);
        this.actionLabel.addClickListener(() -> {
            ProfileSnapshotFrame profileSnapshotFrame = ClientSettings.getFrame(ProfileSnapshotFrame.class);
            if (resetAction) {
                ConfirmationDialogComponent.showStandard(this.L$src$Lgg_vape_ui_click_frame_Frame_$1djx6sa(), "Are you sure you want to reset all settings?", "Reset", "reset_circle", () -> this.handleResetConfirmed(profileSnapshotFrame));
            } else {
                if (this.getSnapshot() == null) return;
                Profile snapshotProfile = this.getSnapshot().getProfile();
                if (Vape.INSTANCE.getProfilesManager().captureIfActive(snapshotProfile)) {
                    ProfileSnapshot refreshed = snapshotProfile.createSnapshot(false);
                    if (refreshed != null) this.setSnapshot(refreshed);
                }
                profileSnapshotFrame.setSnapshot(this.getSnapshot(), this.applyCallback);
                if (ClientSettings.INSTANCE.getActiveStack() instanceof ClickGuiFrameManager) {
                    ClickGuiFrameManager clickGuiFrameManager = (ClickGuiFrameManager)ClientSettings.INSTANCE.getActiveStack();
                    profileSnapshotFrame.setReturnStack(clickGuiFrameManager);
                    clickGuiFrameManager.setSidecarFrame(profileSnapshotFrame);
                } else {
                    FrameStackManager origin = this.getReturnStack();
                    profileSnapshotFrame.setReturnStack(origin != null ? origin : ClientSettings.INSTANCE.getActiveStack());
                    ClientSettings.INSTANCE.switchFrameStack(ClientSettings.profileSnapshotStack);
                }
            }
        });
        this.setSnapshot(snapshot);
    }

    public void setLeftPadding(float leftPadding) {
        this.leftPadding = leftPadding;
    }

    @Override
    public void c() {
        super.c();
        SmoothFontRenderer smoothFontRenderer = this.getAlternateFontRenderer(0.8);
        SmoothFontRenderer smoothFontRenderer2 = this.getAlternateFontRenderer(0.8);
        double textY = this.actionLabel.getTextY() + 0.5;
        if (this.countAllModules) {
            smoothFontRenderer.d(this.affectedModuleCount + " ", this.G$src$D$1b2f02a() + (double)this.leftPadding, textY, ProfileSnapshotApplyBarComponent.J.A);
            smoothFontRenderer2.d("MODULES", this.G$src$D$1b2f02a() + (double)this.leftPadding + smoothFontRenderer.N(this.affectedModuleCount + " "), textY, ProfileSnapshotApplyBarComponent.J.h);
        } else {
            smoothFontRenderer.d(this.affectedModuleCount + " ", this.G$src$D$1b2f02a() + (double)this.leftPadding, textY, ProfileSnapshotApplyBarComponent.J.A);
            smoothFontRenderer2.d("AFFECTED MODULES", this.G$src$D$1b2f02a() + (double)this.leftPadding + smoothFontRenderer.N(this.affectedModuleCount + " "), textY, ProfileSnapshotApplyBarComponent.J.h);
        }
    }

    public void setReturnStack(@Nullable FrameStackManager returnStack) {
        this.returnStack = returnStack;
    }
}
