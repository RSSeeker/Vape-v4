package gg.vape.ui.click.frame.impl.profile;

import gg.vape.config.ProfileModuleSnapshot;
import gg.vape.config.ProfileSnapshot;
import gg.vape.module.none.ClientSettings;
import gg.vape.ui.click.component.IconButtonComponent;
import gg.vape.ui.click.component.LabeledTextInputComponent;
import gg.vape.ui.click.component.PanelComponent;
import gg.vape.ui.click.component.SpacerComponent;
import gg.vape.ui.click.component.TruncatedTextComponent;
import gg.vape.ui.click.component.gui.InteractiveComponent;
import gg.vape.ui.click.frame.impl.profile.ProfileSnapshotApplyBarComponent;
import gg.vape.ui.click.frame.impl.profile.ProfileSnapshotFrame;
import gg.vape.ui.click.frame.impl.profile.ProfileSnapshotModuleRowComponent;
import gg.vape.ui.click.frame.impl.profile.ProfileSnapshotModuleSearchInputComponent;
import gg.vape.value.ModuleNameSuggestionProvider;
import gg.vape.utils.StringUtils;
import java.util.ArrayList;

public class ProfileSnapshotModuleListPanel
extends PanelComponent {
    private final PanelComponent moduleList;
    private final ProfileSnapshotApplyBarComponent applyBar;
    private final ArrayList<ProfileSnapshotModuleRowComponent> moduleRows;
    private final LabeledTextInputComponent searchInput = new ProfileSnapshotModuleSearchInputComponent(this, "Search modules...", false, true);
    private final IconButtonComponent filterButton = new IconButtonComponent("filter_search", 0.5, 16.0, 16.0);
    private final TruncatedTextComponent profileNameLabel;
    private boolean showingAllModules = false;
    private ProfileSnapshot snapshot;

    public void rebuildModuleRows() {
        this.moduleRows.clear();
        for (ProfileModuleSnapshot moduleSnapshot : this.snapshot.getSortedModules(true)) {
            ProfileSnapshotModuleRowComponent moduleRow = new ProfileSnapshotModuleRowComponent(moduleSnapshot);
            moduleRow.addClickListener(() -> this.selectModule(moduleRow, moduleSnapshot));
            this.moduleRows.add(moduleRow);
        }
        this.updateRowVisibility();
        this.moduleList.removeMarkedChildren();
        for (ProfileSnapshotModuleRowComponent moduleRow : this.moduleRows) {
            this.moduleList.h(moduleRow, new Object[0]);
        }
    }

    private void selectModule(ProfileSnapshotModuleRowComponent selectedRow, ProfileModuleSnapshot moduleSnapshot) {
        for (ProfileSnapshotModuleRowComponent moduleRow : this.moduleRows) {
            moduleRow.setSelected(false);
        }
        selectedRow.setSelected(true);
        ClientSettings.getFrame(ProfileSnapshotFrame.class).selectModule(moduleSnapshot);
    }

    void filterRows(String query) {
        String normalizedQuery = StringUtils.y(query);
        if (normalizedQuery.isEmpty()) {
            this.updateRowVisibility();
            return;
        }
        for (ProfileSnapshotModuleRowComponent moduleRow : this.moduleRows) {
            moduleRow.setVisible(StringUtils.y(moduleRow.getSnapshot().getName()).contains(normalizedQuery));
        }
    }

    public ProfileSnapshotModuleListPanel() {
        super(108.0, 159.0);
        this.applyBar = new ProfileSnapshotApplyBarComponent(null, 100.0, true);
        this.moduleList = new PanelComponent(108.0, 108.0);
        this.moduleRows = new ArrayList<>();
        this.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("wrap");
        this.profileNameLabel = new TruncatedTextComponent("Profile Name", "...", 108.0, 1.2, ProfileSnapshotModuleListPanel.J.A, true);
        this.profileNameLabel.setHorizontalInset(0.0);
        this.profileNameLabel.setFontScale(1.2);
        this.searchInput.setVerticalInset(0.0f);
        this.searchInput.setLeftInset(0.0f);
        this.searchInput.o(88.0);
        this.searchInput.Y(16.0);
        this.searchInput.setSuggestionProvider(new ModuleNameSuggestionProvider());
        this.filterButton.setBorderColor(ProfileSnapshotModuleListPanel.J.y);
        this.applyBar.setLeftPadding(2.0f);
        PanelComponent panelComponent = new PanelComponent(108.0, 20.0);
        panelComponent.h(this.searchInput, new Object[0]);
        panelComponent.h(new SpacerComponent(2.0, 0.0), new Object[0]);
        panelComponent.h(this.filterButton, new Object[0]);
        this.moduleList.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("wrap");
        this.moduleList.t(108.0);
        this.h(this.profileNameLabel, new Object[0]);
        this.h(new SpacerComponent(0.0, 5.0), new Object[0]);
        this.h(panelComponent, new Object[0]);
        this.h(this.applyBar, new Object[0]);
        this.h(this.moduleList, new Object[0]);
        this.filterButton.w("Toggle showing all modules or only modules with non-default values");
        this.filterButton.addClickListener(this::toggleShowingAllModules);
    }

    @Override
    public void c() {
        super.c();
    }

    public void selectModuleRow(ProfileModuleSnapshot moduleSnapshot) {
        for (ProfileSnapshotModuleRowComponent moduleRow : this.moduleRows) {
            moduleRow.setSelected(moduleRow.getSnapshot().equals(moduleSnapshot));
        }
    }

    public void selectInitialModule() {
        if (this.moduleRows.isEmpty()) return;
        InteractiveComponent interactiveComponent = null;
        for (ProfileSnapshotModuleRowComponent moduleRow : this.moduleRows) {
            if (!moduleRow.V$src$Z$1xhop3l()) continue;
            interactiveComponent = moduleRow;
            break;
        }
        if (interactiveComponent != null) {
            interactiveComponent.dispatchPrimaryClick();
            this.setShowingAllModules(false);
        } else {
            this.moduleRows.get(0).dispatchPrimaryClick();
            this.setShowingAllModules(true);
        }
    }

    private void toggleShowingAllModules() {
        this.setShowingAllModules(!this.showingAllModules);
    }

    private void setShowingAllModules(boolean showingAllModules) {
        this.showingAllModules = showingAllModules;
        this.filterButton.setIconResource(showingAllModules ? "filter_search_2" : "filter_search");
        this.applyBar.setCountAllModules(showingAllModules);
        this.updateRowVisibility();
    }

    public void setSnapshot(ProfileSnapshot snapshot) {
        this.snapshot = snapshot;
        this.profileNameLabel.setText(snapshot.getProfile().getName());
        this.applyBar.setSnapshot(snapshot);
        this.rebuildModuleRows();
    }

    private void updateRowVisibility() {
        for (ProfileSnapshotModuleRowComponent moduleRow : this.moduleRows) {
            moduleRow.setVisible(this.showingAllModules || moduleRow.getSnapshot().hasChanges());
        }
        this.H(true);
    }
}
