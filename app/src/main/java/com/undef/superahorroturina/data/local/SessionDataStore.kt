// Persiste el token JWT y datos básicos del usuario usando DataStore Preferences.
// DataStore es el reemplazo moderno de SharedPreferences — usa coroutines y es type-safe.
// El token se guarda al hacer login y se limpia al cerrar sesión.
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
        val TOKEN      = stringPreferencesKey("token")
        val USER_ID    = intPreferencesKey("user_id")
        val FIRST_NAME = stringPreferencesKey("first_name")
        val LAST_NAME  = stringPreferencesKey("last_name")
        val EMAIL      = stringPreferencesKey("email")
        val PHONE      = stringPreferencesKey("phone")
    }

    // Flow reactivo: cualquier cambio en DataStore emite un nuevo SessionData
    val session: Flow<SessionData> = context.dataStore.data.map { prefs ->
        SessionData(
            token     = prefs[Keys.TOKEN]      ?: "",
            userId    = prefs[Keys.USER_ID]    ?: -1,
            firstName = prefs[Keys.FIRST_NAME] ?: "",
            lastName  = prefs[Keys.LAST_NAME]  ?: "",
            email     = prefs[Keys.EMAIL]      ?: "",
            phone     = prefs[Keys.PHONE]      ?: ""
        )
    }

    suspend fun saveSession(token: String, userId: Int, firstName: String,
                            lastName: String, email: String, phone: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.TOKEN]      = token
            prefs[Keys.USER_ID]    = userId
            prefs[Keys.FIRST_NAME] = firstName
            prefs[Keys.LAST_NAME]  = lastName
            prefs[Keys.EMAIL]      = email
            prefs[Keys.PHONE]      = phone
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { it.clear() }
    }

    // Helper para obtener el Bearer token listo para usar en headers Retrofit
    val bearerToken: Flow<String> = session.map { "Bearer ${it.token}" }
}
