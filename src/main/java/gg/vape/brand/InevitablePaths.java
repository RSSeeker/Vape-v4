package gg.vape.brand;

import java.io.File;

/** Selects the same non-destructive data location as the native launcher. */
public final class InevitablePaths {
    private InevitablePaths() {}

    /** Prefer the new folder, then the previous product, then the original client. */
    public static File clientDirectory(File executableDirectory) {
        File current = new File(executableDirectory, ".inevitable");
        File previous = new File(executableDirectory, ".soulbound");
        File legacy = new File(executableDirectory, ".vapeclient");
        if (current.isDirectory()) return current;
        if (previous.isDirectory()) return previous;
        return legacy.isDirectory() ? legacy : current;
    }

    /** Retain the historical home fallback for service/cache without a launcher. */
    public static File clientDirectoryFromNativeOrHome() {
        String base = System.getProperty("vape.directory");
        if (base == null || base.trim().isEmpty()) base = System.getProperty("user.home", ".");
        return clientDirectory(new File(base));
    }

    /** Reuse an existing service database without copying or deleting it. */
    public static File serviceDataFile(File directory) {
        File current = new File(directory, "inevitable-service.json");
        File previous = new File(directory, "soulbound-service.json");
        File legacy = new File(directory, "vape-service.json");
        if (current.isFile()) return current;
        if (previous.isFile()) return previous;
        return legacy.isFile() ? legacy : current;
    }

    public static File textureCacheDirectory(File directory) {
        File current = new File(directory, "texture-cache");
        File legacy = new File(directory, "vapeTextures");
        return current.isDirectory() || !legacy.isDirectory() ? current : legacy;
    }
}
