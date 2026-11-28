package gg.vape.ui.click.frame.impl.inevitable;

/** Neutral accent only while a new panel (or one of its popups) is being drawn.
 * The saved accent remains available to the HUD, notifications and gameplay. */
public final class InevitableRenderScope {
    private static final ThreadLocal<Boolean> ACTIVE = new ThreadLocal<Boolean>() {
        @Override protected Boolean initialValue() { return Boolean.FALSE; }
    };

    private InevitableRenderScope() {}

    public static boolean isActive() { return ACTIVE.get(); }

    /** Returns the previous state so callers can restore nested render scopes. */
    public static boolean setActive(boolean active) {
        boolean previous = ACTIVE.get();
        ACTIVE.set(active);
        return previous;
    }
}
