package net.oldscape.patcher.transformer;

import net.oldscape.patcher.Transformer;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.objectweb.asm.Opcodes.BIPUSH;
import static org.objectweb.asm.Opcodes.IADD;
import static org.objectweb.asm.Opcodes.ICONST_0;
import static org.objectweb.asm.Opcodes.ICONST_1;
import static org.objectweb.asm.Opcodes.ICONST_2;
import static org.objectweb.asm.Opcodes.ICONST_3;
import static org.objectweb.asm.Opcodes.ICONST_4;
import static org.objectweb.asm.Opcodes.ICONST_5;
import static org.objectweb.asm.Opcodes.ICONST_M1;
import static org.objectweb.asm.Opcodes.IDIV;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.IREM;
import static org.objectweb.asm.Opcodes.ISTORE;
import static org.objectweb.asm.Opcodes.ISUB;

/**
 * Removes unused math expressions such as: <br/>
 * <code>int var1 = -79 % ((var0 - -49) / 42);</code><br/>
 * <br/>
 * This is achieved by keeping track of local ILOAD/ISTORE then checking if a local var is never loaded,
 * if one is never loaded, the last 7 instructions are taken and checked if all are any of: <br/>
 * <code>ILOAD, IADD, IDIV, ISUB, IREM, BIPUSH, ICONST_0, ICONST_1, ICONST_2, ICONST_3, ICONST_4, ICONST_5, ICONST_M1</code><br/>
 * <p>
 * These math expressions are always either 5 or 7 instructions long.
 */
public class RemoveUnusedMath implements Transformer {

    @Override
    public void transform(Map<String, ClassNode> classNodes) {
        for (var classNode : classNodes.values()) {
            classNode.methods.forEach(this::transform);
        }
    }

    private void transform(MethodNode methodNode) {
        var instructions = methodNode.instructions;
        var storedLocalVars = new HashMap<Integer, VarInsnNode>();
        var loadedLocalVars = new HashSet<Integer>();

        // map all method loads & stores (integers only)
        for (var instruction : instructions) {
            if (instruction instanceof VarInsnNode varInsnNode) {
                switch (varInsnNode.getOpcode()) {
                    case ILOAD -> loadedLocalVars.add(varInsnNode.var);
                    case ISTORE -> storedLocalVars.put(varInsnNode.var, varInsnNode);
                }
            }
        }

        // check if any stored locals are never loaded
        for (var localVarEntry : storedLocalVars.entrySet()) {
            if (!loadedLocalVars.contains(localVarEntry.getKey())) {
                removeMathExpression(instructions, localVarEntry.getValue());
            }
        }
    }

    private void removeMathExpression(InsnList instructions, VarInsnNode store) {
        var insnNodes = takeLast(store, 8);

        for (var insnNode : insnNodes) {
            if (!validOpcode(insnNode.getOpcode())) {
                return;
            }
        }
        insnNodes.forEach(instructions::remove);
    }

    private List<AbstractInsnNode> takeLast(AbstractInsnNode start, int count) {
        var counter = 0;
        var insns = new ArrayList<AbstractInsnNode>(count);
        var current = start;

        while (counter++ < count) {
            if (current == null) {
                return List.of();
            }
            insns.add(current);
            current = current.getPrevious();
        }
        return insns;
    }

    private boolean validOpcode(int opcode) {
        return switch (opcode) {
            // iload is a valid opcode as it is used to load the opaque predicate
            case ISTORE, ILOAD, IADD, IDIV, ISUB, IREM, BIPUSH, ICONST_0, ICONST_1, ICONST_2, ICONST_3, ICONST_4, ICONST_5, ICONST_M1 -> true;
            default -> false;
        };
    }
}
