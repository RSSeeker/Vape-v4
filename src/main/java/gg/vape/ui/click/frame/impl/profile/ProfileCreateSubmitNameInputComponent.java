package gg.vape.ui.click.frame.impl.profile;

import gg.vape.Vape;
import gg.vape.config.Profile;
import gg.vape.ui.click.frame.impl.profile.ProfileCreateNameInputComponent;
import gg.vape.ui.click.frame.impl.profile.ProfileCreatePanelComponent;

class ProfileCreateSubmitNameInputComponent
extends ProfileCreateNameInputComponent {
    private final ProfileCreatePanelComponent createPanel;

    @Override
    public void submit() {
        // Invalid or duplicate names must leave the draft open so it can be
        // corrected or cancelled, rather than orphaning it as the active profile.
        if (!this.hasNonBlankText() || Vape.INSTANCE.getProfilesManager().getProfileByName(this.getText()) != null) {
            super.submit();
            return;
        }
        super.submit();
        this.createPanel.setPendingProfile(null);
        this.createPanel.getSettingsFrame().closePopupAndDiscardDraft();
        this.clearFocus();
    }

    ProfileCreateSubmitNameInputComponent(ProfileCreatePanelComponent createPanel, String placeholder, Profile profile) {
        super(placeholder, profile);
        this.createPanel = createPanel;
    }
}
