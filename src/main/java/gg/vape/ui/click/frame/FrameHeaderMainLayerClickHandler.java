package gg.vape.ui.click.frame;

import gg.vape.module.none.ClientSettings;
import gg.vape.ui.click.component.GuiClickListener;

public class FrameHeaderMainLayerClickHandler
implements GuiClickListener {
    @Override
    public void onPrimaryClick() {
        ClientSettings.INSTANCE.returnToMainGui();
    }
}
