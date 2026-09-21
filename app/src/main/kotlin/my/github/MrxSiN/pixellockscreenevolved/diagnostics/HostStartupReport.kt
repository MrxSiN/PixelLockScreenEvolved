package my.github.MrxSiN.pixellockscreenevolved.diagnostics

import android.app.Application
import android.app.Instrumentation
import android.content.Context
import android.os.Build
import android.os.SystemClock

import my.github.MrxSiN.pixellockscreenevolved.core.Logger
import my.github.MrxSiN.pixellockscreenevolved.hook.Hooks
import my.github.MrxSiN.pixellockscreenevolved.hook.HostPatch
import my.github.MrxSiN.pixellockscreenevolved.safemode.SafeModeController
import my.github.MrxSiN.pixellockscreenevolved.safemode.SafeModeStore

/**
 * Once the host has built its application, writes down what this start found
 * ([HostSnapshot]), and carries out whatever is answered for Safe Mode from then on.
 *
 * The contract report was made before any feature installed, when the host had
 * no context to write it through. The first start on a new host version or
 * system build also logs the whole report, readable with
 * `adb logcat -s PixelLockScreenEvolved`, where a bug report can pick it up.
 *
 * A feature stopped for failing ([FeatureFaults]) is written down again at once,
 * so the picker greys it out without waiting for the next start.
 *
 * [onSnapshot] hears every snapshot written, so the host can show what it says.
 * After a hot reload the application already exists ([application]), and the
 * report is made at once.
 */
class HostStartupReport(
    private val hooks: Hooks,
    private val host: Host,
    private val report: ContractReport,
    private val safeMode: SafeModeStore,
    private val faults: FeatureFaults,
    private val framework: Framework,
    private val logger: Logger,
    private val onSnapshot: (Context, HostSnapshot) -> Unit = { _, _ -> },
    private val application: Context? = null,
) : HostPatch {

    /** The Xposed framework this module is loaded by. */
    data class Framework(val apiVersion: Int, val name: String, val version: String)

    override fun install(classLoader: ClassLoader) {
        application?.let {
            start(it)
            return
        }
        val applicationCreated = Instrumentation::class.java.getMethod("callApplicationOnCreate", Application::class.java)
        hooks.after(applicationCreated) { _, args ->
            val application = args.firstOrNull() as? Application ?: return@after
            if (application.packageName == host.packageName) start(application)
        }
    }

    private fun start(context: Context) {
        val snapshots = SnapshotStore(context.contentResolver)
        val previous = snapshots.read(host)
        val current = publish(context, snapshots)
        if (previous?.hostVersion != current.hostVersion || previous.build != current.build) {
            val unavailable = Feature.of(host).count { !report.isCompatible(it) }
            val summary = "${host.title} ${current.hostVersion} on ${current.build}: ${report.requiredResolved} / " +
                "${report.requiredTotal} required APIs, ${report.fallbacks} by fallback, $unavailable unavailable features"
            if (unavailable == 0) logger.info(summary) else logger.warn(summary)
            current.format().lineSequence().forEach(logger::info)
        }

        faults.addListener { publish(context, snapshots) }
        val controller = SafeModeController(safeMode, logger, onChanged = { publish(context, snapshots) })
        snapshots.observeRequests(host, controller::answer)
    }

    private fun publish(context: Context, snapshots: SnapshotStore): HostSnapshot {
        val snapshot = HostSnapshot(
            host = host,
            hostVersion = versionOf(context),
            build = Build.ID,
            bootedAt = bootedAt(),
            xposedApiVersion = framework.apiVersion,
            frameworkName = framework.name,
            frameworkVersion = framework.version,
            report = report,
            held = safeMode.disabled(),
            isAnswered = safeMode.isAnswered(),
            faulted = faults.tripped(),
        )
        runCatching { snapshots.write(snapshot) }.onFailure { logger.warn("The compatibility report could not be stored", it) }
        onSnapshot(context, snapshot)
        return snapshot
    }

    private fun versionOf(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toString()
    }.getOrDefault("?")

    companion object {
        /** When the device booted, in wall-clock milliseconds. */
        fun bootedAt(): Long = System.currentTimeMillis() - SystemClock.elapsedRealtime()
    }
}
