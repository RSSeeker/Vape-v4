package gg.vape.module.combat;

import gg.vape.Vape;
import gg.vape.config.ClientSettings;
import gg.vape.event.Event;
import gg.vape.event.EventHandler;
import gg.vape.event.impl.EventKeyPress;
import gg.vape.event.impl.EventMouseButton;
import gg.vape.event.impl.EventPreTick;
import gg.vape.input.AttackKeyController;
import gg.vape.input.InputEventDispatcher;
import gg.vape.mapping.MappedClasses;
import gg.vape.module.Category;
import gg.vape.module.Mod;
import gg.vape.rotation.RotationManager;
import gg.vape.utils.AttackCooldownUtil;
import gg.vape.utils.RotationUtil;
import gg.vape.utils.TimerUtil;
import gg.vape.value.BooleanValue;
import gg.vape.value.EntityTargetFilterValue;
import gg.vape.value.LimitValue;
import gg.vape.value.NumberValue;
import gg.vape.value.RandomValue;
import gg.vape.wrapper.impl.Entity;
import gg.vape.wrapper.impl.EntityOtherPlayerMP;
import gg.vape.wrapper.impl.EnumHand;
import gg.vape.wrapper.impl.ItemStack;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.RayTraceResult;
import gg.vape.wrapper.impl.RayTraceResult_type;
import java.util.Collections;
import java.util.Random;

public class Triggerbot
extends Mod {
    public final EntityTargetFilterValue targetFilter = EntityTargetFilterValue.createForModule(this);
    private final TimerUtil attackTimer;
    private volatile boolean waitingForFirstHit = false;
    public final NumberValue earlyHitChance;
    private boolean missAttackPending = false;
    private final BooleanValue selectFirstHit;
    private int lastHurtTime = 0;
    public final BooleanValue shieldCheck;
    private final TimerUtil selectTimer;
    public final LimitValue allowedItems;
    private boolean graceActive = false;
    private long mouseOverDelayMs = 0L;
    private volatile boolean mouseDown = false;
    private volatile int selectTargetId = -1;
    private static final long MODULE_ID = -4105462348079232239L;
    public final BooleanValue requireMouseDown;
    private final TimerUtil mouseOverTimer;
    private final Random random;
    private volatile boolean releasePending = false;
    private final Object requestLock = new Object();
    private long requestEpoch;
    private volatile boolean requestInFlight;
    private final RandomValue extraDelay = RandomValue.createWithDescription(this, "Extra delay", "#", "ticks", -20.0, 0.0, 0.0, 20.0, 0.1, "Extra delay after attack cooldown(in ticks)\nNegative values will attack before cooldown is complete");
    public final NumberValue targetMissChance;
    public final BooleanValue airCrits;
    private volatile int mouseOverTargetId = -1;
    private final RandomValue mouseOverDelayValue = RandomValue.createWithDescription(this, "Mouse over delay", "#", "ms", 0.0, 0.0, 0.0, 200.0, 10.0, "Delay after your crosshair reaches a target before attacking");
    private int earlyHitTicks = 0;
    public final BooleanValue ignoreActivationClick;
    public final BooleanValue limitToItems;

    @EventHandler
    public void onTick(EventPreTick event) {
        boolean releasedThisTick = this.releasePendingAttack();
        if (event.getThePlayer().isNull()) {
            this.resetMouseOverState();
            this.resetSelectState();
            this.lastHurtTime = 0;
            return;
        }
        int hurtTime = event.getThePlayer().c$src$I$15a9iwo();
        boolean wasJustHurt = this.lastHurtTime <= 0 && hurtTime > 0;
        this.lastHurtTime = hurtTime;
        if (this.requireMouseDown.getEffectiveValue().booleanValue() && this.mouseDown && !ClientSettings.isKeyBindingDown(Minecraft.gameSettings().F())) {
            this.mouseDown = false;
            this.resetSelectState();
        }
        if (this.selectFirstHit.getEffectiveValue() && this.waitingForFirstHit
                && this.selectTargetId != -1 && wasJustHurt) {
            this.waitingForFirstHit = false;
        }
        if (!this.canAttack(true)) {
            this.resetMouseOverState();
            return;
        }

        // Track crosshair changes even while the attack timer or cooldown is
        // blocking a hit. Otherwise a brief miss can retain an old delay.
        RayTraceResult crosshairHit = RotationManager.INSTANCE.getExtendedReachRayTrace();
        boolean entityHit = crosshairHit != null && crosshairHit.isNotNull()
                && crosshairHit.getTypeOfHit().equals(RayTraceResult_type.entity());
        Entity entity = entityHit ? crosshairHit.getEntity() : null;
        boolean validTarget = entity != null && entity.isNotNull() && this.isValidTarget(entity);
        boolean selectReady = false;
        boolean delayReady = false;
        if (validTarget) {
            selectReady = this.updateSelectFirstHit(entity, wasJustHurt);
            delayReady = this.hasMouseOverDelayElapsed(entity);
        } else {
            this.resetMouseOverState();
            this.updateSelectGrace();
        }
        if (releasedThisTick || this.requestInFlight || !this.attackTimer.hasTimeElapsed(50L)) {
            return;
        }
        float cooldownOffset = (float)(-this.extraDelay.getRandomValue()) + this.earlyHitTicks;
        if (validTarget && selectReady && delayReady && this.isAttackReady(cooldownOffset)) {
            this.queueAttack(entity.getObject(), false, cooldownOffset);
        } else if (!entityHit && this.missAttackPending && this.isSelectFirstHitSatisfied()
                && this.isAttackReady(cooldownOffset)) {
            this.missAttackPending = false;
            this.queueAttack(null, true, cooldownOffset);
        }
    }

    private boolean releasePendingAttack() {
        boolean release;
        synchronized (this.requestLock) {
            release = this.releasePending;
            this.releasePending = false;
        }
        if (release) AttackKeyController.releaseAttackKey();
        return release;
    }

    private void queueAttack(Object expectedTarget, boolean intentionalMiss, float cooldownOffset) {
        final long epoch;
        final Object playerObject = Minecraft.thePlayer().getObject();
        synchronized (this.requestLock) {
            if (this.requestInFlight) return;
            epoch = this.requestEpoch;
            this.requestInFlight = true;
        }
        AttackKeyController.releaseAttackKey();
        AttackKeyController.requestValidatedSyntheticAttack(this, () -> {
            synchronized (this.requestLock) {
                if (epoch != this.requestEpoch || !this.requestInFlight) return false;
            }
            if (!this.isEnabled() || Minecraft.thePlayer().isNull()
                    || Minecraft.thePlayer().getObject() != playerObject || !this.canAttack(true)
                    || !this.isSelectFirstHitSatisfied() || !this.isAttackReady(cooldownOffset)) return false;
            RayTraceResult hit = RotationManager.INSTANCE.getExtendedReachRayTrace();
            boolean entityHit = hit != null && hit.isNotNull()
                    && hit.getTypeOfHit().equals(RayTraceResult_type.entity());
            if (intentionalMiss) return !entityHit;
            if (!entityHit) return false;
            Entity entity = hit.getEntity();
            return entity.isNotNull() && entity.getObject() == expectedTarget
                    && entity.S() == this.mouseOverTargetId && this.isValidTarget(entity);
        }, pressed -> {
            boolean releaseLate = false;
            synchronized (this.requestLock) {
                if (epoch != this.requestEpoch) {
                    releaseLate = pressed;
                } else {
                    this.requestInFlight = false;
                    this.releasePending = pressed;
                }
            }
            if (releaseLate) AttackKeyController.releaseAttackKey();
        });
    }

    private void resetMouseOverState() {
        this.mouseOverTargetId = -1;
        this.mouseOverDelayMs = 0L;
        this.mouseOverTimer.reset();
    }

    private boolean isValidTarget(Entity entity) {
        if (this.shieldCheck.getEffectiveValue()) {
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
            if (enforceShieldCheck && entity.isInstance(MappedClasses.lG)
                    && RotationUtil.n(new EntityOtherPlayerMP(entity.getObject()))) {
                return false;
            }
        }
        return this.targetFilter.isValidTarget(entity);
    }

    private boolean isAttackReady(float cooldownOffset) {
        if (this.canBypassCooldown()) {
            return true;
        }
        return AttackCooldownUtil.isAttackReady(cooldownOffset);
    }

    private boolean canAttack(boolean requireActivation) {
        if (Minecraft.currentScreen().isNotNull() || !InputEventDispatcher.getInstance().getFocusState().isFocused()) {
            return false;
        }
        if (requireActivation && (!InputEventDispatcher.getInstance().getFocusState().isFocused()
                || this.requireMouseDown.getEffectiveValue() && !this.mouseDown)) {
            return false;
        }
        ItemStack heldItem = Minecraft.thePlayer().getHeldItemHand();
        if (this.limitToItems.getEffectiveValue() && !this.allowedItems.isValid(heldItem, false)) {
            return false;
        }
        Entity player = Minecraft.thePlayer();
        if (this.airCrits.getEffectiveValue() && !player.b$src$Z$fqlxe4()) {
            double verticalMovement = player.N() - player.W();
            if (verticalMovement >= 0.0) {
                return false;
            }
        }
        return true;
    }

    private boolean canBypassCooldown() {
        if (!RotationUtil.u(Minecraft.thePlayer())) {
            return false;
        }
        AutoMace autoMace = Vape.INSTANCE.getModManager().getMod(AutoMace.class);
        if (autoMace != null && autoMace.isEnabled() && autoMace.hasReadyMace()) {
            return true;
        }
        HitSwap hitSwap = Vape.INSTANCE.getModManager().getMod(HitSwap.class);
        if (hitSwap == null || !hitSwap.isEnabled()) {
            ItemStack mainHandItem = Minecraft.thePlayer().i(EnumHand.mainHand());
            return mainHandItem.isNotNull() && mainHandItem.getItem().isInstance(MappedClasses.zx)
                    && RotationUtil.u(Minecraft.thePlayer());
        }
        return hitSwap.hasValidWeaponSwap();
    }

    private boolean hasMouseOverDelayElapsed(Entity entity) {
        int entityId = entity.S();
        if (this.mouseOverTargetId != entityId) {
            this.mouseOverTargetId = entityId;
            this.mouseOverDelayMs = (long)this.mouseOverDelayValue.getRandomValue();
            this.mouseOverTimer.reset();
        }
        return this.mouseOverTimer.hasTimeElapsed(this.mouseOverDelayMs);
    }

    private void resetSelectState() {
        this.selectTargetId = -1;
        this.waitingForFirstHit = false;
        this.graceActive = false;
        this.selectTimer.reset();
    }

    private boolean updateSelectFirstHit(Entity entity, boolean wasJustHurt) {
        if (!this.selectFirstHit.getEffectiveValue().booleanValue()) {
            this.resetSelectState();
            return true;
        }
        int entityId = entity.S();
        if (this.selectTargetId != entityId) {
            this.selectTargetId = entityId;
            this.waitingForFirstHit = true;
        }
        this.graceActive = false;
        this.selectTimer.reset();
        if (this.waitingForFirstHit && wasJustHurt) {
            this.waitingForFirstHit = false;
        }
        return !this.waitingForFirstHit;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        this.mouseDown = false;
        this.missAttackPending = false;
        this.earlyHitTicks = 0;
        this.resetMouseOverState();
        this.resetSelectState();
        boolean release;
        synchronized (this.requestLock) {
            ++this.requestEpoch;
            this.requestInFlight = false;
            release = this.releasePending;
            this.releasePending = false;
        }
        if (release) AttackKeyController.releaseAttackKey();
    }

    public Triggerbot() {
        super("Triggerbot", (int)MODULE_ID, Category.COMBAT, "");
        this.selectFirstHit = BooleanValue.create(this, "Select first hit", false, "Waits for the opponent to hit you first before attacking");
        this.requireMouseDown = BooleanValue.create(this, "Require mouse down", false);
        this.ignoreActivationClick = BooleanValue.create(this, "Ignore activation click", false, "Ignores first manual click\n(unless already hovering a valid target with attack ready)");
        this.airCrits = BooleanValue.create(this, "Air crits", false, "Won't attack in air unless you will crit");
        this.shieldCheck = BooleanValue.create(this, "Shield check", false, "Won't attack players blocking with shield\nUsing HitSwap module with axes will override this behavior");
        this.targetMissChance = NumberValue.create(this, "Target miss chance", "#", "%", 0.0, 0.0, 100.0, 1.0, "Chance to attack without hovering a valid target when attack is ready");
        this.earlyHitChance = NumberValue.create(this, "Early hit chance", "#", "%", 0.0, 0.0, 100.0, 1.0, "Chance to attack earlier than attack is ready");
        this.limitToItems = BooleanValue.create(this, "Limit to items", false, "Trigger functions only while holding selected items");
        this.allowedItems = LimitValue.create(this, "trigger-alloweditems", "Allowed Items", LimitValue.ALLOW_LIST_COLOR, Collections.emptyList());
        this.attackTimer = new TimerUtil();
        this.mouseOverTimer = new TimerUtil();
        this.selectTimer = new TimerUtil();
        this.random = new Random();
        this.requireMouseDown.addDependentValues(this.ignoreActivationClick);
        this.limitToItems.addDependentValues(this.allowedItems);
        this.addValue(this.targetFilter, this.extraDelay, this.mouseOverDelayValue, this.requireMouseDown, this.ignoreActivationClick, this.airCrits, this.shieldCheck, this.selectFirstHit, this.targetMissChance, this.earlyHitChance, this.limitToItems, this.allowedItems);
    }

    private boolean isSelectFirstHitSatisfied() {
        return this.selectFirstHit.getEffectiveValue() == false || this.selectTargetId == -1 || !this.waitingForFirstHit;
    }

    private void updateSelectGrace() {
        if (!this.selectFirstHit.getEffectiveValue().booleanValue() || this.selectTargetId == -1) {
            return;
        }
        if (!this.graceActive) {
            this.graceActive = true;
            this.selectTimer.reset();
            return;
        }
        if (this.selectTimer.hasTimeElapsed(1000L)) {
            this.resetSelectState();
        }
    }

    @EventHandler
    public void onKeyPress(EventKeyPress event) {
        if (Minecraft.thePlayer().isNull()) {
            return;
        }
        if (!event.isKeybinding(Minecraft.gameSettings().F())) {
            return;
        }
        this.handleInput(event.isDown(), event);
    }

    @EventHandler
    public void onMouseButton(EventMouseButton event) {
        if (Minecraft.thePlayer().isNull()) {
            return;
        }
        if (!event.isKeybinding(Minecraft.gameSettings().F())) {
            return;
        }
        this.handleInput(event.getButtonState(), event);
    }


    private void handleInput(boolean pressed, Event event) {
        if (this.requireMouseDown.getEffectiveValue() && pressed && !this.mouseDown) {
            this.mouseDown = true;
            if (this.ignoreActivationClick.getEffectiveValue().booleanValue() && this.canAttack(false)) {
                RayTraceResult crosshairHit = RotationManager.INSTANCE.getExtendedReachRayTrace();
                boolean validTarget = crosshairHit != null && crosshairHit.isNotNull()
                        && crosshairHit.getTypeOfHit().equals(RayTraceResult_type.entity())
                        && crosshairHit.getEntity().isNotNull() && this.isValidTarget(crosshairHit.getEntity());
                if (!validTarget || !this.isAttackReady(-this.extraDelay.getMinimumInt())) {
                    event.setCancelled(true);
                }
            }
            return;
        }
        if (pressed) {
            this.attackTimer.reset();
            int earlyHitPercent = (int)((Double)this.earlyHitChance.getValue()).doubleValue();
            this.earlyHitTicks = earlyHitPercent > 0 && this.random.nextInt(100) < earlyHitPercent
                    ? 2 + this.random.nextInt(2) : 0;
            RayTraceResult crosshairHit = RotationManager.INSTANCE.getExtendedReachRayTrace();
            boolean hoveringValidTarget = false;
            if (crosshairHit != null && crosshairHit.isNotNull()
                    && crosshairHit.getTypeOfHit().equals(RayTraceResult_type.entity())) {
                Entity entity = crosshairHit.getEntity();
                hoveringValidTarget = entity.isNotNull() && this.isValidTarget(entity);
            }
            int missPercent = (int)((Double)this.targetMissChance.getValue()).doubleValue();
            this.missAttackPending = hoveringValidTarget && missPercent > 0
                    && this.random.nextInt(100) < missPercent;
        }
    }
}
