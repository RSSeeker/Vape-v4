package gg.vape.ui.click.component.input;

import gg.vape.input.BindCaptureTask;
import gg.vape.ui.click.component.input.BindableInputComponent;
import gg.vape.unmap.Bendable;

public class BindableInputComponentSavingBindCaptureTask
extends BindCaptureTask {
    final BindableInputComponent owner;

    public BindableInputComponentSavingBindCaptureTask(BindableInputComponent bindableInputComponent, Bendable bendable) {
        super(bendable);
        this.owner = bindableInputComponent;
    }

    public void saveSettings() {
        this.owner.saveBindChanges();
    }

    @Override
    protected void onCaptureComplete(Bendable target) {
        this.owner.saveBindChanges(target);
    }

    @Override
    public void onCaptureComplete() {
        this.saveSettings();
    }
}
