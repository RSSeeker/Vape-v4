package gg.vape.module.blatant.disabler;

import gg.vape.event.impl.EventPacketSend;
import gg.vape.mapping.MappedClasses;
import gg.vape.utils.network.AnticheatProfile;
import gg.vape.utils.network.OutboundPacketRule;
import gg.vape.wrapper.impl.Packet;

/**
 * ACA ContainerOrder：关闭容器之后短时间内又出现针对该容器的点击包。
 *
 * <p>原版关界面时会先发 close，再把最后一次点击发出去；频率补丁把 close 延迟后，
 * 服务端就可能先收到点击再收到 close。这里记住 close 被处理的时间，300ms 内的
 * 容器点击包直接丢弃（此时客户端界面已经关闭，玩家本来也点不了东西）。</p>
 */
public class ContainerOrderRule
implements OutboundPacketRule {
    private static final long CLICK_AFTER_CLOSE_WINDOW_MILLIS = 300L;

    private long lastCloseMillis;

    public void reset() {
        this.lastCloseMillis = 0L;
    }

    @Override
    public String getRuleName() {
        return "ACA Container Order";
    }

    @Override
    public AnticheatProfile getProfile() {
        return AnticheatProfile.ACA;
    }

    @Override
    public boolean handle(EventPacketSend event) {
        Packet packet = event.getPacket();
        if (packet == null || packet.isNull()) {
            return false;
        }
        if (packet.isInstance(MappedClasses.l7)) {
            this.lastCloseMillis = System.currentTimeMillis();
            // 交给 InventoryFrequencyRule 处理延迟/去重。
            return false;
        }
        if (packet.isInstance(MappedClasses.F9)
                && System.currentTimeMillis() - this.lastCloseMillis < CLICK_AFTER_CLOSE_WINDOW_MILLIS) {
            event.setCancelled(true);
            return true;
        }
        return false;
    }
}
