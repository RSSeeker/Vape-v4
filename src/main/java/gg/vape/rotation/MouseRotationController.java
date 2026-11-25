package gg.vape.rotation;

import gg.vape.Vape;
import gg.vape.event.impl.EventPostRenderTick;
import gg.vape.event.impl.EventPreEntityRendererMouseUpdate;
import gg.vape.event.impl.EventPreRenderTick;
import gg.vape.module.render.Freecam;
import gg.vape.utils.MathUtil;
import gg.vape.utils.SleepUtil;
import gg.vape.utils.TimerUtil;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.GuiScreen;
import java.util.Random;

public abstract class MouseRotationController {
    private int yawJitterDirection;
    private boolean randomizeMovement;
    public float tolerance = 3.0f;
    private int jitterTicks;
    private TimerUtil jitterTimer;
    public float savedYaw;
    public float pendingPitchDelta;
    private boolean complete;
    float savedPreviousPitch;
    float savedPreviousYaw;
    public float savedPitch;
    private int yawJitter;
    public float pitchDistance;
    private int pitchJitterDirection;
    private int pitchJitter;
    private boolean retainAfterCompletion;
    public float pendingYawDelta;
    public float yawDistance;
    public float speed = 1.0f;
    private final Random random = new Random();
    private static String controlFlowMarker;
    private static Freecam freecam;


    public boolean isComplete() {
        return this.complete;
    }

    public float getYawDistance() {
        return this.yawDistance;
    }

    public void onPostRenderTick(EventPostRenderTick event) {
    }

    public void onPreMouseUpdate(EventPreEntityRendererMouseUpdate event) {
    }

    public void applyMouseDelta(float yawDelta, float pitchDelta) {
        PlayerMouseRotationApplier.applyTrackedMouseDelta(yawDelta, pitchDelta);
    }

    public void setRandomizeMovement(boolean randomizeMovement) {
        this.randomizeMovement = randomizeMovement;
    }

    public MouseRotationController setTolerance(float tolerance) {
        this.tolerance = tolerance;
        return this;
    }

    public void setRetainAfterCompletion(boolean retainAfterCompletion) {
        this.retainAfterCompletion = retainAfterCompletion;
    }

    public void capturePlayerRotation(EventPreRenderTick event) {
        EntityPlayerSP player = event.getThePlayer();
        this.savedYaw = player.J();
        this.savedPitch = player.V();
        this.savedPreviousYaw = player.j();
        this.savedPreviousPitch = player.D();
    }

    public float getSpeed() {
        return this.speed;
    }

    public abstract boolean updateYaw();

    public float getPitchDistance() {
        return this.pitchDistance;
    }

    public boolean isMovementRandomized() {
        return this.randomizeMovement;
    }

    public float getPendingPitchDelta() {
        return this.pendingPitchDelta;
    }

    public void update(EntityPlayerSP player, GuiScreen screen) {
        if (player.isNull() || screen.isNotNull()) {
            return;
        }
        boolean yawComplete = this.updateYaw();
        boolean pitchComplete = this.updatePitch();
        if (yawComplete && pitchComplete && Math.abs(this.pendingYawDelta) < 1.0f
                && Math.abs(this.pendingPitchDelta) < 1.0f) {
            this.complete = true;
        }
    }

    public boolean waitUntilComplete(long timeoutMillis) {
        int attempts = 0;
        while (!this.isComplete()) {
            SleepUtil.sleep(10L);
            if ((long)(++attempts) <= timeoutMillis / 10L) continue;
            return true;
        }
        return false;
    }

    public abstract boolean updatePitch();

    public boolean shouldRetainAfterCompletion() {
        return this.retainAfterCompletion;
    }

    public static String getControlFlowMarker() {
        return controlFlowMarker;
    }

    public MouseRotationController setSpeed(float speed) {
        this.speed = speed;
        return this;
    }

    public static void setControlFlowMarker(String marker) {
        controlFlowMarker = marker;
    }

    public void setComplete(boolean complete) {
        this.complete = complete;
    }

    private void updateJitter() {
        ++this.jitterTicks;
        if (this.jitterTicks >= 250 + this.random.nextInt(50)) {
            this.jitterTicks = MathUtil.randomExclusiveUpper(this.random, -100, -50);
            this.yawJitterDirection = MathUtil.randomExclusiveUpper(this.random, -1, 2);
            this.pitchJitterDirection = -MathUtil.randomExclusiveUpper(this.random, -1, 2);
        }
        int yawStep = this.yawJitterDirection;
        int pitchStep = this.pitchJitterDirection;
        if (this.random.nextInt(10) < 2) {
            yawStep = 0;
        }
        if (this.random.nextInt(10) < 2) {
            pitchStep = 0;
        }
        if (this.jitterTicks < 0) {
            yawStep = 0;
            pitchStep = 0;
        }
        if (this.random.nextInt(20) == 1) {
            this.yawJitter += yawStep;
            this.pitchJitter += pitchStep;
        }
        if (this.pendingYawDelta > 0.0f && this.yawJitter < 0
                || this.pendingYawDelta < 0.0f && this.yawJitter > 0) {
            this.yawJitter = 0;
        }
    }

    public void applyPendingMovement(GuiScreen screen) {
        if (this.randomizeMovement) {
            if (this.jitterTimer == null) {
                this.jitterTimer = new TimerUtil();
                this.jitterTimer.x(-1L);
            }
            long elapsedMillis = Math.min((long)RotationMath.MAX_CONTROLLER_ELAPSED_MILLIS,
                    Math.max(0L, this.jitterTimer.getLastMS()));
            this.jitterTimer.reset();
            while (elapsedMillis-- > 0L) {
                this.updateJitter();
            }
            this.pendingYawDelta += (float)this.yawJitter;
            this.pendingPitchDelta += (float)this.pitchJitter;
        }
        int yawSteps = (int)this.pendingYawDelta;
        int pitchSteps = (int)this.pendingPitchDelta;
        float remainingYaw = this.pendingYawDelta - (float)yawSteps;
        float remainingPitch = this.pendingPitchDelta - (float)pitchSteps;
        boolean hasYawMovement = Math.abs(yawSteps) > 0;
        boolean hasPitchMovement = Math.abs(pitchSteps) > 0;
        if (!hasYawMovement) {
            yawSteps = 0;
        }
        if (!hasPitchMovement) {
            pitchSteps = 0;
        }
        float sensitivity = RotationManager.INSTANCE.getMouseSensitivity();
        float sensitivityBase = sensitivity * 0.6f + 0.2f;
        float mouseScale = sensitivityBase * sensitivityBase * sensitivityBase * 8.0f;
        float yawDelta = (float)yawSteps * mouseScale;
        float pitchDelta = (float)pitchSteps * mouseScale;
        int pitchDirection = -1;
        if (freecam == null) {
            freecam = Vape.INSTANCE.getModManager().getMod(Freecam.class);
        }
        if ((freecam == null || !freecam.isEnabled()) && screen.isNull()) {
            this.applyMouseDelta(yawDelta, pitchDelta * (float)pitchDirection);
        }
        this.yawDistance = (float)((double)this.yawDistance + Math.abs((double)yawDelta * 0.15));
        this.pitchDistance = (float)((double)this.pitchDistance + Math.abs((double)pitchDelta * 0.15));
        this.pendingYawDelta = remainingYaw;
        this.pendingPitchDelta = remainingPitch;
        this.yawJitter = 0;
        this.pitchJitter = 0;
    }

    static {
        if (MouseRotationController.getControlFlowMarker() == null) {
            MouseRotationController.setControlFlowMarker("YmWxEb");
        }
    }

    public float getPendingYawDelta() {
        return this.pendingYawDelta;
    }
}
