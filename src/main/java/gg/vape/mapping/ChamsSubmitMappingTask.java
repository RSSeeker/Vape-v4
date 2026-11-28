package gg.vape.mapping;

import gg.vape.Vape;
import gg.vape.asm.transform.ClassTransformer;
import gg.vape.debug.ChamsRenderHooks;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * 1.21.6+ records entity draws as submit nodes instead of writing vertices immediately, so the
 * RenderType only reaches the GPU later, when the recorded batches are flushed. Swapping the
 * RenderType when the node is submitted is therefore the only point where a player skin can be
 * routed through the depth-test-free pipeline built by {@link ChamsRenderHooks}.
 *
 * <p>Injected with ASM: javassist has to resolve the hook class by name, which fails on NeoForge
 * because the payload classes are defined by the manual-map injector instead of being reachable
 * through a class loader or a resource.</p>
 */
public class ChamsSubmitMappingTask
extends ClassTransformer {
    public ChamsSubmitMappingTask() {
        super(MappedClasses.SubmitNodeStorage);
    }

    @Override
    public void transform() {
        try {
            if (MappedClasses.FZ == null) {
                Vape.debugLog("[Chams] submit hook skipped: RenderType class unavailable");
                return;
            }
            String renderTypeInternal = Type.getInternalName(MappedClasses.FZ);
            int injected = 0;
            for (MethodNode method : this.classNode.methods) {
                if (!ChamsRenderTypeMappingTask.isInjectable(method)) {
                    continue;
                }
                int slot = ChamsRenderTypeMappingTask.renderTypeSlot(method, renderTypeInternal);
                if (slot < 0) {
                    continue;
                }
                InsnList list = new InsnList();
                list.add(new VarInsnNode(Opcodes.ALOAD, slot));
                list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, ChamsRenderHooks.INTERNAL_NAME,
                        "map", "(Ljava/lang/Object;)Ljava/lang/Object;", false));
                list.add(new TypeInsnNode(Opcodes.CHECKCAST, renderTypeInternal));
                list.add(new VarInsnNode(Opcodes.ASTORE, slot));
                method.instructions.insert(list);
                injected++;
            }
            Vape.debugLog("[Chams] submit hook injected into " + injected
                    + " method(s) of " + this.targetClass.getName());
        } catch (Throwable throwable) {
            Vape.logThrowable(throwable);
        }
    }
}
