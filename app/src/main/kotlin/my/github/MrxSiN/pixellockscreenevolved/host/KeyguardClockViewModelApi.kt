package my.github.MrxSiN.pixellockscreenevolved.host

import java.lang.reflect.Field

/**
 * SystemUI's `KeyguardClockViewModel`, which says which clock the lock screen
 * shows and at which size, reached through its class loader.
 */
internal class KeyguardClockViewModelApi(classLoader: ClassLoader) {

    private val type = Class.forName("com.android.systemui.keyguard.ui.viewmodel.KeyguardClockViewModel", false, classLoader)
    private val currentClockFlow = type.field("currentClock")
    private val largeClockVisibleFlow = type.field("isLargeClockVisible")

    /** The `ClockController` shown now, or null while none is. */
    fun currentClock(clockViewModel: Any): Any? = flowValue(clockViewModel, currentClockFlow)

    /** Whether the large clock is shown, rather than the small one. */
    fun isLargeClockVisible(clockViewModel: Any): Boolean = flowValue(clockViewModel, largeClockVisibleFlow) as Boolean? ?: true

    /** The current value of a `StateFlow` field, read through the flow's own `getValue`. */
    private fun flowValue(owner: Any, flow: Field): Any? =
        flow.get(owner)?.let { it.javaClass.getMethod("getValue").invoke(it) }

    private companion object {
        fun Class<*>.field(name: String): Field = getDeclaredField(name).apply { isAccessible = true }
    }
}
