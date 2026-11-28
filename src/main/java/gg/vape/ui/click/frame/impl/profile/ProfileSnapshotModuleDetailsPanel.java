package gg.vape.ui.click.frame.impl.profile;

import gg.vape.config.ProfileModuleSnapshot;
import gg.vape.config.ProfileSnapshot;
import gg.vape.ui.click.component.GuiComponent;
import gg.vape.ui.click.component.PanelComponent;
import gg.vape.ui.click.component.SimpleTextLabelComponent;
import gg.vape.ui.click.component.SpacerComponent;
import gg.vape.ui.click.component.gui.TextButton;
import gg.vape.ui.click.component.input.BindableInputComponent;
import java.util.List;

public class ProfileSnapshotModuleDetailsPanel
extends PanelComponent {
    private static final int PANEL_WIDTH = 206;
    private final SimpleTextLabelComponent moduleNameLabel;
    private final PanelComponent headerPanel = new PanelComponent(192.0, 10.0);
    private final TextButton enabledButton;
    private final BindableInputComponent bindInput;
    private ProfileModuleSnapshot selectedModule;
    private final TextButton resetButton;
    private final PanelComponent settingsPanel = new PanelComponent(PANEL_WIDTH, 144.0);
    private ProfileSnapshot snapshot;


    public ProfileSnapshotModuleDetailsPanel() {
        super(PANEL_WIDTH, 170.0);
        this.moduleNameLabel = new SimpleTextLabelComponent("Module Name");
        this.enabledButton = new TextButton("ON", 0.633, ProfileSnapshotModuleDetailsPanel.J.l, ProfileSnapshotModuleDetailsPanel.J.l, 16.0, 10.0);
        this.bindInput = new BindableInputComponent(null, ProfileSnapshotModuleDetailsPanel.J.A);
        this.bindInput.setSaveChanges(false);
        this.resetButton = new TextButton("RESET THIS MODULE", 0.633, ProfileSnapshotModuleDetailsPanel.J.i, ProfileSnapshotModuleDetailsPanel.J.i, 52.0, 10.0);
        this.settingsPanel.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("wrap");
        this.settingsPanel.t(144.0);
        SimpleTextLabelComponent simpleTextLabelComponent = new SimpleTextLabelComponent("SETTINGS");
        simpleTextLabelComponent.setTextColor(ProfileSnapshotModuleDetailsPanel.J.h);
        simpleTextLabelComponent.setBold(true);
        this.resetButton.setTransparentBackgroundBorder(ProfileSnapshotModuleDetailsPanel.J.l);
        this.resetButton.setShowDisabledOverlay(false);
        this.resetButton.setBorderAlpha(0.75f);
        this.moduleNameLabel.setTextColor(ProfileSnapshotModuleDetailsPanel.J.A);
        this.moduleNameLabel.setOffsetX(0.0f);
        this.moduleNameLabel.setFontScale(1.0);
        this.bindInput.setVisible(false);
        simpleTextLabelComponent.setOffsetX(0.0f);
        this.headerPanel.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().Q(true);
        this.headerPanel.h(this.moduleNameLabel, new Object[0]);
        this.headerPanel.h(new SpacerComponent(3.0, 0.0), new Object[0]);
        this.headerPanel.h(this.enabledButton, new Object[0]);
        this.headerPanel.h(new SpacerComponent(3.0, 0.0), new Object[0]);
        this.headerPanel.h(this.bindInput, new Object[0]);
        this.headerPanel.h(this.resetButton, "alignright");
        this.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("wrap");
        this.h(this.headerPanel, new Object[0]);
        this.h(new SpacerComponent(0.0, 4.0), new Object[0]);
        this.h(simpleTextLabelComponent, new Object[0]);
        this.h(this.settingsPanel, new Object[0]);
        this.enabledButton.addClickListener(this::toggleSelectedModule);
        this.resetButton.addClickListener(this::resetSelectedModule);
    }

    public void setSnapshot(ProfileSnapshot snapshot) {
        this.cancelCaptures();
        this.snapshot = snapshot;
    }

    public void cancelCaptures() {
        BindableInputComponent.cancelCapturesInTree(this);
    }

    private void toggleSelectedModule() {
        if (this.selectedModule == null) return;
        this.selectedModule.setEnabled(!this.selectedModule.isEnabled());
        this.enabledButton.setLabelText(this.selectedModule.isEnabled() ? "ON" : "OFF");
    }

    private void resetSelectedModule() {
        if (this.snapshot == null || this.selectedModule == null) return;
        this.cancelCaptures();
        this.snapshot.getGuiBuilder().resetModule(this.selectedModule);
        this.enabledButton.setLabelText(this.selectedModule.isEnabled() ? "ON" : "OFF");
    }

    public void setSelectedModule(ProfileModuleSnapshot moduleSnapshot) {
        this.cancelCaptures();
        this.selectedModule = moduleSnapshot;
        this.moduleNameLabel.setText(moduleSnapshot.getName());
        this.enabledButton.setLabelText(moduleSnapshot.isEnabled() ? "ON" : "OFF");
        // Keep an empty bind control visible so an unbound module can be bound.
        this.bindInput.setBendable(moduleSnapshot.getBindSnapshot().getValue());
        this.bindInput.setVisible(moduleSnapshot.getModule().getBind().usesOwnKeybindStorage());
        this.settingsPanel.removeMarkedChildren();
        List<GuiComponent> settingComponents = this.snapshot.getGuiBuilder().getModuleComponents(moduleSnapshot);
        for (GuiComponent settingComponent : settingComponents) {
            this.settingsPanel.h(settingComponent, new Object[0]);
        }
        this.H(true);
    }
}

