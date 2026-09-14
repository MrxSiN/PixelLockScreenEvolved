package my.github.MrxSiN.pixellockscreenevolved.host

import java.util.Collections
import java.util.WeakHashMap

/**
 * What Wallpaper & style's process knows about this module's clocks while a
 * person edits one.
 *
 * The picker has no place of its own for a clock size, so the size slider the
 * module adds keeps its choice here until Apply, shows it on every preview
 * clock the picker has built, and hands it to the setting Apply writes.
 */
internal class PickerClockState(private val api: ClockPluginApi) {

    /** The picker's clock registry, once it exists. */
    @Volatile
    var registry: Any? = null

    /** The size step moved to since the picker opened, or null if the slider was not touched. */
    @Volatile
    var chosenSizeStep: Float? = null
        private set

    private val sizeListeners = mutableListOf<(Float) -> Unit>()

    private val previews = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<ClockControllerAdapter, Boolean>()))

    fun track(preview: ClockControllerAdapter) {
        previews.add(preview)
    }

    /** Calls [listener] with every size step chosen from now on. */
    fun onSizeStepChosen(listener: (Float) -> Unit) {
        sizeListeners.add(listener)
    }

    /** Records [step], shows it on every preview clock, and tells whoever listens. */
    fun chooseSizeStep(step: Float) {
        chosenSizeStep = step
        synchronized(previews) { previews.toList() }.forEach { it.previewSizeStep(step) }
        sizeListeners.forEach { it(step) }
    }

    /** Whether the size step moved to differs from the one the setting in effect stores. */
    fun isSizeStepEdited(): Boolean {
        val chosen = chosenSizeStep ?: return false
        return chosen != (storedAxis(ClockAxes.SIZE_KEY) ?: 0f)
    }

    /** Forgets a size moved to earlier, as a newly opened picker should. */
    fun reset() {
        chosenSizeStep = null
    }

    /** The value the setting in effect stores for [key], or null when it stores none. */
    fun storedAxis(key: String): Float? = registry?.let(api::registrySettings)?.let { api.axis(it, key) }

    /** The id of the clock the setting in effect chooses. */
    fun storedClockId(): String? = registry?.let(api::registrySettings)?.let(api::clockId)
}
