package gg.vape.service;

import gg.vape.service.http.LegacyHttpServer;
import gg.vape.service.store.FileStore;
import gg.vape.service.zeus.ZeusServer;
import java.util.concurrent.CountDownLatch;

public final class VapeServiceApplication {
    private VapeServiceApplication() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && (args[0].equals("--help") || args[0].equals("-h"))) {
            printUsage(System.out);
            return;
        }

        final ServiceConfig config;
        try {
            config = ServiceConfig.fromArguments(args);
        } catch (IllegalArgumentException exception) {
            System.err.println("Error: " + exception.getMessage());
            printUsage(System.err);
            System.exit(2);
            return;
        }
        FileStore store = new FileStore(config.dataFile());
        LegacyHttpServer httpServer = new LegacyHttpServer(config.bindAddress(), config.httpPort(), store);
        ZeusServer zeusServer = new ZeusServer(config.bindAddress(), config.zeusPort(), store);

        httpServer.start();
        zeusServer.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            httpServer.close();
            zeusServer.close();
        }, "InevitableClient-service-shutdown"));

        System.out.printf("InevitableClient experimental service started: http://%s:%d, zeus://%s:%d, data=%s%n",
                config.bindAddress(), config.httpPort(), config.bindAddress(), config.zeusPort(),
                config.dataFile().toAbsolutePath());
        new CountDownLatch(1).await();
    }

    private static void printUsage(java.io.PrintStream output) {
        output.println("Usage: java -jar <service.jar> [options]");
        output.println("  --bind-address <address>  Bind address (default: 127.0.0.1)");
        output.println("  --http-port <port>        HTTP port (default: 8080)");
        output.println("  --zeus-port <port>        Zeus port (default: 8091)");
        output.println("  --data-file <path>        Data file (defaults to the legacy file under data/)");
        output.println("  --help                    Show this help");
    }
}
