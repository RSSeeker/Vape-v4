package gg.vape.utils.network;

import gg.vape.event.impl.EventPacketSend;

/**
 * 一条出站包规则。
 *
 * <p>规则在 {@link OutboundPacketPipeline} 里按注册顺序执行。返回 {@code true}
 * 表示世界已经处理完毕（通常是取消了原始包并自行补发了替身包），管线不再继续
 * 询问后面的规则。</p>
 */
public interface OutboundPacketRule {
    /**
     * 人类可读的名字，用于日志/调试。
     */
    String getRuleName();

    /**
     * 该规则针对的反作弊引擎，{@code null} 表示与引擎无关。
     */
    AnticheatProfile getProfile();

    /**
     * @return {@code true} 表示原始包已经被本规则消化（通常已 setCancelled）。
     */
    boolean handle(EventPacketSend event);
}
