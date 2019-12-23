package dev.openrs2.deob.transform

import dev.openrs2.asm.MemberDesc
import dev.openrs2.asm.classpath.ClassPath
import dev.openrs2.asm.classpath.Library
import dev.openrs2.asm.transform.Transformer
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode

class FieldOrderTransformer : Transformer() {
    public override fun transformClass(classPath: ClassPath, library: Library, clazz: ClassNode): Boolean {
        sortFields(clazz, CONSTRUCTOR, Opcodes.PUTFIELD)
        sortFields(clazz, STATIC_CONSTRUCTOR, Opcodes.PUTSTATIC)
        return false
    }

    companion object {
        private const val CONSTRUCTOR = "<init>"
        private const val STATIC_CONSTRUCTOR = "<clinit>"

        private fun sortFields(clazz: ClassNode, ctorName: String, opcode: Int) {
            val ctor = clazz.methods.find { it.name == ctorName } ?: return

            val fields = mutableMapOf<MemberDesc, Int>()
            var index = 0
            for (insn in ctor.instructions) {
                if (insn.opcode != opcode) {
                    continue
                }

                val putfield = insn as FieldInsnNode
                if (putfield.owner != clazz.name) {
                    continue
                }

                val desc = MemberDesc(putfield)
                if (!fields.containsKey(desc)) {
                    fields[desc] = index++
                }
            }

            clazz.fields.sortBy { fields.getOrDefault(MemberDesc(it), -1) }
        }
    }
}
