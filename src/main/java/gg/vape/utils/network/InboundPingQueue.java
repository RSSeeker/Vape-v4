package gg.vape.utils.network;

import gg.vape.event.EventHandler;
import gg.vape.event.EventListener;
import gg.vape.event.impl.EventPacketReceive;
import gg.vape.event.impl.EventPreTick;
import gg.vape.mapping.MappedClasses;
import gg.vape.wrapper.impl.Packet;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 入站心跳扣留队列（Themis Blink 角度）。
 *
 * <p>当 Blink / FakeLag 把出站包压住时，服务端发来的 KeepAlive 如果立刻回，
 * 服务端会看到「客户端还在正常响应、但其它包全停了」，反而更容易被判成 Blink。
 * 这里把入站 KeepAlive 扣住一小段时间，再按原顺序交给 NetHandler 处理，
 * 让服务端的观察变成「整条链路一起延迟」。</p>
 *
 * <p>默认关闭，由 Disabler 的 <code>Themis Hold KeepAlive</code> 开关启用；
 * 扣留上限 {@link #getHoldMillis()}（默认 500ms，超过后即使没有 tick 也会放行），
 * 队列上限 {@link #MAX_HELD}，防止异常累积。</p>
 */
public final class InboundPingQueue
implements EventListener {
    private static final int MAX_HELD = 20;
    private static final List<HeldPacket> HELD = new ArrayList<HeldPacket>();
    private static volatile boolean enabled;
    private static volatile long holdMillis = 500L;

    public InboundPingQueue() {
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        if (!value) {
            HELD.clear();
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setHoldMillis(long value) {
        holdMillis = Math.max(50L, Math.min(2000L, value));
    }

    public static long getHoldMillis() {
        return holdMillis;
    }

    public static void flush() {
        HELD.clear();
    }

    @EventHandler
    public void onPacketReceive(EventPacketReceive event) {
        if (!enabled) {
            return;
        }
        Packet packet = event.getPacket();
        if (!isInboundHeartbeat(packet)) {
            return;
        }
        Object handler = event.getNetHandler().getObject();
        Object packetObject = packet.getObject();
        if (handler == null || packetObject == null) {
            return;
        }
        event.setCancelled(true);
        synchronized (HELD) {
            HELD.add(new HeldPacket(handler, packetObject, System.currentTimeMillis()));
            while (HELD.size() > MAX_HELD) {
                HELD.remove(0);
            }
        }
    }

    @EventHandler
    public void onPreTick(EventPreTick event) {
        if (!enabled || HELD.isEmpty()) {
            return;
        }
        releaseExpired();
    }

    private static void releaseExpired() {
        long now = System.currentTimeMillis();
        long threshold = holdMillis;
        List<HeldPacket> due = null;
        synchronized (HELD) {
            Iterator<HeldPacket> iterator = HELD.iterator();
            while (iterator.hasNext()) {
                HeldPacket held = iterator.next();
                if (now - held.receivedMillis < threshold) {
                    continue;
                }
                iterator.remove();
                if (due == null) {
                    due = new ArrayList<HeldPacket>();
                }
                due.add(held);
            }
        }
        if (due == null) {
            return;
        }
        for (HeldPacket held : due) {
            replay(held);
        }
    }

    /**
     * 把扣留的包重新交给 NetHandler：按参数类型匹配处理方法的唯一性很高
     * （每个 Clientbound 包在 handler 上只有一个 handleXxx 方法），方法名被混淆也不影响。
     */
    private static void replay(HeldPacket held) {
        Class<?> packetClass = held.packet.getClass();
        for (Method method : held.handler.getClass().getMethods()) {
            if (method.getParameterCount() != 1 || method.getReturnType() != Void.TYPE) {
                continue;
            }
            Class<?> parameterType = method.getParameterTypes()[0];
            if (!parameterType.isAssignableFrom(packetClass)) {
                continue;
            }
            try {
                method.invoke(held.handler, held.packet);
                return;
            }
            catch (Throwable ignored) {
                return;
            }
        }
    }

    private static boolean isInboundHeartbeat(Packet packet) {
        if (packet == null || packet.isNull()) {
            return false;
        }
        if (packet.isInstance(MappedClasses.VE)) {
            return true;
        }
        Object handle = packet.getObject();
        if (handle == null) {
            return false;
        }
        String className = handle.getClass().getName();
        return className != null && className.contains("KeepAlive") && className.contains("Clientbound");
    }

    private static final class HeldPacket {
        private final Object handler;
        private final Object packet;
        private final long receivedMillis;

        private HeldPacket(Object handler, Object packet, long receivedMillis) {
            this.handler = handler;
            this.packet = packet;
            this.receivedMillis = receivedMillis;
        }
    }
}
