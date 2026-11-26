package gg.vape.module.combat.automace;

/** Coordinates immediate and deferred synthetic attacks without treating queueing as a hit. */
public final class AutoMaceAttackGate {
    private long epoch;
    private boolean pending;
    private boolean guardedDispatch;
    private boolean releasePending;
    private int outcome;

    /** A locally observed axe attack must precede a later-tick slam. */
    public static boolean followupReady(boolean axeObserved, int currentTick, int attackTick) {
        return axeObserved && currentTick > attackTick;
    }

    /** Do not hold the swapped axe forever if no local post-attack event arrives. */
    public static boolean followupExpired(boolean axeObserved, int currentTick, int attackTick) {
        return !axeObserved && (long)currentTick - (long)attackTick > 6L;
    }

    /** Returns a unique ticket, or -1 if the preceding request is still busy. */
    public synchronized long begin(boolean guardedDispatch) {
        if (this.pending || this.releasePending || this.outcome != 0) return -1L;
        this.pending = true;
        this.guardedDispatch = guardedDispatch;
        this.outcome = 0;
        return ++this.epoch;
    }

    public synchronized boolean isCurrent(long ticket) {
        return this.pending && this.epoch == ticket;
    }

    public synchronized boolean isPending() {
        return this.pending;
    }

    public synchronized boolean isBusy() {
        return this.pending || this.releasePending || this.outcome != 0;
    }

    /** Keeps the AutoMace-owned swap marker set through actual event dispatch. */
    public synchronized boolean isGuardedDispatch() {
        return this.pending && this.guardedDispatch;
    }

    /** Returns true if a stale completion pressed the key and must release it. */
    public synchronized boolean complete(long ticket, boolean pressed) {
        if (!this.isCurrent(ticket)) return pressed;
        this.pending = false;
        this.guardedDispatch = false;
        this.releasePending = pressed;
        this.outcome = pressed ? 1 : -1;
        return false;
    }

    public synchronized boolean takeRelease() {
        boolean result = this.releasePending;
        this.releasePending = false;
        return result;
    }

    public synchronized int takeOutcome() {
        int result = this.outcome;
        this.outcome = 0;
        return result;
    }

    /** Invalidates queued tickets; returns whether a completed press needs release. */
    public synchronized boolean cancel() {
        ++this.epoch;
        this.pending = false;
        this.guardedDispatch = false;
        this.outcome = 0;
        boolean result = this.releasePending;
        this.releasePending = false;
        return result;
    }
}
