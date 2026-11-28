package gg.vape.brand;

import java.io.InputStream;
import java.util.Properties;

/** Visible product identity; protocol and package identifiers remain compatible. */
public final class InevitableBrand {
    private static final String VERSION = loadVersion();
    private InevitableBrand() {}

    private static String loadVersion() {
        try (InputStream stream = InevitableBrand.class.getResourceAsStream("/META-INF/inevitable-version.properties")) {
            if (stream != null) {
                Properties properties = new Properties();
                properties.load(stream);
                String version = properties.getProperty("version", "").trim();
                if (version.matches("[1-9][0-9]*(\\.[0-9]+)*")) return version;
            }
        } catch (Exception ignored) {
            // A damaged version resource should not prevent the client starting.
        }
        return "1";
    }

    public static String version() { return VERSION; }
    public static String displayName() { return "InevitableClientV" + VERSION; }
    public static String wordmark() { return "InevitableV" + VERSION; }
}
