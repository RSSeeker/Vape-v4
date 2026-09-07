package gg.vape.module.macro;

import gg.vape.Vape;
import gg.vape.event.EventHandler;
import gg.vape.event.EventListener;
import gg.vape.event.impl.EventKeyPress;
import gg.vape.event.impl.EventMouseButton;
import gg.vape.event.impl.EventPreTick;
import gg.vape.mapping.MappedClasses;
import gg.vape.module.Macro;
import gg.vape.module.macro.MacroAction;
import gg.vape.wrapper.impl.Minecraft;
import java.util.List;

public class MacroEventListener
implements EventListener {
    private MacroAction activeAction;

    private boolean startMacro(Macro macro) {
        MacroAction nextAction = macro.createAction();
        if (nextAction == null) {
            return false;
        }
        if (this.activeAction != null) {
            this.activeAction.cancel();
            nextAction.inheritState(this.activeAction);
        }
        this.activeAction = nextAction;
        return true;
    }

    @EventHandler
    public void onTick(EventPreTick event) {
        if (this.activeAction == null) {
            return;
        }
        this.activeAction.tick();
        if (this.activeAction.isFinished()) {
            this.activeAction = null;
        }
    }


    @EventHandler
    public void onMouseButton(EventMouseButton event) {
        if (event.getButtonState()) {
            int binding = -100 + event.getButton();
            List<Macro> macros = Vape.INSTANCE.getMacrosManager().getMacros(binding);
            for (Macro macro : macros) {
                if (macro.activateIfMatched(binding) && this.startMacro(macro)) {
                    break;
                }
            }
        }
    }

    @EventHandler
    public void onKeyPress(EventKeyPress event) {
        // Fire on key-DOWN (match onMouseButton and module keybinds). The previous
        // `if (event.isDown()) return;` fired only on key-RELEASE, so the macro never
        // reacted on the press -> "按按键没反应".
        if (!event.isDown()) {
            return;
        }
        if (event.getThePlayer().isNull()) {
            return;
        }
        if (Minecraft.a_pt_1_w().isInstance(MappedClasses.qo)) {
            return;
        }
        int binding = event.getKey();
        List<Macro> macros = Vape.INSTANCE.getMacrosManager().getMacros(binding);
        for (Macro macro : macros) {
            if (macro.activateIfMatched(binding) && this.startMacro(macro)) {
                break;
            }
        }
    }
}

