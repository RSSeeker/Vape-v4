package gg.vape.sync;

import gg.vape.Vape;
import gg.vape.module.none.ClientSettings;
import gg.vape.utils.SleepUtil;

public class SyncDebounceWorker
implements Runnable {
    private static final long DEBOUNCE_MILLIS = 3000L;
    @Override
    public void run() {
        while (!Vape.INSTANCE.isEnabled()) {
            this.processPendingSave();
        }
    }

    private void processPendingSave() {
        try {
            SleepUtil.sleep(1000L);
            if (!Vape.INSTANCE.getPublicProfileSettings().autoSave.getEffectiveValue()) {
                return;
            }
            if (!ClientSettings.INSTANCE.isMainGuiStack() && !ClientSettings.INSTANCE.inputEnabled) {
                return;
            }
            SyncThread sync = Vape.INSTANCE.getSyncThread();
            long observedRevision = sync.getChangeRevision();
            if (sync.hasPendingSave()) {
                SleepUtil.sleep(DEBOUNCE_MILLIS);
                // Compare and consume under the same lock used by markDirty().
                // An edit between the timer check and queueing cannot be lost.
                sync.requestSaveIfUnchanged(observedRevision);
            }
        }
        catch (Exception exception) {
            Vape.logThrowable(exception);
        }
    }
}
