package gg.vape.module.utility.inventory;

import gg.vape.module.utility.inventory.HotbarSlotRule;
import gg.vape.module.utility.inventory.HotbarSlotRuleItemPickerFrame;
import gg.vape.module.utility.inventory.HotbarSlotRuleSlotSelectorComponent;
import gg.vape.ui.click.component.GuiClickListener;

class HotbarSlotRuleSlotSelectClickHandler
implements GuiClickListener {
    final int slotIndex;
    final HotbarSlotRuleItemPickerFrame pickerFrame;
    final HotbarSlotRuleSlotSelectorComponent slotSelector;

    HotbarSlotRuleSlotSelectClickHandler(HotbarSlotRuleSlotSelectorComponent hotbarSlotRuleSlotSelectorComponent, HotbarSlotRuleItemPickerFrame hotbarSlotRuleItemPickerFrame, int slotIndex) {
        this.slotSelector = hotbarSlotRuleSlotSelectorComponent;
        this.pickerFrame = hotbarSlotRuleItemPickerFrame;
        this.slotIndex = slotIndex;
    }

    @Override
    public void onSecondaryClick() {
        this.pickerFrame.getGroupComponent().getRules().set(this.slotIndex, new HotbarSlotRule(0));
        this.pickerFrame.getGroupComponent().notifyRulesChanged();
    }

    @Override
    public void onPrimaryClick() {
        this.pickerFrame.setSelectedSlot(this.slotIndex);
    }
}

