package my.github.MrxSiN.pixellockscreenevolved.host

import android.content.Context
import android.graphics.drawable.Drawable

import java.lang.reflect.AccessibleObject
import java.lang.reflect.Method

import my.github.MrxSiN.pixellockscreenevolved.clock.ClockStyle
import my.github.MrxSiN.pixellockscreenevolved.clock.ClockWeather
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

    /**
     * Called once the registry is ready to answer for its clocks, before it
     * first reads the chosen one, in both host processes.
     *
     * Wallpaper & style keeps `registerListeners`, which runs first. Android 17
     * QPR1 dropped that method from SystemUI's shrunk copy, which reaches its
     * clocks through `querySettings` instead; the registry calls it once as it
     * is built, and again whenever the chosen clock changes.
     */
    val registryReady: Method = REGISTRY_READY
        .firstNotNullOfOrNull { name ->
            registryType.declaredMethods.firstOrNull { it.name == name && it.parameterCount == 0 }
        }
        ?: throw NoSuchMethodException("ClockRegistry.${REGISTRY_READY.joinToString(" or ")}")

    /**
     * How a face reaches the screen on a build whose lock screen is a scene of
     * composed elements, or null on one that still adds and constrains a view.
     */
    val elements: LockscreenElementApi? =
        runCatching { LockscreenElementApi(classLoader) }.getOrNull()

    /**
     * Makes the registry tell its listeners the current clock changed, so each
     * builds it again from the provider now registered. Null where the registry
     * has no such call.
     */
    val currentClockChanged: Method? = registryType.declaredMethods
        .firstOrNull { it.name == "triggerOnCurrentClockChanged" && it.parameterCount == 0 }
        ?.accessible()

    private val availableClocksField = registryType.getDeclaredField("availableClocks").accessible()
    private val registryContextField = registryType.getDeclaredField("context").accessible()
    private val registrySettingsField = registryType.getDeclaredField("settings").accessible()

    /**
     * Where Wallpaper & style's registry stores a clock setting: every write the
     * picker makes goes through it, and it writes its argument. SystemUI's
     * registry never writes, and has none.
     */
    val applySettings: Method? = registryType.declaredMethods.firstOrNull { it.name == "applySettings" }

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
    private val axisStylePut = axisStyleType.getMethod("put", STRING, Float::class.java)
    private val presetGroupConstructor = load("$CLOCKS.AxisPresetConfig\$Group").getConstructor(List::class.java, Drawable::class.java)
    private val presetConfigType = load("$CLOCKS.AxisPresetConfig")
    private val indexedStyleType = load("$CLOCKS.AxisPresetConfig\$IndexedStyle")
    private val presetConfigConstructor = presetConfigType.getConstructor(List::class.java, indexedStyleType)
    private val indexedStyleConstructor =
        indexedStyleType.getConstructor(Int::class.java, Int::class.java, axisStyleType)
    private val indexedStyleGroup = indexedStyleType.getMethod("getGroupIndex")
    private val indexedStylePreset = indexedStyleType.getMethod("getPresetIndex")
    private val indexedStyleStyle = indexedStyleType.getMethod("getStyle")
    private val pickerConfigConstructor = load("$CLOCKS.ClockPickerConfig")
        .getConstructor(STRING, STRING, STRING, Drawable::class.java, BOOLEAN, List::class.java, presetConfigType)
    private val eventListenersConstructor = load("$CLOCKS.ClockEventListeners").getConstructor()

    private val weatherGetters = arrayOf("getTemperature", "getUseCelsius", "getState", "getDescription")

    private val settingsType = load("$CLOCKS.ClockSettings")
    private val settingsClockId = settingsType.getMethod("getClockId")
    private val settingsSeedColor = settingsType.getMethod("getSeedColor")
    private val settingsAxes = settingsType.getMethod("getAxes")

    private val themeType = load("$CLOCKS.ThemeConfig")
    private val themeConstructor = themeType.getConstructor(BOOLEAN, Int::class.javaObjectType)
    private val themeSeedColor = themeType.getMethod("getSeedColor")
    private val themeDefaultColor = themeType.getMethod("getDefaultColor", Context::class.java)
    private val themeAodColor = themeType.getMethod("getAodColor", Context::class.java)

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

    /** A face ticking once a minute, with SystemUI owning its weather display and scene. */
    fun faceConfig(hasCustomWeatherDataDisplay: Boolean): Any = faceConfigConstructor.newInstance(
        tickRateType.getField("PER_MINUTE").get(null),
        hasCustomWeatherDataDisplay,
        false,
        false,
    )

    /** Converts SystemUI's opaque weather payload into the module's small data contract. */
    fun weather(value: Any?): ClockWeather? {
        val data: Any = value ?: return null
        return runCatching {
            val methods = weatherGetters.map { name ->
                data.javaClass.methods.firstOrNull { it.name == name && it.parameterCount == 0 }
                    ?: data.javaClass.declaredMethods.first { it.name == name && it.parameterCount == 0 }.apply { isAccessible = true }
            }
            val temperatureValue = methods[0].invoke(data)
            val temperature = (temperatureValue as? Number)?.toFloat()?.toInt()
                ?: temperatureValue?.toString()?.toFloatOrNull()?.toInt()
                ?: return null
            val celsius = when (val raw = methods[1].invoke(data)) {
                is Boolean -> raw
                else -> raw?.toString()?.toBoolean() ?: false
            }
            val state = methods[2].invoke(data)?.toString().orEmpty()
            val description = methods[3].invoke(data)?.toString().orEmpty()
            ClockWeather(temperature, celsius, state, description)
        }.getOrNull()
    }

    /**
     * How Wallpaper & style lists [style]. A style whose width can be chosen
     * also lists one axis preset per width step, which the picker draws as its
     * "Clock face width" slider, set to the width [settings] already holds. The
     * current preset is picked by width alone, because the stored axes also
     * hold the size.
     */
    fun pickerConfig(style: ClockStyle, context: Context, settings: Any): Any {
        val thumbnail = style.createThumbnail(context)
        val presets = if (style.isWidthAdjustable) {
            widthPresets(thumbnail, ClockAxes.widthStepOf(axis(settings, ClockAxes.WIDTH_KEY)))
        } else {
            null
        }
        return pickerConfigConstructor.newInstance(
            style.id, style.name, style.description, thumbnail, true, emptyList<Any>(), presets,
        )
    }

    private fun widthPresets(icon: Drawable, current: Int): Any {
        val styles = List(ClockAxes.WIDTH_STEPS) { axisStyleConstructor.newInstance(ClockAxes.widthPreset(it)) }
        val groups = listOf(presetGroupConstructor.newInstance(styles, icon))
        return presetConfigConstructor.newInstance(groups, indexedStyleConstructor.newInstance(0, current, styles[current]))
    }

    /** The value [settings] stores for [key], or null when it stores none. */
    fun axis(settings: Any, key: String): Float? = settingsAxes.invoke(settings)?.let { axisOf(it, key) }

    /** The value a `ClockAxisStyle` holds for [key], or null when it holds none. */
    fun axisOf(axisStyle: Any, key: String): Float? = axisStyleGet.invoke(axisStyle, key) as Float?

    /** Writes [key] into the axes [settings] carries, in place. */
    fun putAxis(settings: Any, key: String, value: Float) {
        settingsAxes.invoke(settings)?.let { axisStylePut.invoke(it, key, value) }
    }

    /** Whether an `AxisPresetConfig.IndexedStyle` is one of this module's width presets. */
    fun isWidthPreset(indexedStyle: Any): Boolean = axisOf(requireNotNull(indexedStyleStyle.invoke(indexedStyle)), ClockAxes.WIDTH_KEY) != null

    /** Which preset an `AxisPresetConfig.IndexedStyle` is, within its group. */
    fun presetIndex(indexedStyle: Any): Int = indexedStylePreset.invoke(indexedStyle) as Int

    /** [indexedStyle], the same width preset in the same group, with its axes also holding [chosen]. */
    fun withAxes(indexedStyle: Any, chosen: Map<String, Float>): Any {
        val preset = presetIndex(indexedStyle)
        val style = axisStyleConstructor.newInstance(ClockAxes.widthPresetWith(preset, chosen))
        return indexedStyleConstructor.newInstance(indexedStyleGroup.invoke(indexedStyle), preset, style)
    }

    /**
     * A preset holding [chosen] axes alone, for a clock that lists no width
     * presets: chosen in the picker, it carries them to the preview clock and
     * to the setting Apply writes, as a width preset does.
     */
    fun axesOnlyPreset(chosen: Map<String, Float>): Any =
        indexedStyleConstructor.newInstance(0, 0, axisStyleConstructor.newInstance(chosen))

    /** The setting a registry holds now, or null before it has read one. */
    fun registrySettings(registry: Any): Any? = registrySettingsField.get(registry)

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

        /** Candidates for [registryReady], the one that runs earliest first. */
        val REGISTRY_READY = listOf("registerListeners", "querySettings")
    }
}

/** The two colours a theme gives a clock: awake, and in always-on display. */
internal data class ThemeColors(val normal: Int, val doze: Int)

/** Where the picker preview puts the top of each clock size, in pixels. */
internal data class PreviewTops(val largeClock: Int, val smallClock: Int)
