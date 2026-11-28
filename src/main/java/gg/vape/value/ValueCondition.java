package gg.vape.value;

import gg.vape.value.ConditionalValue;
import gg.vape.value.Value;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

public class ValueCondition {
    private static int[] legacyState;
    private final Map<ConditionalValue, Predicate<Object>> predicatesByValue = new HashMap<ConditionalValue, Predicate<Object>>();

    public ValueCondition requireFalse(ConditionalValue conditionalValue) {
        this.predicatesByValue.put(conditionalValue, Boolean.FALSE::equals);
        return this;
    }


    private static boolean isNotEqual(Object expected, Object candidate) {
        return !expected.equals(candidate);
    }

    public Map<ConditionalValue, Predicate<Object>> getPredicatesByValue() {
        return this.predicatesByValue;
    }

    public static void setLegacyState(int[] state) {
        legacyState = state;
    }

    public ValueCondition requireNotEqual(ConditionalValue conditionalValue, Object expectedValue) {
        this.predicatesByValue.put(conditionalValue, candidate -> ValueCondition.isNotEqual(expectedValue, candidate));
        return this;
    }

    public ValueCondition requireEqual(ConditionalValue conditionalValue, Object expectedValue) {
        this.predicatesByValue.put(conditionalValue, expectedValue::equals);
        return this;
    }

    static {
        if (ValueCondition.getLegacyState() != null) {
            ValueCondition.setLegacyState(new int[1]);
        }
    }

    public void applyTo(Value<?, ?> ... values) {
        for (Map.Entry<ConditionalValue, Predicate<Object>> entry : this.predicatesByValue.entrySet()) {
            ConditionalValue conditionalValue = entry.getKey();
            conditionalValue.addDependentValues(values);
            for (Value<?, ?> value : values) {
                value.addCondition(this);
            }
        }
    }

    public ValueCondition and(ValueCondition other) {
        ValueCondition combined = new ValueCondition();
        combined.predicatesByValue.putAll(this.predicatesByValue);
        combined.predicatesByValue.putAll(other.predicatesByValue);
        return combined;
    }

    /** Rebind predicates to detached profile values without touching live ones. */
    ValueCondition rebind(Map<Value<?, ?>, Value<?, ?>> proxies) {
        ValueCondition copy = new ValueCondition();
        for (Map.Entry<ConditionalValue, Predicate<Object>> entry : this.predicatesByValue.entrySet()) {
            Value<?, ?> proxy = proxies.get(entry.getKey());
            ConditionalValue condition = proxy instanceof ConditionalValue ? (ConditionalValue)proxy : entry.getKey();
            copy.predicatesByValue.put(condition, entry.getValue());
        }
        return copy;
    }

    public ValueCondition require(ConditionalValue conditionalValue, Predicate<Object> predicate) {
        this.predicatesByValue.put(conditionalValue, predicate);
        return this;
    }

    public static int[] getLegacyState() {
        return legacyState;
    }

    public ValueCondition requireTrue(ConditionalValue conditionalValue) {
        this.predicatesByValue.put(conditionalValue, Boolean.TRUE::equals);
        return this;
    }

    public boolean isSatisfied() {
        for (Map.Entry<ConditionalValue, Predicate<Object>> entry : this.predicatesByValue.entrySet()) {
            if (entry.getValue().test(entry.getKey().getValue())) continue;
            return false;
        }
        return true;
    }
}

