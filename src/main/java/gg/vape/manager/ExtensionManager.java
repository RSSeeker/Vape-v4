package gg.vape.manager;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gg.vape.Vape;
import gg.vape.config.LocalConfigStore;
import gg.vape.module.extension.ReloadScriptsModule;
import gg.vape.module.extension.ScriptModule;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
 * {@code name}（显示名，缺省用文件名）、{@code id}（稳定标识，缺省用文件名）、
 * {@code description}（悬停说明）、{@code main}（脚本入口，相对 JSON 所在目录，
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
                    ScriptModule module = readModule(file);
                    if (module == null) {
                        continue;
                    }
                    if (!usedNames.add(module.getName().toLowerCase(Locale.ROOT))) {
                        log("duplicate name '" + module.getName() + "' in " + file.getName() + "; skipped");
                        Vape.logError("[Extension] duplicate script module name '" + module.getName() + "' in "
                                + file.getName() + "; skipped");
                        continue;
                    }
                    ModRegistrationBuilder.create().setModule(module).registerWith(modManager);
                    LOADED_MODULES.add(module);
                    ++loaded;
                    log("registered '" + module.getName() + "' (id=" + module.getScriptId() + ") from " + file.getName());
                    Vape.debugLog("[Extension] registered script module " + module.getName()
                            + " from " + file.getName());
                }
                catch (Throwable throwable) {
                    log("failed to load " + file.getName() + ": " + throwable);
                    Vape.logError("[Extension] failed to load " + file.getName() + ": " + throwable);
                }
            }
        }
        registerReloadModule(modManager, usedNames);
        log("loaded " + loaded + " script module(s)");
        return loaded;
    }

    /**
     * 重新编译所有已注册脚本（供「重新加载脚本」模块调用）。新增/删除 JSON 仍需重启。
     *
     * @return 重新加载成功的脚本数量
     */
    public static int reloadAll() {
        int reloaded = 0;
        for (ScriptModule module : new ArrayList<ScriptModule>(LOADED_MODULES)) {
            try {
                if (module.reloadScript()) {
                    ++reloaded;
                }
                else {
                    log("reload failed for '" + module.getName() + "'");
                }
            }
            catch (Throwable throwable) {
                log("reload failed for '" + module.getName() + "': " + throwable);
                Vape.logError("[Extension] reload failed for " + module.getName() + ": " + throwable);
            }
        }
        log("reloaded " + reloaded + " script module(s)");
        return reloaded;
    }

    /**
     * 供脚本运行时记录错误（会同时写客户端日志和 scripts\..\extension.log）。
     */
    public static void logScriptFailure(String message) {
        log("FAILURE " + message);
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
                + "  \"name\": \"Example Script\",\n"
                + "  \"id\": \"example\",\n"
                + "  \"description\": \"Example JavaScript extension. Edit example.js, then use "
                + "Reload Scripts in the Extension category to recompile it.\",\n"
                + "  \"main\": \"example.js\"\n"
                + "}\n";
        String script = "// Example extension written in JavaScript.\n"
                + "// API: log(message), notify(title, message), chat(message), module(name), player{name,x,y,z,onGround}\n"
                + "var ticks = 0;\n"
                + "\n"
                + "function onEnable() {\n"
                + "    ticks = 0;\n"
                + "    log(\"example script enabled\");\n"
                + "    notify(\"Example\", \"script enabled\");\n"
                + "}\n"
                + "\n"
                + "function onDisable() {\n"
                + "    log(\"example script disabled\");\n"
                + "}\n"
                + "\n"
                + "function onTick() {\n"
                + "    if (player == null) return;\n"
                + "    ticks = ticks + 1;\n"
                + "    if (ticks % 100 == 0) {\n"
                + "        log(\"tick \" + ticks + \" at \" + player.x + \", \" + player.y + \", \" + player.z);\n"
                + "    }\n"
                + "}\n";
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
        String name = optString(json, "name", fallback);
        String id = optString(json, "id", fallback);
        String description = optString(json, "description", "");
        if (name.isEmpty()) {
            name = fallback;
        }
        if (id.isEmpty()) {
            id = fallback;
        }
        if (description.isEmpty()) {
            description = "Third-party script module";
        }
        File scriptFile = resolveScriptFile(file, json, fallback);
        if (!scriptFile.isFile()) {
            log("script entry missing for " + file.getName() + ": " + scriptFile.getAbsolutePath());
        }
        return instantiate(name, description, id, scriptFile.getAbsolutePath());
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
