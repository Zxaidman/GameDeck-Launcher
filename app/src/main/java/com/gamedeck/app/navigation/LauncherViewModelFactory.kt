package com.gamedeck.app.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.gamedeck.core.profile.GameApplicationRepository
import com.gamedeck.feature.launcher.LauncherViewModel

/**
 * Factory for creating LauncherViewModel with dependencies.
 */
class LauncherViewModelFactory(
    private val gameApplicationRepository: GameApplicationRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LauncherViewModel::class.java)) {
            return LauncherViewModel(gameApplicationRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}