package gg.vape.value;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import gg.vape.value.ListValue;
import gg.vape.value.OptionalLimitEntry;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class OptionalLimitValue
extends ListValue<OptionalLimitEntry, OptionalLimitValue> {
    public static final Color ALLOW_LIST_COLOR = new Color(0, 170, 0);
    public static final Color BLOCK_LIST_COLOR;
    private final Color accentColor;
    public static final Color NEUTRAL_LIST_COLOR;

    public List<String> getEnabledValues() {
        ArrayList<String> enabledValues = new ArrayList<String>();
        for (OptionalLimitEntry entry : this.getValue()) {
            if (!entry.isEntryEnabled()) continue;
            enabledValues.add(entry.getValue());
        }
        return enabledValues;
    }

    private static Exception propagateException(Exception exception) {
        return exception;
    }

    @Override
    public void parse(String string) {
    }

    public OptionalLimitValue copyDefinition() {
        return new OptionalLimitValue(null, this.getId(), this.getName(), this.getAccentColor());
    }

    @Override
    public OptionalLimitEntry createEntry(String value, int legacyIndex) {
        return this.addEntry(value, legacyIndex);
    }

    private OptionalLimitValue(Object owner, String id, String name, Color accentColor) {
        super(owner, id, name);
        this.accentColor = accentColor;
    }

    @Override
    public void setValue(List<OptionalLimitEntry> entries) {
        this.replaceEntries(entries);
    }

    @Override
    public OptionalLimitValue copyValueDefinition() {
        return this.copyDefinition();
    }

    @Override
    public boolean isDefault() {
        List<OptionalLimitEntry> current = this.getValue(), defaults = this.getDefaultValue();
        if (current.size() != defaults.size()) return false;
        for (int i = 0; i < current.size(); ++i) {
            if (!current.get(i).toJson().equals(defaults.get(i).toJson())) return false;
        }
        return true;
    }

    @Override
    public JsonObject toJson(boolean bl) {
        JsonObject jsonObject = this.toJson();
        JsonArray jsonArray = new JsonArray();
        for (OptionalLimitEntry entry : this.getValue()) {
            jsonArray.add(entry.toJson());
        }
        jsonObject.add("value", (JsonElement)jsonArray);
        return jsonObject;
    }

    public OptionalLimitEntry addEntry(String value) {
        OptionalLimitEntry entry = new OptionalLimitEntry(value);
        this.getValue().add(entry);
        return entry;
    }

    public OptionalLimitEntry addEntry(String value, int legacyIndex) {
        return this.addEntry(value);
    }

    @Override
    public boolean loadJson(JsonObject jsonObject) {
        if (jsonObject.get("id").getAsString().equalsIgnoreCase(this.getId())) {
            if (jsonObject.get("value").isJsonArray()) {
                JsonArray jsonArray = jsonObject.get("value").getAsJsonArray();
                ArrayList<OptionalLimitEntry> existingEntries = new ArrayList<OptionalLimitEntry>(this.getValue());
                for (OptionalLimitEntry entry : existingEntries) {
                    this.removeEntry(entry);
                }
                for (JsonElement jsonElement : jsonArray) {
                    try {
                        OptionalLimitEntry entry = this.addEntry("");
                        entry.loadJson(jsonElement.getAsJsonObject());
                    }
                    catch (Exception exception) {}
                }
            }
            return true;
        }
        return super.loadJson(jsonObject);
    }

    private static List<OptionalLimitEntry> copyEntries(List<OptionalLimitEntry> entries) {
        List<OptionalLimitEntry> copies = new ArrayList<OptionalLimitEntry>();
        for (OptionalLimitEntry entry : entries) {
            OptionalLimitEntry copy = new OptionalLimitEntry(entry.getValue());
            copy.setEnabled(entry.isEnabled());
            copies.add(copy);
        }
        return copies;
    }

    public void replaceEntries(List<OptionalLimitEntry> entries) {
        // Copy before clearing: callers may pass the list that is being edited.
        List<OptionalLimitEntry> replacements = copyEntries(entries);
        this.getValue().clear();
        this.getValue().addAll(replacements);
    }

    public boolean matches(String value, boolean defaultWhenEmpty) {
        if (this.getValue().isEmpty()) {
            return defaultWhenEmpty;
        }
        for (OptionalLimitEntry entry : this.getValue()) {
            if (!entry.isEntryEnabled() || !entry.getValue().equalsIgnoreCase(value)) continue;
            return true;
        }
        return false;
    }

    static {
        NEUTRAL_LIST_COLOR = new Color(170, 170, 170);
        BLOCK_LIST_COLOR = new Color(170, 0, 0);
    }

    public static OptionalLimitValue createWithDescription(Object owner, String id, String name, String description, Color accentColor, List<String> values) {
        OptionalLimitValue optionalLimitValue = new OptionalLimitValue(owner, id, name, accentColor);
        for (String value : values) {
            optionalLimitValue.addEntry(value);
        }
        optionalLimitValue.setDefaultValue(copyEntries(optionalLimitValue.getValue()));
        return (OptionalLimitValue)optionalLimitValue.setDescription(description);
    }

    public static OptionalLimitValue create(Object owner, String id, String name, Color accentColor, String ... values) {
        return OptionalLimitValue.createWithDescription(owner, id, name, "List of Names/Strings", accentColor, Arrays.asList(values));
    }

    public void removeEntry(OptionalLimitEntry entry) {
        try {
            this.getValue().remove(entry);
        }
        catch (Exception exception) {
            // empty catch block
        }
    }

    @Override
    public String getDisplayValue() {
        List<OptionalLimitEntry> list = this.getValue();
        if (list.isEmpty()) {
            return "None";
        }
        if (list.size() == 1) {
            return ((OptionalLimitEntry)list.get(0)).getValue();
        }
        return ((OptionalLimitEntry)list.get(0)).getValue() + " +" + (list.size() - 1);
    }

    public Color getAccentColor() {
        return this.accentColor;
    }
}
