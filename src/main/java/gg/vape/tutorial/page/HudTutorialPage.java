package gg.vape.tutorial.page;

import gg.vape.module.none.ClientSettings;
import gg.vape.tutorial.MultiComponentHighlightTutorialAction;
import gg.vape.tutorial.SingleComponentHighlightTutorialAction;
import gg.vape.tutorial.TutorialPage;
import gg.vape.tutorial.TutorialTooltipPlacement;
import gg.vape.tutorial.selector.HudTutorialKeystrokesHudModuleRowSelector;
import gg.vape.tutorial.selector.HudTutorialNamedHudModuleRowSelector;
import gg.vape.ui.click.component.GuiComponent;
import gg.vape.ui.click.frame.impl.hud.HudEditorReturnToMainLayerFrame;
import gg.vape.ui.click.frame.impl.hud.HudModuleListEntry;
import gg.vape.ui.click.frame.impl.hud.HudModuleListPanel;
import gg.vape.ui.click.frame.impl.hud.HudModuleSelectorFrame;
import gg.vape.ui.click.frame.impl.hud.KeystrokesHudFrame;

public class HudTutorialPage
extends TutorialPage {
    public HudTutorialPage() {
        super("Legit mod menu");
        this.addAction(new SingleComponentHighlightTutorialAction(ClientSettings.moduleSearchFrame.n$src$Lgg_vape_ui_click_frame_impl_ModuleSearchFrameHe$xia8v2().G$src$Lgg_vape_ui_click_component_IconButtonComponent_$1pnwa51(), "Click this button to open the legit mod menu", "", true).setTooltipPlacement(TutorialTooltipPlacement.BOTTOM));
        this.addAction(new SingleComponentHighlightTutorialAction(ClientSettings.getFrame(HudModuleSelectorFrame.class), "This is the legit mod menu", "It includes various features generally included inside legit clients, or as mods.", false).queueMessage("This is the legit mod menu", "Rules and anti-cheat behavior vary; no module is guaranteed undetectable.").setTooltipPlacement(TutorialTooltipPlacement.TOP));
        this.addAction(new MultiComponentHighlightTutorialAction((GuiComponent)ClientSettings.getFrame(HudModuleListPanel.class), new HudTutorialNamedHudModuleRowSelector(this, HudModuleListEntry.class), "Legit mods", "Some modules such as freelook or NoClickDelay are typically disallowed by servers and thus have been removed from legit clients.", false).queueMessage("Legit mods", "Soulbound includes optional modules. Server rules and anti-cheat behavior vary."));
        this.addAction(new MultiComponentHighlightTutorialAction((GuiComponent)ClientSettings.getFrame(HudModuleListPanel.class), new HudTutorialKeystrokesHudModuleRowSelector(this, HudModuleListEntry.class), "Enable the keystrokes mod", "", true));
        this.addAction(new SingleComponentHighlightTutorialAction(ClientSettings.getFrame(KeystrokesHudFrame.class), "HUD mods", "Certain mods such as Keystrokes is a HUD mod. It will render on top of your screen while in game", false).queueMessage("HUD mods", "You can freely move these HUD mods anywhere on your screen by dragging it").setTooltipPlacement(TutorialTooltipPlacement.RIGHT));
        this.addAction(new SingleComponentHighlightTutorialAction(ClientSettings.getFrame(HudEditorReturnToMainLayerFrame.class).getHeader().getReturnButton(), "Click this button to return to the cheat menu", "", true).setTooltipPlacement(TutorialTooltipPlacement.BOTTOM));
    }
}
