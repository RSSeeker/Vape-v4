package gg.vape.ui.click.component.value;

import gg.vape.module.blatant.antibot.AntiBotBooleanValue;
import gg.vape.module.blatant.antibot.AntiBotModeValue;
import gg.vape.module.utility.inventory.HotbarSlotRuleEditorComponent;
import gg.vape.module.utility.inventory.HotbarSlotRuleValue;
import gg.vape.module.utility.inventory.cleaner.InventoryCleanerProfileValue;
import gg.vape.module.utility.inventory.cleaner.ui.InventoryCleanerProfileValueComponent;
import gg.vape.ui.click.component.DropdownSelectComponent;
import gg.vape.ui.click.component.GuiComponent;
import gg.vape.ui.click.component.input.BindValueRowComponent;
import gg.vape.unmap.ItemLimitData;
import gg.vape.value.BindValue;
import gg.vape.value.BooleanValue;
import gg.vape.value.ColorValue;
import gg.vape.value.EntityTargetFilterValue;
import gg.vape.value.ListValue;
import gg.vape.value.ModeValue;
import gg.vape.value.NumberValue;
import gg.vape.value.OptionalLimitEntry;
import gg.vape.value.RandomValue;
import gg.vape.value.StringMapValue;
import gg.vape.value.StringValue;
import gg.vape.value.Value;
import gg.vape.value.ValueSnapshot;

public class ValueComponentFactory {
    public static GuiComponent createValueComponent(Value<?, ?> value, boolean snapshotEditor, ValueComponentMode componentMode) {
        GuiComponent component = value.getBoundComponent();
        if (value instanceof BooleanValue) {
            component = new BooleanToggleComponent((BooleanValue)value);
        } else if (value instanceof AntiBotModeValue) {
            component = new ColorValueDropdownComponent((AntiBotModeValue)value);
        } else if (value instanceof AntiBotBooleanValue) {
            component = new AntiBotBooleanValueOptionRow((AntiBotBooleanValue)value);
        } else if (value instanceof ModeValue) {
            component = new DropdownSelectComponent((ModeValue)value);
            if (componentMode == ValueComponentMode.STANDALONE) {
                ((DropdownSelectComponent)component).setHighlightedStyle(true);
            }
        } else if (value instanceof NumberValue) {
            component = new NumberSliderComponent((NumberValue)value);
        } else if (value instanceof RandomValue) {
            component = new RandomRangeSliderComponent((RandomValue)value);
        } else if (value instanceof ColorValue) {
            component = new ColorValueEditorComponent((ColorValue)value);
        } else if (value instanceof ListValue) {
            BooleanValue parentToggle;
            ListValueComponent listValueComponent = new ListValueComponent((ListValue)value);
            if (componentMode == ValueComponentMode.STANDALONE) {
                listValueComponent.setMode(ValueComponentMode.STANDALONE);
            }
            component = listValueComponent;
            ListValue listValue = (ListValue)value;
            if (listValue.getParent() instanceof BooleanValue && (parentToggle = (BooleanValue)listValue.getParent()).getTerminalDependentValue() != null && parentToggle.getTerminalDependentValue().equals(listValue)) {
                component = null;
            }
        } else if (value instanceof HotbarSlotRuleValue) {
            HotbarSlotRuleValue hotbarSlotRuleValue = (HotbarSlotRuleValue)value;
            if (!snapshotEditor && componentMode == ValueComponentMode.STANDALONE && hotbarSlotRuleValue.getEditor() != null
                    && hotbarSlotRuleValue.getEditor().getParentFrameComponent() != null
                    && value.getOwner() instanceof gg.vape.module.Mod) {
                return new gg.vape.ui.click.frame.impl.ClientSettingsActionButtonRowComponent("Open original editor",
                        () -> gg.vape.module.none.ClientSettings.INSTANCE.openClassicModule((gg.vape.module.Mod)value.getOwner()));
            }
            if (hotbarSlotRuleValue.getEditor() != null) {
                component = hotbarSlotRuleValue.getEditor();
                component.setVisible(true);
            } else {
                component = new HotbarSlotRuleEditorComponent(hotbarSlotRuleValue);
            }
        } else if (value instanceof EntityTargetFilterValue) {
            component = snapshotEditor ? new EntityTargetFilterPopupComponent((EntityTargetFilterValue)value) : new EntityTargetFilterComponent((EntityTargetFilterValue)value);
        } else if (value instanceof StringValue) {
            component = new StringValueTextInputComponent((StringValue)value);
        } else if (value instanceof StringMapValue) {
            component = new StringMapValueComponent((StringMapValue)value);
        } else if (value instanceof BindValue) {
            component = new BindValueRowComponent((BindValue)value);
        } else if (value instanceof InventoryCleanerProfileValue) {
            component = new InventoryCleanerProfileValueComponent((InventoryCleanerProfileValue)value);
        }
        return component;
    }


    public static GuiComponent createMainValueComponent(Value<?, ?> value) {
        return ValueComponentFactory.createValueComponent(value, false, ValueComponentMode.MAIN);
    }

    public static Value createSnapshotProxyValue(ValueSnapshot valueSnapshot) {
        return valueSnapshot.createProxyValue();
    }

    public static Object getComparableListEntryText(Object entry) {
        if (entry instanceof OptionalLimitEntry || entry instanceof ItemLimitData) {
            return entry.toString();
        }
        return null;
    }

    public static GuiComponent createMainValueComponent(Value<?, ?> value, boolean snapshotEditor) {
        return ValueComponentFactory.createValueComponent(value, snapshotEditor, ValueComponentMode.MAIN);
    }
}
