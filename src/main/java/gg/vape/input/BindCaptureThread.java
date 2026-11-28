package gg.vape.input;

import gg.vape.Vape;
import gg.vape.module.none.ClientSettings;
import gg.vape.unmap.Bendable;
import java.util.ArrayList;

class BindCaptureThread extends Thread {
    private final BindCaptureTask captureTask;
    private final Bendable target;

    BindCaptureThread(BindCaptureTask captureTask, Bendable target) {
        super("Vape bind capture");
        this.setDaemon(true);
        this.captureTask = captureTask;
        this.target = target;
    }

    @Override
    public void run() {
        try {
            ClientSettings clientSettings = Vape.INSTANCE.getModManager().getMod(ClientSettings.class);
            KeyboardInputState keyboardState = InputEventDispatcher.getInstance().getKeyboardState();
            long lastKeyboardChangeTime = keyboardState.getLastChangeTime();
            long lastMouseChangeTime = MouseInput.getLastChangeTimeBridge();
            ArrayList<Integer> capturedInputs = new ArrayList<Integer>();
            int firstKeyboardKey = -1;
            int firstMouseButton = -1;
            while (true) {
                int inputCode;
                if (this.isInterrupted()) return;
                if (firstKeyboardKey != -1 && !keyboardState.isKeyDown(firstKeyboardKey)
                        || firstMouseButton != -1 && !MouseInput.isButtonDown(firstMouseButton)
                        || capturedInputs.size() >= 3) break;
                if (lastKeyboardChangeTime != keyboardState.getLastChangeTime() && keyboardState.isLastKeyDown()) {
                    lastKeyboardChangeTime = keyboardState.getLastChangeTime();
                    inputCode = keyboardState.getLastKey();
                    if (capturedInputs.contains(inputCode)) continue;
                    capturedInputs.add(inputCode);
                    if (firstKeyboardKey == -1) firstKeyboardKey = inputCode;
                    if (!clientSettings.multiKeybinding.getEffectiveValue().booleanValue()) break;
                    continue;
                }
                if (!keyboardState.isKeyDown(160) && lastMouseChangeTime != MouseInput.getLastChangeTimeBridge() && MouseInput.isLastButtonDown()) {
                    lastMouseChangeTime = MouseInput.getLastChangeTimeBridge();
                    if (MouseInput.getLastButtonBridge() == 0) {
                        capturedInputs.clear();
                        break;
                    }
                    inputCode = -100 + MouseInput.getLastButtonBridge();
                    if (firstMouseButton == -1) firstMouseButton = MouseInput.getLastButtonBridge();
                    if (capturedInputs.contains(inputCode)) continue;
                    capturedInputs.add(inputCode);
                    if (!clientSettings.multiKeybinding.getEffectiveValue().booleanValue()) break;
                    continue;
                }
                try {
                    Thread.sleep(10L);
                } catch (InterruptedException cancelled) {
                    return;
                }
            }
            this.captureTask.completeCapture(this, this.target, capturedInputs);
        } finally {
            this.captureTask.finishCapture(this);
        }
    }
}
