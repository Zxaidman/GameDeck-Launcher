package io.github.zxaidman.gamedeck.core.common

/**
 * A failure that domain code can describe without throwing.
 *
 * Configuration import, schema validation, and layout parsing are all required to return a typed
 * error rather than crashing (`docs/CONFIGURATION_SCHEMA.md`, "Validation"). Implementations are
 * expected to be exhaustive sealed hierarchies owned by the subsystem that produces them, so that
 * callers can react to a specific failure instead of matching on message text.
 */
public interface DomainError {
    /** Human-readable description. Must not contain credentials, tokens, or personal data. */
    public val message: String
}

/**
 * The result of an operation that can fail in a way the caller is expected to handle.
 *
 * Used instead of exceptions for expected failures — invalid imported configuration, a missing
 * layout, an unavailable capability. Genuine programming errors should still throw.
 */
public sealed interface Outcome<out T> {

    public data class Success<out T>(public val value: T) : Outcome<T>

    public data class Failure(public val error: DomainError) : Outcome<Nothing>

    public val isSuccess: Boolean
        get() = this is Success

    public val isFailure: Boolean
        get() = this is Failure
}

/** The value on success, or `null` on failure. */
public fun <T> Outcome<T>.valueOrNull(): T? = when (this) {
    is Outcome.Success -> value
    is Outcome.Failure -> null
}

/** The error on failure, or `null` on success. */
public fun <T> Outcome<T>.errorOrNull(): DomainError? = when (this) {
    is Outcome.Success -> null
    is Outcome.Failure -> error
}

/** The value on success, or [fallback] on failure. */
public fun <T> Outcome<T>.valueOr(fallback: T): T = when (this) {
    is Outcome.Success -> value
    is Outcome.Failure -> fallback
}

/** Transforms a successful value, leaving a failure untouched. */
public inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
}

/** Chains another fallible operation, short-circuiting on the first failure. */
public inline fun <T, R> Outcome<T>.flatMap(transform: (T) -> Outcome<R>): Outcome<R> =
    when (this) {
        is Outcome.Success -> transform(value)
        is Outcome.Failure -> this
    }

/** Collapses both branches into a single value. */
public inline fun <T, R> Outcome<T>.fold(
    onSuccess: (T) -> R,
    onFailure: (DomainError) -> R,
): R = when (this) {
    is Outcome.Success -> onSuccess(value)
    is Outcome.Failure -> onFailure(error)
}
