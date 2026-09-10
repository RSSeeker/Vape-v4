package gg.vape.friend;

import gg.vape.Vape;
import gg.vape.friend.ExternalFriend;
import gg.vape.friend.FriendModel;
import gg.vape.friend.GroupUserModel;
import gg.vape.friend.OnlineStatus;
import gg.vape.friend.UserModel;
import gg.vape.friend.ui.OnlineFriendUiHelper;
import gg.vape.notification.INotification;
import gg.vape.notification.Notification;
import gg.vape.notification.NotificationText;
import gg.vape.notification.NotificationType;
import gg.vape.notification.TextNotificationContent;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

public class OnlineFriend {
    private int groupRole = -1;
    private UUID minecraftUuid;
    private static boolean obfuscationState;
    private boolean syncWithFriends;
    private ExternalFriend externalFriend;
    private boolean unreadMessage;
    private boolean hasChatHistory;
    protected UserModel user;
    @Nullable
    private String minecraftServer;
    private OnlineStatus status = OnlineStatus.OFFLINE;
    private String minecraftUsername = "";
    private boolean visible = true;
    protected String displayName;

    public void setMinecraftUsername(String minecraftUsername) {
        this.minecraftUsername = minecraftUsername;
    }

    public void setUnreadMessage(boolean unreadMessage) {
        this.unreadMessage = unreadMessage;
    }

    public void setGroupRole(int groupRole) {
        this.groupRole = groupRole;
    }

    public void setSyncWithFriends(boolean syncWithFriends) {
        this.syncWithFriends = syncWithFriends;
        if (syncWithFriends) {
            if (this.status != OnlineStatus.OFFLINE) {
                Vape.INSTANCE.getFriendManager().addFriend(this.externalFriend);
                OnlineFriendUiHelper.refreshMinecraftFriends();
            }
        } else {
            Vape.INSTANCE.getFriendManager().removeFriend(this.externalFriend);
            OnlineFriendUiHelper.refreshMinecraftFriends();
        }
    }

    public void setHasChatHistory(boolean hasChatHistory) {
        this.hasChatHistory = hasChatHistory;
    }

    public OnlineFriend(GroupUserModel groupUserModel) {
        this(groupUserModel.getUser());
        this.status = OnlineStatus.ONLINE;
        this.minecraftUuid = groupUserModel.getMinecraftUuid();
        this.minecraftUsername = groupUserModel.getMinecraftUsername();
        this.groupRole = groupUserModel.getGroupRole();
    }

    public static boolean getObfuscationState() {
        return obfuscationState;
    }


    public OnlineFriend(String displayName) {
        this(null, displayName);
    }

    public void updateMinecraftProfile(UUID minecraftUuid, String minecraftUsername) {
        this.minecraftUuid = minecraftUuid;
        this.minecraftUsername = minecraftUsername;
    }

    public boolean hasUnreadMessage() {
        return this.unreadMessage;
    }

    public OnlineFriend(FriendModel friendModel) {
        this(friendModel.getUser());
        this.status = OnlineStatus.fromPresenceState(friendModel.getPresenceState());
        this.minecraftUuid = friendModel.getMinecraftUuid();
        this.minecraftUsername = friendModel.getMinecraftUsername();
        this.visible = friendModel.isVisible();
        this.minecraftServer = friendModel.getMinecraftServer();
    }

    public UserModel getUser() {
        return this.user;
    }

    static {
        OnlineFriend.setObfuscationState(false);
    }

    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof OnlineFriend)) {
            return false;
        }
        OnlineFriend onlineFriend = (OnlineFriend)other;
        return this.user.getId() == onlineFriend.user.getId();
    }

    @Nullable
    public UUID getMinecraftUuid() {
        return this.minecraftUuid;
    }

    public static void setObfuscationState(boolean state) {
        obfuscationState = state;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public ExternalFriend getExternalFriend() {
        return this.externalFriend;
    }

    public void setMinecraftServer(@Nullable String minecraftServer) {
        this.minecraftServer = minecraftServer;
    }

    public OnlineStatus getStatus() {
        return this.status;
    }

    public int hashCode() {
        return this.user.hashCode();
    }

    public String getMinecraftUsername() {
        return this.minecraftUsername;
    }

    @Nullable
    public String getMinecraftServer() {
        return this.minecraftServer;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public int getGroupRole() {
        return this.groupRole;
    }

    public boolean hasChatHistory() {
        return this.hasChatHistory;
    }

    public void setStatus(OnlineStatus onlineStatus) {
        this.status = onlineStatus;
        if (onlineStatus.equals((Object)OnlineStatus.ONLINE)) {
            Notification notification = new Notification(NotificationType.FRIENDS_ONLINE, "\u00a7f" + this.getDisplayName() + " " + NotificationText.localize("\u00a77is online"), new TextNotificationContent("", NotificationType.FRIENDS_ONLINE), 0.0, 0.0, 4000L);
            boolean shouldNotify = true;
            for (INotification iNotification : Vape.INSTANCE.getNotificationManager().getNotifications()) {
                if (!(iNotification instanceof Notification)
                        || !((Notification)iNotification).getTitle().equals(notification.getTitle())) continue;
                shouldNotify = false;
                break;
            }
            if (shouldNotify) {
                Vape.INSTANCE.getNotificationManager().show(notification);
            }
        }
        if (this.syncWithFriends) {
            if (onlineStatus == OnlineStatus.ONLINE) {
                Vape.INSTANCE.getFriendManager().addFriend(this.externalFriend);
                OnlineFriendUiHelper.refreshMinecraftFriends();
            } else if (onlineStatus == OnlineStatus.OFFLINE) {
                Vape.INSTANCE.getFriendManager().removeFriend(this.externalFriend);
                OnlineFriendUiHelper.refreshMinecraftFriends();
            }
        }
    }

    public boolean isVisible() {
        return this.visible;
    }

    public static boolean getObfuscationConstant() {
        boolean state = OnlineFriend.getObfuscationState();
        return true;
    }

    public OnlineFriend(UserModel userModel, String displayName) {
        this.user = userModel;
        this.setDisplayName(displayName);
        this.externalFriend = new ExternalFriend(this);
    }

    public OnlineFriend(UserModel userModel) {
        this(userModel, userModel.getDisplayName());
    }

    public boolean isSyncWithFriends() {
        return this.syncWithFriends;
    }

    public void updateFrom(FriendModel friendModel) {
        this.status = OnlineStatus.fromPresenceState(friendModel.getPresenceState());
        this.minecraftUsername = friendModel.getMinecraftUsername();
        this.minecraftUuid = friendModel.getMinecraftUuid();
        this.minecraftServer = friendModel.getMinecraftServer();
    }
}

