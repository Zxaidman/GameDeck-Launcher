package io.github.zxaidman.gamedeck.core.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OutcomeTest {

    private data class TestError(override val message: String) : DomainError

    private val failure = Outcome.Failure(TestError("invalid schemaVersion"))

    @Test
    fun `success reports success and carries its value`() {
        val outcome: Outcome<Int> = Outcome.Success(29)

        assertTrue(outcome.isSuccess)
        assertFalse(outcome.isFailure)
        assertEquals(29, outcome.valueOrNull())
        assertNull(outcome.errorOrNull())
    }

    @Test
    fun `failure reports failure and carries its error`() {
        assertTrue(failure.isFailure)
        assertFalse(failure.isSuccess)
        assertNull(failure.valueOrNull())
        assertEquals("invalid schemaVersion", failure.errorOrNull()?.message)
    }

    @Test
    fun `map transforms a success`() {
        val mapped = Outcome.Success(2).map { it * 3 }

        assertEquals(6, mapped.valueOrNull())
    }

    @Test
    fun `map leaves a failure untouched and does not run the transform`() {
        var transformRan = false

        val mapped: Outcome<Int> = failure.map { transformRan = true; 1 }

        assertFalse(transformRan)
        assertEquals("invalid schemaVersion", mapped.errorOrNull()?.message)
    }

    @Test
    fun `flatMap chains a second fallible step`() {
        val chained = Outcome.Success(4).flatMap { Outcome.Success(it + 1) }

        assertEquals(5, chained.valueOrNull())
    }

    @Test
    fun `flatMap short-circuits on the first failure`() {
        var secondStepRan = false

        val chained: Outcome<Int> = failure.flatMap {
            secondStepRan = true
            Outcome.Success(1)
        }

        assertFalse(secondStepRan)
        assertTrue(chained.isFailure)
    }

    @Test
    fun `flatMap propagates a failure raised by the second step`() {
        val chained = Outcome.Success(1).flatMap { failure }

        assertEquals("invalid schemaVersion", chained.errorOrNull()?.message)
    }

    @Test
    fun `valueOr returns the fallback only on failure`() {
        assertEquals(7, Outcome.Success(7).valueOr(0))
        assertEquals(0, failure.valueOr(0))
    }

    @Test
    fun `fold collapses both branches`() {
        assertEquals("ok:1", Outcome.Success(1).fold({ "ok:$it" }, { "err:${it.message}" }))
        assertEquals("err:invalid schemaVersion", failure.fold({ "ok:$it" }, { "err:${it.message}" }))
    }
}
