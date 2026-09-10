package gg.vape.module.combat.blockhit;

import gg.vape.event.EventHandler;
import gg.vape.event.EventPriority;
import gg.vape.event.impl.EventPacketSend;
import gg.vape.event.impl.EventPreTick;
import gg.vape.event.impl.EventTickBase;
import gg.vape.input.InputEventDispatcher;
import gg.vape.input.KeyBindingInputState;
import gg.vape.mapping.MappedClasses;
import gg.vape.module.Mod;
import gg.vape.combat.AttackPacketTimingTracker;
import gg.vape.module.control.PrimaryActionControlClaim;
import gg.vape.module.control.SharedModuleControlClaims;
import gg.vape.module.combat.BlockHit;
import gg.vape.utils.ItemStackScoreUtil;
import gg.vape.utils.TimerUtil;
import gg.vape.utils.network.PacketDispatchGuard;
import gg.vape.value.RandomValue;
import gg.vape.wrapper.impl.CPacketPlayerDigging;
import gg.vape.wrapper.impl.EntityLivingBase;
import gg.vape.wrapper.impl.ForgeVersion;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.NetHandlerPlayClientImpl;
import gg.vape.wrapper.impl.NetworkManager;
import gg.vape.wrapper.impl.Packet;
import gg.vape.wrapper.impl.PlayerDiggingAction;
import java.util.LinkedList;
import java.util.Queue;

public class LagBlockHitMode
extends BlockHitMode {
    private final Queue<EventPacketSend> queuedPackets;
    private int delayMillis;
    private final PrimaryActionControlClaim primaryActionClaim;
    private boolean blockCycleCompleted;
    private final TimerUtil bufferTimer;
    private boolean blocking;
    private final RandomValue delay;
    private boolean bufferingPackets;
    private final PacketDispatchGuard dispatchGuard = PacketDispatchGuard.b;

    @EventHandler
    public void onTick(EventPreTick event) {
        if (Minecraft.thePlayer().isNull()) {
            return;
        }
        if (!InputEventDispatcher.getInstance().getFocusState().isFocused()) {
            return;
        }
        if (this.findTarget() != null) {
            boolean shouldBlock = this.isHoldingSword();
            if (((BlockHit)this.getParent()).requiresMouseDown() && !gg.vape.config.ClientSettings.isUseItemButtonDown()) {
                shouldBlock = false;
            }
            if (event.getThePlayer().c$src$I$15a9iwo() > AttackPacketTimingTracker.INSTANCE.getExpectedHurtTimeTicks() + 1) {
                this.setBlocking(false);
                this.blockCycleCompleted = false;
                return;
            }
            if (shouldBlock) {
                if (this.bufferingPackets && this.bufferTimer.hasTimeElapsed(this.delayMillis - 50)) {
                    this.setBlocking(true);
                    return;
                }
                if (!this.blockCycleCompleted) {
                    if (!this.blocking) {
                        this.setBlocking(true);
                    } else {
                        this.setBlocking(false);
                        this.blockCycleCompleted = true;
                    }
                } else if (!this.blocking && !event.getThePlayer().o$src$Z$1iprrmi()) {
                    this.blockCycleCompleted = false;
                }
            } else if (this.blocking) {
                this.setBlocking(false);
                this.blockCycleCompleted = false;
            }
            return;
        }
        if (this.isHoldingSword() && ((BlockHit)this.getParent()).requiresMouseDown() && ((BlockHit)this.getParent()).ignoreManualBlock.getEffectiveValue().booleanValue() && !this.bufferingPackets && gg.vape.config.ClientSettings.isUseItemButtonDown() && Minecraft.thePlayer().o$src$Z$1iprrmi()) {
            KeyBindingInputState.sendRightButtonUp();
            this.blocking = false;
            this.blockCycleCompleted = false;
            return;
        }
        if (event.getThePlayer().c$src$I$15a9iwo() > AttackPacketTimingTracker.INSTANCE.getExpectedHurtTimeTicks() + 1) {
            this.setBlocking(false);
            this.blockCycleCompleted = false;
            return;
        }
        if (this.blocking) {
            this.setBlocking(false);
            this.blockCycleCompleted = false;
        }
    }

    @Override
    public boolean isBlocking() {
        return this.blocking;
    }

    public void setBlocking(boolean blocking) {
        if (this.blocking != blocking) {
            this.blocking = blocking;
            if (blocking) {
                KeyBindingInputState.sendRightButtonDown();
            } else {
                KeyBindingInputState.sendRightButtonUp();
            }
        }
    }

    private boolean isReleaseUseItemPacket(Packet packet) {
        if (packet.isInstance(MappedClasses.DN)) {
            CPacketPlayerDigging diggingPacket = new CPacketPlayerDigging(packet);
            return ForgeVersion.MC_1_8_9.d() ? diggingPacket.getAction().equals(PlayerDiggingAction.releaseUseItem()) : diggingPacket.getLegacyActionId() == 5;
        }
        return false;
    }

    private void flushPackets() {
        if (!Thread.currentThread().equals(EventTickBase.PRE_TICK_EXECUTOR.getOwnerThread())) {
            return;
        }
        NetHandlerPlayClientImpl connection = Minecraft.thePlayer().sendQueue();
        NetworkManager networkManager = connection.a();
        PacketDispatchGuard.b(networkManager, this::flushQueuedPackets);
    }

    @Override
    public String getDetailedSuffix() {
        String displayText = "Lag " + this.delay.getDisplayValue() + "ms";
        if (this.bufferingPackets) {
            displayText = "\u00a7c" + displayText;
        }
        return displayText;
    }


    @EventHandler(priority=EventPriority.LOWEST)
    public void onPacketSend(EventPacketSend event) {
        if (event.isCanceled()) {
            return;
        }
        if (event.wasModified()) {
            return;
        }
        Packet packet = event.getPacket();
        if (this.dispatchGuard.R(packet)) {
            return;
        }
        if (Minecraft.thePlayer().isNull() || packet.isInstance(MappedClasses.VP)) {
            return;
        }
        if (!Thread.currentThread().equals(EventTickBase.PRE_TICK_EXECUTOR.getOwnerThread())) {
            return;
        }
        if (this.bufferingPackets) {
            boolean targetLost = this.findTarget() == null || !this.isHoldingSword();
            if (targetLost || this.bufferTimer.hasTimeElapsed(this.delayMillis)) {
                this.flushPackets();
                this.bufferingPackets = false;
                this.blockCycleCompleted = false;
            } else {
                this.queuedPackets.add(event);
                event.setCancelled(true);
            }
            return;
        }
        if (this.isReleaseUseItemPacket(packet)) {
            this.primaryActionClaim.markClaimed();
            this.queuedPackets.add(event);
            event.setCancelled(true);
            this.bufferingPackets = true;
            this.delayMillis = (int)this.delay.getRandomValue();
            this.bufferTimer.reset();
        }
        if (!this.bufferingPackets && this.queuedPackets.isEmpty()) {
            this.primaryActionClaim.clearClaimed();
            this.dispatchGuard.J(packet);
        }
    }

    public EntityLivingBase findTarget() {
        BlockHit parent = (BlockHit)this.getParent();
        return parent.findTarget((Double)parent.targetAngle.getValue(), (Double)parent.targetDistance.getValue());
    }

    private boolean isHoldingSword() {
        if (Minecraft.currentScreen().isNotNull()) {
            return false;
        }
        return Minecraft.thePlayer().getHeldItemHand().isNotNull() && ItemStackScoreUtil.h(Minecraft.thePlayer().getHeldItemHand().getItem());
    }

    private void flushQueuedPackets() {
        for (EventPacketSend event : this.queuedPackets) {
            this.dispatchGuard.o(event);
        }
        this.queuedPackets.clear();
    }

    public LagBlockHitMode(Mod parent, String name) {
        super(parent, name);
        this.queuedPackets = new LinkedList<EventPacketSend>();
        this.delay = RandomValue.create(this, "Delay", "#", "", 0.0, 50.0, 100.0, 500.0);
        this.bufferTimer = new TimerUtil();
        this.primaryActionClaim = SharedModuleControlClaims.primaryAction;
        this.addValue(this.delay);
        this.primaryActionClaim.setPriority(this, 5);
    }
}
