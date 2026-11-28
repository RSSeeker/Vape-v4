package gg.vape.value;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import gg.vape.unmap.ItemLimitData;
import gg.vape.value.ListValue;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.EnumHand;
import gg.vape.wrapper.impl.ForgeVersion;
import gg.vape.wrapper.impl.ItemStack;
import gg.vape.wrapper.impl.Minecraft;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LimitValue
extends ListValue<ItemLimitData, LimitValue> {
    private final Color accentColor;
    public static final Color NEUTRAL_LIST_COLOR;
    public static final Color ALLOW_LIST_COLOR;
    private final int defaultTotalStacks;
    public static final Color BLOCK_LIST_COLOR;
    private boolean includeOffhand = false;

    public void replaceEntries(List<ItemLimitData> entries) {
        super.getValue().clear();
        super.getValue().addAll(entries);
    }

    public static LimitValue createInternal(Object owner, String id, String name, Color accentColor, int defaultTotalStacks, List<ItemLimitData> entries) {
        LimitValue limitValue = new LimitValue(owner, id, name, accentColor, defaultTotalStacks, entries);
        limitValue.getValue().addAll(entries);
        return limitValue;
    }

    public LimitValue copyDefinition() {
        return new LimitValue(null, this.getId(), this.getName(), this.getAccentColor(), this.getDefaultTotalStacks(), this.getDefaultValue());
    }

    public ItemLimitData addEntry(String value, int legacyIndex) {
        return this.addEntry(new ItemLimitData(value, legacyIndex));
    }

    public ItemLimitData addEntry(ItemLimitData itemLimitData) {
        this.getValue().add(itemLimitData);
        if (this.defaultTotalStacks == -1) {
            itemLimitData.setTotalStacks(-1);
        }
        return itemLimitData;
    }

    public boolean matchesEnabledNameOrHasNoEnabledEntries(String name) {
        boolean hasNoEnabledEntries = true;
        for (ItemLimitData entry : this.getValue()) {
            if (!entry.isMatchingEnabled()) continue;
            if (entry.getName().toLowerCase().equals(name.toLowerCase())) {
                return true;
            }
            hasNoEnabledEntries = false;
        }
        return hasNoEnabledEntries;
    }

    @Override
    public boolean loadJson(JsonObject jsonObject) {
        if (jsonObject.get("id").getAsString().equalsIgnoreCase(this.getId())) {
            if (jsonObject.get("value").isJsonArray()) {
                JsonArray jsonArray = jsonObject.get("value").getAsJsonArray();
                ArrayList<ItemLimitData> arrayList = new ArrayList<ItemLimitData>(this.getValue());
                for (ItemLimitData itemLimitData : arrayList) {
                    this.removeEntry(itemLimitData);
                }
                for (JsonElement jsonElement : jsonArray) {
                    try {
                        ItemLimitData itemLimitData2 = new ItemLimitData("", -1);
                        itemLimitData2.loadJson(jsonElement.getAsJsonObject());
                        this.addEntry(itemLimitData2);
                    }
                    catch (Exception exception) {}
                }
            }
            return true;
        }
        return super.loadJson(jsonObject);
    }

    @Override
    public String getDisplayValue() {
        List<ItemLimitData> list = this.getValue();
        if (list.isEmpty()) {
            return "None";
        }
        if (list.size() == 1) {
            return ((ItemLimitData)list.get(0)).getName();
        }
        return ((ItemLimitData)list.get(0)).getName() + " +" + (list.size() - 1);
    }

    public boolean matchesOrEmpty(ItemStack itemStack) {
        if (this.getValue().isEmpty()) {
            return true;
        }
        for (ItemLimitData itemLimitData : this.getValue()) {
            if (!itemLimitData.matches(itemStack)) continue;
            return true;
        }
        return false;
    }

    public boolean matchesHeldItem(boolean includeOffhand) {
        EntityPlayerSP player = Minecraft.thePlayer();
        if (player.isNull()) {
            return false;
        }
        ItemStack mainHandItem = Minecraft.thePlayer().getHeldItemHand();
        if (this.matches(mainHandItem)) {
            return true;
        }
        if (ForgeVersion.MC_1_12_2.d()) {
            ItemStack offHandItem = player.i(EnumHand.offHand());
            if (includeOffhand && this.matches(offHandItem)) {
                return true;
            }
        }
        return false;
    }

    public int getDefaultTotalStacks() {
        return this.defaultTotalStacks;
    }

    public boolean matchesHeldItem() {
        return this.matchesHeldItem(this.includeOffhand);
    }

    public boolean doesNotMatch(ItemStack itemStack) {
        for (ItemLimitData itemLimitData : this.getValue()) {
            if (!itemLimitData.matches(itemStack)) continue;
            return false;
        }
        return true;
    }

    @Override
    public void parse(String string) {
    }

    @Override
    public LimitValue copyValueDefinition() {
        return this.copyDefinition();
    }

    public void removeEntry(ItemLimitData itemLimitData) {
        try {
            this.getValue().remove(itemLimitData);
        }
        catch (Exception exception) {
            // empty catch block
        }
    }

    public Color getAccentColor() {
        return this.accentColor;
    }

    @Override
    public void setDirectValue(List<ItemLimitData> list) {
        this.setEntries(list);
    }

    public void setEntries(List<ItemLimitData> entries) {
        super.setDirectValue(entries);
    }

    @Override
    public JsonObject toJson(boolean bl) {
        JsonObject jsonObject = this.toJson();
        JsonArray jsonArray = new JsonArray();
        for (ItemLimitData itemLimitData : this.getValue()) {
            jsonArray.add(itemLimitData.toJson());
        }
        jsonObject.add("value", (JsonElement)jsonArray);
        return jsonObject;
    }

    public boolean isValid(ItemStack itemStack, boolean requireEntryWhenEmpty) {
        if (requireEntryWhenEmpty && this.getValue().isEmpty()) {
            return false;
        }
        if (!requireEntryWhenEmpty && this.getValue().isEmpty()) {
            return true;
        }
        for (ItemLimitData itemLimitData : this.getValue()) {
            if (!itemLimitData.matches(itemStack)) continue;
            return true;
        }
        return false;
    }

    public ItemLimitData findEntry(String name) {
        for (ItemLimitData entry : this.getValue()) {
            if (!entry.getName().toLowerCase().equals(name.toLowerCase())) continue;
            return entry;
        }
        return null;
    }

    public static LimitValue create(Object owner, String id, String name, Color accentColor, ItemLimitData ... entries) {
        return LimitValue.createInternal(owner, id, name, accentColor, -1, Arrays.asList(entries));
    }

    @Override
    public ItemLimitData createEntry(String value, int legacyIndex) {
        return this.addEntry(value, legacyIndex);
    }

    public boolean matches(ItemStack itemStack) {
        return this.isValid(itemStack, false);
    }

    @Override
    public boolean isDefault() {
        List<ItemLimitData> current = this.getValue(), defaults = this.getDefaultValue();
        if (current.size() != defaults.size()) return false;
        for (int i = 0; i < current.size(); ++i) {
            if (!current.get(i).toJson().equals(defaults.get(i).toJson())) return false;
        }
        return true;
    }

    private static List<ItemLimitData> copyEntries(List<ItemLimitData> entries) {
        List<ItemLimitData> copies = new ArrayList<ItemLimitData>();
        for (ItemLimitData entry : entries) {
            ItemLimitData copy = new ItemLimitData(entry.getName(), entry.getTotalStacks()).withMetadata(entry.getMetadata());
            copy.setEnabled(entry.isEnabled());
            copies.add(copy);
        }
        return copies;
    }

    @Override
    public void setValue(List<ItemLimitData> entries) {
        this.replaceEntries(entries);
    }

    @Override
    public void reset() {
        if (this.isResettable()) super.setValue(copyEntries(this.getDefaultValue()));
    }

    static {
        ALLOW_LIST_COLOR = new Color(0, 170, 0);
        NEUTRAL_LIST_COLOR = new Color(170, 170, 170);
        BLOCK_LIST_COLOR = new Color(170, 0, 0);
    }

    public LimitValue setIncludeOffhand(boolean includeOffhand) {
        this.includeOffhand = includeOffhand;
        return this;
    }

    public static LimitValue create(Object owner, String id, String name, Color accentColor, List<ItemLimitData> entries) {
        return LimitValue.createInternal(owner, id, name, accentColor, -1, entries);
    }

    private static Exception propagateException(Exception exception) {
        return exception;
    }

    private LimitValue(Object owner, String id, String name, Color accentColor, int defaultTotalStacks, List<ItemLimitData> entries) {
        super(owner, id, name, entries);
        // Live entries can be toggled and edited; the default list must not
        // share those mutable objects.
        this.setDefaultValue(copyEntries(entries));
        this.accentColor = accentColor;
        this.defaultTotalStacks = defaultTotalStacks;
    }
}
