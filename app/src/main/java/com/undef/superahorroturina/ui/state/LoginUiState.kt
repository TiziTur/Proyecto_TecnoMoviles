package com.undef.superahorroturina.ui.state

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val showPassword: Boolean = false,
    val emailError: Boolean = false,
    val isLoading: Boolean = false
)
