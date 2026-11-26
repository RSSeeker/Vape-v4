package gg.vape.input;

import gg.vape.event.impl.EventTickBase;
import gg.vape.event.impl.SyntheticAttackRequestEvent;
import gg.vape.input.KeyBindingHelper;
import gg.vape.module.Mod;
import gg.vape.wrapper.impl.ForgeVersion;
import gg.vape.wrapper.impl.KeyBinding;
import gg.vape.wrapper.impl.Minecraft;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class AttackKeyController {
    public static void releaseAttackKey() {
        if (AttackKeyController.shouldRunOnClientTick()) {
            EventTickBase.PRE_TICK_EXECUTOR.execute(AttackKeyController::releaseAttackKeyNow);
            return;
        }
        AttackKeyController.releaseAttackKeyNow();
    }

    private static void releaseAttackKeyNow() {
        KeyBinding attackKey = Minecraft.a_w3_0_S().F();
        KeyBindingHelper.updateKeyBinding(attackKey, false, false);
    }

    public static boolean requestSyntheticAttack(Mod module) {
        if (AttackKeyController.shouldRunOnClientTick()) {
            EventTickBase.PRE_TICK_EXECUTOR.execute(() -> AttackKeyController.requestSyntheticAttackNow(module));
            return true;
        }
        return AttackKeyController.requestSyntheticAttackNow(module);
    }

    /**
     * A queued request is not the same as a completed attack. Recheck the
     * caller's state at execution time and report whether the key was pressed,
     * including event cancellation. Completion may run inline or on the tick
     * executor; callers must invalidate old requests on disable/world changes.
     */
    public static void requestValidatedSyntheticAttack(Mod module, BooleanSupplier stillValid,
                                                        Consumer<Boolean> completed) {
        Runnable request = () -> {
            boolean pressed = false;
            try {
                if (stillValid.getAsBoolean()) {
                    SyntheticAttackRequestEvent event = new SyntheticAttackRequestEvent(module);
                    if (!event.fire() && stillValid.getAsBoolean()) {
                        AttackKeyController.pressAttackKeyNow();
                        pressed = true;
                    }
                }
            } finally {
                completed.accept(pressed);
            }
        };
        if (AttackKeyController.shouldRunOnClientTick()) {
            EventTickBase.PRE_TICK_EXECUTOR.execute(request);
        } else {
            request.run();
        }
    }

    private static boolean requestSyntheticAttackNow(Mod module) {
        SyntheticAttackRequestEvent event = new SyntheticAttackRequestEvent(module);
        if (event.fire()) {
            return false;
        }
        AttackKeyController.pressAttackKeyNow();
        return true;
    }

    public static void pressAttackKey() {
        if (AttackKeyController.shouldRunOnClientTick()) {
            EventTickBase.PRE_TICK_EXECUTOR.execute(AttackKeyController::pressAttackKeyNow);
            return;
        }
        AttackKeyController.pressAttackKeyNow();
    }

    private static void pressAttackKeyNow() {
        KeyBindingHelper.updateKeyBinding(Minecraft.a_w3_0_S().F(), true, true);
    }

    private static boolean shouldRunOnClientTick() {
        if (!ForgeVersion.MC_26_2.d()) {
            return false;
        }
        Thread ownerThread = EventTickBase.PRE_TICK_EXECUTOR.getOwnerThread();
        return ownerThread == null || !Thread.currentThread().equals(ownerThread);
    }
}
