package gg.vape.utils.network;

import gg.vape.Vape;
import gg.vape.event.EventHandler;
import gg.vape.event.EventListener;
import gg.vape.event.impl.EventPacketReceive;
import gg.vape.event.impl.EventPreTick;
import gg.vape.mapping.MappedClasses;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.Packet;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 入站心跳扣留队列（Themis Blink 角度）。
 *
 * <p>当 Blink / FakeLag 把出站包压住时，服务端发来的 KeepAlive 如果立刻回，
 * 服务端会看到「客户端还在正常响应、但其它包全停了」，反而更容易被判成 Blink。
 * 这里把入站 KeepAlive 扣住一小段时间，再交给原来的 NetHandler 处理，
 * 让服务端的观察变成「整条链路一起延迟」。</p>
 *
 * <p>重放不再用「getMethods() 里第一个参数兼容的方法」这种碰运气的方式，而是：</p>
 * <ol>
 *   <li>按「参数类型与包类型最接近」解析出唯一的重放方法并缓存（每个 handler/包类型只反射一次）；</li>
 *   <li>解析不出来时不扣留这个包，直接交给原版处理——宁可扣留失效，也不能把 KeepAlive 吞掉；</li>
 *   <li>重放只在客户端线程执行，非客户端线程（Ixeris 等把 tick 轮询拆线程的情况）走
 *       {@link Minecraft#v(Runnable)} 投递；</li>
 *   <li>关闭开关、{@link #flush()}、队列溢出都不再丢包，而是立即重放。</li>
 * </ol>
 *
 * <p>默认关闭，由 Disabler 的 <code>Themis Hold KeepAlive</code> 开关启用；
 * 扣留上限 {@link #getHoldMillis()}（默认 500ms，超过后即使没有 tick 也会放行），
 * 队列上限 {@link #MAX_HELD}，防止异常累积。</p>
 */
public final class InboundPingQueue
implements EventListener {
    private static final int MAX_HELD = 20;
    /** 参数类型离包类型太远的重载不接受（多半是 handlePacket(Packet) 这类兜底方法）。 */
    private static final int MAX_REPLAY_TYPE_DISTANCE = 6;
    private static final List<HeldPacket> HELD = new ArrayList<HeldPacket>();
    private static final Map<Class<?>, Map<Class<?>, Method>> REPLAY_METHODS = new HashMap<Class<?>, Map<Class<?>, Method>>();
    private static final Map<Class<?>, Boolean> UNRESOLVED_LOGGED = new HashMap<Class<?>, Boolean>();
    private static volatile boolean enabled;
    private static volatile long holdMillis = 500L;

    public InboundPingQueue() {
    }

    public static void setEnabled(boolean value) {
        boolean wasEnabled = enabled;
        enabled = value;
        if (!value && wasEnabled) {
            releaseAll();
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

    /**
     * 立即把当前扣留的包全部交还给 NetHandler（不再等待剩余扣留时间）。
     */
    public static void flush() {
        releaseAll();
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
        Method replayMethod = resolveReplayMethod(handler.getClass(), packetObject.getClass());
        if (replayMethod == null) {
            // 解析不到可靠的重放方法：不扣留，让原版照常处理，避免心跳丢失被踢。
            return;
        }
        event.setCancelled(true);
        List<HeldPacket> overflow = null;
        synchronized (HELD) {
            HELD.add(new HeldPacket(handler, packetObject, replayMethod, System.currentTimeMillis()));
            while (HELD.size() > MAX_HELD) {
                if (overflow == null) {
                    overflow = new ArrayList<HeldPacket>();
                }
                overflow.add(HELD.remove(0));
            }
        }
        if (overflow != null) {
            for (HeldPacket held : overflow) {
                replay(held);
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

    private static void releaseAll() {
        List<HeldPacket> due;
        synchronized (HELD) {
            if (HELD.isEmpty()) {
                return;
            }
            due = new ArrayList<HeldPacket>(HELD);
            HELD.clear();
        }
        for (HeldPacket held : due) {
            replay(held);
        }
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

    private static void replay(HeldPacket held) {
        if (!Minecraft.isOnGameThread()) {
            Minecraft.v(new Runnable() {
                @Override
                public void run() {
                    invokeReplay(held);
                }
            });
            return;
        }
        invokeReplay(held);
    }

    private static void invokeReplay(HeldPacket held) {
        try {
            held.method.invoke(held.handler, held.packet);
        }
        catch (Throwable throwable) {
            Vape.logError("[InboundPingQueue] replay " + held.packet.getClass().getName()
                    + " via " + held.method.getName() + " failed: " + throwable);
        }
    }

    /**
     * 解析「把某个包交还给 NetHandler」的方法，并按 handler + 包类型缓存结果。
     *
     * <p>不能用「第一个参数兼容的方法」：同一个 handler 上往往有
     * {@code handlePacket(Packet)} 之类的兜底重载，先命中的话包会被静默忽略，
     * 服务端的心跳请求就永远得不到回应。这里优先取参数类型与包类型完全一致的方法，
     * 否则取类型距离最近的；距离过远则视为无法可靠重放。</p>
     */
    private static Method resolveReplayMethod(Class<?> handlerClass, Class<?> packetClass) {
        synchronized (REPLAY_METHODS) {
            Map<Class<?>, Method> byPacket = REPLAY_METHODS.get(handlerClass);
            if (byPacket == null) {
                byPacket = new HashMap<Class<?>, Method>();
                REPLAY_METHODS.put(handlerClass, byPacket);
            }
            if (byPacket.containsKey(packetClass)) {
                return byPacket.get(packetClass);
            }
            Method resolved = findReplayMethod(handlerClass, packetClass);
            byPacket.put(packetClass, resolved);
            if (resolved == null) {
                logUnresolvedOnce(handlerClass);
            }
            return resolved;
        }
    }

    private static Method findReplayMethod(Class<?> handlerClass, Class<?> packetClass) {
        Method best = null;
        int bestDistance = Integer.MAX_VALUE;
        try {
            for (Method method : handlerClass.getMethods()) {
                if (method.getParameterCount() != 1 || method.getReturnType() != Void.TYPE) {
                    continue;
                }
                Class<?> parameterType = method.getParameterTypes()[0];
                if (parameterType == packetClass) {
                    return method;
                }
                if (!parameterType.isAssignableFrom(packetClass)) {
                    continue;
                }
                int distance = typeDistance(packetClass, parameterType);
                if (distance < 0 || distance > MAX_REPLAY_TYPE_DISTANCE) {
                    continue;
                }
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = method;
                }
            }
        }
        catch (Throwable throwable) {
            Vape.logError("[InboundPingQueue] resolve replay method failed: " + throwable);
            return null;
        }
        return best;
    }

    /**
     * 从实际类型向上（父类 + 接口）搜索目标类型的最短继承距离；找不到返回 -1。
     */
    private static int typeDistance(Class<?> actual, Class<?> expected) {
        if (actual == null) {
            return -1;
        }
        if (actual == expected) {
            return 0;
        }
        ArrayDeque<Class<?>> queue = new ArrayDeque<Class<?>>();
        Map<Class<?>, Integer> distances = new HashMap<Class<?>, Integer>();
        queue.add(actual);
        distances.put(actual, 0);
        while (!queue.isEmpty()) {
            Class<?> current = queue.poll();
            int next = distances.get(current) + 1;
            List<Class<?>> parents = new ArrayList<Class<?>>();
            if (current.getSuperclass() != null) {
                parents.add(current.getSuperclass());
            }
            Class<?>[] interfaces = current.getInterfaces();
            if (interfaces != null) {
                for (Class<?> implemented : interfaces) {
                    parents.add(implemented);
                }
            }
            for (Class<?> parent : parents) {
                if (distances.containsKey(parent)) {
                    continue;
                }
                if (parent == expected) {
                    return next;
                }
                distances.put(parent, next);
                queue.add(parent);
            }
        }
        return -1;
    }

    private static void logUnresolvedOnce(Class<?> handlerClass) {
        synchronized (UNRESOLVED_LOGGED) {
            if (UNRESOLVED_LOGGED.containsKey(handlerClass)) {
                return;
            }
            UNRESOLVED_LOGGED.put(handlerClass, Boolean.TRUE);
        }
        Vape.logError("[InboundPingQueue] no reliable keepalive replay method on "
                + handlerClass.getName() + "; hold keepalive disabled for this handler");
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
        private final Method method;
        private final long receivedMillis;

        private HeldPacket(Object handler, Object packet, Method method, long receivedMillis) {
            this.handler = handler;
            this.packet = packet;
            this.method = method;
            this.receivedMillis = receivedMillis;
        }
    }
}
