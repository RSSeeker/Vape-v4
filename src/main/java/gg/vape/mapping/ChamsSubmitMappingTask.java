package gg.vape.mapping;

import gg.vape.Vape;
import gg.vape.debug.ChamsRenderHooks;
import javassist.CtClass;
import javassist.CtMethod;

/**
 * 1.21.6+ records entity draws as submit nodes instead of writing vertices immediately, so the
 * RenderType only reaches the GPU later, when the recorded batches are flushed. Swapping the
 * RenderType when the node is submitted is therefore the only point where a player skin can be
 * routed through the depth-test-free pipeline built by {@link ChamsRenderHooks}.
 */
public class ChamsSubmitMappingTask
extends JavassistMappingTask {
    public ChamsSubmitMappingTask() {
        super(MappedClasses.SubmitNodeStorage);
    }

    @Override
    public void transform() {
        try {
            String renderTypeName = ChamsRenderHooks.renderTypeClassName();
            String hooksClassName = ChamsRenderHooks.class.getName();
            if (renderTypeName == null) {
                Vape.debugLog("[Chams] submit injection skipped: RenderType class unavailable");
                return;
            }
            CtClass target = this.x();
            if (target == null) {
                Vape.debugLog("[Chams] submit injection skipped: could not parse SubmitNodeStorage bytecode");
                return;
            }
            int injected = 0;
            for (CtMethod method : target.getDeclaredMethods()) {
                CtClass[] parameterTypes = method.getParameterTypes();
                int index = -1;
                for (int i = 0; i < parameterTypes.length; i++) {
                    if (parameterTypes[i].getName().equals(renderTypeName)) {
                        index = i;
                        break;
                    }
                }
                if (index < 0) {
                    continue;
                }
                String variable = "$" + (index + 1);
                method.insertBefore("{ if (" + hooksClassName + ".enabled) { "
                        + variable + " = (" + renderTypeName + ") " + hooksClassName + ".map(" + variable + "); } }");
                injected++;
            }
            Vape.debugLog("[Chams] submit hook injected into " + injected + " method(s) of " + target.getName());
        } catch (Throwable throwable) {
            Vape.logThrowable(throwable);
        }
    }

}
