#!/usr/bin/env python3
"""Compile real profile/snapshot/manager code with Gson and headless services.

The services and module runtime are doubles; no Minecraft rendering or gameplay
is exercised. The real manager, profile capture and detached apply paths run.
"""
import argparse
import importlib.util
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("value_profile_test_support", ROOT / "tools/tests/run_value_profile_tests.py")
SUPPORT = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(SUPPORT)
STUBS = dict(SUPPORT.STUBS)
STUBS.pop("gg/vape/config/Profile.java")
STUBS.update({
    "org/jetbrains/annotations/NotNull.java": "package org.jetbrains.annotations; public @interface NotNull {}",
    "gg/vape/Vape.java": """package gg.vape; import java.util.*; import java.util.concurrent.*; import com.google.gson.*; import gg.vape.config.*; import gg.vape.manager.client.ProfilesManager; import gg.vape.module.*; import gg.vape.value.*;
        public class Vape { public static final Vape INSTANCE=new Vape(); public final ProfilesManager profiles=new ProfilesManager(); public final ModManager mods=new ModManager(); public final Registry values=new Registry(); public final PublicProfileSettings settings=new PublicProfileSettings(); public int saves;
        public ProfilesManager getProfilesManager(){return profiles;} public ModManager getModManager(){return mods;} public Registry getValueManager(){return values;} public Registry getSettingsManager(){return values;}
        public gg.vape.config.ClientSettings getClientSettings(){return new gg.vape.config.ClientSettings();} public PublicProfileSettings getPublicProfileSettings(){return settings;}
        public Friends getFriendManager(){return new Friends();} public Enemies getEnemyManager(){return new Enemies();} public ItemHelper getItemHelper(){return new ItemHelper();}
        public Metadata getModuleProfileMetadataCodec(){return new Metadata();} public ArraysService getMacrosManager(){return new ArraysService();} public ArraysService getSearch(){return new ArraysService();}
        public PublicProfiles getPublicProfileManager(){return new PublicProfiles();} public void saveAndStop(){saves++; Profile active=profiles.getActiveProfileOrNull(); if(active!=null)active.setDirty(true);} public static void logThrowable(Throwable t){throw new RuntimeException(t);}
        public static class Registry {public int registrations; public final List<Value<?,?>> all=new ArrayList<Value<?,?>>(); public void registerValue(Value<?,?> v){registrations++;all.add(v);} public List<Value<?,?>> getValues(){return all;} public JsonArray toJson(){return new JsonArray();} public void loadJson(JsonArray a){}}
        public static class Friends {public boolean isFriend(Object e){return false;}} public static class Enemies {public boolean isEnemy(String n){return false;}} public static class ItemHelper {public boolean matchesItem(String n,gg.vape.wrapper.impl.ItemStack s){return false;}}
        public static class Metadata {public JsonObject toJson(){return new JsonObject();} public void loadJson(JsonObject o){}} public static class ArraysService {public JsonArray toJson(){return new JsonArray();} public void loadJson(JsonArray a){}}
        public static class PublicProfiles {public Map<Object,PublicProfile> getProfilesById(){return Collections.emptyMap();}}
        public static class ModManager {
            public final List<Mod> top=new ArrayList<Mod>(); public volatile CountDownLatch captureEntered,captureRelease,loadEntered,loadRelease; public volatile String blockedLoad;
            public List<Mod> getTopLevelModules(){return top;} public List<Mod> getAllModules(){return top;} public List<Mod> collectMods(){return top;}
            public <T> T getMod(Class<T> type){return type.cast(new gg.vape.module.utility.InventoryManager());}
            public JsonArray toJson(boolean pub){CountDownLatch entered=captureEntered,release=captureRelease; if(entered!=null){captureEntered=null;entered.countDown();try{release.await();}catch(InterruptedException e){throw new RuntimeException(e);}}
                JsonArray a=new JsonArray(); for(Mod m:top){JsonObject o=m.toJson(pub);if(o!=null)a.add(o);} return a;}
            public void loadJson(JsonArray a){if(blockedLoad!=null && a.toString().contains(blockedLoad)){blockedLoad=null;loadEntered.countDown();try{loadRelease.await();}catch(InterruptedException e){throw new RuntimeException(e);}}
                for(JsonElement e:a){if(!e.isJsonObject())continue;JsonObject o=e.getAsJsonObject();for(Mod m:top)if(m.getName().equalsIgnoreCase(ConfigJsonUtils.getString(o,\"name\")))m.loadJson(o);}}
            public List<Mod> getProfileModules(JsonObject o){List<Mod> r=new ArrayList<Mod>();for(Mod m:top){Boolean b=ConfigJsonUtils.getBooleanIgnoreCase(o,m.getName());if(Boolean.TRUE.equals(b))r.add(m);}return r;}
            public void disableNonHudModules(){for(Mod m:top)if(!(m instanceof gg.vape.module.render.hud.HudModule))m.enabled=false;}
            public void applyProfileModuleStates(Profile p){for(Mod m:top){Boolean b=ConfigJsonUtils.getBooleanIgnoreCase(p.getEnabledModuleStates(),m.getName());if(!(m instanceof gg.vape.module.render.hud.HudModule))m.enabled=Boolean.TRUE.equals(b);}}
            public void applyHudModuleStates(JsonObject o){for(Mod m:top)if(m instanceof gg.vape.module.render.hud.HudModule){Boolean b=ConfigJsonUtils.getBooleanIgnoreCase(o,m.getName());if(b!=null)m.enabled=b;}}
            public JsonObject getEnabledNonHudModuleStates(){JsonObject o=new JsonObject();for(Mod m:top)if(m.enabled && !(m instanceof gg.vape.module.render.hud.HudModule))o.addProperty(m.getName(),true);return o;}
            public JsonObject getEnabledHudModuleStates(){JsonObject o=new JsonObject();for(Mod m:top)if(m instanceof gg.vape.module.render.hud.HudModule)o.addProperty(m.getName(),m.enabled);return o;}
            public void disableHiddenModules(){}
        }}
    """,
    "gg/vape/module/Mod.java": """package gg.vape.module; import java.util.*; import com.google.gson.*; import gg.vape.config.ConfigJsonUtils; import gg.vape.input.BindSet; import gg.vape.value.*;
        public class Mod {public final List<Value<?,?>> values=new ArrayList<Value<?,?>>(); private final String name; private final BindSet bind=new BindSet(); public int defaultKeybind; public boolean enabled; public Category category=Category.COMBAT; private boolean visible=true;
        public Mod(){this(\"stub\");} public Mod(String n){name=n;} public String getName(){return name;} public List<Value<?,?>> getAllValues(){return values;} public void addValue(Value<?,?>... v){Collections.addAll(values,v);}
        public Category getCategory(){return category;} public boolean isDefaultVisible(){return true;} public boolean isEnabled(){return enabled;} public void setVisibility(boolean b){visible=b;} public BindSet getBind(){return bind;} public int getDefaultKeybind(){return defaultKeybind;} public void syncSubModuleStates(boolean a,boolean b){} public void p(SubModuleValue a,SubModuleValue b){}
        public JsonObject toJson(boolean pub){JsonObject o=new JsonObject();o.addProperty(\"name\",name);JsonArray a=new JsonArray();for(Value<?,?> v:values)if(v.isSerializable()&&!v.isDefault())a.add(v.toJson(pub));if(a.size()>0)o.add(\"values\",a);if(!visible)o.addProperty(\"visible\",false);return o.entrySet().size()==1?null:o;}
        public void loadJson(JsonObject o){JsonArray a=ConfigJsonUtils.getJsonArray(o,\"values\");if(a!=null)for(JsonElement e:a)if(e.isJsonObject())for(Value<?,?> v:values)if(v.matchesJsonId(e.getAsJsonObject()))v.loadJson(e.getAsJsonObject());Boolean b=ConfigJsonUtils.getBoolean(o,\"visible\");if(b!=null)visible=b;}}
    """,
    "gg/vape/config/PublicProfileSettings.java": """package gg.vape.config; public class PublicProfileSettings {public final Flag autoLoadModuleStates=new Flag(true),framePositionsPerProfile=new Flag(false); public static class Flag {private final boolean value; public Flag(boolean v){value=v;} public Boolean getEffectiveValue(){return value;}}} """,
    "gg/vape/config/ClientSettings.java": """package gg.vape.config; import gg.vape.wrapper.impl.*; public class ClientSettings {public static final String FORMAT_CODE=\"§\";
        public static boolean isReservedEntity(Entity e){return false;} public static boolean isInputDown(int code){return false;} public boolean isTeammate(EntityPlayerSP p,EntityLivingBase e){return false;} public boolean isBot(EntityLivingBase e){return false;}} """,
    "gg/vape/config/ProfileRemoteMetadata.java": """package gg.vape.config; import com.google.gson.*; public class ProfileRemoteMetadata {private final JsonObject json; private ProfileRemoteMetadata(JsonObject j){json=j;} public static ProfileRemoteMetadata fromJson(JsonElement e){return e==null||!e.isJsonObject()?null:new ProfileRemoteMetadata(e.getAsJsonObject());} public long getPublicProfileId(){return json.get(\"publicProfileId\").getAsLong();} public JsonObject toJson(){return ConfigJsonUtils.deepCopy(json);}} """,
    "gg/vape/config/PublicProfile.java": """package gg.vape.config; import java.util.*; public class PublicProfile {private final String name; private final Map<String,Object> data; private final ShareInfo info;
        public PublicProfile(String n,Map<String,Object> d,UUID derived){name=n;data=d;info=derived==null?null:new ShareInfo(derived);} public String getName(){return name;} public Map<String,Object> getData(){return data;} public ShareInfo getShareInfo(){return info;}
        public static class ShareInfo {private final UUID derived; public ShareInfo(UUID u){derived=u;} public UUID getDerivedFrom(){return derived;}}} """,
    "gg/vape/config/BuiltinProfile.java": """package gg.vape.config; public class BuiltinProfile extends Profile {public BuiltinProfile(){super(\"builtin\",\"4.21\");} public BuiltinProfile applyPreset(){captureCurrentState();return this;} public boolean isApplicable(){return true;}} """,
    "gg/vape/config/BuiltinProfileState.java": "package gg.vape.config; public class BuiltinProfileState extends BuiltinProfile {}",
    "gg/vape/config/Minecraft121BuiltinProfile.java": "package gg.vape.config; public class Minecraft121BuiltinProfile extends BuiltinProfile {}",
    "gg/vape/module/render/Search.java": "package gg.vape.module.render; public class Search extends gg.vape.module.Mod {}",
    "gg/vape/module/none/ClientSettings.java": """package gg.vape.module.none; import java.util.*; import com.google.gson.*; import gg.vape.value.NumberValue; import gg.vape.ui.click.frame.*;
        public class ClientSettings {public static ClientSettings INSTANCE=new ClientSettings(); public static FrameStackManager hotbarRuleEditorStack=new FrameStackManager(); public NumberValue rainbowSpeed;
        public static <T> T getFrame(Class<T> type){try{return type.newInstance();}catch(Exception e){throw new RuntimeException(e);}} public static List<Frame> getAllFrames(){return Collections.emptyList();}
        public static void refreshModuleCategoryHeaders(){} public static void closeListDropdowns(){} public JsonArray serializeFrameStates(){return new JsonArray();} public void loadFrameStates(JsonArray a){}
        public FrameStackManager getActiveStack(){return new FrameStackManager();} public void switchFrameStack(FrameStackManager s){} public void refreshFrameLayouts(){}} """,
    "gg/vape/ui/click/frame/Frame.java": "package gg.vape.ui.click.frame; public class Frame extends FrameComponent {}",
    "gg/vape/ui/click/frame/impl/ClientSettingsFrame.java": "package gg.vape.ui.click.frame.impl; public class ClientSettingsFrame extends gg.vape.ui.click.frame.Frame {public void d$src$V$16knweo(){}}",
    "gg/vape/ui/click/frame/impl/profile/ProfilesSettingsFrame.java": """package gg.vape.ui.click.frame.impl.profile; import gg.vape.config.Profile; public class ProfilesSettingsFrame {public void removeProfile(Profile p){} public void addProfile(Profile p){} public static void refreshProfileList(){}} """,
    "gg/vape/ui/click/component/value/AbstractListValueComponent.java": "package gg.vape.ui.click.component.value; public class AbstractListValueComponent {public void setExpanded(boolean b){}}",
    "gg/vape/ui/click/component/value/FloatingValueDropdownLayer.java": """package gg.vape.ui.click.component.value; public class FloatingValueDropdownLayer extends gg.vape.ui.click.frame.Frame {public Object getSourceComponent(){return new AbstractListValueComponent();}} """,
    "gg/vape/event/EventBus.java": "package gg.vape.event; public class EventBus {private static final EventBus I=new EventBus(); public static EventBus getInstance(){return I;} public void post(Object e){}}",
    "gg/vape/event/impl/ProfileChangeEvent.java": "package gg.vape.event.impl; import gg.vape.config.Profile; public class ProfileChangeEvent {public ProfileChangeEvent(Profile a,Profile b){}}",
    "gg/vape/event/impl/ProfileListMutationAction.java": "package gg.vape.event.impl; public enum ProfileListMutationAction {ADD,REMOVE}",
    "gg/vape/event/impl/ProfileListMutationEvent.java": """package gg.vape.event.impl; import gg.vape.config.Profile; public class ProfileListMutationEvent {public ProfileListMutationEvent(Profile p,ProfileListMutationAction a){} public void fire(){}} """,
})
REAL = [name for name in SUPPORT.REAL if name != "tools/tests/ValueProfileSerializationTest.java"] + [
    "src/main/java/gg/vape/config/Profile.java",
    "src/main/java/gg/vape/api/PublicProfilePartialJsonPayloadBuilder.java",
    "src/main/java/gg/vape/config/ProfilesSyncPayloadBuilder.java",
    "src/main/java/gg/vape/manager/client/ProfilesManager.java",
    "tools/tests/ProfileIntegrationTest.java",
]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--gson", required=True)
    parser.add_argument("--javac", default="javac")
    parser.add_argument("--java", default="java")
    args = parser.parse_args()
    gson = Path(args.gson).resolve()
    if not gson.is_file():
        parser.error("Gson JAR does not exist: " + str(gson))
    with tempfile.TemporaryDirectory(prefix="profile-integration-test-") as directory:
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
        subprocess.run([args.javac, "--release", "8", "-encoding", "UTF-8", "-cp", str(gson), "-d", str(classes)] + sources,
                       cwd=ROOT, check=True)
        subprocess.run([args.java, "-cp", os.pathsep.join([str(classes), str(gson)]), "ProfileIntegrationTest"], cwd=ROOT, check=True)


if __name__ == "__main__":
    main()
