package gg.vape.ui.click.frame.impl.profile;

import com.google.gson.JsonObject;
import gg.vape.Vape;
import gg.vape.api.ApiResponse;
import gg.vape.api.ApiServices;
import gg.vape.api.PagedResult;
import gg.vape.api.PublicProfilePartialJsonPayloadBuilder;
import gg.vape.config.LegacyPublicProfile;
import gg.vape.config.Profile;
import gg.vape.config.ProfileSnapshot;
import gg.vape.config.PublicProfile;
import gg.vape.config.PublicProfileReview;
import gg.vape.config.PublicProfileReviewDisplayType;
import gg.vape.config.PublicProfileShareInfo;
import gg.vape.event.EventBus;
import gg.vape.event.EventHandler;
import gg.vape.event.EventListener;
import gg.vape.event.impl.PublicProfileReviewEvent;
import gg.vape.manager.client.PublicProfileManager;
import gg.vape.module.none.ClientSettings;
import gg.vape.ui.click.component.ConfirmationDialogComponent;
import gg.vape.ui.click.component.DualTextLabelRowComponent;
import gg.vape.ui.click.component.FilledSpacerComponent;
import gg.vape.ui.click.component.FlowLayoutComponent;
import gg.vape.ui.click.component.GuiComponent;
import gg.vape.ui.click.component.InsetFilledSpacerComponent;
import gg.vape.ui.click.component.PagedResultListComponent;
import gg.vape.ui.click.component.PanelComponent;
import gg.vape.ui.click.component.PopupMenuButtonComponent;
import gg.vape.ui.click.component.ProfileSelectionPopupComponent;
import gg.vape.ui.click.component.SimpleTextLabelComponent;
import gg.vape.ui.click.component.SkeletonPlaceholderComponent;
import gg.vape.ui.click.component.SpacerComponent;
import gg.vape.ui.click.component.TextInputComponentBase;
import gg.vape.ui.click.component.TwoLineTextDisplayComponent;
import gg.vape.ui.click.component.WrappingTextLabelComponent;
import gg.vape.ui.click.component.gui.TextLabel;
import gg.vape.ui.click.component.layout.PaddedComponent;
import gg.vape.ui.click.component.publicprofiles.PublicProfileIdBadgeComponent;
import gg.vape.ui.click.component.value.BooleanToggleComponent;
import gg.vape.ui.click.frame.impl.profile.CompactPublicProfileFilterTokenSelectorComponent;
import gg.vape.ui.click.frame.impl.profile.PublicProfileDateFormatUtil;
import gg.vape.ui.click.frame.impl.profile.PublicProfileFilterTokenComponent;
import gg.vape.ui.click.frame.impl.profile.PublicProfileOwnerBooleanToggleClickHandler;
import gg.vape.ui.click.frame.impl.profile.PublicProfileOwnerDetailsUnderlineIconComponent;
import gg.vape.ui.click.frame.impl.profile.PublicProfileOwnerFixedWidthNoSubmitInputComponent;
import gg.vape.ui.click.frame.impl.profile.PublicProfileOwnerShareCodeCopyClickHandler;
import gg.vape.ui.click.frame.impl.profile.PublicProfileReviewComponent;
import gg.vape.ui.click.frame.impl.profile.PublicProfileSnapshotPanelBase;
import gg.vape.ui.click.frame.impl.profile.PublicProfilesFrame;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Predicate;
import org.jetbrains.annotations.Nullable;

public class PublicProfileOwnerDetailsPanel
extends PublicProfileSnapshotPanelBase
implements EventListener {
    private final String[] tabs;
    @Nullable
    protected PublicProfileShareInfo shareInfo;
    @Nullable
    private UUID selectedSourceId;
    private final Set<Long> pendingViewedReviewIds = new HashSet<>();
    private PanelComponent tabContent;
    private CompactPublicProfileFilterTokenSelectorComponent tagSelector;
    private String selectedTab;

    public PublicProfileOwnerDetailsPanel(PublicProfilesFrame publicProfilesFrame, @Nullable PublicProfile publicProfile, @Nullable ProfileSnapshot profileSnapshot) {
        super(publicProfilesFrame, publicProfile, profileSnapshot, true);
        this.tabs = new String[]{"Settings", "Reviews", "Stats"};
        this.selectedTab = this.tabs[0];
        this.publicProfile = publicProfile;
        this.shareInfo = publicProfile != null ? publicProfile.getShareInfo() : null;
        this.selectedSourceId = this.shareInfo != null ? this.shareInfo.getDerivedFrom() : null;
        this.snapshot = profileSnapshot;
        this.gZ = false;
        this.setDetailsCallback(this::showDetailsTabs);
        this.e();
        EventBus.getInstance().registerListener(this, new Predicate[0]);
    }

    private void selectTab(String string, PanelComponent panelComponent) {
        if (string.equalsIgnoreCase("stats")) {
            this.showStats();
        } else if (string.equalsIgnoreCase("reviews")) {
            this.showReviews();
        } else if (string.equalsIgnoreCase("settings")) {
            this.showSettings();
        }
        this.selectedTab = string;
        for (GuiComponent guiComponent : panelComponent.f()) {
            TextLabel textLabel;
            if (!(guiComponent instanceof TextLabel)) continue;
            textLabel = (TextLabel)guiComponent;
            textLabel.setTextColor(textLabel.getText().equals(this.selectedTab) ? PublicProfileOwnerDetailsPanel.J.A : PublicProfileOwnerDetailsPanel.J.C);
            textLabel.setUseAlternateFont(false);
        }
    }

    private void handleShareCodeRegenerated(ApiResponse<PublicProfileShareInfo> apiResponse, Throwable throwable) {
        if (throwable != null) {
            PublicProfileManager.showWarning("Failed to regenerate share code.");
            Vape.logThrowable(throwable);
            return;
        }
        if (!apiResponse.isSuccessful()) {
            PublicProfileManager.showWarning("Failed to regenerate share code: " + apiResponse.getError());
            return;
        }
        assert apiResponse.getData() != null;
        this.publicProfile.setShareCode(((PublicProfileShareInfo)apiResponse.getData()).getUppercaseShareCode());
        this.shareInfo.setShareCode(((PublicProfileShareInfo)apiResponse.getData()).getUppercaseShareCode());
        this.e();
        PublicProfileManager.showInfo("Successfully updated share code!");
    }

    private void handleProfileUpdated(ApiResponse<PublicProfile> apiResponse, Throwable throwable) {
        if (throwable != null) {
            Vape.logThrowable(throwable);
            return;
        }
        if (!apiResponse.isSuccessful()) {
            PublicProfileManager.showWarning("Failed to update profile: " + apiResponse.getError());
            return;
        }
        PublicProfile replacement = apiResponse.getData();
        if (replacement == null) {
            PublicProfileManager.showWarning("The profile update returned no data.");
            return;
        }
        PublicProfile previous = this.publicProfile;
        this.publicProfile = replacement;
        this.shareInfo = replacement.getShareInfo();
        PublicProfileManager.showInfo("Successfully updated profile " + replacement.getName() + "!");
        Vape.INSTANCE.getPublicProfileManager().replaceProfile(previous, replacement);
        this.e();
    }

    private static ApiResponse<Boolean> ignoreMarkAllFailure(Throwable throwable) {
        return null;
    }

    private void clearTabContent() {
        this.tabContent.removeMarkedChildren();
        this.flushViewedReviews();
    }

    private static ApiResponse<Boolean> ignoreMarkViewedFailure(Throwable throwable) {
        return null;
    }

    private PublicProfileReviewComponent createReviewComponent(PublicProfileReview publicProfileReview) {
        double d = this.tabContent.A();
        this.getClass();
        return new PublicProfileReviewComponent(this.publicProfile, publicProfileReview, d - 5.0, PublicProfileReviewDisplayType.REPLY);
    }

    private CompletableFuture updateModulesOnly() {
        return this.updatePublicProfile(this.selectedSourceId, null, null, null, null, null, true);
    }

    private CompletableFuture confirmDeleteProfile() {
        return ConfirmationDialogComponent.showStandard(this.L$src$Lgg_vape_ui_click_frame_Frame_$1djx6sa(), "Are you sure you want to delete this public profile?", "Delete", "newtrash", this::deleteProfile);
    }

    private CompletableFuture confirmRegenerateShareCode() {
        return ConfirmationDialogComponent.showStandard(this.L$src$Lgg_vape_ui_click_frame_Frame_$1djx6sa(), "Are you sure you want to regenerate this share code?", "Regenerate", "newsync", this::regenerateShareCode);
    }

    private void changeSourceProfile(Profile profile) {
        if (profile.getOnlineId() == null) {
            PublicProfileManager.showWarning("Save this profile before using it as a source.");
            return;
        }
        // Keep the source selection staged until the public update succeeds;
        // changing the UI must not mutate the shared PublicProfile object.
        this.selectedSourceId = profile.getOnlineId();
        this.snapshot = ProfileSnapshot.createEditableCopy(this.publicProfile, profile);
        this.e();
    }

    private void queueUnreadReview(PublicProfileReview publicProfileReview) {
        if (publicProfileReview.isRead()) {
            return;
        }
        this.pendingViewedReviewIds.add(publicProfileReview.getCommentId());
    }

    private void deleteProfile() {
        ApiServices.getInstance().getPublicProfileApi().deleteProfile(this.publicProfile.getProfileId()).whenCompleteAsync(this::handleProfileDeleted, ClientSettings.UI_EXECUTOR).exceptionally(PublicProfileOwnerDetailsPanel::ignoreDeleteFailure);
    }

    private CompletableFuture<ApiResponse<PublicProfile>> updatePublicProfile(@Nullable UUID sourceProfileId, @Nullable String description, @Nullable List<String> tags, @Nullable Boolean shareCodeOnly, @Nullable Boolean anonymous, @Nullable Boolean friendsOnly, boolean includeProfileData) {
        assert this.publicProfile != null;
        if (tags != null && tags.size() < 5) {
            String normalizedTag = LegacyPublicProfile.normalizeTag(this.tagSelector.getInput().getText().trim());
            if (normalizedTag != null) {
                String string3 = LegacyPublicProfile.validateTag(normalizedTag);
                if (string3 != null) {
                    PublicProfileManager.showWarning(string3);
                    return null;
                }
                tags.add(normalizedTag);
                this.tagSelector.addToken(new PublicProfileFilterTokenComponent(normalizedTag));
                this.tagSelector.getInput().setText("");
            }
        }
        JsonObject profileData = null;
        if (includeProfileData && this.snapshot != null && this.snapshot.getProfile() != null) {
            profileData = this.snapshot.toPublicationData();
        }
        // A public edit is a detached draft. Do not also overwrite the source
        // private profile (or rename it to the public profile's title).
        return ApiServices.getInstance().getPublicProfileApi().editProfile(PublicProfilePartialJsonPayloadBuilder.build(this.publicProfile.getProfileId(), sourceProfileId, this.publicProfile.getName(), description, tags, shareCodeOnly != null ? Boolean.valueOf(shareCodeOnly == false) : null, anonymous, friendsOnly, profileData))
                .whenCompleteAsync(this::handleProfileUpdated, ClientSettings.UI_EXECUTOR);
    }

    @EventHandler
    public void e(PublicProfileReviewEvent publicProfileReviewEvent) {
        this.showReviews();
    }


    private void handleReviewsMarkedViewed(List<Long> reviewIds, ApiResponse<Boolean> apiResponse, Throwable throwable) {
        if (throwable != null) {
            Vape.logThrowable(throwable);
            return;
        }
        if (!apiResponse.isSuccessful()) {
            PublicProfileManager.showWarning("Failed to mark reviews as read: " + apiResponse.getError());
            return;
        }
        for (PublicProfileReview publicProfileReview : this.publicProfile.getReviews().getContent()) {
            if (!reviewIds.contains(publicProfileReview.getCommentId())) continue;
            publicProfileReview.setRead(true);
            this.updateUnreadReviewCount(this.shareInfo.getUnreadNotifications() - 1L);
        }
    }

    private List<GuiComponent> mapReviewsPage(PagedResultListComponent pagedResultListComponent, Function<PublicProfileReview, PublicProfileReviewComponent> reviewFactory, ApiResponse<PagedResult<PublicProfileReview>> apiResponse) {
        if (!apiResponse.isSuccessful()) {
            return null;
        }
        assert apiResponse.getData() != null;
        pagedResultListComponent.setPageMetadata(apiResponse.getData());
        ArrayList<GuiComponent> arrayList = new ArrayList<GuiComponent>();
        for (PublicProfileReview publicProfileReview : apiResponse.getData().getContent()) {
            arrayList.add(reviewFactory.apply(publicProfileReview).setDisplayedCallback(() -> this.queueUnreadReview(publicProfileReview)).setLayoutChangedCallback(PublicProfileOwnerDetailsPanel::noOp));
        }
        return arrayList;
    }

    private void updateUnreadReviewCount(long unreadCount) {
        assert this.shareInfo != null;
        this.shareInfo.setUnreadNotifications(unreadCount);
        PublicProfile publicProfile = Vape.INSTANCE.getPublicProfileManager().getProfilesById().get(this.publicProfile.getProfileId());
        if (publicProfile != null) {
            assert publicProfile.getShareInfo() != null;
            publicProfile.getShareInfo().setUnreadNotifications(unreadCount);
        }
    }

    private void showSettings() {
        this.clearTabContent();
        double d = this.gg.A();
        PanelComponent panelComponent = new PanelComponent(d, 14.0);
        panelComponent.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("widthwrap");
        panelComponent.setShowDisabledOverlay(false);
        PanelComponent panelComponent2 = new PanelComponent(d, this.tabContent.L() - panelComponent.L() - 8.0);
        panelComponent2.t(panelComponent2.L() - 2.0);
        panelComponent2.setShowDisabledOverlay(false);
        panelComponent2.setDisabledOverlayColor(Color.RED);
        double d2 = d - 3.0;
        FlowLayoutComponent flowLayoutComponent = new FlowLayoutComponent(d);
        flowLayoutComponent.setShowDisabledOverlay(false);
        flowLayoutComponent.setDisabledOverlayColor(Color.YELLOW);
        flowLayoutComponent.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("wrap");
        panelComponent2.h(flowLayoutComponent, new Object[0]);
        this.tabContent.h(panelComponent2, new Object[0]);
        this.tabContent.h(new FilledSpacerComponent(d2, 0.5, PublicProfileOwnerDetailsPanel.J.l), new Object[0]);
        this.tabContent.h(new SpacerComponent(0.0, 8.0), new Object[0]);
        this.tabContent.h(panelComponent, new Object[0]);
        SimpleTextLabelComponent simpleTextLabelComponent = new SimpleTextLabelComponent("DESCRIPTION", 0.7, PublicProfileOwnerDetailsPanel.J.C, true);
        simpleTextLabelComponent.setOffsetX(0.0f);
        flowLayoutComponent.h(simpleTextLabelComponent, new Object[0]);
        PublicProfileOwnerFixedWidthNoSubmitInputComponent publicProfileOwnerFixedWidthNoSubmitInputComponent = new PublicProfileOwnerFixedWidthNoSubmitInputComponent("+   Add Description (optional)", d2);
        publicProfileOwnerFixedWidthNoSubmitInputComponent.setHorizontalInset(0.0);
        publicProfileOwnerFixedWidthNoSubmitInputComponent.setLeftInset(0.0f);
        publicProfileOwnerFixedWidthNoSubmitInputComponent.setRightInset(1.0f);
        publicProfileOwnerFixedWidthNoSubmitInputComponent.setText(this.publicProfile.getDescription());
        publicProfileOwnerFixedWidthNoSubmitInputComponent.setTextColor(PublicProfileOwnerDetailsPanel.J.A);
        publicProfileOwnerFixedWidthNoSubmitInputComponent.setPlaceholderColor(PublicProfileOwnerDetailsPanel.J.Z);
        publicProfileOwnerFixedWidthNoSubmitInputComponent.getActionButton().setVisible(false);
        publicProfileOwnerFixedWidthNoSubmitInputComponent.setShowDisabledOverlay(false);
        publicProfileOwnerFixedWidthNoSubmitInputComponent.setBackgroundVisible(false);
        flowLayoutComponent.h(publicProfileOwnerFixedWidthNoSubmitInputComponent, new Object[0]);
        flowLayoutComponent.h(new InsetFilledSpacerComponent(d2, 2.0, 0.5, 0.0, PublicProfileOwnerDetailsPanel.J.l), new Object[0]);
        flowLayoutComponent.h(new SpacerComponent(0.0, 5.0), new Object[0]);
        SimpleTextLabelComponent simpleTextLabelComponent2 = new SimpleTextLabelComponent("TAGS", 0.7, PublicProfileOwnerDetailsPanel.J.C, true);
        simpleTextLabelComponent2.setOffsetX(0.0f);
        flowLayoutComponent.h(simpleTextLabelComponent2, "widthwrap");
        SimpleTextLabelComponent simpleTextLabelComponent3 = new SimpleTextLabelComponent("Comma-Seperated", 0.7, PublicProfileOwnerDetailsPanel.J.h, false);
        double d3 = simpleTextLabelComponent3.getTextWidth();
        this.getClass();
        simpleTextLabelComponent3.o(d3 + (double)(5.0f * 2.0f));
        flowLayoutComponent.h(simpleTextLabelComponent3, "alignright, wrap");
        this.tagSelector = new CompactPublicProfileFilterTokenSelectorComponent("+   Add Tags (optional)", this.tabContent.A(), 20.0);
        for (String object2 : this.publicProfile.getTags()) {
            this.tagSelector.addToken(new PublicProfileFilterTokenComponent(object2));
        }
        flowLayoutComponent.h(this.tagSelector, "wrap");
        flowLayoutComponent.h(new InsetFilledSpacerComponent(d2, 2.0, 0.5, 0.0, PublicProfileOwnerDetailsPanel.J.l), new Object[0]);
        flowLayoutComponent.h(new SpacerComponent(0.0, 5.0), new Object[0]);
        SimpleTextLabelComponent simpleTextLabelComponent4 = new SimpleTextLabelComponent("PREFERENCES", 0.7, PublicProfileOwnerDetailsPanel.J.C, true);
        simpleTextLabelComponent4.setOffsetX(0.0f);
        flowLayoutComponent.h(simpleTextLabelComponent4, new Object[0]);
        BooleanToggleComponent booleanToggleComponent = new BooleanToggleComponent("Discoverable with a share code only", 0.8);
        booleanToggleComponent.setHorizontalInset(0.0);
        booleanToggleComponent.setExplicitWidth(d2);
        booleanToggleComponent.setShowDisabledOverlay(false);
        booleanToggleComponent.setValue(!this.shareInfo.isListedPublicly());
        flowLayoutComponent.h(booleanToggleComponent, new Object[0]);
        BooleanToggleComponent booleanToggleComponent2 = new BooleanToggleComponent("Friends only discovery", 0.8);
        booleanToggleComponent2.setHorizontalInset(0.0);
        booleanToggleComponent2.setExplicitWidth(d2);
        booleanToggleComponent2.setShowDisabledOverlay(false);
        booleanToggleComponent2.setValue(this.shareInfo.isShareCodeFriendsOnly());
        booleanToggleComponent2.setVisible(!this.shareInfo.isListedPublicly());
        flowLayoutComponent.h(booleanToggleComponent2, new Object[0]);
        booleanToggleComponent.addMouseListener(new PublicProfileOwnerBooleanToggleClickHandler(booleanToggleComponent2));
        PanelComponent panelComponent3 = new PanelComponent(d2, 16.0);
        panelComponent3.setShowDisabledOverlay(true);
        panelComponent3.setDisabledOverlayColor(PublicProfileOwnerDetailsPanel.J.R);
        WrappingTextLabelComponent wrappingTextLabelComponent = new WrappingTextLabelComponent("Share Code: " + this.shareInfo.getUppercaseShareCode(), 0.8, PublicProfileOwnerDetailsPanel.J.B);
        wrappingTextLabelComponent.o(panelComponent3.A());
        wrappingTextLabelComponent.Y(panelComponent3.L());
        wrappingTextLabelComponent.w("Click to copy to clipboard");
        wrappingTextLabelComponent.setBold(true);
        wrappingTextLabelComponent.addMouseListener(new PublicProfileOwnerShareCodeCopyClickHandler(this));
        panelComponent3.h(wrappingTextLabelComponent, new Object[0]);
        PublicProfileOwnerDetailsUnderlineIconComponent publicProfileOwnerDetailsUnderlineIconComponent = new PublicProfileOwnerDetailsUnderlineIconComponent("newsync", 6.0, 6.0, 8.0, 8.0, null, null, null);
        publicProfileOwnerDetailsUnderlineIconComponent.setPropagateMouseEvents(false);
        publicProfileOwnerDetailsUnderlineIconComponent.w("Click to regenerate share code");
        publicProfileOwnerDetailsUnderlineIconComponent.setSingleFutureClickListener(this::confirmRegenerateShareCode);
        panelComponent3.h(publicProfileOwnerDetailsUnderlineIconComponent, "OffsetX 192, OffsetY 5");
        flowLayoutComponent.h(panelComponent3, new Object[0]);
        BooleanToggleComponent booleanToggleComponent3 = new BooleanToggleComponent("Upload anonymously", 0.8);
        booleanToggleComponent3.setHorizontalInset(0.0);
        booleanToggleComponent3.setExplicitWidth(d2);
        booleanToggleComponent3.setShowDisabledOverlay(false);
        booleanToggleComponent3.setValue(this.shareInfo.isUploadAnonymously());
        flowLayoutComponent.h(booleanToggleComponent3, new Object[0]);
        TextLabel textLabel = new TextLabel("Remove", 0.7, true);
        textLabel.setTextColor(PublicProfileOwnerDetailsPanel.J.d);
        textLabel.setUseAlternateFont(true);
        textLabel.o(30.0);
        textLabel.Y(panelComponent.L());
        textLabel.setSingleFutureClickListener(this::confirmDeleteProfile);
        panelComponent.h(textLabel, new Object[0]);
        PanelComponent panelComponent4 = new PanelComponent(75.0, panelComponent.L());
        panelComponent4.setShowDisabledOverlay(false);
        panelComponent4.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("widthwrap");
        panelComponent.h(panelComponent4, "alignright");
        TextLabel textLabel2 = new TextLabel("Cancel", 0.7, true);
        textLabel2.setTextColor(PublicProfileOwnerDetailsPanel.J.Z);
        textLabel2.setUseAlternateFont(true);
        textLabel2.o(30.0);
        textLabel2.Y(panelComponent.L());
        textLabel2.addClickListener(this::closePanel);
        panelComponent4.h(textLabel2, new Object[0]);
        List<GuiComponent> list = Arrays.asList(new TextLabel("Modules only", 0.75, false).setCentered(true).setTextColor(Color.WHITE).setUseAlternateFont(true).setSingleFutureClickListener(this::updateModulesOnly), new TextLabel("Details only", 0.75, false).setCentered(true).setTextColor(Color.WHITE).setUseAlternateFont(true).setSingleFutureClickListener(() -> this.updateDetailsOnly(publicProfileOwnerFixedWidthNoSubmitInputComponent, booleanToggleComponent, booleanToggleComponent3, booleanToggleComponent2)));
        PopupMenuButtonComponent popupMenuButtonComponent = new PopupMenuButtonComponent("UPDATE", list, this.snapshot.getProfile() != null ? PublicProfileOwnerDetailsPanel.J.B : PublicProfileOwnerDetailsPanel.J.l, this.snapshot.getProfile() != null ? PublicProfileOwnerDetailsPanel.J.O : PublicProfileOwnerDetailsPanel.J.l, null, 1.0f, 1.0f);
        popupMenuButtonComponent.setInteractionBlocked(this.snapshot.getProfile() == null);
        popupMenuButtonComponent.setOpenUpward(true);
        popupMenuButtonComponent.o(60.0);
        popupMenuButtonComponent.Y(panelComponent.L());
        popupMenuButtonComponent.setSingleFutureClickListener(() -> this.updateProfileAndDetails(publicProfileOwnerFixedWidthNoSubmitInputComponent, booleanToggleComponent, booleanToggleComponent3, booleanToggleComponent2));
        panelComponent4.h(popupMenuButtonComponent, new Object[0]);
        double d4 = textLabel2.A() + popupMenuButtonComponent.A();
        this.getClass();
        panelComponent4.setExplicitWidth(d4 + 5.0);
    }

    private void showStats() {
        this.clearTabContent();
        String[] stringArray = new String[]{"Positive reviews", "Negative reviews", "Downloads", "Created", "Updated", "Reviews"};
        String[] stringArray2 = new String[]{String.valueOf(this.publicProfile.getLikes()), String.valueOf(this.publicProfile.getDislikes()), String.valueOf(this.publicProfile.getDownloads()), PublicProfileDateFormatUtil.i(this.publicProfile.getCreationDate()), PublicProfileDateFormatUtil.i(this.publicProfile.getLatestDate()), String.valueOf(this.publicProfile.getReviews().getTotalElements())};
        this.tabContent.h(new SpacerComponent(0.0, 5.0), "wrap");
        for (int i = 0; i < stringArray.length; ++i) {
            String string = stringArray[i];
            String string2 = stringArray2[i];
            if (i % 4 == 3) {
                this.tabContent.h(new SpacerComponent(0.0, 1.0), "wrap");
            }
            TwoLineTextDisplayComponent twoLineTextDisplayComponent = new TwoLineTextDisplayComponent(string, string2);
            twoLineTextDisplayComponent.setSecondaryFontScale(1.0);
            twoLineTextDisplayComponent.setPrimaryFontScale(string2.length() >= 5 ? 0.95 : 1.1);
            twoLineTextDisplayComponent.o(this.tabContent.A() / 3.0 - 4.0);
            twoLineTextDisplayComponent.Y(twoLineTextDisplayComponent.A());
            this.tabContent.h(new PaddedComponent(2.0, twoLineTextDisplayComponent), "widthwrap");
        }
    }

    @Override
    public void d$src$V$15t6q4y() {
        super.d$src$V$15t6q4y();
        this.flushViewedReviews();
    }

    private void handleProfileDeleted(ApiResponse<Boolean> apiResponse, Throwable throwable) {
        if (throwable != null) {
            Vape.logThrowable(throwable);
            return;
        }
        if (!apiResponse.isSuccessful()) {
            PublicProfileManager.showWarning("Failed to delete profile: " + apiResponse.getError());
            return;
        }
        PublicProfileManager.showInfo("Successfully deleted profile " + this.publicProfile.getName() + "!");
        Vape.INSTANCE.getPublicProfileManager().removeProfile(this.publicProfile);
        this.profilesFrame.O(null);
    }

    private static void noOp() {
    }

    private CompletableFuture updateDetailsOnly(TextInputComponentBase descriptionInput, BooleanToggleComponent shareCodeOnlyToggle, BooleanToggleComponent anonymousToggle, BooleanToggleComponent friendsOnlyToggle) {
        return this.updatePublicProfile(null, descriptionInput.getText(), this.tagSelector.getTokenValues(), shareCodeOnlyToggle.isOn(), anonymousToggle.isOn(), friendsOnlyToggle.isOn(), false);
    }

    private void showReviews() {
        this.clearTabContent();
        PanelComponent panelComponent = new PanelComponent(this.tabContent.A(), 15.0);
        panelComponent.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("widthwrap");
        panelComponent.t(panelComponent.L());
        panelComponent.setShowDisabledOverlay(false);
        this.tabContent.h(panelComponent, new Object[0]);
        panelComponent.h(new DualTextLabelRowComponent("Reviews", String.valueOf(this.publicProfile.getReviewCount()), 12.0, 0.9), new Object[0]);
        TextLabel textLabel = new TextLabel("mark all as read", 0.8, false, 50.0, 10.0);
        textLabel.setTextColor(null);
        textLabel.setSingleFutureClickListener(this::markAllReviewsRead);
        panelComponent.h(textLabel, "alignright");
        double d = this.tabContent.A();
        double d2 = this.tabContent.L() - this.tabContent.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().y();
        this.getClass();
        PagedResultListComponent pagedResultListComponent = new PagedResultListComponent(d, d2 - 5.0);
        pagedResultListComponent.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("wrap");
        pagedResultListComponent.t(pagedResultListComponent.L());
        pagedResultListComponent.setShowDisabledOverlay(false);
        Function<PublicProfileReview, PublicProfileReviewComponent> function = this::createReviewComponent;
        pagedResultListComponent.setPlaceholderSupplier(() -> PublicProfileOwnerDetailsPanel.createEmptyReviewComponent(function));
        pagedResultListComponent.setPageLoader(() -> this.loadReviewsPage(pagedResultListComponent, function));
        pagedResultListComponent.reload();
        this.tabContent.h(pagedResultListComponent, new Object[0]);
    }

    private static ApiResponse<Boolean> ignoreDeleteFailure(Throwable throwable) {
        return null;
    }

    private static ApiResponse<PublicProfileShareInfo> ignoreRegenerateFailure(Throwable throwable) {
        return null;
    }

    private void handleAllReviewsMarkedRead(ApiResponse<Boolean> apiResponse, Throwable throwable) {
        if (throwable != null) {
            Vape.logThrowable(throwable);
            return;
        }
        if (!apiResponse.isSuccessful()) {
            PublicProfileManager.showWarning("Failed to mark all as read: " + apiResponse.getError());
            return;
        }
        PublicProfileManager.showInfo("Successfully marked all reviews as read!");
        for (PublicProfileReview publicProfileReview : this.publicProfile.getReviews().getContent()) {
            if (publicProfileReview.getRead() == null) continue;
            publicProfileReview.setRead(true);
        }
        this.updateUnreadReviewCount(0L);
        this.showReviews();
    }

    private void regenerateShareCode() {
        ApiServices.getInstance().getPublicProfileApi().regenerateShareCode(this.publicProfile.getProfileId()).whenCompleteAsync(this::handleShareCodeRegenerated, ClientSettings.UI_EXECUTOR).exceptionally(PublicProfileOwnerDetailsPanel::ignoreRegenerateFailure);
    }

    private CompletableFuture markAllReviewsRead() {
        return ApiServices.getInstance().getPublicProfileApi().markAllReviewsRead(this.publicProfile).whenCompleteAsync(this::handleAllReviewsMarkedRead, ClientSettings.UI_EXECUTOR).exceptionally(PublicProfileOwnerDetailsPanel::ignoreMarkAllFailure);
    }

    private void closePanel() {
        this.profilesFrame.O(null);
    }

    private CompletableFuture<List<GuiComponent>> loadReviewsPage(PagedResultListComponent pagedResultListComponent, Function<PublicProfileReview, PublicProfileReviewComponent> reviewFactory) {
        return ApiServices.getInstance().getPublicProfileApi().getReviewPage(this.publicProfile.getProfileId(), pagedResultListComponent.getNextPageIndex()).thenApplyAsync(response -> this.mapReviewsPage(pagedResultListComponent, reviewFactory, response), (Executor)ClientSettings.UI_EXECUTOR);
    }

    @Override
    protected void e() {
        super.e();
        if (this.publicProfile == null || this.snapshot == null) {
            this.showLoadingState();
        }
    }

    @Override
    protected void customizeHeader(PanelComponent panelComponent) {
        if (this.publicProfile == null || this.shareInfo == null) {
            return;
        }
        Profile profile = this.selectedSourceId != null ? Vape.INSTANCE.getProfilesManager().getProfileByOnlineId(this.selectedSourceId) : null;
        List<Profile> list = Vape.INSTANCE.getPublicProfileManager().getDerivedProfiles();
        ArrayList<Profile> arrayList = new ArrayList<Profile>(Vape.INSTANCE.getProfilesManager().getProfiles());
        arrayList.removeIf(list::contains);
        ProfileSelectionPopupComponent profileSelectionPopupComponent = new ProfileSelectionPopupComponent("Derived From", profile, arrayList.toArray(new Profile[0]));
        profileSelectionPopupComponent.Y(6.0);
        profileSelectionPopupComponent.o(panelComponent.A());
        profileSelectionPopupComponent.setShowDisabledOverlay(false);
        profileSelectionPopupComponent.setSelectionCallback(this::changeSourceProfile);
        panelComponent.h(profileSelectionPopupComponent, "widthwrap");
    }

    private CompletableFuture updateProfileAndDetails(TextInputComponentBase descriptionInput, BooleanToggleComponent shareCodeOnlyToggle, BooleanToggleComponent anonymousToggle, BooleanToggleComponent friendsOnlyToggle) {
        return this.updatePublicProfile(this.selectedSourceId, descriptionInput.getText(), this.tagSelector.getTokenValues(), shareCodeOnlyToggle.isOn(), anonymousToggle.isOn(), friendsOnlyToggle.isOn(), true);
    }

    private void flushViewedReviews() {
        assert this.shareInfo != null;
        ArrayList<Long> arrayList = new ArrayList<Long>(this.pendingViewedReviewIds);
        this.pendingViewedReviewIds.clear();
        if (!arrayList.isEmpty()) {
            ApiServices.getInstance().getPublicProfileApi().markReviewsRead(this.publicProfile, arrayList).whenCompleteAsync((response, error) -> this.handleReviewsMarkedViewed(arrayList, response, error), ClientSettings.UI_EXECUTOR).exceptionally(PublicProfileOwnerDetailsPanel::ignoreMarkViewedFailure);
        }
    }

    private static GuiComponent createEmptyReviewComponent(Function<PublicProfileReview, PublicProfileReviewComponent> function) {
        return function.apply(null);
    }

    private void showDetailsTabs() {
        this.b$src$V$s019hq();
        double d = this.gg.A();
        PanelComponent panelComponent = new PanelComponent(d, 15.0);
        panelComponent.setShowDisabledOverlay(false);
        this.gg.h(panelComponent, new Object[0]);
        this.tabContent = new PanelComponent(d, this.gg.L() - panelComponent.L() - 6.0);
        this.tabContent.setShowDisabledOverlay(false);
        this.tabContent.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("wrap");
        this.clearTabContent();
        this.gg.h(this.tabContent, new Object[0]);
        panelComponent.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("widthwrap");
        panelComponent.setShowDisabledOverlay(false);
        for (String string : this.tabs) {
            TextLabel textLabel = new TextLabel(string, 0.75);
            textLabel.setUseAlternateFont(false);
            textLabel.setTextColor(string.equals(this.selectedTab) ? PublicProfileOwnerDetailsPanel.J.A : PublicProfileOwnerDetailsPanel.J.C);
            textLabel.setUppercase(false);
            textLabel.Y(12.0);
            panelComponent.h(textLabel, new Object[0]);
            textLabel.addClickListener(() -> this.selectTab(string, panelComponent));
            if (string.equalsIgnoreCase("reviews") && this.shareInfo.getUnreadNotifications() > 0L) {
                PublicProfileIdBadgeComponent publicProfileIdBadgeComponent = new PublicProfileIdBadgeComponent(this.shareInfo.getUnreadNotifications());
                panelComponent.h(publicProfileIdBadgeComponent, "offsetY 3");
                textLabel.o(textLabel.getTextWidth());
            } else {
                textLabel.o(textLabel.getTextWidth());
            }
            panelComponent.h(new SpacerComponent(8.0, this.L()), new Object[0]);
            if (!string.equalsIgnoreCase("reviews") || this.shareInfo.getUnreadNotifications() <= 0L) continue;
            this.getClass();
            panelComponent.h(new SpacerComponent(5.0, this.L()), new Object[0]);
        }
        this.showSettings();
    }

    protected void showLoadingState() {
        this.showLoadingPlaceholders();
        this.gg.setVisible(false);
        this.gb.setVisible(false);
        double d = this.getRightPanel().A();
        this.getClass();
        PanelComponent panelComponent = new PanelComponent(d - (double)(5.0f * 2.0f), this.getRightPanel().L());
        panelComponent.setShowDisabledOverlay(false);
        panelComponent.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("wrap");
        this.getRightPanel().h(panelComponent, new Object[0]);
        panelComponent.h(new SkeletonPlaceholderComponent(80.0, 10.0), new Object[0]);
        panelComponent.h(new SpacerComponent(0.0, 2.0), new Object[0]);
        panelComponent.h(new SkeletonPlaceholderComponent(40.0, 10.0), new Object[0]);
        panelComponent.h(new SpacerComponent(0.0, 2.0), new Object[0]);
        panelComponent.h(new SkeletonPlaceholderComponent(panelComponent.A(), 15.0), new Object[0]);
        panelComponent.h(new SpacerComponent(0.0, 5.0), new Object[0]);
        panelComponent.h(new SkeletonPlaceholderComponent(20.0, 10.0), new Object[0]);
        panelComponent.h(new SpacerComponent(0.0, 2.0), new Object[0]);
        panelComponent.h(new SkeletonPlaceholderComponent(panelComponent.A(), 15.0), new Object[0]);
        panelComponent.h(new SpacerComponent(0.0, 8.0), new Object[0]);
        panelComponent.h(new SkeletonPlaceholderComponent(50.0, 10.0), new Object[0]);
        panelComponent.h(new SpacerComponent(0.0, 5.0), new Object[0]);
        panelComponent.h(new SkeletonPlaceholderComponent(panelComponent.A(), 12.0), new Object[0]);
        panelComponent.h(new SpacerComponent(0.0, 5.0), new Object[0]);
        panelComponent.h(new SkeletonPlaceholderComponent(panelComponent.A(), 15.0), new Object[0]);
        panelComponent.h(new SpacerComponent(0.0, 5.0), new Object[0]);
        panelComponent.h(new SkeletonPlaceholderComponent(panelComponent.A(), 12.0), new Object[0]);
        panelComponent.h(new SpacerComponent(0.0, 5.0), new Object[0]);
        panelComponent.h(new SkeletonPlaceholderComponent(25.0, 12.0), "widthwrap");
        panelComponent.h(new SpacerComponent(panelComponent.A() - 25.0 - 100.0, 5.0), "widthwrap");
        panelComponent.h(new SkeletonPlaceholderComponent(25.0, 12.0), "widthwrap");
        panelComponent.h(new SpacerComponent(5.0, 5.0), "widthwrap");
        panelComponent.h(new SkeletonPlaceholderComponent(60.0, 12.0), "wrap");
    }

    String getShareCode() {
        return this.shareInfo.getUppercaseShareCode();
    }
}
