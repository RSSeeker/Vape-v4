package gg.vape.ui.click.component.input;

import gg.vape.Vape;
import gg.vape.config.Profile;
import gg.vape.input.BindActivationMode;
import gg.vape.input.BindCaptureTask;
import gg.vape.input.KeyboardInput;
import gg.vape.ui.click.component.GuiComponent;
import gg.vape.ui.click.component.ToolTips;
import gg.vape.ui.click.component.TruncatedTextComponent;
import gg.vape.ui.click.component.gui.InteractiveComponent;
import gg.vape.ui.click.component.input.BindableInputComponentCaptureToggleClickHandler;
import gg.vape.ui.click.component.input.BindableInputComponentSavingBindCaptureTask;
import gg.vape.unmap.Bendable;
import gg.vape.unmap.ColorUtil;
import gg.vape.utils.render.GuiRenderPrimitives;
import gg.vape.utils.render.ImageRenderer;
import java.awt.Color;
import java.util.ArrayList;
import org.jetbrains.annotations.Nullable;

public class BindableInputComponent
extends InteractiveComponent {
    private final TruncatedTextComponent bindLabel;
    private int activeAlpha;
    private boolean highlighted;
    private Color accentColor;
    private Bendable bendable;
    private boolean saveChanges = true;
    private final BindCaptureTask captureTask;
    @Nullable
    private Boolean activeOverride = null;
    private static final long LEGACY_ALPHA_SEED = -6023760571160068045L;
    private final Color inactiveColor = new Color(255, 255, 255, 100);
    private float alphaMultiplier = 1.0f;

    public boolean isHighlighted() {
        return this.highlighted;
    }

    public void setAlphaMultiplier(float alphaMultiplier) {
        this.alphaMultiplier = Math.max(0.0f, Math.min(1.0f, alphaMultiplier));
    }

    public void setBendable(Bendable bendable) {
        this.captureTask.setBendable(bendable);
        this.bendable = bendable;
        this.bindLabel.setText(bendable == null ? "" : bendable.getBindText());
    }

    /** Stop captures in controls that are being hidden or detached. */
    public static void cancelCapturesInTree(GuiComponent component) {
        if (component instanceof BindableInputComponent) {
            ((BindableInputComponent)component).getCaptureTask().cancelCapture();
        }
        for (GuiComponent child : new ArrayList<GuiComponent>(component.f())) cancelCapturesInTree(child);
    }

    public BindCaptureTask getCaptureTask() {
        return this.captureTask;
    }

    /** Detached profile editors commit when their profile is applied. */
    public void setSaveChanges(boolean saveChanges) {
        this.saveChanges = saveChanges;
    }

    void saveBindChanges() {
        this.saveBindChanges(this.bendable);
    }

    void saveBindChanges(Bendable target) {
        if (!this.saveChanges) return;
        // A profile bind can belong to an inactive profile, while the generic
        // save notification marks only the currently active one dirty.
        if (target instanceof Profile) ((Profile)target).setDirty(true);
        Vape.INSTANCE.saveAndStop();
    }

    public BindableInputComponent(Bendable bendable) {
        this(bendable, null);
    }

    @Override
    public void H() {
        this.bindLabel.setVisible(!this.w$src$Z$e457mb() && !this.captureTask.isCapturing());
        if (this.bendable == null) {
            return;
        }
        this.bindLabel.setText(this.bendable.getBindText());
        double labelHeight = this.bindLabel.getTextHeight();
        double labelWidth = this.bindLabel.getRenderedWidth();
        Color effectiveAccentColor = this.accentColor == null ? ColorUtil.getAccentTextColor() : this.accentColor;
        Color labelColor = this.highlighted ? BindableInputComponent.J.Y : (this.isActive() ? effectiveAccentColor : BindableInputComponent.J.h);
        this.bindLabel.setTextColor(this.applyAlphaMultiplier(labelColor));
        float iconSize = 5.0f;
        float iconOffset = this.A() != 10.0 ? (float)this.A() - 6.0f : iconSize;
        if (this.captureTask.isCapturing()) {
            this.setExplicitWidth(10.0);
            this.o(this.getExplicitWidth());
            this.w("Click to remove bind");
        } else {
            if (this.bindLabel.getText() == null || this.bindLabel.getText().isEmpty()) {
                this.setExplicitWidth(10.0);
                this.o(this.getExplicitWidth());
            }
            String tooltipText = this.buildTooltipText();
            ToolTips toolTips = this.bindLabel.getToolTips() != null ? new ToolTips(this, tooltipText, 0.7, BindableInputComponent.J.h, false, this.bindLabel.getToolTips().getText(), 0.8, BindableInputComponent.J.Z, true) : new ToolTips(this, tooltipText, 0.7, BindableInputComponent.J.h, false);
            this.setToolTips(toolTips);
        }
        if (this.w$src$Z$e457mb() || this.captureTask.isCapturing()) {
            Color hoverBackgroundColor = new Color(255, 255, 255, this.isActive() ? this.activeAlpha : 17);
            GuiRenderPrimitives.d(this.G$src$D$1b2f02a(), this.n(), this.A(), this.L(), this.applyAlphaMultiplier(hoverBackgroundColor));
            ImageRenderer.drawImage(this.applyAlphaMultiplier(this.isActive() ? effectiveAccentColor : BindableInputComponent.J.Z), (float)this.G$src$D$1b2f02a() + iconOffset / 2.0f, (float)this.n() + iconSize / 2.0f - 0.5f, this.captureTask.isCapturing() ? "newclose" : "newedit", iconSize, iconSize, false);
            return;
        }
        if (this.bindLabel.getText() == null || this.bindLabel.getText().isEmpty()) {
            Color emptyBackgroundColor = new Color(255, 255, 255, this.isActive() ? this.activeAlpha : 17);
            GuiRenderPrimitives.d(this.G$src$D$1b2f02a(), this.n(), this.A(), this.L(), this.applyAlphaMultiplier(this.highlighted ? BindableInputComponent.J.N : emptyBackgroundColor));
            Color emptyBindIconColor = this.highlighted ? BindableInputComponent.J.I : (this.isActive() ? effectiveAccentColor : BindableInputComponent.J.h);
            ImageRenderer.drawImage(this.applyAlphaMultiplier(emptyBindIconColor), (float)this.G$src$D$1b2f02a() + iconOffset / 2.0f, (float)this.n() + iconSize / 2.0f - 0.5f, "newbind", iconSize, iconSize, false);
            return;
        }
        Color populatedBackgroundColor = this.highlighted ? BindableInputComponent.J.N : new Color(255, 255, 255, this.isActive() ? this.activeAlpha : 17);
        if (this.bindLabel.getText().length() == 1) {
            this.setExplicitWidth(10.0);
        } else {
            this.getClass();
            this.setExplicitWidth(labelWidth + 5.0);
        }
        this.o(this.getExplicitWidth());
        GuiRenderPrimitives.d(this.G$src$D$1b2f02a(), this.n(), this.A(), this.L(), this.applyAlphaMultiplier(populatedBackgroundColor));
        if (this.bendable.getActivationMode().equals((Object)BindActivationMode.HELD)) {
            GuiRenderPrimitives.a(this.G$src$D$1b2f02a() + 2.0, this.n() + this.L() - 1.0, this.A() - 4.0, 1.0f, J.z());
        }
        this.bindLabel.K(this.G$src$D$1b2f02a() + this.A() / 2.0 - labelWidth / 2.0);
        this.bindLabel.S(this.n() + this.L() / 2.0 - labelHeight / 2.0 - 0.5);
    }

    public Bendable getBendable() {
        return this.bendable;
    }


    public void setActiveOverride(@Nullable Boolean activeOverride) {
        this.activeOverride = activeOverride;
    }

    @Override
    public double x() {
        return 10.0;
    }

    private boolean isActive() {
        if (this.bendable == null) {
            return false;
        }
        if (this.activeOverride != null) {
            return this.activeOverride;
        }
        return this.bendable.isActive();
    }

    public BindableInputComponent(Bendable bendable, Color color) {
        this.activeAlpha = (int)LEGACY_ALPHA_SEED;
        this.accentColor = color;
        this.bendable = bendable;
        this.addClickListener(new BindableInputComponentCaptureToggleClickHandler(this));
        this.captureTask = new BindableInputComponentSavingBindCaptureTask(this, this.getBendable());
        this.bindLabel = new TruncatedTextComponent(bendable == null ? "" : bendable.getBindText(), "...", "", 50.0, 0.8, color == null ? ColorUtil.getAccentTextColor() : color, false, false);
        this.addChildren(this.bindLabel);
    }

    boolean isShiftPressed() {
        return KeyboardInput.isKeyDown(160) || KeyboardInput.isKeyDown(161);
    }

    boolean supportsActivationModeConfiguration() {
        return this.bendable != null && this.bendable.supportsActivationMode();
    }

    public void setHighlighted(boolean highlighted) {
        this.highlighted = highlighted;
    }

    public void setAccentColor(Color accentColor) {
        this.accentColor = accentColor;
    }

    String buildTooltipText() {
        String tooltipText = "Click to bind\nShift click to modify bind functionality";
        tooltipText = tooltipText + "\n";
        if (this.isShiftPressed()) {
            tooltipText = tooltipText + "\u00a7c";
        }
        tooltipText = tooltipText + "Bind functionality = " + this.bendable.getActivationMode().getDisplayName();
        return tooltipText;
    }

    @Override
    public double C() {
        return 10.0;
    }

    public TruncatedTextComponent getBindLabel() {
        return this.bindLabel;
    }

    public boolean isCapturing() {
        return this.captureTask.isCapturing();
    }

    private Color applyAlphaMultiplier(Color color) {
        if (color == null) {
            return null;
        }
        int adjustedAlpha = Math.max(0, Math.min(255, Math.round((float)color.getAlpha() * this.alphaMultiplier)));
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), adjustedAlpha);
    }

    public void setActiveAlpha(int activeAlpha) {
        this.activeAlpha = activeAlpha;
    }
}
