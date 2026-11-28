package gg.vape.service.store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gg.vape.config.ConfigJsonUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class FileStore {
    private static final long CHALLENGE_TTL_MILLIS = 5 * 60 * 1000L;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    private final SecureRandom random = new SecureRandom();
    private final Path file;
    private ServiceState state;

    public FileStore(Path file) throws IOException {
        this.file = file.toAbsolutePath().normalize();
        this.state = load();
        normalizeLoadedState();
        save();
    }

    public synchronized Optional<AccountRecord> account(String token) {
        return Optional.ofNullable(state.accountsByToken.get(token));
    }

    public synchronized Optional<AccountRecord> account(long userId) {
        return state.accountsByToken.values().stream()
                .filter(account -> account.userId == userId)
                .findFirst();
    }

    public synchronized Optional<AccountRecord> accountByName(String username) {
        return state.accountsByToken.values().stream()
                .filter(account -> account.username.equalsIgnoreCase(username))
                .findFirst();
    }

    public synchronized LoaderLoginResult loginByUsername(String username) throws IOException {
        String normalized = username == null ? "" : username.trim();
        if (normalized.isEmpty() || normalized.length() > 16) {
            throw new IllegalArgumentException("Username must contain 1 to 16 characters");
        }
        for (Map.Entry<String, AccountRecord> entry : state.accountsByToken.entrySet()) {
            if (entry.getValue().username.equalsIgnoreCase(normalized)) {
                return new LoaderLoginResult(entry.getKey(), entry.getValue());
            }
        }

        String token;
        do {
            token = randomToken();
        } while (state.accountsByToken.containsKey(token));
        AccountRecord account = new AccountRecord();
        account.userId = state.nextUserId++;
        account.username = normalized;
        account.accountCreation = AccountRecord.nowTimestamp();
        account.registered = true;
        state.accountsByToken.put(token, account);
        save();
        return new LoaderLoginResult(token, account);
    }

    /**
     * Bind the loader's per-session token to one persistent local identity.
     * This is called directly by the embedded launcher, never by an HTTP route.
     * It must not replace an existing account associated with the supplied token.
     */
    public synchronized void bindEmbeddedLocalToken(String token) throws IOException {
        if (token == null || !token.matches("[0-9a-fA-F]{48}")) {
            throw new IllegalArgumentException("Invalid embedded loader token");
        }
        if (state.accountsByToken.containsKey(token)) return;
        Map<String, AccountRecord> previousAccounts = new LinkedHashMap<>(state.accountsByToken);
        long previousLocalId = state.embeddedLocalUserId, previousNextId = state.nextUserId;
        try {
            AccountRecord account;
            if (state.embeddedLocalUserId != 0L) {
                account = account(state.embeddedLocalUserId).orElseThrow(
                        () -> new IOException("Embedded local account is missing from the service store"));
                // Keep one token per identity: duplicate deserialized AccountRecord
                // objects would otherwise diverge when one session changes data.
                state.accountsByToken.entrySet().removeIf(entry -> entry.getValue().userId == state.embeddedLocalUserId);
            } else {
                String username = "Local";
                int suffix = 2;
                while (accountByName(username).isPresent()) username = "Local" + suffix++;
                account = new AccountRecord();
                account.userId = state.nextUserId++;
                account.username = username;
                account.accountCreation = AccountRecord.nowTimestamp();
                state.embeddedLocalUserId = account.userId;
            }
            state.accountsByToken.put(token, account);
            save();
        } catch (IOException | RuntimeException failure) {
            state.accountsByToken = previousAccounts;
            state.embeddedLocalUserId = previousLocalId;
            state.nextUserId = previousNextId;
            throw failure;
        }
    }

    public synchronized boolean areFriends(long firstUserId, long secondUserId) {
        return account(firstUserId).map(account -> account.onlineFriends.contains(secondUserId)).orElse(false);
    }

    public synchronized Set<Long> friendIds(long userId) {
        return account(userId).map(account -> Collections.unmodifiableSet(new LinkedHashSet<>(account.onlineFriends)))
                .orElse(Collections.<Long>emptySet());
    }

    public synchronized FriendRequestCreation createFriendRequest(long senderId, String targetName)
            throws IOException {
        AccountRecord sender = account(senderId)
                .orElseThrow(() -> new IllegalStateException("Sender account not found"));
        AccountRecord target = accountByName(targetName).orElse(null);
        if (target == null) {
            return new FriendRequestCreation(4, null, null);
        }
        if (senderId == target.userId) {
            return new FriendRequestCreation(1, null, target);
        }
        if (sender.onlineFriends.contains(target.userId)) {
            return new FriendRequestCreation(2, null, target);
        }
        FriendRequestRecord existing = state.friendRequests.values().stream()
                .filter(request -> request.senderId == senderId && request.receiverId == target.userId)
                .findFirst().orElse(null);
        if (existing != null) {
            return new FriendRequestCreation(3, existing, target);
        }
        FriendRequestRecord request = new FriendRequestRecord();
        request.requestId = state.nextFriendRequestId++;
        request.senderId = senderId;
        request.receiverId = target.userId;
        state.friendRequests.put(request.requestId, request);
        save();
        return new FriendRequestCreation(0, request, target);
    }

    public synchronized FriendRequestUpdate updateFriendRequest(long actorId, long requestId, boolean accepted)
            throws IOException {
        FriendRequestRecord request = state.friendRequests.get(requestId);
        if (request == null || request.receiverId != actorId) {
            return new FriendRequestUpdate(2, null, null);
        }
        state.friendRequests.remove(requestId);
        AccountRecord other = account(request.senderId).orElse(null);
        if (accepted && other != null) {
            AccountRecord actor = account(actorId)
                    .orElseThrow(() -> new IllegalStateException("Actor account not found"));
            actor.onlineFriends.add(other.userId);
            other.onlineFriends.add(actor.userId);
        }
        save();
        return new FriendRequestUpdate(accepted ? 0 : 1, request, other);
    }

    public synchronized boolean deleteFriend(long actorId, long otherId) throws IOException {
        AccountRecord actor = account(actorId)
                .orElseThrow(() -> new IllegalStateException("Actor account not found"));
        AccountRecord other = account(otherId).orElse(null);
        boolean removed = actor.onlineFriends.remove(otherId);
        if (other != null) {
            other.onlineFriends.remove(actorId);
        }
        if (removed) {
            save();
        }
        return removed;
    }

    public synchronized java.util.List<FriendRequestRecord> incomingRequests(long userId) {
        return state.friendRequests.values().stream()
                .filter(request -> request.receiverId == userId).collect(java.util.stream.Collectors.toList());
    }

    public synchronized java.util.List<FriendRequestRecord> outgoingRequests(long userId) {
        return state.friendRequests.values().stream()
                .filter(request -> request.senderId == userId).collect(java.util.stream.Collectors.toList());
    }

    public synchronized PartyRecord createParty(long leaderId) throws IOException {
        PartyRecord existing = partyFor(leaderId).orElse(null);
        if (existing != null) {
            return null;
        }
        PartyRecord party = new PartyRecord();
        party.partyId = state.nextPartyId++;
        party.leaderId = leaderId;
        party.members.add(leaderId);
        state.parties.put(party.partyId, party);
        save();
        return party;
    }

    public synchronized Optional<PartyRecord> partyFor(long userId) {
        return state.parties.values().stream()
                .filter(party -> party.members.contains(userId))
                .findFirst();
    }

    public synchronized PartyInviteResult inviteToParty(long actorId, long targetId) throws IOException {
        PartyRecord party = partyFor(actorId).orElse(null);
        if (party == null) {
            party = createParty(actorId);
        }
        if (party.members.contains(targetId) || party.invitedUsers.contains(targetId)) {
            return new PartyInviteResult(1, party);
        }
        if (party.invitedUsers.size() >= 20) {
            return new PartyInviteResult(2, party);
        }
        party.invitedUsers.add(targetId);
        save();
        return new PartyInviteResult(0, party);
    }

    public synchronized PartyInviteDecision decidePartyInvite(long actorId, long inviterId, boolean accepted)
            throws IOException {
        PartyRecord party = partyFor(inviterId).orElse(null);
        if (party == null || !party.invitedUsers.remove(actorId)) {
            return new PartyInviteDecision(3, null);
        }
        if (accepted) {
            if (party.members.size() >= 20) {
                return new PartyInviteDecision(2, party);
            }
            party.members.add(actorId);
        }
        save();
        return new PartyInviteDecision(accepted ? 0 : 1, party);
    }

    public synchronized boolean uninvite(long actorId, long targetId) throws IOException {
        PartyRecord party = partyFor(actorId).orElse(null);
        if (party == null || (party.leaderId != actorId && !party.openInvites)) {
            return false;
        }
        boolean removed = party.invitedUsers.remove(targetId);
        if (removed) {
            save();
        }
        return removed;
    }

    public synchronized PartyLeaveResult leaveParty(long userId) throws IOException {
        PartyRecord party = partyFor(userId).orElse(null);
        if (party == null) {
            return new PartyLeaveResult(false, null, -1L);
        }
        party.members.remove(userId);
        long newLeaderId = party.leaderId;
        if (party.members.isEmpty()) {
            state.parties.remove(party.partyId);
            newLeaderId = -1L;
        } else if (party.leaderId == userId) {
            party.leaderId = party.members.iterator().next();
            newLeaderId = party.leaderId;
        }
        save();
        return new PartyLeaveResult(true, party, newLeaderId);
    }

    public synchronized PartyRecord deleteParty(long actorId) throws IOException {
        PartyRecord party = partyFor(actorId).orElse(null);
        if (party == null || party.leaderId != actorId) {
            return null;
        }
        state.parties.remove(party.partyId);
        save();
        return party;
    }

    public synchronized boolean kickPartyMember(long actorId, long targetId) throws IOException {
        PartyRecord party = partyFor(actorId).orElse(null);
        if (party == null || party.leaderId != actorId || targetId == actorId) {
            return false;
        }
        boolean removed = party.members.remove(targetId);
        if (removed) {
            save();
        }
        return removed;
    }

    public synchronized PartyRecord promotePartyMember(long actorId, long targetId) throws IOException {
        PartyRecord party = partyFor(actorId).orElse(null);
        if (party == null || party.leaderId != actorId || !party.members.contains(targetId)) {
            return null;
        }
        party.leaderId = targetId;
        save();
        return party;
    }

    public synchronized PartyRecord updatePartyOpenInvites(long actorId, boolean openInvites) throws IOException {
        PartyRecord party = partyFor(actorId).orElse(null);
        if (party == null || party.leaderId != actorId) {
            return null;
        }
        party.openInvites = openInvites;
        save();
        return party;
    }

    public synchronized void updatePresence(long userId, int presence) throws IOException {
        AccountRecord account = require(userId);
        account.presence = presence;
        save();
    }

    public synchronized void updateServerAddress(long userId, String serverAddress) throws IOException {
        require(userId).serverAddress = serverAddress;
        save();
    }

    public synchronized void updateShowUsername(long userId, boolean showUsername) throws IOException {
        require(userId).showUsername = showUsername;
        save();
    }

    public synchronized void updateActiveProfile(long userId, long profileId) throws IOException {
        require(userId).activeProfileId = profileId;
        save();
    }

    private AccountRecord require(long userId) {
        return account(userId).orElseThrow(() -> new IllegalStateException("Account not found: " + userId));
    }

    public static final class FriendRequestCreation {
        private final int status;
        private final FriendRequestRecord request;
        private final AccountRecord target;

        public FriendRequestCreation(int status, FriendRequestRecord request, AccountRecord target) {
            this.status = status;
            this.request = request;
            this.target = target;
        }

        public int status() {
            return status;
        }

        public FriendRequestRecord request() {
            return request;
        }

        public AccountRecord target() {
            return target;
        }
    }

    public static final class FriendRequestUpdate {
        private final int status;
        private final FriendRequestRecord request;
        private final AccountRecord other;

        public FriendRequestUpdate(int status, FriendRequestRecord request, AccountRecord other) {
            this.status = status;
            this.request = request;
            this.other = other;
        }

        public int status() {
            return status;
        }

        public FriendRequestRecord request() {
            return request;
        }

        public AccountRecord other() {
            return other;
        }
    }

    public static final class PartyInviteResult {
        private final int status;
        private final PartyRecord party;

        public PartyInviteResult(int status, PartyRecord party) {
            this.status = status;
            this.party = party;
        }

        public int status() {
            return status;
        }

        public PartyRecord party() {
            return party;
        }
    }

    public static final class PartyInviteDecision {
        private final int status;
        private final PartyRecord party;

        public PartyInviteDecision(int status, PartyRecord party) {
            this.status = status;
            this.party = party;
        }

        public int status() {
            return status;
        }

        public PartyRecord party() {
            return party;
        }
    }

    public static final class PartyLeaveResult {
        private final boolean successful;
        private final PartyRecord party;
        private final long newLeaderId;

        public PartyLeaveResult(boolean successful, PartyRecord party, long newLeaderId) {
            this.successful = successful;
            this.party = party;
            this.newLeaderId = newLeaderId;
        }

        public boolean successful() {
            return successful;
        }

        public PartyRecord party() {
            return party;
        }

        public long newLeaderId() {
            return newLeaderId;
        }
    }

    public static final class LoaderLoginResult {
        private final String token;
        private final AccountRecord account;

        public LoaderLoginResult(String token, AccountRecord account) {
            this.token = token;
            this.account = account;
        }

        public String token() {
            return token;
        }

        public AccountRecord account() {
            return account;
        }
    }

    public synchronized AuthChallengeRecord createChallenge(String edition, String hwid) throws IOException {
        byte[] bytes = new byte[20];
        random.nextBytes(bytes);
        AuthChallengeRecord challenge = new AuthChallengeRecord();
        challenge.challenge = toHex(bytes);
        challenge.edition = edition;
        challenge.hwid = hwid;
        challenge.expiresAt = System.currentTimeMillis() + CHALLENGE_TTL_MILLIS;
        state.challenges.put(challenge.challenge, challenge);
        save();
        return challenge;
    }

    public synchronized boolean approveChallenge(String challengeValue) throws IOException {
        AuthChallengeRecord challenge = state.challenges.get(challengeValue);
        if (challenge == null || challenge.expiresAt < System.currentTimeMillis()) {
            return false;
        }
        if (!challenge.approved) {
            challenge.approved = true;
            challenge.accessToken = randomToken();
            AccountRecord account = new AccountRecord();
            account.userId = state.nextUserId++;
            account.username = "User" + account.userId;
            account.accountCreation = AccountRecord.nowTimestamp();
            state.accountsByToken.put(challenge.accessToken, account);
            save();
        }
        return true;
    }

    public synchronized Optional<AuthChallengeRecord> challenge(String challengeValue) {
        return Optional.ofNullable(state.challenges.get(challengeValue));
    }

    public synchronized void register(String token, String username) throws IOException {
        AccountRecord account = requireAccount(token);
        account.registered = true;
        if (username != null && !username.trim().isEmpty()) {
            account.username = username.substring(0, Math.min(username.length(), 16));
        }
        save();
    }

    public synchronized void updateMinecraftIdentity(String token, UUID uuid, String username) throws IOException {
        AccountRecord account = requireAccount(token);
        account.minecraftUuid = uuid.toString();
        account.minecraftUsername = username;
        save();
    }

    public synchronized boolean updateDisplayName(String token, String displayName) throws IOException {
        for (Map.Entry<String, AccountRecord> entry : state.accountsByToken.entrySet()) {
            if (!entry.getKey().equals(token) && entry.getValue().username.equalsIgnoreCase(displayName)) {
                return false;
            }
        }
        requireAccount(token).username = displayName;
        save();
        return true;
    }

    public synchronized JsonObject globalSettings(String token) {
        return ConfigJsonUtils.deepCopy(requireAccount(token).globalSettings);
    }

    public synchronized void saveGlobalSettings(String token, JsonObject settings) throws IOException {
        requireAccount(token).globalSettings = ConfigJsonUtils.deepCopy(settings);
        save();
    }

    public synchronized JsonObject onlineSettings(String token) {
        return ConfigJsonUtils.deepCopy(requireAccount(token).onlineSettings);
    }

    public synchronized void saveOnlineSettings(String token, JsonObject settings) throws IOException {
        requireAccount(token).onlineSettings = ConfigJsonUtils.deepCopy(settings);
        save();
    }

    public synchronized JsonObject privateData(String token) {
        AccountRecord account = requireAccount(token);
        JsonObject data = new JsonObject();
        data.add("friends", ConfigJsonUtils.deepCopy(account.localFriends));
        JsonObject profiles = new JsonObject();
        account.privateProfiles.forEach((key, value) -> profiles.add(key, ConfigJsonUtils.deepCopy(value)));
        data.add("profiles", profiles);
        JsonObject ownedPublicProfiles = new JsonObject();
        state.publicProfiles.forEach((key, value) -> {
            if (isPublicOwner(account, value)) ownedPublicProfiles.add(key, publicProfileResponse(key, value, account));
        });
        data.add("publicProfiles", ownedPublicProfiles);
        data.add("otherData", ConfigJsonUtils.deepCopy(account.otherData));
        return data;
    }

    public synchronized void savePrivateUserData(String token, JsonElement userData) throws IOException {
        if (!userData.isJsonObject()) {
            throw new IllegalArgumentException("Private user data must be a JSON object");
        }
        JsonObject source = userData.getAsJsonObject();
        AccountRecord account = requireAccount(token);
        if (source.has("friends") && source.get("friends").isJsonArray()) {
            account.localFriends = ConfigJsonUtils.deepCopy(source.getAsJsonArray("friends"));
        }
        JsonElement otherData = source.has("otherData") ? source.get("otherData") : source.get("otherdata");
        if (otherData != null && otherData.isJsonArray()) {
            account.otherData = ConfigJsonUtils.deepCopy(otherData.getAsJsonArray());
        }
        save();
    }

    public synchronized JsonObject savePrivateProfiles(String token, JsonObject profiles) throws IOException {
        AccountRecord account = requireAccount(token);
        JsonArray deletedProfiles = profiles.getAsJsonArray("deletedProfiles");
        if (deletedProfiles != null) {
            for (JsonElement deletedProfile : deletedProfiles) {
                if (deletedProfile.isJsonPrimitive()) {
                    account.privateProfiles.remove(deletedProfile.getAsString());
                }
            }
        }

        JsonArray updatedProfiles = profiles.getAsJsonArray("updatedProfiles");
        if (updatedProfiles != null) {
            for (JsonElement updatedProfile : updatedProfiles) {
                if (!updatedProfile.isJsonObject()) {
                    continue;
                }
                JsonObject storedProfile = ConfigJsonUtils.deepCopy(updatedProfile.getAsJsonObject());
                String profileId = ConfigJsonUtils.getString(storedProfile, "profileId");
                if (profileId == null) {
                    // A response can be lost after the write. Replaying the
                    // same client UUID must update, not duplicate, that record.
                    String clientId = ConfigJsonUtils.getString(storedProfile, "uuid");
                    if (clientId != null) {
                        UUID.fromString(clientId);
                        for (Map.Entry<String, JsonObject> existing : account.privateProfiles.entrySet()) {
                            if (clientId.equalsIgnoreCase(ConfigJsonUtils.getString(existing.getValue(), "uuid"))) {
                                profileId = existing.getKey();
                                break;
                            }
                        }
                    }
                    if (profileId == null) profileId = UUID.randomUUID().toString();
                }
                UUID.fromString(profileId);
                storedProfile.addProperty("profileId", profileId);
                account.privateProfiles.put(profileId, storedProfile);
            }
        }
        save();
        JsonObject result = new JsonObject();
        account.privateProfiles.forEach((key, value) -> result.add(key, ConfigJsonUtils.deepCopy(value)));
        return result;
    }

    public synchronized String reserveProfileId(String token) throws IOException {
        requireAccount(token);
        String id = UUID.randomUUID().toString();
        save();
        return id;
    }

    public synchronized JsonObject publicProfiles() {
        JsonObject profiles = new JsonObject();
        state.publicProfiles.forEach((key, value) -> profiles.add(key, ConfigJsonUtils.deepCopy(value)));
        return profiles;
    }

    /** Create or partially edit an owned publication without replacing omitted fields. */
    public synchronized JsonObject savePublicProfile(String token, JsonObject changes, boolean edit) throws IOException {
        AccountRecord account = requireAccount(token);
        String id;
        JsonObject stored;
        if (edit) {
            String requested = ConfigJsonUtils.getString(changes, "profileId");
            if (requested == null) throw new IllegalArgumentException("Missing profileId");
            id = Long.toString(Long.parseLong(requested));
            JsonObject previous = state.publicProfiles.get(id);
            if (previous == null || !isPublicOwner(account, previous)) {
                throw new IllegalArgumentException("Profile not found or not owned by this account");
            }
            stored = ConfigJsonUtils.deepCopy(previous);
        } else {
            if (ConfigJsonUtils.getString(changes, "name") == null || ConfigJsonUtils.getJsonObject(changes, "profileData") == null) {
                throw new IllegalArgumentException("A name and profileData are required");
            }
            id = Long.toString(state.nextPublicProfileId++);
            stored = new JsonObject();
            stored.addProperty("id", id);
            stored.addProperty("userId", account.userId);
        }
        if (changes.has("profileData") && ConfigJsonUtils.getJsonObject(changes, "profileData") == null) {
            throw new IllegalArgumentException("profileData must be an object");
        }
        if (changes.has("tags") && ConfigJsonUtils.getJsonArray(changes, "tags") == null) {
            throw new IllegalArgumentException("tags must be an array");
        }
        if (changes.has("tags")) for (JsonElement tag : changes.getAsJsonArray("tags")) {
            if (!tag.isJsonPrimitive() || !tag.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("tags must contain strings");
        }
        for (String field : new String[]{"name", "description", "vapeVersion"}) {
            if (changes.has(field) && (!changes.get(field).isJsonPrimitive() || !changes.get(field).getAsJsonPrimitive().isString())) {
                throw new IllegalArgumentException(field + " must be a string");
            }
        }
        for (String field : new String[]{"listed", "anonymous", "shareCodeFriendsOnly"}) {
            if (changes.has(field) && (!changes.get(field).isJsonPrimitive() || !changes.get(field).getAsJsonPrimitive().isBoolean())) {
                throw new IllegalArgumentException(field + " must be a boolean");
            }
        }
        String derivedFrom = ConfigJsonUtils.getString(changes, "derivedFrom");
        if (derivedFrom != null) UUID.fromString(derivedFrom);
        for (String field : new String[]{"name", "description", "tags", "listed", "anonymous",
                "shareCodeFriendsOnly", "derivedFrom", "profileData", "vapeVersion"}) {
            if (changes.has(field) && (!changes.get(field).isJsonNull() || "derivedFrom".equals(field))) {
                stored.add(field, ConfigJsonUtils.deepCopy(changes.get(field)));
            }
        }
        ensurePublicDefaults(id, stored);
        if (edit) {
            stored.addProperty("version", stored.get("version").getAsLong() + 1L);
            stored.addProperty("updatedDate", AccountRecord.nowTimestamp());
        }
        state.publicProfiles.put(id, stored);
        save();
        return publicProfileResponse(id, stored, account);
    }

    private void ensurePublicDefaults(String id, JsonObject stored) {
        if (!stored.has("id")) stored.addProperty("id", id);
        if (ConfigJsonUtils.getString(stored, "name") == null) stored.addProperty("name", "Untitled");
        if (ConfigJsonUtils.getString(stored, "description") == null) stored.addProperty("description", "");
        if (ConfigJsonUtils.getString(stored, "vapeVersion") == null) stored.addProperty("vapeVersion", "4.21");
        if (ConfigJsonUtils.getJsonArray(stored, "tags") == null) stored.add("tags", new JsonArray());
        if (ConfigJsonUtils.getJsonObject(stored, "profileData") == null) stored.add("profileData", new JsonObject());
        if (ConfigJsonUtils.getBoolean(stored, "listed") == null) stored.addProperty("listed", true);
        if (ConfigJsonUtils.getBoolean(stored, "anonymous") == null) stored.addProperty("anonymous", false);
        if (ConfigJsonUtils.getBoolean(stored, "shareCodeFriendsOnly") == null) stored.addProperty("shareCodeFriendsOnly", false);
        if (ConfigJsonUtils.getLong(stored, "version") == null) stored.addProperty("version", 1L);
        if (ConfigJsonUtils.getLong(stored, "likes") == null) stored.addProperty("likes", 0L);
        if (ConfigJsonUtils.getLong(stored, "dislikes") == null) stored.addProperty("dislikes", 0L);
        if (ConfigJsonUtils.getLong(stored, "downloads") == null) stored.addProperty("downloads", 0L);
        if (ConfigJsonUtils.getString(stored, "creationDate") == null) stored.addProperty("creationDate", AccountRecord.nowTimestamp());
        if (ConfigJsonUtils.getString(stored, "shareCode") == null) stored.addProperty("shareCode", newShareCode());
    }

    private String newShareCode() {
        String candidate;
        boolean used;
        do {
            candidate = UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
            used = false;
            for (JsonObject existing : state.publicProfiles.values()) {
                if (candidate.equalsIgnoreCase(ConfigJsonUtils.getString(existing, "shareCode"))) { used = true; break; }
            }
        } while (used);
        return candidate;
    }

    private static boolean isPublicOwner(AccountRecord account, JsonObject stored) {
        Long owner = ConfigJsonUtils.getLong(stored, "userId");
        return owner != null && owner.longValue() == account.userId;
    }

    private boolean canViewPublic(AccountRecord account, JsonObject stored) {
        Long owner = ConfigJsonUtils.getLong(stored, "userId");
        return !ConfigJsonUtils.getBooleanOrFalse(stored, "shareCodeFriendsOnly")
                || isPublicOwner(account, stored) || (owner != null && areFriends(account.userId, owner));
    }

    private static JsonObject emptyPublicReviews() {
        JsonObject page = new JsonObject();
        page.add("content", new JsonArray());
        page.addProperty("last", true);
        page.addProperty("totalPages", 0);
        page.addProperty("totalElements", 0);
        page.addProperty("size", 20);
        page.addProperty("numberOfElements", 0);
        return page;
    }

    private JsonObject publicProfileResponse(String id, JsonObject stored, AccountRecord viewer) {
        JsonObject result = new JsonObject();
        result.addProperty("profileId", Long.parseLong(id));
        Long ownerId = ConfigJsonUtils.getLong(stored, "userId");
        AccountRecord owner = ownerId == null ? null : account(ownerId).orElse(null);
        if (owner != null && (!ConfigJsonUtils.getBooleanOrFalse(stored, "anonymous") || isPublicOwner(viewer, stored))) {
            JsonObject ownerJson = new JsonObject();
            ownerJson.addProperty("userId", owner.userId);
            ownerJson.addProperty("username", owner.username);
            result.add("owner", ownerJson);
        } else result.add("owner", JsonNull.INSTANCE);
        for (String field : new String[]{"name", "description", "tags", "shareCode", "version", "likes", "dislikes", "downloads", "creationDate", "updatedDate"}) {
            result.add(field, stored.has(field) ? ConfigJsonUtils.deepCopy(stored.get(field)) : JsonNull.INSTANCE);
        }
        result.add("data", ConfigJsonUtils.deepCopy(stored.getAsJsonObject("profileData")));
        result.add("reviews", emptyPublicReviews());
        if (isPublicOwner(viewer, stored)) {
            JsonObject metadata = new JsonObject();
            metadata.add("derivedFrom", stored.has("derivedFrom") ? ConfigJsonUtils.deepCopy(stored.get("derivedFrom")) : JsonNull.INSTANCE);
            metadata.addProperty("unreadNotifications", 0);
            metadata.addProperty("shareCode", stored.get("shareCode").getAsString());
            metadata.addProperty("listedPublicly", stored.get("listed").getAsBoolean());
            metadata.addProperty("shareCodeFriendsOnly", stored.get("shareCodeFriendsOnly").getAsBoolean());
            metadata.addProperty("uploadAnonymously", stored.get("anonymous").getAsBoolean());
            result.add("metadata", metadata);
        } else result.add("metadata", JsonNull.INSTANCE);
        return result;
    }

    public synchronized Optional<JsonObject> publicProfile(String token, String id) {
        AccountRecord viewer = requireAccount(token);
        JsonObject stored = state.publicProfiles.get(id);
        return stored == null || !canViewPublic(viewer, stored) ? Optional.empty()
                : Optional.of(publicProfileResponse(id, stored, viewer));
    }

    public synchronized JsonObject listPublicProfiles(String token, JsonObject query) {
        AccountRecord viewer = requireAccount(token);
        String search = ConfigJsonUtils.getString(query, "search");
        search = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        JsonArray tags = ConfigJsonUtils.getJsonArray(query, "tags");
        List<JsonObject> matches = new ArrayList<JsonObject>();
        for (Map.Entry<String, JsonObject> entry : state.publicProfiles.entrySet()) {
            JsonObject stored = entry.getValue();
            if (!canViewPublic(viewer, stored)) continue;
            boolean owner = isPublicOwner(viewer, stored);
            boolean codeMatch = !search.isEmpty() && stored.get("shareCode").getAsString().equalsIgnoreCase(search);
            if (!owner && !stored.get("listed").getAsBoolean() && !codeMatch) continue;
            if (!search.isEmpty() && !codeMatch && !stored.get("name").getAsString().toLowerCase(Locale.ROOT).contains(search)) continue;
            boolean containsTags = true;
            if (tags != null) for (JsonElement tag : tags) {
                boolean found = false;
                for (JsonElement existing : stored.getAsJsonArray("tags")) {
                    if (existing.getAsString().equalsIgnoreCase(tag.getAsString())) { found = true; break; }
                }
                if (!found) { containsTags = false; break; }
            }
            if (containsTags) matches.add(publicProfileResponse(entry.getKey(), stored, viewer));
        }
        String mode = ConfigJsonUtils.getString(query, "mode");
        String sortField = "downloaded".equals(mode) ? "downloads" : "rated".equals(mode) ? "likes" : "profileId";
        matches.sort((a, b) -> Long.compare(b.get(sortField).getAsLong(), a.get(sortField).getAsLong()));
        Long requestedPage = ConfigJsonUtils.getLong(query, "page");
        long pageNumber = requestedPage == null ? 0L : Math.max(0L, requestedPage);
        int start = pageNumber > matches.size() / 20 ? matches.size() : (int)pageNumber * 20;
        int end = Math.min(start + 20, matches.size());
        JsonArray content = new JsonArray();
        for (int i = start; i < end; ++i) content.add(matches.get(i));
        JsonObject page = new JsonObject();
        page.add("content", content);
        page.addProperty("last", end == matches.size());
        page.addProperty("totalPages", (matches.size() + 19) / 20);
        page.addProperty("totalElements", matches.size());
        page.addProperty("size", 20);
        page.addProperty("numberOfElements", end - start);
        return page;
    }

    public synchronized JsonObject regeneratePublicShareCode(String token, String id) throws IOException {
        AccountRecord account = requireAccount(token);
        JsonObject stored = state.publicProfiles.get(id);
        if (stored == null || !isPublicOwner(account, stored)) throw new IllegalArgumentException("Profile not found or not owned by this account");
        stored.addProperty("shareCode", newShareCode());
        save();
        return publicProfileResponse(id, stored, account).getAsJsonObject("metadata");
    }

    /** Download and update keep the viewer's private-profile IDs stable. */
    public synchronized Optional<JsonObject> downloadPublicProfile(String token, String id, boolean update) throws IOException {
        AccountRecord viewer = requireAccount(token);
        JsonObject stored = state.publicProfiles.get(id);
        if (stored == null || !canViewPublic(viewer, stored)) return Optional.empty();
        JsonObject copy = null;
        for (JsonObject privateProfile : viewer.privateProfiles.values()) {
            JsonObject metadata = ConfigJsonUtils.getJsonObject(privateProfile, "metadata");
            Long publicId = metadata == null ? null : ConfigJsonUtils.getLong(metadata, "publicProfileId");
            if (publicId != null && publicId.longValue() == Long.parseLong(id)) { copy = privateProfile; break; }
        }
        if (copy == null && update) return Optional.empty();
        boolean firstDownload = copy == null;
        if (copy == null) {
            copy = new JsonObject();
            copy.addProperty("uuid", UUID.randomUUID().toString());
            copy.addProperty("profileId", UUID.randomUUID().toString());
        } else copy = ConfigJsonUtils.deepCopy(copy);
        copy.add("name", ConfigJsonUtils.deepCopy(stored.get("name")));
        copy.add("vapeVersion", ConfigJsonUtils.deepCopy(stored.get("vapeVersion")));
        copy.add("data", ConfigJsonUtils.deepCopy(stored.get("profileData")));
        JsonObject metadata = new JsonObject();
        metadata.addProperty("publicProfileId", Long.parseLong(id));
        metadata.addProperty("version", stored.get("version").getAsLong());
        metadata.addProperty("publishedVersion", stored.get("version").getAsLong());
        copy.add("metadata", metadata);
        viewer.privateProfiles.put(copy.get("profileId").getAsString(), copy);
        if (firstDownload) stored.addProperty("downloads", stored.get("downloads").getAsLong() + 1L);
        save();
        return Optional.of(ConfigJsonUtils.deepCopy(copy));
    }

    public synchronized boolean deletePublicProfile(String token, String id) throws IOException {
        AccountRecord account = requireAccount(token);
        JsonObject profile = state.publicProfiles.get(id);
        if (profile == null || !profile.has("userId") || profile.get("userId").getAsLong() != account.userId) {
            return false;
        }
        state.publicProfiles.remove(id);
        save();
        return true;
    }

    private AccountRecord requireAccount(String token) {
        AccountRecord account = state.accountsByToken.get(token);
        if (account == null) {
            throw new IllegalArgumentException("Invalid access token");
        }
        return account;
    }

    private String randomToken() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return toHex(bytes);
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(Character.forDigit((value >> 4) & 0xf, 16));
            builder.append(Character.forDigit(value & 0xf, 16));
        }
        return builder.toString();
    }

    private ServiceState load() throws IOException {
        if (!Files.exists(file)) {
            return new ServiceState();
        }
        String json = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        JsonElement root = new JsonParser().parse(json);
        if (root == null || root.isJsonNull()) return new ServiceState();
        if (root.isJsonObject()) {
            JsonElement accounts = root.getAsJsonObject().get("accountsByToken");
            if (accounts != null && accounts.isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : accounts.getAsJsonObject().entrySet()) {
                    if (!entry.getValue().isJsonObject()) continue;
                    JsonObject account = entry.getValue().getAsJsonObject();
                    // Gson 2.2.4 does not support SerializedName.alternate.
                    // Prefer the modern field when both forms are present.
                    if (!account.has("localFriends") && account.has("friends")) {
                        account.add("localFriends", account.get("friends"));
                    }
                }
            }
        }
        ServiceState loaded = gson.fromJson(root, ServiceState.class);
        return loaded == null ? new ServiceState() : loaded;
    }

    private void normalizeLoadedState() {
        if (state.accountsByToken == null) {
            state.accountsByToken = new LinkedHashMap<>();
        }
        if (state.challenges == null) {
            state.challenges = new LinkedHashMap<>();
        }
        if (state.publicProfiles == null) {
            state.publicProfiles = new LinkedHashMap<>();
        }
        for (Map.Entry<String, JsonObject> entry : state.publicProfiles.entrySet()) {
            ensurePublicDefaults(entry.getKey(), entry.getValue());
        }
        if (state.friendRequests == null) {
            state.friendRequests = new LinkedHashMap<>();
        }
        if (state.parties == null) {
            state.parties = new LinkedHashMap<>();
        }
        for (AccountRecord account : state.accountsByToken.values()) {
            account.accountCreation = AccountRecord.normalizeTimestamp(account.accountCreation);
            if (account.onlineFriends == null) {
                account.onlineFriends = new java.util.LinkedHashSet<>();
            }
            if (account.localFriends == null) {
                account.localFriends = new JsonArray();
            }
            if (account.otherData == null) {
                account.otherData = new JsonArray();
            }
            if (account.privateProfiles == null) {
                account.privateProfiles = new LinkedHashMap<>();
            }
        }
    }

    private void save() throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(temporary, gson.toJson(state).getBytes(StandardCharsets.UTF_8));
        try {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
