package com.undef.superahorroturina.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.undef.superahorroturina.R
import com.undef.superahorroturina.ui.components.KlarityButton
import com.undef.superahorroturina.ui.components.KlarityLogoIcon
import com.undef.superahorroturina.ui.theme.SuperAhorroTheme

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit
) {
    var email      by remember { mutableStateOf("") }
    var password   by remember { mutableStateOf("") }
    var showPass   by remember { mutableStateOf(false) }
    var emailError by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(72.dp))

            // Logo + nombre
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

            Spacer(Modifier.height(48.dp))

            // Card del formulario
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp, MaterialTheme.colorScheme.outline
                )
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
                        value = email,
                        onValueChange = { email = it; emailError = false },
                        label = { Text(stringResource(R.string.field_email)) },
                        leadingIcon = {
                            Icon(Icons.Default.Email, contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        isError = emailError,
                        supportingText = if (emailError) {
                            { Text(stringResource(R.string.error_email)) }
                        } else null,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.field_password)) },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        trailingIcon = {
                            IconButton(onClick = { showPass = !showPass }) {
                                Icon(
                                    imageVector = if (showPass) Icons.Default.VisibilityOff
                                                  else Icons.Default.Visibility,
                                    contentDescription = null
                                )
                            }
                        },
                        visualTransformation = if (showPass) VisualTransformation.None
                                               else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )

                    TextButton(
                        onClick = { /* TODO */ },
                        modifier = Modifier.align(Alignment.End),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.login_forgot_password),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    KlarityButton(
                        text = stringResource(R.string.action_login),
                        onClick = {
                            if (email.isBlank()) { emailError = true; return@KlarityButton }
                            onLoginSuccess()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Registro
            Row(
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
}

// ── Preview ───────────────────────────────────────────────────

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
