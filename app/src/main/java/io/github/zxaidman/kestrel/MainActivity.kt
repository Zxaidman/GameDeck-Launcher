package io.github.zxaidman.kestrel

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.content.Intent
import androidx.core.content.FileProvider
import io.github.zxaidman.kestrel.diagnostics.DiagnosticReport
import io.github.zxaidman.kestrel.diagnostics.ExportState
import io.github.zxaidman.kestrel.diagnostics.InputPreviewScreen
import io.github.zxaidman.kestrel.diagnostics.InputPreviewState
import io.github.zxaidman.kestrel.platform.settings.AppSettings
import io.github.zxaidman.kestrel.platform.storage.KestrelStorage
import io.github.zxaidman.kestrel.platform.shizuku.ShizukuCapability
import java.io.File

/**
 * Entry point.
 *
 * This is the assembly layer only. It holds no launcher, session, input or configuration logic —
 * that belongs in `feature/`, `platform/` and `core/` (PROJECT_STRUCTURE.md §4 and §23). What it
 * shows today is a diagnostic screen over `core/`, kept in its own package until `feature/` exists.
 *
 * The events it forwards are events the phone delivers to this window like any other. **Nothing
 * here creates input**: Kestrel has no input backend yet, and this activity only observes.
 */
class MainActivity : ComponentActivity() {

    private val preview = InputPreviewState()

    // The notification is the only always-available way to end a session, so asking for it is
    // asking for the stop control rather than for the ability to interrupt anyone.
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private var pendingExport: String? = null

    /**
     * The folder the user keeps their files in.
     *
     * Here rather than on the screen that shows it, because setup asks for the same thing and two
     * launchers for one choice is two places for it to behave differently.
     */
    private val folderPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val tree = result.data?.data
        AppSettings.message.value = if (tree == null) {
            "No folder was chosen, so nothing changed."
        } else {
            when (val outcome = KestrelStorage.useFolder(this, tree)) {
                is io.github.zxaidman.kestrel.core.common.Outcome.Success -> outcome.value
                is io.github.zxaidman.kestrel.core.common.Outcome.Failure -> outcome.error.message
            }.also { AppSettings.reload(this) }
        }
    }

    private fun chooseFolder() {
        runCatching { folderPicker.launch(KestrelStorage.folderPicker()) }
            .onFailure { AppSettings.message.value = "Could not open the folder picker." }
    }

    private fun askForNotifications() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /** Sends the user to the one screen that can grant drawing over other applications. */
    private fun askForOverlay() {
        runCatching {
            startActivity(
                Intent(
                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:$packageName"),
                )
            )
        }
    }

    /** Lets the user choose where the report lands, rather than hiding it somewhere they cannot reach. */
    private val saveLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val content = pendingExport
        pendingExport = null
        ExportState.message.value = when {
            uri == null -> "Save cancelled."
            content == null -> "Nothing to save."
            else -> try {
                contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
                "Saved. Open your file manager at the folder you chose."
            } catch (e: Exception) {
                "Save failed: ${e.javaClass.simpleName}: ${e.message}"
            }
        }
    }

    private fun saveReport() {
        pendingExport = DiagnosticReport.build(this, preview)
        ExportState.message.value = "Choose a folder…"
        runCatching { saveLauncher.launch("kestrel-${System.currentTimeMillis()}.json") }
            .onFailure { ExportState.message.value = "Could not open the file picker." }
    }

    /** Shares the report as an actual file, not as pasted text that has to be copied back out. */
    private fun shareReport() {
        try {
            val directory = File(cacheDir, "reports").apply { mkdirs() }
            directory.listFiles()?.forEach { it.delete() }
            val file = File(directory, "kestrel-${System.currentTimeMillis()}.json")
            file.writeText(DiagnosticReport.build(this, preview))

            val uri = FileProvider.getUriForFile(this, "$packageName.reports", file)
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_SUBJECT, file.name)
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },
                    "Share report",
                )
            )
            ExportState.message.value = "Sharing ${file.name}"
        } catch (e: Exception) {
            ExportState.message.value = "Share failed: ${e.javaClass.simpleName}"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Before anything reads a setting. Kestrel had never kept one, so every run began by
        // setting the same things up again and an uninstall lost them for good.
        AppSettings.ensureLoaded(this)

        // Nothing is demanded on launch any more. Setup asks, in one place, for everything that is
        // missing — and a permission dialog fired before the user has seen the application is a
        // dialog answered without knowing what it is for.

        // Bind early where possible, so the session controls are usable without a separate step.
        ShizukuCapability.bind(this) { }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = androidx.compose.ui.res.stringResource(id = R.string.app_name),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        // Every one of these is a fact about the phone that can change from
                        // outside Kestrel while this screen is open — a permission granted in
                        // system settings, Shizuku started. So the page leaves of its own accord
                        // rather than waiting for something else to cause a recomposition.
                        var tick by androidx.compose.runtime.remember {
                            androidx.compose.runtime.mutableStateOf(0)
                        }
                        androidx.compose.runtime.LaunchedEffect(Unit) {
                            while (true) {
                                kotlinx.coroutines.delay(1000)
                                tick += 1
                            }
                        }
                        @Suppress("UNUSED_EXPRESSION") tick

                        // Setup is a page, not a banner: on a fresh install everything below it
                        // is unusable anyway, and a screen that cannot do its job is a worse thing
                        // to show than the list of reasons why.
                        if (io.github.zxaidman.kestrel.diagnostics.setupOutstanding(
                                this@MainActivity
                            )
                        ) {
                            io.github.zxaidman.kestrel.diagnostics.SetupScreen(
                                context = this@MainActivity,
                                onNotifications = ::askForNotifications,
                                onOverlay = ::askForOverlay,
                                onFolder = ::chooseFolder,
                            )
                        } else {
                            InputPreviewScreen(
                                state = preview,
                                onSave = ::saveReport,
                                onShare = ::shareReport,
                            )
                        }
                    }
                }
            }
        }
    }

    // Dispatch, not onGenericMotionEvent, so events are seen even where a focused view would
    // consume them first — focus navigation eats directional input before it reaches a handler.
    // Both call through afterwards: this screen observes and never swallows.
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        preview.record(event)
        return super.dispatchGenericMotionEvent(event)
    }

    /**
     * Observes every key, and **consumes the ones a controller sent**.
     *
     * Measured, not guessed: an export showed each controller button arriving twice — `BUTTON_A`
     * with `DPAD_CENTER`, `BUTTON_X` with `DEL`, `BUTTON_Y` with `SPACE`, and `BUTTON_B` with
     * **`BACK`**. Those second events are the platform's fallback keys, generated for a gamepad
     * button that **nothing handled**. This screen was observing without handling, so pressing `B`
     * on Kestrel's own controls asked Kestrel to navigate back.
     *
     * Handling a controller's keys stops the fallbacks being generated at all. Only a controller's:
     * everything else, the back gesture included, still goes where it was going, because swallowing
     * those would take the way out of the screen with it.
     */
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        preview.record(event)
        if (fromController(event)) return true
        return super.dispatchKeyEvent(event)
    }

    private fun fromController(event: KeyEvent): Boolean =
        event.device?.let { device ->
            device.supportsSource(android.view.InputDevice.SOURCE_GAMEPAD) ||
                device.supportsSource(android.view.InputDevice.SOURCE_JOYSTICK)
        } ?: false
}
