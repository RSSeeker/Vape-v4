package gg.vape.ui.click.frame.impl.online;

import gg.vape.manager.client.OnlineConnectionManager;
import gg.vape.notification.NotificationText;
import gg.vape.ui.click.component.gui.UnderlinedTextLabel;
import gg.vape.ui.click.component.layout.PaddedComponent;
import gg.vape.ui.click.frame.impl.online.OnlineConnectionSettingsPageComponent;
import java.awt.Color;

public class OnlineConnectionRetryPageComponent
extends OnlineConnectionSettingsPageComponent {
    private UnderlinedTextLabel yr;


    private static void cancelReconnect() {
        OnlineConnectionManager.INSTANCE.cancelConnectionAttempt();
    }

    @Override
    public void c() {
        super.c();
        long reconnectAt = OnlineConnectionManager.INSTANCE.getNextReconnectAt();
        if (reconnectAt != -1L) {
            int remainingSeconds = (int)((reconnectAt - System.currentTimeMillis()) / 1000L);
            String message = String.format(java.util.Locale.ROOT,
                    NotificationText.localize("Reconnecting in %d seconds..."), remainingSeconds);
            this.getAlternateFontRenderer(0.8).W(message, this.G$src$D$1b2f02a() + this.A() / 2.0, this.n() + 52.0, OnlineConnectionRetryPageComponent.J.A);
        } else {
            this.getAlternateFontRenderer(1.0).W(NotificationText.localize("Reconnecting..."), this.G$src$D$1b2f02a() + this.A() / 2.0, this.n() + 52.0, OnlineConnectionRetryPageComponent.J.A);
        }
        this.yr.setExplicitWidth(60.0);
    }

    public OnlineConnectionRetryPageComponent() {
        this.yr = new UnderlinedTextLabel("Cancel", (double)0.8f, OnlineConnectionRetryPageComponent.J.A, new Color(255, 255, 255, 255));
        this.yr.addClickListener(OnlineConnectionRetryPageComponent::cancelReconnect);
        this.yr.o(50.0);
        this.yr.Y(10.0);
        this.h(new PaddedComponent(125.0, 0.0, 20.0, 0.0, this.yr), new Object[0]);
    }

    @Override
    public void s() {
    }
}

