package gg.vape.sync;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gg.vape.Vape;
import gg.vape.api.ApiResponse;
import gg.vape.api.ApiServices;
import gg.vape.config.LocalConfigStore;
import gg.vape.config.Profile;
import gg.vape.manager.client.ProfilesManager;
import gg.vape.module.Mod;
import gg.vape.notification.SettingsSyncStatusNotification;
import gg.vape.value.StringValue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Real queue, sync and disk code with deterministic fake network responses. */
public final class SyncIntegrationTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static CompletableFuture<ApiResponse<RemoteProfileDataMap>> reply() {
        CompletableFuture<ApiResponse<RemoteProfileDataMap>> result = new CompletableFuture<ApiResponse<RemoteProfileDataMap>>();
        ApiServices.INSTANCE.userData.replies.add(result);
        return result;
    }

    private static JsonObject sent() throws Exception {
        JsonObject result = ApiServices.INSTANCE.userData.sent.poll(4, TimeUnit.SECONDS);
        check(result != null, "timed out waiting for profile save payload");
        return result;
    }

    private static void completed() throws Exception {
        check(SettingsSyncStatusNotification.completed.poll(4, TimeUnit.SECONDS) != null, "timed out waiting for save completion");
    }

    private static String savedText(JsonObject profileData) {
        for (JsonElement element : profileData.getAsJsonArray("modules")) {
            if (!element.isJsonObject()) continue;
            JsonObject module = element.getAsJsonObject();
            if (!module.has("name") || !"Module".equals(module.get("name").getAsString()) || !module.has("values")) continue;
            for (JsonElement entry : module.getAsJsonArray("values")) {
                JsonObject value = entry.getAsJsonObject();
                if ("Text".equals(value.get("id").getAsString())) return value.get("value").getAsString();
            }
        }
        return null;
    }

    private static String payloadText(JsonObject payload) {
        JsonArray profiles = payload.getAsJsonArray("updatedProfiles");
        check(profiles.size() == 1, "expected one updated profile");
        return savedText(profiles.get(0).getAsJsonObject().getAsJsonObject("data"));
    }

    public static void main(String[] args) throws Exception {
        Vape vape = Vape.INSTANCE;
        vape.sync = new SyncThread(vape);
        CompletableFuture<ApiResponse<RemoteProfileDataMap>>[] pending = new CompletableFuture[8];
        try {
            ProfilesManager manager = vape.getProfilesManager();
            Mod module = new Mod("Module");
            StringValue text = StringValue.create(null, "Text", "default");
            module.addValue(text);
            vape.mods.top.add(module);
            Profile profile = new Profile("Private", "4.21");
            profile.updateData(new JsonParser().parse("{\"modules\":[{\"name\":\"Module\",\"values\":[{\"id\":\"Text\",\"value\":\"First\"}]}],\"enabled\":{},\"legit_enabled\":{}}").getAsJsonObject());
            manager.getProfiles().add(profile);
            manager.setActiveProfile(profile);
            profile.setDirty(false);
            vape.sync.clearPendingSave();

            vape.sync.markSettingsDirty();
            long staleRevision = vape.sync.getChangeRevision();
            vape.sync.markSettingsDirty();
            vape.sync.requestSaveIfUnchanged(staleRevision);
            check(vape.sync.hasPendingSave() && ApiServices.INSTANCE.userData.sent.poll(200, TimeUnit.MILLISECONDS) == null,
                    "an outdated debounce observation must not consume a newer change");

            pending[0] = reply();
            pending[1] = reply();
            vape.sync.requestSaveIfUnchanged(vape.sync.getChangeRevision());
            check("First".equals(payloadText(sent())), "the first save captures the active profile");
            text.setValue("Second");
            check(vape.sync.hasPendingSave(), "an edit during an in-flight save becomes pending");
            vape.sync.requestSave(); // A second request while the first is still blocked.
            vape.sync.saveLocalSnapshot();
            JsonObject localProfile = LocalConfigStore.loadProfiles().getAsJsonObject(profile.getLocalId().toString());
            check("Second".equals(savedText(localProfile.getAsJsonObject("data"))), "shutdown-style local capture writes the newest value");
            pending[0].complete(ApiResponse.success(new RemoteProfileDataMap()));
            completed();
            check(profile.isDirty(), "an older successful response does not clear a later edit");
            check("Second".equals(payloadText(sent())), "a request made during a save causes a second save");
            pending[1].complete(ApiResponse.success(new RemoteProfileDataMap()));
            completed();
            check(!profile.isDirty(), "the matching successful response clears the current revision");
            check("Second".equals(savedText(LocalConfigStore.loadProfiles().getAsJsonObject(profile.getLocalId().toString()).getAsJsonObject("data"))),
                    "an older remote completion does not overwrite the newer local snapshot");

            text.setValue("Third");
            pending[2] = reply();
            vape.sync.requestSave();
            check("Third".equals(payloadText(sent())), "the next save starts with the third value");
            long beforeFourth = vape.sync.getChangeRevision();
            text.setValue("Fourth");
            pending[2].complete(ApiResponse.success(new RemoteProfileDataMap()));
            completed();
            check(vape.sync.hasPendingSave() && profile.isDirty(), "a new edit survives completion of an earlier save");
            vape.sync.requestSaveIfUnchanged(beforeFourth);
            check(vape.sync.hasPendingSave(), "a stale timer cannot clear the new pending edit");
            pending[3] = reply();
            vape.sync.requestSaveIfUnchanged(vape.sync.getChangeRevision());
            check("Fourth".equals(payloadText(sent())), "the newer pending edit is sent on the next pass");
            pending[3].complete(ApiResponse.success(new RemoteProfileDataMap()));
            completed();
            check(!profile.isDirty() && !vape.sync.hasPendingSave(), "the latest revision is acknowledged");

            UUID deletedId = UUID.randomUUID();
            Profile removed = new Profile("Remove", "4.21");
            removed.updateData(new JsonParser().parse("{\"modules\":[]}").getAsJsonObject());
            removed.setOnlineId(deletedId);
            manager.getProfiles().add(removed);
            manager.removeProfile(removed);
            pending[4] = reply();
            vape.sync.requestSave();
            JsonObject failedPayload = sent();
            check(failedPayload.getAsJsonArray("deletedProfiles").get(0).getAsString().equals(deletedId.toString()), "deletion is included in the first payload");
            pending[4].complete(ApiResponse.failure("offline"));
            completed();
            check(manager.getPendingDeletionRevisions().containsKey(deletedId), "a failed save retains the deletion for retry");
            JsonObject pendingOnDisk = LocalConfigStore.loadPendingProfileSync();
            check(pendingOnDisk.getAsJsonArray("deletions").get(0).getAsString().equals(deletedId.toString())
                            && pendingOnDisk.getAsJsonArray("updates").get(0).getAsString().equals(profile.getLocalId().toString()),
                    "a failed save stores its pending updates and deletions beside the local profile snapshot");
            // Simulate loading those intents into a fresh in-memory manager.
            manager.acknowledgeDeletions(manager.getPendingDeletionRevisions());
            profile.setDirty(false);
            vape.sync.loadConfig();
            check(manager.getPendingDeletionRevisions().containsKey(deletedId) && profile.isDirty(),
                    "pending profile changes and deletions survive loading the local snapshot");
            pending[5] = reply();
            vape.sync.requestSave();
            JsonObject retryPayload = sent();
            check(retryPayload.getAsJsonArray("deletedProfiles").get(0).getAsString().equals(deletedId.toString()), "the retry sends the same deletion");
            pending[5].complete(ApiResponse.success(new RemoteProfileDataMap()));
            completed();
            check(manager.getPendingDeletionRevisions().isEmpty()
                            && LocalConfigStore.loadPendingProfileSync().getAsJsonArray("deletions").size() == 0
                            && LocalConfigStore.loadPendingProfileSync().getAsJsonArray("updates").size() == 0,
                    "a successful save acknowledges pending changes both in memory and on disk");
            JsonObject staleState = new JsonObject();
            JsonArray staleDeletions = new JsonArray();
            UUID presentId = UUID.randomUUID();
            profile.setOnlineId(presentId);
            staleDeletions.add(new com.google.gson.JsonPrimitive(presentId.toString()));
            staleDeletions.add(new com.google.gson.JsonPrimitive("not-a-uuid"));
            staleState.add("deletions", staleDeletions);
            manager.restorePendingSyncState(staleState);
            check(manager.getPendingDeletionRevisions().isEmpty(), "a stale or malformed intent cannot delete a present profile");

            AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
            Thread paired = new Thread(() -> {
                try {
                    for (int i = 0; i < 30; ++i) {
                        JsonObject settings = new JsonObject(), profiles = new JsonObject();
                        settings.addProperty("revision", i);
                        profiles.addProperty("revision", i);
                        check(LocalConfigStore.saveSettingsAndProfiles(settings, profiles), "paired write failed");
                    }
                } catch (Throwable t) { failure.set(t); }
            });
            Thread global = new Thread(() -> {
                try {
                    for (int i = 0; i < 30; ++i) {
                        JsonObject settings = new JsonObject();
                        settings.addProperty("revision", i);
                        check(LocalConfigStore.saveGlobal(settings), "global write failed");
                    }
                } catch (Throwable t) { failure.set(t); }
            });
            paired.start(); global.start(); paired.join(5000); global.join(5000);
            check(!paired.isAlive() && !global.isAlive() && failure.get() == null, "concurrent local writers completed without corruption");
            check(LocalConfigStore.loadSettings().get("revision").getAsInt() == 29
                            && LocalConfigStore.loadProfiles().get("revision").getAsInt() == 29
                            && LocalConfigStore.loadGlobal().get("revision").getAsInt() == 29,
                    "atomic local writes preserve both profile and global sections");
            System.out.println("Real sync queue, in-flight edits, deletion retry and atomic local storage passed (fake network)");
        } finally {
            vape.stopped = true;
            for (CompletableFuture<ApiResponse<RemoteProfileDataMap>> future : pending) {
                if (future != null) future.complete(ApiResponse.failure("test stopped"));
            }
        }
    }
}
