package gg.vape.ui.click.frame.impl.profile;

import gg.vape.Vape;
import gg.vape.api.ApiResponse;
import gg.vape.api.ApiServices;
import gg.vape.api.PublicProfileJsonPayloadBuilder;
import gg.vape.config.LegacyPublicProfile;
import gg.vape.config.Profile;
import gg.vape.config.ProfileModuleSnapshot;
import gg.vape.config.ProfileSnapshot;
import gg.vape.config.PublicProfile;
import gg.vape.friend.ui.OnlineFriendUiHelper;
import gg.vape.manager.client.PublicProfileManager;
import gg.vape.module.none.ClientSettings;
import gg.vape.ui.click.component.InsetFilledSpacerComponent;
import gg.vape.ui.click.component.PanelComponent;
import gg.vape.ui.click.component.ProfileSelectionPopupComponent;
import gg.vape.ui.click.component.SimpleTextLabelComponent;
import gg.vape.ui.click.component.SpacerComponent;
import gg.vape.ui.click.component.TextInputComponentBase;
import gg.vape.ui.click.component.TruncatedTextComponent;
import gg.vape.ui.click.component.gui.TextButton;
import gg.vape.ui.click.component.gui.TextLabel;
import gg.vape.ui.click.component.value.BooleanToggleComponent;
import gg.vape.ui.click.frame.impl.profile.CompactPublicProfileFilterTokenSelectorComponent;
import gg.vape.ui.click.frame.impl.profile.ProfilePublishEditorBooleanToggleClickHandler;
import gg.vape.ui.click.frame.impl.profile.ProfilePublishFirstFixedWidthNoSubmitInputComponent;
import gg.vape.ui.click.frame.impl.profile.ProfilePublishSecondFixedWidthNoSubmitInputComponent;
import gg.vape.ui.click.frame.impl.profile.ProfileSnapshotModuleCountEmptyStateComponent;
import gg.vape.ui.click.frame.impl.profile.ProfileSnapshotValueRowComponent;
import gg.vape.ui.click.frame.impl.profile.PublicProfileFilterTokenComponent;
import gg.vape.ui.click.frame.impl.profile.PublicProfileOverlayCloseButton;
import gg.vape.ui.click.frame.impl.profile.PublicProfileOverlayPanelBase;
import gg.vape.ui.click.frame.impl.profile.PublicProfilesFrame;
import gg.vape.ui.notification.NotificationType;
import gg.vape.value.ValueSnapshot;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

public class ProfilePublishEditorPanel
extends PublicProfileOverlayPanelBase {
    private ProfileSnapshot snapshot;
    private Profile sourceProfile;
    private CompactPublicProfileFilterTokenSelectorComponent tagSelector;
    private TextInputComponentBase nameInput;
    private BooleanToggleComponent anonymousToggle;
    private BooleanToggleComponent shareCodeOnlyToggle;
    private BooleanToggleComponent friendsOnlyToggle;
    private TruncatedTextComponent titleLabel;
    private TextInputComponentBase descriptionInput;
    private TextButton publishButton;

    private void showDetailsForm() {
        this.b$src$V$s019hq();
        double d = this.gg.A() - 3.0;
        SimpleTextLabelComponent simpleTextLabelComponent = new SimpleTextLabelComponent("NAME", 0.7, ProfilePublishEditorPanel.J.C, true);
        simpleTextLabelComponent.setOffsetX(0.0f);
        this.gg.h(simpleTextLabelComponent, new Object[0]);
        if (this.nameInput == null) {
            this.nameInput = new ProfilePublishFirstFixedWidthNoSubmitInputComponent("+   Enter profile name...", d);
        }
        this.nameInput.setHorizontalInset(0.0);
        this.nameInput.setLeftInset(0.0f);
        this.nameInput.setRightInset(1.0f);
        this.nameInput.setTextColor(ProfilePublishEditorPanel.J.A);
        this.nameInput.setPlaceholderColor(ProfilePublishEditorPanel.J.Z);
        this.nameInput.getActionButton().setVisible(false);
        this.nameInput.setShowDisabledOverlay(false);
        this.nameInput.setBackgroundVisible(false);
        this.nameInput.addKeyTypedListener(this::handleNameInput);
        this.gg.h(this.nameInput, new Object[0]);
        this.gg.h(new InsetFilledSpacerComponent(d, 2.0, 0.5, 0.0, ProfilePublishEditorPanel.J.l), new Object[0]);
        this.gg.h(new SpacerComponent(0.0, 5.0), new Object[0]);
        SimpleTextLabelComponent simpleTextLabelComponent2 = new SimpleTextLabelComponent("DESCRIPTION", 0.7, ProfilePublishEditorPanel.J.C, true);
        simpleTextLabelComponent2.setOffsetX(0.0f);
        this.gg.h(simpleTextLabelComponent2, new Object[0]);
        if (this.descriptionInput == null) {
            this.descriptionInput = new ProfilePublishSecondFixedWidthNoSubmitInputComponent("+   Add Description (optional)", d);
        }
        this.descriptionInput.setHorizontalInset(0.0);
        this.descriptionInput.setLeftInset(0.0f);
        this.descriptionInput.setRightInset(1.0f);
        this.descriptionInput.setTextColor(ProfilePublishEditorPanel.J.A);
        this.descriptionInput.setPlaceholderColor(ProfilePublishEditorPanel.J.Z);
        this.descriptionInput.getActionButton().setVisible(false);
        this.descriptionInput.setShowDisabledOverlay(false);
        this.descriptionInput.setBackgroundVisible(false);
        this.gg.h(this.descriptionInput, new Object[0]);
        this.gg.h(new InsetFilledSpacerComponent(d, 2.0, 0.5, 0.0, ProfilePublishEditorPanel.J.l), new Object[0]);
        this.gg.h(new SpacerComponent(0.0, 5.0), new Object[0]);
        SimpleTextLabelComponent simpleTextLabelComponent3 = new SimpleTextLabelComponent("TAGS", 0.7, ProfilePublishEditorPanel.J.C, true);
        simpleTextLabelComponent3.setOffsetX(0.0f);
        this.gg.h(simpleTextLabelComponent3, "widthwrap");
        SimpleTextLabelComponent simpleTextLabelComponent4 = new SimpleTextLabelComponent("Comma-Seperated", 0.7, ProfilePublishEditorPanel.J.h, false);
        double d2 = simpleTextLabelComponent4.getTextWidth();
        this.getClass();
        simpleTextLabelComponent4.o(d2 + (double)(5.0f * 2.0f));
        this.gg.h(simpleTextLabelComponent4, "alignright, wrap");
        if (this.tagSelector == null) {
            this.tagSelector = new CompactPublicProfileFilterTokenSelectorComponent("+   Add Tags (optional)", d, 20.0);
        }
        this.gg.h(this.tagSelector, "wrap");
        this.gg.h(new InsetFilledSpacerComponent(d, 2.0, 0.5, 0.0, ProfilePublishEditorPanel.J.l), new Object[0]);
        this.gg.h(new SpacerComponent(0.0, 5.0), new Object[0]);
        SimpleTextLabelComponent simpleTextLabelComponent5 = new SimpleTextLabelComponent("PREFERENCES", 0.7, ProfilePublishEditorPanel.J.C, true);
        simpleTextLabelComponent5.setOffsetX(0.0f);
        this.gg.h(simpleTextLabelComponent5, new Object[0]);
        if (this.shareCodeOnlyToggle == null) {
            this.shareCodeOnlyToggle = new BooleanToggleComponent("Discoverable with a Share Code only", 0.8);
        }
        this.shareCodeOnlyToggle.setHorizontalInset(0.0);
        this.shareCodeOnlyToggle.setExplicitWidth(d);
        this.shareCodeOnlyToggle.setShowDisabledOverlay(false);
        this.gg.h(this.shareCodeOnlyToggle, new Object[0]);
        if (this.friendsOnlyToggle == null) {
            this.friendsOnlyToggle = new BooleanToggleComponent("Friends only discovery", 0.8);
            this.shareCodeOnlyToggle.addMouseListener(new ProfilePublishEditorBooleanToggleClickHandler(this));
            this.friendsOnlyToggle.setVisible(false);
        }
        this.friendsOnlyToggle.setHorizontalInset(0.0);
        this.friendsOnlyToggle.setExplicitWidth(d);
        this.friendsOnlyToggle.setShowDisabledOverlay(false);
        this.gg.h(this.friendsOnlyToggle, new Object[0]);
        if (this.anonymousToggle == null) {
            this.anonymousToggle = new BooleanToggleComponent("Upload anonymously", 0.8);
        }
        this.anonymousToggle.setHorizontalInset(0.0);
        this.anonymousToggle.setExplicitWidth(d);
        this.anonymousToggle.setShowDisabledOverlay(false);
        this.gg.h(this.anonymousToggle, new Object[0]);
    }

    private void handlePublishComplete(ApiResponse apiResponse, Throwable throwable) {
        if (throwable != null) {
            Vape.logThrowable(throwable);
            PublicProfileManager.showWarning("Failed to publish profile.");
            this.profilesFrame.O(null);
            return;
        }
        if (!apiResponse.isSuccessful()) {
            PublicProfileManager.showWarning("Failed to publish profile: " + apiResponse.getError());
            return;
        }
        this.profilesFrame.O(null);
        PublicProfile publicProfile = (PublicProfile)apiResponse.getData();
        assert publicProfile != null;
        Vape.INSTANCE.getPublicProfileManager().addProfile(publicProfile);
        this.profilesFrame.N(publicProfile);
    }

    private static ApiResponse ignoreHandledPublishFailure(Throwable throwable) {
        return null;
    }

    private static int compareDefaultValuesLast(ValueSnapshot<?, ?> first, ValueSnapshot<?, ?> second) {
        return Boolean.compare(first.isDefault(), second.isDefault());
    }

    void toggleFriendsOnlyVisibility() {
        this.friendsOnlyToggle.setVisible(!this.friendsOnlyToggle.V$src$Z$1xhop3l());
    }

    private void showModuleDetails(ProfileModuleSnapshot moduleSnapshot) {
        this.b$src$V$s019hq();
        double d = this.gg.A();
        this.getClass();
        double d2 = d - 5.0;
        PanelComponent panelComponent = new PanelComponent(d2, 12.0);
        panelComponent.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("widthwrap");
        panelComponent.setShowDisabledOverlay(false);
        this.gg.h(panelComponent, new Object[0]);
        SimpleTextLabelComponent simpleTextLabelComponent = new SimpleTextLabelComponent(moduleSnapshot.getName(), 1.0);
        simpleTextLabelComponent.setBold(true);
        simpleTextLabelComponent.setTextColor(ProfilePublishEditorPanel.J.A);
        panelComponent.h(simpleTextLabelComponent, new Object[0]);
        List<ValueSnapshot<?, ?>> list = moduleSnapshot.getValueSnapshots().stream().sorted(ProfilePublishEditorPanel::compareDefaultValuesLast).collect(Collectors.toList());
        for (ValueSnapshot<?, ?> valueSnapshot : list) {
            ProfileSnapshotValueRowComponent profileSnapshotValueRowComponent = new ProfileSnapshotValueRowComponent(valueSnapshot);
            profileSnapshotValueRowComponent.o(this.gg.A() - 5.0);
            profileSnapshotValueRowComponent.setDisabledOverlayColor(ProfilePublishEditorPanel.J.m);
            this.gg.h(profileSnapshotValueRowComponent, new Object[0]);
        }
    }

    private void setupFooter() {
        this.s$src$V$1l7a8uk();
        this.gb.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("widthwrap");
        this.gb.h(new SpacerComponent(0.0, 10.0), "wrap");
        this.gb.h(new SpacerComponent(105.0, 0.0), new Object[0]);
        TextLabel textLabel = new TextLabel("CANCEL", 0.7, true);
        textLabel.setTextColor(null);
        textLabel.setUseAlternateFont(true);
        textLabel.o(40.0);
        textLabel.Y(15.0);
        textLabel.addClickListener(this::cancelPublishing);
        this.gb.h(textLabel, new Object[0]);
        this.publishButton = new TextButton("PUBLISH", 0.7, ProfilePublishEditorPanel.J.B, ProfilePublishEditorPanel.J.O);
        this.publishButton.setUseAlternateFont(true);
        this.publishButton.o(60.0);
        this.publishButton.Y(15.0);
        this.publishButton.setDeriveTextColorFromBackground(false);
        this.publishButton.setNormalTextColor(Color.WHITE);
        this.updatePublishButtonState();
        this.publishButton.setSingleFutureClickListener(this::publishProfile);
        this.gb.h(this.publishButton, new Object[0]);
    }

    private void cancelPublishing() {
        this.profilesFrame.O(null);
    }

    private void changeSourceProfile(Profile profile) {
        Vape.INSTANCE.getProfilesManager().captureIfActive(profile);
        ProfileSnapshot profileSnapshot = profile.createSnapshot(true);
        if (profileSnapshot == null) {
            OnlineFriendUiHelper.showNotification(NotificationType.WARNING, "Failed to change derived from.");
            return;
        }
        this.sourceProfile = profile;
        this.snapshot = profileSnapshot;
        this.e();
    }

    public ProfilePublishEditorPanel(PublicProfilesFrame publicProfilesFrame, Profile profile) {
        super(publicProfilesFrame);
        this.sourceProfile = profile;
        Vape.INSTANCE.getProfilesManager().captureIfActive(profile);
        this.snapshot = this.sourceProfile.createSnapshot(true);
        if (this.snapshot == null) this.snapshot = new ProfileSnapshot(profile, new com.google.gson.JsonArray());
        this.e();
    }


    private void openModuleDetails(ProfileModuleSnapshot moduleSnapshot) {
        this.showModuleDetails(moduleSnapshot);
    }

    private void handleNameInput(char character, int keyCode) {
        this.titleLabel.setText(this.nameInput.getText());
        this.updatePublishButtonState();
    }

    private CompletableFuture<ApiResponse> publishProfile() {
        Object object;
        String string = this.nameInput.getText().trim();
        if (string.length() < 3) {
            PublicProfileManager.showWarning("Please provide a profile name!");
            this.nameInput.setPlaceholderColor(ProfilePublishEditorPanel.J.d);
            return null;
        }
        String string2 = this.descriptionInput.getText().trim();
        boolean bl = this.shareCodeOnlyToggle.isOn();
        boolean bl2 = this.friendsOnlyToggle.isOn();
        boolean bl3 = this.anonymousToggle.isOn();
        ArrayList<String> arrayList = new ArrayList<String>(this.tagSelector.getTokenValues());
        if (arrayList.size() < 5 && (object = LegacyPublicProfile.normalizeTag(this.tagSelector.getInput().getText().trim())) != null) {
            String string3 = LegacyPublicProfile.validateTag((String)object);
            if (string3 != null) {
                PublicProfileManager.showWarning(string3);
                return null;
            }
            arrayList.add((String)object);
            this.tagSelector.addToken(new PublicProfileFilterTokenComponent((String)object));
            this.tagSelector.getInput().setText("");
        }
        if (this.snapshot == null) {
            PublicProfileManager.showWarning("This profile has no settings to publish.");
            return null;
        }
        // Serialize the displayed snapshot using the public codec, even when
        // the source is inactive and has no cached published representation.
        object = this.snapshot.toPublicationData();
        return ApiServices.getInstance().getPublicProfileApi().createProfile(PublicProfileJsonPayloadBuilder.build(string, "4.21", string2, arrayList, !bl, bl3, bl2, this.sourceProfile.getOnlineId(), (com.google.gson.JsonObject)object)).whenCompleteAsync(this::handlePublishComplete, (Executor)ClientSettings.UI_EXECUTOR).exceptionally(ProfilePublishEditorPanel::ignoreHandledPublishFailure);
    }

    @Override
    protected void e() {
        super.e();
        if (this.sourceProfile == null) {
            return;
        }
        this.getClass();
        double d = 5.0f * 4.0f;
        this.o(this.profilesFrame.A() - d);
        double d2 = this.profilesFrame.L() - this.profilesFrame.j$src$Lgg_vape_ui_click_frame_FrameHeaderComponent_$175vsfc().L() - 2.0 - d;
        this.getClass();
        this.Y(d2 - 5.0);
        PanelComponent panelComponent = this.getLeftPanel();
        this.getLeftPanel().setShowDisabledOverlay(false);
        this.getLeftPanel().l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("wrap");
        this.h(panelComponent, new Object[0]);
        double d3 = panelComponent.A();
        this.getClass();
        PanelComponent panelComponent2 = new PanelComponent(d3 - (double)(5.0f * 2.0f), 25.0);
        panelComponent2.setShowDisabledOverlay(false);
        panelComponent2.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("wrap");
        PanelComponent panelComponent3 = this.getLeftPanel();
        this.getClass();
        panelComponent3.h(new SpacerComponent(5.0, 0.0), "widthwrap");
        panelComponent.h(panelComponent2, new Object[0]);
        double d4 = panelComponent2.A();
        this.getClass();
        this.titleLabel = new TruncatedTextComponent("New Profile", "...", d4 - (double)(5.0f * 2.0f), 1.0, ProfilePublishEditorPanel.J.A, true);
        this.titleLabel.Y(0.0);
        this.titleLabel.setExplicitHeight(0.0);
        PanelComponent panelComponent4 = new PanelComponent(panelComponent2.A(), 8.0);
        panelComponent4.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("widthwrap");
        panelComponent4.setShowDisabledOverlay(false);
        this.getClass();
        panelComponent4.h(new SpacerComponent(5.0, 0.0), "widthwrap");
        panelComponent4.h(this.titleLabel, new Object[0]);
        panelComponent2.h(panelComponent4, new Object[0]);
        this.getClass();
        panelComponent2.h(new SpacerComponent(0.0, 5.0), new Object[0]);
        List<Profile> list = Vape.INSTANCE.getPublicProfileManager().getDerivedProfiles();
        ArrayList<Profile> arrayList = new ArrayList<Profile>(Vape.INSTANCE.getProfilesManager().getProfiles());
        arrayList.removeIf(list::contains);
        ProfileSelectionPopupComponent profileSelectionPopupComponent = new ProfileSelectionPopupComponent("Derived From", this.sourceProfile, arrayList.toArray(new Profile[0]));
        profileSelectionPopupComponent.Y(6.0);
        profileSelectionPopupComponent.o(panelComponent2.A());
        profileSelectionPopupComponent.setSelectionCallback(this::changeSourceProfile);
        profileSelectionPopupComponent.setShowDisabledOverlay(false);
        panelComponent2.h(profileSelectionPopupComponent, "widthwrap");
        PanelComponent panelComponent5 = new PanelComponent(panelComponent.A(), panelComponent.L() - panelComponent2.L() - 6.0);
        panelComponent.h(panelComponent5, new Object[0]);
        panelComponent5.setShowDisabledOverlay(false);
        panelComponent5.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("wrap");
        PublicProfileOverlayCloseButton publicProfileOverlayCloseButton = new PublicProfileOverlayCloseButton("Details", 0.8, true, this, this::showDetailsForm);
        publicProfileOverlayCloseButton.setUseExplicitWidth(true);
        publicProfileOverlayCloseButton.o(panelComponent5.A());
        panelComponent5.h(publicProfileOverlayCloseButton, new Object[0]);
        panelComponent5.h(new SpacerComponent(0.0, 6.0), new Object[0]);
        List<ProfileModuleSnapshot> list2 = this.snapshot.getSortedModules(false);
        ProfileSnapshotModuleCountEmptyStateComponent profileSnapshotModuleCountEmptyStateComponent = new ProfileSnapshotModuleCountEmptyStateComponent(list2.size());
        panelComponent5.h(profileSnapshotModuleCountEmptyStateComponent, new Object[0]);
        PanelComponent panelComponent6 = new PanelComponent(panelComponent5.A(), panelComponent5.L() - profileSnapshotModuleCountEmptyStateComponent.L() - publicProfileOverlayCloseButton.L());
        panelComponent6.setShowDisabledOverlay(false);
        panelComponent6.t(panelComponent6.L());
        panelComponent5.h(panelComponent6, new Object[0]);
        panelComponent6.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("wrap");
        for (ProfileModuleSnapshot profileModuleSnapshot : list2) {
            PublicProfileOverlayCloseButton publicProfileOverlayCloseButton2 = new PublicProfileOverlayCloseButton(profileModuleSnapshot.getName(), 0.8, this, () -> this.openModuleDetails(profileModuleSnapshot));
            publicProfileOverlayCloseButton2.setUseExplicitWidth(true);
            publicProfileOverlayCloseButton2.o(panelComponent6.A() - 2.0);
            panelComponent6.h(publicProfileOverlayCloseButton2, new Object[0]);
        }
        this.setupFooter();
    }

    private void updatePublishButtonState() {
        String string = this.nameInput.getText().trim();
        if (string.length() < 3) {
            this.publishButton.setBackgroundAnimationColors(ProfilePublishEditorPanel.J.l, ProfilePublishEditorPanel.J.l);
            this.publishButton.setNormalTextColor(ProfilePublishEditorPanel.J.C);
        } else {
            this.publishButton.setBackgroundAnimationColors(ProfilePublishEditorPanel.J.B, ProfilePublishEditorPanel.J.O);
            this.publishButton.setNormalTextColor(Color.WHITE);
        }
        this.nameInput.setPlaceholderColor(ProfilePublishEditorPanel.J.Z);
    }
}
