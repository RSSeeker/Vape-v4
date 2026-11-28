package gg.vape.ui.click.frame.impl.online;

import gg.vape.Vape;
import gg.vape.manager.client.OnlineAccountState;
import gg.vape.manager.client.OnlineConnectionManager;
import gg.vape.manager.client.OnlineConnectionState;
import gg.vape.module.none.ClientSettings;
import gg.vape.ui.click.component.FlowLayoutComponent;
import gg.vape.ui.click.component.GuiComponent;
import gg.vape.ui.click.frame.SettingsSubpageFrame;
import gg.vape.ui.click.frame.impl.ThemeComponentGroupFactory;
import gg.vape.ui.click.layout.ComponentLayout;

public class OnlineConnectionSettingsFrame
extends SettingsSubpageFrame {
    private FlowLayoutComponent xN = new FlowLayoutComponent(104.0);
    private boolean x0 = false;
    private static OnlineConnectionBackdropFrame xp;
    private final OnlineConnectionRetryPageComponent xq;
    private final OnlineAccountSettingsPageComponent xS = new OnlineAccountSettingsPageComponent();
    private final OnlineAccountConnectedPageComponent x2 = new OnlineAccountConnectedPageComponent();
    private final OnlineAccountUnavailablePageComponent xb;
    private boolean xy = false;
    private final OnlineConnectionConnectingPageComponent xE = new OnlineConnectionConnectingPageComponent();
    private final OnlineAccountLinkCodePageComponent xK;
    public static OnlineConnectionSettingsFrame x4;
    private OnlineConnectionState xx = null;

    public void F(OnlineAccountState onlineAccountState, OnlineConnectionState onlineConnectionState) {
        if (onlineConnectionState == OnlineConnectionState.OFFLINE) {
            this.x(onlineConnectionState);
        }
    }

    public void x(OnlineConnectionState onlineConnectionState) {
        OnlineConnectionSettingsPageComponent onlineConnectionSettingsPageComponent = null;
        if (onlineConnectionState == OnlineConnectionState.ONLINE) {
            onlineConnectionSettingsPageComponent = this.xS;
            if (this.x0) {
                this.d$src$V$bbt7r8();
            }
        }
        if (onlineConnectionState == OnlineConnectionState.OFFLINE) {
            onlineConnectionSettingsPageComponent = OnlineConnectionManager.INSTANCE.getNextReconnectAt() == -1L ? (OnlineConnectionManager.INSTANCE.getAccountState() == OnlineAccountState.REGISTERED ? this.x2 : (OnlineConnectionManager.INSTANCE.getAccountState() == OnlineAccountState.UNREGISTERED ? this.xK : this.xb)) : this.xq;
        }
        if (onlineConnectionState == OnlineConnectionState.CONNECTING) {
            onlineConnectionSettingsPageComponent = OnlineConnectionManager.INSTANCE.getNextReconnectAt() == -1L ? this.xE : this.xq;
        }
        if (onlineConnectionSettingsPageComponent != null) {
            ((OnlineConnectionSettingsPageComponent)onlineConnectionSettingsPageComponent).s();
            this.xN.removeMarkedChildren();
            this.xN.h(onlineConnectionSettingsPageComponent, new Object[0]);
        }
    }

    public static synchronized OnlineConnectionSettingsFrame getInstance() {
        if (x4 == null) {
            xp = new OnlineConnectionBackdropFrame();
            x4 = new OnlineConnectionSettingsFrame();
        }
        return x4;
    }

    public static void updateConnectionStateIfCreated(OnlineConnectionState connectionState) {
        OnlineConnectionSettingsFrame frame = x4;
        if (frame != null) {
            frame.x(connectionState);
        }
    }

    public static void updateAccountStateIfCreated(OnlineAccountState accountState,
                                                    OnlineConnectionState connectionState) {
        OnlineConnectionSettingsFrame frame = x4;
        if (frame != null) {
            frame.F(accountState, connectionState);
        }
    }

    @Override
    protected void T(double d, double d2) {
    }

    @Override
    public void H() {
        super.H();
        ClientSettings.INSTANCE.getActiveStack().v(xp);
        ClientSettings.INSTANCE.getActiveStack().v(this);
        if (this.H$src$Lgg_vape_ui_click_frame_CenteredPopupFrame_$1qmombx() != null) {
            ClientSettings.INSTANCE.getActiveStack().R(this, this.H$src$Lgg_vape_ui_click_frame_CenteredPopupFrame_$1qmombx());
        }
        this.H(true);
        this.i$src$Lgg_vape_ui_click_frame_FrameToolbarComponent_$gnpgc6().updateLayout();
    }

    public void e(boolean bl) {
        this.x0 = bl;
        this.xy = true;
        ClientSettings.INSTANCE.getActiveStack().q(xp);
        ClientSettings.INSTANCE.getActiveStack().q(this);
        ClientSettings.INSTANCE.getActiveStack().v(xp);
        ClientSettings.INSTANCE.getActiveStack().v(this);
    }

    public OnlineConnectionSettingsFrame() {
        super("vape_online", "InevitableClient Online", false, false);
        this.xq = new OnlineConnectionRetryPageComponent();
        this.xK = new OnlineAccountLinkCodePageComponent();
        this.xb = new OnlineAccountUnavailablePageComponent();
        try {
            this.o(104.0);
            this.Y(160.0);
            this.xN.setShowDisabledOverlay(false);
            ComponentLayout componentLayout = this.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij();
            componentLayout.t(false);
            componentLayout.M(false);
            componentLayout.U(false);
            componentLayout.I(false);
            componentLayout.u(false);
            this.g(true);
            this.i$src$Lgg_vape_ui_click_frame_FrameToolbarComponent_$gnpgc6().setDefaultCloseActionVisible(true);
            this.i$src$Lgg_vape_ui_click_frame_FrameToolbarComponent_$gnpgc6().setToolbarWidth(104.0);
            this.i$src$Lgg_vape_ui_click_frame_FrameToolbarComponent_$gnpgc6().setDefaultIconScale(0.75f);
            this.i$src$Lgg_vape_ui_click_frame_FrameToolbarComponent_$gnpgc6().getCloseButton().addClickListener(this::d$src$V$bbt7r8);
            GuiComponent[] guiComponentArray = ThemeComponentGroupFactory.k(J);
            this.n(guiComponentArray);
            this.i$src$Lgg_vape_ui_click_frame_FrameToolbarComponent_$gnpgc6().setUseExplicitWidth(true);
            this.i$src$Lgg_vape_ui_click_frame_FrameToolbarComponent_$gnpgc6().o(104.0);
            this.addGlobalMouseListener(new OnlineConnectionBackdropMouseListener(this));
            this.h(this.xN, new Object[0]);
            this.x(OnlineConnectionManager.INSTANCE.getConnectionState());
        }
        catch (Exception exception) {
            Vape.logThrowable(exception);
        }
    }

    public void d$src$V$bbt7r8() {
        this.xy = false;
        if (this.H$src$Lgg_vape_ui_click_frame_CenteredPopupFrame_$1qmombx() != null) {
            this.p();
        }
        ClientSettings.INSTANCE.getActiveStack().m(xp);
        ClientSettings.INSTANCE.getActiveStack().m(this);
    }

}

