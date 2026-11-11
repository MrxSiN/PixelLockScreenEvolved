package my.github.MrxSiN.pixellockscreenevolved.diagnostics

import java.io.File

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks every [HostContract] against each Pixel build the module was verified
 * on, as recorded under `src/test/resources/hosts/<Build.ID>/<package>.txt`
 * ([HostDescriptor], captured with `scripts/capture-host-fixture.sh`).
 *
 * The other diagnostics tests check the module's own logic; this one checks the
 * contracts themselves against Google's SystemUI and Wallpaper & style. A
 * contract a refactor renames, retypes or points at the wrong class fails here,
 * in CI, instead of on a phone as a feature that silently stops.
 */
class HostCompatibilityTest {

    private val recorded: List<HostDescriptor.DescribedHost> = File("src/test/resources/hosts")
        .walkTopDown()
        .filter { it.isFile && it.extension == "txt" }
        .sortedBy { it.path }
        .map { file ->
            HostDescriptor.read(file.readText()).also { described ->
                assertEquals("$file records another build", file.parentFile.name, described.build)
                assertEquals("$file records another host", file.nameWithoutExtension, described.host.packageName)
            }
        }
        .toList()

    @Test
    fun eachRecordedBuildIsVerifiedAndRecordsBothHosts() {
        assertTrue("No host build is recorded", recorded.isNotEmpty())
        for ((build, hosts) in recorded.groupBy { it.build }) {
            assertTrue("$build is recorded but not in VerifiedBuilds", VerifiedBuilds.isVerified(build))
            assertEquals("$build does not record every host", Host.entries.toSet(), hosts.map { it.host }.toSet())
        }
    }

    @Test
    fun eachRecordedBuildCoversEveryClassTheContractsName() {
        for (described in recorded) {
            // A class recorded neither way throws, naming the capture to run again.
            HostContracts.classesNamed(described.host).forEach(described::find)
        }
    }

    @Test
    fun everyContractIsMetOnEveryRecordedBuild() {
        val unmet = recorded.flatMap { described ->
            ContractAnalyzer(described, described.relocations::relocate).analyze(HostContracts.forRuntime(described.host, described)).results
                .filterNot(ContractResult::isMet)
                .map { "${described.build} ${described.host.title}: ${it.contract.signature.describe()} ${it.outcome}" }
        }

        assertEquals("Contracts not met:\n${unmet.joinToString("\n")}", emptyList<String>(), unmet)
    }

    @Test
    fun everyFeatureIsCompatibleOnEveryRecordedBuild() {
        for (described in recorded) {
            val report = ContractAnalyzer(described, described.relocations::relocate).analyze(HostContracts.forRuntime(described.host, described))
            for (feature in Feature.of(described.host)) {
                assertTrue(
                    "${feature.title} is not compatible with ${described.build}",
                    report.isCompatible(feature) || report.isHostManaged(feature),
                )
            }
        }
    }

    /** The suite has teeth: a member renamed in a contract is caught against a real build. */
    @Test
    fun aRenamedContractMemberFailsAgainstARecordedBuild() {
        val described = recorded.first()
        val contract = HostContracts.forRuntime(described.host, described).first { it.signature.member != null }
        val renamed = when (val member = requireNotNull(contract.signature.member)) {
            is Member.Field -> member.copy(name = member.name + "Renamed")
            is Member.Method -> member.copy(name = member.name + "Renamed")
        }
        val broken = contract.copy(signature = contract.signature.copy(member = renamed), alternatives = emptyList())

        assertFalse(ContractAnalyzer(described, described.relocations::relocate).analyze(listOf(broken)).results.single().isMet)
    }
}
