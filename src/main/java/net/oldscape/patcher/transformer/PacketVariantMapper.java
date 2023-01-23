package net.oldscape.patcher.transformer;

import net.oldscape.patcher.Transformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static net.oldscape.patcher.AsmUtils.findMethod;

public class PacketVariantMapper implements Transformer {

    private static final Logger LOGGER = LoggerFactory.getLogger(PacketVariantMapper.class);

    private final Map<String, List<MethodVariants>> variants;

    public PacketVariantMapper(Map<String, List<MethodVariants>> variants) {
        this.variants = variants;
    }

    public static PacketVariantMapper create(Map<String, List<MethodVariants>> variants) {
        return new PacketVariantMapper(variants);
    }

    @Override
    public void transform(Map<String, ClassNode> classNodes) {
        for (var entry : variants.entrySet()) {
            var classNode = classNodes.get(entry.getKey());

            if (classNode == null) {
                LOGGER.warn("No classNode found for mapping {}", entry.getKey());
                continue;
            }
            var methodVariants = entry.getValue();

            for (var methodVariant : methodVariants) {
                methodVariant.variants()
                             .forEach(methodInfo -> mapVariantTo(classNode, methodInfo, methodVariant.method));
            }
        }
    }

    private void mapVariantTo(ClassNode owner, MethodInfo variant, MethodInfo original) {
        var originalMethod = findMethod(owner, original);

        if (originalMethod == null) {
            LOGGER.warn("Couldn't find original method {}.{}{}", owner.name, original.name, original.desc);
            return;
        }
        var argumentTypes = Type.getArgumentTypes(original.desc);
        var variantArgumentTypes = Type.getArgumentTypes(variant.desc);
        var returnType = Type.getReturnType(original.desc);

        var argMapping = variant.argMapping;
        if (argumentTypes.length > 2 && argMapping == null) {
            LOGGER.warn("Variant mapping failed as variant {}{} has more than 1 argument and no arg mapping was provided", variant.name, variant.desc);
            return;
        }
        var insns = new InsnList();
        insns.add(new VarInsnNode(Opcodes.ALOAD, 0));

        if (argMapping == null) {
            // can only deduce parameter order when we have two args methods
            var variantArgIdx = 0;
            for (int argIdx = 0; argIdx < argumentTypes.length; argIdx++) {
                if (original.dummyIdx == argIdx) {
                    insertDummy(insns, original.dummyValue);
                } else {
                    // skip the variant dummy parameter, if any
                    if (variantArgIdx == variant.dummyIdx) {
                        variantArgIdx++;
                    }
                    var opcode = variantArgumentTypes[variantArgIdx].getOpcode(Opcodes.ILOAD);
                    insns.add(new VarInsnNode(opcode, variantArgIdx + 1)); // + 1 as 0 is 'this'
                    variantArgIdx++;
                }
            }
        } else {
            for (int argIdx = 0; argIdx < argumentTypes.length; argIdx++) {
                if (original.dummyIdx == argIdx) {
                    insertDummy(insns, original.dummyValue);
                } else {
                    // skip the variant dummy parameter, if any
                    var variantArgIdx = argMapping[argIdx];
                    var opcode = variantArgumentTypes[variantArgIdx].getOpcode(Opcodes.ILOAD);
                    insns.add(new VarInsnNode(opcode, variantArgIdx + 1)); // + 1 as 0 is 'this'
                }
            }
        }
        insns.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, owner.name, original.name, original.desc));
        insns.add(new InsnNode(returnType.getOpcode(Opcodes.IRETURN)));

        var variantMethod = findMethod(owner, variant);

        if (variantMethod == null) {
            LOGGER.warn("Couldn't find variant method {}.{}{}", owner.name, variant.name, variant.desc);
            return;
        }
        variantMethod.instructions = insns;
        variantMethod.tryCatchBlocks.clear();
    }

    private void insertDummy(InsnList list, Object dummy) {
        if (dummy instanceof Boolean bool) {
            list.add(new InsnNode(bool ? Opcodes.ICONST_1 : Opcodes.ICONST_0));
        } else if (dummy instanceof Integer integer) {
            AbstractInsnNode insn;
            if (integer >= -1 && integer <= 5) {
                insn = new InsnNode(Opcodes.ICONST_0 + integer);
            } else if (integer >= Byte.MIN_VALUE && integer <= Byte.MAX_VALUE) {
                insn = new IntInsnNode(Opcodes.BIPUSH, integer);
            } else if (integer >= Short.MIN_VALUE && integer <= Short.MAX_VALUE) {
                insn = new IntInsnNode(Opcodes.SIPUSH, integer);
            } else {
                insn = new LdcInsnNode(integer);
            }
            list.add(insn);
        } else if (dummy instanceof Long cst) {
            if (cst == 0 || cst == 1) {
                list.add(new InsnNode(Opcodes.LCONST_0 + cst.intValue()));
            } else {
                list.add(new LdcInsnNode(cst));
            }
        }
    }

    public record MethodVariants(String name, MethodInfo method, List<MethodInfo> variants) {

    }

    public record MethodInfo(String name, String desc, int dummyIdx, Object dummyValue, int[] argMapping) {

    }
}
