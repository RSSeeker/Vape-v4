package gg.vape.ui.click.frame.impl.inevitable;

import com.google.gson.JsonObject;
import gg.vape.Vape;
import gg.vape.module.Category;
import gg.vape.module.Mod;
import gg.vape.module.none.ClientSettings;
import gg.vape.ui.click.component.GuiComponent;
import gg.vape.ui.click.component.module.ModuleComponent;
import gg.vape.ui.click.frame.FrameHeaderComponent;
import gg.vape.ui.click.frame.impl.ModuleCategoryFrame;
import gg.vape.utils.NameComparator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

/** Presentation-only grouping of the real module objects and their editors. */
public final class InevitableModuleFrame extends ModuleCategoryFrame {
    private boolean layoutAssigned;
    public InevitableModuleFrame(Category representative) {
        super(representative);
        this.setDisabledOverlayColor(InevitablePanelStyle.BLACK);
        this.L(false, true);
        this.t(true, false);
    }
    public InevitableCategory group() {
        Category category = this.G$src$Lgg_vape_module_Category_$qyt4o7();
        if (category == Category.COMBAT) return InevitableCategory.COMBAT;
        if (category == Category.WORLD) return InevitableCategory.MOVEMENT;
        if (category == Category.RENDER) return InevitableCategory.VISUAL;
        return InevitableCategory.UTILITY;
    }
    @Override public String getName() { return "Inevitable." + this.group().name(); }

    // Called by the superclass constructor after it has assigned the category.
    @Override public void s$src$V$1a2f6mi() {
        this.HF = new InevitableModuleHeader(this);
        this.Y(this.HF);
        this.syncModules();
    }

    public void syncModules() {
        ArrayList<ModuleComponent> existing = new ArrayList<ModuleComponent>();
        Map<Mod, Boolean> expanded = new IdentityHashMap<Mod, Boolean>();
        for (GuiComponent child : this.f()) if (child instanceof ModuleComponent) {
            ModuleComponent row = (ModuleComponent)child;
            existing.add(row);
            if (row.isExpanded()) expanded.put(row.getModule(), Boolean.TRUE);
        }
        ArrayList<Mod> all = new ArrayList<Mod>(Vape.INSTANCE.getModManager().collectMods());
        Collections.sort(all, new NameComparator());
        ArrayList<Mod> wanted = new ArrayList<Mod>();
        for (Mod mod : all) {
            if (mod instanceof gg.vape.module.extension.ScriptModule
                    && !gg.vape.manager.ExtensionManager.isScriptPresent((gg.vape.module.extension.ScriptModule)mod)) continue;
            if (InevitableCategory.forModule(mod.getCategory().getName(), mod.getName()) == this.group()) wanted.add(mod);
        }
        boolean same = existing.size() == wanted.size();
        for (int i = 0; same && i < wanted.size(); i++) same = existing.get(i).getModule() == wanted.get(i);
        if (same) {
            // Recompiled scripts can add or remove settings while open.
            for (ModuleComponent row : existing) if (row.isExpanded()) row.buildValueComponents();
            return;
        }
        ClientSettings.inevitableStack.closePanelPopups(this);
        if (ClientSettings.activeComponent != null
                && ClientSettings.activeComponent.L$src$Lgg_vape_ui_click_frame_Frame_$1djx6sa() == this) {
            ClientSettings.activeComponent = null;
        }
        this.removeMarkedChildren();
        for (Mod mod : wanted) {
            ModuleComponent row = new ModuleComponent(this, mod);
            this.h(row, new Object[0]);
            row.setVisible(!this.q() && (ClientSettings.moduleSearchActive || mod.isVisible()));
            if (!this.q() && expanded.containsKey(mod) && !ClientSettings.moduleSearchActive) row.expandValueComponents();
        }
        this.l$src$V$1mibm4x();
    }
    @Override public int A$src$I$wwnvku() {
        int hiddenCount = super.A$src$I$wwnvku();
        if (this.q()) for (GuiComponent child : this.f()) {
            if (!(child instanceof FrameHeaderComponent)) child.setVisible(false);
        }
        return hiddenCount;
    }
    @Override public void z(boolean scrolling) {
        InevitablePanelStyle.panel(this.G$src$D$1b2f02a(), this.n(), this.A(), scrolling ? this.K : this.getComponentHeight());
    }
    @Override public void t(JsonObject state) {
        super.t(state);
        this.layoutAssigned = state.has("x") && !state.get("x").isJsonNull()
                && state.has("y") && !state.get("y").isJsonNull();
    }
    public boolean hasLayout() { return this.layoutAssigned; }
    public void markLayoutAssigned() { this.layoutAssigned = true; }
    public void clearLayout() { this.layoutAssigned = false; }
}
