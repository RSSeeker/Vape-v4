import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Properties;

/** Checks English UI resources and the matching Chinese translations. */
public final class LocalizationCheck {
    private LocalizationCheck() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("usage: LocalizationCheck <english.properties> <chinese.properties>");
        }
        Properties english = load(args[0]);
        Properties chinese = load(args[1]);
        require("English".equals(english.getProperty("language.native")), "English language name");

        // IdentityFontOption shows English strings as-is, so they must not
        // accidentally contain Chinese text.
        for (String key : english.stringPropertyNames()) {
            String value = english.getProperty(key);
            require(value.codePoints().noneMatch(cp -> Character.UnicodeScript.of(cp)
                    == Character.UnicodeScript.HAN), "Chinese text in English property: " + key);
        }

        checkPair(english, chinese, "cache_data", "Cache data");
        checkPair(english, chinese, "cache_data.tooltip",
                "Caches data locally to improve load time (.vapeclient)");
        checkPair(english, chinese, "spawner", "Spawner");
        checkPair(english, chinese, "storageesp.render_barrel", "Render Barrel");
        checkPair(english, chinese, "storageesp.barrel_color", "Barrel Color");
        checkPair(english, chinese, "storageesp.range", "Range");
        checkPair(english, chinese, "storageesp.cluster_minimum", "Cluster minimum");
        checkPair(english, chinese, "storageesp.cluster_radius", "Cluster radius");
        checkPair(english, chinese, "storageesp.show_coordinates", "Show coordinates");
        checkPair(english, chinese, "storageesp.show_coordinates.tooltip", "Show the block coordinates of visible storage");
        checkPair(english, chinese, "storageesp.range.tooltip", "Maximum distance to loaded storage");
        checkPair(english, chinese, "storageesp.cluster_minimum.tooltip", "Minimum enabled storage blocks nearby, including this one");
        checkPair(english, chinese, "storageesp.cluster_radius.tooltip", "Three-dimensional distance used for the cluster minimum");
        checkPair(english, chinese, "spawnerfinder.range", "Range");
        checkPair(english, chinese, "spawnerfinder.range.tooltip", "Maximum distance to loaded spawners");
        checkPair(english, chinese, "spawnerfinder.show_coordinates", "Show coordinates");
        checkPair(english, chinese, "spawnerfinder.show_coordinates.tooltip", "Show the spawner's block coordinates");
        checkPair(english, chinese, "spawnerfinder.show_unknown", "Show unknown");
        checkPair(english, chinese, "spawnerfinder.show_unknown.tooltip", "Show spawners without a known mob name even when a whitelist is set");
        checkPair(english, chinese, "reconnecting", "Reconnecting...");
        checkPair(english, chinese, "reconnecting_in_seconds", "Reconnecting in %d seconds...");
        for (Properties language : new Properties[]{english, chinese}) {
            String formatted = String.format(Locale.ROOT, language.getProperty("reconnecting_in_seconds"), 12);
            require(formatted.contains("12"), "reconnect countdown placeholder");
        }
        System.out.println("LOCALIZATION OK: English values and dynamic Chinese translations verified");
    }

    private static Properties load(String path) throws Exception {
        Properties values = new Properties();
        try (Reader reader = Files.newBufferedReader(Paths.get(path), StandardCharsets.UTF_8)) {
            values.load(reader);
        }
        return values;
    }

    private static void checkPair(Properties english, Properties chinese, String key, String expected) {
        require(expected.equals(english.getProperty(key)), "English property " + key);
        String translated = chinese.getProperty(key);
        require(translated != null && !translated.trim().isEmpty(), "Chinese property " + key);
    }

    private static void require(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }
}
