package gg.vape.module.kiss;

import gg.vape.event.EventHandler;
import gg.vape.event.impl.EventPreRenderTick;
import gg.vape.mapping.MappedClasses;
import gg.vape.module.Mod;
import gg.vape.module.SubModule;
import gg.vape.module.Category;
import gg.vape.module.none.ClientSettings;
import gg.vape.rotation.PlayerMouseRotationApplier;
import gg.vape.unmap.ModeOption;
import gg.vape.unmap.ModeSelection;
import gg.vape.utils.MathUtil;
import gg.vape.utils.Vec3d;
import gg.vape.value.BooleanValue;
import gg.vape.value.ModeValue;
import gg.vape.value.NumberValue;
import gg.vape.wrapper.impl.Entity;
import gg.vape.wrapper.impl.EntityLivingBase;
import gg.vape.wrapper.impl.EntityPlayer;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.GameProfile;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.PlayerInfo;
import gg.vape.wrapper.impl.RayTraceResult;
import gg.vape.wrapper.impl.Team;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * KissClient AutoAim（KissV1.4 可读源码 {@code com/kiss/module/modules/combat/AutoAim.java}）的等价移植。
 *
 * <p>Kiss 的自动瞄准分两套控制器：</p>
 * <ul>
 *   <li><b>New</b>（默认）：把目标角速度做前馈，再用二阶弹簧（omega² 与 2·omega）积分出 yaw/pitch
 *       角速度与角度，带 deadzone 与 predict 预判；</li>
 *   <li><b>Old</b>：把角度差除以 Smooth 得到本帧目标角，pitch 侧再做距离/速度自适应的额外平滑、EMA
 *       与 deadzone，并叠加 0.01° 的自然漂移。</li>
 * </ul>
 * <p>两者最后都会按鼠标灵敏度 GCD 对齐（{@code snapToSensitivity}）再写回玩家朝向。</p>
 *
 * <p>Vape 侧只把"写回朝向"换成 {@link PlayerMouseRotationApplier#setLocalPlayerRotation(float, float)}，
 * 目标搜索、预判、弹簧积分与 Old 模式的平滑全部按 Kiss 源码逐行移植。Kiss 的 Team Check 使用
 * scoreboard team 比较，NPC Check 使用"tab 列表里没有这个名字"的启发式（等价于 Kiss EntityClassifier
 * 的 STRICT tab 检查部分）。</p>
 */
public class KissAutoAim
extends Mod {
    private static final int MAX_LOST_TICKS = 20;
    private static final int MAX_NEW_LOST_FRAMES = 30;
    private static final float MAX_TARGET_ANG_VEL = 540.0f;
    private static final double MAX_TARGET_JUMP = 2.0;
    private static final double MAX_FRAME_DT = 0.1;
    private static final double Y_PREDICT_ALPHA = 0.3;
    private static final double NATURAL_OFFSET = 0.01;

    private final ModeSelection distanceMode = new ModeOption("Distance");
    private final ModeSelection healthMode = new ModeOption("Health");
    private final ModeSelection singleMode = new ModeOption("Single");
    private final ModeSelection newMode = new ModeOption("New");
    private final ModeSelection oldMode = new ModeOption("Old");

    private final ModeValue targetMode;
    private final ModeValue aimMode;
    private final NumberValue aimX;
    private final NumberValue aimY;
    private final NumberValue aimZ;
    private final NumberValue range;
    private final BooleanValue optimalRange;
    private final NumberValue fov;
    private final NumberValue smooth;
    private final NumberValue pitchSmooth;
    private final NumberValue yPredict;
    private final NumberValue yEmaAlpha;
    private final NumberValue yDeadzone;
    private final NumberValue newResponse;
    private final NumberValue newFeedforward;
    private final NumberValue newPredict;
    private final NumberValue newDeadzone;
    private final BooleanValue mouseDown;
    private final BooleanValue breakBlock;
    private final BooleanValue teamCheck;
    private final BooleanValue npcCheck;

    private EntityLivingBase target;
    private Object lockedTargetObject;
    private boolean wasAttackDown;

    private boolean yStateInitialized;
    private double lastTargetY;
    private double targetVelocityY;
    private float smoothedPitchTarget = Float.NaN;
    private int targetLostTicks;
    private double lastKnownVelocityY;
    private double curOffYaw;
    private double curOffPitch;
    private double tgtOffYaw;
    private double tgtOffPitch;
    private int offTimer;

    private boolean newInit;
    private Object newAimTargetObject;
    private Vec3d lastNewTargetPoint;
    private long lastNewTargetNanos;
    private long lastNewDesiredNanos;
    private float lastNewDesiredYaw;
    private float lastNewDesiredPitch;
    private float newYaw;
    private float newPitch;
    private float newYawVel;
    private float newPitchVel;
    private int newTargetLostFrames;
    private long lastNewFrameNanos;
    private float lastNewFrameDt;

    private static final long MOD_ID = 7719236450942083457L;

    public KissAutoAim() {
        super("Kiss AutoAim", (int)MOD_ID, Category.KISS, "AutoAim logic ported from KissClient: New (spring + feedforward + predict) and Old (per-frame smoothing) controllers.");
        this.targetMode = ModeValue.create(this, "Target mode", "How Kiss AutoAim prioritizes targets", this.distanceMode, this.healthMode, this.distanceMode, this.singleMode);
        this.aimMode = ModeValue.create(this, "Aim mode", "New: spring-mass tracking with feedforward and prediction.\nOld: per-frame smoothing with adaptive pitch.", this.newMode, this.newMode, this.oldMode);
        this.aimX = NumberValue.create(this, "Aim X", "#.##", "", -0.5, 0.0, 0.5, 0.01);
        this.aimY = NumberValue.create(this, "Aim Y", "#.##", "", 0.0, 0.85, 1.0, 0.01);
        this.aimZ = NumberValue.create(this, "Aim Z", "#.##", "", -0.5, 0.0, 0.5, 0.01);
        this.range = NumberValue.create(this, "Range", "#.#", "m", 1.0, 5.0, 30.0, 0.5);
        this.optimalRange = BooleanValue.create(this, "Optimal range", true);
        this.fov = NumberValue.create(this, "FOV", "#", "°", 1.0, 120.0, 360.0, 1.0);
        this.smooth = NumberValue.create(this, "Smooth", "#.#", "", 1.0, 20.0, 90.0, 1.0);
        this.pitchSmooth = NumberValue.create(this, "Pitch smooth", "#.#", "", 1.0, 12.0, 60.0, 1.0);
        this.yPredict = NumberValue.create(this, "Y predict", "#.##", "", 0.0, 0.6, 1.0, 0.05);
        this.yEmaAlpha = NumberValue.create(this, "Y EMA", "#.##", "", 0.05, 0.25, 1.0, 0.05);
        this.yDeadzone = NumberValue.create(this, "Y deadzone", "#.##", "°", 0.0, 0.5, 3.0, 0.05);
        this.newResponse = NumberValue.create(this, "New response", "#.#", "", 1.0, 12.0, 50.0, 0.5);
        this.newFeedforward = NumberValue.create(this, "New feedforward", "#.##", "", 0.0, 0.6, 1.0, 0.05);
        this.newPredict = NumberValue.create(this, "New predict", "#.#", "", 0.0, 1.0, 3.0, 0.1);
        this.newDeadzone = NumberValue.create(this, "New deadzone", "#.##", "°", 0.0, 0.1, 2.0, 0.05);
        this.mouseDown = BooleanValue.create(this, "Mouse down", true);
        this.breakBlock = BooleanValue.create(this, "Break Block", true);
        this.teamCheck = BooleanValue.create(this, "Team Check", true);
        this.npcCheck = BooleanValue.create(this, "NPC Check", true);
        this.addValue(this.targetMode, this.aimMode, this.aimX, this.aimY, this.aimZ, this.range, this.optimalRange,
                this.fov, this.smooth, this.pitchSmooth, this.yPredict, this.yEmaAlpha, this.yDeadzone,
                this.newResponse, this.newFeedforward, this.newPredict, this.newDeadzone, this.mouseDown,
                this.breakBlock, this.teamCheck, this.npcCheck);
    }

    @Override
    public void onEnable() {
        this.resetState();
    }

    @Override
    public void onDisable() {
        this.target = null;
        this.lockedTargetObject = null;
        this.resetState();
    }

    private void resetState() {
        this.yStateInitialized = false;
        this.lastTargetY = 0.0;
        this.targetVelocityY = 0.0;
        this.smoothedPitchTarget = Float.NaN;
        this.targetLostTicks = 0;
        this.lastKnownVelocityY = 0.0;
        this.newInit = false;
        this.lastNewTargetPoint = null;
        this.lastNewTargetNanos = 0L;
        this.lastNewDesiredNanos = 0L;
        this.newYawVel = 0.0f;
        this.newPitchVel = 0.0f;
        this.newTargetLostFrames = 0;
        this.newAimTargetObject = null;
        this.lastNewFrameNanos = 0L;
        this.lastNewFrameDt = 0.0f;
    }

    @EventHandler
    public void onPreRenderTick(EventPreRenderTick event) {
        if (event.getWorld().isNull()) {
            return;
        }
        EntityPlayerSP player = Minecraft.thePlayer();
        if (player.isNull() || Minecraft.currentScreen().getObject() != null) {
            return;
        }
        if (!ClientSettings.INSTANCE.inputEnabled) {
            this.resetState();
            return;
        }
        boolean attackDown = gg.vape.config.ClientSettings.isAttackButtonDown();
        if (this.mouseDown.getEffectiveValue() && !attackDown) {
            this.wasAttackDown = false;
            this.target = null;
            this.resetState();
            return;
        }

        boolean single = this.targetMode.getValue() == this.singleMode;
        if (single && attackDown && !this.wasAttackDown) {
            this.lockedTargetObject = null;
            this.resetState();
        }
        this.wasAttackDown = attackDown;
        if (!this.breakBlock.getEffectiveValue() && attackDown && this.isCrosshairOnBlock()) {
            this.target = null;
            return;
        }

        EntityLivingBase found = this.findTarget(this.range.getValue(), this.fov.getValue());
        if (found == null) {
            if (this.aimMode.getValue() == this.oldMode) {
                this.handleTargetLost();
            } else {
                this.handleNewTargetLost();
            }
            this.target = null;
            return;
        }
        this.target = found;
        this.targetLostTicks = 0;

        Vec3d eye = this.eyePosition(player);
        Vec3d targetPoint = this.getTargetPoint(found);
        float currentYaw = player.J();
        float currentPitch = player.V();
        if (this.aimMode.getValue() == this.newMode) {
            if (this.newAimTargetObject != found.getObject()) {
                this.newAimTargetObject = found.getObject();
                this.lastNewTargetPoint = null;
                this.lastNewDesiredNanos = 0L;
            }
            this.runNewAim(targetPoint, currentYaw, currentPitch);
        } else {
            this.runOldAim(found, targetPoint, eye, currentYaw, currentPitch);
        }
    }

    private EntityLivingBase findTarget(double maxRange, double fovDegrees) {
        EntityPlayerSP player = Minecraft.thePlayer();
        List<EntityLivingBase> candidates = new ArrayList<EntityLivingBase>();
        for (Object object : new ArrayList<Object>(Minecraft.theWorld().z())) {
            Entity wrapper = new Entity(object);
            if (wrapper.isNull() || !wrapper.isInstance(MappedClasses.Yl)) {
                continue;
            }
            EntityLivingBase living = new EntityLivingBase(object);
            if (living.equals(player) || !this.isValidTarget(living, maxRange)) {
                continue;
            }
            candidates.add(living);
        }
        if (candidates.isEmpty()) {
            return null;
        }
        if (this.targetMode.getValue() == this.singleMode && this.lockedTargetObject != null) {
            for (EntityLivingBase candidate : candidates) {
                if (this.lockedTargetObject == candidate.getObject()
                        && this.yawToTarget(candidate, player) <= fovDegrees) {
                    return candidate;
                }
            }
            this.lockedTargetObject = null;
        }
        EntityLivingBase best = null;
        double bestValue = Double.MAX_VALUE;
        for (EntityLivingBase candidate : candidates) {
            double yawDiff = this.yawToTarget(candidate, player);
            if (yawDiff > fovDegrees) {
                continue;
            }
            double compare;
            if (this.targetMode.getValue() == this.healthMode) {
                compare = candidate.w$src$F$15l9epb();
            } else if (this.targetMode.getValue() == this.singleMode) {
                compare = this.distanceTo(candidate, player);
            } else {
                compare = yawDiff;
            }
            if (compare < bestValue) {
                bestValue = compare;
                best = candidate;
            }
        }
        if (this.targetMode.getValue() == this.singleMode && best != null) {
            this.lockedTargetObject = best.getObject();
        }
        return best;
    }

    private boolean isValidTarget(EntityLivingBase candidate, double maxRange) {
        if (candidate.isNull() || candidate.M$src$Z$ff28xj() || candidate.w$src$F$15l9epb() <= 0.0f) {
            return false;
        }
        if (candidate.equals(Minecraft.thePlayer())) {
            return false;
        }
        if (this.teamCheck.getEffectiveValue() && this.isSameTeam(candidate)) {
            return false;
        }
        if (this.npcCheck.getEffectiveValue() && this.isNpc(candidate)) {
            return false;
        }
        return this.distanceTo(candidate, Minecraft.thePlayer()) <= maxRange;
    }

    /**
     * Kiss 的 Break Block：关掉后，按住左键且准星指着方块（也就是在挖方块）时不介入瞄准。
     */
    private boolean isCrosshairOnBlock() {
        try {
            RayTraceResult hit = Minecraft.p$src$Lgg_vape_wrapper_impl_RayTraceResult_$5rw6n0();
            return hit != null && !hit.isNull() && hit.isBlockHit();
        } catch (Throwable throwable) {
            return false;
        }
    }

    /**
     * Kiss 的 Team Check：两侧都在 scoreboard 里同一个 Team 时视为队友。
     */
    private boolean isSameTeam(EntityLivingBase candidate) {
        try {
            Object selfHandle = Minecraft.thePlayer().getObject();
            if (candidate.getObject() == selfHandle) {
                return false;
            }
            Team selfTeam = new EntityPlayer(selfHandle).J$src$Lgg_vape_wrapper_impl_Team_$1jrmnx4();
            Team otherTeam = new EntityPlayer(candidate.getObject()).J$src$Lgg_vape_wrapper_impl_Team_$1jrmnx4();
            if (selfTeam == null || selfTeam.isNull() || otherTeam == null || otherTeam.isNull()) {
                return false;
            }
            return selfTeam.isSameTeam(otherTeam);
        } catch (Throwable throwable) {
            return false;
        }
    }

    /**
     * Kiss 的 NPC Check（STRICT）：显示名带 [NPC] 标记，或者 tab 列表里查不到这个名字。
     */
    private boolean isNpc(EntityLivingBase candidate) {
        String name = this.displayNameOf(candidate);
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("[npc]") || lower.contains("npc")) {
            return true;
        }
        try {
            Collection<?> entries = Minecraft.thePlayer().sendQueue().getPlayerInfoMap();
            if (entries == null) {
                return false;
            }
            for (Object entry : entries) {
                PlayerInfo info = new PlayerInfo(entry);
                GameProfile profile = info.v();
                if (profile == null || profile.isNull()) {
                    continue;
                }
                String profileName = profile.getName();
                if (profileName != null && profileName.equalsIgnoreCase(name)) {
                    return false;
                }
            }
            return true;
        } catch (Throwable throwable) {
            return false;
        }
    }

    private String displayNameOf(EntityLivingBase candidate) {
        try {
            String raw = candidate.getName();
            if (raw == null) {
                return "";
            }
            String cleaned = raw.replace("§", "");
            int bracket = cleaned.lastIndexOf(93);
            if (bracket >= 0 && bracket + 1 < cleaned.length()) {
                cleaned = cleaned.substring(bracket + 1);
            }
            return cleaned.trim();
        } catch (Throwable throwable) {
            return "";
        }
    }

    private double distanceTo(EntityLivingBase candidate, EntityPlayerSP player) {
        boolean optimal = this.optimalRange.getEffectiveValue() && this.aimMode.getValue() == this.newMode;
        if (optimal) {
            Vec3d eye = this.eyePosition(player);
            double dx = candidate.z() - eye.getX();
            double dy = candidate.N() - eye.getY();
            double dz = candidate.h() - eye.getZ();
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
        return player.getDistanceToEntity(candidate);
    }

    private double yawToTarget(EntityLivingBase candidate, EntityPlayerSP player) {
        Vec3d eye = this.eyePosition(player);
        Vec3d point = this.getTargetPoint(candidate);
        float yaw = (float)(Math.toDegrees(Math.atan2(point.getZ() - eye.getZ(), point.getX() - eye.getX())) - 90.0);
        return Math.abs(wrapDegrees(yaw - player.J()));
    }

    private Vec3d eyePosition(EntityPlayerSP player) {
        return new Vec3d(player.z(), player.N() + player.X(), player.h());
    }

    private Vec3d getTargetPoint(EntityLivingBase candidate) {
        double height = candidate.Y();
        double width = candidate.R$src$Lgg_vape_wrapper_impl_AxisAlignedBB_$r19dfl().getMaxX()
                - candidate.R$src$Lgg_vape_wrapper_impl_AxisAlignedBB_$r19dfl().getMinX();
        return new Vec3d(candidate.z() + this.aimX.getValue() * width,
                candidate.N() + this.aimY.getValue() * height,
                candidate.h() + this.aimZ.getValue() * width);
    }

    // ------------------------------------------------------------------ Old mode

    private void runOldAim(EntityLivingBase living, Vec3d targetPoint, Vec3d eye, float currentYaw, float currentPitch) {
        float[] rot = this.rotationTo(eye, targetPoint);
        float yawDelta = wrapDegrees(rot[0] - currentYaw);
        this.updateNaturalOffset();
        float targetYaw = currentYaw + (float)((yawDelta + this.curOffYaw) / Math.max(this.smooth.getValue(), 1.0));
        float targetPitch = this.computeOptimizedPitch(living, targetPoint, eye, currentPitch);
        this.applyRotation(targetYaw, targetPitch);
    }

    private float computeOptimizedPitch(EntityLivingBase living, Vec3d targetPoint, Vec3d eye, float currentPitch) {
        double targetY = targetPoint.getY();
        if (this.yStateInitialized) {
            double deltaY = targetY - this.lastTargetY;
            this.targetVelocityY = this.targetVelocityY * (1.0 - Y_PREDICT_ALPHA) + deltaY * 20.0 * Y_PREDICT_ALPHA;
        } else {
            this.yStateInitialized = true;
        }
        this.lastTargetY = targetY;
        double predictedY = targetY + this.targetVelocityY * this.yPredict.getValue() / 20.0;
        float rawPitchTarget = this.rotationTo(eye, new Vec3d(targetPoint.getX(), predictedY, targetPoint.getZ()))[1];
        double dynamicSmooth = this.computeDynamicPitchSmooth(living);
        float smoothPitch = currentPitch + (float)((rawPitchTarget - currentPitch) / Math.max(dynamicSmooth, 1.0));
        if (Float.isNaN(this.smoothedPitchTarget)) {
            this.smoothedPitchTarget = currentPitch;
        }
        double alpha = this.yEmaAlpha.getValue();
        this.smoothedPitchTarget = (float)(this.smoothedPitchTarget * (1.0 - alpha) + smoothPitch * alpha);
        double deadzone = this.yDeadzone.getValue();
        float pitchDiff = this.smoothedPitchTarget - currentPitch;
        return Math.abs(pitchDiff) < deadzone ? currentPitch : this.smoothedPitchTarget + (float)this.curOffPitch;
    }

    private double computeDynamicPitchSmooth(EntityLivingBase living) {
        double base = this.pitchSmooth.getValue();
        double distance = Minecraft.thePlayer().getDistanceToEntity(living);
        double distanceFactor;
        if (distance < 5.0) {
            distanceFactor = 0.8;
        } else if (distance > 20.0) {
            distanceFactor = 1.2;
        } else {
            distanceFactor = 0.8 + (distance - 5.0) / 15.0 * 0.4;
        }
        double absVelY = Math.abs(this.targetVelocityY);
        double speedFactor = 1.0;
        if (absVelY > 1.0) {
            speedFactor = Math.max(0.6, 1.0 - (absVelY - 1.0) * 0.1);
        }
        return base * distanceFactor * speedFactor;
    }

    private void updateNaturalOffset() {
        if (--this.offTimer <= 0) {
            this.tgtOffYaw = -NATURAL_OFFSET + Math.random() * 2.0 * NATURAL_OFFSET;
            this.tgtOffPitch = -NATURAL_OFFSET + Math.random() * 2.0 * NATURAL_OFFSET;
            this.offTimer = 20 + (int)(Math.random() * 15.0);
        }
        this.curOffYaw += (this.tgtOffYaw - this.curOffYaw) * 0.1;
        this.curOffPitch += (this.tgtOffPitch - this.curOffPitch) * 0.1;
    }

    private void handleTargetLost() {
        EntityPlayerSP player = Minecraft.thePlayer();
        if (++this.targetLostTicks == 1) {
            this.lastKnownVelocityY = this.targetVelocityY;
            this.smoothedPitchTarget = player.V();
        } else if (this.targetLostTicks > MAX_LOST_TICKS) {
            this.resetState();
        } else {
            this.smoothedPitchTarget = (float)(this.smoothedPitchTarget * 0.95 + player.V() * 0.05);
        }
    }

    // ------------------------------------------------------------------ New mode

    private void handleNewTargetLost() {
        ++this.newTargetLostFrames;
        this.lastNewTargetPoint = null;
        this.lastNewDesiredNanos = 0L;
        this.newYawVel *= 0.5f;
        this.newPitchVel *= 0.5f;
        if (this.newTargetLostFrames > MAX_NEW_LOST_FRAMES) {
            this.newInit = false;
        }
    }

    private void runNewAim(Vec3d targetPoint, float currentYaw, float currentPitch) {
        this.newTargetLostFrames = 0;
        long now = System.nanoTime();
        double elapsed = Math.max((now - this.lastNewFrameNanos) / 1.0E9, 0.0);
        boolean gapFrame = elapsed > 0.05 && this.lastNewFrameDt < 0.05;
        this.lastNewFrameDt = (float)elapsed;
        if (gapFrame) {
            this.newYaw = currentYaw;
            this.newPitch = currentPitch;
            this.newYawVel = 0.0f;
            this.newPitchVel = 0.0f;
        }
        double dt = this.newInit ? (gapFrame ? 1.0 / 60.0 : Math.min(elapsed, MAX_FRAME_DT)) : 1.0 / 60.0;
        this.lastNewFrameNanos = now;
        Vec3d eye = this.eyePosition(Minecraft.thePlayer());
        if (this.lastNewTargetPoint != null) {
            double targetDt = Math.max((now - this.lastNewTargetNanos) / 1.0E9, 1.0E-6);
            double vx = (targetPoint.getX() - this.lastNewTargetPoint.getX()) / targetDt;
            double vy = (targetPoint.getY() - this.lastNewTargetPoint.getY()) / targetDt;
            double vz = (targetPoint.getZ() - this.lastNewTargetPoint.getZ()) / targetDt;
            double predict = this.newPredict.getValue();
            if (predict > 0.0 && targetPoint.distanceTo(this.lastNewTargetPoint) <= MAX_TARGET_JUMP) {
                targetPoint = new Vec3d(targetPoint.getX() + vx * predict * 0.05,
                        targetPoint.getY() + vy * predict * 0.05,
                        targetPoint.getZ() + vz * predict * 0.05);
            }
        }
        this.lastNewTargetPoint = targetPoint;
        this.lastNewTargetNanos = now;

        float[] desired = this.rotationTo(eye, targetPoint);
        if (!this.newInit) {
            this.newYaw = currentYaw;
            this.newPitch = currentPitch;
            this.newYawVel = 0.0f;
            this.newPitchVel = 0.0f;
            this.newInit = true;
            this.lastNewDesiredYaw = desired[0];
            this.lastNewDesiredPitch = desired[1];
            this.lastNewDesiredNanos = now;
            return;
        }
        float targetAngVelYaw = 0.0f;
        float targetAngVelPitch = 0.0f;
        if (this.lastNewDesiredNanos != 0L) {
            double desiredDt = Math.max((now - this.lastNewDesiredNanos) / 1.0E9, 1.0E-6);
            targetAngVelYaw = MathUtil.clamp(wrapDegrees(desired[0] - this.lastNewDesiredYaw) / (float)desiredDt,
                    -MAX_TARGET_ANG_VEL, MAX_TARGET_ANG_VEL);
            targetAngVelPitch = MathUtil.clamp((desired[1] - this.lastNewDesiredPitch) / (float)desiredDt,
                    -MAX_TARGET_ANG_VEL, MAX_TARGET_ANG_VEL);
        }
        this.lastNewDesiredYaw = desired[0];
        this.lastNewDesiredPitch = desired[1];
        this.lastNewDesiredNanos = now;

        float deadzone = this.newDeadzone.getValue().floatValue();
        float deltaYaw = wrapDegrees(desired[0] - this.newYaw);
        float deltaPitch = desired[1] - this.newPitch;
        if (Math.abs(deltaYaw) < deadzone) {
            deltaYaw = 0.0f;
        }
        if (Math.abs(deltaPitch) < deadzone) {
            deltaPitch = 0.0f;
        }
        double omega = Math.max(0.5, this.newResponse.getValue());
        double omega2 = omega * omega;
        float feedforward = this.newFeedforward.getValue().floatValue();
        float feedYaw = targetAngVelYaw * feedforward;
        float feedPitch = targetAngVelPitch * feedforward;
        float accelYaw = (float)(omega2 * deltaYaw - 2.0 * omega * (this.newYawVel - feedYaw));
        float accelPitch = (float)(omega2 * deltaPitch - 2.0 * omega * (this.newPitchVel - feedPitch));
        this.newYawVel += accelYaw * (float)dt;
        this.newPitchVel += accelPitch * (float)dt;
        this.newYaw = wrapDegrees(this.newYaw + this.newYawVel * (float)dt);
        this.newPitch = MathUtil.clamp(this.newPitch + this.newPitchVel * (float)dt, -90.0f, 90.0f);
        this.applyRotation(this.newYaw, this.newPitch);
    }

    // ------------------------------------------------------------------ helpers

    /** Kiss {@code rotationTo}: yaw/pitch from eye to point (yaw first). */
    private float[] rotationTo(Vec3d from, Vec3d to) {
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        double distXZ = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float)(-Math.toDegrees(Math.atan2(dy, distXZ)));
        return new float[]{yaw, pitch};
    }

    private void applyRotation(float absoluteYaw, float absolutePitch) {
        float gcd = this.gcdStep();
        float snappedYaw = Math.round(absoluteYaw / gcd) * gcd;
        float snappedPitch = Math.round(absolutePitch / gcd) * gcd;
        snappedPitch = MathUtil.clamp(snappedPitch, -90.0f, 90.0f);
        PlayerMouseRotationApplier.setLocalPlayerRotation(snappedYaw, snappedPitch);
    }

    private float gcdStep() {
        float sensitivity = Minecraft.gameSettings().y();
        float base = sensitivity * 0.6f + 0.2f;
        return base * base * base * 8.0f * 0.15f;
    }

    private static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0f;
        if (wrapped >= 180.0f) {
            wrapped -= 360.0f;
        }
        if (wrapped < -180.0f) {
            wrapped += 360.0f;
        }
        return wrapped;
    }
}
