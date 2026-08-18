package com.gamedeck.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gamedeck.app.GameDeckApplication
import com.gamedeck.core.diagnostics.BackendDiagnostics
import com.gamedeck.core.diagnostics.CompleteDiagnostics
import com.gamedeck.core.diagnostics.DeviceDiagnostics
import com.gamedeck.core.diagnostics.SessionDiagnostics
import com.gamedeck.core.diagnostics.ShizukuDiagnostics
import com.gamedeck.core.model.GameApplication
import com.gamedeck.feature.gamingsession.GamingSessionScreen
import com.gamedeck.feature.launcher.LauncherScreen
import com.gamedeck.feature.launcher.LauncherViewModel
import com.gamedeck.feature.settings.DiagnosticsScreen
import com.gamedeck.feature.settings.SettingsScreen
import com.gamedeck.platform.launcher.AndroidGameLauncher
import com.gamedeck.platform.shizuku.ShizukuCapabilityService

/**
 * Navigation routes for GameDeck.
 */
object Routes {
    const val LAUNCHER = "launcher"
    const val GAMING_SESSION = "gaming_session/{packageName}"
    const val SETTINGS = "settings"
    const val DIAGNOSTICS = "diagnostics"

    fun gamingSession(packageName: String) = "gaming_session/$packageName"
}

/**
 * Root navigation host for GameDeck.
 */
@Composable
fun GameDeckNavHost() {
    val navController = rememberNavController()
    val application = GameDeckApplication.instance

    var selectedApplication by remember { mutableStateOf<GameApplication?>(null) }

    NavHost(
        navController = navController,
        startDestination = Routes.LAUNCHER
    ) {
        composable(Routes.LAUNCHER) {
            val viewModel: LauncherViewModel = viewModel(
                factory = LauncherViewModelFactory(application.gameApplicationRepository)
            )
            LauncherScreen(
                viewModel = viewModel,
                onLaunchGame = { app ->
                    selectedApplication = app
                    navController.navigate(Routes.gamingSession(app.packageName))
                },
                onOpenSettings = {
                    navController.navigate(Routes.SETTINGS)
                }
            )
        }

        composable(
            route = Routes.GAMING_SESSION,
            arguments = listOf(navArgument("packageName") { type = NavType.StringType })
        ) { backStackEntry ->
            val packageName = backStackEntry.arguments?.getString("packageName") ?: ""
            val app = selectedApplication ?: GameApplication(
                packageName = packageName,
                displayName = packageName
            )

            GamingSessionScreen(
                application = app,
                profile = null,
                onExit = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenDiagnostics = {
                    navController.navigate(Routes.DIAGNOSTICS)
                }
            )
        }

        composable(Routes.DIAGNOSTICS) {
            val shizukuService = ShizukuCapabilityService(application)
            val diagnostics = remember {
                CompleteDiagnostics(
                    device = DeviceDiagnostics(
                        androidVersion = android.os.Build.VERSION.RELEASE,
                        androidApi = android.os.Build.VERSION.SDK_INT,
                        deviceModel = android.os.Build.MODEL,
                        manufacturer = android.os.Build.MANUFACTURER,
                        gameDeckVersion = "0.1.0"
                    ),
                    shizuku = ShizukuDiagnostics(
                        state = shizukuService.getShizukuState(),
                        privilegeLevel = shizukuService.getPrivilegeLevel().name,
                        permissionGranted = shizukuService.isPermissionGranted(),
                        userServiceStarted = false
                    ),
                    session = SessionDiagnostics(),
                    backends = listOf(
                        BackendDiagnostics(
                            backendId = "touch-fallback",
                            available = true,
                            capabilities = setOf(com.gamedeck.core.input.InputCapability.TOUCH_FALLBACK)
                        )
                    )
                )
            }

            DiagnosticsScreen(
                diagnostics = diagnostics,
                onBack = { navController.popBackStack() }
            )
        }
    }
}