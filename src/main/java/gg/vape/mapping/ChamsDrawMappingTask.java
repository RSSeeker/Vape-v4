package gg.vape.mapping;

import gg.vape.Vape;
import gg.vape.debug.ChamsRenderHooks;
import javassist.CtClass;
import javassist.CtMethod;

/**
 * Diagnostic and safety net for the 1.21.6+ chams path.
 *
 * <p>Entity draws are recorded as submit nodes and flushed later through
 * {@code RenderType.draw(MeshData)}. Hooking that call tells us whether the custom
 * RenderType produced by {@link ChamsRenderHooks} really reaches the GPU or whether the
 * flush path ignores it (which would explain an invisible player).</p>
 */
public class ChamsDrawMappingTask
extends JavassistMappingTask {
    public ChamsDrawMappingTask() {
        super(MappedClasses.FZ);
    }

    @Override
    public void transform() {
        try {
            String hooksClassName = ChamsRenderHooks.class.getName();
            CtClass target = this.x();
            if (target == null) {
                Vape.debugLog("[Chams] draw injection skipped: could not parse RenderType bytecode");
                return;
            }
            int injected = 0;
            for (CtMethod method : target.getDeclaredMethods()) {
                CtClass[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length != 1 || parameterTypes[0].isPrimitive() || !method.getReturnType().equals(CtClass.voidType)) {
                    continue;
                }
                method.insertBefore("{ " + hooksClassName + ".onDraw(this, $1); }");
                method.insertAfter("{ " + hooksClassName + ".afterDraw(); }");
                injected++;
            }
            Vape.debugLog("[Chams] draw hook injected into " + injected + " method(s) of " + target.getName());
        } catch (Throwable throwable) {
            Vape.logThrowable(throwable);
        }
    }

}
