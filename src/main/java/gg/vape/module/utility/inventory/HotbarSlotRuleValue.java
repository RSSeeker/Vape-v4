package gg.vape.module.utility.inventory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import gg.vape.config.ConfigJsonUtils;
import gg.vape.module.utility.inventory.HotbarSlotRule;
import gg.vape.module.utility.inventory.HotbarSlotRuleEditorComponent;
import gg.vape.value.Value;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

public class HotbarSlotRuleValue
extends Value<List<HotbarSlotRule>, HotbarSlotRuleValue> {
    @Nullable
    private HotbarSlotRuleEditorComponent editor;
    @Nullable
    private JsonObject pendingData;

    public List<HotbarSlotRule> getRules() {
        if (this.editor == null || this.editor.getSelectedGroup() == null) {
            return new ArrayList<HotbarSlotRule>();
        }
        return this.editor.getSelectedGroup().getRules();
    }

    public HotbarSlotRuleValue createCopy() {
        return new HotbarSlotRuleValue(null, this.getId());
    }

    @Override
    public HotbarSlotRuleValue copyValueDefinition() {
        return this.createCopy();
    }

    @Override
    public void parse(String string) {
    }

    public void setEditor(HotbarSlotRuleEditorComponent editor) {
        this.editor = editor;
        if (this.pendingData != null) {
            if (editor.loadJson(this.pendingData)) this.pendingData = null;
            else editor.blockInvalidData();
        }
    }


    public HotbarSlotRuleValue(Object object, String string) {
        super(object, string, new ArrayList());
    }

    @Override
    public boolean loadJson(JsonObject jsonObject) {
        if (!this.matchesJsonId(jsonObject)) return false;
        if (this.editor != null && this.editor.loadJson(jsonObject)) {
            this.pendingData = null;
        } else {
            this.pendingData = ConfigJsonUtils.deepCopy(jsonObject);
            if (this.editor != null) this.editor.blockInvalidData();
        }
        return true;
    }

    @Override
    public boolean isDefault() {
        JsonObject data = this.toJson(false);
        for (Map.Entry<String, JsonElement> entry : data.entrySet()) {
            String key = entry.getKey();
            if (!"id".equals(key) && !"selected".equals(key) && !"panels".equals(key)) return false;
        }
        if (data.has("selected")) {
            try {
                if (data.get("selected").isJsonNull() || data.get("selected").getAsInt() != 0) return false;
            } catch (RuntimeException invalid) {
                return false;
            }
        }
        if (!data.has("panels")) return true;
        return data.get("panels").isJsonArray() && data.getAsJsonArray("panels").size() == 0;
    }

    @Override
    public void reset() {
        if (!this.isResettable()) return;
        this.pendingData = null;
        if (this.editor != null) this.editor.loadJson(new JsonObject());
        super.reset();
    }

    @Override
    public String getDisplayValue() {
        List<HotbarSlotRule> list = this.getRules();
        if (list.isEmpty()) {
            return "None";
        }
        if (list.size() == 1) {
            return String.valueOf(list.get(0).getItemId());
        }
        return list.get(0).getItemId() + " +" + (list.size() - 1);
    }

    @Override
    public JsonObject toJson(boolean bl) {
        JsonObject jsonObject = this.pendingData != null ? ConfigJsonUtils.deepCopy(this.pendingData)
                : this.editor != null ? this.editor.serializeRules() : new JsonObject();
        jsonObject.addProperty("id", this.getId());
        return jsonObject;
    }

    @Nullable
    public HotbarSlotRuleEditorComponent getEditor() {
        return this.editor;
    }

    public static HotbarSlotRuleValue create(Object owner, String id) {
        return new HotbarSlotRuleValue(owner, id);
    }
}
