import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gg.vape.Vape;
import gg.vape.config.ConfigJsonUtils;
import gg.vape.config.ProfileModuleSnapshot;
import gg.vape.config.ProfileSnapshot;
import gg.vape.module.Mod;
import gg.vape.unmap.ModeSelection;
import gg.vape.value.ColorValue;
import gg.vape.value.EntityTargetFilterValue;
import gg.vape.value.ModeValue;
import gg.vape.value.StringMapValue;
import gg.vape.value.StringValue;
import gg.vape.value.ValueSnapshot;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Real value and profile-snapshot code with Gson; Minecraft and module services are doubles. */
public final class ValueProfileSerializationTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class ThrowingStringValue extends StringValue {
        boolean throwOnRead;
        boolean throwOnWrite;
        ThrowingStringValue() { super(null, "throwing", "default"); }
        @Override public void parse(String value) {
            if (throwOnRead) throw new IllegalArgumentException("read failed");
            super.parse(value);
        }
        @Override public JsonObject toJson(boolean includeValue) {
            if (throwOnWrite) throw new IllegalStateException("write failed");
            return super.toJson(includeValue);
        }
    }

    public static void main(String[] args) {
        StringValue encoded = StringValue.create(null, "text", "default").setBase64Encoded(true);
        encoded.setValue("");
        JsonObject encodedJson = encoded.toJson(false);
        check("b64:".equals(encodedJson.get("value").getAsString()), "empty text encoded");
        StringValue restored = StringValue.create(null, "text", "default").setBase64Encoded(true);
        check(restored.loadJson(encodedJson) && "".equals(restored.getValue()), "empty text round trip");
        JsonObject macro = new JsonObject();
        macro.addProperty("name", "b64:");
        check("".equals(ConfigJsonUtils.getDecodedStringOrEmpty(macro, "name")), "empty encoded macro text");
        check("direct".equals(new StringValue(null, "direct", "").copyValueDefinition().getName()),
                "directly constructed text keeps its snapshot name");

        StringMapValue map = new StringMapValue(null, "Mapping", "Key", "Value");
        map.putEntry("", "");
        map.putEntry("snow 雪:🔑", "value:✓");
        JsonObject mapJson = JsonParser.parseString(map.toJson(false).toString()).getAsJsonObject();
        StringMapValue loaded = new StringMapValue(null, "Mapping", "Key", "Value");
        check(loaded.loadJson(mapJson), "map loads");
        check(map.getValue().equals(loaded.getValue()), "empty and Unicode entries round trip");
        JsonObject legacy = new JsonObject();
        JsonObject legacyData = new JsonObject();
        legacyData.addProperty("plain", "legacy:value");
        legacy.add("data", legacyData);
        check(loaded.loadJson(legacy) && "legacy:value".equals(loaded.getValue().get("plain")), "legacy map loads");
        JsonObject invalid = new JsonObject();
        JsonObject invalidData = new JsonObject();
        invalidData.addProperty("b64:%%%", "b64:");
        invalid.add("data", invalidData);
        Map<String, String> beforeInvalid = new LinkedHashMap<>(loaded.getValue());
        check(!loaded.loadJson(invalid) && beforeInvalid.equals(loaded.getValue()), "invalid map leaves live data intact");
        invalidData.addProperty("b64:%%%", "missing-prefix");
        check(!loaded.loadJson(invalid) && beforeInvalid.equals(loaded.getValue()), "invalid encoded pair is atomic");
        invalidData.remove("b64:%%%");
        invalidData.add("b64:", null);
        check(!loaded.loadJson(invalid) && beforeInvalid.equals(loaded.getValue()), "null encoded pair is atomic");

        StringMapValue live = new StringMapValue(null, "Mapping", "Key", "Value");
        live.putEntry("live", "unchanged");
        Map<String, String> liveReference = live.getValue();
        ValueSnapshot<StringMapValue, Map<String, String>> snapshot = new ValueSnapshot<>(live);
        snapshot.getValue().put("preview", "only");
        check(live.getDefaultValue().isEmpty(), "preview does not mutate default map");
        check(live.getValue() == liveReference && live.getValue().size() == 1, "preview does not mutate live map");
        int saves = Vape.INSTANCE.saves;
        JsonObject previewJson = snapshot.toJson();
        check(Vape.INSTANCE.saves == saves && live.getValue() == liveReference, "preview serialization does not save or replace live map");
        check(previewJson.getAsJsonObject("data").size() == 1, "preview serializes its own map");
        live.setResettable(false);
        snapshot.loadJson(mapJson);
        check(snapshot.getValue().equals(map.getValue()), "snapshot loads profile map");
        check(live.getValue() == liveReference && live.getValue().size() == 1 && live.getValue().containsKey("live"),
                "non-resettable map profile load leaves live data intact");
        check(Vape.INSTANCE.saves == saves, "snapshot loading does not save");
        live.setPersistenceSuppressed(true);
        snapshot.toJson();
        snapshot.loadJson(mapJson);
        check(live.isPersistenceSuppressed(), "preexisting suppression restored");
        live.setPersistenceSuppressed(false);

        ThrowingStringValue failing = new ThrowingStringValue();
        failing.setValue("live");
        ValueSnapshot<ThrowingStringValue, String> failingSnapshot = new ValueSnapshot<>(failing);
        JsonObject failureJson = new JsonObject();
        failureJson.addProperty("id", "throwing");
        failureJson.addProperty("value", "other");
        failing.throwOnRead = true;
        try { failingSnapshot.loadJson(failureJson); throw new AssertionError("expected read failure"); }
        catch (IllegalArgumentException expected) {}
        check("live".equals(failing.getValue()) && !failing.isPersistenceSuppressed(), "read failure restores value and suppression");
        failing.throwOnRead = false;
        failing.throwOnWrite = true;
        try { failingSnapshot.toJson(); throw new AssertionError("expected write failure"); }
        catch (IllegalStateException expected) {}
        check("live".equals(failing.getValue()) && !failing.isPersistenceSuppressed(), "write failure restores value and suppression");

        ModeSelection first = new ModeSelection("First"), second = new ModeSelection("Second");
        ModeValue mode = ModeValue.create(null, "Mode", first, first, second);
        mode.parse("Second");
        check(mode.getValue() == second, "mode options are attached for parsing");
        mode.setValue(first);
        JsonObject modeJson = new JsonObject();
        modeJson.addProperty("id", "Mode");
        modeJson.addProperty("value", "Second");
        ValueSnapshot<ModeValue, ModeSelection> modeSnapshot = new ValueSnapshot<>(mode);
        modeSnapshot.loadJson(modeJson);
        check(modeSnapshot.getValue() == second && mode.getValue() == first, "mode snapshot is isolated");
        check("Second".equals(modeSnapshot.toJson().get("value").getAsString()) && mode.getValue() == first,
                "mode snapshot serializes without changing live selection");

        ColorValue color = ColorValue.createFromChannels(null, "Color", 20, 30, 40, 255);
        color.setComponentValues(new Object[]{120.0, 70.0, 80.0, 180.0, true});
        Object[] liveColor = color.getComponentValuesSnapshot();
        ValueSnapshot<ColorValue, Object[]> colorSnapshot = new ValueSnapshot<>(color);
        JsonObject colorJson = new JsonObject();
        colorJson.addProperty("id", "Color");
        colorJson.addProperty("hue2", 42.0);
        colorJson.addProperty("brightness2", 72.0);
        colorJson.addProperty("rainbow", false);
        saves = Vape.INSTANCE.saves;
        colorSnapshot.loadJson(colorJson);
        check(Arrays.equals(liveColor, color.getComponentValuesSnapshot()), "color profile preview restores live channels");
        check(Vape.INSTANCE.saves == saves, "color profile preview does not save child values");
        check(((Double)colorSnapshot.getValue()[0]) == 42.0 && ((Double)colorSnapshot.getValue()[1]) == 72.0,
                "color snapshot captures profile channels");
        JsonObject colorOutput = colorSnapshot.toJson();
        check(colorOutput.get("hue2").getAsDouble() == 42.0 && colorOutput.get("brightness2").getAsDouble() == 72.0,
                "color snapshot serializes its child values");
        check(Arrays.equals(liveColor, color.getComponentValuesSnapshot()) && Vape.INSTANCE.saves == saves,
                "color snapshot serialization restores live channels without saving");
        ColorValue colorProxy = (ColorValue)colorSnapshot.createProxyValue();
        check(Arrays.equals(colorSnapshot.getValue(), colorProxy.getComponentValuesSnapshot()), "color proxy starts with preview channels");
        check(Arrays.equals(liveColor, color.getComponentValuesSnapshot()) && Vape.INSTANCE.saves == saves,
                "creating color proxy does not change live channels or save");
        colorProxy.getHueValue().setValue(57.0);
        check(((Double)colorSnapshot.getValue()[0]) == 57.0 && Arrays.equals(liveColor, color.getComponentValuesSnapshot()),
                "editing color proxy updates only the snapshot");

        EntityTargetFilterValue targets = new EntityTargetFilterValue(null, "Targets");
        targets.setFilterValues(new Boolean[]{true, true, false, false, true, false, false});
        Boolean[] liveTargets = targets.getFilterValueSnapshot();
        ValueSnapshot<EntityTargetFilterValue, Boolean[]> targetsSnapshot = new ValueSnapshot<>(targets);
        JsonObject targetsJson = new JsonObject();
        targetsJson.addProperty("id", "Targets");
        targetsJson.addProperty("value", "0101001");
        saves = Vape.INSTANCE.saves;
        targetsSnapshot.loadJson(targetsJson);
        check(Arrays.equals(liveTargets, targets.getFilterValueSnapshot()), "target profile preview restores live filters");
        check(Vape.INSTANCE.saves == saves, "target profile preview does not save child values");
        check(Arrays.equals(new Boolean[]{false, true, false, true, false, false, true}, targetsSnapshot.getValue()),
                "target snapshot captures profile filters");
        JsonObject targetsOutput = targetsSnapshot.toJson();
        check("0101001".equals(targetsOutput.get("value").getAsString()), "target snapshot serializes its child values");
        check(Arrays.equals(liveTargets, targets.getFilterValueSnapshot()) && Vape.INSTANCE.saves == saves,
                "target snapshot serialization restores live filters without saving");
        EntityTargetFilterValue targetsProxy = (EntityTargetFilterValue)targetsSnapshot.createProxyValue();
        check(Arrays.equals(targetsSnapshot.getValue(), targetsProxy.getFilterValueSnapshot()), "target proxy starts with preview filters");
        check(Arrays.equals(liveTargets, targets.getFilterValueSnapshot()) && Vape.INSTANCE.saves == saves,
                "creating target proxy does not change live filters or save");
        targetsProxy.getPlayersValue().setValue(true);
        check(targetsSnapshot.getValue()[0] && Arrays.equals(liveTargets, targets.getFilterValueSnapshot()),
                "editing target proxy updates only the snapshot");

        saves = Vape.INSTANCE.saves;
        StringMapValue mapProxy = (StringMapValue)snapshot.createProxyValue();
        check(mapProxy.getValue().equals(snapshot.getValue()) && Vape.INSTANCE.saves == saves, "map proxy initializes without saving");
        mapProxy.putEntry("new", "preview");
        check(snapshot.getValue().containsKey("new") && !live.getValue().containsKey("new"), "map proxy edits the snapshot only");

        Mod module = new Mod("Profile test");
        module.values.add(live);
        module.values.add(mode);
        module.values.add(encoded);
        JsonObject moduleJson = new JsonObject();
        JsonArray values = new JsonArray();
        values.add(mapJson);
        values.add(modeJson);
        values.add(encodedJson);
        moduleJson.add("values", values);
        ProfileModuleSnapshot profile = new ProfileModuleSnapshot(new ProfileSnapshot(), module, moduleJson);
        check(profile.getValueSnapshots().size() == 3, "profile snapshots all definitions");
        check(((Map<?, ?>)profile.getValueSnapshots().get(0).getValue()).equals(map.getValue()), "profile map decoded");
        check(profile.getValueSnapshots().get(1).getValue() == second, "profile mode decoded");
        check("".equals(profile.getValueSnapshots().get(2).getValue()), "profile empty text decoded");
        check(live.getValue() == liveReference && mode.getValue() == first && "".equals(encoded.getValue()),
                "profile construction does not replace live values");
        saves = Vape.INSTANCE.saves;
        JsonObject profileOutput = profile.toJson();
        check(profileOutput != null && profileOutput.getAsJsonArray("values").size() == 3, "profile serializes changed values");
        check(Vape.INSTANCE.saves == saves && live.getValue() == liveReference && mode.getValue() == first,
                "profile serialization is isolated from live state and saving");
        System.out.println("Value/profile serialization and snapshot checks passed (real Gson and values, headless module doubles)");
    }
}
