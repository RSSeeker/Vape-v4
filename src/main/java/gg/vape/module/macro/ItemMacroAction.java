package gg.vape.module.macro;

import gg.vape.Vape;
import gg.vape.config.ClientSettings;
import gg.vape.input.KeyBindingInputState;
import gg.vape.mapping.MappedClasses;
import gg.vape.module.Mod;
import gg.vape.module.combat.BlockHit;
import gg.vape.utils.RandomUtil;
import gg.vape.utils.TimerUtil;
import gg.vape.wrapper.Wrapper;
import gg.vape.wrapper.impl.Entity;
import gg.vape.wrapper.impl.EntityPlayer;
import gg.vape.wrapper.impl.EntityPlayerMacroBridge;
import gg.vape.wrapper.impl.Minecraft;

public class ItemMacroAction
implements MacroAction {
    private final TimerUtil timer = new TimerUtil();
    private boolean finished;
    private final ItemMacro macro;
    private int phase;
    private boolean secondClickStarted;
    private int originalHotbarSlot = -1;

    public int getOriginalHotbarSlot() {
        return this.originalHotbarSlot;
    }

    @Override
    public void cancel() {
        this.finished = true;
    }

    @Override
    public void inheritState(MacroAction previousAction) {
        if (previousAction instanceof ItemMacroAction) {
            this.originalHotbarSlot = ((ItemMacroAction)previousAction).getOriginalHotbarSlot();
        }
    }

    @Override
    public boolean isFinished() {
        return this.finished;
    }

    public ItemMacroAction(ItemMacro itemMacro) {
        this.macro = itemMacro;
    }

    @Override
    public void tick() {
        if (this.originalHotbarSlot == -1) {
            int targetHotbarSlot = this.macro.findHotbarSlot();
            if (targetHotbarSlot == -1) {
                this.finished = true;
                return;
            }
            this.originalHotbarSlot = Minecraft.thePlayer().V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6().v();
            Minecraft.thePlayer().V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6().g(targetHotbarSlot);
            this.timer.reset();
            BlockHit blockHit = Vape.INSTANCE.getModManager().getMod(BlockHit.class);
            if (ClientSettings.isUseItemButtonDown()) {
                if (((Mod)blockHit).isEnabled() && blockHit.requiresMouseDown() && ClientSettings.isKeyBindingDown(Minecraft.gameSettings().b$src$Lgg_vape_wrapper_impl_KeyBinding_$1yi3362())) {
                    if (blockHit.getActiveMode().isBlocking() && !blockHit.getActiveMode().shouldBlock()) {
                        this.phase = 2;
                    }
                } else {
                    this.phase = 2;
                }
            }
        }
        switch (this.phase) {
            case 0: {
                KeyBindingInputState.sendUseKeyDown();
                ++this.phase;
                break;
            }
            case 1: {
                KeyBindingInputState.sendUseKeyUp();
                this.timer.reset();
                ++this.phase;
                break;
            }
            case 2: {
                if (this.macro.getDoubleClick().getEffectiveValue().booleanValue() && !this.secondClickStarted) {
                    boolean secondClickReady = this.timer.hasTimeElapsed(RandomUtil.i(this.macro.getDoubleClickDelay()));
                    if (this.macro instanceof FishingRodMacro) {
                        EntityPlayer player = Minecraft.thePlayer();
                        EntityPlayerMacroBridge macroBridge = player.K$src$Lgg_vape_wrapper_impl_EntityPlayerMacroBridge_$1agjn9();
                        if (((Wrapper)player).isNotNull() && macroBridge.isNotNull()) {
                            Entity hookedEntity = macroBridge.r$src$Lgg_vape_wrapper_impl_Entity_$18p7x3h();
                            if (hookedEntity.isNotNull() && hookedEntity.isInstance(MappedClasses.lG) || macroBridge.o()) {
                                secondClickReady = true;
                            }
                        }
                    }
                    if (!secondClickReady) break;
                    this.secondClickStarted = true;
                    this.phase = 0;
                    break;
                }
                ++this.phase;
                break;
            }
            case 3: {
                if (!this.timer.hasTimeElapsed(RandomUtil.i(this.macro.getDelay()) - 2)) break;
                ++this.phase;
                break;
            }
            case 4: {
                Minecraft.thePlayer().V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6().g(this.originalHotbarSlot);
                this.finished = true;
            }
        }
    }
}
