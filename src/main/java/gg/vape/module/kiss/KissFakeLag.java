package gg.vape.module.kiss;

import gg.vape.event.EventHandler;
import gg.vape.event.impl.DelayedPacketSendEntry;
import gg.vape.event.impl.EventPacketReceive;
import gg.vape.event.impl.EventPacketSend;
import gg.vape.event.impl.EventPreTick;
import gg.vape.mapping.MappedClasses;
import gg.vape.module.Category;
import gg.vape.module.Mod;
import gg.vape.unmap.ModeOption;
import gg.vape.unmap.ModeSelection;
import gg.vape.utils.network.PacketDispatchGuard;
import gg.vape.value.BooleanValue;
import gg.vape.value.ModeValue;
import gg.vape.value.NumberValue;
import gg.vape.wrapper.impl.Entity;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.Packet;
import java.util.ArrayList;
import java.util.List;

/**
 * FakeLag mode ported from KissClient's FakeLag module.
 *
 * KissClient buffers outgoing movement packets, releases them either after
 * {@code Delay} milliseconds or as soon as a configurable trigger fires
 * (attack / interact / incoming damage / server teleport). After each release
 * packets flow freely for {@code Recoil Time} milliseconds before buffering
 * starts again. {@code Dynamic} mode only buffers while another player is
 * within {@code Range} blocks.
 */
public class KissFakeLag
extends Mod {
    private final ModeOption dynamicMode = new ModeOption("Dynamic");
    private final ModeOption constantMode = new ModeOption("Constant");
    private final ModeValue modeValue;
    private final NumberValue range = NumberValue.create((Object)this, "Range", "#", " blocks", 1.0, 4.5, 10.0, 0.5);
    private final NumberValue delay = NumberValue.create((Object)this, "Delay", "#", "ms", 50.0, 160.0, 1000.0, 10.0);
    private final NumberValue recoilTime = NumberValue.create((Object)this, "Recoil Time", "#", "ms", 0.0, 250.0, 1000.0, 10.0);
    private final BooleanValue flushOnAttack = BooleanValue.create((Object)this, "Flush On Attack", true);
    private final BooleanValue flushOnInteract = BooleanValue.create((Object)this, "Flush On Interact", true);
    private final BooleanValue flushOnDamage = BooleanValue.create((Object)this, "Flush On Damage", true);
    private final List<DelayedPacketSendEntry> bufferedPackets = new ArrayList<DelayedPacketSendEntry>();
    private final PacketDispatchGuard dispatchGuard = PacketDispatchGuard.b;
    private long lastFlushTime;
    private boolean playerNearby;

    private static final long MOD_ID = -3384277592044188671L;

    public KissFakeLag() {
        super("Kiss FakeLag", (int)MOD_ID, Category.KISS, "FakeLag logic ported from KissClient: buffers movement packets, releases on delay or on attack/interact/damage triggers.");
        this.modeValue = ModeValue.create((Object)this, "Mode", (ModeSelection)this.dynamicMode, this.dynamicMode, this.constantMode);
        this.addValue(this.modeValue, this.range, this.delay, this.recoilTime, this.flushOnAttack, this.flushOnInteract, this.flushOnDamage);
    }

    private boolean isDynamic() {
        return this.modeValue.getValue() == this.dynamicMode;
    }

    private long numberValue(NumberValue value) {
        return ((Double)value.getValue()).longValue();
    }

    private boolean isMovementPacket(Packet packet) {
        return packet.isInstance(MappedClasses.qD);
    }

    private boolean isAttackPacket(Packet packet) {
        return packet.isInstance(MappedClasses.Fa) || packet.isInstance(MappedClasses.VF);
    }

    private boolean isInteractPacket(Packet packet) {
        return packet.isInstance(MappedClasses.YB) || packet.isInstance(MappedClasses.DN);
    }

    private boolean isSelfDamage(Packet packet) {
        if (packet.isInstance(MappedClasses.YX)) {
            return true;
        }
        return packet.isInstance(MappedClasses.qN);
    }

    private void flushBufferedPackets() {
        if (this.bufferedPackets.isEmpty()) {
            return;
        }
        for (DelayedPacketSendEntry entry : this.bufferedPackets) {
            try {
                this.dispatchGuard.o(entry.getEvent());
            }
            catch (Exception ignored) {
            }
        }
        this.bufferedPackets.clear();
        this.lastFlushTime = System.currentTimeMillis();
    }

    private boolean hasPlayerWithinRange() {
        if (Minecraft.thePlayer().isNull() || Minecraft.theWorld().isNull()) {
            return false;
        }
        double range = (Double)this.range.getValue();
        Object self = Minecraft.thePlayer().getObject();
        Entity selfEntity = new Entity(self);
        for (Object object : Minecraft.theWorld().X()) {
            if (object == null || object == self) {
                continue;
            }
            if (!MappedClasses.Yl.isInstance(object)) {
                continue;
            }
            Entity entity = new Entity(object);
            if (selfEntity.getDistanceToEntity(entity) <= range) {
                return true;
            }
        }
        return false;
    }

    @EventHandler
    public void onPreTick(EventPreTick event) {
        if (Minecraft.thePlayer().isNull() || Minecraft.theWorld().isNull()) {
            return;
        }
        this.playerNearby = this.hasPlayerWithinRange();
        if (!this.bufferedPackets.isEmpty() && System.currentTimeMillis() - this.lastFlushTime >= this.numberValue(this.delay)) {
            this.flushBufferedPackets();
        }
    }

    @EventHandler
    public void onPacketSend(EventPacketSend event) {
        if (event.isCanceled() || event.wasModified()) {
            return;
        }
        if (Minecraft.thePlayer().isNull() || Minecraft.theWorld().isNull()) {
            return;
        }
        Packet packet = event.getPacket();
        if (packet == null || packet.isInstance(MappedClasses.VP)) {
            return;
        }
        if (this.dispatchGuard.R(packet)) {
            return;
        }
        if (this.isMovementPacket(packet)) {
            if (this.isDynamic() && !this.playerNearby) {
                this.flushBufferedPackets();
                return;
            }
            if (System.currentTimeMillis() - this.lastFlushTime >= this.numberValue(this.recoilTime)) {
                this.bufferedPackets.add(new DelayedPacketSendEntry(event));
                event.setCancelled(true);
            }
            return;
        }
        if (this.flushOnAttack.getEffectiveValue().booleanValue() && this.isAttackPacket(packet)) {
            this.flushBufferedPackets();
            return;
        }
        if (this.flushOnInteract.getEffectiveValue().booleanValue() && this.isInteractPacket(packet)) {
            this.flushBufferedPackets();
        }
    }

    @EventHandler
    public void onPacketReceive(EventPacketReceive event) {
        if (this.bufferedPackets.isEmpty()) {
            return;
        }
        if (Minecraft.thePlayer().isNull()) {
            return;
        }
        Packet packet = event.getPacket();
        if (packet == null) {
            return;
        }
        if (packet.isInstance(MappedClasses.zw)) {
            this.flushBufferedPackets();
            return;
        }
        if (this.flushOnDamage.getEffectiveValue().booleanValue() && this.isSelfDamage(packet)) {
            this.flushBufferedPackets();
        }
    }

    @Override
    public void onDisable() {
        this.flushBufferedPackets();
    }

    @Override
    public String getDetailedSuffix() {
        return this.modeValue.getDisplayValue() + " " + this.delay.getDisplayValue() + "ms";
    }
}
