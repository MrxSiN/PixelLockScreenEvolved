package my.github.MrxSiN.pixellockscreenevolved.diagnostics

/** A process this module changes. */
enum class Host(val title: String, val packageName: String) {
    SYSTEMUI("SystemUI", "com.android.systemui"),
    PICKER("Wallpaper & style", "com.google.android.apps.wallpaper"),
    ;

    companion object {
        fun of(packageName: String): Host? = entries.firstOrNull { it.packageName == packageName }
    }
}

/**
 * One thing this module does, as the compatibility page and Safe Mode name it,
 * and the process it is done in.
 *
 * Each is installed only when every member of its host it cannot work without
 * is there ([HostContracts]), and not while Safe Mode holds it off.
 */
enum class Feature(val title: String, val host: Host, val hostManagedWhenAbsent: Boolean = false) {
    CLOCK_STYLES("Clock styles", Host.SYSTEMUI),
    CLOCK_SIZE_MOTION("Clock size motion", Host.SYSTEMUI, hostManagedWhenAbsent = true),
    CLOCK_POSITION("Clock position", Host.SYSTEMUI),
    SMARTSPACE_PLACEMENT("Date and smartspace placement", Host.SYSTEMUI),
    DEPTH_EFFECT("Depth effect", Host.SYSTEMUI),
    AOD_WALLPAPER("Wallpaper on always-on display", Host.SYSTEMUI),
    DOUBLE_TAP_TO_SLEEP("Double tap to turn off screen", Host.SYSTEMUI),
    PICKER_CLOCKS("Clock styles in Wallpaper & style", Host.PICKER),
    PICKER_CLOCK_SIZE("Clock size slider", Host.PICKER),
    PICKER_CLOCK_POSITION("Clock position controls", Host.PICKER),
    PICKER_LOCK_SCREEN_OPTIONS("Lock screen options", Host.PICKER),
    ;

    companion object {
        fun of(name: String): Feature? = entries.firstOrNull { it.name == name }

        fun of(host: Host): List<Feature> = entries.filter { it.host == host }
    }
}
