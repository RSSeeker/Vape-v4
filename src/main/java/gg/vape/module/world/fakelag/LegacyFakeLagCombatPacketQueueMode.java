package gg.vape.module.world.fakelag;

import gg.vape.event.EventHandler;
import gg.vape.event.EventPriority;
import gg.vape.event.impl.EventPacketSend;
import gg.vape.event.impl.EventPreTick;
import gg.vape.event.impl.EventTickBase;
import gg.vape.mapping.MappedClasses;
import gg.vape.utils.network.ImmediateResponsePackets;
import gg.vape.module.Mod;
import gg.vape.module.SubModule;
import gg.vape.combat.AttackPacketTimingTracker;
import gg.vape.module.control.PrimaryActionControlClaim;
import gg.vape.module.control.SharedModuleControlClaims;
import gg.vape.module.world.FakeLag;
import gg.vape.utils.SleepUtil;
import gg.vape.utils.network.PacketDispatchGuard;
import gg.vape.wrapper.impl.Entity;
import gg.vape.wrapper.impl.EntityLivingBase;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.NetHandlerPlayClientImpl;
import gg.vape.wrapper.impl.Packet;
import gg.vape.wrapper.impl.UseEntityPacketBridge;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

public class LegacyFakeLagCombatPacketQueueMode
extends SubModule<FakeLag> {
    private final Queue<EventPacketSend> queuedPackets;
    private long flushDeadline;
    private final PacketDispatchGuard dispatchGuard = PacketDispatchGuard.b;
    private final AtomicBoolean flushing;
    private EntityLivingBase targetEntity;
    private final PrimaryActionControlClaim controlClaim;

    public LegacyFakeLagCombatPacketQueueMode(Mod parent, String name) {
        super(parent, name);
        this.queuedPackets = new LinkedList<EventPacketSend>();
        this.flushing = new AtomicBoolean(false);
        this.controlClaim = SharedModuleControlClaims.primaryAction;
    }

    @Override
    public String getDetailedSuffix() {
        String suffix = "Repel " + ((FakeLag)this.getParent()).delay.getDisplayValue() + "ms";
        if (!this.queuedPackets.isEmpty()) {
            suffix = "\u00a7c" + suffix;
        }
        return suffix;
    }

    @EventHandler
    public void onTick(EventPreTick eventPreTick) {
        NetHandlerPlayClientImpl netHandlerPlayClientImpl = Minecraft.N();
        EntityLivingBase target = this.targetEntity;
        if (target != null
                && target.c$src$I$15a9iwo() > 0
                && target.c$src$I$15a9iwo() <= AttackPacketTimingTracker.INSTANCE.getExpectedHurtTimeTicks()
                && !this.queuedPackets.isEmpty()
                && netHandlerPlayClientImpl.isNotNull()) {
            this.flushPackets();
        }
    }


    private void flushPackets() {
        if (this.queuedPackets.isEmpty() || !Thread.currentThread().equals(EventTickBase.PRE_TICK_EXECUTOR.getOwnerThread())) {
            return;
        }
        this.flushing.set(true);
        this.queuedPackets.forEach(this::dispatchQueuedPacket);
        this.queuedPackets.clear();
        this.flushing.set(false);
    }

    private void dispatchQueuedPacket(EventPacketSend eventPacketSend) {
        this.dispatchGuard.o(eventPacketSend);
    }

    @EventHandler(priority=EventPriority.LOW)
    public void onPacketSend(EventPacketSend eventPacketSend) {
        Entity entity;
        UseEntityPacketBridge useEntityPacketBridge;
        if (this.controlClaim.isClaimed()) {
            this.flushPackets();
            return;
        }
        Packet packet = eventPacketSend.getPacket();
        if (this.dispatchGuard.R(packet)) {
            return;
        }
        // KeepAlive/pong 不进队列，否则服务端会记 skipped 并判定异常。
        if (ImmediateResponsePackets.isImmediateResponse(packet)) {
            return;
        }
        while (this.flushing.get()) {
            SleepUtil.sleep(10L);
        }
        if (!this.queuedPackets.isEmpty()) {
            UseEntityPacketBridge useEntityPacketBridge2;
            boolean shouldFlush = System.currentTimeMillis() >= this.flushDeadline;
            if (UseEntityPacketBridge.isUseEntityPacket(packet) && (useEntityPacketBridge2 = new UseEntityPacketBridge(packet)).isAttack()) {
                shouldFlush = true;
            }
            if (packet.isInstance(MappedClasses.u7)) {
                shouldFlush = true;
            }
            if (!Thread.currentThread().equals(EventTickBase.PRE_TICK_EXECUTOR.getOwnerThread())) {
                shouldFlush = false;
            }
            if (shouldFlush) {
                this.queuedPackets.add(eventPacketSend);
                eventPacketSend.setCancelled(true);
                this.flushPackets();
                return;
            }
            this.queuedPackets.add(eventPacketSend);
            eventPacketSend.setCancelled(true);
            return;
        }
        if (UseEntityPacketBridge.isUseEntityPacket(packet) && (useEntityPacketBridge = new UseEntityPacketBridge(packet)).isAttack() && (entity = Minecraft.theWorld().V(useEntityPacketBridge.getEntityId())).isInstance(MappedClasses.zm)) {
            this.targetEntity = new EntityLivingBase(entity);
            long delay = ((Double)((FakeLag)this.getParent()).delay.getValue()).longValue() + (long)ThreadLocalRandom.current().nextInt(100);
            if (this.targetEntity.c$src$I$15a9iwo() > 0 && this.targetEntity.c$src$I$15a9iwo() <= (int)Math.ceil((double)delay / 50.0)) {
                this.queuedPackets.add(eventPacketSend);
                this.flushDeadline = System.currentTimeMillis() + delay;
                eventPacketSend.setCancelled(true);
            }
        }
        if (this.queuedPackets.isEmpty()) {
            this.dispatchGuard.J(packet);
        }
    }
}
