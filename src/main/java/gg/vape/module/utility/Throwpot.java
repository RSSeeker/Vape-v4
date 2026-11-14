package gg.vape.module.utility;

import gg.vape.event.EventHandler;
import gg.vape.event.impl.EventPreTick;
import gg.vape.input.KeyBindingHelper;
import gg.vape.mapping.MappedClasses;
import gg.vape.module.Category;
import gg.vape.module.UtilityMod;
import gg.vape.module.utility.invcleaner.ItemDataComparator;
import gg.vape.unmap.ModeOption;
import gg.vape.unmap.ModeSelection;
import gg.vape.utils.ItemStackScoreUtil;
import gg.vape.utils.datas.ItemStackData;
import gg.vape.value.BooleanValue;
import gg.vape.value.ModeValue;
import gg.vape.value.NumberValue;
import gg.vape.value.RandomValue;
import gg.vape.wrapper.impl.GameSettings;
import gg.vape.wrapper.impl.InventoryPlayer;
import gg.vape.wrapper.impl.Item;
import gg.vape.wrapper.impl.ItemSplashPotion;
import gg.vape.wrapper.impl.ItemStack;
import gg.vape.wrapper.impl.KeyBinding;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.PotionEffect;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.CopyOnWriteArrayList;

public class Throwpot
extends UtilityMod {
    private final CopyOnWriteArrayList<ItemStackData> itemsToThrow;
    private final NumberValue scrollDelay;
    private final ModeValue modeValue;
    private int savedSlot;
    private final ModeValue typeValue;
    private final ModeOption singleOption;
    private final ModeOption potsOption;
    private final BooleanValue scrollValue;
    private final ModeOption bothOption = new ModeOption("Both");
    private final ModeOption dynamicOption;
    private final BooleanValue throwBowls;
    private boolean active;
    private final BooleanValue randomValue;
    private final RandomValue delayValue;
    private final ModeOption soupOption;

    @Override
    public void onEnable() {
        if (this.active) {
            this.setEnabled(false);
            return;
        }
        if (Minecraft.thePlayer().isNull() || Minecraft.currentScreen().isNotNull()) {
            this.setEnabled(false);
            return;
        }
        if (!this.active && this.collectHealingItems()) {
            InventoryPlayer inventory = Minecraft.thePlayer().V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6();
            this.savedSlot = inventory.v();
            this.active = true;
            this.v(0L, false);
        } else {
            this.itemsToThrow.clear();
            this.setEnabled(false);
        }
    }

    /** True when the player is currently able to eat food (vanilla Player#canEat(false)). */
    private boolean canConsumeFood() {
        Object handle;
        try {
            handle = Minecraft.thePlayer().getObject();
        } catch (Throwable throwable) {
            return true;
        }
        if (handle == null) {
            return true;
        }
        try {
            Class<?> type = handle.getClass();
            while (type != null) {
                for (java.lang.reflect.Method candidate : type.getDeclaredMethods()) {
                    if (!candidate.getName().equals("canEat") || candidate.getParameterCount() != 1
                            || candidate.getParameterTypes()[0] != Boolean.TYPE) {
                        continue;
                    }
                    candidate.setAccessible(true);
                    return (Boolean)candidate.invoke(handle, Boolean.FALSE);
                }
                type = type.getSuperclass();
            }
        } catch (Throwable throwable) {
            return true;
        }
        return true;
    }

    private boolean collectHealingItems() {
        ArrayList<Integer> hotbarSlots = new ArrayList<Integer>();
        for (int hotbarSlot = 0; hotbarSlot < 9; ++hotbarSlot) {
            hotbarSlots.add(hotbarSlot);
        }
        if (this.randomValue.getEffectiveValue().booleanValue()) {
            Collections.shuffle(hotbarSlots);
        }
        Object[] hotbarContents = Minecraft.thePlayer().V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6().M();
        int queuedHealing = 0;
        for (Integer hotbarSlot : hotbarSlots) {
            Item item;
            ItemStack itemStack = new ItemStack(hotbarContents[hotbarSlot]);
            if (itemStack.isNull() || (item = itemStack.getItem()).isNull()) continue;
            boolean soupEnabled = ((ModeSelection)this.typeValue.getValue()).equals(this.soupOption) || ((ModeSelection)this.typeValue.getValue()).equals(this.bothOption);
            boolean potsEnabled = ((ModeSelection)this.typeValue.getValue()).equals(this.potsOption) || ((ModeSelection)this.typeValue.getValue()).equals(this.bothOption);
            if (ItemStackScoreUtil.v(item) && soupEnabled && this.canConsumeFood()) {
                if (((ModeSelection)this.modeValue.getValue()).equals(this.singleOption)) {
                    this.itemsToThrow.add(new ItemStackData(hotbarSlot, itemStack));
                    break;
                }
                int soupHealing = 8;
                if ((double)(queuedHealing + soupHealing) + Math.floor(Minecraft.thePlayer().w$src$F$15l9epb()) > (double)Minecraft.thePlayer().I$src$F$14vyvep()) continue;
                queuedHealing += soupHealing;
                this.itemsToThrow.add(new ItemStackData(hotbarSlot, itemStack));
            }
            if (!MappedClasses.Di.isInstance(item.getObject()) || !potsEnabled || !ItemSplashPotion.isSplashPotion(itemStack)) continue;
            if (((ModeSelection)this.modeValue.getValue()).equals(this.singleOption) && ItemStackScoreUtil.i(itemStack)) {
                this.itemsToThrow.add(new ItemStackData(hotbarSlot, itemStack));
                break;
            }
            ItemSplashPotion splashPotion = new ItemSplashPotion(item.getObject());
            PotionEffect potionEffect = new PotionEffect(splashPotion.getRawPotionEffects(itemStack).get(0));
            int potionHealing = 4 * (potionEffect.L() + 1);
            if (!ItemStackScoreUtil.i(itemStack) || (double)(queuedHealing + potionHealing) + Math.floor(Minecraft.thePlayer().w$src$F$15l9epb()) > (double)Minecraft.thePlayer().I$src$F$14vyvep()) continue;
            queuedHealing += potionHealing;
            this.itemsToThrow.add(new ItemStackData(hotbarSlot, itemStack));
        }
        return !this.itemsToThrow.isEmpty();
    }

    @Override
    public void onScheduledAction() {
        if (!this.active) {
            return;
        }
        try {
            GameSettings gameSettings = Minecraft.gameSettings();
            KeyBinding useKey = gameSettings.b$src$Lgg_vape_wrapper_impl_KeyBinding_$1yi3362();
            KeyBinding dropKey = gameSettings.v$src$Lgg_vape_wrapper_impl_KeyBinding_$11ijh0e();
            this.itemsToThrow.sort(new ItemDataComparator(this.savedSlot));
            boolean restoreUseKey = false;
            for (ItemStackData itemToThrow : this.itemsToThrow) {
                this.selectHotbarSlot(itemToThrow.getSlot());
                if (useKey.isKeyDown() && ItemStackScoreUtil.v(itemToThrow.getItemStack().getItem())) {
                    KeyBindingHelper.updateKeyBinding(useKey, false, false);
                    Thread.sleep(51L);
                    restoreUseKey = true;
                }
                boolean soup = ItemStackScoreUtil.v(itemToThrow.getItemStack().getItem());
                if (soup && !this.canConsumeFood()) {
                    continue;
                }
                if (soup) {
                    KeyBindingHelper.updateKeyBinding(useKey, true, true);
                    this.waitForSoupConsumed(itemToThrow.getSlot(), itemToThrow.getItemStack());
                    KeyBindingHelper.updateKeyBinding(useKey, false, false);
                } else {
                    KeyBindingHelper.setPressedAndTick(useKey, true);
                    Thread.sleep(51L);
                    KeyBindingHelper.updateKeyBinding(useKey, false, false);
                }
                if (this.throwBowls.getEffectiveValue().booleanValue() && soup) {
                    KeyBindingHelper.setPressedAndTick(dropKey, true);
                    Thread.sleep(51L);
                    KeyBindingHelper.updateKeyBinding(dropKey, false, false);
                }
                Thread.sleep((long)this.delayValue.getRandomValue());
            }
            this.selectHotbarSlot(this.savedSlot);
            if (restoreUseKey) {
                KeyBindingHelper.setPressedAndTick(useKey, true);
            }
        }
        catch (Exception exception) {
            this.active = false;
            exception.printStackTrace();
        }
        this.active = false;
    }

    @EventHandler
    public void onTick(EventPreTick event) {
        if (!this.active && this.isEnabled()) {
            this.setEnabled(false);
        }
    }

    private boolean waitForSoupConsumed(int slot, ItemStack before) {
        long deadline = System.currentTimeMillis() + 2500L;
        String beforeName = before.getItem().isNull() ? "" : before.getItem().A();
        while (System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(25L);
            }
            catch (InterruptedException ignored) {
            }
            Object[] contents = Minecraft.thePlayer().V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6().M();
            if (contents == null || slot < 0 || slot >= contents.length) {
                return false;
            }
            ItemStack current = new ItemStack(contents[slot]);
            if (current.isNull()) {
                return true;
            }
            Item item = current.getItem();
            if (item.isNull()) {
                return true;
            }
            if (!item.A().equals(beforeName)) {
                return true;
            }
        }
        return false;
    }

    private void selectHotbarSlot(int targetSlot) {
        if (!this.scrollValue.getEffectiveValue().booleanValue()) {
            Minecraft.thePlayer().V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6().g(targetSlot);
            return;
        }
        int currentSlot = Minecraft.thePlayer().V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6().v();
        while (true) {
            Minecraft.thePlayer().V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6().g(currentSlot);
            try {
                Thread.sleep(((Double)this.scrollDelay.getValue()).longValue());
            }
            catch (InterruptedException ignored) {
            }
            if (targetSlot > currentSlot) {
                ++currentSlot;
                continue;
            }
            if (targetSlot >= currentSlot) break;
            --currentSlot;
        }
    }

    public Throwpot() {
        super("Throwpot", Category.INVENTORY, "Throws or consumes healing items automatically or upon pressing keybind");
        this.potsOption = new ModeOption("Pots");
        this.soupOption = new ModeOption("Soup");
        this.typeValue = ModeValue.create((Object)this, "Type", this.bothOption, this.bothOption, this.potsOption, this.soupOption);
        this.dynamicOption = new ModeOption("Dynamic");
        this.singleOption = new ModeOption("Single");
        this.modeValue = ModeValue.create((Object)this, "Mode", "Dynamic - uses only as many items as needed to heal as much as possible without over-healing\nSingle - Always uses one item, regardless of health", (ModeSelection)this.dynamicOption, this.dynamicOption, this.singleOption);
        this.scrollDelay = NumberValue.create(this, "Scroll delay", "#", "ms", 0.0, 100.0, 200.0);
        this.delayValue = RandomValue.createWithIncrement(this, "Delay", "#", "ms", 0.0, 80.0, 115.0, 200.0, 1.0);
        this.scrollValue = BooleanValue.create(this, "Scroll", false);
        this.randomValue = BooleanValue.create(this, "Random", false);
        this.throwBowls = BooleanValue.create(this, "Throw bowls", true, "Throws soup bowls after consuming");
        this.itemsToThrow = new CopyOnWriteArrayList<ItemStackData>();
        this.scrollValue.addDependentValues(this.scrollDelay);
        this.addValue(this.typeValue, this.modeValue, this.delayValue, this.scrollValue, this.scrollDelay, this.randomValue, this.throwBowls);
    }

    @Override
    public void onDisable() {
        this.itemsToThrow.clear();
    }
}

