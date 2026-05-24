// ViewModel para el chat IA con Gemini.
// Mantiene el historial de conversación en memoria durante la sesión.
package com.undef.superahorroturina.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.undef.superahorroturina.data.local.SessionDataStore
import com.undef.superahorroturina.data.network.ApiService
import com.undef.superahorroturina.data.network.dto.ChatMessage
import com.undef.superahorroturina.data.network.dto.ChatRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiMessage(
    val text: String,
    val isUser: Boolean,
    val isLoading: Boolean = false
)

data class ChatUiState(
    val messages: List<ChatUiMessage> = listOf(
        ChatUiMessage(
            text = "¡Hola! Soy tu asistente financiero de Klarity 🧾\n\nPodés preguntarme cosas como:\n• ¿Cuánto gasté este mes?\n• ¿Cuál es mi supermercado más caro?\n• ¿En qué gasto más?",
            isUser = false
        )
    ),
    val inputText: String = "",
    val isSending: Boolean = false,
    val error: String = ""
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val api: ApiService,
    private val session: SessionDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    fun onInputChange(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank() || _uiState.value.isSending) return

        // Agregar mensaje del usuario a la lista
        val userMsg = ChatUiMessage(text = text, isUser = true)
        val loadingMsg = ChatUiMessage(text = "...", isUser = false, isLoading = true)
        _uiState.value = _uiState.value.copy(
            messages  = _uiState.value.messages + userMsg + loadingMsg,
            inputText = "",
            isSending = true,
            error     = ""
        )

        viewModelScope.launch {
            try {
                val token = session.bearerToken.first()

                // Construir historial para el backend (excluir el mensaje de bienvenida inicial y el loading)
                val history = _uiState.value.messages
                    .filter { !it.isLoading }
                    .drop(1) // saltar el mensaje de bienvenida del sistema
                    .dropLast(1) // saltar el mensaje del usuario que acabamos de agregar
                    .map { ChatMessage(role = if (it.isUser) "user" else "model", text = it.text) }

                val response = api.chat(token, ChatRequest(message = text, history = history))

                val reply = if (response.isSuccessful) {
                    response.body()?.reply ?: "Sin respuesta"
                } else {
                    "Error al conectar con el asistente (${response.code()})"
                }

                // Reemplazar el loading con la respuesta real
                val updatedMessages = _uiState.value.messages
                    .filter { !it.isLoading } + ChatUiMessage(text = reply, isUser = false)

                _uiState.value = _uiState.value.copy(
                    messages  = updatedMessages,
                    isSending = false
                )
            } catch (e: Exception) {
                val updatedMessages = _uiState.value.messages.filter { !it.isLoading } +
                    ChatUiMessage(text = "Error de conexión: ${e.message}", isUser = false)
                _uiState.value = _uiState.value.copy(
                    messages  = updatedMessages,
                    isSending = false,
                    error     = e.message ?: "Error"
                )
            }
        }
    }
}
