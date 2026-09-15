package my.github.MrxSiN.pixellockscreenevolved.clock.ios

/**
 * Which characters of the time stay and which change as it moves from one text
 * to the next, for the iOS time's roll from one minute to the next.
 *
 * The two texts are lined up from their right ends, so the minutes keep their
 * places however many digits the hour has: `9:59` to `10:00` changes every
 * digit and brings a new one in on the left, and `1:09` to `1:10` changes only
 * the last two. Kept free of Android so it can be unit tested.
 */
object IosDigitTransition {

    /** One place in the time: the character there before and after, by index, either missing where the texts differ in length. */
    data class Place(val before: Int?, val after: Int?, val changes: Boolean)

    fun between(before: String, after: String): List<Place> {
        val count = maxOf(before.length, after.length)
        return (count - 1 downTo 0).map { fromRight ->
            val old = (before.length - 1 - fromRight).takeIf { it >= 0 }
            val new = (after.length - 1 - fromRight).takeIf { it >= 0 }
            Place(old, new, changes = old == null || new == null || before[old] != after[new])
        }
    }
}
