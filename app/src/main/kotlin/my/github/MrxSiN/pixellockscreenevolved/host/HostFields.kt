package my.github.MrxSiN.pixellockscreenevolved.host

import java.lang.reflect.Field

/** This class's own field [name], opened for reading and writing whatever its visibility. */
internal fun Class<*>.field(name: String): Field = getDeclaredField(name).apply { isAccessible = true }
