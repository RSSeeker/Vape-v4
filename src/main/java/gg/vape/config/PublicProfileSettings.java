package gg.vape.config;

import gg.vape.config.Profile;
import gg.vape.config.PublicProfilePrimaryDirtyStringValue;
import gg.vape.config.PublicProfileSecondaryDirtyStringValue;
import gg.vape.config.PublicProfileSelectedProfileStringValue;
import gg.vape.config.PublicProfileSettingsBindSet;
import gg.vape.config.PublicProfileSettingsBindValue;
import gg.vape.ui.font.FontSelector;
import gg.vape.unmap.ModeOption;
import gg.vape.unmap.ModeSelection;
import gg.vape.value.BindValue;
import gg.vape.value.BooleanValue;
import gg.vape.value.ModeValue;
import gg.vape.value.NumberValue;
import gg.vape.value.StringValue;

public class PublicProfileSettings {
    public NumberValue volume;
    public BooleanValue profileSwitchNotifications;
    private Profile selectedProfile;
    public ModeValue language;
    public BooleanValue friendNotifications;
    public BindValue guiBind;
    public BooleanValue muted;
    public BooleanValue autoSave;
    public BooleanValue notifications;
    public final ModeOption centralGuiStyle;
    public BooleanValue autoLoadModuleStates;
    public final ModeOption framesGuiStyle;
    public StringValue alternateAccounts;
    private static int runtimeState;
    public BooleanValue saveToCloud = BooleanValue.create(this, "Save to Cloud", false, "Logs into an account in offline mode.");
    public ModeValue guiStyle;
    public StringValue alteningKey;
    public StringValue selectedProfileId;
    public BooleanValue framePositionsPerProfile;
    public BooleanValue toggleAlerts;

    private void clearAlteningKeyWhenCloudEnabled(BooleanValue saveToCloudValue) {
        if (saveToCloudValue.getEffectiveValue().booleanValue()) {
            this.alteningKey.setValue("");
        }
    }

    public static int getDefaultRuntimeState() {
        int currentState = PublicProfileSettings.getRuntimeState();
        return 0;
    }

    public static void setRuntimeState(int runtimeState) {
        PublicProfileSettings.runtimeState = runtimeState;
    }

    public PublicProfileSettings() {
        this.saveToCloud.addChangeListener(this::clearAlteningKeyWhenCloudEnabled);
        this.alteningKey = new PublicProfilePrimaryDirtyStringValue(this, this, "alteningKey", "");
        this.autoLoadModuleStates = BooleanValue.create(this, "Auto-load module states", true, "Automatically enable saved module states upon loading, and when selecting profiles");
        this.alternateAccounts = new PublicProfileSecondaryDirtyStringValue(this, this, "alts", "");
        this.selectedProfileId = (StringValue)((StringValue)new PublicProfileSelectedProfileStringValue(this, this, "selectedprofile_uuid", "").setBase64Encoded(true)).addAlias("selectedprofile");
        this.autoSave = BooleanValue.create(this, "Auto save", true, "Automatically sync your settings");
        this.framePositionsPerProfile = BooleanValue.create(this, "Frame positions per profile", false, "Saves the positions of your GUI frames per profile");
        this.language = (ModeValue)ModeValue.create((Object)this, "Language", FontSelector.j, FontSelector.j, FontSelector.c).setResettable(false);
        this.volume = NumberValue.create(this, "Volume", "#", "%", 0.0, 50.0, 100.0);
        this.muted = BooleanValue.create(this, "Muted", false, "Mutes all sounds");
        this.guiBind = new PublicProfileSettingsBindValue(this, this, "GUI Bind", new PublicProfileSettingsBindSet(this, 161));
        this.framesGuiStyle = new ModeOption("Frames", 0.8);
        this.centralGuiStyle = new ModeOption("Central", 0.8);
        this.guiStyle = ModeValue.create((Object)this, "GUI style", "Switch between the frames gui and the central gui", (ModeSelection)this.framesGuiStyle, this.centralGuiStyle, this.framesGuiStyle);
        this.notifications = BooleanValue.create(this, "Notifications", true, "Shows notifications");
        this.toggleAlerts = BooleanValue.create(this, "Toggle alert", false, "Notifies you if a module is enabled/disabled.");
        this.profileSwitchNotifications = BooleanValue.create(this, "Profile switch", false, "Notifies you when you switch profiles");
        this.friendNotifications = BooleanValue.create(this, "Friend notifications", true, "Shows notifications related to friends");
    }

    public Profile getSelectedProfile() {
        return this.selectedProfile;
    }

    static {
        PublicProfileSettings.setRuntimeState(36);
    }

    public static Profile setSelectedProfile(PublicProfileSettings settings, Profile profile) {
        settings.selectedProfile = profile;
        return settings.selectedProfile;
    }

    public static int getRuntimeState() {
        return runtimeState;
    }

}
