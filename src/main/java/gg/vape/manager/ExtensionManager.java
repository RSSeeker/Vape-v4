package gg.vape.manager;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gg.vape.Vape;
import gg.vape.config.LocalConfigStore;
import gg.vape.module.MinecraftVersionComparisonOperator;
import gg.vape.module.MinecraftVersionConstraint;
import gg.vape.module.extension.ReloadScriptsModule;
import gg.vape.module.extension.ScriptModule;
import gg.vape.script.ScriptTranslation;
import gg.vape.wrapper.impl.ForgeVersion;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * 第三方脚本模块的加载器。
 *
 * <p>扫描 {@code .vapeclient\scripts\*.json}，每个 JSON 描述一个脚本模块，字段：
 * {@code name}（显示名，缺省用文件名；可写成 {@code {"en": "...", "zh": "..."}} 实现中英双语，
 * 内部名/配置 key 始终用英文）、{@code id}（稳定标识，缺省用文件名）、
 * {@code description}（悬停说明，同样支持对象形式）、{@code main}（脚本入口，相对 JSON 所在目录，
 * 缺省为「JSON 同名 .js」）。目录不存在时会生成一个 {@code example.json} 模板，
 * 方便先看到效果。</p>
 *
 * <p>每个脚本都会被动态生成一个 {@code ScriptModule} 子类再注册：{@code ModManager}
 * 以 {@code Class} 为 key 保存模块，共用同一个类会让多个脚本互相覆盖。生成的类名
 * 与父类名都在运行时从 {@link ScriptModule} 反射得到，因此即使产物做了混淆也不会失效。</p>
 *
 * <p>注册发生在 {@code ModManager.init()} 之后、配置载入之前，所以脚本模块的开关状态
 * 会像内置模块一样被保存和恢复。运行时由 {@link gg.vape.script.ScriptRuntimeHost} 提供；
 * 改完脚本后点扩展分类里的「重新加载脚本」即可重新编译，不需要重启游戏。</p>
 */
public final class ExtensionManager {
    private static final String SCRIPTS_DIRECTORY = "scripts";
    private static final String EXAMPLE_FILE_NAME = "example.json";
    private static final String EXAMPLE_SCRIPT_NAME = "example.js";
    private static final AtomicInteger GENERATED_CLASS_COUNTER = new AtomicInteger();
    private static final List<ScriptModule> LOADED_MODULES = new ArrayList<ScriptModule>();
    /** json 文件规范路径 → 已注册模块，用于「重新加载脚本」时区分新增/删除。 */
    private static final Map<String, ScriptModule> MODULES_BY_JSON = new HashMap<String, ScriptModule>();
    private static boolean reloadModuleRegistered;

    private ExtensionManager() {
    }

    /**
     * 读取脚本目录下所有 JSON 并注册为 {@code Extension} 分类的模块。
     *
     * @return 成功注册的模块数量
     */
    public static int loadFromDisk(ModManager modManager) {
        File directory = scriptsDirectory();
        log("scanning " + directory.getAbsolutePath());
        Set<String> usedNames = new HashSet<String>();
        int loaded = 0;
        File[] files = directory.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".json"));
        if (files == null || files.length == 0) {
            log("no script json files found");
        }
        else {
            log("found " + files.length + " script json file(s)");
            Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
            for (File file : files) {
                try {
                    ScriptModule module = registerScriptModule(modManager, file, usedNames);
                    if (module == null) {
                        continue;
                    }
                    MODULES_BY_JSON.put(canonicalPath(file), module);
                    ++loaded;
                }
                catch (Throwable throwable) {
                    log("failed to load " + file.getName() + ": " + throwable);
                    Vape.logError("[Extension] failed to load " + file.getName() + ": " + throwable);
                }
            }
        }
        registerReloadModule(modManager, usedNames);
        gg.vape.script.ScriptTranslation.flush();
        log("loaded " + loaded + " script module(s)");
        return loaded;
    }

    /**
     * 重新扫描脚本目录并重载：已注册脚本重新编译，新增的 JSON 立即注册，删掉的 JSON 把对应模块关掉。
     *
     * <p>删除的脚本在本次进程里仍会留在 GUI 列表（没有反向注册 API），但模块会被禁用、配置也不再
     * 继续保存；下次启动就不会再出现。</p>
     *
     * @return 重新编译 + 新注册的脚本数量
     */
    public static int reloadAll() {
        ModManager modManager = Vape.INSTANCE == null ? null : Vape.INSTANCE.getModManager();
        if (modManager == null) {
            log("reload skipped: module manager is not ready");
            return 0;
        }
        File directory = scriptsDirectory();
        File[] files = directory.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".json"));
        Set<String> seen = new HashSet<String>();
        Set<String> usedNames = new HashSet<String>();
        for (ScriptModule module : LOADED_MODULES) {
            usedNames.add(module.getName().toLowerCase(Locale.ROOT));
        }
        int reloaded = 0;
        int added = 0;
        int failed = 0;
        if (files != null) {
            Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
            for (File file : files) {
                String key = canonicalPath(file);
                seen.add(key);
                ScriptModule existing = MODULES_BY_JSON.get(key);
                if (existing != null) {
                    try {
                        if (existing.reloadScript()) {
                            ++reloaded;
                            log("recompiled '" + existing.getName() + "' from " + file.getName());
                        }
                        else {
                            ++failed;
                            log("reload failed for '" + existing.getName() + "' (" + file.getName() + ")");
                        }
                    }
                    catch (Throwable throwable) {
                        ++failed;
                        log("reload failed for '" + existing.getName() + "': " + throwable);
                        Vape.logError("[Extension] reload failed for " + existing.getName() + ": " + throwable);
                    }
                    continue;
                }
                try {
                    ScriptModule module = registerScriptModule(modManager, file, usedNames);
                    if (module == null) {
                        ++failed;
                        continue;
                    }
                    MODULES_BY_JSON.put(key, module);
                    ++added;
                    log("registered new script '" + module.getName() + "' from " + file.getName());
                }
                catch (Throwable throwable) {
                    ++failed;
                    log("failed to load new script " + file.getName() + ": " + throwable);
                    Vape.logError("[Extension] failed to load new script " + file.getName() + ": " + throwable);
                }
            }
        }
        int removed = disableVanishedModules(seen);
        // Recompiles can add or remove settings even without changing the module list.
        refreshGuiLayout();
        gg.vape.script.ScriptTranslation.flush();
        log("reload summary: " + reloaded + " recompiled, " + added + " added, " + failed
                + " failed, " + removed + " disabled (json removed)");
        return reloaded + added;
    }

    /**
     * 把 JSON 已经被删掉的脚本模块禁用；没有反向注册 API，所以只能做到「不再运行」。
     */
    private static int disableVanishedModules(Set<String> seen) {
        int disabled = 0;
        for (Map.Entry<String, ScriptModule> entry
                : new ArrayList<Map.Entry<String, ScriptModule>>(MODULES_BY_JSON.entrySet())) {
            if (seen.contains(entry.getKey())) {
                continue;
            }
            ScriptModule module = entry.getValue();
            try {
                if (module.isEnabled()) {
                    module.setEnabled(false);
                }
            }
            catch (Throwable throwable) {
                log("failed to disable removed script '" + module.getName() + "': " + throwable);
            }
            ++disabled;
            log("script json was removed: '" + module.getName() + "' disabled; it disappears after restart");
        }
        return disabled;
    }

    /** Removed script modules remain registered for compatibility until restart,
     * but should no longer be shown by a refreshed module list. */
    public static boolean isScriptPresent(ScriptModule module) {
        for (Map.Entry<String, ScriptModule> entry : MODULES_BY_JSON.entrySet()) {
            if (entry.getValue() == module) return new File(entry.getKey()).isFile();
        }
        return true;
    }

    private static void refreshGuiLayout() {
        try {
            if (gg.vape.module.none.ClientSettings.INSTANCE != null) {
                gg.vape.module.none.ClientSettings.UI_EXECUTOR.execute(() -> {
                    if (gg.vape.module.none.ClientSettings.inevitableStack != null) {
                        gg.vape.module.none.ClientSettings.inevitableStack.syncModules();
                    }
                    gg.vape.module.none.ClientSettings.INSTANCE.requestFrameLayoutRefresh();
                });
            }
        }
        catch (Throwable ignored) {
            // GUI 还没初始化时无需刷新
        }
    }

    /**
     * 读取一个 JSON、注册成模块；重名、版本不匹配、声明期加载失败都返回 null。
     */
    private static ScriptModule registerScriptModule(ModManager modManager, File file,
                                                     Set<String> usedNames) throws Exception {
        ScriptModule module = readModule(file);
        if (module == null) {
            return null;
        }
        if (!usedNames.add(module.getName().toLowerCase(Locale.ROOT))) {
            log("duplicate name '" + module.getName() + "' in " + file.getName() + "; skipped");
            Vape.logError("[Extension] duplicate script module name '" + module.getName() + "' in "
                    + file.getName() + "; skipped");
            return null;
        }
        ModRegistrationBuilder<ScriptModule> registration =
                ModRegistrationBuilder.<ScriptModule>create().setModule(module);
        for (List<MinecraftVersionConstraint> group : module.getVersionConstraints()) {
            registration.addVersionConstraintList(group);
        }
        module.setDefaultVisibility(!module.isDefaultHidden());
        registration.registerWith(modManager);
        if (modManager.getMod(module.getName()) == null) {
            log("skipped '" + module.getName() + "': current Minecraft version does not match "
                    + "its versions list");
            return null;
        }
        LOADED_MODULES.add(module);
        log("registered '" + module.getName() + "' (id=" + module.getScriptId()
                + (module.isDefaultHidden() ? ", hidden by default" : "") + ") from " + file.getName());
        Vape.debugLog("[Extension] registered script module " + module.getName()
                + " from " + file.getName());
        if (module.isDefaultEnabled()) {
            // 隐藏模块不能用普通开关打开；默认开启时绕过可见性检查
            module.setEnabled(true, true);
            log("enabled '" + module.getName() + "' because it defaults to enabled");
        }
        return module;
    }

    private static String canonicalPath(File file) {
        try {
            return file.getCanonicalPath();
        }
        catch (Throwable ignored) {
            return file.getAbsolutePath();
        }
    }

    /**
     * 供脚本运行时记录错误（会同时写客户端日志、scripts 上级目录的 extension.log，并弹一条游戏内提示）。
     */
    public static void logScriptFailure(String message) {
        log("FAILURE " + message);
        notifyScriptFailure(message);
    }

    private static void notifyScriptFailure(String message) {
        try {
            if (Vape.INSTANCE == null) {
                return;
            }
            gg.vape.notification.NotificationManager notifications = Vape.INSTANCE.getNotificationManager();
            if (notifications == null) {
                return;
            }
            String text = message;
            if (text != null && text.length() > 160) {
                text = text.substring(0, 157) + "...";
            }
            notifications.showInfo("Script error", text == null ? "" : text, 6000L);
        }
        catch (Throwable ignored) {
            // 提示失败不能反过来影响脚本加载
        }
    }

    public static void log(String message) {
        try {
            File file = new File(LocalConfigStore.directory(), "extension.log");
            String line = "[" + LocalTime.now().withNano(0) + "] " + message + System.lineSeparator();
            Files.write(file.toPath(), line.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        catch (Throwable ignored) {
            // 日志失败不能影响脚本模块加载
        }
    }

    private static void registerReloadModule(ModManager modManager, Set<String> usedNames) {
        if (reloadModuleRegistered || usedNames.contains("reload scripts")) {
            return;
        }
        try {
            ModRegistrationBuilder.create().setModule(new ReloadScriptsModule()).registerWith(modManager);
            reloadModuleRegistered = true;
            log("registered built-in 'Reload Scripts'");
        }
        catch (Throwable throwable) {
            log("failed to register built-in reload module: " + throwable);
        }
    }

    private static File scriptsDirectory() {
        File directory = new File(LocalConfigStore.directory(), SCRIPTS_DIRECTORY);
        if (!directory.exists() && !directory.mkdirs()) {
            Vape.logError("[Extension] cannot create script directory " + directory.getAbsolutePath());
        }
        File example = new File(directory, EXAMPLE_FILE_NAME);
        if (!example.exists()) {
            writeExampleScript(directory, example);
        }
        else {
            upgradePlaceholderExample(directory);
        }
        return directory;
    }

    private static void writeExampleScript(File directory, File example) {
        String json = "{\n"
                + "  \"name\": {\"en\": \"Example Script\", \"zh\": \"示例脚本\"},\n"
                + "  \"id\": \"example\",\n"
                + "  \"description\": {\n"
                + "    \"en\": \"Example JavaScript extension. Edit example.js, then use Reload Scripts "
                + "in the Extension category to recompile it.\",\n"
                + "    \"zh\": \"示例 JavaScript 扩展。修改 example.js 后，在「扩展」分类里点「重新加载脚本」即可重新编译。\"\n"
                + "  },\n"
                + "  \"main\": \"example.js\",\n"
                + "  \"defaultHidden\": false,\n"
                + "  \"defaultEnabled\": false,\n"
                + "  \"versions\": []\n"
                + "}\n";
        String script = String.join("\n", new String[] {
                "// Example extension written in JavaScript.",
                "//",
                "// Metadata (example.json):",
                "//   name/description - string, or {\"en\": \"...\", \"zh\": \"...\"} for bilingual display",
                "//   defaultHidden  - true hides the module from the GUI until the user reveals it",
                "//   defaultEnabled - true switches the module on when the client starts",
                "//   versions       - restrict the module to Minecraft versions, e.g.",
                "//                    [\"1.20.1\", \">=1.21.4\"] or [\"<26.1\"]; [] means every version",
                "//",
                "// API:",
                "//   log(message), notify(title, message), chat(message), isFriend(name)",
                "//   player{name,x,y,z,onGround,yaw,pitch,health,food}",
                "//   entities() / players(): snapshots {name,type,x,y,z,distance,isSelf,isPlayer,isFriend,health}",
                "//   module(name): {name,category,enabled,visible,setEnabled(v),toggle(),setting(name),settings()}",
                "//   modules(): every registered module handle",
                "//   setting.toggle(name, default), setting.number(name, default, min, max)",
                "//   setting.text(name, default), setting.mode(name, default, [options])",
                "//   setting.color(name, 0xAARRGGBB), setting.keybind(name, keyCode)",
                "//   draw.text(x, y, text, argb), draw.rect(x, y, w, h, argb), draw.hline(x, y, len, argb)",
                "//   draw.textWidth(text), draw.fontHeight(), draw.displayWidth(), draw.displayHeight()",
                "//   storage.get(key, fallback), storage.set(key, value), storage.has(key),",
                "//   storage.remove(key), storage.clear()",
                "//   after(ms, fn): run fn once after ms milliseconds (driven by the player tick)",
                "//   load(\"lib.js\"): run another script file in the same scope (once per path)",
                "//",
                "// Callbacks:",
                "//   onTick(), onEnable(), onDisable()",
                "//   onPacketSend(event) / onPacketReceive(event): event{kind,name,className,cancelled,cancel()}",
                "//   onRender2D(event): event{width,height}; draw.* only works inside this callback",
                "//   onKeyPress(event): event{key,down}",
                "//   onChat(event): event{message}",
                "//   onAttack(event): event{target}   (target may be null)",
                "//   onMove(event): event{x,y,z}",
                "//   onWorldChange()",
                "//",
                "// Settings must be declared at the top level: they are turned into real ClickGUI values",
                "// while the script loads, so they can be shown and saved before onEnable runs.",
                "var notifyOnEnable = setting.toggle({en: \"Notify on enable\", zh: \"开启时通知\"}, true);",
                "var showHud = setting.toggle({en: \"Show HUD\", zh: \"显示 HUD\"}, true);",
                "var speed = setting.number({en: \"Speed\", zh: \"速度\"}, 1, 0, 10);",
                "var label = setting.text({en: \"Label\", zh: \"标签\"}, \"hello\");",
                "var mode = setting.mode({en: \"Mode\", zh: \"模式\"}, \"Normal\", [\"Normal\", \"Fast\"]);",
                "var tint = setting.color({en: \"Tint\", zh: \"颜色\"}, 0xFFFFFFFF);",
                "var hotkey = setting.keybind({en: \"Hotkey\", zh: \"快捷键\"}, 0);",
                "",
                "var ticks = 0;",
                "",
                "function onEnable() {",
                "    ticks = storage.get(\"ticks\", 0);",
                "    log(\"example script enabled (mode=\" + mode.get() + \", speed=\" + speed.get() + \")\");",
                "    if (notifyOnEnable.get()) {",
                "        notify(\"Example\", \"script enabled\");",
                "    }",
                "    after(1000, function() { log(\"one second after enabling\"); });",
                "}",
                "",
                "function onDisable() {",
                "    storage.set(\"ticks\", ticks);",
                "    log(\"example script disabled after \" + ticks + \" ticks\");",
                "}",
                "",
                "function onTick() {",
                "    if (player == null) return;",
                "    ticks = ticks + 1;",
                "    if (ticks % 100 == 0) {",
                "        log(\"tick \" + ticks + \" at \" + player.x + \", \" + player.y + \", \" + player.z",
                "            + \" yaw=\" + player.yaw + \" hp=\" + player.health + \" food=\" + player.food",
                "            + \" players=\" + players().length + \" speed=\" + speed.get()",
                "            + \" label=\" + label.get());",
                "    }",
                "}",
                "",
                "function onRender2D(event) {",
                "    if (!showHud.get() || player == null) return;",
                "    var color = tint.get();",
                "    draw.text(4, 4, label.get() + \"  ticks=\" + ticks, color);",
                "    draw.hline(4, 16, 60, color);",
                "}",
                "",
                "function onKeyPress(event) {",
                "    // event.key / event.down",
                "}",
                "",
                "function onChat(event) {",
                "    // event.message: formatted chat text",
                "}",
                "",
                "function onAttack(event) {",
                "    // event.target: entity snapshot or null",
                "}",
                "",
                "function onMove(event) {",
                "    // event.x / event.y / event.z: this tick's motion",
                "}",
                "",
                "function onWorldChange() {",
                "    log(\"world changed\");",
                "}",
                "",
                "function onPacketSend(event) {",
                "    // Call event.cancel() to drop the outgoing packet.",
                "}",
                "",
                "function onPacketReceive(event) {",
                "    // Call event.cancel() to drop the incoming packet.",
                "}"
        });

        try {
            Files.write(example.toPath(), json.getBytes(StandardCharsets.UTF_8));
            Files.write(new File(directory, EXAMPLE_SCRIPT_NAME).toPath(), script.getBytes(StandardCharsets.UTF_8));
        }
        catch (IOException exception) {
            Vape.logError("[Extension] cannot write example script: " + exception);
        }
    }

    /**
     * 把旧版占位/演示 example.js 升级成当前模板；用户改过的脚本不动。
     */
    private static void upgradePlaceholderExample(File directory) {
        File scriptFile = new File(directory, EXAMPLE_SCRIPT_NAME);
        if (!scriptFile.exists()) {
            return;
        }
        try {
            String existing = new String(Files.readAllBytes(scriptFile.toPath()), StandardCharsets.UTF_8);
            if (existing.contains("Placeholder executed by the script runtime")) {
                writeExampleScript(directory, new File(directory, EXAMPLE_FILE_NAME));
                log("upgraded placeholder example.js");
                return;
            }
            File metadataFile = new File(directory, EXAMPLE_FILE_NAME);
            String metadata = metadataFile.exists()
                    ? new String(Files.readAllBytes(metadataFile.toPath()), StandardCharsets.UTF_8) : "";
            if (existing.contains("setting.toggle(\"Notify on enable\"") && !metadata.contains("\"zh\"")) {
                // 上一版模板只有英文名称/设置项；升级到支持 {en, zh} 的双语模板。
                writeExampleScript(directory, metadataFile);
                log("upgraded example.js to the bilingual template");
                return;
            }
            if (existing.contains("module(name), modules()") && !existing.contains("onRender2D")) {
                // 上一版模板还没有渲染/实体查询/storage/after/load 等能力，升级到覆盖新 API 的模板。
                writeExampleScript(directory, new File(directory, EXAMPLE_FILE_NAME));
                log("upgraded example.js to the capability template");
                return;
            }
            if (existing.contains("// API: log(message), notify(title, message), chat(message), module(name), player{name,x,y,z,onGround}")) {
                // 旧模板只演示了 player/module；升级到带 modules() 与包事件回调的新模板。
                writeExampleScript(directory, new File(directory, EXAMPLE_FILE_NAME));
                log("upgraded example.js to the current API template");
                return;
            }
            if (!existing.contains("setting.toggle") && existing.contains("// API:")
                    && existing.contains("onPacketSend")) {
                // 上一版模板：升级到带 setting.* 与元数据的模板。
                writeExampleScript(directory, new File(directory, EXAMPLE_FILE_NAME));
                log("upgraded example.js to the settings-capable template");
                return;
            }
            // 早期演示版用聊天栏输出；聊天消息可能被服务端丢弃，改成客户端通知。
            if (existing.contains("chat(\"[Example] script enabled\")")) {
                String upgraded = existing.replace("chat(\"[Example] script enabled\")",
                        "notify(\"Example\", \"script enabled\")");
                Files.write(scriptFile.toPath(), upgraded.getBytes(StandardCharsets.UTF_8));
                log("upgraded example.js to notification output");
            }
        }
        catch (Throwable throwable) {
            log("failed to upgrade example.js: " + throwable);
        }
    }

    private static ScriptModule readModule(File file) throws Exception {
        JsonObject json;
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            json = JsonParser.parseReader(reader).getAsJsonObject();
        }
        String fileName = file.getName();
        String fallback = fileName.substring(0, fileName.length() - ".json".length());
        String[] localizedName = optLocalized(json, "name");
        String[] localizedDescription = optLocalized(json, "description");
        // 内部名/配置 key 固定用英文；中文只注册渲染期翻译（切换语言不丢配置）。
        String name = localizedName[0].isEmpty() ? localizedName[1] : localizedName[0];
        String id = optString(json, "id", fallback);
        String description = localizedDescription[0].isEmpty()
                ? localizedDescription[1] : localizedDescription[0];
        if (name.isEmpty()) {
            name = fallback;
        }
        if (id.isEmpty()) {
            id = fallback;
        }
        if (description.isEmpty()) {
            description = "Third-party script module";
        }
        ScriptTranslation.register(name, localizedName[1]);
        ScriptTranslation.register(description, localizedDescription[1]);
        ScriptTranslation.register("Third-party script module", "第三方脚本模块");
        File scriptFile = resolveScriptFile(file, json, fallback);
        if (!scriptFile.isFile()) {
            log("script entry missing for " + file.getName() + ": " + scriptFile.getAbsolutePath());
        }
        boolean defaultHidden = optBoolean(json, "defaultHidden", false);
        boolean defaultEnabled = optBoolean(json, "defaultEnabled", false);
        List<List<MinecraftVersionConstraint>> versionConstraints = parseVersionConstraints(json);
        if (versionConstraints == null) {
            log("skipping " + file.getName() + ": versions contains an unknown entry");
            return null;
        }
        ScriptModule module = instantiate(name, description, id, scriptFile.getAbsolutePath());
        module.configureMetadata(defaultHidden, defaultEnabled, versionConstraints);
        // 声明阶段必须发生在注册与配置载入之前：脚本顶层的 setting.* 会在这里创建真实 Value，
        // 注册得早 GUI 才能看到设置项，配置载入得晚才能把保存值恢复到这些 Value 上。
        if (!module.prepareSettings()) {
            log("skipping " + file.getName() + ": script failed to load during declaration");
            return null;
        }
        return module;
    }

    private static File resolveScriptFile(File jsonFile, JsonObject json, String fallback) {
        String main = optString(json, "main", "");
        File parent = jsonFile.getParentFile();
        if (main.isEmpty()) {
            return new File(parent, fallback + ".js");
        }
        File resolved = new File(parent, main);
        if (!resolved.exists() && !main.toLowerCase(Locale.ROOT).endsWith(".js")) {
            File withExtension = new File(parent, main + ".js");
            if (withExtension.exists()) {
                return withExtension;
            }
        }
        return resolved;
    }

    private static String optString(JsonObject json, String key, String fallback) {
        if (json == null || !json.has(key) || json.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return json.get(key).getAsString().trim();
        }
        catch (Throwable ignored) {
            return fallback;
        }
    }

    /**
     * 读取双语字段：既接受字符串（旧格式，同时也是唯一名称），也接受
     * {@code {"en": "...", "zh": "..."}} 对象（也识别 {@code cn}/{@code default} 别名），
     * 并兼容同级 {@code <key>Zh} 字符串。
     *
     * @return {@code [canonical, localized]}；canonical 为空时表示该字段缺省
     */
    private static String[] optLocalized(JsonObject json, String key) {
        String siblingChinese = optString(json, key + "Zh", "");
        JsonElement element = json == null ? null : json.get(key);
        if (element == null || element.isJsonNull()) {
            return new String[]{"", siblingChinese};
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            String english = optString(object, "en", "");
            if (english.isEmpty()) {
                english = optString(object, "default", "");
            }
            String chinese = optString(object, "zh", "");
            if (chinese.isEmpty()) {
                chinese = optString(object, "cn", "");
            }
            if (chinese.isEmpty()) {
                chinese = siblingChinese;
            }
            return new String[]{english, chinese};
        }
        return new String[]{optString(json, key, ""), siblingChinese};
    }

    private static boolean optBoolean(JsonObject json, String key, boolean fallback) {
        if (json == null || !json.has(key) || json.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return json.get(key).getAsBoolean();
        }
        catch (Throwable ignored) {
            return fallback;
        }
    }

    /**
     * 解析 {@code versions} 数组：支持 {@code "1.20.1"}、{@code ">=1.20.1"}、{@code ">"}、
     * {@code "<="}、{@code "<"}、{@code "!="}、{@code "="}、{@code "*"}（全部版本），也接受
     * 协议号（如 {@code "47"}）。每个条目是一组单约束（组间 OR、组内 AND）。
     *
     * @return 约束组；空列表表示不限版本；{@code null} 表示存在无法识别的条目
     */
    private static List<List<MinecraftVersionConstraint>> parseVersionConstraints(JsonObject json) {
        JsonElement element = json == null ? null : json.get("versions");
        if (element == null || element.isJsonNull() || !element.isJsonArray()) {
            return new ArrayList<List<MinecraftVersionConstraint>>();
        }
        JsonArray array = element.getAsJsonArray();
        if (array.size() == 0) {
            return new ArrayList<List<MinecraftVersionConstraint>>();
        }
        List<List<MinecraftVersionConstraint>> groups = new ArrayList<List<MinecraftVersionConstraint>>();
        for (JsonElement entry : array) {
            String spec = entry == null || entry.isJsonNull() ? "" : entry.getAsString().trim();
            if (spec.isEmpty() || "*".equals(spec)) {
                return new ArrayList<List<MinecraftVersionConstraint>>();
            }
            MinecraftVersionConstraint constraint = parseVersionConstraint(spec);
            if (constraint == null) {
                return null;
            }
            groups.add(Collections.singletonList(constraint));
        }
        return groups;
    }

    private static MinecraftVersionConstraint parseVersionConstraint(String spec) {
        String operator = "=";
        String version = spec;
        String[] operators = {">=", "<=", "!=", ">", "<", "="};
        for (String candidate : operators) {
            if (spec.startsWith(candidate)) {
                operator = candidate;
                version = spec.substring(candidate.length()).trim();
                break;
            }
        }
        ForgeVersion target = findForgeVersion(version);
        if (target == null) {
            return null;
        }
        MinecraftVersionComparisonOperator comparison;
        if (">=".equals(operator)) {
            comparison = MinecraftVersionComparisonOperator.GREATHER_THAN_OR_EQUAL;
        }
        else if ("<=".equals(operator)) {
            comparison = MinecraftVersionComparisonOperator.LESS_THAN_OR_EQUAL;
        }
        else if (">".equals(operator)) {
            comparison = MinecraftVersionComparisonOperator.GREATER_THAN;
        }
        else if ("<".equals(operator)) {
            comparison = MinecraftVersionComparisonOperator.LESS_THAN;
        }
        else if ("!=".equals(operator)) {
            comparison = MinecraftVersionComparisonOperator.NOT_EQUAL;
        }
        else {
            comparison = MinecraftVersionComparisonOperator.EQUALS;
        }
        return new MinecraftVersionConstraint(target, comparison);
    }

    private static ForgeVersion findForgeVersion(String version) {
        String normalized = normalizeVersionName(version);
        if (!normalized.isEmpty()) {
            for (ForgeVersion candidate : ForgeVersion.values()) {
                if (normalizeVersionName(candidate.name()).equals(normalized)) {
                    return candidate;
                }
            }
        }
        try {
            int protocol = Integer.parseInt(version.trim());
            for (ForgeVersion candidate : ForgeVersion.values()) {
                if (candidate.getProtocolVersion() == protocol) {
                    return candidate;
                }
            }
        }
        catch (Throwable ignored) {
            // 不是协议号就按名字匹配失败处理
        }
        return null;
    }

    private static String normalizeVersionName(String version) {
        if (version == null) {
            return "";
        }
        String normalized = version.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("mc_")) {
            normalized = normalized.substring(3);
        }
        return normalized.replace('_', '.').replace("-actual", ".actual");
    }

    private static ScriptModule instantiate(String name, String description, String id, String sourcePath)
    throws ReflectiveOperationException {
        Class<?> generated = defineGeneratedClass(GENERATED_CLASS_COUNTER.incrementAndGet());
        return (ScriptModule) generated
                .getConstructor(String.class, String.class, String.class, String.class)
                .newInstance(name, description, id, sourcePath);
    }

    private static Class<?> defineGeneratedClass(int index) {
        String superName = Type.getInternalName(ScriptModule.class);
        String generatedName = superName + "$" + index;
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER,
                generatedName, null, superName, null);
        MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitVarInsn(Opcodes.ALOAD, 1);
        constructor.visitVarInsn(Opcodes.ALOAD, 2);
        constructor.visitVarInsn(Opcodes.ALOAD, 3);
        constructor.visitVarInsn(Opcodes.ALOAD, 4);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, superName, "<init>",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();
        writer.visitEnd();
        byte[] bytecode = writer.toByteArray();
        ClassLoader parent = ScriptModule.class.getClassLoader();
        return new GeneratedClassLoader(parent).define(generatedName.replace('/', '.'), bytecode);
    }

    private static final class GeneratedClassLoader
    extends ClassLoader {
        private GeneratedClassLoader(ClassLoader parent) {
            super(parent);
        }

        private Class<?> define(String name, byte[] bytecode) {
            return this.defineClass(name, bytecode, 0, bytecode.length);
        }
    }
}
