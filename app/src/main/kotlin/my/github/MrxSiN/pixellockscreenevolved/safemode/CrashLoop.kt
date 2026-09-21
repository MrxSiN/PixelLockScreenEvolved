package my.github.MrxSiN.pixellockscreenevolved.safemode

/**
 * When a host's crashes count as a loop.
 *
 * Three crashes inside a minute is not bad luck: it is a host that starts,
 * reaches the same broken hook and dies again. For SystemUI that leaves no lock
 * screen, no status bar and no way into Wallpaper & style, and enough of them in
 * a row sends the phone to recovery. One crash is not a loop and is left alone.
 */
object CrashLoop {

    const val CRASHES: Int = 3
    const val WINDOW_MILLIS: Long = 60_000L

    /** Whether [crashes], as wall-clock times, make a loop as of [now]. */
    fun isLooping(crashes: List<Long>, now: Long): Boolean =
        recent(crashes, now).size >= CRASHES

    /** The crashes still inside the window, which are the only ones worth keeping. */
    fun recent(crashes: List<Long>, now: Long): List<Long> =
        crashes.filter { now - it in 0..WINDOW_MILLIS }
}
