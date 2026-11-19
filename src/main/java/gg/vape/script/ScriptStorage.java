package gg.vape.script;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import gg.vape.Vape;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeJSON;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.Undefined;

/**
 * 脚本级的持久化小仓库。
 *
 * <p>每个脚本模块对应 {@code .vapeclient\scripts\data\<scriptId>.json} 一个文件，脚本里
 * 通过 {@code storage.get(key, fallback)} / {@code storage.set(key, value)} 读写。文件在
 * 第一次访问时惰性加载，{@code set/remove/clear} 之后立即落盘（数据量很小，不值得做延迟
 * 刷新），客户端异常退出也不会丢已写入的值。</p>
 *
 * <p>支持的 JS 值：字符串、数字、布尔值、{@code null}，以及对象/数组——后者会用
 * {@link NativeJSON#stringify} 序列化成 JSON 文本存进去，读回来时是字符串（脚本可以自己
 * {@code JSON.parse}）。这样做是为了不让任意 Java 对象被存进配置文件。</p>
 */
public final class ScriptStorage {
    private final File file;
    private JsonObject data;
    private boolean dirty;

    public ScriptStorage(File file) {
        this.file = file;
    }

    public File getFile() {
        return this.file;
    }

    public synchronized boolean has(String key) {
        return key != null && this.object().has(key) && !this.object().get(key).isJsonNull();
    }

    public synchronized Object get(String key, Object fallback) {
        if (key == null) {
            return fallback;
        }
        JsonElement element = this.object().get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        return toJava(element);
    }

    public synchronized void set(String key, Object value) {
        if (key == null || key.isEmpty()) {
            return;
        }
        JsonObject object = this.object();
        if (value == null || value == Undefined.instance) {
            object.remove(key);
        }
        else {
            JsonElement element = toJson(value);
            if (element == null) {
                object.remove(key);
            }
            else {
                object.add(key, element);
            }
        }
        this.dirty = true;
        this.flush();
    }

    public synchronized void remove(String key) {
        if (key == null) {
            return;
        }
        if (this.object().remove(key) != null) {
            this.dirty = true;
            this.flush();
        }
    }

    public synchronized void clear() {
        this.data = new JsonObject();
        this.dirty = true;
        this.flush();
    }

    /**
     * 把内存里的内容写回磁盘；没有改动时什么都不做。
     */
    public synchronized void flush() {
        if (!this.dirty || this.data == null) {
            return;
        }
        try {
            File parent = this.file.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
                Vape.logError("[Script] cannot create storage directory " + parent.getAbsolutePath());
                return;
            }
            Files.write(this.file.toPath(), this.data.toString().getBytes(StandardCharsets.UTF_8));
            this.dirty = false;
        }
        catch (Throwable throwable) {
            Vape.logError("[Script] failed to write storage " + this.file.getAbsolutePath() + ": " + throwable);
        }
    }

    private JsonObject object() {
        if (this.data == null) {
            this.data = this.read();
        }
        return this.data;
    }

    private JsonObject read() {
        try {
            if (!this.file.isFile()) {
                return new JsonObject();
            }
            String text = new String(Files.readAllBytes(this.file.toPath()), StandardCharsets.UTF_8);
            if (text.trim().isEmpty()) {
                return new JsonObject();
            }
            JsonElement parsed = JsonParser.parseString(text);
            if (parsed != null && parsed.isJsonObject()) {
                return parsed.getAsJsonObject();
            }
            Vape.logError("[Script] storage file is not a JSON object, starting empty: "
                    + this.file.getAbsolutePath());
        }
        catch (Throwable throwable) {
            Vape.logError("[Script] failed to read storage " + this.file.getAbsolutePath() + ": " + throwable);
        }
        return new JsonObject();
    }

    private static Object toJava(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                return primitive.getAsBoolean();
            }
            if (primitive.isNumber()) {
                return primitive.getAsDouble();
            }
            return primitive.getAsString();
        }
        // 对象/数组：读回 JSON 文本，让脚本自己 JSON.parse
        return element.toString();
    }

    private static JsonElement toJson(Object value) {
        if (value == null || value == Undefined.instance) {
            return null;
        }
        if (value instanceof Boolean) {
            return new JsonPrimitive((Boolean) value);
        }
        if (value instanceof Number) {
            double number = ((Number) value).doubleValue();
            if (Double.isNaN(number) || Double.isInfinite(number)) {
                return null;
            }
            return new JsonPrimitive(number);
        }
        if (value instanceof CharSequence) {
            return new JsonPrimitive(value.toString());
        }
        try {
            Context context = Context.getCurrentContext();
            Scriptable scope = value instanceof Scriptable ? (Scriptable) value : null;
            Object serialized = NativeJSON.stringify(context, scope, value, null, null);
            if (serialized instanceof CharSequence) {
                return JsonParser.parseString(serialized.toString());
            }
        }
        catch (Throwable throwable) {
            Vape.logError("[Script] cannot store value of type " + value.getClass().getName() + ": " + throwable);
        }
        return new JsonPrimitive(String.valueOf(value));
    }
}
