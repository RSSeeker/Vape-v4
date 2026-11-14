package gg.vape.module.render.hud;

import gg.vape.Vape;
import gg.vape.event.EventHandler;
import gg.vape.event.EventPriority;
import gg.vape.event.impl.EventPostEntityRendererMouseUpdate;
import gg.vape.event.impl.EventPostRenderTick;
import gg.vape.event.impl.EventPostRenderWorldPass;
import gg.vape.event.impl.EventPostTick;
import gg.vape.event.impl.EventPreEntityRendererMouseUpdate;
import gg.vape.event.impl.EventPreRenderTick;
import gg.vape.event.impl.EventPreRenderWorldPass;
import gg.vape.event.impl.EventRenderPlayerPost;
import gg.vape.event.impl.EventRenderPlayerPre;
import gg.vape.event.impl.EventTickBase;
import gg.vape.module.control.SharedModuleControlClaims;
import gg.vape.rotation.RotationManager;
import gg.vape.unmap.ModeOption;
import gg.vape.unmap.ModeSelection;
import gg.vape.utils.MathUtil;
import gg.vape.value.BooleanValue;
import gg.vape.value.ModeValue;
import gg.vape.value.NumberValue;
import gg.vape.wrapper.impl.EntityLivingBase;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.ForgeVersion;
import gg.vape.wrapper.impl.Minecraft;

public class FreeLookHudModule
extends HudModule {
    private static float savedPitch;
    private final ModeOption holdMode = new ModeOption("Hold");
    private final ModeOption forwardMode;
    private static float renderPitch;
    private static float originalYaw;
    private final ModeOption thirdPersonMode;
    private final NumberValue sensitivity;
    private static boolean active;
    private double mouseDY;
    private EntityLivingBase renderPlayer;
    private final ModeOption toggleMode = new ModeOption("Toggle");
    private static float savedYaw;
    private double mouseLastY;
    private final BooleanValue customSensitivity;
    private final ModeOption firstPersonMode;
    private static float renderYaw;
    private boolean enabled = false;
    public final ModeValue startingPositionMode;
    private static float originalPitch;
    private static float pitchOffset;
    private static float yawOffset;
    private boolean mouseBaselinePending;
    private double mouseLastX;
    public final ModeValue activationMode = ModeValue.create((Object)this, "Activate Freelook", this.holdMode, this.holdMode, this.toggleMode);
    public final ModeValue perspectiveMode;
    private final ModeOption backwardMode;
    private int savedPerspective = -1;
    private double mouseDX;


    public static float getRenderYaw() {
        return renderYaw;
    }

    @EventHandler
    public void onPostRenderTick(EventPostRenderTick event) {
        if (!this.enabled) {
            return;
        }
        this.restorePlayerView();
    }

    @EventHandler(priority=EventPriority.LOWEST)
    public void onPreRenderWorldPass(EventPreRenderWorldPass event) {
        this.applyWorldRotation();
    }

    private double getSensitivity() {
        if (!this.customSensitivity.getEffectiveValue().booleanValue()) {
            return (double)Minecraft.gameSettings().y() * 0.6 * 0.2 * 8.0;
        }
        return (Double)this.sensitivity.getValue();
    }

    private void applyRenderRotation() {
        if (!this.enabled) {
            return;
        }
        if (RotationManager.INSTANCE.hasAdaptiveController() && Vape.INSTANCE.getClientSettings().thirdPersonAimView.getEffectiveValue().booleanValue()) {
            return;
        }
        this.applyCameraRotation();
    }

    @Override
    public void onBindActivated() {
        if (!this.isEnabled() || Minecraft.currentScreen().isNotNull()) {
            return;
        }
        if (ForgeVersion.MC_1_16_5.d()) {
            this.mouseLastX = Minecraft.s().R();
            this.mouseLastY = Minecraft.s().b();
        }
        if (this.activationMode.getValue() == this.toggleMode) {
            EventTickBase.POST_TICK_EXECUTOR.execute(this::toggleFreelook);
        }
    }

    @EventHandler(priority=EventPriority.LOWEST)
    public void onRenderPlayerPost(EventRenderPlayerPost event) {
        this.applyRenderRotation();
    }

    @EventHandler
    public void onPreMouseUpdate(EventPreEntityRendererMouseUpdate event) {
        if (!active) {
            return;
        }
        if (!this.enabled) {
            return;
        }
        if (Minecraft.currentScreen().isNotNull()) {
            return;
        }
        this.applyCameraRotation();
    }

    @EventHandler
    public void onPostTick(EventPostTick event) {
        if (this.activationMode.getValue() != this.holdMode) {
            return;
        }
        if (Minecraft.currentScreen().isNotNull()) {
            return;
        }
        if (Minecraft.thePlayer().isNull()) {
            return;
        }
        this.enabled = this.getBind().areBoundInputsDown();
        if (!this.enabled) {
            active = false;
            if (this.savedPerspective == -1) {
                return;
            }
            Minecraft.gameSettings().I(this.savedPerspective);
            this.savedPerspective = -1;
            this.restorePlayerView();
        }
    }

    @EventHandler
    public void onPostMouseUpdate(EventPostEntityRendererMouseUpdate event) {
        if (!this.enabled) {
            return;
        }
        this.restorePlayerView();
    }

    public static boolean isActive() {
        return active;
    }

    private void toggleFreelook() {
        this.enabled = !this.enabled;
        if (!this.enabled) {
            active = false;
            if (this.savedPerspective == -1) {
                return;
            }
            Minecraft.gameSettings().I(this.savedPerspective);
            this.savedPerspective = -1;
            this.restorePlayerView();
        }
    }

    private void restorePlayerView() {
        EntityPlayerSP entityPlayerSP = Minecraft.thePlayer();
        if (entityPlayerSP.isNull() || Minecraft.theWorld().isNull()) {
            return;
        }
        entityPlayerSP.H(savedPitch);
        entityPlayerSP.D(savedPitch);
        entityPlayerSP.z(savedPitch);
        entityPlayerSP.o(savedPitch);
        entityPlayerSP.C(savedYaw);
        entityPlayerSP.l(savedYaw);
    }

    public void capturePlayerRotation(float pitch, float yaw) {
        savedPitch = pitch;
        savedYaw = yaw;
    }

    public static float getRenderPitch() {
        return renderPitch;
    }

    private void applyWorldRotation() {
        if (!this.enabled || !active) {
            return;
        }
        if (RotationManager.INSTANCE.hasAdaptiveController() && Vape.INSTANCE.getClientSettings().thirdPersonAimView.getEffectiveValue().booleanValue()) {
            return;
        }
        this.restorePlayerView();
    }

    @EventHandler(priority=EventPriority.LOWEST)
    public void onRenderPlayerPre(EventRenderPlayerPre event) {
        this.applyWorldRotation();
    }

    private void applyCameraRotation() {
        if (!active) {
            return;
        }
        if (SharedModuleControlClaims.renderPass.isRenderBlocked()) {
            return;
        }
        this.renderPlayer.H(renderPitch);
        this.renderPlayer.D(renderPitch);
        this.renderPlayer.C(renderYaw);
        this.renderPlayer.l(renderYaw);
    }

    public static float getSavedPitch() {
        return savedPitch;
    }

    @EventHandler(priority=EventPriority.LOWEST)
    public void onPostRenderWorldPass(EventPostRenderWorldPass event) {
        this.applyRenderRotation();
    }

    public FreeLookHudModule() {
        super("Freelook", HudModuleGroup.GAME, "freelook2");
        this.customSensitivity = BooleanValue.create(this, "Use Custom Sensitivity", false, "Enable to set a separate sensitivity from Minecraft using a slider");
        this.sensitivity = NumberValue.create(this, "Sensitivity", "#.#", "", 0.001, 0.5, 1.0);
        this.thirdPersonMode = new ModeOption("3rd Person");
        this.firstPersonMode = new ModeOption("1st Person");
        this.perspectiveMode = ModeValue.create((Object)this, "Perspective", this.thirdPersonMode, this.thirdPersonMode, this.firstPersonMode);
        this.forwardMode = new ModeOption("Forward");
        this.backwardMode = new ModeOption("Backward");
        this.startingPositionMode = ModeValue.create((Object)this, "Starting Position", this.forwardMode, this.forwardMode, this.backwardMode);
        this.setSuffix("Freely rotates your perspective");
        this.setShowKeybindSetting(true);
        this.addValue(this.activationMode, this.startingPositionMode, this.customSensitivity, this.sensitivity);
        this.customSensitivity.addDependentValues(this.sensitivity);
    }

    public static float getSavedYaw() {
        return savedYaw;
    }

    private void updateMouseDelta() {
        if (this.mouseBaselinePending) {
            this.mouseBaselinePending = false;
            this.mouseDX = 0.0;
            this.mouseDY = 0.0;
            if (ForgeVersion.MC_1_16_5.d()) {
                this.mouseLastX = Minecraft.s().R();
                this.mouseLastY = Minecraft.s().b();
            }
            return;
        }
        if (ForgeVersion.MC_1_16_5.d()) {
            this.mouseDX = this.mouseLastX - Minecraft.s().R();
            this.mouseDY = this.mouseLastY - Minecraft.s().b();
            this.mouseLastX = Minecraft.s().R();
            this.mouseLastY = Minecraft.s().b();
            return;
        }
        this.mouseDX = -Minecraft.s().d();
        this.mouseDY = Minecraft.s().z();
    }

    @EventHandler
    public void onPreRenderTick(EventPreRenderTick event) {
        int perspective;
        this.renderPlayer = event.getThePlayer();
        if (!this.enabled) {
            return;
        }
        if (!active) {
            originalYaw = this.renderPlayer.V();
            originalPitch = this.renderPlayer.J();
            this.savedPerspective = Minecraft.gameSettings().x();
            yawOffset = ((ModeSelection)this.startingPositionMode.getValue()).equals(this.forwardMode) ? 0.0f : 180.0f;
            pitchOffset = 0.0f;
            this.mouseDX = 0.0;
            this.mouseDY = 0.0;
            this.mouseBaselinePending = true;
            renderPitch = originalPitch;
            renderYaw = originalYaw;
            savedYaw = originalYaw;
            savedPitch = originalPitch;
            active = true;
            return;
        }
        perspective = ((ModeSelection)this.perspectiveMode.getValue()).equals(this.firstPersonMode) ? 0 : 1;
        if (Minecraft.gameSettings().x() != perspective) {
            Minecraft.gameSettings().I(perspective);
        }
        if (Minecraft.currentScreen().isNotNull()) {
            yawOffset = 0.0f;
            pitchOffset = 0.0f;
            this.mouseDX = this.mouseDY = (double)0.0f;
            this.mouseBaselinePending = true;
        }
        this.updateMouseDelta();
        double yawDelta = this.mouseDX * this.getSensitivity() * 0.15;
        double pitchDelta = this.mouseDY * this.getSensitivity() * 0.15;
        renderPitch = (float)(yawDelta - (double)yawOffset + (double)originalPitch);
        renderYaw = (float)(pitchDelta - (double)pitchOffset + (double)originalYaw);
        renderYaw = MathUtil.clamp(renderYaw, -90.0f, 90.0f);
        yawOffset = (float)(yawDelta + (double)yawOffset);
        pitchOffset = (float)(pitchDelta + (double)pitchOffset);
        pitchOffset = MathUtil.clamp(pitchOffset, -(90.0f - originalYaw), 90.0f + originalYaw);
    }
}
