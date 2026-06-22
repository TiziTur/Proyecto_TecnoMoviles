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
                BiometricWelcomeCard(
                    savedUserName    = savedUserName,
                    showUnlockButton = activity != null && canUseBiometric(activity),
                    onUnlockClick = {
                        coroutineScope.launch {
                            when (val unlockState = viewModel.prepareUnlockCipher()) {
                                is BiometricUnlockState.Ready -> {
                                    showBiometricPrompt(
                                        activity     = activity!!,
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
                )
            }

            Spacer(Modifier.height(if (hasSavedSession) 16.dp else 48.dp))

            LoginFormCard(
                uiState    = uiState,
                isDark     = isDark,
                onEmailChange    = { viewModel.onEmailChange(it) },
                onPasswordChange = { viewModel.onPasswordChange(it) },
                onTogglePasswordVisibility = { viewModel.onTogglePasswordVisibility() },
                onLogin    = { viewModel.onLogin(onLoginSuccess) }
            )

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
            title = { Text(stringResource(R.string.biometric_disabled_title)) },
            text  = { Text(biometricMessage) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearBiometricMessage() }) { Text(stringResource(R.string.action_understood)) }
            }
        )
    }

    // ── Diálogo de consentimiento para activar huella tras login con contraseña ──
    if (showEnrollDialog && activity != null && canUseBiometric(activity)) {
        AlertDialog(
            onDismissRequest = { viewModel.onEnrollDeclined(onDone = onLoginSuccess) },
            title = { Text(stringResource(R.string.biometric_enroll_title)) },
            text  = { Text(stringResource(R.string.biometric_enroll_message)) },
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
                }) { Text(stringResource(R.string.action_activate)) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onEnrollDeclined(onDone = onLoginSuccess) }) { Text(stringResource(R.string.action_not_now)) }
            }
        )
    }
}

// ── Tarjeta de bienvenida rápida con huella ───────────────────────────────────

@Composable
private fun BiometricWelcomeCard(
    savedUserName: String,
    showUnlockButton: Boolean,
    onUnlockClick: () -> Unit
) {
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
                    text  = stringResource(R.string.login_welcome_back_named, savedUserName),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text  = stringResource(R.string.login_tap_fingerprint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
            if (showUnlockButton) {
                IconButton(onClick = onUnlockClick) {
                    Icon(
                        Icons.Default.Fingerprint,
                        contentDescription = stringResource(R.string.action_authenticate_fingerprint),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

// ── Formulario de email/contraseña ────────────────────────────────────────────

@Composable
private fun LoginFormCard(
    uiState: com.undef.superahorroturina.ui.state.LoginUiState,
    isDark: Boolean,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onLogin: () -> Unit
) {
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
                onValueChange = onEmailChange,
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
                onValueChange = onPasswordChange,
                label = { Text(stringResource(R.string.field_password)) },
                leadingIcon = {
                    Icon(Icons.Default.Lock, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                trailingIcon = {
                    IconButton(onClick = onTogglePasswordVisibility) {
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
                onClick = onLogin,
                loading = uiState.isLoading,
                modifier = Modifier.fillMaxWidth()
            )
        }
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
