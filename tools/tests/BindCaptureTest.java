package gg.vape.input;

import gg.vape.Vape;
import gg.vape.unmap.Bendable;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Real asynchronous capture worker against controlled keyboard/mouse doubles. */
public final class BindCaptureTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static final class Target extends Bendable {
        Target(int key) { super(key); }
    }

    private static final class Capture extends BindCaptureTask {
        volatile int completed;
        Capture(Bendable target) { super(target); }
        @Override public void onCaptureComplete() { ++this.completed; }
    }

    private static final class BlockingCapture extends BindCaptureTask {
        final CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        volatile Bendable completedTarget;
        volatile boolean heldLock;
        BlockingCapture(Bendable target) { super(target); }
        @Override protected void onCaptureComplete(Bendable target) {
            this.completedTarget = target;
            this.heldLock = Thread.holdsLock(this);
            this.started.countDown();
            try { this.release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        @Override public void onCaptureComplete() {}
    }

    private static BindCaptureThread worker(BindCaptureTask task) throws Exception {
        Field field = BindCaptureTask.class.getDeclaredField("captureThread");
        field.setAccessible(true);
        return (BindCaptureThread)field.get(task);
    }

    private static BindCaptureThread start(BindCaptureTask task) throws Exception {
        MouseInput.prepare();
        task.run();
        BindCaptureThread thread = worker(task);
        check(thread != null && MouseInput.ready.await(2, TimeUnit.SECONDS), "capture did not start");
        return thread;
    }

    private static void waitFor(BindCaptureThread thread) throws Exception {
        thread.join(2000);
        if (thread.isAlive()) thread.interrupt();
        check(!thread.isAlive(), "capture worker did not stop");
    }

    public static void main(String[] args) throws Exception {
        KeyboardInputState keyboard = InputEventDispatcher.getInstance().getKeyboardState();
        Vape.INSTANCE.settings.multiKeybinding.set(false);
        Target first = new Target(11), second = new Target(22);
        Capture capture = new Capture(first);
        BindCaptureThread old = start(capture);
        capture.setBendable(second);
        waitFor(old);
        check(!capture.isCapturing() && first.inputs().equals(Arrays.asList(11))
                && second.inputs().equals(Arrays.asList(22)) && capture.completed == 0,
                "retargeting cancels without changing or saving either binding");

        BindCaptureThread current = start(capture);
        // A worker which exits late cannot finish or write into its successor.
        capture.completeCapture(old, first, Arrays.asList(33));
        capture.finishCapture(old);
        check(capture.isCapturing() && first.inputs().equals(Arrays.asList(11)) && capture.completed == 0,
                "a stale worker cannot commit or end a newer capture");
        keyboard.press(44);
        waitFor(current);
        check(second.inputs().equals(Arrays.asList(44)) && first.inputs().equals(Arrays.asList(11))
                && capture.completed == 1 && !capture.isCapturing(), "new capture commits only to its original target");

        keyboard.release();
        BindCaptureThread cancelled = start(capture);
        capture.cancelCapture();
        waitFor(cancelled);
        check(second.inputs().equals(Arrays.asList(44)) && capture.completed == 1,
                "interrupted sleep cancels without a late write or callback");

        Vape.INSTANCE.settings.multiKeybinding.set(true);
        keyboard.release();
        keyboard.prepareObservation();
        BindCaptureThread multi = start(capture);
        keyboard.press(55);
        check(keyboard.observed.await(2, TimeUnit.SECONDS), "multi-key capture did not observe first key");
        MouseInput.press(0);
        waitFor(multi);
        check(second.inputs().equals(Collections.<Integer>emptyList()) && capture.completed == 2,
                "left click clears the whole partial multi-key capture");

        capture.setBendable(null);
        capture.run();
        check(!capture.isCapturing(), "a control without a target cannot start a capture");

        Vape.INSTANCE.settings.multiKeybinding.set(false);
        keyboard.release();
        BlockingCapture saving = new BlockingCapture(first);
        BindCaptureThread completed = start(saving);
        keyboard.press(66);
        check(saving.started.await(2, TimeUnit.SECONDS), "capture did not enter its save callback");
        Thread retarget = new Thread(() -> saving.setBendable(second));
        retarget.setDaemon(true);
        retarget.start();
        retarget.join(500);
        boolean retargetedWithoutWaiting = !retarget.isAlive();
        saving.release.countDown();
        retarget.join(2000);
        waitFor(completed);
        check(retargetedWithoutWaiting && !saving.heldLock && saving.completedTarget == first
                        && first.inputs().equals(Arrays.asList(66)) && second.inputs().isEmpty(),
                "saving runs outside the capture lock and retains the completed target after retargeting");
        System.out.println("Bind capture cancellation, retargeting, multi-key and save-lock checks passed (real worker, headless input doubles)");
    }
}
