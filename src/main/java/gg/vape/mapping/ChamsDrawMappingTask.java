package gg.vape.mapping;

import gg.vape.Vape;
import gg.vape.asm.transform.ClassTransformer;
import gg.vape.debug.ChamsRenderHooks;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Diagnostic and safety net for the 1.21.6+ chams path: entity draws are flushed through
 * {@code RenderType.draw(MeshData)}, and the flush is the point where the pipeline used by the GPU
 * can still be corrected.
 *
 * <p>Injected with ASM: javassist has to resolve the hook class by name, which fails on NeoForge
 * because the payload classes are defined by the manual-map injector instead of being reachable
 * through a class loader or a resource.</p>
 */
public class ChamsDrawMappingTask
extends ClassTransformer {
    public ChamsDrawMappingTask() {
        super(MappedClasses.FZ);
    }

    @Override
    public void transform() {
        try {
            int injected = 0;
            for (MethodNode method : this.classNode.methods) {
                if (!ChamsRenderTypeMappingTask.isInjectable(method)) {
                    continue;
                }
                Type[] argumentTypes = Type.getArgumentTypes(method.desc);
                if (argumentTypes.length != 1 || argumentTypes[0].getSort() == Type.METHOD) {
                    continue;
                }
                if (argumentTypes[0].getSort() < Type.ARRAY) {
                    continue;
                }
                if ((method.access & Opcodes.ACC_STATIC) != 0) {
                    continue;
                }
                if (!Type.VOID_TYPE.equals(Type.getReturnType(method.desc))) {
                    continue;
                }
                InsnList entry = new InsnList();
                entry.add(new VarInsnNode(Opcodes.ALOAD, 0));
                entry.add(new VarInsnNode(Opcodes.ALOAD, 1));
                entry.add(new MethodInsnNode(Opcodes.INVOKESTATIC, ChamsRenderHooks.INTERNAL_NAME,
                        "onDraw", "(Ljava/lang/Object;Ljava/lang/Object;)V", false));
                method.instructions.insert(entry);

                for (AbstractInsnNode instruction = method.instructions.getFirst();
                     instruction != null; instruction = instruction.getNext()) {
                    if (instruction.getOpcode() == Opcodes.RETURN) {
                        method.instructions.insertBefore(instruction,
                                new MethodInsnNode(Opcodes.INVOKESTATIC, ChamsRenderHooks.INTERNAL_NAME,
                                        "afterDraw", "()V", false));
                    }
                }
                injected++;
            }
            Vape.debugLog("[Chams] draw hook injected into " + injected
                    + " method(s) of " + this.targetClass.getName());
        } catch (Throwable throwable) {
            Vape.logThrowable(throwable);
        }
    }
}
