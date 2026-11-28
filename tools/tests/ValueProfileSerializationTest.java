import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import gg.vape.Vape;
import gg.vape.config.ConfigJsonUtils;
import gg.vape.config.Profile;
import gg.vape.config.ProfileDataRetention;
import gg.vape.config.ProfileModuleSnapshot;
import gg.vape.config.ProfileSnapshot;
import gg.vape.module.Mod;
import gg.vape.module.blatant.antibot.AntiBotBooleanValue;
import gg.vape.module.blatant.antibot.AntiBotModeValue;
import gg.vape.module.utility.inventory.HotbarSlotRule;
import gg.vape.module.utility.inventory.HotbarSlotRuleAddButton;
import gg.vape.module.utility.inventory.HotbarSlotRuleGroupComponent;
import gg.vape.module.utility.inventory.HotbarSlotRuleEditorComponent;
import gg.vape.module.utility.inventory.HotbarSlotRuleValue;
import gg.vape.module.utility.inventory.cleaner.InventoryCleanerProfile;
import gg.vape.module.utility.inventory.cleaner.InventoryCleanerProfileValue;
import gg.vape.input.BindActivationMode;
import gg.vape.input.BindSet;
import gg.vape.ui.click.component.SquareIconButtonComponent;
import gg.vape.unmap.ItemLimitData;
import gg.vape.unmap.ModeSelection;
import gg.vape.unmap.ModeOption;
import gg.vape.ui.click.component.GuiComponent;
import gg.vape.ui.click.component.value.ColorValueDropdownComponent;
import gg.vape.ui.click.component.value.ColorPaletteSliderComponent;
import gg.vape.value.BindValue;
import gg.vape.value.BooleanValue;
import gg.vape.value.ColorValue;
import gg.vape.value.EntityTargetFilterValue;
import gg.vape.value.LimitValue;
import gg.vape.value.ModeValue;
import gg.vape.value.NumberValue;
import gg.vape.value.RandomValue;
import gg.vape.value.RandomClickDelayValue;
import gg.vape.value.OptionalLimitEntry;
import gg.vape.value.OptionalLimitValue;
import gg.vape.value.StringMapValue;
import gg.vape.value.StringValue;
import gg.vape.value.Value;
import gg.vape.value.ValueSnapshot;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
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

    private static final class PublicationValue extends StringValue {
        PublicationValue() { super(null, "Publish codec", "default"); }
        @Override public PublicationValue copyValueDefinition() { return new PublicationValue(); }
        @Override public JsonObject toJson(boolean forPublication) {
            JsonObject result = super.toJson(forPublication);
            if (forPublication) result.addProperty("embedded", true);
            return result;
        }
    }

    private static JsonObject findValue(JsonArray values, String id) {
        for (com.google.gson.JsonElement element : values) {
            if (element.isJsonObject() && element.getAsJsonObject().has("id")
                    && id.equals(element.getAsJsonObject().get("id").getAsString())) return element.getAsJsonObject();
        }
        return null;
    }

    private static JsonObject findModule(JsonArray modules, String name) {
        for (com.google.gson.JsonElement element : modules) {
            if (element.isJsonObject() && element.getAsJsonObject().has("name")
                    && name.equals(element.getAsJsonObject().get("name").getAsString())) return element.getAsJsonObject();
        }
        return null;
    }

    private static JsonObject hotbarJson(int firstItem) {
        JsonArray slots = new JsonArray();
        for (int i = 0; i < 9; ++i) {
            JsonObject slot = new JsonObject();
            slot.addProperty("item-id", i == 0 ? firstItem : 0);
            slots.add(slot);
        }
        JsonObject panel = new JsonObject();
        panel.add("hotbars", slots);
        JsonArray panels = new JsonArray();
        panels.add(panel);
        JsonObject result = new JsonObject();
        result.addProperty("id", "hotbar");
        result.addProperty("selected", 0);
        result.add("panels", panels);
        return result;
    }

    public static void main(String[] args) throws Exception {
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
        JsonObject mapJson = new JsonParser().parse(map.toJson(false).toString()).getAsJsonObject();
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
        check(previewJson.getAsJsonObject("data").entrySet().size() == 1, "preview serializes its own map");
        live.setResettable(false);
        ValueSnapshot<StringMapValue, Map<String, String>> nonResettable = new ValueSnapshot<>(live);
        check(nonResettable.getValue().equals(live.getValue()) && nonResettable.getValue() != live.getValue(),
                "omitted non-resettable settings retain an independent copy of live state");
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

        StringMapValue resettableMap = new StringMapValue(null, "Mapping", "Key", "Value");
        Map<String, String> mapDefaults = new LinkedHashMap<>();
        mapDefaults.put("default", "retained");
        resettableMap.setDefaultValue(mapDefaults);
        resettableMap.putEntry("live", "retained");
        Map<String, String> resettableLive = resettableMap.getValue();
        ValueSnapshot<StringMapValue, Map<String, String>> resettableSnapshot = new ValueSnapshot<>(resettableMap);
        resettableSnapshot.loadJson(mapJson);
        check(resettableMap.getDefaultValue().equals(mapDefaults) && mapDefaults.size() == 1,
                "resettable preview does not mutate map defaults");
        check(resettableMap.getValue() == resettableLive && resettableLive.containsKey("live"),
                "resettable preview restores live map");
        resettableMap.reset();
        check(resettableMap.getValue().equals(mapDefaults) && resettableMap.getValue() != mapDefaults,
                "map reset restores an independent copy of nonempty defaults");
        resettableMap.putEntry("new", "entry");
        check(mapDefaults.size() == 1, "mutating a reset map does not mutate defaults");

        ThrowingStringValue failing = new ThrowingStringValue();
        failing.setValue("live");
        int[] unsupportedChanges = {0};
        failing.addChangeListener(ignored -> ++unsupportedChanges[0]);
        ValueSnapshot<ThrowingStringValue, String> failingSnapshot = new ValueSnapshot<>(failing);
        JsonObject failureJson = new JsonObject();
        failureJson.addProperty("id", "throwing");
        failureJson.addProperty("value", "other");
        failureJson.addProperty("custom", "opaque");
        failing.throwOnRead = true;
        failing.throwOnWrite = true;
        failingSnapshot.loadJson(failureJson);
        check(failingSnapshot.createProxyValue() == null && failureJson.equals(failingSnapshot.toJson())
                        && !failingSnapshot.isDefault() && unsupportedChanges[0] == 0
                        && "live".equals(failing.getValue()) && !failing.isPersistenceSuppressed(),
                "unknown subclass stays opaque without invoking its codec, listeners or live setters");
        failingSnapshot.resetToDefault();
        check(failingSnapshot.isDefault() && failingSnapshot.toJson().entrySet().size() == 1,
                "resetting an opaque snapshot explicitly discards its preserved data");

        NumberValue number = NumberValue.create(null, "Steps", "#.#", "ticks", 0.0, 1.0, 10.0, 2.0);
        number.setRawValue(3.3);
        int[] numberChanges = {0};
        number.addChangeListener(ignored -> ++numberChanges[0]);
        ValueSnapshot<NumberValue, Double> numberSnapshot = new ValueSnapshot<>(number);
        JsonObject numberJson = new JsonObject();
        numberJson.addProperty("id", "Steps");
        numberJson.addProperty("value", 4.3);
        saves = Vape.INSTANCE.saves;
        numberSnapshot.loadJson(numberJson);
        check(numberSnapshot.getValue() == 4.3 && number.getValue() == 3.3 && numberChanges[0] == 0,
                "numeric preview never notifies or temporarily overwrites the live value");
        NumberValue numberProxy = (NumberValue)numberSnapshot.createProxyValue();
        check(numberProxy.getIncrement() == 2.0 && numberSnapshot.getValue() == 4.3,
                "numeric proxy preserves its increment without rounding on initialization");
        numberProxy.setValue(5.2);
        check(numberSnapshot.getValue() == 6.0 && number.getValue() == 3.3 && numberChanges[0] == 0
                        && Vape.INSTANCE.saves == saves, "numeric editor changes only its snapshot");
        numberSnapshot.toJson();
        check(numberChanges[0] == 0 && Vape.INSTANCE.saves == saves, "numeric serialization does not notify live listeners");

        RandomValue range = RandomValue.createWithIncrement(null, "Range", "#.#", "ms", 0.0, 2.0, 8.0, 20.0, 2.0);
        ValueSnapshot<RandomValue, double[]> rangeSnapshot = new ValueSnapshot<>(range);
        RandomValue rangeProxy = (RandomValue)rangeSnapshot.createProxyValue();
        check(rangeProxy.getIncrement() == 2.0 && " ms".equals(rangeProxy.getSuffix()),
                "range proxy preserves the increment and suffix");
        rangeProxy.setClampedMinimumValue(3.4);
        check(rangeSnapshot.getValue()[0] == 4.0 && range.getMinimumValue() == 2.0,
                "range proxy applies the real increment without modifying the live range");
        RandomClickDelayValue clickRate = RandomClickDelayValue.createWithIncrement(null, "Clicks", "#.#", "cps", 1, 6, 13, 20, 0.5);
        clickRate.setRange(new double[]{7, 12});
        int[] clickChanges = {0}; clickRate.addChangeListener(ignored -> ++clickChanges[0]);
        ValueSnapshot<RandomClickDelayValue, double[]> clickSnapshot = new ValueSnapshot<>(clickRate);
        JsonObject clickJson = new JsonParser().parse("{\"id\":\"Clicks\",\"minimum\":8,\"maximum\":10}").getAsJsonObject();
        saves = Vape.INSTANCE.saves;
        clickSnapshot.loadJson(clickJson);
        check(clickSnapshot.createProxyValue() instanceof RandomClickDelayValue
                        && clickSnapshot.getValue()[0] == 8 && clickSnapshot.getValue()[1] == 10
                        && clickSnapshot.toJson().get("maximum").getAsDouble() == 10
                        && clickRate.getMinimumValue() == 7 && clickChanges[0] == 0 && Vape.INSTANCE.saves == saves,
                "click-rate subclass has a detached codec with no live listener side effects");

        AntiBotBooleanValue botColor = new AntiBotBooleanValue(null, "Bot Color", 0x123456);
        botColor.setColor(Color.GREEN);
        ValueSnapshot<AntiBotBooleanValue, Integer> botSnapshot = new ValueSnapshot<>(botColor);
        saves = Vape.INSTANCE.saves;
        botSnapshot.createProxyValue();
        botSnapshot.toJson();
        check(Vape.INSTANCE.saves == saves && botColor.getValue() == (Color.GREEN.getRGB() & 0xffffff),
                "antibot color preview constructs without saving or changing the live color");

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
        mode.setValue(second);
        check(mode.copyValueDefinition().getDefaultValue() == first, "mode definition retains its original default");
        mode.setValue(first);
        ModeSelection botFirst = new ModeSelection("Normal"), botSecond = new ModeSelection("Color");
        AntiBotModeValue botMode = AntiBotModeValue.create(null, "Bot Mode", "Mode", botFirst, botFirst, botSecond);
        ValueSnapshot<ModeValue, ModeSelection> botModeSnapshot = new ValueSnapshot<>(botMode);
        JsonObject botModeJson = new JsonObject(); botModeJson.addProperty("id", "Bot Mode"); botModeJson.addProperty("value", "Color");
        botModeSnapshot.loadJson(botModeJson);
        AntiBotModeValue botModeProxy = (AntiBotModeValue)botModeSnapshot.createProxyValue();
        saves = Vape.INSTANCE.saves;
        ColorValueDropdownComponent botEditor = new ColorValueDropdownComponent(botModeProxy);
        botEditor.u();
        check(botModeProxy.getValue() == botSecond && botModeProxy.isPersistenceSuppressed() && Vape.INSTANCE.saves == saves,
                "specialized color mode initializes from the snapshot without saving or losing suppression");
        java.lang.reflect.Field sliderField = ColorValueDropdownComponent.class.getDeclaredField("colorSlider");
        sliderField.setAccessible(true);
        ColorPaletteSliderComponent botSlider = (ColorPaletteSliderComponent)sliderField.get(botEditor);
        botSlider.selectPaletteIndex(0);
        botSlider.synchronizeSelectionFromValue();
        botEditor.u();
        check(botModeSnapshot.getValue() == botFirst && botMode.getValue() == botFirst
                        && botModeProxy.isPersistenceSuppressed() && Vape.INSTANCE.saves == saves,
                "specialized color slider changes only its detached mode and never schedules a save");
        botMode.setValue(botSecond);
        saves = Vape.INSTANCE.saves;
        ColorValueDropdownComponent liveBotEditor = new ColorValueDropdownComponent(botMode);
        liveBotEditor.u();
        ColorPaletteSliderComponent liveBotSlider = (ColorPaletteSliderComponent)sliderField.get(liveBotEditor);
        liveBotSlider.selectPaletteIndex(0);
        liveBotSlider.synchronizeSelectionFromValue();
        liveBotEditor.u();
        check(botMode.getValue() == botFirst && Vape.INSTANCE.saves > saves,
                "specialized color slider saves actual live selection changes");

        BindSet liveBinding = new BindSet(Arrays.asList(7), false, true);
        BindValue bind = new BindValue(null, "Bind", liveBinding);
        liveBinding.setBoundInputs(Arrays.asList(42));
        liveBinding.setActivationMode(BindActivationMode.HELD);
        ValueSnapshot<BindValue, BindSet> bindSnapshot = new ValueSnapshot<>(bind);
        check(bindSnapshot.getValue() != liveBinding && bindSnapshot.getValue().getBoundInputs().equals(Arrays.asList(7)),
                "binding snapshot starts with an independent default");
        JsonObject bindJson = new JsonObject();
        bindJson.addProperty("id", "Bind");
        JsonArray bindCodes = new JsonArray();
        bindCodes.add(new JsonPrimitive(9));
        bindJson.add("binds", bindCodes);
        saves = Vape.INSTANCE.saves;
        bindSnapshot.loadJson(bindJson);
        check(bindSnapshot.getValue().getBoundInputs().equals(Arrays.asList(9)), "binding snapshot loads profile input");
        check(bind.getValue() == liveBinding && liveBinding.getBoundInputs().equals(Arrays.asList(42))
                        && liveBinding.getActivationMode() == BindActivationMode.HELD && Vape.INSTANCE.saves == saves,
                "binding profile preview leaves the live binding unchanged");
        check(bindSnapshot.toJson().getAsJsonArray("binds").get(0).getAsInt() == 9
                        && liveBinding.getBoundInputs().equals(Arrays.asList(42)),
                "binding snapshot serializes its own inputs");
        BindValue bindProxy = (BindValue)bindSnapshot.createProxyValue();
        bindProxy.getValue().setBoundInputs(Arrays.asList(11));
        check(bindSnapshot.getValue().getBoundInputs().equals(Arrays.asList(11))
                        && liveBinding.getBoundInputs().equals(Arrays.asList(42)),
                "binding proxy edits only its snapshot");
        BindSet boundEditorObject = bindProxy.getValue();
        bindProxy.reset();
        check(bindSnapshot.getValue() == boundEditorObject && boundEditorObject.getBoundInputs().equals(Arrays.asList(7)),
                "binding reset keeps the object already held by its input control");

        OptionalLimitValue names = OptionalLimitValue.create(null, "Names", "Names", Color.GRAY, "default");
        List<OptionalLimitEntry> liveNames = names.getValue();
        liveNames.get(0).setEnabled(false);
        names.addEntry("live");
        check(names.getDefaultValue().get(0).isEnabled(), "list defaults are independent of live entries");
        ValueSnapshot<OptionalLimitValue, List<OptionalLimitEntry>> namesSnapshot = new ValueSnapshot<>(names);
        check(namesSnapshot.isDefault() && namesSnapshot.getValue().get(0).isEnabled(), "list snapshot starts at independent defaults");
        JsonObject namesJson = new JsonObject();
        namesJson.addProperty("id", "Names");
        JsonArray namesData = new JsonArray();
        JsonObject emptyName = new JsonObject();
        emptyName.addProperty("item-id", "b64:");
        emptyName.addProperty("enabled", false);
        namesData.add(emptyName);
        namesJson.add("value", namesData);
        saves = Vape.INSTANCE.saves;
        namesSnapshot.loadJson(namesJson);
        check(namesSnapshot.getValue().size() == 1 && "".equals(namesSnapshot.getValue().get(0).getValue())
                        && !namesSnapshot.getValue().get(0).isEnabled() && !namesSnapshot.isDefault(),
                "list snapshot loads empty disabled entry");
        check(names.getValue() == liveNames && liveNames.size() == 2 && !liveNames.get(0).isEnabled()
                        && names.getDefaultValue().get(0).isEnabled() && Vape.INSTANCE.saves == saves,
                "list preview leaves the live and default entries unchanged");
        JsonObject namesOutput = namesSnapshot.toJson();
        check(!namesOutput.getAsJsonArray("value").get(0).getAsJsonObject().get("enabled").getAsBoolean(),
                "list snapshot serializes entry flags");
        OptionalLimitValue namesProxy = (OptionalLimitValue)namesSnapshot.createProxyValue();
        check(namesProxy.getValue().size() == 1 && !namesProxy.getValue().get(0).isEnabled()
                        && Vape.INSTANCE.saves == saves, "list proxy starts with snapshot state without saving");
        namesProxy.addEntry("preview");
        check(namesSnapshot.getValue().size() == 2 && liveNames.size() == 2, "list proxy edits only its snapshot");
        List<OptionalLimitEntry> editorList = namesProxy.getValue();
        namesProxy.reset();
        check(namesSnapshot.getValue() == editorList && editorList.size() == 1 && "default".equals(editorList.get(0).getValue()),
                "list reset keeps the collection already held by its editor");

        LimitValue items = LimitValue.createInternal(null, "Items", "Items", Color.GRAY, 4, new ArrayList<ItemLimitData>());
        ItemLimitData liveItem = items.addEntry("live", 1);
        List<ItemLimitData> liveItems = items.getValue();
        ValueSnapshot<LimitValue, List<ItemLimitData>> itemsSnapshot = new ValueSnapshot<>(items);
        JsonObject itemsJson = new JsonObject();
        itemsJson.addProperty("id", "Items");
        JsonArray itemsData = new JsonArray();
        ItemLimitData profileItem = new ItemLimitData("stone:2", 3);
        profileItem.setEnabled(false);
        itemsData.add(profileItem.toJson());
        itemsJson.add("value", itemsData);
        saves = Vape.INSTANCE.saves;
        itemsSnapshot.loadJson(itemsJson);
        check(itemsSnapshot.getValue().size() == 1 && itemsSnapshot.getValue().get(0).getMetadata() == 2
                        && itemsSnapshot.getValue().get(0).getTotalStacks() == 3 && !itemsSnapshot.getValue().get(0).isEnabled(),
                "item-list snapshot preserves metadata, quantity and enabled state");
        check(items.getValue() == liveItems && liveItems.size() == 1 && liveItems.get(0) == liveItem && Vape.INSTANCE.saves == saves,
                "item-list preview leaves live entries intact");
        LimitValue itemsProxy = (LimitValue)itemsSnapshot.createProxyValue();
        check(itemsProxy.getValue().size() == 1 && !itemsProxy.getValue().get(0).isEnabled(), "item-list proxy retains profile entries");
        itemsProxy.getValue().get(0).setEnabled(true);
        check(itemsSnapshot.getValue().get(0).isEnabled() && liveItem.isEnabled(), "item-list proxy edits only snapshot data");

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
        int[] liveColorChanges = {0};
        color.getHueValue().addChangeListener(ignored -> ++liveColorChanges[0]);
        colorSnapshot.loadJson(colorJson);
        check(Arrays.equals(liveColor, color.getComponentValuesSnapshot()) && liveColorChanges[0] == 0,
                "color profile preview never changes or notifies live channels");
        check(Vape.INSTANCE.saves == saves, "color profile preview does not save child values");
        check(((Double)colorSnapshot.getValue()[0]) == 42.0 && ((Double)colorSnapshot.getValue()[1]) == 72.0,
                "color snapshot captures profile channels");
        JsonObject colorOutput = colorSnapshot.toJson();
        check(colorOutput.get("hue2").getAsDouble() == 42.0 && colorOutput.get("brightness2").getAsDouble() == 72.0,
                "color snapshot serializes its child values");
        check(Arrays.equals(liveColor, color.getComponentValuesSnapshot()) && liveColorChanges[0] == 0 && Vape.INSTANCE.saves == saves,
                "color snapshot serialization never changes or notifies live channels");
        ColorValue colorProxy = (ColorValue)colorSnapshot.createProxyValue();
        check(Arrays.equals(colorSnapshot.getValue(), colorProxy.getComponentValuesSnapshot()), "color proxy starts with preview channels");
        check(Arrays.equals(liveColor, color.getComponentValuesSnapshot()) && Vape.INSTANCE.saves == saves,
                "creating color proxy does not change live channels or save");
        colorProxy.getHueValue().setValue(57.0);
        check(((Double)colorSnapshot.getValue()[0]) == 57.0 && Arrays.equals(liveColor, color.getComponentValuesSnapshot())
                        && Vape.INSTANCE.saves == saves, "editing color proxy updates only the snapshot without saving");
        colorProxy.applyConfiguredColorTransform();
        check(colorProxy.isPersistenceSuppressed() && colorProxy.getHueValue().isPersistenceSuppressed(),
                "nested color transforms retain preview suppression");

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
        check(targetsSnapshot.getValue()[0] && Arrays.equals(liveTargets, targets.getFilterValueSnapshot())
                        && Vape.INSTANCE.saves == saves, "editing target proxy updates only the snapshot without saving");

        saves = Vape.INSTANCE.saves;
        StringMapValue mapProxy = (StringMapValue)snapshot.createProxyValue();
        check(mapProxy.getValue().equals(snapshot.getValue()) && Vape.INSTANCE.saves == saves, "map proxy initializes without saving");
        mapProxy.putEntry("new", "preview");
        check(snapshot.getValue().containsKey("new") && !live.getValue().containsKey("new"), "map proxy edits the snapshot only");
        mapProxy.reset();
        snapshot.resetToDefault();
        check(snapshot.getValue().containsKey("new"), "non-resettable snapshots remain unchanged by reset");

        HotbarSlotRuleValue hotbar = HotbarSlotRuleValue.create(null, "hotbar");
        JsonObject liveHotbar = hotbarJson(1);
        check(hotbar.loadJson(liveHotbar) && !hotbar.isDefault(), "hotbar data is retained before its editor exists");
        HotbarSlotRuleEditorComponent hotbarEditor = new HotbarSlotRuleEditorComponent(hotbar);
        check(!hotbar.isDefault() && hotbar.toJson(false).equals(liveHotbar), "pending hotbar data attaches to its editor");
        ValueSnapshot<HotbarSlotRuleValue, List<HotbarSlotRule>> hotbarSnapshot = new ValueSnapshot<>(hotbar);
        JsonObject otherHotbar = hotbarJson(2);
        otherHotbar.addProperty("futureTop", "preserve");
        JsonObject previewPanel = otherHotbar.getAsJsonArray("panels").get(0).getAsJsonObject();
        previewPanel.addProperty("futurePanel", 7);
        previewPanel.getAsJsonArray("hotbars").get(0).getAsJsonObject().addProperty("futureSlot", true);
        saves = Vape.INSTANCE.saves;
        hotbarSnapshot.loadJson(otherHotbar);
        check(!hotbarSnapshot.isDefault() && hotbarSnapshot.toJson().equals(otherHotbar), "hotbar snapshot preserves profile payload");
        HotbarSlotRuleValue hotbarProxy = (HotbarSlotRuleValue)hotbarSnapshot.createProxyValue();
        HotbarSlotRuleEditorComponent previewEditor = new HotbarSlotRuleEditorComponent(hotbarProxy);
        check(hotbarProxy != hotbar && previewEditor != hotbarEditor && hotbarSnapshot.toJson().equals(otherHotbar),
                "hotbar profile editor is detached and retains unknown top-level, panel and slot data");
        previewEditor.getSelectedGroup().getRules().set(0, new HotbarSlotRule(42));
        previewEditor.getSelectedGroup().notifyRulesChanged();
        JsonObject editedHotbar = hotbarSnapshot.toJson();
        JsonObject editedPanel = editedHotbar.getAsJsonArray("panels").get(0).getAsJsonObject();
        JsonObject editedSlot = editedPanel.getAsJsonArray("hotbars").get(0).getAsJsonObject();
        check(editedSlot.get("item-id").getAsInt() == 42 && editedSlot.get("futureSlot").getAsBoolean()
                        && editedPanel.get("futurePanel").getAsInt() == 7 && editedHotbar.has("futureTop")
                        && hotbar.toJson(false).equals(liveHotbar) && Vape.INSTANCE.saves == saves,
                "hotbar preview edits preserve unknown fields without changing or saving the live editor");
        hotbarSnapshot.resetToDefault();
        check(hotbarSnapshot.isDefault() && previewEditor.getGroups().isEmpty() && !hotbarSnapshot.toJson().has("futureTop")
                        && hotbar.toJson(false).equals(liveHotbar),
                "resetting a hotbar snapshot clears its editor and opaque fields, not the live editor");
        JsonObject malformedHotbar = hotbarJson(3);
        malformedHotbar.getAsJsonArray("panels").get(0).getAsJsonObject().remove("hotbars");
        hotbarSnapshot.loadJson(malformedHotbar);
        java.lang.reflect.Field addField = HotbarSlotRuleEditorComponent.class.getDeclaredField("addGroupButton");
        addField.setAccessible(true);
        check(hotbarSnapshot.toJson().equals(malformedHotbar) && !hotbarSnapshot.isDefault()
                        && !previewEditor.f().contains(addField.get(previewEditor)),
                "malformed hotbar data remains opaque and editing is blocked until reset");
        hotbarSnapshot.resetToDefault();
        check(hotbarSnapshot.isDefault() && previewEditor.f().contains(addField.get(previewEditor)),
                "resetting malformed hotbar data re-enables the detached editor");
        hotbarEditor.loadJson(malformedHotbar);
        check(hotbar.toJson(false).equals(liveHotbar), "malformed hotbar payload leaves existing groups intact");
        hotbar.setResettable(false);
        ValueSnapshot<HotbarSlotRuleValue, List<HotbarSlotRule>> fixedHotbar = new ValueSnapshot<>(hotbar);
        HotbarSlotRuleValue fixedProxy = (HotbarSlotRuleValue)fixedHotbar.createProxyValue();
        HotbarSlotRuleEditorComponent fixedEditor = new HotbarSlotRuleEditorComponent(fixedProxy);
        fixedHotbar.resetToDefault();
        check(fixedEditor.getGroups().size() == 1 && fixedHotbar.toJson().equals(liveHotbar),
                "non-resettable hotbar previews retain independent live data");
        hotbar.setResettable(true);
        ((HotbarSlotRuleAddButton)addField.get(hotbarEditor)).click();
        check(hotbarEditor.getGroups().size() == 2 && Vape.INSTANCE.saves > saves, "adding hotbar group schedules a save");
        HotbarSlotRuleGroupComponent secondGroup = hotbarEditor.getGroups().get(1);
        hotbarEditor.selectGroup(secondGroup);
        hotbarEditor.rebuildChildren();
        hotbarEditor.rebuildChildren();
        java.lang.reflect.Field closeField = HotbarSlotRuleGroupComponent.class.getDeclaredField("closeButton");
        closeField.setAccessible(true);
        ((SquareIconButtonComponent)closeField.get(secondGroup)).dispatchPrimaryClick();
        check(hotbarEditor.getGroups().size() == 1 && hotbarEditor.getSelectedGroup() == hotbarEditor.getGroups().get(0),
                "removing the selected hotbar group selects a remaining group");
        hotbar.reset();
        check(hotbar.isDefault() && hotbarEditor.getSelectedGroup() == null, "switching profiles clears live hotbar groups");

        InventoryCleanerProfileValue inventories = InventoryCleanerProfileValue.create(null, "Inventory");
        InventoryCleanerProfile liveInventory = new InventoryCleanerProfile(new JsonParser().parse("{\"name\":\"Live\"}").getAsJsonObject());
        inventories.addProfile(liveInventory);
        inventories.setValue(liveInventory);
        ValueSnapshot<InventoryCleanerProfileValue, InventoryCleanerProfile> inventorySnapshot = new ValueSnapshot<>(inventories);
        JsonObject inventoryJson = new JsonParser().parse("{\"id\":\"Inventory\",\"selected\":\"Preview\",\"futureValue\":42,\"inventories\":[{\"name\":\"Preview\",\"futureInventory\":\"keep\"}]}").getAsJsonObject();
        saves = Vape.INSTANCE.saves;
        int registrations = Vape.INSTANCE.values.registrations;
        int modeRegistrations = ModeSelection.selectionsByMode.size();
        inventorySnapshot.loadJson(inventoryJson);
        check(!inventorySnapshot.isDefault() && "Preview".equals(inventorySnapshot.getValue().getName()), "inventory snapshot loads its own selection");
        check(inventories.getValue() == liveInventory && inventories.getProfiles().size() == 1 && Vape.INSTANCE.saves == saves
                        && Vape.INSTANCE.values.registrations == registrations && ModeSelection.selectionsByMode.size() == modeRegistrations,
                "inventory profile preview preserves live profiles without saving or registering global modes");
        InventoryCleanerProfileValue inventoryProxy = (InventoryCleanerProfileValue)inventorySnapshot.createProxyValue();
        check(inventoryProxy.getValue().isDetachedPreview(), "inventory preview carries its detached editing context");
        inventoryProxy.getValue().armorMode.parse("Best armor");
        check(inventoryProxy.getValue().bestArmor.isSelected(), "unregistered embedded mode still parses and selects armor");
        inventoryProxy.getValue().setName("Edited preview");
        check("Edited preview".equals(inventorySnapshot.toJson().get("selected").getAsString())
                        && inventorySnapshot.toJson().get("futureValue").getAsInt() == 42
                        && "keep".equals(inventorySnapshot.toJson().getAsJsonArray("inventories").get(0).getAsJsonObject().get("futureInventory").getAsString())
                        && "Live".equals(liveInventory.getName()) && Vape.INSTANCE.saves == saves,
                "inventory preview editor preserves unknown fields and mutates detached profiles only");
        JsonObject malformedInventory = new JsonParser().parse("{\"id\":\"Inventory\",\"inventories\":[{\"name\":\"Good\"},{}]}").getAsJsonObject();
        check(!inventoryProxy.loadJson(malformedInventory) && inventoryProxy.hasUnsupportedData()
                        && inventoryProxy.getProfiles().size() == 1 && "Edited preview".equals(inventoryProxy.getValue().getName())
                        && malformedInventory.equals(inventorySnapshot.toJson()) && !inventorySnapshot.isDefault(),
                "malformed inventory data stays opaque instead of partially replacing or erasing profiles");
        ValueSnapshot<InventoryCleanerProfileValue, InventoryCleanerProfile> malformedSnapshot = new ValueSnapshot<>(inventories);
        malformedSnapshot.loadJson(malformedInventory);
        check(malformedInventory.equals(malformedSnapshot.toJson()) && !malformedSnapshot.isDefault(),
                "constructing a fresh snapshot retains malformed inventory data for later application");
        JsonObject futureArmor = new JsonParser().parse("{\"id\":\"Inventory\",\"inventories\":[{\"name\":\"Future\",\"armor_mode\":\"New armor mode\"}]}").getAsJsonObject();
        malformedSnapshot.loadJson(futureArmor);
        check(futureArmor.equals(malformedSnapshot.toJson()), "an unknown armor mode is preserved rather than changed to a default");
        inventoryProxy.reset();
        check(!inventoryProxy.hasUnsupportedData() && inventoryProxy.isDefault(), "an explicit reset unblocks unsupported inventory data");
        check(inventoryProxy.loadJson(inventoryJson), "a valid inventory can be loaded after reset");
        InventoryCleanerProfile addedInventory = inventoryProxy.createProfile();
        inventoryProxy.addProfile(addedInventory);
        InventoryCleanerProfile anotherInventory = inventoryProxy.createProfile();
        check(!anotherInventory.getName().equalsIgnoreCase(addedInventory.getName()) && anotherInventory.isDetachedPreview(),
                "new detached inventories receive unique names and preserve preview isolation");
        inventorySnapshot.resetToDefault();
        check(inventorySnapshot.isDefault() && inventories.getValue() == liveInventory, "inventory snapshot reset is isolated");
        inventories.reset();
        check(inventories.isDefault() && inventories.getValue() == null, "inventory reset clears a null-default selection");

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
        ProfileModuleSnapshot profile = new ProfileModuleSnapshot(new ProfileSnapshot(null, null), module, moduleJson);
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

        Mod legacyModule = new Mod("Legacy profile");
        legacyModule.defaultKeybind = 5;
        StringValue renamed = StringValue.create(null, "New name", "default").addAlias("Old name");
        legacyModule.values.add(renamed);
        JsonObject legacyModuleJson = new JsonParser().parse("{\"name\":\"Legacy profile\",\"keybinds\":[3],\"futureField\":{\"keep\":true},"
                + "\"values\":[{\"id\":\"Old name\",\"value\":\"saved\"},{\"id\":\"Unrecognized setting\",\"value\":\"keep\"}]}").getAsJsonObject();
        ProfileModuleSnapshot legacyProfile = new ProfileModuleSnapshot(new ProfileSnapshot(null, null), legacyModule, legacyModuleJson);
        check(legacyProfile.getBindSnapshot().getValue().getBoundInputs().equals(Arrays.asList(1003)),
                "legacy profile keys use the legacy conversion path");
        check("saved".equals(legacyProfile.getValueSnapshots().get(0).getValue()), "profile aliases resolve case-insensitively");
        JsonObject legacyOutput = legacyProfile.toJson();
        check(legacyOutput.getAsJsonObject("futureField").get("keep").getAsBoolean()
                        && findValue(legacyOutput.getAsJsonArray("values"), "Unrecognized setting") != null
                        && "saved".equals(findValue(legacyOutput.getAsJsonArray("values"), "New name").get("value").getAsString()),
                "unknown fields and settings survive profile preview while known aliases are normalized");
        legacyProfile.getBindSnapshot().getValue().setBoundInputs(new ArrayList<Integer>());
        check(legacyProfile.toJson().getAsJsonArray("keybinds_2").size() == 0,
                "clearing a default module key writes an explicit empty binding");
        legacyProfile.resetBind();
        check(legacyProfile.getBindSnapshot().getValue().getBoundInputs().equals(Arrays.asList(5)),
                "resetting a module restores its default binding");
        ProfileModuleSnapshot defaultBinding = new ProfileModuleSnapshot(new ProfileSnapshot(null, null), legacyModule, null);
        check(defaultBinding.getBindSnapshot().getValue().getBoundInputs().equals(Arrays.asList(5))
                        && defaultBinding.toJson() == null, "absent profile binding retains the module default");
        JsonArray multiBinding = new JsonArray(); multiBinding.add(new JsonPrimitive(5)); multiBinding.add(new JsonPrimitive(6));
        check(!ConfigJsonUtils.isDefaultBinding(multiBinding, 0) && !ConfigJsonUtils.isDefaultBinding(multiBinding, 5),
                "multi-key bindings are never mistaken for defaults");
        Mod hiddenModule = new Mod("Hidden");
        JsonObject hiddenJson = new JsonParser().parse("{\"visible\":false}").getAsJsonObject();
        check(new ProfileModuleSnapshot(new ProfileSnapshot(null, null), hiddenModule, hiddenJson).hasChanges(),
                "visibility-only profile changes are listed");

        Vape.INSTANCE.mods.top.add(legacyModule);
        gg.vape.module.render.hud.HudModule hud = new gg.vape.module.render.hud.HudModule("HUD");
        hud.enabled = true;
        Vape.INSTANCE.mods.top.add(hud);
        Profile savedProfile = new Profile();
        JsonObject savedData = new JsonParser().parse("{\"enabled\":{\"Missing script\":true,\"legacy PROFILE\":true},\"legit_enabled\":{\"hud\":false},\"modules\":["
                + "{\"name\":\"legacy PROFILE\",\"visible\":false,\"values\":[{\"id\":\"Old name\",\"value\":\"saved\"}]},"
                + "{\"name\":\"Missing script\",\"values\":[{\"id\":\"custom\",\"value\":\"keep\"}]},\"opaque\"]}").getAsJsonObject();
        savedProfile.updateData(savedData);
        ProfileSnapshot fullSnapshot = new ProfileSnapshot(savedProfile, savedData.getAsJsonArray("modules"));
        ProfileModuleSnapshot legacySnapshot = null, hudSnapshot = null;
        for (ProfileModuleSnapshot candidate : fullSnapshot.getAllModules()) {
            if (candidate.getModule() == legacyModule) legacySnapshot = candidate;
            if (candidate.getModule() == hud) hudSnapshot = candidate;
        }
        check(fullSnapshot.getAllModules().size() == 2 && legacySnapshot != null
                        && "saved".equals(legacySnapshot.getValueSnapshots().get(0).getValue()) && legacySnapshot.isEnabled(),
                "profile modules and enabled states are matched without case sensitivity or live visibility");
        check(hudSnapshot != null && !hudSnapshot.isEnabled(), "HUD preview uses the target profile's explicit off state");
        hudSnapshot.setEnabled(true);
        fullSnapshot.applyToProfile();
        JsonArray savedModules = savedProfile.getData().getAsJsonArray("modules");
        check(findModule(savedModules, "Missing script") != null
                        && findModule(savedModules, "Missing script").getAsJsonArray("values").size() == 1
                        && savedModules.size() == 3 && savedProfile.getData().getAsJsonObject("enabled").get("Missing script").getAsBoolean()
                        && savedProfile.getData().getAsJsonObject("enabled").get("Legacy profile").getAsBoolean()
                        && savedProfile.getData().getAsJsonObject("legit_enabled").get("HUD").getAsBoolean(),
                "applying a preview preserves unavailable modules and writes edited normal and HUD states");
        saves = Vape.INSTANCE.saves;
        Vape.INSTANCE.profiles.active = savedProfile;
        Vape.INSTANCE.profiles.getProfiles().add(savedProfile);
        fullSnapshot.applyAndSave();
        check(savedProfile.dirty && Vape.INSTANCE.profiles.reloads == 1 && Vape.INSTANCE.profiles.reloadLocked
                        && Vape.INSTANCE.saves == saves + 1,
                "applying the active profile reloads it under the capture lock and schedules persistence");
        Vape.INSTANCE.profiles.active = null;
        savedProfile.dirty = false;
        fullSnapshot.applyAndSave();
        check(savedProfile.dirty && Vape.INSTANCE.profiles.reloads == 1 && Vape.INSTANCE.saves == saves + 2,
                "applying an inactive profile marks that profile dirty without reloading the active one");
        Vape.INSTANCE.mods.top.clear();

        Mod publicationModule = new Mod("Publication");
        PublicationValue publicationValue = new PublicationValue();
        publicationModule.values.add(publicationValue);
        Vape.INSTANCE.mods.top.add(publicationModule);
        Profile publicationProfile = new Profile();
        JsonObject publicationData = new JsonParser().parse("{\"modules\":[{\"name\":\"Publication\",\"values\":[{\"id\":\"Publish codec\",\"value\":\"edited\"}]}],\"privateRoot\":7}").getAsJsonObject();
        publicationProfile.updateData(publicationData);
        publicationProfile.setPublishedData(new JsonParser().parse("{\"publishedOnly\":\"keep\",\"modules\":[]}").getAsJsonObject());
        ProfileSnapshot publicationSnapshot = new ProfileSnapshot(publicationProfile, publicationData.getAsJsonArray("modules"));
        publicationSnapshot.applyToProfile();
        JsonObject localPublicationValue = findValue(findModule(publicationProfile.getData().getAsJsonArray("modules"), "Publication").getAsJsonArray("values"), "Publish codec");
        JsonObject publicPublicationValue = findValue(findModule(publicationProfile.getPublishedData().getAsJsonArray("modules"), "Publication").getAsJsonArray("values"), "Publish codec");
        check(!localPublicationValue.has("embedded") && publicPublicationValue.get("embedded").getAsBoolean()
                        && "keep".equals(publicationProfile.getPublishedData().get("publishedOnly").getAsString())
                        && !publicationProfile.getPublishedData().has("privateRoot"),
                "applying a snapshot writes the publication codec while preserving existing publication-only data");
        publicationSnapshot.getGuiBuilder().resetModule(publicationSnapshot.getAllModules().get(0));
        publicationSnapshot.applyToProfile();
        check(findModule(publicationProfile.getPublishedData().getAsJsonArray("modules"), "Publication") == null,
                "reset values are removed from the published snapshot as well");
        Vape.INSTANCE.mods.top.clear();
        Vape.INSTANCE.profiles.getProfiles().clear();

        StringValue globalKnown = StringValue.create(null, "Global", "default");
        JsonObject oldData = new JsonParser().parse("{\"modules\":[{\"name\":\"legacy PROFILE\",\"visible\":false,\"keybinds_2\":[9],"
                + "\"future\":{\"x\":1},\"values\":[{\"id\":\"Old name\",\"value\":\"old\"},{\"id\":\"Future setting\",\"value\":\"keep\"}]},"
                + "{\"name\":\"Missing script\",\"values\":[{\"id\":\"custom\",\"value\":\"keep\"}]}],"
                + "\"values\":[{\"id\":\"Global\",\"value\":\"old\"},{\"id\":\"Future global\",\"value\":\"keep\"}],"
                + "\"enabled\":{\"legacy PROFILE\":true,\"Missing script\":true},\"legit_enabled\":{\"Missing HUD\":false},\"futureRoot\":42}").getAsJsonObject();
        JsonObject capturedData = new JsonParser().parse("{\"modules\":[],\"values\":[],\"enabled\":{},\"legit_enabled\":{}}").getAsJsonObject();
        ProfileDataRetention.preserveUnknown(oldData, capturedData, Arrays.asList(legacyModule), Arrays.asList(legacyModule), Arrays.<Value<?, ?>>asList(globalKnown));
        JsonObject retainedKnown = findModule(capturedData.getAsJsonArray("modules"), "Legacy profile");
        check(retainedKnown != null && retainedKnown.has("future") && !retainedKnown.has("visible") && !retainedKnown.has("keybinds_2")
                        && findValue(retainedKnown.getAsJsonArray("values"), "Future setting") != null
                        && findValue(retainedKnown.getAsJsonArray("values"), "Old name") == null,
                "capture keeps unknown fields and values without resurrecting reset known settings");
        check(findModule(capturedData.getAsJsonArray("modules"), "Missing script") != null
                        && findValue(capturedData.getAsJsonArray("values"), "Future global") != null
                        && findValue(capturedData.getAsJsonArray("values"), "Global") == null
                        && capturedData.getAsJsonObject("enabled").get("Missing script").getAsBoolean()
                        && !capturedData.getAsJsonObject("enabled").has("legacy PROFILE")
                        && !capturedData.getAsJsonObject("legit_enabled").get("Missing HUD").getAsBoolean()
                        && capturedData.get("futureRoot").getAsInt() == 42,
                "capture preserves unavailable modules, unknown global data and states");
        JsonObject recaptured = new JsonParser().parse("{\"modules\":[],\"values\":[],\"enabled\":{},\"legit_enabled\":{}}").getAsJsonObject();
        ProfileDataRetention.preserveUnknown(capturedData, recaptured, Arrays.asList(legacyModule), Arrays.asList(legacyModule), Arrays.<Value<?, ?>>asList(globalKnown));
        check(capturedData.equals(recaptured), "repeated live captures do not duplicate retained unknown data");

        Mod conditionalModule = new Mod("Conditional");
        ModeOption optionA = new ModeOption("A"), optionB = new ModeOption("B");
        ModeValue selector = ModeValue.create(null, "Selector", optionA, optionA, optionB);
        NumberValue modeChild = NumberValue.create(null, "Mode child", "#", "", 0, 2, 10, 1);
        selector.addModeDependentValues(optionB, modeChild);
        BooleanValue gate = BooleanValue.create(null, "Gate", false);
        StringValue predicateChild = StringValue.create(null, "Predicate child", "default");
        gate.whenEqualTo(false).applyTo(predicateChild);
        BooleanValue compactParent = BooleanValue.create(null, "Compact", true);
        OptionalLimitValue compactChild = OptionalLimitValue.create(null, "Compact list", "Compact list", Color.GRAY, "initial");
        compactParent.addDependentValues(compactChild);
        compactParent.setCompactListValue(compactChild);
        conditionalModule.addValue(selector, modeChild, gate, predicateChild, compactParent, compactChild);
        Vape.INSTANCE.mods.top.add(conditionalModule);
        JsonArray conditionalJson = new JsonParser().parse("[{\"name\":\"Conditional\",\"values\":["
                + "{\"id\":\"Selector\",\"value\":\"B\"},{\"id\":\"Gate\",\"value\":\"true\"}]}]").getAsJsonArray();
        saves = Vape.INSTANCE.saves;
        ProfileSnapshot conditionalSnapshot = new ProfileSnapshot(null, conditionalJson);
        gg.vape.ui.click.frame.impl.profile.ProfileSnapshotGuiBuilder builder = conditionalSnapshot.getGuiBuilder();
        java.lang.reflect.Field proxiesField = builder.getClass().getDeclaredField("proxyBySourceValue");
        proxiesField.setAccessible(true);
        Map<Value<?, ?>, Value<?, ?>> proxies = (Map<Value<?, ?>, Value<?, ?>>)proxiesField.get(builder);
        ModeValue selectorProxy = (ModeValue)proxies.get(selector);
        NumberValue modeChildProxy = (NumberValue)proxies.get(modeChild);
        BooleanValue gateProxy = (BooleanValue)proxies.get(gate);
        StringValue predicateProxy = (StringValue)proxies.get(predicateChild);
        BooleanValue compactParentProxy = (BooleanValue)proxies.get(compactParent);
        OptionalLimitValue compactChildProxy = (OptionalLimitValue)proxies.get(compactChild);
        List<GuiComponent> conditionalRows = builder.getModuleComponents(conditionalSnapshot.getAllModules().get(0));
        check(selectorProxy.getValue() == optionB && modeChildProxy.areConditionsMet() && conditionalRows.get(1).V$src$Z$1xhop3l()
                        && selector.getValue() == optionA && optionB.getMode() == selector,
                "mode-dependent rows use the preview selection without reassigning live options");
        selectorProxy.setValue(optionA);
        check(!modeChildProxy.areConditionsMet() && !conditionalRows.get(1).V$src$Z$1xhop3l(),
                "switching the preview mode hides the dependent row and its wrapper");
        check(!predicateProxy.areConditionsMet() && !conditionalRows.get(3).V$src$Z$1xhop3l(),
                "predicate-based conditions initially use the preview value");
        gateProxy.setValue(false);
        check(predicateProxy.areConditionsMet() && conditionalRows.get(3).V$src$Z$1xhop3l() && !gate.getValue(),
                "predicate-based conditions follow detached changes only");
        check(compactParentProxy.getTerminalDependentValue() == compactChildProxy && conditionalRows.size() == 5,
                "compact lists are attached to their detached boolean without a duplicate row");
        List<OptionalLimitEntry> compactEditorEntries = compactChildProxy.getValue();
        compactChildProxy.addEntry("preview");
        builder.resetModule(conditionalSnapshot.getAllModules().get(0));
        check(compactChildProxy.getValue() == compactEditorEntries && compactEditorEntries.size() == 1
                        && compactChild.getValue().size() == 1 && Vape.INSTANCE.saves == saves,
                "resetting the editor keeps compact list identity and leaves live settings unchanged");
        Vape.INSTANCE.mods.top.clear();
        System.out.println("Value/profile serialization and snapshot checks passed (real Gson and values, headless module and GUI doubles)");
    }
}
