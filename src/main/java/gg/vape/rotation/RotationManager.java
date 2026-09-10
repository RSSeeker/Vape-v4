package gg.vape.rotation;

import gg.vape.Vape;
import gg.vape.config.ClientSettings;
import gg.vape.event.EventHandler;
import gg.vape.event.EventListener;
import gg.vape.event.EventPriority;
import gg.vape.event.impl.EventMotion;
import gg.vape.event.impl.EventMouseOverUpdate;
import gg.vape.event.impl.EventPacketReceive;
import gg.vape.event.impl.EventPacketSend;
import gg.vape.event.impl.EventPostLocalPlayerTick;
import gg.vape.event.impl.EventPostRenderTick;
import gg.vape.event.impl.EventPostRenderWorldPass;
import gg.vape.event.impl.EventPreEntityRendererMouseUpdate;
import gg.vape.event.impl.EventPreLocalPlayerTick;
import gg.vape.event.impl.EventPreMotion;
import gg.vape.event.impl.EventPreRenderTick;
import gg.vape.event.impl.EventPreRenderWorldPass;
import gg.vape.event.impl.EventPreTick;
import gg.vape.event.impl.EventRender2D;
import gg.vape.event.impl.EventRenderPlayerPost;
import gg.vape.event.impl.EventRenderPlayerPre;
import gg.vape.event.impl.EventRightClickMouse;
import gg.vape.event.impl.EventSendClickBlockToController;
import gg.vape.event.impl.EventThreadBoundPostTick;
import gg.vape.event.impl.EventThreadBoundPreTick;
import gg.vape.mapping.MappedClasses;
import gg.vape.module.blatant.HitBoxes;
import gg.vape.module.blatant.InvWalk;
import gg.vape.module.combat.Reach;
import gg.vape.module.render.hud.FreeLookHudModule;
import gg.vape.movement.MovementInputHelper;
import gg.vape.movement.PlayerMovementTaskManager;
import gg.vape.unmap.ModeSelection;
import gg.vape.utils.MathUtil;
import gg.vape.utils.MouseOverRayTraceUpdater;
import gg.vape.utils.MutableColor;
import gg.vape.utils.NanoTimerUtil;
import gg.vape.utils.RayTraceUtil;
import gg.vape.utils.RotationUtil;
import gg.vape.utils.render.BufferedGuiRenderPrimitives;
import gg.vape.utils.render.GuiRenderPrimitives;
import gg.vape.utils.render.OpenGlBackendHolder;
import gg.vape.utils.render.RenderUtils;
import gg.vape.wrapper.impl.Entity;
import gg.vape.wrapper.impl.EntityLivingBase;
import gg.vape.wrapper.impl.EntityPlayer;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.ForgeVersion;
import gg.vape.wrapper.impl.GameSettings;
import gg.vape.wrapper.impl.GlStateManager;
import gg.vape.wrapper.impl.GuiScreen;
import gg.vape.wrapper.impl.KeyBinding;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.Packet;
import gg.vape.wrapper.impl.PlayerInteractEventAction;
import gg.vape.wrapper.impl.PlayerPositionLookPacketModern;
import gg.vape.wrapper.impl.RayTraceResult;
import gg.vape.wrapper.impl.S08PacketPlayerPosLook;
import gg.vape.wrapper.impl.ScaledResolution;
import gg.vape.wrapper.impl.World;
import java.awt.Color;
import java.util.Set;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;

public class RotationManager
implements EventListener {
    private boolean playerYawRestorePending;
    private MouseRotationController activeController;
    private float renderBodyYaw;
    private float savedPlayerYaw;
    private static final RayTraceResult EMPTY_RAY_TRACE;
    private float managedPitch;
    private float temporarilySavedPitch;
    public static final RotationManager INSTANCE;
    private float temporarilySavedYaw;
    private float motionYaw;
    private float savedPreviousYaw;
    private boolean rayTraceRefreshPending;
    private float managedYaw;
    private float previousRenderBodyYaw;
    private int controllerUpdatesThisTick;
    private float previousMotionYaw;
    private MouseButtonActionState forwardMovementOverride;
    private float previousMotionPitch;
    private float mouseSensitivity = 0.0f;
    private boolean movementKeysRemapped;
    private boolean savedLeftKeyState;
    private RayTraceResult normalReachRayTrace;
    private RayTraceResult extendedReachRayTrace;
    private boolean savedRightKeyState;
    private float savedPreviousPitch;
    private boolean savedForwardKeyState;
    private RayTraceResult cachedMouseOverRayTrace;
    private double controllerUpdateAccumulator;
    private boolean controllerHooksPending;
    private float savedPlayerPitch;
    private boolean savedBackKeyState;
    private final NanoTimerUtil controllerUpdateTimer = new NanoTimerUtil();
    private static final float DEFAULT_MOVEMENT_THRESHOLD = 0.4f;
    private boolean renderRotationActive;
    private float motionPitch;
    private float savedRenderYawOffset;

    public float adjustMovementYawFromBindings(float yaw, boolean useCurrentKeyState) {
        GameSettings settings = Minecraft.gameSettings();
        KeyBinding forwardKey = settings.Y();
        KeyBinding leftKey = settings.g$src$Lgg_vape_wrapper_impl_KeyBinding_$qqn5n3();
        KeyBinding rightKey = settings.x$src$Lgg_vape_wrapper_impl_KeyBinding_$1cf7isg();
        KeyBinding backKey = settings.s();
        boolean forward = useCurrentKeyState ? forwardKey.isKeyDown() : ClientSettings.isPhysicalKeyDown(forwardKey);
        boolean left = useCurrentKeyState ? leftKey.isKeyDown() : ClientSettings.isPhysicalKeyDown(leftKey);
        boolean right = useCurrentKeyState ? rightKey.isKeyDown() : ClientSettings.isPhysicalKeyDown(rightKey);
        boolean back = useCurrentKeyState ? backKey.isKeyDown() : ClientSettings.isPhysicalKeyDown(backKey);
        return this.adjustMovementYaw(yaw, forward, left, right, back);
    }

    private void updateBodyYaw(float targetYaw) {
        float targetDelta = MathUtil.wrapAngleTo180(targetYaw - this.renderBodyYaw);
        this.renderBodyYaw += targetDelta * 0.3f;
        float headDelta = MathUtil.wrapAngleTo180(this.motionYaw - this.renderBodyYaw);
        if (headDelta < -75.0f) {
            headDelta = -75.0f;
        }
        if (headDelta >= 75.0f) {
            headDelta = 75.0f;
        }
        this.renderBodyYaw = this.motionYaw - headDelta;
        if (headDelta * headDelta > 2500.0f) {
            this.renderBodyYaw += headDelta * 0.2f;
        }
    }

    @EventHandler(priority=EventPriority.LOWEST)
    public void onPostLocalPlayerTick(EventPostLocalPlayerTick event) {
        EntityPlayerSP player = event.getPlayer();
        if (this.playerYawRestorePending) {
            player.H(this.savedPlayerYaw);
            player.z(this.savedPlayerYaw);
            player.F((float)((double)this.savedRenderYawOffset + (double)(this.savedPlayerYaw - this.savedRenderYawOffset) * 0.5));
            this.playerYawRestorePending = false;
        }
        if (this.movementKeysRemapped) {
            this.restoreMovementKeyStates();
            this.movementKeysRemapped = false;
        }
        if (this.renderRotationActive) {
            this.updatePlayerBodyYaw(player);
        }
    }

    public void releaseActiveController() {
        this.releaseController(this.activeController);
    }

    public static float getViewYaw(EntityPlayerSP player) {
        return FreeLookHudModule.isActive() ? FreeLookHudModule.getSavedPitch() : player.J();
    }

    private void applyRenderRotation(EntityPlayerSP player) {
        if (this.renderRotationActive) {
            this.savedPlayerYaw = player.J();
            this.savedPreviousYaw = player.j();
            this.savedPlayerPitch = player.V();
            this.savedPreviousPitch = player.D();
            player.z(this.motionYaw);
            player.o(this.previousMotionYaw);
            player.X(this.renderBodyYaw);
            player.Y(this.previousRenderBodyYaw);
            player.C(this.motionPitch);
            player.l(this.previousMotionPitch);
        }
    }

    private void resetControllerUpdateState() {
        this.controllerUpdateAccumulator = 0.0;
        this.controllerUpdatesThisTick = 0;
        this.controllerUpdateTimer.reset();
    }

    public MouseRotationController getActiveController() {
        return this.activeController;
    }

    private void applyManagedRotation(boolean apply) {
        EntityLivingBase player = Minecraft.F();
        if (apply) {
            this.temporarilySavedYaw = player.J();
            this.temporarilySavedPitch = player.V();
            player.H(this.managedYaw);
            player.z(this.managedYaw);
            player.C(this.managedPitch);
        } else {
            player.H(this.temporarilySavedYaw);
            player.z(this.temporarilySavedYaw);
            player.C(this.temporarilySavedPitch);
        }
    }

    public void releaseController(MouseRotationController controller) {
        if (this.activeController != null && this.activeController.equals(controller)) {
            if (this.activeController instanceof AdaptiveRotationController) {
                AdaptiveRotationController adaptiveController = (AdaptiveRotationController)this.activeController;
                adaptiveController.setRelativeMode(true);
            } else {
                this.activeController.setRetainAfterCompletion(false);
                this.activeController.setComplete(true);
            }
        }
    }

    public float getManagedYaw() {
        return this.hasAdaptiveController() ? this.managedYaw : Minecraft.F().J();
    }

    @EventHandler(priority=EventPriority.LOWEST)
    public void onPacketSend(EventPacketSend eventPacketSend) {
        if (ForgeVersion.MC_1_21_4.d() && this.hasAdaptiveController() && eventPacketSend.getPacket().isInstance(MappedClasses.Dg)) {
            S08PacketPlayerPosLook s08PacketPlayerPosLook = new S08PacketPlayerPosLook(eventPacketSend.getPacket());
            s08PacketPlayerPosLook.setPitch(this.managedPitch);
            s08PacketPlayerPosLook.setYaw(this.managedYaw);
        }
    }

    public boolean hasAdaptiveController() {
        return this.activeController != null && this.activeController instanceof AdaptiveRotationController;
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void onMouseOverUpdateHighest(EventMouseOverUpdate eventMouseOverUpdate) {
        if (this.hasAdaptiveController()) {
            this.applyManagedMouseOver();
        }
    }

    private void advanceControllerUpdates(double updateCount, EntityPlayerSP player, GuiScreen screen) {
        this.controllerUpdateAccumulator += updateCount;
        int wholeUpdates = (int)Math.round(this.controllerUpdateAccumulator);
        for (int updateIndex = 0; updateIndex < wholeUpdates; ++updateIndex) {
            try {
                this.activeController.update(player, screen);
                this.activeController.applyPendingMovement(screen);
            }
            catch (Exception exception) {
                // empty catch block
            }
            ++this.controllerUpdatesThisTick;
        }
        this.controllerUpdateAccumulator -= (double)wholeUpdates;
    }

    @EventHandler(priority=EventPriority.LOWEST)
    public void onPreEntityRendererMouseUpdate(EventPreEntityRendererMouseUpdate eventPreEntityRendererMouseUpdate) {
        if (this.controllerHooksPending && this.activeController != null) {
            this.activeController.onPreMouseUpdate(eventPreEntityRendererMouseUpdate);
        }
    }

    public void suppressForwardMovement() {
        this.forwardMovementOverride = MouseButtonActionState.RELEASE;
    }

    private void restoreRenderRotation(EntityPlayerSP player) {
        if (this.renderRotationActive) {
            player.H(this.savedPlayerYaw);
            player.D(this.savedPreviousYaw);
            player.C(this.savedPlayerPitch);
            player.l(this.savedPreviousPitch);
        }
    }

    @EventHandler
    public void onRender2D(EventRender2D event) {
        EntityPlayerSP player = event.getThePlayer();
        if (!Vape.INSTANCE.getClientSettings().aimIndicator.getEffectiveValue().booleanValue()
                || player.isNull() || Minecraft.currentScreen().isNotNull()) {
            return;
        }
        boolean thirdPersonView = Minecraft.gameSettings().x() > 0;
        if (this.hasAdaptiveController() && !thirdPersonView) {
            AdaptiveRotationController controller = (AdaptiveRotationController)this.activeController;
            double centerZ = 0.0;
            float directionLineLength = 15.0f;
            float directionLineWidth = 2.0f;
            OpenGlBackendHolder.backend.pushMatrix();
            RenderUtils.g();
            ScaledResolution resolution = new ScaledResolution();
            float guiScale = 2.0f / (float)Minecraft.G().getScaleFactor();
            OpenGlBackendHolder.backend.translate(
                    resolution.getScaledWidthDouble() / 2.0 / (double)guiScale,
                    resolution.getScaledHeightDouble() / 2.0 / (double)guiScale,
                    centerZ);
            EntityLivingBase renderedPlayer = Minecraft.F();
            float partialTicks = Minecraft.getTimer().renderPartialTicks();
            float interpolatedPlayerYaw = renderedPlayer.J()
                    + (renderedPlayer.J() - renderedPlayer.j()) * partialTicks;
            float interpolatedPlayerPitch = renderedPlayer.V()
                    + (renderedPlayer.V() - renderedPlayer.D()) * partialTicks;
            float interpolatedManagedYaw = this.previousMotionYaw
                    + (this.motionYaw - this.previousMotionYaw) * partialTicks + 90.0f;
            OpenGlBackendHolder.backend.rotate(interpolatedPlayerPitch, -1.0f, 0.0f, 0.0f);
            OpenGlBackendHolder.backend.rotate(90.0f, 0.0f, -1.0f, 0.0f);
            OpenGlBackendHolder.backend.scale(-1.0f, -1.0f, -1.0f);
            OpenGlBackendHolder.backend.translate(0.0, 5.0, 0.5);
            OpenGlBackendHolder.backend.rotate(20.0f, 0.0f, 0.0f, 1.0f);
            this.drawLine(new Color(-1442840576, true), 3.0f, 0.0, -5.0, 0.0, 0.0, 5.0, 0.0);
            float relativeManagedYaw = interpolatedManagedYaw - interpolatedPlayerYaw - 90.0f;
            OpenGlBackendHolder.backend.rotate(relativeManagedYaw, 0.0f, -1.0f, 0.0f);
            this.drawLine(new Color(-1441787888), directionLineWidth + 1.0f,
                    0.0, 0.0, 0.0, directionLineLength, 0.0, 0.0);
            MutableColor lineColor = controller.usesSecondaryColor()
                    ? controller.getSecondaryColor() : controller.getPrimaryColor();
            this.drawLine(new MutableColor(lineColor).withAlpha(255), directionLineWidth,
                    0.0, 0.0, 0.0, directionLineLength, 0.0, 0.0);
            OpenGlBackendHolder.backend.rotate(relativeManagedYaw, 0.0f, 1.0f, 0.0f);
            OpenGlBackendHolder.backend.rotate(interpolatedPlayerYaw, 0.0f, 1.0f, 0.0f);
            boolean blendEnabled = OpenGlBackendHolder.backend.isCapabilityEnabled(3042);
            boolean lightingEnabled = OpenGlBackendHolder.backend.isCapabilityEnabled(2896);
            if (!blendEnabled) {
                OpenGlBackendHolder.backend.enableCapability(3042);
            }
            if (lightingEnabled) {
                OpenGlBackendHolder.backend.disableCapability(2896);
            }
            GL11.glBlendFunc((int)770, (int)771);
            OpenGlBackendHolder.backend.enableCapability(2848);
            OpenGlBackendHolder.backend.disableCapability(3553);
            double markerHalfSize = 5.0;
            Color markerOutlineColor = new Color(-1728053248, true);
            Color markerFillColor = new Color(-1719631744, true);
            if (GuiRenderPrimitives.d()) {
                BufferedGuiRenderPrimitives.drawLine3D(-markerHalfSize, -5.0, -markerHalfSize, markerHalfSize, -5.0, -markerHalfSize, directionLineWidth, markerOutlineColor);
                BufferedGuiRenderPrimitives.drawLine3D(markerHalfSize, -5.0, -markerHalfSize, markerHalfSize, -5.0, markerHalfSize, directionLineWidth, markerOutlineColor);
                BufferedGuiRenderPrimitives.drawLine3D(markerHalfSize, -5.0, markerHalfSize, -markerHalfSize, -5.0, markerHalfSize, directionLineWidth, markerOutlineColor);
                BufferedGuiRenderPrimitives.drawLine3D(-markerHalfSize, -5.0, markerHalfSize, -markerHalfSize, -5.0, -markerHalfSize, directionLineWidth, markerOutlineColor);
                BufferedGuiRenderPrimitives.fillQuad(-markerHalfSize, -5.0, -markerHalfSize, markerHalfSize, -5.0, -markerHalfSize, markerHalfSize, -5.0, markerHalfSize, -markerHalfSize, -5.0, markerHalfSize, markerFillColor);
            } else {
                GL11.glLineWidth((float)1.0f);
                GL11.glBegin((int)2);
                RenderUtils.w(markerOutlineColor);
                GL11.glVertex3d(-markerHalfSize, -5.0, -markerHalfSize);
                GL11.glVertex3d(markerHalfSize, -5.0, -markerHalfSize);
                GL11.glVertex3d(markerHalfSize, -5.0, markerHalfSize);
                GL11.glVertex3d(-markerHalfSize, -5.0, markerHalfSize);
                GL11.glEnd();
                GlStateManager.Y();
                GL11.glBegin((int)7);
                RenderUtils.w(markerFillColor);
                GL11.glVertex3d(-markerHalfSize, -5.0, -markerHalfSize);
                GL11.glVertex3d(markerHalfSize, -5.0, -markerHalfSize);
                GL11.glVertex3d(markerHalfSize, -5.0, markerHalfSize);
                GL11.glVertex3d(-markerHalfSize, -5.0, markerHalfSize);
                GL11.glEnd();
                GlStateManager.L();
            }
            if (lightingEnabled) {
                OpenGlBackendHolder.backend.enableCapability(2896);
            }
            if (!blendEnabled) {
                OpenGlBackendHolder.backend.disableCapability(3042);
            }
            OpenGlBackendHolder.backend.enableCapability(3553);
            OpenGlBackendHolder.backend.disableCapability(2848);
            RenderUtils.f();
            OpenGlBackendHolder.backend.popMatrix();
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void onMotionUpdate(EventPreMotion event) {
        if (this.hasAdaptiveController()) {
            EventMotion.setRotationYaw(this.managedYaw);
            EventMotion.setRotationPitch(this.managedPitch);
        }
        EntityPlayerSP player = Minecraft.thePlayer();
        this.previousMotionYaw = player.g();
        this.previousMotionPitch = player.a$src$F$1txy325();
        this.motionYaw = EventMotion.getRotationYaw();
        this.motionPitch = EventMotion.getRotationPitch();
        boolean rotationChanged = this.motionYaw != player.J() || this.motionPitch != player.V();
        if (!this.renderRotationActive && rotationChanged) {
            this.renderBodyYaw = player.W$src$F$153nzpr();
            this.previousRenderBodyYaw = player.S$src$F$151gtcb();
        }
        this.renderRotationActive = rotationChanged;
    }

    public float getPreviousManagedPitch() {
        return this.hasAdaptiveController() ? Minecraft.thePlayer().a$src$F$1txy325() : Minecraft.F().D();
    }

    public boolean isControllerUpdating() {
        return this.activeController != null && !this.activeController.isComplete();
    }

    private void refreshReachRayTraces() {
        double reachDistance = Vape.INSTANCE.getModManager().getMod(Reach.class).getReachDistance();
        double hitBoxExpansion = Vape.INSTANCE.getModManager().getMod(HitBoxes.class).getExpansionAmount();
        boolean extendedReach = reachDistance > 3.0;
        if (!this.hasAdaptiveController() && FreeLookHudModule.isActive()) {
            this.managedYaw = FreeLookHudModule.getSavedPitch();
            this.managedPitch = FreeLookHudModule.getSavedYaw();
        }
        this.extendedReachRayTrace = this.rayTraceUsingManagedRotation(reachDistance, (float)hitBoxExpansion, extendedReach);
        this.normalReachRayTrace = this.rayTraceUsingManagedRotation(3.0, 0.0f, false);
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void onRightClickMouse(EventRightClickMouse eventRightClickMouse) {
        if (this.hasAdaptiveController()) {
            this.applyManagedMouseOver();
        }
    }

    public RotationManager() {
        this.forwardMovementOverride = MouseButtonActionState.NONE;
        this.extendedReachRayTrace = EMPTY_RAY_TRACE;
        this.normalReachRayTrace = EMPTY_RAY_TRACE;
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void onThreadBoundPreTick(EventThreadBoundPreTick eventThreadBoundPreTick) {
        if (this.hasAdaptiveController()) {
            this.applyManagedRotation(true);
        }
    }

    @EventHandler(priority=EventPriority.LOWEST)
    public void onPreRenderWorldPass(EventPreRenderWorldPass eventPreRenderWorldPass) {
        if (!Vape.INSTANCE.getClientSettings().thirdPersonAimView.getEffectiveValue().booleanValue()) {
            return;
        }
        this.applyRenderRotation(eventPreRenderWorldPass.getPlayer());
    }

    private void updatePlayerBodyYaw(EntityPlayerSP player) {
        this.previousRenderBodyYaw = this.renderBodyYaw;
        double deltaX = player.z() - player.f();
        double deltaZ = player.h() - player.R();
        float horizontalDistanceSquared = (float)(deltaX * deltaX + deltaZ * deltaZ);
        float targetBodyYaw = this.renderBodyYaw;
        if (horizontalDistanceSquared > 0.0025000002f) {
            targetBodyYaw = (float)MathUtil.V(deltaZ, deltaX) * 180.0f / (float)Math.PI - 90.0f;
        }
        if (player.i() > 0) {
            targetBodyYaw = this.motionYaw;
        }
        this.updateBodyYaw(targetBodyYaw);
        while (this.renderBodyYaw - this.previousRenderBodyYaw < -180.0f) {
            this.previousRenderBodyYaw -= 360.0f;
        }
        while (this.renderBodyYaw - this.previousRenderBodyYaw >= 180.0f) {
            this.previousRenderBodyYaw += 360.0f;
        }
    }

    @EventHandler
    public void onPostRenderTick(EventPostRenderTick eventPostRenderTick) {
        if (this.controllerHooksPending) {
            if (this.activeController != null && eventPostRenderTick.getThePlayer().isNotNull()) {
                try {
                    this.activeController.onPostRenderTick(eventPostRenderTick);
                }
                catch (NullPointerException nullPointerException) {
                    Vape.logThrowable(nullPointerException);
                }
            }
            this.controllerHooksPending = false;
        }
    }

    @EventHandler(priority=EventPriority.LOWEST)
    public void onPostRenderWorldPass(EventPostRenderWorldPass eventPostRenderWorldPass) {
        if (!Vape.INSTANCE.getClientSettings().thirdPersonAimView.getEffectiveValue().booleanValue()) {
            return;
        }
        this.restoreRenderRotation(eventPostRenderWorldPass.getPlayer());
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void onSendClickBlockToController(EventSendClickBlockToController eventSendClickBlockToController) {
        if (this.hasAdaptiveController()) {
            this.applyManagedMouseOver();
        }
    }

    public float getMouseSensitivity() {
        return this.mouseSensitivity;
    }

    private void drawLine(Color color, float width, double startX, double startY, double startZ,
                          double endX, double endY, double endZ) {
        boolean blendEnabled = OpenGlBackendHolder.backend.isCapabilityEnabled(3042);
        boolean lightingEnabled = OpenGlBackendHolder.backend.isCapabilityEnabled(2896);
        GL11.glBlendFunc((int)770, (int)771);
        if (!blendEnabled) {
            OpenGlBackendHolder.backend.enableCapability(3042);
        }
        if (lightingEnabled) {
            OpenGlBackendHolder.backend.disableCapability(2896);
        }
        GL11.glBlendFunc((int)770, (int)771);
        OpenGlBackendHolder.backend.enableCapability(2848);
        OpenGlBackendHolder.backend.disableCapability(3553);
        if (GuiRenderPrimitives.d()) {
            BufferedGuiRenderPrimitives.drawLine3D(startX, startY, startZ, endX, endY, endZ, width, color);
        } else {
            GL11.glLineWidth(width);
            GL11.glBegin((int)1);
            RenderUtils.w(color);
            GL11.glVertex3d(startX, startY, startZ);
            GL11.glVertex3d(endX, endY, endZ);
            GL11.glEnd();
        }
        if (lightingEnabled) {
            OpenGlBackendHolder.backend.enableCapability(2896);
        }
        if (!blendEnabled) {
            OpenGlBackendHolder.backend.disableCapability(3042);
        }
        OpenGlBackendHolder.backend.enableCapability(3553);
        OpenGlBackendHolder.backend.disableCapability(2848);
    }

    public float getSensitivityTimeScale() {
        float sensitivityBase = INSTANCE.getMouseSensitivity() * 0.6f + 0.2f;
        float currentScale = sensitivityBase * sensitivityBase * sensitivityBase * 8.0f;
        float referenceBase = 0.5f;
        float referenceScale = referenceBase * referenceBase * referenceBase * 8.0f;
        return referenceScale / currentScale;
    }

    public float adjustMovementYaw(float yaw, boolean forward, boolean left, boolean right, boolean back) {
        float adjustedYaw = yaw;
        if (forward && left) {
            adjustedYaw += 45.0f;
        } else if (back && left) {
            adjustedYaw += 135.0f;
        } else if (left) {
            adjustedYaw += 90.0f;
        } else if (forward && right) {
            adjustedYaw -= 45.0f;
        } else if (back && right) {
            adjustedYaw -= 135.0f;
        } else if (right) {
            adjustedYaw -= 90.0f;
        } else if (back) {
            adjustedYaw += 180.0f;
        }
        return adjustedYaw;
    }

    static {
        EMPTY_RAY_TRACE = new RayTraceResult(null);
        INSTANCE = new RotationManager();
    }

    private void applyManagedMouseOver() {
        if (this.cachedMouseOverRayTrace == null || this.cachedMouseOverRayTrace.isNull()) {
            EntityLivingBase player = Minecraft.F();
            float savedYaw = player.J();
            float savedRenderYaw = player.s();
            float savedPitch = player.V();
            player.H(this.managedYaw);
            player.z(this.managedYaw);
            player.C(this.managedPitch);
            double reachDistance = 3.0;
            if (Vape.INSTANCE.getClientSettings().useReach.getEffectiveValue().booleanValue()) {
                reachDistance = Vape.INSTANCE.getModManager().getMod(Reach.class).getReachDistance();
            }
            double hitBoxExpansion = 0.0;
            if (Vape.INSTANCE.getClientSettings().useHitboxes.getEffectiveValue().booleanValue()) {
                hitBoxExpansion = Vape.INSTANCE.getModManager().getMod(HitBoxes.class).getExpansionAmount();
            }
            MouseOverRayTraceUpdater.s((float)reachDistance, (float)hitBoxExpansion);
            this.cachedMouseOverRayTrace = Minecraft.p$src$Lgg_vape_wrapper_impl_RayTraceResult_$5rw6n0();
            player.H(savedYaw);
            player.z(savedRenderYaw);
            player.C(savedPitch);
        }
        Minecraft.O(this.cachedMouseOverRayTrace);
    }

    @EventHandler(priority=EventPriority.LOWEST)
    public void onPacketReceive(EventPacketReceive event) {
        if (!this.hasAdaptiveController() || event.isCanceled()) {
            return;
        }
        EntityPlayerSP player = Minecraft.thePlayer();
        if (player.isNull()) {
            return;
        }
        AdaptiveRotationController controller = (AdaptiveRotationController)this.activeController;
        Packet packet = event.getPacket();
        if (packet.isInstance(MappedClasses.zw)) {
            if (controller.isRelativeMode()) {
                controller.setComplete(true);
            } else {
                PlayerPositionLookPacketModern positionLookPacket = new PlayerPositionLookPacketModern(packet);
                float packetYaw = positionLookPacket.getYaw();
                float packetPitch = positionLookPacket.getPitch();
                if (ForgeVersion.MC_1_7_10.Y()) {
                    // MC_1_7_10.Y() means "current > 1.7.10", so this branch is for
                    // 1.8.9 and above; 1.7.10 itself never reaches it.
                    // A server position-look packet with relative yaw/pitch flags is
                    // a delta from the rotation the client last reported, so the
                    // managed angles must be re-applied to the player and then
                    // advanced by the packet deltas. Without this, managedYaw /
                    // managedPitch desync from the server view and silent rotation
                    // drifts (matches the OpenVape implementation).
                    Set relativeFlags = positionLookPacket.getRelativeFlags();
                    for (Object relativeFlag : relativeFlags) {
                        PlayerInteractEventAction action = new PlayerInteractEventAction(relativeFlag);
                        if (action.T() == PlayerInteractEventAction.e()) {
                            player.C(this.managedPitch);
                            this.managedPitch += packetPitch;
                        }
                        if (action.T() != PlayerInteractEventAction.t()) continue;
                        player.H(this.managedYaw);
                        this.managedYaw += packetYaw;
                    }
                }
            }
        }
    }

    public RayTraceResult getNormalReachRayTrace() {
        return this.normalReachRayTrace;
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void onThreadBoundPostTick(EventThreadBoundPostTick eventThreadBoundPostTick) {
        if (this.hasAdaptiveController()) {
            this.applyManagedRotation(false);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void onPreLocalPlayerTick(EventPreLocalPlayerTick event) {
        if (Minecraft.thePlayer().isNull()) {
            this.movementKeysRemapped = false;
            return;
        }
        ModeSelection movementMode = (ModeSelection)Vape.INSTANCE.getClientSettings().movementCorrection.getValue();
        if (movementMode.equals(ClientSettings.NO_MOVEMENT_CORRECTION) || !this.isMovementCorrectionMode(movementMode)) {
            return;
        }
        GameSettings settings = Minecraft.gameSettings();
        boolean keyStateMismatch = this.hasMovementKeyStateMismatch(settings);
        if (!this.hasAdaptiveController()) {
            if (this.movementKeysRemapped) {
                if (!keyStateMismatch) {
                    this.restoreMovementKeyStates();
                }
                this.movementKeysRemapped = false;
            }
            return;
        }

        EntityPlayerSP player = event.getPlayer();
        boolean movementActive = keyStateMismatch
                ? RotationUtil.x() : MovementInputHelper.isPhysicalMovementInputActive();
        boolean shouldRemapMovement = !this.isRotationBlockedByScreen() && movementActive;
        AdaptiveRotationController controller = (AdaptiveRotationController)this.activeController;
        this.savedPlayerYaw = FreeLookHudModule.isActive()
                ? FreeLookHudModule.getSavedPitch() : player.J();
        this.savedRenderYawOffset = player.q$src$F$1u6qsjx();
        float referenceYaw = controller.getReferenceYaw();
        float movementYaw = this.adjustMovementYawFromBindings(referenceYaw, keyStateMismatch);
        float appliedPlayerYaw = movementMode.equals(ClientSettings.SLOW_MOVEMENT_CORRECTION)
                ? movementYaw + 180.0f : this.managedYaw;
        player.H(appliedPlayerYaw);
        player.z(appliedPlayerYaw);
        this.playerYawRestorePending = true;

        if (shouldRemapMovement) {
            float relativeMovementYaw = MathUtil.wrapAngleTo180(
                    MathUtil.wrapAngleTo180(appliedPlayerYaw) - movementYaw);
            float relativeMovementRadians = relativeMovementYaw * ((float)Math.PI / 180);
            float forwardProjection = (float)Math.cos(relativeMovementRadians);
            float leftProjection = (float)(-Math.sin(relativeMovementRadians));
            double movementThreshold = PlayerMovementTaskManager.INSTANCE.getActiveTask() != null
                    ? 0.075 : (double)DEFAULT_MOVEMENT_THRESHOLD;
            boolean pressForward = (double)forwardProjection >= movementThreshold;
            boolean pressLeft = (double)leftProjection >= movementThreshold;
            boolean pressRight = (double)leftProjection <= -movementThreshold;
            boolean pressBack = (double)forwardProjection <= -movementThreshold;
            if (this.forwardMovementOverride == MouseButtonActionState.PRESS) {
                pressForward = true;
                pressBack = false;
            } else if (this.forwardMovementOverride == MouseButtonActionState.RELEASE) {
                pressForward = false;
                pressBack = false;
            }
            this.savedForwardKeyState = settings.Y().isKeyDown();
            this.savedLeftKeyState = settings.g$src$Lgg_vape_wrapper_impl_KeyBinding_$qqn5n3().isKeyDown();
            this.savedRightKeyState = settings.x$src$Lgg_vape_wrapper_impl_KeyBinding_$1cf7isg().isKeyDown();
            this.savedBackKeyState = settings.s().isKeyDown();
            settings.Y().setPressed(pressForward);
            settings.g$src$Lgg_vape_wrapper_impl_KeyBinding_$qqn5n3().setPressed(pressLeft);
            settings.x$src$Lgg_vape_wrapper_impl_KeyBinding_$1cf7isg().setPressed(pressRight);
            settings.s().setPressed(pressBack);
            this.movementKeysRemapped = true;
        } else if (this.movementKeysRemapped) {
            if (!keyStateMismatch) {
                this.restoreMovementKeyStates();
            }
            this.movementKeysRemapped = false;
        }
    }

    private boolean isMovementCorrectionMode(ModeSelection mode) {
        return mode.equals(ClientSettings.PROPER_MOVEMENT_CORRECTION) || mode.equals(ClientSettings.SLOW_MOVEMENT_CORRECTION);
    }

    private boolean hasMovementKeyStateMismatch(GameSettings settings) {
        KeyBinding[] movementKeys = new KeyBinding[]{
                settings.Y(),
                settings.g$src$Lgg_vape_wrapper_impl_KeyBinding_$qqn5n3(),
                settings.x$src$Lgg_vape_wrapper_impl_KeyBinding_$1cf7isg(),
                settings.s()
        };
        for (KeyBinding key : movementKeys) {
            if (ClientSettings.isPhysicalKeyDown(key) != key.isKeyDown()) {
                return true;
            }
        }
        return false;
    }

    public RayTraceResult rayTraceUsingManagedRotation(double distance, float hitBoxExpansion, boolean extendedReach) {
        return this.rayTraceUsingManagedRotation(distance, hitBoxExpansion, extendedReach, null);
    }

    @EventHandler(priority=EventPriority.LOWEST)
    public void onRenderPlayerPre(EventRenderPlayerPre eventRenderPlayerPre) {
        if (!Vape.INSTANCE.getClientSettings().thirdPersonAimView.getEffectiveValue().booleanValue()) {
            return;
        }
        EntityPlayer player = eventRenderPlayerPre.getEntityPlayer();
        if (!player.isInstance(MappedClasses.z5)) {
            return;
        }
        this.applyRenderRotation(new EntityPlayerSP(player));
    }

    public RayTraceResult rayTraceUsingManagedRotation(boolean includeFluids) {
        RayTraceResult result;
        EntityPlayerSP player = Minecraft.thePlayer();
        World world = player.getWorld();
        if (this.hasAdaptiveController() || FreeLookHudModule.isActive()) {
            EntityLivingBase renderedPlayer = Minecraft.F();
            float savedYaw = renderedPlayer.J();
            float savedRenderYaw = renderedPlayer.s();
            float savedPitch = renderedPlayer.V();
            renderedPlayer.H(this.managedYaw);
            renderedPlayer.z(this.managedYaw);
            renderedPlayer.C(this.managedPitch);
            result = RayTraceUtil.p(world, player, includeFluids);
            renderedPlayer.H(savedYaw);
            renderedPlayer.z(savedRenderYaw);
            renderedPlayer.C(savedPitch);
        } else {
            result = RayTraceUtil.p(world, player, includeFluids);
        }
        return result;
    }

    @EventHandler(priority=EventPriority.LOWEST)
    public void onTick(EventPreTick event) {
        EntityPlayerSP player = event.getThePlayer();
        GuiScreen screen = event.getCurrentScreen();
        this.mouseSensitivity = Minecraft.gameSettings().y();
        this.rayTraceRefreshPending = true;
        this.cachedMouseOverRayTrace = EMPTY_RAY_TRACE;
        if (player.isNotNull()) {
            this.refreshReachRayTraces();
        }
        if (this.activeController == null || player.isNull()) {
            this.activeController = null;
            this.resetControllerUpdateState();
            return;
        }
        if (this.activeController.isComplete() && !this.activeController.shouldRetainAfterCompletion()) {
            this.activeController = null;
        }
        if (this.activeController == null) {
            this.resetControllerUpdateState();
            return;
        }
        this.controllerUpdateTimer.reset();
        double targetUpdates = Math.round(50.0f * this.getSensitivityTimeScale());
        double remainingUpdates = Math.max(targetUpdates - (double)this.controllerUpdatesThisTick, 0.0);
        this.advanceControllerUpdates(remainingUpdates, player, screen);
        this.controllerUpdatesThisTick = 0;
        if (this.hasAdaptiveController()) {
            AdaptiveRotationController controller = (AdaptiveRotationController)this.activeController;
            this.managedYaw = controller.getRenderedYaw();
            this.managedPitch = MathUtil.clamp(controller.getRenderedPitch(), -90.0f, 90.0f);
            this.motionYaw = this.managedYaw;
            this.motionPitch = this.managedPitch;
            this.applyManagedMouseOver();
        }
        this.refreshReachRayTraces();
    }

    public static float getViewPitch(EntityPlayerSP player) {
        return FreeLookHudModule.isActive() ? FreeLookHudModule.getSavedYaw() : player.V();
    }

    private boolean isRotationBlockedByScreen() {
        if (Minecraft.currentScreen().isNull()) {
            return false;
        }
        InvWalk invWalk = Vape.INSTANCE.getModManager().getMod(InvWalk.class);
        return invWalk == null || !invWalk.isEnabled() || !invWalk.shouldHandleCurrentScreen();
    }

    public float getManagedPitch() {
        return this.hasAdaptiveController() ? this.managedPitch : Minecraft.F().V();
    }

    @EventHandler
    public void onPreRenderTick(EventPreRenderTick event) {
        EntityPlayerSP player = event.getThePlayer();
        if (this.activeController == null || player.isNull()) {
            this.activeController = null;
            this.resetControllerUpdateState();
            return;
        }
        if (this.activeController.isComplete() && !this.activeController.shouldRetainAfterCompletion()) {
            this.activeController = null;
        }
        if (this.activeController == null) {
            this.resetControllerUpdateState();
            return;
        }
        double timerSpeed = Minecraft.getTimer().getTimerSpeed();
        double elapsedGameMillis = this.controllerUpdateTimer.getElapsedMilliseconds() * timerSpeed;
        this.controllerUpdateTimer.reset();
        double scaledUpdates = elapsedGameMillis * (double)this.getSensitivityTimeScale();
        this.advanceControllerUpdates(scaledUpdates, player, event.getCurrentScreen());
        this.controllerHooksPending = true;
        this.activeController.capturePlayerRotation(event);
    }

    public RayTraceResult getExtendedReachRayTrace() {
        return this.extendedReachRayTrace;
    }

    public void setController(@NotNull MouseRotationController controller) {
        AdaptiveRotationController previousAdaptiveController;
        if (this.activeController == controller) {
            return;
        }
        if (this.activeController instanceof AdaptiveRotationController
                && controller instanceof AdaptiveRotationController) {
            previousAdaptiveController = (AdaptiveRotationController)this.activeController;
            previousAdaptiveController.setRelativeMode(true);
            previousAdaptiveController.setComplete(true);
            AdaptiveRotationController nextAdaptiveController = (AdaptiveRotationController)controller;
            nextAdaptiveController.setRelativeMode(false);
            nextAdaptiveController.setCurrentYaw(previousAdaptiveController.getRenderedYaw());
            nextAdaptiveController.setCurrentPitch(previousAdaptiveController.getRenderedPitch());
        }
        if (this.activeController == null && controller instanceof AdaptiveRotationController) {
            AdaptiveRotationController adaptiveController = (AdaptiveRotationController)controller;
            this.managedYaw = adaptiveController.getRenderedYaw();
            this.managedPitch = adaptiveController.getRenderedPitch();
        }
        this.activeController = controller;
    }

    @EventHandler(priority=EventPriority.LOW)
    public void onMouseOverUpdate(EventMouseOverUpdate eventMouseOverUpdate) {
        if (!this.rayTraceRefreshPending) {
            return;
        }
        if (Minecraft.thePlayer().isNotNull()) {
            this.refreshReachRayTraces();
            this.rayTraceRefreshPending = false;
        }
    }

    public double getControllerUpdateAccumulator() {
        return this.controllerUpdateAccumulator;
    }

    private void restoreMovementKeyStates() {
        GameSettings settings = Minecraft.gameSettings();
        MovementInputHelper.synchronizeKeyState(settings.Y(), this.savedForwardKeyState);
        MovementInputHelper.synchronizeKeyState(settings.g$src$Lgg_vape_wrapper_impl_KeyBinding_$qqn5n3(), this.savedLeftKeyState);
        MovementInputHelper.synchronizeKeyState(settings.x$src$Lgg_vape_wrapper_impl_KeyBinding_$1cf7isg(), this.savedRightKeyState);
        MovementInputHelper.synchronizeKeyState(settings.s(), this.savedBackKeyState);
    }

    public RayTraceResult rayTraceUsingManagedRotation(double distance, float hitBoxExpansion,
                                                       boolean extendedReach,
                                                       @Nullable Predicate<Entity> predicate) {
        RayTraceResult result;
        if (this.hasAdaptiveController() || FreeLookHudModule.isActive()) {
            EntityLivingBase player = Minecraft.F();
            float savedYaw = player.J();
            float savedRenderYaw = player.s();
            float savedPitch = player.V();
            player.H(this.managedYaw);
            player.z(this.managedYaw);
            player.C(this.managedPitch);
            result = RayTraceUtil.U(player, distance, hitBoxExpansion, extendedReach, predicate);
            player.H(savedYaw);
            player.z(savedRenderYaw);
            player.C(savedPitch);
        } else {
            result = RayTraceUtil.q(distance, hitBoxExpansion, extendedReach, predicate);
        }
        return result;
    }

    @EventHandler(priority=EventPriority.LOWEST)
    public void onRenderPlayerPost(EventRenderPlayerPost eventRenderPlayerPost) {
        if (!Vape.INSTANCE.getClientSettings().thirdPersonAimView.getEffectiveValue().booleanValue()) {
            return;
        }
        EntityPlayer player = eventRenderPlayerPost.getEntityPlayer();
        if (!player.isInstance(MappedClasses.z5)) {
            return;
        }
        this.restoreRenderRotation(new EntityPlayerSP(player));
    }

    public void setForwardMovementOverride(boolean pressed) {
        this.forwardMovementOverride = pressed ? MouseButtonActionState.PRESS : MouseButtonActionState.NONE;
    }

    public float getPreviousManagedYaw() {
        return this.hasAdaptiveController() ? Minecraft.thePlayer().g() : Minecraft.F().j();
    }
}
