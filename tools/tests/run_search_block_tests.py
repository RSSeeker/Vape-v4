#!/usr/bin/env python3
"""Compile the real SearchBlock query parser against lightweight registry stubs."""
import argparse
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
STUBS = {
    "org/jetbrains/annotations/Nullable.java": """package org.jetbrains.annotations; public @interface Nullable {}""",
    "com/google/gson/JsonElement.java": """package com.google.gson; public class JsonElement { public boolean isJsonNull(){return false;} public String getAsString(){return \"\";} public int getAsInt(){return 0;} public boolean getAsBoolean(){return false;} }""",
    "com/google/gson/JsonObject.java": """package com.google.gson; public class JsonObject { public JsonElement get(String k){return null;} public void addProperty(String k,String v){} public void addProperty(String k,Number v){} public void addProperty(String k,Boolean v){} }""",
    "gg/vape/Vape.java": """package gg.vape; import java.util.function.Predicate; public class Vape { public static final Vape INSTANCE=new Vape(); public Helper getItemHelper(){return new Helper();} public static class Helper {public Predicate<Character> findCharacterRule(String n){return n.equals(\"rule\")?c->true:null;}} }""",
    "gg/vape/utils/Base64Util.java": """package gg.vape.utils; public class Base64Util { public static String encodeUtf8Base64(String s){return s;} public static String decodeUtf8Base64(String s){return s;} }""",
    "gg/vape/utils/MutableColor.java": """package gg.vape.utils; import java.awt.Color; public class MutableColor extends Color { public MutableColor(int n){super(n);} public MutableColor(Color c){super(c.getRGB());} public Integer l(){return getRGB();} }""",
    "gg/vape/utils/BlockUtil.java": """package gg.vape.utils; public class BlockUtil { public static String last; public static int[] r(String n){last=n;if(n.equals(\"barrel\"))return new int[]{42,-1};if(n.equals(\"chest\"))return new int[]{54,-1};if(n.equals(\"mod:thing\"))return new int[]{70,-1};return new int[]{-1,-1};} public static char t(int id,int metadata){return (char)(id<<4|metadata&15);} }""",
}
PRODUCTION = ["src/main/java/gg/vape/ui/unmap/SearchBlock.java", "tools/tests/SearchBlockQueryTest.java"]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java", default="java")
    parser.add_argument("--javac", default="javac")
    args = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix="vape-search-query-test-") as temp:
        base = Path(temp)
        classes = base / "classes"
        classes.mkdir()
        sources = []
        for name, text in STUBS.items():
            path = base / "stubs" / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text, encoding="utf-8")
            sources.append(str(path))
        subprocess.run([args.javac, "--release", "8", "-encoding", "UTF-8", "-d", str(classes)]
                       + sources + [str(ROOT / name) for name in PRODUCTION], cwd=ROOT, check=True)
        subprocess.run([args.java, "-cp", str(classes), "SearchBlockQueryTest"], cwd=ROOT, check=True)


if __name__ == "__main__":
    main()
