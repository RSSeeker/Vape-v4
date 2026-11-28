package gg.vape.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import gg.vape.Vape;
import gg.vape.api.ApiHttpClient;
import gg.vape.config.ConfigJsonUtils;
import gg.vape.config.Profile;
import gg.vape.config.ProfileModuleSnapshot;
import gg.vape.config.ProfileModuleSnapshotOrderComparator;
import gg.vape.config.PublicProfile;
import gg.vape.module.Category;
import gg.vape.module.Mod;
import gg.vape.module.SubModule;
import gg.vape.module.render.hud.HudModule;
import gg.vape.ui.click.frame.impl.profile.ProfileSnapshotGuiBuilder;
import gg.vape.utils.NameComparator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ProfileSnapshot {
    private final List<ProfileModuleSnapshot> moduleSnapshots;
    private final List<JsonElement> unknownModules;
    private final JsonObject enabledModules;
    private final JsonObject hudEnabledModules;
    private final JsonObject unknownEnabledModules;
    private final JsonObject unknownHudEnabledModules;
    private final ProfileSnapshotGuiBuilder guiBuilder;
    private Profile profile;

    public List<ProfileModuleSnapshot> getSortedModules(boolean includeDefaults) {
        List<ProfileModuleSnapshot> modules = this.getModules(includeDefaults);
        modules.sort(new ProfileModuleSnapshotOrderComparator());
        return modules;
    }

    public JsonObject serializeEnabledModules() {
        JsonObject enabledModules = ConfigJsonUtils.deepCopy(this.unknownEnabledModules);
        for (ProfileModuleSnapshot moduleSnapshot : this.getAllModules()) {
            if (moduleSnapshot.getModule() instanceof HudModule || !moduleSnapshot.isEnabled()) continue;
            enabledModules.addProperty(moduleSnapshot.getName(), Boolean.valueOf(moduleSnapshot.isEnabled()));
        }
        return enabledModules;
    }

    public JsonObject serializeHudEnabledModules() {
        JsonObject enabled = ConfigJsonUtils.deepCopy(this.unknownHudEnabledModules);
        for (ProfileModuleSnapshot moduleSnapshot : this.getAllModules()) {
            if (!(moduleSnapshot.getModule() instanceof HudModule) || !moduleSnapshot.isEnabledSpecified()) continue;
            enabled.addProperty(moduleSnapshot.getName(), Boolean.valueOf(moduleSnapshot.isEnabled()));
        }
        return enabled;
    }

    Boolean getSavedEnabledState(Mod module) {
        return ConfigJsonUtils.getBooleanIgnoreCase(module instanceof HudModule ? this.hudEnabledModules : this.enabledModules, module.getName());
    }

    public ProfileSnapshotGuiBuilder getGuiBuilder() {
        return this.guiBuilder;
    }

    public void applyToProfile() {
        Profile target = this.profile;
        if (target == null) return;
        Object manager = Vape.INSTANCE.getProfilesManager();
        synchronized (manager == null ? target : manager) {
            synchronized (target) {
                JsonObject data = target.getData();
                JsonObject profileJson = data == null ? new JsonObject() : ConfigJsonUtils.deepCopy(data);
                profileJson.add("modules", this.serializeModules(false));
                profileJson.add("enabled", this.serializeEnabledModules());
                profileJson.add("legit_enabled", this.serializeHudEnabledModules());

                JsonObject published = this.buildPublicationData(profileJson, target.getPublishedData());
                target.updateData(profileJson);
                target.setPublishedData(published);
            }
        }
    }

    private JsonObject buildPublicationData(JsonObject localData, JsonObject existingPublished) {
        JsonObject published = existingPublished == null ? new JsonObject() : ConfigJsonUtils.deepCopy(existingPublished);
        if (existingPublished == null && localData != null) {
            // Match Profile.serializeCurrentData without publishing arbitrary
            // private root fields when no published payload exists yet.
            for (String key : new String[]{"favorites", "values", "macros", "search", "frames"}) {
                if (localData.has(key)) published.add(key, ConfigJsonUtils.deepCopy(localData.get(key)));
            }
        }
        published.add("modules", this.serializeModules(true));
        published.add("enabled", this.serializeEnabledModules());
        published.add("legit_enabled", this.serializeHudEnabledModules());
        published.remove("sortOrder");
        return published;
    }

    /** Build a publication payload without switching or modifying a private profile. */
    public JsonObject toPublicationData() {
        Profile target = this.profile;
        if (target == null) return this.buildPublicationData(null, null);
        Object manager = Vape.INSTANCE.getProfilesManager();
        synchronized (manager == null ? target : manager) {
            synchronized (target) {
                return this.buildPublicationData(target.getData(), target.getPublishedData());
            }
        }
    }

    /** Commit a local editor, including an immediate reload when it edits the
     * active profile. Autosave captures under the same profile lock, so it
     * cannot replace the edited data with the old live state mid-apply. */
    public void applyAndSave() {
        Profile target = this.profile;
        if (target == null) return;
        Object manager = Vape.INSTANCE.getProfilesManager();
        boolean persistentTarget = false;
        synchronized (manager == null ? target : manager) {
            synchronized (target) {
                this.applyToProfile();
                if (manager != null) {
                    boolean active = Vape.INSTANCE.getProfilesManager().getActiveProfileOrNull() == target;
                    persistentTarget = active || Vape.INSTANCE.getProfilesManager().getProfiles().contains(target);
                    if (active) Vape.INSTANCE.getProfilesManager().reloadActiveProfile(target);
                }
                target.setDirty(true);
            }
        }
        // Public-profile drafts are deliberately detached from private profiles.
        if (persistentTarget) Vape.INSTANCE.saveAndStop();
    }

    /** Work on a private copy so discarding an editor cannot mutate its caller. */
    public ProfileSnapshot fork() {
        JsonObject data = new JsonObject();
        JsonArray modules = this.serializeModules(false);
        data.add("modules", modules);
        data.add("enabled", this.serializeEnabledModules());
        data.add("legit_enabled", this.serializeHudEnabledModules());
        return new ProfileSnapshot(this.profile, modules, data);
    }

    public void setProfile(Profile profile) {
        this.profile = profile;
    }

    public Profile getProfile() {
        return this.profile;
    }

    public JsonArray serializeModules() {
        return this.serializeModules(false);
    }

    public JsonArray serializeModules(boolean forPublication) {
        JsonArray modulesJson = new JsonArray();
        for (ProfileModuleSnapshot moduleSnapshot : this.moduleSnapshots) {
            JsonObject moduleJson = moduleSnapshot.toJson(forPublication);
            if (moduleJson == null) continue;
            modulesJson.add(moduleJson);
        }
        // Profiles may contain a script or module unavailable in this session.
        // Editing another module must not silently delete that data.
        for (JsonElement module : this.unknownModules) modulesJson.add(ConfigJsonUtils.deepCopy(module));
        return modulesJson;
    }

    public List<ProfileModuleSnapshot> getModules(boolean includeDefaults) {
        ArrayList<ProfileModuleSnapshot> modules = new ArrayList<>();
        for (ProfileModuleSnapshot moduleSnapshot : this.getAllModules()) {
            if (moduleSnapshot.getModule() instanceof SubModule || moduleSnapshot.getModule().getCategory() == Category.NONE || !moduleSnapshot.hasChanges() && !includeDefaults) continue;
            modules.add(moduleSnapshot);
        }
        return modules;
    }

    public ProfileSnapshot(Profile profile, JsonArray modulesJson) {
        this(profile, modulesJson, profile == null ? null : profile.getData());
    }

    public ProfileSnapshot(Profile profile, JsonArray modulesJson, JsonObject snapshotData) {
        this.profile = profile;
        this.moduleSnapshots = new ArrayList<>();
        this.unknownModules = new ArrayList<JsonElement>();
        JsonObject enabled = snapshotData == null ? null : ConfigJsonUtils.getJsonObject(snapshotData, "enabled");
        JsonObject hudEnabled = snapshotData == null ? null : ConfigJsonUtils.getJsonObject(snapshotData, "legit_enabled");
        this.enabledModules = enabled == null ? new JsonObject() : ConfigJsonUtils.deepCopy(enabled);
        this.hudEnabledModules = hudEnabled == null ? new JsonObject() : ConfigJsonUtils.deepCopy(hudEnabled);
        this.unknownEnabledModules = new JsonObject();
        this.unknownHudEnabledModules = new JsonObject();
        List<Mod> knownModules = new ArrayList<Mod>(Vape.INSTANCE.getModManager().getTopLevelModules());
        List<JsonElement> inputModules = new ArrayList<JsonElement>();
        if (modulesJson != null) for (JsonElement element : modulesJson) inputModules.add(element);
        boolean[] recognized = new boolean[inputModules.size()];
        for (Mod module : knownModules) {
            JsonObject moduleJson = null;
            for (int i = 0; i < inputModules.size(); ++i) {
                JsonElement element = inputModules.get(i);
                if (element == null || !element.isJsonObject()) continue;
                JsonObject candidate = element.getAsJsonObject();
                String name = ConfigJsonUtils.getString(candidate, "name");
                if (name != null && module.getName().equalsIgnoreCase(name)) {
                    recognized[i] = true;
                    moduleJson = candidate;
                }
            }
            this.moduleSnapshots.add(new ProfileModuleSnapshot(this, module, moduleJson));
        }
        for (int i = 0; i < inputModules.size(); ++i) {
            if (!recognized[i]) this.unknownModules.add(ConfigJsonUtils.deepCopy(inputModules.get(i)));
        }
        copyUnknownEnabledStates(this.enabledModules, this.unknownEnabledModules, knownModules);
        copyUnknownEnabledStates(this.hudEnabledModules, this.unknownHudEnabledModules, knownModules);
        this.moduleSnapshots.sort(new NameComparator());
        this.guiBuilder = new ProfileSnapshotGuiBuilder(this);
    }

    private static void copyUnknownEnabledStates(JsonObject source, JsonObject destination, List<Mod> knownModules) {
        for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
            boolean known = false;
            for (Mod module : knownModules) {
                if (module.getName().equalsIgnoreCase(entry.getKey())) { known = true; break; }
            }
            if (!known) destination.add(entry.getKey(), ConfigJsonUtils.deepCopy(entry.getValue()));
        }
    }

    public static ProfileSnapshot createEditableCopy(PublicProfile publicProfile, Profile sourceProfile) {
        Object manager = Vape.INSTANCE.getProfilesManager();
        synchronized (manager == null ? sourceProfile : manager) {
            if (manager != null) Vape.INSTANCE.getProfilesManager().captureIfActive(sourceProfile);
            Profile editableProfile = new Profile(publicProfile.getName(), sourceProfile.getClientVersion());
            editableProfile.loadJson(sourceProfile.toJson(true));
            editableProfile.setPublishedData(sourceProfile.copyPublishedData());
            editableProfile.setDraft(true);
            editableProfile.setName(publicProfile.getName());
            ProfileSnapshot snapshot = editableProfile.createSnapshot(true);
            return snapshot != null ? snapshot : new ProfileSnapshot(editableProfile, new JsonArray());
        }
    }

    public List<ProfileModuleSnapshot> getAllModules() {
        return this.moduleSnapshots;
    }

    public static ProfileSnapshot resolvePublicProfileSnapshot(PublicProfile publicProfile) {
        Profile localProfile = publicProfile.getShareInfo() != null && publicProfile.getShareInfo().getDerivedFrom() != null
            ? Vape.INSTANCE.getProfilesManager().getProfileByOnlineId(publicProfile.getShareInfo().getDerivedFrom())
            : null;
        if (localProfile != null) return createEditableCopy(publicProfile, localProfile);

        // Keep the entire published payload, including enabled states and
        // fields from modules unavailable in this client, not just its modules.
        JsonElement tree = ApiHttpClient.GSON.toJsonTree(publicProfile.getData());
        JsonObject data = tree != null && tree.isJsonObject() ? tree.getAsJsonObject() : new JsonObject();
        JsonArray modulesJson = ConfigJsonUtils.getJsonArray(data, "modules");
        if (modulesJson == null) {
            modulesJson = new JsonArray();
            data.add("modules", modulesJson);
        }
        Profile detachedProfile = new Profile(publicProfile.getName(), "4.21");
        detachedProfile.setDraft(true);
        detachedProfile.updateData(data);
        detachedProfile.setPublishedData(data);
        return new ProfileSnapshot(detachedProfile, modulesJson, data);
    }
}

