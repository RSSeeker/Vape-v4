package gg.vape.ui.click.frame.impl.main;

import gg.vape.Vape;
import gg.vape.friend.Friend;
import gg.vape.friend.FriendEntry;
import gg.vape.friend.LocalOnlineFriend;
import gg.vape.friend.OnlineFriend;
import gg.vape.friend.OnlineStatus;
import gg.vape.friend.PartyInvite;
import gg.vape.friend.PartyManager;
import gg.vape.friend.ui.FriendAliasEditInputComponent;
import gg.vape.friend.ui.OnlineFriendUiHelper;
import gg.vape.friend.ui.PartyMemberEntryComponent;
import gg.vape.notification.NotificationText;
import gg.vape.ui.click.animation.ColorAnimation;
import gg.vape.ui.click.component.FriendModuleInteractiveComponent;
import gg.vape.ui.click.component.GlyphIconComponent;
import gg.vape.ui.click.component.GuiComponent;
import gg.vape.ui.click.component.LabeledTextInputComponent;
import gg.vape.ui.click.component.MultilineTextBlockComponent;
import gg.vape.ui.click.component.PanelComponent;
import gg.vape.ui.click.component.SimpleTextLabelComponent;
import gg.vape.ui.click.component.SpacerComponent;
import gg.vape.ui.click.component.gui.TextButton;
import gg.vape.ui.click.component.input.TrailingActionTextInputComponent;
import gg.vape.ui.click.component.layout.PaddedComponent;
import gg.vape.ui.click.frame.FrameScrollbarPlacement;
import gg.vape.ui.click.frame.impl.main.ClickGuiFriendSourceMode;
import gg.vape.ui.click.frame.impl.main.ClickGuiFriendSourceModeSwitchMap;
import gg.vape.ui.click.frame.impl.main.ClickGuiFriendsFriendActionComponent;
import gg.vape.ui.click.frame.impl.main.ClickGuiFriendsFriendCardFactory;
import gg.vape.ui.click.frame.impl.main.ClickGuiFriendsFriendListComponent;
import gg.vape.ui.click.frame.impl.main.ClickGuiFriendsFriendRequestComponent;
import gg.vape.ui.click.frame.impl.main.ClickGuiFriendsFriendStatusComponent;
import gg.vape.ui.click.frame.impl.main.ClickGuiFriendsModeToggleComponent;
import gg.vape.ui.click.frame.impl.main.ClickGuiFriendsNameInputListener;
import gg.vape.ui.click.frame.impl.main.ClickGuiFriendsThemeConfigFactory;
import gg.vape.ui.click.frame.impl.main.ClickGuiMainFrame;
import gg.vape.ui.click.frame.impl.main.ClickGuiOverlayPlacement;
import gg.vape.ui.click.frame.impl.main.ClickGuiOverlaySpec;
import gg.vape.ui.click.frame.impl.main.ClickGuiPageBase;
import gg.vape.ui.click.frame.impl.main.ClickGuiThemeOverlayFactory;
import gg.vape.wrapper.impl.EntityPlayer;
import gg.vape.wrapper.impl.ForgeVersion;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.PlayerInfo;
import gg.vape.wrapper.impl.WorldClient;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;

public class ClickGuiFriendsPage
extends ClickGuiPageBase {
    private PanelComponent searchResultsPanel;
    private final ClickGuiMainFrame mainFrame;
    private ClickGuiFriendSourceMode sourceMode = ClickGuiFriendSourceMode.MINECRAFT;
    private PanelComponent contentPanel;
    private GuiComponent searchResultsHeader;
    private LabeledTextInputComponent searchInput;
    private PanelComponent friendsPanel;
    private String searchQuery = "";

    private void populateSearchResults(String searchQuery) {
        Collection<?> playerInfoMap;
        this.searchResultsPanel.removeMarkedChildren();
        ClickGuiFriendsFriendActionComponent clickGuiFriendsFriendActionComponent = new ClickGuiFriendsFriendActionComponent("\"" + searchQuery + "\"", "Search Result");
        clickGuiFriendsFriendActionComponent.o(this.contentPanel.A());
        boolean alreadyFriend = Vape.INSTANCE.getFriendManager().isFriend(searchQuery);
        clickGuiFriendsFriendActionComponent.setRemoveMode(alreadyFriend);
        clickGuiFriendsFriendActionComponent.setAddPrimaryClickListener(() -> this.addFriendByName(searchQuery));
        clickGuiFriendsFriendActionComponent.setRemovePrimaryClickListener(() -> this.removeFriendByName(searchQuery));
        this.searchResultsPanel.h((GuiComponent)new PaddedComponent(0.0, 3.0, 0.0, 0.0, (GuiComponent)clickGuiFriendsFriendActionComponent), new Object[0]);
        ArrayList<String> arrayList = new ArrayList<String>();
        WorldClient worldClient = Minecraft.theWorld();
        if (worldClient.isNotNull()) {
            for (Object object3 : worldClient.X()) {
                EntityPlayer entityPlayer = new EntityPlayer(object3);
                if (entityPlayer.isNull()) continue;
                String nearbyPlayerName = entityPlayer.getName();
                if (!nearbyPlayerName.toLowerCase().contains(searchQuery) || arrayList.contains(nearbyPlayerName)) continue;
                arrayList.add(nearbyPlayerName);
                ClickGuiFriendsFriendActionComponent nearbyPlayerComponent = new ClickGuiFriendsFriendActionComponent(nearbyPlayerName, "Nearby", entityPlayer);
                nearbyPlayerComponent.o(this.contentPanel.A());
                boolean nearbyPlayerIsFriend = Vape.INSTANCE.getFriendManager().isFriend(nearbyPlayerName);
                nearbyPlayerComponent.setRemoveMode(nearbyPlayerIsFriend);
                nearbyPlayerComponent.setAddPrimaryClickListener(() -> this.addFriendByName(nearbyPlayerName));
                nearbyPlayerComponent.setRemovePrimaryClickListener(() -> this.removeFriendByName(nearbyPlayerName));
                this.searchResultsPanel.h((GuiComponent)new PaddedComponent(0.0, 3.0, 0.0, 0.0, (GuiComponent)nearbyPlayerComponent), new Object[0]);
            }
        }
        if (ForgeVersion.MC_1_8_9.d() && (playerInfoMap = Minecraft.N().getPlayerInfoMap()) != null && !playerInfoMap.isEmpty()) {
            for (Object playerInfoHandle : playerInfoMap) {
                PlayerInfo playerInfo = new PlayerInfo(playerInfoHandle);
                String onlinePlayerName = playerInfo.v().getName();
                if (!onlinePlayerName.toLowerCase().contains(searchQuery) || arrayList.contains(onlinePlayerName)) continue;
                arrayList.add(onlinePlayerName);
                ClickGuiFriendsFriendActionComponent clickGuiFriendsFriendActionComponent2 = new ClickGuiFriendsFriendActionComponent(onlinePlayerName, "Online Player", null, playerInfo);
                clickGuiFriendsFriendActionComponent2.o(this.contentPanel.A());
                boolean bl3 = Vape.INSTANCE.getFriendManager().isFriend(onlinePlayerName);
                clickGuiFriendsFriendActionComponent2.setRemoveMode(bl3);
                clickGuiFriendsFriendActionComponent2.setAddPrimaryClickListener(() -> this.addFriendByName(onlinePlayerName));
                clickGuiFriendsFriendActionComponent2.setRemovePrimaryClickListener(() -> this.removeFriendByName(onlinePlayerName));
                this.searchResultsPanel.h((GuiComponent)new PaddedComponent(0.0, 3.0, 0.0, 0.0, (GuiComponent)clickGuiFriendsFriendActionComponent2), new Object[0]);
            }
        }
    }

    private Boolean isOnlineSource() {
        return this.sourceMode == ClickGuiFriendSourceMode.ONLINE;
    }

    private void renderOnlinePlaceholder() {
        this.getMainContent().removeMarkedChildren();
        SimpleTextLabelComponent simpleTextLabelComponent = new SimpleTextLabelComponent("Under construction. Use frames GUI mode to use Vape friends for now.");
        simpleTextLabelComponent.setFontScale(0.8);
        simpleTextLabelComponent.setTextColor(ClickGuiFriendsPage.J.A);
        simpleTextLabelComponent.setOffsetX(0.0f);
        this.getMainContent().h((GuiComponent)new PaddedComponent(0.0, 12.0, 0.0, 0.0, (GuiComponent)simpleTextLabelComponent), new Object[]{"wrap"});
        this.updateMainChildWidths();
    }

    private void renderSidebar() {
        GuiComponent guiComponent = (GuiComponent)this.getSidebarHeader().f().get(0);
        this.getSidebarHeader().removeMarkedChildren();
        GlyphIconComponent glyphIconComponent = new GlyphIconComponent("newsettings", 6.0, 6.0, 10.0, 10.0, null, null, null);
        glyphIconComponent.setCenterVertically(true);
        glyphIconComponent.setCenterHorizontally(true);
        glyphIconComponent.setShowDisabledOverlay(true);
        glyphIconComponent.addClickListener(this::openThemeSettings);
        this.getSidebarHeader().h(guiComponent, new Object[0]);
        this.getSidebarHeader().h((GuiComponent)new SpacerComponent(this.getSidebarHeader().A() - this.getSidebarHeader().l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().C() - glyphIconComponent.A(), 0.0), new Object[0]);
        this.getSidebarHeader().h((GuiComponent)new PaddedComponent(4.0, 0.0, 0.0, 0.0, (GuiComponent)glyphIconComponent), new Object[0]);
        this.getSidebarContent().removeMarkedChildren();
        FriendModuleInteractiveComponent friendModuleInteractiveComponent = new FriendModuleInteractiveComponent("Minecraft Friends", null, this::isMinecraftSource, null, "expandarrow");
        friendModuleInteractiveComponent.addClickListener(this::selectMinecraftSource);
        FriendModuleInteractiveComponent friendModuleInteractiveComponent2 = new FriendModuleInteractiveComponent("VAPE Friends", null, this::isOnlineSource, ClickGuiFriendsPage::getOnlineSourceBadgeCount, "expandarrow");
        friendModuleInteractiveComponent2.addClickListener(this::selectOnlineSource);
        this.getSidebarContent().h((GuiComponent)new PaddedComponent(0.0, 3.0, 0.0, 0.0, (GuiComponent)friendModuleInteractiveComponent), new Object[]{"wrap"});
        this.getSidebarContent().h((GuiComponent)new PaddedComponent(0.0, 3.0, 0.0, 0.0, (GuiComponent)friendModuleInteractiveComponent2), new Object[]{"wrap"});
        PartyManager partyManager = Vape.INSTANCE.getOnlineManager().getPartyManager();
        boolean bl = partyManager.getCurrentParty() != null;
        LocalOnlineFriend localOnlineFriend = Vape.INSTANCE.getOnlineManager().getLocalFriend();
        OnlineStatus onlineStatus = localOnlineFriend != null ? localOnlineFriend.getStatus() : null;
        String string = this.firstNonBlank(localOnlineFriend != null ? localOnlineFriend.getDisplayName() : null, "You");
        String string2 = onlineStatus != null ? onlineStatus.getDisplayName() : "Offline";
        ClickGuiFriendsFriendStatusComponent clickGuiFriendsFriendStatusComponent = new ClickGuiFriendsFriendStatusComponent(string, string2, bl);
        clickGuiFriendsFriendStatusComponent.setHorizontalInset(0.0);
        clickGuiFriendsFriendStatusComponent.setStatusColor(onlineStatus != null ? onlineStatus.getColor() : null);
        clickGuiFriendsFriendStatusComponent.setStatusText(string2);
        clickGuiFriendsFriendStatusComponent.setPartyVisible(bl);
        clickGuiFriendsFriendStatusComponent.setClickListener(this::openOnlineSettings);
        this.getSidebarContent().h((GuiComponent)new SpacerComponent(0.0, this.getSidebarContent().L() - this.getSidebarContent().l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().y() - clickGuiFriendsFriendStatusComponent.L() - 1.0), new Object[]{"wrap"});
        this.updateSidebarChildWidths();
    }

    public ClickGuiFriendsPage(ClickGuiMainFrame clickGuiMainFrame, double d, double d2, double d3) {
        super(d, d2, d3, 0.0, "Friends");
        this.mainFrame = clickGuiMainFrame;
        this.renderSidebar();
        this.renderCurrentSource();
    }

    private void selectOnlineSource() {
        if (this.sourceMode != ClickGuiFriendSourceMode.ONLINE) {
            this.sourceMode = ClickGuiFriendSourceMode.ONLINE;
            this.renderCurrentSource();
        }
    }

    private static List<OnlineFriend> createOnlineStatusGroup(OnlineStatus onlineStatus) {
        return new ArrayList<OnlineFriend>();
    }

    private void resizeChildRows(PanelComponent panelComponent, double d) {
        for (GuiComponent guiComponent : panelComponent.f()) {
            if (guiComponent instanceof PaddedComponent) {
                ClickGuiFriendsFriendRequestComponent clickGuiFriendsFriendRequestComponent = (ClickGuiFriendsFriendRequestComponent)((PaddedComponent)guiComponent).t(ClickGuiFriendsFriendRequestComponent.class);
                if (clickGuiFriendsFriendRequestComponent != null) {
                    clickGuiFriendsFriendRequestComponent.o(d);
                    continue;
                }
                ClickGuiFriendsFriendActionComponent clickGuiFriendsFriendActionComponent = (ClickGuiFriendsFriendActionComponent)((PaddedComponent)guiComponent).t(ClickGuiFriendsFriendActionComponent.class);
                if (clickGuiFriendsFriendActionComponent != null) {
                    clickGuiFriendsFriendActionComponent.o(d);
                    continue;
                }
                SimpleTextLabelComponent simpleTextLabelComponent = (SimpleTextLabelComponent)((PaddedComponent)guiComponent).t(SimpleTextLabelComponent.class);
                if (simpleTextLabelComponent == null) continue;
                simpleTextLabelComponent.o(d);
                continue;
            }
            if (guiComponent instanceof ClickGuiFriendsFriendRequestComponent) {
                guiComponent.o(d);
                continue;
            }
            if (!(guiComponent instanceof ClickGuiFriendsFriendActionComponent)) continue;
            guiComponent.o(d);
        }
    }

    private void openFriendSettings(FriendEntry friendEntry) {
        this.mainFrame.showOverlay(ClickGuiOverlaySpec.builder().title(friendEntry.getName()).sidecarIcon("newsettings").placement(ClickGuiOverlayPlacement.DOCKED).initializeContent(panel -> this.populateFriendSettings(friendEntry, (PanelComponent)panel)).build());
    }

    private static int getOnlineStatusOrder(OnlineFriend onlineFriend) {
        OnlineStatus onlineStatus = onlineFriend.getStatus();
        return onlineStatus != null ? onlineStatus.ordinal() : Integer.MAX_VALUE;
    }

    private void removeFriend(FriendEntry friendEntry) {
        Vape.INSTANCE.getFriendManager().removeFriend(friendEntry);
        Vape.INSTANCE.getNotificationManager().showInfo("\u00a7cRemoved\u00a7r " + friendEntry.getName() + " " + NotificationText.localize("from friends"), "", 2000L);
        this.refreshSearchResults();
    }

    private static void restoreAlias(TrailingActionTextInputComponent trailingActionTextInputComponent, String[] stringArray) {
        trailingActionTextInputComponent.setText(stringArray[0]);
    }

    private void updateMainChildWidths() {
        double d = this.getMainContent().A();
        for (GuiComponent guiComponent : this.getMainContent().f()) {
            if (guiComponent instanceof PaddedComponent) {
                ClickGuiFriendsFriendRequestComponent clickGuiFriendsFriendRequestComponent = (ClickGuiFriendsFriendRequestComponent)((PaddedComponent)guiComponent).t(ClickGuiFriendsFriendRequestComponent.class);
                if (clickGuiFriendsFriendRequestComponent != null) {
                    clickGuiFriendsFriendRequestComponent.o(d);
                    continue;
                }
                ClickGuiFriendsFriendActionComponent clickGuiFriendsFriendActionComponent = (ClickGuiFriendsFriendActionComponent)((PaddedComponent)guiComponent).t(ClickGuiFriendsFriendActionComponent.class);
                if (clickGuiFriendsFriendActionComponent != null) {
                    clickGuiFriendsFriendActionComponent.o(d);
                    continue;
                }
                PartyMemberEntryComponent partyMemberEntryComponent = (PartyMemberEntryComponent)((PaddedComponent)guiComponent).t(PartyMemberEntryComponent.class);
                if (partyMemberEntryComponent != null) {
                    partyMemberEntryComponent.o(d);
                    continue;
                }
                ClickGuiFriendsFriendStatusComponent clickGuiFriendsFriendStatusComponent = (ClickGuiFriendsFriendStatusComponent)((PaddedComponent)guiComponent).t(ClickGuiFriendsFriendStatusComponent.class);
                if (clickGuiFriendsFriendStatusComponent != null) {
                    clickGuiFriendsFriendStatusComponent.o(Math.min(d, ClickGuiFriendsFriendStatusComponent.getPreferredWidth()));
                    continue;
                }
                ClickGuiFriendsFriendListComponent clickGuiFriendsFriendListComponent = (ClickGuiFriendsFriendListComponent)((PaddedComponent)guiComponent).t(ClickGuiFriendsFriendListComponent.class);
                if (clickGuiFriendsFriendListComponent != null) {
                    clickGuiFriendsFriendListComponent.o(d);
                    continue;
                }
                TrailingActionTextInputComponent trailingActionTextInputComponent = (TrailingActionTextInputComponent)((PaddedComponent)guiComponent).t(TrailingActionTextInputComponent.class);
                if (trailingActionTextInputComponent != null) {
                    trailingActionTextInputComponent.o(d);
                    continue;
                }
                LabeledTextInputComponent labeledTextInputComponent = (LabeledTextInputComponent)((PaddedComponent)guiComponent).t(LabeledTextInputComponent.class);
                if (labeledTextInputComponent == null) continue;
                labeledTextInputComponent.o(d - 0.0);
                continue;
            }
            if (guiComponent instanceof ClickGuiFriendsFriendRequestComponent) {
                guiComponent.o(d);
                continue;
            }
            if (guiComponent instanceof ClickGuiFriendsFriendActionComponent) {
                guiComponent.o(d);
                continue;
            }
            if (guiComponent instanceof PartyMemberEntryComponent) {
                guiComponent.o(d);
                continue;
            }
            if (guiComponent instanceof ClickGuiFriendsFriendStatusComponent) {
                guiComponent.o(Math.min(d, ClickGuiFriendsFriendStatusComponent.getPreferredWidth()));
                continue;
            }
            if (guiComponent instanceof ClickGuiFriendsFriendListComponent) {
                guiComponent.o(d);
                continue;
            }
            if (guiComponent instanceof TrailingActionTextInputComponent) {
                guiComponent.o(d);
                continue;
            }
            if (guiComponent instanceof LabeledTextInputComponent) {
                guiComponent.o(d - 0.0);
                continue;
            }
            if (!(guiComponent instanceof PanelComponent)) continue;
            this.resizeChildRows((PanelComponent)guiComponent, d);
        }
    }

    private static Integer getOnlineSourceBadgeCount() {
        return 0;
    }

    private void removeFriendByName(String string) {
        FriendEntry friendEntry = Vape.INSTANCE.getFriendManager().findTargetedFriend(string);
        if (friendEntry != null) {
            Vape.INSTANCE.getFriendManager().removeFriend(friendEntry);
            Vape.INSTANCE.getNotificationManager().showInfo("\u00a7cRemoved\u00a7r " + string + " " + NotificationText.localize("from friends"), "", 2000L);
            this.refreshSearchResults();
        }
    }

    private void populateFriends(String string) {
        boolean bl = !string.isEmpty();
        boolean bl2 = bl;
        if (bl) {
            for (FriendEntry friendEntry : Vape.INSTANCE.getFriendManager().getFriends()) {
                String friendName = friendEntry.getName();
                String string2 = friendEntry.getDisplayName();
                boolean bl4 = friendName != null && friendName.toLowerCase().contains(string);
                boolean bl3 = string2 != null && string2.toLowerCase().contains(string);
                boolean bl5 = bl3;
                if (!bl4 && !bl3) continue;
                ClickGuiFriendsFriendRequestComponent friendComponent = new ClickGuiFriendsFriendRequestComponent(friendEntry);
                friendComponent.o(this.contentPanel.A());
                friendComponent.setRemovePrimaryClickListener(() -> this.removeFriend(friendEntry));
                friendComponent.setSettingsPrimaryClickListener(() -> this.openFriendSettings(friendEntry));
                this.friendsPanel.h((GuiComponent)new PaddedComponent(0.0, 3.0, 0.0, 0.0, (GuiComponent)friendComponent), new Object[0]);
            }
            if (this.friendsPanel.f().isEmpty()) {
                // empty if block
            }
            return;
        }
        for (FriendEntry friendEntry : Vape.INSTANCE.getFriendManager().getFriends()) {
            ClickGuiFriendsFriendRequestComponent clickGuiFriendsFriendRequestComponent = new ClickGuiFriendsFriendRequestComponent(friendEntry);
            clickGuiFriendsFriendRequestComponent.o(this.contentPanel.A());
            clickGuiFriendsFriendRequestComponent.setRemovePrimaryClickListener(() -> this.removeFriend(friendEntry));
            clickGuiFriendsFriendRequestComponent.setSettingsPrimaryClickListener(() -> this.openFriendSettings(friendEntry));
            this.friendsPanel.h((GuiComponent)new PaddedComponent(0.0, 3.0, 0.0, 0.0, (GuiComponent)clickGuiFriendsFriendRequestComponent), new Object[0]);
        }
        if (this.friendsPanel.f().isEmpty()) {
            MultilineTextBlockComponent multilineTextBlockComponent = new MultilineTextBlockComponent("INFO", "Enter a username in the search bar to add a friend.");
            multilineTextBlockComponent.setWidth(this.contentPanel.A());
            this.friendsPanel.h((GuiComponent)new PaddedComponent(3.0, 3.0, 0.0, 0.0, (GuiComponent)multilineTextBlockComponent), new Object[0]);
        }
    }

    private void addFriendByName(String string) {
        Vape.INSTANCE.getFriendManager().addFriend((FriendEntry)new Friend(string, string));
        Vape.INSTANCE.getNotificationManager().showInfo("\u00a7aAdded\u00a7r " + string + " " + NotificationText.localize("to friends"), "", 2000L);
        this.refreshSearchResults();
    }
    private void renderCurrentSource() {
        switch (ClickGuiFriendSourceModeSwitchMap.Z[this.sourceMode.ordinal()]) {
            case 1: {
                this.renderOnlinePlaceholder();
                break;
            }
            default: {
                this.renderMinecraftFriendsContent();
            }
        }
    }

    private void selectMinecraftSource() {
        if (this.sourceMode != ClickGuiFriendSourceMode.MINECRAFT) {
            this.sourceMode = ClickGuiFriendSourceMode.MINECRAFT;
            this.renderCurrentSource();
        }
    }

    private void createFriendsSection(String string) {
        this.contentPanel.h((GuiComponent)new PaddedComponent(0.0, 3.0, 0.0, 0.0, (GuiComponent)this.createSectionLabel("Your Friends")), new Object[]{"wrap"});
        this.friendsPanel = new PanelComponent(this.contentPanel.A(), 0.0);
        this.friendsPanel.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("wrap");
        this.friendsPanel.setShowDisabledOverlay(false);
        this.friendsPanel.F(FrameScrollbarPlacement.OUTSIDE);
        this.populateFriends(string);
        this.contentPanel.h((GuiComponent)this.friendsPanel, new Object[0]);
    }

    private void updatePanelHeights() {
        if (this.contentPanel == null || this.friendsPanel == null) {
            return;
        }
        double d = this.contentPanel.L();
        boolean bl = this.searchResultsPanel != null;
        boolean bl2 = bl;
        if (bl) {
            double d2 = Math.max(0.0, d - 22.0);
            double d3 = Math.min(this.searchResultsPanel.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().y() + 1.0, d2 * 0.5);
            this.searchResultsPanel.setExplicitHeight(d3);
            this.searchResultsPanel.Y(d3);
            this.searchResultsPanel.t(d3);
            double d4 = d2 - d3 - 6.0;
            this.friendsPanel.setExplicitHeight(d4);
            this.friendsPanel.Y(d4);
            this.friendsPanel.t(d4);
        } else {
            double d5 = 11.0;
            double d6 = Math.max(0.0, d - 11.0);
            this.friendsPanel.setExplicitHeight(d6);
            this.friendsPanel.Y(d6);
            this.friendsPanel.t(d6);
        }
    }

    private void renderOnlineFriendsContent() {
        OnlineStatus iterator;
        Object object;
        int onlineFriend = 0;
        this.getMainContent().removeMarkedChildren();
        PartyManager partyManager = Vape.INSTANCE.getOnlineManager().getPartyManager();
        Collection<PartyInvite> collection = partyManager.getInvites();
        boolean bl = partyManager.getCurrentParty() != null;
        this.getMainContent().h((GuiComponent)new PaddedComponent(0.0, 3.0, 0.0, 0.0, (GuiComponent)this.createSectionLabel("Party")), new Object[]{"wrap"});
        boolean bl2 = false;
        if (bl) {
            PartyMemberEntryComponent partyMemberEntryComponent = new PartyMemberEntryComponent(() -> ((PartyManager)partyManager).getCurrentParty());
            partyMemberEntryComponent.o(this.getMainContent().A());
            this.getMainContent().h((GuiComponent)new PaddedComponent(0.0, 3.0, 0.0, 0.0, (GuiComponent)partyMemberEntryComponent), new Object[0]);
            bl2 = true;
        }
        for (PartyInvite partyInvite : collection) {
            object = new PartyMemberEntryComponent(partyInvite);
            ((GuiComponent)object).o(this.getMainContent().A());
            this.getMainContent().h((GuiComponent)new PaddedComponent(0.0, 3.0, 0.0, 0.0, (GuiComponent)object), new Object[0]);
            bl2 = true;
        }
        if (!bl2) {
            this.getMainContent().h((GuiComponent)new PaddedComponent(0.0, 6.0, 0.0, 0.0, (GuiComponent)this.createInfoLabel("No active parties yet.")), new Object[]{"wrap"});
        } else {
            this.getMainContent().h((GuiComponent)new SpacerComponent(0.0, 6.0), new Object[0]);
        }
        ArrayList<OnlineFriend> arrayList = new ArrayList<OnlineFriend>(Vape.INSTANCE.getOnlineFriendManager().getFriends());
        arrayList.sort(Comparator.comparingInt(ClickGuiFriendsPage::getOnlineStatusOrder).thenComparing(ClickGuiFriendsPage::getOnlineFriendName, String.CASE_INSENSITIVE_ORDER));
        int n = arrayList.size();
        object = n > 0 ? "VAPE Friends (" + n + ")" : "VAPE Friends";
        this.getMainContent().h((GuiComponent)new PaddedComponent(0.0, 3.0, 0.0, 0.0, (GuiComponent)this.createSectionLabel((String)object)), new Object[]{"wrap"});
        if (arrayList.isEmpty()) {
            this.getMainContent().h((GuiComponent)new PaddedComponent(0.0, 0.0, 0.0, 0.0, (GuiComponent)this.createInfoLabel("No VAPE friends online yet.")), new Object[]{"wrap"});
            this.updateMainChildWidths();
            return;
        }
        EnumMap<OnlineStatus, List<OnlineFriend>> enumMap = new EnumMap<OnlineStatus, List<OnlineFriend>>(OnlineStatus.class);
        OnlineStatus[] onlineStatuses = OnlineStatus.values();
        int n2 = onlineStatuses.length;
        boolean bl3 = false;
        while (onlineFriend < n2) {
            iterator = onlineStatuses[onlineFriend];
            enumMap.put(iterator, new ArrayList<OnlineFriend>());
            ++onlineFriend;
        }
        ArrayList<OnlineFriend> uncategorizedFriends = new ArrayList<OnlineFriend>();
        for (OnlineFriend onlineFriend2 : arrayList) {
            iterator = onlineFriend2.getStatus();
            if (iterator == null) {
                uncategorizedFriends.add(onlineFriend2);
                continue;
            }
            enumMap.computeIfAbsent(iterator, ClickGuiFriendsPage::createOnlineStatusGroup).add(onlineFriend2);
        }
        boolean bl32 = false;
        for (OnlineStatus onlineStatus : OnlineStatus.values()) {
            List<OnlineFriend> list = enumMap.getOrDefault(onlineStatus, Collections.emptyList());
            if (list.isEmpty()) continue;
            if (bl32) {
                this.getMainContent().h((GuiComponent)new SpacerComponent(0.0, 4.0), new Object[0]);
            }
            SimpleTextLabelComponent simpleTextLabelComponent = this.createWrappedInfoLabel(onlineStatus.getDisplayName());
            this.getMainContent().h((GuiComponent)new PaddedComponent(0.0, 3.0, 0.0, 0.0, (GuiComponent)simpleTextLabelComponent), new Object[]{"wrap"});
            for (OnlineFriend onlineFriend2 : list) {
                ClickGuiFriendsFriendListComponent clickGuiFriendsFriendListComponent = new ClickGuiFriendsFriendListComponent(onlineFriend2);
                clickGuiFriendsFriendListComponent.setChatActionVisible(onlineStatus != OnlineStatus.OFFLINE);
                clickGuiFriendsFriendListComponent.o(this.getMainContent().A());
                clickGuiFriendsFriendListComponent.setSettingsClickListener(() -> this.openOnlineFriend(onlineFriend2));
                this.getMainContent().h((GuiComponent)new PaddedComponent(0.0, 3.0, 0.0, 0.0, (GuiComponent)clickGuiFriendsFriendListComponent), new Object[]{"wrap"});
            }
            bl32 = true;
        }
        if (!uncategorizedFriends.isEmpty()) {
            this.getMainContent().h((GuiComponent)new SpacerComponent(0.0, 4.0), new Object[0]);
            SimpleTextLabelComponent simpleTextLabelComponent = this.createWrappedInfoLabel("Other");
            this.getMainContent().h((GuiComponent)new PaddedComponent(0.0, 3.0, 0.0, 0.0, (GuiComponent)simpleTextLabelComponent), new Object[]{"wrap"});
            for (OnlineFriend onlineFriend3 : uncategorizedFriends) {
                ClickGuiFriendsFriendListComponent object2 = new ClickGuiFriendsFriendListComponent(onlineFriend3);
                object2.setChatActionVisible(false);
                ((GuiComponent)object2).o(this.getMainContent().A());
                object2.setSettingsClickListener(() -> this.openOnlineFriend(onlineFriend3));
                this.getMainContent().h((GuiComponent)new PaddedComponent(0.0, 3.0, 0.0, 0.0, (GuiComponent)object2), new Object[]{"wrap"});
            }
        }
        this.updateMainChildWidths();
    }

    private void openOnlineFriend(OnlineFriend onlineFriend) {
        this.mainFrame.showOverlay(ClickGuiFriendsFriendCardFactory.createOverlay((OnlineFriend)onlineFriend));
    }

    private void populateFriendSettings(FriendEntry friendEntry, PanelComponent panelComponent) {
        SimpleTextLabelComponent simpleTextLabelComponent = new SimpleTextLabelComponent("Alias");
        simpleTextLabelComponent.setFontScale(0.7);
        simpleTextLabelComponent.setTextColor(ClickGuiFriendsPage.J.A);
        simpleTextLabelComponent.setBold(true);
        simpleTextLabelComponent.setOffsetX(0.0f);
        simpleTextLabelComponent.setExtraHeight(2);
        panelComponent.h((GuiComponent)new PaddedComponent(8.0, 0.0, 8.0, 8.0, (GuiComponent)simpleTextLabelComponent), new Object[0]);
        String[] stringArray = new String[]{friendEntry.getAlias() != null && !friendEntry.getAlias().equals(friendEntry.getName()) ? friendEntry.getAlias() : ""};
        TextButton textButton = new TextButton("Y", 0.7, ClickGuiFriendsPage.J.B, ClickGuiFriendsPage.J.O, null, 2.0f, 1.0f, 12.0, 13.0);
        textButton.setUppercase(true);
        textButton.setUseAlternateFont(true);
        textButton.setDeriveTextColorFromBackground(false);
        textButton.setNormalTextColor(ClickGuiFriendsPage.J.A);
        textButton.setVisible(false);
        TextButton textButton2 = new TextButton("N", 0.7, ClickGuiFriendsPage.J.d, ClickGuiFriendsPage.J.c, null, 2.0f, 1.0f, 12.0, 13.0);
        textButton2.setUppercase(true);
        textButton2.setUseAlternateFont(true);
        textButton2.setDeriveTextColorFromBackground(false);
        textButton2.setNormalTextColor(ClickGuiFriendsPage.J.A);
        textButton2.setVisible(false);
        ArrayList<TextButton> arrayList = new ArrayList<TextButton>();
        arrayList.add(textButton2);
        arrayList.add(textButton);
        FriendAliasEditInputComponent friendAliasEditInputComponent = new FriendAliasEditInputComponent(this, "Set alias...", arrayList, stringArray, textButton, textButton2, friendEntry);
        friendAliasEditInputComponent.setUseExplicitWidth(true);
        friendAliasEditInputComponent.setUseExplicitHeight(true);
        friendAliasEditInputComponent.o(20.0);
        friendAliasEditInputComponent.Y(20.0);
        friendAliasEditInputComponent.setBorderThickness(0.75f);
        friendAliasEditInputComponent.setCornerRadius(4.0f);
        friendAliasEditInputComponent.setBorderAnimation(ColorAnimation.Y((Color)ClickGuiFriendsPage.J.s));
        friendAliasEditInputComponent.setBackgroundColorOrNull(null);
        friendAliasEditInputComponent.P(stringArray[0]);
        textButton.addClickListener(() -> this.saveFriendAlias(friendEntry, (TrailingActionTextInputComponent)friendAliasEditInputComponent, stringArray, textButton, textButton2));
        textButton2.addClickListener(() -> ClickGuiFriendsPage.restoreAlias((TrailingActionTextInputComponent)friendAliasEditInputComponent, stringArray));
        panelComponent.h((GuiComponent)new PaddedComponent(0.0, 2.0, 0.0, 0.0, (GuiComponent)friendAliasEditInputComponent), new Object[0]);
        ClickGuiFriendsModeToggleComponent clickGuiFriendsModeToggleComponent = new ClickGuiFriendsModeToggleComponent("Active", 0.8, friendEntry);
        clickGuiFriendsModeToggleComponent.o(panelComponent.A() - 16.0);
        clickGuiFriendsModeToggleComponent.setHorizontalInset(0.0);
        clickGuiFriendsModeToggleComponent.setShowDisabledOverlay(false);
        clickGuiFriendsModeToggleComponent.synchronizeAnimationsImmediately();
        panelComponent.h((GuiComponent)new PaddedComponent(0.0, 0.0, 5.0, 5.0, (GuiComponent)clickGuiFriendsModeToggleComponent), new Object[0]);
    }

    private void saveFriendAlias(FriendEntry friendEntry, TrailingActionTextInputComponent trailingActionTextInputComponent, String[] stringArray, TextButton textButton, TextButton textButton2) {
        this.updateFriendAlias(friendEntry, trailingActionTextInputComponent.getText());
        stringArray[0] = trailingActionTextInputComponent.getText();
        textButton.setVisible(false);
        textButton2.setVisible(false);
    }

    private void createSearchResultsSection(String string) {
        boolean bl = !string.isEmpty();
        boolean bl2 = bl;
        if (!bl) {
            return;
        }
        this.searchResultsHeader = new PaddedComponent(0.0, 3.0, 0.0, 0.0, (GuiComponent)this.createSectionLabel("Results"));
        this.contentPanel.h(this.searchResultsHeader, new Object[]{"wrap"});
        this.searchResultsPanel = new PanelComponent(this.contentPanel.A(), 30.0);
        this.searchResultsPanel.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("wrap");
        this.searchResultsPanel.setShowDisabledOverlay(false);
        this.searchResultsPanel.F(FrameScrollbarPlacement.OUTSIDE);
        this.contentPanel.h((GuiComponent)this.searchResultsPanel, new Object[0]);
        this.contentPanel.h((GuiComponent)new SpacerComponent(0.0, 6.0), new Object[0]);
    }

    public static void refreshSearchResults(ClickGuiFriendsPage page) {
        page.refreshSearchResults();
    }

    private SimpleTextLabelComponent createWrappedInfoLabel(String string) {
        SimpleTextLabelComponent simpleTextLabelComponent = new SimpleTextLabelComponent(string);
        simpleTextLabelComponent.setFontScale(0.625);
        simpleTextLabelComponent.setTextColor(ClickGuiFriendsPage.J.C);
        simpleTextLabelComponent.setBold(true);
        simpleTextLabelComponent.setOffsetX(0.0f);
        simpleTextLabelComponent.setExtraHeight(2);
        return simpleTextLabelComponent;
    }

    private void renderMinecraftFriendsContent() {
        this.getMainContent().removeMarkedChildren();
        this.searchInput = new ClickGuiFriendsNameInputListener(this, "Search friends...");
        this.searchInput.o(this.getMainContent().A());
        this.searchInput.setLeftInset(0.0f);
        this.searchInput.setHorizontalInset(0.0);
        this.searchInput.setRightInset(0.0f);
        this.searchInput.setVerticalInset(0.0f);
        this.searchInput.setUseExplicitHeight(true);
        this.searchInput.setSearchIconTrailing(false);
        this.searchInput.Y(16.0);
        this.searchInput.setBorderThickness(0.75f);
        this.searchInput.setCornerRadius(4.0f);
        this.searchInput.setBorderAnimation(ColorAnimation.Y((Color)ClickGuiFriendsPage.J.s));
        this.searchInput.setBackgroundColorOrNull(null);
        this.getMainContent().h((GuiComponent)this.searchInput, new Object[0]);
        if (this.searchQuery != null && !this.searchQuery.isEmpty()) {
            this.searchInput.setText(this.searchQuery);
        }
        this.getMainContent().h((GuiComponent)new SpacerComponent(0.0, 6.0), new Object[0]);
        String string = this.searchInput != null ? this.searchInput.getText() : "";
        String string2 = string == null ? "" : string.trim().toLowerCase();
        double d = this.getMainContent().L() - this.getMainContent().l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().y() - 1.0;
        if (d < 0.0) {
            d = 0.0;
        }
        this.contentPanel = new PanelComponent(this.getMainContent().A(), d);
        this.contentPanel.N(false);
        this.contentPanel.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("wrap");
        this.contentPanel.setShowDisabledOverlay(false);
        this.contentPanel.t(this.contentPanel.L());
        this.contentPanel.F(FrameScrollbarPlacement.OUTSIDE);
        this.createSearchResultsSection(string2);
        this.createFriendsSection(string2);
        this.updatePanelHeights();
        this.getMainContent().h((GuiComponent)this.contentPanel, new Object[0]);
        this.updateMainChildWidths();
    }

    private void openOnlineSettings() {
        this.mainFrame.showOverlay(ClickGuiFriendsThemeConfigFactory.createOverlay());
    }

    private SimpleTextLabelComponent createInfoLabel(String string) {
        SimpleTextLabelComponent simpleTextLabelComponent = new SimpleTextLabelComponent(string);
        simpleTextLabelComponent.setFontScale(0.625);
        simpleTextLabelComponent.setTextColor(ClickGuiFriendsPage.J.C);
        simpleTextLabelComponent.setOffsetX(0.0f);
        simpleTextLabelComponent.setExtraHeight(2);
        return simpleTextLabelComponent;
    }

    private String firstNonBlank(String string, String string2) {
        if (string == null || string.trim().isEmpty()) {
            return string2;
        }
        return string;
    }

    private void refreshSearchResults() {
        if (this.contentPanel == null) {
            return;
        }
        String string = this.searchInput != null ? this.searchInput.getText() : "";
        String string2 = string == null ? "" : string.trim().toLowerCase();
        boolean bl2 = !string2.isEmpty();
        boolean bl3 = bl2;
        if (bl2) {
            boolean bl4 = this.searchResultsPanel == null;
            boolean bl5 = false;
            if (bl4) {
                this.contentPanel.removeMarkedChildren();
                this.createSearchResultsSection(string2);
                this.populateSearchResults(string2);
                this.createFriendsSection(string2);
                this.updatePanelHeights();
                this.contentPanel.H(true);
                this.updateMainChildWidths();
                return;
            }
            if (this.searchResultsPanel != null) {
                this.populateSearchResults(string2);
                this.searchResultsPanel.H(true);
            }
            if (this.friendsPanel != null) {
                this.friendsPanel.removeMarkedChildren();
                this.populateFriends(string2);
                this.friendsPanel.H(true);
            }
            this.contentPanel.H(true);
            this.updatePanelHeights();
            this.updateMainChildWidths();
            return;
        }
        boolean bl6 = false;
        boolean bl = this.searchResultsPanel != null;
        boolean bl7 = bl;
        if (bl) {
            this.contentPanel.removeMarkedChildren();
            this.searchResultsPanel = null;
            this.searchResultsHeader = null;
            this.createFriendsSection(string2);
            this.updatePanelHeights();
            this.contentPanel.H(true);
            this.updateMainChildWidths();
            return;
        }
        if (this.searchResultsPanel != null) {
            // empty if block
        }
        if (this.friendsPanel != null) {
            this.friendsPanel.removeMarkedChildren();
            this.populateFriends(string2);
            this.friendsPanel.H(true);
        }
        this.contentPanel.H(true);
        this.updatePanelHeights();
        this.updateMainChildWidths();
    }

    private void updateFriendAlias(FriendEntry friendEntry, String string) {
        if (friendEntry instanceof Friend) {
            Friend friend = (Friend)friendEntry;
            String string2 = string == null ? "" : string.trim();
            String string3 = string2;
            if (string2.isEmpty()) {
                string2 = friend.getName();
            }
            friend.setAlias(string2);
        }
        Vape.INSTANCE.getFriendManager().refreshPlayerNames();
        OnlineFriendUiHelper.refreshMinecraftFriends();
    }

    public static String setSearchQuery(ClickGuiFriendsPage page, String string) {
        page.searchQuery = string;
        return page.searchQuery;
    }

    private Boolean isMinecraftSource() {
        return this.sourceMode == ClickGuiFriendSourceMode.MINECRAFT;
    }

    private void updateSidebarChildWidths() {
        double d = this.getSidebarContent().A();
        for (GuiComponent guiComponent : this.getSidebarContent().f()) {
            if (guiComponent instanceof PaddedComponent) {
                FriendModuleInteractiveComponent friendModuleInteractiveComponent = (FriendModuleInteractiveComponent)((PaddedComponent)guiComponent).t(FriendModuleInteractiveComponent.class);
                if (friendModuleInteractiveComponent == null) continue;
                friendModuleInteractiveComponent.o(d);
                continue;
            }
            if (!(guiComponent instanceof FriendModuleInteractiveComponent)) continue;
            guiComponent.o(d);
        }
    }

    private static String getOnlineFriendName(OnlineFriend onlineFriend) {
        String string = onlineFriend.getDisplayName();
        return string == null ? "" : string;
    }

    public static void updateFriendAlias(ClickGuiFriendsPage page, FriendEntry friendEntry, String string) {
        page.updateFriendAlias(friendEntry, string);
    }

    private void openThemeSettings() {
        this.mainFrame.showOverlay(ClickGuiThemeOverlayFactory.m((ClickGuiMainFrame)this.mainFrame));
    }

    private SimpleTextLabelComponent createSectionLabel(String string) {
        SimpleTextLabelComponent simpleTextLabelComponent = new SimpleTextLabelComponent(string);
        simpleTextLabelComponent.setFontScale(0.75);
        simpleTextLabelComponent.setTextColor(ClickGuiFriendsPage.J.A);
        simpleTextLabelComponent.setBold(true);
        simpleTextLabelComponent.setOffsetX(0.0f);
        simpleTextLabelComponent.setExtraHeight(2);
        return simpleTextLabelComponent;
    }
}
