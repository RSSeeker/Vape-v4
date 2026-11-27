package gg.vape.tutorial.page;

import gg.vape.module.none.ClientSettings;
import gg.vape.tutorial.MultiComponentHighlightTutorialAction;
import gg.vape.tutorial.TutorialPage;
import gg.vape.tutorial.TutorialTooltipPlacement;
import gg.vape.tutorial.selector.ModulesTutorialBindExpandButtonSelector;
import gg.vape.tutorial.selector.ModulesTutorialCategoryButtonSelector;
import gg.vape.tutorial.selector.ModulesTutorialModuleBindInputSelector;
import gg.vape.tutorial.selector.ModulesTutorialModuleValuesSelector;
import gg.vape.tutorial.selector.ModulesTutorialNamedModuleRowSelector;
import gg.vape.tutorial.selector.ModulesTutorialValuesExpandButtonSelector;
import gg.vape.ui.click.component.GuiComponent;
import gg.vape.ui.click.component.module.ModuleComponent;
import gg.vape.ui.click.frame.ModuleCategoryNavigationButtonComponent;

public class ModulesTutorialPage
extends TutorialPage {
    private static String obfuscationState;

    static {
        ModulesTutorialPage.setPageObfuscationState(null);
    }

    public static String getPageObfuscationState() {
        return obfuscationState;
    }

    public static void setPageObfuscationState(String state) {
        obfuscationState = state;
    }

    public ModulesTutorialPage() {
        super("Modules");
        this.addAction(new MultiComponentHighlightTutorialAction((GuiComponent)ClientSettings.settingsSearchFrame, ModuleCategoryNavigationButtonComponent.class, "Soulbound Module Categories", "Soulbound primarily consists of modules, split into separate module categories", false).setTooltipPlacement(TutorialTooltipPlacement.RIGHT));
        this.addAction(new MultiComponentHighlightTutorialAction((GuiComponent)ClientSettings.settingsSearchFrame, new ModulesTutorialCategoryButtonSelector(this, ModuleCategoryNavigationButtonComponent.class), "Open the combat category", "Click the combat category button", true).setTooltipPlacement(TutorialTooltipPlacement.RIGHT));
        this.addAction(new MultiComponentHighlightTutorialAction((GuiComponent)ClientSettings.legitModuleCategoryFrame, ModuleComponent.class, "Modules", "Modules in this category are listed here.", false).setTooltipPlacement(TutorialTooltipPlacement.RIGHT));
        this.addAction(new MultiComponentHighlightTutorialAction((GuiComponent)ClientSettings.legitModuleCategoryFrame, new ModulesTutorialNamedModuleRowSelector(this, ModuleComponent.class), "Module", "For a module to do anything, you can enable it by left clicking its button.", false).setTooltipPlacement(TutorialTooltipPlacement.RIGHT));
        this.addAction(new MultiComponentHighlightTutorialAction((GuiComponent)ClientSettings.legitModuleCategoryFrame, new ModulesTutorialValuesExpandButtonSelector(this, ModuleComponent.class), "Open the Module Settings", "Open the module settings by clicking this button", true).setTooltipPlacement(TutorialTooltipPlacement.RIGHT));
        this.addAction(new MultiComponentHighlightTutorialAction((GuiComponent)ClientSettings.legitModuleCategoryFrame, new ModulesTutorialModuleValuesSelector(this, ModuleComponent.class), "Module Settings", "These are the module settings. You can configure various options to change the way the module works.", false).queueMessage("Module Settings", "Be aware that your ability to bypass depends primarily on the modules you use, and the settings that you use. We suggest checking the forums for advice on settings to use for each server that you play on.").setTooltipPlacement(TutorialTooltipPlacement.RIGHT));
        this.addAction(new MultiComponentHighlightTutorialAction((GuiComponent)ClientSettings.legitModuleCategoryFrame, new ModulesTutorialBindExpandButtonSelector(this, ModuleComponent.class), "Close the settings", "You can close the module settings by clicking the settings button again", true).setTooltipPlacement(TutorialTooltipPlacement.RIGHT));
        this.addAction(new MultiComponentHighlightTutorialAction((GuiComponent)ClientSettings.legitModuleCategoryFrame, new ModulesTutorialModuleBindInputSelector(this, ModuleComponent.class), "Binding modules", "You can bind a module to a key on your keyboard by clicking this button", false).queueMessage("Binding modules", "If you press the bound key while in game, it will toggle the module without having to open the GUI").setIncludeInactiveTargets(true).setTooltipPlacement(TutorialTooltipPlacement.RIGHT));
    }
}
