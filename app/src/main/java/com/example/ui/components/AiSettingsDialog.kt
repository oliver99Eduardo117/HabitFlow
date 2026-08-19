package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.network.AiChatClient
import com.example.util.AiProviderPreferences
import kotlinx.coroutines.launch

@Composable
fun AiSettingsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val aiPrefs = remember { AiProviderPreferences.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()

    var isEnabled by remember { mutableStateOf(aiPrefs.isEnabled.value) }
    var baseUrl by remember { mutableStateOf(aiPrefs.baseUrl.value) }
    var apiKey by remember { mutableStateOf(aiPrefs.apiKey.value) }
    var modelName by remember { mutableStateOf(aiPrefs.modelName.value) }

    var showApiKeyPassword by remember { mutableStateOf(false) }
    var isTestingConnection by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) } // Pair(isSuccess, message)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.90f)
                .padding(vertical = 16.dp)
                .testTag("ai_settings_dialog"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header with icon and close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SmartToy,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "Proveedor de IA",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Configuración OpenAI Chat Completions",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("ai_settings_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Enable/Disable Switch Card
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isEnabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = CardDefaults.outlinedCardBorder(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isEnabled) Icons.Default.AutoAwesome else Icons.Default.AutoAwesomeMotion,
                                        contentDescription = null,
                                        tint = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text(
                                        text = "Activar Insights con IA",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (isEnabled) "Análisis activo con proveedor personalizado" else "Usa análisis local honesto sin conexión",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Switch(
                                checked = isEnabled,
                                onCheckedChange = { isEnabled = it },
                                modifier = Modifier.testTag("ai_enabled_switch")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Preset Templates Bar
                    Text(
                        text = "Plantillas rápidas de conexión:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PresetChip(
                            label = "Ollama Local",
                            icon = Icons.Default.Computer,
                            onClick = {
                                baseUrl = "http://192.168.1.100:11434/v1"
                                modelName = "llama3.2:latest"
                                testResult = null
                            }
                        )
                        PresetChip(
                            label = "LM Studio",
                            icon = Icons.Default.Dns,
                            onClick = {
                                baseUrl = "http://192.168.1.100:1234/v1"
                                modelName = "local-model"
                                testResult = null
                            }
                        )
                        PresetChip(
                            label = "OpenAI",
                            icon = Icons.Default.Cloud,
                            onClick = {
                                baseUrl = "https://api.openai.com/v1"
                                modelName = "gpt-4o-mini"
                                testResult = null
                            }
                        )
                        PresetChip(
                            label = "Gemini OpenAI API",
                            icon = Icons.Default.Stars,
                            onClick = {
                                baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai/"
                                modelName = "gemini-2.5-flash"
                                testResult = null
                            }
                        )
                        PresetChip(
                            label = "Groq",
                            icon = Icons.Default.Bolt,
                            onClick = {
                                baseUrl = "https://api.groq.com/openai/v1"
                                modelName = "llama-3.3-70b-versatile"
                                testResult = null
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Base URL input
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = {
                            baseUrl = it
                            testResult = null
                        },
                        label = { Text("URL Base del Servidor (Endpoint) *") },
                        placeholder = { Text("https://api.openai.com/v1") },
                        leadingIcon = {
                            Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(20.dp))
                        },
                        trailingIcon = {
                            if (baseUrl.isNotEmpty()) {
                                IconButton(onClick = { baseUrl = ""; testResult = null }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Limpiar", modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        supportingText = {
                            Column(modifier = Modifier.padding(top = 2.dp)) {
                                Text(
                                    text = "Ejemplos reales:",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "• Ollama local: http://TU_IP_LOCAL:11434/v1\n• LM Studio: http://localhost:1234/v1 o http://IP:1234/v1\n• OpenAI: https://api.openai.com/v1\n• Gemini: https://generativelanguage.googleapis.com/v1beta/openai/",
                                    fontSize = 10.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ai_base_url_input"),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // API Key input (Optional for local)
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = {
                            apiKey = it
                            testResult = null
                        },
                        label = { Text("Clave de API (API Key)") },
                        placeholder = { Text("sk-... (Opcional en servidores locales)") },
                        leadingIcon = {
                            Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(20.dp))
                        },
                        trailingIcon = {
                            IconButton(onClick = { showApiKeyPassword = !showApiKeyPassword }) {
                                Icon(
                                    imageVector = if (showApiKeyPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showApiKeyPassword) "Ocultar clave" else "Mostrar clave",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        visualTransformation = if (showApiKeyPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        supportingText = {
                            Text(
                                text = "Opcional. Servidores locales (Ollama, LM Studio, llama.cpp) habitualmente no requieren autenticación.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ai_api_key_input"),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Model Name input
                    OutlinedTextField(
                        value = modelName,
                        onValueChange = {
                            modelName = it
                            testResult = null
                        },
                        label = { Text("Nombre del Modelo (Model) *") },
                        placeholder = { Text("gpt-4o-mini, gemini-2.5-flash, llama3.2:latest...") },
                        leadingIcon = {
                            Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(20.dp))
                        },
                        supportingText = {
                            Text(
                                text = "Identificador exacto configurado en tu servidor o proveedor de IA.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ai_model_name_input"),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Test Connection Button
                    OutlinedButton(
                        onClick = {
                            if (baseUrl.isBlank() || modelName.isBlank()) {
                                testResult = Pair(false, "Debes completar la URL Base y el Nombre del Modelo antes de probar.")
                                return@OutlinedButton
                            }
                            isTestingConnection = true
                            testResult = null
                            coroutineScope.launch {
                                val result = AiChatClient.getChatCompletion(
                                    baseUrl = baseUrl,
                                    apiKey = apiKey,
                                    model = modelName,
                                    systemPrompt = "Eres una prueba de conexión.",
                                    userPrompt = "Responde únicamente con OK"
                                )
                                isTestingConnection = false
                                result.fold(
                                    onSuccess = { responseText ->
                                        testResult = Pair(true, "Conexión exitosa. Respuesta de la IA: \"$responseText\"")
                                    },
                                    onFailure = { error ->
                                        testResult = Pair(false, error.message ?: "Error desconocido de conexión.")
                                    }
                                )
                            }
                        },
                        enabled = !isTestingConnection && baseUrl.isNotBlank() && modelName.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("ai_test_connection_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (isTestingConnection) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Probando conexión...", fontSize = 13.sp)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Sensors,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Probar Conexión", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    // Test Connection Result Box
                    AnimatedVisibility(visible = testResult != null) {
                        testResult?.let { (isSuccess, message) ->
                            val resultColor = if (isSuccess) Color(0xFF10B981) else MaterialTheme.colorScheme.error
                            val resultBg = if (isSuccess) Color(0xFF10B981).copy(alpha = 0.12f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)

                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = resultBg,
                                border = BorderStroke(1.dp, resultColor.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        tint = resultColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (isSuccess) "Servidor Respondiendo" else "Fallo de Comunicación",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = resultColor
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = message,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("ai_cancel_button")
                    ) {
                        Text("Cancelar")
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Button(
                        onClick = {
                            aiPrefs.saveConfiguration(
                                enabled = isEnabled,
                                baseUrl = baseUrl,
                                apiKey = apiKey,
                                modelName = modelName
                            )
                            onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("ai_save_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Guardar", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
