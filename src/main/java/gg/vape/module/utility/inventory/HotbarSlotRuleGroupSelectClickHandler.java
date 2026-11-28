package gg.vape.module.utility.inventory;

import gg.vape.module.utility.inventory.HotbarSlotRuleEditorComponent;
import gg.vape.module.utility.inventory.HotbarSlotRuleGroupComponent;
import gg.vape.ui.click.component.GuiClickListener;

class HotbarSlotRuleGroupSelectClickHandler
implements GuiClickListener {
    final HotbarSlotRuleGroupComponent group;
    final HotbarSlotRuleEditorComponent editor;


    @Override
    public void onPrimaryClick() {
        this.editor.removeGroup(this.group);
        this.editor.rebuildChildren();
    }

    HotbarSlotRuleGroupSelectClickHandler(HotbarSlotRuleEditorComponent hotbarSlotRuleEditorComponent, HotbarSlotRuleGroupComponent hotbarSlotRuleGroupComponent) {
        this.editor = hotbarSlotRuleEditorComponent;
        this.group = hotbarSlotRuleGroupComponent;
    }
}

