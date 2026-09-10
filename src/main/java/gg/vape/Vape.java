package gg.vape;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import gg.vape.account.AccountInfo;
import gg.vape.account.AccountInfoResponse;
import gg.vape.account.LicenseManager;
import gg.vape.api.ApiAccessTokenProvider;
import gg.vape.api.ApiResponse;
import gg.vape.api.ApiServices;
import gg.vape.asm.helper.DescUtils;
import gg.vape.combat.AttackStrengthTracker;
import gg.vape.config.ConfigJsonUtils;
import gg.vape.config.ModuleProfileMetadataCodec;
import gg.vape.config.Profile;
import gg.vape.config.PublicProfileSettings;
import gg.vape.event.ClientListenerBootstrap;
import gg.vape.event.EventBus;
import gg.vape.event.EventNameFormatRewriteService;
import gg.vape.event.IEvent;
import gg.vape.event.impl.EventRenderWorldPassExecutorDrain;
import gg.vape.event.listener.ClientSettingsEventForwarder;
import gg.vape.event.listener.EventTimingOverlayListener;
import gg.vape.event.listener.VapeClientEventListener;
import gg.vape.event.listener.VapeLifecycleEventListener;
import gg.vape.event.listener.VapeShutdownEventListener;
import gg.vape.event.listener.WorldChangeEventDispatcher;
import gg.vape.friend.FriendAliasEventListener;
import gg.vape.friend.OnlineFriendActivityListener;
import gg.vape.friend.ping.PingManager;
import gg.vape.friend.ui.OnlineActivityPanelOptions;
import gg.vape.input.InputEventDispatcher;
import gg.vape.lifecycle.ClientDirectoryCleanupCallback;
import gg.vape.manager.MacroManager;
import gg.vape.manager.ModManager;
import gg.vape.manager.SearchManager;
import gg.vape.manager.ValueManager;
import gg.vape.manager.client.EnemyManager;
import gg.vape.manager.client.FriendManager;
import gg.vape.manager.client.IndependentSettingsManager;
import gg.vape.manager.client.OnlineConnectionManager;
import gg.vape.manager.client.OnlineFriendManager;
import gg.vape.manager.client.OnlineManager;
import gg.vape.manager.client.ProfilesManager;
import gg.vape.manager.client.PublicProfileManager;
import gg.vape.mapping.MappedClasses;
import gg.vape.mapping.Mapper;
import gg.vape.mapping.MappingProfileSnapshotRegistry;
import gg.vape.mapping.PrimaryMappingTaskSet;
import gg.vape.mapping.runtime.RuntimeNameMappingRegistry;
import gg.vape.module.blatant.AntiBot;
import gg.vape.combat.AttackPacketTimingTracker;
import gg.vape.module.macro.MacroEventListener;
import gg.vape.module.none.ClientSettings;
import gg.vape.module.render.nametags.NameTagsRenderStateTracker;
import gg.vape.module.render.entity.RenderEntityContextCacheListener;
import gg.vape.module.utility.inventory.ItemStackSemanticResolver;
import gg.vape.module.utility.inventory.cleaner.InventoryFilterPresetRegistry;
import gg.vape.module.utility.inventory.cleaner.ui.InventoryCleanerProfileValueRefreshListener;
import gg.vape.movement.PlayerMovementTaskManager;
import gg.vape.module.combat.SilentAura;
import gg.vape.notification.NotificationManager;
import gg.vape.notification.NotificationSoundPlayer;
import gg.vape.notification.NotificationText;
import gg.vape.notification.NotificationType;
import gg.vape.rotation.RotationManager;
import gg.vape.runtime.NativeBridge;
import gg.vape.service.VapeServiceLauncher;
import gg.vape.status.NativePresenceUpdater;
import gg.vape.sync.SyncThread;
import gg.vape.tutorial.TutorialManager;
import gg.vape.ui.click.component.GuiComponent;
import gg.vape.ui.click.component.value.FloatingValueDropdownLayer;
import gg.vape.ui.click.frame.Frame;
import gg.vape.ui.font.FontManager;
import gg.vape.ui.font.FontOption;
import gg.vape.ui.font.FontSelector;
import gg.vape.unmap.BendableInputDispatcher;
import gg.vape.unmap.GLUtils;
import gg.vape.unmap.ItemHelper;
import gg.vape.utils.AttackCooldownUtil;
import gg.vape.utils.Base64Util;
import gg.vape.utils.TimerUtil;
import gg.vape.utils.network.PacketDispatchGuard;
import gg.vape.utils.render.GuiRenderPrimitives;
import gg.vape.utils.render.ImageRenderer;
import gg.vape.utils.render.OpenGlDeviceInfo;
import gg.vape.utils.render.RenderBatchManager;
import gg.vape.wrapper.impl.ForgeVersion;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.PotionRegistry;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Array;
import java.util.Date;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

public class Vape {
    private static int opaqueState; // obfuscation opaque-predicate counter (get=getOpaqueState(), set=setOpaqueState(int)); do not treat as meaningful state
    public static Vape INSTANCE;
    public static boolean mappingsLoaded;
    public boolean enabled;
    public static boolean renderReady;
    public static final boolean DEBUG = false; // always false; likely dead compile-time debug toggle
    public static final boolean DEV = false; // always false; likely dead compile-time toggle
    public static final String VERSION;
    static TimerUtil loadTimer;
    private boolean forgeAbsent;
    private boolean mappingsRemapped;
    private boolean vanillaMinecraftPresentCache;
    private boolean renderInitialized;
    private boolean vanillaMinecraftChecked;
    private boolean pendingTickAction; // one-shot: set via setPendingTickAction(true), consumed+cleared next client tick in EventTickBase.fire()
    private boolean unclassifiedFlag463; // purpose unconfirmed: setUnclassifiedFlag463(boolean)/isUnclassifiedFlag463Set have no discoverable callers
    private FriendManager friendManager;
    private FontManager fontManager;
    private ItemHelper itemHelper;
    private ProfilesManager profilesManager;
    private ClientListenerBootstrap clientListenerBootstrap;
    private PublicProfileSettings publicProfileSettings;
    private InventoryFilterPresetRegistry inventoryFilterPresetRegistry;
    private NotificationSoundPlayer notificationSoundPlayer;
    private GLUtils glUtils;
    private SearchManager searchManager;
    private EnemyManager enemyManager;
    private PublicProfileManager publicProfileManager;
    private AccountInfo accountInfo;
    private MacroManager macroManager;
    private String cachedAllData;
    private OnlineFriendManager onlineFriendManager;
    private ModManager modManager;
    private LicenseManager licenseManager;
    private IndependentSettingsManager independentSettingsManager;
    private Boolean isLabyModCache;
    private PrimaryMappingTaskSet primaryMappingTaskSet;
    private OnlineManager onlineManager;
    private ValueManager valueManager;
    private FontSelector fontSelector;
    private Mapper mapper;
    private NotificationManager notificationManager;
    private ItemStackSemanticResolver itemStackSemanticResolver;
    private gg.vape.config.ClientSettings clientSettings;
    private NativePresenceUpdater nativePresenceUpdater;
    private Boolean isFabricPresentCache;
    private ModuleProfileMetadataCodec moduleProfileMetadataCodec;
    private Object directoryCleanupCallback;
    private SyncThread syncThread;
    //used for time bomb
    private static Date buildDate;
    private static int[] supportedVersionIds;

    public boolean isOnlineConnected() {
        return OnlineConnectionManager.INSTANCE.getGlobalSettingsController().isFirstRun();
    }

    public boolean isMappingsRemapped() {
        return this.mappingsRemapped;
    }

    public void loadConfigData(JsonObject configRoot, boolean useNewOtherDataKey) {
        JsonObject profilesData;
        JsonObject profilesElement;
        JsonArray friendsArray = ConfigJsonUtils.getJsonArray(configRoot, "friends");
        if (friendsArray != null) {
            this.friendManager.loadFriends(friendsArray);
        }
        if ((profilesElement = ConfigJsonUtils.getJsonObject(configRoot, "profiles")) != null) {
            profilesData = configRoot.get("profiles").getAsJsonObject();
            this.profilesManager.loadJson(profilesData);
        } else {
            Vape.debugLog("profilesData is NULL!");
            // No stored profiles: create the built-in defaults so at least one
            // profile exists to persist module settings into (avoids settings
            // being lost on every fresh/offline session).
            this.profilesManager.loadJson(new JsonObject());
        }
        JsonArray otherData = ConfigJsonUtils.getJsonArray(configRoot, useNewOtherDataKey ? "otherData" : "otherdata");
        if (otherData != null && otherData.size() > 0) {
            this.independentSettingsManager.loadIndependentSettings(otherData);
        } else {
            Vape.debugLog("otherData is NULL!");
        }
        INSTANCE.getProfilesManager().updatePublicProfileLinks();
    }

    public MacroManager getMacrosManager() {
        return this.macroManager;
    }

    public void loadMappings() {
        this.traceStep(18);
        int opaqueSeed = Vape.getOpaqueState();
        MappedClasses.p();
        mappingsLoaded = true;
        int opaqueBranch = opaqueSeed;
        if (opaqueBranch != 0) {
            this.traceStep(19);
            if (this.forgeAbsent && ForgeVersion.MC_26_1.v()) {
                NativeBridge.fs();
                MappedClasses.p();
                NativeBridge.rsc();
            }
            MappingProfileSnapshotRegistry.X();
            RuntimeNameMappingRegistry.initializeRegistry();
            this.traceStep(20);
            this.mapper = new Mapper();
            this.mapper.loadMappings();
            this.traceStep(21);
            MappingProfileSnapshotRegistry.y();
            if (this.isForgeRemapActive()) {
                NativeBridge.fs();
                Mapper.RF.clear();
                this.mappingsRemapped = true;
                this.mapper = new Mapper();
                this.mapper.loadMappings();
            } else {
                this.mappingsRemapped = false;
            }
            MappingProfileSnapshotRegistry.h();
            this.traceStep(22);
            NativeBridge.su(Minecraft.Q$src$Lgg_vape_account_MinecraftSessionWrapper_$1ftnn3u().getUsername());
            if (GuiComponent.getLegacyComponentState() == null) {
                Vape.setOpaqueState(++opaqueBranch);
            }
            return;
        }
        this.traceStep(19);
        this.mapper = new Mapper();
        this.mapper.loadMappings();
        this.traceStep(21);
        MappingProfileSnapshotRegistry.y();
        this.mappingsRemapped = false;
        MappingProfileSnapshotRegistry.h();
        this.traceStep(22);
        NativeBridge.su(Minecraft.Q$src$Lgg_vape_account_MinecraftSessionWrapper_$1ftnn3u().getUsername());
        if (GuiComponent.getLegacyComponentState() == null) {
            Vape.setOpaqueState(++opaqueBranch);
        }
    }

    public static String formatThrowable(Throwable error) {
        StringWriter stackTraceWriter = new StringWriter();
        PrintWriter stackTracePrinter = new PrintWriter(stackTraceWriter);
        error.printStackTrace(stackTracePrinter);
        return "\n异常 " + error.getClass().getCanonicalName() + " " + stackTraceWriter.toString();
    }

    public FriendManager getFriendManager() {
        return this.friendManager;
    }

    public InventoryFilterPresetRegistry getInventoryFilterPresetRegistry() {
        return this.inventoryFilterPresetRegistry;
    }

    private void logInitError(String phase, Throwable error) {
        StringBuilder message = new StringBuilder();
        message.append(RenderBatchManager.buildInitializationDiagnostics(phase, error));
        message.append(Vape.formatThrowable(error));
        Vape.logError(message.toString());
        Vape.logThrowable(error);
    }

    public static void setOpaqueState(int value) {
        opaqueState = value;
    }

    public void setPendingTickAction(boolean pending) {
        this.pendingTickAction = pending;
    }

    public Mapper getMappings() {
        return this.mapper;
    }

    public static void notifyNativeStackTrace() {
        DescUtils.traceStack();
        NativeBridge.mb(101);
    }

    private static boolean lambda$registerListeners$0(IEvent event) {
        return AttackStrengthTracker.INSTANCE.isHealthPredictionEnabled();
    }

    public SearchManager getSearch() {
        return this.searchManager;
    }

    public EnemyManager getEnemyManager() {
        return this.enemyManager;
    }

    public boolean isForgeRemapInactive() {
        return !this.isForgeRemapActive();
    }

    public NotificationSoundPlayer getNotificationSoundPlayer() {
        return this.notificationSoundPlayer;
    }

    public FontSelector getFontSelector() {
        return this.fontSelector;
    }

    public void setUnclassifiedFlag463(boolean value) {
        this.unclassifiedFlag463 = value;
    }

    public boolean isFabricMinecraftPresent() {
        if (this.isFabricPresentCache == null) {
            String fabricMinecraftClass = "net/minecraft/class_310";
            this.isFabricPresentCache = NativeBridge.gc(fabricMinecraftClass) != null;
        }
        return this.isFabricPresentCache;
    }

    public ItemHelper getItemHelper() {
        return this.itemHelper;
    }

    public AccountInfo getAccountInfo() {
        return this.accountInfo;
    }

    public boolean isForgeRemapActive() {
        if (this.isLabyModPresent()) {
            return false;
        }
        // NeoForge/Forge 1.20.1 and 1.21.1 run mojmap names, so the
        // obfuscated-name remap path does not apply to them.
        if (NativeBridge.isNeoForge1201Runtime()
                || NativeBridge.isNeoForge1211Runtime()) {
            return false;
        }
        return ForgeVersion.MC_26_1.v();
    }

    public static byte[] readResource(String resourceName) {
        String resourcePath = "resources/" + resourceName;
        return NativeBridge.gfb(resourcePath);
    }

    public boolean initAccountInfo() {
        ApiAccessTokenProvider.getAccessToken();
        try {
            ApiResponse<AccountInfoResponse> response = ApiServices.getInstance().getAccountInfo().join();
            if (response == null || !response.isSuccessful() || response.getData() == null) {
                String error = response == null ? "empty response" : response.getError();
                Vape.logError("初始化账户信息失败1: " + error);
                return false;
            }
            AccountInfo initializedAccount = AccountInfo.fromResponse(response.getData());
            if (initializedAccount == null) {
                Vape.logError("初始化账户信息失败1: 账户数据为空");
                return false;
            }
            this.accountInfo = initializedAccount;
        }
        catch (CancellationException | CompletionException error) {
            Vape.logError("初始化账户信息失败2: " + Vape.formatThrowable(error));
            return false;
        }
        return true;
    }

    public boolean isFeatureDisabled() {
        return false;
    }

    public ModManager getModManager() {
        return this.modManager;
    }

    public gg.vape.config.ClientSettings getClientSettings() {
        return this.clientSettings;
    }

    public void initializeManagers() {
        this.glUtils = new GLUtils();
        this.glUtils.initializeVertexBuffer(24, 1);
        this.itemHelper = new ItemHelper();
        this.publicProfileManager = new PublicProfileManager();
        Vape.opaquePredicate();
        this.fontSelector = new FontSelector();
        this.valueManager = new ValueManager();
        this.independentSettingsManager = new IndependentSettingsManager();
        this.friendManager = new FriendManager();
        this.onlineFriendManager = new OnlineFriendManager();
        this.enemyManager = new EnemyManager();
        this.macroManager = new MacroManager();
        this.searchManager = new SearchManager();
        this.clientSettings = new gg.vape.config.ClientSettings();
        this.publicProfileSettings = new PublicProfileSettings();
        this.getFontSelector().N((FontOption)this.publicProfileSettings.language.getValue());
        this.modManager = new ModManager();
        this.modManager.init();
        this.clientSettings.antiBot = this.modManager.getMod(AntiBot.class);
        this.moduleProfileMetadataCodec = new ModuleProfileMetadataCodec();
        this.traceStep(23);
        this.initPrimaryMappingTasks();
        this.traceStep(24);
        this.itemStackSemanticResolver = new ItemStackSemanticResolver();
        this.itemStackSemanticResolver.loadMappings();
        try {
            this.itemStackSemanticResolver.reportMissingMappings();
        }
        catch (Throwable ignored) {
            // empty catch block
        }
        PotionRegistry.d();
        this.inventoryFilterPresetRegistry = new InventoryFilterPresetRegistry();
        this.profilesManager = new ProfilesManager();
        this.modManager.initializeAllModules();
        this.notificationManager = new NotificationManager();
        this.syncThread.loadConfig();
        this.traceStep(25);
        this.modManager.finishModuleInitialization();
        if (this.publicProfileSettings.getSelectedProfile() != null) {
            this.getProfilesManager().setActiveProfile(this.publicProfileSettings.getSelectedProfile());
        } else {
            this.getProfilesManager().getActiveProfile();
        }
        if (this.profilesManager.getActiveProfile() != null) {
            if (Vape.INSTANCE.getPublicProfileSettings().autoLoadModuleStates.getEffectiveValue().booleanValue()) {
                this.profilesManager.getActiveProfile().applyEnabledModuleStates();
            }
            this.profilesManager.getActiveProfile().applyLegitEnabledModuleStates();
        }
        // 已按需求停用：不再在启动时把 AI 旋转模式的 SilentAura 强制关闭，
        // 也不再弹 "AI mode is not allowed" 警告。恢复时取消下面这行注释即可
        // （方法体 autoDisableAiSilentAuraOnce() 原样保留）。
        // this.autoDisableAiSilentAuraOnce();
        this.traceStep(26);
        this.onlineManager = new OnlineManager();
        this.traceStep(27);
        this.notificationSoundPlayer = new NotificationSoundPlayer();
        InputEventDispatcher.getInstance().registerHandlers();
        this.initClientListeners();
        this.showLoadCompleteNotification();
        this.syncThread.clearPendingSave();
        this.getFontSelector().N((FontOption)Vape.INSTANCE.getPublicProfileSettings().language.getValue());
        this.syncThread.start();
        this.traceStep(28);
        NativeBridge.dc();
        this.registerEventListeners();
        try {
            OnlineConnectionManager.INSTANCE.initialize();
        }
        catch (Exception exception) {
            Vape.logThrowable(exception);
        }
        try {
            VapeServiceLauncher.start();
        }
        catch (Throwable throwable) {
            Vape.logThrowable(throwable);
        }
    }

    public ItemStackSemanticResolver getItemStackResolver() {
        return this.itemStackSemanticResolver;
    }

    public boolean isVanillaMinecraftPresent() {
        if (this.vanillaMinecraftChecked) {
            return this.vanillaMinecraftPresentCache;
        }
        this.vanillaMinecraftChecked = true;
        if (!this.forgeAbsent) {
            return false;
        }
        String vanillaMinecraftClass = "net/minecraft/client/Minecraft";
        this.vanillaMinecraftPresentCache = NativeBridge.gvc(vanillaMinecraftClass) != null;
        return this.vanillaMinecraftPresentCache;
    }

    public static int opaquePredicate() {
        int state = Vape.getOpaqueState();
        if (state == 0) {
            return 20;
        }
        return 0;
    }

    public boolean isUnclassifiedFlag463Set() {
        return this.unclassifiedFlag463;
    }

    public ModuleProfileMetadataCodec getModuleProfileMetadataCodec() {
        return this.moduleProfileMetadataCodec;
    }

    public boolean isLabyModPresent() {
        if (this.isLabyModCache != null) {
            return this.isLabyModCache;
        }
        if (!ForgeVersion.MC_1_8_9.L()) {
            this.isLabyModCache = false;
            return false;
        }
        try {
            Class.forName("net.laby.launcher.classloading.LabyClassLoader", false, Vape.class.getClassLoader());
            this.isLabyModCache = true;
        }
        catch (Throwable ignored) {
            this.isLabyModCache = false;
        }
        return this.isLabyModCache;
    }

    public int getAccountTier() {
        if (this.accountInfo == null) {
            return 0;
        }
        return (int)this.accountInfo.getUserId();
    }

    public NotificationManager getNotificationManager() {
        return this.notificationManager;
    }

    public TutorialManager getTutorialManager() {
        return null;
    }

    public PrimaryMappingTaskSet getPrimaryMappingTaskSet() {
        return this.primaryMappingTaskSet;
    }

    public ValueManager getValueManager() {
        return this.valueManager;
    }

    public NativePresenceUpdater getNativePresenceUpdater() {
        return this.nativePresenceUpdater;
    }

    public boolean isTickActionPending() {
        return this.pendingTickAction;
    }

    public void saveAndStop() {
        Profile activeProfile;
        this.syncThread.markDirty();
        if (this.profilesManager != null && (activeProfile = this.profilesManager.getActiveProfileOrNull()) != null) {
            activeProfile.setDirty(true);
        }
    }

    public void exportFramesConfig(String filePath) {
        try {
            String output = this.modManager.buildTranslationTemplate();
            for (Frame frame : ClientSettings.getAllFrames()) {
                if (frame instanceof FloatingValueDropdownLayer || !frame.J$src$Z$1eqdghz()) continue;
                String frameLine = "frame." + frame.getName().toLowerCase().replace(" ", "_") + "=" + frame.getName();
                output = output + frameLine + "\n";
            }
            FileOutputStream fileOut = new FileOutputStream(filePath);
            fileOut.write(output.getBytes());
        }
        catch (Exception exception) {
            Vape.logThrowable(exception);
            exception.printStackTrace();
        }
    }

    public FontManager getFontManager() {
        return this.fontManager;
    }

    public OnlineManager getOnlineManager() {
        return this.onlineManager;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public static boolean isSupportedVersion() {
        for (int versionId : supportedVersionIds) {
            if (versionId != ForgeVersion.c()) continue;
            return true;
        }
        return false;
    }

    private static boolean lambda$registerListeners$1(IEvent event) {
        return EventBus.timingEnabled;
    }

    public void initClientListeners() {
        this.primaryMappingTaskSet.X();
        this.primaryMappingTaskSet.d();
        this.clientListenerBootstrap = new ClientListenerBootstrap();
        this.clientListenerBootstrap.registerListeners();
    }

    private void registerEventListeners() {
        EventBus.getInstance().registerListener(PacketDispatchGuard.b, new Predicate[0]);
        EventBus.getInstance().registerListener(new BendableInputDispatcher(), new Predicate[0]);
        EventBus.getInstance().registerListener(AttackPacketTimingTracker.INSTANCE, new Predicate[0]);
        EventBus.getInstance().registerListener(PingManager.INSTANCE, new Predicate[0]);
        EventBus.getInstance().registerListener(RotationManager.INSTANCE, new Predicate[0]);
        EventBus.getInstance().registerListener(PlayerMovementTaskManager.INSTANCE, new Predicate[0]);
        EventBus.getInstance().registerListener(OnlineFriendActivityListener.INSTANCE, new Predicate[0]);
        EventBus.getInstance().registerListener(OnlineActivityPanelOptions.INSTANCE, new Predicate[0]);
        EventBus.getInstance().registerListener(AttackStrengthTracker.INSTANCE, Vape::lambda$registerListeners$0);
        EventBus.getInstance().registerListener(new ClientSettingsEventForwarder(), new Predicate[0]);
        EventBus.getInstance().registerListener(new WorldChangeEventDispatcher(), new Predicate[0]);
        EventBus.getInstance().registerListener(new RenderEntityContextCacheListener(), new Predicate[0]);
        EventBus.getInstance().registerListener(new MacroEventListener(), new Predicate[0]);
        EventBus.getInstance().registerListener(new InventoryCleanerProfileValueRefreshListener(), new Predicate[0]);
        EventBus.getInstance().registerListener(EventTimingOverlayListener.INSTANCE, Vape::lambda$registerListeners$1);
        EventBus.getInstance().registerListener(new VapeShutdownEventListener(), new Predicate[0]);
        EventBus.getInstance().registerListener(NameTagsRenderStateTracker.INSTANCE, new Predicate[0]);
        EventBus.getInstance().registerListener(this.notificationManager, new Predicate[0]);
        EventBus.getInstance().registerListener(new AttackCooldownUtil(), new Predicate[0]);
        EventBus.getInstance().registerListener(this.modManager, new Predicate[0]);
        if (ForgeVersion.MC_1_8_9.L()) {
            EventBus.getInstance().registerListener(new VapeLifecycleEventListener(), new Predicate[0]);
        }
        if (ForgeVersion.MC_1_16_5.d()) {
            EventBus.getInstance().registerListener(new EventNameFormatRewriteService(), new Predicate[0]);
        } else {
            EventBus.getInstance().registerListener(new FriendAliasEventListener(), new Predicate[0]);
        }
        if (ForgeVersion.MC_1_21_10.d()) {
            EventBus.getInstance().registerListener(new VapeClientEventListener(), new Predicate[0]);
        }
    }

    private void showLoadCompleteNotification() {
        ClientSettings clientSettingsModule = INSTANCE.getModManager().getMod(ClientSettings.class);
        if (clientSettingsModule.guiBindIndicator.getEffectiveValue().booleanValue()) {
            this.notificationManager.showInfo(NotificationText.localize("Loaded"), NotificationText.localize("Press ") + clientSettingsModule.getBind().getBindText() + " " + NotificationText.localize("to open the GUI"), 5000L);
        }
    }

    public boolean isForgeAbsent() {
        return this.forgeAbsent;
    }

    public ProfilesManager getProfilesManager() {
        return this.profilesManager;
    }

    public void initializeRender() {
        if (this.renderInitialized) {
            return;
        }
        try {
            OpenGlDeviceInfo.collectDeviceInfo();
            if (GuiRenderPrimitives.d()) {
                try {
                    RenderBatchManager.getInstance();
                }
                catch (Throwable error) {
                    this.logInitError("RenderEngine initialization", error);
                    return;
                }
            }
            try {
                this.fontManager = new FontManager();
                this.fontManager.e();
            }
            catch (Throwable error) {
                this.logInitError("FontManager initialization", error);
                return;
            }
            try {
                ImageRenderer.preloadResources();
            }
            catch (Throwable error) {
                this.logInitError("DrawTexture pre-cache", error);
                return;
            }
            try {
                GuiRenderPrimitives.B();
            }
            catch (Throwable error) {
                this.logInitError("VapeRender initialization", error);
                return;
            }
            this.renderInitialized = true;
        }
        catch (Throwable error) {
            Vape.logThrowable(error);
        }
    }

    public void traceStep(int step) {
        NativeBridge.trs(step);
    }

    public static void logThrowable(Throwable error) {
        Vape.debugLog(Vape.formatThrowable(error));
    }

    /**
     * 启动注入完成后执行一次：若 SilentAura 配置为 AI 旋转模式，
     * 强制关闭 SilentAura（仅启动时关闭一次，不修改配置本身）。
     *
     * <p><b>当前已按需求停用</b>：调用点与方法实现均已注释，因此启动时不会再
     * 强制关闭 AI 模式的 SilentAura，也不会再弹 "AI mode is not allowed;
     * SilentAura disabled on startup" 警告。恢复时同时取消下面整段与
     * {@code traceStep(26)} 上方调用点的注释即可（上方 import
     * {@code SilentAura} / {@code NotificationType} 为恢复而保留）。</p>
     */
    /*
    private void autoDisableAiSilentAuraOnce() {
        try {
            SilentAura silentAura = this.modManager.getMod(SilentAura.class);
            if (silentAura != null && silentAura.isEnabled() && silentAura.isAiMode()) {
                silentAura.setEnabled(false);
                if (this.notificationManager != null) {
                    this.notificationManager.show("SilentAura",
                            "AI mode is not allowed; SilentAura disabled on startup",
                            NotificationType.WARNING, 2500L);
                }
            }
        }
        catch (Throwable throwable) {
            Vape.logThrowable(throwable);
        }
    }
    */

    public LicenseManager getLicenseManager() {
        return this.licenseManager;
    }

    public GLUtils getGlUtils() {
        return this.glUtils;
    }

    public static void logTimed(String message) {
        NativeBridge.printLog(String.format("[%02d] %s", System.currentTimeMillis(), message + " " + loadTimer.getLastMS()));
        loadTimer.reset();
    }

    static {
        Vape.setOpaqueState(66);
        VERSION = "4.21";
        supportedVersionIds = new int[]{13, 15, 23, 35, 36, 37, 47, 50, 51, 52,
                54, 55, 56, 60, 61, 100, 110};
        buildDate = new Date(1710640988922L);
        renderReady = false;
        mappingsLoaded = false;
        loadTimer = new TimerUtil();
    }

    /*
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    private void initPrimaryMappingTasks() {
        int opaqueSeed = Vape.opaquePredicate();
        this.primaryMappingTaskSet = new PrimaryMappingTaskSet();
        if (NativeBridge.isBadlion189Runtime()) {
            FutureTask<Void> initializationTask = new FutureTask<Void>(() -> {
                ClientSettings.initializeFrames();
                return null;
            });
            Minecraft.v(initializationTask);
            try {
                initializationTask.get(30L, TimeUnit.SECONDS);
            }
            catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                initializationTask.cancel(false);
                throw new CompletionException(interrupted);
            }
            catch (ExecutionException execution) {
                Throwable cause = execution.getCause();
                throw new CompletionException(
                        cause == null ? execution : cause);
            }
            catch (TimeoutException timeout) {
                initializationTask.cancel(false);
                throw new CompletionException(timeout);
            }
            return;
        }
        this.primaryMappingTaskSet.L();
        this.primaryMappingTaskSet.d();
        int opaqueBranch = opaqueSeed;
        EventRenderWorldPassExecutorDrain.EXECUTOR.execute(ClientSettings::initializeFrames);
        try {
            while (!ClientSettings.framesInitialized) {
                try {
                    Thread.sleep(10L);
                    if (opaqueBranch != 0) return;
                }
                catch (InterruptedException interrupted) {
                    Vape.logThrowable(interrupted);
                    if (opaqueBranch == 0) continue;
                    break;
                }
            }
        }
        catch (CompletionException completionException) {
            try {
                throw Vape.rethrow(completionException);
            }
            catch (CompletionException retriedCompletionException) {
                throw Vape.rethrow(retriedCompletionException);
            }
        }
        this.primaryMappingTaskSet.C();
    }

    public OnlineFriendManager getOnlineFriendManager() {
        return this.onlineFriendManager;
    }

    public IndependentSettingsManager getSettingsManager() {
        return this.independentSettingsManager;
    }

    public static void logError(String message) {
        NativeBridge.sce(message);
    }

    public static void debugLog(String message) {
        String normalizedMessage = message == null ? "<null>" : message;
        try {
            NativeBridge.sce("DEBUG " + normalizedMessage);
        }
        catch (Throwable nativeLoggingFailure) {
            System.err.println("[Vape421] " + normalizedMessage);
        }
    }

    private static <T extends Throwable> T rethrow(T error) {
        return error;
    }

    public SyncThread getSyncThread() {
        return this.syncThread;
    }

    public PublicProfileManager getPublicProfileManager() {
        return this.publicProfileManager;
    }

    public PublicProfileSettings getPublicProfileSettings() {
        return this.publicProfileSettings;
    }

    public static int getOpaqueState() {
        return opaqueState;
    }

    public Vape() {
        int opaqueBranch = Vape.opaquePredicate();
        if (opaqueBranch != 0) {
            this.nativePresenceUpdater = new NativePresenceUpdater();
            this.syncThread = new SyncThread(this);
            this.vanillaMinecraftChecked = false;
            this.vanillaMinecraftPresentCache = false;
            this.isFabricPresentCache = null;
            this.renderInitialized = false;
            this.isLabyModCache = null;
            this.traceStep(17);
            INSTANCE = this;
            this.forgeAbsent = NativeBridge.isForgeAbsent();
            this.directoryCleanupCallback = new ClientDirectoryCleanupCallback();
            GuiComponent.setLegacyComponentState(new GuiComponent[4]);
            return;
        }
        this.nativePresenceUpdater = new NativePresenceUpdater();
        this.syncThread = new SyncThread(this);
        this.vanillaMinecraftChecked = false;
        this.vanillaMinecraftPresentCache = false;
        this.isFabricPresentCache = null;
        this.renderInitialized = false;
        this.isLabyModCache = null;
        this.traceStep(17);
        INSTANCE = this;
        this.forgeAbsent = NativeBridge.isForgeAbsent();
        this.directoryCleanupCallback = new ClientDirectoryCleanupCallback();
    }

    public synchronized String getDecodedAllData(boolean refresh) {
        block6: {
            block5: {
                try {
                    if (!refresh || this.cachedAllData != null) break block5;
                }
                catch (CompletionException completionException) {
                    throw Vape.rethrow(completionException);
                }
                String encoded = NativeBridge.gp("all");
                byte[] decoded = Base64Util.decodeBase64(encoded);
                this.cachedAllData = new String(decoded);
                break block6;
            }
            try {
                if (this.cachedAllData == null) {
                    return "";
                }
            }
            catch (CompletionException completionException) {
                throw Vape.rethrow(completionException);
            }
        }
        return this.cachedAllData;
    }

    public Mapper getMappingsCompat() {
        return this.getMappings();
    }

    public Mapper getMappingsMapperCompat() {
        return this.getMappings();
    }
}
