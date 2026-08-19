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
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.content.Intent
import androidx.core.content.FileProvider
import io.github.zxaidman.kestrel.diagnostics.DiagnosticReport
import io.github.zxaidman.kestrel.diagnostics.ExportState
import io.github.zxaidman.kestrel.diagnostics.InputPreviewScreen
import io.github.zxaidman.kestrel.diagnostics.InputPreviewState
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

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

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

    // Dispatch, not onGenericMotionEvent, so events are seen even where a focused view would
    // consume them first — focus navigation eats directional input before it reaches a handler.
    // Both call through afterwards: this screen observes and never swallows.
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        preview.record(event)
        return super.dispatchGenericMotionEvent(event)
    }

    // androidx marks its own override as library-group restricted. Calling through to it is
    // exactly what an observer must do, and not calling through would swallow the back gesture.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        preview.record(event)
        return super.dispatchKeyEvent(event)
    }
}
