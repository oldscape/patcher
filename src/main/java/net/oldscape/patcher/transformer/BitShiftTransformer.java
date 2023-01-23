package net.oldscape.patcher.transformer;

import net.oldscape.patcher.Transformer;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Map;

import static org.objectweb.asm.Opcodes.ISHL;
import static org.objectweb.asm.Opcodes.ISHR;
import static org.objectweb.asm.Opcodes.IUSHR;
import static org.objectweb.asm.Opcodes.LSHL;
import static org.objectweb.asm.Opcodes.LSHR;
import static org.objectweb.asm.Opcodes.LUSHR;

public class BitShiftTransformer implements Transformer {

    private static final int MASK = 0x1F;
    private static final int LONG_MASK = 0x3F;

    @Override
    public void transform(Map<String, ClassNode> classNodes) {
        classNodes.values().forEach(this::transform);
    }

    private void transform(ClassNode classNode) {
        classNode.methods.forEach(this::transform);
    }

    private void transform(MethodNode method) {
        for (var insnNode : method.instructions) {
            switch (insnNode.getOpcode()) {
                case ISHR, ISHL, IUSHR -> maskIntLdc(insnNode.getPrevious());
                case LSHR, LSHL, LUSHR -> maskLongLdc(insnNode.getPrevious());
            }
        }
    }

    private void maskIntLdc(AbstractInsnNode insnNode) {
        if (insnNode instanceof LdcInsnNode ldcInsnNode) {
            var constant = ldcInsnNode.cst;

            if (constant instanceof Integer integer) {
                ldcInsnNode.cst = integer & MASK;
            }
        }
    }

    private void maskLongLdc(AbstractInsnNode insnNode) {
        if (insnNode instanceof LdcInsnNode ldcInsnNode) {
            var constant = ldcInsnNode.cst;

            if (constant instanceof Long l) {
                ldcInsnNode.cst = l & LONG_MASK;
            }
        }
    }
}