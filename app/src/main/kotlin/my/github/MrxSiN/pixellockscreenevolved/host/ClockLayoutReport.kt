package my.github.MrxSiN.pixellockscreenevolved.host

import android.content.ContentResolver

import java.util.concurrent.Executors

/**
 * What SystemUI's lock screen tells Wallpaper & style about where a large
 * clock may stand, so the picker's preview, which cannot read the photo's
 * subject or the fingerprint sensor, places the clock as the lock screen will:
 * the anchor an adaptive clock stands at ([ClockLayout]), and the lowest its
 * bottom may reach ([LockScreenFloor]), as a share of the screen's height.
 *
 * Written only when a value changes, off the main thread; read from what was
 * last seen, kept up to date by one observer per process ([follow]).
 */
internal object ClockLayoutReport {

    private val adaptiveSetting = SecureIntSetting("pixel_lock_screen_evolved_clock_adaptive", 0)
    private val floorSetting = SecureIntSetting("pixel_lock_screen_evolved_clock_floor", DEFAULT_FLOOR_PERMILLE)

    private val writer by lazy { Executors.newSingleThreadExecutor() }
    private val written = IntArray(2) { UNWRITTEN }
    private var following = false

    /** The share of the band SystemUI last put an adaptive clock at, as last seen. */
    @Volatile
    var adaptiveShare: Float = 0f
        private set

    /** The lowest a large clock's bottom may reach, as a share of the screen's height, as last seen. */
    @Volatile
    var floorShare: Float = DEFAULT_FLOOR_PERMILLE / PERMILLE
        private set

    /** Reports the share of the band an adaptive clock moved to. On the main thread. */
    fun putAdaptive(resolver: ContentResolver, share: Float) = write(resolver, ADAPTIVE, adaptiveSetting, (share * PERMILLE).toInt())

    /** Reports the lowest a large clock's bottom may reach, as a share of the screen's height. On the main thread. */
    fun putFloor(resolver: ContentResolver, share: Float) = write(resolver, FLOOR, floorSetting, (share * PERMILLE).toInt())

    /** Starts keeping [adaptiveShare] and [floorShare] up to date, once per process. On the main thread. */
    fun follow(resolver: ContentResolver) {
        if (following) return
        following = true
        adaptiveSetting.observe(resolver) { adaptiveShare = adaptiveSetting.get(resolver) / PERMILLE }
        floorSetting.observe(resolver) { floorShare = floorSetting.get(resolver) / PERMILLE }
    }

    private fun write(resolver: ContentResolver, slot: Int, setting: SecureIntSetting, value: Int) {
        if (written[slot] == value) return
        written[slot] = value
        writer.execute { runCatching { setting.put(resolver, value) } }
    }

    private const val ADAPTIVE = 0
    private const val FLOOR = 1
    private const val UNWRITTEN = Int.MIN_VALUE
    private const val PERMILLE = 1000f

    /** Where a phone with nothing reported keeps the clock above: the shortcuts at the bottom. */
    private const val DEFAULT_FLOOR_PERMILLE = 700
}
