package gg.vape.module;

import gg.vape.ui.click.component.GuiComponent;
import gg.vape.unmap.INamed;
import java.util.ArrayList;
import java.util.List;

public class Category
implements INamed {
    public static Category COMBAT;
    public static Category RENDER;
    public static Category NONE;
    private final String name;
    public static Category WORLD;
    private static List<Category> values;
    private final int color;
    private final String description;
    public static Category INVENTORY;
    public static Category HIDDEN;
    private static GuiComponent[] components;
    public static Category UTILITY;
    private final String iconKey;
    public static Category OTHER;
    public static Category EXTENSION;
    public static Category FAVORITES;
    public static Category NETWORK;
    public static Category MINIGAMES;

    public static GuiComponent[] getLegacyComponents() {
        return components;
    }

    public int getColor() {
        return this.color;
    }

    private Category(String name, String iconKey, String description, int color) {
        this.name = name;
        this.iconKey = iconKey;
        this.description = description;
        this.color = color;
    }

    static {
        if (Category.getLegacyComponents() == null) {
            Category.setLegacyComponents(new GuiComponent[4]);
        }
        NONE = new Category("None", "newfavorites", -2914273);
        COMBAT = new Category("Combat", "combat", "Combat advantage modules", -2745594);
        UTILITY = new Category("Utility", "utility", -14970556);
        RENDER = new Category("Render", "render", "All kinds of visual goodies", -7926107);
        WORLD = new Category("World", "world", -12170992);
        INVENTORY = new Category("Inventory", "inventory", -12170992);
        NETWORK = new Category("Network", "network", -14651232);
        OTHER = new Category("Other", "other", -2419323);
        MINIGAMES = new Category("Minigames", "minigames", "BedBreaker, BedPlates, MurdererFinder, PropHunt", -13421773);
        // 第三方脚本模块的归属分类：模块由 .vapeclient\scripts 下的脚本在运行时注册，
        // 和内置模块共用同一套开关 / 事件 / 配置逻辑。图标用拼图块，表示"可扩展"。
        EXTENSION = new Category("Extension", "extension", "Third-party script modules", -11363066);
        FAVORITES = new Category("Favorites", "newfavorites", "", 0);
        HIDDEN = new Category("Hidden", "favorites", -2419323);
        values = new ArrayList<Category>();
        values.add(FAVORITES);
        values.add(COMBAT);
        values.add(RENDER);
        values.add(UTILITY);
        values.add(WORLD);
        values.add(INVENTORY);
        values.add(MINIGAMES);
        values.add(OTHER);
        values.add(EXTENSION);
        values.add(NONE);
    }

    private Category(String name, String iconKey, int color) {
        this(name, iconKey, "", color);
    }

    public String toString() {
        return this.name;
    }

    public static void setLegacyComponents(GuiComponent[] legacyComponents) {
        components = legacyComponents;
    }

    @Override
    public String getName() {
        return this.name;
    }

    public static List<Category> values() {
        return values;
    }

    public static void destruct() {
        values.clear();
        values = null;
        NONE = null;
        COMBAT = null;
        UTILITY = null;
        RENDER = null;
        OTHER = null;
        EXTENSION = null;
        WORLD = null;
        FAVORITES = null;
        MINIGAMES = null;
    }

    public String getDescription() {
        return this.description;
    }

    public String getIconKey() {
        return this.iconKey;
    }
}

