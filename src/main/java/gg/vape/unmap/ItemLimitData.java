package gg.vape.unmap;

import com.google.gson.JsonObject;
import gg.vape.Vape;
import gg.vape.utils.Base64Util;
import gg.vape.value.ToggleableListEntry;
import gg.vape.wrapper.impl.Item;
import gg.vape.wrapper.impl.ItemStack;
import gg.vape.wrapper.impl.Minecraft;
import java.util.Arrays;
import java.util.List;

public class ItemLimitData
implements ToggleableListEntry {
    private int metadata = -1;
    private String name = "";
    private static String legacyState;
    private int totalStacks;
    public static final List<ItemLimitData> INTERACTIVE_BLOCKS;
    private boolean enabled = true;
    public static ItemLimitData AIR;
    public static final List<ItemLimitData> DEFAULT_BLOCK_BLACKLIST;

    private static Exception propagateException(Exception exception) {
        return exception;
    }

    public void setMatchingEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean isEnabled() {
        return this.enabled;
    }

    private boolean matchesNumericIdOrDisplayName(ItemStack itemStack) {
        Item item;
        String normalizedName = this.getName().toLowerCase();
        if (normalizedName.equals(String.valueOf((item = itemStack.getItem()).P()))) {
            return true;
        }
        return normalizedName.equals(item.getItemStackDisplayName(itemStack).toLowerCase());
    }

    public void loadJson(JsonObject jsonObject) {
        if (jsonObject.get("item-id") != null) {
            this.name = jsonObject.get("item-id").getAsString();
            if (this.name.startsWith("b64:")) {
                this.name = Base64Util.decodeUtf8Base64(this.name.substring(4));
            }
            this.parseMetadataSuffix();
        }
        if (jsonObject.get("total-stacks") != null) {
            this.totalStacks = jsonObject.get("total-stacks").getAsInt();
        }
        if (jsonObject.get("enabled") != null) {
            this.enabled = jsonObject.get("enabled").getAsBoolean();
        }
    }

    public boolean isMatchingEnabled() {
        return this.enabled;
    }

    public ItemLimitData withMetadata(int metadata) {
        this.metadata = metadata;
        return this;
    }

    public Item resolveItem() {
        return Item.L(this.getName());
    }

    public void setTotalStacks(int totalStacks) {
        this.totalStacks = totalStacks;
    }

    public boolean matches(ItemStack itemStack) {
        try {
            int itemMetadata;
            int slotNumber;
            if (!this.isMatchingEnabled()) {
                return false;
            }
            if (this.getName().toLowerCase().startsWith("slot")) {
                slotNumber = 0;
                try {
                    slotNumber = Integer.parseInt(this.getName().substring(4));
                }
                catch (Exception exception) {
                    // empty catch block
                }
                if (slotNumber >= 1 && Minecraft.thePlayer().V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6().v() + 1 == slotNumber) {
                    return true;
                }
            }
            if (this.matchesRegisteredItem(itemStack)) {
                return true;
            }
            if (itemStack.isNull() || itemStack.getItem().isNull()) {
                return false;
            }
            boolean matches = this.matchesNumericIdOrDisplayName(itemStack);
            if (matches && this.metadata != -1 && this.metadata != (itemMetadata = itemStack.L())) {
                matches = false;
            }
            return matches;
        }
        catch (Exception exception) {
            return false;
        }
    }

    static {
        ItemLimitData.setLegacyState(null);
        AIR = new ItemLimitData("air", 0);
        DEFAULT_BLOCK_BLACKLIST = Arrays.asList(new ItemLimitData("Dispenser"), new ItemLimitData("Note Block"), new ItemLimitData("Cobweb"), new ItemLimitData("TNT"), new ItemLimitData("Monster Spawner"), new ItemLimitData("Enchantment Table"), new ItemLimitData("Oak Fence"), new ItemLimitData("Jukebox"), new ItemLimitData("Melon"), new ItemLimitData("Command Block"), new ItemLimitData("Anvil"), new ItemLimitData("Glass Pane"), new ItemLimitData("White Stained Glass Pane"), new ItemLimitData("Iron Bars"), new ItemLimitData("Ice"), new ItemLimitData("Packed Ice"), new ItemLimitData("Anvil"), new ItemLimitData("Block of Redstone"), new ItemLimitData("Gold Ore"), new ItemLimitData("Iron Ore"), new ItemLimitData("Coal Ore"), new ItemLimitData("Lapis Lazuli Ore"), new ItemLimitData("Redstone Ore"), new ItemLimitData("Acacia Wood Stairs"), new ItemLimitData("Wooden Pressure Plate"), new ItemLimitData("Stone Pressure Plate"), new ItemLimitData("Beacon"), new ItemLimitData("Oak Sapling"), new ItemLimitData("Powered Rail"), new ItemLimitData("Detector Rail"), new ItemLimitData("Shrub"), new ItemLimitData("Dead Bush"), new ItemLimitData("Dandelion"), new ItemLimitData("Poppy"), new ItemLimitData("Mushroom"), new ItemLimitData("Ladder"), new ItemLimitData("Rail"), new ItemLimitData("Wooden Trapdoor"), new ItemLimitData("Lily Pad"), new ItemLimitData("Tripwire Hook"), new ItemLimitData("Carpet"), new ItemLimitData("Snow"), new ItemLimitData("Trapped Chest"), new ItemLimitData("Daylight Sensor"), new ItemLimitData("Hopper"), new ItemLimitData("Chest"), new ItemLimitData("Torch"), new ItemLimitData("Lever"), new ItemLimitData("Redstone Torch"), new ItemLimitData("Button"), new ItemLimitData("Cactus"));
        INTERACTIVE_BLOCKS = Arrays.asList(new ItemLimitData("Anvil"), new ItemLimitData("Barrier"), new ItemLimitData("Beacon"), new ItemLimitData("Bed"), new ItemLimitData("Brewing Stand"), new ItemLimitData("Button"), new ItemLimitData("Cake"), new ItemLimitData("Chest"), new ItemLimitData("Crafting Table"), new ItemLimitData("Trapped Chest"), new ItemLimitData("Ender Chest"), new ItemLimitData("Command Block"), new ItemLimitData("Dragon Egg"), new ItemLimitData("Daylight Detector"), new ItemLimitData("Door"), new ItemLimitData("Dispenser"), new ItemLimitData("Dropper"), new ItemLimitData("Enchanting Table"), new ItemLimitData("Fence Gate"), new ItemLimitData("Furnace"), new ItemLimitData("Hopper"), new ItemLimitData("Jukebox"), new ItemLimitData("Lever"), new ItemLimitData("Note Block"), new ItemLimitData("Sign"), new ItemLimitData("Redstone Comparator"), new ItemLimitData("Redstone Repeater"), new ItemLimitData("Trapdoor"));
    }

    private boolean matchesRegisteredItem(ItemStack itemStack) {
        return Vape.INSTANCE.getItemHelper().matchesItem(this.getName().toLowerCase(), itemStack);
    }

    public String getName() {
        return this.name;
    }

    public int getMetadata() {
        return this.metadata;
    }

    public ItemLimitData(String name, int totalStacks) {
        this.name = name;
        this.totalStacks = totalStacks;
        this.parseMetadataSuffix();
    }

    private void parseMetadataSuffix() {
        if (this.name.contains(":")) {
            String[] parts = this.name.split(":");
            this.name = parts[0];
            String metadataText = parts[1];
            if (metadataText.isEmpty()) {
                return;
            }
            try {
                this.metadata = Integer.parseInt(metadataText);
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getTotalStacks() {
        return this.totalStacks;
    }

    public ItemLimitData(int numericItemId) {
        this(String.valueOf(numericItemId));
    }

    public String toString() {
        return this.getName();
    }

    @Override
    public void toggleEnabled() {
        this.enabled = !this.enabled;
    }

    public JsonObject toJson() {
        JsonObject jsonObject = new JsonObject();
        String serializedName = this.name + (this.metadata == -1 ? "" : ":" + this.metadata);
        serializedName = "b64:" + Base64Util.encodeUtf8Base64(serializedName);
        jsonObject.addProperty("item-id", serializedName);
        jsonObject.addProperty("total-stacks", (Number)this.totalStacks);
        jsonObject.addProperty("enabled", Boolean.valueOf(this.enabled));
        return jsonObject;
    }

    public ItemLimitData(String name) {
        this(name, 1);
    }

    public void setName(String name) {
        this.name = name;
    }

    public static String getLegacyState() {
        return legacyState;
    }

    public static void setLegacyState(String state) {
        legacyState = state;
    }
}
