package io.github.zxaidman.kestrel.platform.settings

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import io.github.zxaidman.kestrel.core.common.Outcome
import io.github.zxaidman.kestrel.core.settings.KestrelSettings
import io.github.zxaidman.kestrel.core.settings.SettingsDocument
import io.github.zxaidman.kestrel.platform.session.SessionState
import io.github.zxaidman.kestrel.platform.storage.KestrelStorage

/**
 * The settings the application is running with, and the only place that reads or writes them.
 *
 * Settings had never been kept at all: control size and stick shaping were adjusted on screen,
 * used, and lost the moment the process ended. That is the reason a test run began with setting
 * everything up again, and the reason an uninstall was a real cost rather than an inconvenience.
 *
 * Two behaviours here are decisions rather than plumbing.
 *
 * **A read failure does not lose the file.** If `settings.json` exists and cannot be understood,
 * Kestrel runs on defaults and says so — and does **not** write over it. A file that failed to
 * parse is a file the user may be able to fix, and overwriting it with defaults would destroy the
 * only copy of what they had while looking like recovery.
 *
 * **Writing is deliberate, not continuous.** A slider produces a value on every frame it moves; a
 * document written sixty times a second would be sixty writes to the user's storage for one
 * decision. State changes as it is dragged, and is persisted when the drag ends.
 */
public object AppSettings {

    /** What the application is using now. Compose state, so a change shows immediately. */
    public val current: MutableState<KestrelSettings> = mutableStateOf(KestrelSettings())

    /** What the last load or save did, for a screen that must never fail silently. */
    public val message: MutableState<String> = mutableStateOf("")

    /**
     * True when the file on disk could not be read.
     *
     * Held separately from [message] because it changes what saving is allowed to do: while this is
     * set, saving would overwrite a file the user might still want.
     */
    public val unreadable: MutableState<Boolean> = mutableStateOf(false)

    @Volatile
    private var loaded = false

    /** Reads settings once per process, and applies them to everything that already existed. */
    public fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            loaded = true
            reload(context)
        }
    }

    public fun reload(context: Context) {
        val store = KestrelStorage.current(context)
        when (val outcome = SettingsDocument.load(store)) {
            is Outcome.Success -> {
                current.value = outcome.value
                unreadable.value = false
                message.value = "Settings loaded from ${store.description}."
                apply(outcome.value)
            }

            is Outcome.Failure -> {
                unreadable.value = true
                message.value =
                    "settings.json could not be read, so Kestrel is running on defaults and has " +
                        "left the file alone: ${outcome.error.message}"
            }
        }
    }

    /** Changes settings in memory. Nothing is written until [persist]. */
    public fun update(transform: (KestrelSettings) -> KestrelSettings) {
        val updated = transform(current.value)
        current.value = updated
        apply(updated)
    }

    /** Writes the current settings, unless doing so would overwrite a file that failed to read. */
    public fun persist(context: Context) {
        if (unreadable.value) {
            message.value =
                "Not saved: settings.json could not be read, and writing would replace it. " +
                    "Fix or remove the file, then reload."
            return
        }
        val store = KestrelStorage.current(context)
        message.value = when (val outcome = SettingsDocument.save(store, current.value)) {
            is Outcome.Success -> "Saved to ${store.description}."
            is Outcome.Failure -> "Not saved: ${outcome.error.message}"
        }
    }

    /** Pushes settings into the parts of the application that hold their own copy. */
    private fun apply(settings: KestrelSettings) {
        SessionState.profile = settings.stickProfile
        SessionState.controlScale.value = settings.controlScale.toFloat()
        SessionState.overlay?.update(settings.stickProfile)
        SessionState.overlay?.resize(settings.controlScale.toFloat())
    }
}
