package gg.vape.service.store;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class AccountRecord {
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
            .withZone(ZoneOffset.UTC);
    public long userId;
    public String username;
    public String accountCreation;
    public boolean licensed = true;
    public boolean registered = true;
    public boolean profiles = true;
    public boolean banned;
    public String minecraftUuid = "00000000-0000-0000-0000-000000000000";
    public String minecraftUsername = "";
    public boolean showUsername = true;
    public int presence = 2;
    public String serverAddress;
    public long activeProfileId = -1L;
    public Set<Long> onlineFriends = new LinkedHashSet<>();
    public JsonObject globalSettings = defaultGlobalSettings();
    public JsonObject onlineSettings = defaultOnlineSettings();
    public JsonArray localFriends = new JsonArray();
    public JsonArray otherData = new JsonArray();
    public Map<String, JsonObject> privateProfiles = new LinkedHashMap<>();

    public static AccountRecord developmentAccount() {
        AccountRecord account = new AccountRecord();
        account.userId = 1L;
        account.username = "Developer";
        account.accountCreation = nowTimestamp();
        return account;
    }

    public static String nowTimestamp() {
        return TIMESTAMP_FORMAT.format(Instant.now());
    }

    public static String normalizeTimestamp(String timestamp) {
        try {
            return TIMESTAMP_FORMAT.format(Instant.parse(timestamp));
        } catch (RuntimeException ignored) {
            return nowTimestamp();
        }
    }

    private static JsonObject defaultGlobalSettings() {
        JsonObject settings = new JsonObject();
        settings.addProperty("cache", false);
        settings.addProperty("firstRun", true);
        return settings;
    }

    private static JsonObject defaultOnlineSettings() {
        JsonObject settings = new JsonObject();
        settings.addProperty("inventorySwitchMode", 0);
        settings.addProperty("partyShowTarget", true);
        settings.addProperty("autoLogin", true);
        settings.addProperty("showSelf", true);
        settings.addProperty("showUsername", true);
        settings.add("showInventoryKeybind", new JsonArray());
        settings.add("friendStates", new JsonObject());
        settings.addProperty("shareInventory", false);
        settings.addProperty("showServer", true);
        settings.add("pingKeybind", new JsonArray());
        return settings;
    }

    public JsonObject accountJson() {
        JsonObject json = new JsonObject();
        json.addProperty("userId", userId);
        json.addProperty("username", username);
        json.addProperty("accountCreation", accountCreation);
        json.addProperty("licensed", licensed);
        json.addProperty("registered", registered);
        json.addProperty("profiles", profiles);
        json.addProperty("banned", banned);
        return json;
    }
}
