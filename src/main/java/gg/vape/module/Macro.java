package gg.vape.module;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import gg.vape.config.ClientSettings;
import gg.vape.config.ConfigJsonUtils;
import gg.vape.input.KeyboardCodeUtil;
import gg.vape.module.macro.CommandMacro;
import gg.vape.module.macro.ItemMacro;
import gg.vape.module.macro.FishingRodMacro;
import gg.vape.module.macro.MacroAction;
import gg.vape.unmap.Bendable;
import gg.vape.utils.Base64Util;
import gg.vape.value.BooleanValue;
import gg.vape.value.RandomValue;
import java.util.Collections;

public abstract class Macro
extends Bendable {
    private final RandomValue delay = RandomValue.createWithIncrement(this, "Delay", "#", "", 1.0, 100.0, 200.0, 1000.0, 1.0);
    private final BooleanValue doubleClick = BooleanValue.create(this, "Double Click", false);
    private final RandomValue doubleClickDelay = RandomValue.create(this, "Double Click Delay", "#", "", 1.0, 100.0, 200.0, 1000.0);
    private String name;

    public BooleanValue getDoubleClick() {
        return this.doubleClick;
    }

    public Macro loadJson(JsonObject jsonObject) {
        if (jsonObject.has("name")) {
            this.name = ConfigJsonUtils.getDecodedStringOrEmpty(jsonObject, "name");
        }
        if (jsonObject.get("keybinds") != null && jsonObject.get("keybinds").isJsonArray()) {
            try {
                this.loadBoundInputs(jsonObject.getAsJsonArray("keybinds"), false);
            }
            catch (Exception exception) {}
        } else if (jsonObject.get("key_2") != null && !jsonObject.get("key_2").isJsonNull()) {
            this.setBoundInputs(Collections.singletonList(jsonObject.get("key_2").getAsInt()));
        } else if (jsonObject.get("key") != null && !jsonObject.get("key").isJsonNull()) {
            int legacyKey = jsonObject.get("key").getAsInt();
            if (legacyKey > 0) {
                legacyKey = KeyboardCodeUtil.convertLegacyKeyCode(legacyKey);
            }
            this.setBoundInputs(Collections.singletonList(legacyKey));
        } else {
            this.getBoundInputs().clear();
        }
        if (jsonObject.get("double_click_enabled") != null && !jsonObject.get("double_click_enabled").isJsonNull()) {
            this.doubleClick.loadJson(jsonObject.get("double_click_enabled").getAsJsonObject());
        }
        if (jsonObject.get("delay") != null && jsonObject.get("delay").isJsonObject()) {
            this.delay.loadJson(jsonObject.get("delay").getAsJsonObject());
        }
        if (jsonObject.get("double_click") != null && !jsonObject.get("double_click").isJsonNull()) {
            this.doubleClickDelay.loadJson(jsonObject.get("double_click").getAsJsonObject());
        }
        return this;
    }

    protected Macro(String name) {
        this.name = name;
        this.doubleClick.getDependentValues().add(this.doubleClickDelay);
    }

    public abstract MacroAction createAction();

    @Override
    public String getDisplayText() {
        return String.format(" %s7[%sr%s%s7]%sr %s", ClientSettings.FORMAT_CODE, ClientSettings.FORMAT_CODE, this.getBindText(), ClientSettings.FORMAT_CODE, ClientSettings.FORMAT_CODE, this.getName());
    }

    public String getName() {
        return this.name;
    }

    public static Macro create(String name) {
        if (name.startsWith("fishing rod")) {
            return new FishingRodMacro();
        }
        if (name.startsWith("/")) {
            return new CommandMacro(name);
        }
        return new ItemMacro(name);
    }

    @Override
    public void onBindActivated() {
    }

    public JsonObject toJson() {
        JsonObject jsonObject = new JsonObject();
        String encodedName = "b64:" + Base64Util.encodeUtf8Base64(this.name);
        jsonObject.addProperty("name", encodedName);
        jsonObject.add("keybinds", (JsonElement)this.serializeBoundInputs());
        jsonObject.add("delay", (JsonElement)this.delay.toJson(false));
        jsonObject.add("double_click_enabled", (JsonElement)this.doubleClick.toJson(false));
        jsonObject.add("double_click", (JsonElement)this.doubleClickDelay.toJson(false));
        return jsonObject;
    }

    public RandomValue getDelay() {
        return this.delay;
    }

    public RandomValue getDoubleClickDelay() {
        return this.doubleClickDelay;
    }

    @Override
    public boolean isActive() {
        return false;
    }
}

