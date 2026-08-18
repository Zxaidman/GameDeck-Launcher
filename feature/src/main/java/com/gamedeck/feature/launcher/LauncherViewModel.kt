package com.gamedeck.feature.launcher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamedeck.core.model.GameApplication
import com.gamedeck.core.profile.GameApplicationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the gaming launcher screen.
 */
class LauncherViewModel(
    private val gameApplicationRepository: GameApplicationRepository
) : ViewModel() {

    private val _applications = MutableStateFlow<List<GameApplication>>(emptyList())
    val applications: StateFlow<List<GameApplication>> = _applications.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        refresh()
    }

    /**
     * Refresh the list of gaming applications.
     */
    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                _applications.value = gameApplicationRepository.list()
            } catch (e: Exception) {
                _error.value = "Failed to load gaming applications: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Add an application manually by package name.
     */
    fun addManual(packageName: String) {
        viewModelScope.launch {
            val result = gameApplicationRepository.addManual(packageName)
            result.onSuccess {
                refresh()
            }.onFailure {
                _error.value = "Failed to add application: ${it.message}"
            }
        }
    }
}