package io.github.zxaidman.kestrel.core.layout

import io.github.zxaidman.kestrel.core.common.Outcome
import io.github.zxaidman.kestrel.core.common.flatMap
import io.github.zxaidman.kestrel.core.configuration.ConfigurationError
import io.github.zxaidman.kestrel.core.configuration.Json

/**
 * The layouts Kestrel ships with, as documents rather than as code.
 *
 * They are **files, parsed by the same reader that parses an imported one**, and that is the point
 * rather than an implementation detail. A built-in defined in Kotlin would be the one layout in the
 * product that never goes through validation — so the schema could drift from what the application
 * actually renders, and the drift would surface first for a user importing a file, not for us. This
 * way the built-in is a continuous test of the reader, and the reader is a continuous test of the
 * built-in.
 *
 * **Immutable, per `ADR-001` and `docs/CONFIGURATION_SCHEMA.md`.** Editing one is not disallowed by
 * hiding a button; it is impossible, because the source is a read-only resource. The workflow is
 * Built-in → Duplicate → User copy → Edit, and `requireEditable` in `core/configuration` is what
 * enforces the middle step for anything that reaches a repository.
 */
public object BuiltInLayouts {

    /** The arrangement the reference device was tested with, in Xbox terms. */
    public const val XBOX_DEFAULT: String = "builtin.xbox.default"

    public fun ids(): List<String> = listOf(XBOX_DEFAULT)

    /**
     * Loads and validates one built-in.
     *
     * Returns an [Outcome] rather than throwing, even though a missing or invalid built-in is a
     * packaging fault rather than a user's. The caller is the same code that loads a user's layout,
     * and giving it two ways to fail — an exception here, a typed error there — would mean every
     * call site handling both.
     */
    public fun load(id: String): Outcome<ControllerLayout> {
        val text = read("/layouts/$id.json")
            ?: return Outcome.Failure(
                ConfigurationError.UnresolvedReference("builtInLayouts", id)
            )
        return Json.parse(text).flatMap { ControllerLayoutReader.read(it) }
    }

    private fun read(path: String): String? =
        BuiltInLayouts::class.java.getResourceAsStream(path)?.use { stream ->
            stream.readBytes().decodeToString()
        }
}
