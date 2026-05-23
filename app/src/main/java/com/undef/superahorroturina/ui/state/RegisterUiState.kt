package com.undef.superahorroturina.ui.state

data class RegisterUiState(
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val phone: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val showPassword: Boolean = false,
    val passwordError: Boolean = false,
    val isLoading: Boolean = false,
    val apiError: String = ""
)
