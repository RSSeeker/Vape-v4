import gg.vape.event.impl.EventKeyPress;
import gg.vape.event.impl.EventPostAttack;
import gg.vape.event.impl.EventPreTick;
import gg.vape.event.impl.SyntheticAttackRequestEvent;
import gg.vape.input.AttackKeyController;
import gg.vape.input.InputEventDispatcher;
import gg.vape.module.Mod;
import gg.vape.module.combat.AutoMace;
import gg.vape.module.combat.HitSwap;
import gg.vape.module.combat.automace.AutoMaceRotationController;
import gg.vape.module.none.ClientSettings;
import gg.vape.rotation.RotationManager;
import gg.vape.utils.AttackCooldownUtil;
import gg.vape.utils.RotationUtil;
import gg.vape.utils.TimerUtil;
import gg.vape.wrapper.impl.*;

/** Runs production AutoMace handlers with controllable ticks, inputs and attack completions.
 * These stubs do not model Minecraft's mappings, network packets, or server combat rules. */
public final class AutoMaceModuleTest {
    private static int checks;

    private static void check(boolean condition, String message) {
        ++checks;
        if (!condition) throw new AssertionError(message);
    }

    private static void reset() {
        Minecraft.player = new EntityPlayerSP();
        Minecraft.world = new WorldClient();
        Minecraft.player.inventory.slots[0] = new ItemStack(new Sword());
        Minecraft.player.inventory.slots[1] = new ItemStack(new Axe());
        Minecraft.player.inventory.slots[2] = new ItemStack(new Mace());
        Minecraft.screen.open = false;
        InputEventDispatcher.getInstance().focus.focused = true;
        ClientSettings.INSTANCE.inputEnabled = true;
        RotationUtil.smash = true;
        AttackCooldownUtil.ready = true;
        TimerUtil.now = 0;
        AttackKeyController.reset();
        RotationManager.INSTANCE.setController(null);
        RotationManager.INSTANCE.hit = RayTraceResult.miss();
    }

    private static EntityOtherPlayerMP target(int id, boolean shielded) {
        EntityOtherPlayerMP target = new EntityOtherPlayerMP();
        target.id = id;
        target.blocking = shielded;
        return target;
    }

    private static void hit(Entity target) {
        RotationManager.INSTANCE.hit = new RayTraceResult(RayTraceResult_type.entity(), target);
    }

    private static AutoMace stunModule() {
        AutoMace module = new AutoMace();
        module.stunSlam.value = true;
        return module;
    }

    private static EventKeyPress attack(AutoMace module) {
        EventKeyPress event = new EventKeyPress();
        module.onKeyPress(event);
        return event;
    }

    private static void tick(AutoMace module) {
        ++Minecraft.player.ticks;
        module.onTick(new EventPreTick());
    }

    private static void observed(AutoMace module, Entity target) {
        module.onPostAttack(new EventPostAttack(target.getObject()));
    }

    private static void testStunSlamLifecycle() {
        reset();
        AutoMace module = stunModule();
        EntityOtherPlayerMP target = target(7, true);
        hit(target);
        AttackKeyController.defer = true;
        final boolean[] marked = {false};
        AttackKeyController.duringEvent = () -> marked[0] = module.isSyntheticAttackInProgress();
        check(attack(module).canceled && Minecraft.player.inventory.v() == 1,
                "swapping to an axe cancels the original action and selects the axe");
        check(AttackKeyController.requests == 1 && AttackKeyController.presses == 0,
                "the axe action remains queued until the executor runs");
        tick(module);
        check(AttackKeyController.requests == 1 && Minecraft.player.inventory.v() == 1,
                "a pending axe action cannot schedule a follow-up or restore early");
        AttackKeyController.runDeferred();
        check(marked[0] && AttackKeyController.presses == 1,
                "follow-up marker is visible when the queued axe event is dispatched");
        tick(module);
        check(AttackKeyController.requests == 1, "a synthetic press alone does not authorize the slam");
        EntityOtherPlayerMP replacement = target(7, true);
        observed(module, replacement);
        tick(module);
        check(AttackKeyController.requests == 1, "a different entity with a reused id does not authorize the slam");
        observed(module, target);
        module.onTick(new EventPreTick());
        check(AttackKeyController.requests == 1, "an observed axe attack does not slam in its own tick");
        tick(module);
        check(AttackKeyController.requests == 2 && AttackKeyController.presses == 1
                && Minecraft.player.inventory.v() == 2, "a later tick queues the mace follow-up");
        marked[0] = false;
        AttackKeyController.runDeferred();
        check(marked[0] && AttackKeyController.presses == 2,
                "the actual follow-up dispatch retains the recursion guard");
        tick(module);
        tick(module);
        tick(module);
        check(Minecraft.player.inventory.v() == 0, "completed slam restores the original slot");
    }

    private static void testMissingAndRejectedAxe() {
        reset();
        AutoMace module = stunModule();
        EntityOtherPlayerMP target = target(8, true);
        hit(target);
        Minecraft.player.inventory.g(1);
        check(!attack(module).canceled && AttackKeyController.requests == 0,
                "an already-held axe leaves the original click intact");
        for (int i = 0; i < 7; ++i) tick(module);
        check(AttackKeyController.requests == 0 && Minecraft.player.inventory.v() == 1,
                "a missing local axe event times out without a slam");

        reset();
        module = stunModule();
        target = target(9, true);
        hit(target);
        AttackKeyController.defer = true;
        AttackKeyController.accept = false;
        attack(module);
        AttackKeyController.runDeferred();
        tick(module);
        observed(module, target);
        tick(module);
        check(AttackKeyController.requests == 1 && AttackKeyController.presses == 0
                && Minecraft.player.inventory.v() == 0, "a rejected axe action aborts and restores");

        reset();
        module = stunModule();
        target = target(10, true);
        hit(target);
        AttackKeyController.defer = true;
        attack(module);
        Minecraft.player.inventory.slots[1] = new ItemStack(new Sword());
        AttackKeyController.runDeferred();
        tick(module);
        check(AttackKeyController.presses == 0 && Minecraft.player.inventory.v() == 0,
                "replacing the item in the same slot rejects the queued axe action");

        reset();
        module = stunModule();
        target = target(10, true);
        hit(target);
        AttackKeyController.defer = true;
        attack(module);
        module.stunSlam.value = false;
        AttackKeyController.runDeferred();
        tick(module);
        check(AttackKeyController.presses == 0 && Minecraft.player.inventory.v() == 0,
                "disabling stun slam invalidates its queued axe action");

        reset();
        module = stunModule();
        target = target(10, true);
        hit(target);
        AttackKeyController.defer = true;
        attack(module);
        AttackKeyController.runDeferred();
        tick(module);
        observed(module, target);
        RotationUtil.smash = false;
        tick(module);
        check(AttackKeyController.requests == 1 && Minecraft.player.inventory.v() == 0,
                "smash-only prevents a follow-up once smash readiness is lost");
    }

    private static void testImmediateCompletion() {
        reset();
        AutoMace module = stunModule();
        hit(target(19, true));
        check(attack(module).canceled && AttackKeyController.presses == 1,
                "an inline accepted axe action completes without waiting for the executor");
        int releases = AttackKeyController.releases;
        module.onDisable();
        check(AttackKeyController.releases > releases && Minecraft.player.inventory.v() == 0,
                "disable releases an inline press and restores its slot");

        reset();
        module = stunModule();
        hit(target(19, true));
        AttackKeyController.accept = false;
        check(attack(module).canceled && AttackKeyController.presses == 0,
                "a canceled inline synthetic event does not press");
        tick(module);
        check(Minecraft.player.inventory.v() == 0, "a rejected inline action is restored on the next tick");
    }

    private static void testInvalidationAndRestoration() {
        reset();
        AutoMace module = stunModule();
        EntityOtherPlayerMP first = target(11, true);
        hit(first);
        AttackKeyController.defer = true;
        attack(module);
        hit(target(11, true));
        AttackKeyController.runDeferred();
        tick(module);
        check(AttackKeyController.presses == 0 && Minecraft.player.inventory.v() == 0,
                "a different target with the same id rejects the queued action");

        reset();
        module = stunModule();
        hit(first);
        AttackKeyController.defer = true;
        attack(module);
        Minecraft.player.inventory.g(8);
        AttackKeyController.runDeferred();
        tick(module);
        check(AttackKeyController.presses == 0 && Minecraft.player.inventory.v() == 8,
                "a manual slot change is not attacked with or overwritten");

        reset();
        module = stunModule();
        hit(first);
        AttackKeyController.defer = true;
        attack(module);
        Minecraft.world = new WorldClient();
        AttackKeyController.runDeferred();
        tick(module);
        check(AttackKeyController.presses == 0 && Minecraft.player.inventory.v() == 0,
                "world replacement invalidates a queued action even if the player remains");

        reset();
        module = stunModule();
        hit(first);
        AttackKeyController.defer = true;
        attack(module);
        EntityPlayerSP replacement = new EntityPlayerSP();
        replacement.inventory.g(1);
        Minecraft.player = replacement;
        AttackKeyController.runDeferred();
        tick(module);
        check(AttackKeyController.presses == 0 && replacement.inventory.v() == 1,
                "a replacement player is never given the old player's original slot");

        reset();
        module = stunModule();
        hit(first);
        AttackKeyController.defer = true;
        attack(module);
        InputEventDispatcher.getInstance().focus.focused = false;
        AttackKeyController.runDeferred();
        tick(module);
        check(AttackKeyController.presses == 0 && Minecraft.player.inventory.v() == 0,
                "focus loss rejects and restores the pending swap");

        reset();
        module = new AutoMace();
        module.attack.value = true;
        Minecraft.player.inventory.g(2);
        hit(first);
        AttackKeyController.defer = true;
        TimerUtil.now = 100;
        tick(module);
        check(AttackKeyController.requests == 1, "automatic held-mace action is queued without a swap");
        InputEventDispatcher.getInstance().focus.focused = false;
        tick(module);
        InputEventDispatcher.getInstance().focus.focused = true;
        AttackKeyController.runDeferred();
        check(AttackKeyController.presses == 0, "observed focus loss invalidates a no-swap queued action");

        reset();
        module = stunModule();
        hit(first);
        AttackKeyController.defer = true;
        attack(module);
        module.enabled = false;
        module.onDisable();
        AttackKeyController.runDeferred();
        check(AttackKeyController.presses == 0 && Minecraft.player.inventory.v() == 0,
                "disable invalidates the request and restores its slot");

        reset();
        final AutoMace reentrant = stunModule();
        hit(first);
        AttackKeyController.defer = true;
        attack(reentrant);
        AttackKeyController.duringEvent = () -> { reentrant.enabled = false; reentrant.onDisable(); };
        AttackKeyController.runDeferred();
        check(AttackKeyController.presses == 0 && Minecraft.player.inventory.v() == 0,
                "disabling inside a synthetic event is revalidated before pressing");

        reset();
        module = stunModule();
        hit(first);
        AttackKeyController.defer = true;
        attack(module);
        AttackKeyController.duringEvent = () -> hit(target(12, true));
        AttackKeyController.runDeferred();
        tick(module);
        check(AttackKeyController.presses == 0 && Minecraft.player.inventory.v() == 0,
                "a target changed by an event listener is revalidated before pressing");

        reset();
        module = stunModule();
        hit(first);
        AttackKeyController.defer = true;
        attack(module);
        for (int i = 0; i < 5; ++i) tick(module);
        AttackKeyController.runDeferred();
        check(AttackKeyController.presses == 0 && Minecraft.player.inventory.v() == 0,
                "a stalled executor times out and its later callback cannot press");
    }

    private static void testSyntheticWorldSelection() {
        reset();
        Minecraft.player.y = 10;
        Minecraft.player.previousY = 11;
        EntityOtherPlayerMP nearer = target(30, false);
        nearer.x = 1;
        nearer.y = 7.1;
        nearer.height = 0.2;
        EntityOtherPlayerMP farther = target(31, false);
        farther.x = 3.1;
        farther.y = 10;
        Minecraft.world.entities.add(nearer);
        Minecraft.world.entities.add(farther);
        AutoMace module = new AutoMace();
        module.aim.value = true;
        tick(module);
        AutoMaceRotationController controller = RotationManager.INSTANCE.getActiveController();
        check(controller != null && controller.target.getObject() == farther && controller.direct,
                "an unreachable nearer synthetic-world entity does not mask a directly reachable one");
        nearer.height = 1.8;
        tick(module);
        controller = RotationManager.INSTANCE.getActiveController();
        check(controller != null && controller.target.getObject() == nearer && controller.direct,
                "the nearer candidate wins once it is directly reachable");
        RotationUtil.smash = false;
        tick(module);
        controller = RotationManager.INSTANCE.getActiveController();
        check(controller != null && controller.target.getObject() == nearer
                && !controller.direct && controller.predictionValid && controller.predictionTick == 1,
                "a valid simulated impact can select a candidate without direct reach");
        Minecraft.screen.open = true;
        tick(module);
        check(RotationManager.INSTANCE.getActiveController() == null,
                "opening a screen releases the AutoMace rotation controller immediately");
    }

    private static void testHitSwapInteroperability() {
        reset();
        EntityOtherPlayerMP target = target(14, true);
        hit(target);
        HitSwap ordinarySwap = new HitSwap();
        SyntheticAttackRequestEvent ordinary = new SyntheticAttackRequestEvent(new Mod());
        ordinarySwap.onSyntheticAttack(ordinary);
        check(ordinary.isCanceled(), "HitSwap would intercept an ordinary synthetic action in this setup");
        ordinarySwap.onDisable();

        AutoMace module = stunModule();
        HitSwap hitSwap = new HitSwap();
        AttackKeyController.defer = true;
        final int[] passed = {0};
        AttackKeyController.duringEvent = () -> {
            SyntheticAttackRequestEvent event = new SyntheticAttackRequestEvent(module);
            hitSwap.onSyntheticAttack(event);
            if (event.isCanceled()) AttackKeyController.accept = false;
            else ++passed[0];
        };
        attack(module);
        AttackKeyController.runDeferred();
        tick(module);
        observed(module, target);
        tick(module);
        AttackKeyController.runDeferred();
        check(passed[0] == 2 && AttackKeyController.presses == 2,
                "HitSwap leaves both guarded AutoMace synthetic phases alone");

        reset();
        hit(target);
        AutoMace ordinaryAuto = new AutoMace();
        ordinaryAuto.attack.value = true;
        HitSwap otherSwap = new HitSwap();
        AttackKeyController.defer = true;
        AttackKeyController.duringEvent = () -> {
            SyntheticAttackRequestEvent event = new SyntheticAttackRequestEvent(ordinaryAuto);
            otherSwap.onSyntheticAttack(event);
            if (event.isCanceled()) AttackKeyController.accept = false;
        };
        TimerUtil.now = 100;
        tick(ordinaryAuto);
        AttackKeyController.runDeferred();
        check(AttackKeyController.presses == 1 && Minecraft.player.inventory.v() == 2,
                "HitSwap cannot replace an AutoMace-owned automatic mace swap");
        ordinaryAuto.attack.value = false;
        tick(ordinaryAuto);
        tick(ordinaryAuto);
        tick(ordinaryAuto);
        check(Minecraft.player.inventory.v() == 0, "the guarded automatic swap restores its original slot");
    }

    private static void testAutomaticStunSlam() {
        reset();
        AutoMace module = stunModule();
        module.attack.value = true;
        EntityOtherPlayerMP target = target(15, true);
        hit(target);
        AttackKeyController.defer = true;
        TimerUtil.now = 100;
        tick(module);
        check(AttackKeyController.requests == 1 && Minecraft.player.inventory.v() == 1,
                "automatic stun starts by queueing the axe action");
        module.attack.value = false;
        AttackKeyController.runDeferred();
        tick(module);
        check(AttackKeyController.presses == 0 && Minecraft.player.inventory.v() == 0,
                "turning automatic attack off rejects its deferred axe action");

        reset();
        module = stunModule();
        module.attack.value = true;
        hit(target);
        AttackKeyController.defer = true;
        TimerUtil.now = 100;
        tick(module);
        Minecraft.player.previousY = Minecraft.player.y;
        AttackKeyController.runDeferred();
        tick(module);
        check(AttackKeyController.presses == 0 && Minecraft.player.inventory.v() == 0,
                "automatic attack is rechecked for falling at execution");

        reset();
        module = stunModule();
        module.attack.value = true;
        hit(target);
        AttackKeyController.defer = true;
        TimerUtil.now = 100;
        tick(module);
        AttackKeyController.runDeferred();
        tick(module);
        observed(module, target);
        module.attack.value = false;
        tick(module);
        check(AttackKeyController.requests == 1 && Minecraft.player.inventory.v() == 0,
                "automatic slam aborts when its setting is turned off after the axe attempt");

        reset();
        module = stunModule();
        module.attack.value = true;
        hit(target);
        AttackKeyController.defer = true;
        TimerUtil.now = 100;
        tick(module);
        AttackKeyController.runDeferred();
        tick(module);
        observed(module, target);
        tick(module);
        check(AttackKeyController.requests == 2 && Minecraft.player.inventory.v() == 2,
                "automatic slam queues its follow-up after a local axe event");
        module.attack.value = false;
        AttackKeyController.runDeferred();
        tick(module);
        check(AttackKeyController.presses == 1 && Minecraft.player.inventory.v() == 0,
                "a queued automatic follow-up is invalidated if automatic attack is turned off");

        reset();
        module = stunModule();
        module.attack.value = true;
        hit(target);
        AttackKeyController.defer = true;
        TimerUtil.now = 100;
        tick(module);
        AttackKeyController.runDeferred();
        tick(module);
        observed(module, target);
        tick(module);
        RotationUtil.smash = false;
        AttackKeyController.runDeferred();
        tick(module);
        check(AttackKeyController.presses == 1 && Minecraft.player.inventory.v() == 0,
                "a queued smash-only follow-up rechecks smash readiness at execution");
    }

    public static void main(String[] args) {
        testStunSlamLifecycle();
        testMissingAndRejectedAxe();
        testImmediateCompletion();
        testInvalidationAndRestoration();
        testSyntheticWorldSelection();
        testHitSwapInteroperability();
        testAutomaticStunSlam();
        System.out.println("AutoMace module: " + checks + " headless integration assertions passed");
    }
}
