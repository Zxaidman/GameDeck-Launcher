package io.github.zxaidman.kestrel.core.profile

import io.github.zxaidman.kestrel.core.common.Outcome
import io.github.zxaidman.kestrel.core.configuration.ConfigurationId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private fun id(raw: String) = (ConfigurationId.parse(raw) as Outcome.Success).value

private fun profile(
    idRaw: String,
    scope: ProfileScope,
    pinnedTo: String? = null,
    enabled: Boolean = true,
) = ProfileSummary(id(idRaw), idRaw, scope, pinnedTo, enabled)

private val EMULATOR = TargetDescriptor("org.example.emulator", family = "emulator")

class ProfileMatchingTest {

    private val default = profile("user.default", ProfileScope.Default)
    private val family = profile("user.emulators", ProfileScope.Family("emulator"))
    private val exact = profile("user.that-one", ProfileScope.Target("org.example.emulator"))

    @Test
    fun `the most specific rule wins`() {
        val match = matchProfile(EMULATOR, listOf(default, family, exact))

        assertEquals(exact.id, match.profile?.id)
        assertEquals(MatchReason.EXACT_TARGET, match.reason)
    }

    @Test
    fun `a family rule beats the default`() {
        val match = matchProfile(EMULATOR, listOf(default, family))

        assertEquals(family.id, match.profile?.id)
        assertEquals(MatchReason.FAMILY, match.reason)
    }

    @Test
    fun `the default applies when nothing more specific does`() {
        val match = matchProfile(TargetDescriptor("org.example.other"), listOf(default, family))

        assertEquals(default.id, match.profile?.id)
        assertEquals(MatchReason.DEFAULT, match.reason)
    }

    @Test
    fun `a user's pin beats every rule, including a more specific one`() {
        val pinned = profile("user.mine", ProfileScope.Default, pinnedTo = "org.example.emulator")

        val match = matchProfile(EMULATOR, listOf(default, family, exact, pinned))

        // The user overruled the product on purpose. Nothing automatic may outrank that.
        assertEquals(pinned.id, match.profile?.id)
        assertEquals(MatchReason.PINNED, match.reason)
    }

    @Test
    fun `a pin for a different target does not apply`() {
        val elsewhere = profile("user.elsewhere", ProfileScope.Default, pinnedTo = "org.example.other")

        val match = matchProfile(EMULATOR, listOf(family, elsewhere))

        assertEquals(family.id, match.profile?.id)
    }

    @Test
    fun `a disabled profile is skipped rather than chosen and ignored`() {
        val disabledExact = profile("user.that-one", ProfileScope.Target(EMULATOR.targetId), enabled = false)

        val match = matchProfile(EMULATOR, listOf(default, family, disabledExact))

        assertEquals(family.id, match.profile?.id)
    }

    @Test
    fun `no profiles at all is a stated outcome, not a silent one`() {
        val match = matchProfile(EMULATOR, emptyList())

        assertNull(match.profile)
        assertEquals(MatchReason.NONE, match.reason)
        assertTrue(!match.hasProfile)
    }

    @Test
    fun `a family rule does not apply to a target with no family`() {
        val match = matchProfile(TargetDescriptor("org.example.unknown"), listOf(family))

        assertEquals(MatchReason.NONE, match.reason)
    }

    @Test
    fun `ties break the same way every time`() {
        val a = profile("user.aaa", ProfileScope.Family("emulator"))
        val b = profile("user.bbb", ProfileScope.Family("emulator"))

        // Deliberately arbitrary and deliberately stable. Breaking ties by "most recently edited"
        // would mean opening the editor silently changes which layout appears next launch.
        assertEquals(a.id, matchProfile(EMULATOR, listOf(a, b)).profile?.id)
        assertEquals(a.id, matchProfile(EMULATOR, listOf(b, a)).profile?.id)
    }

    @Test
    fun `the order profiles are supplied in never changes the answer`() {
        val all = listOf(default, family, exact)

        val forwards = matchProfile(EMULATOR, all)
        val backwards = matchProfile(EMULATOR, all.reversed())

        assertEquals(forwards.profile?.id, backwards.profile?.id)
        assertEquals(forwards.reason, backwards.reason)
    }
}

class CandidateProfilesTest {

    private val default = profile("user.default", ProfileScope.Default)
    private val family = profile("user.emulators", ProfileScope.Family("emulator"))
    private val exact = profile("user.that-one", ProfileScope.Target("org.example.emulator"))
    private val unrelated = profile("user.other", ProfileScope.Target("org.example.other"))

    @Test
    fun `every applicable profile is offered, best first`() {
        val candidates = candidateProfiles(EMULATOR, listOf(default, unrelated, family, exact))

        assertEquals(listOf(exact.id, family.id, default.id), candidates.map { it.id })
    }

    @Test
    fun `the head of the list is always what matching returns`() {
        val all = listOf(default, family, exact)

        assertEquals(
            matchProfile(EMULATOR, all).profile?.id,
            candidateProfiles(EMULATOR, all).first().id,
        )
    }

    @Test
    fun `a profile for another target is not offered`() {
        val candidates = candidateProfiles(EMULATOR, listOf(unrelated))

        assertTrue(candidates.isEmpty())
    }
}
