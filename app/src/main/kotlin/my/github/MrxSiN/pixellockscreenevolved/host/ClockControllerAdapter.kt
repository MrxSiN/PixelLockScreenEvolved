package my.github.MrxSiN.pixellockscreenevolved.host

import android.content.Context

import java.io.PrintWriter

import my.github.MrxSiN.pixellockscreenevolved.clock.ClockStyle
import my.github.MrxSiN.pixellockscreenevolved.clock.FaceSize
import my.github.MrxSiN.pixellockscreenevolved.clock.WeatherClockFace

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

    /** Both faces, for whatever needs the one on screen. */
    val faces: List<ClockFaceAdapter> = listOf(large, small)

    init {
        val width = api.axis(settings, ClockAxes.WIDTH_KEY)
        val sizeStep = api.axis(settings, ClockAxes.SIZE_KEY)
        faces.forEach {
            it.setWidth(width)
            it.setSizeStep(sizeStep)
        }
        large.setPosition(api.axis(settings, ClockAxes.POSITION_KEY), api.axis(settings, ClockAxes.POSITION_SHARE_KEY))
    }

    /** The large face, the one the depth effect draws in front of. */
    val largeFace: ClockFaceAdapter get() = large

    /** The small face, shown while notifications are. */
    val smallFace: ClockFaceAdapter get() = small

    /** Shows [step] on the large face at once, as the picker's size slider moves. */
    fun previewSizeStep(step: Float) {
        large.setSizeStep(step)
    }

    /** Shows [position] on the large face at once, as the picker's position controls change. */
    fun previewPosition(position: ClockPosition) {
        large.clockLayout.position = position
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
            "onWeatherDataChanged" to { args ->
                val weather = api.weather(args.firstOrNull())
                faces.forEach { (it.viewFace as? WeatherClockFace)?.setWeather(weather) }
            },
            "onZenDataChanged" to IgnoreCall,
        ),
    )

    private val config = api.clockConfig(style)
    private val eventListeners = api.eventListeners()

    /** The `ClockController` SystemUI holds for this clock. */
    val controller: Any by lazy { createController() }

    private fun createController(): Any = proxies.create(
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
        sizeSteps = style.sizeSteps,
        movable = style.isMovable,
        context = context,
        initialTheme = initialTheme,
    )
}
