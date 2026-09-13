package my.github.MrxSiN.pixellockscreenevolved.host

import android.content.Context

import my.github.MrxSiN.pixellockscreenevolved.clock.ClockStyle

/**
 * Every [ClockStyle] presented to a clock registry as one `ClockProvider`,
 * the same shape SystemUI's own clocks and clock plugins take.
 */
internal class ClockProviderAdapter(
    private val api: ClockPluginApi,
    private val proxies: InterfaceProxy,
    private val styles: List<ClockStyle>,
    private val registryContext: Context,
    private val onClockCreated: (ClockControllerAdapter) -> Unit = {},
) {

    fun create(): Any = proxies.create(
        api.providerType,
        mapOf(
            "initialize" to IgnoreCall,
            "getClocks" to { _ -> styles.map(api::metadata) },
            "createClock" to { args -> createClock(args[0] as Context, requireNotNull(args[1])) },
            "getClockPickerConfig" to { args ->
                val settings = requireNotNull(args[0])
                styleFor(settings)?.let { api.pickerConfig(it, registryContext, settings) }
            },
        ),
    )

    private fun createClock(context: Context, settings: Any): Any? =
        styleFor(settings)?.let { style ->
            ClockControllerAdapter(api, proxies, style, context, settings).also(onClockCreated).create()
        }

    private fun styleFor(settings: Any): ClockStyle? {
        val id = api.clockId(settings)
        return styles.firstOrNull { it.id == id }
    }
}
