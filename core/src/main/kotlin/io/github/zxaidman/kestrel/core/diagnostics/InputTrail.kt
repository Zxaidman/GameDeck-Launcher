package io.github.zxaidman.kestrel.core.diagnostics

/**
 * One thing that happened, at the moment it happened.
 *
 * `kind` says what sort of thing — a key, an axis, a written report — and `detail` says which one
 * and with what value. Deliberately a string rather than a sealed hierarchy: this is a record for a
 * human reading an export, not a value the product makes decisions from, and a shape that never
 * needs migrating is worth more here than one that can be pattern-matched.
 */
public data class InputMark(
    public val atMillis: Long,
    public val kind: String,
    public val detail: String,
)

/**
 * The last few hundred things that happened, so a report can show a sequence rather than a moment.
 *
 * The exports this project has relied on carried only the **most recent** value of each field, which
 * is enough to answer "did anything arrive" and nothing else. It cannot show that a press arrived
 * without its release, that two controls fired when one was touched, or that a value climbed when it
 * should have been steady — and those are the failures that have actually cost time here. A trail
 * turns each of them into something visible in the file.
 *
 * **Bounded on purpose.** A stick held still produces sixty positions a second, so an unbounded log
 * is a memory leak with extra steps and a bounded one that keeps the *oldest* entries would fill
 * with the moments before the interesting one. This keeps the newest and counts what it dropped, so
 * a reader can tell a quiet trail from a truncated one.
 *
 * Callers are expected to coalesce high-frequency streams before adding to it — see [changedEnough].
 */
public class InputTrail(private val capacity: Int = DEFAULT_CAPACITY) {

    private val marks = ArrayDeque<InputMark>()
    private var droppedCount: Long = 0

    /** How many marks were pushed out by newer ones. Zero means the trail is complete. */
    public val dropped: Long
        @Synchronized get() = droppedCount

    @Synchronized
    public fun add(atMillis: Long, kind: String, detail: String) {
        if (marks.size >= capacity) {
            marks.removeFirst()
            droppedCount += 1
        }
        marks.addLast(InputMark(atMillis, kind, detail))
    }

    /** Oldest first, which is the order a sequence is read in. */
    @Synchronized
    public fun snapshot(): List<InputMark> = marks.toList()

    @Synchronized
    public fun clear() {
        marks.clear()
        droppedCount = 0
    }

    public companion object {
        /**
         * Enough to hold a few seconds of coalesced analog movement and every key around it.
         *
         * Not a memory limit — four hundred short records is nothing — but a readability one: a
         * trail longer than this stops being something a person scans and starts being something
         * they need a tool for.
         */
        public const val DEFAULT_CAPACITY: Int = 400
    }
}

/**
 * Whether an analog value has moved enough to be worth recording.
 *
 * Sixty identical positions a second say nothing that the first one did not, and they crowd out the
 * key presses around them. A value is recorded when it has moved past this threshold since the last
 * one recorded — small enough to keep the shape of a movement, large enough that a resting thumb
 * writes nothing at all.
 */
public fun changedEnough(previous: Double, next: Double, threshold: Double = 0.02): Boolean =
    kotlin.math.abs(next - previous) >= threshold
