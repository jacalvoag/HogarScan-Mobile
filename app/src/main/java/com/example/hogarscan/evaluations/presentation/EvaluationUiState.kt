package com.example.hogarscan.evaluations.presentation

import com.example.hogarscan.evaluations.domain.model.EvaluationResult

sealed interface EvaluationUiState {
    data object Ready : EvaluationUiState
    data object Loading : EvaluationUiState
    data class Error(val message: String) : EvaluationUiState
    data class Success(val result: EvaluationResult) : EvaluationUiState
}
