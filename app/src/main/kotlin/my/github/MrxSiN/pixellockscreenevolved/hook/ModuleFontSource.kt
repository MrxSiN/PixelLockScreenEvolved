package my.github.MrxSiN.pixellockscreenevolved.hook

import android.content.Context
import android.graphics.Typeface

import java.util.concurrent.ConcurrentHashMap

import io.github.libxposed.api.XposedInterface

import my.github.MrxSiN.pixellockscreenevolved.clock.FontSource
import my.github.MrxSiN.pixellockscreenevolved.core.Logger

/**
 * [FontSource] over the module's own APK, read from inside a host process.
 *
 * The framework reports where the module is installed; the host's package
 * manager opens that APK's assets like any other app's, so no file is copied
 * anywhere. A font is read once per process.
 */
class ModuleFontSource(
    private val xposed: XposedInterface,
    private val logger: Logger,
) : FontSource {

    private val loaded = ConcurrentHashMap<String, Typeface>()

    override fun load(context: Context, assetPath: String): Typeface? =
        loaded[assetPath] ?: runCatching {
            val assets = context.packageManager.getResourcesForApplication(xposed.moduleApplicationInfo).assets
            Typeface.Builder(assets, assetPath).build()
        }.onFailure {
            logger.warn("Module font $assetPath could not be read", it)
        }.getOrNull()?.also { loaded[assetPath] = it }
}
