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
