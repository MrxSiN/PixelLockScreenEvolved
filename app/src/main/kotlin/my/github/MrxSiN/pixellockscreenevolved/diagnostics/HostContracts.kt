package my.github.MrxSiN.pixellockscreenevolved.diagnostics

import my.github.MrxSiN.pixellockscreenevolved.diagnostics.Feature.AOD_WALLPAPER
import my.github.MrxSiN.pixellockscreenevolved.diagnostics.Feature.CLOCK_POSITION
import my.github.MrxSiN.pixellockscreenevolved.diagnostics.Feature.CLOCK_SIZE_MOTION
import my.github.MrxSiN.pixellockscreenevolved.diagnostics.Feature.CLOCK_STYLES
import my.github.MrxSiN.pixellockscreenevolved.diagnostics.Feature.DEPTH_EFFECT
import my.github.MrxSiN.pixellockscreenevolved.diagnostics.Feature.DOUBLE_TAP_TO_SLEEP
import my.github.MrxSiN.pixellockscreenevolved.diagnostics.Feature.PICKER_CLOCKS
import my.github.MrxSiN.pixellockscreenevolved.diagnostics.Feature.PICKER_CLOCK_POSITION
import my.github.MrxSiN.pixellockscreenevolved.diagnostics.Feature.PICKER_CLOCK_SIZE
import my.github.MrxSiN.pixellockscreenevolved.diagnostics.Feature.PICKER_LOCK_SCREEN_OPTIONS
import my.github.MrxSiN.pixellockscreenevolved.diagnostics.Feature.SMARTSPACE_PLACEMENT

/**
 * The host members every feature stands on, as read off `CP3A.260905.009`
 * (double tap to turn off screen and clock position: `CP3A.261005.005`).
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
    private const val TOUCH_HANDLING = "$KEYGUARD.domain.interactor.KeyguardTouchHandlingInteractor"
    private const val TAP_CALLBACKS = "$KEYGUARD.ui.composable.LockscreenTouchHandlingKt\$LockscreenTouchHandling\$3\$1\$\$ExternalSyntheticLambda0"
    private const val LIGHT_REVEAL_SCRIM = "com.android.systemui.statusbar.LightRevealScrim"
    private const val CANVAS_ENGINE = "com.android.systemui.wallpapers.ImageWallpaper\$CanvasEngine"
    private const val FINGERPRINT_MANAGER = "android.hardware.fingerprint.FingerprintManager"
    private const val FINGERPRINT_SENSOR = "android.hardware.fingerprint.FingerprintSensorPropertiesInternal"
    private const val SENSOR_LOCATION = "android.hardware.biometrics.SensorLocationInternal"

    private const val PICKER_FRAGMENT = "com.android.wallpaper.picker.customization.ui.CustomizationPickerFragment"
    private const val SHEET_BINDER = "com.android.customization.picker.clock.ui.binder.ClockFloatingSheetBinder"
    private const val SHEET_BINDING = "$SHEET_BINDER\$bind\$13\$1"
    private const val PICKER_VIEW_MODEL = "com.android.customization.picker.clock.ui.viewmodel.ClockPickerViewModel"
    private const val CLOCK_OPTION_BUILDER = "$PICKER_VIEW_MODEL\$clockStyleOptions\$1"
    private const val SLIDER = "com.google.android.material.slider.BaseSlider"
    private const val SHAPE_DRAWABLE = "com.google.android.material.shape.MaterialShapeDrawable"
    private const val TAB_ADAPTER = "com.android.wallpaper.picker.customization.ui.view.adapter.FloatingToolbarTabAdapter"
    private const val RECYCLER_VIEW = "androidx.recyclerview.widget.RecyclerView"
    private const val TAB_VIEW_MODEL = "com.android.wallpaper.picker.customization.ui.viewmodel.FloatingToolbarTabViewModel"
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
        contract("$CLOCKS.AxisPresetConfig\$Group", null, features),
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

    val systemUi: List<HostContract> = clockPlugin(setOf(CLOCK_STYLES, SMARTSPACE_PLACEMENT, CLOCK_POSITION)) + listOf(
        // The lock screen scene of composed elements, from Android 17 QPR1; a build without it constrains views.
        optional("$ELEMENTS.MovableLockscreenElement", null, CLOCK_STYLES),
        optional("$ELEMENTS.LockscreenElementKeys\$Clock", method("getLarge"), CLOCK_STYLES),
        optional("$ELEMENTS.LockscreenElementKeys\$Clock", method("getSmall"), CLOCK_STYLES),
        optional("$ELEMENTS.BaseLockscreenElement\$ElementSource", Member.Field("DYNAMIC"), CLOCK_STYLES),
        optional("$ELEMENTS.LockscreenScope", Member.Method("burnInAware"), CLOCK_STYLES),
        optional("$ELEMENTS.LockscreenScope", Member.Method("nonAuthUI"), CLOCK_STYLES),
        optional("com.android.systemui.customization.clocks.view.DefaultClockFaceLayout\$Companion", Member.Method("clockView"), CLOCK_STYLES),

        // Clock position: the under-display fingerprint sensor it keeps clear, as the framework describes it.
        optional(FINGERPRINT_MANAGER, method("getSensorPropertiesInternal"), CLOCK_POSITION),
        optional(FINGERPRINT_SENSOR, method("isAnyUdfpsType"), CLOCK_POSITION),
        optional(FINGERPRINT_SENSOR, method("getLocation"), CLOCK_POSITION),
        optional(SENSOR_LOCATION, Member.Field("sensorLocationY"), CLOCK_POSITION),
        optional(SENSOR_LOCATION, Member.Field("sensorRadius"), CLOCK_POSITION),

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
        optional("com.android.systemui.customization.clocks.R\$dimen", Member.Field("small_clock_height"), SMARTSPACE_PLACEMENT),
        optional("androidx.compose.ui.res.PrimitiveResources_androidKt", Member.Method("dimensionResource"), SMARTSPACE_PLACEMENT),
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
        contract(LIGHT_REVEAL_SCRIM, method("onDraw", "android.graphics.Canvas"), AOD_WALLPAPER),

        // Double tap to turn off screen: SystemUI's own gesture, its flag set from the stock setting.
        contract(TOUCH_HANDLING, method("onDoubleClick"), DOUBLE_TAP_TO_SLEEP),
        contract(TOUCH_HANDLING, Member.Field("isDoubleTapHandlingEnabled"), DOUBLE_TAP_TO_SLEEP),
        contract(TOUCH_HANDLING, Member.Field("context"), DOUBLE_TAP_TO_SLEEP),
        contract("kotlinx.coroutines.flow.ReadonlyStateFlow", Member.Field("\$\$delegate_0"), DOUBLE_TAP_TO_SLEEP),
        contract("kotlinx.coroutines.flow.StateFlowImpl", method("getValue"), DOUBLE_TAP_TO_SLEEP),
        contract("kotlinx.coroutines.flow.StateFlowImpl", method("setValue", OBJECT), DOUBLE_TAP_TO_SLEEP),
        // Its screen off drawn as a circle closing on the tap; without these it keeps SystemUI's lift.
        optional(TAP_CALLBACKS, method("invoke", OBJECT), DOUBLE_TAP_TO_SLEEP),
        optional("androidx.compose.ui.geometry.Offset", Member.Field("packedValue"), DOUBLE_TAP_TO_SLEEP),
        optional("com.android.systemui.statusbar.LiftReveal", method("setRevealAmountOnScrim", FLOAT, LIGHT_REVEAL_SCRIM), DOUBLE_TAP_TO_SLEEP),
        optional("com.android.systemui.statusbar.PowerButtonReveal", method("setRevealAmountOnScrim", FLOAT, LIGHT_REVEAL_SCRIM), DOUBLE_TAP_TO_SLEEP),
        optional("com.android.systemui.statusbar.CircleReveal", method("setRevealAmountOnScrim", FLOAT, LIGHT_REVEAL_SCRIM), DOUBLE_TAP_TO_SLEEP),
        optional(LIGHT_REVEAL_SCRIM, method("setRevealGradientBounds", FLOAT, FLOAT, FLOAT, FLOAT), DOUBLE_TAP_TO_SLEEP),
        optional(LIGHT_REVEAL_SCRIM, method("setRevealGradientEndColorAlpha", FLOAT), DOUBLE_TAP_TO_SLEEP),
        // The first tap after it wakes again; without these that tap keeps SystemUI's falsing check.
        optional("com.android.systemui.shade.PulsingGestureListener", method("onSingleTapUp", FLOAT, FLOAT), DOUBLE_TAP_TO_SLEEP),
        optional("com.android.systemui.classifier.BrightLineFalsingManager", method("isFalseTap", INT), DOUBLE_TAP_TO_SLEEP),
    )

    val picker: List<HostContract> = clockPlugin(setOf(PICKER_CLOCKS, PICKER_CLOCK_SIZE, PICKER_CLOCK_POSITION)) + listOf(
        optional(LIST_SPACING, Member.Method("getItemOffsets"), PICKER_CLOCKS),
        optional(LIST_SPACING, Member.Method("onDrawOver"), PICKER_CLOCKS),
        optional(LIST_SPACING, Member.Field("dividerIndex"), PICKER_CLOCKS),
        optional("com.android.wallpaper.picker.option.ui.viewmodel.OptionItemViewModel2", Member.Field("key"), PICKER_CLOCKS),
        optional(CLOCK_OPTION_BUILDER, method("invokeSuspend", OBJECT), PICKER_CLOCKS),

        // Clock size slider and position controls
        contract(REGISTRY, Member.Method("applySettings"), PICKER_CLOCK_SIZE, PICKER_CLOCK_POSITION),
        contract(SHEET_BINDING, method("invokeSuspend", OBJECT), PICKER_CLOCK_SIZE, PICKER_CLOCK_POSITION),
        contract(SHEET_BINDING, Member.Field("\$view"), PICKER_CLOCK_SIZE, PICKER_CLOCK_POSITION),
        contract(SHEET_BINDING, Member.Field("\$optionsViewModel"), PICKER_CLOCK_SIZE, PICKER_CLOCK_POSITION),
        contract(SHEET_BINDER, Member.Field("_clockFloatingSheetHeights"), PICKER_CLOCK_SIZE, PICKER_CLOCK_POSITION),
        contract("com.android.customization.picker.clock.ui.viewmodel.ClockFloatingSheetHeightsViewModel", Member.Field("clockSizeContentHeight"), PICKER_CLOCK_SIZE, PICKER_CLOCK_POSITION),
        contract(SLIDER, Member.Field("valueFrom"), PICKER_CLOCK_SIZE, PICKER_CLOCK_POSITION),
        contract(SLIDER, Member.Field("valueTo"), PICKER_CLOCK_SIZE, PICKER_CLOCK_POSITION),
        contract(SLIDER, Member.Field("stepSize"), PICKER_CLOCK_SIZE, PICKER_CLOCK_POSITION),
        contract(SLIDER, Member.Field("dirtyConfig"), PICKER_CLOCK_SIZE, PICKER_CLOCK_POSITION),
        contract(SLIDER, Member.Field("changeListeners"), PICKER_CLOCK_SIZE, PICKER_CLOCK_POSITION),
        contract(SLIDER, method("setValuesInternal", "java.util.ArrayList"), PICKER_CLOCK_SIZE, PICKER_CLOCK_POSITION),
        HostContract(
            Signature(SLIDER, Member.Field("defaultThumbDrawables")),
            setOf(PICKER_CLOCK_SIZE, PICKER_CLOCK_POSITION),
            alternatives = listOf(Signature(SLIDER, Member.Field("defaultThumbDrawable"))),
        ),
        contract("com.google.android.material.slider.BaseOnChangeListener", null, PICKER_CLOCK_SIZE, PICKER_CLOCK_POSITION),
        contract(SHAPE_DRAWABLE, Member.Field("drawableState"), PICKER_CLOCK_SIZE, PICKER_CLOCK_POSITION),
        contract(SHAPE_DRAWABLE, method("setFillColor", "android.content.res.ColorStateList"), PICKER_CLOCK_SIZE, PICKER_CLOCK_POSITION),
        contract("$SHAPE_DRAWABLE\$MaterialShapeDrawableState", Member.Field("fillColor"), PICKER_CLOCK_SIZE, PICKER_CLOCK_POSITION),
        contract("$PICKER_VIEW_MODEL\$isClockAxisStyleEdited\$1", method("invokeSuspend", OBJECT), PICKER_CLOCK_SIZE, PICKER_CLOCK_POSITION),
        contract("$PICKER_VIEW_MODEL\$isClockAxisStyleEdited\$1", Member.Field("L\$0"), PICKER_CLOCK_SIZE, PICKER_CLOCK_POSITION),
        contract("$PICKER_VIEW_MODEL\$isClockAxisStyleEdited\$1", Member.Field("L\$1"), PICKER_CLOCK_SIZE, PICKER_CLOCK_POSITION),
        contract(PICKER_VIEW_MODEL, Member.Field("overridingClockPresetIndexedStyle"), PICKER_CLOCK_SIZE, PICKER_CLOCK_POSITION),
        contract(
            "com.android.wallpaper.customization.ui.viewmodel.ThemePickerCustomizationOptionsViewModel",
            Member.Field("clockPickerViewModel"),
            PICKER_CLOCK_SIZE,
            PICKER_CLOCK_POSITION,
        ),

        // The clock previewed, for the Size tab's rows, and the sheet's colours, for the position switch.
        optional(PICKER_VIEW_MODEL, Member.Field("overridingClock"), PICKER_CLOCK_SIZE, PICKER_CLOCK_POSITION),
        optional("com.android.customization.picker.clock.shared.model.ClockMetadataModel", Member.Field("clockId"), PICKER_CLOCK_SIZE, PICKER_CLOCK_POSITION),
        optional(SHEET_BINDING, Member.Field("\$colorUpdateViewModel"), PICKER_CLOCK_POSITION),
        optional(SHEET_BINDING, Member.Field("\$lifecycleOwner"), PICKER_CLOCK_POSITION),

        // Position tab: the clock sheet's tab list, rewritten on its way to the adapter, riding the Size slot.
        contract(SHEET_BINDING, Member.Field("\$tabAdapter"), PICKER_CLOCK_POSITION),
        contract(SHEET_BINDING, Member.Field("\$viewModel"), PICKER_CLOCK_POSITION),
        contract(TAB_ADAPTER, method("submitList", "java.util.List"), PICKER_CLOCK_POSITION),
        contract(TAB_VIEW_MODEL, Member.Field("icon"), PICKER_CLOCK_POSITION),
        contract(TAB_VIEW_MODEL, Member.Field("text"), PICKER_CLOCK_POSITION),
        contract(TAB_VIEW_MODEL, Member.Field("isSelected"), PICKER_CLOCK_POSITION),
        contract(TAB_VIEW_MODEL, Member.Field("onClick"), PICKER_CLOCK_POSITION),
        contract("com.android.wallpaper.picker.common.icon.ui.viewmodel.Icon\$Loaded", null, PICKER_CLOCK_POSITION),
        contract("com.android.wallpaper.picker.common.text.ui.viewmodel.Text\$Loaded", null, PICKER_CLOCK_POSITION),
        contract(PICKER_VIEW_MODEL, Member.Field("_selectedTab"), PICKER_CLOCK_POSITION),
        contract("$PICKER_VIEW_MODEL\$Tab", Member.Field("SIZE"), PICKER_CLOCK_POSITION),
        optional("$PICKER_VIEW_MODEL\$tabs\$1\$1", method("invoke"), PICKER_CLOCK_POSITION),
        optional(RECYCLER_VIEW, Member.Field("mAdapter"), PICKER_CLOCK_POSITION),
        optional(RECYCLER_VIEW, Member.Field("mItemAnimator"), PICKER_CLOCK_POSITION),
        optional(RECYCLER_VIEW, method("hasPendingAdapterUpdates"), PICKER_CLOCK_POSITION),
        optional("androidx.recyclerview.widget.DefaultItemAnimator", method("endAnimations"), PICKER_CLOCK_POSITION),

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

    /** Every class a contract of [host] names, alternatives included: what a host description has to cover. */
    fun classesNamed(host: Host): Set<String> =
        of(host).flatMapTo(sortedSetOf()) { contract -> (listOf(contract.signature) + contract.alternatives).map(Signature::owner) }

    /**
     * Contracts for the implementation this host actually uses. QPR1's Compose
     * scene animates movable clock elements itself, so the removed legacy view
     * transition is neither installed nor reported as broken there.
     */
    fun forRuntime(host: Host, types: HostTypes): List<HostContract> {
        var contracts = of(host)
        if (host != Host.SYSTEMUI) return contracts

        val registryMethods = types.find(REGISTRY)?.methods.orEmpty()
        val hasRegisterListeners = registryMethods.any { it.name == "registerListeners" && it.parameters.isEmpty() }
        val hasQuerySettings = registryMethods.any { it.name == "querySettings" && it.parameters.isEmpty() }
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

        val composeClock = types.find("$ELEMENTS.MovableLockscreenElement") != null
        val legacyMotion = types.find("$SIZE_TRANSITION\$ClockFaceTransition") != null
        if (composeClock && !legacyMotion) {
            contracts = contracts.mapNotNull { contract ->
                if (CLOCK_SIZE_MOTION !in contract.features) return@mapNotNull contract
                val remaining = contract.features - CLOCK_SIZE_MOTION
                contract.copy(features = remaining).takeIf { remaining.isNotEmpty() }
            }
        }

        val composeSmartspace = types.find("$SMARTSPACE_ELEMENTS\$DWARowElement") != null
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
