package my.github.MrxSiN.pixellockscreenevolved.host

import android.content.Context
import android.content.pm.ApplicationInfo

import my.github.MrxSiN.pixellockscreenevolved.clock.ClockStyle
import my.github.MrxSiN.pixellockscreenevolved.core.Logger
import my.github.MrxSiN.pixellockscreenevolved.diagnostics.Feature
import my.github.MrxSiN.pixellockscreenevolved.diagnostics.FeatureAvailability
import my.github.MrxSiN.pixellockscreenevolved.diagnostics.FeatureGate
import my.github.MrxSiN.pixellockscreenevolved.diagnostics.HostStartupReport
import my.github.MrxSiN.pixellockscreenevolved.diagnostics.SnapshotStore
import my.github.MrxSiN.pixellockscreenevolved.hook.Hooks
import my.github.MrxSiN.pixellockscreenevolved.hook.HostPatch

/**
 * Everything Wallpaper & style's process needs, sharing one [PickerClockState]:
 * the clock styles in its registry, the width presets and size slider, the
 * Apply button the size enables, the setting Apply writes, this module's rows
 * in the Lock screen list with the compatibility page, the previews collapsing
 * no further for them, a divider between this module's clocks and the Pixel's,
 * and Safe Mode's question.
 *
 * Each feature is installed only where [gate] allows it. The clock styles are
 * not offered at all while SystemUI reports it cannot draw them.
 */
internal class PickerClockPatches(
    private val hooks: Hooks,
    /** Hooks that answer for one feature, so a failing one is stopped alone. */
    private val hooksFor: (Feature) -> Hooks,
    private val styles: List<ClockStyle>,
    private val gate: FeatureGate,
    private val moduleInfo: ApplicationInfo,
    private val logger: Logger,
    /** The picker's clock registry, told of once it exists, and the one there already was before a hot reload. */
    private val onRegistry: (Any) -> Unit = {},
    private val reloadedRegistry: Any? = null,
) : HostPatch {

    override fun install(classLoader: ClassLoader) {
        val state = try {
            PickerClockState(ClockPluginApi(classLoader))
        } catch (error: ReflectiveOperationException) {
            logger.warn("Clock plugin API not found; no clock style can be added", error)
            return
        }

        val optionHooks = hooksFor(Feature.PICKER_LOCK_SCREEN_OPTIONS)
        val clockHooks = hooksFor(Feature.PICKER_CLOCKS)
        val sizeHooks = hooksFor(Feature.PICKER_CLOCK_SIZE)
        val lockScreenOptions = PickerLockScreenOptions(optionHooks, PickerDiagnosticsDialog(moduleInfo), logger)
        val clocksDrawn = { context: Context ->
            FeatureAvailability.current(SnapshotStore(context.contentResolver), HostStartupReport.bootedAt())
                .isAvailable(Feature.CLOCK_STYLES)
                .also { if (!it) logger.warn("SystemUI cannot draw this module's clocks now; they are not offered") }
        }
        listOf(
            gate.gate(
                Feature.PICKER_CLOCKS,
                ClockRegistryInjector(
                    clockHooks, styles, logger,
                    onRegistry = {
                        state.registry = it
                        onRegistry(it)
                    },
                    onClockCreated = state::track,
                    isOffered = clocksDrawn,
                    reloadedRegistry = reloadedRegistry,
                ),
            ),
            gate.gate(Feature.PICKER_CLOCKS, PickerClockSection(clockHooks, styles, logger)),
            gate.gate(Feature.PICKER_CLOCK_SIZE, PickerClockSliders(sizeHooks, styles, state, logger)),
            gate.gate(Feature.PICKER_CLOCK_SIZE, PickerClockSettings(sizeHooks, styles, state, logger)),
            gate.gate(Feature.PICKER_CLOCK_SIZE, PickerSizeEdit(sizeHooks, state, logger)),
            gate.gate(Feature.PICKER_LOCK_SCREEN_OPTIONS, lockScreenOptions),
            gate.gate(Feature.PICKER_LOCK_SCREEN_OPTIONS, PickerPreviewCollapse(optionHooks, logger, addedHeight = lockScreenOptions::addedHeight)),
            PickerSafeModePrompt(hooks, logger),
        ).forEach { patch -> runCatching { patch.install(classLoader) }.onFailure { logger.warn("A picker patch failed to install", it) } }
    }
}
