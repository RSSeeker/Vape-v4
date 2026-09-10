package gg.vape.notification;

import gg.vape.ui.click.component.GuiComponent;
import gg.vape.ui.click.component.gui.WrappedTextComponent;
import gg.vape.ui.theme.ThemeColors;

public class TextNotificationContent
implements NotificationContent {
    private NotificationType type;
    private boolean emphasized;
    private WrappedTextComponent textComponent;
    private static GuiComponent[] controlFlowMarker;

    @Override
    public double getWidth() {
        return this.textComponent.A() + 4.0;
    }

    public TextNotificationContent(String text) {
        this(text, NotificationType.INFO, false);
    }

    public static void setControlFlowMarker(GuiComponent[] marker) {
        controlFlowMarker = marker;
    }

    public TextNotificationContent(String text, boolean emphasized) {
        this(text, NotificationType.INFO, emphasized);
    }

    static {
        if (TextNotificationContent.getControlFlowMarker() == null) {
            TextNotificationContent.setControlFlowMarker(new GuiComponent[1]);
        }
    }

    @Override
    public void render(double x, double y) {
        this.textComponent.K(x);
        this.textComponent.S(y);
        this.textComponent.c();
    }


    public String getText() {
        return this.textComponent.getText();
    }

    public TextNotificationContent(String text, NotificationType type) {
        this(text, type, false);
    }

    public void setText(String text) {
        this.textComponent.setText(NotificationText.localize(text));
    }

    public NotificationType getType() {
        return this.type;
    }

    public TextNotificationContent(String text, NotificationType type, boolean emphasized) {
        this.textComponent = new WrappedTextComponent(NotificationText.localize(text), 0.9, ThemeColors.J.Z, false);
        this.textComponent.setWrapWidth(100.0);
        this.textComponent.setCentered(true);
        this.type = type;
        this.emphasized = emphasized;
    }

    public boolean isEmphasized() {
        return this.emphasized;
    }

    public void setType(NotificationType type) {
        this.type = type;
    }

    public static GuiComponent[] getControlFlowMarker() {
        return controlFlowMarker;
    }

    @Override
    public double getHeight() {
        if (this.textComponent.getText().equals("")) {
            return 0.0;
        }
        return this.textComponent.C() + 4.0;
    }
}

