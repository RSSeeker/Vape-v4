package gg.vape.module.utility;

import gg.vape.Vape;
import gg.vape.config.ClientSettings;
import gg.vape.event.EventHandler;
import gg.vape.event.impl.EventKeyPress;
import gg.vape.event.impl.EventPreTick;
import gg.vape.input.MovementInputLock;
import gg.vape.mapping.MappedClasses;
import gg.vape.module.Category;
import gg.vape.module.Mod;
import gg.vape.module.blatant.blockin.BlockPlacementPathSegmentState;
import gg.vape.module.control.SharedModuleControlClaims;
import gg.vape.module.utility.clutch.BlockPathSearchStrategy;
import gg.vape.module.utility.clutch.BlockPlacementNode;
import gg.vape.module.utility.clutch.ClutchBlockPlacementPathSearchStrategy;
import gg.vape.module.utility.clutch.ClutchPlacementPathUtils;
import gg.vape.module.utility.clutch.ClutchSolidBlockPathSearchStrategy;
import gg.vape.module.utility.clutch.PlacementTarget;
import gg.vape.movement.PlayerMovementTaskManager;
import gg.vape.movement.TargetPositionMovementTask;
import gg.vape.notification.NotificationText;
import gg.vape.notification.NotificationType;
import gg.vape.rotation.AdaptiveRotationController;
import gg.vape.rotation.FixedRotationController;
import gg.vape.rotation.PointRotationController;
import gg.vape.rotation.RotationControlClaim;
import gg.vape.rotation.RotationManager;
import gg.vape.unmap.ItemLimitData;
import gg.vape.unmap.ModeOption;
import gg.vape.unmap.ModeSelection;
import gg.vape.utils.BlockUtil;
import gg.vape.utils.MathUtil;
import gg.vape.utils.TimerUtil;
import gg.vape.utils.datas.BlockCoordinate;
import gg.vape.utils.datas.BlockData;
import gg.vape.utils.datas.DirectionalPosition;
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
import gg.vape.wrapper.impl.ForgeVersion;
import gg.vape.wrapper.impl.Item;
import gg.vape.wrapper.impl.ItemStack;
import gg.vape.wrapper.impl.KeyBinding;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.RayTraceResult;
import gg.vape.wrapper.impl.Vec3;
import gg.vape.wrapper.impl.World;
import gg.vape.wrapper.impl.WorldClient;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Stack;
import java.util.Vector;

public class BlockIn
extends Mod {
    private final BooleanValue keepSneak;
    private final ArrayList<String> blockPriorityNames;
    private final MovementInputLock movementLock;
    private TargetPositionMovementTask movementTask;
    private final ModeOption lowestCostMode = new ModeOption("Lowest cost");
    private final NumberValue aimSpeed;
    private final ArrayList<BlockPlacementNode> placementNodes;
    private final BooleanValue returnToLastSlot;
    private FixedRotationController rotationController;
    private final RotationControlClaim rotationClaim;
    private final int[][] faceOffsets;
    private int previousSlot = -1;
    private final ModeOption hardestMode = new ModeOption("Hardest");
    private final BooleanValue useBlacklist;
    private final RandomValue placeDelay;
    private PlacementTarget currentTarget;
    private final TimerUtil clickTimer;
    private final EnumFacing[] horizontalFacings = EnumFacing.c$src$ALgg_vape_wrapper_impl_EnumFacing_$1i3g4ft();
    private final BooleanValue silentAim;
    private final BooleanValue sneak;
    private final TimerUtil aimTimer;
    private boolean paused;
    private final ModeValue blockPriorityMode;
    private boolean sneaking;
    private final BooleanValue bedFinder;
    private final EnumFacing[] verticalFacings = EnumFacing.t();
    private final LimitValue blockBlacklist;
    private int blockSlot = -1;

    @Override
    public boolean isRequiresBind() {
        return true;
    }

    private boolean isUsableBlockSlot(EntityPlayerSP player, World world, int slot) {
        if (player.isNull() || world.isNull()) {
            return false;
        }
        ItemStack itemStack = player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6().c(slot);
        if (itemStack.isNull() || itemStack.getItem().isNull() || itemStack.t() <= 0) {
            return false;
        }
        return this.isAllowedBlock(itemStack);
    }


    public BlockIn() {
        super("Block-In", 8191953, Category.WORLD, "Automatically blocks you in by building walls around you");
        this.faceOffsets = new int[][]{null, null, {5, 4}, {5, 4}, {2, 3}, {2, 3}};
        this.silentAim = BooleanValue.create(this, "Silent aim", false, "Uses Silent Aim system");
        this.sneak = BooleanValue.create(this, "Sneak", false, "Sneak when placing blocks");
        this.keepSneak = BooleanValue.create(this, "Keep sneak", true, "Keeps sneak held after placing blocks.\nMust press sneak again to release");
        this.bedFinder = BooleanValue.create(this, "Bed finder", true, "Finds nearby beds to block in towards");
        this.blockPriorityMode = ModeValue.create((Object)this, "Block priority", "Lowest cost - Prioritizes by block type: Wool > Stone > Wood > Sandstone > Stained Clay > End Stone > Obsidian\nHardest - uses the block hardness value.", (ModeSelection)this.lowestCostMode, this.lowestCostMode, this.hardestMode);
        this.placeDelay = RandomValue.createWithDescription(this, "Place delay", "#", "ms", 0.0, 0.0, 30.0, 250.0, 1.0, "Delay between each block placement");
        this.aimSpeed = NumberValue.create(this, "Aim speed", "#.#", "", 1.0, 12.0, 25.0, 0.1, "Speed of aim when placing blocks");
        this.returnToLastSlot = BooleanValue.create(this, "Return to last slot", true, "Returns hotbar to previous slot when completed");
        this.useBlacklist = BooleanValue.create(this, "Use blacklist", true, "BlockIn will not use these blocks");
        this.blockBlacklist = LimitValue.create(this, "blockin-blacklist", "Block blacklist", LimitValue.BLOCK_LIST_COLOR, ItemLimitData.DEFAULT_BLOCK_BLACKLIST);
        this.blockPriorityNames = new ArrayList<String>(Arrays.asList("Wool", "Stone", "Wood Planks", "Red Sandstone", "Stained Clay", "End Stone", "Obsidian"));
        this.rotationClaim = SharedModuleControlClaims.rotation;
        this.movementLock = SharedModuleControlClaims.movementInput;
        this.clickTimer = new TimerUtil();
        this.aimTimer = new TimerUtil();
        this.placementNodes = new ArrayList();
        this.useBlacklist.addDependentValues(this.blockBlacklist);
        this.sneak.addDependentValues(this.keepSneak);
        this.addValue(this.aimSpeed, this.placeDelay, this.blockPriorityMode, this.silentAim, this.sneak, this.keepSneak, this.bedFinder, this.returnToLastSlot, this.useBlacklist, this.blockBlacklist);
        this.rotationClaim.setPriority(this, 9);
    }

    @EventHandler
    public void onKeyPress(EventKeyPress eventKeyPress) {
        if (!this.sneak.getEffectiveValue().booleanValue()) {
            return;
        }
        KeyBinding sneakKey = Minecraft.gameSettings().d$src$Lgg_vape_wrapper_impl_KeyBinding_$adn2z0();
        int keyCode = ClientSettings.getPlatformKeyCode(sneakKey);
        if (keyCode == eventKeyPress.getKey()) {
            eventKeyPress.setCancelled(true);
        }
    }

    @Override
    public void onDisable() {
        if (this.sneaking && !this.keepSneak.getEffectiveValue().booleanValue()) {
            this.sneaking = false;
            KeyBinding sneakKey = Minecraft.gameSettings().d$src$Lgg_vape_wrapper_impl_KeyBinding_$adn2z0();
            sneakKey.Z();
        }
    }

    private Stack<BlockPlacementNode> findBestPath(BlockData startBlock, EnumFacing facing, World world, BlockPathSearchStrategy searchStrategy) {
        Stack<BlockPlacementNode> bestPath = null;
        int bestCost = Integer.MAX_VALUE;
        for (int offset = 0; offset < 4; ++offset) {
            int facingIndex = (facing.c() + offset) % 4;
            EnumFacing candidateFacing = this.horizontalFacings[facingIndex];
            Stack<BlockPlacementNode> candidatePath = ClutchPlacementPathUtils.findBlockPlacementPath(startBlock, null, facing, searchStrategy, 0);
            if (candidateFacing.equals(facing.getOpposite()) || candidatePath == null || candidatePath.isEmpty()) continue;
            int candidateCost = this.computePathCost(world, candidatePath);
            if (bestPath != null && candidateCost >= bestCost) continue;
            bestCost = candidateCost;
            bestPath = candidatePath;
        }
        if (bestPath != null && !bestPath.isEmpty()) {
            for (int index = bestPath.size() - 1; index >= 2; --index) {
                BlockPlacementNode currentNode = bestPath.get(index);
                BlockPlacementNode previousNode = bestPath.get(index - 1);
                BlockPlacementNode precedingNode = bestPath.get(index - 2);
                if (!currentNode.hasSupportBlock() || previousNode.hasSupportBlock() || !precedingNode.hasSupportBlock()) continue;
                currentNode.blockBaseFacing(previousNode.baseFacing);
                precedingNode.blockSupportFacing(precedingNode.supportFacing);
            }
        }
        return bestPath;
    }

    private int[] faceOffsetsFor(EnumFacing enumFacing) {
        return this.faceOffsets[enumFacing.Y()];
    }

    private void reset() {
        if (this.returnToLastSlot.getEffectiveValue().booleanValue() && this.previousSlot != -1) {
            EntityPlayerSP localPlayer = Minecraft.thePlayer();
            if (localPlayer.isNotNull() && this.previousSlot != localPlayer.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6().v()) {
                localPlayer.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6().g(this.previousSlot);
            }
            this.previousSlot = -1;
        }
        this.currentTarget = null;
        this.blockSlot = -1;
        this.placementNodes.removeIf(BlockPlacementNode::clearCandidatePaths);
        this.clearMovementTask();
        this.updateRotationController();
        if (this.isEnabled() && !this.paused) {
            super.setEnabled(false);
        }
    }

    @EventHandler
    public void onTick(EventPreTick eventPreTick) {
        EntityPlayerSP localPlayer = eventPreTick.getThePlayer();
        WorldClient world = eventPreTick.getWorld();
        if (localPlayer.isNull() || world.isNull()) {
            this.reset();
            return;
        }
        if (this.paused || this.rotationClaim.isBlockedFor(this)) {
            this.reset();
            return;
        }
        if (!this.rotationClaim.isOwnedBy(this) && !this.rotationClaim.acquire(this, this.silentAim.getEffectiveValue())) {
            return;
        }
        if (this.blockSlot == -1 || !this.isUsableBlockSlot(localPlayer, world, this.blockSlot)) {
            this.blockSlot = this.selectBestBlockSlot(localPlayer, world);
        }
        if (this.blockSlot != -1 && !this.paused && this.placementNodes.isEmpty()) {
            this.buildPlacementNodes(localPlayer, world);
        }
        if (this.blockSlot != -1 && !this.placementNodes.isEmpty() && !this.paused) {
            BlockPlacementNode firstNode = this.placementNodes.get(0);
            BlockData enclosureBlock = firstNode.baseBlock;
            if (!ClutchPlacementPathUtils.isPlacementSpaceClear(world, localPlayer, enclosureBlock)) {
                this.reset();
                Vape.INSTANCE.getNotificationManager().show("Block-In Disabled", "Entity in the way!", NotificationType.WARNING, 2000L);
                return;
            }
            localPlayer.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6().g(this.blockSlot);
            boolean standingInEnclosure = BlockUtil.e(localPlayer, enclosureBlock);
            if (!standingInEnclosure) {
                double targetX = (double)enclosureBlock.D() + 0.5;
                double targetZ = (double)enclosureBlock.G() + 0.5;
                double distanceToTarget = localPlayer.i(targetX, localPlayer.N(), targetZ);
                if (distanceToTarget > 1.75) {
                    this.reset();
                    return;
                }
                this.movementLock.lock();
                if (this.movementTask == null) {
                    this.movementTask = new TargetPositionMovementTask(targetX, targetZ);
                    this.movementTask.setCompletionTolerance(0.075);
                    this.movementTask.setRestoreInputOnCompletion(false);
                    PlayerMovementTaskManager.INSTANCE.submit(this.movementTask);
                }
            } else {
                if (this.sneak.getEffectiveValue().booleanValue() && !this.sneaking) {
                    KeyBinding sneakKey = Minecraft.gameSettings().d$src$Lgg_vape_wrapper_impl_KeyBinding_$adn2z0();
                    sneakKey.setPressed(true);
                    this.sneaking = true;
                }
                this.clearMovementTask();
            }
            int nodesWithoutPaths = 0;
            for (int i = this.placementNodes.size() - 1; i >= 0; --i) {
                BlockPlacementNode node = this.placementNodes.get(i);
                if (!node.candidatePaths.isEmpty() || node.retryState != 0) continue;
                this.computePlacementPaths(enclosureBlock, node, localPlayer, world);
                if (!node.candidatePaths.isEmpty() || !node.hasSupportBlock()) continue;
                ++nodesWithoutPaths;
            }
            double eyeHeight = ForgeVersion.MC_1_7_10.Y() ? (double)localPlayer.X() : 0.0;
            Vec3 eyePosition = Vec3.create(localPlayer.z(), localPlayer.A() + eyeHeight, localPlayer.h());
            this.currentTarget = null;
            int failedPlacements = 0;
            for (int i = this.placementNodes.size() - 1; i >= 0; --i) {
                BlockPlacementNode node = this.placementNodes.get(i);
                if (node.candidatePaths.isEmpty() || this.currentTarget != null) continue;
                ArrayList<BlockPlacementPathSegmentState> candidatePaths = node.candidatePaths;
                int invalidPaths = 0;
                pathSearch: while (!candidatePaths.isEmpty()) {
                    BlockPlacementPathSegmentState pathState = candidatePaths.get(0);
                    Vector<PlacementTarget> pendingTargets = pathState.pendingTargets;
                    while (!pendingTargets.isEmpty()) {
                        PlacementTarget placementTarget = pendingTargets.firstElement();
                        if (placementTarget == null) continue;
                        BlockData placedBlock = placementTarget.getPlacedBlock();
                        Block block = world.getBlockByPos(placedBlock.D(), placedBlock.B(), placedBlock.G());
                        if (BlockUtil.u(block)) {
                            if (ClutchPlacementPathUtils.isPlacementSpaceClear(world, localPlayer, placedBlock)) {
                                Vec3 hitPoint = ClutchPlacementPathUtils.findFirstPlacementHitPoint(localPlayer, world, eyePosition, placementTarget);
                                if (hitPoint != null) {
                                    AxisAlignedBB nodeBounds = AxisAlignedBB.create(node.baseBlock.D(), node.baseBlock.B(), node.baseBlock.G(), node.baseBlock.D() + 1, node.baseBlock.B() + (node.hasSupportBlock() ? 2 : 1), node.baseBlock.G() + 1).expand(1.0, 1.0, 1.0);
                                    if (ClutchPlacementPathUtils.isEntityPathClear(world, localPlayer, nodeBounds, eyePosition, hitPoint)) {
                                        this.currentTarget = placementTarget;
                                        this.currentTarget.hitPoint = hitPoint;
                                        break pathSearch;
                                    }
                                    if (node.retryState == 0) {
                                        ++invalidPaths;
                                    } else {
                                        ++failedPlacements;
                                    }
                                    candidatePaths.remove(0);
                                    break;
                                }
                                if (node.retryState == 0) {
                                    ++invalidPaths;
                                } else {
                                    ++failedPlacements;
                                }
                                candidatePaths.remove(0);
                                break;
                            }
                            if (node.retryState == 0) {
                                ++invalidPaths;
                            } else {
                                ++failedPlacements;
                            }
                            candidatePaths.remove(0);
                            break;
                        }
                        pendingTargets.removeElementAt(0);
                    }
                    if (this.currentTarget != null) break;
                    if (candidatePaths.isEmpty() || invalidPaths != 0) continue;
                    candidatePaths.remove(0);
                    node.retryState = -1;
                }
                if (invalidPaths <= 0 || this.currentTarget != null || !node.candidatePaths.isEmpty() || node.retryState != 0) continue;
                this.computePlacementPaths(enclosureBlock, node, localPlayer, world);
                ++node.retryState;
                ++i;
            }
            if (this.currentTarget != null) {
                BlockData placedBlock = this.currentTarget.getPlacedBlock();
                boolean canPlaceImmediately = (standingInEnclosure || !BlockUtil.e(localPlayer, placedBlock)) && this.isFacingTarget();
                if (!canPlaceImmediately) {
                    if (this.rotationController == null) {
                        this.rotationController = this.silentAim.getEffectiveValue() != false ? new AdaptiveRotationController() : new PointRotationController(this.currentTarget.hitPoint);
                    }
                    this.rotationController.setTolerance(0.0f);
                    this.rotationController.setClampStepToRemaining(true);
                    this.rotationController.setRetainAfterCompletion(true);
                    this.rotationController.setLinearAcceleration(true);
                    this.rotationController.setScaleAxesProportionally(true);
                    this.rotationController.setSpeed(((Double)this.aimSpeed.getValue()).floatValue());
                    this.rotationController.setRandomizeMovement(false);
                    if (this.rotationController instanceof PointRotationController) {
                        ((PointRotationController)this.rotationController).setNormalizeYaw(false);
                    }
                    if (this.aimTimer.hasTimeElapsed((long)this.placeDelay.getRandomValue() / 2L)) {
                        if (this.rotationController instanceof PointRotationController) {
                            ((PointRotationController)this.rotationController).setTarget(this.currentTarget.hitPoint);
                        } else if (this.rotationController instanceof AdaptiveRotationController) {
                            ((AdaptiveRotationController)this.rotationController).setTarget(this.currentTarget.hitPoint);
                        }
                    } else {
                        this.rotationController.setTargetRotation(-999.0f, -999.0f);
                    }
                    RotationManager.INSTANCE.setController(this.rotationController);
                } else {
                    if (this.rotationController != null) {
                        if (this.rotationController instanceof PointRotationController) {
                            RotationManager.INSTANCE.releaseController(this.rotationController);
                        } else {
                            this.rotationController.setTargetRotation(-999.0f, -999.0f);
                        }
                    }
                    if (this.clickTimer.hasTimeElapsed((long)this.placeDelay.getRandomValue())) {
                        KeyBinding useKey = Minecraft.gameSettings().F();
                        if (useKey.u() || useKey.isPressed()) {
                            KeyBinding.setKeyBindState(useKey, false);
                        }
                        KeyBinding placementKey = Minecraft.gameSettings().b$src$Lgg_vape_wrapper_impl_KeyBinding_$1yi3362();
                        placementKey.onTick(1);
                        this.clickTimer.reset();
                        this.aimTimer.reset();
                    }
                }
            } else {
                this.reset();
                if (failedPlacements > 0) {
                    Vape.INSTANCE.getNotificationManager().show("Block-In Disabled", NotificationText.localize("Failed to place ") + failedPlacements + " " + NotificationText.localize("block(s)!"), NotificationType.WARNING, 2000L);
                } else if (nodesWithoutPaths > 0) {
                    Vape.INSTANCE.getNotificationManager().show("Block-In Disabled", "No valid path found!", NotificationType.WARNING, 2000L);
                }
            }
        } else {
            boolean hasPlacementNodes = !this.placementNodes.isEmpty();
            String message = this.blockSlot == -1 ? (hasPlacementNodes ? "Ran out of blocks!" : "No blocks in hotbar!") : "Could not block in!";
            Vape.INSTANCE.getNotificationManager().show("Block-In Disabled", message, NotificationType.WARNING, 2000L);
            this.reset();
        }
    }


    @Override
    public void setEnabled(boolean enabled, boolean bypassVisibilityCheck) {
        if (!enabled && this.rotationController instanceof AdaptiveRotationController) {
            this.paused = !this.paused;
        } else {
            super.setEnabled(enabled, bypassVisibilityCheck);
            this.paused = false;
            if (!enabled) {
                this.reset();
            } else {
                this.sneaking = false;
            }
        }
    }

    private int blockPriorityIndex(ItemStack itemStack) {
        if (itemStack.isNull() || itemStack.getItem().isNull()) {
            return Integer.MAX_VALUE;
        }
        int priority = this.blockPriorityNames.size();
        for (int i = 0; i < this.blockPriorityNames.size(); ++i) {
            String blockName = this.blockPriorityNames.get(i);
            if (!itemStack.x().contains(blockName)) continue;
            priority = i;
            break;
        }
        return priority;
    }

    private DirectionalPosition findClutchPosition(EntityPlayerSP player, World world) {
        DirectionalPosition bestPosition = null;
        double bestCost = Double.MAX_VALUE;
        DirectionalPosition playerPosition = new DirectionalPosition(player.z(), player.R$src$Lgg_vape_wrapper_impl_AxisAlignedBB_$r19dfl().getMinY(), player.h(), 0);
        Stack<BlockPlacementNode> candidatePath = new Stack<>();
        for (int yOffset = 0; yOffset > -2; --yOffset) {
            BlockCoordinate layerPosition = playerPosition.F(0, yOffset, 0);
            for (int facingOffset = 0; facingOffset < 4; ++facingOffset) {
                int playerFacing = player.J$src$Lgg_vape_wrapper_impl_EnumFacing_$10aeq5x().c();
                EnumFacing facing = this.horizontalFacings[(playerFacing + facingOffset) % 4];
                BlockCoordinate adjacentPosition = layerPosition.i(facing);
                Block block = world.getBlockByPos(adjacentPosition.B(), adjacentPosition.E(), adjacentPosition.A());
                if (BlockUtil.p(block) && BlockUtil.b(world.getBlockByPos(adjacentPosition.B(), adjacentPosition.E() - 1, adjacentPosition.A()))) {
                    int[] faceOffsets = this.faceOffsetsFor(facing);
                    if (faceOffsets == null) continue;
                    for (int faceIndex : faceOffsets) {
                        BlockCoordinate supportPosition = adjacentPosition.i(EnumFacing.T(faceIndex));
                        Block supportBlock = world.getBlockByPos(supportPosition.B(), supportPosition.E(), supportPosition.A());
                        if (!BlockUtil.b(supportBlock)) continue;
                        candidatePath.clear();
                        candidatePath.push(new BlockPlacementNode(adjacentPosition.O(), facing));
                        double cost = this.computePathCost(world, candidatePath);
                        if (!(cost < bestCost)) continue;
                        bestCost = cost;
                        bestPosition = new DirectionalPosition(adjacentPosition, faceIndex);
                    }
                    continue;
                }
                double cost = (double)this.computePathCost(world, candidatePath);
                if (!BlockUtil.b(block) || !(cost < bestCost)) continue;
                bestCost = cost;
                bestPosition = new DirectionalPosition(playerPosition, facing.Y());
            }
        }
        return bestPosition == null ? playerPosition : bestPosition;
    }

    private boolean isFacingTarget() {
        boolean facingTarget = false;
        RayTraceResult rayTraceResult = RotationManager.INSTANCE.getNormalReachRayTrace();
        if (this.currentTarget != null && rayTraceResult.isBlockHit()) {
            boolean hitSupportBlock;
            if (ForgeVersion.MC_1_7_10.Y()) {
                hitSupportBlock = rayTraceResult.getBlockPos().equals(BlockPos.d(this.currentTarget.supportBlock));
            } else {
                hitSupportBlock = rayTraceResult.g() == this.currentTarget.supportBlock.D() && rayTraceResult.T() == this.currentTarget.supportBlock.B() && rayTraceResult.a$src$I$8nuo9d() == this.currentTarget.supportBlock.G();
            }
            if (hitSupportBlock) {
                EnumFacing requiredFacing = this.currentTarget.offsetFromSupport ? this.currentTarget.facing : null;
                facingTarget = requiredFacing == null || requiredFacing.equals(rayTraceResult.getSideHit()) && this.currentTarget.hitPoint.distanceTo(rayTraceResult.getHitVec()) <= 0.3;
            }
        }
        return facingTarget;
    }

    public int computePathCost(World world, Vector<BlockPlacementNode> path) {
        int cost = path.size();
        for (BlockPlacementNode node : path) {
            for (EnumFacing facing : this.horizontalFacings) {
                if (node.baseFacing != null && node.baseFacing.Y() == facing.Y() || node.supportFacing != null && node.supportFacing.Y() == facing.Y()) continue;
                if (node.hasSupportBlock()) {
                    BlockData upperAdjacentBlock = node.supportBlock.R(facing);
                    Block upperAdjacentState = world.getBlockByPos(upperAdjacentBlock.D(), upperAdjacentBlock.B(), upperAdjacentBlock.G());
                    if (!node.isSupportFacingBlocked(facing) && BlockUtil.u(upperAdjacentState)) {
                        ++cost;
                    }
                    BlockData upperBlock = node.supportBlock;
                    Block upperState = world.getBlockByPos(upperBlock.D(), upperBlock.B(), upperBlock.G());
                    if (!BlockUtil.u(upperState) && !BlockUtil.f(upperState)) {
                        ++cost;
                    }
                }
                BlockData baseBlock = node.baseBlock;
                Block baseState = world.getBlockByPos(baseBlock.D(), baseBlock.B(), baseBlock.G());
                if (!BlockUtil.u(baseState) && !BlockUtil.f(baseState)) {
                    ++cost;
                }
                BlockData adjacentBlock = node.baseBlock.R(facing);
                Block adjacentState = world.getBlockByPos(adjacentBlock.D(), adjacentBlock.B(), adjacentBlock.G());
                if (node.isBaseFacingBlocked(facing) || !BlockUtil.u(adjacentState)) continue;
                ++cost;
            }
        }
        return cost;
    }

    private void clearMovementTask() {
        if (this.movementTask != null) {
            this.movementLock.unlock();
            PlayerMovementTaskManager.INSTANCE.cancel(this.movementTask);
            this.movementTask = null;
        }
    }

    private void buildPlacementNodes(EntityPlayerSP player, World world) {
        if (!this.placementNodes.isEmpty()) {
            return;
        }
        ClutchSolidBlockPathSearchStrategy searchStrategy = new ClutchSolidBlockPathSearchStrategy(this, world);
        if (this.isStandingOnSolid(player, world)) {
            DirectionalPosition clutchPosition = this.findClutchPosition(player, world);
            BlockData baseBlock = clutchPosition.O();
            int facingIndex = clutchPosition.getFacingIndex();
            BlockPlacementNode baseNode = new BlockPlacementNode(baseBlock, null);
            DirectionalPosition nearestBed = this.findNearestBed(world, baseBlock);
            if (this.bedFinder.getEffectiveValue().booleanValue() && facingIndex != 0 && nearestBed != null) {
                Stack<BlockPlacementNode> bedPath = this.findBestPath(baseBlock, nearestBed.getFacing(), world, searchStrategy);
                if (bedPath != null && !bedPath.isEmpty()) {
                    Collections.reverse(bedPath);
                    this.placementNodes.addAll(bedPath);
                }
            }
            if (this.placementNodes.isEmpty()) {
                this.placementNodes.add(baseNode);
            }
            boolean playerAtBaseHeight = baseBlock.B() == MathUtil.floor(player.N());
            ArrayList<BlockPlacementNode> upperNodes = new ArrayList<>();
            for (BlockPlacementNode node : this.placementNodes) {
                if (!node.hasSupportBlock()) continue;
                upperNodes.add(new BlockPlacementNode(node.supportBlock.y(0, 1, 0)));
            }
            if (playerAtBaseHeight) {
                this.placementNodes.addAll(upperNodes);
            } else {
                this.placementNodes.addAll(0, upperNodes);
            }
        }
    }

    private void computePlacementPaths(BlockData enclosureBlock, BlockPlacementNode node, EntityPlayerSP player, World world) {
        if (node == null) {
            return;
        }
        if (!node.candidatePaths.isEmpty()) {
            return;
        }
        int startingFacing = player.J$src$Lgg_vape_wrapper_impl_EnumFacing_$10aeq5x().c();
        if (node.baseFacing != null && node.baseFacing.c() != -1) {
            startingFacing = node.baseFacing.getOpposite().c();
        }
        HashSet<BlockData> occupiedBlocks = new HashSet<>();
        HashSet<BlockData> enclosureBlocks = new HashSet<>();
        for (int i = this.placementNodes.size() - 1; i >= 0; --i) {
            BlockPlacementNode existingNode = this.placementNodes.get(i);
            if (existingNode.hasSupportBlock()) {
                enclosureBlocks.add(existingNode.baseBlock);
                enclosureBlocks.add(existingNode.supportBlock);
            }
            for (BlockPlacementPathSegmentState pathState : existingNode.candidatePaths) {
                for (PlacementTarget placementTarget : pathState.pendingTargets) {
                    if (placementTarget == null) continue;
                    occupiedBlocks.add(placementTarget.getPlacedBlock());
                }
            }
        }
        BlockData baseBlock = node.baseBlock;
        BlockData upperBlock = node.supportBlock;
        EnumFacing upperFacing = node.supportFacing;
        EnumFacing baseFacing = node.baseFacing;
        ClutchBlockPlacementPathSearchStrategy searchStrategy = new ClutchBlockPlacementPathSearchStrategy(this, enclosureBlocks, node, world, player, occupiedBlocks);
        boolean verticalPlacement = !node.hasSupportBlock() && (double)baseBlock.B() > player.N();
        if (!verticalPlacement) {
            for (int facingOffset = 0; facingOffset < 4; ++facingOffset) {
                EnumFacing facing = this.horizontalFacings[(startingFacing + facingOffset) % 4];
                if (upperFacing != null && facing.Y() == upperFacing.Y() || baseFacing != null && facing.Y() == baseFacing.Y()) continue;
                BlockData adjacentBase = baseBlock.R(facing);
                Block adjacentBaseState = world.getBlockByPos(adjacentBase.D(), adjacentBase.B(), adjacentBase.G());
                boolean baseBlocked = occupiedBlocks.contains(adjacentBase) || node.isBaseFacingBlocked(facing) || BlockUtil.b(adjacentBaseState);
                boolean upperBlocked = true;
                if (node.hasSupportBlock()) {
                    BlockData adjacentUpper = upperBlock.R(facing);
                    Block adjacentUpperState = world.getBlockByPos(adjacentUpper.D(), adjacentUpper.B(), adjacentUpper.G());
                    upperBlocked = occupiedBlocks.contains(adjacentUpper) || node.isSupportFacingBlocked(facing) || BlockUtil.b(adjacentUpperState);
                }
                if (baseBlocked && upperBlocked) continue;
                BlockData primarySource = baseBlocked ? upperBlock : baseBlock;
                double eyeHeight = ForgeVersion.MC_1_7_10.Y() ? (double)player.X() : 0.0;
                Vec3 eyePosition = Vec3.create((float)enclosureBlock.D() + 0.5f, player.A() + eyeHeight, (float)enclosureBlock.G() + 0.5f);
                Vector<PlacementTarget> path = ClutchPlacementPathUtils.findPlacementPath(enclosureBlock, eyePosition, player, world, primarySource.R(facing), facing, facing, searchStrategy, 0);
                if (path == null || path.isEmpty()) {
                    if (baseBlocked || !upperBlocked && ((path = ClutchPlacementPathUtils.findPlacementPath(enclosureBlock, eyePosition, player, world, upperBlock.R(facing), facing, facing, searchStrategy, 0)) == null || path.isEmpty())) {
                        continue;
                    }
                } else if (!baseBlocked && !upperBlocked) {
                    PlacementTarget upperPlacement = new PlacementTarget(baseBlock.R(facing), this.verticalFacings[1]);
                    upperPlacement.depth = path.size();
                    path.add(upperPlacement);
                }
                if (path == null || path.isEmpty()) continue;
                node.candidatePaths.add(new BlockPlacementPathSegmentState(facing, path));
            }
            int playerFacingIndex = player.J$src$Lgg_vape_wrapper_impl_EnumFacing_$10aeq5x().c();
            node.candidatePaths.sort((firstPath, secondPath) -> {
                int firstDistance = Math.abs(playerFacingIndex - firstPath.placementFacing.Y());
                int secondDistance = Math.abs(playerFacingIndex - secondPath.placementFacing.Y());
                return Integer.compare(secondDistance, firstDistance);
            });
        } else {
            EnumFacing upwardFacing = EnumFacing.F$src$Lgg_vape_wrapper_impl_EnumFacing_$glfxl5();
            Block baseState = world.getBlockByPos(baseBlock.D(), baseBlock.B(), baseBlock.G());
            if (BlockUtil.u(baseState)) {
                double eyeHeight = ForgeVersion.MC_1_7_10.Y() ? (double)player.X() : 0.0;
                Vec3 eyePosition = Vec3.create((float)enclosureBlock.D() + 0.5f, player.A() + eyeHeight, (float)enclosureBlock.G() + 0.5f);
                Vector<PlacementTarget> path = ClutchPlacementPathUtils.findPlacementPath(enclosureBlock, eyePosition, player, world, baseBlock, upwardFacing, upwardFacing.getOpposite(), searchStrategy, 0);
                if (path != null && !path.isEmpty()) {
                    node.candidatePaths.add(new BlockPlacementPathSegmentState(upwardFacing, path));
                }
            }
        }
    }

    private boolean isAllowedBlock(ItemStack itemStack) {
        if (itemStack.isNull() || itemStack.getItem().isNull()) {
            return false;
        }
        Item item = itemStack.getItem();
        if (!item.isInstance(MappedClasses.Vw)) {
            return false;
        }
        return this.useBlacklist.getEffectiveValue() == false || this.blockBlacklist.doesNotMatch(itemStack);
    }

    private int selectBestBlockSlot(EntityPlayerSP player, World world) {
        if (player.isNull() || world.isNull()) {
            return -1;
        }
        int bestSlot = -1;
        boolean prioritizeHardness = this.blockPriorityMode.getValue() == this.hardestMode;
        if (prioritizeHardness) {
            float bestHardness = -1.0f;
            int bestStackSize = 0;
            for (int i = 0; i < 9; ++i) {
                if (!this.isUsableBlockSlot(player, world, i)) continue;
                ItemStack itemStack = player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6().c(i);
                float hardness = BlockUtil.O(itemStack);
                int stackSize = itemStack.t();
                if (!(hardness > bestHardness) && (hardness != bestHardness || stackSize <= bestStackSize)) continue;
                bestHardness = hardness;
                bestStackSize = stackSize;
                bestSlot = i;
            }
        } else {
            int bestPriority = Integer.MAX_VALUE;
            int bestStackSize = 0;
            for (int i = 0; i < 9; ++i) {
                if (!this.isUsableBlockSlot(player, world, i)) continue;
                ItemStack itemStack = player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6().c(i);
                int priority = this.blockPriorityIndex(itemStack);
                int stackSize = itemStack.t();
                if (priority >= bestPriority && (priority != bestPriority || stackSize <= bestStackSize)) continue;
                bestPriority = priority;
                bestStackSize = stackSize;
                bestSlot = i;
            }
        }
        return bestSlot;
    }


    private void updateRotationController() {
        if (this.rotationController != null) {
            this.rotationController.setScaleAxesProportionally(true);
            this.rotationController.setRandomizeMovement(false);
            this.rotationController.setLinearAcceleration(true);
            this.rotationController.setSpeed(6.0f);
            RotationManager.INSTANCE.releaseController(this.rotationController);
        }
        if (RotationManager.INSTANCE.getActiveController() == null || RotationManager.INSTANCE.getActiveController() != this.rotationController || this.rotationController != null && !this.rotationController.shouldRetainAfterCompletion() && this.rotationController.isComplete()) {
            this.rotationController = null;
            this.rotationClaim.release(this);
            if (this.paused) {
                this.paused = false;
                super.setEnabled(false, true);
            }
        }
    }

    public boolean isRightTool(Block block, ItemStack itemStack) {
        if (block.H().isToolNotRequired()) {
            return true;
        }
        return true;
    }

    private DirectionalPosition findNearestBed(World world, BlockData origin) {
        DirectionalPosition nearestBed = null;
        double nearestDistance = Double.MAX_VALUE;
        int originX = origin.D();
        int originY = origin.B();
        int originZ = origin.G();
        int searchRadius = 10;
        for (int xOffset = -searchRadius; xOffset < searchRadius; ++xOffset) {
            for (int zOffset = -searchRadius; zOffset < searchRadius; ++zOffset) {
                for (int k = -3; k < 3; ++k) {
                    int blockX = originX + xOffset;
                    int blockY = originY + k;
                    int blockZ = originZ + zOffset;
                    double distance = Math.sqrt(Math.pow(blockX - originX, 2.0) + Math.pow(blockZ - originZ, 2.0));
                    if (!BlockUtil.f(world.getBlockByPos(blockX, blockY, blockZ)) || !(distance < nearestDistance)) continue;
                    double angle = Math.toDegrees(Math.atan2(blockZ - originZ, blockX - originX)) - 90.0;
                    EnumFacing facing = EnumFacing.p(angle);
                    nearestDistance = distance;
                    nearestBed = new DirectionalPosition(blockX, blockY, blockZ, facing.Y());
                }
            }
        }
        return nearestBed;
    }

    private boolean isStandingOnSolid(EntityPlayerSP player, World world) {
        int blockX = MathUtil.floor(player.z());
        int blockY = MathUtil.floor(player.R$src$Lgg_vape_wrapper_impl_AxisAlignedBB_$r19dfl().getMinY() - 1.0);
        int blockZ = MathUtil.floor(player.h());
        Block block = world.getBlockByPos(blockX, blockY, blockZ);
        return BlockUtil.b(block);
    }

}
