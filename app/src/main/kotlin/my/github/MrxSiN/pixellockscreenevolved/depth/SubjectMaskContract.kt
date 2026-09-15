package my.github.MrxSiN.pixellockscreenevolved.depth

/**
 * The messages SystemUI and [SubjectMaskService] exchange.
 *
 * SystemUI sends [SEGMENT] with a photo under [KEY_PHOTO] and a request number
 * in `arg1`; the service answers [MASK] with the same number and, under
 * [KEY_MASK], an `ALPHA_8` bitmap the size of the photo whose alpha is how
 * surely each pixel belongs to the subject, or [FAILED] with a reason under
 * [KEY_REASON]. Kept free of both the model and SystemUI so either side can read it.
 */
object SubjectMaskContract {

    /** The service, in the module's own package. */
    const val SERVICE_CLASS = "my.github.MrxSiN.pixellockscreenevolved.depth.SubjectMaskService"

    const val SEGMENT = 1
    const val MASK = 2
    const val FAILED = 3

    const val KEY_PHOTO = "photo"
    const val KEY_MASK = "mask"
    const val KEY_REASON = "reason"

    /**
     * The model the service finds masks with. A mask found by another model is
     * not the same mask, so whatever keeps masks keeps them under this name.
     */
    const val MODEL = "birefnet_lite"

    /** The only process the service answers. */
    const val CALLER_PACKAGE = "com.android.systemui"
}
