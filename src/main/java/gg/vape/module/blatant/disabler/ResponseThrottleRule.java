package gg.vape.module.blatant.disabler;

import gg.vape.event.impl.EventPacketSend;
import gg.vape.mapping.MappedClasses;
import gg.vape.utils.network.AnticheatProfile;
import gg.vape.utils.network.OutboundPacketRule;
import gg.vape.wrapper.impl.Packet;

/**
 * Themis / ACA：应答类包（pong、confirm transaction）重复或成串发送。
 *
 * <p>服务端 ping 的应答本身有节流（约 1 秒一次），一旦 60ms 内出现第二条同类包，
 * 说明队列被补发/重放。这里把多余的那条丢掉，保留第一条，避免
 * {@code packetanalysis.parts.duplicatepong} 与 {@code TransactionOrder}。</p>
 */
public class ResponseThrottleRule
implements OutboundPacketRule {
    private static final long WINDOW_MILLIS = 60L;

    private long lastPongMillis;
    private long lastTransactionMillis;

    public void reset() {
        this.lastPongMillis = 0L;
        this.lastTransactionMillis = 0L;
    }

    @Override
    public String getRuleName() {
        return "Themis Response Throttle";
    }

    @Override
    public AnticheatProfile getProfile() {
        return AnticheatProfile.ALL;
    }

    @Override
    public boolean handle(EventPacketSend event) {
        Packet packet = event.getPacket();
        if (packet == null || packet.isNull()) {
            return false;
        }
        Object handle = packet.getObject();
        if (handle == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (packet.isInstance(MappedClasses.zy)) {
            if (now - this.lastTransactionMillis < WINDOW_MILLIS) {
                event.setCancelled(true);
                return true;
            }
            this.lastTransactionMillis = now;
            return false;
        }
        String className = handle.getClass().getName();
        if (className != null && (className.contains("Pong") || className.endsWith("ServerboundPongPacket"))) {
            if (now - this.lastPongMillis < WINDOW_MILLIS) {
                event.setCancelled(true);
                return true;
            }
            this.lastPongMillis = now;
        }
        return false;
    }
}
