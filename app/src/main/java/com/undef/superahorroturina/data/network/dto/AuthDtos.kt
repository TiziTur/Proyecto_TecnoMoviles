// DTOs para autenticación — mapean exactamente lo que envía/recibe el backend.
// Se separan del modelo de dominio para no acoplar la red con el resto de la app.
package com.undef.superahorroturina.data.network.dto

// ── Request ───────────────────────────────────────────────────

data class LoginRequest(
    val email: String,
    val password: String
)

data class RegisterRequest(
    val firstName: String,
    val lastName: String,
    val email: String,
    val password: String,
    val phone: String = ""
)

// ── Response ──────────────────────────────────────────────────

data class AuthResponse(
    val token: String,
    val user: UserDto
)

// El backend devuelve camelCase (firstName, lastName) — sin @SerializedName
data class UserDto(
    val id: Int,
    val firstName: String,
    val lastName: String,
    val email: String,
    val phone: String = ""
)

data class UpdateUserRequest(
    val firstName: String,
    val lastName: String,
    val email: String,
    val phone: String
)
