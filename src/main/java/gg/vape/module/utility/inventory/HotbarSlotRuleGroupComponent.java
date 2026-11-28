package gg.vape.module.utility.inventory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import gg.vape.config.ConfigJsonUtils;
import func.skidline.RectData;
import gg.vape.module.none.ClientSettings;
import gg.vape.module.utility.inventory.HotbarSlotRule;
import gg.vape.module.utility.inventory.HotbarSlotRuleEditorComponent;
import gg.vape.module.utility.inventory.HotbarSlotRuleItemPickerFrame;
import gg.vape.ui.click.GuiMouseEvent;
import gg.vape.ui.click.component.GuiClickListener;
import gg.vape.ui.click.component.GuiComponent;
import gg.vape.ui.click.component.SquareIconButtonComponent;
import gg.vape.ui.click.frame.impl.main.ClickGuiFrameManager;
import gg.vape.utils.render.GuiRenderPrimitives;
import gg.vape.utils.render.ItemIconRenderer;
import gg.vape.wrapper.impl.ItemStack;
import gg.vape.wrapper.impl.Minecraft;
import java.util.List;
import java.util.Map;

public class HotbarSlotRuleGroupComponent
extends GuiComponent {
    private static String[] iconNames;
    private HotbarSlotRuleEditorComponent editor;
    private List<HotbarSlotRule> rules;
    private JsonObject preservedData = new JsonObject();
    private SquareIconButtonComponent closeButton = new SquareIconButtonComponent("newclose");

    public static void V(String[] stringArray) {
        iconNames = stringArray;
    }

    @Override
    public void g(GuiMouseEvent guiMouseEvent) {
        ClientSettings.getFrame(HotbarSlotRuleItemPickerFrame.class).setGroupComponent(this);
        if (this.equals(this.editor.getSelectedGroup())) {
            HotbarSlotRuleItemPickerFrame hotbarSlotRuleItemPickerFrame = ClientSettings.getFrame(HotbarSlotRuleItemPickerFrame.class);
            if (ClientSettings.INSTANCE.getActiveStack() instanceof ClickGuiFrameManager) {
                ClickGuiFrameManager clickGuiFrameManager = (ClickGuiFrameManager)ClientSettings.INSTANCE.getActiveStack();
                hotbarSlotRuleItemPickerFrame.setParentStackManager(clickGuiFrameManager);
                clickGuiFrameManager.setSidecarFrame(hotbarSlotRuleItemPickerFrame);
                clickGuiFrameManager.q(hotbarSlotRuleItemPickerFrame.getItemListFrame());
                clickGuiFrameManager.R(hotbarSlotRuleItemPickerFrame, hotbarSlotRuleItemPickerFrame.getItemListFrame());
            } else {
                hotbarSlotRuleItemPickerFrame.setParentStackManager(ClientSettings.INSTANCE.getActiveStack());
                hotbarSlotRuleItemPickerFrame.t(true, false);
                ClientSettings.INSTANCE.switchFrameStack(ClientSettings.hotbarRuleEditorStack);
                RectData rectData = new RectData(0.0, 0.0, Minecraft.J(), Minecraft.h());
                ClientSettings.INSTANCE.refreshFrameLayouts();
            }
        }
        this.editor.selectGroup(this);
    }

    public List<HotbarSlotRule> getRules() {
        return this.rules;
    }

    @Override
    public double x() {
        return 110.0;
    }

    @Override
    public void H() {
        boolean selected = this.editor.getSelectedGroup() != null && this.getRules().equals(this.editor.getSelectedGroup().getRules());
        GuiRenderPrimitives.d(this.G$src$D$1b2f02a() + 5.0, this.n() + 1.0, this.A() - 10.0, this.L() - 2.0, selected ? HotbarSlotRuleGroupComponent.J.K : HotbarSlotRuleGroupComponent.J.m);
        double iconX = this.G$src$D$1b2f02a() + 10.0;
        for (HotbarSlotRule hotbarSlotRule : this.rules) {
            double iconSize = 9.0;
            GuiRenderPrimitives.C(iconX, this.n() + this.L() / 2.0 - iconSize / 2.0, 8.5, iconSize, HotbarSlotRuleGroupComponent.J.r);
            ItemStack itemStack = hotbarSlotRule.createItemStack();
            if (itemStack != null && itemStack.isNotNull()) {
                float renderX = (float)iconX;
                float renderY = (float)(this.n() + this.L() / 2.0 - 4.0);
                ItemIconRenderer.renderItemStack(itemStack, renderX, renderY, 8, 8);
            }
            iconX += 9.0;
        }
        this.closeButton.K(this.G$src$D$1b2f02a() + this.A() - 10.0 - 8.0);
        this.closeButton.S(this.n());
        this.closeButton.Y(this.L());
    }

    static {
        HotbarSlotRuleGroupComponent.V(null);
    }

    public HotbarSlotRuleGroupComponent addCloseListener(GuiClickListener guiClickListener) {
        // rebuildChildren may visit this group repeatedly; keep one handler.
        this.closeButton.setClickListener(guiClickListener);
        return this;
    }

    public void notifyRulesChanged() {
        this.editor.getRuleValue().notifyChanged();
    }

    public JsonObject toJson() {
        JsonObject jsonObject = ConfigJsonUtils.deepCopy(this.preservedData);
        JsonArray original = ConfigJsonUtils.getJsonArray(this.preservedData, "hotbars");
        JsonArray jsonArray = new JsonArray();
        for (int i = 0; i < this.rules.size(); ++i) {
            JsonObject slot = original != null && i < original.size() && original.get(i).isJsonObject()
                    ? ConfigJsonUtils.deepCopy(original.get(i).getAsJsonObject()) : new JsonObject();
            for (Map.Entry<String, JsonElement> entry : this.rules.get(i).toJson().entrySet()) {
                slot.add(entry.getKey(), ConfigJsonUtils.deepCopy(entry.getValue()));
            }
            jsonArray.add(slot);
        }
        jsonObject.add("hotbars", jsonArray);
        return jsonObject;
    }

    @Override
    public void I() {
    }


    public static String[] e() {
        return iconNames;
    }

    @Override
    public double C() {
        return 15.0;
    }

    @Override
    public void u() {
    }

    public void setRules(List<HotbarSlotRule> rules) {
        this.rules = rules;
    }

    public HotbarSlotRuleGroupComponent(HotbarSlotRuleEditorComponent hotbarSlotRuleEditorComponent, List<HotbarSlotRule> list) {
        this.rules = list;
        this.editor = hotbarSlotRuleEditorComponent;
        this.addChildren(this.closeButton);
    }

    public void loadJson(JsonObject jsonObject) {
        JsonArray jsonArray = jsonObject.getAsJsonArray("hotbars");
        if (jsonArray == null || jsonArray.size() > this.getRules().size()) {
            throw new IllegalArgumentException("Invalid hotbar slots");
        }
        int n = jsonArray.size();
        for (int i = 0; i < n; ++i) {
            JsonObject jsonObject2 = jsonArray.get(i).getAsJsonObject();
            this.getRules().get(i).loadJson(jsonObject2);
        }
        this.preservedData = ConfigJsonUtils.deepCopy(jsonObject);
    }

    @Override
    public void F() {
    }
}

