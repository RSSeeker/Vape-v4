package gg.vape.module.macro;

import gg.vape.module.Macro;
import gg.vape.module.macro.ItemMacroAction;
import gg.vape.module.macro.MacroAction;
import gg.vape.utils.StringUtils;
import gg.vape.wrapper.impl.Item;
import gg.vape.wrapper.impl.ItemStack;
import gg.vape.wrapper.impl.Minecraft;

public class ItemMacro
extends Macro {
    public ItemMacro(String name) {
        super(name);
    }

    @Override
    public MacroAction createAction() {
        int hotbarSlot = this.findHotbarSlot();
        if (hotbarSlot == -1) {
            return null;
        }
        return new ItemMacroAction(this);
    }

    int findHotbarSlot() {
        try {
            for (int hotbarSlot = 0; hotbarSlot < 9; ++hotbarSlot) {
                ItemStack itemStack = Minecraft.thePlayer().V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6().c(hotbarSlot);
                if (itemStack == null || itemStack.getObject() == null || itemStack.getItem() == null || itemStack.getItem().getObject() == null) continue;
                Item item = itemStack.getItem();
                // 注册名用物品对象自身的 toString（返回如 minecraft:air / minecraft:snowball），
                // 避免 item.O() 在 26.1 下因 MItem.S 未注册而抛 NPE，中断匹配。
                String registryName = String.valueOf(item.getObject());
                String itemName = null;
                String stackName = null;
                try {
                    itemName = StringUtils.l(itemStack.x());
                }
                catch (Throwable ignored) {}
                try {
                    stackName = StringUtils.l(item.getItemStackDisplayName(itemStack));
                }
                catch (Throwable ignored) {}
                if (registryName != null && registryName.equalsIgnoreCase(this.getName())) {
                    return hotbarSlot;
                }
                // 也接受去掉命名空间前缀的短名（minecraft:snowball -> snowball）
                String shortName = registryName != null && registryName.contains(":")
                        ? registryName.substring(registryName.indexOf(':') + 1) : registryName;
                if (shortName != null && shortName.equalsIgnoreCase(this.getName())) {
                    return hotbarSlot;
                }
                if (String.valueOf(Item.f(item)).equals(this.getName())) {
                    return hotbarSlot;
                }
                if (itemName != null && itemName.equalsIgnoreCase(this.getName())) {
                    return hotbarSlot;
                }
                if (stackName != null && stackName.equalsIgnoreCase(this.getName())) {
                    return hotbarSlot;
                }
            }
        }
        catch (Exception ignored) {
            // empty catch block
        }
        return -1;
    }
}
