#!/usr/bin/env python3
"""Exercise real ScriptModule/ScriptRuntimeHost with Rhino and headless doubles.

The engine is real; Minecraft, UI, value persistence and the broad script API
are test doubles. This is not a live Minecraft or full script-API test.
"""
import argparse
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
STUBS = {
    "gg/vape/Vape.java": """package gg.vape; public class Vape { public static final Vape INSTANCE=new Vape(); public static void debugLog(String s){} public static void logError(String s){} }""",
    "gg/vape/event/EventHandler.java": """package gg.vape.event; public @interface EventHandler {}""",
    "gg/vape/event/impl/EventChat.java": """package gg.vape.event.impl; public class EventChat { public Text getMessage(){return null;} public static class Text {public String getFormattedText(){return \"\";}} }""",
    "gg/vape/event/impl/EventKeyPress.java": """package gg.vape.event.impl; public class EventKeyPress {public int getKey(){return 0;} public boolean isDown(){return false;}}""",
    "gg/vape/event/impl/EventPacketReceive.java": """package gg.vape.event.impl; public class EventPacketReceive {public Object getPacketInstance(){return null;} public void setCancelled(boolean b){}}""",
    "gg/vape/event/impl/EventPacketSend.java": """package gg.vape.event.impl; public class EventPacketSend {public Object getPacketInstance(){return null;} public void setCancelled(boolean b){}}""",
    "gg/vape/event/impl/EventPreAttack.java": """package gg.vape.event.impl; public class EventPreAttack {public Target getTarget(){return null;} public static class Target {public Object getObject(){return null;}}}""",
    "gg/vape/event/impl/EventPreMove.java": """package gg.vape.event.impl; public class EventPreMove {public double getX(){return 0;} public double getY(){return 0;} public double getZ(){return 0;}}""",
    "gg/vape/event/impl/EventRender2D.java": """package gg.vape.event.impl; public class EventRender2D {public int getDisplayWidth(){return 0;} public int getDisplayHeight(){return 0;}}""",
    "gg/vape/event/impl/EventPrePlayerTick.java": """package gg.vape.event.impl; public class EventPrePlayerTick {}""",
    "gg/vape/event/impl/EventWorldChange.java": """package gg.vape.event.impl; public class EventWorldChange {}""",
    "gg/vape/input/BindSet.java": """package gg.vape.input; import java.util.*; public class BindSet {private List<Integer> keys=new ArrayList<Integer>(); public List<Integer> getBoundInputs(){return keys;} public void setBoundInputs(List<Integer> v){keys=new ArrayList<Integer>(v);}}""",
    "gg/vape/manager/ExtensionManager.java": """package gg.vape.manager; public class ExtensionManager {public static void logScriptFailure(String s){}}""",
    "gg/vape/module/Category.java": """package gg.vape.module; public class Category {public static final Category EXTENSION=new Category();}""",
    "gg/vape/module/MinecraftVersionConstraint.java": """package gg.vape.module; public class MinecraftVersionConstraint {}""",
    "gg/vape/module/Mod.java": """package gg.vape.module; import java.util.*; import gg.vape.value.Value; public class Mod {private String name; private boolean enabled; private final List<Value<?,?>> visible=new ArrayList<Value<?,?>>(),all=new ArrayList<Value<?,?>>(); public Mod(String n,int ignored,Category c,String tip){name=n;} public String getName(){return name;} public List<Value<?,?>> getValues(){return visible;} public List<Value<?,?>> getAllValues(){return all;} public void addValue(Value<?,?> v){visible.add(v);all.add(v);} public Value<?,?> getValue(String name){for(Value<?,?> v:all)if(v.getName().equalsIgnoreCase(name))return v;return null;} public boolean isEnabled(){return enabled;} public void setEnabled(boolean value){if(enabled==value)return;enabled=value;if(value)onEnable();else onDisable();} public void onEnable(){} public void onDisable(){} }""",
    "gg/vape/script/ScriptStorage.java": """package gg.vape.script; import java.io.File; public class ScriptStorage {public ScriptStorage(File f){}}""",
    "gg/vape/script/ScriptTranslation.java": """package gg.vape.script; public class ScriptTranslation {public static void register(String a,String b){}}""",
    "gg/vape/script/ScriptDrawContext.java": """package gg.vape.script; import gg.vape.event.impl.EventRender2D; public class ScriptDrawContext {public static void begin(EventRender2D e){} public static void end(){}}""",
    "gg/vape/unmap/ColorUtil.java": """package gg.vape.unmap; public class ColorUtil {public static int packRgba(int r,int g,int b,int a){return a<<24|r<<16|g<<8|b;}}""",
    "gg/vape/unmap/ModeSelection.java": """package gg.vape.unmap; import gg.vape.value.ModeValue; public class ModeSelection {private final String name; private ModeValue mode; public ModeSelection(String n){name=n;} public String getName(){return name;} public ModeValue getMode(){return mode;} public void attachToMode(ModeValue m){mode=m;}}""",
    "gg/vape/value/Value.java": """package gg.vape.value; public class Value<K,T extends Value<K,T>> {private final String name; private K value; public Value(Object owner,String name,K value){this.name=name;this.value=value;} public String getName(){return name;} public K getValue(){return value;} public void setValue(K value){this.value=value;} public void notifyChangeListeners(){} }""",
    "gg/vape/value/BooleanValue.java": """package gg.vape.value; public class BooleanValue extends Value<Boolean,BooleanValue> {public BooleanValue(Object o,String n,boolean v){super(o,n,v);}}""",
    "gg/vape/value/NumberValue.java": """package gg.vape.value; public class NumberValue extends Value<Double,NumberValue> {public NumberValue(Object o,String n,double v,double min,double max,String f,String u){super(o,n,v);}}""",
    "gg/vape/value/StringValue.java": """package gg.vape.value; public class StringValue extends Value<String,StringValue> {public StringValue(Object o,String n,String v){super(o,n,v);}}""",
    "gg/vape/value/ModeValue.java": """package gg.vape.value; import gg.vape.unmap.ModeSelection; public class ModeValue extends Value<ModeSelection,ModeValue> {private final ModeSelection[] modes; public ModeValue(Object o,String n,ModeSelection v,ModeSelection[] m){super(o,n,v);modes=m;} public static ModeValue create(Object o,String n,ModeSelection v,ModeSelection... m){ModeValue x=new ModeValue(o,n,v,m);for(ModeSelection s:m)s.attachToMode(x);return x;} public ModeSelection[] getModes(){return modes;}}""",
    "gg/vape/value/ColorValue.java": """package gg.vape.value; import java.awt.Color; public class ColorValue extends Value<Color,ColorValue> {private ColorValue(Object o,String n,Color c){super(o,n,c);} public static ColorValue create(Object o,String n,Color c){return new ColorValue(o,n,c);} public Color getDisplayColor(){return getValue();} public void setColor(Color c){setValue(c);}}""",
    "gg/vape/value/BindValue.java": """package gg.vape.value; import gg.vape.input.BindSet; import java.util.*; public class BindValue extends Value<BindSet,BindValue> {private BindValue(Object o,String n){super(o,n,new BindSet());} public static BindValue createEmpty(Object o,String n){return new BindValue(o,n);} public static BindValue createWithKey(Object o,String n,int k){BindValue v=new BindValue(o,n);v.getValue().setBoundInputs(Collections.singletonList(k));return v;}}""",
    "gg/vape/script/ScriptApi.java": r"""package gg.vape.script;
import org.mozilla.javascript.*; import java.util.function.Supplier; import java.util.function.Consumer;
public class ScriptApi {
    public static void install(Context cx,Scriptable scope,ScriptSettingHost host){
        Scriptable settings=cx.newObject(scope);
        for(String kind:new String[]{"toggle","number","text","mode","color","keybind"}){
            ScriptableObject.putProperty(settings,kind,new BaseFunction(){public Object call(Context c,Scriptable s,Scriptable t,Object[] a){return host.createSetting(c,s,kind,a);}});
        }
        ScriptableObject.putProperty(scope,"setting",settings);
        ScriptableObject.putProperty(scope,"load",new BaseFunction(){public Object call(Context c,Scriptable s,Scriptable t,Object[] a){host.getRuntime().loadAdditionalScript(c,s,stringArgument(a,0));return Undefined.instance;}});
        ScriptableObject.putProperty(scope,"after",new BaseFunction(){public Object call(Context c,Scriptable s,Scriptable t,Object[] a){host.getRuntime().schedule((long)numberArgument(a,0,0),(Function)a[1]);return Undefined.instance;}});
    }
    public static void updateBindings(Context c,Scriptable s){}
    public static String[] localizedName(Context c,Object[] a,int i){return new String[]{stringArgument(a,i),""};}
    public static boolean booleanArgument(Object[] a,int i,boolean d){return i<a.length?Context.toBoolean(a[i]):d;}
    public static double numberArgument(Object[] a,int i,double d){return i<a.length?Context.toNumber(a[i]):d;}
    public static String stringArgument(Object[] a,int i){return i<a.length?Context.toString(a[i]):"";}
    public static String[] stringArrayArgument(Object[] a,int i){if(i>=a.length||!(a[i] instanceof Scriptable))return new String[0];Scriptable v=(Scriptable)a[i];int n=(int)Context.toNumber(ScriptableObject.getProperty(v,"length"));String[] out=new String[n];for(int j=0;j<n;j++)out[j]=Context.toString(ScriptableObject.getProperty(v,j));return out;}
    public static int colorArgument(Object[] a,int i,int d){return i<a.length?(int)Context.toNumber(a[i]):d;}
    public static Object newSettingHandle(Context c,Scriptable s,String kind,String name,Supplier<?> get,Consumer<Object> set){
        Scriptable out=c.newObject(s); ScriptableObject.putProperty(out,"get",new BaseFunction(){public Object call(Context cx,Scriptable scope,Scriptable self,Object[] args){return get.get();}});
        ScriptableObject.putProperty(out,"set",new BaseFunction(){public Object call(Context cx,Scriptable scope,Scriptable self,Object[] args){set.accept(args.length==0?null:args[0]);return Undefined.instance;}});return out;
    }
    public static Scriptable newPacketEvent(Context c,Scriptable s,String k,Object p,Runnable r){return c.newObject(s);}
    public static Scriptable newRenderEvent(Context c,Scriptable s,int w,int h){return c.newObject(s);}
    public static Scriptable newKeyEvent(Context c,Scriptable s,int k,boolean d){return c.newObject(s);}
    public static Scriptable newChatEvent(Context c,Scriptable s,String t){return c.newObject(s);}
    public static Scriptable newAttackEvent(Context c,Scriptable s,Object t){return c.newObject(s);}
    public static Scriptable newMoveEvent(Context c,Scriptable s,double x,double y,double z){return c.newObject(s);}
} """,
}

TEST = r"""
import gg.vape.module.extension.ScriptModule;
import gg.vape.value.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
public class ScriptReloadTest {
    static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
    static void write(Path p,String s)throws Exception{Files.write(p,s.getBytes(StandardCharsets.UTF_8));}
    public static void main(String[] args)throws Exception{
        Path dir=Files.createTempDirectory("script-reload-"); Path main=dir.resolve("main.js"),lib=dir.resolve("lib.js");
        write(lib,"var includeCount=(typeof includeCount==='undefined'?0:includeCount)+1; setting.text('Library','lib');");
        write(main,"var alpha=setting.number('Alpha',1,0,100); setting.toggle('Keep',true); setting.mode('Choice','One',['One','Two']); load('lib.js'); load('lib.js'); load('main.js'); if(includeCount!==1)throw Error('include'); after(0,function(){alpha.set(99);}); function onTick(){alpha.set(alpha.get()+1);}");
        ScriptModule m=new ScriptModule("Example","", "test",main.toString());
        check(m.prepareSettings(),"initial declarations load");
        NumberValue alpha=(NumberValue)m.getValue("alpha"); BooleanValue keep=(BooleanValue)m.getValue("Keep");
        ModeValue mode=(ModeValue)m.getValue("Choice");
        check(mode.getModes()[0].getMode()==mode,"mode options attached for persistence");
        alpha.setValue(7.0); keep.setValue(false);
        write(main,"var alpha=setting.number('alpha',0,0,100); setting.text('New','new'); load('lib.js'); if(includeCount!==1)throw Error('include reload'); after(0,function(){alpha.set(8);}); function onTick(){alpha.set(alpha.get()+1);}");
        check(m.reloadScript(),"reload executes declarations again");
        check(m.getValue("Alpha")==alpha && alpha.getValue()==7.0,"value survives case-insensitive redeclaration");
        check(m.getValues().contains(alpha)&&m.getValues().contains(m.getValue("New")),"retained and new values visible");
        check(!m.getValues().contains(keep)&&m.getAllValues().contains(keep),"retired definition retained for profiles");
        check(m.getValues().contains(m.getValue("Library")),"library re-executes on reload");
        m.getRuntime().tickScheduler(); check(alpha.getValue()==8.0,"old scope callbacks were discarded");
        m.getRuntime().invoke("onTick");check(alpha.getValue()==9.0,"new scope callbacks run");
        write(main,"setting.toggle('KEEP',true); load('lib.js'); if(includeCount!==1)throw Error('include again');");
        check(m.reloadScript(),"redeclaration reload");
        check(m.getValue("Keep")==keep && m.getValues().contains(keep) && !keep.getValue(),"retired value restored unchanged");
        check(!m.getValues().contains(alpha),"undeclared value hidden");
        m.setEnabled(true);
        write(main,"setting.text('Partial','x'); after(0,function(){throw Error('partial timer');}); throw Error('intentional');");
        check(!m.reloadScript(),"failed top-level execution reported");
        check(!m.isEnabled()&&m.getRuntime().isBroken(),"failed load disables module and marks runtime broken");
        check(m.getValue("Partial")==null && m.getValues().contains(keep),"partial declaration rolled back");
        m.getRuntime().tickScheduler();
        write(main,"setting.toggle('Keep',true); load('lib.js'); if(includeCount!==1)throw Error('recovery include');");
        check(m.reloadScript()&&!m.getRuntime().isBroken(),"valid script recovers after failure");
        check(m.getValue("Keep")==keep && !keep.getValue(),"settings survive failure and recovery");
        write(main,"function broken( {");
        check(!m.reloadScript(),"compile failure reported");
        check(m.getValues().contains(keep),"compile failure retains visible declarations");
        System.out.println("Script reload, settings, includes and callback checks passed (real Rhino, headless doubles)");
    }
}
"""


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--rhino", required=True, help="Path to a Rhino 1.7.15 JAR")
    parser.add_argument("--javac", default="javac")
    parser.add_argument("--java", default="java")
    args = parser.parse_args()
    rhino = Path(args.rhino).resolve()
    if not rhino.is_file():
        parser.error("Rhino JAR does not exist: " + str(rhino))
    with tempfile.TemporaryDirectory(prefix="script-reload-test-") as tmp:
        tmp = Path(tmp)
        sources = []
        for name, content in STUBS.items():
            path = tmp / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content, encoding="utf-8")
            sources.append(str(path))
        test = tmp / "ScriptReloadTest.java"
        test.write_text(TEST, encoding="utf-8")
        sources += [str(test), str(ROOT / "src/main/java/gg/vape/script/ScriptSettingHost.java"),
                    str(ROOT / "src/main/java/gg/vape/script/ScriptRuntimeHost.java"),
                    str(ROOT / "src/main/java/gg/vape/module/extension/ScriptModule.java")]
        classes = tmp / "classes"
        classes.mkdir()
        subprocess.run([args.javac, "--release", "8", "-encoding", "UTF-8", "-cp", str(rhino),
                        "-d", str(classes)] + sources, cwd=ROOT, check=True)
        import os
        subprocess.run([args.java, "-cp", os.pathsep.join([str(classes), str(rhino)]), "ScriptReloadTest"],
                       cwd=ROOT, check=True)


if __name__ == "__main__":
    main()
