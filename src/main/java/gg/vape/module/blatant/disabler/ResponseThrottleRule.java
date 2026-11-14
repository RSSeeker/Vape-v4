package gg.vape.module.blatant.disabler;

import gg.vape.event.impl.EventPacketSend;
import gg.vape.mapping.MappedClasses;
import gg.vape.utils.network.AnticheatProfile;
import gg.vape.utils.network.OutboundPacketRule;
import gg.vape.wrapper.impl.Packet;

/**
 * Themis / ACA：重复的 pong 应答。
 *
 * <p>服务端 ping 的应答本身有节流（约 1 秒一次），一旦 60ms 内出现第二条 pong，
 * 说明队列被补发/重放，丢弃多余的那条即可消除 {@code packetanalysis.parts.duplicatepong}。</p>
 *
 * <p><b>不要</b>对 {@code C0FPacketConfirmTransaction} 做去重：每个确认包对应服务端
 * 分配的唯一 transaction id，丢掉任意一条都会让服务端看到 id 跳跃（Grim
 * {@code TransactionOrder} / {@code skipped}），比重复更可疑。</p>
 */
public class ResponseThrottleRule
implements OutboundPacketRule {
    private static final long WINDOW_MILLIS = 60L;

    private long lastPongMillis;

    public void reset() {
        this.lastPongMillis = 0L;
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
        if (packet.isInstance(MappedClasses.zy)) {
            return false;
        }
        long now = System.currentTimeMillis();
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
