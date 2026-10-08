package my.github.MrxSiN.pixellockscreenevolved.clock

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View

/**
 * One lock screen clock design.
 *
 * A style knows how to draw itself and nothing about SystemUI: the host
 * adapters in [my.github.MrxSiN.pixellockscreenevolved.host] turn it into the
 * plugin objects SystemUI and Wallpaper & style expect. Adding a design means
 * adding one of these and listing it in [clockStyles].
 */
interface ClockStyle {

    /**
     * Stable identifier written to the system clock setting. Renaming one
     * silently resets every device that picked it back to the default clock.
     */
    val id: String

    /** Name shown in Wallpaper & style. */
    val name: String

    /** Accessibility description shown in Wallpaper & style. */
    val description: String

    /**
     * Whether the size can be chosen in Wallpaper & style. The faces of a
     * resizable style implement [ResizableClockFace].
     */
    val isResizable: Boolean get() = false

    /** How many stops the size slider has for this style, the smallest first. */
    val sizeSteps: Int get() = DEFAULT_SIZE_STEPS

    /**
     * Whether the width can be chosen in Wallpaper & style. The faces of such a
     * style implement [AdjustableWidthClockFace].
     */
    val isWidthAdjustable: Boolean get() = false

    /**
     * Whether the large face can be placed lower down the lock screen, as
     * Wallpaper & style's position controls choose. A face laid out around the
     * photo's subject is not ([SubjectAwareClockFace]): moving it would move
     * what it lays itself out against.
     */
    val isMovable: Boolean get() = true

    fun createFace(context: Context, size: FaceSize): ClockFace

    /** The small picture Wallpaper & style shows for this style in its list. */
    fun createThumbnail(context: Context): Drawable

    companion object {
        /** The size slider's stops for a style that does not choose its own. */
        const val DEFAULT_SIZE_STEPS: Int = 6
    }
}

/**
 * The two faces SystemUI swaps between: large while the lock screen is empty,
 * small once notifications need the room.
 */
enum class FaceSize { LARGE, SMALL }

/** One drawn face of a [ClockStyle]. */
interface ClockFace {

    val view: View

    /**
     * The part of [view] that reads as the time. On unlock it flies to the
     * status bar clock, so it should be the digits alone where the face has more.
     */
    val timeView: View get() = view

    /**
     * Whether the time is moving to new text, as digits roll to a new minute, so
     * what [timeView] draws now is not yet what it will show.
     */
    val isChanging: Boolean get() = false

    /**
     * Whether this face writes the date itself. SystemUI then leaves out its
     * own date and weather line beside it, so the date is never shown twice.
     */
    val drawsDate: Boolean

    /**
     * Re-reads the time, time zone, locale and 12/24-hour setting and redraws.
     * Called on every tick and whenever one of those changes.
     */
    fun refresh()

    /** Colour to draw in, already settled for wallpaper contrast and doze. */
    fun setColor(color: Int)
}

/** Weather supplied to a clock by SystemUI, without host implementation types. */
data class ClockWeather(
    val temperature: Int,
    val celsius: Boolean,
    val condition: String,
    val description: String,
)

/** A face that presents SystemUI's weather data. */
interface WeatherClockFace {
    fun setWeather(weather: ClockWeather?)
}
