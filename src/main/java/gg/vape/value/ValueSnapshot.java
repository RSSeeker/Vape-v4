package gg.vape.value;

import com.google.gson.JsonObject;
import gg.vape.config.ConfigJsonUtils;
import gg.vape.input.BindActivationMode;
import gg.vape.input.BindSet;
import gg.vape.module.utility.inventory.HotbarSlotRuleValue;
import gg.vape.module.utility.inventory.cleaner.InventoryCleanerProfile;
import gg.vape.module.utility.inventory.cleaner.InventoryCleanerProfileValue;
import gg.vape.unmap.ItemLimitData;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ValueSnapshot<T extends Value<R, ?>, R> {
    private final T sourceValue;
    private final InventoryCleanerProfileValue detachedInventory;
    private final HotbarSlotRuleValue detachedHotbar;
    private JsonObject opaqueData;
    private R value;

    private R copySnapshotValue(Object object) {
        if (object instanceof Object[]) return (R)Arrays.copyOf((Object[])object, ((Object[])object).length);
        if (object instanceof double[]) return (R)Arrays.copyOf((double[])object, ((double[])object).length);
        if (object instanceof Map) return (R)new LinkedHashMap<>((Map<?, ?>)object);
        if (object instanceof BindSet) {
            BindSet original = (BindSet)object;
            BindSet copy = new BindSet(new ArrayList<Integer>(original.getDefaultKeys()), false, original.supportsActivationMode());
            copy.setBoundInputs(new ArrayList<Integer>(original.getBoundInputs()));
            copy.setActivationMode(original.getActivationMode());
            return (R)copy;
        }
        if (object instanceof List) {
            List<Object> copy = new ArrayList<Object>();
            for (Object entry : (List<?>)object) {
                if (entry instanceof OptionalLimitEntry) {
                    OptionalLimitEntry original = (OptionalLimitEntry)entry;
                    OptionalLimitEntry duplicate = new OptionalLimitEntry(original.getValue());
                    duplicate.setEnabled(original.isEnabled());
                    copy.add(duplicate);
                } else if (entry instanceof ItemLimitData) {
                    ItemLimitData original = (ItemLimitData)entry;
                    ItemLimitData duplicate = new ItemLimitData(original.getName(), original.getTotalStacks()).withMetadata(original.getMetadata());
                    duplicate.setEnabled(original.isEnabled());
                    copy.add(duplicate);
                } else {
                    copy.add(entry);
                }
            }
            return (R)copy;
        }
        return (R)object;
    }

    private R copyDefaultValue() {
        R value = this.copySnapshotValue(this.sourceValue.getDefaultValue());
        if (value instanceof BindSet) {
            BindSet binding = (BindSet)value;
            binding.setBoundInputs(new ArrayList<Integer>(binding.getDefaultKeys()));
            binding.setActivationMode(BindActivationMode.TOGGLE);
        }
        return value;
    }

    /** Build an independent codec so preview loading and serialization do not
     * briefly change the running module or invoke its change listeners. */
    private Value detachedValue(R initialValue) {
        Value detached = this.sourceValue.copyValueDefinition();
        detached.setPersistenceSuppressed(true);
        detached.setBase64Encoded(this.sourceValue.isBase64Encoded());
        detached.setDefaultValue(this.copyDefaultValue());
        R copied = this.copySnapshotValue(initialValue);
        if (detached instanceof ColorValue && copied instanceof Object[]) {
            ((ColorValue)detached).setComponentValues((Object[])copied);
        } else if (detached instanceof EntityTargetFilterValue && copied instanceof Boolean[]) {
            ((EntityTargetFilterValue)detached).setFilterValues((Boolean[])copied);
        } else if (detached instanceof ListValue) {
            List entries = (List)detached.getValue();
            entries.clear();
            entries.addAll((List)copied);
        } else if (detached instanceof NumberValue) {
            ((NumberValue)detached).setRawValue((Double)copied);
        } else {
            detached.setValue(copied);
        }
        return detached;
    }

    private boolean hasEquivalentCodec(Value detached) {
        // A subclass can override serialization while inheriting a base copy
        // method. Such values must remain opaque rather than borrowing the
        // live object as a temporary codec and firing its listeners.
        return detached.getClass() == this.sourceValue.getClass();
    }

    public void setValue(Object object) {
        if (this.detachedInventory != null) {
            this.detachedInventory.setValue((InventoryCleanerProfile)object);
        } else {
            this.value = this.copySnapshotValue(object);
        }
    }

    public R getValue() {
        return this.detachedInventory != null ? (R)this.detachedInventory.getValue() : this.value;
    }

    /** Reset even settings which have no profile-preview editor. */
    public void resetToDefault() {
        if (!this.sourceValue.isResettable()) return;
        if (this.detachedInventory != null) this.detachedInventory.reset();
        if (this.detachedHotbar != null) this.detachedHotbar.reset();
        this.opaqueData = null;
        if (this.sourceValue.getDefaultValue() != null) this.value = this.copyDefaultValue();
    }

    public boolean isDefault() {
        if (this.opaqueData != null) return false;
        if (this.detachedInventory != null) return this.detachedInventory.isDefault();
        if (this.detachedHotbar != null) return this.detachedHotbar.isDefault();
        R defaultValue = this.sourceValue.getDefaultValue();
        if (defaultValue instanceof Object[]) {
            return Arrays.equals((Object[])defaultValue, (Object[])this.value);
        }
        if (defaultValue instanceof double[]) {
            return Arrays.equals((double[])defaultValue, (double[])this.value);
        }
        if (defaultValue instanceof BindSet && this.value instanceof BindSet) {
            BindSet original = (BindSet)defaultValue, current = (BindSet)this.value;
            return original.getDefaultKeys().equals(current.getBoundInputs())
                    && current.getActivationMode() == BindActivationMode.TOGGLE;
        }
        if (defaultValue instanceof List && this.value instanceof List) {
            List<?> defaults = (List<?>)defaultValue, current = (List<?>)this.value;
            if (defaults.size() != current.size()) return false;
            for (int i = 0; i < defaults.size(); ++i) {
                Object a = defaults.get(i), b = current.get(i);
                if (a instanceof OptionalLimitEntry && b instanceof OptionalLimitEntry) {
                    if (!((OptionalLimitEntry)a).toJson().equals(((OptionalLimitEntry)b).toJson())) return false;
                } else if (a instanceof ItemLimitData && b instanceof ItemLimitData) {
                    if (!((ItemLimitData)a).toJson().equals(((ItemLimitData)b).toJson())) return false;
                } else if (!Objects.equals(a, b)) return false;
            }
            return true;
        }
        return Objects.equals(defaultValue, this.value);
    }

    public T getSourceValue() {
        return this.sourceValue;
    }

    /** Create an editor bound to this snapshot rather than the live value. */
    public Value<?, ?> createProxyValue() {
        if (this.detachedInventory != null) return this.detachedInventory;
        if (this.detachedHotbar != null) return this.detachedHotbar;
        Value proxy = this.sourceValue.copyValueDefinition();
        if (!this.hasEquivalentCodec(proxy)) return null;
        // The editor is detached from the active profile. Its writes should
        // only update the snapshot, not schedule live configuration saves.
        proxy.setPersistenceSuppressed(true);
        proxy.setResettable(this.sourceValue.isResettable());
        proxy.setHidden(this.sourceValue.isHidden());
        proxy.setSerializable(this.sourceValue.isSerializable());
        proxy.setBase64Encoded(this.sourceValue.isBase64Encoded());
        proxy.setDescription(this.sourceValue.getDescription());
        proxy.setOverrideColor(this.sourceValue.getOverrideColor());
        proxy.setDefaultValue(this.copyDefaultValue());
        proxy.setAccessor(new SnapshotValueAccessor(this, proxy));
        if (proxy instanceof ColorValue && this.value instanceof Object[]) {
            ((ColorValue)proxy).setComponentValues((Object[])this.value);
        } else if (proxy instanceof EntityTargetFilterValue && this.value instanceof Boolean[]) {
            ((EntityTargetFilterValue)proxy).setFilterValues((Boolean[])this.value);
        }
        // The accessor already exposes the snapshot. Calling ordinary setters
        // here can replace a binding held by the editor, clear a list, or round
        // a saved numeric value before the user changes it.
        return proxy;
    }

    /** Composite values serialize their child settings rather than just the backing array. */
    private R captureValue(Value candidate) {
        if (candidate instanceof ColorValue) return (R)((ColorValue)candidate).getComponentValuesSnapshot();
        if (candidate instanceof EntityTargetFilterValue) return (R)((EntityTargetFilterValue)candidate).getFilterValueSnapshot();
        return (R)candidate.getValueCompat();
    }

    private R captureSourceValue() {
        return this.captureValue(this.sourceValue);
    }

    public void loadJson(JsonObject jsonObject) {
        if (this.detachedInventory != null) {
            if (this.sourceValue.matchesJsonId(jsonObject)) {
                JsonObject normalized = ConfigJsonUtils.deepCopy(jsonObject);
                normalized.addProperty("id", this.detachedInventory.getId());
                this.detachedInventory.loadJson(normalized);
            }
            return;
        }
        if (this.detachedHotbar != null) {
            if (this.sourceValue.matchesJsonId(jsonObject)) {
                JsonObject normalized = ConfigJsonUtils.deepCopy(jsonObject);
                normalized.addProperty("id", this.detachedHotbar.getId());
                this.detachedHotbar.loadJson(normalized);
            }
            return;
        }
        if (!this.sourceValue.matchesJsonId(jsonObject)) return;
        R initial = this.sourceValue.isResettable() ? this.copyDefaultValue() : this.captureSourceValue();
        Value detached = this.detachedValue(initial);
        if (this.hasEquivalentCodec(detached)) {
            JsonObject normalized = ConfigJsonUtils.deepCopy(jsonObject);
            normalized.addProperty("id", detached.getId());
            if (detached.loadJson(normalized)) this.value = this.copySnapshotValue(this.captureValue(detached));
            return;
        }
        // Unknown subclass codecs cannot safely be reconstructed. Preserve
        // their original payload unchanged and offer no misleading editor.
        this.opaqueData = ConfigJsonUtils.deepCopy(jsonObject);
    }

    public ValueSnapshot(T sourceValue) {
        this.sourceValue = sourceValue;
        // Profile switching leaves non-resettable and null-default settings as
        // they are. Other omitted settings begin at the target's defaults.
        this.value = sourceValue.isResettable() && sourceValue.getDefaultValue() != null
                ? this.copyDefaultValue() : this.copySnapshotValue(this.captureSourceValue());
        if (sourceValue instanceof InventoryCleanerProfileValue) {
            this.detachedInventory = ((InventoryCleanerProfileValue)sourceValue).copyValueDefinition();
            this.detachedInventory.setPersistenceSuppressed(true);
            this.detachedInventory.setResettable(sourceValue.isResettable());
            this.detachedInventory.setHidden(sourceValue.isHidden());
            this.detachedInventory.setSerializable(sourceValue.isSerializable());
            this.detachedInventory.setDescription(sourceValue.getDescription());
            this.detachedInventory.setOverrideColor(sourceValue.getOverrideColor());
            if (!sourceValue.isResettable()) this.detachedInventory.loadJson(sourceValue.toJson(false));
        } else {
            this.detachedInventory = null;
        }
        if (sourceValue instanceof HotbarSlotRuleValue) {
            this.detachedHotbar = ((HotbarSlotRuleValue)sourceValue).copyValueDefinition();
            this.detachedHotbar.setPersistenceSuppressed(true);
            this.detachedHotbar.setResettable(sourceValue.isResettable());
            this.detachedHotbar.setHidden(sourceValue.isHidden());
            this.detachedHotbar.setSerializable(sourceValue.isSerializable());
            this.detachedHotbar.setDescription(sourceValue.getDescription());
            this.detachedHotbar.setOverrideColor(sourceValue.getOverrideColor());
            if (!sourceValue.isResettable()) this.detachedHotbar.loadJson(sourceValue.toJson(false));
        } else {
            this.detachedHotbar = null;
        }
    }

    public JsonObject toJson() {
        return this.toJson(false);
    }

    public JsonObject toJson(boolean forPublication) {
        if (this.opaqueData != null) return ConfigJsonUtils.deepCopy(this.opaqueData);
        if (this.detachedInventory != null) return this.detachedInventory.toJson(forPublication);
        if (this.detachedHotbar != null) return this.detachedHotbar.toJson(forPublication);
        Value detached = this.detachedValue(this.getValue());
        if (this.hasEquivalentCodec(detached)) {
            JsonObject json = detached.toJson(forPublication);
            json.addProperty("id", this.sourceValue.getId());
            return json;
        }
        JsonObject empty = new JsonObject();
        empty.addProperty("id", this.sourceValue.getId());
        return empty;
    }
}
