#!/usr/bin/env python3
"""Exercise the actual RenderUtil.U body in an isolated, stubbed render environment."""
import argparse
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]


def method_body(source, signature):
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 1
    i = opening + 1
    while depth and i < len(source):
        depth += (source[i] == "{") - (source[i] == "}")
        i += 1
    if depth:
        raise ValueError("unclosed render-label method")
    return source[start:i]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java", default="java")
    parser.add_argument("--javac", default="javac")
    args = parser.parse_args()
    production = (ROOT / "src/main/java/gg/vape/utils/render/RenderUtil.java").read_text(encoding="utf-8")
    label = method_body(production, "    public static void U(String string,")
    fixture = (ROOT / "tools/tests/WorldLabelLifecycleTest.java").read_text(encoding="utf-8")
    if fixture.count("/* __RENDER_UTIL_U__ */") != 1:
        raise ValueError("missing or duplicated fixture marker")
    with tempfile.TemporaryDirectory(prefix="vape-world-label-test-") as temp:
        path = Path(temp) / "WorldLabelLifecycleTest.java"
        path.write_text(fixture.replace("/* __RENDER_UTIL_U__ */", label), encoding="utf-8")
        subprocess.run([args.javac, "--release", "8", "-encoding", "UTF-8", "-d", temp, str(path)],
                       cwd=ROOT, check=True)
        subprocess.run([args.java, "-cp", temp, "WorldLabelLifecycleTest"], cwd=ROOT, check=True)


if __name__ == "__main__":
    main()
