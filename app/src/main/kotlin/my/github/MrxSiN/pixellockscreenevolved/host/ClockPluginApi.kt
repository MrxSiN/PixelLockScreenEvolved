package my.github.MrxSiN.pixellockscreenevolved.host

import android.content.Context
import android.graphics.drawable.Drawable

import java.lang.reflect.AccessibleObject
import java.lang.reflect.Method

import my.github.MrxSiN.pixellockscreenevolved.clock.ClockStyle
import my.github.MrxSiN.pixellockscreenevolved.clock.FaceSize

/**
 * The SystemUI clock plugin contract, reached through the host class loader.
 *
 * Every class name, constructor and member this module relies on is looked up
 * here and nowhere else, so a SystemUI update that moves one is fixed in one
 * file. Lookups happen when this is built: a missing member fails installation
 * up front rather than the lock screen later. HOOK_NOTES.md records where each
 * signature was read from.
 */
internal class ClockPluginApi(private val classLoader: ClassLoader) {

    val registryType: Class<*> = load("com.android.systemui.shared.clocks.ClockRegistry")
    val providerType: Class<*> = load("$CLOCKS.ClockProvider")
    val controllerType: Class<*> = load("$CLOCKS.ClockController")
    val faceControllerType: Class<*> = load("$CLOCKS.ClockFaceController")
    val eventsType: Class<*> = load("$CLOCKS.ClockEvents")
    val faceEventsType: Class<*> = load("$CLOCKS.ClockFaceEvents")
    val animationsType: Class<*> = load("$CLOCKS.ClockAnimations")
    val faceLayoutType: Class<*> = load("$CLOCKS.ClockFaceLayout")

    /** Called once, right after the registry is built, in both host processes. */
    val registerListeners: Method = registryType.getDeclaredMethod("registerListeners")

    private val availableClocksField = registryType.getDeclaredField("availableClocks").accessible()
    private val registryContextField = registryType.getDeclaredField("context").accessible()

    private val metadataType = load("$CLOCKS.ClockMetadata")
    private val metadataConstructor = metadataType.getConstructor(STRING, BOOLEAN, STRING)
    private val clockInfoConstructor = load("com.android.systemui.shared.clocks.ClockRegistry\$ClockInfo")
        .getDeclaredConstructor(metadataType, providerType, load("com.android.systemui.plugins.PluginLifecycleManager"))
        .accessible()
    private val clockConfigType = load("$CLOCKS.ClockConfig")
    private val clockConfigConstructor = clockConfigType.getConstructor(STRING, STRING, STRING, BOOLEAN, BOOLEAN)
    private val clockConfigId = clockConfigType.getMethod("getId")
    private val controllerConfig = controllerType.getMethod("getConfig")
    private val tickRateType = load("$CLOCKS.ClockTickRate")
    private val faceConfigConstructor = load("$CLOCKS.ClockFaceConfig")
        .getConstructor(tickRateType, BOOLEAN, BOOLEAN, BOOLEAN)
    private val axisStyleType = load("$CLOCKS.ClockAxisStyle")
    private val axisStyleConstructor = axisStyleType.getConstructor(Map::class.java)
    private val axisStyleGet = axisStyleType.getMethod("get", STRING)
    val presetGroupType: Class<*> = load("$CLOCKS.AxisPresetConfig\$Group")
    private val presetGroupConstructor = presetGroupType.getConstructor(List::class.java, Drawable::class.java)
    private val presetGroupPresets = presetGroupType.getMethod("getPresets")
    private val presetConfigType = load("$CLOCKS.AxisPresetConfig")
    private val indexedStyleType = load("$CLOCKS.AxisPresetConfig\$IndexedStyle")
    private val presetConfigConstructor = presetConfigType.getConstructor(List::class.java, indexedStyleType)
    private val presetConfigFindStyle = presetConfigType.getMethod("findStyle", axisStyleType)
    private val pickerConfigConstructor = load("$CLOCKS.ClockPickerConfig")
        .getConstructor(STRING, STRING, STRING, Drawable::class.java, BOOLEAN, List::class.java, presetConfigType)
    private val eventListenersConstructor = load("$CLOCKS.ClockEventListeners").getConstructor()

    private val settingsType = load("$CLOCKS.ClockSettings")
    private val settingsClockId = settingsType.getMethod("getClockId")
    private val settingsSeedColor = settingsType.getMethod("getSeedColor")
    private val settingsAxes = settingsType.getMethod("getAxes")

    private val themeType = load("$CLOCKS.ThemeConfig")
    private val themeConstructor = themeType.getConstructor(BOOLEAN, Int::class.javaObjectType)
    private val themeSeedColor = themeType.getMethod("getSeedColor")
    private val themeDefaultColor = themeType.getMethod("getDefaultColor", Context::class.java)
    private val themeAodColor = themeType.getMethod("getAodColor", Context::class.java)

    private val burnInType = load("$CLOCKS.AodClockBurnInModel")
    private val burnInScale = burnInType.getMethod("getScale")
    private val burnInTranslationX = burnInType.getMethod("getTranslationX")
    private val burnInTranslationY = burnInType.getMethod("getTranslationY")

    private val previewType = load("$CLOCKS.ClockPreviewConfig")
    private val previewClockTopMargin = previewType.getMethod("getClockTopMargin")
    private val previewStatusBarHeight = previewType.getMethod("getStatusBarHeight")
    private val previewSmallClockTopPadding =
        previewType.getMethod("getSmallClockTopPadding", Int::class.java)

    private val viewIds = load("$CLOCKS.ClockViewIds")
    private val viewIdsInstance = viewIds.getField("INSTANCE").get(null)
    private val largeViewId = viewIds.getMethod("getLOCKSCREEN_CLOCK_VIEW_LARGE")
    private val smallViewId = viewIds.getMethod("getLOCKSCREEN_CLOCK_VIEW_SMALL")

    @Suppress("UNCHECKED_CAST")
    fun availableClocks(registry: Any): MutableMap<String, Any> =
        availableClocksField.get(registry) as MutableMap<String, Any>

    fun registryContext(registry: Any): Context = registryContextField.get(registry) as Context

    fun clockInfo(style: ClockStyle, provider: Any): Any =
        clockInfoConstructor.newInstance(metadata(style), provider, null)

    fun metadata(style: ClockStyle): Any = metadataConstructor.newInstance(style.id, false, null)

    fun clockConfig(style: ClockStyle): Any =
        clockConfigConstructor.newInstance(style.id, style.name, style.description, false, false)

    /**
     * A face ticking once a minute. [drawsOwnDate] tells SystemUI to leave out
     * its own date and weather line beside that face, so the date is not shown
     * twice.
     */
    fun faceConfig(drawsOwnDate: Boolean): Any = faceConfigConstructor.newInstance(
        tickRateType.getField("PER_MINUTE").get(null),
        drawsOwnDate,
        false,
        false,
    )

    /**
     * How Wallpaper & style lists [style]. A resizable style also lists one
     * axis preset per size step, which the picker draws as a stepped slider,
     * set to the step [settings] already holds.
     */
    fun pickerConfig(style: ClockStyle, context: Context, settings: Any): Any {
        val thumbnail = style.createThumbnail(context)
        val presets = if (style.isResizable) sizePresets(thumbnail, axes(settings)) else null
        return pickerConfigConstructor.newInstance(
            style.id, style.name, style.description, thumbnail, true, emptyList<Any>(), presets,
        )
    }

    private fun sizePresets(icon: Drawable, chosen: Any?): Any {
        val styles = ClockSizePresets.values.map { axisStyleConstructor.newInstance(ClockSizePresets.axesOf(it)) }
        val groups = listOf(presetGroupConstructor.newInstance(styles, icon))
        val unset = presetConfigConstructor.newInstance(groups, null)
        val current = chosen?.let { presetConfigFindStyle.invoke(unset, it) }
        return presetConfigConstructor.newInstance(groups, current)
    }

    /** The chosen size step held in [settings], or null when none was stored. */
    fun sizeStep(settings: Any): Float? = axes(settings)?.let(::sizeStepOf)

    /** The size step held in a `ClockAxisStyle`, or null when it holds none. */
    fun sizeStepOf(axisStyle: Any): Float? = axisStyleGet.invoke(axisStyle, ClockSizePresets.AXIS_KEY) as Float?

    /** Whether a preset group is the size steps this module lists. */
    fun isSizePresetGroup(group: Any): Boolean {
        val firstPreset = (presetGroupPresets.invoke(group) as List<*>).firstOrNull() ?: return false
        return sizeStepOf(firstPreset) != null
    }

    private fun axes(settings: Any): Any? = settingsAxes.invoke(settings)

    fun eventListeners(): Any = eventListenersConstructor.newInstance()

    fun clockId(settings: Any): String? = settingsClockId.invoke(settings) as String?

    /** The id a live `ClockController` reports, whoever provides it. */
    fun controllerClockId(controller: Any): String? =
        clockConfigId.invoke(controllerConfig.invoke(controller)) as String?

    fun seedColor(settings: Any): Int? = settingsSeedColor.invoke(settings) as Int?

    fun theme(isDarkTheme: Boolean, seedColor: Int?): Any = themeConstructor.newInstance(isDarkTheme, seedColor)

    /** [theme] with its dark flag replaced and its seed colour kept. */
    fun withDarkTheme(theme: Any, isDarkTheme: Boolean): Any =
        theme(isDarkTheme, themeSeedColor.invoke(theme) as Int?)

    fun colors(theme: Any, context: Context): ThemeColors = ThemeColors(
        normal = themeDefaultColor.invoke(theme, context) as Int,
        doze = themeAodColor.invoke(theme, context) as Int,
    )

    fun burnIn(model: Any): BurnIn = BurnIn(
        scale = burnInScale.invoke(model) as Float,
        translationX = burnInTranslationX.invoke(model) as Float,
        translationY = burnInTranslationY.invoke(model) as Float,
    )

    fun previewTops(config: Any): PreviewTops = PreviewTops(
        largeClock = previewClockTopMargin.invoke(config) as Int,
        smallClock = previewSmallClockTopPadding.invoke(config, previewStatusBarHeight.invoke(config)) as Int,
    )

    fun viewId(size: FaceSize): Int = when (size) {
        FaceSize.LARGE -> largeViewId
        FaceSize.SMALL -> smallViewId
    }.invoke(viewIdsInstance) as Int

    private fun load(name: String): Class<*> = Class.forName(name, false, classLoader)

    private fun <T : AccessibleObject> T.accessible(): T = apply { isAccessible = true }

    private companion object {
        const val CLOCKS = "com.android.systemui.plugins.keyguard.ui.clocks"
        val STRING = String::class.java
        val BOOLEAN = Boolean::class.java
    }
}

/** The two colours a theme gives a clock: awake, and in always-on display. */
internal data class ThemeColors(val normal: Int, val doze: Int)

/** How far always-on display moves the clock to spare the panel. */
internal data class BurnIn(val scale: Float, val translationX: Float, val translationY: Float)

/** Where the picker preview puts the top of each clock size, in pixels. */
internal data class PreviewTops(val largeClock: Int, val smallClock: Int)
