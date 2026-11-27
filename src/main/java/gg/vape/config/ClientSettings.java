package gg.vape.config;

import gg.vape.Vape;
import gg.vape.event.impl.EventTickBase;
import gg.vape.input.BindSet;
import gg.vape.input.GlfwToVirtualKeyCodeMap;
import gg.vape.input.KeyBindingInputState;
import gg.vape.input.KeyboardCodeUtil;
import gg.vape.input.KeyboardInput;
import gg.vape.input.MouseInput;
import gg.vape.mapping.MappedClasses;
import gg.vape.module.Mod;
import gg.vape.module.blatant.AntiBot;
import gg.vape.module.render.Freecam;
import gg.vape.module.render.entity.RenderEntityContext;
import gg.vape.notification.NotificationText;
import gg.vape.notification.NotificationType;
import gg.vape.unmap.ModeOption;
import gg.vape.unmap.ModeSelection;
import gg.vape.utils.ItemStackScoreUtil;
import gg.vape.utils.KeyBoardUtil;
import gg.vape.utils.MutableColor;
import gg.vape.utils.RotationUtil;
import gg.vape.value.BindValue;
import gg.vape.value.BooleanValue;
import gg.vape.value.ColorValue;
import gg.vape.value.ModeValue;
import gg.vape.wrapper.impl.AttributeModifier;
import gg.vape.wrapper.impl.Enchantment;
import gg.vape.wrapper.impl.EnchantmentHelper;
import gg.vape.wrapper.impl.Entity;
import gg.vape.wrapper.impl.EntityLivingBase;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.EnumCreatureAttribute;
import gg.vape.wrapper.impl.ForgeVersion;
import gg.vape.wrapper.impl.ItemAttributeModifiers;
import gg.vape.wrapper.impl.ItemStack;
import gg.vape.wrapper.impl.KeyBinding;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.PotionRegistry;
import java.awt.Color;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.Nullable;

public class ClientSettings {
    public BooleanValue estimateFallDamage;
    public BooleanValue sanityCheck;
    public BooleanValue showNbtTags;
    private static int runtimeState;
    public final BooleanValue useHitboxes;
    private static final long ENTITY_ID_RANDOM_BOUND;
    public BooleanValue lobbyCheck;
    public BindValue addFriendBind;
    public final BooleanValue thirdPersonAimView;
    private static Set<Integer> reservedEntityIds;
    @Nullable
    public AntiBot antiBot;
    public BooleanValue healthPrediction;
    private static final Random ENTITY_ID_RANDOM;
    public final BooleanValue aimIndicator;
    public static boolean pendingSanityReset;
    public static String FORMAT_CODE;
    public static boolean IS_LEGACY_1_7;
    public ModeValue guiScale;
    public BooleanValue estimateFoodHealing;
    static double[] GUI_SCALE_FACTORS;
    public static final ModeOption SLOW_MOVEMENT_CORRECTION;
    public final BooleanValue useReach;
    public static final ModeOption NO_MOVEMENT_CORRECTION;
    public final ModeValue movementCorrection;
    public ColorValue guiColor = ColorValue.create(this, "Gui Color", new Color(126, 105, 218));
    public final BooleanValue disableFreecamOnInject;
    public static final ModeOption PROPER_MOVEMENT_CORRECTION;
    private static boolean injectFreecamCheckCompleted;

    public static boolean isReservedEntity(Entity entity) {
        return ClientSettings.isReservedEntityId(entity.S());
    }

    public static void setPhysicalKeyState(KeyBinding keyBinding, boolean pressed) {
        int keyCode = ClientSettings.getPlatformKeyCode(keyBinding);
        if (keyCode > 0) {
            if (pressed) {
                KeyBoardUtil.l(keyCode);
            } else {
                KeyBoardUtil.K(keyCode);
            }
        }
    }

    public static boolean isReservedEntityId(int entityId) {
        return reservedEntityIds.contains(entityId);
    }

    public static boolean isInputDown(int inputCode) {
        if (inputCode < 0) {
            return MouseInput.isButtonDown(100 + inputCode);
        }
        return KeyboardInput.isKeyDown(inputCode);
    }

    public static boolean isKeyBindingDown(KeyBinding keyBinding) {
        int keyCode = ClientSettings.getPlatformKeyCode(keyBinding);
        boolean usesModernInputCodes = ForgeVersion.MC_1_16_5.d();
        int mouseButtonThreshold = usesModernInputCodes ? 4 : 0;
        if (keyCode > mouseButtonThreshold) {
            return KeyBoardUtil.m(keyCode);
        }
        if (!usesModernInputCodes) {
            keyCode += 100;
        }
        return KeyBindingInputState.isMouseButtonDown(keyCode);
    }

    public static double getToolDamageScore(ItemStack itemStack) {
        double damage = 0.0;
        ItemAttributeModifiers attributeModifiers = itemStack.o();
        if (attributeModifiers.size() > 0) {
            int modifierIndex = ForgeVersion.MC_1_12_2.L() ? 1 : 0;
            AttributeModifier attributeModifier = new AttributeModifier(attributeModifiers.values().toArray()[modifierIndex]);
            damage = attributeModifier.getAmount();
        }
        return damage += (double)EnchantmentHelper.C(itemStack, EnumCreatureAttribute.undefined());
    }

    public static void releaseReservedEntityId(int entityId) {
        reservedEntityIds.remove(entityId);
    }

    public static double getHiddenItemScore(ItemStack itemStack) {
        return ItemStackScoreUtil.O(itemStack);
    }

    public static boolean isAttackButtonDown() {
        int configuredCode = Minecraft.gameSettings().F().getKeyCode();
        int mouseButton = ForgeVersion.MC_1_16_5.d() ? configuredCode : 100 + configuredCode;
        if (mouseButton == 0) {
            return KeyBindingInputState.isLeftButtonDown();
        }
        return KeyBindingInputState.isRightButtonDown();
    }

    public static void applyAttackEffects(EntityLivingBase target) {
        EntityPlayerSP player = Minecraft.thePlayer();
        int sprintThreshold = 0;
        int settingsState = PublicProfileSettings.getDefaultRuntimeState();
        if (!player.i(PotionRegistry.E)) {
            sprintThreshold = player.i(PotionRegistry.u) ? 6 + (1 + player.b(PotionRegistry.u).L()) * 2 : 6;
        }
        if (!player.Y$src$Z$154rldp() || player.i() >= sprintThreshold / 2 || player.i() < 0) {
            player.p(-1);
            player.H(true);
        }
        boolean shouldApplyKnockback = player.M$src$F$ff28gb() > 0.0f && !player.b$src$Z$fqlxe4() && !player.S$src$Z$151gttj() && !player.h$src$Z$ftwoya() && !player.i(PotionRegistry.K) && player.S$src$Lgg_vape_wrapper_impl_Entity_$dgzs12().isNull();
        float enchantmentDamage = EnchantmentHelper.C(player.B$src$Lgg_vape_wrapper_impl_ItemStack_$impdvt(), target.k$src$Lgg_vape_wrapper_impl_EnumCreatureAttribute_$uojvxj());
        if (shouldApplyKnockback) {
            player.e(target);
        }
        if (enchantmentDamage > 0.0f) {
            player.C(target);
        }
    }

    public boolean isTeammate(@Nullable EntityPlayerSP player, Entity entity) {
        if (this.antiBot == null || !this.antiBot.isEnabled()) {
            return false;
        }
        return this.antiBot.isTeammate(player, entity);
    }

    public MutableColor resolveEntityColor(RenderEntityContext context) {
        String entityName = context.getName();
        if (Vape.INSTANCE.getFriendManager().isFriend(entityName) && Vape.INSTANCE.getFriendManager().recolorVisuals.getEffectiveValue().booleanValue()) {
            return Vape.INSTANCE.getFriendManager().friendColor.getMutableColor();
        }
        if (Vape.INSTANCE.getEnemyManager().isEnemy(entityName) && Vape.INSTANCE.getEnemyManager().useColor.getEffectiveValue().booleanValue()) {
            return Vape.INSTANCE.getEnemyManager().enemyColor.getMutableColor();
        }
        return this.resolveTeamColor(context);
    }

    public boolean isValidTarget(Entity entity, boolean rejectRotationBlocked) {
        EntityPlayerSP player = Minecraft.thePlayer();
        if (entity.isNull()) {
            return false;
        }
        if (entity.isInstance(MappedClasses.zS)) {
            return false;
        }
        if (entity.equals(player)) {
            return false;
        }
        if (!entity.isInstance(MappedClasses.zm)) {
            return false;
        }
        if (ForgeVersion.MC_1_7_10.Y() && entity.isInstance(MappedClasses.FT)) {
            return false;
        }
        EntityLivingBase livingEntity = new EntityLivingBase(entity.getObject());
        if (livingEntity.w$src$F$15l9epb() <= 0.0f) {
            return false;
        }
        if (rejectRotationBlocked && RotationUtil.k(livingEntity)) {
            return false;
        }
        if (Vape.INSTANCE.getFriendManager().isFriend(livingEntity)) {
            return false;
        }
        if (this.isTeammate(player, entity)) {
            return false;
        }
        return !this.isBot(entity);
    }

    public static int getRuntimeState() {
        return runtimeState;
    }

    static {
        ClientSettings.setRuntimeState(30);
        ENTITY_ID_RANDOM_BOUND = -1790989181904648544L;
        IS_LEGACY_1_7 = ForgeVersion.MC_1_7_10.Y();
        pendingSanityReset = false;
        FORMAT_CODE = new String(new char[]{'\u00a7'});
        NO_MOVEMENT_CORRECTION = new ModeOption("None");
        SLOW_MOVEMENT_CORRECTION = new ModeOption("Slow");
        PROPER_MOVEMENT_CORRECTION = new ModeOption("Proper");
        reservedEntityIds = ConcurrentHashMap.newKeySet();
        ENTITY_ID_RANDOM = new Random();
        GUI_SCALE_FACTORS = new double[]{0.5, 0.8, 1.0, 1.2, 1.5};
    }

    public static boolean isPhysicalKeyDown(KeyBinding keyBinding) {
        int keyCode = ClientSettings.getPlatformKeyCode(keyBinding);
        if (keyCode > 0) {
            return KeyboardInput.isKeyDown(keyCode);
        }
        return MouseInput.isButtonDown(100 + keyCode);
    }

    private double getAutomaticGuiScale() {
        int displayWidth = Minecraft.h();
        if (displayWidth >= 2000) {
            return 1.5;
        }
        if (displayWidth >= 1000) {
            return 1.2;
        }
        return 1.0;
    }

    public static int getPlatformKeyCode(KeyBinding keyBinding) {
        int keyCode = keyBinding.getKeyCode();
        if (keyCode > 0) {
            if (ForgeVersion.MC_1_16_5.v()) {
                keyCode = KeyboardCodeUtil.convertLegacyKeyCode(keyCode);
            } else {
                int virtualKey = GlfwToVirtualKeyCodeMap.toVirtualKey(keyCode);
                if (virtualKey != 0) {
                    keyCode = virtualKey;
                }
            }
        }
        return keyCode;
    }

    public static int getDefaultRuntimeState() {
        int currentState = ClientSettings.getRuntimeState();
        return 0;
    }

    public MutableColor resolveTeamColor(RenderEntityContext context, boolean includeSelf) {
        return this.resolveTeamColor(context, includeSelf, false);
    }

    public boolean isBot(Entity entity) {
        if (ForgeVersion.MC_1_7_10.L()) {
            return false;
        }
        if (this.antiBot != null) {
            return this.antiBot.isBot(entity);
        }
        return false;
    }

    public double getGuiScaleFactor() {
        int selectedIndex = this.guiScale.getSelectedIndex();
        if (selectedIndex == 0) {
            return this.getAutomaticGuiScale();
        }
        return GUI_SCALE_FACTORS[selectedIndex - 1];
    }

    public static void setRuntimeState(int runtimeState) {
        ClientSettings.runtimeState = runtimeState;
    }


    public static double getWeaponDamageScore(ItemStack itemStack) {
        double damage = ItemStackScoreUtil.a$src$F$2aw1mh(itemStack);
        damage += (double)EnchantmentHelper.C(itemStack, EnumCreatureAttribute.undefined());
        return damage += (double)((float)EnchantmentHelper.q(Enchantment.fireAspect().getId(), itemStack) * 0.01f);
    }

    public void onModuleChanged(Mod module) {
    }

    public static boolean isUseItemButtonDown() {
        int configuredCode = Minecraft.gameSettings().b$src$Lgg_vape_wrapper_impl_KeyBinding_$1yi3362().getKeyCode();
        int mouseButton = ForgeVersion.MC_1_16_5.d() ? configuredCode : 100 + configuredCode;
        if (mouseButton == 0) {
            return KeyBindingInputState.isLeftButtonDown();
        }
        return KeyBindingInputState.isRightButtonDown();
    }

    public MutableColor resolveTeamColor(RenderEntityContext context) {
        return this.resolveTeamColor(context, false);
    }

    public ClientSettings() {
        this.addFriendBind = BindValue.createEmpty(this, "Add friend bind");
        this.showNbtTags = BooleanValue.create(this, "Show NBT Tags", false, "Shows NBT tags set by the server.\nUseful for servers with custom items.");
        this.lobbyCheck = BooleanValue.create(this, "Lobby Check", false, "Temporarily disables certain features in server lobbies.");
        this.sanityCheck = BooleanValue.create(this, "Sanity Check", false, "Disables all modules when you connect/disconnect from a server.");
        this.healthPrediction = BooleanValue.create(this, "Health prediction", false, "Estimates player health on pvp servers\nBy default attacks and health pots will be estimated\nNOTE: This feature may not always be accurate!");
        this.estimateFoodHealing = BooleanValue.create(this, "Estimate Food", true, "Automatically estimates food + healing from food.");
        this.estimateFallDamage = BooleanValue.create(this, "Estimate Fall", true, "Automatically estimates damage from falling.");
        this.movementCorrection = ModeValue.create((Object)this, "Movement", "Corrects your movement to prevent irregular speeds whilst silent aiming, which is normally impossible\nNone - Does not correct your movement\nSlow - Will slow down your movement to prevent irregular speeds\nProper - Will attempt to steer you towards your cursor location with proper movements", (ModeSelection)PROPER_MOVEMENT_CORRECTION, NO_MOVEMENT_CORRECTION, SLOW_MOVEMENT_CORRECTION, PROPER_MOVEMENT_CORRECTION);
        this.thirdPersonAimView = BooleanValue.create(this, "3rd person aim view", false, "In 3rd person sets your 3D model angles where you are aiming silently");
        this.aimIndicator = BooleanValue.create(this, "Aim indicator", false, "Shows a line where you are aiming silently");
        this.useReach = BooleanValue.create(this, "Use Reach", false, "Uses Reach module to increase reach for Silent Aim modules");
        this.useHitboxes = BooleanValue.create(this, "Use Hitboxes", false, "Uses Hitboxes module to increase hitboxes for Silent Aim modules");
        this.disableFreecamOnInject = BooleanValue.create(this, "Disable Freecam on inject", true, "Turns Freecam off if it is still enabled when the client is injected");
        ModeOption modeOption = new ModeOption("Auto");
        this.guiScale = ModeValue.create((Object)this, "GUI Scale", "Scale of GUI", (ModeSelection)modeOption, modeOption, new ModeOption("Tiny"), new ModeOption("Small"), new ModeOption("Normal"), new ModeOption("Large"), new ModeOption("Huge"));
        this.guiColor.setColorTransformEnabled(true);
        this.healthPrediction.addDependentValues(this.estimateFoodHealing, this.estimateFallDamage);
        ((BindSet)this.addFriendBind.getValue()).addChangeListener(new ClientSettingsBindChangeListener(this));
    }

    /**
     * 注入时的一次性检查（由 {@code Vape.loadConfigData} 在配置/配置档案应用完成后调用）：
     * 若此时 Freecam 仍处于开启状态（例如上次会话残留在配置里），就自动关闭并弹出通知。
     * 受客户端设置 "Disable Freecam on inject" 控制，且每次注入只生效一次。
     */
    public void checkFreecamStateOnInject() {
        if (injectFreecamCheckCompleted) {
            return;
        }
        injectFreecamCheckCompleted = true;
        if (!this.disableFreecamOnInject.getEffectiveValue().booleanValue()) {
            return;
        }
        Freecam freecam = Vape.INSTANCE.getModManager().getMod(Freecam.class);
        if (freecam == null || !freecam.isEnabled()) {
            return;
        }
        // 配置加载可能发生在同步线程，模块状态变更与通知统一放到客户端线程执行
        EventTickBase.POST_TICK_EXECUTOR.execute(() -> {
            if (!freecam.isEnabled()) {
                return;
            }
            freecam.setEnabled(false, false);
            // Freecam 的关闭默认是异步的（置 disableRequested，由控制器 tick 完成）；
            // 注入早期可能还没有世界/tick，这里就地完成关闭。
            freecam.completeDisable();
            Vape.INSTANCE.getNotificationManager().show(NotificationText.localize("Freecam"), NotificationText.localize("Freecam was still enabled after injection and has been turned off"), NotificationType.WARNING, 5000L);
        });
    }

    public static int reserveEntityId() {
        int entityId;
        while (reservedEntityIds.contains(entityId = -ENTITY_ID_RANDOM.ints(1, (int)ENTITY_ID_RANDOM_BOUND).findFirst().getAsInt()) || Minecraft.theWorld().V(entityId).isNotNull()) {
        }
        reservedEntityIds.add(entityId);
        return entityId;
    }

    public MutableColor resolveTeamColor(RenderEntityContext context, boolean includeSelf, boolean useFallback) {
        if (this.antiBot == null) {
            return null;
        }
        return this.antiBot.resolveEntityTeamColor(context, includeSelf, useFallback);
    }

    public boolean isTeammate(Entity entity) {
        return this.isTeammate(null, entity);
    }

    public boolean isLobbyCheckActive() {
        return this.lobbyCheck.getEffectiveValue() != false && !Minecraft.thePlayer().C$src$Lgg_vape_wrapper_impl_ModelPlayer_$19uhx86().c();
    }
}
