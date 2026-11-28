package gg.vape.value;

import com.google.gson.JsonObject;
import gg.vape.value.Value;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class ValueSnapshot<T extends Value<R, ?>, R> {
    private final T sourceValue;
    private R value;

    private R copySnapshotValue(Object object) {
        if (object instanceof Object[]) return (R)Arrays.copyOf((Object[])object, ((Object[])object).length);
        if (object instanceof double[]) return (R)Arrays.copyOf((double[])object, ((double[])object).length);
        if (object instanceof Map) return (R)new LinkedHashMap<>((Map<?, ?>)object);
        return (R)object;
    }

    public void setValue(Object object) {
        this.value = this.copySnapshotValue(object);
    }

    public R getValue() {
        return this.value;
    }

    public boolean isDefault() {
        R defaultValue = this.sourceValue.getDefaultValue();
        if (defaultValue instanceof Object[]) {
            return Arrays.equals((Object[])defaultValue, (Object[])this.value);
        }
        if (defaultValue instanceof double[]) {
            return Arrays.equals((double[])defaultValue, (double[])this.value);
        }
        return Objects.equals(defaultValue, this.value);
    }

    public T getSourceValue() {
        return this.sourceValue;
    }

    /** Create an editor bound to this snapshot rather than the live value. */
    public Value<?, ?> createProxyValue() {
        Value proxy = this.sourceValue.copyValueDefinition();
        boolean wasSuppressed = proxy.isPersistenceSuppressed();
        proxy.setPersistenceSuppressed(true);
        try {
            proxy.setDefaultValue(this.sourceValue.getDefaultValue());
            proxy.setAccessor(new SnapshotValueAccessor(this, proxy));
            if (proxy instanceof ColorValue && this.value instanceof Object[]) {
                ((ColorValue)proxy).setComponentValues((Object[])this.value);
            } else if (proxy instanceof EntityTargetFilterValue && this.value instanceof Boolean[]) {
                ((EntityTargetFilterValue)proxy).setFilterValues((Boolean[])this.value);
            } else {
                proxy.setValue(this.value);
            }
            return proxy;
        } finally {
            proxy.setPersistenceSuppressed(wasSuppressed);
        }
    }

    /** Composite values serialize their child settings rather than just the backing array. */
    private R captureSourceValue() {
        if (this.sourceValue instanceof ColorValue) return (R)((ColorValue)this.sourceValue).getComponentValuesSnapshot();
        if (this.sourceValue instanceof EntityTargetFilterValue) return (R)((EntityTargetFilterValue)this.sourceValue).getFilterValueSnapshot();
        return this.sourceValue.getValueCompat();
    }

    private void applySourceValue(R value) {
        if (this.sourceValue instanceof ColorValue && value instanceof Object[]) {
            ((ColorValue)this.sourceValue).setComponentValues((Object[])value);
        } else if (this.sourceValue instanceof EntityTargetFilterValue && value instanceof Boolean[]) {
            ((EntityTargetFilterValue)this.sourceValue).setFilterValues((Boolean[])value);
        } else {
            this.sourceValue.setValue(value);
        }
    }

    public void loadJson(JsonObject jsonObject) {
        boolean wasSuppressed = this.sourceValue.isPersistenceSuppressed();
        R originalValue = this.captureSourceValue();
        this.sourceValue.setPersistenceSuppressed(true);
        try {
            // Map values load in place. Isolate even a non-resettable map so
            // reading a profile preview cannot mutate the live module.
            if (originalValue instanceof Map) this.applySourceValue(this.copySnapshotValue(originalValue));
            this.sourceValue.reset();
            if (this.sourceValue.loadJson(jsonObject)) {
                this.value = this.copySnapshotValue(this.captureSourceValue());
            }
        } finally {
            try {
                this.applySourceValue(originalValue);
            } finally {
                this.sourceValue.setPersistenceSuppressed(wasSuppressed);
            }
        }
    }

    public ValueSnapshot(T sourceValue) {
        this.sourceValue = sourceValue;
        this.value = this.copySnapshotValue(sourceValue.getDefaultValue());
    }

    public JsonObject toJson() {
        boolean wasSuppressed = this.sourceValue.isPersistenceSuppressed();
        R originalValue = this.captureSourceValue();
        this.sourceValue.setPersistenceSuppressed(true);
        try {
            this.applySourceValue(this.getValue());
            return this.sourceValue.toJson(false);
        } finally {
            try {
                this.applySourceValue(originalValue);
            } finally {
                this.sourceValue.setPersistenceSuppressed(wasSuppressed);
            }
        }
    }
}
