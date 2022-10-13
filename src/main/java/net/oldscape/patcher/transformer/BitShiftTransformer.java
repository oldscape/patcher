package net.oldscape.patcher.transformer;

import net.oldscape.patcher.Transformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Map;

public class BitShiftTransformer implements Transformer {

    private static final int MASK = 0x1F;

    @Override
    public void transform(Map<String, ClassNode> classNodes) {
        classNodes.values().forEach(this::transform);
    }

    private void transform(ClassNode classNode) {
        classNode.methods.forEach(this::transform);
    }

    private void transform(MethodNode method) {
        for (var insnNode : method.instructions) {
            if (insnNode.getOpcode() != Opcodes.ISHR && insnNode.getOpcode() != Opcodes.ISHL) {
                continue;
            }
            var previous = insnNode.getPrevious();

            if (previous instanceof LdcInsnNode ldcInsnNode) {
                var constant = ldcInsnNode.cst;

                if (constant instanceof Integer integer) {
                    ldcInsnNode.cst = integer & MASK;
                }
            }
        }
    }
}