package gg.vape.module.combat;

import gg.vape.Vape;
import gg.vape.config.ClientSettings;
import gg.vape.mapping.MappedClasses;
import gg.vape.module.Category;
import gg.vape.module.Mod;
import gg.vape.module.combat.aimassist.AimAssistKissSubModule;
import gg.vape.module.combat.aimassist.AimAssistRotationSubModule;
import gg.vape.module.combat.aimassist.AimAssistTargetingSubModule;
import gg.vape.module.control.SharedModuleControlClaims;
import gg.vape.unmap.ItemLimitData;
import gg.vape.unmap.ModeOption;
import gg.vape.unmap.ModeSelection;
import gg.vape.utils.EntityAngleComparator;
import gg.vape.utils.EntityArmorValueComparator;
import gg.vape.utils.EntityDistanceComparator;
import gg.vape.utils.EntityEquipmentValueComparator;
import gg.vape.utils.EntityHealthComparator;
import gg.vape.utils.RayTraceUtil;
import gg.vape.utils.RotationUtil;
import gg.vape.value.BooleanValue;
import gg.vape.value.EntityTargetFilterValue;
import gg.vape.value.LimitValue;
import gg.vape.value.ModeValue;
import gg.vape.value.NumberValue;
import gg.vape.wrapper.impl.Entity;
import gg.vape.wrapper.impl.EntityLivingBase;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.ItemStack;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.PlayerControllerMP;
import gg.vape.wrapper.impl.RayTraceResult;
import gg.vape.wrapper.impl.RayTraceResult_type;
import gg.vape.wrapper.impl.WorldClient;
import java.util.ArrayList;
import java.util.Arrays;
import org.jetbrains.annotations.Nullable;

public class AimAssist
extends Mod {
    private int blockBreakCooldown = 0;
    private final BooleanValue strafeIncrease;
    public ModeValue targetArea;
    protected final ModeValue mode;
    public final ModeOption closestAreaMode;
    public final ModeOption centerMode;
    private final AimAssistTargetingSubModule adaptiveTargeting;
    public final ModeOption yawMode;
    public final ModeOption threatMode;
    private final NumberValue horizontalSpeed;
    private final AimAssistKissSubModule kissRotation = new AimAssistKissSubModule(this, "Kiss");
    private final AimAssistRotationSubModule simpleRotation = new AimAssistRotationSubModule(this, "Simple");
    private final LimitValue allowedItems;
    private final NumberValue maxAngle;
    private final BooleanValue limitToItems;
    public ModeValue targetMode;
    private final LimitValue blockBreakItems;
    private final BooleanValue aimVertically;
    public final ModeOption healthMode;
    private final BooleanValue breakBlocksWhitelist;
    private final BooleanValue checkBlockBreak;
    private final ModeOption distanceMode;
    private final EntityTargetFilterValue targetFilter;
    private final NumberValue verticalSpeed;
    private final BooleanValue requireMouseDown;
    public final ModeOption armorMode;
    private final NumberValue distance;

    @Nullable
    public EntityLivingBase findBestTarget() {
        WorldClient worldClient = Minecraft.theWorld();
        if (worldClient.isNull()) {
            return null;
        }
        ArrayList<EntityLivingBase> targets = new ArrayList<EntityLivingBase>();
        ArrayList<?> loadedEntities = new ArrayList<Object>(worldClient.z());
        for (Object entityObject : loadedEntities) {
            Entity entity = new Entity(entityObject);
            EntityLivingBase livingEntity;
            if (ClientSettings.IS_LEGACY_1_7 && entity.isInstance(MappedClasses.FT)
                    || !entity.isInstance(MappedClasses.zm)
                    || !this.isValidTarget(livingEntity = new EntityLivingBase(entityObject))) {
                continue;
            }
            targets.add(livingEntity);
        }
        if (this.targetMode.getValue() == this.yawMode) {
            targets.sort(new EntityAngleComparator());
        } else if (this.targetMode.getValue() == this.distanceMode) {
            targets.sort(new EntityDistanceComparator());
        } else if (this.targetMode.getValue() == this.threatMode) {
            targets.sort(new EntityArmorValueComparator());
        } else if (this.targetMode.getValue() == this.armorMode) {
            targets.sort(new EntityEquipmentValueComparator());
        } else if (this.targetMode.getValue() == this.healthMode) {
            targets.sort(new EntityHealthComparator());
        }
        if (!targets.isEmpty()) {
            return targets.get(0);
        }
        return null;
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

    public BooleanValue getStrafeIncrease() {
        return this.strafeIncrease;
    }

    public BooleanValue getRequireMouseDown() {
        return this.requireMouseDown;
    }

    public NumberValue getVerticalSpeed() {
        return this.verticalSpeed;
    }

    /** Kiss 子模块复用父模块的目标筛选（玩家/生物/和平、物品限制）。 */
    public boolean isTargetFiltered(EntityLivingBase target) {
        return this.passesItemFilter(target);
    }

    /** Kiss 子模块复用父模块的好友过滤。 */
    public boolean isFriendTarget(EntityLivingBase target) {
        return Vape.INSTANCE.getFriendManager().isFriend(target);
    }

    public boolean isValidTarget(EntityLivingBase target) {
        if (target.isNull()) {
            return false;
        }
        if (target.equals(Minecraft.thePlayer())) {
            return false;
        }
        if (target.w$src$F$15l9epb() <= 0.0f || target.M$src$Z$ff28xj()) {
            return false;
        }
        if (Minecraft.thePlayer().getDistanceToEntity(target) >= (float)((Double)this.distance.getValue()).intValue()) {
            return false;
        }
        if (RotationUtil.a(Minecraft.thePlayer(), target) > ((Double)this.maxAngle.getValue()).intValue() / 2) {
            return false;
        }
        if (Vape.INSTANCE.getFriendManager().isFriend(target)) {
            return false;
        }
        if (target.equals(Minecraft.thePlayer().S$src$Lgg_vape_wrapper_impl_Entity_$dgzs12())) {
            return false;
        }
        return this.passesItemFilter(target);
    }


    public NumberValue getHorizontalSpeed() {
        return this.horizontalSpeed;
    }

    private boolean hasRequiredItem() {
        if (!this.limitToItems.getEffectiveValue().booleanValue()) {
            return true;
        }
        ItemStack itemStack = Minecraft.thePlayer().getHeldItemHand();
        return this.allowedItems.isValid(itemStack, false);
    }

    public AimAssist() {
        super("AimAssist", -327674, Category.COMBAT, "Smoothly aims to closest valid target");
        this.adaptiveTargeting = new AimAssistTargetingSubModule(this, "Adaptive");
        this.mode = ModeValue.create((Object)this, "Mode", "Simple - Lightweight smooth aiming\nAdaptive - Advanced tracking with adaptive behavior\nKiss - KissClient AutoAim spring/smoothing controller", (ModeSelection)this.simpleRotation.getSelectionValue(), this.simpleRotation.getSelectionValue(), this.adaptiveTargeting.getSelectionValue(), this.kissRotation.getSelectionValue());
        this.targetFilter = EntityTargetFilterValue.createForModule(this);
        this.requireMouseDown = BooleanValue.create(this, "Require mouse down", true, "Only aim while mouse is down");
        this.aimVertically = BooleanValue.create(this, "Aim vertically", false, "Aims up and down as well");
        this.strafeIncrease = BooleanValue.create(this, "Strafe increase", false, "Increase speed while strafing away from target");
        this.checkBlockBreak = BooleanValue.create(this, "Check block break", false, "Prevents from aiming while breaking blocks");
        this.breakBlocksWhitelist = BooleanValue.create(this, "Break blocks whitelist", false);
        this.blockBreakItems = LimitValue.create(this, "aimassist-blockbreak-items", "Items", LimitValue.ALLOW_LIST_COLOR, Arrays.asList(new ItemLimitData("pickaxes"), new ItemLimitData("shovels")));
        this.limitToItems = BooleanValue.create(this, "Limit to items", false, "AimAssist functions only while holding selected items");
        this.allowedItems = LimitValue.create(this, "aimassist-alloweditems", "Allowed Items", LimitValue.ALLOW_LIST_COLOR, new ItemLimitData("swords"));
        this.verticalSpeed = NumberValue.create(this, "Vertical speed", "#.#", "", 1.0, 5.0, 10.0);
        this.horizontalSpeed = NumberValue.create(this, "Horizontal speed", "#.#", "", 1.0, 5.0, 10.0);
        this.maxAngle = NumberValue.create(this, "Max angle", "#", "", 1.0, 180.0, 360.0, 1.0, "Maximum allowed angle to still aim at target");
        this.distance = NumberValue.create(this, "Distance", "#.#", "", 1.0, 5.0, 8.0, 0.1, "Maximum distance allowed to still aim at target");
        this.distanceMode = new ModeOption("Distance");
        this.yawMode = new ModeOption("Yaw");
        this.armorMode = new ModeOption("Armor");
        this.threatMode = new ModeOption("Threat");
        this.healthMode = new ModeOption("Health");
        this.targetMode = ModeValue.create((Object)this, "Target mode", "How Aimassist should prioritize targets\nArmor/Threat will default to Distance for non player targets", (ModeSelection)this.yawMode, this.yawMode, this.distanceMode, this.armorMode, this.threatMode, this.healthMode);
        this.centerMode = new ModeOption("Center");
        this.closestAreaMode = new ModeOption("Closest");
        this.targetArea = ModeValue.create((Object)this, "Target area", "Where Aimassist will aim towards\nCenter: Center of entity\nClosest: Closest position on entity hitbox", (ModeSelection)this.centerMode, this.centerMode, this.closestAreaMode);
        this.aimVertically.addDependentValues(this.verticalSpeed);
        this.limitToItems.addDependentValues(this.allowedItems);
        this.limitToItems.setCompactListValue(this.allowedItems);
        this.breakBlocksWhitelist.setCompactListValue(this.blockBreakItems);
        this.breakBlocksWhitelist.addDependentValues(this.blockBreakItems);
        this.checkBlockBreak.addDependentValues(this.breakBlocksWhitelist);
        this.addValue(this.mode, this.targetFilter, this.requireMouseDown, this.strafeIncrease, this.checkBlockBreak, this.breakBlocksWhitelist, this.blockBreakItems, this.aimVertically, this.verticalSpeed, this.horizontalSpeed, this.maxAngle, this.distance, this.limitToItems, this.allowedItems, this.targetArea, this.targetMode);
        this.horizontalSpeed.setMaximumFractionDigits(0);

        // Kiss 模式自带 Range / FOV / Aim mode / Mouse down，父模块这些仅对 Simple/Adaptive 生效的设置
        // 在 Kiss 下隐藏，避免出现「距离/最大角度/水平速度」等重复项。
        ModeOption simpleModeOption = (ModeOption)this.simpleRotation.getSelectionValue();
        ModeOption adaptiveModeOption = (ModeOption)this.adaptiveTargeting.getSelectionValue();
        gg.vape.value.Value<?, ?>[] parentOnly = new gg.vape.value.Value<?, ?>[]{
                this.strafeIncrease, this.checkBlockBreak, this.breakBlocksWhitelist,
                this.blockBreakItems, this.aimVertically, this.verticalSpeed, this.horizontalSpeed,
                this.maxAngle, this.distance, this.limitToItems, this.allowedItems, this.targetArea,
                this.targetMode};
        this.mode.addModeDependentValues(simpleModeOption, parentOnly);
        this.mode.addModeDependentValues(adaptiveModeOption, parentOnly);

        // 把模式选择、目标筛选、鼠标按下排到设置列表最上方（子模块的值会先注册，把下拉框挤到下面）。
        java.util.List<gg.vape.value.Value<?, ?>> valueOrder = this.getValues();
        valueOrder.remove(this.mode);
        valueOrder.add(0, this.mode);
        valueOrder.remove(this.targetFilter);
        valueOrder.add(1, this.targetFilter);
        valueOrder.remove(this.requireMouseDown);
        valueOrder.add(2, this.requireMouseDown);
    }

    public BooleanValue getAimVertically() {
        return this.aimVertically;
    }

    @Override
    public String getDetailedSuffix() {
        return this.horizontalSpeed.getDisplayValue();
    }

    @Nullable
    public EntityLivingBase getCurrentTarget() {
        if (this.simpleRotation.isSelectedSubModule()) {
            return this.simpleRotation.getTarget();
        }
        if (this.adaptiveTargeting.isSelectedSubModule()) {
            return this.adaptiveTargeting.getTarget();
        }
        return null;
    }

    public NumberValue getMaxAngle() {
        return this.maxAngle;
    }

    public NumberValue getDistance() {
        return this.distance;
    }

    public boolean canAim() {
        EntityPlayerSP player = Minecraft.thePlayer();
        PlayerControllerMP playerController = Minecraft.playerController();
        if (player.isNull() || playerController.isNull()) {
            return false;
        }
        if (SharedModuleControlClaims.movementInput.isLocked()) {
            return false;
        }
        boolean checkCurrentBlock = this.checkBlockBreak.getEffectiveValue();
        if (checkCurrentBlock && this.breakBlocksWhitelist.getEffectiveValue().booleanValue()) {
            checkCurrentBlock = this.blockBreakItems.matches(player.getHeldItemHand());
        }
        if (checkCurrentBlock) {
            RayTraceResult mouseOver = RayTraceUtil.o();
            boolean aimingAtBlock = mouseOver.isNotNull() && mouseOver.getTypeOfHit().equals(RayTraceResult_type.block());
            if (aimingAtBlock) {
                this.blockBreakCooldown = 250;
                return false;
            }
            if (this.blockBreakCooldown > 0) {
                --this.blockBreakCooldown;
            }
            if (this.blockBreakCooldown > 0) {
                return false;
            }
        }
        return this.hasRequiredItem();
    }

}

