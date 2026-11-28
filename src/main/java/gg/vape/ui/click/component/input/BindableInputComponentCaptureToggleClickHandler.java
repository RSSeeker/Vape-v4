package gg.vape.ui.click.component.input;

import gg.vape.ui.click.component.GuiClickListener;
import gg.vape.ui.click.component.input.BindableInputComponent;

class BindableInputComponentCaptureToggleClickHandler
implements GuiClickListener {
    final BindableInputComponent owner;

    BindableInputComponentCaptureToggleClickHandler(BindableInputComponent bindableInputComponent) {
        this.owner = bindableInputComponent;
    }

    @Override
    public void onPrimaryClick() {
        if (this.owner.getCaptureTask().isCapturing()) {
            this.owner.getBindLabel().setToolTips(null);
            this.owner.w(this.owner.buildTooltipText());
            return;
        }
        if (this.owner.supportsActivationModeConfiguration() && this.owner.isShiftPressed()) {
            this.owner.getBendable().toggleActivationMode();
            this.owner.saveBindChanges();
            return;
        }
        this.owner.getCaptureTask().run();
    }

}

