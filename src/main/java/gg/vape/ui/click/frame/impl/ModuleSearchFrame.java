package gg.vape.ui.click.frame.impl;

import gg.vape.Vape;
import gg.vape.module.Category;
import gg.vape.module.Mod;
import gg.vape.module.none.ClientSettings;
import gg.vape.ui.click.component.module.ModuleComponent;
import gg.vape.ui.click.frame.impl.ModuleCategoryFrame;
import gg.vape.ui.click.frame.impl.ModuleSearchFrameHeader;
import gg.vape.unmap.ModeSelection;
import gg.vape.utils.StringUtils;
import gg.vape.wrapper.impl.Minecraft;

public class ModuleSearchFrame
extends ModuleCategoryFrame {
    private ModuleSearchFrameHeader Rt;

    public void D(String string) {
        this.removeMarkedChildren();
        ClientSettings.activeTooltips = null;
        if (string == null || string.length() < 1) {
            return;
        }
        for (Mod mod : Vape.INSTANCE.getModManager().collectMods()) {
            if (mod.getCategory().equals(Category.NONE)) continue;
            String string2 = StringUtils.y(mod.getName());
            String string3 = StringUtils.y(string);
            String string4 = StringUtils.y(Vape.INSTANCE.getFontSelector().W().s(mod.getName()));
            if (mod.getCategory().equals(Category.OTHER) ? (!string2.equals(string3) && !string4.equals(string3)) : (!string2.contains(string3) && !string4.contains(string3))) continue;
            ModuleComponent moduleComponent = new ModuleComponent(this, mod);
            this.h(moduleComponent, new Object[0]);
            moduleComponent.buildValueComponents();
        }
    }

    @Override
    public boolean V$src$Z$1xhop3l() {
        return ClientSettings.INSTANCE.isFloatingSearchActive() || ClientSettings.INSTANCE.showLegitMode.getEffectiveValue() != false;
    }

    @Override
    public void w() {
    }

    @Override
    public String getName() {
        return "ModuleSearch";
    }

    public ModuleSearchFrame() {
        super(Category.NONE);
        this.setDisabledOverlayColor(ModuleSearchFrame.J.r);
        this.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M(false);
        this.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("wrap");
        this.I2 = false;
        this.Rt = new ModuleSearchFrameHeader(this);
        this.Y(this.Rt);
    }

    public ModuleSearchFrameHeader n$src$Lgg_vape_ui_click_frame_impl_ModuleSearchFrameHe$xia8v2() {
        return this.Rt;
    }

    public void p() {
        this.M((double)Minecraft.J() / (4.0 * Vape.INSTANCE.getClientSettings().getGuiScaleFactor()) - this.A() / 2.0, 7.0);
    }


    @Override
    public void v() {
    }

    @Override
    public void Y() {
        if (this.n() != 7.0) {
            this.p();
        }
    }

    @Override
    public void V() {
    }
}

