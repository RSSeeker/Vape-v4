package gg.vape.ui.click.frame.impl;

import com.google.gson.JsonObject;
import gg.vape.Vape;
import gg.vape.api.ApiResponse;
import gg.vape.api.ApiServices;
import gg.vape.config.PublicProfileSettings;
import gg.vape.manager.client.OnlineConnectionManager;
import gg.vape.manager.client.OnlineConnectionState;
import gg.vape.mapping.ItemMappingEntry;
import gg.vape.module.none.ClientSettings;
import gg.vape.notification.NotificationText;
import gg.vape.protocol.ZeusConnectionManager;
import gg.vape.protocol.packet.GroupCreateResponsePacket;
import gg.vape.protocol.packet.GroupCreateStatus;
import gg.vape.ui.click.component.ColorDividerComponent;
import gg.vape.ui.click.component.DropdownSelectComponent;
import gg.vape.ui.click.component.GuiComponent;
import gg.vape.ui.click.component.input.BindValueRowComponent;
import gg.vape.ui.click.component.module.ModuleComponent;
import gg.vape.ui.click.component.value.BooleanToggleComponent;
import gg.vape.ui.click.component.value.ClientSettingsColorValueEditorComponent;
import gg.vape.ui.click.component.value.ClientSettingsEntityCheckDependentToggleComponent;
import gg.vape.ui.click.component.value.ClientSettingsPrimaryBooleanToggle;
import gg.vape.ui.click.component.value.ClientSettingsSecondaryBooleanToggle;
import gg.vape.ui.click.component.value.ClientSettingsTertiaryBooleanToggle;
import gg.vape.ui.click.component.value.ClientSettingsThemeBooleanToggle;
import gg.vape.ui.click.component.value.NumberSliderComponent;
import gg.vape.ui.click.component.value.PublicProfileModeDropdownComponent;
import gg.vape.ui.click.frame.Frame;
import gg.vape.ui.click.text.SuffixTextTruncationIndexCache;
import gg.vape.ui.click.text.TextTruncationIndexCache;
import gg.vape.ui.font.FontManager;
import gg.vape.ui.font.SmoothFontRenderer;
import gg.vape.ui.theme.ThemeColors;
import gg.vape.utils.render.ItemIconRenderer;
import gg.vape.wrapper.Wrapper;
import gg.vape.wrapper.impl.ForgeVersion;
import gg.vape.wrapper.impl.I18n;
import gg.vape.wrapper.impl.Item;
import gg.vape.wrapper.impl.ItemStack;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.Potion;
import gg.vape.wrapper.impl.StatusEffect;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ClientSettingsComponentFactory {
    private static void lambda$createSubmenuComponents$14() {
        for (int i = 0; i < 45; ++i) {
            ItemStack itemStack = Minecraft.thePlayer().V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6().c(i);
            if (itemStack.isNull()) continue;
            Vape.debugLog("Display Name: " + itemStack.x() + " ID: " + Item.f(itemStack.getItem()) + " Slot: " + i);
        }
    }

    private static void lambda$createMainChildren$9() {
        Vape.debugLog("Saving settings...");
        JsonObject jsonObject = Vape.INSTANCE.getSyncThread().buildSettingsPayload(true);
        ApiServices.getInstance().getUserDataApi().saveUserData(jsonObject).thenAccept(ClientSettingsComponentFactory::lambda$null$7).exceptionally(ClientSettingsComponentFactory::lambda$null$8);
    }

    private static GuiComponent[] W(ClientSettings clientSettings, boolean bl) {
        ArrayList<GuiComponent> arrayList = new ArrayList<GuiComponent>();
        arrayList.add(new BooleanToggleComponent(clientSettings.blurBackground));
        arrayList.add(new BooleanToggleComponent(clientSettings.guiBindIndicator));
        arrayList.add(new BooleanToggleComponent(clientSettings.showTooltips));
        if (!bl) {
            arrayList.add(new BooleanToggleComponent(clientSettings.showLegitMode));
        }
        arrayList.add(new NumberSliderComponent(clientSettings.rainbowSpeed));
        arrayList.add(new DropdownSelectComponent(Vape.INSTANCE.getPublicProfileSettings().guiStyle));
        if (bl) {
            arrayList.add(new BooleanToggleComponent(clientSettings.showEnabledCount));
        }
        arrayList.add(new DropdownSelectComponent(Vape.INSTANCE.getClientSettings().guiScale));
        if (!bl) {
            arrayList.add(new DropdownSelectComponent(ClientSettings.INSTANCE.searchBarStyle));
        }
        return arrayList.toArray(new GuiComponent[0]);
    }

    private static Void lambda$null$8(Throwable throwable) {
        Vape.logThrowable(throwable);
        return null;
    }

    private static Void lambda$null$5(Throwable throwable) {
        Vape.logThrowable(throwable);
        return null;
    }

    public static List<GuiComponent> M(ThemeColors themeColors, gg.vape.config.ClientSettings clientSettings, ClientSettings clientSettings2, boolean bl) {
        ArrayList<GuiComponent> arrayList = new ArrayList<GuiComponent>();
        ClientSettingsColorValueEditorComponent clientSettingsColorValueEditorComponent = new ClientSettingsColorValueEditorComponent(clientSettings.guiColor);
        arrayList.add(clientSettingsColorValueEditorComponent);
        arrayList.add(new BindValueRowComponent("Rebind GUI", clientSettings2.getBind()).w("Change the bind of the GUI"));
        arrayList.add(new ColorDividerComponent(themeColors.i));
        return arrayList;
    }

    private static void lambda$createSubmenuComponents$13() {
        Vape.INSTANCE.getProfilesManager().resetAllSettings();
    }

    private static void lambda$createSubmenuComponents$16() {
        int n = 0;
        Vape.debugLog("Potion Data Dump Version: " + ForgeVersion.c());
        Vape.debugLog("__________________________________________________________");
        while (true) {
            Wrapper wrapper;
            if (ForgeVersion.MC_1_16_5.d()) {
                wrapper = StatusEffect.E(n);
                if (wrapper.isNull()) {
                    if (n != 0) break;
                    ++n;
                    continue;
                }
                Vape.debugLog(n + " -> Display Name: " + ((StatusEffect)wrapper).d());
            } else {
                wrapper = Potion.getPotionById(n);
                if (wrapper.isNull()) {
                    if (n != 0) break;
                    ++n;
                    continue;
                }
                Vape.debugLog(n + " -> Translation Key: " + ((Potion)wrapper).y$src$Ljava_lang_String_$yl6pfj() + " Display Name: " + I18n.format(((Potion)wrapper).y$src$Ljava_lang_String_$yl6pfj(), new Object[0]));
            }
            ++n;
        }
        Vape.debugLog("__________________________________________________________");
    }

    private static void lambda$createSubmenuComponents$17() {
        Vape.INSTANCE.getFontManager().U();
    }

    private static void lambda$createMainChildren$1() {
        try {
            ItemIconRenderer.clear();
        }
        catch (Exception exception) {
            // empty catch block
        }
        Vape.INSTANCE.getNotificationManager().showInfo("Cleared", "Cleared ItemStack Cache", 3000L);
    }

    private static void lambda$createMainChildren$12() {
        if (OnlineConnectionManager.INSTANCE.getConnectionState() != OnlineConnectionState.ONLINE) {
            Vape.INSTANCE.getNotificationManager().showInfo("Vape Online", "You must be logged in to perform this action!", 3000L);
            return;
        }
        ZeusConnectionManager.T().u().w(ClientSettingsComponentFactory::lambda$null$10, ClientSettingsComponentFactory::lambda$null$11);
    }

    private static void lambda$createMainChildren$6() {
        ApiServices.getInstance().getUserDataApi().getUserData().thenAccept(ClientSettingsComponentFactory::lambda$null$4).exceptionally(ClientSettingsComponentFactory::lambda$null$5);
    }

    private static void lambda$null$11() {
    }

    private static void lambda$null$10(GroupCreateResponsePacket groupCreateResponsePacket) {
        if (groupCreateResponsePacket.getStatus() == GroupCreateStatus.SUCCESS) {
            Vape.INSTANCE.getNotificationManager().showInfo("Vape Online", "Party created", 3000L);
        } else {
            Vape.INSTANCE.getNotificationManager().showInfo("Vape Online", NotificationText.localize("Party creation error: ") + (Object)((Object)groupCreateResponsePacket.getStatus()), 3000L);
        }
    }

    private static void lambda$createMainChildren$3() {
        Vape.debugLog("Reloading settings...");
        Vape.INSTANCE.getSyncThread().loadConfig();
    }

    private static void lambda$createMainChildren$2() {
        ItemMappingEntry itemMappingEntry = Vape.INSTANCE.getItemStackResolver().resolve(Minecraft.thePlayer().B$src$Lgg_vape_wrapper_impl_ItemStack_$impdvt());
        Vape.INSTANCE.getNotificationManager().showInfo("Universal Item", NotificationText.localize("You're holding: ") + (itemMappingEntry != null ? itemMappingEntry.getResourceKey() : null), 3000L);
    }

    private static void lambda$createSubmenuComponents$15() {
        for (Frame frame : ClientSettings.getAllFrames()) {
            if (!(frame instanceof ModuleCategoryFrame)) continue;
            ModuleCategoryFrame moduleCategoryFrame = (ModuleCategoryFrame)frame;
            moduleCategoryFrame.setVisible(true);
            for (GuiComponent guiComponent : moduleCategoryFrame.f()) {
                if (!(guiComponent instanceof ModuleComponent)) continue;
                ModuleComponent moduleComponent = (ModuleComponent)guiComponent;
                moduleComponent.expandValueComponents();
            }
        }
        ClientSettings.clearPositionedFrames();
    }

    private static void lambda$null$4(ApiResponse apiResponse) {
        Vape.debugLog("response = " + apiResponse);
    }


    private ClientSettingsComponentFactory() {
    }

    public static Map<ThemeComponentGroupKey, GuiComponent[]> d(ThemeColors themeColors, gg.vape.config.ClientSettings clientSettings, ClientSettings clientSettings2, boolean bl) {
        PublicProfileSettings publicProfileSettings = Vape.INSTANCE.getPublicProfileSettings();
        LinkedHashMap<ThemeComponentGroupKey, GuiComponent[]> linkedHashMap = new LinkedHashMap<ThemeComponentGroupKey, GuiComponent[]>();
        linkedHashMap.put(new ThemeComponentGroupKey("General", null), new GuiComponent[]{new BooleanToggleComponent(clientSettings2.multiKeybinding), new BooleanToggleComponent(publicProfileSettings.autoSave), new BooleanToggleComponent(OnlineConnectionManager.INSTANCE.getGlobalSettingsController().getCacheData()), new PublicProfileModeDropdownComponent(publicProfileSettings.language), new ClientSettingsActionButtonRowComponent("Reset current profile", ClientSettingsComponentFactory::lambda$createSubmenuComponents$13).w("This will set your current profile to the default settings of Vape")});
        linkedHashMap.put(new ThemeComponentGroupKey("Modules", null), new GuiComponent[]{new ColorDividerComponent(themeColors.l), new BooleanToggleComponent(clientSettings.lobbyCheck), new BooleanToggleComponent(clientSettings.sanityCheck), new BooleanToggleComponent(clientSettings.showNbtTags), new BooleanToggleComponent(clientSettings.healthPrediction), new ClientSettingsEntityCheckDependentToggleComponent(clientSettings.estimateFoodHealing, clientSettings), new ClientSettingsThemeBooleanToggle(clientSettings.estimateFallDamage, clientSettings)});
        linkedHashMap.put(new ThemeComponentGroupKey("Silent Aim", null), new GuiComponent[]{new DropdownSelectComponent(clientSettings.movementCorrection), new BooleanToggleComponent(clientSettings.thirdPersonAimView), new BooleanToggleComponent(clientSettings.aimIndicator), new BooleanToggleComponent(clientSettings.useReach), new BooleanToggleComponent(clientSettings.useHitboxes)});
        linkedHashMap.put(new ThemeComponentGroupKey("GUI", "newgui"), ClientSettingsComponentFactory.W(clientSettings2, bl));
        linkedHashMap.put(new ThemeComponentGroupKey("Sound", null), new GuiComponent[]{new NumberSliderComponent(publicProfileSettings.volume), new BooleanToggleComponent(publicProfileSettings.muted)});
        linkedHashMap.put(new ThemeComponentGroupKey("Notifications", null), new GuiComponent[]{new BooleanToggleComponent(publicProfileSettings.notifications), new ClientSettingsPrimaryBooleanToggle(publicProfileSettings.toggleAlerts, publicProfileSettings), new ClientSettingsTertiaryBooleanToggle(publicProfileSettings.profileSwitchNotifications, publicProfileSettings), new ClientSettingsSecondaryBooleanToggle(publicProfileSettings.friendNotifications, publicProfileSettings)});
        return linkedHashMap;
    }

    private static void lambda$createMainChildren$0() {
        Vape.INSTANCE.getNotificationManager().showInfo("Test header", "test body", 500000L);
    }

    private static void lambda$null$7(ApiResponse apiResponse) {
        Vape.debugLog("response = " + apiResponse);
    }

    private static void x() {
        Vape.debugLog("=== Cache Sizes ===");
        Vape.debugLog("CutoffLabelManager: " + SuffixTextTruncationIndexCache.INSTANCE.getCacheSize());
        Vape.debugLog("SmoothCutoffLabelManager: " + TextTruncationIndexCache.INSTANCE.getCacheSize());
        Vape.debugLog("--- FontRenderers ---");
        FontManager fontManager = Vape.INSTANCE.getFontManager();
        Map<String, Map<Integer, SmoothFontRenderer>> map = fontManager.n();
        for (Map.Entry<String, Map<Integer, SmoothFontRenderer>> entry : map.entrySet()) {
            String string = entry.getKey();
            Map<Integer, SmoothFontRenderer> map2 = entry.getValue();
            for (Map.Entry<Integer, SmoothFontRenderer> entry2 : map2.entrySet()) {
                int n = entry2.getKey();
                SmoothFontRenderer smoothFontRenderer = entry2.getValue();
                Vape.debugLog(string + "[" + n + "] cachedWidths=" + smoothFontRenderer.b() + " cachedStripped=" + smoothFontRenderer.R());
            }
        }
        Vape.debugLog("===================");
    }
}
