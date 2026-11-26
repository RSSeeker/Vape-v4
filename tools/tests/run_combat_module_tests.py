#!/usr/bin/env python3
"""Compile real Triggerbot and HitSwap against deterministic headless stubs."""
import argparse
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
STUBS = {
    "gg/vape/event/Event.java": """package gg.vape.event; public class Event { public boolean canceled; public void setCancelled(boolean b){canceled=b;} }""",
    "gg/vape/event/EventPriority.java": """package gg.vape.event; public enum EventPriority { HIGH, NORMAL }""",
    "gg/vape/event/EventHandler.java": """package gg.vape.event; public @interface EventHandler { EventPriority priority() default EventPriority.NORMAL; }""",
    "gg/vape/event/impl/EventKeyPress.java": """package gg.vape.event.impl; import gg.vape.event.Event; public class EventKeyPress extends Event { public boolean down=true; public boolean isKeybinding(Object b){return true;} public boolean isDown(){return down;} }""",
    "gg/vape/event/impl/EventMouseButton.java": """package gg.vape.event.impl; import gg.vape.event.Event; public class EventMouseButton extends Event { public boolean down=true; public boolean isKeybinding(Object b){return true;} public boolean isDown(){return down;} public boolean getButtonState(){return down;} }""",
    "gg/vape/event/impl/EventPreTick.java": """package gg.vape.event.impl; import gg.vape.wrapper.impl.*; public class EventPreTick { public EntityPlayerSP getThePlayer(){return Minecraft.thePlayer();} }""",
    "gg/vape/event/impl/SyntheticAttackRequestEvent.java": """package gg.vape.event.impl; import gg.vape.event.Event; import gg.vape.module.Mod; public class SyntheticAttackRequestEvent extends Event { public Mod getSource(){return null;} }""",
    "gg/vape/module/Category.java": """package gg.vape.module; public enum Category { COMBAT,UTILITY }""",
    "gg/vape/module/Mod.java": """package gg.vape.module; public class Mod { public boolean enabled=true; public Mod(){} public Mod(String n,int id,Category c,String... d){} public boolean isEnabled(){return enabled;} public void addValue(Object... v){} public void onDisable(){} }""",
    "gg/vape/module/control/AttackCancellationAdapter.java": """package gg.vape.module.control; public interface AttackCancellationAdapter { void setCancelled(boolean b); }""",
    "gg/vape/module/control/PhysicalAttackCancellationAdapter.java": """package gg.vape.module.control; import gg.vape.event.Event; public class PhysicalAttackCancellationAdapter implements AttackCancellationAdapter { Event event; public PhysicalAttackCancellationAdapter(Event e){event=e;} public void setCancelled(boolean b){event.setCancelled(b);} }""",
    "gg/vape/module/control/SyntheticAttackCancellationAdapter.java": """package gg.vape.module.control; import gg.vape.event.Event; public class SyntheticAttackCancellationAdapter implements AttackCancellationAdapter { Event event; public SyntheticAttackCancellationAdapter(Event e){event=e;} public void setCancelled(boolean b){event.setCancelled(b);} }""",
    "gg/vape/input/AttackKeyController.java": """package gg.vape.input; import gg.vape.module.Mod; import java.util.*; import java.util.function.*; public class AttackKeyController { public static boolean accept=true,defer; public static int requests,releases,presses; public static Runnable duringEvent; private static final Queue<Runnable> pending=new ArrayDeque<Runnable>(); public static void releaseAttackKey(){releases++;} public static boolean requestSyntheticAttack(Mod m){requests++;return accept;} public static void requestValidatedSyntheticAttack(Mod m,BooleanSupplier valid,Consumer<Boolean> done){requests++;Runnable task=()->{boolean pressed=false;try{if(valid.getAsBoolean()){if(duringEvent!=null)duringEvent.run();if(accept&&valid.getAsBoolean()){presses++;pressed=true;}}}finally{done.accept(pressed);}};if(defer)pending.add(task);else task.run();} public static void runDeferred(){while(!pending.isEmpty())pending.remove().run();} public static void reset(){accept=true;defer=false;requests=0;releases=0;presses=0;duringEvent=null;pending.clear();} }""",
    "gg/vape/input/InputEventDispatcher.java": """package gg.vape.input; public class InputEventDispatcher { private static final InputEventDispatcher I=new InputEventDispatcher(); public final Focus focus=new Focus(); public static InputEventDispatcher getInstance(){return I;} public Focus getFocusState(){return focus;} public static class Focus { public boolean focused=true; public boolean isFocused(){return focused;} } }""",
    "gg/vape/config/ClientSettings.java": """package gg.vape.config; public class ClientSettings { public static boolean keyDown=true; public static boolean isKeyBindingDown(Object key){return keyDown;} }""",
    "gg/vape/mapping/MappedClasses.java": """package gg.vape.mapping; import gg.vape.wrapper.impl.*; public class MappedClasses { public static Class<?> lG=EntityOtherPlayerMP.class, YP=Axe.class, zx=Mace.class; }""",
    "gg/vape/Vape.java": """package gg.vape; import java.util.*; public class Vape { public static final Vape INSTANCE=new Vape(); public final Manager manager=new Manager(); public Manager getModManager(){return manager;} public static class Manager { public final Map<Class<?>,Object> mods=new HashMap<Class<?>,Object>(); public <T> T getMod(Class<T> c){return c.cast(mods.get(c));} } }""",
    "gg/vape/module/combat/ShieldBreaker.java": """package gg.vape.module.combat; import gg.vape.module.Mod; public class ShieldBreaker extends Mod { public boolean hasAxeInHotbar(){return false;} }""",
    "gg/vape/module/combat/AutoMace.java": """package gg.vape.module.combat; import gg.vape.module.Mod; public class AutoMace extends Mod { public boolean canHandleMaceAttack(){return false;} public boolean hasReadyMace(){return false;} public boolean isSyntheticAttackInProgress(){return false;} }""",
    "gg/vape/rotation/RotationManager.java": """package gg.vape.rotation; import gg.vape.wrapper.impl.*; public class RotationManager { public static final RotationManager INSTANCE=new RotationManager(); public RayTraceResult hit=RayTraceResult.miss(); public RayTraceResult getExtendedReachRayTrace(){return hit;} }""",
    "gg/vape/utils/TimerUtil.java": """package gg.vape.utils; public class TimerUtil { public static long now; private long at=now; public void reset(){at=now;} public boolean hasTimeElapsed(long ms){return now-at>=ms;} }""",
    "gg/vape/utils/AttackCooldownUtil.java": """package gg.vape.utils; public class AttackCooldownUtil { public static boolean ready=true; public static boolean isAttackReady(float v){return ready;} }""",
    "gg/vape/utils/RotationUtil.java": """package gg.vape.utils; import gg.vape.wrapper.impl.*; public class RotationUtil { public static boolean smash=true; public static EntityOtherPlayerMP decoy; public static boolean u(Entity e){return smash;} public static boolean n(EntityOtherPlayerMP e){return e.active();} public static EnumHand q(EntityOtherPlayerMP e){return e.active()?EnumHand.mainHand():null;} public static EntityOtherPlayerMP m(EntityPlayerSP p,double d,double a,boolean b){return decoy;} }""",
    "gg/vape/utils/ItemStackScoreUtil.java": """package gg.vape.utils; import gg.vape.wrapper.impl.*; public class ItemStackScoreUtil { public static boolean h(Item i){return i instanceof Sword;} }""",
    "gg/vape/value/BooleanValue.java": """package gg.vape.value; public class BooleanValue { public boolean value; public BooleanValue(boolean b){value=b;} public Boolean getEffectiveValue(){return value;} public static BooleanValue create(Object o,String n,boolean b,String... d){return new BooleanValue(b);} public void addDependentValues(Object... v){} }""",
    "gg/vape/value/NumberValue.java": """package gg.vape.value; public class NumberValue { public double value; public NumberValue(double v){value=v;} public Double getValue(){return value;} public static NumberValue create(Object o,String n,String f,String u,double min,double v,double max,double step,String... d){return new NumberValue(v);} }""",
    "gg/vape/value/RandomValue.java": """package gg.vape.value; public class RandomValue { public double value; public double getRandomValue(){return value;} public int getMinimumInt(){return (int)value;} public static RandomValue createWithDescription(Object o,String n,String f,String u,double a,double b,double c,double d,double step,String text){return new RandomValue();} }""",
    "gg/vape/value/EntityTargetFilterValue.java": """package gg.vape.value; import gg.vape.wrapper.impl.Entity; public class EntityTargetFilterValue { public static EntityTargetFilterValue createForModule(Object o){return new EntityTargetFilterValue();} public boolean isValidTarget(Entity e){return e!=null && e.isNotNull() && e.valid();} }""",
    "gg/vape/value/LimitValue.java": """package gg.vape.value; import java.util.*; import gg.vape.wrapper.impl.ItemStack; public class LimitValue { public static final Object ALLOW_LIST_COLOR=new Object(); public static LimitValue create(Object o,String id,String n,Object c,List<?> l){return new LimitValue();} public boolean isValid(ItemStack s,boolean b){return true;} }""",
    "gg/vape/unmap/ItemLimitData.java": """package gg.vape.unmap; public class ItemLimitData { public ItemLimitData(String s){} }""",
    "gg/vape/wrapper/impl/EnumHand.java": """package gg.vape.wrapper.impl; public class EnumHand { private static final EnumHand MAIN=new EnumHand(); public static EnumHand mainHand(){return MAIN;} }""",
    "gg/vape/wrapper/impl/Item.java": """package gg.vape.wrapper.impl; public class Item { public boolean present=true; public boolean isNull(){return !present;} public boolean isNotNull(){return present;} public boolean isInstance(Class<?> c){return present && c.isInstance(this);} public int I(ItemStack s,EntityOtherPlayerMP p){return 100;} }""",
    "gg/vape/wrapper/impl/Axe.java": """package gg.vape.wrapper.impl; public class Axe extends Item {}""",
    "gg/vape/wrapper/impl/Mace.java": """package gg.vape.wrapper.impl; public class Mace extends Item {}""",
    "gg/vape/wrapper/impl/Sword.java": """package gg.vape.wrapper.impl; public class Sword extends Item {}""",
    "gg/vape/wrapper/impl/ItemStack.java": """package gg.vape.wrapper.impl; public class ItemStack { public final Item item; public ItemStack(Item i){item=i;} public boolean isNull(){return item==null;} public boolean isNotNull(){return item!=null;} public Item getItem(){if(item!=null)return item;Item empty=new Item();empty.present=false;return empty;} }""",
    "gg/vape/wrapper/impl/InventoryPlayer.java": """package gg.vape.wrapper.impl; public class InventoryPlayer { public int selected; public ItemStack[] slots=new ItemStack[9]; public InventoryPlayer(){for(int i=0;i<9;i++)slots[i]=new ItemStack(null);} public int v(){return selected;} public void g(int i){selected=i;} public ItemStack c(int i){return slots[i];} }""",
    "gg/vape/wrapper/impl/Entity.java": """package gg.vape.wrapper.impl; public class Entity { protected Object object; public int id; public boolean valid=true,present=true; public Entity(){object=this;} public Entity(Object o){object=o;} protected Entity data(){return object instanceof Entity?(Entity)object:this;} public Object getObject(){return object;} public boolean isNull(){return object==null || !data().present;} public boolean isNotNull(){return !isNull();} public boolean isInstance(Class<?> c){return object!=null && c.isInstance(object);} public int S(){return data().id;} public boolean valid(){return data().valid;} public boolean b$src$Z$fqlxe4(){return true;} public double N(){return 0;} public double W(){return 0;} }""",
    "gg/vape/wrapper/impl/EntityOtherPlayerMP.java": """package gg.vape.wrapper.impl; public class EntityOtherPlayerMP extends Entity { public boolean blocking; public EntityOtherPlayerMP(){} public EntityOtherPlayerMP(Object o){super(o);} public boolean active(){return ((EntityOtherPlayerMP)data()).blocking;} public ItemStack i(EnumHand h){return new ItemStack(new Item());} public float d(EnumHand h){return 90;} }""",
    "gg/vape/wrapper/impl/EntityPlayerSP.java": """package gg.vape.wrapper.impl; public class EntityPlayerSP extends Entity { public InventoryPlayer inventory=new InventoryPlayer(); public int hurt; public InventoryPlayer V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6(){return inventory;} public ItemStack getHeldItemHand(){return inventory.c(inventory.v());} public ItemStack i(EnumHand h){return getHeldItemHand();} public int c$src$I$15a9iwo(){return hurt;} public boolean l$src$Z$1io4duf(){return false;} }""",
    "gg/vape/wrapper/impl/EnchantmentHelper.java": """package gg.vape.wrapper.impl; public class EnchantmentHelper { public static int e(String s,ItemStack i){return 1;} }""",
    "gg/vape/wrapper/impl/RayTraceResult_type.java": """package gg.vape.wrapper.impl; public class RayTraceResult_type { public static final RayTraceResult_type ENTITY=new RayTraceResult_type(),BLOCK=new RayTraceResult_type(),MISS=new RayTraceResult_type(); public static RayTraceResult_type entity(){return ENTITY;} public static RayTraceResult_type block(){return BLOCK;} public static RayTraceResult_type miss(){return MISS;} }""",
    "gg/vape/wrapper/impl/RayTraceResult.java": """package gg.vape.wrapper.impl; public class RayTraceResult { public RayTraceResult_type type; public Entity target; public boolean present=true; public RayTraceResult(RayTraceResult_type t,Entity e){type=t;target=e;} public static RayTraceResult miss(){return new RayTraceResult(RayTraceResult_type.miss(),new Entity(null));} public boolean isNull(){return !present;} public boolean isNotNull(){return present;} public RayTraceResult_type getTypeOfHit(){return type;} public Entity getEntity(){return target==null?new Entity(null):target;} }""",
    "gg/vape/wrapper/impl/Minecraft.java": """package gg.vape.wrapper.impl; public class Minecraft { public static final EntityPlayerSP player=new EntityPlayerSP(); public static final Screen screen=new Screen(); public static final Settings settings=new Settings(); public static EntityPlayerSP thePlayer(){return player;} public static Screen currentScreen(){return screen;} public static Settings gameSettings(){return settings;} public static class Screen { public boolean open; public boolean isNotNull(){return open;} public boolean isNull(){return !open;} } public static class Settings { public Object F(){return this;} } }""",
}
PRODUCTION = ["src/main/java/gg/vape/module/combat/Triggerbot.java", "src/main/java/gg/vape/module/combat/HitSwap.java", "tools/tests/CombatModuleTest.java"]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java", default="java")
    parser.add_argument("--javac", default="javac")
    parser.add_argument("--ecj")
    args = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix="vape-combat-test-") as temp:
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
        subprocess.run([args.java, "-cp", str(classes), "CombatModuleTest"], cwd=ROOT, check=True)


if __name__ == "__main__":
    main()
