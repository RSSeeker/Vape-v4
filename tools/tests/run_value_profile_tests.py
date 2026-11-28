#!/usr/bin/env python3
"""Test real value and profile-snapshot serialization with Gson and headless services.

This does not exercise the Minecraft runtime, complete profile manager or GUI.
"""
import argparse
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
STUBS = {
    "org/jetbrains/annotations/Nullable.java": "package org.jetbrains.annotations; import java.lang.annotation.*; @Target({ElementType.TYPE_USE,ElementType.METHOD,ElementType.PARAMETER,ElementType.FIELD}) public @interface Nullable {}",
    "gg/vape/Vape.java": """package gg.vape; import gg.vape.value.Value; public class Vape {
        public static final Vape INSTANCE=new Vape(); public int saves; public void saveAndStop(){saves++;} public static void logThrowable(Throwable t){}
        public Registry getSettingsManager(){return new Registry();} public Registry getValueManager(){return new Registry();}
        public Friends getFriendManager(){return new Friends();} public Enemies getEnemyManager(){return new Enemies();}
        public gg.vape.config.ClientSettings getClientSettings(){return new gg.vape.config.ClientSettings();}
        public static class Registry { public void registerValue(Value<?,?> value){} }
        public static class Friends {public boolean isFriend(Object entity){return false;}}
        public static class Enemies {public boolean isEnemy(String name){return false;}}
    }""",
    "gg/vape/unmap/PropertyContainer.java": "package gg.vape.unmap; public class PropertyContainer {}",
    "gg/vape/config/PublicProfileSettings.java": "package gg.vape.config; public class PublicProfileSettings {}",
    "gg/vape/config/ProfileSnapshot.java": "package gg.vape.config; public class ProfileSnapshot {public Profile getProfile(){return null;}}",
    "gg/vape/config/Profile.java": "package gg.vape.config; import java.util.*; import gg.vape.module.Mod; public class Profile {public Set<Mod> getEnabledModules(){return Collections.emptySet();}}",
    "gg/vape/module/Category.java": "package gg.vape.module; public class Category {public static final Category NONE=new Category();}",
    "gg/vape/module/Mod.java": """package gg.vape.module; import java.util.*; import gg.vape.input.BindSet; import gg.vape.value.*;
        public class Mod { public final List<Value<?,?>> values=new ArrayList<>(); private final String name; private final BindSet bind=new BindSet();
        public Mod(){this(\"stub\");} public Mod(String name){this.name=name;} public String getName(){return name;}
        public List<Value<?,?>> getAllValues(){return values;} public void addValue(Value<?,?>... v){Collections.addAll(values,v);}
        public Category getCategory(){return Category.NONE;} public boolean isDefaultVisible(){return true;} public BindSet getBind(){return bind;}
        public void p(SubModuleValue a,SubModuleValue b){} }""",
    "gg/vape/module/SubModule.java": "package gg.vape.module; public class SubModule extends Mod { public Object getParent(){return null;} }",
    "gg/vape/ui/click/component/GuiComponent.java": "package gg.vape.ui.click.component; public class GuiComponent {}",
    "gg/vape/ui/click/component/value/ValueComponentFactory.java": "package gg.vape.ui.click.component.value; public class ValueComponentFactory {public static Object getComparableListEntryText(Object o){return o;}}",
    "gg/vape/input/KeyboardCodeUtil.java": "package gg.vape.input; public class KeyboardCodeUtil {public static int convertLegacyKeyCode(int k){return k;}}",
    "gg/vape/input/BindActivationMode.java": "package gg.vape.input; public enum BindActivationMode {TOGGLE,HELD}",
    "gg/vape/input/BindSet.java": """package gg.vape.input; import java.util.*; import com.google.gson.*; public class BindSet {
        private List<Integer> keys; private List<Integer> defaults; private BindActivationMode mode=BindActivationMode.TOGGLE; private boolean modes;
        public BindSet(){this(new ArrayList<Integer>(),false,false);} public BindSet(int k){this(Arrays.asList(k),false,false);}
        public BindSet(List<Integer> keys,boolean register,boolean modes){this.keys=new ArrayList<>(keys);this.defaults=new ArrayList<>(keys);this.modes=modes;}
        public void setBoundInputs(List<Integer> keys){this.keys=new ArrayList<>(keys);} public List<Integer> getBoundInputs(){return keys;}
        public List<Integer> getDefaultKeys(){return defaults;} public boolean supportsActivationMode(){return modes;}
        public void setActivationMode(BindActivationMode mode){this.mode=mode;} public BindActivationMode getActivationMode(){return mode;}
        public boolean hasValidBinding(){return !keys.isEmpty();} public boolean usesOwnKeybindStorage(){return true;}
        public String getBindText(){return keys.toString();} public JsonArray serializeBoundInputs(){JsonArray a=new JsonArray();for(int k:keys)a.add(k);return a;}
        public void loadBoundInputs(JsonArray a,boolean convert){keys.clear();if(a!=null)for(JsonElement e:a)keys.add(e.getAsInt());}
    }""",
    "gg/vape/module/none/ClientSettings.java": "package gg.vape.module.none; import gg.vape.value.NumberValue; public class ClientSettings {public static ClientSettings INSTANCE=new ClientSettings(); public NumberValue rainbowSpeed;}",
    "gg/vape/config/ClientSettings.java": """package gg.vape.config; import gg.vape.wrapper.impl.*; public class ClientSettings {
        public static boolean isReservedEntity(Entity e){return false;} public boolean isTeammate(EntityPlayerSP p,EntityLivingBase e){return false;} public boolean isBot(EntityLivingBase e){return false;}
    }""",
    "gg/vape/unmap/ColorUtil.java": "package gg.vape.unmap; import java.awt.Color; public class ColorUtil {public static Color createReadableHsbColor(float h,float s,float b){return Color.getHSBColor(h,s,b);}}",
    "gg/vape/utils/MathUtil.java": "package gg.vape.utils; public class MathUtil {public static double roundToIncrement(double n,double increment){return Math.round(n/increment)*increment;}}",
    "gg/vape/utils/MutableColor.java": "package gg.vape.utils; import java.awt.Color; public class MutableColor extends Color {public MutableColor(int rgb){super(rgb);} public MutableColor withAlpha(int alpha){return this;}}",
    "gg/vape/utils/RotationUtil.java": "package gg.vape.utils; import gg.vape.wrapper.impl.EntityLivingBase; public class RotationUtil {public static boolean k(EntityLivingBase e){return false;} public static boolean b$src$Z$reqs95(EntityLivingBase e){return false;}}",
    "gg/vape/mapping/MappedClasses.java": "package gg.vape.mapping; public class MappedClasses {public static Class zS,zm,FT,lG,Yw,Fr,Zo,ur,Z8,ZP,Fb,qf;}",
    "gg/vape/wrapper/impl/Entity.java": "package gg.vape.wrapper.impl; public class Entity {public boolean isNull(){return false;} public boolean isInstance(Class c){return false;} public Object getObject(){return null;} public String getName(){return \"\";}}",
    "gg/vape/wrapper/impl/EntityLivingBase.java": "package gg.vape.wrapper.impl; public class EntityLivingBase extends Entity {public EntityLivingBase(Object o){} public float w$src$F$15l9epb(){return 1;}}",
    "gg/vape/wrapper/impl/EntityPlayerSP.java": "package gg.vape.wrapper.impl; public class EntityPlayerSP extends EntityLivingBase {public EntityPlayerSP(){super(null);} public boolean canEntityBeSeen(Entity e){return true;}}",
    "gg/vape/wrapper/impl/Minecraft.java": "package gg.vape.wrapper.impl; public class Minecraft {public static EntityPlayerSP thePlayer(){return new EntityPlayerSP();}}",
    "gg/vape/wrapper/impl/ForgeVersion.java": "package gg.vape.wrapper.impl; public class ForgeVersion {public static final ForgeVersion MC_1_7_10=new ForgeVersion(),MC_1_21_4=new ForgeVersion(); public boolean Y(){return false;} public boolean d(){return false;}}",
    "gg/vape/value/ListValue.java": """package gg.vape.value; import java.util.*; public class ListValue extends Value<List,ListValue> {
        public ListValue(){super(null,\"list\",new ArrayList());} public void parse(String s){} public ListValue copyValueDefinition(){return null;}
    }""",
}
REAL = [
    "src/main/java/gg/vape/unmap/INamed.java",
    "src/main/java/gg/vape/unmap/ModeSelection.java",
    "src/main/java/gg/vape/unmap/ModeOption.java",
    "src/main/java/gg/vape/utils/Base64Util.java",
    "src/main/java/gg/vape/config/ConfigJsonUtils.java",
    "src/main/java/gg/vape/config/ProfileModuleSnapshot.java",
    "src/main/java/gg/vape/value/Value.java",
    "src/main/java/gg/vape/value/ValueAccessor.java",
    "src/main/java/gg/vape/value/DirectValueAccessor.java",
    "src/main/java/gg/vape/value/SnapshotValueAccessor.java",
    "src/main/java/gg/vape/value/ValueChangeListener.java",
    "src/main/java/gg/vape/value/ValueChangeValidator.java",
    "src/main/java/gg/vape/value/ValueCondition.java",
    "src/main/java/gg/vape/value/ConditionalValue.java",
    "src/main/java/gg/vape/value/BooleanValue.java",
    "src/main/java/gg/vape/value/NumberValue.java",
    "src/main/java/gg/vape/value/ColorValue.java",
    "src/main/java/gg/vape/value/EntityTargetFilterValue.java",
    "src/main/java/gg/vape/value/BindValue.java",
    "src/main/java/gg/vape/value/ModeValue.java",
    "src/main/java/gg/vape/value/SubModuleValue.java",
    "src/main/java/gg/vape/value/StringValue.java",
    "src/main/java/gg/vape/value/StringMapValue.java",
    "src/main/java/gg/vape/value/ValueSnapshot.java",
    "tools/tests/ValueProfileSerializationTest.java",
]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--gson", required=True, help="Gson 2.10.1 JAR")
    parser.add_argument("--javac", default="javac")
    parser.add_argument("--java", default="java")
    args = parser.parse_args()
    gson = Path(args.gson).resolve()
    if not gson.is_file():
        parser.error("Gson JAR does not exist: " + str(gson))
    with tempfile.TemporaryDirectory(prefix="value-profile-test-") as directory:
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
        subprocess.run([args.javac, "--release", "8", "-encoding", "UTF-8", "-cp", str(gson),
                        "-d", str(classes)] + sources, cwd=ROOT, check=True)
        subprocess.run([args.java, "-cp", os.pathsep.join([str(classes), str(gson)]),
                        "ValueProfileSerializationTest"], cwd=ROOT, check=True)


if __name__ == "__main__":
    main()
