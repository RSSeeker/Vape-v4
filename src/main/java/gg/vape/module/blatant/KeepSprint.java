package gg.vape.module.blatant;

import gg.vape.Vape;
import gg.vape.event.EventHandler;
import gg.vape.event.impl.EventPostAttack;
import gg.vape.event.impl.EventPreAttack;
import gg.vape.module.Category;
import gg.vape.module.Mod;
import gg.vape.value.BooleanValue;
import gg.vape.value.NumberValue;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.Minecraft;

public class KeepSprint extends Mod {
    private static final long MODULE_ID = -249991328817855756L;
    private static final double VANILLA_ATTACK_SLOWDOWN = 0.6;

    private final NumberValue slowDownVelocity = NumberValue.create(
            this, "Hit Slow Down During Velocity", "#.##", "", 0.0, 0.6, 1.0, 0.05);
    private final NumberValue slowDownNormal = NumberValue.create(
            this, "Hit Slow Down Normal", "#.##", "", 0.0, 0.6, 1.0, 0.05);
    private final NumberValue bufferDecrease = NumberValue.create(
            this, "Buffer Decrease", "#.#", "", 0.1, 1.0, 10.0, 0.1);
    private final NumberValue maxBuffer = NumberValue.create(
            this, "Max Buffer", "#", "", 1.0, 5.0, 10.0, 1.0);
    private final BooleanValue sprintSlowDownVelocity = new BooleanValue(
            this, "Velocity Hit Sprint", true);
    private final BooleanValue sprintSlowDownNormal = new BooleanValue(
            this, "Normal Hit Sprint", true);
    private final BooleanValue bufferAbuse = new BooleanValue(this, "Buffer Abuse", false);
    private final BooleanValue onlyInAir = new BooleanValue(this, "Only In Air", false);

    private double preAttackMotionX;
    private double preAttackMotionZ;
    private double combo;
    private boolean resetting;
    private boolean attackStateCaptured;
    private Scaffold scaffold;

    public KeepSprint() {
        super("KeepSprint", (int)MODULE_ID, Category.OTHER,
                "Controls attack slowdown and sprint state");
        this.bufferAbuse.addDependentValues(this.bufferDecrease, this.maxBuffer);
        this.addValue(this.slowDownVelocity, this.slowDownNormal,
                this.sprintSlowDownVelocity, this.sprintSlowDownNormal,
                this.bufferAbuse, this.bufferDecrease, this.maxBuffer, this.onlyInAir);
    }

    @Override
    public String getDetailedSuffix() {
        return this.slowDownNormal.getDisplayValue();
    }

    @Override
    public boolean isBlatantMod() {
        return true;
    }

    @Override
    public void onEnable() {
        this.resetState();
    }

    @Override
    public void onDisable() {
        this.resetState();
    }

    @EventHandler
    public void onPreAttack(EventPreAttack event) {
        this.attackStateCaptured = false;
        EntityPlayerSP player = Minecraft.thePlayer();
        if (player.isNull() || this.isScaffolding()) {
            return;
        }
        this.preAttackMotionX = player.t();
        this.preAttackMotionZ = player.T();
        this.attackStateCaptured = true;
    }

    @EventHandler
    public void onPostAttack(EventPostAttack event) {
        EntityPlayerSP player = Minecraft.thePlayer();
        if (!this.attackStateCaptured || player.isNull()) {
            return;
        }
        this.attackStateCaptured = false;
        if (this.isScaffolding() || this.onlyInAir.getEffectiveValue() && player.b$src$Z$fqlxe4()) {
            return;
        }
        if (player.F() <= 0.0f || !this.hasVanillaAttackSlowdown(player)) {
            return;
        }
        if (!this.shouldApplyConfiguredSlowdown()) {
            return;
        }

        boolean takingVelocity = player.c$src$I$15a9iwo() > 0;
        double slowDown = takingVelocity
                ? this.slowDownVelocity.getValue()
                : this.slowDownNormal.getValue();
        boolean sprint = takingVelocity
                ? this.sprintSlowDownVelocity.getEffectiveValue()
                : this.sprintSlowDownNormal.getEffectiveValue();

        player.r(this.preAttackMotionX * slowDown);
        player.i(this.preAttackMotionZ * slowDown);
        player.R(sprint);
    }

    private boolean hasVanillaAttackSlowdown(EntityPlayerSP player) {
        return Double.compare(player.t(), this.preAttackMotionX * VANILLA_ATTACK_SLOWDOWN) == 0
                && Double.compare(player.T(), this.preAttackMotionZ * VANILLA_ATTACK_SLOWDOWN) == 0;
    }

    private boolean shouldApplyConfiguredSlowdown() {
        if (!this.bufferAbuse.getEffectiveValue()) {
            this.combo = 0.0;
            return true;
        }
        if (this.combo < this.maxBuffer.getValue() && !this.resetting) {
            ++this.combo;
            return true;
        }
        if (this.combo > 0.0) {
            this.combo = Math.max(0.0, this.combo - this.bufferDecrease.getValue());
            this.resetting = true;
            return false;
        }
        this.resetting = false;
        return true;
    }

    private boolean isScaffolding() {
        if (this.scaffold == null) {
            this.scaffold = Vape.INSTANCE.getModManager().getMod(Scaffold.class);
        }
        return this.scaffold != null && this.scaffold.isActivelyScaffolding();
    }

    private void resetState() {
        this.combo = 0.0;
        this.resetting = false;
        this.attackStateCaptured = false;
    }
}
