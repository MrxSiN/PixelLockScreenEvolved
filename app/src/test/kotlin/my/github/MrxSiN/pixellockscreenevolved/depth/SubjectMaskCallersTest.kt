package my.github.MrxSiN.pixellockscreenevolved.depth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The service's own check of each message, behind the manifest's bind permission. */
class SubjectMaskCallersTest {

    private val packages = mapOf(
        SYSTEMUI_UID to arrayOf(SubjectMaskContract.CALLER_PACKAGE),
        APP_UID to arrayOf("com.example.app"),
        SHARED_UID to arrayOf("com.example.one", "com.example.two"),
    )
    private val callers = SubjectMaskCallers { uid -> packages[uid] }

    @Test
    fun systemUiIsAnswered() {
        assertTrue(callers.isAllowed(SYSTEMUI_UID))
    }

    @Test
    fun anotherAppIsNot() {
        assertFalse(callers.isAllowed(APP_UID))
        assertFalse(callers.isAllowed(SHARED_UID))
    }

    @Test
    fun aUidWithNoPackageIsNot() {
        assertFalse(callers.isAllowed(ROOT_UID))
        assertFalse(callers.isAllowed(UNKNOWN_UID))
    }

    /** A message sent within one process carries no sending uid, which is -1. */
    @Test
    fun aMessageBinderDidNotVouchForIsNot() {
        val trusting = SubjectMaskCallers { arrayOf(SubjectMaskContract.CALLER_PACKAGE) }

        assertFalse(trusting.isAllowed(-1))
    }

    private companion object {
        const val ROOT_UID = 0
        const val SYSTEMUI_UID = 10229
        const val APP_UID = 10300
        const val SHARED_UID = 10301
        const val UNKNOWN_UID = 10999
    }
}
