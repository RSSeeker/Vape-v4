package gg.vape.ui.click.frame.impl;

import gg.vape.module.none.ClientSettings;
import gg.vape.ui.click.GuiMouseEvent;
import gg.vape.ui.click.component.GuiComponent;
import gg.vape.ui.click.component.HalfScaleIconButtonComponent;
import gg.vape.ui.click.component.IconButtonComponent;
import gg.vape.ui.click.component.SquareIconButtonComponent;
import gg.vape.ui.click.component.input.ModuleSearchInputComponent;
import gg.vape.ui.click.component.module.ModuleComponent;
import gg.vape.ui.click.frame.FrameHeaderComponent;
import gg.vape.ui.click.frame.impl.ModuleSearchFrame;
import gg.vape.ui.click.frame.impl.ModuleSearchFrameHeaderRefreshListener;
import gg.vape.ui.click.frame.impl.hud.HudModuleListPanel;
import gg.vape.ui.click.frame.impl.hud.HudModuleOverviewListFrame;
import gg.vape.ui.click.frame.impl.hud.HudModuleSelectorFrame;
import gg.vape.unmap.ModeSelection;
import gg.vape.utils.render.GuiRenderPrimitives;
import gg.vape.value.ModuleNameSuggestionProvider;
import java.awt.Color;
import java.util.List;

public class ModuleSearchFrameHeader
extends FrameHeaderComponent {
    private final ModuleSearchFrame I;
    private final IconButtonComponent v;
    private final SquareIconButtonComponent R;
    private final IconButtonComponent G = new IconButtonComponent("newsearch", 0.9);
    private final ModuleSearchInputComponent K;

    public void K$src$V$10w6uwu() {
        this.I.G(null);
        ClientSettings.getFrame(ModuleSearchFrame.class).D(this.K.getText());
    }

    @Override
    public double C() {
        return 16.0;
    }

    public void l$src$V$11ec2hr() {
        List<GuiComponent> list = ClientSettings.getFrame(ModuleSearchFrame.class).f();
        for (GuiComponent guiComponent : list) {
            if (!(guiComponent instanceof ModuleComponent)) continue;
            ModuleComponent moduleComponent = (ModuleComponent)guiComponent;
            moduleComponent.collapseValueComponents();
        }
    }

    @Override
    public void I() {
    }

    private void lambda$new$1() {
        this.s();
    }

    @Override
    public void Y(double d) {
        super.Y(d);
    }

    private void lambda$new$0() {
        ClientSettings.activeComponent = this.K;
    }

    @Override
    public void u() {
        this.w$src$Lgg_vape_ui_click_frame_Frame_$y4htd0().l$src$V$1mibm4x();
    }


    public IconButtonComponent G$src$Lgg_vape_ui_click_component_IconButtonComponent_$1pnwa51() {
        return this.v;
    }

    public ModuleSearchFrameHeader(ModuleSearchFrame moduleSearchFrame) {
        super(moduleSearchFrame);
        this.v = new HalfScaleIconButtonComponent("legit_switch");
        this.R = new SquareIconButtonComponent("newclose");
        this.K = new ModuleSearchInputComponent(this);
        this.I = moduleSearchFrame;
        this.setPropagateMouseEvents(true);
        this.G.addClickListener(this::lambda$new$0);
        this.R.addClickListener(this::lambda$new$1);
        this.K.addRefreshListener(new ModuleSearchFrameHeaderRefreshListener(this));
        this.v.addClickListener(this::lambda$new$2);
        ModuleNameSuggestionProvider moduleNameSuggestionProvider = new ModuleNameSuggestionProvider(true);
        moduleNameSuggestionProvider.setComparator(null);
        this.K.setSuggestionProvider(moduleNameSuggestionProvider);
        this.addChildren(this.G, this.R, this.K, this.v);
    }

    public void s() {
        this.l$src$V$11ec2hr();
        this.K.setText("");
        this.K$src$V$10w6uwu();
    }

    @Override
    public void F() {
    }

    @Override
    public void H() {
        this.I.p();
        boolean bl = ClientSettings.INSTANCE.isFloatingSearchActive();
        if (bl) {
            if (ClientSettings.INSTANCE.showLegitMode.getEffectiveValue().booleanValue()) {
                this.v.setVisible(true);
                this.v.K(this.G$src$D$1b2f02a() + 3.0);
                this.v.S(this.n() + 1.0);
                this.v.o(18.0);
                this.v.Y(this.L());
                this.v.setUseExplicitHeight(true);
                this.v.setShowDisabledOverlay(true);
                this.v.setDisabledOverlayColor(Color.RED);
                if (this.v.w$src$Z$e457mb()) {
                    this.v.setOverrideColor(J.z().brighter());
                } else {
                    this.v.setOverrideColor(J.z());
                }
            } else {
                this.v.setVisible(false);
            }
            this.K.setUseExplicitHeight(true);
            double d = this.G$src$D$1b2f02a() + 6.0;
            if (this.v.V$src$Z$1xhop3l()) {
                d = this.v.G$src$D$1b2f02a() + this.v.A() + 4.0;
                GuiRenderPrimitives.d(this.v.G$src$D$1b2f02a() + this.v.A() + 1.0, this.n() + 6.0, 6.0, 0.75f, new Color(100, 100, 100, 255));
            }
            this.K.K(d);
            this.K.S(this.n());
            this.K.o(this.v.V$src$Z$1xhop3l() ? this.A() - this.v.A() - this.R.A() - 4.0 : this.A() - this.R.A() - 3.0);
            this.K.Y(this.L());
            if (!this.K.getText().isEmpty()) {
                this.G.setVisible(false);
                this.R.setVisible(true);
                this.R.K(this.G$src$D$1b2f02a() + this.A() - 13.0);
                this.R.S(this.n() + 1.0);
                this.R.o(10.0);
                this.R.Y(this.L());
            } else {
                this.R.setVisible(false);
                this.G.setVisible(true);
                this.G.K(this.G$src$D$1b2f02a() + this.A() - 14.0);
                this.G.S(this.n() + 0.5);
                this.G.o(10.0);
                this.G.Y(this.L());
            }
            this.K.setVisible(true);
            this.o(110.0);
            this.setExplicitWidth(110.0);
            this.setUseExplicitWidth(true);
            return;
        }
        if (ClientSettings.INSTANCE.showLegitMode.getEffectiveValue().booleanValue()) {
            this.v.setVisible(true);
            this.v.K(this.G$src$D$1b2f02a() + 3.0);
            this.v.S(this.n() + 1.0);
            this.v.o(18.0);
            this.v.Y(this.L());
            this.v.setUseExplicitHeight(true);
            this.v.setShowDisabledOverlay(true);
            this.v.setDisabledOverlayColor(Color.RED);
            if (this.v.w$src$Z$e457mb()) {
                this.v.setOverrideColor(J.z().brighter());
            } else {
                this.v.setOverrideColor(J.z());
            }
        } else {
            this.v.setVisible(false);
        }
        this.K.setUseExplicitHeight(true);
        double d = this.G$src$D$1b2f02a() + 6.0;
        if (this.v.V$src$Z$1xhop3l()) {
            // empty if block
        }
        this.K.K(d);
        this.K.S(this.n());
        this.K.o(this.v.V$src$Z$1xhop3l() ? this.A() - this.v.A() - this.R.A() - 4.0 : this.A() - this.R.A() - 3.0);
        this.K.Y(this.L());
        if (!this.K.getText().isEmpty()) {
            this.G.setVisible(false);
            this.R.setVisible(true);
            this.R.K(this.G$src$D$1b2f02a() + this.A() - 13.0);
            this.R.S(this.n() + 1.0);
            this.R.o(10.0);
            this.R.Y(this.L());
        } else {
            this.R.setVisible(false);
            this.G.setVisible(true);
            this.G.K(this.G$src$D$1b2f02a() + this.A() - 14.0);
            this.G.S(this.n() + 0.5);
            this.G.o(10.0);
            this.G.Y(this.L());
        }
        boolean bl2 = this.K.V$src$Z$1xhop3l();
        this.K.setVisible(false);
        this.G.setVisible(false);
        if (bl2) {
            this.s();
        }
        this.o(22.0);
        this.setExplicitWidth(22.0);
        this.setUseExplicitWidth(true);
    }

    public ModuleSearchInputComponent A$src$Lgg_vape_ui_click_component_input_ModuleSearchIn$1efzz7n() {
        return this.K;
    }

    @Override
    public void g(GuiMouseEvent guiMouseEvent) {
    }

    private void lambda$new$2() {
        this.K.setText("");
        this.K$src$V$10w6uwu();
        if (HudModuleSelectorFrame.overviewVisible) {
            ClientSettings.getFrame(HudModuleOverviewListFrame.class).refreshFavorites();
        } else {
            ClientSettings.getFrame(HudModuleListPanel.class).refreshModules();
        }
        ClientSettings.INSTANCE.switchFrameStack(ClientSettings.hudEditorStack);
    }
}
