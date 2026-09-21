package my.github.MrxSiN.pixellockscreenevolved.safemode

import java.io.File
import java.util.Properties

import my.github.MrxSiN.pixellockscreenevolved.diagnostics.Feature

/** What Safe Mode remembers in one host: recent crashes, what it holds off, and whether that was answered. */
interface SafeModeStore {

    fun crashes(): List<Long>

    /** Written before the process dies, so it has to land synchronously. */
    fun recordCrash(time: Long)

    fun clearCrashes()

    /** The features Safe Mode holds off; they are not installed until restored. */
    fun disabled(): Set<Feature>

    fun setDisabled(features: Set<Feature>)

    /** Whether the person has answered for what is held off, so they are not asked again. */
    fun isAnswered(): Boolean

    fun setAnswered(answered: Boolean)
}

/**
 * [SafeModeStore] in a file of the host's own device-protected storage.
 *
 * It has to be read before any feature installs, which is before the host has
 * built a context to read settings through, and a crash can come before the
 * device is first unlocked, when credential-protected storage is still locked.
 */
class FileSafeModeStore(private val file: File) : SafeModeStore {

    override fun crashes(): List<Long> = list(CRASHES).mapNotNull(String::toLongOrNull)

    override fun recordCrash(time: Long) = update {
        setProperty(CRASHES, (CrashLoop.recent(crashes(), time) + time).joinToString(SEPARATOR))
    }

    override fun clearCrashes() = update { remove(CRASHES) }

    override fun disabled(): Set<Feature> = list(DISABLED).mapNotNull(Feature::of).toSet()

    override fun setDisabled(features: Set<Feature>) = update {
        setProperty(DISABLED, features.joinToString(SEPARATOR) { it.name })
    }

    override fun isAnswered(): Boolean = read().getProperty(ANSWERED).toBoolean()

    override fun setAnswered(answered: Boolean) = update { setProperty(ANSWERED, answered.toString()) }

    private fun list(key: String): List<String> =
        read().getProperty(key).orEmpty().split(SEPARATOR).filter(String::isNotBlank)

    @Synchronized
    private fun read(): Properties = Properties().apply {
        runCatching { file.inputStream().use(::load) }
    }

    @Synchronized
    private fun update(change: Properties.() -> Unit) {
        val properties = read().apply(change)
        file.parentFile?.mkdirs()
        file.outputStream().use { stream ->
            properties.store(stream, null)
            // The process may be about to end: the write has to reach the disk now.
            stream.fd.sync()
        }
    }

    private companion object {
        const val CRASHES = "crashes"
        const val DISABLED = "disabled"
        const val ANSWERED = "answered"
        const val SEPARATOR = ","
    }
}
