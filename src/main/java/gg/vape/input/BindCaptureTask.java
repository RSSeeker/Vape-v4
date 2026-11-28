package gg.vape.input;

import gg.vape.unmap.Bendable;
import java.util.List;

/** Owns at most one capture, tied to the target it started with. */
public abstract class BindCaptureTask implements Runnable {
    private volatile BindCaptureThread captureThread;
    private Bendable bendable;

    public BindCaptureTask(Bendable bendable) {
        this.bendable = bendable;
    }

    @Override
    public synchronized void run() {
        if (this.captureThread != null || this.bendable == null) return;
        BindCaptureThread thread = new BindCaptureThread(this, this.bendable);
        this.captureThread = thread;
        thread.start();
    }

    /** Retargeting invalidates an in-flight capture instead of redirecting it. */
    public synchronized void setBendable(Bendable bendable) {
        if (this.bendable != bendable) this.cancelCapture();
        this.bendable = bendable;
    }

    public boolean isCapturing() {
        return this.captureThread != null;
    }

    /** Stop without changing or saving the binding. */
    public synchronized void cancelCapture() {
        BindCaptureThread previous = this.captureThread;
        if (previous == null) return;
        this.captureThread = null;
        previous.interrupt();
    }

    /** A cancelled or superseded worker must never write to any target. */
    void completeCapture(BindCaptureThread thread, Bendable target, List<Integer> inputs) {
        synchronized (this) {
            if (this.captureThread != thread || this.bendable != target) return;
            target.setBoundInputs(inputs);
            this.captureThread = null;
        }
        // Never call persistence while holding the capture lock: a profile
        // transition may hold the manager lock while cancelling controls.
        this.onCaptureComplete(target);
    }

    /** Preserve the completed target if the control is retargeted afterwards. */
    protected void onCaptureComplete(Bendable target) {
        this.onCaptureComplete();
    }

    /** Release a worker which exits without a successful capture. */
    synchronized void finishCapture(BindCaptureThread thread) {
        if (this.captureThread == thread) this.captureThread = null;
    }

    public abstract void onCaptureComplete();
}
