package gg.vape.module.utility.inventory.cleaner;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import gg.vape.Vape;
import gg.vape.config.ConfigJsonUtils;
import gg.vape.module.utility.InventoryManager;
import gg.vape.unmap.ModeOption;
import gg.vape.unmap.ModeSelection;
import gg.vape.value.ModeValue;
import gg.vape.wrapper.impl.ItemStack;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

public class InventoryCleanerProfile {
    private String name = "";
    private final boolean detachedPreview;
    public final ModeOption noArmorManagement;
    public ModeValue armorMode;
    private final Set<ItemInventoryFilterRule> itemRules;
    private final Map<Integer, SlotInventoryFilterRule> slotRules = new LinkedHashMap<Integer, SlotInventoryFilterRule>();
    private JsonObject preservedData = new JsonObject();
    public final ModeOption bestArmor;

    public void clearItemRules() {
        this.itemRules.clear();
    }

    public JsonObject toJson(boolean embedSharedPresets) {
        JsonObject jsonObject = ConfigJsonUtils.deepCopy(this.preservedData);
        jsonObject.remove("slots");
        jsonObject.remove("inventoryFilters");
        jsonObject.addProperty("name", this.name);
        JsonArray slotRulesJson = new JsonArray();
        for (SlotInventoryFilterRule rule : this.slotRules.values()) {
            JsonObject ruleJson = rule.toJson(embedSharedPresets);
            if (ruleJson.entrySet().size() <= 1) continue;
            slotRulesJson.add((JsonElement)ruleJson);
        }
        if (slotRulesJson.size() > 0) {
            jsonObject.add("slots", (JsonElement)slotRulesJson);
        }
        JsonArray itemRulesJson = new JsonArray();
        for (ItemInventoryFilterRule itemRule : this.itemRules) {
            itemRulesJson.add((JsonElement)itemRule.toJson(embedSharedPresets));
        }
        if (itemRulesJson.size() > 0) {
            jsonObject.add("inventoryFilters", (JsonElement)itemRulesJson);
        }
        jsonObject.addProperty("armor_mode", ((ModeSelection)this.armorMode.getValue()).getName());
        return jsonObject;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void addItemRule(ItemInventoryFilterRule itemRule) {
        this.itemRules.add(itemRule);
    }

    public void removeItemRule(ItemInventoryFilterRule itemRule) {
        this.itemRules.remove(itemRule);
    }


    public String getName() {
        return this.name;
    }

    /** A profile preview must not edit the shared preset registry. */
    public boolean isDetachedPreview() {
        return this.detachedPreview;
    }

    public void assignDefaultName() {
        this.assignDefaultName(Vape.INSTANCE.getModManager().getMod(InventoryManager.class).getProfileValue().getProfiles());
    }

    public void assignDefaultName(Collection<InventoryCleanerProfile> profiles) {
        int suffix = 1;
        boolean taken;
        do {
            taken = false;
            for (InventoryCleanerProfile profile : profiles) {
                if (profile != this && profile.getName().equalsIgnoreCase("Inventory #" + suffix)) {
                    taken = true;
                    ++suffix;
                    break;
                }
            }
        } while (taken);
        this.name = "Inventory #" + suffix;
    }

    public @UnmodifiableView Collection<SlotInventoryFilterRule> getSlotRules() {
        return this.slotRules.values();
    }

    public InventoryCleanerProfile(JsonObject jsonObject) {
        this(jsonObject, false);
    }

    public InventoryCleanerProfile(JsonObject jsonObject, boolean detachedPreview) {
        JsonElement jsonElement;
        SlotInventoryFilterRule slotInventoryFilterRule;
        this.detachedPreview = detachedPreview;
        this.preservedData = ConfigJsonUtils.deepCopy(jsonObject);
        this.itemRules = new LinkedHashSet<ItemInventoryFilterRule>();
        this.noArmorManagement = new ModeOption("No armor management");
        this.bestArmor = new ModeOption("Best armor");
        this.armorMode = this.createArmorMode();
        this.armorMode.setPersistenceSuppressed(true);
        this.name = jsonObject.get("name").getAsString();
        if (this.name.trim().isEmpty()) {
            this.assignDefaultName();
        }
        JsonArray slotRulesJson = jsonObject.getAsJsonArray("slots");
        if (slotRulesJson != null) {
            for (int i = 0; i < slotRulesJson.size(); ++i) {
                JsonObject ruleJson = slotRulesJson.get(i).getAsJsonObject();
                slotInventoryFilterRule = new SlotInventoryFilterRule(ruleJson);
                this.slotRules.put(slotInventoryFilterRule.getSlot(), slotInventoryFilterRule);
            }
        }
        JsonArray itemRulesJson = jsonObject.getAsJsonArray("inventoryFilters");
        if (itemRulesJson != null) {
            for (int i = 0; i < itemRulesJson.size(); ++i) {
                JsonObject filterJson = itemRulesJson.get(i).getAsJsonObject();
                ItemInventoryFilterRule itemInventoryFilterRule = new ItemInventoryFilterRule(filterJson);
                this.itemRules.add(itemInventoryFilterRule);
            }
        }
        if ((jsonElement = jsonObject.get("armor_mode")) != null) {
            String savedMode = jsonElement.getAsString();
            if (!this.noArmorManagement.getSerializedName().equalsIgnoreCase(savedMode)
                    && !this.bestArmor.getSerializedName().equalsIgnoreCase(savedMode)) {
                throw new IllegalArgumentException("Unsupported armor mode");
            }
            this.armorMode.parse(savedMode);
        }
        this.armorMode.setPersistenceSuppressed(detachedPreview);
    }

    private ModeValue createArmorMode() {
        // The mode is serialized inside the inventory, not as an independent
        // global value. Its options still need an owner for isSelected().
        ModeValue mode = new ModeValue(null, "armor_mode", "", this.noArmorManagement,
                new ModeSelection[]{this.noArmorManagement, this.bestArmor});
        mode.setDescription("Armor Mode");
        this.noArmorManagement.attachToModeWithoutRegistry(mode);
        this.bestArmor.attachToModeWithoutRegistry(mode);
        return mode;
    }

    public SlotInventoryFilterRule getOrCreateSlotRule(int slot) {
        return this.slotRules.computeIfAbsent(slot, SlotInventoryFilterRule::new);
    }

    public InventoryCleanerProfile() {
        this(false);
    }

    public InventoryCleanerProfile(boolean detachedPreview) {
        this.detachedPreview = detachedPreview;
        this.itemRules = new LinkedHashSet<ItemInventoryFilterRule>();
        this.noArmorManagement = new ModeOption("No armor management");
        this.bestArmor = new ModeOption("Best armor");
        this.armorMode = this.createArmorMode();
        this.armorMode.setPersistenceSuppressed(detachedPreview);
        this.assignDefaultName();
        for (int i = 0; i < 9; ++i) {
            this.slotRules.put(i, new SlotInventoryFilterRule(i));
        }
    }

    @Nullable
    public ItemInventoryFilterRule findMatchingItemRule(ItemStack itemStack) {
        for (ItemInventoryFilterRule itemRule : this.itemRules) {
            if (!itemRule.getItemSelection().matches(itemStack) || !itemRule.matches(itemStack)) continue;
            return itemRule;
        }
        return null;
    }

    public @UnmodifiableView Collection<ItemInventoryFilterRule> getItemRules() {
        return this.itemRules;
    }
}
