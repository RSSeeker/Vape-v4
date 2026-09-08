package gg.vape.mapping;

import gg.vape.Vape;
import gg.vape.event.impl.EventClickMouse;
import gg.vape.event.impl.EventMouseOverUpdate;
import gg.vape.event.impl.EventPostTick;
import gg.vape.event.impl.EventPreTick;
import gg.vape.mapping.JavassistMappingTask;
import gg.vape.mapping.MappedClasses;
import gg.vape.mapping.MappingMethod;
import gg.vape.mapping.MinecraftTickCallbackExprEditor;
import gg.vape.wrapper.impl.ForgeVersion;
import javassist.CtBehavior;
import javassist.expr.MethodCall;

public class MinecraftTickEventMappingTask
extends JavassistMappingTask {
    private static Exception a(Exception exception) {
        return exception;
    }

    public MinecraftTickEventMappingTask() {
        super(MappedClasses.uP);
    }

    @Override
    public void transform() {
        try {
            MappingMethod mappingMethod = Vape.INSTANCE.getMappings().U.a;
            CtBehavior ctBehavior = this.F(Vape.INSTANCE.getMappings().U.v);
            boolean[] blArray = new boolean[]{false};
            ctBehavior.instrument(new MinecraftTickCallbackExprEditor(this, mappingMethod, blArray));
            if (!blArray[0]) {
                MappingMethod gameLoop = Vape.INSTANCE.getMappings().U.v;
                this.O(gameLoop, EventPreTick.class, "", "");
                this.j(gameLoop, EventPostTick.class, "", "");
            }
            if (ForgeVersion.MC_1_20_6.d()) {
                MappingMethod mappingMethod2 = Vape.INSTANCE.getMappings().U.q;
                this.O(mappingMethod2, EventClickMouse.class, "", "false");
                if (ForgeVersion.MC_26_1.d() && Vape.INSTANCE.getMappings().U.i != null && !Vape.INSTANCE.getMappings().U.i.hasResolutionFailed()) {
                    this.k(Vape.INSTANCE.getMappings().U.i, EventMouseOverUpdate.class, "$1");
                }
            }
        }
        catch (Exception exception) {
            throw new RuntimeException("Failed to create hooks", exception);
        }
    }

    public static boolean y(MethodCall methodCall, MappingMethod mappingMethod) {
        return MinecraftTickEventMappingTask.c(methodCall, mappingMethod);
    }
}
