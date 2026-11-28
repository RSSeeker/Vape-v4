package gg.vape.utils;

import gg.vape.ui.click.component.GuiComponent;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;
import org.jetbrains.annotations.NotNull;

public class ThreadBoundExecutor
implements Executor {
    private final Queue<Runnable> pendingTasks = new ArrayDeque<Runnable>();
    private static GuiComponent[] controlFlowMarker;
    private Runnable currentTask;
    private Thread ownerThread;

    public static void setControlFlowMarker(GuiComponent[] marker) {
        controlFlowMarker = marker;
    }

    private static Throwable propagateThrowable(Throwable error) {
        return error;
    }

    public synchronized void runPending() {
        try {
            if (this.ownerThread == null) {
                this.ownerThread = Thread.currentThread();
            }
        }
        catch (Throwable error) {
            throw ThreadBoundExecutor.sneakyThrow(ThreadBoundExecutor.propagateThrowable(error));
        }
        while ((this.currentTask = this.pendingTasks.poll()) != null) {
            try {
                this.currentTask.run();
            }
            catch (Throwable taskFailure) {
                if (gg.vape.Vape.INSTANCE != null) {
                    gg.vape.Vape.debugLog("TBE task failed: " + this.currentTask.getClass().getName()
                            + " -> " + taskFailure);
                    java.io.StringWriter stack = new java.io.StringWriter();
                    taskFailure.printStackTrace(new java.io.PrintWriter(stack));
                    gg.vape.Vape.debugLog(stack.toString());
                    Throwable cause = taskFailure.getCause();
                    int depth = 0;
                    while (cause != null && depth < 8) {
                        java.io.StringWriter causeStack = new java.io.StringWriter();
                        cause.printStackTrace(new java.io.PrintWriter(causeStack));
                        gg.vape.Vape.debugLog("TBE cause[" + depth + "]: " + causeStack);
                        cause = cause.getCause();
                        ++depth;
                    }
                }
            }
        }
    }

    public Thread getOwnerThread() {
        return this.ownerThread;
    }

    public static GuiComponent[] getControlFlowMarker() {
        return controlFlowMarker;
    }

    /** Queue even on the owner thread, for work that must not mutate a tree
     * while an input event is still being dispatched. */
    public synchronized void executeLater(@NotNull Runnable runnable) {
        this.pendingTasks.offer(runnable);
    }

    @Override
    public synchronized void execute(@NotNull Runnable runnable) {
        boolean onOwnerThread = this.ownerThread != null && Thread.currentThread().equals(this.ownerThread);
        if (onOwnerThread) {
            runnable.run();
            return;
        }
        this.pendingTasks.offer(runnable);
    }

    private static RuntimeException sneakyThrow(Throwable error) {
        ThreadBoundExecutor.throwUnchecked(error);
        throw new AssertionError((Object)"unreachable");
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void throwUnchecked(Throwable error) throws T {
        throw (T)error;
    }

    static {
        if (ThreadBoundExecutor.getControlFlowMarker() != null) {
            ThreadBoundExecutor.setControlFlowMarker(new GuiComponent[1]);
        }
    }
}
