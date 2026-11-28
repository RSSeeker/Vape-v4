package gg.vape.mapping;

import gg.vape.Vape;
import gg.vape.asm.transform.ClassTransformer;
import gg.vape.debug.ChamsRenderHooks;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Routes every RenderType handed to MultiBufferSource$BufferSource#getBuffer through
 * ChamsRenderHooks.map so player skins can be rendered with a depth-test-free pipeline.
 *
 * <p>Injected with ASM: javassist has to resolve the hook class by name, which fails on NeoForge
 * because the payload classes are defined by the manual-map injector instead of being reachable
 * through a class loader or a resource.</p>
 */
public class ChamsRenderTypeMappingTask
extends ClassTransformer {
    public ChamsRenderTypeMappingTask() {
        super(MappedClasses.q6);
    }

    @Override
    public void transform() {
        try {
            if (MappedClasses.FZ == null) {
                Vape.debugLog("[Chams] getBuffer hook skipped: RenderType class unavailable");
                return;
            }
            String renderTypeInternal = Type.getInternalName(MappedClasses.FZ);
            int injected = 0;
            for (MethodNode method : this.classNode.methods) {
                int slot = renderTypeSlot(method, renderTypeInternal);
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
            Vape.debugLog("[Chams] getBuffer argument hook injected into " + injected
                    + " method(s) of " + this.targetClass.getName());
        } catch (Throwable throwable) {
            Vape.logThrowable(throwable);
        }
    }

    static boolean isInjectable(MethodNode method) {
        return (method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0
                && method.instructions != null && method.instructions.size() > 0;
    }

    static int renderTypeSlot(MethodNode method, String renderTypeInternal) {
        boolean isStatic = (method.access & Opcodes.ACC_STATIC) != 0;
        int slot = isStatic ? 0 : 1;
        Type[] argumentTypes = Type.getArgumentTypes(method.desc);
        for (Type argumentType : argumentTypes) {
            if (argumentType.getSort() == Type.OBJECT
                    && renderTypeInternal.equals(argumentType.getInternalName())) {
                return slot;
            }
            slot += argumentType.getSize();
        }
        return -1;
    }
}
