package io.github.zxaidman.kestrel.core.layout

import io.github.zxaidman.kestrel.core.common.Outcome
import io.github.zxaidman.kestrel.core.common.flatMap
import io.github.zxaidman.kestrel.core.configuration.ConfigurationError
import io.github.zxaidman.kestrel.core.configuration.ConfigurationId
import io.github.zxaidman.kestrel.core.configuration.Json
import io.github.zxaidman.kestrel.core.storage.DocumentStore
import io.github.zxaidman.kestrel.core.storage.StoreFolder

/**
 * Where a layout comes from, whoever wrote it.
 *
 * Two sources with one interface, because the difference between them is ownership rather than
 * format: a built-in is a file Kestrel ships and a user layout is a file in the user's folder, and
 * both are read by the same reader and validated by the same rules. A caller asks for an id and
 * gets a layout or a typed reason.
 *
 * **Built-ins are immutable, and this is where that is enforced** rather than by a disabled button
 * somewhere in the interface. [save] refuses a built-in id outright; [duplicate] is the only way to
 * get an editable copy of one. That makes the workflow `docs/CONFIGURATION_SCHEMA.md` describes —
 * Built-in → Duplicate → User copy → Edit — the only path there is, instead of the path people are
 * asked to follow.
 */
public class LayoutRepository(private val store: DocumentStore) {

    /** Loads any layout by id, from wherever that id lives. */
    public fun load(id: String): Outcome<ControllerLayout> {
        val parsed = when (val p = ConfigurationId.parse(id, "layoutId")) {
            is Outcome.Failure -> return p
            is Outcome.Success -> p.value
        }
        return if (parsed.isBuiltIn) {
            BuiltInLayouts.load(id)
        } else {
            store.read(StoreFolder.LAYOUTS, fileName(id))
                .flatMap { Json.parse(it) }
                .flatMap { ControllerLayoutReader.read(it) }
        }
    }

    /**
     * Loads a layout, falling back to the default when it cannot be had.
     *
     * A layout that has been deleted, or a folder that has been forgotten, must not leave the user
     * with no controls at all — that is a worse answer than the wrong pad. The reason is reported
     * alongside so the interface can say what happened rather than pretending nothing did.
     */
    public fun loadOrDefault(id: String): Loaded = when (val result = load(id)) {
        is Outcome.Success -> Loaded(result.value, null)
        is Outcome.Failure -> when (val fallback = BuiltInLayouts.load(BuiltInLayouts.XBOX_DEFAULT)) {
            is Outcome.Success -> Loaded(fallback.value, result.error.message)
            // Nothing left to fall back to: a built-in that will not load is a packaging fault.
            is Outcome.Failure -> Loaded(null, fallback.error.message)
        }
    }

    public data class Loaded(
        public val layout: ControllerLayout?,
        /** Null when the requested layout loaded. Otherwise why it did not. */
        public val problem: String?,
    )

    /** Every layout available to choose from: what Kestrel ships, then what the user has made. */
    public fun list(): List<String> = BuiltInLayouts.ids() + userIds()

    public fun userIds(): List<String> = when (val listed = store.list(StoreFolder.LAYOUTS)) {
        // A folder that cannot be listed reads as empty rather than as an error. The caller is
        // building a list of choices, and "no user layouts yet" is the same offer as "the folder is
        // gone" — both leave the built-ins, which is what the interface shows either way.
        is Outcome.Failure -> emptyList()
        is Outcome.Success -> listed.value
            .filter { it.endsWith(EXTENSION) }
            .map { it.removeSuffix(EXTENSION) }
            .filter { ConfigurationId.parse(it) is Outcome.Success }
            .sorted()
    }

    /**
     * Writes a layout the user owns.
     *
     * Refuses a built-in id, which is the whole of built-in immutability: there is no code path
     * that overwrites a shipped layout, so no interface can accidentally offer one.
     */
    public fun save(layout: ControllerLayout): Outcome<Unit> {
        val id = layout.header.id
        if (id.isBuiltIn) {
            return Outcome.Failure(ConfigurationError.ImmutableDocument(id.value))
        }
        return store.write(
            StoreFolder.LAYOUTS,
            fileName(id.value),
            Json.write(ControllerLayoutWriter.write(layout)),
        )
    }

    /**
     * Copies a layout into a new one the user owns.
     *
     * The copy gets a new identity and keeps everything else, because a duplicate is the same pad
     * under a different name — and because an id that travelled with the copy would make two
     * documents claim to be the same thing, which every reference between documents then resolves
     * arbitrarily.
     */
    public fun duplicate(source: ControllerLayout, unique: String, name: String): Outcome<ControllerLayout> {
        val id = when (val i = ConfigurationId.user(unique)) {
            is Outcome.Failure -> return i
            is Outcome.Success -> i.value
        }
        val copy = source.copy(header = source.header.copy(id = id, name = name))
        return when (val saved = save(copy)) {
            is Outcome.Failure -> saved
            is Outcome.Success -> Outcome.Success(copy)
        }
    }

    public fun delete(id: String): Outcome<Unit> {
        val parsed = when (val p = ConfigurationId.parse(id, "layoutId")) {
            is Outcome.Failure -> return p
            is Outcome.Success -> p.value
        }
        if (parsed.isBuiltIn) return Outcome.Failure(ConfigurationError.ImmutableDocument(id))
        return store.delete(StoreFolder.LAYOUTS, fileName(id))
    }

    private fun fileName(id: String): String = "$id$EXTENSION"

    private companion object {
        const val EXTENSION = ".json"
    }
}
