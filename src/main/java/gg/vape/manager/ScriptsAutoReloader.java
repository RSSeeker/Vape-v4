package gg.vape.manager;

import gg.vape.event.EventHandler;
import gg.vape.event.EventListener;
import gg.vape.event.impl.EventPreTick;

/**
 * Watches .vapeclient/scripts for edits and reloads them automatically.
 *
 * <p>Clicking "Reload Scripts" is unreliable for users who keep the GUI open, so
 * this listener checks the directory signature once per second while the game is
 * running. It only reloads when the signature actually changed, so it is a no-op
 * in the steady state.</p>
 */
public final class ScriptsAutoReloader
implements EventListener {
    private static final int CHECK_INTERVAL_TICKS = 20;

    private int ticks;
    private long signature;
    private boolean primed;

    @EventHandler
    public void onTick(EventPreTick event) {
        if (++this.ticks < CHECK_INTERVAL_TICKS) {
            return;
        }
        this.ticks = 0;
        long current = ExtensionManager.scriptsSignature();
        if (!this.primed) {
            this.primed = true;
            this.signature = current;
            return;
        }
        if (current == this.signature) {
            return;
        }
        this.signature = current;
        try {
            int reloaded = ExtensionManager.reloadAll();
            ExtensionManager.notifyAutoReload(reloaded);
        }
        catch (Throwable ignored) {
            // 自动重载失败不能影响游戏
        }
    }
}
