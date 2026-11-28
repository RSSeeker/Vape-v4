package gg.vape.module.blatant.antibot;

import gg.vape.unmap.ModeSelection;
import gg.vape.value.ModeValue;

public class AntiBotModeValue
extends ModeValue {
    public static AntiBotModeValue create(Object owner, String name, String description,
                                          ModeSelection defaultMode, ModeSelection ... modes) {
        AntiBotModeValue value = new AntiBotModeValue(owner, name, name, defaultMode, modes);
        value.setDescription(description);
        for (ModeSelection mode : modes) {
            mode.attachToMode(value);
        }
        return value;
    }

    @Override
    public AntiBotModeValue copyValueDefinition() {
        AntiBotModeValue copy = new AntiBotModeValue(null, this.getId(), this.getName(), this.getDefaultValue(), this.getModes());
        copy.setDescription(this.getDescription());
        return copy;
    }

    public AntiBotModeValue(Object owner, String name, String description,
                            ModeSelection defaultMode, ModeSelection[] modes) {
        super(owner, name, description, defaultMode, modes);
    }
}
