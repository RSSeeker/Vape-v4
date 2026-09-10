package gg.vape.module.combat;

import gg.vape.Vape;
import gg.vape.event.EventHandler;
import gg.vape.event.EventPriority;
import gg.vape.event.impl.EventKeyPress;
import gg.vape.event.impl.EventMouseButton;
import gg.vape.event.impl.EventPostAttack;
import gg.vape.event.impl.EventPreTick;
import gg.vape.event.impl.SyntheticAttackRequestEvent;
import gg.vape.input.AttackKeyController;
import gg.vape.mapping.MappedClasses;
import gg.vape.module.Category;
import gg.vape.module.Mod;
import gg.vape.module.blatant.blockin.BlockPathPlanner;
import gg.vape.module.blatant.blockin.BlockPlacementGraph;
import gg.vape.module.combat.automace.AutoMaceRotationController;
import gg.vape.module.control.AttackCancellationAdapter;
import gg.vape.module.control.PhysicalAttackCancellationAdapter;
import gg.vape.module.control.SharedModuleControlClaims;
import gg.vape.module.control.SyntheticAttackCancellationAdapter;
import gg.vape.module.none.ClientSettings;
import gg.vape.rotation.RotationControlClaim;
import gg.vape.rotation.RotationManager;
import gg.vape.unmap.ItemLimitData;
import gg.vape.unmap.ModeOption;
import gg.vape.utils.AttackCooldownUtil;
import gg.vape.utils.ItemStackScoreUtil;
import gg.vape.utils.RotationUtil;
import gg.vape.utils.TimerUtil;
import gg.vape.value.BooleanValue;
import gg.vape.value.EntityTargetFilterValue;
import gg.vape.value.LimitValue;
import gg.vape.value.ModeValue;
import gg.vape.value.NumberValue;
import gg.vape.value.RandomValue;
import gg.vape.wrapper.impl.AxisAlignedBB;
import gg.vape.wrapper.impl.EnchantmentHelper;
import gg.vape.wrapper.impl.Entity;
import gg.vape.wrapper.impl.EntityLivingBase;
import gg.vape.wrapper.impl.EntityOtherPlayerMP;
import gg.vape.wrapper.impl.EntityPlayer;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.EnumHand;
import gg.vape.wrapper.impl.InventoryPlayer;
import gg.vape.wrapper.impl.Item;
import gg.vape.wrapper.impl.ItemStack;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.MonsterAttributesBridge;
import gg.vape.wrapper.impl.RayTraceResult;
import gg.vape.wrapper.impl.RayTraceResult_type;
import gg.vape.wrapper.impl.WorldClient;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AutoMace extends Mod {
    private static final int MODULE_ID = -16732037;
    private static final int PREDICTION_TICKS = 20;
    private static final int MAX_AIM_IMPACT_TICK = 6;
    private static final int MIN_ELYTRA_IMPACT_TICK = 3;
    private static final int ELYTRA_EXIT_TIMEOUT_TICKS = 8;
    private static final double REACH_MARGIN = 0.9;

    private static final ModeOption SELECTION_MANUAL = new ModeOption("Manual");
    private static final ModeOption SELECTION_AUTO = new ModeOption("Auto");
    private static final ModeOption TYPE_DENSITY = new ModeOption("Density");
    private static final ModeOption TYPE_BREACH = new ModeOption("Breach");

    public final EntityTargetFilterValue targetFilter = EntityTargetFilterValue.createForModule(this);
    public final ModeValue maceSelection;
    public final ModeValue maceType;
    public final BooleanValue stunSlam;
    public final NumberValue stunSlamChance;
    public final BooleanValue aim;
    public final BooleanValue silentAim;
    public final NumberValue aimRange;
    public final BooleanValue attack;
    public final RandomValue extraDelay;
    public final BooleanValue autoUnequipElytra;
    public final BooleanValue reEquipElytra;
    public final BooleanValue smashOnly;
    public final BooleanValue limitToItems;
    public final LimitValue allowedItems;

    private final TimerUtil inputTimer = new TimerUtil();
    private final RotationControlClaim rotationClaim = SharedModuleControlClaims.rotation;
    private AutoMaceRotationController rotationController;
    private BlockPlacementGraph movementSnapshot;
    private Item cachedElytra;

    private boolean releasePending;
    private boolean dispatchingSyntheticAttack;
    private boolean weaponSwapActive;
    private boolean stunSlamFollowupPending;
    private int originalSlot = -1;
    private int maceSlot = -1;
    private int targetId = -1;
    private int originalAttackTick = -1;
    private int weaponSwapTicks;

    private int armorOriginalSlot = -1;
    private int armorTargetSlot = -1;
    private int armorSwapTicks;
    private boolean armorSwapToElytra;
    private boolean swappedItemWasElytra;
    private boolean shouldReequipElytra;
    private boolean sawDownwardMotion;
    private ElytraFlightState elytraFlightState = ElytraFlightState.IDLE;
    private int elytraFlightStateTicks;

    public AutoMace() {
        super("AutoMace", MODULE_ID, Category.COMBAT);
        this.maceSelection = ModeValue.create(this, "Mace selection",
                "Manual uses Mace type. Auto chooses the best mace enchantment for your fall distance and target armor.",
                SELECTION_MANUAL, SELECTION_MANUAL, SELECTION_AUTO);
        this.maceType = ModeValue.create(this, "Mace type",
                "Selects which mace enchantment AutoMace should use. Bind this setting to cycle it in game.",
                TYPE_DENSITY, TYPE_DENSITY, TYPE_BREACH);
        this.stunSlam = BooleanValue.create(this, "Stun slam", false,
                "When holding an axe and attacking a shielded player:\nHits with axe first (breaks shield), then swaps to mace for a follow-up slam");
        this.stunSlamChance = NumberValue.create(this, "Chance", "#", "%", 0.0, 100.0, 100.0, 1.0,
                "Chance that Stun slam will trigger");
        this.aim = BooleanValue.create(this, "Aim", false,
                "Aims at the nearest valid target while falling for a smash attack");
        this.silentAim = BooleanValue.create(this, "Silent aim", false, "Uses Silent Aim system");
        this.aimRange = NumberValue.create(this, "Aim range", "#.#", "", 2.0, 6.0, 10.0, 0.1,
                "Maximum horizontal distance to search for targets");
        this.attack = BooleanValue.create(this, "Attack", false, "Automatically attacks valid mace targets");
        this.extraDelay = RandomValue.createWithDescription(this, "Extra delay", "#", "ticks",
                -20.0, 0.0, 0.0, 20.0, 0.1,
                "Extra delay after attack cooldown(in ticks)\nNegative values will attack before cooldown is complete");
        this.autoUnequipElytra = BooleanValue.create(this, "Auto unequip Elytra", false,
                "Equips a hotbar chestplate when your predicted fall can reach a mace target");
        this.reEquipElytra = BooleanValue.create(this, "Re-equip Elytra", false,
                "Puts the Elytra back on after upward mace bounce movement is detected");
        this.smashOnly = BooleanValue.create(this, "Smash only", true, "Only swap to mace if will smash");
        this.limitToItems = BooleanValue.create(this, "Limit to items", false);
        this.allowedItems = LimitValue.create(this, "am-alloweditems", "Allowed Items",
                LimitValue.ALLOW_LIST_COLOR, Arrays.asList(new ItemLimitData("swords")));

        this.addValue(this.targetFilter, this.aim, this.silentAim, this.aimRange, this.attack, this.extraDelay,
                this.autoUnequipElytra, this.reEquipElytra, this.smashOnly, this.maceSelection, this.maceType,
                this.stunSlam, this.stunSlamChance, this.limitToItems, this.allowedItems);
        this.aim.addDependentValues(this.silentAim, this.aimRange);
        this.attack.addDependentValues(this.extraDelay);
        this.autoUnequipElytra.addDependentValues(this.reEquipElytra);
        this.maceSelection.addModeDependentValues(SELECTION_MANUAL, this.maceType);
        this.stunSlam.addDependentValues(this.stunSlamChance);
        this.limitToItems.addDependentValues(this.allowedItems);
        this.rotationClaim.setPriority(this, 5);
    }

    @Override
    public String getSimpleSuffix() {
        return this.isAutomaticSelection()
                ? this.maceSelection.getValue().toString()
                : this.maceType.getValue().toString();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onKeyPress(EventKeyPress event) {
        if (event.isKeybinding(Minecraft.gameSettings().F()) && event.isDown()) {
            this.inputTimer.reset();
            if (!event.isCanceled()) {
                this.handleAttack(new PhysicalAttackCancellationAdapter(event));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMouseButton(EventMouseButton event) {
        if (event.isKeybinding(Minecraft.gameSettings().F()) && event.isDown()) {
            this.inputTimer.reset();
            if (!event.isCanceled()) {
                this.handleAttack(new PhysicalAttackCancellationAdapter(event));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSyntheticAttack(SyntheticAttackRequestEvent event) {
        Mod source = event.getSource();
        if (source == this || source instanceof HitSwap || source instanceof ShieldBreaker) {
            return;
        }
        this.inputTimer.reset();
        if (!event.isCanceled()) {
            this.handleAttack(new SyntheticAttackCancellationAdapter(event));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPostAttack(EventPostAttack event) {
        if (!this.weaponSwapActive || !this.stunSlamFollowupPending || event.getTarget().isNull()
                || event.getTarget().S() != this.targetId) {
            return;
        }
        EntityPlayerSP player = Minecraft.thePlayer();
        if (!this.isHoldingAxe(player)) {
            this.abortWeaponSwap(player);
            return;
        }
        this.completeStunSlamFollowup(player);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onTick(EventPreTick event) {
        EntityPlayerSP player = event.getThePlayer();
        if (player.isNull()) {
            this.releaseRotation();
            return;
        }

        this.movementSnapshot = new BlockPlacementGraph(player);
        boolean elytraBusy = this.updateElytraState(player, event.getWorld());
        if (elytraBusy) {
            this.releaseRotation();
        } else {
            this.updateAim(player, event.getWorld());
        }

        boolean releasedAttackThisTick = false;
        if (this.releasePending) {
            AttackKeyController.releaseAttackKey();
            this.releasePending = false;
            releasedAttackThisTick = true;
        }

        if (this.weaponSwapActive && this.stunSlamFollowupPending) {
            if (player.l() <= this.originalAttackTick) {
                return;
            }
            if (!this.isCrosshairTarget(this.targetId)) {
                this.abortWeaponSwap(player);
                return;
            }
            this.completeStunSlamFollowup(player);
            return;
        }

        if (this.weaponSwapActive && this.weaponSwapTicks++ > 1) {
            if (this.originalSlot != -1) {
                player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6().g(this.originalSlot);
                this.originalSlot = -1;
            }
            this.clearWeaponSwapState();
        }

        if (!releasedAttackThisTick && !elytraBusy) {
            this.updateAutoAttack(player);
        }
    }

    private void handleAttack(AttackCancellationAdapter cancellation) {
        if (this.weaponSwapActive || !this.canOperate()) {
            return;
        }
        EntityPlayerSP player = Minecraft.thePlayer();
        if (player.isNull() || player.Y$src$Z$154rldp() || player.l$src$Z$1io4duf()) {
            return;
        }
        EntityLivingBase target = this.getCrosshairTarget();
        if (target == null) {
            return;
        }
        InventoryPlayer inventory = player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6();
        if (this.shouldStunSlam(target)) {
            int selectedMaceSlot = this.findBestMaceSlot(target, false);
            if (selectedMaceSlot < 0) {
                return;
            }
            if (this.isHoldingAxe(player)) {
                this.beginStunSlam(player, selectedMaceSlot, target.S());
                return;
            }
            int axeSlot = this.findAxeSlot(inventory);
            if (axeSlot >= 0) {
                cancellation.setCancelled(true);
                this.originalSlot = inventory.v();
                inventory.g(axeSlot);
                this.maceSlot = selectedMaceSlot;
                this.originalAttackTick = player.l();
                this.targetId = target.S();
                this.weaponSwapActive = true;
                this.stunSlamFollowupPending = true;
                this.weaponSwapTicks = 0;
                return;
            }
        }

        int selectedMaceSlot = this.findBestMaceSlot(target, true);
        if (selectedMaceSlot < 0) {
            return;
        }
        this.originalSlot = inventory.v();
        inventory.g(selectedMaceSlot);
        this.weaponSwapActive = true;
        this.weaponSwapTicks = 0;
    }

    private void beginStunSlam(EntityPlayerSP player, int selectedMaceSlot, int selectedTargetId) {
        InventoryPlayer inventory = player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6();
        this.originalSlot = inventory.v();
        this.maceSlot = selectedMaceSlot;
        this.originalAttackTick = player.l();
        this.targetId = selectedTargetId;
        this.weaponSwapActive = true;
        this.stunSlamFollowupPending = true;
        this.weaponSwapTicks = 0;
    }

    private void completeStunSlamFollowup(EntityPlayerSP player) {
        if (player.isNotNull() && this.isValidMaceSlot(player, this.maceSlot)) {
            player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6().g(this.maceSlot);
            AttackKeyController.releaseAttackKey();
            this.releasePending = this.requestSyntheticAttack(true);
        }
        this.stunSlamFollowupPending = false;
        this.maceSlot = -1;
        this.originalAttackTick = -1;
        this.targetId = -1;
    }

    private boolean isValidMaceSlot(EntityPlayerSP player, int slot) {
        if (slot < 0 || slot >= 9) {
            return false;
        }
        return this.isMace(player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6().c(slot));
    }

    private void updateAutoAttack(EntityPlayerSP player) {
        if (!this.attack.getEffectiveValue().booleanValue() || this.weaponSwapActive || this.releasePending
                || player.l$src$Z$1io4duf() || player.Y$src$Z$154rldp() || !this.isFalling(player)
                || !this.inputTimer.hasTimeElapsed(50L) || !this.canUseAttackInput()) {
            return;
        }

        EntityLivingBase target = this.getCrosshairTarget();
        if (target == null || !this.isAutoAttackReady(player, target,
                (float)(-this.extraDelay.getRandomValue()))) {
            return;
        }

        boolean heldMaceReady = this.isHeldMaceReady(player, target);
        boolean alternateMaceReady = this.findBestMaceSlot(target, true) >= 0;
        boolean stunSlamReady = this.stunSlam.getEffectiveValue().booleanValue()
                && this.findBestMaceSlot(target, false) >= 0
                && this.findAxeSlot(player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6()) >= 0;
        if (!heldMaceReady && !alternateMaceReady && !stunSlamReady) {
            return;
        }

        final boolean[] cancelled = new boolean[1];
        this.handleAttack(value -> cancelled[0] = value);
        if (!cancelled[0] && (heldMaceReady || alternateMaceReady || this.stunSlamFollowupPending)) {
            AttackKeyController.releaseAttackKey();
            this.releasePending = this.requestSyntheticAttack(false);
        }
    }

    private boolean isAutoAttackReady(EntityPlayerSP player, EntityLivingBase target, float cooldownOffset) {
        if (RotationUtil.u(player)
                && (this.findBestMaceSlot(target, true) >= 0 || this.isHeldMaceReady(player, target))) {
            return true;
        }
        return AttackCooldownUtil.isAttackReady(cooldownOffset);
    }

    private void updateAim(EntityPlayerSP player, WorldClient world) {
        if (!this.aim.getEffectiveValue().booleanValue() || world.isNull() || this.movementSnapshot == null
                || !this.canOperate() || player.b$src$Z$fqlxe4() || player.Y$src$Z$154rldp()
                || player.f$src$Z$fst3rk() || player.h$src$Z$ftwoya() || player.S$src$Z$151gttj()
                || player.C$src$Lgg_vape_wrapper_impl_ModelPlayer_$19uhx86().isFlying()
                || !this.hasMaceInHotbar()) {
            this.releaseRotation();
            return;
        }

        EntityLivingBase target = this.findNearestTarget(player, world);
        if (target == null) {
            this.releaseRotation();
            return;
        }

        double reach = this.getMaceReach();
        boolean fallingForSmash = RotationUtil.u(player);
        boolean directlyReachable = fallingForSmash
                && distanceToTarget(player.z(), player.N() + player.X(), player.h(), target, 0.0, 0.0, 0.0) <= reach;
        ImpactPrediction prediction = this.findImpact(this.simulateTrajectory(player, world, false, 0), target, reach);
        if (!(prediction.valid && prediction.tick <= MAX_AIM_IMPACT_TICK || directlyReachable)) {
            this.releaseRotation();
            return;
        }

        if (this.rotationClaim.isBlockedFor(this)) {
            this.releaseRotation();
            return;
        }
        boolean silent = this.silentAim.getEffectiveValue().booleanValue();
        if (!this.rotationClaim.isOwnedBy(this) && !this.rotationClaim.acquire(this, silent)) {
            return;
        }
        if (this.rotationController == null
                || RotationManager.INSTANCE.getActiveController() != this.rotationController) {
            this.rotationController = new AutoMaceRotationController(silent);
            this.rotationController.setRetainAfterCompletion(true);
            RotationManager.INSTANCE.setController(this.rotationController);
        }
        this.rotationController.setTargetEntity(target);
        this.rotationController.setRenderLineWidth(reach);
        this.rotationController.setPrediction(prediction.valid, prediction.tick,
                prediction.sourceX, prediction.sourceY, prediction.sourceZ,
                prediction.aimX, prediction.aimY, prediction.aimZ, directlyReachable);
    }

    private List<FallSample> simulateTrajectory(EntityPlayerSP player, WorldClient world,
                                                boolean startsFallFlying, int glideTicks) {
        List<FallSample> samples = new ArrayList<FallSample>();
        if (this.movementSnapshot == null) {
            return samples;
        }
        BlockPathPlanner simulation = new BlockPathPlanner(player, player, world, this.movementSnapshot);
        simulation.applySnapshot(this.movementSnapshot);
        simulation.restoreSnapshotInput();
        EntityPlayer simulatedPlayer = simulation.getSimulatedPlayer();
        float fallDistance = player.getFallDistance();
        double previousY = simulatedPlayer.N();
        boolean glideEnded = !startsFallFlying;
        for (int tick = 1; tick <= PREDICTION_TICKS; ++tick) {
            if (!glideEnded && simulatedPlayer.Y$src$Z$154rldp()) {
                if (simulatedPlayer.q() > -0.5 && fallDistance > 1.0f) {
                    fallDistance = 1.0f;
                }
                if (tick > glideTicks) {
                    simulatedPlayer.k(7, false);
                    glideEnded = true;
                }
            }
            simulation.simulateTick();
            double currentY = simulatedPlayer.N();
            double verticalDelta = currentY - previousY;
            if (verticalDelta < 0.0) {
                fallDistance += (float)(-verticalDelta);
            }
            previousY = currentY;
            FallSample sample = new FallSample(tick, simulatedPlayer.z(), currentY + simulatedPlayer.X(),
                    simulatedPlayer.h(), fallDistance, simulatedPlayer.b$src$Z$fqlxe4(),
                    simulatedPlayer.Y$src$Z$154rldp());
            samples.add(sample);
            if (sample.onGround) {
                break;
            }
        }
        return samples;
    }

    private ImpactPrediction findImpact(List<FallSample> samples, EntityLivingBase target, double reach) {
        ImpactPrediction result = new ImpactPrediction();
        AxisAlignedBB bounds = target.u$src$Lgg_vape_wrapper_impl_AxisAlignedBB_$kogbsu();
        double motionX = target.t();
        double motionY = target.q();
        double motionZ = target.T();
        boolean targetOnGround = target.b$src$Z$fqlxe4();
        for (FallSample sample : samples) {
            double horizontalTicks = Math.min(sample.tick, 5);
            double offsetX = motionX * horizontalTicks;
            double offsetZ = motionZ * horizontalTicks;
            double offsetY = targetOnGround ? 0.0 : motionY * Math.min(sample.tick, 3);
            double distance = distanceToTarget(sample.x, sample.y, sample.z, target, offsetX, offsetY, offsetZ);
            if (sample.onGround || sample.fallFlying || distance > reach * REACH_MARGIN
                    || sample.fallDistance <= 1.5f) {
                continue;
            }
            result.valid = true;
            result.tick = sample.tick;
            result.sourceX = sample.x;
            result.sourceY = sample.y;
            result.sourceZ = sample.z;
            double minY = bounds.getMinY() + offsetY;
            double maxY = bounds.getMaxY() + offsetY;
            result.aimX = (bounds.getMinX() + bounds.getMaxX()) * 0.5 + offsetX;
            result.aimY = minY + (maxY - minY) * 0.75;
            result.aimZ = (bounds.getMinZ() + bounds.getMaxZ()) * 0.5 + offsetZ;
            break;
        }
        return result;
    }

    private boolean updateElytraState(EntityPlayerSP player, WorldClient world) {
        if (!this.autoUnequipElytra.getEffectiveValue().booleanValue()) {
            this.restoreArmorSlot(player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6());
            this.resetElytraFlightState(true);
            return player.Y$src$Z$154rldp();
        }

        InventoryPlayer inventory = player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6();
        if (this.isArmorSwapActive(false)) {
            return this.updateElytraUnequipSwap(player, inventory);
        }
        if (this.isArmorSwapActive(true)) {
            this.updateElytraReequipSwap(player, inventory);
            return player.Y$src$Z$154rldp();
        }

        if (!player.Y$src$Z$154rldp()) {
            this.updateBounceReequip(player);
            this.resetElytraFlightState(false);
            return false;
        }
        if (this.elytraFlightState == ElytraFlightState.WAITING_FOR_GLIDE_END) {
            if (++this.elytraFlightStateTicks > ELYTRA_EXIT_TIMEOUT_TICKS) {
                this.elytraFlightState = ElytraFlightState.ABORTED_THIS_FLIGHT;
            }
            return true;
        }
        if (this.elytraFlightState == ElytraFlightState.ABORTED_THIS_FLIGHT || world.isNull()
                || this.movementSnapshot == null || this.weaponSwapActive || this.releasePending
                || player.l$src$Z$1io4duf() || player.b$src$Z$fqlxe4()
                || player.f$src$Z$fst3rk() || player.h$src$Z$ftwoya() || player.S$src$Z$151gttj()
                || player.C$src$Lgg_vape_wrapper_impl_ModelPlayer_$19uhx86().isFlying()
                || !this.canOperate() || !ClientSettings.INSTANCE.isInputEnabled() || !this.hasMaceInHotbar()) {
            return true;
        }

        int chestplateSlot = this.findBestChestplateSlot(inventory);
        if (chestplateSlot < 0 || SharedModuleControlClaims.rightClickUse.isClaimed()) {
            return true;
        }
        ElytraTarget target = this.findElytraTarget(player, world, this.getMaceReach());
        if (target == null) {
            return true;
        }

        this.armorOriginalSlot = inventory.v();
        this.armorTargetSlot = chestplateSlot;
        this.armorSwapToElytra = false;
        this.armorSwapTicks = 0;
        inventory.g(chestplateSlot);
        this.swappedItemWasElytra = false;
        return true;
    }

    private boolean updateElytraUnequipSwap(EntityPlayerSP player, InventoryPlayer inventory) {
        if (inventory.v() != this.armorTargetSlot) {
            this.clearArmorSwapState();
            this.elytraFlightState = this.swappedItemWasElytra
                    ? ElytraFlightState.WAITING_FOR_GLIDE_END
                    : ElytraFlightState.ABORTED_THIS_FLIGHT;
            this.elytraFlightStateTicks = 0;
            return player.Y$src$Z$154rldp();
        }
        if (!player.Y$src$Z$154rldp() && this.armorSwapTicks < 2) {
            this.restoreArmorSlot(inventory);
            this.resetElytraFlightState(false);
            return false;
        }
        int ticks = this.armorSwapTicks + 1;
        if (ticks == 2) {
            if (player.l$src$Z$1io4duf() || SharedModuleControlClaims.rightClickUse.isClaimed()) {
                return true;
            }
            this.useSelectedItem();
            this.swappedItemWasElytra = this.isElytra(inventory.c(this.armorTargetSlot));
            this.shouldReequipElytra = this.swappedItemWasElytra
                    && this.reEquipElytra.getEffectiveValue().booleanValue();
            this.sawDownwardMotion = false;
        }
        this.armorSwapTicks = ticks;
        if (this.armorSwapTicks < 4) {
            return true;
        }
        this.restoreArmorSlot(inventory);
        this.elytraFlightState = this.swappedItemWasElytra
                ? ElytraFlightState.WAITING_FOR_GLIDE_END
                : ElytraFlightState.ABORTED_THIS_FLIGHT;
        this.elytraFlightStateTicks = 0;
        if (!player.Y$src$Z$154rldp()) {
            this.resetElytraFlightState(false);
            return false;
        }
        return true;
    }

    private void updateElytraReequipSwap(EntityPlayerSP player, InventoryPlayer inventory) {
        if (inventory.v() != this.armorTargetSlot) {
            this.clearArmorSwapState();
            this.shouldReequipElytra = false;
            this.sawDownwardMotion = false;
            return;
        }
        int ticks = this.armorSwapTicks + 1;
        if (ticks == 2) {
            if (player.l$src$Z$1io4duf() || SharedModuleControlClaims.rightClickUse.isClaimed()) {
                return;
            }
            this.useSelectedItem();
        }
        this.armorSwapTicks = ticks;
        if (this.armorSwapTicks < 4) {
            return;
        }
        this.restoreArmorSlot(inventory);
        this.shouldReequipElytra = false;
        this.sawDownwardMotion = false;
    }

    private void updateBounceReequip(EntityPlayerSP player) {
        if (!this.shouldReequipElytra) {
            return;
        }
        InventoryPlayer inventory = player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6();
        if (!this.reEquipElytra.getEffectiveValue().booleanValue()) {
            this.restoreArmorSlot(inventory);
            this.shouldReequipElytra = false;
            this.sawDownwardMotion = false;
            return;
        }
        if (this.isArmorSwapActive(true)) {
            this.updateElytraReequipSwap(player, inventory);
            return;
        }
        double verticalMotion = player.N() - player.W();
        if (verticalMotion < -0.05) {
            this.sawDownwardMotion = true;
        }
        if (!this.sawDownwardMotion || verticalMotion <= 0.05 || this.weaponSwapActive
                || this.releasePending || player.l$src$Z$1io4duf()
                || SharedModuleControlClaims.rightClickUse.isClaimed()) {
            return;
        }
        int elytraSlot = this.findElytraSlot(inventory);
        if (elytraSlot < 0) {
            this.shouldReequipElytra = false;
            return;
        }
        this.armorOriginalSlot = inventory.v();
        this.armorTargetSlot = elytraSlot;
        this.armorSwapToElytra = true;
        this.armorSwapTicks = 0;
        inventory.g(elytraSlot);
    }

    private ElytraTarget findElytraTarget(EntityPlayerSP player, WorldClient world, double reach) {
        List<FallSample> immediateExit = this.simulateTrajectory(player, world, true, 0);
        List<FallSample> delayedExit = this.simulateTrajectory(player, world, true, 1);
        if (immediateExit.isEmpty() || delayedExit.isEmpty()) {
            return null;
        }
        ElytraTarget best = null;
        double horizontalRange = this.aimRange.getValue();
        for (Object entityObject : new ArrayList<Object>(world.z())) {
            Entity entity = new Entity(entityObject);
            if (!entity.isInstance(MappedClasses.zm)) {
                continue;
            }
            EntityLivingBase target = new EntityLivingBase(entityObject);
            if (!this.isValidTarget(player, target)
                    || !this.isWithinHorizontalRange(immediateExit, target, horizontalRange)
                    && !this.isWithinHorizontalRange(delayedExit, target, horizontalRange)) {
                continue;
            }
            ImpactPrediction immediateImpact = this.findImpact(immediateExit, target, reach);
            ImpactPrediction delayedImpact = this.findImpact(delayedExit, target, reach);
            if (!immediateImpact.valid || !delayedImpact.valid
                    || immediateImpact.tick < MIN_ELYTRA_IMPACT_TICK
                    || delayedImpact.tick < MIN_ELYTRA_IMPACT_TICK) {
                continue;
            }
            int impactTick = Math.max(immediateImpact.tick, delayedImpact.tick);
            if (best != null && impactTick >= best.impactTick) {
                continue;
            }
            best = new ElytraTarget(impactTick);
        }
        return best;
    }

    private boolean isWithinHorizontalRange(List<FallSample> samples, EntityLivingBase target, double range) {
        AxisAlignedBB bounds = target.u$src$Lgg_vape_wrapper_impl_AxisAlignedBB_$kogbsu();
        double motionX = target.t();
        double motionZ = target.T();
        for (FallSample sample : samples) {
            double ticks = Math.min(sample.tick, 5);
            double minX = bounds.getMinX() + motionX * ticks;
            double maxX = bounds.getMaxX() + motionX * ticks;
            double minZ = bounds.getMinZ() + motionZ * ticks;
            double maxZ = bounds.getMaxZ() + motionZ * ticks;
            double closestX = clamp(sample.x, minX, maxX);
            double closestZ = clamp(sample.z, minZ, maxZ);
            double deltaX = sample.x - closestX;
            double deltaZ = sample.z - closestZ;
            if (Math.sqrt(deltaX * deltaX + deltaZ * deltaZ) <= range) {
                return true;
            }
        }
        return false;
    }

    private void useSelectedItem() {
        SharedModuleControlClaims.rightClickUse.blockUse();
        try {
            Minecraft.F$src$V$aoypvc();
        } finally {
            SharedModuleControlClaims.rightClickUse.clearClaimed();
        }
    }

    private boolean isArmorSwapActive(boolean toElytra) {
        return this.armorTargetSlot >= 0 && this.armorSwapToElytra == toElytra;
    }

    private void restoreArmorSlot(InventoryPlayer inventory) {
        if (this.armorTargetSlot >= 0 && this.armorOriginalSlot >= 0 && inventory.v() == this.armorTargetSlot) {
            inventory.g(this.armorOriginalSlot);
        }
        this.clearArmorSwapState();
    }

    private void clearArmorSwapState() {
        this.armorSwapTicks = 0;
        this.armorSwapToElytra = false;
        this.armorTargetSlot = -1;
        this.armorOriginalSlot = -1;
    }

    private void resetElytraFlightState(boolean clearReequip) {
        this.elytraFlightState = ElytraFlightState.IDLE;
        this.elytraFlightStateTicks = 0;
        this.swappedItemWasElytra = false;
        if (clearReequip) {
            this.shouldReequipElytra = false;
            this.sawDownwardMotion = false;
            this.clearArmorSwapState();
        }
    }

    private int findBestChestplateSlot(InventoryPlayer inventory) {
        int bestSlot = -1;
        double bestScore = -1.0;
        for (int slot = 0; slot < 9; ++slot) {
            ItemStack stack = inventory.c(slot);
            if (stack.isNull() || stack.r() || stack.getItem().isNull() || this.isElytra(stack)
                    || ItemStackScoreUtil.t(stack) != 1 || ItemStackScoreUtil.P(stack) <= 0.0) {
                continue;
            }
            double score = ItemStackScoreUtil.L(stack);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = slot;
            }
        }
        return bestSlot;
    }

    private int findElytraSlot(InventoryPlayer inventory) {
        for (int slot = 0; slot < 9; ++slot) {
            if (this.isElytra(inventory.c(slot))) {
                return slot;
            }
        }
        return -1;
    }

    private boolean isElytra(ItemStack stack) {
        if (stack == null || stack.isNull() || stack.getItem().isNull()) {
            return false;
        }
        if (this.cachedElytra == null || this.cachedElytra.isNull()) {
            this.cachedElytra = Item.L("minecraft:elytra");
        }
        return this.cachedElytra != null && this.cachedElytra.isNotNull()
                && stack.getItem().equals(this.cachedElytra);
    }

    private EntityLivingBase findNearestTarget(EntityPlayerSP player, WorldClient world) {
        double range = this.aimRange.getValue();
        EntityLivingBase best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Object entityObject : new ArrayList<Object>(world.z())) {
            Entity entity = new Entity(entityObject);
            if (!entity.isInstance(MappedClasses.zm)) {
                continue;
            }
            EntityLivingBase candidate = new EntityLivingBase(entityObject);
            double deltaX = candidate.z() - player.z();
            double deltaZ = candidate.h() - player.h();
            double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
            double distance = player.getDistanceToEntity(candidate);
            if (!this.isValidTarget(player, candidate) || horizontalDistance > range || distance >= bestDistance) {
                continue;
            }
            best = candidate;
            bestDistance = distance;
        }
        return best;
    }

    private boolean isValidTarget(EntityPlayerSP player, EntityLivingBase target) {
        if (target == null || target.isNull() || target.equals(player) || target.C$src$Z$f9kazx()
                || target.w$src$F$15l9epb() <= 0.0f) {
            return false;
        }
        Entity vehicle = player.S$src$Lgg_vape_wrapper_impl_Entity_$dgzs12();
        if (vehicle.isNotNull() && target.equals(vehicle)) {
            return false;
        }
        return this.targetFilter.isValidTarget(target) && target.N() <= player.N() + 1.0;
    }

    private EntityLivingBase getCrosshairTarget() {
        RayTraceResult rayTrace = RotationManager.INSTANCE.getExtendedReachRayTrace();
        if (rayTrace == null || rayTrace.isNull()
                || !rayTrace.getTypeOfHit().equals(RayTraceResult_type.entity())) {
            return null;
        }
        Entity entity = rayTrace.getEntity();
        return this.targetFilter.isValidTarget(entity) ? new EntityLivingBase(entity.getObject()) : null;
    }

    private boolean isCrosshairTarget(int entityId) {
        RayTraceResult rayTrace = RotationManager.INSTANCE.getExtendedReachRayTrace();
        return rayTrace != null && rayTrace.isNotNull() && rayTrace.getEntity().isNotNull()
                && rayTrace.getEntity().S() == entityId;
    }

    private boolean canOperate() {
        if (Minecraft.currentScreen().isNotNull()) {
            return false;
        }
        EntityPlayerSP player = Minecraft.thePlayer();
        return player.isNotNull() && (!this.limitToItems.getEffectiveValue().booleanValue()
                || this.allowedItems.isValid(player.getHeldItemHand(), false));
    }

    private boolean canUseAttackInput() {
        return this.canOperate() && ClientSettings.INSTANCE.isInputEnabled();
    }

    private boolean isFalling(EntityPlayerSP player) {
        return !player.b$src$Z$fqlxe4() && player.N() - player.W() < 0.0;
    }

    private boolean shouldStunSlam(EntityLivingBase target) {
        double chance = this.stunSlamChance.getValue();
        if (!this.stunSlam.getEffectiveValue().booleanValue() || chance <= 0.0
                || chance < 100.0 && chance <= Math.random() * 100.0) {
            return false;
        }
        Entity entity = new Entity(target.getObject());
        if (!entity.isInstance(MappedClasses.lG)) {
            return false;
        }
        EntityOtherPlayerMP targetPlayer = new EntityOtherPlayerMP(entity.getObject());
        EnumHand shieldHand = RotationUtil.q(targetPlayer);
        return shieldHand != null && RotationUtil.f(targetPlayer, shieldHand) >= 5.0f;
    }

    private boolean isHeldMaceReady(EntityPlayerSP player, EntityLivingBase target) {
        return this.scoreMace(player.getHeldItemHand(), this.hasConfiguredMaceInHotbar(), target) >= 0.0;
    }

    private int findBestMaceSlot(EntityLivingBase target, boolean excludeSelected) {
        EntityPlayerSP player = Minecraft.thePlayer();
        InventoryPlayer inventory = player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6();
        int selectedSlot = inventory.v();
        boolean filterByEnchantment = this.hasConfiguredMaceInHotbar();
        int bestSlot = -1;
        double bestScore = -1.0;
        for (int slot = 0; slot < 9; ++slot) {
            if (excludeSelected && slot == selectedSlot) {
                continue;
            }
            double score = this.scoreMace(inventory.c(slot), filterByEnchantment, target);
            if (score < 0.0 || this.isAutomaticSelection() && score <= bestScore) {
                continue;
            }
            bestSlot = slot;
            bestScore = score;
            if (!this.isAutomaticSelection()) {
                return slot;
            }
        }
        return bestSlot;
    }

    private double scoreMace(ItemStack stack, boolean filterByEnchantment, EntityLivingBase target) {
        if (this.smashOnly.getEffectiveValue().booleanValue() && !RotationUtil.u(Minecraft.thePlayer())
                || !this.isMace(stack)) {
            return -1.0;
        }
        if (!filterByEnchantment) {
            return 0.0;
        }
        int density = EnchantmentHelper.e("density", stack);
        int breach = EnchantmentHelper.e("breach", stack);
        if (this.isAutomaticSelection()) {
            return this.scoreAutomaticMace(density, breach, target);
        }
        if (this.isDensityType() && density > 0) {
            return 0.0;
        }
        if (this.isBreachType() && breach > 0) {
            return 0.0;
        }
        return -1.0;
    }

    private double scoreAutomaticMace(int density, int breach, EntityLivingBase target) {
        EntityPlayerSP player = Minecraft.thePlayer();
        if (player.b$src$Z$fqlxe4()) {
            return breach > 0 ? 0.0 : -1.0;
        }
        double score = -1.0;
        if (density > 0) {
            score = this.estimateMaceDamage(player.getFallDistance(), target, density, 0);
        }
        if (breach > 0) {
            score = Math.max(score, this.estimateMaceDamage(player.getFallDistance(), target, 0, breach));
        }
        return score;
    }

    private double estimateMaceDamage(float fallDistance, EntityLivingBase target, int density, int breach) {
        double damage = 6.0 + this.smashBonusDamage(fallDistance) + 0.5 * density * fallDistance;
        if (target == null || target.isNull()) {
            return damage;
        }
        double armor = target.o(MonsterAttributesBridge.L());
        double toughness = target.o(MonsterAttributesBridge.m$src$Lgg_vape_wrapper_impl_Holder_$1lgjxui());
        double effectiveArmor = clamp(armor - damage / (2.0 + toughness / 4.0), armor * 0.2, 20.0);
        double reduction = clamp(effectiveArmor / 25.0 - 0.15 * breach, 0.0, 1.0);
        return damage * (1.0 - reduction);
    }

    private double smashBonusDamage(float fallDistance) {
        if (fallDistance <= 3.0f) {
            return 4.0 * fallDistance;
        }
        if (fallDistance <= 8.0f) {
            return 12.0 + 2.0 * (fallDistance - 3.0f);
        }
        return 22.0 + fallDistance - 8.0f;
    }

    private boolean hasConfiguredMaceInHotbar() {
        EntityPlayerSP player = Minecraft.thePlayer();
        if (player.isNull()) {
            return false;
        }
        InventoryPlayer inventory = player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6();
        for (int slot = 0; slot < 9; ++slot) {
            if (this.isConfiguredMace(inventory.c(slot))) {
                return true;
            }
        }
        return false;
    }

    private boolean isConfiguredMace(ItemStack stack) {
        if (!this.isMace(stack)) {
            return false;
        }
        if (this.isAutomaticSelection()) {
            return EnchantmentHelper.e("density", stack) > 0 || EnchantmentHelper.e("breach", stack) > 0;
        }
        if (this.isDensityType()) {
            return EnchantmentHelper.e("density", stack) > 0;
        }
        return this.isBreachType() && EnchantmentHelper.e("breach", stack) > 0;
    }

    private boolean hasMaceInHotbar() {
        EntityPlayerSP player = Minecraft.thePlayer();
        if (player.isNull()) {
            return false;
        }
        InventoryPlayer inventory = player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6();
        for (int slot = 0; slot < 9; ++slot) {
            if (this.isMace(inventory.c(slot))) {
                return true;
            }
        }
        return false;
    }

    private int findAxeSlot(InventoryPlayer inventory) {
        int selectedSlot = inventory.v();
        for (int slot = 0; slot < 9; ++slot) {
            ItemStack stack = inventory.c(slot);
            if (slot != selectedSlot && this.isAxe(stack)) {
                return slot;
            }
        }
        return -1;
    }

    private boolean isHoldingAxe(EntityPlayerSP player) {
        return player.isNotNull() && this.isAxe(player.getHeldItemHand());
    }

    private boolean isAxe(ItemStack stack) {
        return stack != null && stack.isNotNull() && !stack.r()
                && stack.getItem().isNotNull() && ItemStackScoreUtil.T(stack.getItem());
    }

    private boolean isMace(ItemStack stack) {
        return stack != null && stack.isNotNull() && stack.getItem().isNotNull()
                && stack.getItem().isInstance(MappedClasses.zx);
    }

    private boolean isAutomaticSelection() {
        return this.maceSelection.getValue().toString().equals(SELECTION_AUTO.toString());
    }

    private boolean isDensityType() {
        return this.maceType.getValue().toString().equals(TYPE_DENSITY.toString());
    }

    private boolean isBreachType() {
        return this.maceType.getValue().toString().equals(TYPE_BREACH.toString());
    }

    private double getMaceReach() {
        Reach reach = Vape.INSTANCE.getModManager().getMod(Reach.class);
        return reach == null ? 3.0 : reach.getReachDistance();
    }

    private static double distanceToTarget(double x, double y, double z, EntityLivingBase target,
                                           double offsetX, double offsetY, double offsetZ) {
        AxisAlignedBB bounds = target.u$src$Lgg_vape_wrapper_impl_AxisAlignedBB_$kogbsu();
        double closestX = clamp(x, bounds.getMinX() + offsetX, bounds.getMaxX() + offsetX);
        double closestY = clamp(y, bounds.getMinY() + offsetY, bounds.getMaxY() + offsetY);
        double closestZ = clamp(z, bounds.getMinZ() + offsetZ, bounds.getMaxZ() + offsetZ);
        double deltaX = x - closestX;
        double deltaY = y - closestY;
        double deltaZ = z - closestZ;
        return Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private boolean requestSyntheticAttack(boolean markInProgress) {
        if (!markInProgress) {
            return AttackKeyController.requestSyntheticAttack(this);
        }
        this.dispatchingSyntheticAttack = true;
        try {
            return AttackKeyController.requestSyntheticAttack(this);
        } finally {
            this.dispatchingSyntheticAttack = false;
        }
    }

    public boolean canHandleMaceAttack() {
        if (!this.stunSlam.getEffectiveValue().booleanValue()) {
            return false;
        }
        EntityPlayerSP player = Minecraft.thePlayer();
        return this.isHoldingAxe(player)
                || player.isNotNull()
                && this.findAxeSlot(player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6()) >= 0;
    }

    public boolean isSyntheticAttackInProgress() {
        return this.dispatchingSyntheticAttack;
    }

    public boolean hasReadyMace() {
        return this.findBestMaceSlot(this.getCrosshairTarget(), true) >= 0;
    }

    private void abortWeaponSwap(EntityPlayerSP player) {
        if (player.isNotNull() && this.originalSlot != -1) {
            player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6().g(this.originalSlot);
        }
        this.originalSlot = -1;
        this.clearWeaponSwapState();
    }

    private void clearWeaponSwapState() {
        this.weaponSwapActive = false;
        this.weaponSwapTicks = 0;
        this.stunSlamFollowupPending = false;
        this.maceSlot = -1;
        this.originalAttackTick = -1;
        this.targetId = -1;
    }

    private void releaseRotation() {
        if (this.rotationController != null) {
            this.rotationController.clearTarget();
            if (RotationManager.INSTANCE.getActiveController() == this.rotationController) {
                RotationManager.INSTANCE.releaseController(this.rotationController);
            }
            this.rotationController = null;
        }
        this.rotationClaim.release(this);
    }

    @Override
    public void onDisable() {
        EntityPlayerSP player = Minecraft.thePlayer();
        if (player.isNotNull()) {
            this.restoreArmorSlot(player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6());
        }
        this.releaseRotation();
        this.movementSnapshot = null;
        if (this.releasePending) {
            AttackKeyController.releaseAttackKey();
        }
        this.releasePending = false;
        this.weaponSwapActive = false;
        this.originalSlot = -1;
        this.weaponSwapTicks = 0;
        this.stunSlamFollowupPending = false;
        this.maceSlot = -1;
        this.originalAttackTick = -1;
        this.targetId = -1;
        this.dispatchingSyntheticAttack = false;
        this.resetElytraFlightState(true);
    }

    private enum ElytraFlightState {
        IDLE,
        WAITING_FOR_GLIDE_END,
        ABORTED_THIS_FLIGHT
    }

    private static final class FallSample {
        private final int tick;
        private final double x;
        private final double y;
        private final double z;
        private final float fallDistance;
        private final boolean onGround;
        private final boolean fallFlying;

        private FallSample(int tick, double x, double y, double z, float fallDistance,
                           boolean onGround, boolean fallFlying) {
            this.tick = tick;
            this.x = x;
            this.y = y;
            this.z = z;
            this.fallDistance = fallDistance;
            this.onGround = onGround;
            this.fallFlying = fallFlying;
        }
    }

    private static final class ImpactPrediction {
        private boolean valid;
        private int tick;
        private double sourceX;
        private double sourceY;
        private double sourceZ;
        private double aimX;
        private double aimY;
        private double aimZ;
    }

    private static final class ElytraTarget {
        private final int impactTick;

        private ElytraTarget(int impactTick) {
            this.impactTick = impactTick;
        }
    }
}
