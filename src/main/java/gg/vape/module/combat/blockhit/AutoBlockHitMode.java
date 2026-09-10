package gg.vape.module.combat.blockhit;

import gg.vape.config.ClientSettings;
import gg.vape.event.EventHandler;
import gg.vape.event.impl.EventMouseButton;
import gg.vape.module.Mod;
import gg.vape.module.combat.BlockHit;
import gg.vape.rotation.RotationManager;
import gg.vape.utils.ItemStackScoreUtil;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.ItemStack;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.RayTraceResult;
import gg.vape.wrapper.impl.RayTraceResult_type;

public class AutoBlockHitMode
extends BlockHitMode {
    private int targetStreak = 1;

    public AutoBlockHitMode(Mod parent, String name) {
        super(parent, name);
    }

    @Override
    public boolean isBlocking() {
        return this.shouldBlock();
    }

    @EventHandler
    public void onMouseButton(EventMouseButton event) {
        if (!((BlockHit)this.getParent()).isHoldingSword()) {
            return;
        }
        int buttonBinding = -100 + event.getButton();
        if (event.getButtonState() && buttonBinding == Minecraft.gameSettings().b$src$Lgg_vape_wrapper_impl_KeyBinding_$1yi3362().getKeyCode() && ((BlockHit)this.getParent()).requiresMouseDown() && ClientSettings.isAttackButtonDown()) {
            event.setCancelled(true);
            return;
        }
    }


    private boolean shouldBlockSwordUse() {
        if (((BlockHit)this.getParent()).requiresMouseDown() && !ClientSettings.isUseItemButtonDown()) {
            return false;
        }
        boolean shouldBlock = true;
        EntityPlayerSP player = Minecraft.thePlayer();
        ItemStack itemStack = player.B$src$Lgg_vape_wrapper_impl_ItemStack_$impdvt();
        if (itemStack.isNull() || itemStack.getItem().isNull() || !ItemStackScoreUtil.h(itemStack.getItem())) {
            return false;
        }
        boolean targetingEntity = false;
        RayTraceResult rayTraceResult = RotationManager.INSTANCE.getExtendedReachRayTrace();
        if (rayTraceResult.isNotNull() && rayTraceResult.getTypeOfHit().equals(RayTraceResult_type.entity())) {
            targetingEntity = true;
        }
        if (this.targetStreak != 1 || !targetingEntity) {
            shouldBlock = false;
            int maxTargetStreak = 3;
            if (this.targetStreak >= maxTargetStreak) {
                this.targetStreak = 0;
            }
        }
        this.targetStreak = targetingEntity ? ++this.targetStreak : 1;
        return shouldBlock;
    }

    @Override
    public boolean shouldBlock() {
        return this.shouldBlockSwordUse();
    }
}
