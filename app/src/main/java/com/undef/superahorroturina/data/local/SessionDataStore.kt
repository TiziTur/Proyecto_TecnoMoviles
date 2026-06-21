// Persiste los datos no sensibles del usuario en DataStore Preferences (nombre,
// email, etc.) y mantiene el token JWT SOLO en memoria — nunca en disco en texto
// plano. El token sobrevive a un reinicio de la app únicamente si la biometría está
// activada (ver BiometricCryptoManager), que lo guarda cifrado por separado.
package com.undef.superahorroturina.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Extensión a nivel top-level — instancia única del DataStore para toda la app
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

data class SessionData(
    val token: String = "",
    val userId: Int = -1,
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val phone: String = ""
)

@Singleton
class SessionDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val USER_ID    = intPreferencesKey("user_id")
        val FIRST_NAME = stringPreferencesKey("first_name")
        val LAST_NAME  = stringPreferencesKey("last_name")
        val EMAIL      = stringPreferencesKey("email")
        val PHONE      = stringPreferencesKey("phone")
    }

    // Token en memoria — nunca se persiste en texto plano; solo vive mientras corre el proceso.
    private val _token = MutableStateFlow("")

    // Flow reactivo: combina el token en memoria con los datos no sensibles persistidos.
    val session: Flow<SessionData> = combine(_token, context.dataStore.data) { token, prefs ->
        SessionData(
            token     = token,
            userId    = prefs[Keys.USER_ID]    ?: -1,
            firstName = prefs[Keys.FIRST_NAME] ?: "",
            lastName  = prefs[Keys.LAST_NAME]  ?: "",
            email     = prefs[Keys.EMAIL]      ?: "",
            phone     = prefs[Keys.PHONE]      ?: ""
        )
    }

    suspend fun saveSession(token: String, userId: Int, firstName: String,
                            lastName: String, email: String, phone: String) {
        _token.value = token
        context.dataStore.edit { prefs ->
            prefs[Keys.USER_ID]    = userId
            prefs[Keys.FIRST_NAME] = firstName
            prefs[Keys.LAST_NAME]  = lastName
            prefs[Keys.EMAIL]      = email
            prefs[Keys.PHONE]      = phone
        }
    }

    // Restaura el token en memoria tras un desbloqueo biométrico exitoso, sin tocar
    // el resto de la sesión (que ya está persistida en DataStore).
    fun restoreToken(token: String) {
        _token.value = token
    }

    suspend fun clearSession() {
        _token.value = ""
        context.dataStore.edit { it.clear() }
    }

    // Helper para obtener el Bearer token listo para usar en headers Retrofit
    val bearerToken: Flow<String> = session.map { "Bearer ${it.token}" }
}
