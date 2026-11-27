package gg.vape.service;

import gg.vape.brand.SoulboundPaths;
import gg.vape.service.http.LegacyHttpServer;
import gg.vape.service.store.FileStore;
import gg.vape.service.zeus.ZeusServer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Embedded launcher that starts the Vape experimental service (HTTP + Zeus)
 * inside the injected Minecraft process. Never throws into the game: any
 * failure is logged and the service is simply not available.
 */
public final class VapeServiceLauncher {
    private static final int DEFAULT_HTTP_PORT = 8080;
    private static final int DEFAULT_ZEUS_PORT = 8091;
    private static final int PORT_PROBE_LIMIT = 20;

    private static volatile LegacyHttpServer httpServer;
    private static volatile ZeusServer zeusServer;
    private static volatile boolean started;

    private VapeServiceLauncher() {
    }

    public static synchronized void start() {
        if (started) {
            return;
        }
        started = true;
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                startInternal();
            }
        }, "Soulbound-service");
        thread.setDaemon(true);
        thread.start();
    }

    public static boolean isRunning() {
        return httpServer != null || zeusServer != null;
    }

    public static synchronized void stop() {
        LegacyHttpServer http = httpServer;
        ZeusServer zeus = zeusServer;
        httpServer = null;
        zeusServer = null;
        if (http != null) {
            try {
                http.close();
            } catch (Throwable ignored) {
                // best-effort shutdown
            }
        }
        if (zeus != null) {
            try {
                zeus.close();
            } catch (Throwable ignored) {
                // best-effort shutdown
            }
        }
    }

    private static void startInternal() {
        try {
            Path dataFile = dataFile();
            FileStore store = new FileStore(dataFile);

            String bindAddress = env("VAPE_BIND_ADDRESS", "127.0.0.1");
            int httpPort = findFreePort(bindAddress, port("VAPE_HTTP_PORT", DEFAULT_HTTP_PORT));
            int zeusPort = findFreePort(bindAddress, port("VAPE_ZEUS_PORT", DEFAULT_ZEUS_PORT));

            LegacyHttpServer http = new LegacyHttpServer(bindAddress, httpPort, store);
            ZeusServer zeus = new ZeusServer(bindAddress, zeusPort, store);

            http.start();
            try {
                zeus.start();
            } catch (Throwable failure) {
                try {
                    http.close();
                } catch (Throwable ignored) {
                    // best-effort rollback
                }
                throw failure;
            }

            httpServer = http;
            zeusServer = zeus;
            System.out.println("[Soulbound] experimental service started: http://" + bindAddress + ":" + http.port()
                    + ", zeus://" + bindAddress + ":" + zeus.port() + ", data=" + dataFile.toAbsolutePath());
        } catch (Throwable failure) {
            httpServer = null;
            zeusServer = null;
            System.out.println("[Soulbound] experimental service unavailable: " + failure.getMessage());
        }
    }

    private static Path dataFile() {
        String configured = env("VAPE_DATA_FILE", null);
        if (configured != null && !configured.trim().isEmpty()) {
            return Paths.get(configured);
        }
        return SoulboundPaths.serviceDataFile(SoulboundPaths.clientDirectoryFromNativeOrHome()).toPath();
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static int port(String name, int fallback) {
        String value = System.getenv(name);
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed >= 1 && parsed <= 65535) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
            // fall through to default
        }
        System.out.println("[Soulbound] invalid " + name + "=" + value + ", using default " + fallback);
        return fallback;
    }

    private static int findFreePort(String bindAddress, int preferred) {
        for (int offset = 0; offset < PORT_PROBE_LIMIT; offset++) {
            int candidate = preferred + offset;
            if (candidate > 65535) {
                break;
            }
            if (canBind(bindAddress, candidate)) {
                return candidate;
            }
        }
        // Fall back to the preferred port; the server startup will surface the error.
        return preferred;
    }

    private static boolean canBind(String bindAddress, int port) {
        try (java.net.ServerSocket socket = new java.net.ServerSocket()) {
            socket.setReuseAddress(false);
            socket.bind(new java.net.InetSocketAddress(bindAddress, port));
            return true;
        } catch (IOException exception) {
            return false;
        }
    }
}
