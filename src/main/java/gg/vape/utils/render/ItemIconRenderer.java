package gg.vape.utils.render;

import gg.vape.Vape;
import gg.vape.utils.ItemStackScoreUtil;
import gg.vape.utils.render.GuiRenderPrimitives;
import gg.vape.utils.render.ItemIcon;
import gg.vape.utils.render.ItemIconKey;
import gg.vape.utils.render.ItemIconRenderBackend;
import gg.vape.utils.render.Post117ItemIconFramebufferRenderer;
import gg.vape.wrapper.impl.ForgeVersion;
import gg.vape.wrapper.impl.Item;
import gg.vape.wrapper.impl.ItemArmor;
import gg.vape.wrapper.impl.ItemStack;
import java.util.HashMap;

public class ItemIconRenderer {
    private static String[] legacyMarkers;
    static HashMap<ItemIconKey, ItemIconRenderBackend> cache;

    public static void precache(ItemStack itemStack) {
        Item item = itemStack.getItem();
        ItemIconRenderer.cacheItem(item.P(), ItemIconRenderer.resolveMetadata(itemStack, item), 1.0f);
    }

    public static String[] getLegacyMarkers() {
        return legacyMarkers;
    }

    public static void renderItem(int itemId, int metadata, float x, float y, int width, int height) {
        ItemIconRenderer.renderItem(itemId, metadata, x, y, width, height, 1.0f, 1.0f, false);
    }

    private static ItemIconKey createKey(int itemId, int metadata, float scale, ItemStack itemStack) {
        Item item;
        ItemIconKey cacheKey = new ItemIconKey(itemId, metadata, scale);
        if (itemStack != null && ItemStackScoreUtil.R(item = itemStack.getItem()) && ForgeVersion.MC_1_8_9.B()) {
            ItemArmor armorItem = new ItemArmor(item.getObject());
            cacheKey.setArmorColor(armorItem.Y(itemStack));
        }
        return cacheKey;
    }

    public static void renderItem(int itemId, int metadata, float x, float y, int width, int height, float opacity) {
        ItemIconRenderer.renderItem(itemId, metadata, x, y, width, height, opacity, 1.0f, false);
    }

    public static void renderItemStack(ItemStack itemStack, float x, float y, int width, int height, float opacity, float scale) {
        ItemIconRenderer.renderItemStack(itemStack, null, x, y, width, height, opacity, scale, false);
    }

    public static void renderItem(int itemId, int metadata, float x, float y, int width, int height, float opacity, boolean worldSpace) {
        ItemIconRenderer.renderItem(itemId, metadata, x, y, width, height, opacity, 1.0f, worldSpace);
    }

    public static void renderItemStack(ItemStack itemStack, float x, float y, int width, int height, float opacity, boolean worldSpace) {
        ItemIconRenderer.renderItemStack(itemStack, null, x, y, width, height, opacity, 1.0f, worldSpace);
    }


    public static void renderItemStack(ItemStack itemStack, float x, float y, int width, int height) {
        ItemIconRenderer.renderItemStack(itemStack, null, x, y, width, height, 1.0f, 1.0f, false);
    }

    public static void renderItem(int itemId, int metadata, float x, float y, int width, int height, float opacity, float scale, boolean worldSpace) {
        ItemStack itemStack = ItemStack.S(Item.T(itemId));
        itemStack.s(metadata);
        ItemIconRenderer.renderCached(itemId, metadata, itemStack, x, y, width, height, opacity, scale, worldSpace);
    }

    public static void clear() {
        for (ItemIconRenderBackend renderer : cache.values()) {
            renderer.dispose();
        }
        cache.clear();
    }

    private static ItemIconRenderBackend createRenderer(ItemStack itemStack, ItemIconKey cacheKey) {
        ItemIconRenderBackend renderer = GuiRenderPrimitives.d() ? new Post117ItemIconFramebufferRenderer() : new ItemIcon();
        // Always cache the renderer even when capture fails/throws. Otherwise a broken
        // capture (the 1.20.1 getBakedModel mapping NPE) re-runs the expensive offscreen
        // capture EVERY frame and logs an exception every frame, which is the per-frame lag.
        // renderQueued() null-guards the framebuffer so a failed capture simply draws nothing.
        try {
            renderer.capture(itemStack, cacheKey.getScale());
        }
        catch (Throwable throwable) {
            Vape.logThrowable(throwable);
        }
        cache.put(cacheKey, renderer);
        return renderer;
    }

    public static void renderItemStack(ItemStack itemStack, float x, float y, int width, int height, float opacity) {
        ItemIconRenderer.renderItemStack(itemStack, null, x, y, width, height, opacity, 1.0f, false);
    }

    private static int resolveMetadata(ItemStack itemStack, Item item) {
        if (ItemStackScoreUtil.I(item) || ItemStackScoreUtil.R(item)) {
            return 0;
        }
        return itemStack.L();
    }

    public static void setLegacyMarkers(String[] legacyMarkers) {
        ItemIconRenderer.legacyMarkers = legacyMarkers;
    }

    public static ItemIconRenderBackend createIfAbsent(ItemIconKey cacheKey, ItemStack itemStack) {
        if (!cache.containsKey(cacheKey)) {
            return ItemIconRenderer.createRenderer(itemStack, cacheKey);
        }
        return null;
    }

    public static void renderItemStack(ItemStack itemStack, Item item, float x, float y, int width, int height, float opacity, float scale, boolean worldSpace) {
        if (itemStack == null) {
            return;
        }
        if (item == null) {
            item = itemStack.getItem();
        }
        int itemId = item.P();
        int metadata = ItemIconRenderer.resolveMetadata(itemStack, item);
        ItemIconRenderer.renderCached(itemId, metadata, itemStack, x, y, width, height, opacity, scale, worldSpace);
    }

    public static void precache(int itemId, int metadata) {
        ItemIconRenderer.cacheItem(itemId, metadata, 1.0f);
    }

    public static ItemIconRenderBackend cacheItem(int itemId, int metadata, float scale) {
        ItemIconKey cacheKey = new ItemIconKey(itemId, metadata, scale);
        if (!cache.containsKey(cacheKey)) {
            Item item = Item.T(itemId);
            if (item.isNull()) {
                return null;
            }
            ItemStack itemStack = ItemStack.S(item);
            itemStack.s(metadata);
            return ItemIconRenderer.createRenderer(itemStack, cacheKey);
        }
        return null;
    }

    public static void renderItem(int itemId, int metadata, float x, float y, int width, int height, boolean worldSpace) {
        ItemIconRenderer.renderItem(itemId, metadata, x, y, width, height, 1.0f, 1.0f, worldSpace);
    }

    private static void renderCached(int itemId, int metadata, ItemStack itemStack, float x, float y, int width, int height, float opacity, float scale, boolean worldSpace) {
        ItemIconKey cacheKey = ItemIconRenderer.createKey(itemId, metadata, scale, itemStack);
        ItemIconRenderBackend renderer = cache.get(cacheKey);
        if (renderer != null) {
            renderer.renderQueued(x, y, width, height, opacity, worldSpace);
            return;
        }
        ItemIconRenderBackend createdRenderer = ItemIconRenderer.createIfAbsent(cacheKey, itemStack);
        if (createdRenderer != null) {
            createdRenderer.renderQueued(x, y, width, height, opacity, worldSpace);
        }
    }

    static {
        cache = new HashMap();
        ItemIconRenderer.setLegacyMarkers(new String[5]);
    }
}

