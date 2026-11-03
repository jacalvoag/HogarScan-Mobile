package com.example.hogarscan.scan.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hogarscan.scan.domain.repository.WifiNetworkRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WifiViewModel(
    private val repository: WifiNetworkRepository
) : ViewModel() {

    private val _uiState =
        MutableStateFlow<WifiUiState>(WifiUiState.Idle)

    val uiState: StateFlow<WifiUiState> = _uiState.asStateFlow()

    fun refresh() {
        if (_uiState.value == WifiUiState.Loading) {
            return
        }

        _uiState.value = WifiUiState.Loading

        viewModelScope.launch {
            try {
                _uiState.value = WifiUiState.Loaded(
                    repository.getCurrentNetwork()
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                _uiState.value = WifiUiState.Error
            }
        }
    }
}