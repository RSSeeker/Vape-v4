package gg.vape.ui.click.frame.impl.inevitable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Presentation-only grouping; original category and module IDs never change. */
public enum InevitableCategory {
    COMBAT("COMBAT", "combat"), MOVEMENT("MOVEMENT", "world"),
    VISUAL("VISUAL", "render"), UTILITY("UTILITY", "utility");

    private static final Set<String> MOVEMENT_NAMES = new HashSet<String>(Arrays.asList(
            "sprint", "omnisprint", "keepsprint", "speed", "fly", "longjump", "strafe",
            "safewalk", "parkour", "invwalk", "noslowdown", "phase", "antifall", "autoladder"));
    private static final Set<String> COMBAT_NAMES = new HashSet<String>(Arrays.asList(
            "killaura", "hitboxes", "velocity", "knockbackdelay", "hitswap", "antifireball",
            "autototem", "autoheal", "autoanchor", "backtrack", "noitemrelease"));
    private static final Set<String> VISUAL_NAMES = new HashSet<String>(Arrays.asList(
            "freecam", "xray", "bedplates", "prophunt"));
    private final String title;
    private final String icon;
    InevitableCategory(String title, String icon) { this.title = title; this.icon = icon; }
    public String title() { return this.title; }
    public String icon() { return this.icon; }

    /** Returns null only for internal non-module entries such as the GUI itself. */
    public static InevitableCategory forModule(String category, String moduleName) {
        if (category == null || "None".equalsIgnoreCase(category) || "Favorites".equalsIgnoreCase(category)) return null;
        String name = moduleName == null ? "" : moduleName.toLowerCase(Locale.ROOT).replace("-", "").replace(" ", "");
        if (MOVEMENT_NAMES.contains(name)) return MOVEMENT;
        if (COMBAT_NAMES.contains(name) || "Combat".equalsIgnoreCase(category)) return COMBAT;
        if (VISUAL_NAMES.contains(name) || "Render".equalsIgnoreCase(category)) return VISUAL;
        return UTILITY;
    }
}
