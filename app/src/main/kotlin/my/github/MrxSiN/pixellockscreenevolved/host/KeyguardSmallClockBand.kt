package my.github.MrxSiN.pixellockscreenevolved.host

import java.lang.reflect.Method

import my.github.MrxSiN.pixellockscreenevolved.core.Logger
import my.github.MrxSiN.pixellockscreenevolved.hook.Hooks
import my.github.MrxSiN.pixellockscreenevolved.hook.HostPatch

/**
 * Makes the band Android 17's composed lock screen lays its small clock out in
 * as tall as this module's small face, so SystemUI's date row, the smartspace
 * card and the notifications stack below the face, as they stack below a
 * Pixel small clock.
 *
 * The scene caps the band at SystemUI's `small_clock_height` dimension
 * (`ClockRegionElementProvider$SmallClockRegionElement` reads it through
 * Compose's `dimensionResource` and gives the clock `heightIn(max = …)`), a
 * height made for a one-line Pixel clock. A taller face overflowed it upward
 * and the row stood almost against the time. Where a face of this module's is
 * the small clock, that one read answers the face's own height instead, never
 * less than SystemUI's; every other dimension, and every other clock, is left
 * alone.
 */
internal class KeyguardSmallClockBand(private val hooks: Hooks, private val logger: Logger) : HostPatch {

    override fun install(classLoader: ClassLoader) {
        val band: Int
        val dimensionResource: Method
        try {
            band = Class.forName(CLOCK_DIMENS, false, classLoader).getField(SMALL_CLOCK_HEIGHT).getInt(null)
            dimensionResource = Class.forName(PRIMITIVE_RESOURCES, false, classLoader).declaredMethods
                .first { it.name == DIMENSION_RESOURCE && it.parameterCount == 3 }
        } catch (error: ReflectiveOperationException) {
            logger.warn("The small clock's band was not found; a tall small face overflows it", error)
            return
        } catch (error: NoSuchElementException) {
            logger.warn("Compose's dimensionResource was not found; a tall small face overflows its band", error)
            return
        }
        hooks.replaceResult(dimensionResource) { _, args, result ->
            if (args.firstOrNull() != band || result !is Float) return@replaceResult result
            val face = ModuleClockFaces.smallFaceHeightDp()
            if (face > result) face else result
        }
    }

    private companion object {
        const val CLOCK_DIMENS = "com.android.systemui.customization.clocks.R\$dimen"
        const val SMALL_CLOCK_HEIGHT = "small_clock_height"
        const val PRIMITIVE_RESOURCES = "androidx.compose.ui.res.PrimitiveResources_androidKt"
        const val DIMENSION_RESOURCE = "dimensionResource"
    }
}
