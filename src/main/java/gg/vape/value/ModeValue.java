package gg.vape.value;

import com.google.gson.JsonObject;
import gg.vape.module.Mod;
import gg.vape.module.SubModule;
import gg.vape.ui.click.component.GuiComponent;
import gg.vape.unmap.ModeOption;
import gg.vape.unmap.ModeSelection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ModeValue
extends ConditionalValue<ModeSelection, ModeValue> {
    private static GuiComponent[] legacyGuiState;
    private static final String JSON_VALUE_PROPERTY;
    private final Map<Value<?, ?>, ArrayList<ModeSelection>> activeModesByDependentValue = new HashMap();
    private final String displayName;
    private final ModeSelection[] modes;

    public static void setLegacyGuiState(GuiComponent[] state) {
        legacyGuiState = state;
    }

    public static ModeValue create(Object owner, String name, ModeSelection defaultMode, ModeSelection ... modes) {
        return ModeValue.create(owner, name, name, defaultMode, modes);
    }

    public static ModeValue createConfigured(Object owner, String name, String displayName, String description, ModeSelection defaultMode, int legacyIndex, ModeSelection[] modes) {
        ModeValue modeValue = new ModeValue(owner, name, displayName, defaultMode, modes);
        modeValue.setDescription(description);
        for (ModeSelection mode : modes) {
            mode.attachToMode(modeValue);
        }
        return modeValue;
    }

    @Override
    public String getDisplayValue() {
        return ((ModeSelection)this.getValue()).toString();
    }

    public int getSelectedIndex() {
        for (int index = 0; index < this.getModes().length; ++index) {
            if (!((ModeSelection)this.getValue()).equals(this.getModes()[index])) continue;
            return index;
        }
        return 0;
    }

    public static GuiComponent[] getLegacyGuiState() {
        return legacyGuiState;
    }

    @Override
    public boolean hasActiveDependentBranch() {
        return true;
    }

    public static ModeValue createWithDescriptionAndLegacyIndex(Object owner, String name, String description, ModeSelection defaultMode, int legacyIndex, ModeSelection ... modes) {
        return ModeValue.createConfigured(owner, name, name, description, defaultMode, legacyIndex, modes);
    }

    public void addActiveMode(Value dependentValue, ModeOption modeOption) {
        this.addDependentValues(dependentValue);
        if (!this.activeModesByDependentValue.containsKey(dependentValue)) {
            this.activeModesByDependentValue.put(dependentValue, new ArrayList());
        }
        ArrayList<ModeSelection> activeModes = this.activeModesByDependentValue.get(dependentValue);
        activeModes.add(modeOption);
    }

    static {
        ModeValue.setLegacyGuiState(null);
        JSON_VALUE_PROPERTY = "value";
    }

    public ModeValue copyDefinition() {
        return new ModeValue(null, this.getId(), this.getName(), this.getDefaultValue(), this.getModes());
    }

    @Override
    public ModeValue copyValueDefinition() {
        return this.copyDefinition();
    }

    @Override
    public String getName() {
        return this.displayName;
    }

    public void setValue(ModeSelection newMode) {
        if (((ModeSelection)this.getValue()).equals(newMode)) {
            return;
        }
        if (this.getOwner() != null && newMode instanceof SubModuleValue && this.getValue() instanceof SubModuleValue) {
            this.switchSubModule((SubModuleValue)this.getValue(), (SubModuleValue)newMode);
        }
        super.setValue(newMode);
    }

    public ModeSelection[] getModes() {
        return this.modes;
    }

    @Override
    public JsonObject toJson(boolean includeValue) {
        JsonObject jsonObject = this.toJson();
        if (this.getValue() != null) {
            jsonObject.addProperty(JSON_VALUE_PROPERTY, ((ModeSelection)this.getValue()).getSerializedName());
        }
        return jsonObject;
    }

    @Override
    public boolean isDependentValueActive(Value dependentValue) {
        if (this.activeModesByDependentValue.containsKey(dependentValue)) {
            ArrayList<ModeSelection> activeModes = this.activeModesByDependentValue.get(dependentValue);
            return activeModes.contains(this.getValue());
        }
        return false;
    }

    private void switchSubModule(SubModuleValue previousMode, SubModuleValue nextMode) {
        if (this.isPersistenceSuppressed()) {
            return;
        }
        Object parent = ((SubModule)previousMode.getInstance()).getParent();
        ((Mod)parent).p(previousMode, nextMode);
    }

    public static ModeValue create(Object owner, String name, String description, ModeSelection defaultMode, ModeSelection ... modes) {
        return ModeValue.createConfigured(owner, name, name, description, defaultMode, 1, modes);
    }

    public static ModeValue create(Object owner, String name, String displayName, String description, ModeSelection defaultMode, ModeSelection ... modes) {
        return ModeValue.createConfigured(owner, name, displayName, description, defaultMode, 1, modes);
    }

    public void setSelectedIndex(int index) {
        this.setValue(this.getModes()[index]);
    }

    public ModeValue(Object owner, String name, String displayName, ModeSelection defaultMode, ModeSelection[] modes) {
        super(owner, name, defaultMode);
        this.displayName = displayName;
        this.modes = modes;
        if (owner instanceof Mod) {
            Mod mod = (Mod)owner;
            for (ModeSelection mode : modes) {
                if (!(mode instanceof SubModuleValue)) continue;
                SubModuleValue subModuleMode = (SubModuleValue)mode;
                for (Value<?, ?> dependentValue : ((Mod)subModuleMode.getInstance()).getAllValues()) {
                    mod.addValue(dependentValue);
                    this.addActiveMode(dependentValue, subModuleMode);
                }
            }
        }
    }

    @Override
    public void parse(String serializedMode) {
        // The definition's options are authoritative. Looking them up through
        // a global registry tied to the currently selected option leaked
        // short-lived embedded modes and could resolve a different owner.
        for (ModeSelection mode : this.modes) {
            if (mode.getSerializedName().equalsIgnoreCase(serializedMode)) {
                this.setValue(mode);
                return;
            }
        }
    }

    /** Transfer mode-dependent visibility to snapshot proxies, retaining the
     * original immutable options without reassigning their live ownership. */
    public void copyModeDependenciesTo(ModeValue target, Map<Value<?, ?>, Value<?, ?>> proxies) {
        for (Map.Entry<Value<?, ?>, ArrayList<ModeSelection>> entry : this.activeModesByDependentValue.entrySet()) {
            Value<?, ?> dependent = proxies.get(entry.getKey());
            if (dependent == null) continue;
            for (ModeSelection selection : entry.getValue()) {
                if (selection instanceof ModeOption) target.addActiveMode(dependent, (ModeOption)selection);
            }
        }
    }

    public void addModeDependentValues(ModeOption modeOption, Value ... values) {
        this.addDependentValues(values);
        for (Value dependentValue : values) {
            this.addActiveMode(dependentValue, modeOption);
        }
    }

}
