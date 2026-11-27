package my.github.MrxSiN.pixellockscreenevolved.clock.ios

import android.content.Context
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface

import java.io.File
import java.util.zip.ZipFile

import my.github.MrxSiN.pixellockscreenevolved.core.AndroidLogger

/**
 * The typeface the iOS time is set in.
 *
 * First a font the owner put on the phone, in the host's own files folder
 * ([OWN_FONT], `context.filesDir`: `/data/user_de/0/com.android.systemui/files/`,
 * `/data/user/0/com.google.android.apps.wallpaper/files/`), never shipped with the module. Made for iOS 26's own
 * clock font, `.SF Adaptive Soft Numeric`, whose `HGHT` axis draws the figures
 * taller from the same strokes ([Font.adaptive]); any other font is drawn as
 * Inter would be, with its `wght` axis. Read once per process: a host restart
 * picks up a new file.
 *
 * Otherwise Inter, the free (SIL Open Font
 * License 1.1) look-alike of Apple's SF Pro, whose licence keeps it on
 * Apple's platforms. Inter's `opsz` axis reaches its Display cut at 32, the
 * counterpart of SF Pro Display, which iOS sets its lock screen clock in.
 *
 * The font ships in this module's APK (`assets/fonts/Inter.ttf`, licence
 * beside it), cut down to what the time draws, the figures, the colon and
 * the capital I its stems are measured by, with both axes kept: 15 KB rather
 * than 860 KB. Made from Google Fonts' `ofl/inter/Inter[opsz,wght].ttf`
 * (Inter 4.001) with fontTools:
 * `pyftsubset Inter[opsz,wght].ttf --text="0123456789:I" --layout-features=pnum,tnum,kern,ss01,cv01
 * --output-file=Inter.ttf`. The hosts cannot load this module's resources, so it is read straight out of the APK ([apk], set as the module loads) into the host's
 * own cache once per APK, and loaded from there. Where that fails the time
 * falls back to the Pixel clock's own numerals ([FlexClockNumerals]).
 */
internal object IosClockFont {

    /** The module's APK, as the framework installed it; set as the module loads. */
    @Volatile
    var apk: String? = null

    private const val ENTRY = "assets/fonts/Inter.ttf"

    /** The file name of the owner's own font in each host's files folder. */
    const val OWN_FONT = "pixel_lock_screen_evolved_clock_font"

    /** A loaded font, and whether it has iOS 26's `HGHT` axis, so its figures are made tall by the font itself. */
    class Font(val typeface: Typeface, val adaptive: Boolean)

    @Volatile
    private var loaded: Font? = null

    /** The owner's own font, or Inter, or null where neither can be read. */
    fun font(context: Context): Font? {
        loaded?.let { return it }
        return synchronized(this) {
            loaded ?: (own(context) ?: inter(context))?.also { loaded = it }
        }
    }

    private fun own(context: Context): Font? {
        val file = File(context.filesDir, OWN_FONT).takeIf { it.isFile } ?: return null
        return runCatching {
            val typeface = Typeface.Builder(file).build()
            Font(typeface, adaptive = hasHeightAxis(typeface)).also {
                AndroidLogger.info("iOS clock set in ${file.path} (HGHT axis: ${it.adaptive})")
            }
        }.onFailure { AndroidLogger.warn("${file.path} could not be loaded; the iOS time falls back to Inter", it) }.getOrNull()
    }

    private fun inter(context: Context): Font? = runCatching { Font(load(context), adaptive = false) }
        .onFailure { AndroidLogger.warn("Inter could not be loaded; the iOS time keeps the Pixel's numerals", it) }
        .getOrNull()

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

    private fun load(context: Context): Typeface {
        val source = File(requireNotNull(apk) { "Module APK unknown" })
        // Keyed by the APK's size and time, so a new build of the module copies its font afresh.
        val copy = File(context.cacheDir, "pixel_lock_screen_evolved_inter_${source.length()}_${source.lastModified()}.ttf")
        if (!copy.isFile || copy.length() == 0L) {
            context.cacheDir.listFiles { file -> file.name.startsWith("pixel_lock_screen_evolved_inter_") }?.forEach(File::delete)
            ZipFile(source).use { zip ->
                val entry = requireNotNull(zip.getEntry(ENTRY)) { "$ENTRY missing" }
                val partial = File(copy.path + ".part")
                zip.getInputStream(entry).use { input -> partial.outputStream().use { input.copyTo(it) } }
                check(partial.renameTo(copy)) { "Could not keep $copy" }
            }
        }
        return Typeface.Builder(copy).build()
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
