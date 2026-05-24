// BiometricHelper.kt — Encapsula la lógica de BiometricPrompt de AndroidX.
// Muestra el diálogo nativo del sistema para autenticación con huella dactilar
// o credenciales de dispositivo (PIN/patrón) como fallback.
// Se usa desde LoginScreen cuando el usuario ya tiene sesión guardada.
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
 * Lanza el diálogo biométrico del sistema.
 * @param activity  La Activity host (necesaria para FragmentManager)
 * @param title     Título del diálogo
 * @param subtitle  Subtítulo del diálogo
 * @param onSuccess Callback al autenticarse correctamente
 * @param onError   Callback al cancelar o fallar (recibe mensaje de error)
 */
fun showBiometricPrompt(
    activity: FragmentActivity,
    title: String = "Desbloquear Klarity",
    subtitle: String = "Usá tu huella dactilar para continuar",
    onSuccess: () -> Unit,
    onError: (String) -> Unit = {}
) {
    val executor = ContextCompat.getMainExecutor(activity)

    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            super.onAuthenticationSucceeded(result)
            onSuccess()
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

    prompt.authenticate(promptInfo)
}
