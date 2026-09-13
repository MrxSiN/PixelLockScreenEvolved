package my.github.MrxSiN.pixellockscreenevolved.clock

import android.content.Context
import android.graphics.Typeface

/**
 * Where a clock style gets a font it ships rather than one on the device.
 *
 * Styles draw inside SystemUI and Wallpaper & style, whose own assets are not
 * this module's; the hook layer knows how to reach the module's APK from there.
 */
fun interface FontSource {

    /** The font at [assetPath] in the module's assets, or null when it cannot be read. */
    fun load(context: Context, assetPath: String): Typeface?
}
