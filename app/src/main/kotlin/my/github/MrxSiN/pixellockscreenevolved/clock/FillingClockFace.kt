package my.github.MrxSiN.pixellockscreenevolved.clock

/**
 * A large face that runs down the lock screen, and so must know how far it may:
 * from the large clock's top to the lock screen's floor, above the fingerprint
 * sensor and any card under the clock. Sized from the screen alone, such a face
 * ran under the sensor awake, and off the top of the screen once the always-on
 * display lifted it clear of the sensor.
 *
 * Kept apart from [ClockFace] so a face that keeps to its own box is never made
 * to answer for the room below it.
 */
interface FillingClockFace : ClockFace {

    /** The face may be [height] pixels tall, as laid out (before the scene scales it). */
    fun setRoom(height: Float)
}
