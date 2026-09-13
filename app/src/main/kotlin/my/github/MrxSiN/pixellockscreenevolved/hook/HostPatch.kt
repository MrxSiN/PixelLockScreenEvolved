package my.github.MrxSiN.pixellockscreenevolved.hook

/**
 * One change this module makes to a host process.
 *
 * The entry point only decides which patches a process gets; what each one
 * changes, and how it finds its way into the host, is its own business.
 */
fun interface HostPatch {

    /** Installs into the host whose classes [classLoader] loads. */
    fun install(classLoader: ClassLoader)
}
