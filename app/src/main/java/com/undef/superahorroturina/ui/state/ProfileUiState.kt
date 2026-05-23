package com.undef.superahorroturina.ui.state

data class ProfileUiState(
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val phone: String = "",
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val saveError: String = ""
)
