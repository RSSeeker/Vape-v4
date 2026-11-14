package gg.vape.module.blatant.disabler;

import gg.vape.Vape;
import gg.vape.event.impl.EventPacketSend;
import gg.vape.mapping.MappedClasses;
import gg.vape.utils.network.AnticheatProfile;
import gg.vape.utils.network.OutboundPacketRule;
import gg.vape.wrapper.impl.Packet;

/**
 * Grim BadPacketsA：同一槽位的重复切槽包会被判定为异常，直接取消掉。
 *
 * <p>正常客户端只在槽位变化时发 C09/ServerboundSetCarriedItem，收到与上一次
 * 相同的槽位说明是模块（自动切槽、吃药、补货等）叠加发出的重复包。</p>
 */
public class DuplicateSlotRule
implements OutboundPacketRule {
    private int lastSlot = Integer.MIN_VALUE;

    @Override
    public String getRuleName() {
        return "Grim Duplicate Slot";
    }

    @Override
    public AnticheatProfile getProfile() {
        return AnticheatProfile.GRIM;
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
        boolean duplicate = slot == this.lastSlot;
        this.lastSlot = slot;
        if (duplicate) {
            event.setCancelled(true);
            return true;
        }
        return false;
    }
}
