package gg.vape.module.blatant.disabler;

import gg.vape.Vape;
import gg.vape.event.impl.EventPacketSend;
import gg.vape.mapping.MappedClasses;
import gg.vape.mapping.mappings.MCPacketPlayer;
import gg.vape.utils.network.AnticheatProfile;
import gg.vape.utils.network.OutboundPacketRule;
import gg.vape.wrapper.impl.Packet;

/**
 * Grim AimDuplicateLook：同一 tick 内重复发出完全相同的视角包会被记为重复瞄准。
 *
 * <p>原版只在视角变化时发包，所以重复的纯视角包一定来自客户端模块（旋转控制、
 * 静默自瞄等）。这里把 90ms 内 yaw/pitch 完全相同的纯旋转包丢掉；带位移的
 * {@code C06PacketPlayerPosLook} 不处理，避免影响移动同步。</p>
 */
public class RotationGuardRule
implements OutboundPacketRule {
    private static final long DUPLICATE_WINDOW_NANOS = 90000000L;
    private static final float EPSILON = 1.0E-4f;

    private long lastLookNanos;
    private float lastYaw;
    private float lastPitch;
    private boolean hasLast;

    public void reset() {
        this.hasLast = false;
        this.lastLookNanos = 0L;
    }

    @Override
    public String getRuleName() {
        return "Grim Rotation Guard";
    }

    @Override
    public AnticheatProfile getProfile() {
        return AnticheatProfile.GRIM;
    }

    @Override
    public boolean handle(EventPacketSend event) {
        Packet packet = event.getPacket();
        if (packet == null || packet.isNull() || !packet.isInstance(MappedClasses.qD)) {
            return false;
        }
        Object handle = packet.getObject();
        if (handle == null) {
            return false;
        }
        MCPacketPlayer mapper = Vape.INSTANCE.getMappings().Dq;
        if (mapper == null) {
            return false;
        }
        float yaw;
        float pitch;
        boolean hasPosition;
        try {
            yaw = mapper.getYaw(handle);
            pitch = mapper.getPitch(handle);
            hasPosition = mapper.hasPosition(handle);
        }
        catch (Throwable throwable) {
            // 某些版本上没有旋转字段映射，放弃这条规则即可。
            return false;
        }
        if (hasPosition) {
            return false;
        }
        long now = System.nanoTime();
        if (this.hasLast
                && now - this.lastLookNanos < DUPLICATE_WINDOW_NANOS
                && Math.abs(yaw - this.lastYaw) < EPSILON
                && Math.abs(pitch - this.lastPitch) < EPSILON) {
            event.setCancelled(true);
            return true;
        }
        this.hasLast = true;
        this.lastLookNanos = now;
        this.lastYaw = yaw;
        this.lastPitch = pitch;
        return false;
    }
}
