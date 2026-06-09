package com.undef.superahorroturina.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.undef.superahorroturina.R
import com.undef.superahorroturina.ui.components.AppTopBar
import com.undef.superahorroturina.ui.components.GradientDivider
import com.undef.superahorroturina.ui.components.KlarityButton
import com.undef.superahorroturina.ui.components.coloredShadow
import com.undef.superahorroturina.ui.components.dotPatternBackground
import com.undef.superahorroturina.ui.components.glowBorder

@Composable
fun ProfileScreen(
    onNavigateBack: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isDark   = isSystemInDarkTheme()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title    = stringResource(R.string.screen_profile),
                showBack = true,
                onBack   = onNavigateBack,
                actions  = {
                    IconButton(onClick = { viewModel.onToggleEditing() }) {
                        Icon(
                            imageVector = if (uiState.isEditing) Icons.Default.Check
                                          else Icons.Default.Edit,
                            contentDescription = stringResource(R.string.action_edit)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
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
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .coloredShadow(
                            color        = MaterialTheme.colorScheme.primary,
                            borderRadius = 24.dp,
                            blurRadius   = 20.dp,
                            offsetY      = 6.dp
                        )
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary
                                )
                            )
                        )
                ) {
                    Column(
                        modifier            = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val initials = buildString {
                            if (uiState.firstName.isNotEmpty()) append(uiState.firstName.first().uppercaseChar())
                            if (uiState.lastName.isNotEmpty())  append(uiState.lastName.first().uppercaseChar())
                        }
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text       = initials,
                                style      = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color      = Color.White
                            )
                        }
                        Text(
                            text       = "${uiState.firstName} ${uiState.lastName}",
                            style      = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color      = Color.White
                        )
                        Text(
                            text  = uiState.email,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.75f)
                        )
                        if (uiState.isEditing) {
                            TextButton(
                                onClick = { /* TODO: intent galería */ },
                                colors  = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = 0.85f))
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null,
                                    modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.profile_change_photo))
                            }
                        }
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .coloredShadow(
                            color        = MaterialTheme.colorScheme.secondary,
                            borderRadius = 20.dp,
                            blurRadius   = 12.dp,
                            offsetY      = 3.dp
                        )
                        .glowBorder(cornerRadius = 20.dp, isDark = isDark),
                    shape     = RoundedCornerShape(20.dp),
                    colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        GradientDivider(color = MaterialTheme.colorScheme.primary)

                        OutlinedTextField(
                            value          = uiState.firstName,
                            onValueChange  = { viewModel.onFirstNameChange(it) },
                            label          = { Text(stringResource(R.string.field_first_name)) },
                            leadingIcon    = { Icon(Icons.Default.Person, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                            enabled        = uiState.isEditing,
                            modifier       = Modifier.fillMaxWidth(),
                            singleLine     = true,
                            shape          = MaterialTheme.shapes.medium
                        )
                        OutlinedTextField(
                            value          = uiState.lastName,
                            onValueChange  = { viewModel.onLastNameChange(it) },
                            label          = { Text(stringResource(R.string.field_last_name)) },
                            leadingIcon    = { Icon(Icons.Default.Person, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                            enabled        = uiState.isEditing,
                            modifier       = Modifier.fillMaxWidth(),
                            singleLine     = true,
                            shape          = MaterialTheme.shapes.medium
                        )
                        OutlinedTextField(
                            value          = uiState.email,
                            onValueChange  = { viewModel.onEmailChange(it) },
                            label          = { Text(stringResource(R.string.field_email)) },
                            leadingIcon    = { Icon(Icons.Default.Email, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            enabled        = uiState.isEditing,
                            modifier       = Modifier.fillMaxWidth(),
                            singleLine     = true,
                            shape          = MaterialTheme.shapes.medium
                        )
                        OutlinedTextField(
                            value          = uiState.phone,
                            onValueChange  = { viewModel.onPhoneChange(it) },
                            label          = { Text(stringResource(R.string.field_phone)) },
                            leadingIcon    = { Icon(Icons.Default.Phone, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            enabled        = uiState.isEditing,
                            modifier       = Modifier.fillMaxWidth(),
                            singleLine     = true,
                            shape          = MaterialTheme.shapes.medium
                        )

                        if (uiState.isEditing) {
                            KlarityButton(
                                text     = stringResource(R.string.action_save),
                                onClick  = { viewModel.onSave() },
                                loading  = uiState.isSaving,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
