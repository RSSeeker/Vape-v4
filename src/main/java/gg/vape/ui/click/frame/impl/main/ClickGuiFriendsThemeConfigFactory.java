package gg.vape.ui.click.frame.impl.main;

import gg.vape.ui.click.component.GuiComponent;
import gg.vape.ui.click.component.PanelComponent;
import gg.vape.ui.click.frame.impl.ThemeComponentGroupFactory;
import gg.vape.ui.click.frame.impl.main.ClickGuiOverlaySpec;
import gg.vape.ui.click.frame.impl.main.ClickGuiOverlayTransitionMode;
import gg.vape.ui.theme.ThemeColors;

public final class ClickGuiFriendsThemeConfigFactory {
    private ClickGuiFriendsThemeConfigFactory() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static ClickGuiOverlaySpec createOverlay() {
        return ClickGuiOverlaySpec.builder().title("InevitableClient Online Settings").sidecarIcon("settings").initializeContent(ClickGuiFriendsThemeConfigFactory::populate).transitionMode(ClickGuiOverlayTransitionMode.REPLACE).build();
    }

    private static void populate(PanelComponent panelComponent) {
        for (GuiComponent guiComponent : ThemeComponentGroupFactory.k(ThemeColors.J)) {
            guiComponent.setExplicitWidth(panelComponent.A() - 4.0);
            panelComponent.h(guiComponent, new Object[0]);
        }
    }
}

