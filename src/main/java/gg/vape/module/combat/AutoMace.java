package gg.vape.module.combat;

import gg.vape.event.Event;
import gg.vape.event.EventHandler;
import gg.vape.event.EventPriority;
import gg.vape.event.impl.EventKeyPress;
import gg.vape.event.impl.EventMouseButton;
import gg.vape.event.impl.EventPreTick;
import gg.vape.event.impl.SyntheticAttackRequestEvent;
import gg.vape.input.AttackKeyController;
import gg.vape.mapping.MappedClasses;
import gg.vape.module.Category;
import gg.vape.module.Mod;
import gg.vape.module.control.SharedModuleControlClaims;
import gg.vape.rotation.AdaptiveRotationController;
import gg.vape.rotation.MouseRotationController;
import gg.vape.rotation.PointRotationController;
import gg.vape.rotation.RotationControlClaim;
import gg.vape.rotation.RotationManager;
import gg.vape.unmap.ItemLimitData;
import gg.vape.unmap.ModeOption;
import gg.vape.unmap.ModeSelection;
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
import gg.vape.wrapper.impl.EnchantmentHelper;
import gg.vape.wrapper.impl.Entity;
import gg.vape.wrapper.impl.EntityLivingBase;
import gg.vape.wrapper.impl.EntityOtherPlayerMP;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.InventoryPlayer;
import gg.vape.wrapper.impl.Item;
import gg.vape.wrapper.impl.ItemStack;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.RayTraceResult;
import gg.vape.wrapper.impl.RayTraceResult_type;
import java.util.Arrays;

public class AutoMace extends Mod {
    private static final int MODULE_ID = -16732037;
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
    private MouseRotationController rotationController;
    private EntityLivingBase aimTarget;
    private boolean releasePending;
    private boolean dispatchingSyntheticAttack;
    private boolean swapActive;
    private boolean stunSlamActive;
    private boolean stunSlamFollowupPending;
    private int originalSlot = -1;
    private int maceSlot = -1;
    private int targetId = -1;
    private int swapTicks;
    private boolean armorSwapActive;
    private boolean armorSwapToElytra;
    private boolean waitingForBounce;
    private boolean sawDownwardMotion;
    private int armorOriginalSlot = -1;
    private int armorTargetSlot = -1;
    private int armorSwapTicks;

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

        this.maceSelection.addModeDependentValues(SELECTION_MANUAL, this.maceType);
        this.stunSlam.addDependentValues(this.stunSlamChance);
        this.aim.addDependentValues(this.silentAim, this.aimRange);
        this.attack.addDependentValues(this.extraDelay);
        this.autoUnequipElytra.addDependentValues(this.reEquipElytra);
        this.limitToItems.addDependentValues(this.allowedItems);
        this.addValue(this.targetFilter, this.aim, this.silentAim, this.aimRange, this.attack, this.extraDelay,
                this.autoUnequipElytra, this.reEquipElytra, this.smashOnly, this.maceSelection, this.maceType,
                this.stunSlam, this.stunSlamChance, this.limitToItems, this.allowedItems);
        this.rotationClaim.setPriority(this, 5);
    }

    @Override
    public String getSimpleSuffix() {
        return this.maceSelection.getValue().toString().equals(SELECTION_AUTO.toString())
                ? SELECTION_AUTO.toString() : this.maceType.getValue().toString();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onKeyPress(EventKeyPress event) {
        if (event.isKeybinding(Minecraft.gameSettings().F()) && event.isDown()) {
            this.handleAttack(event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMouseButton(EventMouseButton event) {
        if (event.isKeybinding(Minecraft.gameSettings().F()) && event.isDown()) {
            this.handleAttack(event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSyntheticAttack(SyntheticAttackRequestEvent event) {
        Mod source = event.getSource();
        if (source != this && !(source instanceof HitSwap) && !(source instanceof ShieldBreaker)) {
            this.handleAttack(event);
        }
    }

    @EventHandler
    public void onTick(EventPreTick event) {
        EntityPlayerSP player = event.getThePlayer();
        if (player.isNull()) {
            this.reset(false);
            return;
        }
        if (this.releasePending) {
            AttackKeyController.releaseAttackKey();
            this.releasePending = false;
        }
        this.updateSwap(player);
        this.updateAimAndAutoAttack(player);
        this.updateElytraSwap(player);
    }

    private void handleAttack(Event event) {
        if (this.swapActive || !this.canOperate()) {
            return;
        }
        EntityPlayerSP player = Minecraft.thePlayer();
        EntityLivingBase target = this.getCrosshairTarget();
        if (player.isNull() || target == null) {
            return;
        }
        int selectedMaceSlot = this.findBestMaceSlot(target, false);
        if (selectedMaceSlot < 0) {
            return;
        }
        InventoryPlayer inventory = player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6();
        if (selectedMaceSlot == inventory.v()) {
            return;
        }
        if (this.shouldStunSlam(target)) {
            ItemStack held = player.getHeldItemHand();
            if (this.isAxe(held)) {
                this.beginSwap(player, selectedMaceSlot, target.S(), false);
                this.stunSlamFollowupPending = true;
                return;
            }
            int axeSlot = this.findAxeSlot(player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6());
            if (axeSlot >= 0) {
                event.setCancelled(true);
                this.beginSwap(player, selectedMaceSlot, target.S(), true);
                player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6().g(axeSlot);
                this.stunSlamActive = true;
                this.releasePending = this.requestSyntheticAttack();
                return;
            }
        }
        event.setCancelled(true);
        this.beginSwap(player, selectedMaceSlot, target.S(), true);
        player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6().g(selectedMaceSlot);
        this.releasePending = this.requestSyntheticAttack();
    }

    private void beginSwap(EntityPlayerSP player, int selectedMaceSlot, int selectedTargetId, boolean countCurrentTick) {
        InventoryPlayer inventory = player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6();
        this.originalSlot = inventory.v();
        this.maceSlot = selectedMaceSlot;
        this.targetId = selectedTargetId;
        this.swapTicks = countCurrentTick ? 1 : 0;
        this.swapActive = true;
    }

    private void updateSwap(EntityPlayerSP player) {
        if (!this.swapActive) {
            return;
        }
        InventoryPlayer inventory = player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6();
        if (this.stunSlamFollowupPending && ++this.swapTicks >= 1) {
            inventory.g(this.maceSlot);
            this.releasePending = this.requestSyntheticAttack();
            this.stunSlamFollowupPending = false;
            return;
        }
        if (this.stunSlamActive && ++this.swapTicks >= 1) {
            Entity target = Minecraft.theWorld().V(this.targetId);
            if (target.isNotNull()) {
                inventory.g(this.maceSlot);
                this.releasePending = this.requestSyntheticAttack();
            }
            this.stunSlamActive = false;
            return;
        }
        if (++this.swapTicks > 3) {
            if (this.originalSlot >= 0) {
                inventory.g(this.originalSlot);
            }
            this.clearSwapState();
        }
    }

    private void updateAimAndAutoAttack(EntityPlayerSP player) {
        if (!this.isFalling(player) || Minecraft.currentScreen().isNotNull()) {
            this.releaseRotation();
            return;
        }
        EntityLivingBase target = this.findNearestTarget(player);
        if (target == null) {
            this.releaseRotation();
            return;
        }
        if (this.aim.getEffectiveValue().booleanValue()) {
            this.updateRotation(target);
        } else {
            this.releaseRotation();
        }
        if (!this.attack.getEffectiveValue().booleanValue() || this.swapActive || !this.inputTimer.hasTimeElapsed(50L)) {
            return;
        }
        if (this.aim.getEffectiveValue().booleanValue() && !this.isAimingAtTarget(target)) {
            return;
        }
        float cooldownOffset = (float)(-this.extraDelay.getRandomValue());
        if (!this.isAutoAttackReady(target, cooldownOffset)) {
            return;
        }
        int selectedMaceSlot = this.findBestMaceSlot(target, false);
        if (selectedMaceSlot < 0) {
            return;
        }
        InventoryPlayer inventory = player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6();
        if (selectedMaceSlot != inventory.v()) {
            this.beginSwap(player, selectedMaceSlot, target.S(), true);
            inventory.g(selectedMaceSlot);
        }
        this.releasePending = this.requestSyntheticAttack();
        this.inputTimer.reset();
    }

    private boolean isAutoAttackReady(EntityLivingBase target, float cooldownOffset) {
        if (RotationUtil.u(Minecraft.thePlayer()) && this.findBestMaceSlot(target, false) >= 0) {
            return true;
        }
        return AttackCooldownUtil.isAttackReady(cooldownOffset);
    }

    private boolean isFalling(EntityPlayerSP player) {
        return !player.b$src$Z$fqlxe4() && player.N() - player.W() < 0.0;
    }

    private void updateElytraSwap(EntityPlayerSP player) {
        if (this.armorSwapActive) {
            InventoryPlayer inventory = player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6();
            if (inventory.v() != this.armorTargetSlot) {
                this.clearArmorSwap(false);
                return;
            }
            if (++this.armorSwapTicks == 2 && Minecraft.currentScreen().isNull()) {
                Minecraft.F$src$V$aoypvc();
            }
            if (this.armorSwapTicks >= 4) {
                inventory.g(this.armorOriginalSlot);
                boolean equippedElytra = this.armorSwapToElytra;
                this.clearArmorSwap(false);
                if (!equippedElytra) {
                    this.waitingForBounce = true;
                    this.sawDownwardMotion = false;
                }
            }
            return;
        }
        double verticalMotion = player.N() - player.W();
        if (this.waitingForBounce) {
            if (verticalMotion < -0.05) {
                this.sawDownwardMotion = true;
            }
            if (this.sawDownwardMotion && verticalMotion > 0.05) {
                if (this.reEquipElytra.getEffectiveValue().booleanValue()) {
                    int elytraSlot = this.findElytraSlot(player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6());
                    if (elytraSlot >= 0) {
                        this.beginArmorSwap(player, elytraSlot, true);
                    }
                }
                this.waitingForBounce = false;
                this.sawDownwardMotion = false;
            }
            return;
        }
        if (!this.autoUnequipElytra.getEffectiveValue().booleanValue() || !player.Y$src$Z$154rldp()
                || verticalMotion >= 0.0 || this.swapActive) {
            return;
        }
        EntityLivingBase target = this.findNearestTarget(player);
        if (target == null || !this.canReachTargetSoon(player, target, verticalMotion)) {
            return;
        }
        InventoryPlayer inventory = player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6();
        int chestplateSlot = this.findChestplateSlot(inventory);
        if (chestplateSlot >= 0) {
            this.beginArmorSwap(player, chestplateSlot, false);
        }
    }

    private boolean canReachTargetSoon(EntityPlayerSP player, EntityLivingBase target, double verticalMotion) {
        double horizontalX = target.z() - player.z();
        double horizontalZ = target.h() - player.h();
        double horizontalDistance = Math.sqrt(horizontalX * horizontalX + horizontalZ * horizontalZ);
        double verticalDistance = Math.max(0.0, player.N() - target.N());
        double predictedDrop = Math.max(0.0, -verticalMotion) * 8.0 + 2.0;
        return horizontalDistance <= this.aimRange.getValue() && verticalDistance <= predictedDrop;
    }

    private void beginArmorSwap(EntityPlayerSP player, int slot, boolean toElytra) {
        InventoryPlayer inventory = player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6();
        this.armorOriginalSlot = inventory.v();
        this.armorTargetSlot = slot;
        this.armorSwapToElytra = toElytra;
        this.armorSwapTicks = 0;
        this.armorSwapActive = true;
        inventory.g(slot);
    }

    private int findChestplateSlot(InventoryPlayer inventory) {
        for (int slot = 0; slot < 9; ++slot) {
            ItemStack stack = inventory.c(slot);
            if (stack.isNotNull() && !this.isElytra(stack) && ItemStackScoreUtil.t(stack) == 1) {
                return slot;
            }
        }
        return -1;
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
        if (stack.isNull() || stack.getItem().isNull()) {
            return false;
        }
        Item elytra = Item.L("minecraft:elytra");
        return elytra != null && elytra.isNotNull() && stack.getItem().equals(elytra);
    }

    private void updateRotation(EntityLivingBase target) {
        boolean silent = this.silentAim.getEffectiveValue().booleanValue();
        if (!this.rotationClaim.isOwnedBy(this) && !this.rotationClaim.acquire(this, silent)) {
            return;
        }
        boolean needsAdaptiveController = silent;
        if (this.rotationController == null
                || needsAdaptiveController != (this.rotationController instanceof AdaptiveRotationController)) {
            if (this.rotationController != null
                    && RotationManager.INSTANCE.getActiveController() == this.rotationController) {
                RotationManager.INSTANCE.releaseController(this.rotationController);
            }
            this.rotationController = needsAdaptiveController
                    ? new AdaptiveRotationController()
                    : new PointRotationController(target.z(), target.N(), target.h());
            this.rotationController.setRetainAfterCompletion(true);
            this.rotationController.setTolerance(0.0f);
            this.rotationController.setSpeed(12.0f);
        }
        double minY = target.u$src$Lgg_vape_wrapper_impl_AxisAlignedBB_$kogbsu().getMinY();
        double maxY = target.u$src$Lgg_vape_wrapper_impl_AxisAlignedBB_$kogbsu().getMaxY();
        double targetY = (minY + maxY) * 0.5;
        if (this.rotationController instanceof AdaptiveRotationController) {
            AdaptiveRotationController adaptive = (AdaptiveRotationController)this.rotationController;
            adaptive.setRelativeMode(false);
            adaptive.setNormalizeTargetYaw(false);
            adaptive.setTarget(target.z(), targetY, target.h());
        } else {
            PointRotationController point = (PointRotationController)this.rotationController;
            point.setNormalizeYaw(false);
            point.setTarget(target.z(), targetY, target.h());
        }
        RotationManager.INSTANCE.setController(this.rotationController);
        this.aimTarget = target;
    }

    private boolean isAimingAtTarget(EntityLivingBase target) {
        RayTraceResult rayTrace = RotationManager.INSTANCE.getExtendedReachRayTrace();
        return rayTrace != null && rayTrace.isNotNull() && rayTrace.isEntityHit()
                && rayTrace.getEntity().isNotNull() && rayTrace.getEntity().equals(target);
    }

    private void releaseRotation() {
        this.aimTarget = null;
        this.rotationClaim.release(this);
        if (this.rotationController != null && RotationManager.INSTANCE.getActiveController() == this.rotationController) {
            RotationManager.INSTANCE.releaseController(this.rotationController);
        }
        this.rotationController = null;
    }

    private EntityLivingBase findNearestTarget(EntityPlayerSP player) {
        double range = this.aimRange.getValue();
        EntityLivingBase best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Object entityObject : Minecraft.theWorld().z()) {
            Entity entity = new Entity(entityObject);
            if (!this.targetFilter.isValidTarget(entity)) {
                continue;
            }
            EntityLivingBase candidate = new EntityLivingBase(entityObject);
            double horizontalX = candidate.z() - player.z();
            double horizontalZ = candidate.h() - player.h();
            double horizontalDistance = Math.sqrt(horizontalX * horizontalX + horizontalZ * horizontalZ);
            double distance = player.getDistanceToEntity(candidate);
            if (horizontalDistance > range || candidate.N() > player.N() + 1.0 || distance >= bestDistance) {
                continue;
            }
            best = candidate;
            bestDistance = distance;
        }
        return best;
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

    private boolean canOperate() {
        if (Minecraft.currentScreen().isNotNull()) {
            return false;
        }
        EntityPlayerSP player = Minecraft.thePlayer();
        return player.isNotNull() && (!this.limitToItems.getEffectiveValue().booleanValue()
                || this.allowedItems.isValid(player.getHeldItemHand(), false));
    }

    private boolean shouldStunSlam(EntityLivingBase target) {
        if (!this.stunSlam.getEffectiveValue().booleanValue()
                || Math.random() * 100.0 >= this.stunSlamChance.getValue()) {
            return false;
        }
        Entity entity = new Entity(target.getObject());
        return entity.isInstance(MappedClasses.lG)
                && RotationUtil.n(new EntityOtherPlayerMP(entity.getObject()));
    }

    private int findBestMaceSlot(EntityLivingBase target, boolean excludeSelected) {
        EntityPlayerSP player = Minecraft.thePlayer();
        InventoryPlayer inventory = player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6();
        int selectedSlot = inventory.v();
        int bestSlot = -1;
        double bestScore = -1.0;
        for (int slot = 0; slot < 9; ++slot) {
            if (excludeSelected && slot == selectedSlot) {
                continue;
            }
            ItemStack stack = inventory.c(slot);
            double score = this.scoreMace(stack, target);
            if (score <= bestScore) {
                continue;
            }
            bestScore = score;
            bestSlot = slot;
        }
        return bestSlot;
    }

    private double scoreMace(ItemStack stack, EntityLivingBase target) {
        if (!this.isMace(stack) || this.smashOnly.getEffectiveValue().booleanValue()
                && !RotationUtil.u(Minecraft.thePlayer())) {
            return -1.0;
        }
        int density = EnchantmentHelper.e("density", stack);
        int breach = EnchantmentHelper.e("breach", stack);
        if (this.maceSelection.getValue().toString().equals(SELECTION_MANUAL.toString())) {
            if (this.maceType.getValue().toString().equals(TYPE_DENSITY.toString())) {
                return density > 0 ? density : 0.5;
            }
            return breach > 0 ? breach : 0.5;
        }
        float fallDistance = Minecraft.thePlayer().getFallDistance();
        double densityScore = density > 0 ? this.baseSmashDamage(fallDistance) + density * fallDistance * 0.5 : -1.0;
        double breachScore = breach > 0 ? this.baseSmashDamage(fallDistance) + breach * 2.0 : -1.0;
        if (target != null && new Entity(target.getObject()).isInstance(MappedClasses.lG)
                && RotationUtil.n(new EntityOtherPlayerMP(target.getObject()))) {
            breachScore += 8.0;
        }
        return Math.max(densityScore, breachScore);
    }

    private double baseSmashDamage(float fallDistance) {
        if (fallDistance <= 3.0f) {
            return 6.0 + 4.0 * fallDistance;
        }
        if (fallDistance <= 8.0f) {
            return 18.0 + 2.0 * (fallDistance - 3.0f);
        }
        return 28.0 + fallDistance - 8.0f;
    }

    public boolean canHandleMaceAttack() {
        if (!this.stunSlam.getEffectiveValue().booleanValue()) {
            return false;
        }
        EntityPlayerSP player = Minecraft.thePlayer();
        return player.isNotNull() && (this.isAxe(player.getHeldItemHand())
                || this.findAxeSlot(player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6()) >= 0);
    }

    public boolean isSyntheticAttackInProgress() {
        return this.dispatchingSyntheticAttack;
    }

    private boolean requestSyntheticAttack() {
        this.dispatchingSyntheticAttack = true;
        try {
            return AttackKeyController.requestSyntheticAttack(this);
        } finally {
            this.dispatchingSyntheticAttack = false;
        }
    }

    public boolean hasReadyMace() {
        return this.findBestMaceSlot(this.getCrosshairTarget(), false) >= 0;
    }

    private int findAxeSlot(InventoryPlayer inventory) {
        int selectedSlot = inventory.v();
        for (int slot = 0; slot < 9; ++slot) {
            if (slot != selectedSlot && this.isAxe(inventory.c(slot))) {
                return slot;
            }
        }
        return -1;
    }

    private boolean isAxe(ItemStack stack) {
        return stack.isNotNull() && stack.getItem().isNotNull() && ItemStackScoreUtil.T(stack.getItem());
    }

    private boolean isMace(ItemStack stack) {
        if (stack.isNull() || stack.getItem().isNull()) {
            return false;
        }
        Item item = stack.getItem();
        return item.isInstance(MappedClasses.zx);
    }

    private void clearSwapState() {
        this.swapActive = false;
        this.stunSlamActive = false;
        this.stunSlamFollowupPending = false;
        this.originalSlot = -1;
        this.maceSlot = -1;
        this.targetId = -1;
        this.swapTicks = 0;
    }

    private void clearArmorSwap(boolean restoreSlot) {
        EntityPlayerSP player = Minecraft.thePlayer();
        if (restoreSlot && player.isNotNull() && this.armorOriginalSlot >= 0) {
            player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6().g(this.armorOriginalSlot);
        }
        this.armorSwapActive = false;
        this.armorSwapToElytra = false;
        this.armorOriginalSlot = -1;
        this.armorTargetSlot = -1;
        this.armorSwapTicks = 0;
    }

    private void reset(boolean restoreSlot) {
        EntityPlayerSP player = Minecraft.thePlayer();
        if (restoreSlot && player.isNotNull() && this.originalSlot >= 0) {
            player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6().g(this.originalSlot);
        }
        if (this.releasePending) {
            AttackKeyController.releaseAttackKey();
        }
        this.releasePending = false;
        this.dispatchingSyntheticAttack = false;
        this.clearSwapState();
        this.clearArmorSwap(true);
        this.waitingForBounce = false;
        this.sawDownwardMotion = false;
        this.releaseRotation();
    }

    @Override
    public void onDisable() {
        this.reset(true);
    }
}
