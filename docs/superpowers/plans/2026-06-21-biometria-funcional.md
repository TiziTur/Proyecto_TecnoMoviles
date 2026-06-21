# Biometría funcional Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Hacer que el inicio de sesión biométrico proteja realmente el token JWT, en vez
de ser solo un gate de UI sobre un token en texto plano siempre legible.

**Architecture:** El token deja de persistirse en texto plano: vive únicamente en
memoria mientras el proceso corre. Para sobrevivir a un reinicio de la app, se cifra con
una clave de Android Keystore que exige autenticación biométrica reciente
(`BiometricPrompt.CryptoObject`) tanto para cifrar (al activar la huella) como para
descifrar (al desbloquear). Un toggle en Ajustes permite activar/desactivar
manualmente, reutilizando la misma lógica de cifrado que el diálogo de consentimiento
del login.

**Tech Stack:** Android Kotlin/Jetpack Compose/Hilt, `androidx.biometric:biometric`
(ya en el proyecto), Android Keystore (`javax.crypto`, `android.security.keystore`),
DataStore Preferences.

**Spec:** `docs/superpowers/specs/2026-06-21-biometria-funcional-design.md`

**Convención de testing de este repo:** no hay framework de tests automatizados en
Android. Verificación por `./gradlew :app:compileDebugKotlin` más prueba manual en
dispositivo/emulador con biometría enrolada — el Keystore real no existe en una JVM de
test, así que no hay forma de probar el cifrado/descifrado fuera de un runtime Android
real. Cada tarea de este plan termina con compilación; la prueba manual de extremo a
extremo se hace al final, sobre el conjunto completo.

---

### Task 1: `BiometricCryptoManager` — cifrado atado a Keystore

**Files:**
- Create: `app/src/main/java/com/undef/superahorroturina/ui/biometric/BiometricCryptoManager.kt`

- [ ] **Step 1: Crear el archivo**

```kotlin
// BiometricCryptoManager.kt — Cifra/descifra el token de sesión usando una clave de
// Android Keystore que exige autenticación biométrica (o PIN del dispositivo) reciente
// para poder usarse. El sistema operativo se niega a operar la clave sin esa
// autenticación — la huella no es solo un gate de UI, es lo que habilita el
// descifrado real del token.
package com.undef.superahorroturina.ui.biometric

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

private val Context.biometricDataStore: DataStore<Preferences> by preferencesDataStore(name = "biometric_session")

@Singleton
class BiometricCryptoManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val ENABLED        = booleanPreferencesKey("biometric_enabled")
        val TOKEN_CIPHER   = stringPreferencesKey("token_ciphertext")
        val TOKEN_IV       = stringPreferencesKey("token_iv")
    }

    // Resultado de pedir un Cipher para cifrar o descifrar — distingue los tres casos
    // que le importan al que llama: listo para usar, clave invalidada (se agregó/quitó
    // una huella del sistema desde que se activó), o nada guardado todavía.
    sealed class CipherResult {
        data class Ready(val cipher: Cipher) : CipherResult()
        object KeyInvalidated : CipherResult()
        object NoStoredToken : CipherResult()
    }

    suspend fun isBiometricEnabled(): Boolean =
        context.biometricDataStore.data.first()[Keys.ENABLED] ?: false

    // Cipher en modo cifrado — usado al activar la huella (login o Ajustes).
    fun createEncryptCipherResult(): CipherResult = try {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        CipherResult.Ready(cipher)
    } catch (e: KeyPermanentlyInvalidatedException) {
        CipherResult.KeyInvalidated
    }

    // Cipher en modo descifrado — usado al reabrir la app con biometría ya activada.
    suspend fun createDecryptCipherResult(): CipherResult {
        val ivBase64 = context.biometricDataStore.data.first()[Keys.TOKEN_IV]
            ?: return CipherResult.NoStoredToken
        return try {
            val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val key = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                .getKey(KEY_ALIAS, null) as? SecretKey
                ?: return CipherResult.NoStoredToken
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            CipherResult.Ready(cipher)
        } catch (e: KeyPermanentlyInvalidatedException) {
            CipherResult.KeyInvalidated
        }
    }

    // Cifra el token con un Cipher ya autenticado (resultado de un BiometricPrompt
    // exitoso) y lo persiste junto con el IV usado. Marca biometricEnabled = true.
    suspend fun saveEncryptedToken(cipher: Cipher, token: String) {
        val ciphertext = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        val ivBase64         = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val ciphertextBase64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        context.biometricDataStore.edit { prefs ->
            prefs[Keys.ENABLED]      = true
            prefs[Keys.TOKEN_CIPHER] = ciphertextBase64
            prefs[Keys.TOKEN_IV]     = ivBase64
        }
    }

    // Descifra el ciphertext guardado con un Cipher ya autenticado.
    suspend fun decryptStoredToken(cipher: Cipher): String? {
        val ciphertextBase64 = context.biometricDataStore.data.first()[Keys.TOKEN_CIPHER] ?: return null
        val ciphertext = Base64.decode(ciphertextBase64, Base64.NO_WRAP)
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    // Borra la clave de Keystore y todo lo persistido — usado al desactivar manualmente,
    // al detectar invalidación de la clave, o al cerrar sesión.
    suspend fun disableBiometricLogin() {
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            keyStore.deleteEntry(KEY_ALIAS)
        } catch (e: Exception) {
            // La clave ya no existe o el Keystore no está disponible — no bloquea la limpieza.
        }
        context.biometricDataStore.edit { it.clear() }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val specBuilder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            specBuilder.setUserAuthenticationParameters(
                0,
                KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
            )
        } else {
            @Suppress("DEPRECATION")
            specBuilder.setUserAuthenticationValidityDurationSeconds(-1)
        }

        keyGenerator.init(specBuilder.build())
        return keyGenerator.generateKey()
    }

    companion object {
        private const val KEY_ALIAS = "klarity_session_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
```

- [ ] **Step 2: Verificar que compila**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`. Este archivo no se usa todavía en ningún lado — solo debe
compilar de forma aislada.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/undef/superahorroturina/ui/biometric/BiometricCryptoManager.kt
git commit -m "feat: BiometricCryptoManager para cifrar el token con Keystore"
```

---

### Task 2: `AuthRepository` limpia la biometría al cerrar sesión

**Files:**
- Modify: `app/src/main/java/com/undef/superahorroturina/data/repository/AuthRepository.kt`

Contenido actual completo (114 líneas) — ya leído íntegro. Cambios:

- [ ] **Step 1: Inyectar `BiometricCryptoManager` y usarlo en `logout()`**

Agregar el import, junto a los existentes (línea 6-12):

```kotlin
import com.undef.superahorroturina.ui.biometric.BiometricCryptoManager
```

Cambiar el constructor (líneas 25-29):

```kotlin
@Singleton
class AuthRepository @Inject constructor(
    private val api: ApiService,
    private val session: SessionDataStore,
    private val database: AppDatabase,
    private val biometricCryptoManager: BiometricCryptoManager
) {
```

Cambiar `logout()` (líneas 108-111):

```kotlin
    suspend fun logout() {
        session.clearSession()
        biometricCryptoManager.disableBiometricLogin()
        clearLocalCache()
    }
```

No cambiar nada más del archivo (`login`, `register`, `updateProfile`,
`getSessionFlow` quedan exactamente igual).

- [ ] **Step 2: Verificar que compila**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/undef/superahorroturina/data/repository/AuthRepository.kt
git commit -m "feat: logout tambien desactiva la biometria guardada"
```

---

### Task 3: `SessionDataStore` — el token vive solo en memoria

**Files:**
- Modify: `app/src/main/java/com/undef/superahorroturina/data/local/SessionDataStore.kt`

Contenido actual completo (75 líneas) — ya leído íntegro.

- [ ] **Step 1: Reemplazar el archivo completo**

```kotlin
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
```

- [ ] **Step 2: Verificar que compila**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`. La interfaz pública (`session`, `bearerToken`,
`saveSession`, `clearSession`) no cambió de forma — todo lo que ya la consume (cada
ViewModel que llama `session.bearerToken.first()`, y `AuthRepository.updateProfile`
que lee `session.session.first().token`) sigue compilando sin cambios.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/undef/superahorroturina/data/local/SessionDataStore.kt
git commit -m "feat: el token JWT vive solo en memoria, nunca en texto plano persistido"
```

---

### Task 4: Toggle de biometría — estado y lógica en `SettingsViewModel`

**Files:**
- Modify: `app/src/main/java/com/undef/superahorroturina/ui/state/SettingsUiState.kt`
- Modify: `app/src/main/java/com/undef/superahorroturina/ui/screens/settings/SettingsViewModel.kt`

Contenido actual completo de ambos archivos ya leído íntegro (11 y 68 líneas
respectivamente).

- [ ] **Step 1: Agregar el campo a `SettingsUiState`**

```kotlin
package com.undef.superahorroturina.ui.state

data class SettingsUiState(
    val darkMode: Boolean = false,
    val notifications: Boolean = true,
    val priceAlerts: Boolean = true,
    val language: String = "Español",
    val languageExpanded: Boolean = false,
    val selectedSort: String = "Más reciente",
    val monthlyLimit: Float = 50000f,
    val biometricEnabled: Boolean = false
)
```

- [ ] **Step 2: Agregar la lógica al `SettingsViewModel`**

Reemplazar el archivo completo:

```kotlin
package com.undef.superahorroturina.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.undef.superahorroturina.data.local.SessionDataStore
import com.undef.superahorroturina.data.local.ThemeDataStore
import com.undef.superahorroturina.ui.biometric.BiometricCryptoManager
import com.undef.superahorroturina.ui.state.SettingsUiState
import com.undef.superahorroturina.workers.PriceAlertWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val themeDataStore: ThemeDataStore,
    private val biometricCryptoManager: BiometricCryptoManager,
    private val sessionDataStore: SessionDataStore,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { themeDataStore.isDarkMode.collect { _uiState.value = _uiState.value.copy(darkMode = it) } }
        viewModelScope.launch { themeDataStore.monthlyLimit.collect { _uiState.value = _uiState.value.copy(monthlyLimit = it) } }
        viewModelScope.launch { themeDataStore.priceAlertsEnabled.collect { _uiState.value = _uiState.value.copy(priceAlerts = it) } }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(biometricEnabled = biometricCryptoManager.isBiometricEnabled())
        }
    }

    fun onDarkModeChange(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(darkMode = enabled)
        viewModelScope.launch { themeDataStore.setDarkMode(enabled) }
    }

    fun onNotificationsChange(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(notifications = enabled)
    }

    fun onPriceAlertsChange(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(priceAlerts = enabled)
        viewModelScope.launch {
            themeDataStore.setPriceAlertsEnabled(enabled)
            if (enabled) {
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    PriceAlertWorker.WORK_TAG,
                    ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<PriceAlertWorker>(1, TimeUnit.DAYS)
                        .addTag(PriceAlertWorker.WORK_TAG)
                        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                        .build()
                )
            } else {
                WorkManager.getInstance(context).cancelUniqueWork(PriceAlertWorker.WORK_TAG)
            }
        }
    }

    fun onLanguageChange(language: String) { _uiState.value = _uiState.value.copy(language = language, languageExpanded = false) }
    fun onLanguageExpandedChange(expanded: Boolean) { _uiState.value = _uiState.value.copy(languageExpanded = expanded) }
    fun onSortChange(sort: String) { _uiState.value = _uiState.value.copy(selectedSort = sort) }
    fun onMonthlyLimitChange(limit: Float) {
        _uiState.value = _uiState.value.copy(monthlyLimit = limit)
        viewModelScope.launch { themeDataStore.setMonthlyLimit(limit) }
    }

    // Apagar no necesita huella: solo borra lo guardado.
    fun onBiometricDisabled() {
        viewModelScope.launch {
            biometricCryptoManager.disableBiometricLogin()
            _uiState.value = _uiState.value.copy(biometricEnabled = false)
        }
    }

    // Prender necesita un Cipher autenticado por BiometricPrompt — la Composable se
    // encarga de mostrar el prompt (necesita la Activity) y llama a estas dos
    // funciones para preparar y luego confirmar el cifrado.
    suspend fun prepareBiometricEnrollCipher(): Cipher? =
        (biometricCryptoManager.createEncryptCipherResult() as? BiometricCryptoManager.CipherResult.Ready)?.cipher

    fun onBiometricEnrollConfirmed(cipher: Cipher) {
        viewModelScope.launch {
            val token = sessionDataStore.session.first().token
            if (token.isNotBlank()) {
                biometricCryptoManager.saveEncryptedToken(cipher, token)
                _uiState.value = _uiState.value.copy(biometricEnabled = true)
            }
        }
    }
}
```

- [ ] **Step 3: Verificar que compila**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`. `SettingsScreen.kt` todavía no lee `biometricEnabled` ni
llama a los métodos nuevos — eso es la Tarea 6, y no rompe nada porque solo se
agregaron miembros, no se quitó ni renombró nada existente.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/undef/superahorroturina/ui/state/SettingsUiState.kt app/src/main/java/com/undef/superahorroturina/ui/screens/settings/SettingsViewModel.kt
git commit -m "feat: SettingsViewModel maneja activar/desactivar biometria"
```

---

### Task 5: Flujo de login con `CryptoObject` (BiometricHelper + LoginViewModel + LoginScreen)

**Files:**
- Modify: `app/src/main/java/com/undef/superahorroturina/ui/biometric/BiometricHelper.kt`
- Modify: `app/src/main/java/com/undef/superahorroturina/ui/screens/auth/LoginViewModel.kt`
- Modify: `app/src/main/java/com/undef/superahorroturina/ui/screens/auth/LoginScreen.kt`

Estos tres archivos cambian juntos en un solo commit porque están acoplados: cambiar
la firma de `showBiometricPrompt` rompe las llamadas existentes en `LoginScreen`, y
`LoginViewModel` pierde el método `onBiometricSuccess` que `LoginScreen` llama hoy —
hacerlo en commits separados dejaría el proyecto sin compilar en un punto intermedio.

Contenido actual completo de los tres archivos ya leído íntegro (69, 84 y 311 líneas
respectivamente).

- [ ] **Step 1: Reemplazar `BiometricHelper.kt` completo**

```kotlin
// BiometricHelper.kt — Encapsula la lógica de BiometricPrompt de AndroidX.
// Muestra el diálogo nativo del sistema para autenticación con huella dactilar
// o credenciales de dispositivo (PIN/patrón) como fallback. El prompt siempre va
// atado a un CryptoObject (ver BiometricCryptoManager) — la autenticación no es
// solo un gate de UI, es lo que habilita usar la clave de Keystore para cifrar o
// descifrar el token de sesión.
package com.undef.superahorroturina.ui.biometric

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Verifica si el dispositivo puede usar autenticación biométrica fuerte.
 * Retorna true si hay al menos un método de autenticación disponible y registrado.
 */
fun canUseBiometric(activity: FragmentActivity): Boolean {
    val manager = BiometricManager.from(activity)
    return manager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) ==
           BiometricManager.BIOMETRIC_SUCCESS
}

/**
 * Lanza el diálogo biométrico del sistema, atado a un CryptoObject.
 * @param activity     La Activity host (necesaria para FragmentManager)
 * @param cryptoObject El Cipher (de BiometricCryptoManager) que se autentica con este prompt
 * @param title        Título del diálogo
 * @param subtitle     Subtítulo del diálogo
 * @param onSuccess    Callback al autenticarse correctamente (recibe el resultado, con el Cipher ya autenticado)
 * @param onError      Callback al cancelar o fallar (recibe mensaje de error)
 */
fun showBiometricPrompt(
    activity: FragmentActivity,
    cryptoObject: BiometricPrompt.CryptoObject,
    title: String = "Desbloquear Klarity",
    subtitle: String = "Usá tu huella dactilar para continuar",
    onSuccess: (BiometricPrompt.AuthenticationResult) -> Unit,
    onError: (String) -> Unit = {}
) {
    val executor = ContextCompat.getMainExecutor(activity)

    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            super.onAuthenticationSucceeded(result)
            onSuccess(result)
        }
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            super.onAuthenticationError(errorCode, errString)
            // Código 13 = usuario canceló, no mostramos error en ese caso
            if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                onError(errString.toString())
            }
        }
        override fun onAuthenticationFailed() {
            super.onAuthenticationFailed()
            // El sistema ya muestra el "Huella no reconocida" — no necesitamos hacer nada
        }
    }

    val prompt = BiometricPrompt(activity, executor, callback)

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
        .build()

    prompt.authenticate(promptInfo, cryptoObject)
}
```

- [ ] **Step 2: Reemplazar `LoginViewModel.kt` completo**

```kotlin
// ViewModel para Login conectado al AuthRepository real.
// v3: el token solo se restaura en memoria tras un desbloqueo biométrico real
// (CryptoObject), no se asume válido por el solo hecho de existir.
package com.undef.superahorroturina.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.undef.superahorroturina.data.local.SessionDataStore
import com.undef.superahorroturina.data.repository.ApiResult
import com.undef.superahorroturina.data.repository.AuthRepository
import com.undef.superahorroturina.ui.biometric.BiometricCryptoManager
import com.undef.superahorroturina.ui.state.LoginUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.crypto.Cipher
import javax.inject.Inject

// Estado de qué Cipher (si alguno) está listo para desbloquear con biometría al abrir la app.
sealed class BiometricUnlockState {
    object NotAvailable : BiometricUnlockState()
    data class Ready(val cipher: Cipher) : BiometricUnlockState()
    object KeyInvalidated : BiometricUnlockState()
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionDataStore: SessionDataStore,
    private val biometricCryptoManager: BiometricCryptoManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    // Nombre del usuario guardado en sesión (para mostrar "Bienvenido de vuelta, Juan")
    private val _savedUserName = MutableStateFlow("")
    val savedUserName: StateFlow<String> = _savedUserName.asStateFlow()

    // true si hay biometría activada (no implica que el token ya esté descifrado)
    private val _hasSavedSession = MutableStateFlow(false)
    val hasSavedSession: StateFlow<Boolean> = _hasSavedSession.asStateFlow()

    // Diálogo de consentimiento para activar huella, mostrado una vez tras un login con contraseña exitoso
    private val _showEnrollDialog = MutableStateFlow(false)
    val showEnrollDialog: StateFlow<Boolean> = _showEnrollDialog.asStateFlow()

    // Mensaje a mostrar si se detectó que la clave de biometría quedó invalidada
    private val _biometricMessage = MutableStateFlow("")
    val biometricMessage: StateFlow<String> = _biometricMessage.asStateFlow()

    init {
        viewModelScope.launch {
            _hasSavedSession.value = biometricCryptoManager.isBiometricEnabled()
            _savedUserName.value   = authRepository.getSessionFlow().first().firstName
        }
    }

    fun onEmailChange(value: String) {
        _uiState.value = _uiState.value.copy(email = value, emailError = false, apiError = "")
    }

    fun onPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(password = value, apiError = "")
    }

    fun onTogglePasswordVisibility() {
        _uiState.value = _uiState.value.copy(showPassword = !_uiState.value.showPassword)
    }

    fun onLogin(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.email.isBlank() || !state.email.contains("@")) {
            _uiState.value = state.copy(emailError = true)
            return
        }
        if (state.password.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, apiError = "")
            when (val result = authRepository.login(state.email, state.password)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    if (biometricCryptoManager.isBiometricEnabled()) {
                        onSuccess()
                    } else {
                        // Recién logueado, sin biometría activada todavía: ofrecer activarla.
                        _showEnrollDialog.value = true
                    }
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, apiError = result.message)
                }
            }
        }
    }

    // ── Activar biometría tras login con contraseña (o el usuario la rechaza) ──────

    suspend fun prepareEnrollCipher(): Cipher? =
        (biometricCryptoManager.createEncryptCipherResult() as? BiometricCryptoManager.CipherResult.Ready)?.cipher

    fun onEnrollConfirmed(cipher: Cipher, onDone: () -> Unit) {
        viewModelScope.launch {
            val token = sessionDataStore.session.first().token
            if (token.isNotBlank()) {
                biometricCryptoManager.saveEncryptedToken(cipher, token)
                _hasSavedSession.value = true
            }
            _showEnrollDialog.value = false
            onDone()
        }
    }

    fun onEnrollDeclined(onDone: () -> Unit) {
        _showEnrollDialog.value = false
        onDone()
    }

    // ── Desbloqueo biométrico al reabrir la app con biometría ya activada ──────────

    suspend fun prepareUnlockCipher(): BiometricUnlockState {
        if (!biometricCryptoManager.isBiometricEnabled()) return BiometricUnlockState.NotAvailable
        return when (val r = biometricCryptoManager.createDecryptCipherResult()) {
            is BiometricCryptoManager.CipherResult.Ready ->
                BiometricUnlockState.Ready(r.cipher)
            is BiometricCryptoManager.CipherResult.KeyInvalidated ->
                BiometricUnlockState.KeyInvalidated
            is BiometricCryptoManager.CipherResult.NoStoredToken ->
                BiometricUnlockState.NotAvailable
        }
    }

    suspend fun onBiometricUnlockSuccess(cipher: Cipher) {
        val token = biometricCryptoManager.decryptStoredToken(cipher) ?: return
        sessionDataStore.restoreToken(token)
    }

    suspend fun onBiometricKeyInvalidated() {
        biometricCryptoManager.disableBiometricLogin()
        _hasSavedSession.value = false
        _biometricMessage.value = "Tu configuración de huella cambió, iniciá sesión de nuevo."
    }

    fun clearBiometricMessage() {
        _biometricMessage.value = ""
    }
}
```

- [ ] **Step 3: Reemplazar `LoginScreen.kt` completo**

```kotlin
// Pantalla de login conectada a LoginViewModel.
// v4: la huella desbloquea el token cifrado en Keystore (CryptoObject), no solo
// gatea la navegación. Tras un login con contraseña sin biometría activada, se
// ofrece un diálogo único para activarla.
package com.undef.superahorroturina.ui.screens.auth

import androidx.biometric.BiometricPrompt
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.undef.superahorroturina.R
import com.undef.superahorroturina.ui.biometric.canUseBiometric
import com.undef.superahorroturina.ui.biometric.showBiometricPrompt
import com.undef.superahorroturina.ui.components.*
import com.undef.superahorroturina.ui.theme.SuperAhorroTheme
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState         by viewModel.uiState.collectAsStateWithLifecycle()
    val hasSavedSession by viewModel.hasSavedSession.collectAsStateWithLifecycle()
    val savedUserName   by viewModel.savedUserName.collectAsStateWithLifecycle()
    val showEnrollDialog by viewModel.showEnrollDialog.collectAsStateWithLifecycle()
    val biometricMessage by viewModel.biometricMessage.collectAsStateWithLifecycle()
    val isDark           = isSystemInDarkTheme()
    val coroutineScope    = rememberCoroutineScope()

    // LocalActivity es la FragmentActivity host — necesaria para BiometricPrompt
    val activity = LocalContext.current as? FragmentActivity

    // Si hay biometría activada Y el dispositivo la soporta, intentamos desbloquear al entrar
    LaunchedEffect(hasSavedSession) {
        if (hasSavedSession && activity != null && canUseBiometric(activity)) {
            when (val unlockState = viewModel.prepareUnlockCipher()) {
                is BiometricUnlockState.Ready -> {
                    showBiometricPrompt(
                        activity     = activity,
                        cryptoObject = BiometricPrompt.CryptoObject(unlockState.cipher),
                        title        = "Bienvenido de vuelta${if (savedUserName.isNotBlank()) ", $savedUserName" else ""}",
                        subtitle     = "Usá tu huella para acceder a Klarity",
                        onSuccess    = { result ->
                            coroutineScope.launch {
                                viewModel.onBiometricUnlockSuccess(result.cryptoObject!!.cipher!!)
                                onLoginSuccess()
                            }
                        }
                    )
                }
                is BiometricUnlockState.KeyInvalidated -> {
                    viewModel.onBiometricKeyInvalidated()
                }
                is BiometricUnlockState.NotAvailable -> Unit
            }
        }
    }

    // Si el diálogo de activar huella quedó pendiente pero el dispositivo no la soporta,
    // no nos quedamos trabados: directamente seguimos a Home (ya está logueado).
    LaunchedEffect(showEnrollDialog) {
        if (showEnrollDialog && (activity == null || !canUseBiometric(activity))) {
            viewModel.onEnrollDeclined(onDone = onLoginSuccess)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .dotPatternBackground(
                dotColor  = if (isDark) Color.White.copy(alpha = 0.025f) else Color.Black.copy(alpha = 0.018f),
                dotRadius = 1.2f,
                spacing   = 22f
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(72.dp))

            KlarityLogoIcon(size = 64)
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.splash_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            // ── Bienvenida rápida si hay biometría activada ───────────────
            if (hasSavedSession && savedUserName.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors   = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = MaterialTheme.shapes.large
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Fingerprint,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text  = "Bienvenido de vuelta, $savedUserName",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text  = "Tocá para usar huella dactilar",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                        if (activity != null && canUseBiometric(activity)) {
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        when (val unlockState = viewModel.prepareUnlockCipher()) {
                                            is BiometricUnlockState.Ready -> {
                                                showBiometricPrompt(
                                                    activity     = activity,
                                                    cryptoObject = BiometricPrompt.CryptoObject(unlockState.cipher),
                                                    title        = "Bienvenido de vuelta, $savedUserName",
                                                    subtitle     = "Usá tu huella para acceder",
                                                    onSuccess    = { result ->
                                                        coroutineScope.launch {
                                                            viewModel.onBiometricUnlockSuccess(result.cryptoObject!!.cipher!!)
                                                            onLoginSuccess()
                                                        }
                                                    }
                                                )
                                            }
                                            is BiometricUnlockState.KeyInvalidated -> {
                                                viewModel.onBiometricKeyInvalidated()
                                            }
                                            is BiometricUnlockState.NotAvailable -> Unit
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Default.Fingerprint,
                                    contentDescription = "Autenticar con huella",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(if (hasSavedSession) 16.dp else 48.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .coloredShadow(
                        color        = MaterialTheme.colorScheme.primary,
                        borderRadius = 28.dp,
                        blurRadius   = 20.dp,
                        offsetY      = 6.dp
                    )
                    .glowBorder(cornerRadius = 28.dp, isDark = isDark),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.login_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedTextField(
                        value = uiState.email,
                        onValueChange = { viewModel.onEmailChange(it) },
                        label = { Text(stringResource(R.string.field_email)) },
                        leadingIcon = {
                            Icon(Icons.Default.Email, contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        isError = uiState.emailError,
                        supportingText = if (uiState.emailError) {
                            { Text(stringResource(R.string.error_email)) }
                        } else null,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )

                    OutlinedTextField(
                        value = uiState.password,
                        onValueChange = { viewModel.onPasswordChange(it) },
                        label = { Text(stringResource(R.string.field_password)) },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        trailingIcon = {
                            IconButton(onClick = { viewModel.onTogglePasswordVisibility() }) {
                                Icon(
                                    imageVector = if (uiState.showPassword) Icons.Default.VisibilityOff
                                                  else Icons.Default.Visibility,
                                    contentDescription = null
                                )
                            }
                        },
                        visualTransformation = if (uiState.showPassword) VisualTransformation.None
                                               else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )

                    Box(modifier = Modifier.fillMaxWidth()) {
                        TextButton(
                            onClick = { /* TODO */ },
                            modifier = Modifier.align(Alignment.CenterEnd),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.login_forgot_password),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (uiState.apiError.isNotBlank()) {
                        Text(
                            text = uiState.apiError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    KlarityButton(
                        text = stringResource(R.string.action_login),
                        onClick = { viewModel.onLogin(onLoginSuccess) },
                        loading = uiState.isLoading,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.login_no_account),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = onNavigateToRegister,
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.action_register),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(48.dp))
        }
    }

    // ── Diálogo de invalidación de clave biométrica ───────────────────
    if (biometricMessage.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { viewModel.clearBiometricMessage() },
            title = { Text("Huella desactivada") },
            text  = { Text(biometricMessage) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearBiometricMessage() }) { Text("Entendido") }
            }
        )
    }

    // ── Diálogo de consentimiento para activar huella tras login con contraseña ──
    if (showEnrollDialog && activity != null && canUseBiometric(activity)) {
        AlertDialog(
            onDismissRequest = { viewModel.onEnrollDeclined(onDone = onLoginSuccess) },
            title = { Text("¿Activar inicio con huella?") },
            text  = { Text("Vas a poder volver a entrar a Klarity con tu huella o PIN, sin escribir la contraseña cada vez.") },
            confirmButton = {
                TextButton(onClick = {
                    coroutineScope.launch {
                        val cipher = viewModel.prepareEnrollCipher()
                        if (cipher != null) {
                            showBiometricPrompt(
                                activity     = activity,
                                cryptoObject = BiometricPrompt.CryptoObject(cipher),
                                title        = "Activar huella",
                                subtitle     = "Confirmá tu huella para activar el acceso rápido",
                                onSuccess    = { result ->
                                    coroutineScope.launch {
                                        viewModel.onEnrollConfirmed(result.cryptoObject!!.cipher!!, onDone = onLoginSuccess)
                                    }
                                },
                                onError = { viewModel.onEnrollDeclined(onDone = onLoginSuccess) }
                            )
                        } else {
                            viewModel.onEnrollDeclined(onDone = onLoginSuccess)
                        }
                    }
                }) { Text("Activar") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onEnrollDeclined(onDone = onLoginSuccess) }) { Text("Ahora no") }
            }
        )
    }
}

@Preview(showBackground = true, name = "Login Screen – Light")
@Composable
private fun LoginScreenPreview() {
    SuperAhorroTheme(darkTheme = false) {
        LoginScreen(onLoginSuccess = {}, onNavigateToRegister = {})
    }
}

@Preview(showBackground = true, name = "Login Screen – Dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LoginScreenDarkPreview() {
    SuperAhorroTheme(darkTheme = true) {
        LoginScreen(onLoginSuccess = {}, onNavigateToRegister = {})
    }
}
```

- [ ] **Step 4: Verificar que compila**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/undef/superahorroturina/ui/biometric/BiometricHelper.kt app/src/main/java/com/undef/superahorroturina/ui/screens/auth/LoginViewModel.kt app/src/main/java/com/undef/superahorroturina/ui/screens/auth/LoginScreen.kt
git commit -m "feat: la huella desbloquea el token cifrado en Keystore (CryptoObject)"
```

---

### Task 6: Switch de biometría en `SettingsScreen`

**Files:**
- Modify: `app/src/main/java/com/undef/superahorroturina/ui/screens/settings/SettingsScreen.kt`

Contenido actual completo (310 líneas) ya leído íntegro.

- [ ] **Step 1: Agregar imports necesarios**

Agregar, junto a los imports existentes (después de la línea `import
androidx.compose.ui.unit.dp`):

```kotlin
import androidx.biometric.BiometricPrompt
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import com.undef.superahorroturina.ui.biometric.canUseBiometric
import com.undef.superahorroturina.ui.biometric.showBiometricPrompt
import kotlinx.coroutines.launch
```

- [ ] **Step 2: Declarar `activity` y `coroutineScope` al principio de la función**

Justo después de `val sortOptions = listOf(...)` (antes del `Scaffold`), agregar:

```kotlin
    val activity = LocalContext.current as? FragmentActivity
    val coroutineScope = rememberCoroutineScope()
    val biometricAvailable = activity != null && canUseBiometric(activity)
```

- [ ] **Step 3: Agregar la nueva tarjeta con el switch**

Insertar un nuevo `SettingsCard` después de la tarjeta de "Apariencia" (la que tiene
`SettingsCategoryHeader(stringResource(R.string.settings_appearance))` y termina con
el `DropdownMenu` de idiomas) y antes de la tarjeta de "Notificaciones" — es decir,
justo antes de `SettingsCard(isDark = isDark) {` que contiene
`SettingsCategoryHeader(stringResource(R.string.settings_notifications))`:

```kotlin
                SettingsCard(isDark = isDark) {
                    SettingsCategoryHeader("Seguridad")

                    SettingsToggleItem(
                        icon            = Icons.Default.Fingerprint,
                        title           = "Inicio con huella",
                        subtitle        = if (biometricAvailable)
                            "Usá tu huella o PIN para entrar más rápido"
                        else
                            "Tu dispositivo no tiene biometría ni PIN configurado",
                        checked         = uiState.biometricEnabled && biometricAvailable,
                        onCheckedChange = { enabled ->
                            if (biometricAvailable) {
                                if (!enabled) {
                                    viewModel.onBiometricDisabled()
                                } else {
                                    coroutineScope.launch {
                                        val cipher = viewModel.prepareBiometricEnrollCipher()
                                        if (cipher != null && activity != null) {
                                            showBiometricPrompt(
                                                activity     = activity,
                                                cryptoObject = BiometricPrompt.CryptoObject(cipher),
                                                title        = "Activar huella",
                                                subtitle     = "Confirmá tu huella para activar el acceso rápido",
                                                onSuccess    = { result ->
                                                    viewModel.onBiometricEnrollConfirmed(result.cryptoObject!!.cipher!!)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    )
                }

```

Nota: `SettingsToggleItem` usa un `Switch` normal de Material3 — el lambda
`onCheckedChange` recibe el valor que el usuario *intenta* poner, no hace falta que el
switch quede deshabilitado visualmente si `!biometricAvailable`: alcanza con que el
`checked` mostrado siempre sea `false` en ese caso y que el `onCheckedChange` no haga
nada cuando `!biometricAvailable` (el `if` envolvente evita ejecutar cualquier lógica
sin necesitar un `return` etiquetado). Esto evita tener que tocar la firma de
`SettingsToggleItem`.

- [ ] **Step 4: Verificar que compila**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/undef/superahorroturina/ui/screens/settings/SettingsScreen.kt
git commit -m "feat: switch de biometria en Ajustes"
```

---

## Verificación final (manual, en dispositivo/emulador)

- [ ] `./gradlew :app:compileDebugKotlin` sin errores (repetir tras la Tarea 6).
- [ ] Login con contraseña → aceptar "¿Activar inicio con huella?" → escanear huella →
      entra a Home. Cerrar la app por completo (no solo minimizar) y reabrir → debe
      ofrecer la huella automáticamente y, al escanear, entrar directo a Home sin pedir
      contraseña.
- [ ] Repetir el login (otra cuenta o tras `onBiometricDisabled`) y esta vez **rechazar**
      el diálogo de activar huella → reabrir la app → no debe ofrecer biometría, debe
      mostrar el formulario de contraseña.
- [ ] Activar el switch en Ajustes después de haber rechazado al loguearse → debe pedir
      un escaneo y dejar la huella activa para la próxima apertura.
- [ ] Desactivar el switch en Ajustes → reabrir la app → no debe ofrecer biometría.
- [ ] Cerrar sesión manualmente con biometría activada → reabrir la app → no debe
      ofrecer biometría (el logout debe haber limpiado todo vía
      `disableBiometricLogin()`).
- [ ] (Si el emulador lo permite) cambiar las huellas enroladas en los ajustes del
      sistema y reabrir la app → debe detectar `KeyPermanentlyInvalidatedException`,
      mostrar el mensaje de invalidación y caer a login con contraseña sin crash.
