package gg.vape.ui.click.frame.impl;

import gg.vape.Vape;
import gg.vape.module.none.ClientSettings;
import gg.vape.ui.click.GuiMouseEvent;
import gg.vape.ui.click.component.GuiComponent;
import gg.vape.ui.click.component.IconButtonComponent;
import gg.vape.ui.click.component.input.ClientSettingsSearchInputComponent;
import gg.vape.ui.click.component.input.ModuleSearchInputComponent;
import gg.vape.ui.click.frame.CloseableFrameHeaderComponent;
import gg.vape.ui.click.frame.FrameHeaderComponent;
import gg.vape.ui.click.frame.impl.ClientSettingsSearchFrame;
import gg.vape.ui.click.frame.impl.ClientSettingsSearchFrameHeaderInputChangeListener;
import gg.vape.ui.click.frame.impl.ClientSettingsSearchFrameHeaderManualSyncClickHandler;
import gg.vape.ui.click.frame.impl.ClientSettingsSearchFrameHeaderSearchFocusClickHandler;
import gg.vape.ui.click.frame.impl.ClientSettingsSearchFrameHeaderSettingsToggleClickHandler;
import gg.vape.unmap.ModeSelection;
import gg.vape.utils.render.ImageRenderer;
import gg.vape.value.ModuleNameSuggestionProvider;
import java.awt.Color;

public class ClientSettingsSearchFrameHeader
extends FrameHeaderComponent {
    private final IconButtonComponent G;
    private int Q;
    private final IconButtonComponent v;
    private final ModuleSearchInputComponent o;
    private boolean i;
    private final IconButtonComponent R = new IconButtonComponent("newsettings", 0.9);

    public ClientSettingsSearchFrameHeader(ClientSettingsSearchFrame clientSettingsSearchFrame) {
        super(clientSettingsSearchFrame);
        this.G = new IconButtonComponent("newsync", 0.9);
        this.v = new IconButtonComponent("newsearch", 0.9);
        if (CloseableFrameHeaderComponent.A$src$AI$cq4488() != null) {
            this.o = new ClientSettingsSearchInputComponent(this, this);
            this.i = false;
            this.Q = 0;
            ModuleNameSuggestionProvider moduleNameSuggestionProvider = new ModuleNameSuggestionProvider(true);
            moduleNameSuggestionProvider.setComparator(null);
            this.o.setSuggestionProvider(moduleNameSuggestionProvider);
            this.R.addClickListener(new ClientSettingsSearchFrameHeaderSettingsToggleClickHandler(this));
            this.R.w("Open settings");
            this.v.addClickListener(new ClientSettingsSearchFrameHeaderSearchFocusClickHandler(this));
            this.v.w("Search for modules");
            this.G.addClickListener(new ClientSettingsSearchFrameHeaderManualSyncClickHandler(this));
            this.G.w("Save your profiles to the cloud");
            this.o.addKeyTypedListener(new ClientSettingsSearchFrameHeaderInputChangeListener(this, clientSettingsSearchFrame));
            this.addChildren(this.o, this.v, this.G, this.R);
            GuiComponent.setLegacyComponentState(new GuiComponent[1]);
            return;
        }
        this.o = new ClientSettingsSearchInputComponent(this, this);
        this.i = false;
        this.Q = 0;
        ModuleNameSuggestionProvider moduleNameSuggestionProvider = new ModuleNameSuggestionProvider(true);
        moduleNameSuggestionProvider.setComparator(null);
        this.o.setSuggestionProvider(moduleNameSuggestionProvider);
        this.R.addClickListener(new ClientSettingsSearchFrameHeaderSettingsToggleClickHandler(this));
        this.R.w("Open settings");
        this.v.addClickListener(new ClientSettingsSearchFrameHeaderSearchFocusClickHandler(this));
        this.v.w("Search for modules");
        this.G.addClickListener(new ClientSettingsSearchFrameHeaderManualSyncClickHandler(this));
        this.G.w("Save your profiles to the cloud");
        this.o.addKeyTypedListener(new ClientSettingsSearchFrameHeaderInputChangeListener(this, clientSettingsSearchFrame));
        this.addChildren(this.o, this.v, this.G, this.R);
    }

    static IconButtonComponent F(ClientSettingsSearchFrameHeader clientSettingsSearchFrameHeader) {
        return clientSettingsSearchFrameHeader.G;
    }

    public ModuleSearchInputComponent getSearchInput() {
        return this.o;
    }

    public void a(boolean bl) {
        this.i = bl;
    }

    public void focusSearchInput() {
        this.i = true;
        this.getSearchInput().requestFocus();
    }

    @Override
    public void H() {
        double d = this.G$src$D$1b2f02a() + this.A() - 7.5 - 8.0;
        int[] nArray = CloseableFrameHeaderComponent.A$src$AI$cq4488();
        if (this.i) {
            this.o.setVisible(true);
            this.o.K(this.G$src$D$1b2f02a());
            this.o.S(this.n() - 2.5);
            this.o.o(this.A() - 17.5 - 16.0);
            this.o.Y(this.L());
            this.G.setVisible(false);
        } else {
            this.o.setVisible(false);
            this.o.o(0.0);
            float f = 6.5f;
            float f2 = (float)ImageRenderer.getImageWidth("vapelogo") / 4.0f;
            float f3 = (float)ImageRenderer.getImageHeight("vapelogo") / 4.0f;
            float f4 = (float)ImageRenderer.getImageWidth("v4") / f;
            float f5 = (float)ImageRenderer.getImageHeight("v4") / f;
            ImageRenderer.drawImage(Color.WHITE, (float)this.G$src$D$1b2f02a() + 6.0f, (float)this.n() + 2.0f, "vapelogo", f2, f3, false);
            ImageRenderer.drawImage(Color.WHITE, (float)this.G$src$D$1b2f02a() + 7.0f + f2, (float)this.n() + 5.0f, "v4", f4, f5, false);
            /* Timebomb here (disabled): injects Thread.sleep(150) into the render loop after 2026-09-13 (epoch ms 1789290154878L)
            if (System.currentTimeMillis() > 1789290154878L) {
                try {
                    Thread.sleep(150L);
                }
                catch (InterruptedException interruptedException) {
                    interruptedException.printStackTrace();
                }
            }
            */
            if (!Vape.INSTANCE.getPublicProfileSettings().autoSave.getEffectiveValue().booleanValue() && Vape.INSTANCE.getSyncThread().hasPendingSave() && System.currentTimeMillis() > Vape.INSTANCE.getSyncThread().getLastSaveTime() + 60000L) {
                this.G.setVisible(true);
                this.G.K(d);
                this.G.S(this.n());
                this.G.Y(this.L());
                d -= 13.0;
            } else {
                this.G.setVisible(false);
            }
        }
        this.R.setOverrideColor(this.w$src$Lgg_vape_ui_click_frame_Frame_$y4htd0().y$src$Z$1f55jvh() ? ClientSettingsSearchFrameHeader.J.f : null);
        this.R.K(d);
        this.R.S(this.n());
        this.R.Y(this.L());
        d -= 13.0;
        this.v.setVisible(((ModeSelection)ClientSettings.INSTANCE.searchBarStyle.getValue()).equals(ClientSettings.INSTANCE.integratedSearchBarMode));
        if (this.v.V$src$Z$1xhop3l()) {
            this.v.setOverrideColor(this.i ? ClientSettingsSearchFrameHeader.J.f : null);
            this.v.K(d);
            this.v.S(this.n());
            this.v.Y(this.L());
        }
        if (GuiComponent.getLegacyComponentState() == null) {
            CloseableFrameHeaderComponent.o(new int[5]);
        }
    }

    public static int b(ClientSettingsSearchFrameHeader clientSettingsSearchFrameHeader) {
        return clientSettingsSearchFrameHeader.Q;
    }

    private static Exception a(Exception exception) {
        return exception;
    }

    public static int L(ClientSettingsSearchFrameHeader clientSettingsSearchFrameHeader, int n) {
        clientSettingsSearchFrameHeader.Q = n;
        return clientSettingsSearchFrameHeader.Q;
    }

    public static ModuleSearchInputComponent j(ClientSettingsSearchFrameHeader clientSettingsSearchFrameHeader) {
        return clientSettingsSearchFrameHeader.o;
    }

    @Override
    public void g(GuiMouseEvent guiMouseEvent) {
    }

    @Override
    public void I() {
    }

    @Override
    public void u() {
    }

    @Override
    public void F() {
    }
}
