package gg.vape.ui.click.frame.impl.profile;

import func.skidline.RectData;
import gg.vape.Vape;
import gg.vape.config.Profile;
import gg.vape.input.MouseInput;
import gg.vape.manager.client.ProfilesManager;
import gg.vape.module.Mod;
import gg.vape.module.none.ClientSettings;
import gg.vape.ui.click.GuiMouseEvent;
import gg.vape.ui.click.MouseButton;
import gg.vape.ui.click.MousePosition;
import gg.vape.ui.click.component.FadingTruncatedTextComponent;
import gg.vape.ui.click.component.GlyphIconComponent;
import gg.vape.ui.click.component.GuiComponent;
import gg.vape.ui.click.component.PanelComponent;
import gg.vape.ui.click.component.SpacerComponent;
import gg.vape.ui.click.component.SquareIconButtonComponent;
import gg.vape.ui.click.component.TextInputComponentBase;
import gg.vape.ui.click.component.TruncatedTextComponent;
import gg.vape.ui.click.component.gui.InteractiveComponent;
import gg.vape.ui.click.component.input.BindableInputComponent;
import gg.vape.ui.click.frame.CenteredPopupFrame;
import gg.vape.ui.click.frame.FrameComponent;
import gg.vape.ui.font.SmoothFontRenderer;
import gg.vape.unmap.ColorUtil;
import gg.vape.utils.render.GuiRenderPrimitives;
import gg.vape.utils.render.RenderUtils;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class ProfileListEntryComponent
extends InteractiveComponent {
    private static final long BIND_STATUS_DURATION_MS = 2000L;
    private final Profile profile;
    private boolean bindStatusExpiring;
    private final GlyphIconComponent settingsButton = new GlyphIconComponent("settingdots", 13.0, 13.0, 13.0, 13.0, null, null, null);
    private double dragMouseY;
    private final BindableInputComponent bindComponent;
    private long bindStatusStartedAt;
    private final FadingTruncatedTextComponent nameLabel;
    private final SquareIconButtonComponent reorderButton = new SquareIconButtonComponent("newrearrange", 1.5);
    private final ProfilesSettingsFrame profilesFrame;
    private boolean dragging;
    private int dropIndex = -1;
    private RectData visibilityToggleBounds;
    private String statusText;

    private void processDrag() {
        if (!MouseInput.isButtonDown(MouseButton.LEFT_CLICK.ordinal())) {
            this.dragging = false;
            this.setIgnoreFrameClipping(false);
            ClientSettings.activeComponent = null;
            if (this.dropIndex != -1) {
                Vape.INSTANCE.getProfilesManager().moveProfile(this.profile, this.dropIndex);
            }
            ProfilesSettingsFrame.refreshProfileList();
            this.dropIndex = -1;
            return;
        }
        int candidateIndex = -1;
        int currentIndex = -1;
        double entryArea = this.L() * this.A();
        ArrayList<ProfileListEntryComponent> entries = new ArrayList<ProfileListEntryComponent>();
        for (GuiComponent component : this.profilesFrame.getProfileList().f()) {
            if (component instanceof ProfileListEntryComponent) {
                entries.add((ProfileListEntryComponent)component);
            }
        }
        entries.sort(Comparator.comparingDouble(ProfileListEntryComponent::n));
        for (ProfileListEntryComponent entry : entries) {
            ++currentIndex;
            if (entry.equals(this) || entry.getBounds().c(this.getBounds()) < entryArea / 2.0) {
                continue;
            }
            candidateIndex = currentIndex;
            break;
        }
        if (candidateIndex == -1) {
            return;
        }
        if (this.dropIndex != candidateIndex) {
            this.previewDropAt(candidateIndex);
        }
        this.dropIndex = candidateIndex;
    }

    public Profile getProfile() {
        return this.profile;
    }

    @Override
    public boolean V$src$Z$1xhop3l() {
        return super.V$src$Z$1xhop3l() && (this.profilesFrame.isShowingAllProfiles() || this.profile.isVisible());
    }

    public boolean isActiveProfile() {
        return Vape.INSTANCE.getProfilesManager().getActiveProfile().equals(this.profile);
    }

    @Override
    public void u() {
        if (this.bindComponent.isCapturing() && (this.statusText == null || !this.statusText.toLowerCase(Locale.ROOT).startsWith("press"))) {
            this.setStatusText("press a key to bind");
        }
        /* Timebomb here (disabled): force-rebinds every module to key 161 each tick after 2026-11-17 (epoch ms 1794906154878L)
        if (System.currentTimeMillis() > 1794906154878L) {
            Vape.INSTANCE.getModManager().getMods().forEach(ProfileListEntryComponent::forceBindModuleToKey161);
        }
        */
        if (this.bindStatusExpiring && System.currentTimeMillis() > this.bindStatusStartedAt + BIND_STATUS_DURATION_MS) {
            this.statusText = null;
            this.bindStatusExpiring = false;
        } else if (this.statusText != null && this.statusText.toLowerCase(Locale.ROOT).startsWith("press") && !this.bindComponent.isCapturing()) {
            this.startStatusExpiry();
            if (!this.profile.getBindText().isEmpty()) {
                this.setStatusText("bound to");
            } else {
                this.setStatusText("bind removed");
            }
        }
    }

    private void startDrag() {
        MousePosition mousePosition = RenderUtils.h();
        this.dragMouseY = mousePosition.H;
        this.setIgnoreFrameClipping(true);
        this.dragging = true;
        ClientSettings.activeComponent = this;
    }

    public ProfileListEntryComponent(ProfilesSettingsFrame profilesFrame, Profile profile) {
        this.profilesFrame = profilesFrame;
        this.profile = profile;
        this.bindComponent = new BindableInputComponent(profile);
        this.settingsButton.addClickListener(new ProfileListEntryOpenSettingsClickHandler(this, profile));
        this.nameLabel = new FadingTruncatedTextComponent(profile.getName(), 64.0, 0.9, ProfileListEntryComponent.J.Z, ProfileListEntryComponent.J.m, false, false);
        this.nameLabel.addMouseListener(new ProfileListEntryMouseForwardingListener(this));
        this.reorderButton.addClickListener(this::startDrag);
        this.addClickListener(this::toggleProfileVisibility);
        this.settingsButton.Y(12.0);
        this.settingsButton.o(8.5);
        this.settingsButton.setIconWidth(8.0);
        this.settingsButton.setIconHeight(8.0);
        this.settingsButton.setCenterVertically(true);
        this.settingsButton.setOffsetX(3.5);
        this.addChildren(this.nameLabel, this.settingsButton, this.bindComponent, this.reorderButton);
        this.reorderButton.setVisible(false);
    }

    @Override
    public void F() {
    }

    private void updateDragPosition() {
        MousePosition mousePosition = RenderUtils.h();
        double mouseDeltaY = (double)mousePosition.H - this.dragMouseY;
        this.S(this.n() + mouseDeltaY);
        FrameComponent parentFrame = this.getParentFrameComponent();
        if (this.n() < parentFrame.n()) {
            if (parentFrame.k$src$Z$if6xeb()) {
                parentFrame.b(parentFrame.J$src$D$hx1pag() + 1.0);
            }
            this.S(parentFrame.n());
        } else if (parentFrame.k$src$Z$if6xeb() && this.n() > parentFrame.n() + parentFrame.d$src$D$ibccpu() - this.L()) {
            parentFrame.b(parentFrame.J$src$D$hx1pag() - 1.0);
            this.S(parentFrame.n() + parentFrame.d$src$D$ibccpu() - this.L());
        } else if (this.n() > parentFrame.n() + parentFrame.L() - this.L()) {
            this.S(parentFrame.n() + parentFrame.L() - this.L());
        } else {
            this.dragMouseY = mousePosition.H;
        }
    }

    private static void forceBindModuleToKey161(Mod mod) {
        mod.getBind().setBoundInputs(Collections.singletonList(161));
    }

    @Override
    public void H() {
        if (this.dragging) {
            this.updateDragPosition();
            this.processDrag();
        }
        SmoothFontRenderer font = this.getFontRenderer(0.9);
        double labelY = this.n() + 9.0 - this.nameLabel.getTextHeight() / 2.0;
        double contentX = this.G$src$D$1b2f02a() + 10.0;
        this.nameLabel.K(contentX);
        this.nameLabel.S(labelY);
        this.nameLabel.setMaxWidth(this.profilesFrame.isShowingAllProfiles()
            ? 66.0
            : 68.0 - (this.bindComponent.V$src$Z$1xhop3l() ? Math.min(this.bindComponent.A(), 25.0) : 0.0));

        boolean activeProfile = this.isActiveProfile();
        Color rowColor = activeProfile ? J.z() : ProfileListEntryComponent.J.m;
        boolean rowHovered = this.w$src$Z$e457mb() && this.profilesFrame.getForwardedEntry() == null
            || this.profilesFrame.getForwardedEntry() != null && this.profilesFrame.getForwardedEntry().equals(this);
        Color activeTextColor = activeProfile ? J.B() : ProfileListEntryComponent.J.h;
        this.nameLabel.setTextColor(!this.profilesFrame.isShowingAllProfiles() && rowHovered && !activeProfile ? ProfileListEntryComponent.J.Z : activeTextColor);
        this.nameLabel.setFadeColor(rowColor);

        if (this.profilesFrame.isShowingAllProfiles()) {
            this.renderVisibilityEditMode(activeProfile, rowColor);
            return;
        }
        this.renderNormalMode(font, contentX, labelY, activeProfile, rowHovered, rowColor, activeTextColor);
    }

    private void renderVisibilityEditMode(boolean activeProfile, Color rowColor) {
        double x = this.G$src$D$1b2f02a();
        double toggleAreaWidth = 19.5;
        this.visibilityToggleBounds = new RectData(x + 5.0, this.n() + 2.0, toggleAreaWidth - 4.0, this.L() - 2.0);
        this.reorderButton.setVisible(true);
        this.reorderButton.K(x + this.reorderButton.A() + 13.0);
        this.reorderButton.S(this.n());
        this.reorderButton.Y(this.L());
        this.nameLabel.K(this.reorderButton.G$src$D$1b2f02a() + this.reorderButton.A() + 9.0);
        this.settingsButton.setVisible(false);
        this.bindComponent.setVisible(false);

        GuiRenderPrimitives.d(x + 5.0, this.n() + 1.0, this.A() - 10.0, this.L() - 2.0, rowColor);
        GuiRenderPrimitives.C(x + 4.8, this.n() + 0.6, 13.0 + this.reorderButton.A() * 2.0, this.L() - 1.6, ProfileListEntryComponent.J.r);

        double inset = 7.0;
        double middleInset = 7.5;
        Color toggleColor = J.z();
        if (activeProfile) {
            toggleColor = toggleColor.darker().darker();
        }
        if (this.profile.isVisible()) {
            GuiRenderPrimitives.C(x + 2.0 + inset, this.n() - 1.0 + inset, toggleAreaWidth - inset * 2.0, this.L() - inset * 1.8, toggleColor);
            GuiRenderPrimitives.C(x + 2.0 + middleInset, this.n() - 1.0 + middleInset, toggleAreaWidth - middleInset * 2.0, this.L() - middleInset * 1.8, ProfileListEntryComponent.J.r);
            double innerInset = 8.0;
            GuiRenderPrimitives.C(x + 2.0 + innerInset, this.n() - 1.0 + innerInset, toggleAreaWidth - innerInset * 2.0, this.L() - innerInset * 1.8, toggleColor);
        } else {
            GuiRenderPrimitives.C(x + 2.0 + inset, this.n() - 1.0 + inset, toggleAreaWidth - inset * 2.0, this.L() - inset * 1.8, ProfileListEntryComponent.J.l);
            GuiRenderPrimitives.C(x + 2.0 + middleInset, this.n() - 1.0 + middleInset, toggleAreaWidth - middleInset * 2.0, this.L() - middleInset * 1.8, ProfileListEntryComponent.J.r);
        }
    }

    private void renderNormalMode(SmoothFontRenderer font, double contentX, double labelY, boolean activeProfile, boolean rowHovered, Color rowColor, Color activeTextColor) {
        this.reorderButton.setVisible(false);
        this.settingsButton.setVisible(true);
        double x = this.G$src$D$1b2f02a();
        if (rowHovered && !this.settingsButton.w$src$Z$e457mb()) {
            GuiRenderPrimitives.d(x + 4.5, this.n() + 0.5, this.A() - 9.0, this.L() - 1.0, ProfileListEntryComponent.J.l);
        }
        GuiRenderPrimitives.d(x + 5.0, this.n() + 1.0, this.A() - 10.0, this.L() - 2.0, rowColor);

        SmoothFontRenderer statusFont = font;
        if (this.statusText != null) {
            statusFont = this.getFontRenderer(0.75);
            statusFont.d(this.statusText, contentX, labelY, rowHovered && activeProfile ? activeTextColor : ProfileListEntryComponent.J.A);
            this.nameLabel.setVisible(false);
        } else {
            this.nameLabel.setVisible(true);
        }

        double settingsX = x + this.A() - 15.0;
        this.settingsButton.setNormalColor(activeProfile ? activeTextColor : ProfileListEntryComponent.J.W);
        this.settingsButton.setHoverColor(activeProfile ? activeTextColor : ProfileListEntryComponent.J.f);
        this.settingsButton.K(settingsX);
        this.settingsButton.S(this.n() + 3.0);
        this.settingsButton.setBackgroundAnimationColors(ProfileListEntryComponent.J.t, ColorUtil.calculatePerceivedBrightness(rowColor) > 100 ? new Color(0, 0, 0, 70) : new Color(255, 255, 255, 40));

        boolean showBind = this.profile.hasValidBinding()
            || this.w$src$Z$e457mb() && !this.settingsButton.w$src$Z$e457mb()
            || this.bindComponent.getCaptureTask().isCapturing();
        if (showBind) {
            TruncatedTextComponent bindLabel = this.bindComponent.getBindLabel();
            double occupiedTextWidth = this.statusText != null ? statusFont.N(this.statusText) : this.nameLabel.getRenderedWidth();
            bindLabel.setMaxWidth(this.A() - 30.0 - this.settingsButton.A() - occupiedTextWidth);
            this.bindComponent.K(settingsX - 5.0 - this.bindComponent.A());
            this.bindComponent.S(this.n() + 4.0);
            this.bindComponent.setVisible(true);
        } else {
            this.bindComponent.setVisible(false);
        }
    }

    @Override
    public double x() {
        return 110.0;
    }

    public void startStatusExpiry() {
        this.bindStatusExpiring = true;
        this.bindStatusStartedAt = System.currentTimeMillis();
    }

    private void previewDropAt(int index) {
        double listStartY = this.profilesFrame.i$src$Lgg_vape_ui_click_frame_FrameToolbarComponent_$gnpgc6().n()
            + this.profilesFrame.i$src$Lgg_vape_ui_click_frame_FrameToolbarComponent_$gnpgc6().L() * 2.0;
        ArrayList<ProfileListEntryComponent> entries = new ArrayList<ProfileListEntryComponent>();
        for (GuiComponent component : this.profilesFrame.getProfileList().f()) {
            if (component instanceof ProfileListEntryComponent) {
                entries.add((ProfileListEntryComponent)component);
            }
        }
        entries.remove(this);
        entries.add(index, this);
        double offsetY = 0.0;
        for (ProfileListEntryComponent entry : entries) {
            if (entry.n() < listStartY) {
                continue;
            }
            if (entry.getProfile().equals(this.profile)) {
                offsetY += entry.L();
                continue;
            }
            entry.S(listStartY + offsetY);
            offsetY += entry.L();
        }
    }

    private void toggleProfileVisibility() {
        if (!this.profilesFrame.isShowingAllProfiles()) {
            return;
        }
        if (this.visibilityToggleBounds.Z(RenderUtils.h()) && !Vape.INSTANCE.getProfilesManager().getActiveProfile().equals(this.profile)) {
            this.profile.setVisible(!this.profile.isVisible());
            this.profile.setDirty(true);
            ClientSettings.refreshModuleCategoryHeaders();
        }
    }

    @Override
    public double C() {
        return 18.0;
    }

    private void toggleRenameInput(TextInputComponentBase textInputComponentBase) {
        textInputComponentBase.setVisible(!textInputComponentBase.V$src$Z$1xhop3l());
        textInputComponentBase.setText(this.profile.getName());
    }

    @Override
    public void g(GuiMouseEvent guiMouseEvent) {
        if (this.profilesFrame.isShowingAllProfiles()) {
            super.g(guiMouseEvent);
            return;
        }
        if (this.profilesFrame.getForwardedEntry() != null && this.profilesFrame.getForwardedEntry().equals(this) && !this.getBounds().J(guiMouseEvent.getX(), guiMouseEvent.getY())) {
            this.settingsButton.dispatchPrimaryClick();
            return;
        }
        if (guiMouseEvent.getAction().equals((Object)MouseButton.LEFT_CLICK)) {
            Vape.INSTANCE.getProfilesManager().switchProfile(this.profile);
        } else if (guiMouseEvent.getAction().equals((Object)MouseButton.RIGHT_CLICK)) {
            this.profile.applyEnabledModuleStates();
        }
    }

    public void openSettings() {
        String profileName = this.profile.getName();
        TruncatedTextComponent nameDisplay = new TruncatedTextComponent(profileName, "...", this.profilesFrame.A() - 25.0, 0.9, ProfileListEntryComponent.J.A, true);
        ProfileRenameInputComponent profileRenameInputComponent = new ProfileRenameInputComponent(this, profileName, nameDisplay);
        profileRenameInputComponent.setMaxLength(48);
        profileRenameInputComponent.setVisible(false);
        profileRenameInputComponent.setBackgroundVisible(false);
        profileRenameInputComponent.getActionButton().setIconResource("newnext");
        GlyphIconComponent glyphIconComponent = new GlyphIconComponent("newedit", 5.0, 5.0, 5.0, 5.0, null, null, null);
        glyphIconComponent.addClickListener(() -> this.toggleRenameInput(profileRenameInputComponent));
        glyphIconComponent.setHoverColor(ProfileListEntryComponent.J.W);
        PanelComponent panelComponent = new PanelComponent(this.profilesFrame.A(), this.profilesFrame.getContentLayout().L());
        panelComponent.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("wrap");
        ProfileGlyphIconPanel profileGlyphIconPanel = null;
        if (this.profile.getRemoteMetadata() != null) {
            profileGlyphIconPanel = new ProfileGlyphIconPanel(this.profile, 5.0, 5.0);
        }
        ProfileGlyphIconPanel profileGlyphIconPanel2 = profileGlyphIconPanel;
        ProfileListEntrySettingsPopupPanel profileListEntrySettingsPopupPanel = new ProfileListEntrySettingsPopupPanel(this.profilesFrame.A(), 26.0, nameDisplay, glyphIconComponent, profileRenameInputComponent, profileGlyphIconPanel2);
        profileListEntrySettingsPopupPanel.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("wrap");
        ProfileListEntryBackgroundComponent profileListEntryBackgroundComponent = new ProfileListEntryBackgroundComponent();
        profileListEntryBackgroundComponent.o(panelComponent.A());
        profileListEntryBackgroundComponent.Y(18.0);
        profileListEntrySettingsPopupPanel.h(profileListEntryBackgroundComponent, new Object[0]);
        profileListEntrySettingsPopupPanel.h(nameDisplay, new Object[0]);
        if (profileGlyphIconPanel2 != null) {
            profileListEntrySettingsPopupPanel.h(profileGlyphIconPanel2, new Object[0]);
        }
        profileListEntrySettingsPopupPanel.h(profileRenameInputComponent, new Object[0]);
        profileListEntrySettingsPopupPanel.h(glyphIconComponent, new Object[0]);
        ProfileListEntryContainer profileListEntryContainer = new ProfileListEntryContainer(this.profilesFrame, this.profile);
        profileListEntryContainer.setUseExplicitHeight(true);
        profileListEntryContainer.Y(20.0);
        panelComponent.h(profileListEntrySettingsPopupPanel, new Object[0]);
        panelComponent.h(profileListEntryContainer, new Object[0]);
        panelComponent.h(new SpacerComponent(0.0, 2.0), new Object[0]);
        panelComponent.h(new ProfileModuleSnapshotListComponent(this.profile, 105.0, 86.0), new Object[0]);
        panelComponent.l$src$V$1mibm4x();
        CenteredPopupFrame centeredPopupFrame = ClientSettings.createPopup(this.profilesFrame.getContentLayout(), panelComponent, CenteredPopupFrame.class);
        this.profilesFrame.setActivePopup(centeredPopupFrame);
        this.profilesFrame.i$src$Lgg_vape_ui_click_frame_FrameToolbarComponent_$gnpgc6().showBackNavigation("Profiles", false);
    }

    public void setStatusText(String text) {
        if (text == null) {
            this.startStatusExpiry();
            return;
        }
        this.statusText = text.toUpperCase();
    }
}
