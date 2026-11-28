import gg.vape.ui.click.frame.impl.inevitable.InevitableCategory;
import gg.vape.ui.click.frame.impl.inevitable.InevitableLayout;
import gg.vape.ui.click.frame.impl.inevitable.InevitableRenderScope;

public final class InevitableLayoutTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void near(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > 0.0001) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }

    public static void main(String[] args) throws Exception {
        for (int width : new int[]{640, 800, 1280, 1920, 3840}) {
            for (double configured : new double[]{0.5, 0.8, 1.0, 1.2, 1.5}) {
                double fitted = InevitableLayout.fitScale(configured, width);
                check(fitted > 0 && fitted <= configured, "fit only reduces the scale");
                double viewport = width / (2.0 * fitted);
                for (int i = 0; i < InevitableLayout.PANEL_COUNT; i++) {
                    double x = InevitableLayout.panelX(viewport, i);
                    check(x >= 0 && x + InevitableLayout.PANEL_WIDTH <= viewport + 0.0001,
                            "all default panels fit the framebuffer");
                    if (i > 0) near(InevitableLayout.PANEL_WIDTH + InevitableLayout.GAP,
                            x - InevitableLayout.panelX(viewport, i - 1), "panel separation");
                }
            }
        }
        near(0.0, InevitableLayout.clampX(-10, 800), "left clamp");
        near(690.0, InevitableLayout.clampX(999, 800), "right clamp");
        near(0.0, InevitableLayout.clampY(-10, 300), "top clamp");
        near(278.0, InevitableLayout.clampY(500, 300), "header remains visible");
        near(111.0, InevitableLayout.overlayX(0, 110, 100, 800), "overlay prefers right");
        near(599.0, InevitableLayout.overlayX(700, 100, 100, 800), "overlay flips left");
        near(0.0, InevitableLayout.overlayX(0, 110, 900, 800), "oversize overlay clamps");
        near(230.0, InevitableLayout.overlayY(250, 70, 300), "overlay bottom clamp");
        near(0.0, InevitableLayout.overlayY(-10, 70, 300), "overlay top clamp");

        check(InevitableCategory.forModule("Combat", "Reach") == InevitableCategory.COMBAT, "combat");
        check(InevitableCategory.forModule("World", "Safe Walk") == InevitableCategory.MOVEMENT, "movement override");
        check(InevitableCategory.forModule("World", "X-ray") == InevitableCategory.VISUAL, "visual override");
        check(InevitableCategory.forModule("Inventory", "AutoHotbar") == InevitableCategory.UTILITY, "utility");
        check(InevitableCategory.forModule("None", "GUI") == null, "internal module hidden");

        check(!InevitableRenderScope.isActive(), "scope initially disabled");
        boolean previous = InevitableRenderScope.setActive(true);
        check(!previous && InevitableRenderScope.isActive(), "scope activation");
        boolean nested = InevitableRenderScope.setActive(false);
        check(nested && !InevitableRenderScope.isActive(), "nested scope");
        InevitableRenderScope.setActive(nested);
        final boolean[] other = new boolean[1];
        Thread thread = new Thread(() -> other[0] = InevitableRenderScope.isActive());
        thread.start();
        thread.join();
        check(!other[0] && InevitableRenderScope.isActive(), "scope is thread-local");
        InevitableRenderScope.setActive(previous);
        System.out.println("Inevitable layout, category and render-scope checks passed");
    }
}
