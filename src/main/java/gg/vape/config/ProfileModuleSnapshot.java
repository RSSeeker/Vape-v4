package gg.vape.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import gg.vape.input.BindActivationMode;
import gg.vape.input.BindSet;
import gg.vape.module.Category;
import gg.vape.module.Mod;
import gg.vape.unmap.INamed;
import gg.vape.value.BindValue;
import gg.vape.value.Value;
import gg.vape.value.ValueSnapshot;
import java.util.ArrayList;
import java.util.List;

public class ProfileModuleSnapshot
implements INamed {
    private final Mod module;
    private final ValueSnapshot<BindValue, BindSet> bindSnapshot;
    private final List<ValueSnapshot<?, ?>> valueSnapshots;
    private final List<JsonElement> unknownValues;
    private final JsonObject extraFields;
    private final ProfileSnapshot profileSnapshot;
    private boolean enabled;
    private boolean enabledSpecified;
    private boolean visible;

    public int getSortPriority() {
        int priority = 0;
        if (this.hasBind()) {
            priority += 2;
        }
        if (this.isEnabled()) {
            ++priority;
        }
        if (this.hasBind() && this.isEnabled()) {
            ++priority;
        }
        return priority;
    }

    public void resetBind() {
        List<Integer> defaults = new ArrayList<Integer>();
        if (this.module.getDefaultKeybind() != 0) defaults.add(this.module.getDefaultKeybind());
        this.bindSnapshot.getValue().setBoundInputs(defaults);
        this.bindSnapshot.getValue().setActivationMode(BindActivationMode.TOGGLE);
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public List<ValueSnapshot<?, ?>> getValueSnapshots() {
        return this.valueSnapshots;
    }

    public Mod getModule() {
        return this.module;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        this.enabledSpecified = true;
    }

    boolean isEnabledSpecified() {
        return this.enabledSpecified;
    }

    public ProfileModuleSnapshot(ProfileSnapshot profileSnapshot, Mod module, JsonObject moduleJson) {
        this.profileSnapshot = profileSnapshot;
        this.module = module;
        this.valueSnapshots = new ArrayList<>();
        this.unknownValues = new ArrayList<JsonElement>();
        this.extraFields = moduleJson == null ? new JsonObject() : ConfigJsonUtils.deepCopy(moduleJson);
        this.extraFields.remove("name");
        this.extraFields.remove("visible");
        List<JsonElement> inputValues = new ArrayList<JsonElement>();
        if (moduleJson != null) {
            JsonArray valuesJson = ConfigJsonUtils.getJsonArray(moduleJson, "values");
            if (valuesJson != null) {
                this.extraFields.remove("values");
                for (JsonElement valueElement : valuesJson) inputValues.add(valueElement);
            }
        }
        List<Integer> inputCodes = new ArrayList<Integer>();
        if (module.getBind().usesOwnKeybindStorage()) {
            this.extraFields.remove("keybinds");
            this.extraFields.remove("keybinds_2");
            this.extraFields.remove("bind_mode");
            JsonArray keybinds = moduleJson == null ? null : ConfigJsonUtils.getJsonArray(moduleJson, "keybinds_2");
            boolean legacyKeys = false;
            if (keybinds == null && moduleJson != null) {
                keybinds = ConfigJsonUtils.getJsonArray(moduleJson, "keybinds");
                legacyKeys = keybinds != null;
            }
            if (keybinds != null) inputCodes.addAll(ConfigJsonUtils.parseInputCodes(keybinds, legacyKeys));
            else if (module.getDefaultKeybind() != 0) inputCodes.add(module.getDefaultKeybind());
        }
        Boolean visible = moduleJson == null ? null : ConfigJsonUtils.getBoolean(moduleJson, "visible");
        this.visible = visible != null ? visible.booleanValue() : module.isDefaultVisible();
        this.bindSnapshot = new ValueSnapshot<>(new BindValue((Object)null, "", new BindSet(inputCodes, false, module.getBind().supportsActivationMode())));
        String bindMode = moduleJson == null ? null : ConfigJsonUtils.getString(moduleJson, "bind_mode");
        if (bindMode != null && this.bindSnapshot.getValue().supportsActivationMode()) {
            try {
                this.bindSnapshot.getValue().setActivationMode(BindActivationMode.valueOf(bindMode));
            }
            catch (IllegalArgumentException illegalArgumentException) {
                this.bindSnapshot.getValue().setActivationMode(BindActivationMode.TOGGLE);
            }
        }
        boolean[] recognized = new boolean[inputValues.size()];
        for (Value<?, ?> value : module.getAllValues()) {
            JsonObject valueJson = null;
            for (int i = 0; i < inputValues.size(); ++i) {
                JsonElement element = inputValues.get(i);
                if (element != null && element.isJsonObject() && value.matchesJsonId(element.getAsJsonObject())) {
                    recognized[i] = true;
                    valueJson = element.getAsJsonObject();
                }
            }
            ValueSnapshot<?, ?> valueSnapshot = new ValueSnapshot<>(value);
            if (valueJson != null) valueSnapshot.loadJson(valueJson);
            this.valueSnapshots.add(valueSnapshot);
        }
        for (int i = 0; i < inputValues.size(); ++i) {
            if (!recognized[i]) this.unknownValues.add(ConfigJsonUtils.deepCopy(inputValues.get(i)));
        }
        Boolean savedEnabled = this.profileSnapshot.getSavedEnabledState(module);
        this.enabledSpecified = savedEnabled != null;
        // Old profiles without a HUD entry leave the current HUD state alone.
        this.enabled = savedEnabled != null ? savedEnabled.booleanValue()
                : module instanceof gg.vape.module.render.hud.HudModule && module.isEnabled();
    }

    @Override
    public String getName() {
        return this.module.getName();
    }

    public boolean hasChanges() {
        if (this.module.getCategory() == Category.NONE) {
            return false;
        }
        if (!this.unknownValues.isEmpty() || !this.extraFields.entrySet().isEmpty()
                || this.visible != this.module.isDefaultVisible()) return true;
        for (ValueSnapshot<?, ?> valueSnapshot : this.getValueSnapshots()) {
            if (valueSnapshot.isDefault()) continue;
            return true;
        }
        if (this.module.getBind().usesOwnKeybindStorage()
                && (this.hasBind() || !ConfigJsonUtils.isDefaultBinding(this.bindSnapshot.getValue().serializeBoundInputs(), this.module.getDefaultKeybind())
                || this.bindSnapshot.getValue().getActivationMode() != BindActivationMode.TOGGLE)) {
            return true;
        }
        return this.isEnabled();
    }

    public List<ValueSnapshot<?, ?>> getValues(boolean includeDefaults) {
        ArrayList<ValueSnapshot<?, ?>> values = new ArrayList<>();
        for (ValueSnapshot<?, ?> valueSnapshot : this.getValueSnapshots()) {
            if (valueSnapshot.isDefault() && !includeDefaults) continue;
            values.add(valueSnapshot);
        }
        return values;
    }

    public boolean hasBind() {
        return this.bindSnapshot.getValue().hasValidBinding();
    }

    public JsonObject toJson() {
        return this.toJson(false);
    }

    public JsonObject toJson(boolean forPublication) {
        JsonObject moduleJson = ConfigJsonUtils.deepCopy(this.extraFields);
        moduleJson.addProperty("name", this.module.getName());
        if (this.module.getBind().usesOwnKeybindStorage()) {
            JsonArray keys = this.bindSnapshot.getValue().serializeBoundInputs();
            // An explicit empty array must be retained when a default key was
            // removed; an absent array would restore that key on profile load.
            if (!ConfigJsonUtils.isDefaultBinding(keys, this.module.getDefaultKeybind())) moduleJson.add("keybinds_2", keys);
            if (this.bindSnapshot.getValue().supportsActivationMode() && this.bindSnapshot.getValue().getActivationMode() != BindActivationMode.TOGGLE) {
                moduleJson.addProperty("bind_mode", this.bindSnapshot.getValue().getActivationMode().name());
            }
        }
        JsonArray valuesJson = new JsonArray();
        for (JsonElement unknown : this.unknownValues) valuesJson.add(ConfigJsonUtils.deepCopy(unknown));
        for (ValueSnapshot<?, ?> valueSnapshot : this.valueSnapshots) {
            JsonObject valueJson;
            if (!((Value)valueSnapshot.getSourceValue()).isSerializable() || valueSnapshot.isDefault() || (valueJson = valueSnapshot.toJson(forPublication)).entrySet().size() <= 1) continue;
            valuesJson.add(valueJson);
        }
        if (valuesJson.size() != 0) {
            moduleJson.add("values", (JsonElement)valuesJson);
        }
        if (this.visible != this.module.isDefaultVisible()) {
            moduleJson.addProperty("visible", Boolean.valueOf(this.visible));
        }
        if (moduleJson.entrySet().size() == 1) {
            return null;
        }
        return moduleJson;
    }

    public String getBindDisplayText() {
        return this.bindSnapshot.getValue().getBindText();
    }

    public ValueSnapshot<BindValue, BindSet> getBindSnapshot() {
        return this.bindSnapshot;
    }
}
