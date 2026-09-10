package gg.vape.module;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import gg.vape.Vape;
import gg.vape.config.ConfigJsonUtils;
import gg.vape.event.EventBus;
import gg.vape.event.EventListener;
import gg.vape.event.IEvent;
import gg.vape.event.impl.EventKeyPress;
import gg.vape.event.impl.EventModStateChange;
import gg.vape.input.BindActivationMode;
import gg.vape.notification.NotificationText;
import gg.vape.notification.NotificationType;
import gg.vape.notification.ReusableTextNotification;
import gg.vape.module.render.hud.HudModule;
import gg.vape.module.none.ClientSettings;
import gg.vape.ui.click.frame.Frame;
import gg.vape.ui.click.frame.impl.hud.HudModuleFrameBase;
import gg.vape.ui.click.frame.impl.main.ClickGuiModuleCardRenderState;
import gg.vape.unmap.Bendable;
import gg.vape.unmap.INamed;
import gg.vape.unmap.ModBendable;
import gg.vape.unmap.PropertyContainer;
import gg.vape.value.ModuleValueDisplayFormatter;
import gg.vape.value.SubModuleValue;
import gg.vape.value.Value;
import gg.vape.value.ValueDisplayDescriptor;
import gg.vape.wrapper.impl.Minecraft;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import org.jetbrains.annotations.Nullable;

public class Mod
extends PropertyContainer
implements INamed,
EventListener {
    private boolean enabled;
    private static String staticName;
    private final List<ValueDisplayDescriptor> valueDisplayDescriptors;
    private final ReusableTextNotification toggleNotification;
    private DelayedModuleToggleTask moduleRunnable;
    private final Bendable bind;
    protected Category category;
    private final List<Value<?, ?>> values;
    private boolean requiresBind;
    private boolean favorited;
    private final String name;
    private final int defaultKeybind;
    private final List<SubModule> subModules;
    private ModuleDisplayScope displayScope;
    private final int guiColor;
    private boolean developmentWarningShown;
    private boolean defaultVisible = true;
    private boolean visible = true;
    private final List<Value<?, ?>> allValues = new ArrayList();
    private String tooltip;

    public ModuleDisplayInfo getModuleDisplayInfo() {
        return null;
    }

    public String getSimpleSuffix() {
        return "";
    }

    public void registerSubModule(SubModule ... subModuleArray) {
        this.getSubModules().addAll(Arrays.asList(subModuleArray));
    }

    public ModuleDisplayScope getModuleDisplayScope() {
        return this.displayScope;
    }

    public void onEnable() {
    }

    public SubModule getSubModule(String string) {
        for (SubModule subModule : this.getSubModules()) {
            if (!subModule.getName().equalsIgnoreCase(string)) continue;
            return subModule;
        }
        return null;
    }

    public void setVisibility(boolean bl) {
        this.visible = bl;
    }

    public boolean Q() {
        return this.getProperty(PropertyContainer.LEGACY_FLAG);
    }

    public void i(ValueDisplayDescriptor ... valueDisplayDescriptorArray) {
        this.valueDisplayDescriptors.clear();
        this.valueDisplayDescriptors.addAll(Arrays.asList(valueDisplayDescriptorArray));
    }

    public String getDetailedSuffix() {
        return "";
    }

    public void loadJson(JsonObject jsonObject) {
        String string;
        if (this.bind.usesOwnKeybindStorage()) {
            this.bind.getBoundInputs().clear();
        }
        if ((string = ConfigJsonUtils.getString(jsonObject, "name")) != null && string.equalsIgnoreCase(this.getName())) {
            Object object;
            JsonArray jsonArray;
            if (this.bind.usesOwnKeybindStorage()) {
                jsonArray = ConfigJsonUtils.getJsonArray(jsonObject, "keybinds_2");
                if (jsonArray != null) {
                    try {
                        this.bind.loadBoundInputs(jsonArray, false);
                    }
                    catch (Exception exception) {}
                } else {
                    jsonArray = ConfigJsonUtils.getJsonArray(jsonObject, "keybinds");
                    if (jsonArray != null) {
                        try {
                            this.bind.loadBoundInputs(jsonArray, true);
                        }
                        catch (Exception exception) {}
                    } else {
                        this.bind.getBoundInputs().clear();
                        if (this.defaultKeybind != 0) {
                            this.bind.getBoundInputs().add(this.defaultKeybind);
                        }
                    }
                }
                if ((object = ConfigJsonUtils.getString(jsonObject, "bind_mode")) != null && this.bind.supportsActivationMode()) {
                    try {
                        this.bind.setActivationMode(BindActivationMode.valueOf((String)object));
                    }
                    catch (IllegalArgumentException illegalArgumentException) {
                        this.bind.setActivationMode(BindActivationMode.TOGGLE);
                    }
                } else {
                    this.bind.setActivationMode(BindActivationMode.TOGGLE);
                }
            }
            if ((jsonArray = jsonObject.getAsJsonArray("values")) != null) {
                java.util.Iterator<JsonElement> valueIterator = jsonArray.iterator();
                while (valueIterator.hasNext()) {
                    JsonElement jsonElement = valueIterator.next();
                    JsonObject jsonObject2 = jsonElement.getAsJsonObject();
                    for (Value<?, ?> value : this.allValues) {
                        if (!value.matchesJsonId(jsonObject2)) continue;
                        value.loadJson(jsonObject2);
                    }
                }
            }
            if ((object = ConfigJsonUtils.getBoolean(jsonObject, "visible")) != null) {
                this.visible = (Boolean)object;
            }
        }
    }

    public Category getCategory() {
        return this.category;
    }

    public void setDefaultVisibility(boolean bl) {
        this.defaultVisible = bl;
        this.visible = bl;
    }

    public void showToggleNotification() {
        this.toggleNotification.setDefaultDuration(1500L);
        this.toggleNotification.withTitle("§f" + this.getName())
                .withMessage(this.isEnabled() ? "§2Enabled" : "§cDisabled").reset();
        Vape.INSTANCE.getNotificationManager().show(this.toggleNotification);
    }

    @Nullable
    public JsonObject toJson(boolean bl) {
        JsonArray jsonArray;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("name", this.getName());
        if (this.bind.usesOwnKeybindStorage()) {
            jsonArray = this.bind.serializeBoundInputs();
            if (this.defaultKeybind == 0 && jsonArray.size() != 0 || jsonArray.size() == 1 && jsonArray.get(0).getAsInt() != this.defaultKeybind) {
                jsonObject.add("keybinds_2", (JsonElement)jsonArray);
            }
            if (this.bind.supportsActivationMode() && this.bind.getActivationMode() != BindActivationMode.TOGGLE) {
                jsonObject.addProperty("bind_mode", this.bind.getActivationMode().name());
            }
        }
        jsonArray = new JsonArray();
        for (Value<?, ?> value : this.allValues) {
            JsonObject jsonObject2;
            if (!value.isSerializable() || value.isDefault() || (jsonObject2 = value.toJson(bl)).entrySet().size() <= 1) continue;
            jsonArray.add((JsonElement)jsonObject2);
        }
        if (jsonArray.size() != 0) {
            jsonObject.add("values", (JsonElement)jsonArray);
        }
        if (this.visible != this.defaultVisible) {
            jsonObject.addProperty("visible", Boolean.valueOf(this.visible));
        }
        if (jsonObject.entrySet().size() == 1) {
            return null;
        }
        return jsonObject;
    }

    public void setSuffix(String string) {
        this.tooltip = string;
    }

    public void syncSubModuleStates(boolean enabled, boolean bypassVisibilityCheck) {
        for (SubModule subModule : this.getSubModules()) {
            if (subModule.isSelectedSubModule()) {
                if (subModule.isEnabled() == enabled) continue;
                subModule.setEnabled(enabled, bypassVisibilityCheck);
                continue;
            }
            if (!subModule.isParentEnabled()) continue;
            subModule.setEnabled(false, bypassVisibilityCheck);
        }
    }

    public String getSuffixForMode(int mode) {
        String string = "";
        if (mode == 0) {
            string = this.getSimpleSuffix();
        }
        if (mode == 1 && ((string = this.getDetailedSuffix()) == null || string.isEmpty())) {
            string = this.getSimpleSuffix();
        }
        return string;
    }

    public Mod(String string, int n) {
        this(string, n, 0, Category.NONE, null);
    }

    public void setFavorite(boolean bl) {
        this.favorited = bl;
    }

    public void v(long l, boolean bl) {
        if (this.moduleRunnable != null) {
            this.moduleRunnable.setRunning(false);
        }
        this.moduleRunnable = new DelayedModuleToggleTask(this, l, bl);
        new Thread(this.moduleRunnable).start();
    }

    public boolean L() {
        return this.getProperty(PropertyContainer.NEW_BADGE);
    }

    public void toggle() {
        if (this.isRequiresBind() && this.bind.usesOwnKeybindStorage() && this.bind.getBoundInputs().isEmpty()) {
            return;
        }
        this.setEnabled(!this.enabled);
    }

    public void onBindActivated() {
        this.toggle();
    }

    public void setEnabled(boolean enabled, boolean bypassVisibilityCheck) {
        boolean stateChanged = this.enabled != enabled;
        if (!bypassVisibilityCheck && !this.isVisible() && this.category != Category.NONE && enabled) {
            if (Vape.INSTANCE.getNotificationManager() != null) {
                Vape.INSTANCE.getNotificationManager().show("Hidden Module", NotificationText.localize("Attempted to toggle ") + this.getName() + "!", NotificationType.WARNING, 2500L);
            }
            return;
        }
        if (enabled && this.isBlatantMod()) {
            Vape.INSTANCE.getPrimaryMappingTaskSet().f();
        }
        if (!enabled) {
            this.onBeforeDisable();
        }
        this.enabled = enabled;
        if (this.enabled) {
            EventBus.getInstance().registerListener(this, this.w());
            new EventModStateChange(this, true).fire();
            this.onEnable();
        } else {
            new EventModStateChange(this, false).fire();
            this.onDisable();
            if (!this.enabled) {
                EventBus.getInstance().unregisterListener(this);
            }
        }
        this.syncSubModuleStates(enabled, bypassVisibilityCheck);
        this.syncHudModuleFrame(enabled);
        if (stateChanged && (this.category != Category.NONE || this instanceof HudModule)) {
            Vape.INSTANCE.saveAndStop();
        }
    }

    private void syncHudModuleFrame(boolean enabled) {
        if (!(this instanceof HudModule)) {
            return;
        }
        Class<? extends Frame> frameClass = ((HudModule)this).getConfigFrameClass();
        if (frameClass == null) {
            return;
        }
        Frame frame = ClientSettings.getFrame(frameClass);
        if (frame != null) {
            frame.setVisible(enabled);
            frame.c(enabled);
            if (enabled && frame instanceof HudModuleFrameBase) {
                ((HudModuleFrameBase)frame).clampToVisibleScreen();
            }
        } else {
            Vape.debugLog("HUD frame not registered: " + frameClass.getSimpleName());
        }
    }

    private static Exception passThrough(Exception exception) {
        return exception;
    }

    public void onFinishModuleInitialization() {
    }

    public String getToolTip() {
        return this.tooltip;
    }

    public void p(SubModuleValue subModuleValue, SubModuleValue subModuleValue2) {
        if (this instanceof SubModule) {
            return;
        }
        if (!this.enabled) {
            return;
        }
        Object t = subModuleValue.getInstance();
        Object t2 = subModuleValue2.getInstance();
        if (((SubModule)t).isParentEnabled()) {
            ((Mod)t).setEnabled(false);
        }
        if (!((SubModule)t2).isParentEnabled()) {
            ((Mod)t2).setEnabled(true);
        }
    }

    public boolean q$src$Z$12h8h4c() {
        return this.getCategory() != Category.NONE && !(this instanceof UtilityMod);
    }

    public void K(boolean bl) {
        if (bl) {
            Vape.INSTANCE.getModuleProfileMetadataCodec().addModule(this);
        } else {
            Vape.INSTANCE.getModuleProfileMetadataCodec().removeModule(this);
        }
    }

    public Mod(String string, int n, Category category) {
        this(string, 0, n, category, null);
    }

    public void u(EventKeyPress eventKeyPress) {
        if (eventKeyPress.getKey() <= 0) {
            return;
        }
        if (this.bind.getBoundInputs().isEmpty()) {
            return;
        }
        if (Minecraft.currentScreen().getObject() == null && this.bind.handleInput(eventKeyPress.getKey(), eventKeyPress.isDown())) {
            eventKeyPress.setCancelled(true);
        }
    }

    public void setEnabled(boolean bl) {
        this.setEnabled(bl, false);
    }

    public Mod(String string, int n, Category category, String string2) {
        this(string, 0, n, category, string2);
    }

    public void onBeforeDisable() {
    }

    @Override
    public String getName() {
        return this.name;
    }

    public boolean isRequiresBind() {
        return this.requiresBind;
    }

    public boolean isVisible() {
        return this.visible;
    }

    public String toString() {
        return "Module{name='" + this.name + '\'' + ", defaultKeybind=" + this.defaultKeybind + ", guiColor=" + this.guiColor + ", values=" + this.values + ", subModules=" + this.subModules + ", guiCategory=" + this.category + ", enabled=" + this.enabled + ", requiresBind=" + this.requiresBind + ", tooltip='" + this.tooltip + '\'' + ", moduleRunnable=" + this.moduleRunnable + ", defaultVisible=" + this.defaultVisible + ", visible=" + this.visible + ", favorited=" + this.favorited + '}';
    }

    public List<SubModule> getSubModules() {
        return this.subModules;
    }

    public void U(Value<?, ?> value, MinecraftVersionConstraint ... minecraftVersionConstraintArray) {
        this.allValues.add(value);
        List<MinecraftVersionConstraint> list = MinecraftVersionConstraint.L(minecraftVersionConstraintArray);
        if (!list.isEmpty()) {
            return;
        }
        this.values.add(value);
    }

    protected Predicate<IEvent> w() {
        return this::matchesEnabled;
    }

    public boolean isDefaultVisible() {
        return this.defaultVisible;
    }

    public void onScheduledAction() {
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public static String getStaticName() {
        return staticName;
    }

    public boolean k() {
        return this.enabled;
    }

    public void X(MinecraftVersionConstraint minecraftVersionConstraint, Value<?, ?> ... valueArray) {
        for (Value<?, ?> value : valueArray) {
            this.U(value, minecraftVersionConstraint);
        }
    }

    public void U(Mod mod) {
    }

    public String f() {
        if (!this.valueDisplayDescriptors.isEmpty()) {
            return ModuleValueDisplayFormatter.formatDescriptorSummary(this.valueDisplayDescriptors);
        }
        if (this.getValues().isEmpty()) {
            return "";
        }
        return ModuleValueDisplayFormatter.formatValueSummary(this.getValues());
    }

    public List<Value<?, ?>> getAllValues() {
        return this.allValues;
    }

    public void setModuleDisplayScope(ModuleDisplayScope moduleDisplayScope) {
        this.displayScope = moduleDisplayScope;
    }

    public Value getValue(String string) {
        for (Value<?, ?> value : this.getAllValues()) {
            if (!value.getId().equalsIgnoreCase(string) && !value.getName().equalsIgnoreCase(string)) continue;
            return value;
        }
        return null;
    }

    public int getGuiColor() {
        return this.guiColor;
    }

    public void addValue(Value<?, ?> ... valueArray) {
        for (Value<?, ?> value : valueArray) {
            this.U(value, new MinecraftVersionConstraint[0]);
        }
    }

    public void j() {
        if (!this.developmentWarningShown && this.Q()) {
            this.developmentWarningShown = true;
            Vape.INSTANCE.getNotificationManager().show("Module in development", this.getName() + " " + NotificationText.localize("is in development\n\nUse with caution and report issues to support"), NotificationType.WARNING, 10000L);
        }
    }

    static {
        Mod.A("r6YMSc");
    }

    public Bendable getBind() {
        return this.bind;
    }

    public void I() {
    }

    private boolean matchesEnabled(IEvent iEvent) {
        return this.isEnabled();
    }

    public boolean isFavorite() {
        return this.favorited;
    }

    public int getDefaultKeybind() {
        return this.defaultKeybind;
    }

    public List<ValueDisplayDescriptor> getValueDisplayDescriptors() {
        return this.valueDisplayDescriptors;
    }

    public boolean isBlatantMod() {
        return false;
    }

    public static void A(String string) {
        staticName = string;
    }

    public void onDisable() {
    }

    public List<Value<?, ?>> getValues() {
        return this.values;
    }

    public List<ClickGuiModuleCardRenderState> S() {
        if (!this.valueDisplayDescriptors.isEmpty()) {
            return ModuleValueDisplayFormatter.buildDescriptorRenderStates(this.valueDisplayDescriptors);
        }
        if (this.getValues().isEmpty()) {
            return Collections.emptyList();
        }
        return ModuleValueDisplayFormatter.buildValueRenderStates(this.getValues());
    }

    public Mod(String string) {
        this(string, 0, Category.NONE);
    }

    public Mod(String string, int n, int n2, Category category, String string2) {
        this.values = new ArrayList();
        this.subModules = new ArrayList<SubModule>();
        this.valueDisplayDescriptors = new ArrayList<ValueDisplayDescriptor>();
        this.displayScope = ModuleDisplayScope.ALL;
        this.toggleNotification = new ReusableTextNotification(NotificationType.INFO, "", "", 1000L);
        this.name = string;
        this.defaultKeybind = n;
        this.guiColor = n2;
        this.category = category;
        this.tooltip = string2;
        this.bind = this.C$src$Lgg_vape_unmap_Bendable_$1we4j6l();
        if (this.bind.usesOwnKeybindStorage() && n != 0) {
            this.bind.getBoundInputs().add(n);
        }
    }

    public boolean t$src$Z$14g275z() {
        return this.getProperty(PropertyContainer.BETA_BADGE);
    }

    public void P(Value<?, ?> value, MinecraftVersionConstraint ... minecraftVersionConstraintArray) {
        this.allValues.add(0, value);
        List<MinecraftVersionConstraint> list = MinecraftVersionConstraint.L(minecraftVersionConstraintArray);
        if (!list.isEmpty()) {
            return;
        }
        this.values.add(0, value);
    }

    protected Bendable C$src$Lgg_vape_unmap_Bendable_$1we4j6l() {
        return new ModBendable(this);
    }

    public boolean boolean_r() {
        return this.isEnabled();
    }
}
