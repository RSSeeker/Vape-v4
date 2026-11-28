package gg.vape.module.utility.inventory.cleaner;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import gg.vape.config.ConfigJsonUtils;
import gg.vape.module.utility.inventory.cleaner.InventoryFilterPreset;
import gg.vape.module.utility.inventory.cleaner.InventoryFilterRule;
import gg.vape.module.utility.inventory.cleaner.InventoryItemCategory;
import gg.vape.module.utility.inventory.cleaner.InventoryItemCategoryRegistry;
import gg.vape.module.utility.inventory.cleaner.InventoryItemMatcher;
import gg.vape.module.utility.inventory.cleaner.InventoryItemMatcherRegistry;
import gg.vape.module.utility.inventory.cleaner.ItemFilterSelection;
import gg.vape.module.utility.inventory.cleaner.SharedInventoryFilterPreset;
import gg.vape.module.utility.inventory.cleaner.ui.ItemPickerSelection;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractInventoryFilterRule
implements InventoryFilterRule {
    @Nullable
    private InventoryFilterPreset inlinePreset;
    @Nullable
    private InventoryItemCategory priorityOverride;
    private final ItemFilterSelection itemSelection;
    private JsonObject preservedData = new JsonObject();
    @Nullable
    private UUID sharedPresetId;

    @Override
    public ItemFilterSelection getItemSelection() {
        return this.itemSelection;
    }

    @Override
    @Nullable
    public UUID getSharedPresetId() {
        return this.sharedPresetId;
    }

    @Override
    public InventoryItemCategory getDefaultPriority() {
        InventoryItemCategory category;
        InventoryItemMatcher matcher = this.getItemSelection().getMatcher();
        if (matcher == null && this.itemSelection.getItemStack() != null) {
            matcher = InventoryItemMatcherRegistry.findBestMatch(this.itemSelection.getItemStack());
        }
        if (matcher != null && (category = matcher.getCategory()) != null) {
            return category;
        }
        return InventoryItemCategoryRegistry.FIRST_AVAILABLE;
    }

    @Override
    @NotNull
    public InventoryItemCategory getPriority() {
        return this.priorityOverride != null ? this.priorityOverride : this.getDefaultPriority();
    }

    @Override
    public void clearPresetReference() {
        this.inlinePreset = null;
        this.sharedPresetId = null;
    }

    @Override
    public void setPreset(@Nullable InventoryFilterPreset preset) {
        this.clearPresetReference();
        if (preset == null) {
            return;
        }
        if (preset instanceof SharedInventoryFilterPreset) {
            this.sharedPresetId = preset.getId();
        } else {
            this.inlinePreset = preset;
        }
    }

    @Override
    public void setPriorityOverride(@Nullable InventoryItemCategory priority) {
        this.priorityOverride = priority;
    }

    public AbstractInventoryFilterRule(JsonObject jsonObject) {
        this.preservedData = ConfigJsonUtils.deepCopy(jsonObject);
        this.itemSelection = new ItemFilterSelection(jsonObject.get("itemFilter"));
        if (jsonObject.has("customRule")) {
            JsonElement jsonElement = jsonObject.get("customRule");
            if (jsonElement.isJsonPrimitive()) {
                this.sharedPresetId = ConfigJsonUtils.getUuid(jsonObject, "customRule");
                if (this.sharedPresetId == null) throw new IllegalArgumentException("Invalid shared preset ID");
            } else {
                this.inlinePreset = new InventoryFilterPreset(jsonObject.getAsJsonObject("customRule"));
            }
        }
        if (jsonObject.has("priority")) {
            this.priorityOverride = InventoryItemCategoryRegistry.getById(jsonObject.get("priority").getAsString());
        }
    }

    public AbstractInventoryFilterRule() {
        this.itemSelection = new ItemFilterSelection();
    }

    public JsonObject toJson(boolean embedSharedPreset) {
        JsonObject jsonObject = ConfigJsonUtils.deepCopy(this.preservedData);
        jsonObject.remove("itemFilter");
        jsonObject.remove("customRule");
        jsonObject.remove("priority");
        jsonObject.add("itemFilter", this.itemSelection.toJson());
        InventoryFilterPreset preset = this.inlinePreset;
        if (embedSharedPreset || preset != null) {
            if (preset == null) {
                preset = this.resolvePreset();
            }
            if (preset != null) {
                jsonObject.add("customRule", (JsonElement)preset.toJson());
            } else if (this.sharedPresetId != null) {
                // If a shared preset is unavailable, preserve the reference
                // instead of silently publishing a rule with no condition.
                jsonObject.addProperty("customRule", this.sharedPresetId.toString());
            }
        } else if (this.sharedPresetId != null) {
            jsonObject.addProperty("customRule", this.sharedPresetId.toString());
        }
        if (this.priorityOverride != null) {
            jsonObject.addProperty("priority", this.priorityOverride.getId());
        }
        return jsonObject;
    }


    @Nullable
    protected InventoryFilterPreset getInlinePreset() {
        return this.inlinePreset;
    }

    @Override
    public void reset() {
        this.itemSelection.setSelection(ItemPickerSelection.empty());
        this.clearPresetReference();
    }
}

