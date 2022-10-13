package net.oldscape.patcher.transformer;

import net.oldscape.patcher.AsmUtils;
import net.oldscape.patcher.Transformer;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;

import java.math.BigInteger;
import java.security.spec.RSAPublicKeySpec;
import java.util.Map;

public class RSAPubKeyReplacer implements Transformer {

    private final RSAPublicKeySpec replacement;
    private final RSAKeyFields keyFields;

    private RSAPubKeyReplacer(RSAPublicKeySpec replacement, RSAKeyFields keyFields) {
        this.replacement = replacement;
        this.keyFields = keyFields;
    }

    public static RSAPubKeyReplacer create(RSAPublicKeySpec replacement, RSAKeyFields keyFields) {
        return new RSAPubKeyReplacer(replacement, keyFields);
    }

    @Override
    public void transform(Map<String, ClassNode> classNodes) {
        replaceBigInteger(classNodes.get(keyFields.modulusClass()), keyFields.modulusField(), replacement.getModulus());
        replaceBigInteger(classNodes.get(keyFields.exponentClass()), keyFields.exponentField(), replacement.getPublicExponent());
    }

    private void replaceBigInteger(ClassNode classNode, String field, BigInteger replacement) {
        if (classNode == null) {
            throw new IllegalStateException("classNode is null");
        }
        var clinit = AsmUtils.findClinit(classNode);

        if (clinit == null) {
            throw new IllegalStateException("clinit is null");
        }
        var bigIntegerDesc = Type.getDescriptor(BigInteger.class);

        for (var insn : clinit.instructions) {
            if (insn instanceof FieldInsnNode fieldInsn
                    && fieldInsn.name.equals(field)
                    && fieldInsn.desc.equals(bigIntegerDesc)
            ) {
                if (insn.getPrevious().getPrevious() instanceof LdcInsnNode ldcInsn) {
                    ldcInsn.cst = replacement.toString();
                }
            }
        }
    }

    public record RSAKeyFields(String modulus, String exponent) {

        public String modulusClass() {
            return modulus.substring(0, modulus.indexOf("."));
        }

        public String modulusField() {
            return modulus.substring(modulus.indexOf(".") + 1);
        }

        public String exponentClass() {
            return exponent.substring(0, exponent.indexOf("."));
        }

        public String exponentField() {
            return exponent.substring(exponent.indexOf(".") + 1);
        }
    }
}