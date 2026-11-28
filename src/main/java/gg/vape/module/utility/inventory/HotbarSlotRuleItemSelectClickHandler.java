package gg.vape.module.utility.inventory;

import gg.vape.module.utility.inventory.HotbarSlotRule;
import gg.vape.module.utility.inventory.HotbarSlotRuleItemListFrame;
import gg.vape.ui.click.component.GuiClickListener;
import gg.vape.wrapper.impl.ItemStack;
import java.util.List;

class HotbarSlotRuleItemSelectClickHandler
implements GuiClickListener {
    final HotbarSlotRuleItemListFrame itemListFrame;
    final List<ItemStack> items;
    final int itemIndex;

    HotbarSlotRuleItemSelectClickHandler(HotbarSlotRuleItemListFrame hotbarSlotRuleItemListFrame, List list, int itemIndex) {
        this.itemListFrame = hotbarSlotRuleItemListFrame;
        this.items = list;
        this.itemIndex = itemIndex;
    }

    @Override
    public void onPrimaryClick() {
        HotbarSlotRule hotbarSlotRule = HotbarSlotRule.fromItemStack(this.items.get(this.itemIndex));
        HotbarSlotRuleItemPickerFrame picker = HotbarSlotRuleItemListFrame.getPickerFrame(this.itemListFrame);
        picker.getGroupComponent().getRules().set(picker.getSelectedSlot(), hotbarSlotRule);
        picker.getGroupComponent().notifyRulesChanged();
    }
}

