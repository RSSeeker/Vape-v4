import gg.vape.Vape;
import gg.vape.config.ClientSettings;
import gg.vape.event.impl.EventKeyPress;
import gg.vape.event.impl.EventMouseButton;
import gg.vape.event.impl.EventPreTick;
import gg.vape.input.AttackKeyController;
import gg.vape.input.InputEventDispatcher;
import gg.vape.module.combat.HitSwap;
import gg.vape.module.combat.Triggerbot;
import gg.vape.rotation.RotationManager;
import gg.vape.utils.AttackCooldownUtil;
import gg.vape.utils.RotationUtil;
import gg.vape.utils.TimerUtil;
import gg.vape.value.RandomValue;
import gg.vape.wrapper.impl.*;
import java.lang.reflect.Field;

/** Exercises actual module event handlers; these stubs do not model the game or server. */
public final class CombatModuleTest {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static Field field(Class<?> c, String name) throws Exception {
        Field f = c.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }
    private static EntityOtherPlayerMP player(int id, boolean blocking) {
        EntityOtherPlayerMP p = new EntityOtherPlayerMP();
        p.id = id;
        p.blocking = blocking;
        return p;
    }
    private static void hit(Entity e) {
        RotationManager.INSTANCE.hit = new RayTraceResult(RayTraceResult_type.entity(), e);
    }
    private static void miss() { RotationManager.INSTANCE.hit = RayTraceResult.miss(); }
    private static void block() {
        RotationManager.INSTANCE.hit = new RayTraceResult(RayTraceResult_type.block(), null);
    }
    private static void reset() {
        Minecraft.player = new EntityPlayerSP();
        Minecraft.world = new WorldClient();
        Minecraft.player.present = true;
        Minecraft.player.hurt = 0;
        Minecraft.player.inventory = new InventoryPlayer();
        Minecraft.player.inventory.slots[0] = new ItemStack(new Sword());
        Minecraft.screen.open = false;
        InputEventDispatcher.getInstance().focus.focused = true;
        ClientSettings.keyDown = true;
        TimerUtil.now = 0L;
        AttackCooldownUtil.ready = true;
        RotationUtil.smash = false;
        RotationUtil.decoy = null;
        AttackKeyController.reset();
        Vape.INSTANCE.manager.mods.clear();
        miss();
    }
    private static EventKeyPress activate(Triggerbot bot) {
        EventKeyPress event = new EventKeyPress();
        bot.onKeyPress(event);
        return event;
    }
    private static Triggerbot activationBot() {
        Triggerbot bot = new Triggerbot();
        bot.requireMouseDown.value = true;
        bot.ignoreActivationClick.value = true;
        return bot;
    }

    private static void testTriggerTracking() throws Exception {
        reset();
        Triggerbot bot = new Triggerbot();
        ((RandomValue)field(Triggerbot.class, "mouseOverDelayValue").get(bot)).value = 100;
        EntityOtherPlayerMP target = player(7, false);
        AttackCooldownUtil.ready = false;
        TimerUtil.now = 100;
        hit(target);
        bot.onTick(new EventPreTick());
        check(field(Triggerbot.class, "mouseOverTargetId").getInt(bot) == 7,
                "mouse-over starts tracking while cooldown blocks attacks");
        TimerUtil.now = 120;
        block();
        bot.onKeyPress(new EventKeyPress()); // reset the physical-attack gate
        bot.onTick(new EventPreTick());
        check(field(Triggerbot.class, "mouseOverTargetId").getInt(bot) == -1,
                "block hit clears mouse-over even during the 50ms attack gate");
        TimerUtil.now = 130;
        hit(target);
        bot.onTick(new EventPreTick());
        AttackCooldownUtil.ready = true;
        TimerUtil.now = 180;
        bot.onTick(new EventPreTick());
        check(AttackKeyController.requests == 0, "reacquired target does not inherit old mouse-over delay");
        TimerUtil.now = 230;
        bot.onTick(new EventPreTick());
        check(AttackKeyController.requests == 1, "attack occurs after the new delay");
        InputEventDispatcher.getInstance().focus.focused = false;
        TimerUtil.now = 240;
        bot.onTick(new EventPreTick());
        check(field(Triggerbot.class, "mouseOverTargetId").getInt(bot) == -1, "focus loss resets mouse-over");
        check(AttackKeyController.releases >= 2, "pending synthetic key is released even when focus is lost");
    }

    private static void testTriggerActivation() {
        reset();
        check(activate(activationBot()).canceled, "activation miss is canceled");
        reset();
        block();
        check(activate(activationBot()).canceled, "activation block hit is canceled");
        reset();
        RotationManager.INSTANCE.hit = null;
        check(activate(activationBot()).canceled, "missing ray trace is canceled");
        reset();
        EntityOtherPlayerMP invalid = player(9, false);
        invalid.valid = false;
        hit(invalid);
        check(activate(activationBot()).canceled, "invalid entity activation is canceled");
        reset();
        hit(player(1, false));
        check(!activate(activationBot()).canceled, "valid ready target allows activation click");
        reset();
        hit(player(1, false));
        AttackCooldownUtil.ready = false;
        check(activate(activationBot()).canceled, "cooldown-blocked activation is canceled");
        reset();
        hit(player(1, false));
        RotationUtil.smash = true;
        Minecraft.player.inventory.slots[0] = new ItemStack(new Mace());
        AttackCooldownUtil.ready = false;
        check(!activate(activationBot()).canceled, "missing HitSwap does not crash mace cooldown bypass");
    }

    private static HitSwap configuredSwap() {
        Minecraft.player.inventory.slots[1] = new ItemStack(new Axe());
        Minecraft.player.inventory.slots[2] = new ItemStack(new Mace());
        RotationUtil.smash = true;
        return new HitSwap();
    }
    private static EventKeyPress begin(HitSwap swap) {
        EventKeyPress event = new EventKeyPress();
        swap.onKeyPress(event);
        return event;
    }
    private static void testHitSwapTargetsAndRestoration() throws Exception {
        reset();
        HitSwap canceledSwap = configuredSwap();
        hit(player(99, false));
        EventKeyPress canceledKey = new EventKeyPress();
        canceledKey.setCancelled(true);
        canceledSwap.onKeyPress(canceledKey);
        check(Minecraft.player.inventory.v() == 0, "already-canceled physical input does not start a swap");
        EventMouseButton canceledMouse = new EventMouseButton();
        canceledMouse.setCancelled(true);
        canceledSwap.onMouseButton(canceledMouse);
        check(Minecraft.player.inventory.v() == 0, "already-canceled mouse input does not start a swap");
        gg.vape.event.impl.SyntheticAttackRequestEvent canceledSynthetic =
                new gg.vape.event.impl.SyntheticAttackRequestEvent();
        canceledSynthetic.setCancelled(true);
        canceledSwap.onSyntheticAttack(canceledSynthetic);
        check(Minecraft.player.inventory.v() == 0, "already-canceled synthetic input does not start a swap");
        reset();
        EntityOtherPlayerMP first = player(1, false);
        EntityOtherPlayerMP second = player(2, false);
        HitSwap swap = configuredSwap();
        hit(first);
        check(begin(swap).canceled && Minecraft.player.inventory.v() == 2, "swap selects an alternate mace");
        hit(second);
        swap.onTick(new EventPreTick());
        check(AttackKeyController.requests == 0 && Minecraft.player.inventory.v() == 0,
                "changed crosshair aborts pending hit and restores original slot");

        hit(first);
        begin(swap);
        Minecraft.player.inventory.g(8);
        swap.onTick(new EventPreTick());
        check(AttackKeyController.requests == 0 && Minecraft.player.inventory.v() == 8,
                "manual slot change is neither attacked with nor overwritten");

        Minecraft.player.inventory.g(0);
        begin(swap);
        AttackKeyController.accept = false;
        swap.onTick(new EventPreTick());
        check(AttackKeyController.requests == 1 && Minecraft.player.inventory.v() == 0,
                "canceled synthetic request immediately restores the original slot");
        check(!field(HitSwap.class, "swingReleasePending").getBoolean(swap),
                "rejected request does not leave a synthetic key pending");

        AttackKeyController.reset();
        begin(swap);
        swap.onTick(new EventPreTick());
        check(AttackKeyController.requests == 1 && Minecraft.player.inventory.v() == 2,
                "unchanged target permits the delayed synthetic hit");
        swap.onTick(new EventPreTick());
        swap.onTick(new EventPreTick());
        check(Minecraft.player.inventory.v() == 0, "completed swap restores original slot");

        begin(swap);
        swap.onDisable();
        check(Minecraft.player.inventory.v() == 0, "disable restores an active swap");
        begin(swap);
        InputEventDispatcher.getInstance().focus.focused = false;
        int requests = AttackKeyController.requests;
        swap.onTick(new EventPreTick());
        check(AttackKeyController.requests == requests && Minecraft.player.inventory.v() == 0,
                "focus loss aborts a delayed hit");
    }

    private static void testHitSwapShieldTarget() throws Exception {
        reset();
        HitSwap swap = configuredSwap();
        swap.stunSlam.value = true;
        EntityOtherPlayerMP crosshair = player(1, false);
        EntityOtherPlayerMP decoy = player(2, true);
        RotationUtil.decoy = decoy;
        hit(crosshair);
        begin(swap);
        check(!field(HitSwap.class, "stunSlamActive").getBoolean(swap)
                && Minecraft.player.inventory.v() == 2,
                "nearby shielding player does not replace the actual crosshair target");
        swap.onDisable();
        AttackKeyController.reset();
        hit(decoy);
        begin(swap);
        check(field(HitSwap.class, "stunSlamActive").getBoolean(swap)
                && Minecraft.player.inventory.v() == 1,
                "shielded crosshair target selects axe stun phase");
        AttackKeyController.accept = false;
        swap.onTick(new EventPreTick());
        swap.onTick(new EventPreTick());
        check(AttackKeyController.requests == 1 && Minecraft.player.inventory.v() == 0,
                "canceled axe phase cannot schedule a follow-up slam");
    }

    private static void testDeferredHitSwap() throws Exception {
        reset();
        EntityOtherPlayerMP first = player(11, false);
        EntityOtherPlayerMP second = player(12, false);
        HitSwap swap = configuredSwap();
        hit(first);
        AttackKeyController.defer = true;
        begin(swap);
        swap.onTick(new EventPreTick());
        check(AttackKeyController.requests == 1 && AttackKeyController.presses == 0,
                "queued swap is not treated as a completed attack");
        swap.onTick(new EventPreTick());
        swap.onTick(new EventPreTick());
        check(AttackKeyController.requests == 1 && Minecraft.player.inventory.v() == 2,
                "pending request does not duplicate attacks or restore early");
        AttackKeyController.runDeferred();
        check(AttackKeyController.presses == 1, "unchanged deferred target is attacked");
        swap.onTick(new EventPreTick());
        swap.onTick(new EventPreTick());
        swap.onTick(new EventPreTick());
        check(Minecraft.player.inventory.v() == 0, "completed deferred swap restores the slot");

        AttackKeyController.reset();
        AttackKeyController.defer = true;
        hit(first);
        begin(swap);
        swap.onTick(new EventPreTick());
        hit(second);
        AttackKeyController.runDeferred();
        swap.onTick(new EventPreTick());
        check(AttackKeyController.presses == 0 && Minecraft.player.inventory.v() == 0,
                "target change before queued execution rejects the hit and restores");

        AttackKeyController.reset();
        AttackKeyController.defer = true;
        hit(first);
        begin(swap);
        swap.onTick(new EventPreTick());
        Minecraft.player.inventory.g(8);
        AttackKeyController.runDeferred();
        swap.onTick(new EventPreTick());
        check(AttackKeyController.presses == 0 && Minecraft.player.inventory.v() == 8,
                "manual slot change before execution is not overwritten");

        Minecraft.player.inventory.g(0);
        AttackKeyController.reset();
        AttackKeyController.defer = true;
        hit(first);
        begin(swap);
        swap.onTick(new EventPreTick());
        swap.onDisable();
        AttackKeyController.runDeferred();
        check(AttackKeyController.presses == 0 && Minecraft.player.inventory.v() == 0,
                "disable invalidates queued swap attacks");

        AttackKeyController.reset();
        AttackKeyController.defer = true;
        hit(first);
        begin(swap);
        swap.onTick(new EventPreTick());
        for (int i = 0; i < 5; ++i) swap.onTick(new EventPreTick());
        AttackKeyController.runDeferred();
        check(AttackKeyController.presses == 0 && Minecraft.player.inventory.v() == 0,
                "stalled executor times out and invalidates a queued swap");

        AttackKeyController.reset();
        AttackKeyController.defer = true;
        hit(first);
        begin(swap);
        swap.onTick(new EventPreTick());
        AttackKeyController.duringEvent = () -> hit(second);
        AttackKeyController.runDeferred();
        swap.onTick(new EventPreTick());
        check(AttackKeyController.presses == 0 && Minecraft.player.inventory.v() == 0,
                "target is revalidated after attack event listeners run");

        reset();
        swap = configuredSwap();
        swap.stunSlam.value = true;
        hit(player(13, true));
        AttackKeyController.defer = true;
        AttackKeyController.accept = false;
        begin(swap);
        swap.onTick(new EventPreTick());
        AttackKeyController.runDeferred();
        swap.onTick(new EventPreTick());
        swap.onTick(new EventPreTick());
        check(AttackKeyController.requests == 1 && AttackKeyController.presses == 0
                && Minecraft.player.inventory.v() == 0,
                "deferred event cancellation cannot advance stun into slam");
    }

    private static void testHitSwapWorldAndInventoryChanges() {
        reset();
        EntityOtherPlayerMP target = player(16, false);
        HitSwap swap = configuredSwap();
        hit(target);
        AttackKeyController.defer = true;
        begin(swap);
        swap.onTick(new EventPreTick());
        Minecraft.player.inventory.slots[2] = new ItemStack(new Sword());
        AttackKeyController.runDeferred();
        swap.onTick(new EventPreTick());
        check(AttackKeyController.presses == 0 && Minecraft.player.inventory.v() == 0,
                "replacing the item in a swapped slot rejects the queued hit");

        reset();
        swap = configuredSwap();
        hit(target);
        AttackKeyController.defer = true;
        begin(swap);
        swap.onTick(new EventPreTick());
        Minecraft.world = new WorldClient();
        AttackKeyController.runDeferred();
        swap.onTick(new EventPreTick());
        check(AttackKeyController.presses == 0 && Minecraft.player.inventory.v() == 0,
                "world replacement invalidates the pending swap even when player and target remain");

        reset();
        swap = configuredSwap();
        hit(target);
        AttackKeyController.defer = true;
        begin(swap);
        swap.onTick(new EventPreTick());
        EntityPlayerSP replacement = new EntityPlayerSP();
        replacement.inventory.g(2);
        Minecraft.player = replacement;
        AttackKeyController.runDeferred();
        swap.onTick(new EventPreTick());
        check(AttackKeyController.presses == 0 && replacement.inventory.v() == 2,
                "a new player never receives the previous player's restored slot");

        reset();
        swap = configuredSwap();
        hit(target);
        begin(swap);
        replacement = new EntityPlayerSP();
        replacement.inventory.g(2);
        Minecraft.player = replacement;
        swap.onDisable();
        check(replacement.inventory.v() == 2, "disable cannot restore a slot on a replacement player");
    }

    private static void testDeferredTriggerbot() {
        reset();
        EntityOtherPlayerMP first = player(20, false);
        EntityOtherPlayerMP second = player(21, false);
        Triggerbot bot = new Triggerbot();
        AttackKeyController.defer = true;
        TimerUtil.now = 100;
        hit(first);
        bot.onTick(new EventPreTick());
        bot.onTick(new EventPreTick());
        check(AttackKeyController.requests == 1 && AttackKeyController.presses == 0,
                "triggerbot keeps at most one request in flight");
        hit(second);
        AttackKeyController.runDeferred();
        check(AttackKeyController.presses == 0, "triggerbot rejects a changed deferred target");

        reset();
        bot = new Triggerbot();
        AttackKeyController.defer = true;
        TimerUtil.now = 100;
        hit(first);
        bot.onTick(new EventPreTick());
        InputEventDispatcher.getInstance().focus.focused = false;
        AttackKeyController.runDeferred();
        check(AttackKeyController.presses == 0, "triggerbot rejects deferred hits after focus loss");

        reset();
        bot = new Triggerbot();
        AttackKeyController.defer = true;
        TimerUtil.now = 100;
        hit(first);
        bot.onTick(new EventPreTick());
        bot.onDisable();
        AttackKeyController.runDeferred();
        check(AttackKeyController.presses == 0, "disable invalidates deferred triggerbot hits");

        reset();
        bot = new Triggerbot();
        AttackKeyController.defer = true;
        TimerUtil.now = 100;
        hit(first);
        bot.onTick(new EventPreTick());
        AttackKeyController.runDeferred();
        int releases = AttackKeyController.releases;
        Minecraft.player.present = false;
        bot.onTick(new EventPreTick());
        check(AttackKeyController.presses == 1 && AttackKeyController.releases > releases,
                "pressed key is released even if the player disappears");
    }

    public static void main(String[] args) throws Exception {
        testDeferredHitSwap();
        testHitSwapWorldAndInventoryChanges();
        testDeferredTriggerbot();
        testTriggerTracking();
        testTriggerActivation();
        testHitSwapTargetsAndRestoration();
        testHitSwapShieldTarget();
        System.out.println("Combat modules: " + checks + " headless integration assertions passed");
    }
}
