package gg.vape.mapping;

import gg.vape.Vape;
import gg.vape.debug.ChamsRenderHooks;
import javassist.CtClass;
import javassist.CtMethod;

/**
 * Routes every RenderType handed to MultiBufferSource$BufferSource#getBuffer through
 * ChamsRenderHooks.map so player skins can be rendered with a depth-test-free pipeline.
 */
public class ChamsRenderTypeMappingTask
extends JavassistMappingTask {
    public ChamsRenderTypeMappingTask() {
        super(MappedClasses.q6);
    }

    @Override
    public void transform() {
        try {
            String renderTypeName = ChamsRenderHooks.renderTypeClassName();
            String hooksClassName = ChamsRenderHooks.class.getName();
            if (renderTypeName == null) {
                Vape.debugLog("[Chams] injection skipped: RenderType class unavailable");
                return;
            }
            CtClass target = this.x();
            if (target == null) {
                Vape.debugLog("[Chams] injection skipped: could not parse BufferSource bytecode");
                return;
            }
            int injected = 0;
            for (CtMethod method : target.getDeclaredMethods()) {
                CtClass[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length != 1) {
                    continue;
                }
                if (!parameterTypes[0].getName().equals(renderTypeName)) {
                    continue;
                }
                method.insertBefore("{ $1 = (" + renderTypeName + ") " + hooksClassName + ".map($1); }");
                injected++;
            }
            Vape.debugLog("[Chams] getBuffer argument hook injected into " + injected + " method(s) of " + target.getName());
        } catch (Throwable throwable) {
            Vape.logThrowable(throwable);
        }
    }

}
