package gg.vape.ui.click.frame.impl.profile;

import gg.vape.config.ProfileModuleSnapshot;
import gg.vape.config.ProfileSnapshot;
import gg.vape.ui.click.component.FlowLayoutComponent;
import gg.vape.ui.click.component.GuiComponent;
import gg.vape.ui.click.component.gui.InteractiveComponent;
import gg.vape.ui.click.component.input.BindableInputComponent;
import gg.vape.ui.click.component.value.BooleanToggleComponent;
import gg.vape.ui.click.component.value.ValueComponentFactory;
import gg.vape.value.BooleanValue;
import gg.vape.value.ConditionalValue;
import gg.vape.value.ListValue;
import gg.vape.value.ModeValue;
import gg.vape.value.Value;
import gg.vape.value.ValueSnapshot;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProfileSnapshotGuiBuilder {
    private static final String RESET_ICON = "reset_circle";
    private static final int SETTING_ROW_WIDTH = 210;
    private final HashMap<ProfileModuleSnapshot, List<GuiComponent>> componentsByModule;
    private final ProfileSnapshot snapshot;
    private final HashMap<Value<?, ?>, ValueSnapshot<?, ?>> snapshotByProxyValue;
    private final HashMap<Value<?, ?>, Value<?, ?>> proxyBySourceValue;
    private final Color resetButtonColor = new Color(54, 53, 54, 128);
    private final HashMap<Value<?, ?>, GuiComponent> componentByProxyValue;

    public ProfileSnapshotGuiBuilder(ProfileSnapshot profileSnapshot) {
        this.proxyBySourceValue = new HashMap<>();
        this.snapshotByProxyValue = new HashMap<>();
        this.componentByProxyValue = new HashMap<>();
        this.componentsByModule = new HashMap<>();
        this.snapshot = profileSnapshot;
        this.buildComponents();
    }

    public void resetValue(Value<?, ?> sourceValue) {
        Value<?, ?> proxyValue = this.proxyBySourceValue.get(sourceValue);
        if (proxyValue == null) {
            return;
        }
        ValueSnapshot<?, ?> valueSnapshot = this.snapshotByProxyValue.get(proxyValue);
        if (valueSnapshot == null) {
            return;
        }
        // Proxy setters already target the snapshot. Do not replace its
        // mutable binding/list after resetting: editor controls may hold it.
        proxyValue.reset();
        proxyValue.notifyChanged();
        if (sourceValue instanceof ConditionalValue) {
            ConditionalValue conditionalValue = (ConditionalValue)sourceValue;
            List<Value> list = conditionalValue.getDependentValues();
            for (Value childValue : list) {
                Value<?, ?> childProxyValue = this.proxyBySourceValue.get(childValue);
                GuiComponent guiComponent = this.componentByProxyValue.get(childProxyValue);
                if (guiComponent != null) continue;
                this.resetValue(childValue);
            }
        }
    }

    public List<GuiComponent> getModuleComponents(ProfileModuleSnapshot moduleSnapshot) {
        return this.componentsByModule.get(moduleSnapshot);
    }

    private void buildComponents() {
        // Build every proxy before wiring dependencies: mode settings often
        // appear before their dependent settings in a module's value list.
        for (ProfileModuleSnapshot module : this.snapshot.getAllModules()) {
            this.componentsByModule.put(module, new ArrayList<GuiComponent>());
            for (ValueSnapshot<?, ?> valueSnapshot : module.getValueSnapshots()) {
                Value<?, ?> proxy = ValueComponentFactory.createSnapshotProxyValue(valueSnapshot);
                if (proxy == null) continue;
                this.proxyBySourceValue.put(valueSnapshot.getSourceValue(), proxy);
                this.snapshotByProxyValue.put(proxy, valueSnapshot);
            }
        }
        for (Map.Entry<Value<?, ?>, Value<?, ?>> entry : this.proxyBySourceValue.entrySet()) {
            if (entry.getKey() instanceof ModeValue && entry.getValue() instanceof ModeValue) {
                ((ModeValue)entry.getKey()).copyModeDependenciesTo((ModeValue)entry.getValue(), this.proxyBySourceValue);
            }
        }
        for (Map.Entry<Value<?, ?>, Value<?, ?>> entry : this.proxyBySourceValue.entrySet()) {
            Value<?, ?> source = entry.getKey();
            Value<?, ?> proxy = entry.getValue();
            Value<?, ?> parent = this.proxyBySourceValue.get(source.getParent());
            if (parent instanceof ConditionalValue) {
                ConditionalValue conditional = (ConditionalValue)parent;
                if (!conditional.getDependentValues().contains(proxy)) conditional.addDependentValues(proxy);
            }
            if (source instanceof ListValue && source.getParent() instanceof BooleanValue && parent instanceof BooleanValue
                    && ((BooleanValue)source.getParent()).getTerminalDependentValue() == source) {
                ((BooleanValue)parent).setCompactListValue((ListValue)proxy);
            }
        }
        for (Map.Entry<Value<?, ?>, Value<?, ?>> entry : this.proxyBySourceValue.entrySet()) {
            entry.getKey().copyConditionsTo(entry.getValue(), this.proxyBySourceValue);
        }
        for (ProfileModuleSnapshot module : this.snapshot.getAllModules()) {
            List<GuiComponent> rows = this.componentsByModule.get(module);
            for (ValueSnapshot<?, ?> valueSnapshot : module.getValueSnapshots()) {
                Value<?, ?> proxy = this.proxyBySourceValue.get(valueSnapshot.getSourceValue());
                if (proxy == null) continue;
                final GuiComponent valueComponent = ValueComponentFactory.createMainValueComponent(proxy, true);
                if (valueComponent == null) continue;
                if (valueComponent instanceof BooleanToggleComponent) {
                    BooleanToggleComponent toggle = (BooleanToggleComponent)valueComponent;
                    if (toggle.getCompactListComponent() != null) toggle.getCompactListComponent().setUseAnchoredPopup(true);
                }
                valueComponent.setUseExplicitWidth(true);
                valueComponent.setExplicitWidth(182.0);
                valueComponent.setHorizontalInset(0.0);
                FlowLayoutComponent row = new FlowLayoutComponent(SETTING_ROW_WIDTH) {
                    @Override public boolean V$src$Z$1xhop3l() {
                        return super.V$src$Z$1xhop3l() && valueComponent.V$src$Z$1xhop3l();
                    }
                };
                row.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().Q(true);
                ProfileSnapshotValueResetButtonComponent reset = new ProfileSnapshotValueResetButtonComponent(RESET_ICON, this.resetButtonColor, 0.75, 11.0, 11.0, valueComponent, valueSnapshot);
                reset.setVisible(!proxy.isDefault());
                ((InteractiveComponent)reset).setClickListener(() -> {
                    BindableInputComponent.cancelCapturesInTree(valueComponent);
                    this.resetValue(valueSnapshot.getSourceValue());
                });
                row.h(valueComponent, new Object[0]);
                row.h(reset, new Object[0]);
                rows.add(row);
                this.componentByProxyValue.put(proxy, valueComponent);
            }
        }
    }

    public void resetModule(ProfileModuleSnapshot moduleSnapshot) {
        for (ValueSnapshot<?, ?> valueSnapshot : moduleSnapshot.getValueSnapshots()) {
            Value<?, ?> source = valueSnapshot.getSourceValue();
            if (this.proxyBySourceValue.containsKey(source)) this.resetValue(source);
            else valueSnapshot.resetToDefault();
        }
        moduleSnapshot.resetBind();
        moduleSnapshot.setEnabled(false);
    }

    public void resetAllModules() {
        for (ProfileModuleSnapshot moduleSnapshot : this.snapshot.getAllModules()) {
            this.resetModule(moduleSnapshot);
        }
    }

}
