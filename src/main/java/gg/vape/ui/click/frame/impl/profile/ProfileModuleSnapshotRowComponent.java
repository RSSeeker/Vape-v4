package gg.vape.ui.click.frame.impl.profile;

import gg.vape.Vape;
import gg.vape.config.ProfileModuleSnapshot;
import gg.vape.config.ProfileSnapshot;
import gg.vape.module.none.ClientSettings;
import gg.vape.ui.click.GuiMouseEvent;
import gg.vape.ui.click.component.GuiComponent;
import gg.vape.ui.click.component.TruncatedTextComponent;
import gg.vape.ui.click.frame.impl.main.ClickGuiFrameManager;
import gg.vape.ui.click.frame.impl.profile.ProfileSnapshotFrame;
import gg.vape.ui.font.SmoothFontRenderer;
import gg.vape.unmap.ColorUtil;
import gg.vape.utils.render.GuiRenderPrimitives;
import java.awt.Color;
import java.util.function.Consumer;

public class ProfileModuleSnapshotRowComponent
extends GuiComponent {
    private final ProfileModuleSnapshot moduleSnapshot;
    private Color badgeTextColor;
    private Color textColor;
    private final ProfileSnapshot snapshot;
    private final Consumer<ProfileSnapshot> applyCallback;
    private Color enabledBadgeColor;
    private Color enabledTextColor;
    private Color hoverBorderColor;
    private Color backgroundColor;

    @Override
    public void g(GuiMouseEvent guiMouseEvent) {
        ProfileSnapshot editable = this.snapshot;
        ProfileModuleSnapshot selected = this.moduleSnapshot;
        if (Vape.INSTANCE.getProfilesManager().captureIfActive(editable.getProfile())) {
            ProfileSnapshot refreshed = editable.getProfile().createSnapshot(false);
            if (refreshed != null) {
                for (ProfileModuleSnapshot candidate : refreshed.getAllModules()) {
                    if (candidate.getModule() == selected.getModule()) {
                        editable = refreshed;
                        selected = candidate;
                        break;
                    }
                }
            }
        }
        ProfileSnapshotFrame profileSnapshotFrame = ClientSettings.getFrame(ProfileSnapshotFrame.class);
        profileSnapshotFrame.setSnapshot(editable, this.applyCallback);
        profileSnapshotFrame.selectModule(selected);
        if (ClientSettings.INSTANCE.getActiveStack() instanceof ClickGuiFrameManager) {
            ClickGuiFrameManager clickGuiFrameManager = (ClickGuiFrameManager)ClientSettings.INSTANCE.getActiveStack();
            profileSnapshotFrame.setReturnStack(clickGuiFrameManager);
            clickGuiFrameManager.setSidecarFrame(profileSnapshotFrame);
        } else {
            profileSnapshotFrame.setReturnStack(ClientSettings.INSTANCE.getActiveStack());
            ClientSettings.INSTANCE.switchFrameStack(ClientSettings.profileSnapshotStack);
        }
    }

    @Override
    public double x() {
        return 110.0;
    }

    public void setColors(Color backgroundColor, Color hoverBorderColor, Color badgeTextColor, Color textColor) {
        this.backgroundColor = backgroundColor;
        this.hoverBorderColor = hoverBorderColor;
        this.badgeTextColor = badgeTextColor;
        this.textColor = textColor;
    }

    @Override
    public double C() {
        return 20.0;
    }

    @Override
    public void H() {
        double d;
        String string;
        SmoothFontRenderer smoothFontRenderer = this.getFontRenderer(0.9);
        double d2 = this.G$src$D$1b2f02a();
        this.getClass();
        GuiRenderPrimitives.d(d2 + 5.0, this.n() + 1.0, this.A() - 11.0, this.L() - 2.0, this.backgroundColor);
        if (this.w$src$Z$e457mb()) {
            double d3 = this.G$src$D$1b2f02a();
            this.getClass();
            GuiRenderPrimitives.P(d3 + 5.0, this.n() + 1.0, this.A() - 11.0, this.L() - 2.0, this.hoverBorderColor, 2.0f, 0.8f, 1.0f);
        }
        double d4 = -1.0;
        double d5 = (float)(this.G$src$D$1b2f02a() + this.A()) - 14.0f;
        SmoothFontRenderer smoothFontRenderer2 = this.getAlternateFontRenderer(0.633);
        if (this.moduleSnapshot.hasBind()) {
            string = this.moduleSnapshot.getBindDisplayText();
            d = smoothFontRenderer2.N(string);
            double d6 = Math.max(8.0, d) + 6.0;
            double d7 = d6 - d;
            d4 = this.G$src$D$1b2f02a() + this.A() - 24.0 - d / 2.0;
            GuiRenderPrimitives.B(d4, this.n() + 6.0, d6, 8.0, this.hoverBorderColor, 2.0f);
            smoothFontRenderer2.d(string, d4 + d7 / 2.0, this.n() + 8.0, this.badgeTextColor);
        }
        if (this.moduleSnapshot.isEnabled()) {
            this.enabledBadgeColor = J.z();
            this.enabledTextColor = ColorUtil.getAccentTextColor();
            string = "ON";
            d = 14.0;
            d4 = d4 == -1.0 ? d5 - 11.0 : (d4 -= d + 2.0);
            GuiRenderPrimitives.B(d4, this.n() + 6.0, d, 8.0, this.enabledBadgeColor, 2.0f);
            smoothFontRenderer2.d(string, d4 + 3.0, this.n() + 8.0, this.enabledTextColor);
        }
        double d8 = this.G$src$D$1b2f02a() + 10.0;
        double d9 = this.A() - 8.0;
        if (d4 != -1.0) {
            d9 = d4 - d8 - 4.0;
        }
        TruncatedTextComponent truncatedTextComponent = new TruncatedTextComponent(this.moduleSnapshot.getName(), "...", d9, 0.9, this.textColor, false);
        truncatedTextComponent.renderAt(d8, this.n() + 10.0 - smoothFontRenderer.d(this.moduleSnapshot.getModule().getName()) / 2.0);
    }

    public ProfileModuleSnapshotRowComponent(double width, ProfileSnapshot snapshot, ProfileModuleSnapshot moduleSnapshot) {
        this(width, snapshot, moduleSnapshot, null);
    }

    public ProfileModuleSnapshotRowComponent(double width, ProfileSnapshot snapshot, ProfileModuleSnapshot moduleSnapshot,
                                             Consumer<ProfileSnapshot> applyCallback) {
        this.applyCallback = applyCallback;
        this.backgroundColor = ProfileModuleSnapshotRowComponent.J.m;
        this.hoverBorderColor = ProfileModuleSnapshotRowComponent.J.l;
        this.badgeTextColor = ProfileModuleSnapshotRowComponent.J.Z;
        this.enabledBadgeColor = J.z();
        this.enabledTextColor = ColorUtil.getAccentTextColor();
        this.textColor = ProfileModuleSnapshotRowComponent.J.A;
        this.moduleSnapshot = moduleSnapshot;
        this.snapshot = snapshot;
        this.o(width);
    }
}

