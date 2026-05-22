package com.pythontopia.feature.fields

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class FieldsViewModel @Inject constructor() : ViewModel() {
    private val _state = MutableStateFlow(FieldsUiState())
    val state: StateFlow<FieldsUiState> = _state.asStateFlow()
}

data class FieldsUiState(val isLoading: Boolean = false)
