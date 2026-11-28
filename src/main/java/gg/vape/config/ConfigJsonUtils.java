package gg.vape.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import gg.vape.input.KeyboardCodeUtil;
import gg.vape.ui.click.component.GuiComponent;
import gg.vape.utils.Base64Util;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.jetbrains.annotations.Nullable;

public class ConfigJsonUtils {
    private static GuiComponent[] sharedGuiComponents;
    private static final String BASE64_PREFIX;

    @Nullable
    public static Boolean getBoolean(JsonObject object, String key) {
        return ConfigJsonUtils.getPrimitiveValue(object, key, JsonPrimitive::getAsBoolean);
    }

    public static void setSharedGuiComponents(GuiComponent[] components) {
        sharedGuiComponents = components;
    }

    public static GuiComponent[] getSharedGuiComponents() {
        return sharedGuiComponents;
    }

    @Nullable
    public static String getString(JsonObject object, String key) {
        return ConfigJsonUtils.getElementValue(object, key, ConfigJsonUtils::getStringValue);
    }

    public static boolean getBooleanOrFalse(JsonObject object, String key) {
        Boolean value = ConfigJsonUtils.getBoolean(object, key);
        return value != null ? value : false;
    }

    static {
        ConfigJsonUtils.setSharedGuiComponents(new GuiComponent[5]);
        BASE64_PREFIX = "b64:";
    }

    public static String getDecodedStringOrEmpty(JsonObject object, String key) {
        if (object.get(key) != null && !object.get(key).isJsonNull()) {
            String value = object.get(key).getAsString();
            if (value.startsWith(BASE64_PREFIX)) {
                value = Base64Util.decodeUtf8Base64(value.substring(BASE64_PREFIX.length()));
            }
            return value;
        }
        return "";
    }

    @Nullable
    public static Long getLong(JsonObject object, String key) {
        return ConfigJsonUtils.getPrimitiveValue(object, key, JsonPrimitive::getAsLong);
    }

    @Nullable
    public static JsonArray getJsonArray(JsonObject object, String key) {
        return ConfigJsonUtils.getElementValue(object, key, ConfigJsonUtils::getJsonArrayValue);
    }

    private static Object applyPrimitiveConverter(Function converter, JsonElement element) {
        if (!element.isJsonPrimitive()) {
            return null;
        }
        return converter.apply(element.getAsJsonPrimitive());
    }

    private static JsonArray getJsonArrayValue(JsonElement element) {
        if (!element.isJsonArray()) {
            return null;
        }
        return element.getAsJsonArray();
    }

    @Nullable
    private static <T> T getPrimitiveValue(JsonObject object, String key, Function<JsonPrimitive, T> converter) {
        return (T)ConfigJsonUtils.getElementValue(object, key, element -> ConfigJsonUtils.applyPrimitiveConverter(converter, element));
    }

    @Nullable
    public static String getDecodedBase64String(JsonObject object, String key) {
        String encodedValue = ConfigJsonUtils.getString(object, key);
        if (encodedValue == null) {
            return null;
        }
        return Base64Util.decodeUtf8Base64(encodedValue);
    }

    private static IllegalArgumentException preserveIllegalArgumentException(IllegalArgumentException error) {
        return error;
    }

    @Nullable
    public static UUID getUuid(JsonObject object, String key) {
        String value = ConfigJsonUtils.getString(object, key);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        }
        catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @Nullable
    public static Double getDouble(JsonObject object, String key) {
        return ConfigJsonUtils.getPrimitiveValue(object, key, JsonPrimitive::getAsDouble);
    }

    public static List<Integer> parseInputCodes(JsonArray array, boolean convertLegacyKeyCodes) {
        List<Integer> serializedCodes = ConfigJsonUtils.getIntegerList(array);
        ArrayList<Integer> inputCodes = new ArrayList<Integer>();
        for (Integer inputCode : serializedCodes) {
            if (inputCode == 0) continue;
            if (convertLegacyKeyCodes && inputCode > 0) {
                inputCode = KeyboardCodeUtil.convertLegacyKeyCode(inputCode);
            }
            if (inputCode == 0 || inputCode == 27) continue;
            inputCodes.add(inputCode);
        }
        return inputCodes;
    }

    @Nullable
    public static Short getShort(JsonObject object, String key) {
        return ConfigJsonUtils.getPrimitiveValue(object, key, JsonPrimitive::getAsShort);
    }

    private static JsonObject getJsonObjectValue(JsonElement element) {
        if (!element.isJsonObject()) {
            return null;
        }
        return element.getAsJsonObject();
    }

    @Nullable
    public static Integer getInteger(JsonObject object, String key) {
        return ConfigJsonUtils.getPrimitiveValue(object, key, JsonPrimitive::getAsInt);
    }

    public static List<Integer> getIntegerList(JsonArray array) {
        ArrayList<Integer> values = new ArrayList<Integer>();
        for (JsonElement element : array) {
            JsonPrimitive primitive;
            if (element.isJsonNull() || !element.isJsonPrimitive()) continue;
            primitive = element.getAsJsonPrimitive();
            // JsonPrimitive.isNumber() only exists on Gson >= 2.8.0; the Gson
            // bundled with 1.7.10/1.8.9 (2.2.4) lacks it.
            try {
                if (primitive.getAsNumber() == null) continue;
            }
            catch (Throwable ignored) {
                continue;
            }
            values.add(primitive.getAsInt());
        }
        return values;
    }

    /**
     * Deep-copies a JsonElement. Gson's JsonElement.deepCopy() only exists on
     * Gson >= 2.8.0; 1.7.10/1.8.9 (2.2.4) needs this manual recursive copy.
     */
    public static JsonElement deepCopy(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return JsonNull.INSTANCE;
        }
        if (element.isJsonPrimitive()) {
            return element;
        }
        if (element.isJsonArray()) {
            JsonArray copy = new JsonArray();
            for (JsonElement child : element.getAsJsonArray()) {
                copy.add(ConfigJsonUtils.deepCopy(child));
            }
            return copy;
        }
        JsonObject copy = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            copy.add(entry.getKey(), ConfigJsonUtils.deepCopy(entry.getValue()));
        }
        return copy;
    }

    public static JsonObject deepCopy(JsonObject object) {
        return (JsonObject)ConfigJsonUtils.deepCopy((JsonElement)object);
    }

    public static JsonArray deepCopy(JsonArray array) {
        return (JsonArray)ConfigJsonUtils.deepCopy((JsonElement)array);
    }

    @Nullable
    public static JsonObject getJsonObject(JsonObject object, String key) {
        return ConfigJsonUtils.getElementValue(object, key, ConfigJsonUtils::getJsonObjectValue);
    }

    @Nullable
    private static <T> T getElementValue(JsonObject object, String key, Function<JsonElement, T> converter) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        return converter.apply(element);
    }

    private static String getStringValue(JsonElement element) {
        if (!element.isJsonPrimitive()) {
            return null;
        }
        return element.getAsString();
    }
}
