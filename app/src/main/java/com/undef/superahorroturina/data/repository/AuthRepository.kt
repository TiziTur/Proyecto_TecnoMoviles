// Repositorio de autenticación: encapsula las llamadas de red de login/registro
// y persiste el token en DataStore. Los ViewModels solo conocen este repositorio,
// no saben nada de Retrofit ni DataStore — eso es la separación de capas.
package com.undef.superahorroturina.data.repository

import com.undef.superahorroturina.data.local.SessionDataStore
import com.undef.superahorroturina.data.local.db.AppDatabase
import com.undef.superahorroturina.data.network.ApiService
import com.undef.superahorroturina.data.network.dto.LoginRequest
import com.undef.superahorroturina.data.network.dto.RegisterRequest
import com.undef.superahorroturina.data.network.dto.UpdateUserRequest
import com.undef.superahorroturina.model.User
import com.undef.superahorroturina.ui.biometric.BiometricCryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val message: String) : ApiResult<Nothing>()
}

@Singleton
class AuthRepository @Inject constructor(
    private val api: ApiService,
    private val session: SessionDataStore,
    private val database: AppDatabase,
    private val biometricCryptoManager: BiometricCryptoManager
) {
    // Borra la caché local (Room) de la cuenta anterior para que un nuevo
    // login/registro no muestre compras de otro usuario que quedaron cacheadas.
    private suspend fun clearLocalCache() = withContext(Dispatchers.IO) {
        database.clearAllTables()
    }

    suspend fun login(email: String, password: String): ApiResult<User> = runCatching {
        val response = api.login(LoginRequest(email, password))
        if (response.isSuccessful) {
            val body = response.body()!!
            clearLocalCache()
            session.saveSession(
                token     = body.token,
                userId    = body.user.id,
                firstName = body.user.firstName,
                lastName  = body.user.lastName,
                email     = body.user.email,
                phone     = body.user.phone
            )
            ApiResult.Success(
                User(body.user.id, body.user.firstName, body.user.lastName,
                     body.user.email, body.user.phone)
            )
        } else {
            val msg = when (response.code()) {
                401  -> "Email o contraseña incorrectos"
                else -> "Error ${response.code()}"
            }
            ApiResult.Error(msg)
        }
    }.getOrElse { ApiResult.Error(it.message ?: "Error de conexión") }

    suspend fun register(
        firstName: String, lastName: String,
        email: String, password: String, phone: String
    ): ApiResult<User> = runCatching {
        val response = api.register(RegisterRequest(firstName, lastName, email, password, phone))
        if (response.isSuccessful) {
            val body = response.body()!!
            clearLocalCache()
            session.saveSession(
                token     = body.token,
                userId    = body.user.id,
                firstName = body.user.firstName,
                lastName  = body.user.lastName,
                email     = body.user.email,
                phone     = body.user.phone
            )
            ApiResult.Success(
                User(body.user.id, body.user.firstName, body.user.lastName,
                     body.user.email, body.user.phone)
            )
        } else {
            val msg = when (response.code()) {
                409  -> "El email ya está registrado"
                else -> "Error ${response.code()}"
            }
            ApiResult.Error(msg)
        }
    }.getOrElse { ApiResult.Error(it.message ?: "Error de conexión") }

    suspend fun updateProfile(
        firstName: String, lastName: String,
        email: String, phone: String
    ): ApiResult<User> = runCatching {
        val token = session.bearerToken.first()
        val response = api.updateMe(token, UpdateUserRequest(firstName, lastName, email, phone))
        if (response.isSuccessful) {
            val u = response.body()!!
            // Actualizar datos en DataStore sin cambiar el token
            val currentSession = session.session.first()
            session.saveSession(currentSession.token, u.id, u.firstName, u.lastName, u.email, u.phone)
            ApiResult.Success(User(u.id, u.firstName, u.lastName, u.email, u.phone))
        } else {
            ApiResult.Error("Error al actualizar perfil")
        }
    }.getOrElse { ApiResult.Error(it.message ?: "Error de conexión") }

    suspend fun logout() {
        session.clearSession()
        biometricCryptoManager.disableBiometricLogin()
        clearLocalCache()
    }

    fun getSessionFlow() = session.session
}
