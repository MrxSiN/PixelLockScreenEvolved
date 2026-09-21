package my.github.MrxSiN.pixellockscreenevolved.host

import android.view.View

import java.lang.reflect.Method

import my.github.MrxSiN.pixellockscreenevolved.clock.FaceSize

/**
 * How the lock screen draws a clock face, reached through SystemUI's class
 * loader. HOOK_NOTES.md records the shapes.
 *
 * Android 17 QPR1 moved the lock screen to a scene of composed elements: a
 * face is no longer a view the keyguard adds and constrains, but a
 * `LockscreenElement` the keyguard composes at a key of its own. SystemUI
 * keeps a composable that hosts a plain view inside that scene, so a face
 * still draws the view it already builds; only how it reaches the screen
 * changed.
 *
 * Building this fails on a build without the element system, which is what
 * tells [ClockPluginApi] to leave the older view path in place.
 */
internal class LockscreenElementApi(private val classLoader: ClassLoader) {

    /**
     * The interface a clock face answers with, one movable element per size.
     *
     * Clock keys are `MovableElementKey`s. SystemUI checks the interface type
     * before choosing its movable-element path; implementing the more general
     * `LockscreenElement` leaves the face outside the clock region's placement.
     */
    val elementType: Class<*> = load("$ELEMENTS.MovableLockscreenElement")

    private val keys = load("$ELEMENTS.LockscreenElementKeys\$Clock")
    private val keysInstance = keys.getField("INSTANCE").get(null)
    private val largeKey = keys.getMethod("getLarge")
    private val smallKey = keys.getMethod("getSmall")

    /**
     * What the lock screen treats a face as. Google's own faces are standard
     * ones, and this module's stand in the same place for the same key, so they
     * are sorted and drawn the same way.
     */
    val dynamicSource: Any = load("$ELEMENTS.BaseLockscreenElement\$ElementSource")
        .getField("DYNAMIC")
        .get(null)

    private val scopeType = load("$ELEMENTS.LockscreenScope")
    private val modifierType = load("androidx.compose.ui.Modifier")
    private val modifier: Any = modifierType.getField("Companion").get(null)

    private val sizeKt = load("androidx.compose.foundation.layout.SizeKt")
    private val alignmentType = load("androidx.compose.ui.Alignment")

    /** Keeps a face the size of what it draws, as SystemUI's own faces are. */
    private val wrapContentSize = sizeKt.getMethod(
        "wrapContentSize\$default",
        modifierType,
        alignmentType,
        Boolean::class.java,
        Int::class.java,
        Any::class.java,
    )

    /**
     * Spans the width, which is what keeps SystemUI's date and weather line
     * below the small face rather than beside it.
     */
    private val fillMaxWidth = sizeKt.getMethod(
        "fillMaxWidth\$default",
        modifierType,
        Float::class.java,
        Int::class.java,
        Any::class.java,
    )

    /**
     * Gives a face the height it draws at, whatever height it was offered.
     *
     * The lock screen offers the small clock the band a Pixel small clock needs,
     * which is shorter than this module's, and a face measured into that band
     * has its numerals cut off at the foot. Asking for the height it wants both
     * stops the cut and tells the scene how much room the face takes, so the
     * date and weather line still lands below it.
     */
    private val requiredHeight = sizeKt.getMethod("requiredHeight-3ABfNKs", modifierType, Float::class.java)

    /** Centres a face across whatever width it was given, and keeps it at the top. */
    private val topCenter: Any = load("androidx.compose.ui.Alignment\$Companion").let { companion ->
        val field = companion.getDeclaredField("TopCenter").apply { isAccessible = true }
        field.get(runCatching { companion.getField("\$\$INSTANCE").get(null) }.getOrNull())
    }

    /** Lets the always-on display shift a face to spare the screen, as it shifts Google's. */
    private val burnInAware = scopeType.getMethod("burnInAware", modifierType, Boolean::class.java)

    /** Marks a face as content that is shown before the device is unlocked. */
    private val nonAuthUI = scopeType.getMethod("nonAuthUI", modifierType)

    /** SystemUI's own composable for putting a plain view in the lock screen scene. */
    private val clockView = load("com.android.systemui.customization.clocks.view.DefaultClockFaceLayout\$Companion")
        .getMethod("clockView", View::class.java, modifierType, load("androidx.compose.runtime.Composer"))

    /** Where the lock screen scene puts a face of this size. */
    fun key(size: FaceSize): Any = when (size) {
        FaceSize.LARGE -> largeKey
        FaceSize.SMALL -> smallKey
    }.invoke(keysInstance) as Any

    /**
     * Draws [view] as this face, in the scene [scope] describes, into the
     * composition [composer] is building. Called only from a composition, so it
     * runs on whichever thread SystemUI composes on.
     */
    fun draw(scope: Any, composer: Any, view: View, size: FaceSize) {
        // Both faces sit at the top of what the scene gives them rather than in
        // the middle of it, which is where the default alignment would centre
        // them, and measure at the size they draw rather than the size they are
        // offered, so a tall face is not cut off. The small face spans the
        // width, which keeps SystemUI's date and weather line below it.
        val room = when (size) {
            FaceSize.LARGE -> modifier
            FaceSize.SMALL -> fillMaxWidth.invoke(null, modifier, 0f, FILL_DEFAULTS, null)
                .let { wide -> requiredHeight.invoke(null, wide, drawnHeightDp(view)) }
        }
        var shape = wrapContentSize.invoke(null, room, topCenter, false, NOTHING_DEFAULTED, null)
        shape = burnInAware.invoke(scope, shape, true)
        shape = nonAuthUI.invoke(scope, shape)
        clockView.invoke(null, view, shape, composer)
    }

    /** How tall [view] draws when nothing limits it, in the density-independent pixels a modifier takes. */
    private fun drawnHeightDp(view: View): Float {
        val unlimited = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.measure(unlimited, unlimited)
        return view.measuredHeight / view.resources.displayMetrics.density
    }

    private fun load(name: String): Class<*> = Class.forName(name, false, classLoader)

    private companion object {
        const val ELEMENTS = "com.android.systemui.plugins.keyguard.ui.composable.elements"

        /** Gives `wrapContentSize` both its alignment and its bounds. */
        const val NOTHING_DEFAULTED = 0

        /** Leaves `fillMaxWidth`'s fraction at the whole width. */
        const val FILL_DEFAULTS = 1
    }
}
