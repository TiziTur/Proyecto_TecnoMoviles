// DTOs para autenticación — mapean exactamente lo que envía/recibe el backend.
// Se separan del modelo de dominio para no acoplar la red con el resto de la app.
package com.undef.superahorroturina.data.network.dto

import com.google.gson.annotations.SerializedName

// ── Request ───────────────────────────────────────────────────

data class LoginRequest(
    val email: String,
    val password: String
)

data class RegisterRequest(
    @SerializedName("first_name") val firstName: String,
    @SerializedName("last_name")  val lastName: String,
    val email: String,
    val password: String,
    val phone: String = ""
)

// ── Response ──────────────────────────────────────────────────

data class AuthResponse(
    val token: String,
    val user: UserDto
)

data class UserDto(
    val id: Int,
    @SerializedName("first_name") val firstName: String,
    @SerializedName("last_name")  val lastName: String,
    val email: String,
    val phone: String = ""
)

data class UpdateUserRequest(
    @SerializedName("first_name") val firstName: String,
    @SerializedName("last_name")  val lastName: String,
    val email: String,
    val phone: String
)
