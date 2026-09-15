package my.github.MrxSiN.pixellockscreenevolved.host

import android.app.WallpaperColors
import android.content.Context

import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * How SystemUI draws the lock screen previews Wallpaper & style asks for,
 * reached through its class loader. HOOK_NOTES.md records the shapes.
 *
 * Each preview has a renderer, which colours the preview's clock for the
 * wallpaper the picker shows behind it; the picker names that wallpaper only
 * by its colours, in the preview's request.
 */
internal class KeyguardPreviewApi(classLoader: ClassLoader) {

    private val rendererType = load("com.android.systemui.keyguard.ui.preview.KeyguardPreviewRenderer", classLoader)

    /**
     * Colours a preview's clock for its wallpaper; its first two arguments are
     * the renderer and the clock's plugin `ClockController`.
     */
    val clockAppearanceUpdated: Method = rendererType.declaredMethods.firstOrNull { it.name == UPDATE_CLOCK_APPEARANCE }
        ?: throw NoSuchMethodException("KeyguardPreviewRenderer.$UPDATE_CLOCK_APPEARANCE")

    private val rendererContext = rendererType.field("context")
    private val rendererViewModel = rendererType.field("previewViewModel")
    private val viewModelInteractor = load("$KEYGUARD.ui.viewmodel.KeyguardPreviewViewModel", classLoader).field("interactor")
    private val interactorRepository = load("$KEYGUARD.domain.interactor.KeyguardPreviewInteractor", classLoader).field("repository")
    private val repositoryColors = load("$KEYGUARD.data.repository.KeyguardPreviewRepository", classLoader).field("wallpaperColors")

    /** The context a renderer draws its preview in. */
    fun context(renderer: Any): Context = rendererContext.get(renderer) as Context

    /** The colours of the wallpaper the picker shows behind a renderer's preview, or null if it named none. */
    fun wallpaperColors(renderer: Any): WallpaperColors? {
        val interactor = viewModelInteractor.get(rendererViewModel.get(renderer))
        return repositoryColors.get(interactorRepository.get(interactor)) as WallpaperColors?
    }

    private companion object {
        const val KEYGUARD = "com.android.systemui.keyguard"
        const val UPDATE_CLOCK_APPEARANCE = "access\$updateClockAppearance"

        fun load(name: String, classLoader: ClassLoader): Class<*> = Class.forName(name, false, classLoader)

        fun Class<*>.field(name: String): Field = getDeclaredField(name).apply { isAccessible = true }
    }
}
