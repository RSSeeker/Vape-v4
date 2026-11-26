#!/usr/bin/env python3
"""Compile the real attack-key controller with a controllable tick executor."""
import argparse
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
STUBS = {
    "gg/vape/module/Mod.java": "package gg.vape.module; public class Mod {}",
    "gg/vape/wrapper/impl/ForgeVersion.java": "package gg.vape.wrapper.impl; public class ForgeVersion { public static final ForgeVersion MC_26_2=new ForgeVersion(); public boolean modern; public boolean d(){return modern;} }",
    "gg/vape/wrapper/impl/KeyBinding.java": "package gg.vape.wrapper.impl; public class KeyBinding {}",
    "gg/vape/wrapper/impl/Minecraft.java": "package gg.vape.wrapper.impl; public class Minecraft { public static Minecraft a_w3_0_S(){return new Minecraft();} public KeyBinding F(){return new KeyBinding();} }",
    "gg/vape/input/KeyBindingHelper.java": "package gg.vape.input; import gg.vape.wrapper.impl.KeyBinding; public class KeyBindingHelper { public static int presses,releases; public static void updateKeyBinding(KeyBinding k,boolean down,boolean click){if(down)presses++;else releases++;} }",
    "gg/vape/event/impl/SyntheticAttackRequestEvent.java": "package gg.vape.event.impl; import gg.vape.module.Mod; public class SyntheticAttackRequestEvent { public static boolean cancel; public static int fired; public static Runnable onFire; public SyntheticAttackRequestEvent(Mod m){} public boolean fire(){fired++;if(onFire!=null)onFire.run();return cancel;} }",
    "gg/vape/event/impl/EventTickBase.java": """package gg.vape.event.impl; import java.util.*; public class EventTickBase { public static final Executor PRE_TICK_EXECUTOR=new Executor(); public static class Executor { private Thread owner; private final Queue<Runnable> tasks=new ArrayDeque<Runnable>(); public Thread getOwnerThread(){return owner;} public void execute(Runnable r){if(Thread.currentThread()==owner)r.run();else tasks.add(r);} public void runPending(){owner=Thread.currentThread();while(!tasks.isEmpty())tasks.remove().run();} public void reset(){owner=null;tasks.clear();} } }""",
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java", default="java")
    parser.add_argument("--javac", default="javac")
    args = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix="vape-attack-controller-") as temp:
        base = Path(temp)
        classes = base / "classes"
        classes.mkdir()
        sources = []
        for name, text in STUBS.items():
            path = base / "stubs" / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text, encoding="utf-8")
            sources.append(str(path))
        sources += [str(ROOT / "src/main/java/gg/vape/input/AttackKeyController.java"),
                    str(ROOT / "tools/tests/AttackKeyControllerTest.java")]
        subprocess.run([args.javac, "--release", "8", "-encoding", "UTF-8", "-d", str(classes)]
                       + sources, cwd=ROOT, check=True)
        subprocess.run([args.java, "-cp", str(classes), "AttackKeyControllerTest"], cwd=ROOT, check=True)


if __name__ == "__main__":
    main()
