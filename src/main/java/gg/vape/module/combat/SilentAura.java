package gg.vape.module.combat;

import gg.vape.Vape;
import gg.vape.config.ClientSettings;
import gg.vape.event.EventHandler;
import gg.vape.event.EventPriority;
import gg.vape.event.impl.EventMotion;
import gg.vape.event.impl.EventPrePlayerTick;
import gg.vape.event.impl.EventPreTick;
import gg.vape.event.impl.EventRender3D;
import gg.vape.event.impl.SyntheticAttackRequestEvent;
import gg.vape.input.AttackKeyController;
import gg.vape.input.InputEventDispatcher;
import gg.vape.input.KeyBindingHelper;
import gg.vape.mapping.MappedClasses;
import gg.vape.module.Category;
import gg.vape.module.Mod;
import gg.vape.module.combat.silentaura.SilentAuraAimJitter;
import gg.vape.module.combat.silentaura.SilentAuraClicker;
import gg.vape.module.combat.silentaura.SilentAuraEntityIdComparator;
import gg.vape.module.combat.silentaura.SilentAuraRotationController;
import gg.vape.module.control.SharedModuleControlClaims;
import gg.vape.module.render.Freecam;
import gg.vape.rotation.AdaptiveRotationController;
import gg.vape.rotation.MouseRotationController;
import gg.vape.rotation.RotationAngles;
import gg.vape.rotation.RotationControlClaim;
import gg.vape.rotation.RotationManager;
import gg.vape.unmap.ItemLimitData;
import gg.vape.unmap.ModeOption;
import gg.vape.unmap.ModeSelection;
import gg.vape.utils.EntityAngleComparator;
import gg.vape.utils.EntityArmorValueComparator;
import gg.vape.utils.EntityDistanceComparator;
import gg.vape.utils.EntityEquipmentValueComparator;
import gg.vape.utils.EntityHealthComparator;
import gg.vape.utils.MathUtil;
import gg.vape.utils.RayTraceUtil;
import gg.vape.utils.RotationUtil;
import gg.vape.utils.RotationVectorMath;
import gg.vape.utils.TimerUtil;
import gg.vape.utils.Vec3d;
import gg.vape.utils.render.GuiRenderPrimitives;
import gg.vape.utils.render.RenderUtil;
import gg.vape.value.BooleanValue;
import gg.vape.value.ColorValue;
import gg.vape.value.EntityTargetFilterValue;
import gg.vape.value.LimitValue;
import gg.vape.value.ModeValue;
import gg.vape.value.NumberValue;
import gg.vape.value.RandomClickDelayValue;
import gg.vape.value.RandomValue;
import gg.vape.value.Value;
import gg.vape.wrapper.impl.Entity;
import gg.vape.wrapper.impl.EntityLivingBase;
import gg.vape.wrapper.impl.EntityOtherPlayerMP;
import gg.vape.wrapper.impl.AxisAlignedBB;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.ForgeVersion;
import gg.vape.wrapper.impl.GuiScreen;
import gg.vape.wrapper.impl.ItemStack;
import gg.vape.wrapper.impl.KeyBinding;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.PlayerControllerMP;
import gg.vape.wrapper.impl.RayTraceResult;
import gg.vape.wrapper.impl.TitledScreen;
import gg.vape.wrapper.impl.Vec3;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;

public class SilentAura
extends Mod {
    private final ModeOption distanceMode;
    private final SilentAuraAimJitter xJitter;
    private long nextBreakBlockTime = 0L;
    private final SilentAuraAimJitter pitchJitter;
    public final ModeOption armorMode;
    public final BooleanValue showTarget;
    private boolean perfectSwingAttackPending = false;
    private final RandomClickDelayValue attackRate;
    private float yawIntegralScale = 1.0f;
    public final ModeOption threatMode;
    public final NumberValue maxAngle;
    private AdaptiveRotationController rotationController;
    public final EntityTargetFilterValue targetFilter = EntityTargetFilterValue.createForModule(this);
    public final ModeOption closestMode;
    private final BooleanValue switchTargets;
    private final BooleanValue limitToItems;
    public final ModeOption yawMode;
    private final RotationControlClaim rotationClaim;
    private EntityLivingBase target = null;
    private static SilentAuraClicker clicker;
    private final RandomValue breakBlocksDelay;
    private boolean readyToAttack;
    public final ModeOption ringMode;
    private final BooleanValue requireMouseDown;
    private final LimitValue allowedItems;
    private float yawIntegral = 0.0f;
    private float pitchIntegral = 0.0f;
    private boolean legacyAimLocked;
    private final BooleanValue perfectSwing;
    private final BooleanValue shieldCheck;
    public final ModeOption centerMode;
    private float pitchProportionalScale = 1.0f;
    private final ColorValue attackColor;
    private final NumberValue aimSpeed = NumberValue.createWithDescription(this, "Aim speed", "#.#", "", 1.0, 7.0, 10.0, "How fast aiming will be done silently");
    public final ModeOption healthMode;
    private boolean onTarget = false;
    private final BooleanValue breakBlocksWhitelist;
    public final BooleanValue disableOnDeath;
    private final SilentAuraAimJitter zJitter;
    private boolean breakingBlocks;
    private boolean toggledOff = false;
    public final ModeOption boxMode;
    public final NumberValue extraSwingDistance = NumberValue.createWithDescription(this, "Extra swing distance", "#.#", "", 0.0, 1.0, 3.0, "Extra distance past attack range at which aura will begin to engage, before attacking");
    private final BooleanValue breakBlocks;
    private static Freecam freecam;
    private final LimitValue blockBreakItems;
    private final ColorValue targetColor;
    private final Random random;
    private final TimerUtil breakBlocksTimer;
    public ModeValue renderType;
    private float yawProportionalScale = 1.0f;
    private float pitchIntegralScale = 1.0f;
    private static final long MODULE_ID;
    public ModeValue targetArea;
    public ModeValue targetMode;
    private final BooleanValue randomAim;
    private final NumberValue randomSize;
    private final NumberValue randomAimSpeed;
    private final TimerUtil randomAimRetargetTimer;
    private double randomAimCurrentX = 0.0;
    private double randomAimCurrentY = 0.0;
    private double randomAimCurrentZ = 0.0;
    private double randomAimTargetX = 0.0;
    private double randomAimTargetY = 0.0;
    private double randomAimTargetZ = 0.0;
    private boolean deathHandled = false;

    public EntityLivingBase getTarget() {
        return this.target;
    }

    public RotationControlClaim getRotationClaim() {
        return this.rotationClaim;
    }

    public NumberValue getAimSpeed() {
        return this.aimSpeed;
    }

    public RandomClickDelayValue getAttackRate() {
        return this.attackRate;
    }

    public BooleanValue getLimitToItems() {
        return this.limitToItems;
    }

    public LimitValue getAllowedItems() {
        return this.allowedItems;
    }

    public BooleanValue getRequireMouseDown() {
        return this.requireMouseDown;
    }

    @EventHandler
    public void onRender3D(EventRender3D eventRender3D) {
        if (this.showTarget.getEffectiveValue().booleanValue() && this.target != null && Minecraft.currentScreen().isNull()) {
            float ringRadius = this.target.isInstance(MappedClasses.Yl) || this.target.isInstance(MappedClasses.lG)
                    ? 0.7f : this.target.f$src$F$fst3ac();
            if (this.ringMode.isSelected()) {
                GuiRenderPrimitives.R(this.target.c(), this.target.A(), this.target.Z(), 50.0f, ringRadius, this.target.Y(), this.isLookingAtTarget() ? this.attackColor.getMutableColor() : this.targetColor.getMutableColor());
            } else {
                RenderUtil.k(this.target, 1.0, null, this.isLookingAtTarget() ? this.attackColor.getMutableColor() : this.targetColor.getMutableColor(), eventRender3D.getTicks());
            }
        }
    }

    private boolean passesItemFilter(EntityLivingBase target) {
        if (this.limitToItems.getEffectiveValue().booleanValue()) {
            ItemStack itemStack = Minecraft.thePlayer().getHeldItemHand();
            if (!this.allowedItems.isValid(itemStack, false)) {
                return false;
            }
            return this.targetFilter.isValidTarget(target);
        }
        return this.targetFilter.isValidTarget(target);
    }

    @Override
    public String getDetailedSuffix() {
        if (ForgeVersion.MC_1_12_2.d() && this.perfectSwing.getEffectiveValue().booleanValue()) {
            float attackStrength = Minecraft.thePlayer().getCooledAttackStrength(0.0f);
            if (attackStrength == 1.0f) {
                return "\u00a76Ready";
            }
            return String.format("%.1f", Float.valueOf(attackStrength));
        }
        return this.attackRate.getDisplayValue() + "cps";
    }

    public boolean isValidTarget(EntityLivingBase candidate) {
        if (candidate.isNull()) {
            return false;
        }
        if (candidate.equals(Minecraft.thePlayer())) {
            return false;
        }
        if (candidate.w$src$F$15l9epb() <= 0.0f || candidate.M$src$Z$ff28xj()) {
            return false;
        }
        if (!this.isInRange(candidate)) {
            return false;
        }
        if (RotationUtil.a(Minecraft.thePlayer(), candidate) > ((Double)this.maxAngle.getValue()).intValue() / 2) {
            return false;
        }
        if (Vape.INSTANCE.getFriendManager().isFriend(candidate)) {
            return false;
        }
        if (candidate.equals(Minecraft.thePlayer().S$src$Lgg_vape_wrapper_impl_Entity_$dgzs12())) {
            return false;
        }
        if (this.shieldCheck.getEffectiveValue().booleanValue()
                && candidate.isInstance(MappedClasses.lG)
                && RotationUtil.n(new EntityOtherPlayerMP(candidate.getObject()))) {
            boolean enforceShieldCheck = true;
            ShieldBreaker shieldBreaker = Vape.INSTANCE.getModManager().getMod(ShieldBreaker.class);
            if (shieldBreaker != null && shieldBreaker.isEnabled() && shieldBreaker.hasAxeInHotbar()) {
                enforceShieldCheck = false;
            }
            HitSwap hitSwap = Vape.INSTANCE.getModManager().getMod(HitSwap.class);
            if (hitSwap != null && hitSwap.isEnabled() && hitSwap.hasAlternateAxe()) {
                enforceShieldCheck = false;
            }
            AutoMace autoMace = Vape.INSTANCE.getModManager().getMod(AutoMace.class);
            if (autoMace != null && autoMace.isEnabled() && autoMace.canHandleMaceAttack()) {
                enforceShieldCheck = false;
            }
            if (enforceShieldCheck) {
                return false;
            }
        }
        return this.passesItemFilter(candidate);
    }

    private boolean isInRange(EntityLivingBase target) {
        double[] aimCoordinates = this.computeAimCoords(target);
        double distance = Minecraft.thePlayer().i(aimCoordinates[0], aimCoordinates[1], aimCoordinates[2]);
        return distance <= this.getAttackRange();
    }

    private void randomizeGainFactors() {
        this.pitchProportionalScale = 0.85f + this.random.nextFloat() * 0.3f;
        this.pitchIntegralScale = 0.85f + this.random.nextFloat() * 0.3f;
        this.yawProportionalScale = 0.8f + this.random.nextFloat() * 0.4f;
        this.yawIntegralScale = 0.85f + this.random.nextFloat() * 0.3f;
    }

    public boolean isAttackCooldownReady() {
        if (ForgeVersion.MC_1_12_2.d() && this.perfectSwing.getEffectiveValue().booleanValue()) {
            if (RotationUtil.u(Minecraft.thePlayer())) {
                AutoMace autoMace = Vape.INSTANCE.getModManager().getMod(AutoMace.class);
                if (autoMace != null && autoMace.isEnabled() && autoMace.hasReadyMace()) {
                    return true;
                }
                ItemStack heldItem = Minecraft.thePlayer().getHeldItemHand();
                if (heldItem.isNotNull() && heldItem.getItem().isNotNull()
                        && heldItem.getItem().isInstance(MappedClasses.zx)) {
                    return true;
                }
            }
            float attackStrength = Minecraft.thePlayer().getCooledAttackStrength(0.0f);
            return attackStrength == 1.0f;
        }
        return this.attackRate.hasClickDelayElapsed();
    }

    private boolean handleBreakBlocks() {
        EntityPlayerSP player = Minecraft.thePlayer();
        if (player.isNull()) {
            return false;
        }
        if (this.breakBlocks.getEffectiveValue().booleanValue() && this.breakBlocksTimer.hasTimeElapsed(this.nextBreakBlockTime)) {
            if (this.breakBlocksWhitelist.getEffectiveValue().booleanValue() && !this.blockBreakItems.matches(player.getHeldItemHand())) {
                return false;
            }
            if (Minecraft.currentScreen().isInstance(MappedClasses.Ft)) {
                return false;
            }
            RayTraceResult rayTraceResult = RayTraceUtil.o();
            if (rayTraceResult.isNotNull() && rayTraceResult.isBlockHit() && ClientSettings.isAttackButtonDown()) {
                KeyBinding keyBinding = Minecraft.gameSettings().F();
                KeyBindingHelper.updateKeyBinding(keyBinding, true, false);
                return true;
            }
            this.nextBreakBlockTime = (long)this.breakBlocksDelay.getRandomValue();
            this.breakBlocksTimer.reset();
        }
        return false;
    }

    public SilentAura() {
        super("SilentAura", (int)MODULE_ID, Category.COMBAT, "Simulates feel of Killaura\nAttacks and aims safely using built in AutoClicker to click, and Silent Aim system to aim");
        this.requireMouseDown = BooleanValue.create(this, "Require mouse down", false);
        this.disableOnDeath = BooleanValue.create(this, "Disable on death", false);
        this.showTarget = BooleanValue.create(this, "Show target", false);
        this.switchTargets = BooleanValue.create(this, "Switch", false, "Attacks other targets while current target is in hit cooldown");
        this.limitToItems = BooleanValue.create(this, "Limit to items", false, "Aura functions only while holding selected items");
        this.breakBlocks = BooleanValue.create(this, "Break blocks", false, "Prevents from aiming while attempting to break blocks");
        this.breakBlocksDelay = RandomValue.createWithDescription(this, "Break blocks delay", "#", "", 0.0, 0.0, 10.0, 2000.0, 1.0, "Delay in milliseconds before breaking blocks");
        this.breakBlocksWhitelist = BooleanValue.create(this, "Break blocks whitelist", false);
        this.blockBreakItems = LimitValue.create(this, "SilentBlockBreakingItems", "Block breaking items", LimitValue.ALLOW_LIST_COLOR, Arrays.asList(new ItemLimitData("pickaxes"), new ItemLimitData("shovels")));
        this.attackRate = RandomClickDelayValue.create(this, "Attacks per Second", "#.#", "", 1.0, 6.0, 13.0, 20.0);
        this.maxAngle = NumberValue.create(this, "Max angle", "#", "", 1.0, 120.0, 360.0, 5.0, "Angle at which targets will be acquired and aimed at\n(From your cursor)");
        this.targetColor = ColorValue.createWithAlpha(this, "Target color", new Color(255, 200, 112), 50);
        this.attackColor = ColorValue.create(this, "Attack color", new Color(255, 0, 0, 100));
        this.distanceMode = new ModeOption("Distance");
        this.yawMode = new ModeOption("Yaw");
        this.armorMode = new ModeOption("Armor");
        this.threatMode = new ModeOption("Threat");
        this.healthMode = new ModeOption("Health");
        this.targetMode = ModeValue.create((Object)this, "Target mode", "How Aura should prioritize targets\nArmor/Threat will default to Distance for non player targets", (ModeSelection)this.distanceMode, this.distanceMode, this.yawMode, this.armorMode, this.threatMode, this.healthMode);
        this.allowedItems = LimitValue.create(this, "silentaura-alloweditems", "Allowed Items", LimitValue.ALLOW_LIST_COLOR, Collections.emptyList());
        this.centerMode = new ModeOption("Center");
        this.closestMode = new ModeOption("Closest");
        this.targetArea = ModeValue.create((Object)this, "Target area", "Where Aura will aim towards\nCenter: Center of entity\nClosest: Closest position on entity hitbox", (ModeSelection)this.centerMode, this.centerMode, this.closestMode);
        this.ringMode = new ModeOption("Ring");
        this.boxMode = new ModeOption("Box");
        this.renderType = ModeValue.create((Object)this, "Render type", this.ringMode, this.ringMode, this.boxMode);
        this.perfectSwing = BooleanValue.create(this, "Perfect swing", false, "Only attacks when there is no attack cooldown\nAdditionally, only swings when hovering(trigger)");
        this.shieldCheck = BooleanValue.create(this, "Shield check", false,
                "Won't attack players blocking with shield\nUsing ShieldBreaker, HitSwap, or AutoMace will override this behavior");
        this.breakBlocksTimer = new TimerUtil();
        this.rotationClaim = SharedModuleControlClaims.rotation;
        this.random = new Random();
        this.pitchJitter = new SilentAuraAimJitter(-0.3, 0.25);
        this.xJitter = new SilentAuraAimJitter(-0.15, 0.15);
        this.zJitter = new SilentAuraAimJitter(-0.15, 0.15);
        this.randomAim = BooleanValue.create(this, "Random aim", false, "Randomizes the aim point within a configurable range to bypass anti-cheat");
        this.randomSize = NumberValue.create(this, "Random size", "#", "%", 0.0, 30.0, 100.0, 5.0, "Size of the random aim range as a percentage of your bounding box");
        this.randomAimSpeed = NumberValue.create(this, "Random aim speed", "#.#", "", 0.1, 2.0, 5.0, 0.1, "Speed at which the random aim point moves");
        this.randomAimRetargetTimer = new TimerUtil();
        this.randomAim.addDependentValues(this.randomSize, this.randomAimSpeed);
        this.perfectSwing.whenEqualTo(false).applyTo(this.attackRate);
        // 1.7.10 attacks via attackEntity(target) directly and never rewrites
        // the outgoing packet view, so the silent aim speed is unused there.
        if (ForgeVersion.MC_1_7_10.L()) {
            this.aimSpeed.setHidden(true);
        }
        this.addValue(this.targetFilter, this.aimSpeed, this.attackRate, this.extraSwingDistance, this.maxAngle, this.targetMode, this.targetArea, this.shieldCheck);
        this.showTarget.addDependentValues(this.targetColor, this.attackColor, this.renderType);
        this.breakBlocks.addDependentValues(this.breakBlocksDelay, this.breakBlocksWhitelist);
        this.breakBlocksWhitelist.addDependentValues(this.blockBreakItems);
        this.U(this.perfectSwing, ForgeVersion.MC_1_8_9.N());
        this.U(this.switchTargets, ForgeVersion.MC_1_8_9.H());
        this.addValue(new Value[]{this.disableOnDeath, this.breakBlocks, this.breakBlocksDelay, this.breakBlocksWhitelist, this.blockBreakItems, this.requireMouseDown, this.showTarget, this.targetColor, this.attackColor, this.renderType, this.limitToItems.addDependentValues(this.allowedItems), this.allowedItems, this.randomAim, this.randomSize, this.randomAimSpeed});
        this.limitToItems.setCompactListValue(this.allowedItems);
        this.rotationClaim.setPriority(this, 5);
        this.attackRate.setMaximumFractionDigits(0);
    }

    static {
        MODULE_ID = 267655872188715318L;
    }

    public boolean canAttack() {
        if (Minecraft.theWorld().isNull()) {
            return false;
        }
        if (!this.isAttackCooldownReady()) {
            return false;
        }
        if (!this.readyToAttack) {
            return false;
        }
        if (Minecraft.currentScreen().isNotNull()) {
            return false;
        }
        return !this.breakingBlocks;
    }

    @EventHandler
    public void onTick(EventPrePlayerTick eventPrePlayerTick) {
    }

    /** 1.7.10: attack the tracked target directly instead of letting MC attack
     *  whatever the crosshair ray-trace hits (the crosshair never points at the
     *  target because the legacy rotation path only rewrites outgoing packets).
     *  The local camera is never touched; swingItem() plays the swing locally
     *  and broadcasts the swing packet so other players see it. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onSyntheticAttack(SyntheticAttackRequestEvent event) {
        if (event.getSource() == this) {
            return;
        }
        if (!ForgeVersion.MC_1_7_10.L()) {
            return;
        }
        EntityLivingBase target = this.target;
        if (!this.isEnabled() || target == null || target.isNull()
                || !this.readyToAttack || !this.isInRange(target)) {
            return;
        }
        EntityPlayerSP player = Minecraft.thePlayer();
        if (player.isNull()) {
            return;
        }
        player.swingItem();
        PlayerControllerMP controller = Minecraft.playerController();
        if (controller != null && controller.isNotNull()) {
            controller.attackEntity(player, target);
        }
        event.setCancelled(true);
    }


    public boolean canClickAttack() {
        if (ForgeVersion.MC_1_12_2.d() && this.perfectSwing.getEffectiveValue().booleanValue()) {
            return false;
        }
        return this.canAttack();
    }

    private void updateRotationColors() {
        if (this.target != null && this.isControllingRotation()) {
            this.onTarget = this.isLookingAtTarget();
            this.rotationController.setSecondaryColor(this.attackColor.getMutableColor());
            this.rotationController.setPrimaryColor(this.targetColor.getMutableColor());
            this.rotationController.setUseSecondaryColor(this.onTarget);
        }
    }

    @EventHandler(priority=EventPriority.LOWEST)
    public void onTick(EventPreTick eventPreTick) {
        this.breakingBlocks = this.handleBreakBlocks();
        if (this.perfectSwingAttackPending) {
            this.perfectSwingAttackPending = false;
            AttackKeyController.releaseAttackKey();
        }
        if (Minecraft.thePlayer().isNull()) {
            this.resetTargeting();
            return;
        }
        if (this.toggledOff || !this.isEnabled() || this.breakingBlocks) {
            this.resetTargeting();
            return;
        }
        if (clicker == null) {
            clicker = Vape.INSTANCE.getModManager().getMod(SilentAuraClicker.class);
        }
        if (!clicker.isEnabled()) {
            clicker.setEnabled(true);
        }
        this.updateRotationColors();
        if (this.disableOnDeath.getEffectiveValue().booleanValue()) {
            if (Minecraft.thePlayer().M$src$Z$ff28xj() || Minecraft.thePlayer().w$src$F$15l9epb() <= 0.0f) {
                this.toggle();
                return;
            }
            if (ForgeVersion.MC_1_16_5.d()) {
                GuiScreen guiScreen = Minecraft.currentScreen();
                if (guiScreen.isNotNull()) {
                    if (!this.deathHandled && guiScreen.isInstance(MappedClasses.D2)) {
                        this.deathHandled = true;
                        this.toggle();
                        return;
                    }
                    this.deathHandled = false;
                }
            } else {
                TitledScreen titledScreen = Minecraft.k();
                if (titledScreen.isNotNull()) {
                    String screenTitle = titledScreen.getDisplayedTitle();
                    if (!this.deathHandled && screenTitle != null && (screenTitle.toLowerCase().contains("died") || screenTitle.toLowerCase().contains("dead"))) {
                        this.deathHandled = true;
                        this.toggle();
                        return;
                    }
                    if (screenTitle == null || screenTitle.equals("")) {
                        this.deathHandled = false;
                    }
                }
            }
        }
        if (!InputEventDispatcher.getInstance().getFocusState().isFocused() || this.requireMouseDown.getEffectiveValue().booleanValue() && !ClientSettings.isAttackButtonDown()) {
            this.resetTargeting();
            return;
        }
        this.updateTarget();
        this.updateAim();
        if (ForgeVersion.MC_1_12_2.d() && this.perfectSwing.getEffectiveValue().booleanValue() && this.canAttack()) {
            AttackKeyController.releaseAttackKey();
            this.perfectSwingAttackPending = AttackKeyController.requestSyntheticAttack(this);
        }
    }

    private boolean isControllingRotation() {
        MouseRotationController mouseRotationController = RotationManager.INSTANCE.getActiveController();
        return mouseRotationController != null && mouseRotationController.equals(this.rotationController);
    }

    private void updateTarget() {
        ArrayList<EntityLivingBase> candidates = new ArrayList<EntityLivingBase>();
        if (this.shouldSkip()) {
            return;
        }
        ArrayList<?> loadedEntities = new ArrayList<Object>(Minecraft.theWorld().z());
        for (Object entityObject : loadedEntities) {
            Entity entity = new Entity(entityObject);
            EntityLivingBase candidate;
            if (ClientSettings.IS_LEGACY_1_7 && entity.isInstance(MappedClasses.FT)
                    || !entity.isInstance(MappedClasses.zm)
                    || !this.isValidTarget(candidate = new EntityLivingBase(entityObject))) {
                continue;
            }
            candidates.add(candidate);
        }
        if (this.targetMode.getValue() == this.yawMode) {
            candidates.sort(new EntityAngleComparator());
        } else if (this.targetMode.getValue() == this.distanceMode) {
            candidates.sort(new EntityDistanceComparator());
        } else if (this.targetMode.getValue() == this.threatMode) {
            candidates.sort(new EntityArmorValueComparator());
        } else if (this.targetMode.getValue() == this.armorMode) {
            candidates.sort(new EntityEquipmentValueComparator());
        } else if (this.targetMode.getValue() == this.healthMode) {
            candidates.sort(new EntityHealthComparator());
        }
        if (this.switchTargets.getEffectiveValue().booleanValue()) {
            candidates.sort(new SilentAuraEntityIdComparator());
        }
        if (!candidates.isEmpty()) {
            EntityLivingBase selectedTarget = candidates.get(0);
            boolean targetChanged = this.target != null && !this.target.equals(selectedTarget);
            if (this.target == null || targetChanged) {
                AttackKeyController.releaseAttackKey();
                this.randomizeGainFactors();
            }
            this.target = selectedTarget;
            if (!this.rotationClaim.isOwnedBy(this)) {
                this.rotationClaim.acquire(this, true);
            }
            this.readyToAttack = false;
            if (this.rotationController != null) {
                if (ForgeVersion.MC_1_12_2.d() && this.perfectSwing.getEffectiveValue().booleanValue()) {
                    RayTraceResult rayTrace = RotationManager.INSTANCE.getExtendedReachRayTrace();
                    if (rayTrace.isNotNull() && rayTrace.getEntity().isNotNull() && rayTrace.getEntity().equals(this.target)) {
                        this.readyToAttack = true;
                    }
                } else {
                    double angularDistance = RotationUtil.L(this.target);
                    if (angularDistance < 3.0 && this.isInRange(this.target)) {
                        this.readyToAttack = true;
                    }
                }
            }
        } else {
            this.resetTargeting();
        }
    }

    private double[] computeAimCoords(EntityLivingBase target) {
        if (this.targetArea.getValue() == this.closestMode) {
            Vec3d closestPoint = RotationUtil.T(Minecraft.thePlayer(), target.R$src$Lgg_vape_wrapper_impl_AxisAlignedBB_$r19dfl(), 0.0, 0.0, 0.0);
            double motionX = target.z() - target.f();
            double motionZ = target.h() - target.R();
            double currentX = closestPoint.getX();
            double currentY = target.N();
            double currentZ = closestPoint.getZ();
            double previousX = currentX - motionX;
            double previousY = target.H();
            double previousZ = currentZ - motionZ;
            return new double[]{currentX, currentY, currentZ, previousX, previousY, previousZ};
        }
        return new double[]{target.z(), target.N(), target.h(), target.f(), target.H(), target.R()};
    }

    private void updateAim() {
        // 1.7.10: RotationManager's adaptive path depends on ThreadBound
        // pre/post tick events injected into Item.onItemRightClick /
        // getMovingObjectPositionFromPlayer, which do not pair reliably for
        // silent left-click attacks (the camera ends up stuck on the managed
        // yaw). Rotate the real view toward the target directly instead; the
        // clicker attacks through the legacy packet path with this rotation.
        if (ForgeVersion.MC_1_7_10.L()) {
            this.updateAimLegacy();
            return;
        }
        if (Minecraft.theWorld().isNull()) {
            this.resetTargeting();
            return;
        }
        boolean hasValidTarget = this.target != null && this.isValidTarget(this.target);
        boolean ownsRotation = this.rotationClaim.isOwnedBy(this);
        if (hasValidTarget && ownsRotation) {
            this.resetJitter();
            EntityPlayerSP player = Minecraft.thePlayer();
            double[] aimCoordinates = this.computeAimCoords(this.target);
            double targetX = aimCoordinates[0];
            double targetY = aimCoordinates[1];
            double targetZ = aimCoordinates[2];
            double previousTargetX = aimCoordinates[3];
            double previousTargetZ = aimCoordinates[5];
            double distanceToTarget = player.i(targetX, targetY, targetZ);
            double targetMotionX = targetX - previousTargetX;
            double targetMotionZ = targetZ - previousTargetZ;
            double horizontalTargetMotion = Math.sqrt(targetMotionX * targetMotionX + targetMotionZ * targetMotionZ);
            double jitteredTargetX = targetX + this.xJitter.getCurrentOffset() * (1.0 + horizontalTargetMotion);
            double jitteredTargetZ = targetZ + this.zJitter.getCurrentOffset() * (1.0 + horizontalTargetMotion);
            double targetHeight = this.target.Y();
            double playerEyeY = player.N() + 1.62;
            double jitteredTargetY = playerEyeY < targetY
                    ? targetY + this.pitchJitter.getCurrentOffset() * 0.5
                    : Math.min(playerEyeY, targetY + targetHeight) - 0.275 + this.pitchJitter.getCurrentOffset();
            if (this.randomAim.getEffectiveValue() && this.target != null) {
                AxisAlignedBB playerBB = player.R$src$Lgg_vape_wrapper_impl_AxisAlignedBB_$r19dfl();
                double playerWidth = playerBB.getMaxX() - playerBB.getMinX();
                double playerHeight = playerBB.getMaxY() - playerBB.getMinY();
                double playerDepth = playerBB.getMaxZ() - playerBB.getMinZ();
                double sizePercent = ((Double)this.randomSize.getValue()).doubleValue() / 100.0;
                double maxOffsetX = playerWidth * sizePercent / 2.0;
                double maxOffsetY = playerHeight * sizePercent / 2.0;
                double maxOffsetZ = playerDepth * sizePercent / 2.0;
                if (this.randomAimRetargetTimer.hasTimeElapsed(100 + this.random.nextInt(200))) {
                    this.randomAimRetargetTimer.reset();
                    this.randomAimTargetX = (this.random.nextDouble() * 2.0 - 1.0) * maxOffsetX;
                    this.randomAimTargetY = (this.random.nextDouble() * 2.0 - 1.0) * maxOffsetY;
                    this.randomAimTargetZ = (this.random.nextDouble() * 2.0 - 1.0) * maxOffsetZ;
                }
                double lerpSpeed = ((Double)this.randomAimSpeed.getValue()).doubleValue() * 0.08;
                this.randomAimCurrentX += (this.randomAimTargetX - this.randomAimCurrentX) * lerpSpeed;
                this.randomAimCurrentY += (this.randomAimTargetY - this.randomAimCurrentY) * lerpSpeed;
                this.randomAimCurrentZ += (this.randomAimTargetZ - this.randomAimCurrentZ) * lerpSpeed;
                jitteredTargetX += this.randomAimCurrentX;
                jitteredTargetY += this.randomAimCurrentY;
                jitteredTargetZ += this.randomAimCurrentZ;
            }
            if (this.rotationController == null) {
                this.rotationController = new SilentAuraRotationController(this);
                this.rotationController.setRelativeMode(false);
                this.rotationController.setRetainAfterCompletion(true);
                this.rotationController.setClampStepToRemaining(true);
                this.rotationController.setTolerance(0.0f);
                this.rotationController.setScaleAxesProportionally(false);
                this.rotationController.setLinearAcceleration(false);
                RotationManager.INSTANCE.setController(this.rotationController);
            } else {
                this.rotationController.setRelativeMode(false);
                this.rotationController.setScaleAxesProportionally(false);
                this.rotationController.setLinearAcceleration(false);
                RotationAngles desiredRotation = this.rotationController.calculateRotation(Vec3.create(jitteredTargetX, jitteredTargetY, jitteredTargetZ));
                float targetPitch = (float)RotationUtil.h(player, targetX, jitteredTargetY, targetZ);
                float managedYaw = RotationManager.INSTANCE.getManagedYaw();
                float managedPitch = RotationManager.INSTANCE.getManagedPitch();
                float yawError = MathUtil.wrapAngleTo180(desiredRotation.getYaw() - managedYaw);
                float pitchError = MathUtil.wrapAngleTo180(targetPitch - managedPitch);
                float previousPitchStep = managedPitch - RotationManager.INSTANCE.getPreviousManagedPitch();
                float previousYawStep = managedYaw - RotationManager.INSTANCE.getPreviousManagedYaw();
                float integrationStep = 0.05f;
                boolean yawContinuingInSameDirection = Math.signum(yawError) == Math.signum(previousYawStep);
                double playerHorizontalSpeed = Math.sqrt(player.t() * player.t() + player.T() * player.T());
                float pitchProportionalGain = 0.45f * this.pitchProportionalScale;
                float pitchIntegralGain = 0.91f * this.pitchIntegralScale;
                float yawProportionalGain = (this.onTarget ? 0.05f : 0.1f) * this.yawProportionalScale;
                float yawIntegralGain = 0.33f * this.yawIntegralScale;
                double playerVerticalMotion = player.q();
                if (Math.abs(playerVerticalMotion) > 0.1) {
                    pitchError *= (float)(1.0 + Math.random() * 0.32);
                }
                if (yawContinuingInSameDirection && Math.abs(yawError) < 20.0f) {
                    yawProportionalGain *= 2.5f;
                    previousYawStep *= (float)(1.0 + Math.min(horizontalTargetMotion + playerHorizontalSpeed, 0.25));
                }
                if (distanceToTarget < 0.8) {
                    double distanceScale = distanceToTarget / 0.8;
                    pitchError *= (float)(distanceScale * distanceScale);
                    yawError *= (float)distanceScale;
                }
                float pitchControlError = pitchError - previousPitchStep
                        + previousYawStep * integrationStep * (float)(Math.random() >= 0.5 ? -1 : 1);
                float yawControlError = yawError - previousYawStep;
                this.pitchIntegral += pitchControlError * integrationStep;
                this.yawIntegral += yawControlError * integrationStep;
                float pitchAdjustment = pitchProportionalGain * pitchControlError + pitchIntegralGain * this.pitchIntegral;
                float yawAdjustment = yawProportionalGain * yawControlError + yawIntegralGain * this.yawIntegral;
                if (Math.abs(yawError) > 120.0f) {
                    this.yawIntegral = 0.0f;
                    yawAdjustment = 0.0f;
                }
                if (Minecraft.currentScreen().isNotNull()) {
                    this.yawIntegral = 0.0f;
                    this.pitchIntegral = 0.0f;
                }
                this.rotationController.setTargetRotation(managedYaw + yawError + yawAdjustment / 3.0f, managedPitch + pitchAdjustment);
            }
            if (RotationManager.INSTANCE.getActiveController() == null || !this.isControllingRotation() && RotationManager.INSTANCE.hasAdaptiveController()) {
                RotationManager.INSTANCE.setController(this.rotationController);
            }
        } else {
            this.resetTargeting();
        }
    }

    /** 1.7.10 fallback: track the target and mark it ready to attack. The
     *  outgoing packet rotation is NOT modified: rewriting the server-side
     *  view makes the server send S08 PositionLook syncs that yank the local
     *  camera toward the target (and cause entity-tick NPEs). attackEntity()
     *  targets the tracked entity directly, so no view rewrite is needed. */
    private void updateAimLegacy() {
        if (Minecraft.theWorld().isNull()) {
            this.resetTargeting();
            return;
        }
        if (this.target == null || !this.isValidTarget(this.target)
                || !this.rotationClaim.isOwnedBy(this)) {
            this.legacyAimLocked = false;
            this.readyToAttack = this.target != null && this.isInRange(this.target);
            return;
        }
        EntityPlayerSP player = Minecraft.thePlayer();
        if (player.isNull()) {
            return;
        }
        this.legacyAimLocked = true;
        this.readyToAttack = this.isInRange(this.target);
    }

    @Override
    public void setEnabled(boolean enabledState, boolean bypassVisibilityCheck) {
        this.pitchIntegral = 0.0f;
        this.yawIntegral = 0.0f;
        if (!enabledState && this.isControllingRotation()) {
            this.toggledOff = !this.toggledOff;
        } else {
            this.toggledOff = false;
            super.setEnabled(enabledState, bypassVisibilityCheck);
        }
    }

    private boolean isLookingAtTarget() {
        RayTraceResult rayTraceResult;
        return this.target != null
                && this.isControllingRotation()
                && this.isInRange(this.target)
                && (rayTraceResult = RotationManager.INSTANCE.getExtendedReachRayTrace()) != null
                && rayTraceResult.isNotNull()
                && this.target.equals(rayTraceResult.getEntity());
    }

    public void resetTargeting() {
        this.pitchIntegral = 0.0f;
        this.yawIntegral = 0.0f;
        this.target = null;
        this.readyToAttack = false;
        if (this.rotationController != null && this.isControllingRotation()) {
            this.rotationController.setScaleAxesProportionally(true);
            this.rotationController.setLinearAcceleration(true);
            RotationManager.INSTANCE.releaseController(this.rotationController);
        }
        if (RotationManager.INSTANCE.getActiveController() == null || RotationManager.INSTANCE.getActiveController() != this.rotationController || this.rotationController != null && this.rotationController.isRelativeMode() && this.rotationController.isComplete()) {
            this.rotationController = null;
            this.rotationClaim.release(this);
            if (this.toggledOff) {
                this.toggledOff = false;
                super.setEnabled(false, true);
            }
        }
    }

    private double getAttackRange() {
        return 3.0 + (Double)this.extraSwingDistance.getValue();
    }

    private void resetJitter() {
        this.pitchJitter.update();
        this.xJitter.update();
        this.zJitter.update();
    }

    private boolean shouldSkip() {
        if (freecam == null) {
            freecam = Vape.INSTANCE.getModManager().getMod(Freecam.class);
        }
        return this.toggledOff || freecam != null && freecam.isEnabled() || this.breakingBlocks || this.rotationClaim.isBlockedFor(this) && !this.rotationClaim.acquire(this, true);
    }

    @Override
    public void onDisable() {
        if (this.rotationController != null) {
            this.rotationController = null;
        }
        if (this.perfectSwingAttackPending) {
            AttackKeyController.releaseAttackKey();
            this.perfectSwingAttackPending = false;
        }
        this.rotationClaim.release(this);
    }
}
