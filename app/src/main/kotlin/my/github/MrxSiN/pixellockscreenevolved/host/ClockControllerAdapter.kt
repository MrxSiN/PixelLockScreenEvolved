package my.github.MrxSiN.pixellockscreenevolved.host

import android.content.Context

import java.io.PrintWriter

import my.github.MrxSiN.pixellockscreenevolved.clock.ClockStyle
import my.github.MrxSiN.pixellockscreenevolved.clock.FaceSize

/**
 * A [ClockStyle] presented to SystemUI as one `ClockController`: a large and a
 * small face, the clock-wide events, and the configuration that names it.
 */
internal class ClockControllerAdapter(
    private val api: ClockPluginApi,
    private val proxies: InterfaceProxy,
    private val style: ClockStyle,
    private val context: Context,
    settings: Any,
) {

    private val initialTheme = api.theme(isDarkTheme = true, seedColor = api.seedColor(settings))

    private val large = face(FaceSize.LARGE)
    private val small = face(FaceSize.SMALL)
    private val faces = listOf(large, small)

    init {
        val font = api.axis(settings, ClockAxes.FONT_KEY)
        val sizeStep = api.axis(settings, ClockAxes.SIZE_KEY)
        faces.forEach {
            it.setFont(font)
            it.setSizeStep(sizeStep)
        }
    }

    /** Shows [step] on the large face at once, as the picker's size slider moves. */
    fun previewSizeStep(step: Float) {
        large.setSizeStep(step)
    }

    /** Anything that changes how the time is written redraws both faces. */
    private val refreshFaces: MethodHandler = { _ -> faces.forEach { it.refresh() } }

    private val events = proxies.create(
        api.eventsType,
        mapOf(
            "onTimeZoneChanged" to refreshFaces,
            "onTimeFormatChanged" to refreshFaces,
            "onLocaleChanged" to refreshFaces,
            "onAlarmDataChanged" to IgnoreCall,
            "onWeatherDataChanged" to IgnoreCall,
            "onZenDataChanged" to IgnoreCall,
        ),
    )

    private val config = api.clockConfig(style)
    private val eventListeners = api.eventListeners()

    fun create(): Any = proxies.create(
        api.controllerType,
        mapOf(
            "getSmallClock" to { _ -> small.controller },
            "getLargeClock" to { _ -> large.controller },
            "getEvents" to { _ -> events },
            "getConfig" to { _ -> config },
            "getEventListeners" to { _ -> eventListeners },
            "initialize" to { args -> initialize(args[0] as Boolean, args[1] as Float) },
            "dump" to { args -> (args[0] as PrintWriter).println("${style.id}: ${style.name}") },
        ),
    )

    private fun initialize(isDarkTheme: Boolean, dozeFraction: Float) {
        faces.forEach {
            it.setDarkTheme(isDarkTheme)
            it.setDoze(dozeFraction)
            it.refresh()
        }
    }

    private fun face(size: FaceSize) = ClockFaceAdapter(
        api = api,
        proxies = proxies,
        face = style.createFace(context, size),
        size = size,
        fontCount = style.fontNames.size,
        context = context,
        initialTheme = initialTheme,
    )
}
