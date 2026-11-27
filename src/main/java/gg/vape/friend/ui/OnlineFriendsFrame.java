package gg.vape.friend.ui;

import gg.vape.Vape;
import gg.vape.manager.client.OnlineConnectionManager;
import gg.vape.manager.client.OnlineConnectionState;
import gg.vape.module.none.ClientSettings;
import gg.vape.ui.click.GuiMouseListener;
import gg.vape.ui.click.component.ColorDividerComponent;
import gg.vape.ui.click.component.DropdownSelectComponent;
import gg.vape.ui.click.component.GuiComponent;
import gg.vape.ui.click.component.IconButtonComponent;
import gg.vape.ui.click.component.PanelComponent;
import gg.vape.ui.click.component.SimpleTextLabelComponent;
import gg.vape.ui.click.component.SpacerComponent;
import gg.vape.ui.click.component.input.BindValueRowComponent;
import gg.vape.ui.click.component.value.BooleanToggleComponent;
import gg.vape.ui.click.component.value.ColorValueEditorComponent;
import gg.vape.ui.click.frame.CenteredPopupFrame;
import gg.vape.ui.click.frame.PopupFrame;
import gg.vape.ui.click.frame.SettingsSectionComponent;
import gg.vape.ui.click.frame.SettingsSubpageFrame;
import gg.vape.ui.click.frame.impl.ThemeComponentGroupFactory;
import gg.vape.ui.click.frame.impl.ThemeComponentGroupKey;
import gg.vape.ui.notification.NotificationToastOverlay;
import gg.vape.unmap.ModeOption;
import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OnlineFriendsFrame
extends SettingsSubpageFrame {
    private final PanelComponent popupAnchorPanel = new PanelComponent(104.0, 150.0);
    private BooleanToggleComponent reservedToggle01;
    private PopupFrame friendManagementPopup;
    private OnlineRegistrationPanel registrationPanel;
    private BooleanToggleComponent reservedToggle02;
    private BooleanToggleComponent reservedToggle03;
    private FriendRequestsPanel friendRequestsPanel;
    private PopupFrame registrationPopup;
    private final OnlineFriendsEmptyStatePanel emptyStatePanel;
    private BooleanToggleComponent reservedToggle04;
    private static String[] obfuscationStrings;
    private BooleanToggleComponent reservedToggle05;
    private BooleanToggleComponent reservedToggle06;
    private IconButtonComponent addFriendsButton;
    private BooleanToggleComponent reservedToggle07;
    private ColorValueEditorComponent reservedColorEditor;
    private NotificationToastOverlay notificationOverlay;
    private BooleanToggleComponent reservedToggle08;
    private final OnlineConnectionStatusPanel connectionStatusPanel;
    private GuiMouseListener popupOutsideClickListener;
    private SimpleTextLabelComponent reservedLabel;
    private BooleanToggleComponent reservedToggle09;
    private final OnlineFriendsListPanel onlineFriendsListPanel;
    private PopupFrame notificationPopup;
    private PopupFrame friendRequestsPopup;
    private BooleanToggleComponent reservedToggle10;
    private final PanelComponent contentPanel;
    private ColorDividerComponent toolbarDivider;
    private DropdownSelectComponent<ModeOption> reservedModeDropdown;
    private BooleanToggleComponent reservedToggle11;
    private final FriendManagementPanel friendManagementPanel;
    private PanelComponent activeContentPanel;
    private BooleanToggleComponent reservedToggle12;
    private final PanelComponent pagePanel = new PanelComponent(104.0, 150.0);
    private OnlineModeToggleComponent modeToggle;
    private BooleanToggleComponent reservedToggle13;

    public OnlineFriendsFrame() {
        super("newfriends", "Friends");
        this.contentPanel = new PanelComponent(104.0, 130.0);
        this.modeToggle = new OnlineFriendsFrameModeToggleComponent(this, "Soulbound FRIENDS", "MINECRAFT FRIENDS", true);
        this.friendManagementPanel = new FriendManagementPanel();
        this.friendRequestsPanel = new FriendRequestsPanel();
        this.addFriendsButton = new IconButtonComponent("add friends@2x", 1.0, new Color(180, 180, 180), Color.WHITE, 13.0, 13.0);
        this.toolbarDivider = new ColorDividerComponent(OnlineFriendsFrame.J.l);
        this.onlineFriendsListPanel = new OnlineFriendsListPanel();
        this.emptyStatePanel = new OnlineFriendsEmptyStatePanel();
        this.connectionStatusPanel = new OnlineConnectionStatusPanel();
        this.popupOutsideClickListener = new OnlineFriendsFramePopupOutsideClickListener(this);
        this.setVisible(false);
        this.o(103.0);
        this.N(false);
        this.D(true);
        this.configureToolbar();
        this.addThemeSettings();
        this.buildLayout();
        this.showContent(this.emptyStatePanel);
        this.addFriendsButton.w("Add Soulbound friends");
        this.modeToggle.setTooltips("", "");
        this.notificationOverlay = new NotificationToastOverlay(this);
        this.addMouseListener(this.popupOutsideClickListener);
        this.i$src$Lgg_vape_ui_click_frame_FrameToolbarComponent_$gnpgc6().setDefaultIconScale(0.8f);
    }

    @Override
    public void u() {
        super.u();
        if (Vape.INSTANCE.getOnlineManager() == null) {
            return;
        }
        int n = Vape.INSTANCE.getOnlineManager().getFriendRequestManager().getIncomingRequests().size();
        if (n > 0) {
            this.addFriendsButton.setIconResource("add friends notification@2x");
        } else {
            this.addFriendsButton.setIconResource("add friends@2x");
        }
        this.addFriendsButton.setVisible(false);
        if (this.modeToggle.isLeftSelected().booleanValue()) {
            if (OnlineConnectionManager.INSTANCE.getConnectionState().equals((Object)OnlineConnectionState.ONLINE)) {
                if (Vape.INSTANCE.getOnlineFriendManager().getFriends().size() > 0 || Vape.INSTANCE.getOnlineManager().getPartyManager().getCurrentParty() != null) {
                    this.showContent(this.onlineFriendsListPanel);
                } else {
                    this.showContent(this.emptyStatePanel);
                }
                this.addFriendsButton.setVisible(true);
            } else {
                this.showContent(this.connectionStatusPanel);
            }
            if (this.registrationPopup != null) {
                this.activeContentPanel.setVisible(false);
            } else if (this.activeContentPanel != null) {
                this.activeContentPanel.setVisible(true);
            }
        } else {
            if (this.activeContentPanel != null) {
                this.activeContentPanel.setVisible(false);
            }
            if (!this.q$src$Lgg_vape_ui_click_component_IconButtonComponent_$1bvowkh().V$src$Z$1xhop3l()) {
                this.q$src$Lgg_vape_ui_click_component_IconButtonComponent_$1bvowkh().setVisible(true);
            }
            if (OnlineConnectionManager.INSTANCE.getConnectionState().equals((Object)OnlineConnectionState.ONLINE)) {
                this.addFriendsButton.setVisible(true);
            }
        }
    }

    public void refreshOnlineData() {
        try {
            this.closeAllPopups();
            OnlineFriendUiHelper.refreshMinecraftFriends();
            if (!this.modeToggle.isLeftSelected().booleanValue()) {
                this.modeToggle.setLeftSelected(false);
            }
        }
        catch (Exception exception) {
            Vape.logThrowable(exception);
        }
    }

    public OnlineFriendEntriesPanel getOnlineFriendEntriesPanel() {
        return this.onlineFriendsListPanel.getFriendEntriesPanel();
    }

    public void updateFriendManagementPopup(boolean vapeFriendsSelected) {
        this.closeAllPopups();
        if (!vapeFriendsSelected) {
            if (this.friendManagementPopup == null) {
                this.friendManagementPopup = this.createPopup(this.contentPanel, this.friendManagementPanel, CenteredPopupFrame.class);
            }
        } else if (this.friendManagementPopup != null) {
            ClientSettings.removePopup(this.friendManagementPopup);
            this.friendManagementPopup = null;
        }
    }

    public void closeRegistrationIfOpen() {
        if (this.registrationPopup != null) {
            this.closeRegistrationPopup();
        }
    }

    private void configureToolbar() {
        this.i$src$Lgg_vape_ui_click_frame_FrameToolbarComponent_$gnpgc6().addAction(this.addFriendsButton);
        this.addFriendsButton.addClickListener(new OnlineFriendsFramePopupCloseClickHandler(this));
        this.i$src$Lgg_vape_ui_click_frame_FrameToolbarComponent_$gnpgc6().getNavigationButton().addClickListener(new OnlineFriendsFrameConditionalPopupCloseClickHandler(this));
    }

    public static NotificationToastOverlay getNotificationOverlay(OnlineFriendsFrame frame) {
        return frame.notificationOverlay;
    }

    public static void toggleFriendRequestsPopup(OnlineFriendsFrame frame) {
        frame.toggleFriendRequestsPopup();
    }

    private void toggleFriendRequestsPopup() {
        if (this.friendRequestsPopup == null) {
            this.friendRequestsPopup = this.createPopup(this.popupAnchorPanel, this.friendRequestsPanel, CenteredPopupFrame.class);
            this.i$src$Lgg_vape_ui_click_frame_FrameToolbarComponent_$gnpgc6().showBackNavigation("Friend requests", false);
        } else {
            ClientSettings.removePopup(this.friendRequestsPopup);
            this.friendRequestsPopup = null;
            this.i$src$Lgg_vape_ui_click_frame_FrameToolbarComponent_$gnpgc6().restoreDefaultNavigation();
        }
    }

    private void updateNotificationPopup() {
        if (this.notificationPopup == null) {
            this.notificationPopup = this.createPopup(this, this.notificationOverlay, PopupFrame.class);
        }
        this.c(this.notificationPopup);
        this.notificationPopup.K(this.G$src$D$1b2f02a());
        this.notificationPopup.S(this.n());
    }

    public PartyInvitesPanel getPartyInvitesPanel() {
        return this.onlineFriendsListPanel.getInvitesPanel();
    }


    public static void setObfuscationStrings(String[] values) {
        obfuscationStrings = values;
    }

    public void showRegistration() {
        if (this.registrationPopup != null) {
            this.closeRegistrationPopup();
        }
        if (this.registrationPanel == null) {
            this.registrationPanel = new OnlineRegistrationPanel();
        }
        this.registrationPopup = this.createPopup(this.popupAnchorPanel, this.registrationPanel, CenteredPopupFrame.class);
        this.q$src$Lgg_vape_ui_click_component_IconButtonComponent_$1bvowkh().setVisible(false);
        this.addFriendsButton.setVisible(false);
    }

    public PanelComponent getPagePanel() {
        return this.pagePanel;
    }

    public OnlineModeToggleComponent getModeToggle() {
        return this.modeToggle;
    }

    static {
        OnlineFriendsFrame.setObfuscationStrings(new String[4]);
    }

    public PopupFrame createPopup(GuiComponent anchor, GuiComponent content, Class<? extends PopupFrame> popupClass) {
        PopupFrame popupFrame = ClientSettings.createPopup(anchor, content, popupClass);
        popupFrame.addMouseListener(this.popupOutsideClickListener);
        return popupFrame;
    }

    private void buildLayout() {
        this.pagePanel.t(150.0);
        this.pagePanel.N(false);
        this.pagePanel.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("wrap");
        this.pagePanel.h(new SpacerComponent(0.0, 2.0), new Object[0]);
        this.pagePanel.h(this.modeToggle, new Object[0]);
        this.pagePanel.h(new SpacerComponent(1.0, 4.0), new Object[0]);
        this.pagePanel.h(this.contentPanel, new Object[0]);
        this.popupAnchorPanel.t(150.0);
        this.popupAnchorPanel.N(false);
        this.popupAnchorPanel.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("spanWidth, offsetX 6");
        this.popupAnchorPanel.h(this.pagePanel, new Object[0]);
        this.h(this.popupAnchorPanel, new Object[0]);
    }

    public FriendRequestsPanel getFriendRequestsPanel() {
        return this.friendRequestsPanel;
    }

    public void closeRegistrationPopup() {
        if (this.registrationPopup == null) {
            return;
        }
        this.i$src$Lgg_vape_ui_click_frame_FrameToolbarComponent_$gnpgc6().restoreDefaultNavigation();
        ClientSettings.removePopup(this.registrationPopup);
        this.registrationPopup = null;
        this.q$src$Lgg_vape_ui_click_component_IconButtonComponent_$1bvowkh().setVisible(true);
        this.addFriendsButton.setVisible(true);
    }

    public FriendEntriesPanel getMinecraftFriendEntriesPanel() {
        return this.friendManagementPanel.getEntriesPanel();
    }

    public static String[] getObfuscationStrings() {
        return obfuscationStrings;
    }

    public OnlineFriendsListPanel getOnlineFriendsListPanel() {
        return this.onlineFriendsListPanel;
    }

    private void addThemeSettings() {
        LinkedHashMap<ThemeComponentGroupKey, GuiComponent[]> linkedHashMap = ThemeComponentGroupFactory.R(J);
        for (Map.Entry<ThemeComponentGroupKey, GuiComponent[]> entry : linkedHashMap.entrySet()) {
            String string = entry.getKey().h();
            GuiComponent[] guiComponentArray = entry.getValue();
            this.n(this.s(string, guiComponentArray));
        }
        GuiComponent[] guiComponentArray = ThemeComponentGroupFactory.E(J);
        if (guiComponentArray != null && guiComponentArray.length > 0) {
            this.n(guiComponentArray);
        }
    }

    @Override
    public void w() {
        super.w();
        this.updateFriendManagementPopup(this.modeToggle.isLeftSelected());
    }

    public OnlineFriendsEmptyStatePanel getEmptyStatePanel() {
        return this.emptyStatePanel;
    }

    public NotificationToastOverlay getNotificationOverlay() {
        return this.notificationOverlay;
    }

    public void switchToMinecraftFriends() {
        ClientSettings.getFrame(OnlineFriendsFrame.class).getModeToggle().setLeftSelected(false);
        this.q$src$Lgg_vape_ui_click_component_IconButtonComponent_$1bvowkh().setVisible(true);
    }

    private void showContent(PanelComponent panel) {
        if (this.activeContentPanel != panel) {
            this.contentPanel.t$src$V$zbu1jn();
            this.contentPanel.h(panel, new Object[0]);
            this.activeContentPanel = panel;
            this.closeRegistrationPopup();
            this.closeAllPopups();
        }
    }

    @Override
    public void p() {
        if (this.H$src$Lgg_vape_ui_click_frame_CenteredPopupFrame_$1qmombx() == null) {
            boolean bl = OnlineConnectionManager.INSTANCE.getConnectionState().equals((Object)OnlineConnectionState.ONLINE);
            List<GuiComponent> list = this.h();
            for (GuiComponent guiComponent : list) {
                if (guiComponent instanceof SettingsSectionComponent) {
                    SettingsSectionComponent settingsSectionComponent = (SettingsSectionComponent)guiComponent;
                    String string = settingsSectionComponent.A$src$Ljava_lang_String_$9tmd4u();
                    if (!"Notification Settings".equals(string) && !"Party Settings".equals(string)) continue;
                    settingsSectionComponent.setVisible(bl);
                    continue;
                }
                if (!(guiComponent instanceof BindValueRowComponent)) continue;
                guiComponent.setVisible(bl);
            }
        }
        super.p();
    }

    public static PopupFrame getFriendRequestsPopup(OnlineFriendsFrame frame) {
        return frame.friendRequestsPopup;
    }

    @Override
    public void c() {
        this.updateNotificationPopup();
        this.l$src$V$1mibm4x();
        super.c();
    }

    public void closeAllPopups() {
        ClientSettings.removeFramePopups(this);
        this.registrationPopup = null;
        this.friendManagementPopup = null;
        this.friendRequestsPopup = null;
        this.notificationPopup = null;
        this.i$src$Lgg_vape_ui_click_frame_FrameToolbarComponent_$gnpgc6().restoreDefaultNavigation();
    }
}

