package my.github.MrxSiN.pixellockscreenevolved.diagnostics

import android.os.Build

import java.io.File

import kotlin.concurrent.thread

import my.github.MrxSiN.pixellockscreenevolved.core.Logger
import my.github.MrxSiN.pixellockscreenevolved.hook.HostPatch

/**
 * Writes down the running host's build ([HostDescriptor]) when a developer asks
 * for it, so the build can join the recorded builds the tests check every
 * contract against.
 *
 * Asked for with `adb shell setprop debug.pixel_lock_screen_evolved.capture 1`
 * before the host starts; `scripts/capture-host-fixture.sh` asks, restarts the
 * hosts and pulls what they wrote. The description is taken through the same
 * class loader the contract check reads, so it is the host exactly as the check
 * sees it. Nothing is read or written unless asked.
 */
class HostDescriptorCapture(
    private val host: Host,
    private val types: HostTypes,
    private val file: File,
    private val logger: Logger,
) : HostPatch {

    override fun install(classLoader: ClassLoader) {
        if (!isRequested()) return
        // Off the host's start: a description reads a few hundred classes.
        thread(name = "PixelLockScreenEvolved-capture") {
            runCatching {
                // Written aside and moved in whole, so a reader never sees half of it.
                val written = File(file.path + ".tmp")
                written.writeText(HostDescriptor.write(Build.ID, host, types, HostContracts.classesNamed(host), HostSymbols.current))
                check(written.renameTo(file)) { "Could not move $written to $file" }
                logger.info("Described ${host.packageName} on ${Build.ID} in $file")
            }.onFailure { logger.warn("Could not describe ${host.packageName}", it) }
        }
    }

    private fun isRequested(): Boolean = runCatching {
        Class.forName("android.os.SystemProperties").getMethod("get", String::class.java).invoke(null, PROPERTY) == "1"
    }.getOrDefault(false)

    companion object {
        const val PROPERTY = "debug.pixel_lock_screen_evolved.capture"

        /** Where each host writes its description, in its own device-protected storage. */
        const val FILE = "pixel_lock_screen_evolved_host_descriptor.txt"
    }
}
