package gg.vape.manager.client;

import gg.vape.Vape;
import gg.vape.account.AccountEntitlements;
import gg.vape.api.ApiAccessTokenProvider;
import gg.vape.config.PublicProfileUser;
import gg.vape.friend.FriendModel;
import gg.vape.friend.FriendRequestModel;
import gg.vape.friend.IncomingFriendRequest;
import gg.vape.friend.LocalOnlineFriend;
import gg.vape.friend.OnlineFriend;
import gg.vape.friend.OnlineStatus;
import gg.vape.friend.OutgoingFriendRequest;
import gg.vape.friend.PartyInvite;
import gg.vape.friend.PartyState;
import gg.vape.friend.ui.OnlineFriendUiHelper;
import gg.vape.friend.ui.OnlineFriendsFrame;
import gg.vape.friend.ui.PartyMemberRow;
import gg.vape.friend.ui.PartyMemberTextStatusComponent;
import gg.vape.module.none.ClientSettings;
import gg.vape.notification.NotificationText;
import gg.vape.notification.NotificationType;
import gg.vape.protocol.ZeusClient;
import gg.vape.protocol.ZeusConnectionManager;
import gg.vape.protocol.event.FriendChatMessageEvent;
import gg.vape.protocol.event.FriendMinecraftProfileUpdateEvent;
import gg.vape.protocol.event.FriendModelUpdateEvent;
import gg.vape.protocol.event.FriendPresenceStateEvent;
import gg.vape.protocol.event.FriendRemovedEvent;
import gg.vape.protocol.event.FriendRequestEvent;
import gg.vape.protocol.event.FriendRequestReceivedEvent;
import gg.vape.protocol.event.FriendRequestRemovedEvent;
import gg.vape.protocol.event.FriendRequestSentEvent;
import gg.vape.protocol.event.FriendServerAddressEvent;
import gg.vape.protocol.event.FriendVisibilityUpdateEvent;
import gg.vape.protocol.event.GroupChatMessageEvent;
import gg.vape.protocol.event.GroupCreatedEvent;
import gg.vape.protocol.event.GroupDeletedEvent;
import gg.vape.protocol.event.GroupInviteAcceptedEvent;
import gg.vape.protocol.event.GroupInviteSentEvent;
import gg.vape.protocol.event.GroupLeftEvent;
import gg.vape.protocol.event.GroupOptionUpdatedEvent;
import gg.vape.protocol.event.InitialOnlineFriendStateEvent;
import gg.vape.protocol.event.OnlineEventDispatcher;
import gg.vape.protocol.event.PartyInviteReceivedEvent;
import gg.vape.protocol.event.PartyInviteRemovedEvent;
import gg.vape.protocol.event.PartyLeaderChangedEvent;
import gg.vape.protocol.event.PartyMemberAction;
import gg.vape.protocol.event.PartyMemberUpdateEvent;
import gg.vape.protocol.event.UserDisplayNameChangedEvent;
import gg.vape.protocol.packet.AuthenticationResponsePacket;
import gg.vape.ui.click.frame.impl.online.OnlineConnectionSettingsFrame;
import gg.vape.utils.TimerUtil;
import gg.vape.value.Value;
import io.netty.channel.Channel;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

public class OnlineConnectionManager {
    private long nextReconnectAt = -1L;
    private boolean hasConnectedSuccessfully = false;
    public static final OnlineConnectionManager INSTANCE;
    private OnlineAccountState accountState;
    private final TimerUtil friendRequestNotificationTimer;
    private static final String JOINED_PARTY_SUFFIX;
    private boolean initializationStarted = false;
    private boolean manualDisconnectRequested = false;
    private boolean listenersRegistered = false;
    private OnlineConnectionState connectionState = OnlineConnectionState.OFFLINE;
    @Nullable
    private Thread connectionThread;
    private final OnlineSettings settings;
    private final GlobalSettingsController globalSettingsController;
    @Nullable
    private OnlineDisconnectReason disconnectReason;
    private int reconnectAttemptCount;

    private static void updateConnectionStateUi(OnlineConnectionState onlineConnectionState) {
        OnlineConnectionSettingsFrame.updateConnectionStateIfCreated(onlineConnectionState);
    }

    private static void handleFriendRequestReceived(FriendRequestReceivedEvent friendRequestReceivedEvent) {
        Vape.INSTANCE.getOnlineManager().getFriendRequestManager().addRequest(new IncomingFriendRequest(friendRequestReceivedEvent.q()));
    }

    public void setManualDisconnectRequested(boolean manualDisconnectRequested) {
        this.manualDisconnectRequested = manualDisconnectRequested;
    }

    public OnlineAccountState getAccountState() {
        return this.accountState;
    }

    private static void handlePartyLeaderChanged(PartyLeaderChangedEvent partyLeaderChangedEvent) {
        PartyState partyState = Vape.INSTANCE.getOnlineManager().getPartyManager().getCurrentParty();
        if (partyState == null) {
            return;
        }
        OnlineFriend onlineFriend = partyState.findMember(partyLeaderChangedEvent.z());
        if (onlineFriend == null) {
            return;
        }
        partyState.setLeader(onlineFriend);
    }

    private void handleDisconnected(AtomicReference<Thread> connectionThreadReference) {
        OnlineDisconnectReason onlineDisconnectReason = this.disconnectReason;
        OnlineConnectionState onlineConnectionState = this.connectionState;
        if (onlineConnectionState == OnlineConnectionState.OUTDATED_CLIENT) {
            return;
        }
        if (onlineConnectionState != OnlineConnectionState.OUTDATED_SERVER) {
            this.setConnectionState(OnlineConnectionState.OFFLINE);
        }
        if (onlineDisconnectReason == OnlineDisconnectReason.LOGGED_IN_FROM_ANOTHER_LOCATION) {
            ClientSettings.getFrame(OnlineFriendsFrame.class).closeRegistrationIfOpen();
            return;
        }
        if (onlineDisconnectReason == OnlineDisconnectReason.BANNED) {
            this.setAccountState(OnlineAccountState.BANNED);
            return;
        }
        if (!this.manualDisconnectRequested && (onlineDisconnectReason == null || onlineDisconnectReason.allowsAutomaticReconnect())) {
            int reconnectDelaySeconds;
            if ((reconnectDelaySeconds = 5 * ++this.reconnectAttemptCount) > 30 || onlineConnectionState == OnlineConnectionState.OUTDATED_SERVER) {
                reconnectDelaySeconds = 30;
            }
            try {
                int reconnectDelayMillis = reconnectDelaySeconds * 1000;
                this.nextReconnectAt = System.currentTimeMillis() + reconnectDelayMillis;
                Thread.sleep(reconnectDelayMillis);
            }
            catch (InterruptedException interruptedException) {
                this.nextReconnectAt = -1L;
                return;
            }
            if (this.connectionThread == connectionThreadReference.get()) {
                this.connect();
            }
            this.nextReconnectAt = -1L;
        }
    }

    private static void updateAccountStateUi(OnlineAccountState onlineAccountState, OnlineConnectionState onlineConnectionState) {
        OnlineConnectionSettingsFrame.updateAccountStateIfCreated(onlineAccountState, onlineConnectionState);
    }

    private static OnlineFriend createFriendFromRequestEvent(FriendRequestEvent friendRequestEvent) {
        return new OnlineFriend(friendRequestEvent.f());
    }

    public OnlineConnectionState getConnectionState() {
        return this.connectionState;
    }

    private static void forwardAcceptedParty(Consumer<PartyState> partyConsumer, GroupInviteAcceptedEvent groupInviteAcceptedEvent) {
        partyConsumer.accept(groupInviteAcceptedEvent.P());
    }

    private static void handleFriendServerAddress(FriendServerAddressEvent friendServerAddressEvent) {
        OnlineFriend onlineFriend = Vape.INSTANCE.getOnlineManager().getFriendCache().getFriend(friendServerAddressEvent.a());
        if (onlineFriend != null) {
            onlineFriend.setMinecraftServer(friendServerAddressEvent.Z());
        }
    }

    public void cancelConnectionAttempt() {
        this.disconnect();
        this.nextReconnectAt = -1L;
        if (this.connectionThread != null) {
            try {
                this.connectionThread.interrupt();
                this.connectionThread = null;
            }
            catch (Throwable throwable) {
                Vape.logThrowable(throwable);
            }
        }
        this.setConnectionState(OnlineConnectionState.OFFLINE);
    }

    private static void handleGroupDeleted(Runnable clearPartyState, GroupDeletedEvent groupDeletedEvent) {
        clearPartyState.run();
    }

    private static void handleGroupInviteSent(GroupInviteSentEvent groupInviteSentEvent) {
        PartyState partyState = Vape.INSTANCE.getOnlineManager().getPartyManager().getCurrentParty();
        if (partyState == null) {
            return;
        }
        partyState.addInvitedUser(groupInviteSentEvent.n());
    }

    private void authenticate() {
        String accessToken = ApiAccessTokenProvider.getAccessToken();
        ZeusConnectionManager.T().u().J(accessToken, this::handleAuthenticationSuccess);
    }

    @Nullable
    public OnlineDisconnectReason getDisconnectReason() {
        return this.disconnectReason;
    }

    private static void handlePartyInviteReceived(PartyInviteReceivedEvent partyInviteReceivedEvent) {
        PartyInvite partyInvite = new PartyInvite(Vape.INSTANCE.getOnlineManager().getFriendCache().getOrCreateFriend(partyInviteReceivedEvent.R().getId(), () -> OnlineConnectionManager.createFriendFromPartyInvite(partyInviteReceivedEvent)));
        Vape.INSTANCE.getOnlineManager().getPartyManager().addInvite(partyInvite);
    }

    private static void handleGroupOptionUpdated(GroupOptionUpdatedEvent groupOptionUpdatedEvent) {
        PartyState partyState = Vape.INSTANCE.getOnlineManager().getPartyManager().getCurrentParty();
        if (partyState == null) {
            return;
        }
        Value<?, ?> value = partyState.getOptions().get((Object)groupOptionUpdatedEvent.j());
        if (value != null) {
            ((Value)value).setValue(groupOptionUpdatedEvent.U());
        }
    }

    private static OnlineFriend createFriendFromModelUpdate(FriendModelUpdateEvent friendModelUpdateEvent) {
        return new OnlineFriend(friendModelUpdateEvent.q());
    }

    public boolean isCurrentAccountUser(long userId) {
        long currentUserId = Vape.INSTANCE.getAccountInfo().getUserId();
        return currentUserId != -1L && currentUserId == userId;
    }

    private static OnlineFriend createFriendFromPartyInvite(PartyInviteReceivedEvent partyInviteReceivedEvent) {
        return new OnlineFriend(partyInviteReceivedEvent.R());
    }

    public void setAccountState(OnlineAccountState onlineAccountState) {
        this.accountState = onlineAccountState;
        OnlineConnectionState onlineConnectionState = this.connectionState;
        ClientSettings.UI_EXECUTOR.execute(() -> OnlineConnectionManager.updateAccountStateUi(onlineAccountState, onlineConnectionState));
    }

    private static void handleGroupChatMessage(GroupChatMessageEvent groupChatMessageEvent) {
        OnlineFriend onlineFriend = Vape.INSTANCE.getOnlineFriendManager().getByUser(groupChatMessageEvent.V());
        if (onlineFriend == null) {
            return;
        }
        OnlineFriendUiHelper.addFriendChatMessage(onlineFriend, onlineFriend, groupChatMessageEvent.K());
    }

    public long getNextReconnectAt() {
        return this.nextReconnectAt;
    }

    private void runConnectionAttempt(AtomicReference<Thread> connectionThreadReference) {
        try {
            this.manualDisconnectRequested = false;
            ZeusConnectionManager.T().V(this::handleTransportConnected, () -> this.handleDisconnected(connectionThreadReference));
        }
        catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    private static void handlePartyInviteRemoved(PartyInviteRemovedEvent partyInviteRemovedEvent) {
        PartyState partyState = Vape.INSTANCE.getOnlineManager().getPartyManager().getCurrentParty();
        if (partyState == null) {
            return;
        }
        partyState.removeInvitedUser(partyInviteRemovedEvent.D());
    }

    private static void handleFriendRequestEvent(FriendRequestEvent friendRequestEvent) {
        PartyInvite partyInvite = Vape.INSTANCE.getOnlineManager().getPartyManager().getInvite(Vape.INSTANCE.getOnlineManager().getFriendCache().getOrCreateFriend(friendRequestEvent.f().getId(), () -> OnlineConnectionManager.createFriendFromRequestEvent(friendRequestEvent)));
        if (partyInvite == null) {
            return;
        }
        Vape.INSTANCE.getOnlineManager().getPartyManager().removeInvite(partyInvite);
    }

    private static void forwardCreatedParty(Consumer<PartyState> partyConsumer, GroupCreatedEvent groupCreatedEvent) {
        partyConsumer.accept(groupCreatedEvent.V());
    }

    public GlobalSettingsController getGlobalSettingsController() {
        return this.globalSettingsController;
    }

    public void disconnect() {
        Channel channel = ZeusConnectionManager.T().u().D();
        if (channel == null) {
            return;
        }
        INSTANCE.setManualDisconnectRequested(true);
        channel.close();
        INSTANCE.setConnectionState(OnlineConnectionState.OFFLINE);
    }

    public OnlineSettings getSettings() {
        return this.settings;
    }

    private void handleInitializationFailure() {
        this.setAccountState(OnlineAccountState.REGISTRATION_OFFLINE);
        ClientSettings.getFrame(OnlineFriendsFrame.class).showRegistration();
        this.initializationStarted = false;
    }

    private void setupListeners() {
        if (this.listenersRegistered) {
            return;
        }
        this.listenersRegistered = true;
        OnlineEventDispatcher.O.M(InitialOnlineFriendStateEvent.class, OnlineConnectionManager::handleInitialOnlineFriendState);
        OnlineEventDispatcher.O.M(FriendModelUpdateEvent.class, this::handleFriendModelUpdate);
        OnlineEventDispatcher.O.M(FriendPresenceStateEvent.class, OnlineConnectionManager::handleFriendPresenceState);
        OnlineEventDispatcher.O.M(FriendRemovedEvent.class, OnlineConnectionManager::handleFriendRemoved);
        OnlineEventDispatcher.O.M(FriendRequestReceivedEvent.class, OnlineConnectionManager::handleFriendRequestReceived);
        OnlineEventDispatcher.O.M(FriendRequestRemovedEvent.class, OnlineConnectionManager::handleFriendRequestRemoved);
        OnlineEventDispatcher.O.M(FriendRequestSentEvent.class, OnlineConnectionManager::handleFriendRequestSent);
        OnlineEventDispatcher.O.M(GroupChatMessageEvent.class, OnlineConnectionManager::handleGroupChatMessage);
        Consumer<PartyState> partyConsumer = OnlineConnectionManager::setCurrentPartyIfAbsent;
        OnlineEventDispatcher.O.M(GroupCreatedEvent.class, event -> OnlineConnectionManager.forwardCreatedParty(partyConsumer, event));
        OnlineEventDispatcher.O.M(GroupInviteAcceptedEvent.class, event -> OnlineConnectionManager.forwardAcceptedParty(partyConsumer, event));
        Runnable clearPartyState = OnlineConnectionManager::clearPartyState;
        OnlineEventDispatcher.O.M(GroupDeletedEvent.class, event -> OnlineConnectionManager.handleGroupDeleted(clearPartyState, event));
        OnlineEventDispatcher.O.M(GroupLeftEvent.class, event -> OnlineConnectionManager.handleGroupLeft(clearPartyState, event));
        OnlineEventDispatcher.O.M(PartyInviteReceivedEvent.class, OnlineConnectionManager::handlePartyInviteReceived);
        OnlineEventDispatcher.O.M(FriendRequestEvent.class, OnlineConnectionManager::handleFriendRequestEvent);
        OnlineEventDispatcher.O.M(GroupInviteSentEvent.class, OnlineConnectionManager::handleGroupInviteSent);
        OnlineEventDispatcher.O.M(PartyInviteRemovedEvent.class, OnlineConnectionManager::handlePartyInviteRemoved);
        OnlineEventDispatcher.O.M(PartyMemberUpdateEvent.class, event -> OnlineConnectionManager.handlePartyMemberUpdate(clearPartyState, event));
        OnlineEventDispatcher.O.M(PartyLeaderChangedEvent.class, OnlineConnectionManager::handlePartyLeaderChanged);
        OnlineEventDispatcher.O.M(FriendChatMessageEvent.class, OnlineConnectionManager::handleFriendChatMessage);
        OnlineEventDispatcher.O.M(GroupOptionUpdatedEvent.class, OnlineConnectionManager::handleGroupOptionUpdated);
        OnlineEventDispatcher.O.M(FriendMinecraftProfileUpdateEvent.class, OnlineConnectionManager::handleFriendMinecraftProfileUpdate);
        OnlineEventDispatcher.O.M(FriendVisibilityUpdateEvent.class, OnlineConnectionManager::handleFriendVisibilityUpdate);
        OnlineEventDispatcher.O.M(UserDisplayNameChangedEvent.class, OnlineConnectionManager::handleUserDisplayNameChanged);
        OnlineEventDispatcher.O.M(FriendServerAddressEvent.class, OnlineConnectionManager::handleFriendServerAddress);
    }

    private void handleAuthenticationSuccess(AuthenticationResponsePacket authenticationResponsePacket) {
        this.reconnectAttemptCount = 0;
        ClientSettings.getFrame(OnlineFriendsFrame.class).closeRegistrationPopup();
        this.setConnectionState(OnlineConnectionState.ONLINE);
        this.hasConnectedSuccessfully = true;
    }

    private void handleFriendModelUpdate(FriendModelUpdateEvent friendModelUpdateEvent) {
        OnlineFriend onlineFriend = Vape.INSTANCE.getOnlineManager().getFriendCache().getOrCreateFriend(friendModelUpdateEvent.q().getUserId(), () -> OnlineConnectionManager.createFriendFromModelUpdate(friendModelUpdateEvent));
        onlineFriend.updateFrom(friendModelUpdateEvent.q());
        Vape.INSTANCE.getOnlineFriendManager().addFriend(onlineFriend);
        Vape.INSTANCE.getOnlineManager().getFriendRequestManager().removeRequestsForFriend(onlineFriend);
        Boolean syncWithFriends = this.getSettings().getPayload().getFriendStates().get(onlineFriend.getUser().getId());
        if (syncWithFriends != null) {
            onlineFriend.setSyncWithFriends(syncWithFriends);
        }
        OnlineFriendUiHelper.refreshFriendLists();
    }

    public OnlineConnectionManager() {
        this.accountState = OnlineAccountState.CONNECTING;
        this.friendRequestNotificationTimer = new TimerUtil();
        this.settings = new OnlineSettings();
        this.globalSettingsController = new GlobalSettingsController();
    }

    private static void handleFriendPresenceState(FriendPresenceStateEvent friendPresenceStateEvent) {
        OnlineFriend onlineFriend = Vape.INSTANCE.getOnlineFriendManager().getByUser(friendPresenceStateEvent.f());
        if (onlineFriend == null) {
            return;
        }
        onlineFriend.setStatus(OnlineStatus.fromPresenceState(friendPresenceStateEvent.O()));
        OnlineFriendUiHelper.refreshFriendLists();
    }

    private static OnlineFriend createFriendFromPartyMemberUpdate(PartyMemberUpdateEvent partyMemberUpdateEvent) {
        return new OnlineFriend(partyMemberUpdateEvent.S());
    }

    static {
        JOINED_PARTY_SUFFIX = "joined the party";
        INSTANCE = new OnlineConnectionManager();
    }

    private static void handleFriendRequestRemoved(FriendRequestRemovedEvent friendRequestRemovedEvent) {
        Vape.INSTANCE.getOnlineManager().getFriendRequestManager().removeRequestById(friendRequestRemovedEvent.v());
    }

    private static void handleInitialOnlineFriendState(InitialOnlineFriendStateEvent initialOnlineFriendStateEvent) {
        Vape.INSTANCE.getOnlineManager().clearOnlineState();
        ZeusClient zeusClient = ZeusConnectionManager.T().u();
        Vape.INSTANCE.getOnlineManager().getLocalFriend().setDisplayName(zeusClient.i().getDisplayName());
        for (FriendModel friendModel : initialOnlineFriendStateEvent.q()) {
            new FriendModelUpdateEvent(zeusClient, friendModel).u();
        }
        for (FriendRequestModel friendRequestModel : initialOnlineFriendStateEvent.z()) {
            new FriendRequestReceivedEvent(zeusClient, friendRequestModel).u();
        }
        for (FriendRequestModel friendRequestModel : initialOnlineFriendStateEvent.Z()) {
            new FriendRequestSentEvent(zeusClient, friendRequestModel).u();
        }
    }

    private static void clearPartyState() {
        PartyState partyState = Vape.INSTANCE.getOnlineManager().getPartyManager().getCurrentParty();
        if (partyState == null) {
            return;
        }
        Vape.INSTANCE.getOnlineManager().getPartyManager().setCurrentParty(null);
        Vape.INSTANCE.getOnlineManager().getLocalFriend().setGroupRole(-1);
        for (OnlineFriend onlineFriend : Vape.INSTANCE.getOnlineManager().getFriendCache().getFriends()) {
            onlineFriend.setGroupRole(-1);
        }
    }

    private static void handleFriendVisibilityUpdate(FriendVisibilityUpdateEvent friendVisibilityUpdateEvent) {
        OnlineFriend onlineFriend = Vape.INSTANCE.getOnlineManager().getFriendCache().getFriend(friendVisibilityUpdateEvent.N());
        if (onlineFriend != null) {
            onlineFriend.setVisible(friendVisibilityUpdateEvent.q());
        }
    }

    private static void handleFriendChatMessage(FriendChatMessageEvent friendChatMessageEvent) {
        PartyState partyState = Vape.INSTANCE.getOnlineManager().getPartyManager().getCurrentParty();
        if (partyState == null) {
            return;
        }
        OnlineFriend onlineFriend = partyState.findMember(friendChatMessageEvent.U());
        if (onlineFriend == null) {
            return;
        }
        PartyMemberTextStatusComponent partyMemberTextStatusComponent = new PartyMemberTextStatusComponent(friendChatMessageEvent.g());
        if (friendChatMessageEvent.U().equals(Vape.INSTANCE.getOnlineManager().getLocalFriend().getUser())) {
            partyState.addChatRow(new PartyMemberRow(Vape.INSTANCE.getOnlineManager().getLocalFriend(), partyMemberTextStatusComponent));
        } else {
            partyState.addChatRow(new PartyMemberRow(onlineFriend, partyMemberTextStatusComponent));
        }
    }

    private static void handleFriendRemoved(FriendRemovedEvent friendRemovedEvent) {
        OnlineFriend onlineFriend = Vape.INSTANCE.getOnlineFriendManager().getByUser(friendRemovedEvent.f());
        if (onlineFriend == null) {
            return;
        }
        Vape.INSTANCE.getFriendManager().removeFriend(onlineFriend.getExternalFriend());
        Vape.INSTANCE.getOnlineFriendManager().removeFriend(onlineFriend);
    }

    private static void handleGroupLeft(Runnable clearPartyState, GroupLeftEvent groupLeftEvent) {
        clearPartyState.run();
    }

    private static void handleFriendRequestSent(FriendRequestSentEvent friendRequestSentEvent) {
        Vape.INSTANCE.getOnlineManager().getFriendRequestManager().addRequest(new OutgoingFriendRequest(friendRequestSentEvent.q()));
    }

    public boolean isManualDisconnectRequested() {
        return this.manualDisconnectRequested;
    }

    private static void handleUserDisplayNameChanged(UserDisplayNameChangedEvent userDisplayNameChangedEvent) {
        OnlineFriend onlineFriend;
        if (userDisplayNameChangedEvent.R() == Vape.INSTANCE.getOnlineManager().getLocalFriend().getUser().getId()) {
            Vape.INSTANCE.getOnlineManager().getLocalFriend().setDisplayName(userDisplayNameChangedEvent.v());
        }
        if ((onlineFriend = Vape.INSTANCE.getOnlineManager().getFriendCache().getFriend(userDisplayNameChangedEvent.R())) != null) {
            onlineFriend.setDisplayName(userDisplayNameChangedEvent.v());
        }
    }

    public void setDisconnectReason(@Nullable OnlineDisconnectReason onlineDisconnectReason) {
        this.disconnectReason = onlineDisconnectReason;
    }

    private static void handleFriendMinecraftProfileUpdate(FriendMinecraftProfileUpdateEvent friendMinecraftProfileUpdateEvent) {
        OnlineFriend onlineFriend = Vape.INSTANCE.getOnlineManager().getFriendCache().getFriend(friendMinecraftProfileUpdateEvent.b());
        if (onlineFriend != null) {
            onlineFriend.updateMinecraftProfile(friendMinecraftProfileUpdateEvent.h(), friendMinecraftProfileUpdateEvent.b$src$Ljava_lang_String_$171yzxt());
        }
    }

    private void handleTransportConnected() {
        if (!this.hasConnectedSuccessfully) {
            this.friendRequestNotificationTimer.reset();
        }
        this.authenticate();
    }

    public boolean hasConnectedSuccessfully() {
        return this.hasConnectedSuccessfully;
    }

    public TimerUtil getFriendRequestNotificationTimer() {
        return this.friendRequestNotificationTimer;
    }

    private static void setCurrentPartyIfAbsent(PartyState partyState) {
        PartyState currentParty = Vape.INSTANCE.getOnlineManager().getPartyManager().getCurrentParty();
        if (currentParty != null) {
            return;
        }
        LocalOnlineFriend localOnlineFriend = Vape.INSTANCE.getOnlineManager().getLocalFriend();
        OnlineFriend onlineFriend = partyState.findMember(localOnlineFriend.getUser());
        if (onlineFriend != null) {
            localOnlineFriend.setGroupRole(onlineFriend.getGroupRole());
        }
        Vape.INSTANCE.getOnlineManager().getPartyManager().setCurrentParty(partyState);
    }

    public void connect() {
        if (!this.connectionState.isOfflineState()) {
            return;
        }
        this.setDisconnectReason(null);
        this.setConnectionState(OnlineConnectionState.CONNECTING);
        if (this.connectionThread != null) {
            try {
                this.connectionThread.interrupt();
                this.connectionThread = null;
            }
            catch (Throwable throwable) {
                Vape.logThrowable(throwable);
            }
        }
        AtomicReference<Thread> connectionThreadReference = new AtomicReference<Thread>();
        Thread thread = new Thread(() -> this.runConnectionAttempt(connectionThreadReference));
        connectionThreadReference.set(thread);
        thread.start();
        this.connectionThread = thread;
    }

    private static void handlePartyMemberUpdate(Runnable clearPartyState, PartyMemberUpdateEvent partyMemberUpdateEvent) {
        PartyState partyState = Vape.INSTANCE.getOnlineManager().getPartyManager().getCurrentParty();
        if (partyState == null) {
            return;
        }
        OnlineFriend onlineFriend = Vape.INSTANCE.getOnlineManager().getFriendCache().getOrCreateFriend(partyMemberUpdateEvent.S().getUserId(), () -> OnlineConnectionManager.createFriendFromPartyMemberUpdate(partyMemberUpdateEvent));
        if (partyMemberUpdateEvent.q() == PartyMemberAction.ADD) {
            partyState.removeInvitedUser(onlineFriend);
            partyState.addMember(onlineFriend);
            onlineFriend.setGroupRole(partyMemberUpdateEvent.S().getGroupRole());
            Vape.INSTANCE.getNotificationManager().show(onlineFriend.getDisplayName() + " " + NotificationText.localize(JOINED_PARTY_SUFFIX), "", NotificationType.FRIENDS_PARTY_GENERAL, 3000L);
        } else {
            if (onlineFriend.equals(Vape.INSTANCE.getOnlineManager().getLocalFriend())) {
                clearPartyState.run();
            } else {
                partyState.removeMember(onlineFriend);
            }
            onlineFriend.setGroupRole(-1);
        }
    }

    public void setConnectionState(OnlineConnectionState onlineConnectionState) {
        if (onlineConnectionState == OnlineConnectionState.OFFLINE && this.connectionState != onlineConnectionState) {
            Vape.INSTANCE.getOnlineManager().clearOnlineState();
            ClientSettings.getFrame(OnlineFriendsFrame.class).refreshOnlineData();
        }
        this.connectionState = onlineConnectionState;
        ClientSettings.UI_EXECUTOR.execute(() -> OnlineConnectionManager.updateConnectionStateUi(onlineConnectionState));
    }

    public void initialize() throws Exception {
        if (this.initializationStarted) {
            return;
        }
        this.initializationStarted = true;
        this.setupListeners();
        this.setAccountState(OnlineAccountState.CONNECTING);
        Runnable initializationFailureHandler = this::handleInitializationFailure;
        this.globalSettingsController.load();
        try {
            AccountEntitlements accountEntitlements = Vape.INSTANCE.getAccountInfo().getEntitlements();
            boolean licensed = accountEntitlements.isLicensed();
            boolean registered = accountEntitlements.isRegistered();
            boolean banned = accountEntitlements.isBanned();
            if (banned) {
                this.setAccountState(OnlineAccountState.BANNED);
            } else if (registered) {
                this.setAccountState(OnlineAccountState.REGISTERED);
                this.settings.initialize();
                if (this.settings.getAutoLogin().getEffectiveValue().booleanValue()) {
                    this.connect();
                } else {
                    ClientSettings.getFrame(OnlineFriendsFrame.class).getModeToggle().setLeftSelected(false);
                }
            } else {
                this.setAccountState(OnlineAccountState.UNREGISTERED);
                ClientSettings.getFrame(OnlineFriendsFrame.class).closeRegistrationIfOpen();
                ClientSettings.getFrame(OnlineFriendsFrame.class).switchToMinecraftFriends();
            }
        }
        catch (Throwable throwable) {
            initializationFailureHandler.run();
        }
    }

    private static Throwable passthroughThrowable(Throwable throwable) {
        return throwable;
    }

    public boolean isCurrentUser(@Nullable PublicProfileUser publicProfileUser) {
        long currentUserId = Vape.INSTANCE.getAccountInfo().getUserId();
        return publicProfileUser != null && currentUserId != -1L && currentUserId == publicProfileUser.getUserId();
    }
}
