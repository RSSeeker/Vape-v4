package gg.vape.module.combat.automace;

/** Ranking rule for already-filtered AutoMace candidates. */
public final class AimSelectionRules {
    private AimSelectionRules() {}

    public static boolean isBetterCandidate(double distance, double bestDistance,
                                            boolean directlyReachable, boolean impactValid,
                                            int impactTick, int maximumImpactTick) {
        return Double.isFinite(distance) && distance >= 0.0 && distance < bestDistance
                && (directlyReachable || impactValid && impactTick >= 0 && impactTick <= maximumImpactTick);
    }
}
