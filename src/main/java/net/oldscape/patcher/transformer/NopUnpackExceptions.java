package net.oldscape.patcher.transformer;

import net.oldscape.patcher.Transformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LabelNode;

import java.util.Map;

import static net.oldscape.patcher.AsmUtils.findMethod;

/**
 * Removes the thrown T3 exceptions, these occur when the client fails to unpack a packed file.
 * This mostly happens for mapsquares we don't have keys for.
 */
public class NopUnpackExceptions implements Transformer {

    @Override
    public void transform(Map<String, ClassNode> classNodes) {
        var classNode = classNodes.get("he");
        var methodNode = findMethod(classNode, "a", Type.BOOLEAN_TYPE, Type.getType(int[].class), Type.BYTE_TYPE, Type.INT_TYPE);

        var tryCatchBlock = methodNode.tryCatchBlocks.get(0);

        var newHandler = new InsnList();
        var newHandlerLabel = new LabelNode();

        newHandler.add(newHandlerLabel);
        newHandler.add(new InsnNode(Opcodes.POP));
        newHandler.add(new InsnNode(Opcodes.ICONST_0));
        newHandler.add(new InsnNode(Opcodes.IRETURN));

        tryCatchBlock.handler = newHandlerLabel;
        methodNode.instructions.add(newHandler);
    }
}
