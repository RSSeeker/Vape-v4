package gg.vape.utils.network;

import gg.vape.event.EventHandler;
import gg.vape.event.EventListener;
import gg.vape.event.impl.EventPacketSend;
import gg.vape.event.impl.EventPreTick;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 出站包统一管线。
 *
 * <p>所有「发出前」的封包改写/取消/补发规则都注册到这里，模块只负责注册与开关，
 * 不再各自实现 {@code EventPacketSend} 监听，避免多个模块互相竞争优先级。</p>
 *
 * <p>规则内部自行补发的包（例如 ACA Fast Switch 补中间槽）会再次触发
 * {@link EventPacketSend}，此时 {@code dispatching} 为 {@code true}，管线直接跳过，
 * 防止规则递归处理自己生成的包。</p>
 */
public class OutboundPacketPipeline
implements EventListener {
    private static final List<OutboundPacketRule> RULES = new CopyOnWriteArrayList<OutboundPacketRule>();
    private static final List<DelayedPacket> DELAYED = new CopyOnWriteArrayList<DelayedPacket>();
    private static volatile AnticheatProfile selectedProfile = AnticheatProfile.ALL;
    private static volatile boolean dispatching;

    public static void setSelectedProfile(AnticheatProfile profile) {
        selectedProfile = profile == null ? AnticheatProfile.ALL : profile;
    }

    public static AnticheatProfile getSelectedProfile() {
        return selectedProfile;
    }

    public static void register(OutboundPacketRule rule) {
        if (rule != null && !RULES.contains(rule)) {
            RULES.add(rule);
        }
    }

    public static void unregister(OutboundPacketRule rule) {
        RULES.remove(rule);
    }

    public static int getRuleCount() {
        return RULES.size();
    }

    /**
     * 延迟发送一个出站包：调用方应同时取消原事件。
     */
    public static void scheduleDelayed(EventPacketSend event, long delayMillis) {
        PacketDispatchTask dispatch = new PacketDispatchTask(event.getPacket(), true, event.getNetworkManager());
        DELAYED.add(new DelayedPacket(new TimedPacketDispatchTask(dispatch, null), Math.max(0L, delayMillis)));
    }

    public static int getDelayedCount() {
        return DELAYED.size();
    }

    @EventHandler
    public void onPreTick(EventPreTick event) {
        if (DELAYED.isEmpty()) {
            return;
        }
        Iterator<DelayedPacket> iterator = DELAYED.iterator();
        while (iterator.hasNext()) {
            DelayedPacket delayed = iterator.next();
            if (!delayed.task.j(delayed.delayMillis)) {
                continue;
            }
            iterator.remove();
            try {
                delayed.task.z().t();
            }
            catch (Throwable throwable) {
                gg.vape.Vape.logError("延迟发包失败: " + throwable);
            }
        }
    }

    private static final class DelayedPacket {
        private final TimedPacketDispatchTask task;
        private final long delayMillis;

        private DelayedPacket(TimedPacketDispatchTask task, long delayMillis) {
            this.task = task;
            this.delayMillis = delayMillis;
        }
    }

    @EventHandler
    public void onPacketSend(EventPacketSend event) {
        if (dispatching || RULES.isEmpty()) {
            return;
        }
        AnticheatProfile profile = selectedProfile;
        dispatching = true;
        try {
            for (OutboundPacketRule rule : RULES) {
                if (!profile.covers(rule.getProfile())) {
                    continue;
                }
                if (rule.handle(event)) {
                    break;
                }
            }
        } finally {
            dispatching = false;
        }
    }
}
