package net.oldscape.patcher;

import org.objectweb.asm.tree.ClassNode;

import java.util.List;
import java.util.Map;

public interface Transformer {

    void transform(Map<String, ClassNode> classNodes);
}