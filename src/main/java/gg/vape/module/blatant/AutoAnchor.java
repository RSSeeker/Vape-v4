package gg.vape.module.blatant;

import gg.vape.Vape;
import gg.vape.event.EventHandler;
import gg.vape.event.impl.EventPreTick;
import gg.vape.event.impl.EventRightClickMouse;
import gg.vape.module.Category;
import gg.vape.module.Mod;
import gg.vape.module.blatant.autoanchor.AnchorBlockHitTarget;
import gg.vape.module.blatant.autoanchor.AnchorMacroState;
import gg.vape.module.blatant.autoanchor.AnchorObstructionPlacementCandidate;
import gg.vape.module.control.SharedModuleControlClaims;
import gg.vape.module.utility.clutch.ClutchPlacementPathUtils;
import gg.vape.notification.NotificationText;
import gg.vape.notification.NotificationType;
import gg.vape.rotation.AdaptiveRotationController;
import gg.vape.rotation.FixedRotationController;
import gg.vape.rotation.PointRotationController;
import gg.vape.rotation.RotationAngles;
import gg.vape.rotation.RotationControlClaim;
import gg.vape.rotation.RotationManager;
import gg.vape.unmap.ItemLimitData;
import gg.vape.unmap.ModeOption;
import gg.vape.unmap.ModeSelection;
import gg.vape.utils.BlockUtil;
import gg.vape.utils.MathUtil;
import gg.vape.utils.RayTraceUtil;
import gg.vape.utils.RotationVectorMath;
import gg.vape.utils.TimerUtil;
import gg.vape.utils.datas.BlockData;
import gg.vape.value.BooleanValue;
import gg.vape.value.LimitValue;
import gg.vape.value.ModeValue;
import gg.vape.value.NumberValue;
import gg.vape.value.RandomValue;
import gg.vape.wrapper.impl.AxisAlignedBB;
import gg.vape.wrapper.impl.Block;
import gg.vape.wrapper.impl.BlockPos;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.EnumFacing;
import gg.vape.wrapper.impl.InventoryPlayer;
import gg.vape.wrapper.impl.Item;
import gg.vape.wrapper.impl.ItemStack;
import gg.vape.wrapper.impl.KeyBinding;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.RayTraceResult;
import gg.vape.wrapper.impl.Vec3;
import gg.vape.wrapper.impl.Vec3i;
import gg.vape.wrapper.impl.World;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class AutoAnchor
extends Mod {
    private final BooleanValue explosionItemWhitelist;
    private final BooleanValue safeAnchor;
    private BlockPos anchorPos;
    private boolean anchorPlaced;
    private final NumberValue aimSpeed;
    private int chargeRetries;
    private AnchorMacroState state;
    private final BooleanValue aimAssist;
    private EnumFacing hitFacing;
    private final RotationControlClaim rotationClaim = SharedModuleControlClaims.rotation;
    private BlockData shieldBlock;
    private int glowstoneSlot = -1;
    private static final int MAX_ANCHOR_FACE_RETRIES;
    private static final int SHIELD_MAX_TICKS;
    private final ModeOption onBindMode = new ModeOption("On bind");
    private final BooleanValue silentAim;
    private final TimerUtil timer;
    private int anchorSlot = -1;
    private static final int MAX_CHARGE_RETRIES;
    private int shieldChargeRetries;
    private int retries;
    private final RandomValue delay;
    private BlockPos baseBlockPos;
    private final ModeValue mode;
    private boolean doubleAnchorDone;
    private int shieldStep;
    private static final int MAX_PLACE_RETRIES;
    private final ModeOption onPlaceMode = new ModeOption("On place");
    private int waitRetries;
    private EnumFacing shieldFacing;
    private boolean shieldStarted;
    private boolean shieldPlaced;
    private final BooleanValue doubleAnchor;
    private FixedRotationController rotationController;
    private BlockPos shieldSupportPos;
    private int explosionSlot = -1;
    private int prevSlot = -1;
    private final LimitValue explosionItem;
    private long actionDelay;
    private static final int MAX_DETONATE_RETRIES;

    private float computeVisibilityRatio(Vec3 viewOrigin, BlockData obstruction,
                                         EntityPlayerSP player) {
        AxisAlignedBB playerBounds = player.R$src$Lgg_vape_wrapper_impl_AxisAlignedBB_$r19dfl();
        AxisAlignedBB obstructionBounds = AxisAlignedBB.create(
                obstruction.D(), obstruction.B(), obstruction.G(),
                (double)obstruction.D() + 1.0, (double)obstruction.B() + 1.0,
                (double)obstruction.G() + 1.0);
        double minX = playerBounds.getMinX();
        double minY = playerBounds.getMinY();
        double minZ = playerBounds.getMinZ();
        double maxX = playerBounds.getMaxX();
        double maxY = playerBounds.getMaxY();
        double maxZ = playerBounds.getMaxZ();
        double xStep = 1.0 / ((maxX - minX) * 2.0 + 1.0);
        double yStep = 1.0 / ((maxY - minY) * 2.0 + 1.0);
        double zStep = 1.0 / ((maxZ - minZ) * 2.0 + 1.0);
        if (xStep < 0.0 || yStep < 0.0 || zStep < 0.0) {
            return 0.0f;
        }
        double xOffset = (1.0 - Math.floor(1.0 / xStep) * xStep) / 2.0;
        double zOffset = (1.0 - Math.floor(1.0 / zStep) * zStep) / 2.0;
        int obstructedSamples = 0;
        int totalSamples = 0;
        for (double xFraction = 0.0; xFraction <= 1.0; xFraction += xStep) {
            for (double yFraction = 0.0; yFraction <= 1.0; yFraction += yStep) {
                for (double zFraction = 0.0; zFraction <= 1.0; zFraction += zStep) {
                    double sampleX = minX + (maxX - minX) * xFraction + xOffset;
                    double sampleY = minY + (maxY - minY) * yFraction;
                    double sampleZ = minZ + (maxZ - minZ) * zFraction + zOffset;
                    Vec3 samplePoint = Vec3.create(sampleX, sampleY, sampleZ);
                    RayTraceResult intercept = obstructionBounds.calculateIntercept(viewOrigin, samplePoint);
                    if (intercept != null && intercept.isNotNull()) {
                        ++obstructedSamples;
                    }
                    ++totalSamples;
                }
            }
        }
        return totalSamples > 0 ? (float)obstructedSamples / (float)totalSamples : 0.0f;
    }


    private Vec3 centerOf(BlockPos blockPos) {
        return Vec3.create((double)blockPos.getX() + 0.5, (double)blockPos.getY() + 0.5, (double)blockPos.getZ() + 0.5);
    }

    public AutoAnchor() {
        super("AutoAnchor", -8109323, Category.UTILITY, "Places, charges, and detonates a respawn anchor");
        this.mode = ModeValue.create((Object)this, "Mode", "On bind - places, charges, and detonates on keybind press\nOn place - automatically charges and detonates after you place an anchor", (ModeSelection)this.onBindMode, this.onBindMode, this.onPlaceMode);
        this.doubleAnchor = BooleanValue.create(this, "Double anchor", false, "Places a second anchor on detonation, then charges it\nWill require lower delay to work reliably");
        this.safeAnchor = BooleanValue.create(this, "Safe anchor", false, "Places a glowstone block between you and the anchor before charging\nto reduce explosion damage by providing block cover");
        this.explosionItemWhitelist = BooleanValue.create(this, "Explosion item whitelist", false, "Swaps to the first whitelisted hotbar item before exploding\ninstead of swapping back to the anchor");
        this.explosionItem = LimitValue.create(this, "autoanchor-explosionitemwhitelist", "Explosion item", LimitValue.ALLOW_LIST_COLOR, new ItemLimitData("totem of undying"));
        this.aimAssist = BooleanValue.create(this, "Aim assist", true, "Holds center of the anchor target");
        this.silentAim = BooleanValue.create(this, "Silent aim", true, "Uses Silent Aim system");
        this.aimSpeed = NumberValue.create(this, "Aim speed", "#.#", "", 1.0, 12.0, 15.0, 0.1, "Speed of aim when placing/charging anchors");
        this.delay = RandomValue.createWithDescription(this, "Delay", "#", "ms", 0.0, 50.0, 100.0, 500.0, 1.0, "Delay between each action");
        this.timer = new TimerUtil();
        this.state = AnchorMacroState.FINDING_ITEMS;
        this.addValue(this.mode, this.doubleAnchor, this.safeAnchor, this.explosionItemWhitelist, this.explosionItem, this.aimAssist, this.silentAim, this.aimSpeed, this.delay);
        this.explosionItemWhitelist.addDependentValues(this.explosionItem);
        this.aimAssist.addDependentValues(this.silentAim, this.aimSpeed);
        this.rotationClaim.setPriority(this, 8);
    }

    static {
        SHIELD_MAX_TICKS = 20;
        MAX_CHARGE_RETRIES = 4;
        MAX_ANCHOR_FACE_RETRIES = 4;
        MAX_PLACE_RETRIES = 4;
        MAX_DETONATE_RETRIES = 4;
    }

    private boolean hasRotationClaim() {
        return this.rotationClaim.isOwnedBy(this) || this.rotationClaim.acquire(this, this.silentAim.getEffectiveValue());
    }

    private boolean isShieldBlockClear() {
        if (this.shieldBlock == null) {
            return false;
        }
        Block block = Minecraft.thePlayer().getWorld().getBlockByPos(this.shieldBlock.D(), this.shieldBlock.B(), this.shieldBlock.G());
        return block.isNotNull() && !BlockUtil.u(block);
    }

    private Vec3 computeAnchorAimPoint(EntityPlayerSP entityPlayerSP) {
        if (this.anchorPos == null || this.anchorPos.isNull()) {
            return this.computeBaseAimPoint();
        }
        Vec3 aimPoint = this.findBestAnchorFace(entityPlayerSP, this.shieldBlock);
        if (aimPoint != null) {
            return aimPoint;
        }
        return this.centerOf(this.anchorPos);
    }

    private void finishSequence() {
        this.releaseRotation();
        this.restorePrevSlot();
        if (this.onPlaceMode.isSelected()) {
            this.resetState();
            this.state = AnchorMacroState.IDLE;
        } else {
            this.setEnabled(false, true);
        }
    }

    private BlockPos findRespawnAnchor() {
        RayTraceResult rayTraceResult = RotationManager.INSTANCE.getNormalReachRayTrace();
        if (rayTraceResult.isNull() || !rayTraceResult.isBlockHit()) {
            return null;
        }
        BlockPos blockPos = rayTraceResult.getBlockPos();
        if (blockPos == null || blockPos.isNull()) {
            return null;
        }
        Block block = rayTraceResult.Z$src$Lgg_vape_wrapper_impl_Block_$6x2c9a();
        if (block.isNull()) {
            return null;
        }
        String blockName = block.U();
        if (blockName == null || !blockName.toLowerCase().contains("respawn_anchor")) {
            return null;
        }
        return blockPos;
    }

    private boolean isLookingAtAnchor() {
        RayTraceResult rayTraceResult = RotationManager.INSTANCE.getNormalReachRayTrace();
        if (rayTraceResult.isNull() || !rayTraceResult.isBlockHit() || this.baseBlockPos == null || this.hitFacing == null) {
            return false;
        }
        BlockPos blockPos = rayTraceResult.getBlockPos();
        if (blockPos == null || blockPos.isNull()) {
            return false;
        }
        if (blockPos.equals(this.baseBlockPos)) {
            return true;
        }
        return this.anchorPos != null && blockPos.equals(this.anchorPos);
    }

    private Vec3 computeBaseAimPoint() {
        return this.topCenterOf(this.baseBlockPos);
    }

    private void restorePrevSlot() {
        if (this.prevSlot == -1) {
            return;
        }
        EntityPlayerSP entityPlayerSP = Minecraft.thePlayer();
        if (entityPlayerSP.isNotNull() && entityPlayerSP.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6().isNotNull()) {
            entityPlayerSP.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6().g(this.prevSlot);
        }
        this.prevSlot = -1;
    }

    private void releaseRotation() {
        if (this.rotationController != null) {
            RotationManager.INSTANCE.releaseController(this.rotationController);
            this.rotationController = null;
        }
        this.rotationClaim.release(this);
    }

    @Override
    public void onEnable() {
        this.resetState();
        if (this.onPlaceMode.isSelected()) {
            this.state = AnchorMacroState.IDLE;
        }
    }

    private boolean isRotationDone() {
        return this.rotationController == null || this.rotationController.isComplete();
    }

    private AnchorBlockHitTarget findAnchorBlockHit() {
        RayTraceResult rayTraceResult = RayTraceUtil.p(Minecraft.thePlayer().getWorld(), Minecraft.thePlayer(), false);
        if (rayTraceResult.isNull() || !rayTraceResult.isBlockHit()) {
            return null;
        }
        BlockPos blockPos = rayTraceResult.getBlockPos();
        EnumFacing hitFace = rayTraceResult.getSideHit();
        if (blockPos == null || blockPos.isNull() || hitFace == null || hitFace.isNull()) {
            return null;
        }
        return new AnchorBlockHitTarget(blockPos, hitFace);
    }

    private void notifyMissingItem(String itemName) {
        Vape.INSTANCE.getNotificationManager().show("AutoAnchor", itemName + " " + NotificationText.localize("not in hotbar"), NotificationType.WARNING, 3000L);
    }

    private boolean isAnchorFullyVisible(Vec3 viewOrigin, BlockData obstruction) {
        AxisAlignedBB obstructionBounds = AxisAlignedBB.create(
                obstruction.D(), obstruction.B(), obstruction.G(),
                (double)obstruction.D() + 1.0, (double)obstruction.B() + 1.0,
                (double)obstruction.G() + 1.0);
        int anchorX = this.anchorPos.getX();
        int anchorY = this.anchorPos.getY();
        int anchorZ = this.anchorPos.getZ();
        Vec3[] samplePoints = new Vec3[]{
                Vec3.create((double)anchorX + 0.5, (double)anchorY + 0.5, (double)anchorZ + 0.5),
                Vec3.create((double)anchorX + 0.5, (double)anchorY + 1.0, (double)anchorZ + 0.5),
                Vec3.create((double)anchorX + 0.5, anchorY, (double)anchorZ + 0.5),
                Vec3.create(anchorX, (double)anchorY + 0.5, (double)anchorZ + 0.5),
                Vec3.create((double)anchorX + 1.0, (double)anchorY + 0.5, (double)anchorZ + 0.5),
                Vec3.create((double)anchorX + 0.5, (double)anchorY + 0.5, anchorZ),
                Vec3.create((double)anchorX + 0.5, (double)anchorY + 0.5, (double)anchorZ + 1.0)};
        for (Vec3 samplePoint : samplePoints) {
            RayTraceResult rayTraceResult = obstructionBounds.calculateIntercept(viewOrigin, samplePoint);
            if (rayTraceResult != null && !rayTraceResult.isNull()) continue;
            return false;
        }
        return true;
    }

    private void aimAt(Vec3 target) {
        if (!this.aimAssist.getEffectiveValue().booleanValue() || target == null || target.isNull()) {
            return;
        }
        if (this.rotationController == null) {
            if (this.silentAim.getEffectiveValue().booleanValue()) {
                AdaptiveRotationController adaptiveRotationController = new AdaptiveRotationController();
                adaptiveRotationController.setNormalizeTargetYaw(false);
                this.rotationController = adaptiveRotationController;
            } else {
                PointRotationController pointRotationController = new PointRotationController(target);
                pointRotationController.setNormalizeYaw(false);
                this.rotationController = pointRotationController;
            }
            this.rotationController.setRetainAfterCompletion(true);
            this.rotationController.setClampStepToRemaining(true);
            this.rotationController.setTolerance(0.0f);
            this.rotationController.setSpeed(((Double)this.aimSpeed.getValue()).floatValue());
            this.rotationController.setAngleBasedAcceleration(true);
            this.rotationController.setScaleAxesProportionally(false);
            this.rotationController.setLinearAcceleration(true);
            this.rotationController.setCubicAcceleration(true);
        }
        this.rotationController.setSpeed(((Double)this.aimSpeed.getValue()).floatValue());
        if (this.rotationController instanceof PointRotationController) {
            ((PointRotationController)this.rotationController).setTarget(target);
        } else if (this.rotationController instanceof AdaptiveRotationController) {
            ((AdaptiveRotationController)this.rotationController).setTarget(target);
        }
        RotationManager.INSTANCE.setController(this.rotationController);
    }

    private int findHotbarSlot(String itemNameFragment) {
        InventoryPlayer inventoryPlayer = Minecraft.thePlayer().V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6();
        for (int slot = 0; slot < 9; ++slot) {
            String itemName;
            Item item;
            ItemStack itemStack = inventoryPlayer.c(slot);
            if (itemStack.isNull() || (item = itemStack.getItem()).isNull() || (itemName = item.A()) == null || !itemName.toLowerCase().contains(itemNameFragment)) continue;
            return slot;
        }
        return -1;
    }

    @Override
    public void onDisable() {
        this.releaseRotation();
        this.restorePrevSlot();
        this.resetState();
    }

    private void placeShield(EntityPlayerSP player, InventoryPlayer inventory) {
        if (!this.shieldStarted) {
            this.shieldStarted = true;
            ItemStack heldStack = inventory.c(this.glowstoneSlot);
            if (heldStack.isNull() || heldStack.t() < 2) {
                this.state = AnchorMacroState.CHARGING_ANCHOR;
                this.resetActionTimer();
                this.retries = 0;
                return;
            }
            AnchorObstructionPlacementCandidate candidate = this.findShieldCandidate(player);
            if (candidate == null) {
                this.state = AnchorMacroState.CHARGING_ANCHOR;
                this.resetActionTimer();
                this.retries = 0;
                return;
            }
            this.shieldBlock = candidate.getBlock();
            this.shieldSupportPos = BlockPos.d(candidate.getObstructionBlock());
            this.shieldFacing = candidate.getFacing();
            this.shieldPlaced = false;
            this.shieldChargeRetries = 0;
            this.shieldStep = 0;
        }
        if (this.shieldBlock == null || this.shieldSupportPos == null || this.shieldFacing == null) {
            this.state = AnchorMacroState.CHARGING_ANCHOR;
            this.resetActionTimer();
            this.retries = 0;
            return;
        }
        Vec3 aimPoint = this.faceCenter(this.shieldSupportPos, this.shieldFacing);
        this.aimAt(aimPoint);
        if (!this.timer.hasTimeElapsed(this.actionDelay)) {
            return;
        }
        if (this.shieldStep < 1) {
            ++this.shieldStep;
            return;
        }
        if (!this.shieldPlaced) {
            RayTraceResult rayTraceResult = RotationManager.INSTANCE.getNormalReachRayTrace();
            if (rayTraceResult.isNull() || !rayTraceResult.isBlockHit()) {
                if (++this.retries > 4) {
                    this.state = AnchorMacroState.CHARGING_ANCHOR;
                    this.resetActionTimer();
                    this.retries = 0;
                }
                return;
            }
            inventory.g(this.glowstoneSlot);
            this.rightClick();
            this.shieldPlaced = true;
            this.shieldChargeRetries = 0;
            return;
        }
        if (!this.isShieldBlockClear()) {
            if (++this.shieldChargeRetries > 4) {
                this.state = AnchorMacroState.CHARGING_ANCHOR;
                this.resetActionTimer();
                this.retries = 0;
            }
            return;
        }
        this.state = AnchorMacroState.CHARGING_ANCHOR;
        this.resetActionTimer();
        this.retries = 0;
    }

    private boolean isHoldingAnchor(InventoryPlayer inventory) {
        ItemStack itemStack = inventory.c(inventory.v());
        if (itemStack.isNull()) {
            return false;
        }
        Item item = itemStack.getItem();
        if (item.isNull()) {
            return false;
        }
        String itemName = item.A();
        return itemName != null && itemName.toLowerCase().contains("respawn_anchor");
    }

    private Vec3 findBestAnchorFace(EntityPlayerSP player, BlockData obstruction) {
        if (this.anchorPos == null || this.anchorPos.isNull()) {
            return null;
        }
        World world = player.getWorld();
        if (world.isNull()) {
            return null;
        }
        int anchorX = this.anchorPos.getX();
        int anchorY = this.anchorPos.getY();
        int anchorZ = this.anchorPos.getZ();
        Vec3 eyePosition = Vec3.create(player.z(), player.N() + (double)player.X(), player.h());
        Vec3[] candidatePoints = new Vec3[]{
                Vec3.create((double)anchorX + 0.5, (double)anchorY + 1.0, (double)anchorZ + 0.5),
                Vec3.create((double)anchorX + 0.5, anchorY, (double)anchorZ + 0.5),
                Vec3.create(anchorX, (double)anchorY + 0.5, (double)anchorZ + 0.5),
                Vec3.create((double)anchorX + 1.0, (double)anchorY + 0.5, (double)anchorZ + 0.5),
                Vec3.create((double)anchorX + 0.5, (double)anchorY + 0.5, anchorZ),
                Vec3.create((double)anchorX + 0.5, (double)anchorY + 0.5, (double)anchorZ + 1.0),
                Vec3.create((double)anchorX + 0.25, (double)anchorY + 1.0, (double)anchorZ + 0.5),
                Vec3.create((double)anchorX + 0.75, (double)anchorY + 1.0, (double)anchorZ + 0.5),
                Vec3.create(anchorX, (double)anchorY + 0.25, (double)anchorZ + 0.5),
                Vec3.create((double)anchorX + 1.0, (double)anchorY + 0.75, (double)anchorZ + 0.5),
                Vec3.create((double)anchorX + 0.5, (double)anchorY + 0.25, anchorZ),
                Vec3.create((double)anchorX + 0.5, (double)anchorY + 0.75, (double)anchorZ + 1.0),
                Vec3.create((double)anchorX + 0.25, anchorY, (double)anchorZ + 0.5),
                Vec3.create((double)anchorX + 0.75, anchorY, (double)anchorZ + 0.5)};
        AxisAlignedBB obstructionBounds = null;
        if (obstruction != null) {
            obstructionBounds = AxisAlignedBB.create(
                    obstruction.D(), obstruction.B(), obstruction.G(),
                    (double)obstruction.D() + 1.0, (double)obstruction.B() + 1.0,
                    (double)obstruction.G() + 1.0);
        }
        float currentYaw = player.J();
        float currentPitch = player.V();
        double reachDistance = Minecraft.playerController().N();
        Vec3 bestPoint = null;
        double bestAngularDistance = Double.MAX_VALUE;
        for (Vec3 candidatePoint : candidatePoints) {
            if (obstructionBounds != null) {
                RayTraceResult obstructionHit = obstructionBounds.calculateIntercept(eyePosition, candidatePoint);
                if (obstructionHit != null && obstructionHit.isNotNull()) continue;
            }
            double candidateDistance = eyePosition.distanceTo(candidatePoint);
            if (candidateDistance <= 1.0E-4) continue;
            Vec3 direction = candidatePoint.q(eyePosition);
            double reachScale = reachDistance / candidateDistance;
            Vec3 reachPoint = eyePosition.addVector(
                    direction.getX() * reachScale,
                    direction.getY() * reachScale,
                    direction.getZ() * reachScale);
            RayTraceResult rayTraceResult = RayTraceUtil.b(
                    eyePosition, reachPoint, world, player, false, false, false, null);
            if (rayTraceResult == null || rayTraceResult.isNull() || !rayTraceResult.isBlockHit()) continue;
            BlockPos hitPos = rayTraceResult.getBlockPos();
            if (hitPos == null || hitPos.isNull() || hitPos.getX() != anchorX
                    || hitPos.getY() != anchorY || hitPos.getZ() != anchorZ) continue;
            RotationAngles targetRotation = RotationVectorMath.H(
                    eyePosition, candidatePoint, currentYaw, false);
            float yawDelta = MathUtil.wrapAngleTo180(targetRotation.getYaw() - currentYaw);
            float pitchDelta = targetRotation.getPitch() - currentPitch;
            double angularDistance = Math.sqrt(yawDelta * yawDelta + pitchDelta * pitchDelta);
            if (angularDistance >= bestAngularDistance) continue;
            bestAngularDistance = angularDistance;
            bestPoint = candidatePoint;
        }
        return bestPoint;
    }

    private Vec3 topCenterOf(BlockPos blockPos) {
        return Vec3.create((double)blockPos.getX() + 0.5, (double)blockPos.getY() + 1.0, (double)blockPos.getZ() + 0.5);
    }

    private int findExplosionSlot(InventoryPlayer inventory) {
        int whitelistedSlot;
        if (this.explosionItemWhitelist.getEffectiveValue().booleanValue() && (whitelistedSlot = this.findLimitSlot(this.explosionItem, inventory)) != -1) {
            return whitelistedSlot;
        }
        return this.anchorSlot;
    }

    private Vec3 faceCenter(BlockPos blockPos, EnumFacing facing) {
        Vec3i direction = facing.getDirectionVector();
        return Vec3.create((double)blockPos.getX() + 0.5 + (double)direction.getX() * 0.5, (double)blockPos.getY() + 0.5 + (double)direction.getY() * 0.5, (double)blockPos.getZ() + 0.5 + (double)direction.getZ() * 0.5);
    }

    private AnchorMacroState nextChargeState() {
        if (this.safeAnchor.getEffectiveValue().booleanValue()) {
            return AnchorMacroState.PLACING_SHIELD;
        }
        return AnchorMacroState.CHARGING_ANCHOR;
    }

    private void resetActionTimer() {
        this.timer.reset();
        this.actionDelay = (long)this.delay.getRandomValue();
    }

    private boolean isLookingAtBaseFace() {
        RayTraceResult rayTraceResult = RotationManager.INSTANCE.getNormalReachRayTrace();
        if (rayTraceResult.isNull() || !rayTraceResult.isBlockHit() || this.baseBlockPos == null || this.hitFacing == null) {
            return false;
        }
        BlockPos blockPos = rayTraceResult.getBlockPos();
        EnumFacing hitFace = rayTraceResult.getSideHit();
        if (blockPos == null || blockPos.isNull() || hitFace == null || hitFace.isNull()) {
            return false;
        }
        return blockPos.equals(this.baseBlockPos) && hitFace.equals(this.hitFacing);
    }

    @EventHandler
    public void onTick(EventPreTick eventPreTick) {
        EntityPlayerSP player = eventPreTick.getThePlayer();
        if (player.isNull()) {
            return;
        }
        InventoryPlayer inventory = player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6();
        if (inventory.isNull()) {
            return;
        }
        if (this.state == AnchorMacroState.IDLE) {
            if (this.onBindMode.isSelected()) {
                this.setEnabled(false, true);
            }
            return;
        }
        if (!this.aimAssist.getEffectiveValue().booleanValue() && this.rotationController != null) {
            this.releaseRotation();
        }
        if (this.aimAssist.getEffectiveValue().booleanValue() && !this.hasRotationClaim()) {
            return;
        }
        switch (this.state) {
            case FINDING_ITEMS: {
                this.anchorSlot = this.findHotbarSlot("respawn_anchor");
                this.glowstoneSlot = this.findHotbarSlot("glowstone");
                if (this.anchorSlot == -1 || this.glowstoneSlot == -1) {
                    if (this.anchorSlot == -1) {
                        this.notifyMissingItem("Respawn anchor");
                    }
                    if (this.glowstoneSlot == -1) {
                        this.notifyMissingItem("Glowstone");
                    }
                    this.setEnabled(false, true);
                    return;
                }
                this.prevSlot = inventory.v();
                this.state = AnchorMacroState.PLACING_ANCHOR;
                break;
            }
            case PLACING_ANCHOR: {
                if (this.anchorPos == null || this.baseBlockPos == null || this.hitFacing == null) {
                    if (this.isLookingAtRespawnAnchor()) {
                        RayTraceResult rayTraceResult = RotationManager.INSTANCE.getNormalReachRayTrace();
                        this.baseBlockPos = this.anchorPos = rayTraceResult.getBlockPos();
                        this.state = this.nextChargeState();
                        this.resetActionTimer();
                        this.retries = 0;
                        break;
                    }
                    AnchorBlockHitTarget hitTarget = this.findAnchorBlockHit();
                    if (hitTarget == null) {
                        if (++this.retries <= 20) break;
                        this.state = AnchorMacroState.FINISH;
                        break;
                    }
                    this.baseBlockPos = hitTarget.getHitPos();
                    this.hitFacing = hitTarget.getHitFace();
                    this.anchorPos = this.baseBlockPos.offset(this.hitFacing);
                    this.retries = 0;
                }
                this.aimAt(this.computeBaseAimPoint());
                if (!this.anchorPlaced && !this.isLookingAtAnchor()) {
                    if (++this.retries <= 20) break;
                    this.state = AnchorMacroState.FINISH;
                    break;
                }
                if (!this.anchorPlaced) {
                    inventory.g(this.anchorSlot);
                    this.rightClick();
                    this.anchorPlaced = true;
                    this.waitRetries = 0;
                    break;
                }
                if (!this.isAnchorPresent()) {
                    if (++this.waitRetries <= 4) break;
                    this.anchorPlaced = false;
                    this.waitRetries = 0;
                    if (++this.chargeRetries <= 4) break;
                    this.state = AnchorMacroState.FINISH;
                    break;
                }
                this.anchorPlaced = false;
                this.waitRetries = 0;
                this.chargeRetries = 0;
                this.state = this.nextChargeState();
                this.resetActionTimer();
                this.retries = 0;
                break;
            }
            case WAITING_FOR_ANCHOR: {
                if (this.isAnchorPresent()) {
                    this.state = this.nextChargeState();
                    this.resetActionTimer();
                    this.retries = 0;
                    break;
                }
                BlockPos locatedAnchor = this.findRespawnAnchor();
                if (locatedAnchor != null && locatedAnchor.isNotNull()) {
                    this.anchorPos = locatedAnchor;
                    this.state = this.nextChargeState();
                    this.resetActionTimer();
                    this.retries = 0;
                    break;
                }
                if (++this.waitRetries <= 4) break;
                this.finishSequence();
                break;
            }
            case PLACING_SHIELD: {
                this.placeShield(player, inventory);
                break;
            }
            case CHARGING_ANCHOR: {
                this.aimAt(this.computeAnchorAimPoint(player));
                if (!this.timer.hasTimeElapsed(this.actionDelay)) break;
                if (!this.isLookingAtRespawnAnchor()) {
                    ++this.retries;
                    if (this.retries <= 4) break;
                    this.state = AnchorMacroState.FINISH;
                    break;
                }
                inventory.g(this.glowstoneSlot);
                this.rightClick();
                this.state = AnchorMacroState.SWAPPING_TO_EXPLOSION_ITEM;
                this.resetActionTimer();
                this.retries = 0;
                break;
            }
            case SWAPPING_TO_EXPLOSION_ITEM: {
                this.aimAt(this.computeAnchorAimPoint(player));
                if (!this.timer.hasTimeElapsed(this.actionDelay)) break;
                if (!this.isLookingAtRespawnAnchor()) {
                    ++this.retries;
                    if (this.retries <= 4) break;
                    this.state = AnchorMacroState.FINISH;
                    break;
                }
                this.explosionSlot = this.findExplosionSlot(inventory);
                inventory.g(this.explosionSlot);
                this.state = AnchorMacroState.DETONATING_ANCHOR;
                this.resetActionTimer();
                this.retries = 0;
                break;
            }
            case DETONATING_ANCHOR: {
                this.aimAt(this.computeAnchorAimPoint(player));
                if (!this.timer.hasTimeElapsed(this.actionDelay)) break;
                if (!this.isLookingAtRespawnAnchor()) {
                    ++this.retries;
                    if (this.retries <= 4) break;
                    this.state = AnchorMacroState.FINISH;
                    break;
                }
                this.rightClick();
                if (this.doubleAnchor.getEffectiveValue().booleanValue() && !this.doubleAnchorDone) {
                    this.aimAt(this.computeAnchorAimPoint(player));
                    this.rightClick();
                    this.doubleAnchorDone = true;
                    this.state = AnchorMacroState.CHARGING_ANCHOR;
                    this.resetActionTimer();
                    this.retries = 0;
                    break;
                }
                this.state = AnchorMacroState.FINISH;
                break;
            }
            case FINISH: {
                this.finishSequence();
            }
        }
    }

    private int findLimitSlot(LimitValue limitValue, InventoryPlayer inventory) {
        for (int slot = 0; slot < 9; ++slot) {
            ItemStack itemStack = inventory.c(slot);
            if (itemStack.isNull()) continue;
            for (Object value : (List<?>)limitValue.getValue()) {
                ItemLimitData itemLimitData = (ItemLimitData)value;
                if (!itemLimitData.matches(itemStack)) continue;
                return slot;
            }
        }
        return -1;
    }

    @Override
    public boolean isRequiresBind() {
        return this.onBindMode.isSelected();
    }

    @EventHandler
    public void onRightClick(EventRightClickMouse eventRightClickMouse) {
        if (!this.onPlaceMode.isSelected() || this.state != AnchorMacroState.IDLE) {
            return;
        }
        EntityPlayerSP player = Minecraft.thePlayer();
        if (player.isNull()) {
            return;
        }
        InventoryPlayer inventory = player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6();
        if (inventory.isNull()) {
            return;
        }
        if (!this.isHoldingAnchor(inventory)) {
            return;
        }
        RayTraceResult rayTraceResult = RotationManager.INSTANCE.getNormalReachRayTrace();
        if (rayTraceResult.isNull() || !rayTraceResult.isBlockHit()) {
            return;
        }
        BlockPos hitPos = rayTraceResult.getBlockPos();
        EnumFacing hitFace = rayTraceResult.getSideHit();
        if (hitPos == null || hitPos.isNull() || hitFace == null || hitFace.isNull()) {
            return;
        }
        this.glowstoneSlot = this.findHotbarSlot("glowstone");
        if (this.glowstoneSlot == -1) {
            this.notifyMissingItem("Glowstone");
            return;
        }
        this.anchorSlot = inventory.v();
        this.baseBlockPos = hitPos;
        this.hitFacing = hitFace;
        this.anchorPos = hitPos.offset(hitFace);
        this.prevSlot = inventory.v();
        this.state = AnchorMacroState.WAITING_FOR_ANCHOR;
        this.retries = 0;
        this.waitRetries = 0;
    }

    private void rightClick() {
        KeyBinding keyBinding = Minecraft.gameSettings().b$src$Lgg_vape_wrapper_impl_KeyBinding_$1yi3362();
        KeyBinding.setKeyBindState(keyBinding, true);
        KeyBinding.onTick(keyBinding);
        KeyBinding.setKeyBindState(keyBinding, false);
    }

    private boolean isLookingAtRespawnAnchor() {
        RayTraceResult rayTraceResult = RotationManager.INSTANCE.getNormalReachRayTrace();
        if (rayTraceResult.isNull() || !rayTraceResult.isBlockHit()) {
            return false;
        }
        String blockName = rayTraceResult.Z$src$Lgg_vape_wrapper_impl_Block_$6x2c9a().U();
        return blockName != null && blockName.toLowerCase().contains("respawn_anchor");
    }

    private void resetState() {
        this.state = AnchorMacroState.FINDING_ITEMS;
        this.anchorSlot = -1;
        this.glowstoneSlot = -1;
        this.explosionSlot = -1;
        this.retries = 0;
        this.doubleAnchorDone = false;
        this.anchorPlaced = false;
        this.waitRetries = 0;
        this.chargeRetries = 0;
        this.baseBlockPos = null;
        this.hitFacing = null;
        this.anchorPos = null;
        this.shieldBlock = null;
        this.shieldSupportPos = null;
        this.shieldFacing = null;
        this.shieldPlaced = false;
        this.shieldChargeRetries = 0;
        this.shieldStarted = false;
        this.shieldStep = 0;
    }

    private AnchorObstructionPlacementCandidate findShieldCandidate(EntityPlayerSP player) {
        if (this.anchorPos == null) {
            return null;
        }
        World world = player.getWorld();
        if (world.isNull()) {
            return null;
        }
        double playerX = player.z();
        double playerY = player.N();
        double playerZ = player.h();
        Vec3 eyePosition = Vec3.create(playerX, playerY + (double)player.X(), playerZ);
        Vec3 anchorAimPoint = this.findBestAnchorFace(player, null);
        if (anchorAimPoint == null) {
            return null;
        }
        double deltaX = anchorAimPoint.getX() - playerX;
        double deltaY = anchorAimPoint.getY() - playerY;
        double deltaZ = anchorAimPoint.getZ() - playerZ;
        double distance = Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
        if (distance < 1.5) {
            return null;
        }
        double directionX = deltaX / distance;
        double directionY = deltaY / distance;
        double directionZ = deltaZ / distance;
        HashSet<Long> visitedPositions = new HashSet<Long>();
        ArrayList<BlockData> candidateBlocks = new ArrayList<BlockData>();
        for (double offset = 0.8; offset < distance - 0.5; offset += 0.4) {
            int blockX = (int)Math.floor(playerX + directionX * offset);
            int blockY = (int)Math.floor(playerY + directionY * offset);
            int blockZ = (int)Math.floor(playerZ + directionZ * offset);
            long packedPosition = ((long)blockX & 0x3FFFFFFL) << 38
                    | ((long)blockY & 0xFFFL) << 26
                    | (long)blockZ & 0x3FFFFFFL;
            if (!visitedPositions.add(packedPosition)
                    || blockX == this.anchorPos.getX() && blockY == this.anchorPos.getY()
                    && blockZ == this.anchorPos.getZ()) continue;
            candidateBlocks.add(new BlockData(blockX, blockY, blockZ));
        }
        AnchorObstructionPlacementCandidate bestCandidate = null;
        float bestVisibilityRatio = 0.0f;
        AxisAlignedBB playerBounds = player.R$src$Lgg_vape_wrapper_impl_AxisAlignedBB_$r19dfl();
        for (BlockData candidateBlock : candidateBlocks) {
            Block block = world.getBlockByPos(candidateBlock.D(), candidateBlock.B(), candidateBlock.G());
            if (block.isNull() || !BlockUtil.u(block)
                    || !ClutchPlacementPathUtils.isPlacementSpaceClear(world, player, candidateBlock)) continue;
            AxisAlignedBB candidateBounds = AxisAlignedBB.create(
                    candidateBlock.D(), candidateBlock.B(), candidateBlock.G(),
                    (double)candidateBlock.D() + 1.0, (double)candidateBlock.B() + 1.0,
                    (double)candidateBlock.G() + 1.0);
            if (playerBounds.isNotNull() && candidateBounds.isNotNull()
                    && playerBounds.intersects(candidateBounds)) continue;
            EnumFacing[] supportFacing = new EnumFacing[1];
            BlockData[] supportBlock = new BlockData[1];
            if (!this.findAdjacentSupport(world, candidateBlock, supportFacing, supportBlock)
                    || this.isAnchorFullyVisible(eyePosition, candidateBlock)) continue;
            Vec3 visibleAimPoint = this.findBestAnchorFace(player, candidateBlock);
            if (visibleAimPoint == null) continue;
            float visibilityRatio = this.computeVisibilityRatio(visibleAimPoint, candidateBlock, player);
            if (visibilityRatio >= 0.15f) {
                return new AnchorObstructionPlacementCandidate(
                        candidateBlock, supportBlock[0], supportFacing[0]);
            }
            if (visibilityRatio <= bestVisibilityRatio) continue;
            bestVisibilityRatio = visibilityRatio;
            bestCandidate = new AnchorObstructionPlacementCandidate(
                    candidateBlock, supportBlock[0], supportFacing[0]);
        }
        return bestCandidate;
    }

    private boolean isAnchorPresent() {
        if (this.anchorPos == null || this.anchorPos.isNull()) {
            return false;
        }
        String blockName = Minecraft.thePlayer().getWorld().getBlockByPos(this.anchorPos.getX(), this.anchorPos.getY(), this.anchorPos.getZ()).U();
        return blockName != null && blockName.toLowerCase().contains("respawn_anchor");
    }

    private boolean findAdjacentSupport(World world, BlockData candidateBlock,
                                        EnumFacing[] supportFacing, BlockData[] supportBlock) {
        int[][] offsets = new int[][]{{0, -1, 0}, {0, 1, 0}, {-1, 0, 0}, {1, 0, 0}, {0, 0, -1}, {0, 0, 1}};
        EnumFacing[] facings = new EnumFacing[]{EnumFacing.F$src$Lgg_vape_wrapper_impl_EnumFacing_$glfxl5(), EnumFacing.B(), EnumFacing.g$src$Lgg_vape_wrapper_impl_EnumFacing_$1ii8mzu(), EnumFacing.X(), EnumFacing.M(), EnumFacing.w()};
        for (int index = 0; index < offsets.length; ++index) {
            int supportX = candidateBlock.D() + offsets[index][0];
            int supportY = candidateBlock.B() + offsets[index][1];
            int supportZ = candidateBlock.G() + offsets[index][2];
            Block block = world.getBlockByPos(supportX, supportY, supportZ);
            if (!block.isNotNull() || !BlockUtil.b(block) || BlockUtil.u(block)) continue;
            supportBlock[0] = new BlockData(supportX, supportY, supportZ);
            supportFacing[0] = facings[index];
            return true;
        }
        return false;
    }
}
