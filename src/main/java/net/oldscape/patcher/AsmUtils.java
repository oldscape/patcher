package net.oldscape.patcher;

import net.oldscape.patcher.transformer.PacketVariantMapper.MethodInfo;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Predicate;

public final class AsmUtils {

    private AsmUtils() {
    }

    public static MethodNode findClinit(ClassNode classNode) {
        return findFirstMethodMatching(classNode, methodNode -> Objects.equals(methodNode.name, "<clinit>"));
    }

    public static MethodNode findMethodByDescriptor(ClassNode classNode, Type returnType, Type... argTypes) {
        return findFirstMethodMatching(classNode, methodNode -> signatureMatches(methodNode, returnType, argTypes));
    }

    public static MethodNode findMethod(ClassNode classNode, String name, Type returnType, Type... argTypes) {
        return findFirstMethodMatching(classNode, methodNode -> methodNode.name.equals(name) && signatureMatches(methodNode, returnType, argTypes));
    }

    public static MethodNode findMethod(ClassNode classNode, MethodInfo methodInfo) {
        return findFirstMethodMatching(classNode, methodNode -> methodNode.name.equals(methodInfo.name()) && methodNode.desc.equals(methodInfo.desc()));
    }

    public static MethodNode findFirstMethodMatching(ClassNode classNode, Predicate<MethodNode> predicate) {
        return classNode.methods.stream()
                                .filter(predicate)
                                .findFirst()
                                .orElse(null);
    }

    public static FieldNode findField(ClassNode classNode, Predicate<FieldNode> predicate) {
        return classNode.fields.stream()
                               .filter(predicate)
                               .findFirst()
                               .orElse(null);
    }

    private static boolean signatureMatches(MethodNode methodNode, Type returnType, Type... argTypes) {
        var methodReturnType = Type.getReturnType(methodNode.desc);
        var methodArgTypes = Type.getArgumentTypes(methodNode.desc);
        return Objects.equals(methodReturnType, returnType) && Arrays.equals(methodArgTypes, argTypes);
    }

    public static int extractIntValue(AbstractInsnNode insnNode) {

        if (insnNode instanceof IntInsnNode intInsn) {
            return ((IntInsnNode) insnNode).operand;
        } else if (insnNode.getOpcode() >= Opcodes.ICONST_M1 && insnNode.getOpcode() <= Opcodes.ICONST_5) {
            return insnNode.getOpcode() - Opcodes.ICONST_M1 - 1;
        } else {
            throw new IllegalStateException("Unhandled insn type " + insnNode.getClass() + ", opcode " + insnNode.getOpcode());
        }
    }
}