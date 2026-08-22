package io.github.zxaidman.kestrel.core.profile

import io.github.zxaidman.kestrel.core.configuration.ConfigurationId

/**
 * Choosing which profile applies when a target is launched.
 *
 * The rule the whole design rests on: **the answer is deterministic, and it can always say why.**
 * `docs/DEGRADED_STATE.md` §6 forbids silent outcomes, and a launcher that quietly picks a
 * different layout than last time — or picks one for a reason the user cannot see — is the same
 * failure wearing different clothes.
 */

/** What a profile applies to. More specific rules win, and specificity is defined, not implied. */
public sealed interface ProfileScope {

    /** Applies to one target, named exactly. The most specific thing a user can say. */
    public data class Target(public val targetId: String) : ProfileScope

    /**
     * Applies to a family of targets — every emulator, every streaming client.
     *
     * Useful because a user's preferences are usually about a *kind* of thing: the same layout for
     * every handheld-console emulator, a different one for streaming.
     */
    public data class Family(public val family: String) : ProfileScope

    /** Applies to anything without a better match. */
    public data object Default : ProfileScope

    /** Higher wins. Explicit rather than derived, so precedence is readable in one place. */
    public val specificity: Int
        get() = when (this) {
            is Target -> 3
            is Family -> 2
            Default -> 1
        }
}

/** A profile as far as matching is concerned. The rest of it does not affect which one is chosen. */
public data class ProfileSummary(
    public val id: ConfigurationId,
    public val name: String,
    public val scope: ProfileScope,
    /**
     * Set when the user chose this profile for this target by hand.
     *
     * A pin beats every rule, including a more specific one, because it is the user overruling the
     * product on purpose. Nothing else may outrank it — an automatic choice quietly replacing a
     * deliberate one is the behaviour this whole file exists to prevent.
     */
    public val pinnedToTarget: String? = null,
    /** Ignored while matching, but kept so a disabled profile can be shown as such. */
    public val enabled: Boolean = true,
)

/** What a target looks like at the moment of launching it. */
public data class TargetDescriptor(
    public val targetId: String,
    public val family: String? = null,
)

/** Why a profile was chosen, in words the interface can show without inventing an explanation. */
public enum class MatchReason {
    /** The user pinned this profile to this target. */
    PINNED,

    /** A profile names this target exactly. */
    EXACT_TARGET,

    /** A profile covers this target's family. */
    FAMILY,

    /** Nothing more specific matched, so the default applies. */
    DEFAULT,

    /** Nothing matched at all. */
    NONE,
}

/** The chosen profile and the reason, which are always produced together. */
public data class ProfileMatch(
    public val profile: ProfileSummary?,
    public val reason: MatchReason,
) {
    public val hasProfile: Boolean get() = profile != null

    public companion object {
        public val NOTHING: ProfileMatch = ProfileMatch(null, MatchReason.NONE)
    }
}

/**
 * Picks the profile for a target.
 *
 * Precedence, highest first:
 *
 * 1. **pinned** to this target by the user
 * 2. **exact target** match
 * 3. **family** match
 * 4. **default**
 *
 * Ties within a level are broken by identifier, alphabetically. That rule is arbitrary and is
 * chosen precisely because it is: it makes the outcome **stable**. Breaking ties by "most recently
 * edited" would mean opening the editor changes which layout appears next time, and a launcher that
 * behaves differently depending on invisible history is one nobody can trust or debug.
 *
 * Disabled profiles are skipped rather than chosen and then ignored, so a disabled profile never
 * shadows a working one.
 */
public fun matchProfile(
    target: TargetDescriptor,
    profiles: List<ProfileSummary>,
): ProfileMatch {
    val usable = profiles.filter { it.enabled }

    usable.filter { it.pinnedToTarget == target.targetId }
        .minByOrNull { it.id.value }
        ?.let { return ProfileMatch(it, MatchReason.PINNED) }

    usable.filter { it.scope is ProfileScope.Target && it.scope.targetId == target.targetId }
        .minByOrNull { it.id.value }
        ?.let { return ProfileMatch(it, MatchReason.EXACT_TARGET) }

    if (target.family != null) {
        usable.filter { it.scope is ProfileScope.Family && it.scope.family == target.family }
            .minByOrNull { it.id.value }
            ?.let { return ProfileMatch(it, MatchReason.FAMILY) }
    }

    usable.filter { it.scope is ProfileScope.Default }
        .minByOrNull { it.id.value }
        ?.let { return ProfileMatch(it, MatchReason.DEFAULT) }

    return ProfileMatch.NOTHING
}

/**
 * Every profile that could apply, best first.
 *
 * The launcher needs this as well as the winner: showing a user which profile will be used is
 * worth little if they cannot see what else was available and switch to it. Ordered by the same
 * rules as [matchProfile], so the head of this list is always what that function returns.
 */
public fun candidateProfiles(
    target: TargetDescriptor,
    profiles: List<ProfileSummary>,
): List<ProfileSummary> = profiles
    .filter { it.enabled && appliesTo(it, target) }
    .sortedWith(
        compareByDescending<ProfileSummary> { if (it.pinnedToTarget == target.targetId) 1 else 0 }
            .thenByDescending { it.scope.specificity }
            .thenBy { it.id.value }
    )

private fun appliesTo(profile: ProfileSummary, target: TargetDescriptor): Boolean =
    profile.pinnedToTarget == target.targetId ||
        when (val scope = profile.scope) {
            is ProfileScope.Target -> scope.targetId == target.targetId
            is ProfileScope.Family -> target.family != null && scope.family == target.family
            ProfileScope.Default -> true
        }
