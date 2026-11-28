package gg.vape.ui.click.frame.impl.profile;

import com.google.gson.JsonArray;
import gg.vape.Vape;
import gg.vape.api.ApiHttpClient;
import gg.vape.api.ApiResponse;
import gg.vape.api.ApiServices;
import gg.vape.api.PagedResult;
import gg.vape.api.UserDataResponse;
import gg.vape.config.Profile;
import gg.vape.config.ProfileSnapshot;
import gg.vape.config.PublicProfile;
import gg.vape.config.PublicProfileSortMode;
import gg.vape.config.PublicProfileSummary;
import gg.vape.event.EventBus;
import gg.vape.event.EventHandler;
import gg.vape.event.EventListener;
import gg.vape.event.impl.PublicProfileCreatedEvent;
import gg.vape.event.impl.PublicProfileDeletedEvent;
import gg.vape.friend.ui.OnlineConnectionStatusPanelBody;
import gg.vape.manager.client.OnlineConnectionManager;
import gg.vape.manager.client.OnlineConnectionState;
import gg.vape.manager.client.PublicProfileManager;
import gg.vape.module.none.ClientSettings;
import gg.vape.ui.click.component.FilledSpacerComponent;
import gg.vape.ui.click.component.GlyphIconComponent;
import gg.vape.ui.click.component.GuiComponent;
import gg.vape.ui.click.component.InsetFilledSpacerComponent;
import gg.vape.ui.click.component.PanelComponent;
import gg.vape.ui.click.component.SimpleTextLabelComponent;
import gg.vape.ui.click.component.SpacerComponent;
import gg.vape.ui.click.component.SquareIconButtonComponent;
import gg.vape.ui.click.component.gui.InteractiveComponent;
import gg.vape.ui.click.component.gui.TextButton;
import gg.vape.ui.click.component.layout.PaddedComponent;
import gg.vape.ui.click.component.publicprofiles.PublicProfileListEntryComponent;
import gg.vape.ui.click.frame.Frame;
import gg.vape.ui.click.frame.FrameComponent;
import gg.vape.ui.click.frame.FrameScrollbarPlacement;
import gg.vape.ui.click.frame.PopupFrame;
import gg.vape.ui.click.frame.impl.hud.HudSettingsFrameBase;
import gg.vape.ui.click.frame.impl.main.ClickGuiFrameManager;
import gg.vape.ui.click.layout.WrappingFlowLayout;
import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PublicProfilesFrame
extends Frame
implements EventListener {
    private String lastSearchQuery = "";
    private boolean layoutRebuildRequired;
    private PaddedComponent profileListWrapper;
    private PublicProfileResultsListComponent resultsList;
    private boolean showingOwnedProfiles = true;
    private PanelComponent resultsPanel;
    static final boolean ASSERTIONS_DISABLED = !PublicProfilesFrame.class.desiredAssertionStatus();
    private PublicProfileSearchFilterPanel searchFilterPanel;
    private PublicProfileSortMode sortMode = PublicProfileSortMode.RATED;
    private boolean connectionRefreshPending;
    private boolean ownedRefreshPending;
    private boolean showingConnectionStatus;
    private PanelComponent profileListPanel;
    @Nullable
    private PopupFrame activePopup;
    /** Compatibility aliases for existing frame callbacks. */

    private static ApiResponse lambda$openWithEditor$23(Throwable throwable) {
        return null;
    }

    private static void lambda$openWithEditor$21(PublicProfilesFrame publicProfilesFrame) {
        publicProfilesFrame.l((PublicProfile)null);
    }

    public PublicProfileResultsListComponent P$src$Lgg_vape_ui_click_frame_impl_profile_PublicProfi$1ezbs2g() {
        return this.resultsList;
    }

    private static void lambda$createCenteredOverlayNode$11(Runnable runnable) {
        runnable.run();
    }

    private void t(PanelComponent panelComponent) {
        GuiComponent guiComponent;
        PanelComponent panelComponent2 = new PanelComponent(this.showingOwnedProfiles ? 92.0 : 8.0, panelComponent.L());
        this.profileListWrapper = new PaddedComponent(4.0, 4.0, 6.0, 6.0, panelComponent2);
        panelComponent.h(this.profileListWrapper, new Object[0]);
        panelComponent2.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("wrap");
        PanelComponent panelComponent3 = new PanelComponent(panelComponent2.A(), 12.0);
        if (this.showingOwnedProfiles) {
            guiComponent = new SimpleTextLabelComponent("YOUR PUBLIC PROFILES");
            ((SimpleTextLabelComponent)guiComponent).setBold(true);
            ((SimpleTextLabelComponent)guiComponent).setTextColor(PublicProfilesFrame.J.h);
            ((SimpleTextLabelComponent)guiComponent).setFontScale(0.7);
            ((SimpleTextLabelComponent)guiComponent).setOffsetX(0.0f);
            ((SimpleTextLabelComponent)guiComponent).setOffsetY(-2.0f);
            panelComponent3.N(false);
            panelComponent3.h(guiComponent, new Object[0]);
        }
        guiComponent = new GlyphIconComponent(this.showingOwnedProfiles ? "hide hover@2x" : "show hover@2x", 5.0, 4.0, 5.0, 4.0, null, null, null);
        panelComponent3.h(guiComponent, "alignright");
        ((InteractiveComponent)guiComponent).addClickListener(this::lambda$createLeftContainer$1);
        panelComponent2.h(panelComponent3, new Object[0]);
        if (this.showingOwnedProfiles) {
            PanelComponent panelComponent4 = new PanelComponent(panelComponent2.A(), panelComponent2.L() - panelComponent3.L());
            panelComponent4.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("wrap");
            panelComponent2.h(panelComponent4, "widthwrap");
            TextButton textButton = new TextButton("CREATE NEW", 0.8, PublicProfilesFrame.J.B, PublicProfilesFrame.J.O);
            textButton.o(panelComponent4.A());
            textButton.Y(14.0);
            textButton.addClickListener(() -> this.lambda$createLeftContainer$2(textButton));
            textButton.setUseAlternateFont(true);
            textButton.setDeriveTextColorFromBackground(false);
            textButton.setNormalTextColor(Color.WHITE);
            panelComponent4.h(textButton, new Object[0]);
            SpacerComponent spacerComponent = new SpacerComponent(0.0, 2.0);
            panelComponent4.h(spacerComponent, new Object[0]);
            this.profileListPanel = new PanelComponent(panelComponent4.A(), panelComponent2.L() - panelComponent3.L() - textButton.L() - spacerComponent.L() + 2.0);
            this.profileListPanel.k(true);
            this.profileListPanel.t(this.profileListPanel.L());
            this.profileListPanel.setShowDisabledOverlay(false);
            this.profileListPanel.F(FrameScrollbarPlacement.OUTSIDE);
            this.profileListPanel.T(true);
            this.profileListPanel.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("wrap");
            panelComponent4.h(this.profileListPanel, new Object[0]);
            for (PublicProfile publicProfile : Vape.INSTANCE.getPublicProfileManager().getProfilesById().values()) {
                this.e(publicProfile);
            }
        }
    }

    private void J(PopupFrame popupFrame, boolean bl) {
        if (popupFrame == null) {
            return;
        }
        ClientSettings.removeFramePopups(popupFrame);
        ClientSettings.removePopup(popupFrame);
        if (popupFrame.V$src$Lgg_vape_ui_click_component_GuiComponent_$jpbobc() instanceof PopupFrame && !bl) {
            this.J((PopupFrame)popupFrame.V$src$Lgg_vape_ui_click_component_GuiComponent_$jpbobc(), true);
        }
    }

    private static void lambda$openWithPublicListing$16(PublicProfilesFrame publicProfilesFrame) {
        publicProfilesFrame.l((PublicProfile)null);
    }

    public void O(@Nullable PopupFrame popupFrame) {
        PopupFrame popupFrame2 = this.activePopup;
        if (popupFrame2 != null) {
            this.D(popupFrame2);
            this.a();
            this.l$src$V$1mibm4x();
        }
        this.activePopup = popupFrame;
    }

    private static void lambda$openWithEditor$22(PublicProfileOwnerDetailsPanel publicProfileOwnerDetailsPanel, PublicProfilesFrame publicProfilesFrame, ApiResponse apiResponse, Throwable throwable) {
        if (publicProfileOwnerDetailsPanel.R$src$Ljava_util_concurrent_CompletableFuture_$1ccqvok().isCancelled()) {
            return;
        }
        publicProfileOwnerDetailsPanel.T((CompletableFuture<?>)null);
        if (throwable != null) {
            Vape.logThrowable(throwable);
            publicProfilesFrame.D(publicProfileOwnerDetailsPanel.E());
            return;
        }
        if (!apiResponse.isSuccessful()) {
            Vape.debugLog("Failed to load public profile data: " + apiResponse.getError());
            PublicProfileManager.showWarning("Failed to view profile: " + apiResponse.getError());
            publicProfilesFrame.D(publicProfileOwnerDetailsPanel.E());
            return;
        }
        if (!ASSERTIONS_DISABLED && apiResponse.getData() == null) {
            throw new AssertionError();
        }
        publicProfilesFrame.N((PublicProfile)apiResponse.getData());
    }

    private List<GuiComponent> lambda$null$7(Function<PublicProfileSummary, PaddedComponent> function, ApiResponse<PagedResult<PublicProfileSummary>> apiResponse) {
        if (!apiResponse.isSuccessful()) {
            return null;
        }
        if (!ASSERTIONS_DISABLED && apiResponse.getData() == null) {
            throw new AssertionError();
        }
        this.resultsList.setPageMetadata(apiResponse.getData());
        List<PublicProfileSummary> summaries = apiResponse.getData().getContent();
        ArrayList<GuiComponent> arrayList = new ArrayList<GuiComponent>();
        for (PublicProfileSummary publicProfileSummary : summaries) {
            PaddedComponent paddedComponent = function.apply(publicProfileSummary);
            arrayList.add(paddedComponent);
            Vape.INSTANCE.getPublicProfileManager().addSummaryTags(publicProfileSummary);
            PublicProfileListingResultCardComponent publicProfileListingResultCardComponent = paddedComponent.t(PublicProfileListingResultCardComponent.class);
            AtomicBoolean atomicBoolean = new AtomicBoolean(true);
            publicProfileListingResultCardComponent.addMouseListener(new PublicProfileListingResultOpenClickHandler(this, atomicBoolean, publicProfileSummary));
        }
        return arrayList;
    }

    private static GuiComponent lambda$createRightContainer$6(Function function) {
        return (PaddedComponent)function.apply(null);
    }

    public static void a(@Nullable Consumer<PublicProfilesFrame> consumer) {
        PublicProfilesFrame publicProfilesFrame = ClientSettings.getFrame(PublicProfilesFrame.class);
        publicProfilesFrame.t(true, false);
        ClientSettings.INSTANCE.switchFrameStack(ClientSettings.publicProfilesStack);
        if (consumer != null) {
            consumer.accept(publicProfilesFrame);
        }
    }

    @Override
    public void t(boolean bl, boolean bl2) {
        super.t(bl, bl2);
        this.connectionRefreshPending = false;
        if (bl) this.refreshOwnedProfiles();
    }

    /** Local settings take precedence at startup; fetch owned publications separately. */
    private void refreshOwnedProfiles() {
        if (this.ownedRefreshPending) return;
        PublicProfileManager manager = Vape.INSTANCE.getPublicProfileManager();
        Map<Long, PublicProfile> before = new LinkedHashMap<Long, PublicProfile>(manager.getProfilesById());
        this.ownedRefreshPending = true;
        ApiServices.getInstance().getUserDataApi().getUserData().whenCompleteAsync((response, error) -> {
            this.ownedRefreshPending = false;
            if (error != null || response == null || !response.isSuccessful()) return;
            UserDataResponse data = response.getData();
            // Do not apply a stale response over a create, edit or delete that
            // completed while the request was in flight.
            if (data == null || !before.equals(manager.getProfilesById())) return;
            boolean changed = false;
            for (PublicProfile profile : data.getPublicProfiles().values()) {
                if (!before.containsKey(profile.getProfileId())) {
                    before.put(profile.getProfileId(), profile);
                    changed = true;
                }
            }
            if (changed) {
                manager.replaceProfiles(before.values());
                Vape.INSTANCE.getProfilesManager().updatePublicProfileLinks();
                this.W();
            }
        }, ClientSettings.UI_EXECUTOR);
    }

    @Override
    public void v() {
    }

    @Override
    public void Y() {
    }

    public void T(PopupFrame popupFrame) {
        this.J(popupFrame, false);
    }

    private void lambda$createLeftContainer$1() {
        this.showingOwnedProfiles = !this.showingOwnedProfiles;
        this.W();
    }

    public PublicProfileOverlayPopupFrame y(@Nullable Frame frame, GuiComponent guiComponent) {
        AtomicReference<PublicProfileOverlayPopupFrame> atomicReference = new AtomicReference<PublicProfileOverlayPopupFrame>();
        Frame frame2 = frame != null ? frame : this;
        PublicProfileOverlayPopupFrame publicProfileOverlayPopupFrame = ClientSettings.createPopup(frame2, this.Z(guiComponent, () -> this.lambda$addCenteredOverlay$10(atomicReference)), PublicProfileOverlayPopupFrame.class);
        atomicReference.set(publicProfileOverlayPopupFrame);
        publicProfileOverlayPopupFrame.T((int)((frame2.A() - guiComponent.A()) / 2.0));
        publicProfileOverlayPopupFrame.Q((int)((frame2.L() - guiComponent.L()) / 2.0));
        this.C(publicProfileOverlayPopupFrame);
        return publicProfileOverlayPopupFrame;
    }

    private void lambda$createLeftContainer$2(TextButton textButton) {
        this.h(textButton);
    }

    @Nullable
    public static CompletableFuture<?> J(boolean bl, long l) {
        if (bl) {
            if (!OnlineConnectionManager.INSTANCE.getConnectionState().equals((Object)OnlineConnectionState.ONLINE)) {
                PublicProfilesFrame.a(PublicProfilesFrame::lambda$openWithEditor$20);
                return null;
            }
            PublicProfilesFrame.a(PublicProfilesFrame::lambda$openWithEditor$21);
        }
        PublicProfilesFrame publicProfilesFrame = ClientSettings.getFrame(PublicProfilesFrame.class);
        PublicProfileOwnerDetailsPanel publicProfileOwnerDetailsPanel = publicProfilesFrame.N((PublicProfile)null);
        publicProfileOwnerDetailsPanel.T(ApiServices.getInstance().getPublicProfileApi().viewProfile(l).whenCompleteAsync((arg_0, arg_1) -> PublicProfilesFrame.lambda$openWithEditor$22(publicProfileOwnerDetailsPanel, publicProfilesFrame, arg_0, arg_1), (Executor)ClientSettings.UI_EXECUTOR).exceptionally(PublicProfilesFrame::lambda$openWithEditor$23));
        return publicProfileOwnerDetailsPanel.R$src$Ljava_util_concurrent_CompletableFuture_$1ccqvok();
    }

    private void lambda$null$3() {
        this.resultsList.reload();
    }

    public PublicProfileOverlayPopupFrame S(@Nullable Frame frame, GuiComponent guiComponent) {
        PublicProfileOverlayPopupFrame publicProfileOverlayPopupFrame = ClientSettings.createPopup(frame != null ? frame : this, guiComponent, PublicProfileOverlayPopupFrame.class);
        this.O(publicProfileOverlayPopupFrame);
        return publicProfileOverlayPopupFrame;
    }

    public void l(PublicProfileSortMode publicProfileSortMode) {
        this.sortMode = publicProfileSortMode;
    }

    public PublicProfilesFrame() {
        this.K(200.0);
        this.S(200.0);
        this.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M(false);
        this.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().t(false);
        this.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().I(false);
        this.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().U(false);
        this.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("wrap");
        this.Y(new PublicProfilesFrameHeaderComponent(this, this, "newprofiles", "Public Profiles", 0.5).Q(PublicProfilesFrame::lambda$new$0));
        this.W();
        this.Y(false);
        this.setVisible(true);
        this.L(false, true);
        this.g(true);
        EventBus.getInstance().registerListener(this, new Predicate[0]);
    }


    private static void lambda$null$17(ApiResponse apiResponse, PublicProfilesFrame publicProfilesFrame) {
        publicProfilesFrame.l((PublicProfile)apiResponse.getData());
    }

    private static void lambda$new$0() {
        if (ClientSettings.INSTANCE.getActiveStack() instanceof ClickGuiFrameManager) {
            ClickGuiFrameManager clickGuiFrameManager = (ClickGuiFrameManager)ClientSettings.INSTANCE.getActiveStack();
            clickGuiFrameManager.closeSidecar();
        } else {
            ClientSettings.INSTANCE.returnToMainGui();
        }
    }

    private void W() {
        if (!OnlineConnectionManager.INSTANCE.getConnectionState().equals((Object)OnlineConnectionState.ONLINE)) {
            this.d$src$V$fo8605();
            return;
        }
        this.showingConnectionStatus = false;
        this.removeMarkedChildren();
        this.h(new InsetFilledSpacerComponent(this.A(), 2.0, 0.5, 0.0, PublicProfilesFrame.J.l), new Object[0]);
        PanelComponent panelComponent = new PanelComponent(this.x(), 185.0);
        this.h(panelComponent, new Object[0]);
        panelComponent.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("widthwrap");
        this.t(panelComponent);
        FilledSpacerComponent filledSpacerComponent = new FilledSpacerComponent(1.0, panelComponent.L() + 2.0, PublicProfilesFrame.J.m);
        if (!this.showingOwnedProfiles) {
            panelComponent.h(filledSpacerComponent, new Object[0]);
        }
        panelComponent.h(new InsetFilledSpacerComponent(4.0, 0.0, 0.5, 0.0, new Color(0, 0, 0, 0)), new Object[0]);
        this.D(panelComponent);
        this.resultsList.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("wrap");
        this.H(true);
    }

    private static void lambda$openWithPublicListing$15(PublicProfilesFrame publicProfilesFrame) {
    }

    public PublicProfileOverlayPopupFrame W(@Nullable Frame frame, GuiComponent guiComponent) {
        Frame frame2 = frame != null ? frame : this;
        AtomicReference<PublicProfileOverlayPopupFrame> atomicReference = new AtomicReference<PublicProfileOverlayPopupFrame>();
        PublicProfileOverlayPopupFrame publicProfileOverlayPopupFrame = ClientSettings.createPopup(frame2, this.Z(guiComponent, () -> this.lambda$setCenteredOverlay$9(atomicReference)), PublicProfileOverlayPopupFrame.class);
        atomicReference.set(publicProfileOverlayPopupFrame);
        publicProfileOverlayPopupFrame.T((int)((frame2.A() - guiComponent.A()) / 2.0));
        publicProfileOverlayPopupFrame.Q((int)((frame2.L() - guiComponent.L()) / 2.0));
        this.O(publicProfileOverlayPopupFrame);
        return publicProfileOverlayPopupFrame;
    }

    @Override
    public double L() {
        return 214.0;
    }

    @Override
    public String getName() {
        return "Public Profiles";
    }

    private void D(PopupFrame popupFrame) {
        PanelComponent panelComponent = popupFrame.k(PanelComponent.class);
        if (panelComponent != null) {
            for (GuiComponent guiComponent : panelComponent.f()) {
                FrameComponent frameComponent;
                if (guiComponent instanceof PanelComponent) {
                    frameComponent = (PanelComponent)guiComponent;
                    for (GuiComponent guiComponent2 : frameComponent.f()) {
                        FrameComponent frameComponent2;
                        if (guiComponent2 instanceof PublicProfileOverlayPanelBase) {
                            frameComponent2 = (PublicProfileOverlayPanelBase)guiComponent2;
                            ((PublicProfileOverlayPanelBase)frameComponent2).d$src$V$15t6q4y();
                        }
                        if (!(guiComponent2 instanceof HudSettingsFrameBase)) continue;
                        frameComponent2 = (HudSettingsFrameBase)guiComponent2;
                        ((HudSettingsFrameBase)frameComponent2).onPublicProfileContextChanged();
                    }
                }
                if (guiComponent instanceof PublicProfileOverlayPanelBase) {
                    frameComponent = (PublicProfileOverlayPanelBase)guiComponent;
                    ((PublicProfileOverlayPanelBase)frameComponent).d$src$V$15t6q4y();
                }
                if (!(guiComponent instanceof HudSettingsFrameBase)) continue;
                frameComponent = (HudSettingsFrameBase)guiComponent;
                ((HudSettingsFrameBase)frameComponent).onPublicProfileContextChanged();
            }
        }
        ClientSettings.removePopup(popupFrame);
        if (this.activePopup == popupFrame) {
            this.activePopup = null;
        }
    }

    @Override
    public void a() {
        PopupFrame popupFrame = this.activePopup;
        if (popupFrame != null) {
            this.T(popupFrame);
            this.activePopup = null;
        }
    }

    static void k(PublicProfilesFrame publicProfilesFrame, PopupFrame popupFrame) {
        publicProfilesFrame.D(popupFrame);
    }

    private void lambda$onPublicProfileDelete$14(PublicProfileDeletedEvent publicProfileDeletedEvent) {
        ArrayList<GuiComponent> arrayList = new ArrayList<GuiComponent>();
        for (GuiComponent guiComponent : this.profileListPanel.f()) {
            PaddedComponent paddedComponent;
            PublicProfileListEntryComponent publicProfileListEntryComponent;
            if (!(guiComponent instanceof PaddedComponent) || (publicProfileListEntryComponent = (paddedComponent = (PaddedComponent)guiComponent).t(PublicProfileListEntryComponent.class)) == null || publicProfileListEntryComponent.getPublicProfile().getProfileId() != publicProfileDeletedEvent.getProfile().getProfileId()) continue;
            arrayList.add(guiComponent);
        }
        if (arrayList.isEmpty()) {
            return;
        }
        for (GuiComponent guiComponent : arrayList) {
            this.profileListPanel.removeChild(guiComponent);
        }
        Vape.INSTANCE.getProfilesManager().updatePublicProfileLinks();
    }

    private static PaddedComponent lambda$createRightContainer$5(PublicProfileSummary publicProfileSummary) {
        PublicProfileListingResultCardComponent publicProfileListingResultCardComponent = new PublicProfileListingResultCardComponent(publicProfileSummary);
        return new PaddedComponent(1.0, 2.0, 0.0, 3.0, publicProfileListingResultCardComponent);
    }

    @Nullable
    public static CompletableFuture<?> s(long l) {
        if (!OnlineConnectionManager.INSTANCE.getConnectionState().equals((Object)OnlineConnectionState.ONLINE)) {
            PublicProfilesFrame.a(PublicProfilesFrame::lambda$openWithPublicListing$15);
            return null;
        }
        PublicProfilesFrame.a(PublicProfilesFrame::lambda$openWithPublicListing$16);
        return ApiServices.getInstance().getPublicProfileApi().viewProfile(l).whenCompleteAsync(PublicProfilesFrame::lambda$openWithPublicListing$18, (Executor)ClientSettings.UI_EXECUTOR).exceptionally(PublicProfilesFrame::lambda$openWithPublicListing$19);
    }

    private void lambda$addCenteredOverlay$10(AtomicReference atomicReference) {
        PublicProfileOverlayPopupFrame publicProfileOverlayPopupFrame = (PublicProfileOverlayPopupFrame)atomicReference.get();
        if (publicProfileOverlayPopupFrame != null) {
            this.D(publicProfileOverlayPopupFrame);
        }
    }

    private static void lambda$openWithEditor$20(PublicProfilesFrame publicProfilesFrame) {
    }

    public void e(Profile profile) {
        this.W(null, new ProfilePublishEditorPanel(this, profile));
    }

    @Override
    public void u() {
        if (!OnlineConnectionManager.INSTANCE.getConnectionState().equals((Object)OnlineConnectionState.ONLINE) && !this.showingConnectionStatus) {
            this.d$src$V$fo8605();
        } else if (OnlineConnectionManager.INSTANCE.getConnectionState().equals((Object)OnlineConnectionState.ONLINE) && this.showingConnectionStatus) {
            this.connectionRefreshPending = false;
            this.refreshOwnedProfiles();
        }
        PopupFrame popupFrame = this.activePopup;
        if (popupFrame != null && popupFrame.V$src$Z$1xhop3l()) {
            popupFrame.T$src$V$1wse0de();
        }
    }

    public PublicProfileSortMode Z$src$Lgg_vape_config_PublicProfileSortMode_$18pvsyy() {
        return this.sortMode;
    }

    private static ApiResponse lambda$openWithPublicListing$19(Throwable throwable) {
        return null;
    }

    @Override
    public void c() {
        if (!this.connectionRefreshPending) {
            this.connectionRefreshPending = true;
            this.layoutRebuildRequired = true;
            this.W();
            this.layoutRebuildRequired = false;
        }
        if (this.resultsList != null) {
            this.resultsList.H(true);
        }
        if (this.resultsPanel != null) {
            this.resultsPanel.H(true);
        }
        super.c();
    }

    public static void w$src$V$fyo9a0() {
        PublicProfilesFrame.a((Consumer<PublicProfilesFrame>)null);
    }

    private PanelComponent Z(@NotNull GuiComponent guiComponent, Runnable runnable) {
        PanelComponent panelComponent = new PanelComponent(guiComponent.A(), guiComponent.L() + 11.0);
        panelComponent.setDisabledOverlayColor(PublicProfilesFrame.J.m);
        panelComponent.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("wrap");
        panelComponent.setOutlineColor(PublicProfilesFrame.J.l);
        panelComponent.setBorderWidth(1.0f);
        PanelComponent panelComponent2 = new PanelComponent(guiComponent.A(), 10.0);
        panelComponent2.setShowDisabledOverlay(false);
        panelComponent2.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("alignright");
        panelComponent.h(panelComponent2, new Object[0]);
        SquareIconButtonComponent squareIconButtonComponent = new SquareIconButtonComponent("newclose", 1.2, new Color(0, 0, 0, 0), PublicProfilesFrame.J.h, 8.0, 8.0);
        squareIconButtonComponent.addClickListener(() -> PublicProfilesFrame.lambda$createCenteredOverlayNode$11(runnable));
        panelComponent2.h(new PaddedComponent(2.0, 2.0, 2.0, 2.0, squareIconButtonComponent), new Object[0]);
        panelComponent.h(guiComponent, new Object[0]);
        return panelComponent;
    }

    public void C(PopupFrame popupFrame) {
        Vape.debugLog("addPopup(" + popupFrame + ")");
        PopupFrame popupFrame2 = this.activePopup;
    }

    public PublicProfileOwnerDetailsPanel N(@Nullable PublicProfile publicProfile) {
        if (publicProfile == null) {
            PublicProfileOwnerDetailsPanel publicProfileOwnerDetailsPanel = new PublicProfileOwnerDetailsPanel(this, null, null);
            publicProfileOwnerDetailsPanel.S(this.W(null, publicProfileOwnerDetailsPanel));
            return publicProfileOwnerDetailsPanel;
        }
        ProfileSnapshot profileSnapshot = ProfileSnapshot.resolvePublicProfileSnapshot(publicProfile);
        PublicProfileOwnerDetailsPanel publicProfileOwnerDetailsPanel = new PublicProfileOwnerDetailsPanel(this, publicProfile, profileSnapshot);
        publicProfileOwnerDetailsPanel.S(this.W(null, publicProfileOwnerDetailsPanel));
        return publicProfileOwnerDetailsPanel;
    }

    @Nullable
    public PopupFrame A$src$Lgg_vape_ui_click_frame_PopupFrame_$45a6ba() {
        return this.activePopup;
    }

    public String o$src$Ljava_lang_String_$ububnq() {
        return this.lastSearchQuery;
    }

    private CompletableFuture<List<GuiComponent>> lambda$createRightContainer$8(Function<PublicProfileSummary, PaddedComponent> function) {
        String string;
        this.lastSearchQuery = string = this.searchFilterPanel.getSearchInput().getText().trim();
        return ApiServices.getInstance().getPublicProfileApi().listProfiles(this.sortMode, this.resultsList.getNextPageIndex(), string, this.searchFilterPanel.getTokenSelector().getTokens().stream().map(PublicProfileFilterTokenComponent::getText).collect(Collectors.toList())).thenApplyAsync(arg_0 -> this.lambda$null$7(function, arg_0), (Executor)ClientSettings.UI_EXECUTOR);
    }

    private void lambda$setCenteredOverlay$9(AtomicReference atomicReference) {
        PublicProfileOverlayPopupFrame publicProfileOverlayPopupFrame = (PublicProfileOverlayPopupFrame)atomicReference.get();
        if (publicProfileOverlayPopupFrame != null) {
            this.D(publicProfileOverlayPopupFrame);
        }
    }

    private void e(PublicProfile publicProfile) {
        PublicProfileListEntryComponent publicProfileListEntryComponent = new PublicProfileListEntryComponent(publicProfile);
        publicProfileListEntryComponent.o(92.0);
        publicProfileListEntryComponent.setSingleFutureClickListener(() -> PublicProfilesFrame.lambda$addPublicProfileButton$12(publicProfile));
        this.profileListPanel.h(new PaddedComponent(0.0, 1.0, publicProfileListEntryComponent), new Object[0]);
    }

    @EventHandler
    public void R(PublicProfileCreatedEvent publicProfileCreatedEvent) {
        ClientSettings.UI_EXECUTOR.execute(() -> this.lambda$onPublicProfileCreate$13(publicProfileCreatedEvent));
    }

    private static void lambda$openWithPublicListing$18(ApiResponse apiResponse, Throwable throwable) {
        if (throwable != null) {
            Vape.logThrowable(throwable);
            return;
        }
        if (!apiResponse.isSuccessful()) {
            Vape.debugLog("Failed to load public profile data: " + apiResponse.getError());
            PublicProfileManager.showWarning("Failed to view profile: " + apiResponse.getError());
            return;
        }
        if (!ASSERTIONS_DISABLED && apiResponse.getData() == null) {
            throw new AssertionError();
        }
        PublicProfilesFrame.a(arg_0 -> PublicProfilesFrame.lambda$null$17(apiResponse, arg_0));
    }

    @EventHandler
    public void X(PublicProfileDeletedEvent publicProfileDeletedEvent) {
        ClientSettings.UI_EXECUTOR.execute(() -> this.lambda$onPublicProfileDelete$14(publicProfileDeletedEvent));
    }

    public PublicProfileListingDetailsPanel l(@Nullable PublicProfile publicProfile) {
        if (publicProfile == null) {
            PublicProfileListingDetailsPanel publicProfileListingDetailsPanel = new PublicProfileListingDetailsPanel(this, null, null);
            publicProfileListingDetailsPanel.S(this.W(null, publicProfileListingDetailsPanel));
            return publicProfileListingDetailsPanel;
        }
        Object serializedModules = publicProfile.getData() != null ? publicProfile.getData().getOrDefault("modules", null) : null;
        ProfileSnapshot profileSnapshot = new ProfileSnapshot(null, (JsonArray)ApiHttpClient.GSON.fromJson(serializedModules != null ? ApiHttpClient.GSON.toJson(serializedModules) : "[]", JsonArray.class));
        PublicProfileListingDetailsPanel publicProfileListingDetailsPanel = new PublicProfileListingDetailsPanel(this, publicProfile, profileSnapshot);
        publicProfileListingDetailsPanel.S(this.W(null, publicProfileListingDetailsPanel));
        return publicProfileListingDetailsPanel;
    }

    private void lambda$createRightContainer$4() {
        ClientSettings.UI_EXECUTOR.execute(this::lambda$null$3);
    }

    private void d$src$V$fo8605() {
        this.showingConnectionStatus = true;
        this.removeMarkedChildren();
        OnlineConnectionStatusPanelBody onlineConnectionStatusPanelBody = new OnlineConnectionStatusPanelBody();
        this.h(new PaddedComponent(this.A() / 2.0 - onlineConnectionStatusPanelBody.A() / 2.0, this.L() / 2.0 - onlineConnectionStatusPanelBody.L() / 2.0 - 20.0, onlineConnectionStatusPanelBody), new Object[0]);
        this.H(true);
    }

    private static CompletableFuture lambda$addPublicProfileButton$12(PublicProfile publicProfile) {
        return PublicProfilesFrame.J(false, publicProfile.getProfileId());
    }

    public void h(TextButton textButton) {
        PublicProfileOverlayPopupFrame publicProfileOverlayPopupFrame = this.S(null, new PublicProfilePublishProfilePickerPanel(this));
        publicProfileOverlayPopupFrame.T((int)(textButton.G$src$D$1b2f02a() - this.G$src$D$1b2f02a()) + 45);
        publicProfileOverlayPopupFrame.Q((int)(textButton.n() - this.n()) + 5);
    }

    private void D(PanelComponent panelComponent) {
        this.resultsPanel = new PanelComponent(panelComponent.A() - this.profileListWrapper.A() - (double)(this.showingOwnedProfiles ? 5 : 6), panelComponent.L() + 10.0);
        this.resultsPanel.t(this.resultsPanel.L());
        this.resultsPanel.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("wrap");
        panelComponent.h(this.resultsPanel, new Object[0]);
        this.resultsPanel.h(new SpacerComponent(0.0, 2.0), new Object[0]);
        SimpleTextLabelComponent simpleTextLabelComponent = new SimpleTextLabelComponent("ALL PUBLIC PROFILES");
        simpleTextLabelComponent.setBold(true);
        simpleTextLabelComponent.setTextColor(PublicProfilesFrame.J.h);
        simpleTextLabelComponent.setFontScale(0.7);
        simpleTextLabelComponent.setOffsetX(0.0f);
        this.resultsPanel.h(simpleTextLabelComponent, new Object[0]);
        this.resultsPanel.h(new SpacerComponent(0.0, 5.0), new Object[0]);
        PublicProfileSearchFilterPanel publicProfileSearchFilterPanel = this.searchFilterPanel;
        this.searchFilterPanel = new PublicProfileSearchFilterPanel(this.showingOwnedProfiles ? 240.0 : 324.0, this::lambda$createRightContainer$4);
        if (publicProfileSearchFilterPanel != null) {
            this.searchFilterPanel.getSearchInput().setText(publicProfileSearchFilterPanel.getSearchInput().getText());
            for (PublicProfileFilterTokenComponent publicProfileFilterTokenComponent : publicProfileSearchFilterPanel.getTokenSelector().getTokens()) {
                this.searchFilterPanel.getTokenSelector().addToken(publicProfileFilterTokenComponent);
            }
        }
        this.resultsPanel.h(this.searchFilterPanel, new Object[0]);
        this.resultsPanel.h(new SpacerComponent(0.0, 5.0), new Object[0]);
        if (this.resultsList == null || this.layoutRebuildRequired) {
            this.resultsList = new PublicProfileResultsListComponent(this.resultsPanel.A() - 6.0, 50.0);
            this.resultsList.N(new WrappingFlowLayout(this.resultsList));
            this.resultsList.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("widthwrap");
            this.resultsList.N(false);
            this.resultsList.setComponentsPerRow(this.showingOwnedProfiles ? 3 : 4);
            this.resultsList.F(FrameScrollbarPlacement.OUTSIDE);
            Function<PublicProfileSummary, PaddedComponent> function = PublicProfilesFrame::lambda$createRightContainer$5;
            this.resultsList.setPlaceholderSupplier(() -> PublicProfilesFrame.lambda$createRightContainer$6(function));
            this.resultsList.setPageLoader(() -> this.lambda$createRightContainer$8(function));
            this.resultsList.reload();
        } else {
            this.resultsList.setExplicitWidth(this.resultsPanel.A() - 6.0);
            this.resultsList.setComponentsPerRow(this.showingOwnedProfiles ? 3 : 4);
            this.resultsList.rebuildLayoutPreservingScroll();
        }
        this.resultsList.setScrollContainer(this.resultsPanel);
        this.resultsList.setShowDisabledOverlay(false);
        this.resultsPanel.h(this.resultsList, new Object[0]);
    }

    @Override
    public double x() {
        return 356.0;
    }

    private void lambda$onPublicProfileCreate$13(PublicProfileCreatedEvent publicProfileCreatedEvent) {
        this.e(publicProfileCreatedEvent.getProfile());
        Vape.INSTANCE.getProfilesManager().updatePublicProfileLinks();
    }
}
