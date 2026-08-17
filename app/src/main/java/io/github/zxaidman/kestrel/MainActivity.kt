package io.github.zxaidman.kestrel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Entry point.
 *
 * This is the assembly layer only. It deliberately contains no launcher, session, input, or
 * configuration logic — that belongs in feature/, platform/, and core/ modules which do not exist
 * yet. See PROJECT_STRUCTURE.md §4 and §23.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ProjectStatusScreen()
                }
            }
        }
    }
}

@Composable
private fun ProjectStatusScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResourceAppName(),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = "Build foundation only. Phase 0 input feasibility is not complete, " +
                "so no input backend, overlay, or session behaviour exists yet.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun stringResourceAppName(): String =
    androidx.compose.ui.res.stringResource(id = R.string.app_name)
