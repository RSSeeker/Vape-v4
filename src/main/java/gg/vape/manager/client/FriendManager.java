package gg.vape.manager.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import gg.vape.Vape;
import gg.vape.friend.ExternalFriend;
import gg.vape.friend.Friend;
import gg.vape.friend.FriendEntry;
import gg.vape.friend.ui.OnlineFriendUiHelper;
import gg.vape.mapping.MappedClasses;
import gg.vape.notification.NotificationText;
import gg.vape.utils.RayTraceUtil;
import gg.vape.value.BooleanValue;
import gg.vape.value.ColorValue;
import gg.vape.wrapper.impl.Entity;
import gg.vape.wrapper.impl.EntityLivingBase;
import gg.vape.wrapper.impl.EntityOtherPlayerMP;
import gg.vape.wrapper.impl.EntityPlayer;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.RayTraceResult;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

public class FriendManager {
    public BooleanValue useFriends;
    public BooleanValue spoofAlias;
    private static String obfuscationMarker;
    public ColorValue friendColor;
    public BooleanValue useAlias;
    private final Set<FriendEntry> friends = new HashSet<FriendEntry>();
    public BooleanValue recolorVisuals;

    public static String getObfuscationMarker() {
        return obfuscationMarker;
    }

    public static void setObfuscationMarker(String marker) {
        obfuscationMarker = marker;
    }

    public void refreshPlayerNames() {
        if (Minecraft.theWorld().isNull()) {
            return;
        }
        for (Object playerObject : Minecraft.theWorld().X()) {
            if (playerObject == null) {
                return;
            }
            new EntityPlayer(playerObject).w$src$V$1iu649y();
        }
    }

    @Nullable
    public FriendEntry findTargetedFriend(String name, boolean respectEnabledSetting) {
        if (this.useFriends.getEffectiveValue().booleanValue() || !respectEnabledSetting) {
            ArrayList<FriendEntry> matches = this.getFriendsByName(name);
            if (matches.isEmpty()) {
                return null;
            }
            return matches.stream().filter(FriendEntry::isTargeted).findFirst().orElse(null);
        }
        return null;
    }

    static {
        FriendManager.setObfuscationMarker("p5mJgc");
    }

    public void removeFriend(FriendEntry friendEntry) {
        this.friends.remove(friendEntry);
        this.refreshPlayerNames();
        OnlineFriendUiHelper.refreshMinecraftFriends();
    }

    public JsonArray toJson() {
        JsonArray jsonArray = new JsonArray();
        for (FriendEntry friendEntry : this.getFriends()) {
            if (!friendEntry.isPersistent()) continue;
            jsonArray.add((JsonElement)friendEntry.toJson());
        }
        return jsonArray;
    }

    private static Exception propagateException(Exception exception) {
        return exception;
    }

    public void toggleCrosshairTarget() {
        if (Minecraft.currentScreen().isNotNull()) {
            return;
        }
        RayTraceResult rayTraceResult = RayTraceUtil.o();
        if (rayTraceResult.isNull()) {
            return;
        }
        Entity entity = rayTraceResult.getEntity();
        if (entity.isNull()) {
            return;
        }
        if (entity.isInstance(MappedClasses.lG)) {
            EntityOtherPlayerMP entityOtherPlayerMP = new EntityOtherPlayerMP(entity);
            String name = entityOtherPlayerMP.getName();
            ArrayList<FriendEntry> matches = this.getFriendsByName(name);
            if (matches.isEmpty()) {
                this.addFriend(new Friend(name, name));
                Vape.INSTANCE.getNotificationManager().showInfo("\u00a7aAdded\u00a7r " + name + " " + NotificationText.localize("to friends"), "", 2000L);
            } else {
                this.removeFriend(matches.get(0));
                Vape.INSTANCE.getNotificationManager().showInfo("\u00a7cRemoved\u00a7r " + name + " " + NotificationText.localize("from friends"), "", 2000L);
            }
        }
    }

    @Nullable
    public FriendEntry findTargetedFriend(String name) {
        return this.findTargetedFriend(name, true);
    }

    public boolean isFriend(EntityLivingBase entityLivingBase) {
        boolean matchesName = this.isFriend(entityLivingBase.getName());
        boolean matchesUuid = this.isFriend(entityLivingBase.X$src$Ljava_util_UUID_$1o5dyg6().toString());
        return matchesName || matchesUuid;
    }

    public boolean isFriend(String name) {
        if (!this.useFriends.getEffectiveValue().booleanValue()) {
            return false;
        }
        ArrayList<FriendEntry> matches = this.getFriendsByName(name);
        return !matches.isEmpty() && matches.stream().anyMatch(FriendEntry::isTargeted);
    }

    public ArrayList<FriendEntry> getFriendsByName(String name) {
        ArrayList<FriendEntry> matches = new ArrayList<FriendEntry>();
        for (FriendEntry friendEntry : this.friends) {
            if (!friendEntry.getName().equalsIgnoreCase(name)) continue;
            matches.add(friendEntry);
        }
        return matches;
    }

    public void addFriend(FriendEntry friendEntry) {
        if (friendEntry == null) {
            return;
        }
        ArrayList<FriendEntry> matches = this.getFriendsByName(friendEntry.getName());
        if (!matches.isEmpty()) {
            for (FriendEntry friendEntry2 : matches) {
                if (friendEntry instanceof ExternalFriend && !(friendEntry2 instanceof ExternalFriend) || !(friendEntry instanceof ExternalFriend) && friendEntry2 instanceof ExternalFriend) continue;
                this.removeFriend(friendEntry2);
            }
        }
        this.friends.add(friendEntry);
        this.refreshPlayerNames();
        OnlineFriendUiHelper.refreshMinecraftFriends();
    }

    private void onSpoofAliasChanged(BooleanValue ignored) {
        this.refreshPlayerNames();
    }

    public void loadFriends(JsonArray jsonArray) {
        if (jsonArray.size() == 0) {
            return;
        }
        this.clearFriends();
        for (int i = 0; i < jsonArray.size(); ++i) {
            try {
                if (i > 100) break;
                JsonElement jsonElement = jsonArray.get(i);
                if (!jsonElement.isJsonObject() || jsonElement.isJsonNull()) continue;
                Friend friend = new Friend("", "").loadJson(jsonElement.getAsJsonObject());
                this.addFriend(friend);
                continue;
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
    }

    public Set<FriendEntry> getFriends() {
        return this.friends;
    }

    public void clearFriends() {
        this.getFriends().clear();
    }

    public FriendManager() {
        this.useFriends = BooleanValue.create(this, "Use friends", true, "If enabled, any usernames inside your Minecraft friends list will be excluded from certain modules\nFor example they will not be targeted by KillAura");
        this.useAlias = BooleanValue.create(this, "Use alias", true);
        this.spoofAlias = BooleanValue.create(this, "Spoof alias", false, "Replace the friend's name in chat, tablist, and regular nametags with their alias.");
        this.recolorVisuals = BooleanValue.create(this, "Recolor visuals", true, "Re-colors certain render modules to use \"Friends Color\" on friends");
        this.friendColor = ColorValue.create(this, "Friends Color", new Color(66, 244, 137));
        this.spoofAlias.addChangeListener(this::onSpoofAliasChanged);
        this.recolorVisuals.addDependentValues(this.friendColor);
        this.useAlias.addDependentValues(this.spoofAlias);
    }
}

