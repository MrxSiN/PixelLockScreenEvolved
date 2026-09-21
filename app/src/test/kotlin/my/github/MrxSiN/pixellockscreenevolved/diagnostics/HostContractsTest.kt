package my.github.MrxSiN.pixellockscreenevolved.diagnostics

import java.io.File

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Holds the contract list to the code it describes.
 *
 * Every contract names a class and a member some host API looks up. A lookup
 * moved in code and not in the list would leave the compatibility page checking
 * something nothing uses, so each name has to appear in the sources outside the
 * diagnostics package.
 */
class HostContractsTest {

    private val sources: String = File("src/main/kotlin")
        .walkTopDown()
        .filter { it.isFile && it.extension == "kt" && "diagnostics" !in it.invariantSeparatorsPath.split('/') }
        .joinToString("\n") { it.readText() }
        // A dollar sign in a Kotlin string is written escaped.
        .replace("\\$", "$")

    @Test
    fun everyContractNamesWhatAFeatureLooksUp() {
        val signatures = Host.entries.flatMap(HostContracts::of).flatMap { listOf(it.signature) + it.alternatives }

        for (signature in signatures) {
            val type = signature.owner.substringAfterLast('.').substringBefore('$')
            assertTrue("No feature names $type", type in sources)
            signature.member?.let { member ->
                assertTrue("No feature looks up ${signature.describe()}", "\"${member.name}\"" in sources)
            }
        }
    }

    @Test
    fun everyFeatureHasAContractInItsOwnHost() {
        for (feature in Feature.entries) {
            assertTrue("$feature has no contract", HostContracts.of(feature.host).any { feature in it.features })
        }
    }

    @Test
    fun noContractNamesAFeatureOfTheOtherHost() {
        for (host in Host.entries) {
            for (contract in HostContracts.of(host)) {
                assertTrue("${contract.signature.describe()} names a feature of another host", contract.features.all { it.host == host })
            }
        }
    }

    @Test
    fun composeSystemUiDoesNotProbeTheRemovedViewClockTransition() {
        val contracts = HostContracts.forRuntime(Host.SYSTEMUI) { name ->
            Any::class.java.takeIf { name.endsWith("MovableLockscreenElement") }
        }

        assertTrue(contracts.none { Feature.CLOCK_SIZE_MOTION in it.features })
    }

    @Test
    fun composeSystemUiDoesNotProbeTheRemovedConstraintSmartspacePath() {
        val contracts = HostContracts.forRuntime(Host.SYSTEMUI) { name ->
            Any::class.java.takeIf { name.endsWith("SmartspaceElementProvider\$DWARowElement") }
        }

        assertTrue(contracts.none {
            it.signature.owner.endsWith("SmartspaceSection") &&
                it.signature.member?.name in setOf("applyConstraints", "dateView")
        })
    }

    @Test
    fun legacySystemUiDoesNotProbeTheComposeSmartspacePath() {
        val contracts = HostContracts.forRuntime(Host.SYSTEMUI) { null }

        assertTrue(contracts.none { it.signature.owner.contains("SmartspaceElementProvider\$") })
    }

    @Test
    fun systemUiContractUsesItsActualRegistryEntryPoint() {
        val contracts = HostContracts.forRuntime(Host.SYSTEMUI) { name ->
            QueryOnlyRegistry::class.java.takeIf { name.endsWith("ClockRegistry") }
        }
        val registryReady = contracts.first()

        assertTrue(registryReady.signature.member == Member.Method("querySettings", emptyList()))
        assertTrue(registryReady.alternatives.isEmpty())
    }

    private class QueryOnlyRegistry {
        @Suppress("unused")
        fun querySettings() = Unit
    }
}
