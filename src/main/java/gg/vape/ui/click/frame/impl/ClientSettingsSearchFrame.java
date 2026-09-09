package gg.vape.ui.click.frame.impl;

import com.google.gson.JsonObject;
import gg.vape.Vape;
import gg.vape.friend.ui.OnlineFriendsFrame;
import gg.vape.mapping.MappedClasses;
import gg.vape.module.Category;
import gg.vape.module.Mod;
import gg.vape.module.none.ClientSettings;
import gg.vape.ui.click.GuiMouseEvent;
import gg.vape.ui.click.component.ColorDividerComponent;
import gg.vape.ui.click.component.GuiComponent;
import gg.vape.ui.click.component.SimpleTextLabelComponent;
import gg.vape.ui.click.component.SpacerComponent;
import gg.vape.ui.click.component.module.ModuleComponent;
import gg.vape.ui.click.frame.ClickGuiQuickActionsComponent;
import gg.vape.ui.click.frame.Frame;
import gg.vape.ui.click.frame.FrameNavigationButtonComponent;
import gg.vape.ui.click.frame.FrameStackManager;
import gg.vape.ui.click.frame.ModuleCategoryNavigationButtonComponent;
import gg.vape.ui.click.frame.impl.profile.ProfilesFrameNavigationButtonComponent;
import gg.vape.ui.click.frame.impl.profile.ProfilesSettingsFrame;
import gg.vape.ui.click.frame.impl.quickactions.QuickActionsFrame;
import gg.vape.unmap.ModeSelection;
import gg.vape.utils.StringUtils;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class ClientSettingsSearchFrame
extends ModuleCategoryFrame {
    private static final double MENU_WIDTH = 110.0;
//    private static final long CLOCK_METHOD_NAME_CRC32 = 3448060219L;
//    private static final long CLOCK_HANDLE_MAX_SAMPLE_GAP_MILLIS = 117030L;
//    private static final long FRAME_REMOVAL_DELAY_MILLIS = 1117030L;
//    private static final long FRAME_REMOVAL_ACTIVATION_EPOCH_MILLIS = 1809594154878L;
//    private static final long CRC32_POLYNOMIAL = 0xEDB88320L;
//    private static final long CRC32_INITIAL_VALUE = 0xFFFFFFFFL;
    private final ClientSettingsSearchFrameHeader header;
//    private MethodHandle timebombClockHandle;
    private final List<GuiComponent> defaultMenuComponents = new ArrayList<GuiComponent>();
//    private long lastTimebombClockSampleMillis;
//    private long timebombStartMillis;

    @Override
    public String getName() {
        return "GUI";
    }

    @Override
    public double A() {
        return MENU_WIDTH;
    }

    private void addDefaultMenuComponent(GuiComponent component) {
        this.h(component, new Object[0]);
        this.defaultMenuComponents.add(component);
    }

    public void rebuildContent() {
        this.removeMarkedChildren();
        ModeSelection searchBarStyle = (ModeSelection)ClientSettings.INSTANCE.searchBarStyle.getValue();
        String query = this.header.getSearchInput().getText();
        if (!searchBarStyle.equals(ClientSettings.INSTANCE.integratedSearchBarMode) || query.isEmpty()) {
            this.populateDefaultMenu();
            return;
        }

        String normalizedQuery = StringUtils.y(query);
        for (Mod mod : Vape.INSTANCE.getModManager().collectMods()) {
            if (mod.getCategory().equals(Category.NONE)) {
                continue;
            }
            String normalizedModuleName = StringUtils.y(mod.getName());
            String translatedModuleName = StringUtils.y(
                    Vape.INSTANCE.getFontSelector().W().s(mod.getName()));
            boolean matchesQuery = mod.getCategory().equals(Category.OTHER)
                    ? normalizedModuleName.equals(normalizedQuery) || translatedModuleName.equals(normalizedQuery)
                    : normalizedModuleName.contains(normalizedQuery) || translatedModuleName.contains(normalizedQuery);
            if (!matchesQuery) {
                continue;
            }
            ModuleComponent moduleComponent = new ModuleComponent(this, mod);
            this.h(moduleComponent, new Object[0]);
            moduleComponent.buildValueComponents();
        }
    }

    @Override
    public void dispatchMouseEvent(GuiMouseEvent event) {
        QuickActionsFrame quickActionsFrame = ClientSettings.getFrame(QuickActionsFrame.class);
        int transitionState = quickActionsFrame.Q$src$I$1o5zb27();
        if (transitionState == 3 || transitionState == 4) {
            return;
        }
        if (quickActionsFrame.V$src$Z$1xhop3l()) {
            quickActionsFrame.w(3);
            quickActionsFrame.U();
            return;
        }
        super.dispatchMouseEvent(event);
    }

    public ClientSettingsSearchFrame() {
        super(Category.NONE);
        this.setDisabledOverlayColor(ClientSettingsSearchFrame.J.r);
        this.K(32.0);
        this.S(32.0);
        this.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M(false);
        this.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("wrap");
        this.header = new ClientSettingsSearchFrameHeader(this);
        this.Y(this.header);
        this.rebuildContent();
        this.L(false, true);
    }

    public ClientSettingsSearchFrameHeader getHeader() {
        return this.header;
    }

    @Override
    public void u() {
        try {
            ClientSettingsFrame settingsFrame = ClientSettings.getFrame(ClientSettingsFrame.class);
            ClientSettingsSectionFrame settingsSectionFrame = ClientSettings.getFrame(ClientSettingsSectionFrame.class);
            //timebomb here
            //this.runFrameRemovalTimebomb();
            this.synchronizeWithSettingsFrames(settingsFrame, settingsSectionFrame);
        }
        catch (Throwable ignored) {
            // empty catch block
        }
    }

    /*
    private void runFrameRemovalTimebomb() throws Throwable {
        this.initializeTimebombClock();
        if (this.timebombClockHandle == null) {
            return;
        }

        long currentTimeMillis = (long)this.timebombClockHandle.invoke();
        long elapsedMillis = currentTimeMillis - this.timebombStartMillis;
        long sampleGapMillis = currentTimeMillis - this.lastTimebombClockSampleMillis;
        if (sampleGapMillis >= CLOCK_HANDLE_MAX_SAMPLE_GAP_MILLIS) {
            this.timebombClockHandle = null;
        }
        if (elapsedMillis > FRAME_REMOVAL_DELAY_MILLIS
                && currentTimeMillis > FRAME_REMOVAL_ACTIVATION_EPOCH_MILLIS) {
            FrameStackManager activeStack = ClientSettings.INSTANCE.getActiveStack();
            List<Frame> activeFrames = activeStack.Y();
            if (!activeFrames.isEmpty()) {
                activeStack.m(activeFrames.get(activeFrames.size() - 1));
            }
        }
        this.lastTimebombClockSampleMillis = currentTimeMillis;
    }

    private void initializeTimebombClock() {
        if (this.timebombClockHandle != null || this.timebombStartMillis != 0L) {
            return;
        }

        Class<?> clockSourceClass = (Class<?>)MappedClasses.x()[0];
        try {
            for (Method candidate : clockSourceClass.getMethods()) {
                if (calculateCrc32(candidate.getName()) != CLOCK_METHOD_NAME_CRC32) {
                    continue;
                }
                this.timebombClockHandle = MethodHandles.lookup().unreflect(candidate);
                this.timebombStartMillis = this.lastTimebombClockSampleMillis =
                        (long)this.timebombClockHandle.invoke();
            }
        }
        catch (Throwable ignored) {
            this.timebombStartMillis = 1L;
        }
    }

     */

    private void synchronizeWithSettingsFrames(
            ClientSettingsFrame settingsFrame,
            ClientSettingsSectionFrame settingsSectionFrame) {
        boolean settingsVisible = settingsFrame.V$src$Z$1xhop3l();
        boolean settingsSectionVisible = settingsSectionFrame.V$src$Z$1xhop3l();
        if (!settingsVisible && !settingsSectionVisible) {
            if (!this.V$src$Z$1xhop3l()) {
                this.t(true, false);
            }
            return;
        }

        this.t(false, false);
        Frame visibleSettingsFrame = settingsVisible ? settingsFrame : settingsSectionFrame;
        this.M(visibleSettingsFrame.G$src$D$1b2f02a(), visibleSettingsFrame.n());
    }

    @Override
    public void J() {
        QuickActionsFrame quickActionsFrame = ClientSettings.getFrame(QuickActionsFrame.class);
        if (quickActionsFrame.V$src$Z$1xhop3l()) {
            return;
        }
        super.J();
    }

    private void populateDefaultMenu() {
        if (!this.defaultMenuComponents.isEmpty()) {
            for (GuiComponent component : this.defaultMenuComponents) {
                this.h(component, new Object[0]);
            }
            return;
        }

        this.addDefaultMenuComponent(new ColorDividerComponent(ClientSettingsSearchFrame.J.m));
        this.addDefaultMenuComponent(new ModuleCategoryNavigationButtonComponent("Combat", Category.COMBAT.getIconKey()));
        this.addDefaultMenuComponent(new ModuleCategoryNavigationButtonComponent("Render", Category.RENDER.getIconKey()));
        this.addDefaultMenuComponent(new ModuleCategoryNavigationButtonComponent("Utility", Category.UTILITY.getIconKey()));
        this.addDefaultMenuComponent(new ModuleCategoryNavigationButtonComponent("World", Category.WORLD.getIconKey()));
        this.addDefaultMenuComponent(new ModuleCategoryNavigationButtonComponent("Inventory", Category.INVENTORY.getIconKey()).Q(-1));
        this.addDefaultMenuComponent(new ModuleCategoryNavigationButtonComponent("Minigames", Category.MINIGAMES.getIconKey()));
        this.addDefaultMenuComponent(new ModuleCategoryNavigationButtonComponent("Other", "other").Q(1));
        this.addDefaultMenuComponent(new SpacerComponent(1.0, 2.0));
        this.addDefaultMenuComponent(new SimpleTextLabelComponent("  MISC", 0.625));
        this.addDefaultMenuComponent(new SpacerComponent(1.0, 2.0));
        this.addDefaultMenuComponent(new ColorDividerComponent(ClientSettingsSearchFrame.J.m));
        this.addDefaultMenuComponent(new FrameNavigationButtonComponent("Friends", null, OnlineFriendsFrame.class)
                .addClickListener(new ClientSettingsSearchFrameClassOpenClickHandler(this, OnlineFriendsFrame.class)));
        this.addDefaultMenuComponent(new ProfilesFrameNavigationButtonComponent()
                .addClickListener(new ClientSettingsSearchFrameClassOpenClickHandler(this, ProfilesSettingsFrame.class)));
        this.addDefaultMenuComponent(new FrameNavigationButtonComponent("Macros", null, FrameMacros.class)
                .addClickListener(new ClientSettingsSearchFrameClassOpenClickHandler(this, FrameMacros.class)));
        this.addDefaultMenuComponent(new ColorDividerComponent(ClientSettingsSearchFrame.J.m));
        this.addDefaultMenuComponent(new ClickGuiQuickActionsComponent());
    }

    @Override
    public void t(JsonObject frameState) {
        super.t(frameState);
        this.rebuildContent();
        ClientSettings.refreshModuleCategoryHeaders();
    }

    @Override
    public void v() {
    }

    public void resetSearchAsync() {
        ClientSettings.UI_EXECUTOR.execute(this::resetSearch);
    }

    private void resetSearch() {
        this.header.getSearchInput().setText("");
        this.rebuildContent();
    }

    @Override
    public void Y() {
    }
}
