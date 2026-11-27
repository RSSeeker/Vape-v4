package gg.vape.module.utility;

import gg.vape.Vape;
import gg.vape.event.EventHandler;
import gg.vape.event.impl.EventEntityJoinWorld;
import gg.vape.event.impl.EventPlayerUseItem;
import gg.vape.event.impl.EventPreTick;
import gg.vape.event.impl.EventWorldChange;
import gg.vape.input.KeyBindingHelper;
import gg.vape.mapping.MappedClasses;
import gg.vape.mapping.mappings.MWorld;
import gg.vape.module.Category;
import gg.vape.module.Mod;
import gg.vape.module.control.SharedModuleControlClaims;
import gg.vape.module.utility.autopearl.AutoPearlAimLock;
import gg.vape.module.utility.autopearl.AutoPearlPointRotationController;
import gg.vape.module.utility.autopearl.AutoPearlRotationController;
import gg.vape.module.utility.autopearl.AutoPearlState;
import gg.vape.module.utility.autopearl.AutoPearlTrackedPearl;
import gg.vape.rotation.AdaptiveRotationController;
import gg.vape.rotation.FixedRotationController;
import gg.vape.rotation.RotationControlClaim;
import gg.vape.rotation.RotationManager;
import gg.vape.unmap.ItemLimitData;
import gg.vape.unmap.ModeOption;
import gg.vape.unmap.ModeSelection;
import gg.vape.utils.MathUtil;
import gg.vape.utils.ProjectilePitchUtil;
import gg.vape.utils.TimerUtil;
import gg.vape.value.BooleanValue;
import gg.vape.value.LimitValue;
import gg.vape.value.ModeValue;
import gg.vape.value.NumberValue;
import gg.vape.wrapper.impl.Container;
import gg.vape.wrapper.impl.Entity;
import gg.vape.wrapper.impl.EntityEnderPearl;
import gg.vape.wrapper.impl.EntityPlayer;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.InventoryPlayer;
import gg.vape.wrapper.impl.Item;
import gg.vape.wrapper.impl.ItemStack;
import gg.vape.wrapper.impl.KeyBinding;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.Slot;
import gg.vape.wrapper.impl.Vec3;
import gg.vape.wrapper.impl.WorldClient;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AutoPearl
extends Mod {
    private final TimerUtil cooldownTimer;
    private final Set<Integer> handledPearlIds;
    private final BooleanValue verticalCheckValue;
    private AutoPearlState state;
    private final ModeValue modeValue;
    private static final float MAX_PLAYER_DISTANCE = 128.0f;
    private final TimerUtil throwDelayTimer;
    private final BooleanValue limitToItemsValue;
    private static final long MODULE_ID = -5394869744341811456L;
    private final ModeOption aggroMode = new ModeOption("Aggro");
    private int savedHotbarSlot;
    private final TimerUtil stateTimeoutTimer;
    private final Map<Integer, AutoPearlTrackedPearl> trackedPearls;
    private final TimerUtil pearlUseTimer;
    private final NumberValue cooldownValue;
    private final NumberValue aimSpeedValue = NumberValue.create(this, "Aim speed", "#.#", "", 0.1, 5.0, 15.0, 0.5, "How quickly your aim moves towards the pearl");
    private final BooleanValue silentAimValue;
    private final ModeOption onBindMode;
    private final RotationControlClaim rotationClaim = SharedModuleControlClaims.rotation;
    private final NumberValue minHealthValue;
    @Nullable
    private AutoPearlAimLock aimLock = null;
    private final LimitValue allowedItemsValue = LimitValue.create(this, "autopearl-alloweditems", "Allowed items", LimitValue.ALLOW_LIST_COLOR, new ItemLimitData("swords"), new ItemLimitData("ender pearl"), new ItemLimitData("hand"));
    private final NumberValue distanceLimitValue;
    private AutoPearlTrackedPearl pendingPearl;
    private final NumberValue angleLimitValue;

    private boolean shouldThrowAt(AutoPearlTrackedPearl trackedPearl) {
        EntityEnderPearl pearl = trackedPearl.getPearl();
        EntityPlayer owner = trackedPearl.getOwner();
        if (!this.hasPearlInInventory()) {
            return false;
        }
        if (!this.isHoldingAllowedItem()) {
            return false;
        }
        if (!this.hasEnoughHealth()) {
            return false;
        }
        if (this.isOnCooldown()) {
            return false;
        }
        if (this.isAimLockInvalid()) {
            return false;
        }
        if (!this.isPearlActive(pearl)) {
            return false;
        }
        if (!this.isNotSelf(owner)) {
            return false;
        }
        Vec3 landingPoint = trackedPearl.predictLandingPosition();
        if (!this.isPearlFartherThanOwner(landingPoint, owner)) {
            return false;
        }
        if (!this.isLandingValid(landingPoint)) {
            return false;
        }
        return this.isPearlUnhandled(pearl);
    }

    private void resetState() {
        if (this.aimLock != null && RotationManager.INSTANCE.getActiveController() == this.aimLock.getRotationController()) {
            RotationManager.INSTANCE.releaseController(this.aimLock.getRotationController());
        }
        this.rotationClaim.release(this);
        this.aimLock = null;
        this.pendingPearl = null;
        this.state = AutoPearlState.ACQUIRING_PEARL;
        this.stateTimeoutTimer.reset();
    }


    private boolean isNotSelf(EntityPlayer player) {
        return !player.isInstance(MappedClasses.z5);
    }

    private boolean isPearlInRange(Vec3 position) {
        double x = position.getX();
        double y = position.getY();
        double z = position.getZ();
        float angle = Math.abs(this.angleToPoint(x, z));
        double maximumAngle = (Double)this.angleLimitValue.getValue() / 2.0;
        if ((double)angle > maximumAngle) {
            return false;
        }
        EntityPlayerSP localPlayer = Minecraft.thePlayer();
        if (localPlayer.isNull()) {
            return false;
        }
        double distance = localPlayer.i(x, y, z);
        double minimumDistance = ((Double)this.distanceLimitValue.getValue()).doubleValue();
        if (distance <= minimumDistance) {
            return false;
        }
        if (!this.verticalCheckValue.getEffectiveValue().booleanValue()) {
            return true;
        }
        double verticalOffset = y - localPlayer.N();
        return verticalOffset < 7.0;
    }

    @Override
    public void onDisable() {
        this.resetState();
    }

    private void beginAimLock(AutoPearlTrackedPearl trackedPearl) {
        this.stateTimeoutTimer.reset();
        Vec3 landingPoint = trackedPearl.predictLandingPosition();
        FixedRotationController controller = this.buildRotationController(landingPoint);
        this.aimLock = new AutoPearlAimLock(trackedPearl.getPearl(), trackedPearl.getOwner(), controller, landingPoint, null);
        this.state = AutoPearlState.ACQUIRING_AIMLOCK;
    }

    @EventHandler
    public void onWorldChange(EventWorldChange eventWorldChange) {
        this.resetState();
    }

    private boolean isOnCooldown() {
        long cooldownSeconds = ((Double)this.cooldownValue.getValue()).longValue();
        if ((double)cooldownSeconds <= 0.0) {
            return false;
        }
        long elapsedSeconds = this.cooldownTimer.getLastMS() / 1000L;
        return elapsedSeconds < cooldownSeconds;
    }

    private boolean isPearlActive(EntityEnderPearl pearl) {
        if (pearl.b$src$Z$fqlxe4()) {
            return false;
        }
        return !pearl.M$src$Z$ff28xj();
    }

    private boolean isHoldingAllowedItem() {
        if (!this.limitToItemsValue.getEffectiveValue().booleanValue()) {
            return true;
        }
        ItemStack itemStack = Minecraft.thePlayer().getHeldItemHand();
        if (!itemStack.isNull() && itemStack.getItem().isInstance(MappedClasses.ZH)) {
            return true;
        }
        return this.allowedItemsValue.isValid(itemStack, false);
    }

    @Override
    public void onEnable() {
        this.stateTimeoutTimer.reset();
        if (this.onBindMode.isSelected()) {
            this.resetState();
        }
    }

    private boolean hasEnoughHealth() {
        EntityPlayerSP localPlayer = Minecraft.thePlayer();
        if (localPlayer.isNull() || localPlayer.M$src$Z$ff28xj()) {
            return false;
        }
        float minimumHealth = ((Double)this.minHealthValue.getValue()).floatValue();
        float currentHealth = localPlayer.w$src$F$15l9epb();
        return currentHealth >= minimumHealth;
    }

    @Nullable
    private FixedRotationController buildRotationController(Vec3 targetPosition) {
        Float pitch = this.computeThrowPitch(targetPosition);
        if (pitch == null) {
            return null;
        }
        FixedRotationController controller = this.silentAimValue.getEffectiveValue() != false ? new AutoPearlRotationController(this, pitch) : new AutoPearlPointRotationController(this, targetPosition, pitch);
        if (controller instanceof AdaptiveRotationController) {
            ((AdaptiveRotationController)controller).setTarget(targetPosition);
        }
        controller.setClampStepToRemaining(true);
        controller.setTolerance(0.5f);
        controller.setAngleBasedAcceleration(false);
        controller.setScaleAxesProportionally(true);
        controller.setRetainAfterCompletion(false);
        controller.setCubicAcceleration(true);
        controller.setLinearAcceleration(true);
        controller.setSpeed(((Double)this.aimSpeedValue.getValue()).floatValue() * 0.2f);
        controller.setRandomizeMovement(true);
        if (controller instanceof AdaptiveRotationController) {
            ((AdaptiveRotationController)controller).setRelativeMode(false);
        }
        return controller;
    }

    @EventHandler
    public void onPlayerUseItem(EventPlayerUseItem eventPlayerUseItem) {
        if (((Double)this.cooldownValue.getValue()).longValue() <= 0L) {
            return;
        }
        ItemStack itemStack = eventPlayerUseItem.getItemStack();
        if (itemStack.isNull()) {
            return;
        }
        Item item = itemStack.getItem();
        if (item.isNull() || !item.isInstance(MappedClasses.ZH)) {
            return;
        }
        this.pearlUseTimer.reset();
    }

    private boolean isPearlFartherThanOwner(@NotNull Vec3 landingPoint, @NotNull EntityPlayer owner) {
        EntityPlayerSP localPlayer = Minecraft.thePlayer();
        if (localPlayer.isNull()) {
            return false;
        }
        double landingDistance = localPlayer.i(landingPoint.getX(), landingPoint.getY(), landingPoint.getZ());
        double ownerDistance = (double)localPlayer.getDistanceToEntity(owner);
        return landingDistance > ownerDistance;
    }

    @Nullable
    private EntityPlayer findNearestPlayer(EntityEnderPearl pearl) {
        List entities = Minecraft.theWorld().S();
        EntityPlayer nearestPlayer = null;
        float nearestDistance = Float.MAX_VALUE;
        for (Object handle : entities) {
            Entity entity = new Entity(handle);
            if (!entity.isInstance(MappedClasses.Yl)) continue;
            EntityPlayer player = new EntityPlayer(entity);
            float distance = player.getDistanceToEntity(pearl);
            if (!(distance < nearestDistance) || !(distance <= MAX_PLAYER_DISTANCE)) continue;
            nearestPlayer = player;
            nearestDistance = distance;
        }
        return nearestPlayer;
    }

    private boolean isAimLockInvalid() {
        if (this.aimLock == null) {
            return false;
        }
        return !this.aimLock.getRotationController().isComplete();
    }

    private float angleToPoint(double targetX, double targetZ) {
        EntityPlayerSP localPlayer = Minecraft.thePlayer();
        double deltaX = targetX - localPlayer.z();
        double deltaZ = targetZ - localPlayer.h();
        double targetAngle = deltaZ < 0.0 && deltaX < 0.0 ? 90.0 + Math.toDegrees(Math.atan(deltaZ / deltaX)) : (deltaZ < 0.0 && deltaX > 0.0 ? -90.0 + Math.toDegrees(Math.atan(deltaZ / deltaX)) : Math.toDegrees(-Math.atan(deltaX / deltaZ)));
        return MathUtil.wrapAngleTo180(-(localPlayer.J() - (float)targetAngle));
    }

    private boolean isPearlUnhandled(EntityEnderPearl pearl) {
        return !this.handledPearlIds.contains(pearl.S());
    }

    @Nullable
    private AutoPearlTrackedPearl trackPearl(EntityEnderPearl pearl) {
        AutoPearlTrackedPearl trackedPearl = this.trackedPearls.get(pearl.S());
        if (trackedPearl != null) {
            return trackedPearl;
        }
        EntityPlayer owner = this.findNearestPlayer(pearl);
        if (owner == null || owner.isNull()) {
            return null;
        }
        trackedPearl = new AutoPearlTrackedPearl(pearl, owner, null);
        this.trackedPearls.put(pearl.S(), trackedPearl);
        return trackedPearl;
    }

    private int findPearlSlot() {
        EntityPlayerSP localPlayer = Minecraft.thePlayer();
        if (localPlayer.isNull()) {
            return -1;
        }
        Container container = localPlayer.F$src$Lgg_vape_wrapper_impl_Container_$152y6lm();
        if (container.isNull()) {
            return -1;
        }
        InventoryPlayer inventory = localPlayer.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6();
        if (inventory.isNull()) {
            return -1;
        }
        for (int i = 36; i < 45; ++i) {
            Slot slot = container.getSlot(i);
            if (!slot.hasStack() || !slot.getStack().getItem().isInstance(MappedClasses.ZH)) continue;
            return i;
        }
        return -1;
    }

    public void tryThrowAt(AutoPearlTrackedPearl trackedPearl) {
        if (!this.shouldThrowAt(trackedPearl)) {
            return;
        }
        this.beginAimLock(trackedPearl);
    }

    @Nullable
    private Float computeThrowPitch(Vec3 targetPosition) {
        EntityPlayerSP localPlayer = Minecraft.thePlayer();
        if (localPlayer == null || localPlayer.isNull()) {
            return null;
        }
        float yaw = localPlayer.J();
        double sourceX = localPlayer.z() - (double)(MathUtil.cos(yaw / 180.0f * (float)Math.PI) * 0.16f);
        double sourceY = localPlayer.U() - (double)0.1f;
        double sourceZ = localPlayer.h() - (double)(MathUtil.sin(yaw / 180.0f * (float)Math.PI) * 0.16f);
        float calculatedPitch = ProjectilePitchUtil.calculatePitch(sourceX, sourceY, sourceZ, targetPosition.getX(), targetPosition.getY(), targetPosition.getZ());
        if (Float.isNaN(calculatedPitch)) {
            return null;
        }
        float adjustedPitch = calculatedPitch < 0.0f ? calculatedPitch - 5.0f : (calculatedPitch > 0.0f ? calculatedPitch + 5.0f : calculatedPitch);
        if (adjustedPitch < -90.0f) {
            adjustedPitch = -90.0f;
        } else if (adjustedPitch > 90.0f) {
            adjustedPitch = 90.0f;
        }
        return Float.valueOf(adjustedPitch);
    }

    @Nullable
    private List<AutoPearlTrackedPearl> collectTrackedPearls() {
        WorldClient world = Minecraft.theWorld();
        if (world.isNull()) {
            return null;
        }
        ArrayList<EntityEnderPearl> pearls = new ArrayList<>();
        ArrayList<EntityPlayer> players = new ArrayList<>();
        for (Object handle : world.S()) {
            if (MappedClasses.Zg.isInstance(handle)) {
                pearls.add(new EntityEnderPearl(handle));
                continue;
            }
            if (!MappedClasses.Yl.isInstance(handle)) continue;
            players.add(new EntityPlayer(handle));
        }
        ArrayList<AutoPearlTrackedPearl> results = new ArrayList<>();
        for (EntityEnderPearl pearl : pearls) {
            AutoPearlTrackedPearl trackedPearl = this.trackedPearls.get(pearl.S());
            if (trackedPearl != null) {
                results.add(trackedPearl);
                continue;
            }
            float nearestDistance = Float.MAX_VALUE;
            EntityPlayer nearestPlayer = null;
            for (EntityPlayer player : players) {
                float distance = player.getDistanceToEntity(pearl);
                if (!(distance < nearestDistance) || !(distance <= MAX_PLAYER_DISTANCE)) continue;
                nearestDistance = distance;
                nearestPlayer = player;
            }
            if (nearestPlayer == null) continue;
            trackedPearl = new AutoPearlTrackedPearl(pearl, nearestPlayer, null);
            this.trackedPearls.put(pearl.S(), trackedPearl);
            results.add(trackedPearl);
        }
        Collections.reverse(results);
        return results;
    }

    @EventHandler
    public void onEntityJoinWorld(EventEntityJoinWorld eventEntityJoinWorld) {
        Entity entity = eventEntityJoinWorld.getEntity();
        if (!entity.isInstance(MappedClasses.Zg)) {
            return;
        }
        EntityEnderPearl pearl = new EntityEnderPearl(entity.getObject());
        AutoPearlTrackedPearl trackedPearl = this.trackPearl(pearl);
        if (trackedPearl == null) {
            return;
        }
        EntityPlayer owner = trackedPearl.getOwner();
        if (!owner.isNull()) {
            if (owner.isInstance(MappedClasses.z5)) {
                if (((Double)this.cooldownValue.getValue()).longValue() > 0L) {
                    long elapsedMillis = this.pearlUseTimer.getLastMS();
                    if (elapsedMillis <= 1000L) {
                        this.cooldownTimer.reset();
                    }
                }
            } else {
                this.pendingPearl = trackedPearl;
            }
        }
    }

    private boolean hasPearlInInventory() {
        int pearlSlot = this.findPearlSlot();
        return pearlSlot != -1;
    }

    private boolean isStateStillValid() {
        if (!this.isHoldingAllowedItem()) {
            return false;
        }
        if (!this.hasEnoughHealth()) {
            return false;
        }
        if (this.aimLock != null) {
            Vec3 landingPosition = this.aimLock.getLandingPosition();
            return landingPosition == null || landingPosition.isNull() || this.isLandingValid(landingPosition);
        }
        return true;
    }

    private boolean isLandingValid(Vec3 landingPosition) {
        if (landingPosition == null || landingPosition.isNull()) {
            return false;
        }
        if (!this.isPearlInRange(landingPosition)) {
            return false;
        }
        Float pitch = this.computeThrowPitch(landingPosition);
        return pitch != null;
    }

    public AutoPearl() {
        super("AutoPearl", (int)MODULE_ID, Category.UTILITY, "Aims and throws a pearl at an enemies pearl trajectory.");
        this.trackedPearls = new HashMap<Integer, AutoPearlTrackedPearl>();
        this.handledPearlIds = new HashSet<Integer>();
        this.throwDelayTimer = new TimerUtil();
        this.cooldownValue = NumberValue.createWithDescription(this, "Pearl cooldown", "#.#", "sec", 0.0, 1.0, 15.0, "Minimum delay between pearl throws");
        this.distanceLimitValue = NumberValue.create(this, "Distance limit", "#.#", "m", 0.0, 6.0, 10.0, 0.1, "The minimum distance a pearl needs to land away from you\nin order to pearl towards it.");
        this.angleLimitValue = NumberValue.create(this, "Angle limit", "#", "", 30.0, 180.0, 360.0, 5.0, "Maximum angle from your crosshair a pearl can be\nin order to be chased");
        this.limitToItemsValue = BooleanValue.create(this, "Limit to items", true, "AutoPearl only functions while holding selected items");
        this.minHealthValue = NumberValue.create(this, "Min health", "#", "HP", 1.0, 5.0, 20.0, 1.0, "Minimum amount of health you must have\nin order to throw a pearl");
        this.onBindMode = new ModeOption("On bind");
        this.modeValue = ModeValue.create((Object)this, "Mode", "On bind - searches for thrown pearls and throws upon pressing bind\nAggro - Throws pearl as soon as enemy throws theirs", (ModeSelection)this.onBindMode, this.onBindMode, this.aggroMode);
        this.silentAimValue = BooleanValue.create(this, "Silent aim", false, "Uses Silent Aim system");
        this.stateTimeoutTimer = new TimerUtil();
        this.cooldownTimer = new TimerUtil();
        this.pearlUseTimer = new TimerUtil();
        this.verticalCheckValue = BooleanValue.create(this, "Vertical check", false, "Doesn't attempt to chase pearls that are landing a certain amount above your current Y position.");
        this.state = AutoPearlState.ACQUIRING_PEARL;
        this.limitToItemsValue.addDependentValues(this.allowedItemsValue);
        this.limitToItemsValue.setCompactListValue(this.allowedItemsValue);
        this.addValue(this.modeValue, this.aimSpeedValue, this.angleLimitValue, this.minHealthValue, this.distanceLimitValue, this.verticalCheckValue, this.cooldownValue, this.silentAimValue, this.limitToItemsValue, this.allowedItemsValue);
        this.rotationClaim.setPriority(this, 7);
    }

    @EventHandler
    public void onTick(EventPreTick eventPreTick) {
        AutoPearlState previousState = this.state;
        boolean stopProcessing = false;
        if (this.state != AutoPearlState.ACQUIRING_PEARL && this.stateTimeoutTimer.hasTimeElapsed(5000L)) {
            this.resetState();
        }
        stateMachine: do {
            previousState = this.state;
            if (!(this.state == AutoPearlState.ACQUIRING_PEARL || this.state == AutoPearlState.PENDING_RESET || this.isStateStillValid())) {
                this.resetState();
                stopProcessing = true;
                break;
            }
            switch (this.state) {
                case ACQUIRING_PEARL: {
                    if (this.aimLock != null) {
                        this.state = AutoPearlState.ACQUIRING_AIMLOCK;
                        break;
                    }
                    if (this.onBindMode.isSelected()) {
                        List<AutoPearlTrackedPearl> trackedPearls = this.collectTrackedPearls();
                        for (AutoPearlTrackedPearl trackedPearl : trackedPearls) {
                            if (!this.shouldThrowAt(trackedPearl)) continue;
                            this.tryThrowAt(trackedPearl);
                            break;
                        }
                        if (this.state != previousState) continue stateMachine;
                        this.setEnabled(false);
                        break;
                    }
                    if (this.pendingPearl == null) break;
                    if (this.shouldThrowAt(this.pendingPearl)) {
                        this.tryThrowAt(this.pendingPearl);
                    }
                    this.pendingPearl = null;
                    break;
                }
                case ACQUIRING_AIMLOCK: {
                    boolean rotationClaimed = this.rotationClaim.isOwnedBy(this) || this.rotationClaim.acquire(this, this.silentAimValue.getEffectiveValue());
                    if (!rotationClaimed) continue stateMachine;
                    RotationManager.INSTANCE.setController(this.aimLock.getRotationController());
                    this.handledPearlIds.add(this.aimLock.getPearl().S());
                    this.state = AutoPearlState.PENDING_AIMJOB;
                    break;
                }
                case PENDING_AIMJOB: {
                    if (!this.aimLock.getRotationController().isComplete()) break;
                    this.state = AutoPearlState.PENDING_THROW;
                    this.throwDelayTimer.reset();
                    break;
                }
                case PENDING_THROW: {
                    if (!this.throwDelayTimer.hasTimeElapsed(100L)) break;
                    EntityPlayerSP localPlayer = Minecraft.thePlayer();
                    InventoryPlayer inventory = localPlayer.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6();
                    if (localPlayer.isNull() || inventory.isNull()) continue stateMachine;
                    int pearlSlot = this.findPearlSlot();
                    if (pearlSlot == -1) {
                        this.setEnabled(false);
                        break;
                    }
                    int hotbarIndex = pearlSlot - 36;
                    this.savedHotbarSlot = inventory.v();
                    inventory.g(hotbarIndex);
                    KeyBinding useKey = Minecraft.gameSettings().b$src$Lgg_vape_wrapper_impl_KeyBinding_$1yi3362();
                    KeyBindingHelper.updateKeyBinding(useKey, true, true);
                    this.cooldownTimer.reset();
                    this.state = AutoPearlState.PENDING_RESET;
                    stopProcessing = true;
                    break;
                }
                case PENDING_RESET: {
                    KeyBinding useKey = Minecraft.gameSettings().b$src$Lgg_vape_wrapper_impl_KeyBinding_$1yi3362();
                    KeyBindingHelper.updateKeyBinding(useKey, false, false);
                    EntityPlayerSP localPlayer = Minecraft.thePlayer();
                    InventoryPlayer inventory = localPlayer.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6();
                    if (localPlayer.isNull() || inventory.isNull()) continue stateMachine;
                    inventory.g(this.savedHotbarSlot);
                    this.resetState();
                    if (!this.onBindMode.isSelected()) break;
                    this.setEnabled(false);
                    stopProcessing = true;
                }
            }
        } while (previousState != this.state && !stopProcessing);
    }
}
