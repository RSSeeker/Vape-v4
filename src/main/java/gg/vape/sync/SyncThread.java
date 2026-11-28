package gg.vape.sync;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import gg.vape.Vape;
import gg.vape.account.AccountInfo;
import gg.vape.api.ApiHttpClient;
import gg.vape.api.ApiHttpStatusException;
import gg.vape.api.ApiResponse;
import gg.vape.api.ApiServices;
import gg.vape.api.UserDataResponse;
import gg.vape.config.ConfigJsonUtils;
import gg.vape.config.LocalConfigStore;
import gg.vape.config.Profile;
import gg.vape.config.SettingsDataType;
import gg.vape.event.impl.EventTickBase;
import gg.vape.manager.client.OnlineConnectionManager;
import gg.vape.manager.client.ProfilesManager;
import gg.vape.notification.SettingsSyncStatusNotification;
import gg.vape.runtime.NativeBridge;
import gg.vape.utils.Base64Util;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class SyncThread {
    private SyncStoreRequestWorker storeRequestWorker;
    private final SyncDebounceWorker debounceWorker;
    private final AtomicBoolean pendingSave = new AtomicBoolean();
    private final Object localSaveLock = new Object();
    private final Vape vape;
    private volatile long lastSaveTime;
    // Guarded by this; unlike a timestamp, two edits in one millisecond differ.
    private long changeRevision;
    private long nextAutomaticRetryTime;
    private boolean onlineSettingsApplied;

    public SyncThread(Vape vape) {
        this.vape = vape;
        this.debounceWorker = new SyncDebounceWorker();
        Runtime.getRuntime().addShutdownHook(
                new Thread(this::saveLocalSnapshot, "InevitableClient local config save"));
    }

    public void saveSettings() {
        try {
            SettingsSyncStatusNotification notification = new SettingsSyncStatusNotification();
            if (!this.vape.getPublicProfileSettings().autoSave.getEffectiveValue()) {
                this.vape.getNotificationManager().enqueue(notification, true);
            }

            this.syncOnlineSettings();
            JsonObject settingsPayload;
            JsonObject profilesPayload;
            JsonObject localProfilesPayload;
            JsonObject pendingProfileSync;
            Map<Profile, Long> sentRevisions = new IdentityHashMap<Profile, Long>();
            Map<Profile, String> sentNames = new IdentityHashMap<Profile, String>();
            Map<Profile, UUID> sentOnlineIds = new IdentityHashMap<Profile, UUID>();
            Map<UUID, Long> sentDeletions;
            boolean localSaveOk;
            // The shutdown hook can save too. Serialize capture and local I/O
            // so an older capture cannot overwrite a newer shutdown snapshot.
            synchronized (this.localSaveLock) {
                synchronized (this.vape.getProfilesManager()) {
                    // Record revisions before capture: a concurrent live edit
                    // during or after capture must remain dirty for another save.
                    for (Profile profile : this.vape.getProfilesManager().getProfiles()) {
                        if (profile.isDirty()) sentRevisions.put(profile, profile.getDirtyRevision());
                    }
                    this.prepareActiveProfileForSave();
                    settingsPayload = this.buildSettingsPayload(true);
                    sentDeletions = this.vape.getProfilesManager().getPendingDeletionRevisions();
                    profilesPayload = this.vape.getProfilesManager().toJson(true);
                    // Correlate the response with exactly what was sent, not
                    // the profile's possibly changed name after the request.
                    for (com.google.gson.JsonElement element : profilesPayload.getAsJsonArray("updatedProfiles")) {
                        JsonObject sent = element.getAsJsonObject();
                        Profile profile = this.vape.getProfilesManager().getProfileByLocalId(UUID.fromString(sent.get("uuid").getAsString()));
                        if (profile == null) continue;
                        sentNames.put(profile, sent.get("name").getAsString());
                        String id = ConfigJsonUtils.getString(sent, "profileId");
                        sentOnlineIds.put(profile, id == null ? null : UUID.fromString(id));
                    }
                    localProfilesPayload = this.buildLocalProfilesPayload();
                    pendingProfileSync = this.vape.getProfilesManager().pendingSyncState();
                    for (Profile profile : this.vape.getProfilesManager().getProfiles()) {
                        profile.setSaveQueued(true);
                    }
                }
                localSaveOk = LocalConfigStore.saveSettingsAndProfiles(settingsPayload, localProfilesPayload, pendingProfileSync);
            }

            ApiResponse<Boolean> settingsResponse = ApiServices.getInstance().getUserDataApi().saveUserData(settingsPayload)
                    .exceptionally(error -> handleSettingsSaveFailure(notification, error))
                    .join();
            this.updateSettingsSaveStatus(notification, settingsResponse);

            ApiResponse<RemoteProfileDataMap> profilesResponse = ApiServices.getInstance().getUserDataApi().saveProfileData(profilesPayload)
                    .exceptionally(error -> handleProfilesSaveFailure(notification, error))
                    .join();
            synchronized (this.vape.getProfilesManager()) {
                boolean needsFollowUp = this.applySavedProfileIds(profilesResponse, sentNames, sentOnlineIds);
                this.updateProfilesSaveStatus(notification, profilesResponse, sentRevisions, sentDeletions);
                if (profilesResponse == null || !profilesResponse.isSuccessful()
                        || settingsResponse == null || !settingsResponse.isSuccessful()) {
                    this.scheduleRetry();
                } else if (needsFollowUp) {
                    this.scheduleRetry();
                }
            }
            if (profilesResponse != null && profilesResponse.isSuccessful()) {
                // Commit acknowledged intents and newly assigned IDs locally as
                // well; replay after a crash is otherwise possible.
                this.saveLocalSnapshot();
            }
            if (!localSaveOk) {
                notification.setSettingsSaveStatus(4);
                notification.setApiErrorDetail("Could not write the local settings snapshot.");
                this.scheduleRetry();
            }

            notification.complete();
            if (notification.hasSaveError()
                    && this.vape.getPublicProfileSettings().autoSave.getEffectiveValue()
                    && !localSaveOk) {
                this.vape.getNotificationManager().show(notification);
            }
        }
        catch (Exception exception) {
            this.scheduleRetry();
            Vape.logThrowable(exception);
        }
        // requestSave() consumes the pending flag before the worker starts.
        // A new change during this save must remain pending for the next pass.
    }

    /**
     * Writes the current settings/profiles to the local config file without
     * touching the online API. Used by the JVM shutdown hook so changes made
     * with the GUI closed are still persisted.
     */
    public void saveLocalSnapshot() {
        try {
            synchronized (this.localSaveLock) {
                JsonObject settings;
                JsonObject profiles;
                JsonObject pendingProfileSync;
                synchronized (this.vape.getProfilesManager()) {
                    this.prepareActiveProfileForSave();
                    settings = this.buildSettingsPayload(true);
                    profiles = this.buildLocalProfilesPayload();
                    pendingProfileSync = this.vape.getProfilesManager().pendingSyncState();
                }
                LocalConfigStore.saveSettingsAndProfiles(settings, profiles, pendingProfileSync);
            }
        }
        catch (Throwable ignored) {
        }
    }

    private JsonObject buildLocalProfilesPayload() {
        JsonObject localProfiles = new JsonObject();
        for (Profile profile : this.vape.getProfilesManager().getProfiles()) {
            localProfiles.add(profile.getLocalId().toString(),
                    profile.toJson(false));
        }
        return localProfiles;
    }

    private JsonObject normalizeLocalProfilesPayload(JsonObject payload) {
        if (!payload.has("updatedProfiles")) {
            return payload;
        }
        com.google.gson.JsonArray updated =
                payload.getAsJsonArray("updatedProfiles");
        JsonObject normalized = new JsonObject();
        for (com.google.gson.JsonElement element : updated) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject profile = element.getAsJsonObject();
            String uuid = ConfigJsonUtils.getString(profile, "uuid");
            if (uuid == null) {
                continue;
            }
            normalized.add(uuid, profile);
        }
        return normalized;
    }

    private void prepareActiveProfileForSave() {
        try {
            this.vape.getProfilesManager().captureActiveProfile();
        }
        catch (Throwable throwable) {
            Vape.logThrowable(throwable);
        }
    }

    private void updateSettingsSaveStatus(SettingsSyncStatusNotification notification, ApiResponse<Boolean> response) {
        if (response == null) {
            if (notification.getSettingsSaveStatus() == 1) {
                notification.setSettingsSaveStatus(5);
            }
            return;
        }
        if (response.isSuccessful()) {
            notification.setSettingsSaveStatus(1);
        } else if (isRateLimited(response)) {
            notification.setSettingsSaveStatus(3);
        } else {
            notification.setSettingsSaveStatus(4);
        }
    }

    private void updateProfilesSaveStatus(SettingsSyncStatusNotification notification, ApiResponse<RemoteProfileDataMap> response,
                                          Map<Profile, Long> sentRevisions, Map<UUID, Long> sentDeletions) {
        if (response == null) {
            if (notification.getProfilesSaveStatus() == 1) {
                notification.setProfilesSaveStatus(5);
            }
            return;
        }
        if (response.isSuccessful()) {
            notification.setProfilesSaveStatus(1);
            for (Map.Entry<Profile, Long> entry : sentRevisions.entrySet()) {
                Profile profile = entry.getKey();
                // Without a server ID a new profile cannot safely be updated
                // next time. Leave it dirty if the response could not be matched.
                if (this.vape.getProfilesManager().getProfiles().contains(profile) && profile.getOnlineId() == null) {
                    notification.setProfilesSaveStatus(4);
                    notification.setApiErrorDetail("The server did not identify a saved profile.");
                    continue;
                }
                profile.clearDirtyIfUnchanged(entry.getValue());
            }
            this.vape.getProfilesManager().acknowledgeDeletions(sentDeletions);
        } else if (isRateLimited(response)) {
            notification.setProfilesSaveStatus(3);
        } else {
            notification.setProfilesSaveStatus(4);
            notification.setApiErrorDetail(response.getError());
        }
    }

    /** Match newly assigned IDs to the submitted snapshot, including removed profiles. */
    private boolean applySavedProfileIds(ApiResponse<RemoteProfileDataMap> response,
                                         Map<Profile, String> sentNames, Map<Profile, UUID> sentOnlineIds) {
        if (response == null || !response.isSuccessful()) return false;
        RemoteProfileDataMap data = response.getData();
        ProfilesManager manager = this.vape.getProfilesManager();
        boolean followUp = false;
        for (Map.Entry<Profile, String> sent : sentNames.entrySet()) {
            Profile profile = sent.getKey();
            if (sentOnlineIds.get(profile) != null) continue;
            RemoteProfileData saved = data == null ? null : findSavedProfile(profile, sent.getValue(), sentNames, data);
            UUID savedId = saved == null ? null : saved.getProfileId();
            Profile currentOwner = savedId == null ? null : manager.getProfileByOnlineId(savedId);
            if (currentOwner != null && currentOwner != profile) savedId = null;
            if (!manager.getProfiles().contains(profile)) {
                // Deleting by name alone could delete another existing record.
                // Only enqueue this orphan when its client UUID confirms it.
                if (savedId != null && profile.getLocalId().equals(saved.getUuid())) {
                    followUp |= manager.queueRemoteDeletion(savedId);
                }
                continue;
            }
            UUID currentId = profile.getOnlineId();
            if (currentId == null && savedId != null) {
                profile.setOnlineId(savedId);
            } else if (currentId == null || (savedId != null && !currentId.equals(savedId))) {
                // A late reservation or an incomplete response must not clear
                // the pending update. A confirmed duplicate is removed later.
                if (savedId != null && profile.getLocalId().equals(saved.getUuid())) {
                    manager.queueRemoteDeletion(savedId);
                }
                profile.setDirty(true);
                followUp = true;
            }
        }
        return followUp;
    }

    private static RemoteProfileData findSavedProfile(Profile profile, String sentName,
                                                       Map<Profile, String> sentNames, RemoteProfileDataMap data) {
        RemoteProfileData match = null;
        for (RemoteProfileData candidate : data.getProfiles().values()) {
            if (candidate == null || candidate.getProfileId() == null || !profile.getLocalId().equals(candidate.getUuid())) continue;
            if (match != null) return null;
            match = candidate;
        }
        if (match != null) return match;
        // Some older servers do not echo the client UUID. Use the submitted
        // name only if it identifies exactly one request and one response,
        // and never contradicts an explicit UUID from the server.
        for (RemoteProfileData candidate : data.getProfiles().values()) {
            if (candidate == null || candidate.getProfileId() == null || !matchesProfileName(sentName, candidate)) continue;
            if (candidate.getUuid() != null || match != null) return null;
            match = candidate;
        }
        if (match == null) return null;
        int matchingRequests = 0;
        for (String name : sentNames.values()) if (matchesProfileName(name, match)) ++matchingRequests;
        return matchingRequests == 1 ? match : null;
    }

    private static boolean matchesProfileName(String name, RemoteProfileData remoteProfile) {
        String remoteName = remoteProfile.getName();
        return name != null && remoteName != null && (remoteName.equals(name)
                || remoteName.equals("b64:" + Base64Util.encodeUtf8Base64(name)));
    }

    private static boolean isRateLimited(ApiResponse<?> response) {
        String error = response.getError();
        return error != null && (error.contains("Slow down") || error.contains("Rate limited"));
    }

    private static ApiResponse<Boolean> handleSettingsSaveFailure(SettingsSyncStatusNotification notification, Throwable error) {
        Throwable cause = rootCause(error);
        if (cause instanceof ApiHttpStatusException) {
            notification.setSettingsSaveStatus(((ApiHttpStatusException)cause).getStatusCode());
        } else {
            notification.setSettingsSaveStatus(2);
        }
        return null;
    }

    private static ApiResponse<RemoteProfileDataMap> handleProfilesSaveFailure(SettingsSyncStatusNotification notification, Throwable error) {
        Throwable cause = rootCause(error);
        if (cause instanceof ApiHttpStatusException) {
            notification.setProfilesSaveStatus(((ApiHttpStatusException)cause).getStatusCode());
        } else {
            notification.setProfilesSaveStatus(2);
            if (cause instanceof IOException) {
                notification.setIoExceptionClassName(cause.getClass().getName());
                notification.setIoExceptionMessage(cause.getMessage());
            }
        }
        return null;
    }

    private static Throwable rootCause(Throwable throwable) {
        while (throwable.getCause() != null) {
            throwable = throwable.getCause();
        }
        return throwable;
    }

    public synchronized void requestSave() {
        this.lastSaveTime = System.currentTimeMillis();
        this.nextAutomaticRetryTime = 0L;
        this.pendingSave.set(false);
        if (this.storeRequestWorker == null) {
            this.storeRequestWorker = new SyncStoreRequestWorker();
            new Thread(this.storeRequestWorker, "InevitableClient settings save worker").start();
        }
        this.storeRequestWorker.requestSave();
    }

    synchronized long getChangeRevision() {
        return this.changeRevision;
    }

    synchronized void requestSaveIfUnchanged(long revision) {
        if (this.pendingSave.get() && this.changeRevision == revision
                && System.currentTimeMillis() >= this.nextAutomaticRetryTime) this.requestSave();
    }

    /** Keep failed saves pending, but do not hammer an offline or rate-limited service. */
    private synchronized void scheduleRetry() {
        this.nextAutomaticRetryTime = System.currentTimeMillis() + 30000L;
        this.markDirty();
    }

    public boolean hasPendingSave() {
        return this.pendingSave.get();
    }

    public long getLastSaveTime() {
        return this.lastSaveTime;
    }

    public void loadConfig() {
        try {
            JsonObject localSettings = LocalConfigStore.loadSettings();
            if (localSettings != null) {
                JsonObject config = new JsonObject();
                if (localSettings.has("friends")) {
                    config.add("friends", localSettings.get("friends"));
                }
                if (localSettings.has("otherData")) {
                    config.add("otherData", localSettings.get("otherData"));
                }
                JsonObject localProfiles = LocalConfigStore.loadProfiles();
                if (localProfiles != null) {
                    config.add("profiles",
                            this.normalizeLocalProfilesPayload(localProfiles));
                }
                this.vape.loadConfigData(config, true);
                this.vape.getProfilesManager().restorePendingSyncState(LocalConfigStore.loadPendingProfileSync());
                return;
            }
            // 全新注入（本地没有配置文件，只会读到原生存储 / 云端等其他目录的配置）：
            // 不沿用那边保存的分类面板打开状态 —— 各分类默认保持白色（关闭），
            // 恢复完成后把"关闭"写回当前档案，之后就一直是白色。
            gg.vape.module.none.ClientSettings.suppressStoredCategoryFrameVisibility = true;
            AccountInfo accountInfo = this.vape.getAccountInfo();
            if (accountInfo != null && accountInfo.hasProfilesEnabled()) {
                this.loadRemoteConfig();
            } else {
                this.loadStandaloneConfig();
            }
            gg.vape.module.none.ClientSettings.suppressStoredCategoryFrameVisibility = false;
            EventTickBase.POST_TICK_EXECUTOR.execute(this::finalizeFreshInstallFrameState);
        }
        catch (Throwable ignored) {
        }
    }

    private void finalizeFreshInstallFrameState() {
        gg.vape.module.none.ClientSettings.closeAllCategoryFrames();
        Profile activeProfile = this.vape.getProfilesManager().captureActiveProfile();
        if (activeProfile != null) activeProfile.setDirty(true);
        this.vape.saveAndStop();
    }

    private void loadRemoteConfig() {
        ApiResponse<UserDataResponse> response = ApiServices.getInstance().getUserDataApi().getUserData()
                .exceptionally(error -> null)
                .join();
        if (response == null || !response.isSuccessful()) {
            return;
        }
        UserDataResponse userData = response.getData();
        assert userData != null;
        if (userData == null) {
            return;
        }

        HashMap<UUID, JsonObject> profiles = new HashMap<UUID, JsonObject>();
        for (RemoteProfileData remoteProfile : userData.getProfiles().values()) {
            profiles.put(remoteProfile.getProfileId(), remoteProfile.toJson());
        }
        this.vape.getPublicProfileManager().replaceProfiles(userData.getPublicProfiles().values());

        JsonObject config = new JsonObject();
        if (userData.getFriends() != null) {
            config.add("friends", userData.getFriends());
        }
        config.add("profiles", ApiHttpClient.GSON.toJsonTree(profiles));
        if (userData.getOtherData() != null) {
            config.add("otherData", userData.getOtherData());
        }
        this.vape.loadConfigData(config, true);
    }

    private void loadStandaloneConfig() {
        String encodedSettings = NativeBridge.gp("all");
        String decodedSettings = encodedSettings == null
                ? ""
                : new String(Base64Util.decodeBase64(encodedSettings), StandardCharsets.UTF_8).trim();
        JsonReader reader = new JsonReader(new StringReader(decodedSettings));
        reader.setLenient(true);
        JsonObject config = new Gson().fromJson(reader, JsonObject.class);
        if (config == null) {
            return;
        }
        this.vape.loadConfigData(config, false);
        for (Profile profile : this.vape.getProfilesManager().getProfiles()) {
            profile.setDirty(true);
        }
    }

    public synchronized void clearPendingSave() {
        this.pendingSave.set(false);
    }

    public synchronized void markDirty() {
        ++this.changeRevision;
        this.pendingSave.set(true);
    }

    /** Atomically mark the current profile and queue its save relative to capture. */
    public void markSettingsDirty() {
        ProfilesManager manager = this.vape.getProfilesManager();
        if (manager == null) {
            this.markDirty();
            return;
        }
        synchronized (manager) {
            Profile active = manager.getActiveProfileOrNull();
            if (active != null) active.setDirty(true);
            this.markDirty();
        }
    }

    public void start() {
        new Thread(this.debounceWorker, "InevitableClient settings sync worker").start();
    }

    private void syncOnlineSettings() {
        if (!OnlineConnectionManager.INSTANCE.getSettings().hasLoadFailed()) {
            try {
                ApiServices.getInstance().getSettingsApi().saveSettings(SettingsDataType.ONLINE, OnlineConnectionManager.INSTANCE.getSettings().getPayload());
            }
            catch (Exception ignored) {
            }
        }
        try {
            boolean[] onlineSettingsState = NativeBridge.gls();
            boolean shouldApplySettings = onlineSettingsState[0];
            boolean secondaryState = onlineSettingsState[1];
            if (!this.onlineSettingsApplied && shouldApplySettings) {
                OnlineConnectionManager.INSTANCE.getGlobalSettingsController().getCacheData().setPersistenceSuppressed(true);
                OnlineConnectionManager.INSTANCE.getGlobalSettingsController().getCacheData().setValue(secondaryState);
                OnlineConnectionManager.INSTANCE.getGlobalSettingsController().getCacheData().setPersistenceSuppressed(false);
                this.onlineSettingsApplied = true;
            }
            OnlineConnectionManager.INSTANCE.getGlobalSettingsController().save();
        }
        catch (Throwable ignored) {
        }
    }

    public JsonObject buildSettingsPayload(boolean splitProfiles) {
        JsonObject payload = new JsonObject();
        payload.add("friends", this.vape.getFriendManager().toJson());
        payload.add(splitProfiles ? "otherData" : "otherdata", this.vape.getSettingsManager().toJson());
        if (!splitProfiles) {
            payload.add("profiles", this.vape.getProfilesManager().toJson(false));
        }
        return payload;
    }
}
