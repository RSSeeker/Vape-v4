package gg.vape.module.macro;

import gg.vape.module.macro.CommandMacro;
import gg.vape.module.macro.MacroAction;
import gg.vape.wrapper.impl.Minecraft;

class CommandMacroAction
implements MacroAction {
    private final CommandMacro macro;

    CommandMacroAction(CommandMacro commandMacro) {
        this.macro = commandMacro;
    }

    @Override
    public boolean isFinished() {
        return true;
    }

    @Override
    public void tick() {
        String name = this.macro.getName();
        if (name.startsWith("/") && gg.vape.wrapper.impl.ForgeVersion.MC_1_20_6.d()) {
            Minecraft.a_xH_J().sendCommandMessage(name.substring(1));
        } else {
            Minecraft.a_xH_J().sendChatMessage(name);
        }
    }
}

