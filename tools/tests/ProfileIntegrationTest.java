import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gg.vape.Vape;
import gg.vape.api.PublicProfilePartialJsonPayloadBuilder;
import gg.vape.config.Profile;
import gg.vape.config.ProfileModuleSnapshot;
import gg.vape.config.ProfileSnapshot;
import gg.vape.config.PublicProfile;
import gg.vape.manager.client.ProfilesManager;
import gg.vape.module.Mod;
import gg.vape.value.StringValue;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Real Profile and ProfilesManager with controlled, headless module services. */
public final class ProfileIntegrationTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static Profile profile(String name, String value) {
        Profile result = new Profile(name, "4.21");
        JsonObject data = new JsonParser().parse("{\"modules\":[{\"name\":\"Module\",\"futureModule\":42,\"values\":["
                + "{\"id\":\"Text\",\"value\":\"" + value + "\"},{\"id\":\"Future setting\",\"value\":\"keep\"}]},"
                + "{\"name\":\"Unavailable script\",\"values\":[{\"id\":\"x\",\"value\":\"keep\"}]}],"
                + "\"enabled\":{\"Module\":true,\"Unavailable script\":true},\"legit_enabled\":{},\"futureRoot\":\"keep\"}").getAsJsonObject();
        result.updateData(data);
        result.setPublishedData(new JsonParser().parse("{\"publishedOnly\":\"keep\",\"modules\":[],\"enabled\":{},\"legit_enabled\":{}}").getAsJsonObject());
        return result;
    }

    private static JsonObject module(JsonObject data, String name) {
        JsonArray modules = data.getAsJsonArray("modules");
        for (JsonElement element : modules) {
            if (element.isJsonObject() && name.equalsIgnoreCase(element.getAsJsonObject().get("name").getAsString())) return element.getAsJsonObject();
        }
        return null;
    }

    private static JsonObject value(JsonObject data, String id) {
        JsonObject m = module(data, "Module");
        if (m == null || !m.has("values")) return null;
        for (JsonElement element : m.getAsJsonArray("values")) {
            if (element.isJsonObject() && id.equalsIgnoreCase(element.getAsJsonObject().get("id").getAsString())) return element.getAsJsonObject();
        }
        return null;
    }

    private static ProfileSnapshot edited(Profile profile, String newValue) {
        ProfileSnapshot snapshot = profile.createSnapshot(false);
        check(snapshot != null, "profile has a snapshot");
        ProfileModuleSnapshot module = snapshot.getAllModules().get(0);
        StringValue proxy = (StringValue)module.getValueSnapshots().get(0).createProxyValue();
        proxy.setValue(newValue);
        return snapshot;
    }

    private static Thread run(String name, AtomicReference<Throwable> error, Runnable action) {
        Thread thread = new Thread(() -> {
            try { action.run(); } catch (Throwable failure) { error.compareAndSet(null, failure); }
        }, name);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private static void join(Thread thread, AtomicReference<Throwable> error) throws Exception {
        thread.join(3000);
        check(!thread.isAlive(), "operation did not complete: " + thread.getName());
        if (error.get() != null) throw new AssertionError("background operation failed", error.get());
    }

    public static void main(String[] args) throws Exception {
        ProfilesManager manager = Vape.INSTANCE.getProfilesManager();
        Mod module = new Mod("Module");
        StringValue live = StringValue.create(null, "Text", "default");
        module.addValue(live);
        Vape.INSTANCE.mods.top.add(module);
        Profile a = profile("A", "A-original"), b = profile("B", "B-original");
        JsonObject downloaded = new JsonParser().parse("{\"name\":\"Downloaded\",\"vapeVersion\":\"4.21\",\"data\":{\"modules\":[]},\"metadata\":{\"publicProfileId\":42,\"version\":1,\"publishedVersion\":2}}").getAsJsonObject();
        Profile linked = new Profile("", "4.21").loadJson(downloaded);
        check(linked.toJson(false).getAsJsonObject("metadata").get("publicProfileId").getAsLong() == 42L
                        && linked.toJson(true).getAsJsonObject("metadata").get("publishedVersion").getAsLong() == 2L,
                "downloaded public-profile metadata survives local and remote serialization");
        manager.getProfiles().add(a);
        manager.getProfiles().add(b);
        manager.setActiveProfile(a);
        check("A-original".equals(live.getValue()) && module.enabled, "real profile load applies values and enabled state");

        ProfileSnapshot inactive = edited(b, "B-edited");
        inactive.applyAndSave();
        check("A-original".equals(live.getValue()) && manager.getActiveProfileOrNull() == a
                        && "B-edited".equals(value(b.getData(), "Text").get("value").getAsString()) && b.isDirty(),
                "applying an inactive snapshot changes its data without changing live settings");
        check("keep".equals(b.getPublishedData().get("publishedOnly").getAsString())
                        && "B-edited".equals(value(b.getPublishedData(), "Text").get("value").getAsString()),
                "inactive apply also updates the publication snapshot without losing its extra data");

        ProfileSnapshot active = edited(a, "A-edited");
        AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        Vape.INSTANCE.mods.captureEntered = new CountDownLatch(1);
        Vape.INSTANCE.mods.captureRelease = new CountDownLatch(1);
        CountDownLatch entered = Vape.INSTANCE.mods.captureEntered;
        CountDownLatch release = Vape.INSTANCE.mods.captureRelease;
        Thread capture = run("capture", failure, manager::captureActiveProfile);
        check(entered.await(2, TimeUnit.SECONDS), "capture reached live serialization");
        CountDownLatch applyStarted = new CountDownLatch(1), applyDone = new CountDownLatch(1);
        Thread apply = run("apply", failure, () -> { applyStarted.countDown(); active.applyAndSave(); applyDone.countDown(); });
        check(applyStarted.await(2, TimeUnit.SECONDS) && !applyDone.await(150, TimeUnit.MILLISECONDS),
                "active apply waits for an in-flight capture");
        release.countDown();
        join(capture, failure);
        join(apply, failure);
        check("A-edited".equals(live.getValue()), "active apply reloads the edited value into the running module");
        manager.captureActiveProfile();
        check("A-edited".equals(value(a.getData(), "Text").get("value").getAsString())
                        && value(a.getData(), "Future setting") != null && module(a.getData(), "Unavailable script") != null
                        && module(a.getData(), "Module").get("futureModule").getAsInt() == 42
                        && "keep".equals(a.getData().get("futureRoot").getAsString())
                        && "keep".equals(a.getPublishedData().get("publishedOnly").getAsString()),
                "real capture preserves unknown local and published data after active apply");

        ProfileSnapshot oldProfileEdit = edited(a, "A-after-switch");
        Vape.INSTANCE.mods.loadEntered = new CountDownLatch(1);
        Vape.INSTANCE.mods.loadRelease = new CountDownLatch(1);
        Vape.INSTANCE.mods.blockedLoad = "B-edited";
        CountDownLatch loading = Vape.INSTANCE.mods.loadEntered, continueLoad = Vape.INSTANCE.mods.loadRelease;
        Thread switching = run("switch", failure, () -> manager.switchProfile(b));
        check(loading.await(2, TimeUnit.SECONDS), "switch reached new profile load");
        CountDownLatch oldEditStarted = new CountDownLatch(1), oldEditDone = new CountDownLatch(1);
        Thread editingOld = run("edit old profile", failure, () -> { oldEditStarted.countDown(); oldProfileEdit.applyAndSave(); oldEditDone.countDown(); });
        check(oldEditStarted.await(2, TimeUnit.SECONDS) && !oldEditDone.await(150, TimeUnit.MILLISECONDS),
                "editing the old profile waits until a profile switch completes");
        continueLoad.countDown();
        join(switching, failure);
        join(editingOld, failure);
        check(manager.getActiveProfileOrNull() == b && "B-edited".equals(live.getValue())
                        && "A-after-switch".equals(value(a.getData(), "Text").get("value").getAsString()),
                "an edit finishing after a switch updates the now-inactive profile without reloading it");
        check(!manager.captureIfActive(a) && "A-after-switch".equals(value(a.getData(), "Text").get("value").getAsString()),
                "stale capture requests do not overwrite inactive profiles with the current live state");

        JsonObject serialized = a.toJson(false);
        edited(a, "A-later").applyAndSave();
        check("A-after-switch".equals(value(serialized.getAsJsonObject("data"), "Text").get("value").getAsString()),
                "a serialized payload does not alias later profile edits");
        a.setDirty(true);
        long sentRevision = a.getDirtyRevision();
        a.setDirty(true);
        a.clearDirtyIfUnchanged(sentRevision);
        check(a.isDirty(), "an older save response cannot clear a newer edit");
        a.clearDirtyIfUnchanged(a.getDirtyRevision());
        check(!a.isDirty(), "a matching save response clears the sent revision");

        UUID bOnline = UUID.randomUUID();
        b.setOnlineId(bOnline);
        PublicProfile publicProfile = new PublicProfile("Public title", null, bOnline);
        ProfileSnapshot publicParent = ProfileSnapshot.resolvePublicProfileSnapshot(publicProfile);
        check(publicParent.getProfile() != b && publicParent.getProfile().isDraft()
                        && "B-edited".equals(((StringValue)publicParent.getAllModules().get(0).getValueSnapshots().get(0).createProxyValue()).getValue()),
                "a public owner edits a detached copy of the selected private profile");
        int saves = Vape.INSTANCE.saves;
        ProfileSnapshot publicEditor = publicParent.fork();
        ((StringValue)publicEditor.getAllModules().get(0).getValueSnapshots().get(0).createProxyValue()).setValue("Public edit");
        check("B-edited".equals(((StringValue)publicParent.getAllModules().get(0).getValueSnapshots().get(0).createProxyValue()).getValue())
                        && "B-edited".equals(value(publicParent.getProfile().getData(), "Text").get("value").getAsString()),
                "discarding an editor fork leaves the parent snapshot and profile unchanged");
        publicEditor.applyAndSave();
        JsonObject publicPayload = publicEditor.toPublicationData();
        check("Public edit".equals(value(publicPayload, "Text").get("value").getAsString())
                        && "keep".equals(publicPayload.get("publishedOnly").getAsString())
                        && !publicPayload.has("futureRoot") && "B-edited".equals(live.getValue())
                        && "B-edited".equals(value(b.getData(), "Text").get("value").getAsString())
                        && Vape.INSTANCE.saves == saves,
                "a public draft produces a publication payload without modifying or saving its private source");
        @SuppressWarnings("unchecked") Map<String, Object> publicData = new Gson().fromJson(
                "{\"modules\":[{\"name\":\"Module\",\"values\":[{\"id\":\"Text\",\"value\":\"Remote\"}]}],"
                        + "\"enabled\":{\"Module\":true,\"Missing\":true},\"legit_enabled\":{},\"remoteExtra\":\"keep\"}", Map.class);
        ProfileSnapshot remote = ProfileSnapshot.resolvePublicProfileSnapshot(new PublicProfile("Remote", publicData, null));
        check(remote.getAllModules().get(0).isEnabled()
                        && remote.serializeEnabledModules().get("Missing").getAsBoolean()
                        && "keep".equals(remote.toPublicationData().get("remoteExtra").getAsString())
                        && "Remote".equals(value(remote.toPublicationData(), "Text").get("value").getAsString()),
                "a public profile without a local source keeps its complete published data and enabled states");
        JsonObject modulesOnly = PublicProfilePartialJsonPayloadBuilder.build(7L, bOnline, "Public title", null, null, null, null, null, publicPayload);
        check(!modulesOnly.has("tags") && !modulesOnly.has("description") && !modulesOnly.has("listed")
                        && !modulesOnly.has("anonymous") && !modulesOnly.has("shareCodeFriendsOnly")
                        && bOnline.toString().equals(modulesOnly.get("derivedFrom").getAsString())
                        && "Public edit".equals(value(modulesOnly.getAsJsonObject("profileData"), "Text").get("value").getAsString()),
                "a modules-only partial update cannot clear existing profile details");
        JsonObject detailsOnly = PublicProfilePartialJsonPayloadBuilder.build(7L, null, null, "", java.util.Collections.<String>emptyList(), false, false, true, null);
        check(detailsOnly.getAsJsonArray("tags").size() == 0 && "".equals(detailsOnly.get("description").getAsString())
                        && !detailsOnly.get("listed").getAsBoolean() && !detailsOnly.has("profileData")
                        && !detailsOnly.has("vapeVersion") && !detailsOnly.has("derivedFrom"),
                "explicitly empty details remain updates while omitted modules and source stay untouched");

        UUID deletedId = UUID.randomUUID();
        Profile removed = profile("Removed", "x");
        removed.setOnlineId(deletedId);
        manager.getProfiles().add(removed);
        manager.removeProfile(removed);
        Map<UUID, Long> firstDeletion = manager.getPendingDeletionRevisions();
        check(manager.toJson(true).getAsJsonArray("deletedProfiles").size() == 1
                        && manager.toJson(true).getAsJsonArray("deletedProfiles").size() == 1,
                "failed or merely constructed save payloads do not consume deletion intents");
        Profile removedAgain = profile("Removed again", "x");
        removedAgain.setOnlineId(deletedId);
        manager.getProfiles().add(removedAgain);
        manager.removeProfile(removedAgain);
        Map<UUID, Long> laterDeletion = manager.getPendingDeletionRevisions();
        manager.acknowledgeDeletions(firstDeletion);
        check(manager.getPendingDeletionRevisions().containsKey(deletedId), "an older response cannot acknowledge a later deletion");
        manager.acknowledgeDeletions(laterDeletion);
        check(manager.getPendingDeletionRevisions().isEmpty(), "successful responses acknowledge matching deletions");

        Profile c = profile("C", "C"), d = profile("D", "D");
        manager.getProfiles().add(c);
        manager.getProfiles().add(d);
        for (Profile p : manager.getProfiles()) p.setDirty(false);
        manager.moveProfile(a, 2);
        check(manager.getProfiles().get(0) == b && manager.getProfiles().get(1) == c && manager.getProfiles().get(2) == a
                        && a.isDirty() && b.isDirty() && c.isDirty() && !d.isDirty(),
                "reordering marks every changed sort index dirty");
        JsonObject external = new JsonParser().parse("{\"modules\":[],\"sentinel\":\"before\"}").getAsJsonObject();
        d.updateData(external);
        external.addProperty("sentinel", "after");
        check("before".equals(d.getData().get("sentinel").getAsString()), "profile data does not alias an external JSON object");
        Profile emptyName = new Profile("name", "4.21").loadJson(new JsonParser().parse("{\"name\":\"b64:\",\"data\":{}} ").getAsJsonObject());
        check("".equals(emptyName.getName()), "empty encoded profile names load safely");
        Profile bound = profile("Bound", "x");
        bound.setBoundInputs(Arrays.asList(42));
        bound.updateData(new JsonParser().parse("{\"modules\":[],\"keybinds\":[]}").getAsJsonObject());
        check(bound.getBoundInputs().isEmpty(), "an explicitly empty array clears an existing profile binding");
        bound.setBoundInputs(Arrays.asList(42));
        bound.updateData(new JsonParser().parse("{\"modules\":[]}").getAsJsonObject());
        check(bound.getBoundInputs().equals(Arrays.asList(42)), "an omitted binding leaves the existing profile binding unchanged");
        System.out.println("Real profile capture, apply, switching, public drafts, ordering and dirty/deletion revisions passed (headless services)");
    }
}
