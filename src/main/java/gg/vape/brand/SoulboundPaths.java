package gg.vape.brand;

import java.io.File;

/** Selects the same non-destructive data location as the native launcher. */
public final class SoulboundPaths {
    private SoulboundPaths() {}

    /** New installations use .soulbound; a legacy-only installation stays put. */
    public static File clientDirectory(File executableDirectory) {
        File current = new File(executableDirectory, ".soulbound");
        File legacy = new File(executableDirectory, ".vapeclient");
        return current.isDirectory() || !legacy.isDirectory() ? current : legacy;
    }

    /**
     * The old service and texture cache used user.home when the native launcher
     * had not supplied vape.directory. Preserve that fallback independently of
     * the config store, whose historical fallback was user.dir.
     */
    public static File clientDirectoryFromNativeOrHome() {
        String base = System.getProperty("vape.directory");
        if (base == null || base.trim().isEmpty()) {
            base = System.getProperty("user.home", ".");
        }
        return clientDirectory(new File(base));
    }

    public static File serviceDataFile(File directory) {
        File current = new File(directory, "soulbound-service.json");
        File legacy = new File(directory, "vape-service.json");
        return current.isFile() || !legacy.isFile() ? current : legacy;
    }

    public static File textureCacheDirectory(File directory) {
        File current = new File(directory, "texture-cache");
        File legacy = new File(directory, "vapeTextures");
        return current.isDirectory() || !legacy.isDirectory() ? current : legacy;
    }
}
