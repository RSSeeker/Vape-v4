package gg.vape.manager.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import gg.vape.Vape;
import gg.vape.config.BuiltinProfile;
import gg.vape.config.BuiltinProfileState;
import gg.vape.config.Minecraft121BuiltinProfile;
import gg.vape.config.Profile;
import gg.vape.config.ProfilesSyncPayloadBuilder;
import gg.vape.config.PublicProfile;
import gg.vape.event.EventBus;
import gg.vape.event.impl.ProfileChangeEvent;
import gg.vape.event.impl.ProfileListMutationAction;
import gg.vape.event.impl.ProfileListMutationEvent;
import gg.vape.module.Category;
import gg.vape.module.Mod;
import gg.vape.module.none.ClientSettings;
import gg.vape.module.render.Search;
import gg.vape.ui.click.component.value.AbstractListValueComponent;
import gg.vape.ui.click.component.value.FloatingValueDropdownLayer;
import gg.vape.ui.click.frame.Frame;
import gg.vape.ui.click.frame.impl.ClientSettingsFrame;
import gg.vape.ui.click.frame.impl.profile.ProfilesSettingsFrame;
import gg.vape.value.Value;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jetbrains.annotations.Nullable;

public class ProfilesManager {
    // UI and input handlers also iterate this list while a save can run.
    private final List<Profile> profiles = new CopyOnWriteArrayList<Profile>();
    static final boolean assertionsDisabled = !ProfilesManager.class.desiredAssertionStatus();
    private volatile Profile activeProfile;
    private boolean suppressDefaultFrameStates;
    private final Map<UUID, Long> deletedRemoteProfileRevisions = new LinkedHashMap<UUID, Long>();
    private long deletionRevision;

    public void resetAllSettings() {
        for (Mod object : Vape.INSTANCE.getModManager().getAllModules()) {
            if (object.getCategory() == Category.NONE && !(object instanceof Search) || object.isEnabled()) {
                // empty if block
            }
            if (object.getBind().usesOwnKeybindStorage()) {
                object.getBind().getBoundInputs().clear();
                if (object.getDefaultKeybind() != 0) {
                    object.getBind().getBoundInputs().add(object.getDefaultKeybind());
                }
            }
            object.setVisibility(object.isDefaultVisible());
            for (Value<?, ?> value : object.getAllValues()) {
                value.reset();
            }
        }
        ClientSettings.refreshModuleCategoryHeaders();
        for (Value value : Vape.INSTANCE.getValueManager().getValues()) {
            if (value.getDefaultValue() == null) continue;
            value.reset();
        }
        for (Frame frame : ClientSettings.getAllFrames()) {
            if (frame instanceof ClientSettingsFrame) {
                ((ClientSettingsFrame)frame).d$src$V$16knweo();
                continue;
            }
            if (!(frame instanceof FloatingValueDropdownLayer)) continue;
            ((AbstractListValueComponent)((FloatingValueDropdownLayer)frame).getSourceComponent()).setExpanded(false);
        }
    }

    public List<Profile> getProfiles() {
        return this.profiles;
    }

    public Profile getProfileByOnlineId(UUID onlineId) {
        for (Profile profile : this.profiles) {
            if (profile.getOnlineId() == null || !profile.getOnlineId().toString().equalsIgnoreCase(onlineId.toString())) continue;
            return profile;
        }
        return null;
    }

    public void removeProfile(Profile profile) {
        synchronized (this) {
            if (!this.profiles.remove(profile)) return;
            if (profile.getOnlineId() != null) {
                this.deletedRemoteProfileRevisions.put(profile.getOnlineId(), ++this.deletionRevision);
            }
        }
        ClientSettings.getFrame(ProfilesSettingsFrame.class).removeProfile(profile);
        Vape.INSTANCE.saveAndStop();
        new ProfileListMutationEvent(profile, ProfileListMutationAction.REMOVE).fire();
    }

    public void addProfile(Profile profile, boolean prepend) {
        synchronized (this) {
            // Restoring a profile cancels a pending deletion of the same ID.
            if (profile.getOnlineId() != null) this.deletedRemoteProfileRevisions.remove(profile.getOnlineId());
            if (prepend) {
                this.profiles.add(0, profile);
                for (Profile existing : this.profiles) {
                    existing.setSortOrder(existing.getCurrentSortIndex());
                }
            } else {
                this.profiles.add(profile);
            }
        }
        ClientSettings.getFrame(ProfilesSettingsFrame.class).addProfile(profile);
        ProfilesSettingsFrame.refreshProfileList();
        Vape.INSTANCE.saveAndStop();
        new ProfileListMutationEvent(profile, ProfileListMutationAction.ADD).fire();
    }

    public JsonObject buildSingleProfileSyncPayload(Profile profile) {
        JsonObject jsonObject = new JsonObject();
        JsonArray jsonArray = new JsonArray();
        jsonArray.add((JsonElement)profile.toJson(true));
        jsonObject.add("updatedProfiles", (JsonElement)jsonArray);
        jsonObject.add("deletedProfiles", (JsonElement)new JsonArray());
        return jsonObject;
    }

    public void loadJson(JsonObject jsonObject) {
        if (jsonObject == null) {
            return;
        }
        if (jsonObject.entrySet().isEmpty()) {
            Profile profile = this.initializeBuiltinProfiles();
            if (profile != null) {
                this.setActiveProfile(profile);
            }
            return;
        }
        for (Map.Entry entry : jsonObject.entrySet()) {
            JsonObject jsonObject2 = ((JsonElement)entry.getValue()).getAsJsonObject();
            Profile profile = new Profile("", "", true).loadJson(jsonObject2);
            this.addProfile(profile);
        }
        try {
            this.profiles.sort(ProfilesManager::compareProfileSortOrder);
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        ProfilesSettingsFrame.refreshProfileList();
    }

    private static Throwable propagateThrowable(Throwable throwable) {
        return throwable;
    }

    private Profile initializeBuiltinProfiles() {
        ArrayList<BuiltinProfile> builtinProfiles = new ArrayList<BuiltinProfile>();
        builtinProfiles.add(new Minecraft121BuiltinProfile());
        builtinProfiles.add(new BuiltinProfileState());
        Profile selectedProfile = null;
        for (BuiltinProfile builtinProfile : builtinProfiles) {
            builtinProfile.applyPreset();
            this.addProfile(builtinProfile);
            if (selectedProfile != null || !builtinProfile.isApplicable()) continue;
            selectedProfile = builtinProfile;
        }
        if (selectedProfile == null && !this.profiles.isEmpty()) {
            selectedProfile = this.profiles.get(0);
        }
        return selectedProfile;
    }

    private static int compareProfileSortOrder(Profile first, Profile second) {
        if (first.getSortOrder() != null && second.getSortOrder() != null) {
            return first.getSortOrder().compareTo(second.getSortOrder());
        }
        if (first.getSortOrder() != null) {
            return 1;
        }
        return -1;
    }

    public Profile getProfileByName(String name) {
        for (Profile profile : this.profiles) {
            if (!profile.getName().equalsIgnoreCase(name)) continue;
            return profile;
        }
        return null;
    }

    public synchronized JsonObject toJson(boolean dirtyOnly) {
        if (dirtyOnly) {
            ArrayList<Profile> dirtyProfiles = new ArrayList<Profile>();
            for (Profile profile : this.getProfiles()) {
                if (!profile.isDirty()) continue;
                dirtyProfiles.add(profile);
            }
            ArrayList<UUID> deletedIds = new ArrayList<UUID>(this.deletedRemoteProfileRevisions.keySet());
            return ProfilesSyncPayloadBuilder.build(dirtyProfiles, deletedIds);
        }
        JsonObject result = new JsonObject();
        for (Profile profile : this.getProfiles()) {
            result.add(profile.getLocalId().toString(), (JsonElement)profile.toJson(dirtyOnly));
        }
        return result;
    }

    /** Record a remote ID created by an in-flight save after its local profile was removed. */
    public synchronized boolean queueRemoteDeletion(UUID id) {
        if (id == null || this.getProfileByOnlineId(id) != null || this.deletedRemoteProfileRevisions.containsKey(id)) return false;
        this.deletedRemoteProfileRevisions.put(id, ++this.deletionRevision);
        return true;
    }

    /** Snapshot deletion intents without consuming them until the server accepts them. */
    public synchronized Map<UUID, Long> getPendingDeletionRevisions() {
        return new LinkedHashMap<UUID, Long>(this.deletedRemoteProfileRevisions);
    }

    public synchronized void acknowledgeDeletions(Map<UUID, Long> sent) {
        for (Map.Entry<UUID, Long> entry : sent.entrySet()) {
            if (entry.getValue().equals(this.deletedRemoteProfileRevisions.get(entry.getKey()))) {
                this.deletedRemoteProfileRevisions.remove(entry.getKey());
            }
        }
    }

    /** Keep unsent profile changes alongside the local snapshot across restarts. */
    public synchronized JsonObject pendingSyncState() {
        JsonObject state = new JsonObject();
        JsonArray updates = new JsonArray();
        JsonArray deletions = new JsonArray();
        for (Profile profile : this.profiles) {
            if (profile.isDirty()) updates.add(new JsonPrimitive(profile.getLocalId().toString()));
        }
        for (UUID id : this.deletedRemoteProfileRevisions.keySet()) deletions.add(new JsonPrimitive(id.toString()));
        state.add("updates", updates);
        state.add("deletions", deletions);
        return state;
    }

    public synchronized void restorePendingSyncState(@Nullable JsonObject state) {
        if (state == null) return;
        JsonArray updates = gg.vape.config.ConfigJsonUtils.getJsonArray(state, "updates");
        if (updates != null) for (JsonElement element : updates) {
            UUID id = parsePendingUuid(element);
            Profile profile = id == null ? null : this.getProfileByLocalId(id);
            if (profile != null) profile.setDirty(true);
        }
        JsonArray deletions = gg.vape.config.ConfigJsonUtils.getJsonArray(state, "deletions");
        if (deletions != null) for (JsonElement element : deletions) {
            UUID id = parsePendingUuid(element);
            // Never replay a stale deletion against a profile that is present
            // in the same local snapshot.
            if (id != null && this.getProfileByOnlineId(id) == null && !this.deletedRemoteProfileRevisions.containsKey(id)) {
                this.deletedRemoteProfileRevisions.put(id, ++this.deletionRevision);
            }
        }
    }

    @Nullable
    private static UUID parsePendingUuid(JsonElement element) {
        try {
            return element != null && element.isJsonPrimitive() ? UUID.fromString(element.getAsString()) : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public synchronized boolean hasPendingSync() {
        if (!this.deletedRemoteProfileRevisions.isEmpty()) return true;
        for (Profile profile : this.profiles) if (profile.isDirty()) return true;
        return false;
    }

    public synchronized void clearProfiles() {
        this.profiles.clear();
    }

    public void addProfile(Profile profile) {
        this.addProfile(profile, false);
    }

    public void updatePublicProfileLinks() {
        Collection<PublicProfile> collection = Vape.INSTANCE.getPublicProfileManager().getProfilesById().values();
        for (Profile profile : this.profiles) {
            profile.setPublicProfile(null);
            for (PublicProfile publicProfile : collection) {
                UUID uUID;
                if (publicProfile.getShareInfo() == null || (uUID = publicProfile.getShareInfo().getDerivedFrom()) == null || profile.getOnlineId() == null || !profile.getOnlineId().toString().equalsIgnoreCase(uUID.toString())) continue;
                profile.setPublicProfile(publicProfile);
            }
        }
    }

    public synchronized void setActiveProfile(Profile profile) {
        if (this.activeProfile != null && this.activeProfile.equals(profile)) {
            return;
        }
        Profile previousProfile = this.activeProfile;
        this.resetAllSettings();
        this.activeProfile = profile;
        this.activeProfile.apply();
        Vape.INSTANCE.saveAndStop();
        EventBus.getInstance().post(new ProfileChangeEvent(previousProfile, profile));
    }

    /** Reapply an edited active profile without changing which profile is selected. */
    public synchronized void reloadActiveProfile(Profile expectedProfile) {
        if (expectedProfile == null || this.activeProfile != expectedProfile) return;
        this.resetAllSettings();
        expectedProfile.apply();
        EventBus.getInstance().post(new ProfileChangeEvent(expectedProfile, expectedProfile));
    }

    public synchronized void switchProfile(Profile profile) {
        if (this.getActiveProfile() != null) {
            this.getActiveProfile().captureCurrentState();
        }
        this.setActiveProfile(profile);
    }

    /** Capture the selected profile while preventing a concurrent switch. */
    public synchronized Profile captureActiveProfile() {
        Profile current = this.getActiveProfile();
        if (current != null) current.captureCurrentState();
        return current;
    }

    /** Do not capture a profile which stopped being active before the lock. */
    public synchronized boolean captureIfActive(Profile expected) {
        if (expected == null || this.activeProfile != expected) return false;
        expected.captureCurrentState();
        return true;
    }

    public synchronized void captureProfileState(Profile profile) {
        Profile currentProfile = this.getActiveProfile();
        if (profile.equals(currentProfile)) {
            profile.captureCurrentState();
        } else {
            this.setActiveProfile(profile);
            profile.captureCurrentState();
            this.setActiveProfile(currentProfile);
        }
    }

    public List<Profile> getRemoteProfiles() {
        ArrayList<Profile> remoteProfiles = new ArrayList<Profile>();
        for (Profile profile : this.profiles) {
            if (profile.getRemoteMetadata() == null) continue;
            remoteProfiles.add(profile);
        }
        return remoteProfiles;
    }

    public synchronized Profile getActiveProfile() {
        boolean activeProfileInvalid = this.activeProfile == null || !this.getProfiles().contains(this.activeProfile) && !this.activeProfile.isDraft();
        if (activeProfileInvalid) {
            if (!this.getProfiles().isEmpty()) {
                this.setActiveProfile(this.getProfiles().get(0));
            } else {
                Profile profile = this.initializeBuiltinProfiles();
                this.setActiveProfile(profile);
            }
        }
        return this.activeProfile;
    }

    @Nullable
    public Profile getProfileByPublicProfileId(long publicProfileId) {
        for (Profile profile : this.profiles) {
            if (profile.getRemoteMetadata() == null) continue;
            if (!assertionsDisabled && profile.getRemoteMetadata() == null) {
                throw new AssertionError();
            }
            if (profile.getRemoteMetadata().getPublicProfileId() != publicProfileId) continue;
            return profile;
        }
        return null;
    }

    public synchronized void sortProfiles() {
        this.profiles.sort(Profile::compareSortOrder);
    }

    /** Move and dirty every profile whose serialized sort index changed. */
    public void moveProfile(Profile profile, int destination) {
        synchronized (this) {
            int original = this.profiles.indexOf(profile);
            if (original < 0 || destination < 0 || destination >= this.profiles.size() || original == destination) return;
            this.profiles.remove(original);
            this.profiles.add(destination, profile);
            for (int index = Math.min(original, destination); index <= Math.max(original, destination); ++index) {
                Profile changed = this.profiles.get(index);
                changed.setSortOrder(index);
                changed.setDirty(true);
            }
        }
        Vape.INSTANCE.saveAndStop();
    }

    public Profile getActiveProfileOrNull() {
        return this.activeProfile;
    }

    public Profile getProfileByLocalId(UUID localId) {
        for (Profile profile : this.profiles) {
            if (!profile.getLocalId().toString().equalsIgnoreCase(localId.toString())) continue;
            return profile;
        }
        return null;
    }
}
