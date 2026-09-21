package my.github.MrxSiN.pixellockscreenevolved.host

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.window.BackEvent
import android.window.OnBackAnimationCallback
import android.window.OnBackInvokedDispatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.WindowInsets
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toolbar

/**
 * A page of this module's options, opened from Wallpaper & style's Lock screen
 * list, the way the picker opens a page of its own: over the whole screen,
 * under the picker's own toolbar with its back button, and closed by it or by
 * going back. Its [content] is laid out as the picker lays out its option list.
 *
 * It opens and closes with Android 17 Settings' shared X axis motion, the
 * picker sliding away under it ([SharedAxisTransition]), and a back gesture
 * previews the close as predictive back does.
 *
 * [onClosed] runs once the page is gone, for whatever it opened to follow.
 */
internal class PickerPage(
    context: Context,
    private val rows: PickerRows,
    title: String,
    private val onClosed: () -> Unit = {},
) {

    private val dialog = Dialog(context, android.R.style.Theme_DeviceDefault_NoActionBar)
    private val closers = mutableListOf<AutoCloseable>()
    private val root: LinearLayout
    private val transition: SharedAxisTransition
    private var closing = false

    /** Where the page's groups go, one under the other. */
    val content: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val padding = runCatching {
            context.resources.getDimensionPixelSize(PickerRows.resource(context, "dimen", "customization_option_container_horizontal_padding"))
        }.getOrDefault(PickerRows.dp(context, FALLBACK_PADDING_DP))
        setPadding(padding, 0, padding, PickerRows.dp(context, BOTTOM_PADDING_DP))
    }

    init {
        root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(toolbar(context, title))
            addView(
                ScrollView(context).apply { addView(content) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
            )
            setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                WindowInsets.CONSUMED
            }
        }
        val activity = activityOf(context)
        // The picker's own page colour until the previewed theme's arrives, so the page is never see-through.
        activity?.window?.decorView?.background?.constantState?.newDrawable()?.let { root.background = it }
        rows.paint(root, "colorSurfaceContainer", root::setBackgroundColor)
        dialog.setContentView(root)
        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setDecorFitsSystemWindows(false)
            // The page moves itself; its window stays still and clear, so the picker shows through as it goes.
            setWindowAnimations(0)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        val under = activity?.findViewById<View>(android.R.id.content)
        transition = SharedAxisTransition(root, under)
        // Where the picker has predictive back the gesture previews the close; where it has not, going back
        // arrives as the back key, which would otherwise dismiss the page at once, without its motion.
        dialog.onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, BackGesture())
        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode != KeyEvent.KEYCODE_BACK) return@setOnKeyListener false
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) close()
            true
        }
        dialog.setOnDismissListener {
            under?.apply {
                translationX = 0f
                alpha = 1f
            }
            closers.forEach { runCatching { it.close() } }
            closers.clear()
            onClosed()
        }
    }

    /** Going back, by gesture or button: previews the close as the gesture moves, and closes when it is let go. */
    private inner class BackGesture : OnBackAnimationCallback {
        override fun onBackStarted(event: BackEvent) = onBackProgressed(event)

        override fun onBackProgressed(event: BackEvent) {
            if (!closing) transition.follow(event.progress, event.swipeEdge == BackEvent.EDGE_LEFT)
        }

        override fun onBackCancelled() {
            if (!closing) transition.settle()
        }

        override fun onBackInvoked() = close()
    }

    /** Closes the page with its motion, then lets it go. */
    fun close() {
        if (closing) return
        closing = true
        transition.close { dialog.dismiss() }
    }

    /** Keeps [handle], such as a settings observer, open while the page is, and closes it with the page. */
    fun keep(handle: AutoCloseable) {
        closers += handle
    }

    fun show() {
        root.alpha = 0f
        dialog.show()
        // Opened once laid out, when the page has the width it slides by.
        root.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                root.viewTreeObserver.removeOnPreDrawListener(this)
                transition.open()
                return true
            }
        })
    }

    /**
     * The picker's own toolbar, titled [title], its back button closing the
     * page and its Apply button hidden: this page's options apply as they change.
     */
    private fun toolbar(context: Context, title: String): View {
        val bar = runCatching {
            LayoutInflater.from(context).inflate(PickerRows.resource(context, "layout", "customization_picker2_toolbar"), null, false)
        }.getOrNull() ?: return TextView(context).apply {
            text = title
            setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Large)
            val padding = PickerRows.dp(context, FALLBACK_PADDING_DP)
            setPadding(padding, padding, padding, padding)
            setOnClickListener { close() }
        }
        fun <T : View> find(name: String): T? = bar.findViewById(PickerRows.resource(context, "id", name))
        find<View>("apply_button")?.visibility = View.GONE
        find<View>("nav_button")?.let { back ->
            // The toolbar closes the picker with a cross; a page within it goes back, as the picker's own pages do.
            runCatching { setIcon(back, context.getDrawable(PickerRows.resource(context, "drawable", "ic_arrow_back_24dp"))) }
            runCatching { back.contentDescription = context.getString(PickerRows.resource(context, "string", "bottom_action_bar_back")) }
            back.setOnClickListener { close() }
        }
        find<Toolbar>("toolbar")?.let { toolbar ->
            toolbar.title = title
            rows.paint(toolbar, "colorOnSurface", toolbar::setTitleTextColor)
        }
        return bar
    }

    /**
     * Gives the picker's `MaterialButton` [icon]. Its setters are shrunk away, so the icon is put in its
     * field and the button told to show it again, which tints and places it as the button always does.
     */
    private fun setIcon(button: View, icon: android.graphics.drawable.Drawable?) {
        val type = generateSequence(button.javaClass as Class<*>?) { it.superclass }
            .firstOrNull { type -> type.declaredFields.any { it.name == "icon" } } ?: return
        type.getDeclaredField("icon").apply { isAccessible = true }.set(button, icon?.mutate())
        type.getDeclaredMethod("updateIcon", Boolean::class.javaPrimitiveType).apply { isAccessible = true }.invoke(button, true)
    }

    private companion object {
        const val FALLBACK_PADDING_DP = 16f

        /** The activity [context] belongs to, however many wrappers it is inside. */
        fun activityOf(context: Context): Activity? =
            generateSequence(context) { (it as? android.content.ContextWrapper)?.baseContext }.firstNotNullOfOrNull { it as? Activity }
        const val BOTTOM_PADDING_DP = 24f
    }
}
