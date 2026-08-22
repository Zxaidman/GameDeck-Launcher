package io.github.zxaidman.kestrel.diagnostics

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.zxaidman.kestrel.platform.overlay.ControllerOverlay
import io.github.zxaidman.kestrel.platform.shizuku.ShizukuCapability
import io.github.zxaidman.kestrel.platform.storage.KestrelStorage
import io.github.zxaidman.kestrel.ui.theme.KButton
import io.github.zxaidman.kestrel.ui.theme.KOutlinedButton

/**
 * Whether the setup card has been dismissed **for this run of the application**.
 *
 * Deliberately not persisted, and that is the whole design of it. Someone who skips setup has said
 * "not now", which is a different thing from "never" — and the state it describes is not a
 * preference but a fact about the phone that a user can undo at any time from outside Kestrel. A
 * permission revoked in system settings, or data cleared, has to be visible again next time the
 * application opens, and remembering the dismissal would be the one way to guarantee it is not.
 *
 * So: skipping lasts until the process ends, which is exactly as long as the reason for skipping
 * plausibly lasts.
 */
public object Setup {
    public var skipped: Boolean by mutableStateOf(false)
}

/** One thing that has to be true before Kestrel can do its job, and how to make it true. */
private class Step(
    val title: String,
    val why: String,
    val done: Boolean,
    val required: Boolean,
    val action: String,
    val perform: () -> Unit,
)

/**
 * The page that stands in front of Kestrel until it has what it needs.
 *
 * It exists because there is no single moment when setup happens. A fresh install has nothing; a
 * user who clears data is back to nothing with the application already installed; a permission
 * revoked from system settings takes one thing away and leaves the rest. Asking at first launch
 * handles only the first of those, which is why it is asked **every time the state is incomplete**
 * rather than once.
 *
 * **A page rather than a card**, at the project owner's request, and it earns the whole screen: on
 * a fresh install every one of these is missing and a card would have been a small box above a
 * diagnostics screen that cannot do anything yet. What it must not become is a wall — **Skip for
 * now** is always there, and it hands over the full application rather than a limited one, because
 * a wizard that will not let you past it traps anyone whose phone answers a question differently
 * from how it was expected to.
 */
@Composable
public fun SetupScreen(
    context: Context,
    onNotifications: () -> Unit,
    onOverlay: () -> Unit,
    onFolder: () -> Unit,
) {
    // The state is facts about the phone, and every one of them can change from outside Kestrel
    // while this screen is open — a permission granted in system settings, Shizuku started. So it
    // is re-read rather than remembered.
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            tick += 1
        }
    }
    @Suppress("UNUSED_EXPRESSION") tick

    val shizuku = ShizukuCapability.state()
    val steps = listOf(
        Step(
            title = "Notifications",
            why = "The notification is the only way to stop a controller from outside Kestrel. " +
                "Without it, a session can be ended only from this screen.",
            done = notificationsAllowed(context),
            required = true,
            action = "Allow",
            perform = onNotifications,
        ),
        Step(
            title = "Draw over other apps",
            why = "The controls are drawn on top of what you are playing, in a window that never " +
                "takes focus. Without this there are no on-screen controls at all.",
            done = ControllerOverlay.permitted(context),
            required = true,
            action = "Allow",
            perform = onOverlay,
        ),
        Step(
            title = "Data folder",
            why = "Choose a folder and Kestrel keeps its settings, layouts and skins where you can " +
                "reach them — and where they survive uninstalling Kestrel. Without it they live in " +
                "Kestrel's own directory and are deleted with it.",
            done = KestrelStorage.usingChosenFolder(context),
            required = true,
            action = "Choose",
            perform = onFolder,
        ),
        Step(
            title = "Shizuku",
            why = "A controller needs a privileged shell, and Shizuku is how Kestrel gets one. " +
                "Everything else — layouts, skins, settings — works without it; playing does not.",
            done = shizuku.usable,
            required = false,
            action = if (shizuku.serviceRunning) "Grant" else "Open",
            perform = {
                if (shizuku.serviceRunning) {
                    ShizukuCapability.requestPermission()
                } else {
                    openShizuku(context)
                }
            },
        ),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Set up Kestrel", style = MaterialTheme.typography.headlineSmall)
        Text(text = summary(steps), style = MaterialTheme.typography.bodyMedium)

        steps.forEach { step ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = (if (step.done) "✓  " else "•  ") + step.title +
                                if (step.required) "" else "  (optional)",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = step.why,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    if (!step.done) {
                        KButton(onClick = step.perform) { Text(step.action) }
                    } else {
                        Text("Done", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        // Skipping is a real answer and is treated as one. It hands over the whole application, and
        // it comes back next time Kestrel opens, because what it is hiding is still true.
        TextButton(
            onClick = { Setup.skipped = true },
            modifier = Modifier.padding(top = 4.dp),
        ) { Text(if (steps.any { !it.done }) "Skip for now" else "Continue") }
    }
}

/**
 * Whether the setup page should stand in front of everything.
 *
 * Asked by the caller rather than decided here, so that the page is a page: something shown
 * *instead of* the application rather than something that draws nothing when it has nothing to say.
 */
public fun setupOutstanding(context: Context): Boolean {
    if (Setup.skipped) return false
    return !notificationsAllowed(context) ||
        !ControllerOverlay.permitted(context) ||
        !KestrelStorage.usingChosenFolder(context) ||
        !ShizukuCapability.state().usable
}

private fun summary(steps: List<Step>): String {
    val missingRequired = steps.count { !it.done && it.required }
    val missingOptional = steps.count { !it.done && !it.required }
    return when {
        missingRequired == 0 && missingOptional > 0 ->
            "Everything needed is in place. What is left is optional."
        missingRequired == 1 ->
            "One thing is missing. Kestrel works without it, less well."
        else ->
            "$missingRequired things are missing. This card comes back each time you open Kestrel " +
                "until they are done, because a permission can be taken away from outside."
    }
}

private fun notificationsAllowed(context: Context): Boolean =
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
        // Before the runtime permission existed, notifications were allowed by installing.
        true
    } else {
        context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

/**
 * Opens Shizuku, or the place to get it.
 *
 * Kestrel cannot start Shizuku and does not pretend to: it is a separate application whose lifetime
 * is not ours to manage (`docs/DEGRADED_STATE.md`). What it can do is take the user to it.
 */
private fun openShizuku(context: Context) {
    val launch = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
    val intent = launch?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        ?: android.content.Intent(
            android.content.Intent.ACTION_VIEW,
            android.net.Uri.parse("https://shizuku.rikka.app/"),
        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
