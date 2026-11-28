package gg.vape.value;

import gg.vape.value.Value;

public class StringValue
extends Value<String, StringValue> {
    private String definitionName;

    public String toString() {
        return (String)this.getValue();
    }

    public StringValue(Object owner, String name, String defaultValue) {
        super(owner, name, defaultValue);
        // Values constructed directly (including script settings) must retain
        // their name when a profile snapshot creates an editable proxy.
        this.definitionName = name;
    }

    @Override
    public void parse(String serializedValue) {
        this.setValue(serializedValue);
    }

    public static StringValue create(Object owner, String name, String defaultValue) {
        StringValue stringValue = new StringValue(owner, name, defaultValue);
        stringValue.setDefinitionName(name);
        return stringValue;
    }

    public StringValue copyDefinition() {
        return StringValue.create(null, this.getDefinitionName(), (String)this.getValue());
    }

    @Override
    public StringValue copyValueDefinition() {
        return this.copyDefinition();
    }

    public String getDefinitionName() {
        return this.definitionName;
    }

    private void setDefinitionName(String definitionName) {
        this.definitionName = definitionName;
    }
}
