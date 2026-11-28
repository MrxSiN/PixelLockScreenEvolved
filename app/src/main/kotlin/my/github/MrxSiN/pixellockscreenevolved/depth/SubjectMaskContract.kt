package my.github.MrxSiN.pixellockscreenevolved.depth

/**
 * The messages SystemUI and [SubjectMaskService] exchange.
 *
 * SystemUI sends [SEGMENT] with a photo under [KEY_PHOTO] and a request number
 * in `arg1`, and [KEY_CAREFUL] set when a person asked for the subject to be
 * found again, [KEY_DEPTH] set when the depth map is wanted too, and
 * [KEY_MASK_KNOWN] set with it when the caller already keeps the photo's mask
 * and wants only the depth map; the service answers [MASK] with the same number
 * and, under [KEY_MASK] unless the mask was known, an `ALPHA_8` bitmap the size
 * of the photo whose alpha is how surely each pixel belongs to the subject, and
 * under [KEY_DEPTH], where it was asked for and the depth model ran, an
 * `ALPHA_8` bitmap of how near each pixel is, at the size the model worked at;
 * or [FAILED] with a reason under [KEY_REASON]. Kept free of the models and
 * SystemUI so either side can read it.
 */
object SubjectMaskContract {

    /** The service, in the module's own package. */
    const val SERVICE_CLASS = "my.github.MrxSiN.pixellockscreenevolved.depth.SubjectMaskService"

    const val SEGMENT = 1
    const val MASK = 2
    const val FAILED = 3

    const val KEY_PHOTO = "photo"
    const val KEY_MASK = "mask"
    const val KEY_DEPTH = "depth"
    const val KEY_REASON = "reason"

    /**
     * Whether to look twice, at the photo and at its mirror image, and take the
     * two together: about twice as slow, and steadier where a first look went
     * wrong, since asking the same question twice would only get the same answer.
     */
    const val KEY_CAREFUL = "careful"

    /**
     * The caller keeps the photo's mask already and asks for its depth map alone: the subject model, the
     * longer of the two, is not run, as when the depth map is chosen for a photo whose mask was found without it.
     */
    const val KEY_MASK_KNOWN = "mask_known"

    /**
     * The model the service finds masks with. A mask found by another model is
     * not the same mask, so whatever keeps masks keeps them under this name.
     */
    const val MODEL = "birefnet_lite"

    /** The only process the service answers. */
    const val CALLER_PACKAGE = "com.android.systemui"
}
