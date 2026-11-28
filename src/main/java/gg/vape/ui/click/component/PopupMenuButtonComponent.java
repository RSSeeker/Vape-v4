package gg.vape.ui.click.component;

import gg.vape.module.none.ClientSettings;
import gg.vape.ui.click.GuiMouseEvent;
import gg.vape.ui.click.animation.ColorAnimation;
import gg.vape.ui.click.component.GuiComponent;
import gg.vape.ui.click.component.PanelComponent;
import gg.vape.ui.click.component.SpacerComponent;
import gg.vape.ui.click.component.gui.InteractiveComponent;
import gg.vape.ui.click.component.gui.InteractivePopupOutsideCloseMouseListener;
import gg.vape.ui.click.component.layout.PaddedComponent;
import gg.vape.ui.click.frame.PopupFrame;
import gg.vape.ui.font.SmoothFontRenderer;
import gg.vape.utils.render.GuiRenderPrimitives;
import gg.vape.utils.render.ImageRenderer;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

public class PopupMenuButtonComponent
extends InteractiveComponent {
    private ColorAnimation backgroundAnimation;
    private final float arrowSize;
    private float borderRadius;
    private final Color hoverTextColor;
    private boolean interactionBlocked;
    private boolean useContentInset;
    private boolean openUpward;
    private boolean enabled;
    @Nullable
    private Color secondaryColor;
    private final ColorAnimation textAnimation;
    private Color borderColor;
    @Nullable
    private Color dividerColor;
    private final PanelComponent menuPanel;
    private boolean pressed;
    private final Color normalTextColor;
    private final List<GuiComponent> menuItems;
    private static final float ARROW_SECTION_WIDTH = 12.5f;
    @Nullable
    private PopupFrame popupFrame;
    private final ColorAnimation interactionAnimation;
    private boolean centerLabel;
    private float borderAlpha;
    private final String label;

    @Nullable
    public Color getSecondaryColor() {
        return this.secondaryColor;
    }

    @Override
    public double x() {
        return 0.0;
    }

    public void setUseContentInset(boolean useContentInset) {
        this.useContentInset = useContentInset;
    }

    public boolean isLabelCentered() {
        return this.centerLabel;
    }

    public void setSecondaryColor(@Nullable Color secondaryColor) {
        this.secondaryColor = secondaryColor;
    }

    public boolean isUsingContentInset() {
        return this.useContentInset;
    }

    public boolean isOpeningUpward() {
        return this.openUpward;
    }

    public void setCenterLabel(boolean centerLabel) {
        this.centerLabel = centerLabel;
    }

    @Override
    public void I() {
    }

    public void setDividerColor(@Nullable Color dividerColor) {
        this.dividerColor = dividerColor;
    }

    public PopupMenuButtonComponent(String label, List<GuiComponent> menuItems, Color baseBackgroundColor, Color hoverBackgroundColor, Color borderColor, float borderRadius, float borderAlpha) {
        this.getClass();
        this.interactionAnimation = new ColorAnimation(0.15, PopupMenuButtonComponent.J.l, PopupMenuButtonComponent.J.y);
        this.arrowSize = 2.0f;
        this.normalTextColor = PopupMenuButtonComponent.J.Z;
        this.hoverTextColor = PopupMenuButtonComponent.J.Z;
        this.borderColor = null;
        this.borderRadius = 2.0f;
        this.borderAlpha = 1.0f;
        this.dividerColor = new Color(255, 255, 255, 30);
        this.secondaryColor = new Color(255, 255, 255, 30);
        this.useContentInset = true;
        this.centerLabel = true;
        this.enabled = true;
        this.label = label;
        this.menuItems = new ArrayList<GuiComponent>(menuItems);
        for (GuiComponent menuItem : menuItems) {
            menuItem.setUseExplicitHeight(true);
            menuItem.setUseExplicitWidth(true);
        }
        if (baseBackgroundColor != null) {
            this.setDisabledOverlayColor(baseBackgroundColor);
        }
        this.menuPanel = new PanelComponent(110.0, 20.0);
        this.menuPanel.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("wrap");
        this.menuPanel.t(120.0);
        this.menuPanel.setShowDisabledOverlay(false);
        this.menuPanel.I(true);
        this.getClass();
        this.backgroundAnimation = new ColorAnimation(0.15, this.getDisabledOverlayColor(), hoverBackgroundColor);
        this.getClass();
        this.textAnimation = new ColorAnimation(0.15, this.normalTextColor, this.hoverTextColor);
        if (borderColor != null && baseBackgroundColor != null) {
            this.setBorderAndBackgroundColors(borderColor, baseBackgroundColor);
        }
        this.borderRadius = borderRadius;
        this.borderAlpha = borderAlpha;
    }

    @Override
    public void u() {
        if (this.pressed && !this.w$src$Z$e457mb() && !this.isPopupOpen()) {
            this.interactionAnimation.J();
            this.pressed = false;
        }
    }

    @Override
    public double C() {
        return 0.0;
    }

    @Override
    public void H() {
        double renderHeight;
        double renderY;
        PopupFrame currentPopup = this.popupFrame;
        if (currentPopup != null) {
            if (this.openUpward) {
                renderY = this.n() - currentPopup.L();
                renderHeight = this.L() + currentPopup.L();
            } else {
                renderY = this.n();
                renderHeight = this.L() + currentPopup.L();
            }
        } else {
            renderY = this.n();
            renderHeight = this.L();
        }
        if (this.isPopupOpen()) {
            this.backgroundAnimation.u(this.w$src$Z$e457mb() && !this.isPopupOpen());
            GuiRenderPrimitives.d(this.G$src$D$1b2f02a(), renderY, this.A(), renderHeight, this.backgroundAnimation.getInterpolatedColor());
        } else {
            if (this.isShowDisabledOverlay()) {
                GuiRenderPrimitives.d(this.G$src$D$1b2f02a(), renderY, this.A(), renderHeight, this.backgroundAnimation.getInterpolatedColor());
            }
            if (this.w$src$Z$e457mb()) {
                GuiRenderPrimitives.d(this.G$src$D$1b2f02a(), renderY, this.A(), renderHeight, new Color(100, 100, 100, 10));
            }
        }
        if (this.borderColor != null) {
            GuiRenderPrimitives.P(this.G$src$D$1b2f02a(), renderY, this.A(), renderHeight, this.borderColor, this.borderRadius, this.borderAlpha, 1.0f);
        }
        SmoothFontRenderer fontRenderer = this.getAlternateFontRenderer(0.7);
        double labelHeight = fontRenderer.d(this.label);
        double labelX = this.G$src$D$1b2f02a();
        double labelY = this.n() + this.L() / 2.0 - labelHeight / 2.0;
        double centerY = this.n() + this.L() / 2.0;
        this.getClass();
        double arrowY = centerY - this.arrowSize / 2.0;
        if (this.centerLabel) {
            labelX += (this.A() - ARROW_SECTION_WIDTH) / 2.0;
            fontRenderer.W(this.label, labelX, labelY, PopupMenuButtonComponent.J.A);
        } else {
            this.getClass();
            labelX += 5.0;
            fontRenderer.d(this.label, labelX, labelY, PopupMenuButtonComponent.J.A);
        }
        if (this.dividerColor != null) {
            GuiRenderPrimitives.C(this.G$src$D$1b2f02a() + this.A() - ARROW_SECTION_WIDTH, this.n() + 2.0, 1.0, this.L() - 4.5, this.dividerColor);
        }
        ImageRenderer.drawImage(Color.WHITE, (float)(this.G$src$D$1b2f02a() + this.A()) - 8.0f, (float)arrowY, this.isPopupOpen() ? "upcollapse" : "downexpand", this.arrowSize, this.arrowSize, false);
    }

    public boolean isPopupOpen() {
        return this.popupFrame != null;
    }

    public String getLabel() {
        return this.label;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setInteractionBlocked(boolean interactionBlocked) {
        this.interactionBlocked = interactionBlocked;
    }


    @Nullable
    public Color getDividerColor() {
        return this.dividerColor;
    }

    @Override
    public void F() {
        if (!this.pressed) {
            this.interactionAnimation.J();
        }
        this.pressed = true;
    }

    public boolean isInteractionBlocked() {
        return this.interactionBlocked;
    }

    public void togglePopup() {
        PopupFrame popupFrame = this.popupFrame;
        if (popupFrame != null) {
            this.popupFrame = null;
            ClientSettings.activeComponent = null;
            ClientSettings.removePopup(popupFrame);
        } else {
            this.menuPanel.removeMarkedChildren();
            if (this.useContentInset) {
                this.menuPanel.setExplicitWidth(this.A() - this.getHorizontalInset());
            } else {
                this.menuPanel.setExplicitWidth(this.A());
            }
            this.menuPanel.setCornerRadius(3.0f);
            this.menuPanel.h(new SpacerComponent(0.0, 2.0), new Object[0]);
            for (GuiComponent guiComponent : this.menuItems) {
                guiComponent.setExplicitWidth(this.menuPanel.A() - 3.0);
                guiComponent.setExplicitHeight(12.0);
                this.menuPanel.h(new PaddedComponent(0.0, 0.5, 0.0, 0.0, guiComponent), "wrap");
            }
            this.menuPanel.setExplicitHeight(Math.min(this.menuPanel.d$src$D$ibccpu(), this.menuPanel.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().y()));
            this.menuPanel.H(true);
            this.popupFrame = ClientSettings.createPopup(this, this.menuPanel, PopupFrame.class);
            this.popupFrame.addGlobalMouseListener(new InteractivePopupOutsideCloseMouseListener(this));
            ClientSettings.activeComponent = this;
            this.updatePopupPosition();
        }
    }

    public void setBorderAndBackgroundColors(Color borderColor, Color baseBackgroundColor) {
        this.borderColor = borderColor;
        this.getClass();
        this.backgroundAnimation = new ColorAnimation(0.15, baseBackgroundColor, this.backgroundAnimation.getEndColor());
    }

    @Nullable
    public PopupFrame getPopupFrame() {
        return this.popupFrame;
    }

    /** Close without opening a new menu or clearing another control's focus. */
    public void closePopup() {
        PopupFrame popup = this.popupFrame;
        if (popup == null) return;
        this.popupFrame = null;
        if (ClientSettings.activeComponent == this) ClientSettings.activeComponent = null;
        ClientSettings.removePopup(popup);
    }

    @Override
    public void g(GuiMouseEvent mouseEvent) {
        if (this.dividerColor != null && (double)mouseEvent.getX() < this.G$src$D$1b2f02a() + this.A() - ARROW_SECTION_WIDTH && (double)mouseEvent.getY() > this.n()) {
            super.g(mouseEvent);
            ClientSettings.activeComponent = null;
            return;
        }
        if (this.interactionBlocked) {
            return;
        }
        PopupFrame popupFrame = this.popupFrame;
        if (popupFrame != null) {
            if (popupFrame.t()) {
                popupFrame.dispatchMouseEvent(mouseEvent);
                this.togglePopup();
                return;
            }
            if (!popupFrame.t()) {
                this.togglePopup();
            }
            return;
        }
        this.togglePopup();
    }

    public void setBorderWithTransparentBackground(Color borderColor) {
        this.borderColor = borderColor;
        this.getClass();
        this.backgroundAnimation = new ColorAnimation(0.15, new Color(0, 0, 0, 0), this.backgroundAnimation.getEndColor());
    }

    @Override
    public void c() {
        super.c();
        PopupFrame popupFrame = this.popupFrame;
        if (popupFrame != null) {
            this.updatePopupPosition();
            this.menuPanel.setDisabledOverlayColor(this.backgroundAnimation.getInterpolatedColor());
            this.menuPanel.H(true);
            this.menuPanel.c();
        }
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setOpenUpward(boolean openUpward) {
        this.openUpward = openUpward;
    }

    private void updatePopupPosition() {
        PopupFrame popupFrame = this.popupFrame;
        if (popupFrame != null) {
            if (this.useContentInset) {
                popupFrame.K(this.G$src$D$1b2f02a() + this.getHorizontalInset());
            } else {
                popupFrame.K(this.G$src$D$1b2f02a());
            }
            if (this.openUpward) {
                popupFrame.S(this.n() - popupFrame.L());
            } else {
                popupFrame.S(this.n() + this.L());
            }
        }
    }
}
