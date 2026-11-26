import gg.vape.event.impl.EventTickBase;
import gg.vape.event.impl.SyntheticAttackRequestEvent;
import gg.vape.input.AttackKeyController;
import gg.vape.input.KeyBindingHelper;
import gg.vape.module.Mod;
import gg.vape.wrapper.impl.ForgeVersion;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Exercises the production controller with immediate and queued tick executors. */
public final class AttackKeyControllerTest {
    private static int checks;
    private static void check(boolean value, String message) {
        ++checks;
        if (!value) throw new AssertionError(message);
    }
    private static void reset(boolean modern) {
        ForgeVersion.MC_26_2.modern = modern;
        EventTickBase.PRE_TICK_EXECUTOR.reset();
        SyntheticAttackRequestEvent.cancel = false;
        SyntheticAttackRequestEvent.onFire = null;
        SyntheticAttackRequestEvent.fired = 0;
        KeyBindingHelper.presses = 0;
        KeyBindingHelper.releases = 0;
    }
    public static void main(String[] args) {
        Mod source = new Mod();
        AtomicInteger completion = new AtomicInteger();
        reset(false);
        AttackKeyController.requestValidatedSyntheticAttack(source, () -> true,
                pressed -> completion.set(pressed ? 1 : -1));
        check(completion.get() == 1 && KeyBindingHelper.presses == 1, "immediate request reports press");
        reset(false);
        completion.set(0);
        AttackKeyController.requestValidatedSyntheticAttack(source, () -> false,
                pressed -> completion.set(pressed ? 1 : -1));
        check(completion.get() == -1 && SyntheticAttackRequestEvent.fired == 0 && KeyBindingHelper.presses == 0,
                "invalid immediate request does not fire event or press key");
        reset(false);
        completion.set(0);
        SyntheticAttackRequestEvent.cancel = true;
        AttackKeyController.requestValidatedSyntheticAttack(source, () -> true,
                pressed -> completion.set(pressed ? 1 : -1));
        check(completion.get() == -1 && KeyBindingHelper.presses == 0, "event cancellation is reported");
        reset(true);
        completion.set(0);
        AtomicBoolean valid = new AtomicBoolean(true);
        AttackKeyController.requestValidatedSyntheticAttack(source, valid::get,
                pressed -> completion.set(pressed ? 1 : -1));
        check(completion.get() == 0 && KeyBindingHelper.presses == 0, "26.2 off-owner request is queued");
        valid.set(false);
        EventTickBase.PRE_TICK_EXECUTOR.runPending();
        check(completion.get() == -1 && KeyBindingHelper.presses == 0,
                "queued request revalidates before firing");
        reset(true);
        completion.set(0);
        valid.set(true);
        SyntheticAttackRequestEvent.onFire = () -> valid.set(false);
        AttackKeyController.requestValidatedSyntheticAttack(source, valid::get,
                pressed -> completion.set(pressed ? 1 : -1));
        EventTickBase.PRE_TICK_EXECUTOR.runPending();
        check(SyntheticAttackRequestEvent.fired == 1 && completion.get() == -1 && KeyBindingHelper.presses == 0,
                "state is rechecked after event handlers");
        reset(true);
        completion.set(0);
        AttackKeyController.requestValidatedSyntheticAttack(source, () -> true,
                pressed -> completion.set(pressed ? 1 : -1));
        EventTickBase.PRE_TICK_EXECUTOR.runPending();
        check(completion.get() == 1 && KeyBindingHelper.presses == 1, "valid queued request reports completion");
        AttackKeyController.releaseAttackKey();
        check(KeyBindingHelper.releases == 1, "tick owner releases immediately");
        reset(true);
        check(AttackKeyController.requestSyntheticAttack(source) && KeyBindingHelper.presses == 0,
                "legacy boolean only acknowledges scheduling");
        EventTickBase.PRE_TICK_EXECUTOR.runPending();
        check(KeyBindingHelper.presses == 1, "legacy queued request eventually presses");
        reset(false);
        completion.set(0);
        try {
            AttackKeyController.requestValidatedSyntheticAttack(source, () -> { throw new IllegalStateException(); },
                    pressed -> completion.set(pressed ? 1 : -1));
            throw new AssertionError("validation exception was swallowed");
        } catch (IllegalStateException expected) {
            check(completion.get() == -1, "failed validation still completes as not pressed");
        }
        System.out.println("Attack key controller: " + checks + " headless assertions passed");
    }
}
