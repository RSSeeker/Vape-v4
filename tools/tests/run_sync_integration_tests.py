#!/usr/bin/env python3
"""Exercise the real sync workers, profile manager, and local store headlessly.

Network, Minecraft and notifications are controlled doubles. Gson and the
on-disk read/modify/write code are real, in a temporary test directory.
"""
import argparse
import importlib.util
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("profile_integration_support", ROOT / "tools/tests/run_profile_integration_tests.py")
SUPPORT = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(SUPPORT)
STUBS = dict(SUPPORT.STUBS)
vape = STUBS["gg/vape/Vape.java"]
vape = vape.replace("public int saves;", "public int saves; public volatile boolean stopped; public gg.vape.sync.SyncThread sync;")
vape = vape.replace("public ProfilesManager getProfilesManager(){return profiles;}", "public boolean isEnabled(){return stopped;} public gg.vape.sync.SyncThread getSyncThread(){return sync;} public gg.vape.account.AccountInfo getAccountInfo(){return null;} public Notifications getNotificationManager(){return new Notifications();} public void loadConfigData(JsonObject d,boolean b){} public ProfilesManager getProfilesManager(){return profiles;}")
vape = vape.replace("public void saveAndStop(){saves++; Profile active=profiles.getActiveProfileOrNull(); if(active!=null)active.setDirty(true);}", "public void saveAndStop(){saves++; if(sync!=null)sync.markSettingsDirty();}")
vape = vape.replace("public static class Friends {public boolean isFriend(Object e){return false;}}", "public static class Notifications {public void enqueue(Object n,boolean b){} public void show(Object n){}} public static class Friends {public boolean isFriend(Object e){return false;} public JsonObject toJson(){return new JsonObject();}}")
vape = vape.replace("public static class PublicProfiles {public Map<Object,PublicProfile> getProfilesById(){return Collections.emptyMap();}}", "public static class PublicProfiles {public Map<Object,PublicProfile> getProfilesById(){return Collections.emptyMap();} public void replaceProfiles(java.util.Collection<PublicProfile> p){}}")
STUBS["gg/vape/Vape.java"] = vape
STUBS["gg/vape/config/PublicProfileSettings.java"] = STUBS["gg/vape/config/PublicProfileSettings.java"].replace("framePositionsPerProfile=new Flag(false);", "framePositionsPerProfile=new Flag(false),autoSave=new Flag(false);")
STUBS["gg/vape/module/none/ClientSettings.java"] = STUBS["gg/vape/module/none/ClientSettings.java"].replace("public NumberValue rainbowSpeed;", "public NumberValue rainbowSpeed; public boolean inputEnabled; public static boolean suppressStoredCategoryFrameVisibility; public boolean isMainGuiStack(){return true;} public static void closeAllCategoryFrames(){}")
STUBS.update({
    "gg/vape/account/AccountInfo.java": "package gg.vape.account; public class AccountInfo {public boolean hasProfilesEnabled(){return false;}}",
    "gg/vape/api/ApiHttpStatusException.java": "package gg.vape.api; public class ApiHttpStatusException extends RuntimeException {public int getStatusCode(){return 500;}}",
    "gg/vape/api/UserDataResponse.java": """package gg.vape.api; import java.util.*; import com.google.gson.*; import gg.vape.config.PublicProfile; import gg.vape.sync.RemoteProfileData; public class UserDataResponse {
        public Map<String,RemoteProfileData> getProfiles(){return Collections.emptyMap();} public Map<String,PublicProfile> getPublicProfiles(){return Collections.emptyMap();} public JsonObject getFriends(){return null;} public JsonObject getOtherData(){return null;}} """,
    "gg/vape/api/ApiServices.java": """package gg.vape.api; import java.util.concurrent.*; import com.google.gson.*; import gg.vape.sync.*; public class ApiServices {
        public static final ApiServices INSTANCE=new ApiServices(); public static ApiServices getInstance(){return INSTANCE;} public final UserDataApi userData=new UserDataApi(); public UserDataApi getUserDataApi(){return userData;} public SettingsApi getSettingsApi(){return new SettingsApi();}
        public static class UserDataApi {public final BlockingQueue<JsonObject> sent=new LinkedBlockingQueue<JsonObject>(); public final BlockingQueue<CompletableFuture<ApiResponse<RemoteProfileDataMap>>> replies=new LinkedBlockingQueue<CompletableFuture<ApiResponse<RemoteProfileDataMap>>>();
            public CompletableFuture<ApiResponse<Boolean>> saveUserData(JsonObject o){return CompletableFuture.completedFuture(ApiResponse.success(true));}
            public CompletableFuture<ApiResponse<RemoteProfileDataMap>> saveProfileData(JsonObject o){sent.add(o); CompletableFuture<ApiResponse<RemoteProfileDataMap>> f=replies.poll();if(f==null)throw new IllegalStateException(\"missing fake response\");return f;}
            public CompletableFuture<ApiResponse<UserDataResponse>> getUserData(){return CompletableFuture.completedFuture(ApiResponse.failure(\"offline\"));}}
        public static class SettingsApi {public void saveSettings(Object scope,Object payload){}}} """,
    "gg/vape/config/SettingsDataType.java": "package gg.vape.config; public enum SettingsDataType {ONLINE}",
    "gg/vape/event/impl/EventTickBase.java": "package gg.vape.event.impl; import java.util.concurrent.Executor; public class EventTickBase {public static final Executor POST_TICK_EXECUTOR=Runnable::run;}",
    "gg/vape/manager/client/OnlineConnectionManager.java": """package gg.vape.manager.client; import gg.vape.value.BooleanValue; public class OnlineConnectionManager {public static final OnlineConnectionManager INSTANCE=new OnlineConnectionManager();
        public Settings getSettings(){return new Settings();} public Global getGlobalSettingsController(){return new Global();} public static class Settings {public boolean hasLoadFailed(){return true;} public Object getPayload(){return null;}}
        public static class Global {public BooleanValue getCacheData(){return BooleanValue.createWithDisplayName(null,\"cache\",\"cache\",false);} public void save(){}}} """,
    "gg/vape/notification/SettingsSyncStatusNotification.java": """package gg.vape.notification; import java.util.concurrent.*; public class SettingsSyncStatusNotification {public static final BlockingQueue<SettingsSyncStatusNotification> completed=new LinkedBlockingQueue<SettingsSyncStatusNotification>(); private int settings,profiles;
        public void setSettingsSaveStatus(int s){settings=s;} public void setProfilesSaveStatus(int s){profiles=s;} public int getSettingsSaveStatus(){return settings;} public int getProfilesSaveStatus(){return profiles;}
        public void setApiErrorDetail(String s){} public void setIoExceptionClassName(String s){} public void setIoExceptionMessage(String s){} public void complete(){completed.add(this);} public boolean hasSaveError(){return profiles>1||settings>1;}} """,
    "gg/vape/runtime/NativeBridge.java": "package gg.vape.runtime; public class NativeBridge {public static boolean[] gls(){return new boolean[]{false,false};} public static String gp(String s){return null;}}",
    "gg/vape/utils/SleepUtil.java": "package gg.vape.utils; public class SleepUtil {public static void sleep(long ms){try{Thread.sleep(ms);}catch(InterruptedException e){Thread.currentThread().interrupt();}}}",
    "gg/vape/sync/RemoteProfileData.java": """package gg.vape.sync; import java.util.UUID; import com.google.gson.JsonObject; public class RemoteProfileData {public String getName(){return \"\";} public UUID getProfileId(){return null;} public JsonObject toJson(){return new JsonObject();}} """,
    "gg/vape/sync/RemoteProfileDataMap.java": """package gg.vape.sync; import java.util.*; public class RemoteProfileDataMap {public Map<UUID,RemoteProfileData> getProfiles(){return Collections.emptyMap();}} """,
})
REAL = [name for name in SUPPORT.REAL if name != "tools/tests/ProfileIntegrationTest.java"] + [
    "src/main/java/gg/vape/api/ApiResponse.java",
    "src/main/java/gg/vape/brand/InevitablePaths.java",
    "src/main/java/gg/vape/config/LocalConfigStore.java",
    "src/main/java/gg/vape/sync/SyncThread.java",
    "src/main/java/gg/vape/sync/SyncDebounceWorker.java",
    "src/main/java/gg/vape/sync/SyncStoreRequestWorker.java",
    "tools/tests/SyncIntegrationTest.java",
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
    with tempfile.TemporaryDirectory(prefix="sync-integration-test-") as directory:
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
        subprocess.run([args.java, "-Dvape.directory=" + str(tmp / "data"), "-cp", os.pathsep.join([str(classes), str(gson)]), "gg.vape.sync.SyncIntegrationTest"], cwd=ROOT, check=True, timeout=40)


if __name__ == "__main__":
    main()
