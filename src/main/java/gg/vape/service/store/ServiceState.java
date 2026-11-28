package gg.vape.service.store;

import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;

final class ServiceState {
    long nextUserId = 1L;
    // Stable identity for the in-process, no-login loader across ephemeral tokens.
    long embeddedLocalUserId;
    long nextPublicProfileId = 1L;
    long nextFriendRequestId = 1L;
    long nextPartyId = 1L;
    Map<String, AccountRecord> accountsByToken = new LinkedHashMap<>();
    Map<String, AuthChallengeRecord> challenges = new LinkedHashMap<>();
    Map<String, JsonObject> publicProfiles = new LinkedHashMap<>();
    Map<Long, FriendRequestRecord> friendRequests = new LinkedHashMap<>();
    Map<Long, PartyRecord> parties = new LinkedHashMap<>();
}
