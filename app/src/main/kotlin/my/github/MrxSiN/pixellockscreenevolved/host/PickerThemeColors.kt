package my.github.MrxSiN.pixellockscreenevolved.host

import android.widget.CompoundButton

import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * Wallpaper & style's own colouring, which keeps its views in the colours of
 * the theme being previewed and changes them as that theme changes:
 * `ColorUpdateBinder` for one colour of a view, and `SwitchColorBinder` for a
 * switch, which is bound again whenever the switch flips.
 */
internal class PickerThemeColors(classLoader: ClassLoader) {

    private val bindColor: Method = Class.forName(COLOR_BINDER, false, classLoader).declaredMethods
        .firstOrNull { it.name == "bind" && it.parameterCount == 4 }
        ?: throw NoSuchMethodException("$COLOR_BINDER.bind")

    private val bindSwitch: Method = Class.forName(SWITCH_BINDER, false, classLoader).declaredMethods
        .firstOrNull { it.name == "bind" && it.parameterCount == 5 }
        ?: throw NoSuchMethodException("$SWITCH_BINDER.bind")

    /** The picker's colours in one fragment, for views shown in it. */
    inner class InFragment(fragment: Any) {

        private val viewModel = field(fragment, "colorUpdateViewModel")
        private val lifecycleOwner =
            runCatching { fragment.javaClass.getMethod("getViewLifecycleOwner").invoke(fragment) }.getOrNull() ?: fragment

        /** Calls [setColor] with the theme colour the view model names [name], now and as it changes. */
        fun follow(name: String, setColor: (Int) -> Unit) {
            val flow = field(viewModel, name)
            val set = function(bindColor.parameterTypes[0]) { args -> setColor(args[0] as Int) }
            bindColor.invoke(null, set, flow, animate(bindColor.parameterTypes[2]), lifecycleOwner)
        }

        /** Colours [toggle] as the picker colours its switches, now and whenever it flips. */
        fun follow(toggle: CompoundButton): (Boolean) -> Unit {
            var binding: Any? = null
            val rebind = { checked: Boolean ->
                binding?.let { runCatching { it.javaClass.getMethod("destroy").invoke(it) } }
                binding = bindSwitch.invoke(null, toggle, checked, viewModel, animate(bindSwitch.parameterTypes[3]), lifecycleOwner)
            }
            rebind(toggle.isChecked)
            return rebind
        }
    }

    /** A Kotlin function of [type] that runs [body]. */
    private fun function(type: Class<*>, body: (Array<Any?>) -> Unit): Any =
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args ->
            // The binders ignore what a setter returns.
            if (method.name == "invoke") body(args ?: emptyArray())
            null
        }

    /** A Kotlin `() -> Boolean` that always asks the binder to animate. */
    private fun animate(type: Class<*>): Any =
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ -> if (method.name == "invoke") true else null }

    private fun field(owner: Any, name: String): Any =
        generateSequence(owner.javaClass as Class<*>?) { it.superclass }
            .firstNotNullOfOrNull { type -> runCatching { type.getDeclaredField(name) }.getOrNull() }
            ?.apply { isAccessible = true }
            ?.get(owner)
            ?: throw NoSuchFieldException(name)

    private companion object {
        const val COLOR_BINDER = "com.android.wallpaper.picker.customization.ui.binder.ColorUpdateBinder"
        const val SWITCH_BINDER = "com.android.wallpaper.customization.ui.binder.SwitchColorBinder"
    }
}
