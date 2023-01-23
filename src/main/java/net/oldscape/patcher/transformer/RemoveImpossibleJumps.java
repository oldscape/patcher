package net.oldscape.patcher.transformer;

import net.oldscape.patcher.Transformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Removes impossible jump conditions, there's two variations to this obfuscation: <br/>
 * - The first is where the constant field is loaded into a local variable and then the local is used in jump conditions:
 * <pre>
 * getstatic client.ob:boolean
 * istore 2
 *
 * iload 2
 * ifeq L10
 * </pre>
 * <p>
 * - The second where the constant field is used directly in the jump condition:
 * <pre>
 * getstatic client.ob:boolean
 * ifeq L9
 * </pre>
 * The jump always point to a random label of the method's body also the local var idx is always the last.
 */
public class RemoveImpossibleJumps implements Transformer {

    private static final Logger LOGGER = LoggerFactory.getLogger(RemoveImpossibleJumps.class);

    @Override
    public void transform(Map<String, ClassNode> classNodes) {
        for (var classNode : classNodes.values()) {
            classNode.methods.forEach(this::transform);
        }
    }

    private void transform(MethodNode methodNode) {
        var instructions = methodNode.instructions;

        for (var insn : instructions) {
            if (insn instanceof FieldInsnNode fieldInsnNode && isLoadingDummy(fieldInsnNode)) {
                var next = insn.getNext();

                if (next instanceof VarInsnNode varInsn) {
                    cleanLocalVar(methodNode, varInsn.var);
                } else if (next instanceof JumpInsnNode jumpInsn) {
                    instructions.remove(fieldInsnNode);
                    jumpInsn.setOpcode(Opcodes.GOTO);
                } else {
                    LOGGER.warn("Found dummy load but we don't know how to handle {} instructions", next.getClass().getSimpleName());
                }
            }
        }
    }

    private boolean isLoadingDummy(FieldInsnNode insnNode) {
        return insnNode.owner.equals("client") && insnNode.name.equals("ob") && insnNode.desc.equals("Z");
    }

    private void cleanLocalVar(MethodNode methodNode, int localVarIndex) {
        var instructions = methodNode.instructions;
        for (var insnNode : instructions.toArray()) {
            if (insnNode instanceof VarInsnNode varInsn) {
                if (varInsn.getOpcode() == Opcodes.ISTORE && varInsn.var == localVarIndex) {
                    instructions.remove(varInsn.getPrevious());
                    instructions.remove(varInsn);
                }

                if (varInsn.getOpcode() == Opcodes.ILOAD && varInsn.var == localVarIndex) {
                    if (varInsn.getNext() instanceof JumpInsnNode jumpInsn) {
                        jumpInsn.setOpcode(Opcodes.GOTO);
                    }
                    instructions.remove(varInsn);
                }
            }
        }
    }
}