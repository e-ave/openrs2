package dev.openrs2.deob.remap

import dev.openrs2.asm.classpath.Library
import org.objectweb.asm.commons.Remapper
import org.objectweb.asm.commons.SimpleRemapper

object PrefixRemapper {
    @JvmStatic
    fun create(library: Library, prefix: String): Remapper {
        val mapping = mutableMapOf<String, String>()

        for (clazz in library) {
            if (TypedRemapper.EXCLUDED_CLASSES.contains(clazz.name)) {
                mapping[clazz.name] = clazz.name
            } else {
                mapping[clazz.name] = prefix + clazz.name
            }
        }

        return SimpleRemapper(mapping)
    }
}
