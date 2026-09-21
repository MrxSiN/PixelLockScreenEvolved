package my.github.MrxSiN.pixellockscreenevolved.host

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Typeface
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

import my.github.MrxSiN.pixellockscreenevolved.diagnostics.ContractOutcome
import my.github.MrxSiN.pixellockscreenevolved.diagnostics.ContractResult
import my.github.MrxSiN.pixellockscreenevolved.diagnostics.Feature
import my.github.MrxSiN.pixellockscreenevolved.diagnostics.FeatureAvailability
import my.github.MrxSiN.pixellockscreenevolved.diagnostics.FeatureStatus
import my.github.MrxSiN.pixellockscreenevolved.diagnostics.Host
import my.github.MrxSiN.pixellockscreenevolved.diagnostics.HostSnapshot
import my.github.MrxSiN.pixellockscreenevolved.diagnostics.HostStartupReport
import my.github.MrxSiN.pixellockscreenevolved.diagnostics.SafeModeAnswer
import my.github.MrxSiN.pixellockscreenevolved.diagnostics.SnapshotStore
import my.github.MrxSiN.pixellockscreenevolved.diagnostics.VerifiedBuilds

/**
 * The Compatibility & diagnostics page, opened from Wallpaper & style's Lock
 * screen list: what is running, which features each host can take, and what of
 * each host every feature found.
 *
 * It reads what each host wrote as it last started ([HostSnapshot]), so after a
 * monthly update it says what that update changed rather than what the module
 * was written against.
 */
internal class PickerDiagnosticsDialog(private val moduleInfo: ApplicationInfo) {

    fun show(context: Context) {
        val store = SnapshotStore(context.contentResolver)
        val availability = FeatureAvailability.current(store, HostStartupReport.bootedAt())
        val page = Page(context)

        page.heading("Pixel Lock Screen Evolved", first = true)
        page.value("Module", "Active", Tone.POSITIVE)
        page.value("Version", moduleVersion(context))

        page.heading("System")
        page.value("Android", androidVersion())
        page.value(
            "Build",
            Build.ID,
            if (VerifiedBuilds.isVerified(Build.ID)) Tone.NEUTRAL else Tone.WARNING,
            if (VerifiedBuilds.isVerified(Build.ID)) "" else "Untested build: each feature is checked on its own and turned off if it fails",
        )
        for (host in Host.entries) {
            val snapshot = availability.snapshots[host]
            if (snapshot == null) page.value(host.title, "Not loaded", Tone.ERROR) else page.value(host.title, snapshot.hostVersion)
        }
        availability.snapshots.values.firstOrNull()?.let {
            page.value("libxposed API", it.xposedApiVersion.toString())
            page.value("Framework", "${it.frameworkName} ${it.frameworkVersion}")
        }

        page.heading("Feature compatibility")
        for (feature in Feature.entries) {
            val (value, tone) = when (availability.status(feature)) {
                FeatureStatus.AVAILABLE -> "${ContractResult.PASS} Compatible" to Tone.POSITIVE
                FeatureStatus.HOST_MANAGED -> "${ContractResult.PASS} SystemUI managed" to Tone.POSITIVE
                FeatureStatus.INCOMPATIBLE -> "${ContractResult.FAIL} Unavailable" to Tone.ERROR
                FeatureStatus.HELD_BY_SAFE_MODE -> "${ContractResult.WARN} Safe Mode" to Tone.WARNING
                FeatureStatus.FAULTED -> "${ContractResult.WARN} Stopped" to Tone.WARNING
            }
            page.value(feature.title, value, tone, availability.summary(feature))
        }

        val snapshots = availability.snapshots.values
        page.heading("Host compatibility")
        val requiredTotal = snapshots.sumOf { it.report.requiredTotal }
        val missingRequired = snapshots.sumOf { it.report.missingRequired }
        val fallbacks = snapshots.sumOf { it.report.fallbacks }
        val optionalMissing = snapshots.sumOf { it.report.optionalMissing }
        val unavailableFeatures = Feature.entries.count { availability.status(it) == FeatureStatus.INCOMPATIBLE }
        page.value(
            "Required APIs",
            "${snapshots.sumOf { it.report.requiredResolved }} / $requiredTotal",
            if (missingRequired == 0) Tone.POSITIVE else Tone.WARNING,
        )
        page.value("Fallback signatures", fallbacks.toString(), if (fallbacks == 0) Tone.NEUTRAL else Tone.WARNING)
        page.value("Optional APIs unavailable", optionalMissing.toString(), Tone.NEUTRAL)
        page.value("Unavailable features", unavailableFeatures.toString(), if (unavailableFeatures == 0) Tone.NEUTRAL else Tone.ERROR)

        for (snapshot in snapshots) contracts(page, snapshot)

        val held = snapshots.filter { it.held.isNotEmpty() }
        AlertDialog.Builder(context)
            .setTitle("Compatibility & diagnostics")
            .setView(ScrollView(context).apply { addView(page.root) })
            .setPositiveButton("Close", null)
            .setNeutralButton("Copy report") { _, _ -> copy(context, report(context, availability)) }
            .apply {
                if (held.isNotEmpty()) {
                    setNegativeButton("Restore features") { _, _ -> held.forEach { store.request(it.host, SafeModeAnswer.RESTORE) } }
                }
            }
            .show()
    }

    /** One row per compatibility probe of [snapshot]'s host, the ones that need attention first. */
    private fun contracts(page: Page, snapshot: HostSnapshot) {
        page.heading("API probes: ${snapshot.host.title} ${snapshot.hostVersion}")
        snapshot.report.results
            .sortedBy { it.outcome == ContractOutcome.RESOLVED }
            .forEach { result ->
                val tone = when {
                    result.outcome == ContractOutcome.RESOLVED -> Tone.POSITIVE
                    result.outcome == ContractOutcome.FALLBACK || !result.contract.isRequired -> Tone.WARNING
                    else -> Tone.ERROR
                }
                page.value(result.contract.signature.describe(), result.mark, tone, result.explanation.joinToString("\n"))
            }
    }

    private fun report(context: Context, availability: FeatureAvailability): String = buildString {
        appendLine("Pixel Lock Screen Evolved ${moduleVersion(context)}")
        appendLine("Android ${androidVersion()}, build ${Build.ID}")
        availability.snapshots.values.firstOrNull()?.let {
            appendLine("libxposed API ${it.xposedApiVersion}, ${it.frameworkName} ${it.frameworkVersion}")
        }
        for (host in Host.entries) {
            appendLine()
            appendLine(availability.snapshots[host]?.format() ?: "${host.title}: not loaded")
        }
    }.trimEnd()

    private fun moduleVersion(context: Context): String = runCatching {
        context.packageManager.getPackageArchiveInfo(moduleInfo.sourceDir, 0)?.versionName
    }.getOrNull() ?: "?"

    /** "17 QPR1": the release, and the quarterly platform release when it is one. */
    private fun androidVersion(): String {
        val quarterly = Build.VERSION.SDK_INT_FULL % SDK_FULL_BASE
        val release = Build.VERSION.RELEASE_OR_PREVIEW_DISPLAY
        return if (quarterly > 0) "$release QPR$quarterly" else release
    }

    private fun copy(context: Context, text: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("Pixel Lock Screen Evolved compatibility report", text))
    }

    private enum class Tone { NEUTRAL, POSITIVE, WARNING, ERROR }

    /** The page's rows: headings, and a title with its value at the end and an optional line below. */
    private class Page(private val context: Context) {

        private val dark = context.resources.configuration.isNightModeActive
        private val padding = dp(24)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, dp(8), padding, dp(8))
        }

        fun heading(text: String, first: Boolean = false) {
            root.addView(text(text, SIZE_HEADING_SP, color(android.R.color.system_accent1_600, android.R.color.system_accent1_200)).apply {
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                setPadding(0, if (first) 0 else dp(20), 0, dp(6))
            })
        }

        fun value(title: String, value: String, tone: Tone = Tone.NEUTRAL, summary: String = "") {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
                setPadding(0, dp(4), 0, dp(4))
            }
            val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            texts.addView(text(title, SIZE_BODY_SP, primary()))
            if (summary.isNotBlank()) texts.addView(text(summary, SIZE_SUMMARY_SP, secondary()))
            row.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(
                text(value, SIZE_BODY_SP, toneColor(tone)).apply {
                    gravity = Gravity.END
                    setPadding(dp(12), 0, 0, 0)
                },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT),
            )
            root.addView(row)
        }

        private fun text(text: String, sizeSp: Float, color: Int) = TextView(context).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            setTextColor(color)
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        }

        private fun toneColor(tone: Tone): Int = when (tone) {
            Tone.NEUTRAL -> primary()
            Tone.POSITIVE -> color(android.R.color.system_accent1_600, android.R.color.system_accent1_200)
            Tone.WARNING -> if (dark) WARNING_DARK else WARNING_LIGHT
            Tone.ERROR -> if (dark) ERROR_DARK else ERROR_LIGHT
        }

        private fun primary() = color(android.R.color.system_neutral1_900, android.R.color.system_neutral1_50)

        private fun secondary() = color(android.R.color.system_neutral2_700, android.R.color.system_neutral2_200)

        private fun color(light: Int, dark: Int): Int = context.getColor(if (this.dark) dark else light)

        private fun dp(value: Int): Int =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics).toInt()

        private companion object {
            const val SIZE_HEADING_SP = 14f
            const val SIZE_BODY_SP = 16f
            const val SIZE_SUMMARY_SP = 14f

            /** Material 3's error colour, light and dark. */
            const val ERROR_LIGHT = 0xFFB3261E.toInt()
            const val ERROR_DARK = 0xFFF2B8B5.toInt()

            /** An amber that reads as a warning beside them. */
            const val WARNING_LIGHT = 0xFF8C5000.toInt()
            const val WARNING_DARK = 0xFFFFB95C.toInt()
        }
    }

    private companion object {
        /** `Build.VERSION.SDK_INT_FULL` is the major version times this, plus the minor. */
        const val SDK_FULL_BASE = 100_000
    }
}
