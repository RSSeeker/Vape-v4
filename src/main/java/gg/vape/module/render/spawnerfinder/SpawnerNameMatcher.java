package gg.vape.module.render.spawnerfinder;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/** Compares display names, resource keys and translated Minecraft entity names. */
public final class SpawnerNameMatcher {
    private SpawnerNameMatcher() {}

    private static String normalize(String name) {
        if (name == null) return "";
        String value = name.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("entity.")) {
            int secondDot = value.indexOf('.', "entity.".length());
            if (secondDot >= 0) value = value.substring(secondDot + 1);
        }
        int colon = value.lastIndexOf(':');
        if (colon >= 0) value = value.substring(colon + 1);
        return value.replace(' ', '_').replace('-', '_');
    }

    private static String translationKey(String name) {
        String value = name.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        if (value.startsWith("entity.")) return value;
        int colon = value.indexOf(':');
        if (colon >= 0) return "entity." + value.substring(0, colon) + "." + value.substring(colon + 1);
        return "entity.minecraft." + value;
    }

    public static boolean matches(String name, boolean listIsEmpty, List<String> enabledEntries,
                                  Function<String, String> localize) {
        if (name == null || name.trim().isEmpty()) return false;
        if (listIsEmpty) return true;
        String normalized = normalize(name);
        // Test all literal entries first: a broken localization mapping for
        // one entry must not hide another entry that already matches by name.
        for (String entry : enabledEntries) {
            if (entry != null && !entry.trim().isEmpty() && normalize(entry).equals(normalized)) return true;
        }
        for (String entry : enabledEntries) {
            if (entry == null || entry.trim().isEmpty()) continue;
            try {
                String translated = localize.apply(translationKey(entry));
                if (translated != null && normalize(translated).equals(normalized)) return true;
            } catch (RuntimeException ignored) {
                // Literal names still work when an I18n mapping is unavailable.
            }
        }
        return false;
    }
}
