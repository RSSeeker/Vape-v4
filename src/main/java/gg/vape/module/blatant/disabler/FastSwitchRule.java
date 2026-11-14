package gg.vape.module.blatant.disabler;

import gg.vape.Vape;
import gg.vape.event.impl.EventPacketSend;
import gg.vape.mapping.MappedClasses;
import gg.vape.utils.network.AnticheatProfile;
import gg.vape.utils.network.OutboundPacketRule;
import gg.vape.wrapper.impl.CPacketHeldItemChange;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.Packet;

/**
 * ACA Fast Switch：一次跳多个槽位的切槽包会被判为异常。
 *
 * <p>把原始包取消，按 1..N 的顺序把中间的槽位逐个补发出去，最后落到目标槽，
 * 使服务端看到的是一串合法的相邻切槽序列。</p>
 */
public class FastSwitchRule
implements OutboundPacketRule {
    private static final int HOTBAR_SIZE = 9;

    private int lastSlot = Integer.MIN_VALUE;

    @Override
    public String getRuleName() {
        return "ACA Fast Switch";
    }

    @Override
    public AnticheatProfile getProfile() {
        return AnticheatProfile.ACA;
    }

    public void reset() {
        this.lastSlot = Integer.MIN_VALUE;
    }

    @Override
    public boolean handle(EventPacketSend event) {
        Packet packet = event.getPacket();
        if (packet == null || packet.isNull() || !packet.isInstance(MappedClasses.e)) {
            return false;
        }
        int slot = Vape.INSTANCE.getMappings().Ck.getSelectedSlot(packet.getObject());
        if (this.lastSlot == Integer.MIN_VALUE) {
            this.lastSlot = slot;
            return false;
        }
        int delta = slot - this.lastSlot;
        if (slot < 0 || slot >= HOTBAR_SIZE || Math.abs(delta) <= 1) {
            this.lastSlot = slot;
            return false;
        }
        int from = this.lastSlot;
        this.lastSlot = slot;
        event.setCancelled(true);
        EntityPlayerSP player = Minecraft.thePlayer();
        if (player == null || player.isNull() || player.sendQueue() == null) {
            return true;
        }
        int step = delta > 0 ? 1 : -1;
        for (int intermediate = from + step;; intermediate += step) {
            player.sendQueue().addToSendQueue(CPacketHeldItemChange.create(intermediate));
            if (intermediate == slot) {
                break;
            }
        }
        return true;
    }
}
