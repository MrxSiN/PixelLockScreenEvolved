package my.github.MrxSiN.pixellockscreenevolved.diagnostics

import my.github.MrxSiN.pixellockscreenevolved.diagnostics.Feature.AOD_WALLPAPER
import my.github.MrxSiN.pixellockscreenevolved.diagnostics.Feature.CLOCK_SIZE_MOTION
import my.github.MrxSiN.pixellockscreenevolved.diagnostics.Feature.CLOCK_STYLES
import my.github.MrxSiN.pixellockscreenevolved.diagnostics.Feature.DEPTH_EFFECT
import my.github.MrxSiN.pixellockscreenevolved.diagnostics.Feature.PICKER_CLOCKS
import my.github.MrxSiN.pixellockscreenevolved.diagnostics.Feature.PICKER_CLOCK_SIZE
import my.github.MrxSiN.pixellockscreenevolved.diagnostics.Feature.PICKER_LOCK_SCREEN_OPTIONS
import my.github.MrxSiN.pixellockscreenevolved.diagnostics.Feature.SMARTSPACE_PLACEMENT

/**
 * The host members every feature stands on, as read off `CP3A.260905.009`.
 *
 * Each entry names what a host API class looks up, and `HostContractsTest`
 * holds each name to those sources, so a lookup that moves in code and not here
 * fails the build rather than the report. A monthly update that renames one of
 * these shows up on the compatibility page, and keeps the feature from being
 * installed, before anyone has to find it by a feature silently doing nothing.
 * HOOK_NOTES.md records what each one is.
 */
object HostContracts {

    private const val BOOLEAN = "boolean"
    private const val INT = "int"
    private const val FLOAT = "float"
    private const val OBJECT = "java.lang.Object"
    private const val STRING = "java.lang.String"
    private const val CONTEXT = "android.content.Context"

    private const val CLOCKS = "com.android.systemui.plugins.keyguard.ui.clocks"
    private const val REGISTRY = "com.android.systemui.shared.clocks.ClockRegistry"
    private const val ELEMENTS = "com.android.systemui.plugins.keyguard.ui.composable.elements"
    private const val KEYGUARD = "com.android.systemui.keyguard"
    private const val CLOCK_VIEW_MODEL = "$KEYGUARD.ui.viewmodel.KeyguardClockViewModel"
    private const val SIZE_TRANSITION = "$KEYGUARD.ui.view.layout.sections.transitions.ClockSizeTransition"
    private const val SMARTSPACE_BINDER = "$KEYGUARD.ui.binder.KeyguardSmartspaceViewBinder"
    private const val SMARTSPACE_SECTION = "$KEYGUARD.ui.view.layout.sections.SmartspaceSection"
    private const val SMARTSPACE_ELEMENTS = "$KEYGUARD.ui.composable.elements.SmartspaceElementProvider"
    private const val PREVIEW_SMARTSPACE_BINDER = "$KEYGUARD.ui.binder.KeyguardPreviewSmartspaceViewBinder\$bind\$1\$1\$1\$4"
    private const val PREVIEW_RENDERER = "$KEYGUARD.ui.preview.KeyguardPreviewRenderer"
    private const val CANVAS_ENGINE = "com.android.systemui.wallpapers.ImageWallpaper\$CanvasEngine"

    private const val PICKER_FRAGMENT = "com.android.wallpaper.picker.customization.ui.CustomizationPickerFragment"
    private const val SHEET_BINDER = "com.android.customization.picker.clock.ui.binder.ClockFloatingSheetBinder"
    private const val SHEET_BINDING = "$SHEET_BINDER\$bind\$13\$1"
    private const val PICKER_VIEW_MODEL = "com.android.customization.picker.clock.ui.viewmodel.ClockPickerViewModel"
    private const val CLOCK_OPTION_BUILDER = "$PICKER_VIEW_MODEL\$clockStyleOptions\$1"
    private const val SLIDER = "com.google.android.material.slider.BaseSlider"
    private const val SHAPE_DRAWABLE = "com.google.android.material.shape.MaterialShapeDrawable"
    private const val LIST_SPACING = "com.android.customization.picker.common.ui.view.SingleRowListItemSpacing"

    /** What `ClockPluginApi` looks up, in whichever host builds a clock registry. */
    private fun clockPlugin(features: Set<Feature>): List<HostContract> = listOf(
        HostContract(
            Signature(REGISTRY, Member.Method("registerListeners", emptyList())),
            features,
            alternatives = listOf(Signature(REGISTRY, Member.Method("querySettings", emptyList()))),
        ),
        contract(REGISTRY, Member.Field("availableClocks"), features),
        HostContract(Signature(REGISTRY, Member.Method("triggerOnCurrentClockChanged", emptyList())), features, isRequired = false),
        contract(REGISTRY, Member.Field("context"), features),
        contract(REGISTRY, Member.Field("settings"), features),
        contract("$REGISTRY\$ClockInfo", null, features),
        contract("$CLOCKS.ClockProvider", null, features),
        contract("$CLOCKS.ClockController", method("getConfig"), features),
        contract("$CLOCKS.ClockFaceController", null, features),
        contract("$CLOCKS.ClockEvents", null, features),
        contract("$CLOCKS.ClockFaceEvents", null, features),
        contract("$CLOCKS.ClockAnimations", null, features),
        contract("$CLOCKS.ClockFaceLayout", null, features),
        contract("$CLOCKS.ClockMetadata", null, features),
        contract("$CLOCKS.ClockConfig", method("getId"), features),
        contract("$CLOCKS.ClockTickRate", Member.Field("PER_MINUTE"), features),
        contract("$CLOCKS.ClockFaceConfig", null, features),
        contract("$CLOCKS.ClockAxisStyle", method("get", STRING), features),
        contract("$CLOCKS.ClockAxisStyle", method("put", STRING, FLOAT), features),
        contract("$CLOCKS.AxisPresetConfig\$Group", method("getPresets"), features),
        contract("$CLOCKS.AxisPresetConfig\$IndexedStyle", method("getPresetIndex"), features),
        contract("$CLOCKS.AxisPresetConfig\$IndexedStyle", method("getGroupIndex"), features),
        contract("$CLOCKS.AxisPresetConfig\$IndexedStyle", method("getStyle"), features),
        contract("$CLOCKS.ClockPickerConfig", null, features),
        contract("$CLOCKS.ClockEventListeners", null, features),
        contract("$CLOCKS.ClockSettings", method("getClockId"), features),
        contract("$CLOCKS.ClockSettings", method("getSeedColor"), features),
        contract("$CLOCKS.ClockSettings", method("getAxes"), features),
        contract("$CLOCKS.ThemeConfig", method("getSeedColor"), features),
        contract("$CLOCKS.ThemeConfig", method("getDefaultColor", CONTEXT), features),
        contract("$CLOCKS.ThemeConfig", method("getAodColor", CONTEXT), features),
        contract("$CLOCKS.ClockPreviewConfig", method("getClockTopMargin"), features),
        contract("$CLOCKS.ClockPreviewConfig", method("getStatusBarHeight"), features),
        contract("$CLOCKS.ClockPreviewConfig", method("getSmallClockTopPadding", INT), features),
        contract("$CLOCKS.ClockViewIds", Member.Field("INSTANCE"), features),
        contract("$CLOCKS.ClockViewIds", method("getLOCKSCREEN_CLOCK_VIEW_LARGE"), features),
        contract("$CLOCKS.ClockViewIds", method("getLOCKSCREEN_CLOCK_VIEW_SMALL"), features),
    )

    /** What the lock screen photo is read through, for the features drawing with it. */
    private val lockWallpaper: List<HostContract> = setOf(DEPTH_EFFECT, AOD_WALLPAPER).let { features ->
        listOf(
            contract(CANVAS_ENGINE, method("drawFrameOnCanvas", "android.graphics.Bitmap"), features),
            contract(CANVAS_ENGINE, method("onOffsetsChanged", FLOAT, FLOAT, FLOAT, FLOAT, INT, INT), features),
            contract(CANVAS_ENGINE, Member.Field("mSurfaceHolder"), features),
        )
    }

    val systemUi: List<HostContract> = clockPlugin(setOf(CLOCK_STYLES, SMARTSPACE_PLACEMENT)) + listOf(
        // The lock screen scene of composed elements, from Android 17 QPR1; a build without it constrains views.
        optional("$ELEMENTS.MovableLockscreenElement", null, CLOCK_STYLES),
        optional("$ELEMENTS.LockscreenElementKeys\$Clock", method("getLarge"), CLOCK_STYLES),
        optional("$ELEMENTS.LockscreenElementKeys\$Clock", method("getSmall"), CLOCK_STYLES),
        optional("$ELEMENTS.BaseLockscreenElement\$ElementSource", Member.Field("DYNAMIC"), CLOCK_STYLES),
        optional("$ELEMENTS.LockscreenScope", Member.Method("burnInAware"), CLOCK_STYLES),
        optional("$ELEMENTS.LockscreenScope", Member.Method("nonAuthUI"), CLOCK_STYLES),
        optional("com.android.systemui.customization.clocks.view.DefaultClockFaceLayout\$Companion", Member.Method("clockView"), CLOCK_STYLES),

        // Clock size motion
        contract("$SIZE_TRANSITION\$ClockFaceTransition", method("addTargets"), CLOCK_SIZE_MOTION),
        contract("$SIZE_TRANSITION\$ClockFaceTransition", Member.Field("viewModel"), CLOCK_SIZE_MOTION),
        contract("$SIZE_TRANSITION\$ClockFaceInTransition", null, CLOCK_SIZE_MOTION),
        contract("$SMARTSPACE_BINDER\$bind\$1\$1\$2\$1", Member.Method("emit"), CLOCK_SIZE_MOTION),
        contract("$SMARTSPACE_BINDER\$bind\$1\$1\$2\$1", Member.Field("\$keyguardRootView"), CLOCK_SIZE_MOTION),
        contract("$SMARTSPACE_BINDER\$bind\$1\$1\$4\$4", Member.Method("emit"), CLOCK_SIZE_MOTION),
        contract("$SMARTSPACE_BINDER\$bind\$1\$1\$4\$4", Member.Field("\$keyguardRootView"), CLOCK_SIZE_MOTION),
        contract(CLOCK_VIEW_MODEL, Member.Field("currentClock"), CLOCK_SIZE_MOTION, SMARTSPACE_PLACEMENT),
        contract(CLOCK_VIEW_MODEL, Member.Field("isLargeClockVisible"), CLOCK_SIZE_MOTION),

        // Date and smartspace placement
        contract(SMARTSPACE_SECTION, Member.Field("keyguardClockViewModel"), SMARTSPACE_PLACEMENT),
        optional(SMARTSPACE_SECTION, method("applyConstraints", "androidx.constraintlayout.widget.ConstraintSet"), SMARTSPACE_PLACEMENT),
        optional(SMARTSPACE_SECTION, Member.Field("dateView"), SMARTSPACE_PLACEMENT),
        optional("$SMARTSPACE_ELEMENTS\$DWARowElement", Member.Method("LockscreenElement"), SMARTSPACE_PLACEMENT),
        optional("$SMARTSPACE_ELEMENTS\$DWAColumnElement", Member.Method("LockscreenElement"), SMARTSPACE_PLACEMENT),
        optional("$SMARTSPACE_ELEMENTS\$CardsElement", Member.Method("LockscreenElement"), SMARTSPACE_PLACEMENT),
        contract(PREVIEW_SMARTSPACE_BINDER, Member.Method("emit"), SMARTSPACE_PLACEMENT),
        contract(PREVIEW_SMARTSPACE_BINDER, Member.Field("\$largeDateView"), SMARTSPACE_PLACEMENT),
        contract(PREVIEW_SMARTSPACE_BINDER, Member.Field("\$smallDateView"), SMARTSPACE_PLACEMENT),
        contract(PREVIEW_SMARTSPACE_BINDER, Member.Field("\$viewModel"), SMARTSPACE_PLACEMENT),
        contract("$KEYGUARD.ui.viewmodel.KeyguardPreviewSmartspaceViewModel", Member.Field("clockViewModel"), SMARTSPACE_PLACEMENT),
        contract("$KEYGUARD.ui.viewmodel.KeyguardPreviewClockViewModel", Member.Field("keyguardClockViewModel"), SMARTSPACE_PLACEMENT),
    ) + lockWallpaper + listOf(
        // Depth effect: kept off a preview of another wallpaper.
        optional(PREVIEW_RENDERER, Member.Method("updateClockAppearance"), DEPTH_EFFECT),
        optional(PREVIEW_RENDERER, Member.Field("previewViewModel"), DEPTH_EFFECT),
        optional("$KEYGUARD.ui.viewmodel.KeyguardPreviewViewModel", Member.Field("interactor"), DEPTH_EFFECT),
        optional("$KEYGUARD.domain.interactor.KeyguardPreviewInteractor", Member.Field("repository"), DEPTH_EFFECT),
        optional("$KEYGUARD.data.repository.KeyguardPreviewRepository", Member.Field("wallpaperColors"), DEPTH_EFFECT),

        // Wallpaper on always-on display
        contract("com.android.systemui.statusbar.LightRevealScrim", method("onDraw", "android.graphics.Canvas"), AOD_WALLPAPER),
    )

    val picker: List<HostContract> = clockPlugin(setOf(PICKER_CLOCKS, PICKER_CLOCK_SIZE)) + listOf(
        optional(LIST_SPACING, Member.Method("getItemOffsets"), PICKER_CLOCKS),
        optional(LIST_SPACING, Member.Method("onDrawOver"), PICKER_CLOCKS),
        optional(LIST_SPACING, Member.Field("dividerIndex"), PICKER_CLOCKS),
        optional("com.android.wallpaper.picker.option.ui.viewmodel.OptionItemViewModel2", Member.Field("key"), PICKER_CLOCKS),
        optional(CLOCK_OPTION_BUILDER, method("invokeSuspend", OBJECT), PICKER_CLOCKS),

        // Clock size slider
        contract(REGISTRY, Member.Method("applySettings"), PICKER_CLOCK_SIZE),
        contract(SHEET_BINDING, method("invokeSuspend", OBJECT), PICKER_CLOCK_SIZE),
        contract(SHEET_BINDING, Member.Field("\$view"), PICKER_CLOCK_SIZE),
        contract(SHEET_BINDING, Member.Field("\$optionsViewModel"), PICKER_CLOCK_SIZE),
        contract("$SHEET_BINDING\$12\$1", Member.Method("emit"), PICKER_CLOCK_SIZE),
        contract(SHEET_BINDER, Member.Field("_clockFloatingSheetHeights"), PICKER_CLOCK_SIZE),
        contract("com.android.customization.picker.clock.ui.viewmodel.ClockFloatingSheetHeightsViewModel", Member.Field("clockSizeContentHeight"), PICKER_CLOCK_SIZE),
        contract(SLIDER, Member.Field("valueFrom"), PICKER_CLOCK_SIZE),
        contract(SLIDER, Member.Field("valueTo"), PICKER_CLOCK_SIZE),
        contract(SLIDER, Member.Field("stepSize"), PICKER_CLOCK_SIZE),
        contract(SLIDER, Member.Field("dirtyConfig"), PICKER_CLOCK_SIZE),
        contract(SLIDER, Member.Field("changeListeners"), PICKER_CLOCK_SIZE),
        contract(SLIDER, method("setValuesInternal", "java.util.ArrayList"), PICKER_CLOCK_SIZE),
        HostContract(
            Signature(SLIDER, Member.Field("defaultThumbDrawables")),
            setOf(PICKER_CLOCK_SIZE),
            alternatives = listOf(Signature(SLIDER, Member.Field("defaultThumbDrawable"))),
        ),
        contract("com.google.android.material.slider.BaseOnChangeListener", null, PICKER_CLOCK_SIZE),
        contract(SHAPE_DRAWABLE, Member.Field("drawableState"), PICKER_CLOCK_SIZE),
        contract(SHAPE_DRAWABLE, method("setFillColor", "android.content.res.ColorStateList"), PICKER_CLOCK_SIZE),
        contract("$SHAPE_DRAWABLE\$MaterialShapeDrawableState", Member.Field("fillColor"), PICKER_CLOCK_SIZE),
        contract("$PICKER_VIEW_MODEL\$isClockAxisStyleEdited\$1", method("invokeSuspend", OBJECT), PICKER_CLOCK_SIZE),
        contract("$PICKER_VIEW_MODEL\$isClockAxisStyleEdited\$1", Member.Field("L\$0"), PICKER_CLOCK_SIZE),
        contract("$PICKER_VIEW_MODEL\$isClockAxisStyleEdited\$1", Member.Field("L\$1"), PICKER_CLOCK_SIZE),
        contract(PICKER_VIEW_MODEL, Member.Field("overridingClockPresetIndexedStyle"), PICKER_CLOCK_SIZE),
        contract(
            "com.android.wallpaper.customization.ui.viewmodel.ThemePickerCustomizationOptionsViewModel",
            Member.Field("clockPickerViewModel"),
            PICKER_CLOCK_SIZE,
        ),

        // Lock screen options
        HostContract(
            Signature(PICKER_FRAGMENT, Member.Method("initCustomizationOptionEntries")),
            setOf(PICKER_LOCK_SCREEN_OPTIONS),
            alternatives = listOf(Signature(PICKER_FRAGMENT, Member.Method("access\$initCustomizationOptionEntries"))),
        ),
        optional(PICKER_FRAGMENT, Member.Method("updateHeaderHeightConstraints"), PICKER_LOCK_SCREEN_OPTIONS),
        optional("com.android.wallpaper.picker.customization.ui.binder.ColorUpdateBinder", Member.Method("bind"), PICKER_LOCK_SCREEN_OPTIONS),
        optional("com.android.wallpaper.customization.ui.binder.SwitchColorBinder", Member.Method("bind"), PICKER_LOCK_SCREEN_OPTIONS),
    )

    fun of(host: Host): List<HostContract> = when (host) {
        Host.SYSTEMUI -> systemUi
        Host.PICKER -> picker
    }

    /**
     * Contracts for the implementation this host actually uses. QPR1's Compose
     * scene animates movable clock elements itself, so the removed legacy view
     * transition is neither installed nor reported as broken there.
     */
    fun forRuntime(host: Host, load: (String) -> Class<*>?): List<HostContract> {
        var contracts = of(host)
        if (host != Host.SYSTEMUI) return contracts

        val registryMethods = load(REGISTRY)?.declaredMethods.orEmpty()
        val hasRegisterListeners = registryMethods.any { it.name == "registerListeners" && it.parameterCount == 0 }
        val hasQuerySettings = registryMethods.any { it.name == "querySettings" && it.parameterCount == 0 }
        if (!hasRegisterListeners && hasQuerySettings) {
            contracts = contracts.map { contract ->
                val isRegistryReady = contract.signature.owner == REGISTRY &&
                    contract.signature.member == Member.Method("registerListeners", emptyList())
                if (isRegistryReady) {
                    contract.copy(
                        signature = Signature(REGISTRY, Member.Method("querySettings", emptyList())),
                        alternatives = emptyList(),
                    )
                } else {
                    contract
                }
            }
        }

        val composeClock = load("$ELEMENTS.MovableLockscreenElement") != null
        val legacyMotion = load("$SIZE_TRANSITION\$ClockFaceTransition") != null
        if (composeClock && !legacyMotion) {
            contracts = contracts.mapNotNull { contract ->
                if (CLOCK_SIZE_MOTION !in contract.features) return@mapNotNull contract
                val remaining = contract.features - CLOCK_SIZE_MOTION
                contract.copy(features = remaining).takeIf { remaining.isNotEmpty() }
            }
        }

        val composeSmartspace = load("$SMARTSPACE_ELEMENTS\$DWARowElement") != null
        return contracts.filterNot { contract ->
            val signature = contract.signature
            if (composeSmartspace) {
                signature.owner == SMARTSPACE_SECTION && signature.member?.name in LEGACY_SMARTSPACE_MEMBERS
            } else {
                signature.owner.startsWith("$SMARTSPACE_ELEMENTS\$")
            }
        }
    }

    private fun method(name: String, vararg parameters: String) = Member.Method(name, parameters.toList())

    private fun contract(owner: String, member: Member?, vararg features: Feature) =
        contract(owner, member, features.toSet())

    private fun contract(owner: String, member: Member?, features: Set<Feature>) =
        HostContract(Signature(owner, member), features)

    private fun optional(owner: String, member: Member?, vararg features: Feature) =
        HostContract(Signature(owner, member), features.toSet(), isRequired = false)

    private val LEGACY_SMARTSPACE_MEMBERS = setOf("applyConstraints", "dateView")
}
