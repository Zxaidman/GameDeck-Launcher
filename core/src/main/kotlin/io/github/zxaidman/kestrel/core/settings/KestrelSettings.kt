package io.github.zxaidman.kestrel.core.settings

import io.github.zxaidman.kestrel.core.common.Outcome
import io.github.zxaidman.kestrel.core.common.flatMap
import io.github.zxaidman.kestrel.core.configuration.ConfigNode
import io.github.zxaidman.kestrel.core.configuration.ConfigReader
import io.github.zxaidman.kestrel.core.configuration.DocumentHeader
import io.github.zxaidman.kestrel.core.configuration.DocumentType
import io.github.zxaidman.kestrel.core.configuration.Json
import io.github.zxaidman.kestrel.core.input.AnalogProfile
import io.github.zxaidman.kestrel.core.input.DeadzoneShape
import io.github.zxaidman.kestrel.core.storage.DocumentStore
import io.github.zxaidman.kestrel.core.storage.StoreFolder

/**
 * Everything Kestrel remembers between one run and the next.
 *
 * **A document, not a private key-value store**, and that is the whole point of it. It lives in the
 * folder the user chose, beside their layouts, where they can read it, edit it, copy it to another
 * phone, or keep it when they uninstall. Settings that can only be changed from inside the
 * application are settings that disappear with the application — which is exactly the problem this
 * exists to end.
 *
 * Every field has a default, and reading is **lenient about absence and strict about content**. A
 * settings file with a field missing is a file written by an older build, and refusing to start
 * because of one would make an upgrade a data loss. A field that is present and wrong is a
 * different thing and is reported.
 */
public data class KestrelSettings(
    /** How large the on-screen controls are drawn, as a fraction of the layout's own sizes. */
    public val controlScale: Double = DEFAULT_CONTROL_SCALE,

    /** The shaping applied to both sticks. */
    public val stickProfile: AnalogProfile = AnalogProfile.DEFAULT_STICK,

    /** Which layout the overlay draws. */
    public val layoutId: String = DEFAULT_LAYOUT_ID,

    /** Carried through so a newer build's settings survive being read by an older one. */
    public val unknownFields: Map<String, ConfigNode> = emptyMap(),
) {
    public companion object {
        public const val DOCUMENT_ID: String = "user.settings"
        public const val DOCUMENT_NAME: String = "settings.json"

        public const val DEFAULT_LAYOUT_ID: String = "builtin.xbox.default"

        /** Settled by a hand on the reference device rather than by arithmetic. */
        public const val DEFAULT_CONTROL_SCALE: Double = 0.65
        public const val MIN_CONTROL_SCALE: Double = 0.35
        public const val MAX_CONTROL_SCALE: Double = 1.30
    }
}

/** Reads and writes [KestrelSettings] as a document in a [DocumentStore]. */
public object SettingsDocument {

    private val KNOWN_FIELDS = setOf(
        "schemaVersion", "type", "id", "name",
        "controlScale", "layoutId", "stick",
    )
    private val KNOWN_STICK_FIELDS = setOf(
        "deadzone", "outerLimit", "curve", "sensitivity", "invertX", "invertY", "deadzoneShape",
    )

    /**
     * Loads settings, or returns the defaults when there is nothing to load.
     *
     * A first run has no settings file and that is not an error — it is a first run. Only a file
     * that exists and cannot be understood is worth reporting, because that one means something the
     * user may want to fix rather than something Kestrel should quietly overwrite.
     */
    public fun load(store: DocumentStore): Outcome<KestrelSettings> {
        if (!store.exists(StoreFolder.ROOT, KestrelSettings.DOCUMENT_NAME)) {
            return Outcome.Success(KestrelSettings())
        }
        return store.read(StoreFolder.ROOT, KestrelSettings.DOCUMENT_NAME)
            .flatMap { Json.parse(it) }
            .flatMap { read(it) }
    }

    public fun save(store: DocumentStore, settings: KestrelSettings): Outcome<Unit> =
        store.write(
            StoreFolder.ROOT,
            KestrelSettings.DOCUMENT_NAME,
            Json.write(write(settings)),
        )

    public fun read(node: ConfigNode): Outcome<KestrelSettings> {
        val header = when (val h = DocumentHeader.read(node, DocumentType.SETTINGS)) {
            is Outcome.Failure -> return h
            is Outcome.Success -> h.value
        }
        // The header is validated and then not kept: settings have exactly one identity, so storing
        // the one read from the file would let a hand-edited id become the one Kestrel uses.
        check(header.type == DocumentType.SETTINGS)

        val obj = when (val o = ConfigReader.asObject(node)) {
            is Outcome.Failure -> return o
            is Outcome.Success -> o.value
        }

        val defaults = KestrelSettings()

        val scale = when (
            val v = optionalNumber(
                obj, "controlScale",
                KestrelSettings.MIN_CONTROL_SCALE, KestrelSettings.MAX_CONTROL_SCALE,
                defaults.controlScale,
            )
        ) {
            is Outcome.Failure -> return v
            is Outcome.Success -> v.value
        }

        val layoutId = when (val v = ConfigReader.optionalText(obj, "layoutId")) {
            is Outcome.Failure -> return v
            is Outcome.Success -> v.value ?: defaults.layoutId
        }

        val stick = when (val v = readStick(obj, defaults.stickProfile)) {
            is Outcome.Failure -> return v
            is Outcome.Success -> v.value
        }

        return Outcome.Success(
            KestrelSettings(
                controlScale = scale,
                stickProfile = stick,
                layoutId = layoutId,
                unknownFields = obj.unknownFields(KNOWN_FIELDS),
            )
        )
    }

    public fun write(settings: KestrelSettings): ConfigNode {
        val stick = settings.stickProfile
        val fields = linkedMapOf<String, ConfigNode>(
            "schemaVersion" to ConfigNode.Num(DocumentHeader.CURRENT_SCHEMA_VERSION.toDouble()),
            "type" to ConfigNode.Text(DocumentType.SETTINGS.wireName),
            "id" to ConfigNode.Text(KestrelSettings.DOCUMENT_ID),
            "name" to ConfigNode.Text("Kestrel settings"),
            "controlScale" to ConfigNode.Num(settings.controlScale),
            "layoutId" to ConfigNode.Text(settings.layoutId),
            "stick" to ConfigNode.Obj(
                linkedMapOf(
                    "deadzone" to ConfigNode.Num(stick.deadzone),
                    "outerLimit" to ConfigNode.Num(stick.outerLimit),
                    "curve" to ConfigNode.Num(stick.curve),
                    "sensitivity" to ConfigNode.Num(stick.sensitivity),
                    "invertX" to ConfigNode.Bool(stick.invertX),
                    "invertY" to ConfigNode.Bool(stick.invertY),
                    "deadzoneShape" to ConfigNode.Text(stick.deadzoneShape.name.lowercase()),
                )
            ),
        )
        // Anything a newer build wrote is put back, so an older build reading and rewriting this
        // file does not silently delete what it did not understand.
        settings.unknownFields.forEach { (key, value) -> fields.putIfAbsent(key, value) }
        return ConfigNode.Obj(fields)
    }

    private fun readStick(obj: ConfigNode.Obj, defaults: AnalogProfile): Outcome<AnalogProfile> {
        val stick = when (val node = obj["stick"]) {
            null, ConfigNode.Null -> return Outcome.Success(defaults)
            is ConfigNode.Obj -> node
            // Present and not an object: a real mistake in the file, reported rather than ignored.
            else -> return when (val o = ConfigReader.asObject(node, "stick")) {
                is Outcome.Failure -> o
                is Outcome.Success -> Outcome.Success(defaults)
            }
        }

        fun number(field: String, min: Double, max: Double, fallback: Double): Outcome<Double> =
            optionalNumber(stick, field, min, max, fallback, "stick")

        val deadzone = when (val v = number("deadzone", 0.0, 0.9, defaults.deadzone)) {
            is Outcome.Failure -> return v
            is Outcome.Success -> v.value
        }
        val outerLimit = when (val v = number("outerLimit", 0.1, 1.0, defaults.outerLimit)) {
            is Outcome.Failure -> return v
            is Outcome.Success -> v.value
        }
        val curve = when (val v = number("curve", 0.2, 5.0, defaults.curve)) {
            is Outcome.Failure -> return v
            is Outcome.Success -> v.value
        }
        val sensitivity = when (val v = number("sensitivity", 0.1, 3.0, defaults.sensitivity)) {
            is Outcome.Failure -> return v
            is Outcome.Success -> v.value
        }
        val invertX = when (val v = ConfigReader.boolean(stick, "invertX", defaults.invertX, "stick")) {
            is Outcome.Failure -> return v
            is Outcome.Success -> v.value
        }
        val invertY = when (val v = ConfigReader.boolean(stick, "invertY", defaults.invertY, "stick")) {
            is Outcome.Failure -> return v
            is Outcome.Success -> v.value
        }
        val shape = if (!stick.has("deadzoneShape")) {
            defaults.deadzoneShape
        } else {
            when (
                val v = ConfigReader.enum(
                    stick, "deadzoneShape", DeadzoneShape.entries.toTypedArray(),
                    { it.name.lowercase() }, "stick",
                )
            ) {
                is Outcome.Failure -> return v
                is Outcome.Success -> v.value
            }
        }

        // Unknown fields inside the stick are dropped rather than carried. They are numbers with no
        // home in AnalogProfile, and pretending to preserve them would mean claiming a fidelity
        // this shape cannot offer.
        check(KNOWN_STICK_FIELDS.isNotEmpty())

        return Outcome.Success(
            AnalogProfile(deadzone, outerLimit, curve, sensitivity, invertX, invertY, shape)
        )
    }

    /** Absent means the default; present means it has to be right. */
    private fun optionalNumber(
        obj: ConfigNode.Obj,
        field: String,
        min: Double,
        max: Double,
        fallback: Double,
        path: String = "",
    ): Outcome<Double> =
        if (!obj.has(field)) Outcome.Success(fallback) else ConfigReader.number(obj, field, min, max, path)
}
