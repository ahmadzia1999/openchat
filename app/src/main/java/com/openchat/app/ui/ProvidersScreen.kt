package com.openchat.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openchat.app.AppUiState
import com.openchat.app.ChatViewModel
import com.openchat.app.data.Provider
import com.openchat.app.ui.components.ScreenTopBar
import com.openchat.app.ui.theme.LocalChatColors

@Composable
fun ProvidersScreen(
    uiState: AppUiState,
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onManageModels: (String) -> Unit
) {
    val providers = uiState.data.providers
    var editorOpen by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<Provider?>(null) }

    fun openEditor(providerId: String?) {
        editingId = providerId
        editorOpen = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
    ) {
        ScreenTopBar(
            title = "Providers",
            onBack = onBack,
            actions = {
                IconButton(onClick = { openEditor(null) }) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add provider")
                }
            }
        )

        if (providers.isEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                item(key = "empty-providers") {
                    EmptyProviders(
                        onAdd = { openEditor(null) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "intro") {
                    Text(
                        text = "Connect any service that speaks the OpenAI API. Each provider keeps its own Base URL and API key on this device.",
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalChatColors.current.subtleText
                    )
                }

                items(items = providers, key = { it.id }) { provider ->
                    ProviderCard(
                        provider = provider,
                        modelCount = uiState.data.models.count { it.providerId == provider.id },
                        enabledCount = uiState.data.models.count {
                            it.providerId == provider.id && it.enabled
                        },
                        isFetching = provider.id in uiState.fetchingProviderIds,
                        onClick = { onManageModels(provider.id) },
                        onEdit = { openEditor(provider.id) },
                        onDelete = { deleteTarget = provider }
                    )
                }

                item(key = "add-another") {
                    Button(
                        onClick = { openEditor(null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        contentPadding = PaddingValues(vertical = 13.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Add another provider")
                    }
                }

                item(key = "help") {
                    ProviderHelpCard()
                }
            }
        }
    }

    if (editorOpen) {
        val existing = editingId?.let { id -> providers.firstOrNull { it.id == id } }
        ProviderEditorDialog(
            existing = existing,
            onDismiss = { editorOpen = false },
            onSave = { name, baseUrl, apiKey ->
                val isNew = existing == null
                val savedId = viewModel.saveProvider(existing?.id, name, baseUrl, apiKey)
                if (savedId != null) {
                    editorOpen = false
                    if (isNew) {
                        onManageModels(savedId)
                    }
                }
            }
        )
    }

    deleteTarget?.let { provider ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Remove ${provider.name}?") },
            text = {
                Text(
                    "Its API key and model list will be deleted from this device. " +
                        "Existing conversations are kept, but replies from this provider cannot continue until you connect it again."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteProvider(provider.id)
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = LocalChatColors.current.danger
                    )
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ProviderCard(
    provider: Provider,
    modelCount: Int,
    enabledCount: Int,
    isFetching: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalChatColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(start = 16.dp, end = 4.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = provider.name.trim().take(1).uppercase().ifBlank { "P" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = provider.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = provider.baseUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.subtleText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = buildString {
                        append("$modelCount model")
                        if (modelCount != 1) append("s")
                        append(" • ")
                        append("$enabledCount enabled")
                        append(" • ")
                        append(
                            when {
                                isFetching -> "Fetching…"
                                provider.apiKey.isBlank() -> "No API key"
                                else -> "Key saved"
                            }
                        )
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isFetching) MaterialTheme.colorScheme.primary else colors.subtleText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = "Edit ${provider.name}",
                    tint = colors.subtleText,
                    modifier = Modifier.size(19.dp)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Delete ${provider.name}",
                    tint = colors.subtleText,
                    modifier = Modifier.size(19.dp)
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.subtleText,
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(20.dp)
            )
        }
    }
}

@Composable
private fun EmptyProviders(
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalChatColors.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(30.dp)
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = "Add your first provider",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Enter a Base URL and API key for OpenAI, OpenRouter, Groq, " +
                "Ollama, LM Studio or any other OpenAI-compatible service.",
            modifier = Modifier.padding(horizontal = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.subtleText
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onAdd) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text("Add provider")
        }
        Spacer(Modifier.height(28.dp))
        ProviderHelpCard()
    }
}

@Composable
private fun ProviderHelpCard() {
    val colors = LocalChatColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = colors.subtleText,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Common Base URLs",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(10.dp))
            HelpEndpoint("OpenAI", "https://api.openai.com/v1")
            HelpEndpoint("OpenRouter", "https://openrouter.ai/api/v1")
            HelpEndpoint("Groq", "https://api.groq.com/openai/v1")
            HelpEndpoint("Ollama", "http://<computer-ip>:11434/v1")
            HelpEndpoint("LM Studio", "http://<computer-ip>:1234/v1")
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Text(
                text = "The Base URL is the part before /chat/completions. For local servers, " +
                    "your phone and computer must be on the same Wi-Fi network. " +
                    "Ollama may need OLLAMA_HOST=0.0.0.0 to accept connections from your phone.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.subtleText
            )
        }
    }
}

@Composable
private fun HelpEndpoint(label: String, url: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.width(92.dp),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = url,
            style = MaterialTheme.typography.bodySmall,
            color = LocalChatColors.current.subtleText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ProviderEditorDialog(
    existing: Provider?,
    onDismiss: () -> Unit,
    onSave: (name: String, baseUrl: String, apiKey: String) -> Unit
) {
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var baseUrl by remember(existing?.id) { mutableStateOf(existing?.baseUrl.orEmpty()) }
    var apiKey by remember(existing?.id) { mutableStateOf(existing?.apiKey.orEmpty()) }
    var showKey by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (existing == null) "Add provider" else "Edit provider")
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Provider name") },
                    placeholder = { Text("OpenAI") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Base URL") },
                    placeholder = { Text("https://api.openai.com/v1") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next
                    )
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API key") },
                    placeholder = { Text("sk-… (optional for local servers)") },
                    singleLine = true,
                    visualTransformation = if (showKey) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                imageVector = if (showKey) {
                                    Icons.Outlined.VisibilityOff
                                } else {
                                    Icons.Outlined.Visibility
                                },
                                contentDescription = if (showKey) "Hide API key" else "Show API key"
                            )
                        }
                    }
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Requests go to <Base URL>/models and <Base URL>/chat/completions. " +
                        "The key is stored encrypted on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalChatColors.current.subtleText
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, baseUrl, apiKey) },
                enabled = name.isNotBlank() && baseUrl.isNotBlank()
            ) {
                Text(if (existing == null) "Add" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
