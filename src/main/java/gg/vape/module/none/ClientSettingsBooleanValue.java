package gg.vape.module.none;

import gg.vape.value.BooleanValue;

public class ClientSettingsBooleanValue
extends BooleanValue {
    public ClientSettingsBooleanValue(Object owner, String name, boolean defaultValue) {
        super(owner, name, defaultValue);
    }

    @Override
    public ClientSettingsBooleanValue copyValueDefinition() {
        ClientSettingsBooleanValue copy = new ClientSettingsBooleanValue(null, this.getId(), this.getDefaultValue());
        copy.setDescription(this.getDescription());
        return copy;
    }
}
