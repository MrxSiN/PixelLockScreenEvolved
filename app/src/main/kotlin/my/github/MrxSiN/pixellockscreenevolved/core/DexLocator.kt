package my.github.MrxSiN.pixellockscreenevolved.core

import android.os.Build

import java.io.File

import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.enums.StringMatchType
import org.luckypray.dexkit.query.matchers.ClassMatcher
import org.luckypray.dexkit.query.matchers.MethodMatcher

import my.github.MrxSiN.pixellockscreenevolved.diagnostics.HostSymbols
import my.github.MrxSiN.pixellockscreenevolved.diagnostics.Locator
import my.github.MrxSiN.pixellockscreenevolved.diagnostics.Member
import my.github.MrxSiN.pixellockscreenevolved.diagnostics.Relocations
import my.github.MrxSiN.pixellockscreenevolved.diagnostics.Signature

/**
 * Finds renamed host classes and methods in the host's dex with DexKit.
 *
 * Asked only for names that missed, and answers are kept in [cache] against the
 * host APK and system build, so DexKit (and its native library) is opened once
 * per host update and never on an ordinary start ([HostSymbols]). A locator matching nothing,
 * or more than one thing, is left unanswered: the contract then reports
 * missing and its feature stays off, rather than hooking a guess.
 */
internal class DexLocator(
    private val apk: String,
    /**
     * The module's extracted native libraries. The module packs them compressed
     * (the segmentation runtime is large), so the framework's class loader,
     * which reads libraries straight out of the APK, cannot find DexKit's.
     */
    private val libraries: String,
    private val cache: File,
    private val logger: Logger,
) {

    fun locate(missing: Map<Signature, Locator>): Relocations {
        if (missing.isEmpty()) return Relocations()
        val key = buildKey(missing.keys)
        readCache(key)?.let { return it }

        val started = System.nanoTime()
        val found = runCatching {
            System.load(File(libraries, "libdexkit.so").path)
            DexKitBridge.create(apk).use { bridge ->
                missing.mapNotNull { (signature, locator) -> find(bridge, signature, locator)?.let { signature to it } }.toMap()
            }
        }.getOrElse { error ->
            // Not cached: a search that never ran says nothing about the build.
            logger.warn("DexKit could not search ${File(apk).name}", error)
            return Relocations()
        }

        val relocations = Relocations(found)
        logger.info("DexKit located ${found.size} / ${missing.size} renamed host symbols in ${(System.nanoTime() - started) / 1_000_000} ms")
        relocations.format().forEach { logger.info("  $it") }
        runCatching { cache.writeText((listOf(key) + relocations.format()).joinToString("\n")) }
            .onFailure { logger.warn("Located host symbols could not be cached", it) }
        return relocations
    }

    private fun find(bridge: DexKitBridge, signature: Signature, locator: Locator): Signature? = when (locator) {
        is Locator.NestedClass -> {
            val matcher = ClassMatcher().className("${locator.outer}$", StringMatchType.StartsWith)
            locator.fields.forEach { matcher.addFieldForName(it) }
            locator.methods.forEach { name -> matcher.addMethod(MethodMatcher().name(name)) }
            bridge.findClass(FindClass.create().searchPackages(locator.outer.substringBeforeLast('.')).matcher(matcher))
                .singleOrNull()?.let { Signature(it.name, signature.member) }
        }
        is Locator.MethodCalling -> {
            val calls = MethodMatcher().declaredClass(locator.owner).name(locator.method)
            val matcher = MethodMatcher().declaredClass(signature.owner).addInvoke(calls)
            bridge.findMethod(FindMethod.create().searchPackages(signature.owner.substringBeforeLast('.')).matcher(matcher))
                .singleOrNull { it.isMethod }
                ?.let { Signature(signature.owner, Member.Method(it.methodName, it.paramTypeNames)) }
        }
    }

    private fun readCache(key: String): Relocations? = runCatching {
        val lines = cache.readLines()
        if (lines.firstOrNull() == key) Relocations.parse(lines.drop(1)) else null
    }.getOrNull()

    /** Changes whenever the host APK, the system under it, or what is asked for does; a miss is cached too. */
    private fun buildKey(asked: Set<Signature>): String = File(apk).let { file ->
        "${Build.FINGERPRINT}|${file.length()}|${file.lastModified()}|${Relocations(asked.associateWith { it }).format().hashCode()}"
    }

    companion object {
        /** In each host's own device-protected storage. */
        const val CACHE_FILE = "pixel_lock_screen_evolved_host_symbols"
    }
}
