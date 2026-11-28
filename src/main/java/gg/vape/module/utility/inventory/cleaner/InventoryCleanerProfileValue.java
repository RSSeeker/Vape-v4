package gg.vape.module.utility.inventory.cleaner;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import gg.vape.config.ConfigJsonUtils;
import gg.vape.module.utility.inventory.cleaner.InventoryCleanerProfile;
import gg.vape.value.Value;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

public class InventoryCleanerProfileValue
extends Value<InventoryCleanerProfile, InventoryCleanerProfileValue> {
    private final List<InventoryCleanerProfile> profiles = new ArrayList<InventoryCleanerProfile>();
    private final boolean detachedPreview;
    private JsonObject preservedData = new JsonObject();
    @Nullable
    private JsonObject unsupportedData;

    /** Unsupported data remains opaque until the user explicitly resets it. */
    public boolean hasUnsupportedData() {
        return this.unsupportedData != null;
    }

    @Nullable
    public InventoryCleanerProfile findByName(String name) {
        for (InventoryCleanerProfile profile : this.profiles) {
            if (!profile.getName().equalsIgnoreCase(name)) continue;
            return profile;
        }
        return null;
    }

    public void removeProfile(InventoryCleanerProfile profile) {
        if (!this.profiles.remove(profile)) return;
        if (this.getValue() == profile) this.setValue(null);
        else this.notifyChanged();
    }

    public @UnmodifiableView List<InventoryCleanerProfile> getProfiles() {
        return this.profiles;
    }

    @Override
    public void reset() {
        if (this.isResettable()) {
            this.unsupportedData = null;
            this.preservedData = new JsonObject();
            this.profiles.clear();
            // The default selection is null; Value.reset() intentionally skips
            // null defaults, so it cannot clear a previous profile selection.
            this.setValue(null);
        }
    }

    @Override
    public boolean isDefault() {
        if (this.unsupportedData != null || this.getValue() != null || !this.profiles.isEmpty()) return false;
        for (Map.Entry<String, JsonElement> entry : this.preservedData.entrySet()) {
            String key = entry.getKey();
            if (!"id".equals(key) && !"selected".equals(key) && !"inventories".equals(key)) return false;
        }
        return true;
    }

    public static InventoryCleanerProfileValue create(Object owner, String id) {
        return new InventoryCleanerProfileValue(owner, id);
    }

    @Override
    public InventoryCleanerProfileValue copyValueDefinition() {
        return new InventoryCleanerProfileValue(null, this.getId(), true);
    }

    public InventoryCleanerProfile createProfile() {
        InventoryCleanerProfile profile = new InventoryCleanerProfile(this.detachedPreview);
        profile.assignDefaultName(this.profiles);
        return profile;
    }

    @Override
    public JsonObject toJson(boolean embedSharedPresets) {
        if (this.unsupportedData != null) {
            JsonObject opaque = ConfigJsonUtils.deepCopy(this.unsupportedData);
            opaque.addProperty("id", this.getId());
            return opaque;
        }
        JsonObject jsonObject = ConfigJsonUtils.deepCopy(this.preservedData);
        jsonObject.remove("selected");
        jsonObject.remove("inventories");
        jsonObject.addProperty("id", this.getId());
        InventoryCleanerProfile selectedProfile = (InventoryCleanerProfile)this.getValue();
        if (selectedProfile != null) {
            jsonObject.addProperty("selected", selectedProfile.getName());
        }
        if (!this.profiles.isEmpty()) {
            JsonArray jsonArray = new JsonArray();
            for (InventoryCleanerProfile profile : this.profiles) {
                jsonArray.add((JsonElement)profile.toJson(embedSharedPresets));
            }
            jsonObject.add("inventories", (JsonElement)jsonArray);
        }
        return jsonObject;
    }

    @Override
    public String getDisplayValue() {
        InventoryCleanerProfile selectedProfile = (InventoryCleanerProfile)this.getValue();
        if (selectedProfile == null) {
            return "";
        }
        return selectedProfile.getName();
    }

    @Override
    public void parse(String value) {
    }

    @Override
    public boolean loadJson(JsonObject jsonObject) {
        if (!this.matchesJsonId(jsonObject)) return false;
        List<InventoryCleanerProfile> loaded = new ArrayList<InventoryCleanerProfile>();
        InventoryCleanerProfile selected = null;
        try {
            if (jsonObject.has("inventories")) {
                JsonArray inventories = jsonObject.getAsJsonArray("inventories");
                if (inventories == null) return this.retainUnsupportedData(jsonObject);
                for (JsonElement element : inventories) {
                    JsonObject profileJson = element.getAsJsonObject();
                    InventoryCleanerProfile profile = new InventoryCleanerProfile(profileJson, this.detachedPreview);
                    if (profileJson.get("name").getAsString().trim().isEmpty()) profile.assignDefaultName(loaded);
                    // Some legacy rule codecs silently drop unknown fields or
                    // replace unknown operators with defaults. Refuse editing
                    // unless the nested data survives their actual round trip.
                    JsonObject normalized = profile.toJson(false);
                    if (!preservesNestedRules(profileJson, normalized)) return this.retainUnsupportedData(jsonObject);
                    loaded.add(profile);
                }
            }
            if (jsonObject.has("selected") && !jsonObject.get("selected").isJsonNull()) {
                String selectedName = jsonObject.get("selected").getAsString();
                for (InventoryCleanerProfile profile : loaded) {
                    if (profile.getName().equalsIgnoreCase(selectedName)) {
                        selected = profile;
                        break;
                    }
                }
                if (selected == null) return this.retainUnsupportedData(jsonObject);
            }
        } catch (RuntimeException invalid) {
            // A bad profile must not partially erase an existing inventory set.
            return this.retainUnsupportedData(jsonObject);
        }
        this.unsupportedData = null;
        this.preservedData = ConfigJsonUtils.deepCopy(jsonObject);
        this.profiles.clear();
        this.profiles.addAll(loaded);
        this.setValue(selected);
        return true;
    }

    private boolean retainUnsupportedData(JsonObject jsonObject) {
        this.unsupportedData = ConfigJsonUtils.deepCopy(jsonObject);
        return false;
    }

    private static boolean preservesNestedRules(JsonObject source, JsonObject normalized) {
        for (String field : new String[]{"slots", "inventoryFilters"}) {
            if (!source.has(field)) continue;
            JsonArray before = ConfigJsonUtils.getJsonArray(source, field);
            JsonArray after = ConfigJsonUtils.getJsonArray(normalized, field);
            if (before == null) return false;
            if (before.size() == 0 && after == null) continue;
            if (after == null || before.size() != after.size()) return false;
            for (int i = 0; i < before.size(); ++i) {
                if (!before.get(i).isJsonObject() || !after.get(i).isJsonObject()) return false;
                JsonObject oldRule = before.get(i).getAsJsonObject(), newRule = after.get(i).getAsJsonObject();
                // Unknown outer fields are kept by the rule codec. Check its
                // interpreted fields separately, including nested presets.
                for (String key : new String[]{"slot", "filterAction", "priority", "customRule"}) {
                    if (oldRule.has(key) && (!newRule.has(key) || !preservesJson(oldRule.get(key), newRule.get(key), key))) return false;
                }
                if (oldRule.has("itemFilter") && !preservesItemFilter(oldRule.get("itemFilter"), newRule.get("itemFilter"))) return false;
            }
        }
        return true;
    }

    private static boolean preservesItemFilter(JsonElement before, JsonElement after) {
        if (before != null && before.isJsonObject()) {
            JsonObject legacy = before.getAsJsonObject();
            JsonArray items = ConfigJsonUtils.getJsonArray(legacy, "items");
            // The legacy codec only reads the first item; don't silently lose
            // extra entries or extensions when converting to the scalar form.
            if (legacy.entrySet().size() != 1 || items == null || items.size() > 1) return false;
            return items.size() == 0 ? after == null || after.isJsonNull() : preservesJson(items.get(0), after, "itemFilter");
        }
        return preservesJson(before, after, "itemFilter");
    }

    private static boolean preservesJson(JsonElement before, JsonElement after, String key) {
        if (before == null || before.isJsonNull()) return after == null || after.isJsonNull();
        if (after == null || after.isJsonNull()) return false;
        if (before.isJsonObject() && after.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : before.getAsJsonObject().entrySet()) {
                if (!after.getAsJsonObject().has(entry.getKey())
                        || !preservesJson(entry.getValue(), after.getAsJsonObject().get(entry.getKey()), entry.getKey())) return false;
            }
            return true;
        }
        if (before.isJsonArray() && after.isJsonArray()) {
            JsonArray first = before.getAsJsonArray(), second = after.getAsJsonArray();
            if (first.size() != second.size()) return false;
            for (int i = 0; i < first.size(); ++i) if (!preservesJson(first.get(i), second.get(i), key)) return false;
            return true;
        }
        if (before.equals(after)) return true;
        // These are case-insensitive names/UUIDs in the existing codecs;
        // other strings, notably base64-encoded condition text, are exact.
        return before.isJsonPrimitive() && after.isJsonPrimitive()
                && before.getAsJsonPrimitive().isString() && after.getAsJsonPrimitive().isString()
                && ("uuid".equals(key) || "customRule".equals(key) || "filterAction".equals(key)
                    || "priority".equals(key) || "operator".equals(key) || "mode".equals(key) || "durabilityMode".equals(key))
                && before.getAsString().equalsIgnoreCase(after.getAsString());
    }

    public InventoryCleanerProfileValue(Object owner, String id) {
        this(owner, id, false);
    }

    private InventoryCleanerProfileValue(Object owner, String id, boolean detachedPreview) {
        super(owner, id, null);
        this.detachedPreview = detachedPreview;
    }


    public void addProfile(InventoryCleanerProfile profile) {
        this.profiles.add(profile);
        this.notifyChanged();
    }
}
