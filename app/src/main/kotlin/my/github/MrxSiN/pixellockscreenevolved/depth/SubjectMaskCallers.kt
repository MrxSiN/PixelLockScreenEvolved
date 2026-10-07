package my.github.MrxSiN.pixellockscreenevolved.depth

/**
 * Who [SubjectMaskService] answers: only a process running as
 * [SubjectMaskContract.CALLER_PACKAGE]'s own uid.
 *
 * The manifest lets only holders of `STATUS_BAR_SERVICE` bind at all; this is
 * the second check, made on each message before any of its data is read. The
 * uid is the one Binder vouches for (`Message.sendingUid`), never anything the
 * caller writes into the message.
 *
 * @param packagesForUid the packages installed under a uid, as `PackageManager` names them.
 */
class SubjectMaskCallers(private val packagesForUid: (Int) -> Array<String>?) {

    fun isAllowed(uid: Int): Boolean =
        uid >= 0 && SubjectMaskContract.CALLER_PACKAGE in packagesForUid(uid).orEmpty()
}
