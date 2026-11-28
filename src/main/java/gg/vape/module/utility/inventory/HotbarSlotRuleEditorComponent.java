package gg.vape.module.utility.inventory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import gg.vape.config.ConfigJsonUtils;
import gg.vape.module.utility.inventory.HotbarSlotRule;
import gg.vape.module.utility.inventory.HotbarSlotRuleAddButton;
import gg.vape.module.utility.inventory.HotbarSlotRuleGroupComponent;
import gg.vape.module.utility.inventory.HotbarSlotRuleGroupSelectClickHandler;
import gg.vape.module.utility.inventory.HotbarSlotRuleValue;
import gg.vape.ui.click.GuiMouseEvent;
import gg.vape.ui.click.component.GuiComponent;
import gg.vape.ui.click.component.SimpleTextLabelComponent;
import java.util.ArrayList;
import java.util.List;

public class HotbarSlotRuleEditorComponent
extends GuiComponent {
    private HotbarSlotRuleGroupComponent selectedGroup;
    private List<HotbarSlotRuleGroupComponent> groups;
    private boolean editing;
    private HotbarSlotRuleAddButton addGroupButton = new HotbarSlotRuleAddButton();
    private static String[] opaqueState;
    private HotbarSlotRuleValue ruleValue;
    private JsonObject preservedData = new JsonObject();
    private boolean blockedByInvalidData;
    private final SimpleTextLabelComponent invalidDataLabel = new SimpleTextLabelComponent("Unsupported data; reset to edit", 0.7);

    public HotbarSlotRuleValue getRuleValue() {
        return this.ruleValue;
    }

    static List<HotbarSlotRuleGroupComponent> getGroupsInternal(HotbarSlotRuleEditorComponent hotbarSlotRuleEditorComponent) {
        return hotbarSlotRuleEditorComponent.groups;
    }

    public JsonObject serializeRules() {
        // Preserve fields from newer profile formats that this client does not
        // understand; known groups and selection are written below.
        JsonObject jsonObject = ConfigJsonUtils.deepCopy(this.preservedData);
        JsonArray jsonArray = new JsonArray();
        for (HotbarSlotRuleGroupComponent hotbarSlotRuleGroupComponent : this.groups) {
            jsonArray.add((JsonElement)hotbarSlotRuleGroupComponent.toJson());
        }
        if (this.selectedGroup == null) {
            jsonObject.addProperty("selected", (Number)0);
        } else {
            jsonObject.addProperty("selected", (Number)this.groups.indexOf(this.selectedGroup));
        }
        jsonObject.add("panels", (JsonElement)jsonArray);
        return jsonObject;
    }

    public List<HotbarSlotRuleGroupComponent> getGroups() {
        return this.groups;
    }


    @Override
    public void u() {
    }

    public boolean loadJson(JsonObject jsonObject) {
        // Build first so malformed profile data cannot partially erase the
        // currently configured hotbars.
        List<HotbarSlotRuleGroupComponent> loaded = new ArrayList<HotbarSlotRuleGroupComponent>();
        int selected = 0;
        try {
            JsonArray panels = jsonObject.has("panels") ? jsonObject.getAsJsonArray("panels") : new JsonArray();
            if (panels == null) return false;
            if (jsonObject.has("selected") && !jsonObject.get("selected").isJsonNull()) {
                selected = jsonObject.get("selected").getAsInt();
            }
            for (JsonElement panel : panels) {
                HotbarSlotRuleGroupComponent group = new HotbarSlotRuleGroupComponent(this, this.createEmptyRuleSlots());
                group.loadJson(panel.getAsJsonObject());
                loaded.add(group);
            }
        } catch (RuntimeException invalid) {
            return false;
        }
        this.groups.clear();
        this.groups.addAll(loaded);
        this.selectedGroup = this.groups.isEmpty() ? null : this.groups.get(Math.max(0, Math.min(selected, this.groups.size() - 1)));
        this.preservedData = ConfigJsonUtils.deepCopy(jsonObject);
        this.blockedByInvalidData = false;
        this.rebuildChildren();
        return true;
    }

    /** Keep malformed data opaque until the user explicitly resets it. */
    public void blockInvalidData() {
        this.blockedByInvalidData = true;
        this.rebuildChildren();
    }

    @Override
    public void I() {
    }

    public void rebuildChildren() {
        this.f().clear();
        if (this.blockedByInvalidData) {
            this.addChildren(this.invalidDataLabel);
            if (this.getParentFrameComponent() != null) this.getParentFrameComponent().l$src$V$1mibm4x();
            return;
        }
        this.addChildren(this.addGroupButton);
        for (HotbarSlotRuleGroupComponent hotbarSlotRuleGroupComponent : this.getGroups()) {
            HotbarSlotRuleGroupComponent hotbarSlotRuleGroupComponent2 = hotbarSlotRuleGroupComponent.addCloseListener(new HotbarSlotRuleGroupSelectClickHandler(this, hotbarSlotRuleGroupComponent));
            this.addChildren(hotbarSlotRuleGroupComponent2);
        }
        if (this.getParentFrameComponent() != null) this.getParentFrameComponent().l$src$V$1mibm4x();
    }

    static {
        HotbarSlotRuleEditorComponent.v(new String[1]);
    }

    private List<HotbarSlotRule> createEmptyRuleSlots() {
        ArrayList<HotbarSlotRule> arrayList = new ArrayList<HotbarSlotRule>();
        for (int i = 0; i < 9; ++i) {
            HotbarSlotRule hotbarSlotRule = new HotbarSlotRule(0);
            arrayList.add(hotbarSlotRule);
        }
        return arrayList;
    }

    public HotbarSlotRuleEditorComponent(HotbarSlotRuleValue hotbarSlotRuleValue) {
        this.groups = new ArrayList<HotbarSlotRuleGroupComponent>();
        this.ruleValue = hotbarSlotRuleValue;
        this.bindValue(hotbarSlotRuleValue);
        HotbarSlotRuleEditorComponent hotbarSlotRuleEditorComponent = this;
        this.addGroupButton.addClickListener(() -> {
            HotbarSlotRuleGroupComponent groupComponent = new HotbarSlotRuleGroupComponent(hotbarSlotRuleEditorComponent, this.createEmptyRuleSlots());
            this.groups.add(groupComponent);
            if (this.selectedGroup == null) {
                this.selectedGroup = groupComponent;
            }
            this.rebuildChildren();
            this.ruleValue.notifyChanged();
        });
        this.addChildren(this.addGroupButton);
        // May apply data loaded before the shared editor was constructed.
        hotbarSlotRuleValue.setEditor(this);
    }

    static HotbarSlotRuleGroupComponent getSelectedGroupInternal(HotbarSlotRuleEditorComponent hotbarSlotRuleEditorComponent) {
        return hotbarSlotRuleEditorComponent.selectedGroup;
    }

    public void selectGroup(HotbarSlotRuleGroupComponent hotbarSlotRuleGroupComponent) {
        if (this.selectedGroup != hotbarSlotRuleGroupComponent) {
            this.selectedGroup = hotbarSlotRuleGroupComponent;
            this.ruleValue.notifyChanged();
        }
    }

    @Override
    public double x() {
        return 110.0;
    }

    @Override
    public void H() {
        this.onDisable();
        if (this.blockedByInvalidData) {
            this.invalidDataLabel.K(this.G$src$D$1b2f02a());
            this.invalidDataLabel.S(this.n());
            return;
        }
        this.addGroupButton.K(this.G$src$D$1b2f02a());
        this.addGroupButton.S(this.n());
        double d = this.addGroupButton.n() + this.addGroupButton.L();
        for (HotbarSlotRuleGroupComponent hotbarSlotRuleGroupComponent : this.getGroups()) {
            hotbarSlotRuleGroupComponent.K(this.G$src$D$1b2f02a());
            hotbarSlotRuleGroupComponent.S(d);
            d += hotbarSlotRuleGroupComponent.L();
        }
    }

    @Override
    public void F() {
    }

    public static String[] u$src$ALjava_lang_String_$1im86xh() {
        return opaqueState;
    }

    @Override
    public double C() {
        if (this.blockedByInvalidData) return this.invalidDataLabel.L();
        double d = this.addGroupButton.L();
        for (HotbarSlotRuleGroupComponent hotbarSlotRuleGroupComponent : this.getGroups()) {
            d += hotbarSlotRuleGroupComponent.L();
        }
        return d;
    }

    public static void v(String[] stringArray) {
        opaqueState = stringArray;
    }

    public void removeGroup(HotbarSlotRuleGroupComponent hotbarSlotRuleGroupComponent) {
        if (!this.groups.remove(hotbarSlotRuleGroupComponent)) return;
        if (this.selectedGroup == hotbarSlotRuleGroupComponent) {
            this.selectedGroup = this.groups.isEmpty() ? null : this.groups.get(0);
        }
        this.ruleValue.notifyChanged();
    }

    @Override
    public void g(GuiMouseEvent guiMouseEvent) {
    }

    public HotbarSlotRuleGroupComponent getSelectedGroup() {
        return this.selectedGroup;
    }
}
