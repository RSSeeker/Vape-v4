package gg.vape.ui.click.frame.impl.main;

import gg.vape.Vape;
import gg.vape.api.ApiResponse;
import gg.vape.api.ApiServices;
import gg.vape.config.Profile;
import gg.vape.event.EventBus;
import gg.vape.event.EventHandler;
import gg.vape.event.EventListener;
import gg.vape.event.impl.ProfileListMutationEvent;
import gg.vape.module.none.ClientSettings;
import gg.vape.ui.click.animation.DoubleAnimation;
import gg.vape.ui.click.component.FlowLayoutComponent;
import gg.vape.ui.click.component.GlyphIconComponent;
import gg.vape.ui.click.component.GuiComponent;
import gg.vape.ui.click.component.PanelComponent;
import gg.vape.ui.click.component.SpacerComponent;
import gg.vape.ui.click.component.gui.InteractiveComponent;
import gg.vape.ui.click.component.gui.TextButton;
import gg.vape.ui.click.component.input.BindableInputComponent;
import gg.vape.ui.click.component.input.SmallTextInputComponent;
import gg.vape.ui.click.component.layout.PaddedComponent;
import gg.vape.ui.click.frame.FrameScrollbarPlacement;
import gg.vape.ui.click.frame.impl.main.ClickGuiFrameManager;
import gg.vape.ui.click.frame.impl.main.ClickGuiMainFrame;
import gg.vape.ui.click.frame.impl.main.ClickGuiOverlayPlacement;
import gg.vape.ui.click.frame.impl.main.ClickGuiOverlaySpec;
import gg.vape.ui.click.frame.impl.main.ClickGuiPageBase;
import gg.vape.ui.click.frame.impl.main.ClickGuiProfileCardComponent;
import gg.vape.ui.click.frame.impl.main.ClickGuiProfileHeaderComponent;
import gg.vape.ui.click.frame.impl.profile.ProfileCardActionState;
import gg.vape.ui.click.frame.impl.profile.ProfileListEntryBadgeComponent;
import gg.vape.ui.click.frame.impl.profile.ProfileListEntryMetadataComponent;
import gg.vape.ui.click.frame.impl.profile.ProfileListEntryOpenButtonComponent;
import gg.vape.ui.click.frame.impl.profile.ProfileModuleSnapshotListComponent;
import gg.vape.ui.click.frame.impl.profile.ProfileModuleSnapshotListStyle;
import gg.vape.ui.click.frame.impl.profile.ProfilesPageEmptyStateComponent;
import gg.vape.ui.click.frame.impl.profile.ProfilesPageOverlayController;
import gg.vape.ui.click.frame.impl.profile.ProfilesPageRefreshListener;
import gg.vape.ui.click.frame.impl.profile.ProfilesSettingsFrameState;
import gg.vape.ui.click.frame.impl.profile.PublicProfilesFrame;
import java.awt.Color;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Predicate;
import org.jetbrains.annotations.Nullable;

public class ClickGuiProfilesPage
extends ClickGuiPageBase
implements EventListener {
    private final DoubleAnimation cardSelectionAnimation;
    private final ClickGuiMainFrame mainFrame;
    private final Runnable overlayCloseCallback;
    private TextButton newProfileButton;
    private boolean cardSelectionActive;
    private SmallTextInputComponent createNameInput;
    @Nullable
    private ClickGuiProfileCardComponent draftProfileCard;
    private Profile draftProfile;
    private ProfileModuleSnapshotListComponent snapshotList;
    private FlowLayoutComponent profileList;
    private PanelComponent mainPanel;
    private TextButton createButton;
    private Profile previousActiveProfile;
    private Profile displayedActiveProfile;
    private boolean creatingProfile;
    private ClickGuiProfileHeaderComponent profileHeader;

    private void beginCreateProfile() {
        if (this.creatingProfile) {
            return;
        }
        this.mainFrame.closeActiveOverlay();
        Profile profile = Vape.INSTANCE.getProfilesManager().captureActiveProfile();
        this.previousActiveProfile = profile;
        this.draftProfile = new Profile(profile.getName(), "4.21");
        this.draftProfile.loadJson(profile.toJson(true));
        this.draftProfile.setLocalId(UUID.randomUUID());
        this.draftProfile.setOnlineId(null);
        this.draftProfile.setPublicProfileFlag(false);
        this.draftProfile.setDraft(true);
        ApiServices.getInstance().getUserDataApi().reserveProfileId().whenCompleteAsync(this::handleDraftProfileIdResponse, (Executor)ClientSettings.UI_EXECUTOR).exceptionally(ClickGuiProfilesPage::ignoreCreateProfileFailure);
        Vape.INSTANCE.getProfilesManager().switchProfile(this.draftProfile);
        this.creatingProfile = true;
        this.refreshProfileList();
        this.renderMainContent();
    }

    private void cancelCreateProfile() {
        if (!this.creatingProfile) {
            return;
        }
        Profile profile = Vape.INSTANCE.getProfilesManager().getActiveProfile();
        if (profile != null && profile.isDraft()) {
            Vape.INSTANCE.getProfilesManager().setActiveProfile(this.previousActiveProfile != null ? this.previousActiveProfile : this.displayedActiveProfile);
        }
        this.draftProfile = null;
        this.previousActiveProfile = null;
        this.createNameInput = null;
        this.draftProfileCard = null;
        this.createButton = null;
        this.creatingProfile = false;
        this.refreshProfileList();
        this.renderMainContent();
    }

    private void selectProfileCard(Profile profile) {
        if (this.profileList == null) {
            return;
        }
        this.cardSelectionActive = profile != null;
        for (GuiComponent guiComponent : this.profileList.f()) {
            ClickGuiProfileCardComponent clickGuiProfileCardComponent;
            if (!(guiComponent instanceof PaddedComponent) || (clickGuiProfileCardComponent = ((PaddedComponent)guiComponent).t(ClickGuiProfileCardComponent.class)) == null || clickGuiProfileCardComponent.getProfile() == null) continue;
            boolean bl = profile != null && clickGuiProfileCardComponent.getProfile() == profile;
            clickGuiProfileCardComponent.setSelected(bl);
            boolean bl2 = profile != null && !bl;
            clickGuiProfileCardComponent.setDimmed(bl2);
        }
    }

    private void populateProfileSidecar(Profile profile, PanelComponent panelComponent) {
        double d = 16.0;
        double d2 = 4.0;
        double d3 = 18.0;
        double d4 = 8.0;
        PanelComponent panelComponent2 = new PanelComponent(panelComponent.A() - d4 * 2.0, d);
        panelComponent2.setShowDisabledOverlay(false);
        panelComponent2.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("widthwrap");
        double d5 = panelComponent.A() - d4 * 2.0 - d3 - d2;
        ProfileListEntryMetadataComponent profileListEntryMetadataComponent = new ProfileListEntryMetadataComponent(profile);
        profileListEntryMetadataComponent.setExplicitWidth(d5);
        profileListEntryMetadataComponent.setExplicitHeight(d);
        ProfileListEntryOpenButtonComponent profileListEntryOpenButtonComponent = new ProfileListEntryOpenButtonComponent(profile, this::closeOverlayAndRefreshList);
        profileListEntryOpenButtonComponent.useOverlayStyle();
        profileListEntryOpenButtonComponent.setExplicitWidth(d3);
        profileListEntryOpenButtonComponent.setExplicitHeight(d);
        panelComponent2.h(profileListEntryMetadataComponent, new Object[0]);
        panelComponent2.h(new SpacerComponent(d2, 0.0), new Object[0]);
        panelComponent2.h(profileListEntryOpenButtonComponent, new Object[0]);
        panelComponent.h(new PaddedComponent(4.0, 0.0, d4, d4, panelComponent2), new Object[0]);
        double d6 = d + 4.0 + 8.0;
        double d7 = panelComponent.L() - d6 - 22.0;
        ProfileModuleSnapshotListComponent profileModuleSnapshotListComponent = new ProfileModuleSnapshotListComponent(profile, panelComponent.A() - 4.0, d7);
        profileModuleSnapshotListComponent.setStyle(ProfileModuleSnapshotListStyle.MODERN);
        panelComponent.h(new SpacerComponent(0.0, 4.0), new Object[0]);
        panelComponent.h(profileModuleSnapshotListComponent, new Object[0]);
    }

    private void renderExistingProfile(Profile profile, double d, double d2) {
        this.profileHeader = new ClickGuiProfileHeaderComponent(profile, d2);
        this.mainPanel.h(new PaddedComponent(2.0, d, d, d, this.profileHeader), new Object[0]);
        double d3 = d * 2.0;
        double d4 = this.mainPanel.L() - this.mainPanel.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().y() - d3;
        this.snapshotList = new ProfileModuleSnapshotListComponent(profile, this.mainPanel.A() - 6.0 - 3.0, d4);
        this.snapshotList.setStyle(ProfileModuleSnapshotListStyle.MODERN);
        this.mainPanel.h(new PaddedComponent(0.0, d, 3.0, 0.0, this.snapshotList), new Object[0]);
    }

    private void openProfileSettings() {
        this.mainFrame.showOverlay(ClickGuiOverlaySpec.builder().title("Settings").sidecarIcon("newsettings").placement(ClickGuiOverlayPlacement.DOCKED).initializeContent(this::populateSettings).build());
    }

    public static SmallTextInputComponent getCreateNameInput(ClickGuiProfilesPage page) {
        return page.createNameInput;
    }

    private Color applyCardSelectionState(Color color) {
        return ProfileCardActionState.t(color, this.cardSelectionAnimation, this.cardSelectionActive);
    }

    private void handleProfileCardSettings(Profile profile) {
        if (!this.creatingProfile) {
            this.openProfile(profile);
        }
    }

    private static void openPublicProfiles() {
        PublicProfilesFrame publicProfilesFrame = ClientSettings.getFrame(PublicProfilesFrame.class);
        ClickGuiFrameManager clickGuiFrameManager = (ClickGuiFrameManager)ClientSettings.INSTANCE.getActiveStack();
        clickGuiFrameManager.setSidecarFrame(publicProfilesFrame);
    }

    private void renderSidebar() {
        GuiComponent guiComponent = this.getSidebarHeader().f().get(0);
        this.getSidebarHeader().removeMarkedChildren();
        this.getSidebarHeader().h(guiComponent, "widthwrap");
        this.newProfileButton = new TextButton("NEW PROFILE", 0.625, J.z(), J.z().brighter(), null, 2.0f, 1.0f, 53.0, 16.0);
        this.newProfileButton.setIconResource("newadd");
        this.newProfileButton.setIconSize(6.0f);
        this.newProfileButton.setUseAlternateFont(true);
        this.newProfileButton.setUppercase(true);
        this.newProfileButton.setDeriveTextColorFromBackground(true);
        this.newProfileButton.setUseThemeBackground(true);
        this.newProfileButton.addClickListener(this::beginCreateProfile);
        Object object = new GlyphIconComponent("newsettings", 6.0, 6.0, 10.0, 10.0, null, null, null);
        ((GlyphIconComponent)object).setCenterVertically(true);
        ((GlyphIconComponent)object).setCenterHorizontally(true);
        ((InteractiveComponent)object).addClickListener(this::openProfileSettings);
        this.getSidebarHeader().h(new SpacerComponent(this.getSidebarHeader().A() - this.getSidebarHeader().l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().C() - this.newProfileButton.A() - ((GuiComponent)object).A() - 8.0 - 1.0, 0.0), new Object[0]);
        this.getSidebarHeader().h(new PaddedComponent(0.0, 0.0, 0.0, 8.0, this.newProfileButton), new Object[0]);
        this.getSidebarHeader().h(new PaddedComponent(4.0, 0.0, 0.0, 1.0, (GuiComponent)object), new Object[0]);
        this.getSidebarContent().removeMarkedChildren();
        this.getSidebarContent().h(new SpacerComponent(0.0, 3.0), new Object[0]);
        this.profileList = this.createFlowLayout(this.getSidebarContent().A(), this.getSidebarContent().L() - this.getSidebarContent().l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().y() - 21.0);
        this.profileList.F(FrameScrollbarPlacement.OUTSIDE);
        this.profileList.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("wrap");
        this.profileList.t(this.profileList.L());
        this.profileList.setShowDisabledOverlay(false);
        this.profileList.E(true);
        this.profileList.h(new SpacerComponent(0.0, 1.0), new Object[0]);
        this.getSidebarContent().h(this.profileList, new Object[0]);
        object = Vape.INSTANCE.getProfilesManager().getActiveProfile();
        for (Profile profile : Vape.INSTANCE.getProfilesManager().getProfiles()) {
            ClickGuiProfileCardComponent clickGuiProfileCardComponent = new ClickGuiProfileCardComponent(profile);
            clickGuiProfileCardComponent.o(this.profileList.A());
            clickGuiProfileCardComponent.Y(20.0);
            clickGuiProfileCardComponent.setActive(profile == object);
            clickGuiProfileCardComponent.setSettingsAction(() -> this.openProfileFromCard(profile));
            this.profileList.h(new PaddedComponent(0.0, 3.0, 0.0, 0.0, clickGuiProfileCardComponent), new Object[0]);
        }
        ProfileListEntryBadgeComponent profileListEntryBadgeComponent = new ProfileListEntryBadgeComponent();
        profileListEntryBadgeComponent.setBadgeCount(6);
        profileListEntryBadgeComponent.addClickListener(ClickGuiProfilesPage::openPublicProfiles);
        this.getSidebarContent().h(new PaddedComponent(6.0, 0.0, 3.0, 0.0, profileListEntryBadgeComponent), new Object[0]);
    }

    private void createProfile() {
        if (this.draftProfile == null || this.createNameInput == null) {
            return;
        }
        if (!this.createNameInput.hasNonBlankText()) {
            this.createNameInput.setText("");
            return;
        }
        String string = this.createNameInput.getText();
        Profile profile = Vape.INSTANCE.getProfilesManager().getProfileByName(string);
        if (profile != null) {
            return;
        }
        this.draftProfile.setName(string);
        this.draftProfile.setDirty(true);
        this.draftProfile.setDraft(false);
        Vape.INSTANCE.getProfilesManager().addProfile(this.draftProfile, true);
        Vape.INSTANCE.getProfilesManager().setActiveProfile(this.draftProfile);
        this.draftProfile = null;
        this.previousActiveProfile = null;
        this.createNameInput = null;
        this.draftProfileCard = null;
        this.createButton = null;
        this.creatingProfile = false;
        this.refreshProfileList();
        this.renderMainContent();
    }

    @EventHandler
    public void onProfileListMutation(ProfileListMutationEvent profileListMutationEvent) {
        this.refreshProfileList();
        Profile profile = Vape.INSTANCE.getProfilesManager().getActiveProfile();
        if (!this.creatingProfile && this.displayedActiveProfile != profile) {
            this.renderMainContent();
        }
    }

    private void openProfileFromCard(Profile profile) {
        this.openProfile(profile);
    }

    private void closeOverlayAndRefreshList() {
        this.mainFrame.closeActiveOverlay();
        this.refreshProfileList();
    }

    private void populateSettings(PanelComponent panelComponent) {
        for (GuiComponent guiComponent : ProfilesSettingsFrameState.F(true)) {
            guiComponent.o(panelComponent.A());
            guiComponent.setExplicitWidth(panelComponent.A());
            panelComponent.h(guiComponent, new Object[0]);
        }
    }

    private void renderCreateProfile(Profile profile, double d, double d2) {
        this.createNameInput = new SmallTextInputComponent("Type name");
        this.createNameInput.setRightInset(0.0f);
        this.createNameInput.setHorizontalInset(0.0);
        this.createNameInput.setLeftInset(0.0f);
        this.createNameInput.setVerticalInset(0.0f);
        this.createNameInput.setUseExplicitHeight(true);
        this.createNameInput.Y(14.0);
        this.createNameInput.setMaxLength(48);
        BindableInputComponent bindableInputComponent = new BindableInputComponent(profile, ClickGuiProfilesPage.J.Z);
        bindableInputComponent.setActiveOverride(false);
        bindableInputComponent.Y(10.0);
        ProfilesPageEmptyStateComponent profilesPageEmptyStateComponent = new ProfilesPageEmptyStateComponent(this, bindableInputComponent);
        profilesPageEmptyStateComponent.o(d2);
        profilesPageEmptyStateComponent.Y(22.0);
        profilesPageEmptyStateComponent.setShowDisabledOverlay(false);
        profilesPageEmptyStateComponent.addChildren(this.createNameInput, bindableInputComponent);
        this.mainPanel.h(new PaddedComponent(2.0, d, d, d, profilesPageEmptyStateComponent), new Object[0]);
        PanelComponent panelComponent = new PanelComponent(this.mainPanel.A(), 14.0);
        panelComponent.setShowDisabledOverlay(false);
        panelComponent.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("widthwrap");
        TextButton textButton = new TextButton("CANCEL", 0.625, ClickGuiProfilesPage.J.i, ClickGuiProfilesPage.J.i.brighter(), null, 2.0f, 1.0f, 35.5, 14.0);
        textButton.setUppercase(true);
        textButton.setUseAlternateFont(true);
        textButton.setDeriveTextColorFromBackground(false);
        textButton.setNormalTextColor(ClickGuiProfilesPage.J.A);
        textButton.addClickListener(this::cancelCreateProfile);
        this.createButton = new TextButton("CREATE", 0.625, ClickGuiProfilesPage.J.B, ClickGuiProfilesPage.J.B.brighter(), null, 2.0f, 1.0f, 35.5, 14.0);
        this.createButton.setUppercase(true);
        this.createButton.setUseAlternateFont(true);
        this.createButton.setDeriveTextColorFromBackground(false);
        this.createButton.setNormalTextColor(ClickGuiProfilesPage.J.A);
        this.createButton.setInteractionDisabled(true);
        this.createButton.addClickListener(this::createProfile);
        panelComponent.h(new SpacerComponent(this.mainPanel.A() - textButton.A() - 4.0 - this.createButton.A() - 8.0, 0.0), new Object[0]);
        panelComponent.h(new PaddedComponent(0.0, 0.0, 0.0, 4.0, textButton), new Object[0]);
        panelComponent.h(new PaddedComponent(0.0, 0.0, 0.0, 0.0, this.createButton), new Object[0]);
        double d3 = this.mainPanel.L() - this.mainPanel.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().y() - panelComponent.L() - d * 5.0 + 1.0;
        d3 = Math.max(0.0, d3);
        this.snapshotList = new ProfileModuleSnapshotListComponent(profile, this.mainPanel.A() - 6.0 - 3.0, d3);
        this.snapshotList.setStyle(ProfileModuleSnapshotListStyle.MODERN);
        this.mainPanel.h(new PaddedComponent(0.0, 0.0, 3.0, 0.0, this.snapshotList), new Object[0]);
        this.mainPanel.h(new PaddedComponent(d * 2.0 - 1.0, d, 0.0, 0.0, panelComponent), new Object[0]);
        this.createNameInput.addKeyTypedListener(this::handleNameInputChanged);
        this.createNameInput.addRefreshListener(new ProfilesPageRefreshListener(this));
        this.updateCreateButtonState();
        this.createNameInput.requestFocus();
    }

    public static void updateCreateButtonState(ClickGuiProfilesPage page) {
        page.updateCreateButtonState();
    }

    private void handleDraftProfileIdResponse(ApiResponse apiResponse, Throwable throwable) {
        if (throwable != null) {
            return;
        }
        if (!apiResponse.isSuccessful()) {
            return;
        }
        this.draftProfile.setOnlineId((UUID)apiResponse.getData());
    }

    private void initializeProfileSidecar(Profile profile, PanelComponent panelComponent) {
        this.populateProfileSidecar(profile, panelComponent);
    }

    private void updateCreateButtonState() {
        if (this.createButton == null || this.createNameInput == null) {
            return;
        }
        boolean bl = this.createNameInput.hasNonBlankText();
        this.createButton.setInteractionDisabled(!bl);
        this.updateDraftCardName();
    }

    private void openProfile(Profile profile) {
        this.selectProfileCard(profile);
        ProfilesPageOverlayController profilesPageOverlayController = new ProfilesPageOverlayController(null, profile);
        profilesPageOverlayController.setDividerVisible(false);
        this.mainFrame.showOverlay(ClickGuiOverlaySpec.builder().title(profile.getName()).sidecarIcon("newsettings").placement(ClickGuiOverlayPlacement.DOCKED).width(192.0).backdropEnabled(false).sidecar(profilesPageOverlayController).initializeContent(panel -> this.initializeProfileSidecar(profile, panel)).build());
    }

    private void updateDraftCardName() {
        if (this.draftProfileCard == null || this.createNameInput == null) {
            return;
        }
        String string = this.createNameInput.getText();
        if (string == null || string.trim().isEmpty()) {
            this.draftProfileCard.setDisplayName("New Profile");
        } else {
            this.draftProfileCard.setDisplayName(string);
        }
    }

    private void clearCardSelection() {
        this.selectProfileCard(null);
    }


    @Override
    public void u() {
        this.cardSelectionAnimation.u(this.cardSelectionActive);
        if (this.mainPanel != null) {
            this.mainPanel.setDisabledOverlayColor(this.applyCardSelectionState(ClickGuiProfilesPage.J.m));
        }
        if (this.creatingProfile) {
            this.updateDraftCardName();
        }
        if (this.profileList != null) {
            Profile profile = Vape.INSTANCE.getProfilesManager().getActiveProfile();
            if (this.displayedActiveProfile != profile && !this.creatingProfile) {
                this.displayedActiveProfile = profile;
                if (this.profileHeader != null) {
                    this.profileHeader.updateProfile(profile);
                }
                if (this.snapshotList != null) {
                    this.snapshotList.setProfile(profile);
                }
            }
            for (GuiComponent guiComponent : this.profileList.f()) {
                ClickGuiProfileCardComponent clickGuiProfileCardComponent;
                if (!(guiComponent instanceof PaddedComponent) || (clickGuiProfileCardComponent = ((PaddedComponent)guiComponent).t(ClickGuiProfileCardComponent.class)) == null || clickGuiProfileCardComponent.getProfile() == null || clickGuiProfileCardComponent == this.draftProfileCard) continue;
                clickGuiProfileCardComponent.setActive(clickGuiProfileCardComponent.getProfile() == profile);
            }
        }
    }

    @Override
    public void Z$src$V$15w0jcm() {
        super.Z$src$V$15w0jcm();
        if (this.overlayCloseCallback != null) {
            this.mainFrame.removeContentOverlayCallback(this.overlayCloseCallback);
            this.mainFrame.addContentOverlayCallback(this.overlayCloseCallback);
        }
        EventBus.getInstance().unregisterListener(this);
        EventBus.getInstance().registerListener(this, new Predicate[0]);
        this.refreshProfileList();
    }

    private void renderMainContent() {
        this.getMainContent().removeMarkedChildren();
        Profile profile = Vape.INSTANCE.getProfilesManager().getActiveProfile();
        if (!this.creatingProfile) {
            this.displayedActiveProfile = profile;
        }
        this.mainPanel = new PanelComponent(this.getMainContainer().A(), this.getMainContent().L());
        this.mainPanel.setDisabledOverlayColor(ClickGuiProfilesPage.J.m);
        this.mainPanel.setCornerRadius(3.0f);
        this.mainPanel.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("wrap");
        double d = 8.0;
        double d2 = this.mainPanel.A() - d * 2.0;
        if (this.creatingProfile) {
            this.renderCreateProfile(profile, d, d2);
        } else {
            this.renderExistingProfile(profile, d, d2);
        }
        this.getMainContent().h(this.mainPanel, new Object[0]);
    }

    private static ApiResponse ignoreCreateProfileFailure(Throwable throwable) {
        return null;
    }

    public ClickGuiProfilesPage(ClickGuiMainFrame clickGuiMainFrame, double d, double d2, double d3) {
        super(d, d2, d3, 2.0, "Profiles");
        this.mainFrame = clickGuiMainFrame;
        this.getClass();
        this.cardSelectionAnimation = new DoubleAnimation(0.15, 0.0, 1.0);
        this.cardSelectionActive = false;
        this.creatingProfile = false;
        this.overlayCloseCallback = this::clearCardSelection;
        this.mainFrame.addContentOverlayCallback(this.overlayCloseCallback);
        EventBus.getInstance().registerListener(this, new Predicate[0]);
        this.renderSidebar();
        this.renderMainContent();
    }

    private void handleNameInputChanged(char c, int n) {
        this.updateCreateButtonState();
    }

    private void refreshProfileList() {
        if (this.profileList == null) {
            return;
        }
        this.profileList.removeMarkedChildren();
        this.profileList.h(new SpacerComponent(0.0, 1.0), new Object[0]);
        Profile profile = Vape.INSTANCE.getProfilesManager().getActiveProfile();
        if (this.creatingProfile && this.draftProfile != null) {
            this.draftProfileCard = new ClickGuiProfileCardComponent(this.draftProfile);
            this.draftProfileCard.o(this.profileList.A());
            this.draftProfileCard.Y(20.0);
            this.draftProfileCard.setActive(false);
            this.draftProfileCard.setBadgeVisible(false);
            this.draftProfileCard.setSelected(true);
            this.draftProfileCard.setDisplayName("New Profile");
            this.profileList.h(new PaddedComponent(0.0, 3.0, 0.0, 0.0, this.draftProfileCard), new Object[0]);
        } else {
            this.draftProfileCard = null;
        }
        for (Profile profile2 : Vape.INSTANCE.getProfilesManager().getProfiles()) {
            ClickGuiProfileCardComponent clickGuiProfileCardComponent = new ClickGuiProfileCardComponent(profile2);
            clickGuiProfileCardComponent.o(this.profileList.A());
            clickGuiProfileCardComponent.Y(20.0);
            clickGuiProfileCardComponent.setActive(!this.creatingProfile && profile2 == profile);
            clickGuiProfileCardComponent.setDimmed(this.creatingProfile);
            clickGuiProfileCardComponent.setSettingsAction(() -> this.handleProfileCardSettings(profile2));
            this.profileList.h(new PaddedComponent(0.0, 3.0, 0.0, 0.0, clickGuiProfileCardComponent), new Object[0]);
        }
        this.profileList.l$src$V$1mibm4x();
    }

    @Override
    public void K() {
        super.K();
        if (this.overlayCloseCallback != null) {
            this.mainFrame.removeContentOverlayCallback(this.overlayCloseCallback);
        }
        EventBus.getInstance().unregisterListener(this);
    }
}
