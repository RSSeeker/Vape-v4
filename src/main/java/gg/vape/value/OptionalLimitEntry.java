package gg.vape.value;

import com.google.gson.JsonObject;
import gg.vape.ui.click.component.GuiComponent;
import gg.vape.utils.Base64Util;
import gg.vape.value.ToggleableListEntry;

public class OptionalLimitEntry
implements ToggleableListEntry {
    private boolean enabled = true;
    private String value;
    private static GuiComponent[] legacyComponents;

    @Override
    public boolean isEnabled() {
        return this.enabled;
    }

    @Override
    public void toggleEnabled() {
        this.enabled = !this.enabled;
    }


    public static void setLegacyComponents(GuiComponent[] components) {
        legacyComponents = components;
    }

    public boolean isEntryEnabled() {
        return this.enabled;
    }

    public OptionalLimitEntry(String value) {
        this.value = value;
    }

    public static GuiComponent[] getLegacyComponents() {
        return legacyComponents;
    }

    public void loadJson(JsonObject jsonObject) {
        if (jsonObject.get("item-id") != null) {
            this.value = jsonObject.get("item-id").getAsString();
            if (this.value.startsWith("b64:")) {
                this.value = Base64Util.decodeUtf8Base64(this.value.substring(4));
            }
        }
        if (jsonObject.get("enabled") != null) {
            this.enabled = jsonObject.get("enabled").getAsBoolean();
        }
    }

    public String getValue() {
        return this.value;
    }

    public JsonObject toJson() {
        JsonObject jsonObject = new JsonObject();
        String encodedValue = "b64:" + Base64Util.encodeUtf8Base64(this.value);
        jsonObject.addProperty("item-id", encodedValue);
        jsonObject.addProperty("enabled", Boolean.valueOf(this.enabled));
        return jsonObject;
    }

    public String toString() {
        return this.value;
    }

    static {
        OptionalLimitEntry.setLegacyComponents(new GuiComponent[3]);
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}

