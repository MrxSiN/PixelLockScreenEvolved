package my.github.MrxSiN.pixellockscreenevolved.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureAvailabilityTest {

    @Test
    fun aHostThatHasNotReportedLeavesItsFeaturesUsable() {
        val availability = FeatureAvailability(emptyMap())

        assertTrue(availability.isAvailable(Feature.DEPTH_EFFECT))
        assertNull(availability.unavailableReason(Feature.DEPTH_EFFECT))
    }

    @Test
    fun aFeatureMissingARequiredMemberIsUnavailableAndSaysToUpdate() {
        val availability = FeatureAvailability(mapOf(Host.SYSTEMUI to snapshot(missing = Feature.DEPTH_EFFECT)))

        assertEquals(FeatureStatus.INCOMPATIBLE, availability.status(Feature.DEPTH_EFFECT))
        assertEquals(
            "Unavailable on SystemUI 38 (CP4A). SystemUI internals changed. Update Pixel Lock Screen Evolved.",
            availability.unavailableReason(Feature.DEPTH_EFFECT),
        )
        assertTrue(availability.isAvailable(Feature.AOD_WALLPAPER))
    }

    @Test
    fun aFeatureSafeModeHoldsOffIsUnavailableUntilRestored() {
        val availability = FeatureAvailability(mapOf(Host.SYSTEMUI to snapshot(held = setOf(Feature.AOD_WALLPAPER))))

        assertEquals(FeatureStatus.HELD_BY_SAFE_MODE, availability.status(Feature.AOD_WALLPAPER))
        assertTrue(availability.summary(Feature.DEPTH_EFFECT).startsWith("Supported on SystemUI 38"))
    }

    @Test
    fun aFeatureStoppedForFailingIsUnavailableAndSaysItIsTriedAgain() {
        val availability = FeatureAvailability(mapOf(Host.SYSTEMUI to snapshot(faulted = setOf(Feature.DEPTH_EFFECT))))

        assertEquals(FeatureStatus.FAULTED, availability.status(Feature.DEPTH_EFFECT))
        assertTrue(availability.unavailableReason(Feature.DEPTH_EFFECT).orEmpty().contains("tried again when SystemUI restarts"))
        assertTrue(availability.isAvailable(Feature.AOD_WALLPAPER))
    }

    @Test
    fun anUnknownBuildIsNotVerified() {
        assertFalse(snapshot().isVerifiedBuild)
        assertTrue(VerifiedBuilds.isVerified("CP3A.260905.009"))
    }

    @Test
    fun aHostManagedFeatureNeedsNoModuleHook() {
        val availability = FeatureAvailability(mapOf(Host.SYSTEMUI to snapshot()))

        assertEquals(FeatureStatus.HOST_MANAGED, availability.status(Feature.CLOCK_SIZE_MOTION))
        assertTrue(availability.isAvailable(Feature.CLOCK_SIZE_MOTION))
        assertTrue(availability.summary(Feature.CLOCK_SIZE_MOTION).startsWith("Handled by SystemUI"))
    }

    @Test
    fun aSnapshotOfAnEarlierBootIsToldApart() {
        val snapshot = snapshot()

        assertTrue(snapshot.isFrom(BOOTED_AT + 5_000))
        assertFalse(snapshot.isFrom(BOOTED_AT + 3_600_000))
    }

    private fun snapshot(missing: Feature? = null, held: Set<Feature> = emptySet(), faulted: Set<Feature> = emptySet()): HostSnapshot {
        val results = listOfNotNull(
            missing?.let {
                ContractResult(HostContract(Signature("a.B", Member.Method("gone")), setOf(it)), ContractOutcome.MISSING_MEMBER)
            },
        )
        return HostSnapshot(
            host = Host.SYSTEMUI,
            hostVersion = "38",
            build = "CP4A",
            bootedAt = BOOTED_AT,
            xposedApiVersion = 102,
            frameworkName = "Vector",
            frameworkVersion = "2.2",
            report = ContractReport(results),
            held = held,
            isAnswered = false,
            faulted = faulted,
        )
    }

    private companion object {
        const val BOOTED_AT = 1_789_000_000_000L
    }
}
