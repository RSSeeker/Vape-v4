#!/usr/bin/env python3
"""Exercise the real bind-capture worker with deterministic headless input doubles."""
import argparse
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
STUBS = {
    "gg/vape/unmap/Bendable.java": """package gg.vape.unmap; import java.util.*; public class Bendable {
        private final List<Integer> inputs=new ArrayList<Integer>(); public Bendable(int key){inputs.add(key);}
        public synchronized void setBoundInputs(List<Integer> values){inputs.clear();inputs.addAll(values);}
        public synchronized List<Integer> inputs(){return new ArrayList<Integer>(inputs);}}
    """,
    "gg/vape/module/none/ClientSettings.java": """package gg.vape.module.none; public class ClientSettings {
        public final Flag multiKeybinding=new Flag(); public static class Flag {private volatile boolean value;
        public Boolean getEffectiveValue(){return value;} public void set(boolean b){value=b;}}}
    """,
    "gg/vape/Vape.java": """package gg.vape; import gg.vape.module.none.ClientSettings; public class Vape {
        public static final Vape INSTANCE=new Vape(); public final ClientSettings settings=new ClientSettings();
        public Manager getModManager(){return new Manager();} public class Manager {public ClientSettings getMod(Class<?> type){return settings;}}}
    """,
    "gg/vape/input/InputEventDispatcher.java": """package gg.vape.input; public class InputEventDispatcher {
        private static final InputEventDispatcher INSTANCE=new InputEventDispatcher(); private final KeyboardInputState keyboard=new KeyboardInputState();
        public static InputEventDispatcher getInstance(){return INSTANCE;} public KeyboardInputState getKeyboardState(){return keyboard;}}
    """,
    "gg/vape/input/KeyboardInputState.java": """package gg.vape.input; import java.util.concurrent.CountDownLatch; public class KeyboardInputState {
        private volatile long time; private volatile int key; private volatile boolean down; public volatile CountDownLatch observed=new CountDownLatch(1);
        public long getLastChangeTime(){return time;} public boolean isLastKeyDown(){return down;}
        public int getLastKey(){observed.countDown();return key;} public boolean isKeyDown(int code){return down && key==code;}
        public void prepareObservation(){observed=new CountDownLatch(1);} public void press(int code){key=code;down=true;time++;}
        public void release(){down=false;time++;}}
    """,
    "gg/vape/input/MouseInput.java": """package gg.vape.input; import java.util.concurrent.CountDownLatch; public class MouseInput {
        private static volatile long time; private static volatile int button; private static volatile boolean down;
        public static volatile CountDownLatch ready=new CountDownLatch(1);
        public static void prepare(){ready=new CountDownLatch(1);down=false;time++;}
        public static long getLastChangeTimeBridge(){long result=time;ready.countDown();return result;}
        public static boolean isLastButtonDown(){return down;} public static int getLastButtonBridge(){return button;}
        public static boolean isButtonDown(int code){return down && button==code;} public static void press(int code){button=code;down=true;time++;}}
    """,
}
REAL = [
    "src/main/java/gg/vape/input/BindCaptureTask.java",
    "src/main/java/gg/vape/input/BindCaptureThread.java",
    "tools/tests/BindCaptureTest.java",
]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--javac", default="javac")
    parser.add_argument("--java", default="java")
    args = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix="bind-capture-test-") as directory:
        tmp = Path(directory)
        sources = []
        for name, content in STUBS.items():
            path = tmp / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content, encoding="utf-8")
            sources.append(str(path))
        sources += [str(ROOT / name) for name in REAL]
        classes = tmp / "classes"
        classes.mkdir()
        subprocess.run([args.javac, "--release", "8", "-encoding", "UTF-8", "-d", str(classes)] + sources,
                       cwd=ROOT, check=True)
        subprocess.run([args.java, "-cp", str(classes), "gg.vape.input.BindCaptureTest"], cwd=ROOT, check=True)


if __name__ == "__main__":
    main()
