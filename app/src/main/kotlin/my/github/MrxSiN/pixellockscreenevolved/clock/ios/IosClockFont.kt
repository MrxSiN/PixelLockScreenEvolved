package my.github.MrxSiN.pixellockscreenevolved.clock.ios

import android.content.Context
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface

import java.io.File
import java.util.zip.ZipFile

import my.github.MrxSiN.pixellockscreenevolved.core.AndroidLogger

/**
 * The iOS time uses a custom font from the host's files folder when present.
 * The default is AXS66Clock, whose `HGHT` axis draws its own tall figures.
 * Bundled fonts are extracted from the module [apk] to the host's cache.
 * If AXS66Clock fails to load, the time uses Inter, then [FlexClockNumerals].
 */
internal object IosClockFont {

    /** The module's APK, as the framework installed it; set as the module loads. */
    @Volatile
    var apk: String? = null

    private const val DEFAULT_FONT = "AXS66Clock.otf"
    private const val FALLBACK_FONT = "Inter.ttf"

    /** The file name of the owner's own font in each host's files folder. */
    private const val OWN_FONT = "pixel_lock_screen_evolved_clock_font"

    /** A loaded font, and whether it has iOS 26's `HGHT` axis, so its figures are made tall by the font itself. */
    class Font(val typeface: Typeface, val adaptive: Boolean)

    @Volatile
    private var loaded: Font? = null

    /** The selected typeface, or null when the view should use the Pixel's numerals. */
    fun font(context: Context): Font? {
        loaded?.let { return it }
        return synchronized(this) {
            loaded ?: (own(context) ?: bundled(context, DEFAULT_FONT) ?: bundled(context, FALLBACK_FONT))
                ?.also { loaded = it }
        }
    }

    private fun own(context: Context): Font? {
        val file = File(context.filesDir, OWN_FONT).takeIf { it.isFile } ?: return null
        return load(file.path) { file }
    }

    private fun bundled(context: Context, name: String): Font? =
        load("bundled $name") { extract(context, name) }

    private inline fun load(description: String, file: () -> File): Font? = runCatching {
        val typeface = Typeface.Builder(file()).build()
        Font(typeface, adaptive = hasHeightAxis(typeface)).also {
            AndroidLogger.info("iOS clock set in $description (HGHT axis: ${it.adaptive})")
        }
    }.onFailure { AndroidLogger.warn("Could not load iOS clock font $description", it) }.getOrNull()

    /** Whether a zero drawn at `HGHT` 200 is taller than at 100: the font answers to the axis. */
    private fun hasHeightAxis(typeface: Typeface): Boolean {
        val paint = Paint().apply {
            this.typeface = typeface
            textSize = 100f
        }
        val bounds = Rect()
        fun height(value: Int): Int {
            paint.fontVariationSettings = "'HGHT' $value"
            paint.getTextBounds("0", 0, 1, bounds)
            return bounds.height()
        }
        return height(200) > height(100)
    }

    private fun extract(context: Context, name: String): File {
        val source = File(requireNotNull(apk) { "Module APK unknown" })
        val prefix = "pixel_lock_screen_evolved_${name.substringBeforeLast('.').lowercase()}_"
        // Keyed by the APK's size and time, so a new build of the module copies its font afresh.
        val copy = File(context.cacheDir, "$prefix${source.length()}_${source.lastModified()}.${name.substringAfterLast('.')}")
        if (!copy.isFile || copy.length() == 0L) {
            context.cacheDir.listFiles { file -> file.name.startsWith(prefix) }?.forEach(File::delete)
            ZipFile(source).use { zip ->
                val path = "assets/fonts/$name"
                val entry = requireNotNull(zip.getEntry(path)) { "$path missing" }
                val partial = File(copy.path + ".part")
                zip.getInputStream(entry).use { input -> partial.outputStream().use { input.copyTo(it) } }
                check(partial.renameTo(copy)) { "Could not keep $copy" }
            }
        }
        return copy
    }

    /** Variation settings for Inter's Display cut, or any other own font, at [weight]. */
    fun variation(weight: Float): String = "'opsz' 32, 'wght' $weight"

    /**
     * Variation settings for iOS 26's clock font at [weight], its figures [height] times their own
     * height (`HGHT` 100 per time, up to [MAX_HEIGHT]), `SOFT` at the 70 iOS draws them with.
     */
    fun adaptiveVariation(weight: Float, height: Float): String =
        "'HGHT' ${HEIGHT_PER_TIME * height.coerceIn(1f, MAX_HEIGHT)}, 'wdth' 100, 'wght' $weight, 'SOFT' 70"

    /** `HGHT` 100 draws the figures at their own height, 500 five times it; measured on the font, linear between. */
    private const val HEIGHT_PER_TIME = 100f
    const val MAX_HEIGHT = 5f
}
