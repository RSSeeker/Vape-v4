package gg.vape.runtime;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Loads payload classes without delegating them back to Forge's routing loader.
 */
public final class ForgePayloadClassLoader extends URLClassLoader {
    /**
     * Rewritten by the build-time obfuscation step to the random payload package.
     */
    private static final String RANDOM_CLASS_PREFIX = "gg.vape.";
    private static final String RANDOM_ENTRY_PREFIX = "gg/vape/";
    private static final String LEGACY_CLASS_PREFIX = legacyPrefix('.');
    private static final String LEGACY_ENTRY_PREFIX = legacyPrefix('/');
    private static final String AUXILIARY_CLASS_PREFIX = "func.skidline.";
    private static final String AUXILIARY_ENTRY_PREFIX = "func/skidline/";
    private static final String[] PAYLOAD_CLASS_PREFIXES =
            {RANDOM_CLASS_PREFIX, LEGACY_CLASS_PREFIX};
    private static final String[] AUXILIARY_CLASS_PREFIXES = {AUXILIARY_CLASS_PREFIX};
    private static final String[] PAYLOAD_ENTRY_PREFIXES =
            {RANDOM_ENTRY_PREFIX, LEGACY_ENTRY_PREFIX};
    private static final String[] AUXILIARY_ENTRY_PREFIXES = {AUXILIARY_ENTRY_PREFIX};

    /**
     * Built at runtime so constant folding cannot resurrect the obfuscated prefix.
     */
    private static String legacyPrefix(char separator) {
        return new StringBuilder(8).append("gg").append(separator)
                .append("vape").append(separator).toString();
    }

    static {
        ClassLoader.registerAsParallelCapable();
    }

    public ForgePayloadClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (this.getClassLoadingLock(name)) {
            Class<?> loaded = this.findLoadedClass(name);
            if (loaded == null && isPayloadClass(name)) {
                // Forge routes payload packages here. Delegating a miss to the parent
                // would immediately route it back and recurse indefinitely.
                loaded = this.findClass(name);
            }
            if (loaded == null) {
                loaded = super.loadClass(name, false);
            }
            if (resolve) {
                this.resolveClass(loaded);
            }
            return loaded;
        }
    }

    /**
     * Builds the exact package routes used by SecureModuleClassLoader. The
     * completed copy can be installed atomically without replacing Forge's
     * global fallback or exposing a HashMap resize to concurrent readers.
     */
    public Map<String, ClassLoader> buildPackageRoutingMap(
            Map<String, ClassLoader> currentRoutes) throws IOException {
        Map<String, ClassLoader> routes = new HashMap<String, ClassLoader>();
        if (currentRoutes != null) {
            routes.putAll(currentRoutes);
        }

        Set<String> payloadPackages = new HashSet<String>();
        for (URL url : this.getURLs()) {
            collectPayloadPackages(url, payloadPackages);
        }
        if (payloadPackages.isEmpty()) {
            throw new IOException("payload JAR contains no product classes");
        }
        for (String packageName : payloadPackages) {
            routes.put(packageName, this);
        }
        return routes;
    }

    private static void collectPayloadPackages(URL url, Set<String> packages)
            throws IOException {
        if (!"file".equalsIgnoreCase(url.getProtocol())) {
            throw new IOException("unsupported payload URL: " + url);
        }
        File payload;
        try {
            URI uri = url.toURI();
            payload = new File(uri);
        }
        catch (URISyntaxException exception) {
            throw new IOException("invalid payload URL: " + url, exception);
        }
        if (!payload.isFile()) {
            throw new IOException("payload URL is not a JAR file: " + payload);
        }

        try (JarFile jar = new JarFile(payload)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                String entryName = entries.nextElement().getName();
                if ((!startsWithAny(entryName, PAYLOAD_ENTRY_PREFIXES)
                        && !startsWithAny(entryName, AUXILIARY_ENTRY_PREFIXES))
                        || !entryName.endsWith(".class")) {
                    continue;
                }
                int separator = entryName.lastIndexOf('/');
                if (separator > 0) {
                    packages.add(entryName.substring(0, separator).replace('/', '.'));
                }
            }
        }
    }

    private static boolean startsWithAny(String value, String[] prefixes) {
        for (String prefix : prefixes) {
            if (value.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPayloadClass(String name) {
        return startsWithAny(name, PAYLOAD_CLASS_PREFIXES)
                || startsWithAny(name, AUXILIARY_CLASS_PREFIXES);
    }
}
