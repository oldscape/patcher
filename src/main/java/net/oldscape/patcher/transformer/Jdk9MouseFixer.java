package net.oldscape.patcher.transformer;

import net.oldscape.patcher.Transformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Map;

import static net.oldscape.patcher.AsmUtils.findFirstMethodMatching;

/*
 * This transformer fixes mouse right button clicks on jdks verions 9 and above.
 */
public class Jdk9MouseFixer implements Transformer {

    @Override
    public void transform(Map<String, ClassNode> classNodes) {
        classNodes.values().forEach(this::transform);
    }

    private void transform(ClassNode classNode) {
        var mousePressed = findFirstMethodMatching(classNode, method -> method.name.equals("mousePressed"));

        if (mousePressed != null) {
            patchMousePressed(mousePressed);
        }
    }

    private void patchMousePressed(MethodNode methodNode) {
        var iterator = methodNode.instructions.iterator();

        while (iterator.hasNext()) {
            AbstractInsnNode next = iterator.next();

            if (next instanceof MethodInsnNode methodInsn && methodInsn.name.equals("isMetaDown")) {
                iterator.remove();
                iterator.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "javax/swing/SwingUtilities", "isRightMouseButton", "(Ljava/awt/event/MouseEvent;)Z", false));
            }
        }
    }
}