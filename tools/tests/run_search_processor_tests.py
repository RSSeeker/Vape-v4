#!/usr/bin/env python3
"""Compile the real Search processor/index against a tiny simulated world."""
import argparse
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
STUBS = {
    "gg/vape/Vape.java": """package gg.vape; public class Vape { public static void logThrowable(Throwable t){throw new RuntimeException(t);} }""",
    "gg/vape/ui/unmap/SearchBlock.java": """package gg.vape.ui.unmap; import java.awt.Color; import java.util.concurrent.atomic.AtomicBoolean; public class SearchBlock { public final int id; public final AtomicBoolean enabled=new AtomicBoolean(true); public SearchBlock(int i){id=i;} public boolean b(int i,int m){return enabled.get() && id==i;} public boolean T(){return enabled.get();} public boolean W(){return false;} public Color B(){return Color.WHITE;} public AtomicBoolean I$src$Ljava_util_concurrent_atomic_AtomicBoolean_$10pq3bz(){return enabled;} }""",
    "gg/vape/utils/RotationUtil.java": """package gg.vape.utils; import gg.vape.wrapper.impl.EntityPlayerSP; public class RotationUtil { public static double p(EntityPlayerSP e,double x,double y,double z){return Math.sqrt((e.x-x)*(e.x-x)+(e.y-y)*(e.y-y)+(e.z-z)*(e.z-z));} }""",
    "gg/vape/value/NumberValue.java": """package gg.vape.value; public class NumberValue { public double value; public NumberValue(double v){value=v;} public Double getValue(){return value;} }""",
    "gg/vape/wrapper/impl/Block.java": """package gg.vape.wrapper.impl; public class Block { public final int id; public Block(int i){id=i;} public boolean isNotNull(){return true;} public String U(){return \"block\";} public static int R(Block b){return b.id;} public int d(int x,int y,int z){return 0;} }""",
    "gg/vape/wrapper/impl/EntityPlayerSP.java": """package gg.vape.wrapper.impl; public class EntityPlayerSP { public double x,y,z; public boolean present=true; public boolean isNull(){return !present;} public Object getObject(){return present?this:null;} public double z(){return x;} public double N(){return y;} public double h(){return z;} }""",
    "gg/vape/wrapper/impl/ForgeVersion.java": """package gg.vape.wrapper.impl; public class ForgeVersion { public static final ForgeVersion MC_1_16_5=new ForgeVersion(); public boolean d(){return true;} }""",
    "gg/vape/wrapper/impl/WorldClient.java": """package gg.vape.wrapper.impl; import java.util.concurrent.*; public class WorldClient { public boolean present=true; private final ConcurrentMap<String,Integer> blocks=new ConcurrentHashMap<String,Integer>(); public CountDownLatch entered,release; private static String k(int x,int y,int z){return x+\",\"+y+\",\"+z;} public void put(int x,int y,int z,int id){blocks.put(k(x,y,z),id);} public void gateReads(){entered=new CountDownLatch(1);release=new CountDownLatch(1);} public boolean isNull(){return !present;} public Object getObject(){return present?this:null;} public Block getBlockByPos(int x,int y,int z){if(entered!=null){entered.countDown();boolean interrupted=false;for(;;){try{release.await();break;}catch(InterruptedException e){interrupted=true;}}if(interrupted)Thread.currentThread().interrupt();}Integer n=blocks.get(k(x,y,z));return new Block(n==null?1:n);} }""",
    "gg/vape/wrapper/impl/Minecraft.java": """package gg.vape.wrapper.impl; public class Minecraft { public static WorldClient world=new WorldClient(); public static final EntityPlayerSP player=new EntityPlayerSP(); public static WorldClient theWorld(){return world;} public static EntityPlayerSP thePlayer(){return player;} }""",
}
PRODUCTION = [
    "src/main/java/gg/vape/utils/datas/HSBData.java",
    "src/main/java/gg/vape/utils/datas/SearchResultData.java",
    "src/main/java/gg/vape/utils/processors/SearchResultIndex.java",
    "src/main/java/gg/vape/utils/processors/SearchProcessor.java",
    "tools/tests/SearchProcessorTest.java",
]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java", default="java")
    parser.add_argument("--javac", default="javac")
    parser.add_argument("--ecj")
    args = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix="vape-search-test-") as temp:
        base = Path(temp)
        classes = base / "classes"
        classes.mkdir()
        sources = []
        for name, text in STUBS.items():
            path = base / "stubs" / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text, encoding="utf-8")
            sources.append(str(path))
        compiler = ([args.java, "-cp", args.ecj, "org.eclipse.jdt.internal.compiler.batch.Main", "-8"]
                    if args.ecj else [args.javac, "--release", "8"])
        subprocess.run(compiler + ["-encoding", "UTF-8", "-d", str(classes)] + sources
                       + [str(ROOT / name) for name in PRODUCTION], cwd=ROOT, check=True)
        subprocess.run([args.java, "-cp", str(classes), "SearchProcessorTest"], cwd=ROOT, check=True)


if __name__ == "__main__":
    main()
