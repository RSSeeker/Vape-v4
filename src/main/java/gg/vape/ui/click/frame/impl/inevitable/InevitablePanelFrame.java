package gg.vape.ui.click.frame.impl.inevitable;

import com.google.gson.JsonObject;
import gg.vape.Vape;
import gg.vape.config.Profile;
import gg.vape.friend.ui.OnlineFriendsFrame;
import gg.vape.manager.client.ProfilesManager;
import gg.vape.module.none.ClientSettings;
import gg.vape.ui.click.component.GuiComponent;
import gg.vape.ui.click.frame.CollapsibleFrame;
import gg.vape.ui.click.frame.Frame;
import gg.vape.ui.click.frame.FrameHeaderComponent;
import gg.vape.ui.click.frame.impl.ClientSettingsFrame;
import gg.vape.ui.click.frame.impl.FrameMacros;
import gg.vape.ui.click.frame.impl.VisibleModuleListFrame;
import gg.vape.ui.click.frame.impl.profile.ProfilesSettingsFrame;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Profile selection and settings are real interactive panels. */
public final class InevitablePanelFrame extends Frame implements CollapsibleFrame {
    public enum Kind { PROFILES, SETTINGS }
    private final Kind kind;
    private boolean collapsed;
    private boolean layoutAssigned;
    private String profileSignature;

    public InevitablePanelFrame(Kind kind) {
        this.kind = kind;
        this.setDisabledOverlayColor(InevitablePanelStyle.BLACK);
        this.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M(false);
        this.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("wrap");
        this.Y(new InevitablePanelHeader(this, kind.name()));
        this.L(false, true);
        this.t(true, false);
        if (kind == Kind.PROFILES) addManageProfiles(); else buildSettingsRows();
    }
    private void addRow(Supplier<String> label, BooleanSupplier selected, Runnable action, Runnable secondary, boolean literal) {
        InevitableActionRow row = new InevitableActionRow(label, selected, action, secondary, literal);
        row.setVisible(!this.collapsed);
        this.h(row, new Object[0]);
    }
    private void addRow(Supplier<String> label, BooleanSupplier selected, Runnable action, Runnable secondary) {
        addRow(label, selected, action, secondary, false);
    }
    private void addManageProfiles() {
        addRow(() -> "Manage profiles", () -> false,
                () -> ClientSettings.INSTANCE.openInevitableTool(ProfilesSettingsFrame.class), null);
    }
    private void buildSettingsRows() {
        addRow(() -> "Client settings", () -> false, () -> ClientSettings.INSTANCE.openClassicTools(ClientSettingsFrame.class), null);
        addRow(() -> "Search modules", () -> false, () -> ClientSettings.INSTANCE.openClassicSearch(), null);
        addRow(() -> ClientSettings.moduleSearchActive ? "Done editing" : "Edit hidden modules", () -> ClientSettings.moduleSearchActive, () -> {
            ClientSettings.moduleSearchActive = !ClientSettings.moduleSearchActive;
            ClientSettings.refreshModuleCategoryHeaders();
        }, null);
        addRow(() -> "Favorites", () -> false, () -> ClientSettings.INSTANCE.openInevitableTool(VisibleModuleListFrame.class), null);
        addRow(() -> "HUD editor", () -> false, () -> ClientSettings.INSTANCE.openHudEditor(), null);
        addRow(() -> "Macros", () -> false, () -> ClientSettings.INSTANCE.openInevitableTool(FrameMacros.class), null);
        addRow(() -> "Friends", () -> false, () -> ClientSettings.INSTANCE.openInevitableTool(OnlineFriendsFrame.class), null);
        addRow(() -> "Classic tools", () -> false, () -> ClientSettings.INSTANCE.openClassicTools(null), null);
        addRow(() -> "Reset layout", () -> false, () -> ClientSettings.inevitableStack.resetLayout(), null);
    }
    @Override public void A$src$V$4ceaf0() {
        if (this.kind != Kind.PROFILES || Vape.INSTANCE.getProfilesManager() == null) return;
        ProfilesManager manager = Vape.INSTANCE.getProfilesManager();
        List<Profile> profiles = new ArrayList<Profile>(manager.getProfiles());
        StringBuilder signature = new StringBuilder();
        for (Profile profile : profiles) signature.append(profile.getLocalId()).append(':').append(profile.getName())
                .append(':').append(profile.isVisible()).append('\n');
        String current = signature.toString();
        if (current.equals(this.profileSignature)) return;
        this.profileSignature = current;
        this.removeMarkedChildren();
        addManageProfiles();
        for (Profile profile : profiles) {
            if (!profile.isVisible()) continue;
            addRow(profile::getName, () -> profile.equals(manager.getActiveProfileOrNull()),
                    () -> manager.switchProfile(profile),
                    () -> ClientSettings.INSTANCE.openInevitableTool(ProfilesSettingsFrame.class), true);
        }
        this.l$src$V$1mibm4x();
    }
    @Override public String getName() { return "Inevitable." + this.kind.name(); }
    @Override public boolean q() { return this.collapsed; }
    @Override public void w() {
        this.collapsed = !this.collapsed;
        for (GuiComponent child : this.f()) if (!(child instanceof FrameHeaderComponent)) child.setVisible(!this.collapsed);
        this.l$src$V$1mibm4x();
    }
    @Override public void z(boolean scrolling) {
        InevitablePanelStyle.panel(this.G$src$D$1b2f02a(), this.n(), this.A(), scrolling ? this.K : this.getComponentHeight());
    }
    @Override public void t(JsonObject state) {
        super.t(state);
        this.layoutAssigned = state.has("x") && !state.get("x").isJsonNull()
                && state.has("y") && !state.get("y").isJsonNull();
    }
    public boolean hasLayout() { return this.layoutAssigned; }
    public void markLayoutAssigned() { this.layoutAssigned = true; }
    public void clearLayout() { this.layoutAssigned = false; }
}
