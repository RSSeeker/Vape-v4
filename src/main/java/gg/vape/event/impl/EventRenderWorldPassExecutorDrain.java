package gg.vape.event.impl;

import gg.vape.event.Event;
import gg.vape.event.EventListeners;
import gg.vape.utils.ThreadBoundExecutor;
import gg.vape.utils.render.GuiRenderPrimitives;
import gg.vape.wrapper.impl.DeltaTracker;
import gg.vape.wrapper.impl.Util;

public class EventRenderWorldPassExecutorDrain
extends Event {
    private static final EventListeners EVENT_LISTENERS;
    private final float partialTicks;
    public static final ThreadBoundExecutor EXECUTOR;
    private static String[] obfuscationState;

    public EventRenderWorldPassExecutorDrain(float partialTicks) {
        this.partialTicks = partialTicks;
    }

    public EventRenderWorldPassExecutorDrain() {
        this.partialTicks = 0.0f;
    }

    public static String[] getWorldPassObfuscationState() {
        return obfuscationState;
    }

    private static boolean contextWarned;

    @Override
    public boolean fire() {
        try {
            // 26.2 fabric：注入点（GameRenderer.update 入口）处 GL 上下文可能尚未
            // 绑定。此时执行 initializeFrames（含 glGetString 等 GL 调用）会触发
            // JVM FATAL ERROR（No context is current）。GL 未就绪时跳过本帧，
            // 任务留在队列，待上下文绑定后的某一帧再执行。
            if (GuiRenderPrimitives.d() && !EventRenderWorldPassExecutorDrain.isGlContextReady()) {
                if (!EventRenderWorldPassExecutorDrain.contextWarned) {
                    EventRenderWorldPassExecutorDrain.contextWarned = true;
                    gg.vape.Vape.debugLog("ERWPD: GL context not ready at update entry (thread="
                            + Thread.currentThread().getName() + "). Minecraft 26.2 running with the Vulkan backend has no "
                            + "OpenGL context. Switch the Graphics API to OpenGL in Video Settings and retry.");
                }
                return false;
            }
            EXECUTOR.runPending();
        }
        catch (Throwable throwable) {
            // 记录 TBE 任务异常：initializeFrames 等失败会被这里吞掉导致
            // framesInitialized 永远不置位、注入挂起；先暴露出来便于定位。
            gg.vape.Vape.logThrowable(throwable);
        }
        return false;
    }

    private static boolean isGlContextReady() {
        try {
            return Util.glfwGetCurrentContext() != 0L;
        }
        catch (Throwable throwable) {
            return true;
        }
    }

    public static void setWorldPassObfuscationState(String[] state) {
        obfuscationState = state;
    }

    @Override
    public EventListeners getListeners() {
        return EVENT_LISTENERS;
    }

    public EventRenderWorldPassExecutorDrain(Object deltaTrackerHandle) {
        DeltaTracker deltaTracker = new DeltaTracker(deltaTrackerHandle);
        this.partialTicks = deltaTracker.getGameTimeDeltaTicks();
    }

    public static EventListeners getEventListeners() {
        return EVENT_LISTENERS;
    }

    static {
        EXECUTOR = new ThreadBoundExecutor();
        EVENT_LISTENERS = new EventListeners();
        EventRenderWorldPassExecutorDrain.setWorldPassObfuscationState(null);
    }
}

