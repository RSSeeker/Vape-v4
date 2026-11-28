package gg.vape.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import gg.vape.module.Mod;
import gg.vape.value.Value;
import java.util.Collection;
import java.util.Map;

/** Keeps data owned by unavailable modules or settings when live state is captured. */
public final class ProfileDataRetention {
    private ProfileDataRetention() {
    }

    public static void preserveUnknown(JsonObject previous, JsonObject captured, Collection<Mod> topLevelModules,
                                       Collection<Mod> allModules, Collection<Value<?, ?>> globalValues) {
        if (previous == null || captured == null) return;
        JsonArray previousModules = ConfigJsonUtils.getJsonArray(previous, "modules");
        JsonArray capturedModules = ConfigJsonUtils.getJsonArray(captured, "modules");
        if (previousModules != null && capturedModules != null) {
            for (JsonElement element : previousModules) {
                if (element == null || !element.isJsonObject()) {
                    capturedModules.add(ConfigJsonUtils.deepCopy(element));
                    continue;
                }
                JsonObject oldModule = element.getAsJsonObject();
                String name = ConfigJsonUtils.getString(oldModule, "name");
                Mod module = findModule(topLevelModules, name);
                if (module == null) {
                    capturedModules.add(ConfigJsonUtils.deepCopy(element));
                    continue;
                }
                JsonObject currentModule = findModuleJson(capturedModules, name);
                JsonObject extra = new JsonObject();
                for (Map.Entry<String, JsonElement> entry : oldModule.entrySet()) {
                    String key = entry.getKey();
                    if (!"name".equals(key) && !"values".equals(key) && !"visible".equals(key)
                            && !"keybinds".equals(key) && !"keybinds_2".equals(key) && !"bind_mode".equals(key)) {
                        extra.add(key, ConfigJsonUtils.deepCopy(entry.getValue()));
                    }
                }
                JsonArray oldValues = ConfigJsonUtils.getJsonArray(oldModule, "values");
                JsonArray unknownValues = new JsonArray();
                if (oldValues != null) appendUnknownValues(oldValues, unknownValues, module.getAllValues());
                else if (oldModule.has("values")) extra.add("values", ConfigJsonUtils.deepCopy(oldModule.get("values")));
                if (extra.entrySet().isEmpty() && unknownValues.size() == 0) continue;
                if (currentModule == null) {
                    currentModule = new JsonObject();
                    currentModule.addProperty("name", module.getName());
                    capturedModules.add(currentModule);
                }
                for (Map.Entry<String, JsonElement> entry : extra.entrySet()) {
                    if (!currentModule.has(entry.getKey())) currentModule.add(entry.getKey(), entry.getValue());
                }
                if (unknownValues.size() != 0) {
                    JsonArray values = ConfigJsonUtils.getJsonArray(currentModule, "values");
                    if (values == null) {
                        values = new JsonArray();
                        currentModule.add("values", values);
                    }
                    for (JsonElement value : unknownValues) values.add(value);
                }
            }
        }
        JsonArray oldGlobals = ConfigJsonUtils.getJsonArray(previous, "values");
        JsonArray capturedGlobals = ConfigJsonUtils.getJsonArray(captured, "values");
        if (oldGlobals != null && capturedGlobals != null) appendUnknownValues(oldGlobals, capturedGlobals, globalValues);
        preserveUnknownStates(previous, captured, "enabled", allModules);
        preserveUnknownStates(previous, captured, "legit_enabled", allModules);
        for (Map.Entry<String, JsonElement> entry : previous.entrySet()) {
            if (!captured.has(entry.getKey())) captured.add(entry.getKey(), ConfigJsonUtils.deepCopy(entry.getValue()));
        }
    }

    private static void appendUnknownValues(JsonArray previous, JsonArray destination, Collection<Value<?, ?>> knownValues) {
        for (JsonElement element : previous) {
            boolean known = false;
            if (element != null && element.isJsonObject()) {
                for (Value<?, ?> value : knownValues) {
                    if (value.matchesJsonId(element.getAsJsonObject())) { known = true; break; }
                }
            }
            if (!known) destination.add(ConfigJsonUtils.deepCopy(element));
        }
    }

    private static void preserveUnknownStates(JsonObject previous, JsonObject captured, String key, Collection<Mod> modules) {
        JsonObject oldStates = ConfigJsonUtils.getJsonObject(previous, key);
        JsonObject currentStates = ConfigJsonUtils.getJsonObject(captured, key);
        if (oldStates == null || currentStates == null) return;
        for (Map.Entry<String, JsonElement> entry : oldStates.entrySet()) {
            if (findModule(modules, entry.getKey()) == null && !currentStates.has(entry.getKey())) {
                currentStates.add(entry.getKey(), ConfigJsonUtils.deepCopy(entry.getValue()));
            }
        }
    }

    private static Mod findModule(Collection<Mod> modules, String name) {
        if (name == null) return null;
        for (Mod module : modules) if (module.getName().equalsIgnoreCase(name)) return module;
        return null;
    }

    private static JsonObject findModuleJson(JsonArray modules, String name) {
        if (name == null) return null;
        for (JsonElement element : modules) {
            if (!element.isJsonObject()) continue;
            JsonObject object = element.getAsJsonObject();
            String candidate = ConfigJsonUtils.getString(object, "name");
            if (candidate != null && candidate.equalsIgnoreCase(name)) return object;
        }
        return null;
    }
}
