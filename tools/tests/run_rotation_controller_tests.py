#!/usr/bin/env python3
"""Compile real rotation controllers against tiny headless Minecraft stubs.

The stubs model only the methods needed by these tests; they do not validate
Minecraft mappings, rendering, injection, packets, or anti-cheat behavior.
"""
import argparse
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]

STUBS = {
    "org/jetbrains/annotations/Nullable.java": """package org.jetbrains.annotations; public @interface Nullable {}""",
    "gg/vape/event/EventHandler.java": """package gg.vape.event; public @interface EventHandler {}""",
    "gg/vape/event/impl/EventPostRenderTick.java": """package gg.vape.event.impl; import gg.vape.wrapper.impl.*; public class EventPostRenderTick { public EntityPlayerSP getThePlayer(){return Minecraft.thePlayer();} }""",
    "gg/vape/event/impl/EventPreRenderTick.java": """package gg.vape.event.impl; import gg.vape.wrapper.impl.*; public class EventPreRenderTick { public EntityPlayerSP getThePlayer(){return Minecraft.thePlayer();} public World getWorld(){return Minecraft.theWorld();} }""",
    "gg/vape/event/impl/EventPreEntityRendererMouseUpdate.java": """package gg.vape.event.impl; import gg.vape.wrapper.impl.*; public class EventPreEntityRendererMouseUpdate { public EntityPlayerSP getThePlayer(){return Minecraft.thePlayer();} }""",
    "gg/vape/Vape.java": """package gg.vape; public class Vape { public static final Vape INSTANCE=new Vape(); public Manager getModManager(){return new Manager();} public static class Manager { public <T> T getMod(Class<T> c){return null;} } }""",
    "gg/vape/module/render/Freecam.java": """package gg.vape.module.render; public class Freecam { public boolean isEnabled(){return false;} }""",
    "gg/vape/module/render/hud/FreeLookHudModule.java": """package gg.vape.module.render.hud; public class FreeLookHudModule { public static boolean isActive(){return false;} public static float getSavedPitch(){return 0;} }""",
    "gg/vape/module/Mod.java": """package gg.vape.module; public class Mod {}""",
    "gg/vape/module/SubModule.java": """package gg.vape.module; public class SubModule<T> extends Mod { private T parent; @SuppressWarnings(\"unchecked\") public SubModule(Mod p,String n){parent=(T)p;} public T getParent(){return parent;} public void addValue(Object... values){} public void onEnable(){} public void onDisable(){} }""",
    "gg/vape/module/none/ClientSettings.java": """package gg.vape.module.none; public class ClientSettings { public static final ClientSettings INSTANCE=new ClientSettings(); public boolean inputEnabled=true; }""",
    "gg/vape/config/ClientSettings.java": """package gg.vape.config; public class ClientSettings { public static boolean IS_LEGACY_1_7=false; public static boolean isAttackButtonDown(){return true;} }""",
    "gg/vape/unmap/ModeSelection.java": """package gg.vape.unmap; public class ModeSelection { public final String name; public ModeSelection(String n){name=n;} }""",
    "gg/vape/unmap/ModeOption.java": """package gg.vape.unmap; public class ModeOption extends ModeSelection { public ModeOption(String n){super(n);} }""",
    "gg/vape/value/BooleanValue.java": """package gg.vape.value; public class BooleanValue { public boolean value; public BooleanValue(boolean v){value=v;} public Boolean getEffectiveValue(){return value;} public static BooleanValue create(Object o,String n,boolean v){return new BooleanValue(v);} }""",
    "gg/vape/value/NumberValue.java": """package gg.vape.value; public class NumberValue { public double value; public NumberValue(double v){value=v;} public Double getValue(){return value;} public static NumberValue create(Object o,String n,String f,String u,double min,double v,double max,double step,Object... description){return new NumberValue(v);} }""",
    "gg/vape/value/ModeValue.java": """package gg.vape.value; import gg.vape.unmap.*; public class ModeValue { public ModeSelection value; public ModeValue(ModeSelection v){value=v;} public ModeSelection getValue(){return value;} public void setValue(ModeSelection v){value=v;} public static ModeValue create(Object o,String n,String d,ModeSelection v,ModeSelection... options){return new ModeValue(v);} public void addModeDependentValues(ModeOption m,Object... v){} }""",
    "gg/vape/module/combat/AimAssist.java": """package gg.vape.module.combat; import gg.vape.module.Mod; import gg.vape.unmap.*; import gg.vape.value.*; import gg.vape.wrapper.impl.*; public class AimAssist extends Mod {
 public EntityLivingBase selected; public boolean allowed=true; public ModeOption closestAreaMode=new ModeOption(\"Closest\"); public ModeValue targetArea=new ModeValue(new ModeOption(\"Center\"));
 public BooleanValue require=new BooleanValue(false), vertical=new BooleanValue(false), strafe=new BooleanValue(false);
 public NumberValue distance=new NumberValue(10000000.0), horizontalSpeed=new NumberValue(5), verticalSpeed=new NumberValue(5);
 public boolean canAim(){return allowed;} public BooleanValue getRequireMouseDown(){return require;} public BooleanValue getAimVertically(){return vertical;} public BooleanValue getStrafeIncrease(){return strafe;}
 public NumberValue getDistance(){return distance;} public NumberValue getHorizontalSpeed(){return horizontalSpeed;} public NumberValue getVerticalSpeed(){return verticalSpeed;}
 public EntityLivingBase findBestTarget(){return selected;} public boolean isValidTarget(EntityLivingBase t){return selected!=null && t.getObject()==selected.getObject();}
 public boolean isFriendTarget(EntityLivingBase e){return false;} public boolean isTargetFiltered(EntityLivingBase e){return true;}
}""",
    "gg/vape/module/combat/aimassist/AimAssistRotationWorkerThread.java": """package gg.vape.module.combat.aimassist; public class AimAssistRotationWorkerThread extends Thread { public AimAssistRotationWorkerThread(AimAssistRotationSubModule m){} public void run(){} }""",
    "gg/vape/module/combat/aimassist/AimAssistWorkerThread.java": """package gg.vape.module.combat.aimassist; public class AimAssistWorkerThread extends Thread { public AimAssistWorkerThread(AimAssistTargetingSubModule m){} public void run(){} }""",
    "gg/vape/mapping/MappedClasses.java": """package gg.vape.mapping; import gg.vape.wrapper.impl.*; public class MappedClasses { public static Class<?> z5=EntityPlayerSP.class, FT=String.class, zm=EntityLivingBase.class; }""",
    "gg/vape/utils/MathUtil.java": """package gg.vape.utils; import java.util.Random; public class MathUtil { public static float clamp(float v,float min,float max){return Math.max(min,Math.min(max,v));} public static float wrapAngleTo180(float a){a%=360; if(a>=180)a-=360; if(a< -180)a+=360; return a;} public static int randomExclusiveUpper(Random r,int a,int b){return a+r.nextInt(b-a);} public static double randomRange(Random r,double a,double b){return a+(b-a)*r.nextDouble();} }""",
    "gg/vape/utils/RotationVectorMath.java": """package gg.vape.utils; import gg.vape.rotation.*; import gg.vape.wrapper.impl.*; public class RotationVectorMath { public static RotationAngles H(Vec3 a,Vec3 b,float c,boolean d){return new RotationAngles(0,0);} }""",
    "gg/vape/utils/RotationUtil.java": """package gg.vape.utils; import gg.vape.wrapper.impl.*; public class RotationUtil { public static volatile double predictedX,predictedZ; public static boolean C(EntityLivingBase e){return false;} public static double C(double x,double z,float yaw,double tx,double tz){predictedX=tx;predictedZ=tz;return 10.0;} public static boolean p(double x,double z,float yaw,double tx,double tz){return false;} public static int H(EntityPlayerSP p,double x,double y,double z){return 30;} public static float a(EntityPlayerSP p,EntityLivingBase t){return 10;} public static Vec3d T(EntityPlayerSP p,AxisAlignedBB b,double x,double y,double z){return new Vec3d(0,0,0);} }""",
    "gg/vape/utils/MutableColor.java": """package gg.vape.utils; public class MutableColor { public MutableColor(int value){} }""",
    "gg/vape/utils/SleepUtil.java": """package gg.vape.utils; public class SleepUtil { public static void sleep(long n){} }""",
    "gg/vape/utils/TimerUtil.java": """package gg.vape.utils; public class TimerUtil { public void x(long n){} public long getLastMS(){return 1;} public void reset(){} }""",
    "gg/vape/wrapper/impl/Vec3.java": """package gg.vape.wrapper.impl; public class Vec3 { double x,y,z; public static Vec3 create(double x,double y,double z){Vec3 v=new Vec3();v.x=x;v.y=y;v.z=z;return v;} public double getX(){return x;} public double getY(){return y;} public double getZ(){return z;} }""",
    "gg/vape/wrapper/impl/AxisAlignedBB.java": """package gg.vape.wrapper.impl; public class AxisAlignedBB { public double getMinX(){return 0;} public double getMaxX(){return .6;} public double getMinY(){return 0;} public double getMaxY(){return 1.8;} public double getMinZ(){return 0;} public double getMaxZ(){return .6;} }""",
    "gg/vape/wrapper/impl/MovementInput.java": """package gg.vape.wrapper.impl; public class MovementInput { public float T(){return 0;} }""",
    "gg/vape/wrapper/impl/Entity.java": """package gg.vape.wrapper.impl; public class Entity { protected Object object; public double x,y,z; public float yaw,pitch,prevYaw,prevPitch,renderYaw; public Entity(){object=this;} public Entity(Object o){object=o;} protected Entity data(){return object instanceof Entity && object!=this ? (Entity)object : this;} public Object getObject(){return object;} public boolean isNull(){return object==null;} public boolean isNotNull(){return object!=null;} public boolean isInstance(Class<?> c){return object!=null && c.isInstance(object);} public boolean equals(Object o){return o instanceof Entity && object==((Entity)o).object;} public double c(){return data().x;} public double A(){return data().y;} public double Z(){return data().z;} public double z(){return data().x;} public double N(){return data().y;} public double h(){return data().z;} public double f(){return data().x;} public double R(){return data().z;} public double t(){return 0;} public double q(){return 0;} public double T(){return 0;} public boolean b$src$Z$fqlxe4(){return true;} public float X(){return 1.62f;} public float Y(){return 1.8f;} public float J(){return data().yaw;} public float V(){return data().pitch;} public float j(){return data().prevYaw;} public float D(){return data().prevPitch;} public float s(){return data().renderYaw;} public void H(float v){data().yaw=v;} public void C(float v){data().pitch=v;} public void D(float v){data().prevYaw=v;} public void l(float v){data().prevPitch=v;} public void z(float v){data().renderYaw=v;} public AxisAlignedBB R$src$Lgg_vape_wrapper_impl_AxisAlignedBB_$r19dfl(){return new AxisAlignedBB();} public float getDistanceToEntity(Entity e){return 2.0f;} }""",
    "gg/vape/wrapper/impl/EntityLivingBase.java": """package gg.vape.wrapper.impl; public class EntityLivingBase extends Entity { public EntityLivingBase(){super();} public EntityLivingBase(Object o){super(o);} public float w$src$F$15l9epb(){return 20;} public boolean M$src$Z$ff28xj(){return false;} }""",
    "gg/vape/wrapper/impl/EntityPlayer.java": """package gg.vape.wrapper.impl; public class EntityPlayer extends EntityLivingBase { public EntityPlayer(){super();} public EntityPlayer(Object o){super(o);} }""",
    "gg/vape/wrapper/impl/EntityPlayerSP.java": """package gg.vape.wrapper.impl; public class EntityPlayerSP extends EntityPlayer { public EntityPlayerSP(){super();} public EntityPlayerSP(EntityLivingBase e){super(e.getObject());} public float q$src$F$1u6qsjx(){return 0;} public void F(float f){} public MovementInput movementInput(){return new MovementInput();} }""",
    "gg/vape/wrapper/impl/GuiScreen.java": """package gg.vape.wrapper.impl; public class GuiScreen { public boolean open; public boolean isNull(){return !open;} public boolean isNotNull(){return open;} public Object getObject(){return open?this:null;} }""",
    "gg/vape/wrapper/impl/World.java": """package gg.vape.wrapper.impl; import java.util.*; public class World { public boolean present=true; public List<Object> entities=new ArrayList<Object>(); public boolean isNull(){return !present;} public Object getObject(){return present?this:null;} public List<Object> z(){return entities;} }""",
    "gg/vape/wrapper/impl/ForgeVersion.java": """package gg.vape.wrapper.impl; public class ForgeVersion { public static final ForgeVersion MC_1_7_10=new ForgeVersion(); public boolean Y(){return true;} public boolean L(){return false;} }""",
    "gg/vape/wrapper/impl/Minecraft.java": """package gg.vape.wrapper.impl; public class Minecraft { public static final EntityPlayerSP player=new EntityPlayerSP(); public static final GuiScreen screen=new GuiScreen(); public static final World world=new World(); public static final Settings settings=new Settings(); public static EntityPlayerSP F(){return player;} public static EntityPlayerSP thePlayer(){return player;} public static World theWorld(){return world;} public static GuiScreen currentScreen(){return screen;} public static Settings gameSettings(){return settings;} public static Timer getTimer(){return new Timer();} public static class Settings { public float y(){return .5f;} } public static class Timer { public float renderPartialTicks(){return 1;} } }""",
    "gg/vape/rotation/RotationManager.java": """package gg.vape.rotation; import gg.vape.wrapper.impl.*; public class RotationManager { public static final RotationManager INSTANCE=new RotationManager(); public float getMouseSensitivity(){return .5f;} public float getManagedYaw(){return Minecraft.thePlayer().J();} public float getManagedPitch(){return Minecraft.thePlayer().V();} public static float getViewYaw(EntityPlayerSP p){return p.J();} public static float getViewPitch(EntityPlayerSP p){return p.V();} }""",
    "gg/vape/rotation/PlayerMouseRotationApplier.java": """package gg.vape.rotation; import gg.vape.wrapper.impl.*; public class PlayerMouseRotationApplier { public static void applyTrackedMouseDelta(float yaw,float pitch){EntityPlayerSP p=Minecraft.thePlayer();p.H(p.J()+yaw*.15f);p.C(Math.max(-90,Math.min(90,p.V()-pitch*.15f)));} }""",
}

PRODUCTION = [
    "src/main/java/gg/vape/rotation/RotationMath.java",
    "src/main/java/gg/vape/rotation/RotationAngles.java",
    "src/main/java/gg/vape/rotation/WorldPointRotationTarget.java",
    "src/main/java/gg/vape/rotation/MouseRotationController.java",
    "src/main/java/gg/vape/rotation/FixedRotationController.java",
    "src/main/java/gg/vape/rotation/AdaptiveRotationController.java",
    "src/main/java/gg/vape/utils/SensitivityUtil.java",
    "src/main/java/gg/vape/utils/Vec3d.java",
    "src/main/java/gg/vape/module/combat/aimassist/AimAssistRotationSubModule.java",
    "src/main/java/gg/vape/module/combat/aimassist/AimAssistTargetingSubModule.java",
    "src/main/java/gg/vape/module/combat/aimassist/AimAssistKissSubModule.java",
    "tools/tests/RotationControllerTest.java",
]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java", default="java", help="Java runtime")
    parser.add_argument("--javac", default="javac", help="JDK compiler (default)")
    parser.add_argument("--ecj", help="Use ECJ compiler jar instead of javac")
    args = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix="vape-rotation-test-") as temp:
        base = Path(temp)
        classes = base / "classes"
        classes.mkdir()
        stub_files = []
        for name, contents in STUBS.items():
            path = base / "stubs" / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(contents, encoding="utf-8")
            stub_files.append(str(path))
        if args.ecj:
            compiler = [args.java, "-cp", args.ecj, "org.eclipse.jdt.internal.compiler.batch.Main", "-8"]
        else:
            compiler = [args.javac, "--release", "8"]
        command = compiler + ["-encoding", "UTF-8", "-d", str(classes)]
        command += stub_files + [str(ROOT / name) for name in PRODUCTION]
        subprocess.run(command, cwd=ROOT, check=True)
        subprocess.run([args.java, "-cp", str(classes), "RotationControllerTest"], cwd=ROOT, check=True)


if __name__ == "__main__":
    main()
