package gg.vape.ui.click.frame.impl.inevitable;

/** Coordinate calculations shared by rendering and input scaling. */
public final class InevitableLayout {
    public static final int PANEL_COUNT = 6;
    public static final double PANEL_WIDTH = 110.0;
    public static final double GAP = 8.0;
    public static final double MARGIN = 12.0;

    private InevitableLayout() {}

    public static double contentWidth() {
        return PANEL_COUNT * PANEL_WIDTH + (PANEL_COUNT - 1) * GAP;
    }

    /** Minecraft's UI coordinates are half the framebuffer size. */
    public static double fitScale(double configuredScale, int framebufferWidth) {
        if (configuredScale <= 0.0 || framebufferWidth <= 0) return configuredScale;
        return Math.min(configuredScale, framebufferWidth / (2.0 * (contentWidth() + 2.0 * MARGIN)));
    }

    public static double panelX(double viewportWidth, int index) {
        if (index < 0 || index >= PANEL_COUNT) throw new IllegalArgumentException("panel index");
        return Math.max(MARGIN, (viewportWidth - contentWidth()) / 2.0) + index * (PANEL_WIDTH + GAP);
    }

    public static double panelY(double viewportHeight) {
        return clampY(Math.max(30.0, viewportHeight * 0.18), viewportHeight);
    }

    public static double maxPanelHeight(double viewportHeight, double panelY) {
        return Math.max(22.0, viewportHeight - panelY - MARGIN);
    }

    public static double clampX(double x, double viewportWidth) {
        return Math.max(0.0, Math.min(x, Math.max(0.0, viewportWidth - PANEL_WIDTH)));
    }

    public static double clampY(double y, double viewportHeight) {
        return Math.max(0.0, Math.min(y, Math.max(0.0, viewportHeight - 22.0)));
    }

    /** Prefer the right of a panel, flip left at the viewport edge, then clamp. */
    public static double overlayX(double ownerX, double ownerWidth, double overlayWidth, double viewportWidth) {
        double right = ownerX + ownerWidth + 1.0;
        if (right + overlayWidth <= viewportWidth) return Math.max(0.0, right);
        double left = ownerX - overlayWidth - 1.0;
        if (left >= 0.0) return left;
        return Math.max(0.0, Math.min(right, Math.max(0.0, viewportWidth - overlayWidth)));
    }

    public static double overlayY(double preferredY, double overlayHeight, double viewportHeight) {
        return Math.max(0.0, Math.min(preferredY, Math.max(0.0, viewportHeight - overlayHeight)));
    }
}
