#!/usr/bin/env python3
"""Compile the real attack-key controller and thread-bound executor with stub game bindings."""
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
    "gg/vape/event/impl/EventTickBase.java": """package gg.vape.event.impl; import gg.vape.utils.ThreadBoundExecutor; public class EventTickBase { public static ThreadBoundExecutor PRE_TICK_EXECUTOR=new ThreadBoundExecutor(); }""",
    "gg/vape/Vape.java": """package gg.vape; public class Vape { public static Vape INSTANCE; public static void debugLog(String s){} }""",
    "gg/vape/ui/click/component/GuiComponent.java": """package gg.vape.ui.click.component; public class GuiComponent {}""",
    "org/jetbrains/annotations/NotNull.java": """package org.jetbrains.annotations; public @interface NotNull {}""",
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
                    str(ROOT / "src/main/java/gg/vape/utils/ThreadBoundExecutor.java"),
                    str(ROOT / "src/main/java/gg/vape/module/combat/automace/AutoMaceAttackGate.java"),
                    str(ROOT / "tools/tests/AttackKeyControllerTest.java"),
                    str(ROOT / "tools/tests/AutoMaceAttackGateTest.java")]
        subprocess.run([args.javac, "--release", "8", "-encoding", "UTF-8", "-d", str(classes)]
                       + sources, cwd=ROOT, check=True)
        subprocess.run([args.java, "-cp", str(classes), "AttackKeyControllerTest"], cwd=ROOT, check=True)
        subprocess.run([args.java, "-cp", str(classes), "AutoMaceAttackGateTest"], cwd=ROOT, check=True)


if __name__ == "__main__":
    main()
