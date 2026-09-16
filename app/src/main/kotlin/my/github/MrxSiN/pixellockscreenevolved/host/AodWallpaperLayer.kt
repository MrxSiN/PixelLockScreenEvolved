package my.github.MrxSiN.pixellockscreenevolved.host

import android.content.Context
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver

/**
 * The lock screen photo on the always-on display, behind one clock's lock
 * screen: dimmed, in greys, or as dots ([AodWallpaperPaint]), of only the
 * photo's subject where the depth effect has found one.
 *
 * SystemUI blacks the photo out for the always-on display with scrims beneath
 * the keyguard, so this is a view at the back of the keyguard itself, over
 * those scrims, placed as the window manager places the wallpaper
 * ([LockWallpaperFeed]). It shows only as the display dozes, as far as it has
 * dozed, so it fades in as the lock screen goes dark and out as it wakes while
 * the real photo is revealed beneath it.
 */
internal class AodWallpaperLayer(
    private val clock: ClockControllerAdapter,
    private val feed: LockWallpaperFeed,
    private val look: () -> AodWallpaperLook?,
) {

    private val anchor = clock.largeFace.view
    private val view = PhotoView(anchor.context)
    private val main = Handler(Looper.getMainLooper())
    private var attachedTree: ViewTreeObserver? = null
    private var shownMinute = -1L

    private val follow = ViewTreeObserver.OnPreDrawListener {
        place()
        true
    }

    init {
        anchor.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = listen()
            override fun onViewDetachedFromWindow(view: View) = stopListening()
        })
        if (anchor.isAttachedToWindow) listen()
    }

    /** Draws again, as the photo, its placement or the look has changed. */
    fun invalidate() {
        anchor.invalidate()
        view.invalidate()
    }

    private fun listen() {
        stopListening()
        attachedTree = anchor.viewTreeObserver.also { it.addOnPreDrawListener(follow) }
    }

    /** Stops following the clock; the view is taken out once the parent has finished detaching its children. */
    private fun stopListening() {
        attachedTree?.takeIf { it.isAlive }?.removeOnPreDrawListener(follow)
        attachedTree = null
        main.post { if (attachedTree == null) (view.parent as? ViewGroup)?.removeView(view) }
    }

    private fun place() {
        val parent = anchor.parent as? ViewGroup
        val current = look()
        val doze = clock.faces.maxOf { it.dozeFraction }
        if (parent == null || current == null || !coversScreen(parent)) {
            (view.parent as? ViewGroup)?.removeView(view)
            return
        }
        if (view.parent !== parent || parent.indexOfChild(view) != 0) {
            (view.parent as? ViewGroup)?.removeView(view)
            // Sized in pixels: SystemUI's constraint sets turn a child's match-parent size without constraints into none.
            parent.addView(view, 0, ViewGroup.LayoutParams(parent.width, parent.height))
        } else if (view.layoutParams.width != parent.width || view.layoutParams.height != parent.height) {
            view.layoutParams = view.layoutParams.apply {
                width = parent.width
                height = parent.height
            }
        }
        view.look = current
        val visibility = if (doze > 0f) View.VISIBLE else View.INVISIBLE
        if (view.visibility != visibility) view.visibility = visibility
        if (view.alpha != doze) view.alpha = doze

        val minute = view.paint.minute()
        if (minute != shownMinute) {
            shownMinute = minute
            view.invalidate()
        }
    }

    /** Whether [parent] is the lock screen itself, as big as the screen, rather than a scaled-down preview of it. */
    private fun coversScreen(parent: ViewGroup): Boolean {
        val screen = parent.resources.displayMetrics
        return parent.width >= screen.widthPixels * SCREEN_SHARE && parent.height >= screen.heightPixels * SCREEN_SHARE
    }

    /** The photo, drawn through [AodWallpaperPaint] with the current look. */
    private inner class PhotoView(context: Context) : View(context) {

        val paint = AodWallpaperPaint(context.resources)

        var look: AodWallpaperLook? = null
            set(value) {
                if (field != value) invalidate()
                field = value
            }

        init {
            // SystemUI's constraint sets need every child of the keyguard to have an id.
            id = generateViewId()
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        override fun onDraw(canvas: Canvas) {
            val current = look ?: return
            paint.draw(canvas, current, width, height, feed.place(WallpaperPlacement.Size(width, height), current.surface))
        }
    }

    private companion object {
        const val SCREEN_SHARE = 0.9f
    }
}
