import gg.vape.event.impl.EventTickBase;
import gg.vape.event.impl.SyntheticAttackRequestEvent;
import gg.vape.input.AttackKeyController;
import gg.vape.input.KeyBindingHelper;
import gg.vape.module.Mod;
import gg.vape.module.combat.automace.AutoMaceAttackGate;
import gg.vape.utils.ThreadBoundExecutor;
import gg.vape.wrapper.impl.ForgeVersion;
import java.util.concurrent.atomic.AtomicBoolean;

/** Exercises the production AutoMace ticket gate and attack controller with queued ticks. */
public final class AutoMaceAttackGateTest {
    private static int checks;
    private static void check(boolean value, String message) {
        ++checks;
        if (!value) throw new AssertionError(message);
    }
    private static void reset() {
        ForgeVersion.MC_26_2.modern = true;
        EventTickBase.PRE_TICK_EXECUTOR = new ThreadBoundExecutor();
        SyntheticAttackRequestEvent.cancel = false;
        SyntheticAttackRequestEvent.onFire = null;
        SyntheticAttackRequestEvent.fired = 0;
        KeyBindingHelper.presses = 0;
        KeyBindingHelper.releases = 0;
    }
    private static void queue(AutoMaceAttackGate gate, long ticket, AtomicBoolean valid) {
        AttackKeyController.requestValidatedSyntheticAttack(new Mod(),
                () -> gate.isCurrent(ticket) && valid.get(),
                pressed -> { if (gate.complete(ticket, pressed)) AttackKeyController.releaseAttackKey(); });
    }
    public static void main(String[] args) {
        reset();
        AutoMaceAttackGate gate = new AutoMaceAttackGate();
        check(!AutoMaceAttackGate.followupReady(false, 11, 10), "a tick alone cannot trigger the slam");
        check(!AutoMaceAttackGate.followupReady(true, 10, 10), "observed axe attack waits for a later tick");
        check(AutoMaceAttackGate.followupReady(true, 11, 10), "observed axe attack allows the next-tick follow-up");
        check(!AutoMaceAttackGate.followupExpired(false, 16, 10)
                && AutoMaceAttackGate.followupExpired(false, 17, 10), "missing axe event eventually times out");
        check(!AutoMaceAttackGate.followupExpired(true, 100, 10), "observed axe event is not an unobserved timeout");
        long ticket = gate.begin(true);
        check(ticket > 0 && gate.isPending() && gate.isGuardedDispatch(), "queued follow-up retains its marker");
        check(gate.begin(false) == -1, "second request cannot overlap");
        AtomicBoolean targetValid = new AtomicBoolean(true);
        AtomicBoolean markerAtEvent = new AtomicBoolean(false);
        SyntheticAttackRequestEvent.onFire = () -> markerAtEvent.set(gate.isGuardedDispatch());
        queue(gate, ticket, targetValid);
        check(KeyBindingHelper.presses == 0 && gate.isPending(), "queueing is not completion");
        EventTickBase.PRE_TICK_EXECUTOR.runPending();
        check(markerAtEvent.get() && KeyBindingHelper.presses == 1, "marker is present during actual event dispatch");
        check(!gate.isPending() && !gate.isGuardedDispatch() && gate.isBusy(), "completed press requires release");
        check(gate.takeRelease() && gate.takeOutcome() == 1 && !gate.isBusy(), "successful request can be settled");

        reset();
        ticket = gate.begin(true);
        SyntheticAttackRequestEvent.cancel = true;
        queue(gate, ticket, targetValid);
        EventTickBase.PRE_TICK_EXECUTOR.runPending();
        check(KeyBindingHelper.presses == 0 && !gate.takeRelease() && gate.takeOutcome() == -1,
                "event cancellation reports failure without pressing");
        reset();
        ticket = gate.begin(false);
        queue(gate, ticket, targetValid);
        targetValid.set(false);
        EventTickBase.PRE_TICK_EXECUTOR.runPending();
        check(KeyBindingHelper.presses == 0 && gate.takeOutcome() == -1, "changed target invalidates queued attack");

        reset();
        targetValid.set(true);
        ticket = gate.begin(true);
        queue(gate, ticket, targetValid);
        check(!gate.cancel() && !gate.isGuardedDispatch(), "disable invalidates an unpressed request");
        EventTickBase.PRE_TICK_EXECUTOR.runPending();
        check(KeyBindingHelper.presses == 0 && !gate.isBusy(), "invalidated request cannot press later");
        long oldTicket = gate.begin(true);
        check(!gate.cancel(), "unpressed timeout has no key to release");
        long newTicket = gate.begin(false);
        check(oldTicket != newTicket && gate.isCurrent(newTicket), "tickets cannot revive an older request");
        check(gate.complete(oldTicket, true), "late stale press asks caller to release key");
        check(gate.isCurrent(newTicket), "stale completion cannot alter newer request");
        check(!gate.complete(newTicket, true) && gate.cancel(), "disable releases a completed press");
        System.out.println("AutoMace attack gate: " + checks + " headless assertions passed");
    }
}
