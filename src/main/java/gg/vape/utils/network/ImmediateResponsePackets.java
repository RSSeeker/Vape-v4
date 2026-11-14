package gg.vape.utils.network;

import gg.vape.mapping.MappedClasses;
import gg.vape.wrapper.impl.Packet;

/**
 * 服务端「有应答期限」的包。
 *
 * <p>KeepAlive 超时服务器会踢人；1.17+ 的 {@code ServerboundPongPacket} 是对服务端
 * ping 的应答，被 FakeLag/Blink 压住会让反作弊记「skipped」，累积后触发
 * TransactionOrder / duplicatepong 一类的判定。这些包必须绕过所有出站队列。</p>
 */
public final class ImmediateResponsePackets {
    private ImmediateResponsePackets() {
    }

    public static boolean isImmediateResponse(Packet packet) {
        if (packet == null || packet.isNull()) {
            return false;
        }
        if (packet.isInstance(MappedClasses.VP)) {
            return true;
        }
        Object handle = packet.getObject();
        if (handle == null) {
            return false;
        }
        String className = handle.getClass().getName();
        if (className == null) {
            return false;
        }
        // 1.17+ mojmap / srg 运行时的 pong 应答（Fabric intermediary 下名字是 class_xxx，无法按名识别）。
        return className.contains("Pong") || className.endsWith("ServerboundPongPacket");
    }
}
