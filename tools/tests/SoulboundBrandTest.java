import gg.vape.brand.SoulboundBrand;
import gg.vape.brand.SoulboundPaths;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

public final class SoulboundBrandTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void restore(String key, String value) {
        if (value == null) System.clearProperty(key); else System.setProperty(key, value);
    }
    public static void main(String[] args) throws Exception {
        check(args[0].equals(SoulboundBrand.version()), "version");
        check(("Soulbound V" + args[0]).equals(SoulboundBrand.displayName()), "display");
        Path root = Files.createTempDirectory("soulbound-test");
        String oldNative = System.getProperty("vape.directory");
        String oldHome = System.getProperty("user.home");
        String oldWorking = System.getProperty("user.dir");
        try {
            File current = root.resolve(".soulbound").toFile(), legacy = root.resolve(".vapeclient").toFile();
            check(SoulboundPaths.clientDirectory(root.toFile()).equals(current), "fresh directory");
            Files.createDirectory(legacy.toPath());
            check(SoulboundPaths.clientDirectory(root.toFile()).equals(legacy), "legacy directory");
            Path config = legacy.toPath().resolve("config.json");
            Files.write(config, new byte[]{1, 2, 3});
            File oldData = new File(legacy, "vape-service.json"), newData = new File(legacy, "soulbound-service.json");
            check(SoulboundPaths.serviceDataFile(legacy).equals(newData), "fresh service");
            Files.write(oldData.toPath(), new byte[]{1});
            check(SoulboundPaths.serviceDataFile(legacy).equals(oldData), "legacy service");
            Files.write(newData.toPath(), new byte[]{2});
            check(SoulboundPaths.serviceDataFile(legacy).equals(newData), "current service wins");
            File oldCache = new File(legacy, "vapeTextures"), newCache = new File(legacy, "texture-cache");
            check(SoulboundPaths.textureCacheDirectory(legacy).equals(newCache), "fresh cache");
            Files.createDirectory(oldCache.toPath());
            check(SoulboundPaths.textureCacheDirectory(legacy).equals(oldCache), "legacy cache");
            Files.createDirectory(newCache.toPath());
            check(SoulboundPaths.textureCacheDirectory(legacy).equals(newCache), "current cache wins");
            Files.createDirectory(current.toPath());
            check(SoulboundPaths.clientDirectory(root.toFile()).equals(current), "current directory wins");
            check(Files.readAllBytes(config).length == 3, "legacy settings unchanged");

            Path home = Files.createDirectory(root.resolve("home"));
            Path working = Files.createDirectory(root.resolve("working"));
            Path homeLegacy = Files.createDirectory(home.resolve(".vapeclient"));
            Files.createDirectory(homeLegacy.resolve("vapeTextures"));
            Files.write(homeLegacy.resolve("vape-service.json"), new byte[]{42});
            System.setProperty("user.home", home.toString());
            System.setProperty("user.dir", working.toString());
            System.clearProperty("vape.directory");
            check(SoulboundPaths.clientDirectoryFromNativeOrHome().equals(homeLegacy.toFile()), "home fallback");
            check(SoulboundPaths.textureCacheDirectory(SoulboundPaths.clientDirectoryFromNativeOrHome())
                    .equals(homeLegacy.resolve("vapeTextures").toFile()), "legacy home cache");
            check(SoulboundPaths.serviceDataFile(SoulboundPaths.clientDirectoryFromNativeOrHome())
                    .equals(homeLegacy.resolve("vape-service.json").toFile()), "legacy home service");
            System.setProperty("vape.directory", "  ");
            check(SoulboundPaths.clientDirectoryFromNativeOrHome().equals(homeLegacy.toFile()), "blank property fallback");
            System.setProperty("vape.directory", working.toString());
            check(SoulboundPaths.clientDirectoryFromNativeOrHome().equals(working.resolve(".soulbound").toFile()),
                    "native directory takes precedence");
        } finally {
            restore("vape.directory", oldNative);
            restore("user.home", oldHome);
            restore("user.dir", oldWorking);
            try (Stream<Path> paths = Files.walk(root)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try { Files.delete(path); } catch (Exception ex) { throw new RuntimeException(ex); }
                });
            }
        }
        System.out.println("Soulbound brand and paths OK: " + args[0]);
    }
}
