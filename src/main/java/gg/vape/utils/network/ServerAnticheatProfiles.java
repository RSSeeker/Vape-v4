package gg.vape.utils.network;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gg.vape.config.LocalConfigStore;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 按服务器记住用户手动选择的反作弊引擎。
 *
 * <p>正常服务器不会像测试服那样广播反作弊告警，所以无法被动识别；这里改成
 * 进服时套用上次在同一 IP 上选择过的引擎，用户手动切换后立刻持久化到
 * {@code <.vapeclient>/anticheat-profiles.json}。</p>
 */
public final class ServerAnticheatProfiles {
    private static final String FILE_NAME = "anticheat-profiles.json";
    private static final Map<String, String> PROFILES = new HashMap<String, String>();
    private static boolean loaded = false;

    private ServerAnticheatProfiles() {
    }

    private static File profileFile() {
        return new File(LocalConfigStore.directory(), FILE_NAME);
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        File file = ServerAnticheatProfiles.profileFile();
        if (!file.isFile()) {
            return;
        }
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
            for (Map.Entry<String, com.google.gson.JsonElement> entry : root.entrySet()) {
                if (entry.getValue() == null || entry.getValue().isJsonNull()) {
                    continue;
                }
                PROFILES.put(entry.getKey(), entry.getValue().getAsString());
            }
        }
        catch (Exception ignored) {
            // 配置损坏时按空表处理，不影响启动
        }
    }

    public static String get(String serverIp) {
        if (serverIp == null || serverIp.isEmpty()) {
            return null;
        }
        ServerAnticheatProfiles.load();
        return PROFILES.get(serverIp);
    }

    public static void put(String serverIp, String profileName) {
        if (serverIp == null || serverIp.isEmpty() || profileName == null) {
            return;
        }
        ServerAnticheatProfiles.load();
        String previous = PROFILES.put(serverIp, profileName);
        if (profileName.equals(previous)) {
            return;
        }
        ServerAnticheatProfiles.save();
    }

    private static void save() {
        JsonObject root = new JsonObject();
        for (Map.Entry<String, String> entry : PROFILES.entrySet()) {
            root.addProperty(entry.getKey(), entry.getValue());
        }
        File file = ServerAnticheatProfiles.profileFile();
        File temporary = new File(file.getParentFile(), file.getName() + ".tmp");
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(temporary), StandardCharsets.UTF_8)) {
            writer.write(root.toString());
        }
        catch (Exception ignored) {
            return;
        }
        try {
            java.nio.file.Files.move(temporary.toPath(), file.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        catch (Exception ignored) {
            // 移动失败时保留旧文件，下次再写
        }
    }
}
