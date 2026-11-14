package gg.vape.module.blatant.disabler;

import gg.vape.event.impl.EventPacketSend;
import gg.vape.mapping.MappedClasses;
import gg.vape.utils.network.AnticheatProfile;
import gg.vape.utils.network.OutboundPacketPipeline;
import gg.vape.utils.network.OutboundPacketRule;
import gg.vape.value.NumberValue;
import gg.vape.wrapper.impl.Packet;

/**
 * ACA Inventory Frequency：短时间内反复开关容器会被判定为异常频率。
 *
 * <p>把关闭容器的包延后一点点发出去（默认 150ms），服务端看到的开关间隔被拉开，
 * 但玩家侧没有任何操作延迟（界面已经关了，只是告知服务端的包慢了半拍）。</p>
 */
public class InventoryFrequencyRule
implements OutboundPacketRule {
    private final NumberValue delayValue;
    private long lastCloseMillis;

    public InventoryFrequencyRule(NumberValue delayValue) {
        this.delayValue = delayValue;
    }

    @Override
    public String getRuleName() {
        return "ACA Inventory Frequency";
    }

    @Override
    public AnticheatProfile getProfile() {
        return AnticheatProfile.ACA;
    }

    @Override
    public boolean handle(EventPacketSend event) {
        Packet packet = event.getPacket();
        if (packet == null || packet.isNull() || !packet.isInstance(MappedClasses.l7)) {
            return false;
        }
        long delay = (long)(double)this.delayValue.getValue();
        if (delay <= 0L) {
            return false;
        }
        long now = System.currentTimeMillis();
        // 上一次关容器还在延迟队列里时，重复的关闭包直接丢掉：原版偶尔会在界面
        // 关闭/切换时连发两个，服务端看到的就是"同一次开关发了两次 close"。
        if (now - this.lastCloseMillis < 400L) {
            event.setCancelled(true);
            return true;
        }
        this.lastCloseMillis = now;
        event.setCancelled(true);
        OutboundPacketPipeline.scheduleDelayed(event, delay);
        return true;
    }
}
