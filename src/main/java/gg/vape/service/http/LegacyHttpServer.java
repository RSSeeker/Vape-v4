package gg.vape.service.http;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import gg.vape.service.store.AccountRecord;
import gg.vape.service.store.AuthChallengeRecord;
import gg.vape.service.store.FileStore;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;

public final class LegacyHttpServer implements AutoCloseable {
    private final Gson gson = new GsonBuilder().serializeNulls().create();
    private final FileStore store;
    private final HttpServer server;

    public LegacyHttpServer(String bindAddress, int port, FileStore store) throws IOException {
        this.store = store;
        this.server = HttpServer.create(new InetSocketAddress(bindAddress, port), 64);
        this.server.createContext("/", this::handle);
        this.server.setExecutor(Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "InevitableClient-http");
            thread.setDaemon(true);
            return thread;
        }));
    }

    public void start() {
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(1);
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            if ("/health".equals(path)) {
                sendJson(exchange, 200, object("status", "UP", "time", Instant.now().toString()));
                return;
            }
            if ("/loader/login".equals(path)) {
                handleLoaderLogin(exchange);
                return;
            }
            if ("/api/v1/app-auth/generate".equals(path)) {
                handleGenerate(exchange);
                return;
            }
            if ("/api/v1/app-auth/status".equals(path)) {
                handleAuthStatus(exchange);
                return;
            }
            if (path.startsWith("/app-auth/proceed/")) {
                handleProceed(exchange, path.substring("/app-auth/proceed/".length()));
                return;
            }
            if (path.startsWith("/admin/")) {
                handleAdmin(exchange, path);
                return;
            }
            if (path.startsWith("/api/v1/")) {
                handleLegacyApi(exchange, path.substring("/api/v1/".length()));
                return;
            }
            sendError(exchange, 404, "Unknown endpoint");
        } catch (IllegalArgumentException exception) {
            sendEnvelope(exchange, false, JsonNull.INSTANCE, exception.getMessage());
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            sendError(exchange, 500, "Internal service error");
        } finally {
            exchange.close();
        }
    }

    private void handleGenerate(HttpExchange exchange) throws IOException {
        requireMethod(exchange, "POST");
        Map<String, String> form = parseForm(readBody(exchange));
        AuthChallengeRecord challenge = store.createChallenge(
                form.getOrDefault("edition", "v4"), form.getOrDefault("hwid", "unknown"));
        sendText(exchange, 200, "text/plain; charset=utf-8", challenge.challenge);
    }

    private void handleLoaderLogin(HttpExchange exchange) throws IOException {
        requireMethod(exchange, "POST");
        JsonObject request = readJsonObject(exchange);
        String username = request.has("username") ? request.get("username").getAsString() : "";
        FileStore.LoaderLoginResult login = store.loginByUsername(username);
        JsonObject response = new JsonObject();
        response.addProperty("successful", true);
        response.addProperty("token", login.token());
        response.addProperty("username", login.account().username);
        sendJson(exchange, 200, response);
    }

    private void handleProceed(HttpExchange exchange, String challenge) throws IOException {
        requireMethod(exchange, "GET");
        if (!store.approveChallenge(challenge)) {
            sendText(exchange, 404, "text/html; charset=utf-8",
                    "<!doctype html><title>InevitableClient Auth</title><h1>Invalid or expired challenge</h1>");
            return;
        }
        sendText(exchange, 200, "text/html; charset=utf-8",
                "<!doctype html><title>InevitableClient Auth</title><h1>Authentication complete</h1>");
    }

    private void handleAuthStatus(HttpExchange exchange) throws IOException {
        requireMethod(exchange, "POST");
        String challengeValue = parseForm(readBody(exchange)).get("token");
        Optional<AuthChallengeRecord> result = store.challenge(challengeValue);
        JsonObject response = new JsonObject();
        if (!result.isPresent() || result.get().expiresAt < System.currentTimeMillis()) {
            response.addProperty("status", "timed out");
        } else if (!result.get().approved) {
            response.addProperty("status", "pending");
        } else {
            response.addProperty("status", "success");
            response.addProperty("token", result.get().accessToken);
        }
        sendJson(exchange, 200, response);
    }

    private void handleLegacyApi(HttpExchange exchange, String relativePath) throws IOException {
        List<String> parts = new ArrayList<>(Arrays.asList(relativePath.split("/")));
        parts.removeIf(String::isEmpty);
        if (parts.size() < 2) {
            sendError(exchange, 404, "Missing token or operation");
            return;
        }
        String token = parts.remove(0);
        AccountRecord account = store.account(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid access token"));
        String operation = String.join("/", parts);

        if ("authenticated".equals(operation)) {
            requireMethod(exchange, "GET");
            sendEnvelope(exchange, true, account.accountJson(), null);
        } else if (operation.startsWith("register/")) {
            requireMethod(exchange, "GET");
            store.register(token, decode(operation.substring("register/".length())));
            sendEnvelope(exchange, true, gson.toJsonTree(true), null);
        } else if ("settings/load/global".equals(operation)) {
            requireMethod(exchange, "GET");
            sendEnvelope(exchange, true, store.globalSettings(token), null);
        } else if ("settings/save/global".equals(operation)) {
            requireMethod(exchange, "POST");
            JsonObject settings = readJsonObject(exchange);
            store.saveGlobalSettings(token, settings);
            sendJson(exchange, 200, settings);
        } else if ("settings/load/online".equals(operation)) {
            requireMethod(exchange, "GET");
            sendEnvelope(exchange, true, store.onlineSettings(token), null);
        } else if ("settings/save/online".equals(operation)) {
            requireMethod(exchange, "POST");
            JsonObject settings = readJsonObject(exchange);
            store.saveOnlineSettings(token, settings);
            sendJson(exchange, 200, settings);
        } else if ("profile/private/all".equals(operation)) {
            requireMethod(exchange, "GET");
            sendEnvelope(exchange, true, store.privateData(token), null);
        } else if ("profile/private/save/user".equals(operation)) {
            requireMethod(exchange, "POST");
            store.savePrivateUserData(token, readJson(exchange));
            sendEnvelope(exchange, true, gson.toJsonTree(true), null);
        } else if ("profile/private/save/profile".equals(operation)) {
            requireMethod(exchange, "POST");
            sendEnvelope(exchange, true, store.savePrivateProfiles(token, readJsonObject(exchange)), null);
        } else if ("profile/private/reserve".equals(operation)) {
            requireMethod(exchange, "POST");
            sendEnvelope(exchange, true, gson.toJsonTree(store.reserveProfileId(token)), null);
        } else if ("profile/public/tags".equals(operation)) {
            requireMethod(exchange, "GET");
            sendEnvelope(exchange, true, new JsonArray(), null);
        } else if ("profile/public/list".equals(operation)) {
            requireMethod(exchange, "POST");
            sendEnvelope(exchange, true, store.listPublicProfiles(token, readJsonObject(exchange)), null);
        } else if ("profile/public/create".equals(operation) || "profile/public/edit".equals(operation)) {
            requireMethod(exchange, "POST");
            sendEnvelope(exchange, true, store.savePublicProfile(token, readJsonObject(exchange), operation.endsWith("/edit")), null);
        } else if ("profile/public/regenerate/sharecode".equals(operation)) {
            requireMethod(exchange, "POST");
            String id = readJsonObject(exchange).get("profileId").getAsString();
            sendEnvelope(exchange, true, store.regeneratePublicShareCode(token, id), null);
        } else if (operation.startsWith("profile/public/") && operation.endsWith("/delete")) {
            requireMethod(exchange, "DELETE");
            String id = operation.substring("profile/public/".length(), operation.length() - "/delete".length());
            boolean deleted = store.deletePublicProfile(token, id);
            sendEnvelope(exchange, deleted, deleted ? gson.toJsonTree(true) : JsonNull.INSTANCE,
                    deleted ? null : "Profile not found or not owned by this account");
        } else if (operation.startsWith("profile/public/")
                && (operation.endsWith("/view") || operation.endsWith("/download") || operation.endsWith("/update"))) {
            requireMethod(exchange, "GET");
            String suffix = operation.endsWith("/view") ? "/view"
                    : operation.endsWith("/download") ? "/download" : "/update";
            String id = operation.substring("profile/public/".length(), operation.length() - suffix.length());
            Optional<JsonObject> result = suffix.equals("/view") ? store.publicProfile(token, id)
                    : store.downloadPublicProfile(token, id, suffix.equals("/update"));
            JsonElement profile = result.<JsonElement>map(value -> value).orElse(JsonNull.INSTANCE);
            sendEnvelope(exchange, !profile.isJsonNull(), profile,
                    profile.isJsonNull() ? "Profile not found or unavailable" : null);
        } else if (operation.startsWith("profile/public/review/view/")) {
            requireMethod(exchange, "GET");
            sendEnvelope(exchange, true, emptyPage(), null);
        } else if (operation.contains("/review/") || operation.contains("/reports/")) {
            sendEnvelope(exchange, false, JsonNull.INSTANCE, "Reviews and reports are not implemented by the experimental service");
        } else {
            sendEnvelope(exchange, false, JsonNull.INSTANCE, "Unsupported operation: " + operation);
        }
    }

    private JsonObject emptyPage() {
        JsonObject page = new JsonObject();
        page.add("content", new JsonArray());
        page.addProperty("last", true);
        page.addProperty("totalPages", 0);
        page.addProperty("totalElements", 0);
        page.addProperty("size", 20);
        page.addProperty("numberOfElements", 0);
        return page;
    }

    private void handleAdmin(HttpExchange exchange, String path) throws IOException {
        requireMethod(exchange, "GET");
        if ("/admin/health".equals(path)) {
            sendJson(exchange, 200, object("status", "UP"));
        } else if ("/admin/public-profiles".equals(path)) {
            sendJson(exchange, 200, store.publicProfiles());
        } else {
            sendError(exchange, 404, "Unknown admin endpoint");
        }
    }

    private void sendEnvelope(HttpExchange exchange, boolean successful, JsonElement data, String error)
            throws IOException {
        JsonObject response = new JsonObject();
        response.addProperty("successful", successful);
        response.add("data", data == null ? JsonNull.INSTANCE : data);
        if (error == null) {
            response.add("error", JsonNull.INSTANCE);
        } else {
            response.addProperty("error", error);
        }
        sendJson(exchange, 200, response);
    }

    private static void requireMethod(HttpExchange exchange, String expected) {
        if (!expected.equalsIgnoreCase(exchange.getRequestMethod())) {
            throw new IllegalArgumentException("Expected " + expected + " request");
        }
    }

    private JsonElement readJson(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        return body.trim().isEmpty() ? JsonNull.INSTANCE
                : new JsonParser().parse(body);
    }

    private JsonObject readJsonObject(HttpExchange exchange) throws IOException {
        JsonElement body = readJson(exchange);
        if (!body.isJsonObject()) {
            throw new IllegalArgumentException("Expected a JSON object");
        }
        return body.getAsJsonObject();
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        java.io.InputStream input = exchange.getRequestBody();
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    private static Map<String, String> parseForm(String form) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String field : form.split("&")) {
            int separator = field.indexOf('=');
            if (separator >= 0) {
                values.put(decode(field.substring(0, separator)), decode(field.substring(separator + 1)));
            }
        }
        return values;
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (java.io.UnsupportedEncodingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void sendJson(HttpExchange exchange, int status, JsonElement json) throws IOException {
        sendText(exchange, status, "application/json; charset=utf-8", gson.toJson(json));
    }

    private void sendError(HttpExchange exchange, int status, String message) throws IOException {
        sendJson(exchange, status, object("error", message));
    }

    private static void sendText(HttpExchange exchange, int status, String contentType, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private JsonObject object(String... entries) {
        JsonObject result = new JsonObject();
        for (int index = 0; index < entries.length; index += 2) {
            result.addProperty(entries[index], entries[index + 1]);
        }
        return result;
    }
}
