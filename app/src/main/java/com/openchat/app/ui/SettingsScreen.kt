package com.openchat.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openchat.app.AppUiState
import com.openchat.app.BuildConfig
import com.openchat.app.ChatViewModel
import com.openchat.app.data.ThemeMode
import com.openchat.app.ui.components.ScreenTopBar
import com.openchat.app.ui.theme.LocalChatColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    uiState: AppUiState,
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onOpenProviders: () -> Unit,
    onOpenModels: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = uiState.data.settings
    val chatColors = LocalChatColors.current
    val providersCount = uiState.data.providers.size
    val enabledModels = uiState.data.models.count { model ->
        model.enabled && uiState.data.providers.any { it.id == model.providerId }
    }
    val chatsCount = uiState.data.conversations.size

    var showSystemPrompt by remember { mutableStateOf(false) }
    var showMaxTokens by remember { mutableStateOf(false) }
    var showClearChats by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val saved = runCatching {
                    val content = viewModel.exportChatsJson()
                    withContext(Dispatchers.IO) {
                        val stream = context.contentResolver.openOutputStream(uri)
                            ?: error("Could not open the selected file.")
                        stream.use { it.write(content.toByteArray(Charsets.UTF_8)) }
                    }
                }.isSuccess
                viewModel.notify(if (saved) "Chats exported." else "Could not save the export.")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
    ) {
        ScreenTopBar(title = "Settings", onBack = onBack)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "providers-header") {
                SectionHeader("Your AI")
            }
            item(key = "providers") {
                SettingsCard {
                    SettingsActionRow(
                        icon = Icons.Outlined.Cloud,
                        title = "Providers and API keys",
                        subtitle = "$providersCount connected",
                        onClick = onOpenProviders
                    )
                    GroupDivider()
                    SettingsActionRow(
                        icon = Icons.Outlined.Tune,
                        title = "Manage models",
                        subtitle = "$enabledModels enabled for chat",
                        onClick = onOpenModels
                    )
                }
            }

            item(key = "behavior-header") {
                SectionHeader("Chat behavior")
            }
            item(key = "behavior") {
                SettingsCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "Temperature",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Lower is focused, higher is creative",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = chatColors.subtleText
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = String.format(Locale.US, "%.1f", settings.temperature),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        TemperatureSlider(
                            initial = settings.temperature.toFloat(),
                            onChangeFinished = { viewModel.setTemperature(it.toDouble()) }
                        )
                    }
                    GroupDivider()
                    Column {
                        SettingsActionRow(
                            icon = Icons.Outlined.Tune,
                            title = "Custom instructions",
                            subtitle = if (settings.systemPrompt.isBlank()) {
                                "None set"
                            } else {
                                settings.systemPrompt.replace('\n', ' ')
                            },
                            onClick = { showSystemPrompt = true }
                        )
                        GroupDivider()
                        SettingsActionRow(
                            icon = Icons.Outlined.Tune,
                            title = "Maximum reply length",
                            subtitle = settings.maxTokens?.let { "$it tokens" } ?: "Automatic",
                            onClick = { showMaxTokens = true }
                        )
                        GroupDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Cloud,
                                contentDescription = null,
                                tint = chatColors.subtleText,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "Stream replies",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Show words as they arrive",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = chatColors.subtleText
                                )
                            }
                            Switch(
                                checked = settings.stream,
                                onCheckedChange = { viewModel.setStreaming(it) }
                            )
                        }
                    }
                }
            }

            item(key = "appearance-header") {
                SectionHeader("Appearance")
            }
            item(key = "appearance") {
                SettingsCard {
                    ThemeRow(
                        label = "Follow device",
                        description = "Switch with your phone's dark mode",
                        selected = settings.theme == ThemeMode.SYSTEM,
                        onClick = { viewModel.setTheme(ThemeMode.SYSTEM) },
                        icon = Icons.Outlined.Palette
                    )
                    GroupDivider()
                    ThemeRow(
                        label = "Light",
                        description = "Always use the light theme",
                        selected = settings.theme == ThemeMode.LIGHT,
                        onClick = { viewModel.setTheme(ThemeMode.LIGHT) },
                        icon = Icons.Outlined.Palette
                    )
                    GroupDivider()
                    ThemeRow(
                        label = "Dark",
                        description = "Always use the dark theme",
                        selected = settings.theme == ThemeMode.DARK,
                        onClick = { viewModel.setTheme(ThemeMode.DARK) },
                        icon = Icons.Outlined.Palette
                    )
                }
            }

            item(key = "data-header") {
                SectionHeader("Your data")
            }
            item(key = "data") {
                SettingsCard {
                    SettingsActionRow(
                        icon = Icons.Outlined.FileDownload,
                        title = "Export chats",
                        subtitle = "Save every conversation as JSON (no API keys)",
                        showChevron = false,
                        onClick = {
                            if (chatsCount == 0) {
                                viewModel.notify("There are no chats to export.")
                            } else {
                                val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                                    .format(Date())
                                exportLauncher.launch("openchat-chats-$stamp.json")
                            }
                        }
                    )
                    GroupDivider()
                    SettingsActionRow(
                        icon = Icons.Outlined.Delete,
                        title = "Clear all chats",
                        subtitle = if (chatsCount == 0) {
                            "No saved conversations"
                        } else {
                            "$chatsCount saved conversation${if (chatsCount == 1) "" else "s"}"
                        },
                        showChevron = false,
                        danger = true,
                        enabled = chatsCount > 0,
                        onClick = { showClearChats = true }
                    )
                }
            }

            item(key = "privacy-header") {
                SectionHeader("Privacy and security")
            }
            item(key = "privacy") {
                SettingsCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null,
                                tint = chatColors.subtleText,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(14.dp))
                            Text(
                                text = "How your data is handled",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "API keys, chats and settings are encrypted on this device with the Android Keystore. " +
                                "Requests go directly from your phone to the provider you choose. " +
                                "OpenChat has no server and no analytics.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = chatColors.subtleText
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Only use Base URLs you trust. Local HTTP providers are allowed on this app, " +
                                "so avoid sending private data over an untrusted network.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = chatColors.subtleText
                        )
                    }
                }
            }

            item(key = "about-header") {
                SectionHeader("About")
            }
            item(key = "about") {
                SettingsCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "OpenChat ${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "A bring-your-own-key chat client for any OpenAI-compatible provider.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = chatColors.subtleText
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Not affiliated with OpenAI. ChatGPT is a trademark of OpenAI. " +
                                "Model names and responses belong to their respective providers.",
                            style = MaterialTheme.typography.bodySmall,
                            color = chatColors.subtleText
                        )
                    }
                }
            }
        }
    }

    if (showSystemPrompt) {
        var prompt by remember(settings.systemPrompt) {
            mutableStateOf(settings.systemPrompt)
        }
        AlertDialog(
            onDismissRequest = { showSystemPrompt = false },
            title = { Text("Custom instructions") },
            text = {
                Column {
                    Text(
                        text = "These instructions are sent with every new request to guide the assistant's tone and behavior.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalChatColors.current.subtleText
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Instructions") },
                        placeholder = { Text("Be concise and helpful.") },
                        minLines = 4,
                        maxLines = 8
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setSystemPrompt(prompt)
                        showSystemPrompt = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSystemPrompt = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showMaxTokens) {
        var tokens by remember(settings.maxTokens) {
            mutableStateOf(settings.maxTokens?.toString().orEmpty())
        }
        AlertDialog(
            onDismissRequest = { showMaxTokens = false },
            title = { Text("Maximum reply length") },
            text = {
                Column {
                    Text(
                        text = "Limit how many tokens a reply can contain. Leave it empty for the provider's automatic limit.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalChatColors.current.subtleText
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = tokens,
                        onValueChange = { value ->
                            tokens = value.filter { it.isDigit() }.take(6)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Max tokens") },
                        placeholder = { Text("Automatic") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val parsed = if (tokens.isBlank()) null else tokens.toIntOrNull()
                        if (tokens.isNotBlank() && (parsed == null || parsed <= 0)) {
                            viewModel.notify("Enter a positive number or leave the field empty.")
                        } else {
                            viewModel.setMaxTokens(parsed)
                            showMaxTokens = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMaxTokens = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showClearChats) {
        AlertDialog(
            onDismissRequest = { showClearChats = false },
            title = { Text("Clear all chats?") },
            text = {
                Text("Every conversation will be permanently removed from this device. Providers, API keys and models are not affected.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllConversations()
                        showClearChats = false
                        viewModel.notify("All chats were cleared.")
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = chatColors.danger
                    )
                ) {
                    Text("Clear all")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearChats = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 2.dp),
        style = MaterialTheme.typography.labelMedium,
        color = LocalChatColors.current.subtleText
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(content = content)
    }
}

@Composable
private fun GroupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 50.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
    )
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    showChevron: Boolean = true,
    danger: Boolean = false,
    enabled: Boolean = true
) {
    val chatColors = LocalChatColors.current
    val tint = when {
        !enabled -> chatColors.subtleText.copy(alpha = 0.5f)
        danger -> chatColors.danger
        else -> chatColors.subtleText
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (danger && enabled) {
                    chatColors.danger
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(
                        alpha = if (enabled) 1f else 0.5f
                    )
                }
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) chatColors.subtleText else chatColors.subtleText.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (showChevron) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = chatColors.subtleText,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Spacer(Modifier.width(4.dp))
        }
    }
}

@Composable
private fun ThemeRow(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                LocalChatColors.current.subtleText
            },
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = LocalChatColors.current.subtleText
            )
        }
        RadioButton(selected = selected, onClick = onClick)
    }
}

@Composable
private fun TemperatureSlider(
    initial: Float,
    onChangeFinished: (Float) -> Unit
) {
    var value by remember(initial) { mutableStateOf(initial) }
    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = { onChangeFinished(value) },
            valueRange = 0f..2f,
            steps = 19
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Precise",
                style = MaterialTheme.typography.labelSmall,
                color = LocalChatColors.current.subtleText
            )
            Text(
                text = "Balanced",
                style = MaterialTheme.typography.labelSmall,
                color = LocalChatColors.current.subtleText
            )
            Text(
                text = "Creative",
                style = MaterialTheme.typography.labelSmall,
                color = LocalChatColors.current.subtleText
            )
        }
    }
}
