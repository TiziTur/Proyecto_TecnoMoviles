// Persiste preferencias de UI con DataStore Preferences:
// - dark_mode: tema oscuro/claro
// - monthly_limit: límite mensual de presupuesto (en pesos)
// Se lee desde MainActivity (darkMode) y desde HomeViewModel (monthly_limit).
package com.undef.superahorroturina.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.themeDataStore by preferencesDataStore(name = "theme")

@Singleton
class ThemeDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val DARK_MODE     = booleanPreferencesKey("dark_mode")
    private val MONTHLY_LIMIT = floatPreferencesKey("monthly_limit")

    val isDarkMode: Flow<Boolean> = context.themeDataStore.data
        .map { prefs -> prefs[DARK_MODE] ?: false }

    // Default: 50.000 pesos
    val monthlyLimit: Flow<Float> = context.themeDataStore.data
        .map { prefs -> prefs[MONTHLY_LIMIT] ?: 50_000f }

    suspend fun setDarkMode(enabled: Boolean) {
        context.themeDataStore.edit { it[DARK_MODE] = enabled }
    }

    suspend fun setMonthlyLimit(limit: Float) {
        context.themeDataStore.edit { it[MONTHLY_LIMIT] = limit }
    }
}
