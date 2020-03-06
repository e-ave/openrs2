package dev.openrs2.deob.transform

import com.github.michaelbull.logging.InlineLogger
import dev.openrs2.asm.classpath.ClassPath
import dev.openrs2.asm.classpath.Library
import dev.openrs2.asm.transform.Transformer
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TypeInsnNode

class EmptyClassTransformer : Transformer() {
    private val emptyClasses = mutableSetOf<String>()
    private val referencedClasses = mutableSetOf<String>()

    override fun preTransform(classPath: ClassPath) {
        emptyClasses.clear()
        referencedClasses.clear()
    }

    override fun transformClass(classPath: ClassPath, library: Library, clazz: ClassNode): Boolean {
        if (clazz.access and Opcodes.ACC_INTERFACE == 0 && clazz.fields.isEmpty() && clazz.methods.isEmpty()) {
            emptyClasses.add(clazz.name)
        }

        if (clazz.superName != null) {
            referencedClasses.add(clazz.superName)
        }

        return false
    }

    private fun addTypeReference(type: Type) {
        when (type.sort) {
            Type.OBJECT -> referencedClasses.add(type.internalName)
            Type.ARRAY -> addTypeReference(type.elementType)
            Type.METHOD -> {
                type.argumentTypes.forEach(::addTypeReference)
                addTypeReference(type.returnType)
            }
        }
    }

    override fun transformField(classPath: ClassPath, library: Library, clazz: ClassNode, field: FieldNode): Boolean {
        addTypeReference(Type.getType(field.desc))
        return false
    }

    override fun preTransformMethod(
        classPath: ClassPath,
        library: Library,
        clazz: ClassNode,
        method: MethodNode
    ): Boolean {
        addTypeReference(Type.getType(method.desc))
        return false
    }

    override fun transformCode(classPath: ClassPath, library: Library, clazz: ClassNode, method: MethodNode): Boolean {
        for (insn in method.instructions) {
            when (insn) {
                is LdcInsnNode -> {
                    val cst = insn.cst
                    if (cst is Type) {
                        addTypeReference(cst)
                    }
                }
                is TypeInsnNode -> referencedClasses.add(insn.desc)
            }
        }

        return false
    }

    override fun postTransform(classPath: ClassPath) {
        var removed = 0

        for (name in emptyClasses.subtract(referencedClasses)) {
            for (library in classPath.libraries) {
                if (library.remove(name) != null) {
                    removed++
                }
            }
        }

        logger.info { "Removed $removed unused classes" }
    }

    companion object {
        private val logger = InlineLogger()
    }
}