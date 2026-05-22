package com.pythontopia.feature.arena

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class ArenaViewModel @Inject constructor() : ViewModel() {
    private val _state = MutableStateFlow(ArenaUiState())
    val state: StateFlow<ArenaUiState> = _state.asStateFlow()
}

data class ArenaUiState(val isLoading: Boolean = false)
