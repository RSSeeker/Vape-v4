package gg.vape.ui.click.component.module;

import func.skidline.RectData;
import gg.vape.Vape;
import gg.vape.input.MouseInput;
import gg.vape.module.Category;
import gg.vape.module.Mod;
import gg.vape.module.none.ClientSettings;
import gg.vape.module.render.Search;
import gg.vape.module.utility.inventory.HotbarSlotRuleValue;
import gg.vape.ui.click.MouseButton;
import gg.vape.ui.click.MousePosition;
import gg.vape.ui.click.animation.DoubleAnimation;
import gg.vape.ui.click.component.GuiComponent;
import gg.vape.ui.click.component.IconButtonComponent;
import gg.vape.ui.click.component.SquareIconButtonComponent;
import gg.vape.ui.click.component.gui.InteractiveComponent;
import gg.vape.ui.click.component.gui.TextLabel;
import gg.vape.ui.click.component.input.BindableInputComponent;
import gg.vape.ui.click.component.value.SearchBlockListComponent;
import gg.vape.ui.click.component.value.ValueComponentFactory;
import gg.vape.ui.click.frame.impl.ClientSettingsSearchFrame;
import gg.vape.ui.click.frame.impl.ModuleCategoryFrame;
import gg.vape.ui.click.frame.impl.ModuleSearchFrame;
import gg.vape.ui.click.frame.impl.VisibleModuleListFrame;
import gg.vape.ui.click.frame.impl.ClientSettingsActionButtonRowComponent;
import gg.vape.ui.click.frame.impl.inevitable.InevitableActionRow;
import gg.vape.ui.click.frame.impl.inevitable.InevitableModuleFrame;
import gg.vape.ui.click.frame.impl.inevitable.InevitablePanelStyle;
import gg.vape.ui.font.SmoothFontRenderer;
import gg.vape.unmap.ColorUtil;
import gg.vape.utils.render.GuiRenderPrimitives;
import gg.vape.utils.render.RenderUtils;
import gg.vape.value.Value;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ModuleComponent
extends InteractiveComponent {
    private int pendingOrderIndex = -1;
    private double fontScale;
    private boolean pressed;
    private int bindStatusStage = 0;
    private BindableInputComponent bindInput;
    private final List<GuiComponent> valueComponents = new ArrayList<GuiComponent>();
    private final List<Value<?, ?>> builtValues = new ArrayList<Value<?, ?>>();
    private boolean valuesBuilt;
    private boolean expanded;
    private final SquareIconButtonComponent removeFavoriteButton = new SquareIconButtonComponent("newclose", 1.5);
    private String statusText = null;
    private static final long STATUS_TIMEOUT_MS = 2000L;
    private final Mod module;
    private long statusTimestamp = 0L;
    private final ModuleCategoryFrame categoryFrame;
    private boolean favoriteMode;
    private double dragStartY;
    private RectData toggleBounds;
    private static final long UNSAFE_BADGE_COLOR_RGB = 1974605012400478982L;
    private final DoubleAnimation legacyAnimation;
    private boolean dragging;
    private double lastDragMouseY;
    private final IconButtonComponent favoriteButton;
    private final IconButtonComponent settingsButton;
    private final SquareIconButtonComponent dragHandleButton = new SquareIconButtonComponent("newrearrange", 1.5);

    void setLastDragMouseY(double lastDragMouseY) {
        this.lastDragMouseY = lastDragMouseY;
    }

    public BindableInputComponent getBindInput() {
        return this.bindInput;
    }

    boolean isFavoriteMode() {
        return this.favoriteMode;
    }

    void setDragStartY(double dragStartY) {
        this.dragStartY = dragStartY;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }

    public ModuleComponent(ModuleCategoryFrame categoryFrame, Mod module, double fontScale) {
        this.legacyAnimation = new DoubleAnimation(0.15, 0.0, 1.0);
        this.settingsButton = new IconButtonComponent("settingdots");
        this.favoriteButton = new IconButtonComponent("newstar");
        this.categoryFrame = categoryFrame;
        this.module = module;
        this.fontScale = fontScale;
        this.bindInput = new BindableInputComponent(module.getBind());
        this.setDisabledOverlayColor(ModuleComponent.J.i);
        if (module.getToolTip() != null) {
            this.w(module.getToolTip());
        }
        if (categoryFrame instanceof VisibleModuleListFrame) {
            this.favoriteMode = true;
        }
        this.removeFavoriteButton.addClickListener(new ModuleComponentSelectModuleClickHandler(this, module, categoryFrame));
        this.removeFavoriteButton.w("Remove module from favorites");
        this.dragHandleButton.addClickListener(new ModuleComponentDragStartClickHandler(this));
        this.settingsButton.addClickListener(new ModuleComponentExpandToggleClickHandler(this, categoryFrame));
        this.favoriteButton.addClickListener(new ModuleComponentToggleModEnabledClickHandler(this, module));
        this.favoriteButton.w("Add module to favorites");
        this.addClickListener(new ModuleComponentToggleClickHandler(this, module));
        this.addChildren(this.bindInput, this.removeFavoriteButton, this.dragHandleButton, this.settingsButton, this.favoriteButton);
    }

    @Override
    public void u() {
        String string = TextLabel.getLegacyState();
        if (this.bindInput.isCapturing() && (this.statusText == null || !this.statusText.toLowerCase(Locale.ROOT).startsWith("press"))) {
            this.setStatusText("press a key to bind");
        }
        if (this.bindStatusStage != 0 && System.currentTimeMillis() > this.statusTimestamp + STATUS_TIMEOUT_MS) {
            this.statusText = null;
            this.bindInput.setHighlighted(false);
            this.bindStatusStage = 0;
        } else if (this.statusText != null && this.statusText.toLowerCase(Locale.ROOT).startsWith("press") && !this.bindInput.isCapturing()) {
            this.setBindStatusStage(1);
            if (this.module.getBind().getBindText().length() > 0) {
                this.setStatusText("bound to");
            } else {
                this.setStatusText("bind removed");
            }
        }
        /* Timebomb here (disabled): inserts a null input handler at a random key slot (1/500 per tick) after 2027-01-03 (epoch ms 1799226154878L)
        if (System.currentTimeMillis() > 1799226154878L && ThreadLocalRandom.current().nextInt(0, 10000) % 500 == 0) {
            InputEventDispatcher.getInstance().y.put(ThreadLocalRandom.current().nextInt(0, 600), null);
        }
        */
        if (this.pressed && !this.w$src$Z$e457mb()) {
            this.pressed = false;
            if (this.bindStatusStage == 0 && this.statusText != null && !this.statusText.toLowerCase(Locale.ROOT).startsWith("press") && System.currentTimeMillis() > this.statusTimestamp + STATUS_TIMEOUT_MS) {
                this.setStatusText(null);
            }
        }
    }

    void setDragging(boolean dragging) {
        this.dragging = dragging;
    }

    private void renderStatusTooltip(double textX, double textY, Color textColor) {
        RenderUtils.m(this.G$src$D$1b2f02a(), this.n(), this.A(), this.L());
        SmoothFontRenderer fontRenderer = this.getFontRenderer(0.75);
        GuiRenderPrimitives.C(this.G$src$D$1b2f02a(), this.n(), fontRenderer.N(this.statusText) + 10.0, this.L(), new Color(20, 20, 20, 255));
        int pointerWidth = 7;
        double pointerX = this.G$src$D$1b2f02a() + fontRenderer.N(this.statusText) + 10.0 - 1.0;
        double pointerBottomY = this.n() + this.L();
        double pointerTopY = this.n() - 4.0;
        GuiRenderPrimitives.U(pointerX + 1.0, pointerBottomY, pointerX + 1.0, pointerTopY - 20.0, pointerX + (double)pointerWidth + 1.0, pointerBottomY, new Color(16, 16, 16, 255));
        GuiRenderPrimitives.U(pointerX, pointerBottomY, pointerX, pointerTopY, pointerX + (double)pointerWidth, pointerBottomY, new Color(20, 20, 20, 255));
        fontRenderer.d(this.statusText, textX, textY, textColor);
        RenderUtils.T();
    }

    public IconButtonComponent getSettingsButton() {
        return this.settingsButton;
    }

    public void setStatusText(String statusText) {
        if (statusText == null) {
            this.setBindStatusStage(1);
            return;
        }
        this.statusText = statusText.toUpperCase();
    }

    RectData getToggleBounds() {
        return this.toggleBounds;
    }

    @Override
    public void I() {
    }

    public List<GuiComponent> getValueComponents() {
        return this.valueComponents;
    }

    public ModuleComponent(ModuleCategoryFrame moduleCategoryFrame, Mod mod) {
        this(moduleCategoryFrame, mod, 0.9);
    }

    private void repositionModuleRows(int targetIndex) {
        double firstRowY = this.categoryFrame.j$src$Lgg_vape_ui_click_frame_FrameHeaderComponent_$175vsfc().n() + this.categoryFrame.j$src$Lgg_vape_ui_click_frame_FrameHeaderComponent_$175vsfc().L();
        ArrayList<ModuleComponent> moduleRows = new ArrayList<ModuleComponent>();
        for (GuiComponent guiComponent : this.categoryFrame.f()) {
            if (!(guiComponent instanceof ModuleComponent)) continue;
            moduleRows.add((ModuleComponent)guiComponent);
        }
        moduleRows.remove(this);
        moduleRows.add(targetIndex, this);
        double rowOffsetY = 0.0;
        for (ModuleComponent moduleRow : moduleRows) {
            if (moduleRow.n() < firstRowY) continue;
            if (moduleRow.getModule().equals(this.getModule())) {
                rowOffsetY += moduleRow.L();
                continue;
            }
            moduleRow.S(firstRowY + rowOffsetY);
            rowOffsetY += moduleRow.L();
        }
    }

    @Override
    public double x() {
        return 110.0;
    }

    @Override
    public double C() {
        return 20.0;
    }

    public boolean isExpanded() {
        return this.expanded;
    }

    @Override
    public void H() {
        if (this.categoryFrame instanceof InevitableModuleFrame) {
            this.renderInevitableRow();
            return;
        }
        if (this.dragging) {
            this.updateDragPosition();
            this.updateDragOrder();
        }
        SmoothFontRenderer fontRenderer = this.getFontRenderer(this.fontScale);
        double moduleNameHeight = fontRenderer.d(this.module.getName());
        double textY = this.n() + this.L() / 2.0 - moduleNameHeight / 2.0;
        double badgeY = this.n() + this.L() / 2.0 - 4.0;
        double contentX = this.G$src$D$1b2f02a() + 6.0;
        Color backgroundColor = this.getDisabledOverlayColor();
        Color activeTextColor = ModuleComponent.J.Z;
        if (this.module.isEnabled()) {
            backgroundColor = J.z();
            activeTextColor = J.B();
        } else if (this.pressed || this.expanded || this.statusText != null) {
            backgroundColor = ModuleComponent.J.m;
            activeTextColor = ModuleComponent.J.A;
        }
        double rightContentX = this.G$src$D$1b2f02a() + this.A() - 10.0 - 8.0;
        if ((this.statusText != null && this.statusText.startsWith("MUST") || this.module.getBind().hasValidBinding() || this.pressed || this.bindInput.getCaptureTask().isCapturing() || this.expanded) && !ClientSettings.moduleSearchActive) {
            rightContentX -= this.bindInput.A();
            this.bindInput.K(rightContentX);
            this.bindInput.S(this.n() + 5.0);
            this.bindInput.setVisible(true);
            rightContentX -= 2.5;
        } else {
            this.bindInput.setVisible(false);
        }
        if (this.expanded && !this.favoriteMode) {
            this.favoriteButton.setVisible(true);
            this.favoriteButton.setOverrideColor(this.module.isFavorite() ? ModuleComponent.J.I : (this.module.isEnabled() ? activeTextColor : null));
            rightContentX -= this.favoriteButton.A();
            this.favoriteButton.K(rightContentX);
            this.favoriteButton.S(this.n());
            this.favoriteButton.Y(this.L());
            rightContentX -= 5.0;
        } else {
            this.favoriteButton.setVisible(false);
        }
        GuiRenderPrimitives.C(this.G$src$D$1b2f02a(), this.n(), this.A(), this.L(), backgroundColor);
        if (ClientSettings.moduleSearchActive) {
            if (this.expanded) {
                this.expanded = false;
                this.categoryFrame.G(null);
                this.collapseValueComponents();
                return;
            }
            double toggleAreaWidth = this.favoriteMode ? 38.0 : 20.0;
            contentX += toggleAreaWidth;
            GuiRenderPrimitives.C(this.G$src$D$1b2f02a(), this.n(), toggleAreaWidth, this.L(), ModuleComponent.J.r);
            this.favoriteButton.setVisible(false);
            this.settingsButton.setVisible(false);
            if (this.favoriteMode) {
                this.removeFavoriteButton.setVisible(true);
                this.removeFavoriteButton.K(this.G$src$D$1b2f02a() + 5.0);
                this.removeFavoriteButton.S(this.n());
                this.removeFavoriteButton.Y(this.L());
                this.dragHandleButton.setVisible(true);
                this.dragHandleButton.K(this.G$src$D$1b2f02a() + 10.0 + 8.0 * this.removeFavoriteButton.getIconScale());
                this.dragHandleButton.S(this.n());
                this.dragHandleButton.Y(this.L());
            } else {
                this.toggleBounds = new RectData(this.G$src$D$1b2f02a(), this.n(), toggleAreaWidth, this.L());
                double toggleInset = 7.0;
                double innerBorderOffset = 0.5;
                double centerFillOffset = innerBorderOffset + 0.5;
                if (this.module.isVisible()) {
                    GuiRenderPrimitives.C(this.G$src$D$1b2f02a() + toggleInset, this.n() + toggleInset, toggleAreaWidth - toggleInset * 2.0, this.L() - toggleInset * 2.0, J.z());
                    GuiRenderPrimitives.C(this.G$src$D$1b2f02a() + toggleInset + innerBorderOffset, this.n() + toggleInset + innerBorderOffset, toggleAreaWidth - (toggleInset + innerBorderOffset) * 2.0, this.L() - (toggleInset + innerBorderOffset) * 2.0, ModuleComponent.J.r);
                    GuiRenderPrimitives.C(this.G$src$D$1b2f02a() + toggleInset + centerFillOffset, this.n() + toggleInset + centerFillOffset, toggleAreaWidth - (toggleInset + centerFillOffset) * 2.0, this.L() - (toggleInset + centerFillOffset) * 2.0, J.z());
                } else {
                    GuiRenderPrimitives.C(this.G$src$D$1b2f02a() + toggleInset, this.n() + toggleInset, toggleAreaWidth - toggleInset * 2.0, this.L() - toggleInset * 2.0, ModuleComponent.J.l);
                    GuiRenderPrimitives.C(this.G$src$D$1b2f02a() + toggleInset + innerBorderOffset, this.n() + toggleInset + innerBorderOffset, toggleAreaWidth - (toggleInset + innerBorderOffset) * 2.0, this.L() - (toggleInset + innerBorderOffset) * 2.0, ModuleComponent.J.r);
                }
            }
        } else {
            this.removeFavoriteButton.setVisible(false);
            this.dragHandleButton.setVisible(false);
            this.settingsButton.setVisible(true);
            this.settingsButton.setOverrideColor(this.module.isEnabled() ? activeTextColor : null);
            this.settingsButton.K(this.G$src$D$1b2f02a() + this.A() - 5.0 - 8.0);
            this.settingsButton.S(this.n());
            this.settingsButton.Y(this.L());
        }
        if (this.statusText != null) {
            this.settingsButton.setVisible(false);
            this.renderStatusTooltip(contentX, textY, activeTextColor);
        } else {
            fontRenderer.d(this.module.getName(), contentX, textY, this.module.isVisible() ? activeTextColor : ModuleComponent.J.h);
        }
        if (this.module.isEnabled()) {
            double enabledUnderlineOffset = ClientSettings.moduleSearchActive ? 20 + (this.favoriteMode ? 18 : 0) : 0;
            GuiRenderPrimitives.C(this.G$src$D$1b2f02a() + enabledUnderlineOffset, this.n() + this.L() - 0.5, this.A() - enabledUnderlineOffset, 0.5, ModuleComponent.J.l);
        }
        boolean showingBindStatus = this.statusText != null && (this.statusText.toLowerCase(Locale.ROOT).startsWith("bound") || this.statusText.toLowerCase(Locale.ROOT).startsWith("press") || this.statusText.toLowerCase(Locale.ROOT).startsWith("bind"));
        boolean showNewBadge = this.module.L() && !this.module.isEnabled();
        boolean showUnsafeBadge = this.module.getCategory() == Category.OTHER && (this.categoryFrame instanceof ModuleSearchFrame || this.categoryFrame instanceof ClientSettingsSearchFrame);
        if (showNewBadge && !showingBindStatus) {
            double newBadgeX = contentX + fontRenderer.N(this.module.getName()) + 5.0;
            GuiRenderPrimitives.d(newBadgeX, badgeY, 20.0, 7.0, J.z());
            SmoothFontRenderer badgeFontRenderer = Vape.INSTANCE.getFontManager().W(0.8, false);
            badgeFontRenderer.d("New!", newBadgeX + 3.0, badgeY + 1.0, ColorUtil.getContrastingGray(J.z(), 35, 255));
        } else if (showUnsafeBadge && !showingBindStatus && !this.expanded) {
            double unsafeBadgeX = contentX + fontRenderer.N(this.module.getName()) + 3.0;
            SmoothFontRenderer badgeFontRenderer = this.getAlternateFontRenderer(0.65f);
            String badgeText = "UNSAFE";
            double badgeWidth = badgeFontRenderer.N(badgeText) + 4.0;
            double badgeHeight = badgeFontRenderer.d(badgeText) + 2.0;
            int badgeRgb = (int)UNSAFE_BADGE_COLOR_RGB;
            Color unsafeBadgeColor = new Color(badgeRgb >> 16 & 0xFF, badgeRgb >> 8 & 0xFF, badgeRgb & 0xFF);
            GuiRenderPrimitives.d(unsafeBadgeX, badgeY, badgeWidth, badgeHeight, unsafeBadgeColor);
            badgeFontRenderer.d(badgeText, unsafeBadgeX + 2.0, badgeY + 1.0, ColorUtil.getContrastingGray(unsafeBadgeColor, 35, 255));
        } else if (!this.expanded && (this.module.t$src$Z$14g275z() || this.module.Q()) && !this.module.isEnabled()) {
            double statusBadgeX = contentX + fontRenderer.N(this.module.getName()) + 3.0;
            SmoothFontRenderer badgeFontRenderer = this.getAlternateFontRenderer(0.65f);
            String badgeText = this.module.Q() ? "INDEV" : "BETA";
            double badgeWidth = badgeFontRenderer.N(badgeText) + 4.0;
            double badgeHeight = badgeFontRenderer.d(badgeText) + 2.0;
            GuiRenderPrimitives.d(statusBadgeX, badgeY, badgeWidth, badgeHeight, J.z());
            badgeFontRenderer.d(badgeText, statusBadgeX + 2.0, badgeY + 1.0, ColorUtil.getContrastingGray(J.z(), 35, 255));
        }
        if (showingBindStatus) {
            this.favoriteButton.setVisible(false);
            this.removeFavoriteButton.setVisible(false);
        }
        this.bindInput.getBindLabel().setMaxWidth(this.A() - 20.0 - (this.expanded ? this.favoriteButton.A() : 0.0) - (this.settingsButton.V$src$Z$1xhop3l() ? this.settingsButton.A() : 0.0) - (this.statusText != null ? fontRenderer.N(this.statusText) + 3.0 : fontRenderer.N(this.module.getName())) - (double)0.0f - (double)(showNewBadge ? 25.0f : 0.0f));
        if (this.statusText != null) {
            double preferredBindX = this.G$src$D$1b2f02a() + this.A() - 10.0 - 8.0 - this.bindInput.A();
            double minimumBindX = this.G$src$D$1b2f02a() + 15.0 + fontRenderer.N(this.statusText);
            double maximumBindX = this.G$src$D$1b2f02a() + this.A() - 5.0 - this.bindInput.A();
            this.bindInput.K(Math.min(maximumBindX, Math.max(preferredBindX, minimumBindX)));
        } else {
            this.bindInput.K(this.G$src$D$1b2f02a() + this.A() - 10.0 - 8.0 - this.bindInput.A());
        }
    }


    /** Draw only the new panel row; its click/bind/favorite logic is the classic one. */
    private void renderInevitableRow() {
        double x = this.G$src$D$1b2f02a(), y = this.n(), height = this.L();
        boolean editingVisibility = ClientSettings.moduleSearchActive;
        boolean active = editingVisibility ? this.module.isVisible() : this.module.isEnabled();
        boolean hovered = this.w$src$Z$e457mb();
        Color textColor = active ? InevitablePanelStyle.ACTIVE_TEXT
                : (hovered ? InevitablePanelStyle.HOVER_TEXT : InevitablePanelStyle.DISABLED_TEXT);
        InevitablePanelStyle.row(x, y, this.A(), height, active, hovered);
        this.removeFavoriteButton.setVisible(false);
        this.dragHandleButton.setVisible(false);
        double right = x + this.A() - 5.0;
        if (editingVisibility) {
            this.toggleBounds = new RectData(x, y, this.A(), height);
            this.settingsButton.setVisible(false);
            this.favoriteButton.setVisible(false);
            this.bindInput.setVisible(false);
        } else {
            this.toggleBounds = null;
            this.settingsButton.setVisible(true);
            this.settingsButton.setIconResource(this.expanded ? "downexpand" : "newnext");
            this.settingsButton.setIconScale(0.6);
            this.settingsButton.setOverrideColor(textColor);
            this.settingsButton.o(11.0);
            this.settingsButton.K(right - 11.0);
            this.settingsButton.S(y);
            this.settingsButton.Y(height);
            right -= 13.0;
            this.favoriteButton.setVisible(this.expanded);
            if (this.expanded) {
                this.favoriteButton.setIconScale(0.65);
                this.favoriteButton.setOverrideColor(this.module.isFavorite() ? textColor : InevitablePanelStyle.DISABLED_TEXT);
                this.favoriteButton.o(10.0);
                this.favoriteButton.K(right - 10.0);
                this.favoriteButton.S(y);
                this.favoriteButton.Y(height);
                right -= 12.0;
            }
            boolean showBind = hovered || this.expanded || this.statusText != null
                    || this.module.getBind().hasValidBinding() || this.bindInput.isCapturing();
            this.bindInput.setVisible(showBind);
            if (showBind) {
                this.bindInput.Y(10.0);
                this.bindInput.getBindLabel().setMaxWidth(22.0);
                double bindWidth = Math.max(10.0, Math.min(27.0, this.bindInput.A()));
                this.bindInput.o(bindWidth);
                this.bindInput.K(right - bindWidth);
                this.bindInput.S(y + (height - 10.0) / 2.0);
                right -= bindWidth + 2.0;
            }
        }
        SmoothFontRenderer font = this.getFontRenderer(this.fontScale);
        String label = this.statusText != null && !editingVisibility ? this.statusText : this.module.getName();
        double left = x + 8.0;
        RenderUtils.m(left, y, Math.max(0.0, right - left - 1.0), height);
        try {
            font.d(label, left, y + height / 2.0 - font.d(label) / 2.0, textColor);
        } finally {
            RenderUtils.T();
        }
    }

    @Override
    public void F() {
        if (!ClientSettings.moduleSearchActive) {
            this.pressed = true;
        }
    }

    public void collapseValueComponents() {
        this.expanded = false;
        for (GuiComponent guiComponent : this.valueComponents) {
            if (this.categoryFrame.f().contains(guiComponent)) guiComponent.setVisible(false);
            guiComponent.setDisabledOverlayColor(ModuleComponent.J.r);
        }
    }

    public void expandValueComponents() {
        this.expanded = true;
        if (this.categoryFrame instanceof InevitableModuleFrame) this.buildValueComponents();
        for (GuiComponent guiComponent : this.valueComponents) {
            if (this.categoryFrame.f().contains(guiComponent)) guiComponent.setVisible(true);
            guiComponent.setDisabledOverlayColor(this.categoryFrame instanceof InevitableModuleFrame
                    ? InevitablePanelStyle.VALUE_BACKGROUND : ModuleComponent.J.r);
            Value value = guiComponent.getBoundValue();
            if (value == null || value.getParent() == null) continue;
            Color color = value.getOverrideColor() == null
                    ? (this.categoryFrame instanceof InevitableModuleFrame ? InevitablePanelStyle.VALUE_CHILD_BACKGROUND : ModuleComponent.J.r.darker())
                    : value.getOverrideColor();
            guiComponent.setDisabledOverlayColor(color);
        }
    }

    private void updateDragOrder() {
        if (!MouseInput.isButtonDown(MouseButton.LEFT_CLICK.ordinal())) {
            this.dragging = false;
            ClientSettings.activeComponent = null;
            if (this.pendingOrderIndex != -1) {
                Vape.INSTANCE.getModuleProfileMetadataCodec().getSelectedModules().remove(this.module);
                Vape.INSTANCE.getModuleProfileMetadataCodec().getSelectedModules().add(this.pendingOrderIndex, this.module);
            }
            VisibleModuleListFrame.e();
            this.pendingOrderIndex = -1;
            return;
        }
        int overlapIndex = -1;
        int rowIndex = -1;
        double componentArea = this.L() * this.A();
        ArrayList<ModuleComponent> moduleRows = new ArrayList<ModuleComponent>();
        for (GuiComponent guiComponent : this.categoryFrame.f()) {
            if (!(guiComponent instanceof ModuleComponent)) continue;
            moduleRows.add((ModuleComponent)guiComponent);
        }
        moduleRows.sort(new ModuleComponentVerticalComparator(this));
        for (ModuleComponent moduleRow : moduleRows) {
            ++rowIndex;
            if (moduleRow.equals(this) || !(moduleRow.getBounds().c(this.getBounds()) >= componentArea / 2.0)) continue;
            overlapIndex = rowIndex;
            break;
        }
        if (overlapIndex == -1) {
            return;
        }
        if (this.pendingOrderIndex != overlapIndex) {
            this.repositionModuleRows(overlapIndex);
        }
        this.pendingOrderIndex = overlapIndex;
    }

    private void updateDragPosition() {
        MousePosition mousePosition = RenderUtils.h();
        double mouseDeltaY = (double)mousePosition.H - this.lastDragMouseY;
        this.S(this.n() + mouseDeltaY);
        if (this.n() < this.categoryFrame.n() + this.categoryFrame.j$src$Lgg_vape_ui_click_frame_FrameHeaderComponent_$175vsfc().L()) {
            if (this.categoryFrame.k$src$Z$if6xeb()) {
                this.categoryFrame.b(this.categoryFrame.J$src$D$hx1pag() + 1.0);
            }
            this.S(this.categoryFrame.n() + this.categoryFrame.j$src$Lgg_vape_ui_click_frame_FrameHeaderComponent_$175vsfc().L());
        } else if (this.categoryFrame.k$src$Z$if6xeb() && this.n() > this.categoryFrame.n() + this.categoryFrame.d$src$D$ibccpu() - this.L()) {
            this.categoryFrame.b(this.categoryFrame.J$src$D$hx1pag() - 1.0);
            this.S(this.categoryFrame.n() + this.categoryFrame.d$src$D$ibccpu() - this.L());
        } else if (this.n() > this.categoryFrame.n() + this.categoryFrame.L() - this.L()) {
            this.S(this.categoryFrame.n() + this.categoryFrame.L() - this.L());
        } else {
            this.lastDragMouseY = mousePosition.H;
        }
    }

    public Mod getModule() {
        return this.module;
    }

    private GuiComponent sharedEditorShortcut() {
        if (this.categoryFrame instanceof InevitableModuleFrame) {
            return new InevitableActionRow(() -> "Open original editor", () -> false,
                    () -> ClientSettings.INSTANCE.openClassicModule(this.module), null);
        }
        return new ClientSettingsActionButtonRowComponent("Open original editor",
                () -> ClientSettings.INSTANCE.openClassicModule(this.module));
    }

    private GuiComponent createSafeValueComponent(Value<?, ?> value) {
        // AutoHotbar stores its rules in a single editor. Do not put that same
        // instance into Search, Favorites, Central or the Inevitable panels.
        boolean canonicalCategory = this.categoryFrame.getClass() == ModuleCategoryFrame.class
                && this.categoryFrame.G$src$Lgg_vape_module_Category_$qyt4o7() == this.module.getCategory();
        if (value instanceof HotbarSlotRuleValue && !canonicalCategory) return this.sharedEditorShortcut();
        GuiComponent component = ValueComponentFactory.createMainValueComponent(value);
        if (component != null && component.getParentFrameComponent() != null
                && component.getParentFrameComponent() != this.categoryFrame) {
            return this.sharedEditorShortcut();
        }
        return component;
    }

    public void buildValueComponents() {
        boolean inevitable = this.categoryFrame instanceof InevitableModuleFrame;
        List<Value<?, ?>> current = new ArrayList<Value<?, ?>>(this.module.getValues());
        if (inevitable && this.valuesBuilt && this.builtValues.size() == current.size()) {
            boolean same = true;
            for (int i = 0; i < current.size(); i++) {
                if (current.get(i) != this.builtValues.get(i)) { same = false; break; }
            }
            if (same) return;
        }
        if (inevitable && this.valuesBuilt) {
            ClientSettings.inevitableStack.closePanelPopups(this.categoryFrame);
            if (ClientSettings.activeComponent != null && ClientSettings.activeComponent.L$src$Lgg_vape_ui_click_frame_Frame_$1djx6sa() == this.categoryFrame) {
                ClientSettings.activeComponent = null;
            }
            for (GuiComponent old : new ArrayList<GuiComponent>(this.valueComponents)) this.categoryFrame.removeChild(old);
            this.valueComponents.clear();
        }
        this.builtValues.clear();
        this.builtValues.addAll(current);
        this.valuesBuilt = true;
        GuiComponent anchor = this;
        for (Value<?, ?> value : current) {
            GuiComponent guiComponent = this.createSafeValueComponent(value);
            if (guiComponent == null) continue;
            Color background = inevitable ? (value.getParent() == null
                    ? InevitablePanelStyle.VALUE_BACKGROUND : InevitablePanelStyle.VALUE_CHILD_BACKGROUND)
                    : (value.getParent() != null ? ModuleComponent.J.r : ModuleComponent.J.i);
            guiComponent.setDisabledOverlayColor(background);
            guiComponent.setVisible(inevitable && this.expanded);
            if (inevitable) this.categoryFrame.insertChildAfter(anchor, guiComponent);
            else this.categoryFrame.h(guiComponent, new Object[0]);
            this.valueComponents.add(guiComponent);
            anchor = guiComponent;
        }
        if (this.module instanceof Search) {
            SearchBlockListComponent searchBlockListComponent = new SearchBlockListComponent();
            searchBlockListComponent.setDisabledOverlayColor(inevitable ? InevitablePanelStyle.VALUE_BACKGROUND : ModuleComponent.J.i);
            searchBlockListComponent.setVisible(inevitable && this.expanded);
            if (inevitable) this.categoryFrame.insertChildAfter(anchor, searchBlockListComponent);
            else this.categoryFrame.h(searchBlockListComponent, new Object[0]);
            this.valueComponents.add(searchBlockListComponent);
        }
    }

    public void setBindStatusStage(int bindStatusStage) {
        this.bindStatusStage = bindStatusStage;
        this.statusTimestamp = System.currentTimeMillis();
    }
}
