package com.pythontopia.feature.retro

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class RetroViewModel @Inject constructor() : ViewModel() {
    private val _state = MutableStateFlow(RetroUiState())
    val state: StateFlow<RetroUiState> = _state.asStateFlow()
}

data class RetroUiState(val isLoading: Boolean = false)
