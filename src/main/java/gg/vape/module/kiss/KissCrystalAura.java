package gg.vape.module.kiss;

import gg.vape.Vape;
import gg.vape.event.EventHandler;
import gg.vape.event.impl.EventPreTick;
import gg.vape.event.impl.EventWorldChange;
import gg.vape.mapping.MappedClasses;
import gg.vape.module.Category;
import gg.vape.module.Mod;
import gg.vape.module.combat.CrystalAura;
import gg.vape.module.combat.crystalaura.ExplosionType;
import gg.vape.module.control.SharedModuleControlClaims;
import gg.vape.rotation.AdaptiveRotationController;
import gg.vape.rotation.RotationAngles;
import gg.vape.rotation.RotationControlClaim;
import gg.vape.rotation.RotationManager;
import gg.vape.utils.BlockUtil;
import gg.vape.utils.MathUtil;
import gg.vape.utils.RotationUtil;
import gg.vape.utils.RotationVectorMath;
import gg.vape.utils.datas.BlockData;
import gg.vape.unmap.ModeOption;
import gg.vape.value.BooleanValue;
import gg.vape.value.ModeValue;
import gg.vape.value.NumberValue;
import gg.vape.wrapper.impl.AxisAlignedBB;
import gg.vape.wrapper.impl.Block;
import gg.vape.wrapper.impl.BlockPos;
import gg.vape.wrapper.impl.BlockState;
import gg.vape.wrapper.impl.Entity;
import gg.vape.wrapper.impl.EntityLivingBase;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.KeyBinding;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.MonsterAttributesBridge;
import gg.vape.wrapper.impl.RayTraceResult;
import gg.vape.wrapper.impl.RayTraceResult_type;
import gg.vape.wrapper.impl.Vec3;
import gg.vape.wrapper.impl.World;
import gg.vape.wrapper.impl.WorldClient;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Port of KissClient's CrystalAura (bc/ysg/a.java).
 *
 * <p>Keeps Kiss' observable behaviour: nearest-in-FOV target with a 0.75 block
 * stickiness margin, an IDLE/PLACE_OBBY/PLACE_CRYSTAL/ATTACK/WAIT_BASE state
 * machine, per-action delays (obby/crystal/attack), a blacklist of positions that
 * failed for 40 ticks, the "5 attacks then blacklist the base" rule, and the
 * efficiency filter that prefers damage on the enemy over self damage.</p>
 */
public class KissCrystalAura
extends Mod {
    private static final int MOD_ID = 1263421011;

    /** CrystalAura exposes the damage/hotbar helpers this port needs; the module only exists on 1.21.4+. */
    private CrystalAura aura() {
        return Vape.INSTANCE.getModManager().getMod(CrystalAura.class);
    }
    private enum State {
        IDLE,
        PLACE_OBBY,
        PLACE_CRYSTAL,
        ATTACK,
        WAIT_BASE
    }

    private static final double TARGET_STICKINESS = 0.75;
    private static final int BLACKLIST_TICKS = 40;
    private static final int MAX_HITS_PER_CRYSTAL = 5;
    private static final double SCORE_BASE_BONUS = 0.5;
    private static final int PER_CRYSTAL_ATTACK_COOLDOWN = 10;
    private static final float LOCK_VIEW_STEP = 30.0f;

    private final ModeValue aimMode;
    private final ModeValue attackMode;
    private final ModeOption aimSilent;
    private final ModeOption aimLockView;
    private final ModeOption attackAuto;
    private final ModeOption attackRightClick;
    private final NumberValue fov;
    private final NumberValue range;
    private final NumberValue obbyDelay;
    private final NumberValue crystalDelay;
    private final NumberValue attackDelay;
    private final NumberValue minEnemyDamage;
    private final NumberValue maxSelfDamage;
    private final NumberValue predict;
    private final BooleanValue efficient;
    private final BooleanValue fastPlace;
    private final BooleanValue breakCrystals;
    private final BooleanValue animals;
    private final BooleanValue deathCheck;
    private final BooleanValue autoObsidian;
    private final BooleanValue debug;

    private final RotationManager rotationManager;
    private final RotationControlClaim rotationClaim;
    private final Map<Long, Long> blacklist = new HashMap<>();
    private final Map<Integer, Integer> crystalHits = new HashMap<>();
    private final Map<Integer, Long> crystalCooldowns = new HashMap<>();
    private final List<Vec3> predictedPositions = new ArrayList<>();
    private final List<Entity> scratchEntities = new ArrayList<>();

    private State state = State.IDLE;
    private EntityLivingBase target;
    private Entity lastTarget;
    private BlockData cell;
    private BlockData support;
    private int supportFacing = 1;
    private Entity crystal;
    private long stateStart;
    private long tickCounter;
    private long lastAttackTime;
    private boolean firstAction = true;
    private int savedSlot = -1;
    private AdaptiveRotationController rotationController;
    private boolean viewSaved;
    private float savedViewYaw;
    private boolean homeViewSaved;
    private float homeViewYaw;
    private float homeViewPitch;
    private float savedViewPitch;
    private String status = "";
    private double nearestValidDistance = -1.0;
    private boolean errorLogged;
    private long lastObbyLog;
    private int dbgVisited, dbgBase, dbgSolid, dbgBelowAir, dbgAbove, dbgEntity, dbgDamage, dbgSelf, dbgEff, dbgBlack, dbgNoItem;
    private int aimMissTicks;
    private int diagTicks;
    private int scanLoaded;
    private int scanLiving;
    private int scanValid;
    private String scanSample = "";
    private final StringBuilder scanNames = new StringBuilder();
    private final StringBuilder rejectInfo = new StringBuilder();

    public KissCrystalAura() {
        super("Kiss CrystalAura", MOD_ID, Category.KISS,
                "CrystalAura logic ported from KissClient: state machine, obby/crystal placement and damage-based efficiency checks");
        this.rotationManager = RotationManager.INSTANCE;
        this.rotationClaim = SharedModuleControlClaims.rotation;
        this.aimSilent = new ModeOption("Silent");
        this.aimLockView = new ModeOption("LockView");
        this.attackAuto = new ModeOption("Auto");
        this.attackRightClick = new ModeOption("RightClick");
        this.aimMode = ModeValue.create(this, "Aim Mode", "Silent - rotate outgoing packets only\nLockView - turn your camera towards the target", this.aimSilent, this.aimLockView);
        this.attackMode = ModeValue.create(this, "Attack Mode", "Auto - find targets and place blocks yourself\nRightClick - only handle obsidian you placed manually", this.attackAuto, this.attackRightClick);
        this.fov = NumberValue.create(this, "FOV", "#", "\u00b0", 1.0, 180.0, 360.0, 1.0, "Field of view used when picking a target");
        this.range = NumberValue.create(this, "Range", "#.#", "m", 1.0, 4.5, 6.0, 0.1, "Maximum target range");
        this.obbyDelay = NumberValue.create(this, "Obby Delay", "#", "ms", 0.0, 0.0, 500.0, 10.0, "Delay between obsidian placements");
        this.crystalDelay = NumberValue.create(this, "Crystal Delay", "#", "ms", 0.0, 0.0, 500.0, 10.0, "Delay between crystal placements");
        this.attackDelay = NumberValue.create(this, "Attack Delay", "#", "ms", 0.0, 0.0, 500.0, 10.0, "Delay between crystal attacks");
        this.minEnemyDamage = NumberValue.create(this, "Min enemy damage", "#.#", "HP", 0.0, 3.0, 20.0, 0.5, "Minimum damage required on the enemy");
        this.maxSelfDamage = NumberValue.create(this, "Max self damage", "#", "HP", 0.0, 20.0, 20.0, 1.0, "Maximum self damage allowed");
        this.predict = NumberValue.create(this, "Predict", "#", "ticks", 0.0, 2.0, 5.0, 1.0, "Movement prediction ticks used for damage checks");
        this.efficient = BooleanValue.create(this, "Efficient", true, "Only place when enemy damage beats self damage");
        this.fastPlace = BooleanValue.create(this, "Fast Place", false, "Run two placement steps per tick");
        this.breakCrystals = BooleanValue.create(this, "Break Crystals", true, "Attack crystals that are already placed");
        this.animals = BooleanValue.create(this, "Animals", true, "Allow animals as targets");
        this.deathCheck = BooleanValue.create(this, "Death Check", true, "Disable the module when you die");
        this.autoObsidian = BooleanValue.create(this, "Auto Obsidian", true, "Place obsidian when no base exists");
        this.debug = BooleanValue.create(this, "Debug", false, "Print state changes to the log");
        this.addValue(this.aimMode, this.attackMode, this.fov, this.range, this.obbyDelay, this.crystalDelay, this.attackDelay,
                this.minEnemyDamage, this.maxSelfDamage, this.predict, this.efficient, this.fastPlace,
                this.breakCrystals, this.animals, this.deathCheck, this.autoObsidian, this.debug);
    }

    @Override
    public String getDetailedSuffix() {
        return this.status == null || this.status.isEmpty() ? "Kiss" : "Kiss " + this.status;
    }

    @Override
    public void onEnable() {
        this.errorLogged = false;
        this.diagTicks = 0;
        this.reset(true);
        EntityPlayerSP player = Minecraft.thePlayer();
        if (player != null && !player.isNull()) {
            this.homeViewYaw = player.J();
            this.homeViewPitch = player.V();
            this.homeViewSaved = true;
            this.restoreHomeView(player);
        }
        Vape.debugLog("[KissCrystal] enabled, mode=" + this.aura().getName());
    }

    @Override
    public void onDisable() {
        this.reset(true);
        this.restoreHomeView(Minecraft.thePlayer());
    }

    /** Snap the camera back to the view it had when the module was toggled. */
    private void restoreHomeView(EntityPlayerSP player) {
        if (!this.homeViewSaved || player == null || player.isNull()) {
            return;
        }
        player.z(this.homeViewYaw);
        player.C(this.homeViewPitch);
        this.viewSaved = false;
    }

    @EventHandler
    public void onWorldChange(EventWorldChange eventWorldChange) {
        this.reset(true);
    }

    @EventHandler
    public void onTick(EventPreTick eventPreTick) {
        try {
            this.tick(eventPreTick);
        } catch (Throwable error) {
            if (!this.errorLogged) {
                this.errorLogged = true;
                Vape.debugLog("[KissCrystal] tick failed: " + Vape.formatThrowable(error));
            }
        }
    }

    private void tick(EventPreTick eventPreTick) {
        WorldClient world = eventPreTick.getWorld();
        EntityPlayerSP player = eventPreTick.getThePlayer();
        ++this.tickCounter;
        if (world.isNull() || player.isNull() || !eventPreTick.getCurrentScreen().isNull()) {
            this.reset(true);
            return;
        }
        if (this.deathCheck.getEffectiveValue() && player.w$src$F$15l9epb() <= 0.0f) {
            this.reset(true);
            this.setEnabled(false);
            return;
        }
        if (this.aura().shouldPause()) {
            this.reset(false);
            return;
        }
        this.selectTarget(player, world);
        if (this.diagTicks < 5) {
            ++this.diagTicks;
            Vape.debugLog("[KissCrystal] tick=" + this.tickCounter + " state=" + this.state + " target=" + (this.target != null) + " cell=" + (this.cell != null) + " aimSel=" + this.aimMode.getSelectedIndex() + " loaded=" + this.scanLoaded + " living=" + this.scanLiving + " valid=" + this.scanValid + " sample=" + this.scanSample + " names=" + this.scanNames + " rej=" + this.rejectInfo);
        }
        if (this.tickCounter % 40 == 0) {
            Vape.debugLog("[KissCrystal] period state=" + this.state + " target=" + (this.target != null)
                + " near=" + String.format("%.2f", this.nearestValidDistance) + "/" + this.range.getValue()
                + " status=" + this.status + " cell=" + (this.cell != null)
                + " crSlot=" + this.aura().findCrystalSlot(player) + " obSlot=" + this.aura().findObsidianSlot(player));
        }
        if (this.target == null) {
            this.reset(false);
            this.status = "no target";
            return;
        }
        int iterations = this.fastPlace.getEffectiveValue() ? 2 : 1;
        for (int i = 0; i < iterations; ++i) {
            switch (this.state) {
                case IDLE:
                    if (!this.search(player, world)) {
                        this.releaseRotation(player);
                        return;
                    }
                    break;
                case PLACE_OBBY:
                    if (!this.placeObsidian(player, world)) {
                        return;
                    }
                    break;
                case PLACE_CRYSTAL:
                    if (!this.placeCrystal(player, world)) {
                        return;
                    }
                    break;
                case ATTACK:
                    if (!this.attackCrystal(player, world)) {
                        return;
                    }
                    break;
                case WAIT_BASE:
                    if (!this.waitBase(world)) {
                        return;
                    }
                    break;
                default:
                    return;
            }
        }
    }

    private void selectTarget(EntityPlayerSP player, World world) {
        Vec3 playerPos = player.I$src$Lgg_vape_wrapper_impl_Vec3_$q14opk();
        EntityLivingBase best = null;
        double bestDistance = Double.MAX_VALUE;
        double currentDistance = Double.MAX_VALUE;
        this.scanLoaded = 0;
        this.scanLiving = 0;
        this.scanValid = 0;
        this.scanSample = "";
        this.scanNames.setLength(0);
        this.rejectInfo.setLength(0);
        this.nearestValidDistance = -1.0;
        ArrayList<Object> scanObjects = new ArrayList<Object>(world.z());
        double scanRadius = (Double)this.range.getValue() + 16.0;
        AxisAlignedBB scanBox = AxisAlignedBB.create(player.z() - scanRadius, player.N() - 16.0, player.h() - scanRadius,
                player.z() + scanRadius, player.N() + 16.0, player.h() + scanRadius);
        world.getEntityGetter().forEachEntityInBounds(scanBox, object -> {
            if (object != null && !scanObjects.contains(object)) {
                scanObjects.add(object);
            }
        });
        for (Object entityObject : scanObjects) {
            ++this.scanLoaded;
            Entity entity = new Entity(entityObject);
            if (entityObject != null && this.scanNames.length() < 300) {
                this.scanNames.append(entityObject.getClass().getSimpleName()).append(',');
            }
            if (this.scanSample.isEmpty() && entityObject != null) {
                this.scanSample = entityObject.getClass().getSimpleName() + "/zm=" + entity.isInstance(MappedClasses.zm) + "/null=" + entity.isNull();
            }
            if (entity.isNull() || entity.isInstance(MappedClasses.Ze) || !entity.isInstance(MappedClasses.zm)) {
                continue;
            }
            ++this.scanLiving;
            if (entity.getObject() == player.getObject()) {
                continue;
            }
            EntityLivingBase living = new EntityLivingBase(entityObject);
            if (!this.isValidTarget(player, living, playerPos)) {
                if (this.rejectInfo.length() < 420) {
                    Vec3 tp = living.I$src$Lgg_vape_wrapper_impl_Vec3_$q14opk();
                    double ddx = tp.getX() - playerPos.getX();
                    double ddz = tp.getZ() - playerPos.getZ();
                    double tyaw = Math.toDegrees(Math.atan2(ddz, ddx)) - 90.0;
                    double rd = (tyaw - (double)this.rotationManager.getManagedYaw()) % 360.0;
                    if (rd > 180.0) { rd -= 360.0; } else if (rd < -180.0) { rd += 360.0; }
                    this.rejectInfo.append(living.getObject().getClass().getSimpleName())
                              .append("|hp=").append(living.w$src$F$15l9epb())
                              .append("|type=").append(this.passesTypeFilter(living))
                              .append("|d=").append(String.format("%.1f", living.getDistanceToEntity(player)))
                              .append("|yd=").append(String.format("%.1f", rd))
                              .append(';');
                }
                continue;
            }
            ++this.scanValid;
            double distance = living.getDistanceToEntity(player);
            if (this.nearestValidDistance < 0.0 || distance < this.nearestValidDistance) {
                this.nearestValidDistance = distance;
            }
            if (distance > (Double)this.range.getValue()) {
                continue;
            }
            if (living.equals(this.target)) {
                currentDistance = Math.min(currentDistance, distance);
            }
            if (distance < bestDistance) {
                bestDistance = distance;
                best = living;
            }
        }
        if (best == null) {
            this.target = null;
            return;
        }
        if (this.target != null && this.target.equals(best) && currentDistance <= bestDistance + TARGET_STICKINESS) {
            return;
        }
        this.target = best;
        if (this.lastTarget != best) {
            this.lastTarget = best;
        }
    }

    private static void collectEntities(ArrayList<Entity> out, Object object) {
        out.add(new Entity(object));
    }

    private boolean isValidTarget(EntityPlayerSP player, EntityLivingBase living, Vec3 eyePosition) {
        if (living.isNull() || living.w$src$F$15l9epb() <= 0.0f) {
            return false;
        }
        if (!this.passesTypeFilter(living)) {
            return false;
        }
        Vec3 targetPos = living.I$src$Lgg_vape_wrapper_impl_Vec3_$q14opk();
        double dx = targetPos.getX() - eyePosition.getX();
        double dz = targetPos.getZ() - eyePosition.getZ();
        double yaw = Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
        double rawDiff = (yaw - (double)this.rotationManager.getManagedYaw()) % 360.0;
        if (rawDiff > 180.0) {
            rawDiff -= 360.0;
        } else if (rawDiff < -180.0) {
            rawDiff += 360.0;
        }
        if (Math.abs(rawDiff) > (Double)this.fov.getValue() / 2.0) {
            return false;
        }
        return true;
    }

    private boolean passesTypeFilter(EntityLivingBase living) {
        if (living.isInstance(MappedClasses.Yl)) {
            EntityPlayerSP self = Minecraft.thePlayer();
            return self == null || self.isNull() || living.getObject() != self.getObject();
        }
        return this.animals.getEffectiveValue();
    }

    private boolean search(EntityPlayerSP player, World world) {
        this.dbgVisited = this.dbgBase = this.dbgSolid = this.dbgBelowAir = this.dbgAbove = 0;
        this.dbgEntity = this.dbgDamage = this.dbgSelf = this.dbgEff = this.dbgBlack = this.dbgNoItem = 0;
        entitySearch:
        {
            if (this.breakCrystals.getEffectiveValue()) {
                Entity existing = this.findBestCrystalToBreak(player, world);
                if (existing != null) {
                    Vec3 position = existing.I$src$Lgg_vape_wrapper_impl_Vec3_$q14opk();
                    this.crystal = existing;
                    this.cell = new BlockData(MathUtil.floor(position.getX()), MathUtil.floor(position.getY()) - 1, MathUtil.floor(position.getZ()));
                    this.setState(State.ATTACK, "existing crystal");
                    return true;
                }
            }
            if (this.aura().findCrystalSlot(player) == -1) {
                Vape.debugLog("[KissCrystal] search abort: no crystal in hotbar crSlot=-1 obSlot="
                    + this.aura().findObsidianSlot(player));
                this.status = "no crystal";
                this.reset(false);
                return false;
            }
        }
        if (this.tickCounter % 20 == 0) {
            Vape.debugLog("[KissCrystal] search start crSlot=" + this.aura().findCrystalSlot(player)
                + " obSlot=" + this.aura().findObsidianSlot(player) + " break=" + this.breakCrystals.getEffectiveValue()
                + " eff=" + this.efficient.getEffectiveValue());
        }
        Vec3 eyePosition = this.eyePosition(player);
        Vec3 targetPos = this.target.I$src$Lgg_vape_wrapper_impl_Vec3_$q14opk();
        int playerX = MathUtil.floor(player.z());
        int playerY = MathUtil.floor(player.N());
        int targetY = MathUtil.floor(targetPos.getY());
        int searchRange = (int)Math.ceil((Double)this.range.getValue());
        int minY = Math.min(playerY, targetY) - 3;
        int maxY = Math.max(playerY, targetY) + 2;
        this.predictedPositions.clear();
        this.buildPredictedPositions(targetPos);
        BlockData bestCell = null;
        BlockData bestSupport = null;
        int bestFacing = 1;
        double bestScore = Double.NEGATIVE_INFINITY;
        int visited = 0;
        int baseCells = 0;
        int solidCells = 0;
        int scored = 0;
        for (int y = minY; y <= maxY; ++y) {
            for (int x = playerX - searchRange; x <= playerX + searchRange; ++x) {
                for (int z = MathUtil.floor(player.h()) - searchRange; z <= MathUtil.floor(player.h()) + searchRange; ++z) {
                    BlockData candidate = new BlockData(x, y, z);
                    ++visited;
                    if (this.isBlacklisted(candidate)) {
                        ++this.dbgBlack;
                        continue;
                    }
                    Vec3 crystalPosition = Vec3.create((double)x + 0.5, (double)y + 1.0, (double)z + 0.5);
                    if (this.eyeDistanceSq(eyePosition, crystalPosition) > 20.25) {
                        continue;
                    }
                    BlockState cellState = world.getBlockState(BlockPos.create(x, y, z));
                    boolean base = this.aura().isCrystalBaseBlock(cellState);
                    BlockData candidateSupport = null;
                    int candidateFacing = 1;
                    if (!base) {
                        if (!this.autoObsidian.getEffectiveValue() || this.aura().findObsidianSlot(player) == -1) {
                            ++this.dbgNoItem;
                            continue;
                        }
                        if (!cellState.isNull() && !KissCrystalAura.isAir(cellState)) {
                            ++solidCells;
                            continue;
                        }
                        BlockState belowState = world.getBlockState(BlockPos.create(x, y - 1, z));
                        if (belowState.isNull() || KissCrystalAura.isAir(belowState)) {
                            ++this.dbgBelowAir;
                            continue;
                        }
                        candidateSupport = new BlockData(x, y - 1, z);
                        candidateFacing = 1;
                    } else {
                        ++baseCells;
                    }
                    BlockState aboveState = world.getBlockState(BlockPos.create(x, y + 1, z));
                    if (!aboveState.isNull() && !KissCrystalAura.isAir(aboveState)) {
                        ++this.dbgAbove;
                        continue;
                    }
                    if (this.hasBlockingEntity(world, x, y + 1, z)) {
                        ++this.dbgEntity;
                        continue;
                    }
                    if (!base && candidateSupport != null && this.isBlacklisted(candidateSupport)) {
                        continue;
                    }
                    double enemyDamage = this.bestPredictedDamage(crystalPosition, world);
                    if (enemyDamage <= 0.0 || enemyDamage < (Double)this.minEnemyDamage.getValue() * 0.3) {
                        ++this.dbgDamage;
                        continue;
                    }
                    double selfDamage = this.damageAt(player, crystalPosition, world);
                    if (!this.isSelfDamageSafe(selfDamage)) {
                        ++this.dbgSelf;
                        continue;
                    }
                    if (this.efficient.getEffectiveValue() && enemyDamage <= selfDamage) {
                        ++this.dbgEff;
                    }
                    double score = enemyDamage - selfDamage + (base ? SCORE_BASE_BONUS : 0.0);
                    if (score <= bestScore) {
                        continue;
                    }
                    bestScore = score;
                    bestCell = candidate;
                    bestSupport = candidateSupport;
                    bestFacing = candidateFacing;
                    ++scored;
                }
            }
        }
        if (bestCell == null) {
            this.status = "no candidate";
            Vape.debugLog("[KissCrystal] search empty visited=" + visited + " base=" + baseCells + " solid=" + solidCells
                + " scored=" + scored
                + " black=" + this.dbgBlack + " noItem=" + this.dbgNoItem + " belowAir=" + this.dbgBelowAir
                + " above=" + this.dbgAbove + " entity=" + this.dbgEntity + " dmg=" + this.dbgDamage
                + " self=" + this.dbgSelf + " eff=" + this.dbgEff
                + " range=" + this.range.getValue() + " eye="
                + String.format("%.2f,%.2f,%.2f", player.z(), player.N(), player.h())
                + " target=" + String.format("%.2f,%.2f,%.2f", targetPos.getX(), targetPos.getY(), targetPos.getZ()));
            this.reset(false);
            return false;
        }
        if (!this.verifyCandidate(player, world, bestCell, bestSupport)) {
            this.blacklist(bestCell);
            this.reset(false);
            return false;
        }
        this.cell = bestCell;
        this.support = bestSupport;
        this.supportFacing = bestFacing;
        this.crystal = null;
        this.setState(this.support == null ? State.PLACE_CRYSTAL : State.PLACE_OBBY, "search");
        return true;
    }

    private boolean verifyCandidate(EntityPlayerSP player, World world, BlockData candidate, BlockData candidateSupport) {
        Vec3 crystalPosition = Vec3.create((double)candidate.D() + 0.5, (double)candidate.B() + 1.0, (double)candidate.G() + 0.5);
        double enemyDamage = this.bestPredictedDamage(crystalPosition, world);
        if (enemyDamage < (Double)this.minEnemyDamage.getValue()) {
            return false;
        }
        double selfDamage = this.damageAt(player, crystalPosition, world);
        if (!this.isSelfDamageSafe(selfDamage)) {
            return false;
        }
        return true;
    }

    private boolean placeObsidian(EntityPlayerSP player, World world) {
        if (this.cell == null || this.support == null) {
            this.setState(State.IDLE, "no obby target");
            return false;
        }
        if (this.aura().isCrystalBaseBlock(world.getBlockState(BlockPos.create(this.cell.D(), this.cell.B(), this.cell.G())))) {
            this.setState(State.PLACE_CRYSTAL, "base appeared");
            return true;
        }
        if (!this.delayElapsed(this.obbyDelay, this.stateStart)) {
            return false;
        }
        int slot = this.aura().findObsidianSlot(player);
        if (slot == -1) {
            this.reset(false);
            this.status = "no obby";
            return false;
        }
        this.switchToSlot(player, slot);
        if (!this.aimAt(player, this.support, 1)) {
            this.logObby("aim fail");
            return false;
        }
        if (!this.isCrosshairOnBlock(player, this.support)) {
            this.logObby("crosshair miss");
            if (++this.aimMissTicks > 30) {
                this.aimMissTicks = 0;
                this.blacklist(this.cell);
                this.setState(State.IDLE, "aim stuck");
            }
            return false;
        }
        this.aimMissTicks = 0;
        this.placeItem();
        this.logObby("clicked");
        this.setState(State.WAIT_BASE, "obby placed");
        return true;
    }

    private void logObby(String reason) {
        long now = System.currentTimeMillis();
        if (now - this.lastObbyLog < 200L) {
            return;
        }
        this.lastObbyLog = now;
        EntityPlayerSP player = Minecraft.thePlayer();
        RayTraceResult hit = this.rotationManager.rayTraceUsingManagedRotation(4.5, 0.0f, false);
        String hitDesc = "none";
        if (hit != null && !hit.isNull() && hit.getTypeOfHit().equals(RayTraceResult_type.block())) {
            BlockPos position = hit.getBlockPos();
            hitDesc = position.getX() + "," + position.getY() + "," + position.getZ();
        }
        double distance = -1.0;
        if (player != null && !player.isNull()) {
            Vec3 point = Vec3.create((double)this.support.D() + 0.5, (double)this.support.B() + 1.0, (double)this.support.G() + 0.5);
            distance = Math.sqrt(this.eyeDistanceSq(this.eyePosition(player), point));
        }
        Vape.debugLog("[KissCrystal] obby " + reason
            + " cell=" + this.cell.D() + "," + this.cell.B() + "," + this.cell.G()
            + " support=" + this.support.D() + "," + this.support.B() + "," + this.support.G()
            + " hit=" + hitDesc
            + " dist=" + String.format("%.2f", distance)
            + " lock=" + this.isLockView()
            + " sel=" + (this.rotationManager.getActiveController() == this.rotationController));
    }

    private boolean waitBase(World world) {
        if (this.cell == null) {
            this.setState(State.IDLE, "wait base no cell");
            return false;
        }
        if (this.aura().isCrystalBaseBlock(world.getBlockState(BlockPos.create(this.cell.D(), this.cell.B(), this.cell.G())))) {
            this.setState(State.PLACE_CRYSTAL, "base confirmed");
            return true;
        }
        if (this.ticksSinceStateStart() > 10) {
            this.blacklist(this.cell);
            this.reset(false);
            return false;
        }
        return false;
    }

    private boolean placeCrystal(EntityPlayerSP player, World world) {
        if (this.cell == null) {
            this.setState(State.IDLE, "no place cell");
            return false;
        }
        Entity existing = this.findCrystalAbove(world, this.cell);
        if (existing != null) {
            this.crystal = existing;
            this.setState(State.ATTACK, "crystal exists");
            return true;
        }
        if (!this.aura().isCrystalBaseBlock(world.getBlockState(BlockPos.create(this.cell.D(), this.cell.B(), this.cell.G())))) {
            this.blacklist(this.cell);
            this.reset(false);
            return false;
        }
        if (!this.delayElapsed(this.crystalDelay, this.stateStart)) {
            return false;
        }
        int slot = this.aura().findCrystalSlot(player);
        if (slot == -1) {
            this.reset(false);
            this.status = "no crystal";
            return false;
        }
        if (this.hasBlockingEntity(world, this.cell.D(), this.cell.B() + 1, this.cell.G())) {
            return false;
        }
        Vec3 crystalPosition = Vec3.create((double)this.cell.D() + 0.5, (double)this.cell.B() + 1.0, (double)this.cell.G() + 0.5);
        if (!this.isSelfDamageSafe(this.damageAt(player, crystalPosition, world))) {
            this.reset(false);
            return false;
        }
        this.switchToSlot(player, slot);
        if (!this.aimAt(player, this.cell, 1)) {
            return false;
        }
        if (!this.isCrosshairOnBlock(player, this.cell)) {
            if (++this.aimMissTicks > 30) {
                this.aimMissTicks = 0;
                this.blacklist(this.cell);
                this.setState(State.IDLE, "crystal aim stuck");
            }
            return false;
        }
        this.aimMissTicks = 0;
        this.placeItem();
        this.setState(State.ATTACK, "crystal placed");
        return true;
    }

    private boolean attackCrystal(EntityPlayerSP player, World world) {
        if (this.crystal == null || this.crystal.isNull() || this.crystal.M$src$Z$ff28xj()) {
            this.crystal = this.cell == null ? null : this.findCrystalAbove(world, this.cell);
        }
        if (this.crystal == null || this.crystal.isNull()) {
            if (this.ticksSinceStateStart() > 10) {
                this.blacklist(this.cell);
                this.reset(false);
            }
            return false;
        }
        Vec3 crystalPosition = this.crystal.I$src$Lgg_vape_wrapper_impl_Vec3_$q14opk();
        if (!this.isSelfDamageSafe(this.damageAt(player, crystalPosition, world))) {
            this.reset(false);
            return false;
        }
        if (!this.delayElapsed(this.attackDelay, this.lastAttackTime)) {
            return false;
        }
        this.switchToSlot(player, this.aura().findCrystalSlot(player));
        if (!this.aimAtEntity(player, this.crystal)) {
            return false;
        }
        RayTraceResult hit = this.rotationManager.getNormalReachRayTrace();
        if (hit == null || hit.isNull() || !hit.getTypeOfHit().equals(RayTraceResult_type.entity())) {
            return false;
        }
        Entity hitEntity = hit.getEntity();
        if (hitEntity.isNull() || !hitEntity.isInstance(MappedClasses.Ze)) {
            return false;
        }
        long cooldown = this.crystalCooldowns.getOrDefault(this.crystal.S(), 0L);
        if (this.tickCounter - cooldown < PER_CRYSTAL_ATTACK_COOLDOWN) {
            return false;
        }
        this.attackItem();
        this.lastAttackTime = System.currentTimeMillis();
        this.crystalCooldowns.put(this.crystal.S(), this.tickCounter);
        Integer hits = this.crystalHits.get(this.crystal.S());
        hits = hits == null ? 1 : hits + 1;
        this.crystalHits.put(this.crystal.S(), hits);
        if (hits >= MAX_HITS_PER_CRYSTAL) {
            this.blacklist(this.cell);
            this.reset(false);
        }
        return true;
    }

    private Entity findBestCrystalToBreak(EntityPlayerSP player, World world) {
        ArrayList<Entity> crystals = new ArrayList<>();
        for (Object entityObject : new ArrayList<Object>(world.z())) {
            Entity entity = new Entity(entityObject);
            if (!entity.isNull() && entity.isInstance(MappedClasses.Ze)) {
                crystals.add(entity);
            }
        }
        Entity best = null;
        double bestScore = 0.0;
        for (Entity crystalEntity : crystals) {
            if (crystalEntity.isNull() || crystalEntity.M$src$Z$ff28xj()) {
                continue;
            }
            Vec3 position = crystalEntity.I$src$Lgg_vape_wrapper_impl_Vec3_$q14opk();
            if (this.eyePosition(player).distanceTo(position) > 4.5) {
                continue;
            }
            double enemyDamage = this.bestPredictedDamage(position, world);
            double selfDamage = this.damageAt(player, position, world);
            if (enemyDamage < (Double)this.minEnemyDamage.getValue() || !this.isSelfDamageSafe(selfDamage)) {
                continue;
            }
            if (this.efficient.getEffectiveValue() && enemyDamage <= selfDamage) {
                continue;
            }
            double score = enemyDamage - selfDamage;
            if (score > bestScore) {
                bestScore = score;
                best = crystalEntity;
            }
        }
        if (best != null) {
            return best;
        }
        return null;
    }

    private static void collectCrystals(ArrayList<Entity> out, Object object) {
        if (MappedClasses.Ze.isAssignableFrom(object.getClass())) {
            out.add(new Entity(object));
        }
    }

    private Entity findCrystalAbove(World world, BlockData base) {
        if (base == null) {
            return null;
        }
        AxisAlignedBB box = AxisAlignedBB.create(base.D(), base.B() + 1, base.G(),
                (double)base.D() + 1.0, (double)base.B() + 3.0, (double)base.G() + 1.0);
        ArrayList<Entity> crystals = new ArrayList<>();
        world.getEntityGetter().forEachEntityInBounds(box, object -> KissCrystalAura.collectCrystals(crystals, object));
        for (Entity crystalEntity : crystals) {
            if (crystalEntity.isNull() || crystalEntity.M$src$Z$ff28xj()) {
                continue;
            }
            return crystalEntity;
        }
        return null;
    }

    private boolean hasBlockingEntity(World world, int x, int y, int z) {
        AxisAlignedBB box = AxisAlignedBB.create((double)x - 0.5, y, (double)z - 0.5, (double)x + 1.5, y + 2.0, (double)z + 1.5);
        ArrayList<Entity> entities = new ArrayList<>();
        world.getEntityGetter().forEachEntityInBounds(box, object -> KissCrystalAura.collectEntities(entities, object));
        EntityPlayerSP self = Minecraft.thePlayer();
        Object selfObject = self == null || self.isNull() ? null : self.getObject();
        for (Entity entity : entities) {
            if (entity.isNull() || entity.n$src$Z$fx7gig()) {
                continue;
            }
            if (entity.isInstance(MappedClasses.Ze)) {
                continue;
            }
            if (selfObject != null && selfObject == entity.getObject()) {
                continue;
            }
            return true;
        }
        return false;
    }

    private void buildPredictedPositions(Vec3 targetPos) {
        this.predictedPositions.add(targetPos);
        int count = (int)((Double)this.predict.getValue()).doubleValue();
        if (count <= 0) {
            return;
        }
        EntityLivingBase entity = this.target;
        if (entity == null || entity.isNull()) {
            return;
        }
        double deltaX = entity.z() - entity.M();
        double deltaZ = entity.h() - entity.m$src$D$fwnne5();
        for (int i = 1; i <= count; ++i) {
            this.predictedPositions.add(targetPos.addVector(deltaX * (double)i, 0.0, deltaZ * (double)i));
        }
    }

    private double bestPredictedDamage(Vec3 crystalPosition, World world) {
        double best = 0.0;
        for (Vec3 position : this.predictedPositions) {
            double damage = this.damageAt(this.target, position, world, crystalPosition);
            best = Math.max(best, damage);
        }
        return best;
    }

    private float damageAt(EntityPlayerSP player, Vec3 position, World world) {
        return this.aura().calculateExplosionDamage(player, ExplosionType.CRYSTAL, position, world);
    }

    private double damageAt(EntityLivingBase entity, Vec3 entityPosition, World world, Vec3 crystalPosition) {
        if (entity == null) {
            return 0.0;
        }
        if (entity.isInstance(MappedClasses.Yl)) {
            EntityPlayerSP asPlayer = new EntityPlayerSP(entity.getObject());
            return this.aura().calculateExplosionDamage(asPlayer, ExplosionType.CRYSTAL, crystalPosition, world);
        }
        Vec3 livingPosition = entityPosition != null ? entityPosition : entity.I$src$Lgg_vape_wrapper_impl_Vec3_$q14opk();
        double dx = livingPosition.getX() - crystalPosition.getX();
        double dy = livingPosition.getY() - crystalPosition.getY();
        double dz = livingPosition.getZ() - crystalPosition.getZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double normalizedDistance = distance / 12.0;
        if (normalizedDistance > 1.0) {
            return 0.0;
        }
        float blockDensity = CrystalAura.calculateBlockDensity(crystalPosition, entity, world);
        float rawDamage = this.aura().calculateRawExplosionDamage(12.0f, normalizedDistance, blockDensity);
        float armor = (float)entity.o(MonsterAttributesBridge.L());
        float toughness = (float)entity.o(MonsterAttributesBridge.m$src$Lgg_vape_wrapper_impl_Holder_$1lgjxui());
        return CrystalAura.reduceDamageByArmor(rawDamage, armor, toughness);
    }

    private double eyeDistanceSq(Vec3 eye, Vec3 point) {
        double dx = eye.getX() - point.getX();
        double dy = eye.getY() - point.getY();
        double dz = eye.getZ() - point.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private Vec3 eyePosition(EntityPlayerSP player) {
        return player.I$src$Lgg_vape_wrapper_impl_Vec3_$q14opk().addVector(0.0, player.X(), 0.0);
    }

    private boolean aimAt(EntityPlayerSP player, BlockData block, int facing) {
        if (!this.rotationClaim.isOwnedBy(this.aura()) && !this.rotationClaim.acquire(this.aura(), true)) {
            return false;
        }
        Vec3 point = Vec3.create((double)block.D() + 0.5, (double)block.B() + (facing == 1 ? 0.98 : 0.5), (double)block.G() + 0.5);
        return this.aimAtPoint(player, point);
    }

    private boolean aimAtEntity(EntityPlayerSP player, Entity entity) {
        if (!this.rotationClaim.isOwnedBy(this.aura()) && !this.rotationClaim.acquire(this.aura(), true)) {
            return false;
        }
        Vec3 point = RotationUtil.M(this.eyePosition(player), entity.R$src$Lgg_vape_wrapper_impl_AxisAlignedBB_$r19dfl(), 0.0, 0.0, 0.0).toVec3();
        return this.aimAtPoint(player, point);
    }

    private boolean isLockView() {
        return this.aimMode.getSelectedIndex() == 1;
    }

    private void applyLockViewRotation(EntityPlayerSP player, Vec3 point) {
        Vec3 eye = this.eyePosition(player);
        double dx = point.getX() - eye.getX();
        double dy = point.getY() - eye.getY();
        double dz = point.getZ() - eye.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float targetYaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float targetPitch = (float)(-Math.toDegrees(Math.atan2(dy, horizontal)));
        if (!this.viewSaved) {
            this.savedViewYaw = player.J();
            this.savedViewPitch = player.V();
            this.viewSaved = true;
        }
        float currentYaw = player.J();
        float deltaYaw = targetYaw - currentYaw;
        float deltaPitch = targetPitch - player.V();
        if (deltaYaw > 180.0f) {
            deltaYaw -= 360.0f;
        } else if (deltaYaw < -180.0f) {
            deltaYaw += 360.0f;
        }
        deltaYaw = Math.max(-LOCK_VIEW_STEP, Math.min(LOCK_VIEW_STEP, deltaYaw));
        deltaPitch = Math.max(-LOCK_VIEW_STEP, Math.min(LOCK_VIEW_STEP, deltaPitch));
        player.z(currentYaw + deltaYaw);
        player.C(player.V() + deltaPitch);
    }

    private boolean aimAtPoint(EntityPlayerSP player, Vec3 point) {
        if (this.isLockView()) {
            this.rotationClaim.release(this.aura());
            this.applyLockViewRotation(player, point);
            return true;
        }
        if (this.rotationController == null) {
            this.rotationController = new AdaptiveRotationController();
        }
        this.rotationController.setSpeed(6.0f);
        this.rotationController.setComplete(false);
        this.rotationController.setRetainAfterCompletion(false);
        this.rotationController.setClampStepToRemaining(true);
        this.rotationController.setTolerance(0.0f);
        this.rotationController.setScaleAxesProportionally(true);
        this.rotationController.setLinearAcceleration(true);
        this.rotationController.setRelativeMode(false);
        RotationAngles angles = RotationVectorMath.d(this.eyePosition(player), point, this.rotationManager.getManagedYaw(), this.rotationManager.getManagedPitch());
        this.rotationController.setTargetRotation(angles);
        this.rotationManager.setController(this.rotationController);
        return true;
    }

    private boolean isCrosshairOnBlock(EntityPlayerSP player, BlockData block) {
        if (!this.isLockView() && (this.rotationController == null || this.rotationManager.getActiveController() != this.rotationController)) {
            return false;
        }
        RayTraceResult hit = this.rotationManager.rayTraceUsingManagedRotation(4.5, 0.0f, false);
        if (hit == null || hit.isNull() || !hit.getTypeOfHit().equals(RayTraceResult_type.block())) {
            return false;
        }
        BlockPos position = hit.getBlockPos();
        return position != null && position.getX() == block.D() && position.getY() == block.B() && position.getZ() == block.G();
    }

    private void placeItem() {
        KeyBinding attackKey = Minecraft.gameSettings().F();
        if (attackKey.u() || attackKey.isPressed()) {
            KeyBinding.setKeyBindState(attackKey, false);
        }
        KeyBinding useKey = Minecraft.gameSettings().b$src$Lgg_vape_wrapper_impl_KeyBinding_$1yi3362();
        if (useKey.u() || useKey.isPressed()) {
            KeyBinding.setKeyBindState(useKey, false);
        }
        KeyBinding.setKeyBindState(useKey, true);
        KeyBinding.onTick(useKey);
        KeyBinding.setKeyBindState(useKey, false);
    }

    private void attackItem() {
        KeyBinding useKey = Minecraft.gameSettings().b$src$Lgg_vape_wrapper_impl_KeyBinding_$1yi3362();
        if (useKey.u() || useKey.isPressed()) {
            KeyBinding.setKeyBindState(useKey, false);
        }
        KeyBinding attackKey = Minecraft.gameSettings().F();
        KeyBinding.setKeyBindState(attackKey, false);
        KeyBinding.setKeyBindState(attackKey, true);
        KeyBinding.onTick(attackKey);
        KeyBinding.setKeyBindState(attackKey, false);
    }

    private void switchToSlot(EntityPlayerSP player, int slot) {
        if (slot < 0 || player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6().v() == slot) {
            return;
        }
        if (this.savedSlot == -1) {
            this.savedSlot = player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6().v();
        }
        player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6().g(slot);
    }

    private void restoreSlot(EntityPlayerSP player) {
        if (this.savedSlot != -1 && player != null && !player.isNull()) {
            player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6().g(this.savedSlot);
        }
        this.savedSlot = -1;
    }

    private boolean isSelfDamageSafe(double damage) {
        return damage <= (Double)this.maxSelfDamage.getValue();
    }

    private boolean delayElapsed(NumberValue value, long reference) {
        if (this.firstAction) {
            return true;
        }
        return System.currentTimeMillis() - reference >= (long)((Double)value.getValue()).doubleValue();
    }

    private long ticksSinceStateStart() {
        return (System.currentTimeMillis() - this.stateStart) / 50L;
    }

    private void setState(State newState, String reason) {
        if (this.state == newState) {
            return;
        }
        Vape.debugLog("[KissCrystal] " + this.state + " -> " + newState + " (" + reason + ")");
        this.state = newState;
        this.stateStart = System.currentTimeMillis();
        this.firstAction = false;
        this.status = reason;
    }

    private boolean isBlacklisted(BlockData block) {
        Long expiry = this.blacklist.get(KissCrystalAura.key(block));
        if (expiry == null) {
            return false;
        }
        long tick = this.currentTick();
        if (tick >= expiry) {
            this.blacklist.remove(KissCrystalAura.key(block));
            return false;
        }
        return true;
    }

    private void blacklist(BlockData block) {
        if (block == null) {
            return;
        }
        if (this.blacklist.size() > 64) {
            this.blacklist.clear();
        }
        this.blacklist.put(KissCrystalAura.key(block), this.currentTick() + BLACKLIST_TICKS);
    }

    private long currentTick() {
        return this.tickCounter;
    }

    private static long key(BlockData block) {
        return ((long)block.D() & 0x3FFFFFFL) << 38 | ((long)block.B() & 0xFFFL) << 26 | ((long)block.G() & 0x3FFFFFFL);
    }

    private void reset(boolean clearRotation) {
        this.state = State.IDLE;
        this.cell = null;
        this.support = null;
        this.supportFacing = 1;
        this.crystal = null;
        this.firstAction = true;
        this.status = "";
        EntityPlayerSP player = Minecraft.thePlayer();
        if (player != null && !player.isNull()) {
            this.restoreSlot(player);
        }
        this.releaseRotation(player);
        if (clearRotation) {
            this.crystal = null;
            this.target = null;
            this.blacklist.clear();
            this.crystalHits.clear();
            this.crystalCooldowns.clear();
        }
    }

    private void releaseRotation(EntityPlayerSP player) {
        if (this.rotationController != null) {
            this.rotationController.setComplete(true);
            this.rotationController.setRetainAfterCompletion(false);
            this.rotationManager.releaseController(this.rotationController);
            this.rotationController = null;
        }
        this.rotationClaim.release(this.aura());
        if (this.viewSaved) {
            if (player != null && !player.isNull()) {
                player.z(this.savedViewYaw);
                player.C(this.savedViewPitch);
            }
            this.viewSaved = false;
        }
    }

    private static boolean isAir(BlockState state) {
        Block block = state.getBlock();
        return block.isNull() || BlockUtil.u(block);
    }
}
