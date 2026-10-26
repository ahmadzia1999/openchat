package com.openchat.app.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.openchat.app.AppUiState
import com.openchat.app.ChatViewModel
import com.openchat.app.data.Attachment
import com.openchat.app.data.ChatMessage
import com.openchat.app.data.ChatRole
import com.openchat.app.data.MessageStatus
import com.openchat.app.data.ModelOption
import com.openchat.app.ui.components.AppLogo
import com.openchat.app.ui.components.Base64Image
import com.openchat.app.ui.components.MarkdownText
import com.openchat.app.ui.components.TypingCursor
import com.openchat.app.ui.components.TypingDots
import com.openchat.app.ui.theme.LocalChatColors
import com.openchat.app.util.ImageUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private val SUGGESTIONS = listOf(
    "Explain a hard idea in simple words",
    "Draft a friendly email for me",
    "Plan a productive three-day trip",
    "Help me debug a piece of code"
)

@Composable
fun ChatScreen(
    uiState: AppUiState,
    viewModel: ChatViewModel,
    onOpenDrawer: () -> Unit,
    onManageProviders: () -> Unit,
    onManageModels: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activeId = uiState.activeConversationId
    val messages = uiState.activeConversation?.messages.orEmpty()
    val selection = uiState.resolveSelection()
    val hasModel = selection != null
    val isStreamingHere = activeId != null && uiState.streamingConversationId == activeId
    val isBusyElsewhere = uiState.isStreaming && !isStreamingHere

    var input by rememberSaveable(activeId) { mutableStateOf("") }
    var attachments by remember(activeId) { mutableStateOf<List<Attachment>>(emptyList()) }
    var isPreparingAttachment by remember { mutableStateOf(false) }
    var showModelPicker by remember { mutableStateOf(false) }
    var editingMessage by remember { mutableStateOf<ChatMessage?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(4)
    ) { uris ->
        if (uris.isNotEmpty()) {
            val remaining = 4 - attachments.size
            if (remaining <= 0) {
                viewModel.notify("You can attach up to 4 images.")
            } else {
                scope.launch {
                    isPreparingAttachment = true
                    var failed = false
                    val loaded = mutableListOf<Attachment>()
                    uris.take(remaining).forEachIndexed { index, uri ->
                        try {
                            loaded.add(
                                ImageUtils.loadAttachment(
                                    context = context,
                                    uri = uri,
                                    index = attachments.size + index
                                )
                            )
                        } catch (_: Exception) {
                            failed = true
                        }
                    }
                    attachments = (attachments + loaded).take(4)
                    if (failed) viewModel.notify("Some images could not be added.")
                    isPreparingAttachment = false
                }
            }
        }
    }

    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                input = listOf(input.trim(), spoken.trim())
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
            }
        }
    }

    val startVoiceInput: () -> Unit = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your message")
        }
        try {
            voiceLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            viewModel.notify("Voice input is not available on this device.")
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startVoiceInput()
        } else {
            viewModel.notify("Microphone permission is required for voice input.")
        }
    }

    fun submitMessage() {
        if (uiState.isStreaming || isPreparingAttachment || !hasModel) return
        if (input.isBlank() && attachments.isEmpty()) return
        viewModel.sendMessage(input, attachments)
        input = ""
        attachments = emptyList()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ChatTopBar(
            modelLabel = uiState.currentModelLabel(),
            onOpenDrawer = onOpenDrawer,
            onOpenModelPicker = { showModelPicker = true },
            onNewChat = {
                if (uiState.isStreaming) viewModel.stopStreaming()
                viewModel.startNewChat()
            }
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (messages.isEmpty()) {
                EmptyChat(
                    hasProvider = uiState.data.providers.isNotEmpty(),
                    hasModel = hasModel,
                    onManageProviders = onManageProviders,
                    onManageModels = onManageModels,
                    onSuggestion = { input = it }
                )
            } else {
                MessageList(
                    messages = messages,
                    activeConversationId = activeId,
                    isStreamingHere = isStreamingHere,
                    isAnyStreaming = uiState.isStreaming,
                    onEditMessage = { editingMessage = it },
                    onRegenerate = { viewModel.regenerateResponse(it) },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        if (isBusyElsewhere) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Generating in another chat…",
                    modifier = Modifier.weight(1f),
                    color = LocalChatColors.current.subtleText,
                    style = MaterialTheme.typography.labelMedium
                )
                TextButton(onClick = viewModel::stopStreaming) {
                    Text("Stop")
                }
            }
        }

        ChatComposer(
            text = input,
            onTextChange = { input = it },
            attachments = attachments,
            onRemoveAttachment = { index ->
                attachments = attachments.filterIndexed { itemIndex, _ -> itemIndex != index }
            },
            isPreparingAttachment = isPreparingAttachment,
            hasModel = hasModel,
            isStreamingHere = isStreamingHere,
            isBusyElsewhere = isBusyElsewhere,
            onSend = { submitMessage() },
            onStop = viewModel::stopStreaming,
            onAttach = {
                if (attachments.size >= 4) {
                    viewModel.notify("You can attach up to 4 images.")
                } else {
                    imagePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            },
            onMic = {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) {
                    startVoiceInput()
                } else {
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        )
    }

    if (showModelPicker) {
        ModelPickerSheet(
            state = uiState,
            onDismiss = { showModelPicker = false },
            onSelect = { providerId, modelId ->
                viewModel.selectModel(providerId, modelId)
                showModelPicker = false
            },
            onManageModels = {
                showModelPicker = false
                onManageModels()
            }
        )
    }

    val editing = editingMessage
    if (editing != null) {
        EditMessageDialog(
            message = editing,
            onDismiss = { editingMessage = null },
            onSave = { newText ->
                editingMessage = null
                viewModel.editMessage(editing.id, newText)
            }
        )
    }
}

@Composable
private fun ChatTopBar(
    modelLabel: String,
    onOpenDrawer: () -> Unit,
    onOpenModelPicker: () -> Unit,
    onNewChat: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(58.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onOpenDrawer) {
                Icon(
                    imageVector = Icons.Outlined.Menu,
                    contentDescription = "Open chats"
                )
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onOpenModelPicker)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = modelLabel,
                    modifier = Modifier.weight(1f, fill = false),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(3.dp))
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowDown,
                    contentDescription = "Choose model",
                    modifier = Modifier.size(19.dp),
                    tint = LocalChatColors.current.subtleText
                )
            }
            IconButton(onClick = onNewChat) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "New chat"
                )
            }
        }
    }
}

@Composable
private fun EmptyChat(
    hasProvider: Boolean,
    hasModel: Boolean,
    onManageProviders: () -> Unit,
    onManageModels: () -> Unit,
    onSuggestion: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))
        AppLogo(size = 48.dp)
        Spacer(Modifier.height(20.dp))
        Text(
            text = "What can I help with?",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )

        if (!hasModel) {
            Spacer(Modifier.height(18.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = LocalChatColors.current.suggestionCard,
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        text = if (hasProvider) "Choose a model" else "Connect your AI provider",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(
                        text = if (hasProvider) {
                            "Fetch the models offered by your provider, test one, then enable it for chat."
                        } else {
                            "OpenChat works with any OpenAI-compatible API. Add your Base URL and API key, then choose a model."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalChatColors.current.subtleText
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = if (hasProvider) onManageModels else onManageProviders
                    ) {
                        Text(if (hasProvider) "Choose a model" else "Add a provider")
                    }
                }
            }
        } else {
            Spacer(Modifier.height(26.dp))
            SUGGESTIONS.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowItems.forEach { suggestion ->
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSuggestion(suggestion) },
                            color = LocalChatColors.current.suggestionCard,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                text = suggestion,
                                modifier = Modifier.padding(14.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                minLines = 2
                            )
                        }
                    }
                    if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MessageList(
    messages: List<ChatMessage>,
    activeConversationId: String?,
    isStreamingHere: Boolean,
    isAnyStreaming: Boolean,
    onEditMessage: (ChatMessage) -> Unit,
    onRegenerate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var autoFollow by remember(activeConversationId) { mutableStateOf(true) }

    val isAtLatest by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 &&
                listState.firstVisibleItemScrollOffset < 10
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            val atLatest = listState.firstVisibleItemIndex == 0 &&
                listState.firstVisibleItemScrollOffset < 10
            listState.isScrollInProgress to atLatest
        }.collect { (scrolling, atLatest) ->
            if (!scrolling) autoFollow = atLatest
        }
    }

    LaunchedEffect(isStreamingHere) {
        if (isStreamingHere) {
            autoFollow = true
            if (messages.isNotEmpty()) listState.scrollToItem(0)
        }
    }

    LaunchedEffect(
        activeConversationId,
        messages.size,
        messages.lastOrNull()?.content?.length,
        messages.lastOrNull()?.reasoning?.length
    ) {
        if (autoFollow && messages.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    Box(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            reverseLayout = true,
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 10.dp,
                bottom = 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            items(
                items = messages.asReversed(),
                key = { it.id }
            ) { message ->
                MessageRow(
                    message = message,
                    isAnyStreaming = isAnyStreaming,
                    onEdit = { onEditMessage(message) },
                    onRegenerate = { onRegenerate(message.id) }
                )
            }
        }

        if (!isAtLatest) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .size(36.dp)
                    .clickable {
                        autoFollow = true
                        scope.launch { listState.animateScrollToItem(0) }
                    },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 3.dp,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.KeyboardArrowDown,
                        contentDescription = "Scroll to latest message",
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageRow(
    message: ChatMessage,
    isAnyStreaming: Boolean,
    onEdit: () -> Unit,
    onRegenerate: () -> Unit
) {
    if (message.role == ChatRole.USER) {
        UserMessage(message = message, onEdit = onEdit)
    } else {
        AssistantMessage(
            message = message,
            isAnyStreaming = isAnyStreaming,
            onRegenerate = onRegenerate
        )
    }
}

@Composable
private fun UserMessage(
    message: ChatMessage,
    onEdit: () -> Unit
) {
    val colors = LocalChatColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.End
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.widthIn(max = 360.dp)
        ) {
            Surface(
                color = colors.userBubble,
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (message.attachments.isNotEmpty()) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            message.attachments.forEachIndexed { index, attachment ->
                                val size = if (message.attachments.size == 1) 180.dp else 110.dp
                                Base64Image(
                                    base64 = attachment.base64,
                                    contentDescription = attachment.name,
                                    modifier = Modifier
                                        .size(size)
                                        .clip(RoundedCornerShape(13.dp))
                                )
                            }
                        }
                    }
                    if (message.content.isNotBlank()) {
                        SelectionContainer {
                            Text(
                                text = message.content,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.padding(top = 1.dp),
                horizontalArrangement = Arrangement.End
            ) {
                CopyMessageAction(message)
                MessageActionButton(
                    icon = Icons.Outlined.Edit,
                    contentDescription = "Edit message",
                    onClick = onEdit
                )
            }
        }
    }
}

@Composable
private fun AssistantMessage(
    message: ChatMessage,
    isAnyStreaming: Boolean,
    onRegenerate: () -> Unit
) {
    val colors = LocalChatColors.current
    val isStreaming = message.status == MessageStatus.STREAMING
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        AppLogo(size = 28.dp, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            if (message.reasoning.isNotBlank()) {
                ReasoningSection(reasoning = message.reasoning)
                Spacer(Modifier.height(6.dp))
            }

            when {
                isStreaming && message.content.isBlank() -> TypingDots()
                message.content.isNotBlank() -> MarkdownText(markdown = message.content)
                else -> Unit
            }

            if (isStreaming && message.content.isNotBlank()) {
                TypingCursor()
            }

            if (message.status == MessageStatus.ERROR) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = colors.dangerSurface,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.ErrorOutline,
                                contentDescription = null,
                                tint = colors.danger,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                text = "Response failed",
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.danger
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = message.error ?: "Something went wrong.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.danger
                        )
                    }
                }
            }

            if (message.status == MessageStatus.STOPPED && message.content.isBlank()) {
                Text(
                    text = "Generation stopped.",
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.subtleText
                )
            }

            if (message.status == MessageStatus.STOPPED && message.content.isNotBlank()) {
                Text(
                    text = "Stopped",
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.subtleText
                )
            }

            val promptTokens = message.promptTokens
            val completionTokens = message.completionTokens
            if (!isStreaming && (promptTokens != null || completionTokens != null)) {
                Text(
                    text = "${promptTokens ?: "?"} in · ${completionTokens ?: "?"} out tokens",
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.subtleText
                )
            }

            if (!isStreaming) {
                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CopyMessageAction(message)
                    MessageActionButton(
                        icon = Icons.Outlined.Refresh,
                        contentDescription = "Regenerate response",
                        enabled = !isAnyStreaming,
                        onClick = onRegenerate
                    )
                }
            }
        }
    }
}

@Composable
private fun ReasoningSection(reasoning: String) {
    var expanded by remember { mutableStateOf(false) }
    val colors = LocalChatColors.current
    Column {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp, horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Reasoning",
                style = MaterialTheme.typography.labelMedium,
                color = colors.subtleText
            )
            Spacer(Modifier.width(2.dp))
            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = if (expanded) "Hide reasoning" else "Show reasoning",
                tint = colors.subtleText,
                modifier = Modifier.size(17.dp)
            )
        }
        if (expanded) {
            Surface(
                color = colors.chipBackground,
                shape = RoundedCornerShape(12.dp)
            ) {
                SelectionContainer {
                    Text(
                        text = reasoning,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.subtleText
                    )
                }
            }
        }
    }
}

@Composable
private fun CopyMessageAction(message: ChatMessage) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(message.id) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1400)
            copied = false
        }
    }
    MessageActionButton(
        icon = if (copied) Icons.Filled.Check else Icons.Outlined.ContentCopy,
        contentDescription = if (copied) "Copied" else "Copy message",
        enabled = message.content.isNotBlank(),
        onClick = {
            clipboard.setText(AnnotatedString(message.content))
            copied = true
        }
    )
}

@Composable
private fun MessageActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(32.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(16.dp),
            tint = LocalChatColors.current.subtleText.copy(
                alpha = if (enabled) 1f else 0.4f
            )
        )
    }
}

@Composable
private fun ChatComposer(
    text: String,
    onTextChange: (String) -> Unit,
    attachments: List<Attachment>,
    onRemoveAttachment: (Int) -> Unit,
    isPreparingAttachment: Boolean,
    hasModel: Boolean,
    isStreamingHere: Boolean,
    isBusyElsewhere: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onAttach: () -> Unit,
    onMic: () -> Unit
) {
    val colors = LocalChatColors.current
    val canSend = hasModel &&
        !isPreparingAttachment &&
        !isBusyElsewhere &&
        !isStreamingHere &&
        (text.isNotBlank() || attachments.isNotEmpty())

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 12.dp)
            .padding(bottom = 4.dp)
    ) {
        if (attachments.isNotEmpty() || isPreparingAttachment) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                attachments.forEachIndexed { index, attachment ->
                    Box(modifier = Modifier.size(68.dp)) {
                        Base64Image(
                            base64 = attachment.base64,
                            contentDescription = attachment.name,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(11.dp))
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(3.dp)
                                .size(19.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.62f))
                                .clickable { onRemoveAttachment(index) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Remove image",
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
                if (isPreparingAttachment) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(colors.chipBackground),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = colors.composerBorder,
                    shape = RoundedCornerShape(26.dp)
                ),
            color = colors.composerBackground,
            shape = RoundedCornerShape(26.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                IconButton(
                    onClick = onAttach,
                    enabled = !isPreparingAttachment && !isBusyElsewhere && attachments.size < 4,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AttachFile,
                        contentDescription = "Attach image",
                        tint = colors.subtleText,
                        modifier = Modifier.size(21.dp)
                    )
                }

                BasicTextField(
                    value = text,
                    onValueChange = {
                        if (it.length <= 50_000) onTextChange(it)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 11.dp, horizontal = 4.dp),
                    enabled = hasModel && !isBusyElsewhere && !isPreparingAttachment,
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                        lineHeight = 22.sp
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    maxLines = 6,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = { if (canSend) onSend() }
                    ),
                    decorationBox = { innerTextField ->
                        Box {
                            if (text.isBlank()) {
                                Text(
                                    text = when {
                                        !hasModel -> "Connect a model to start"
                                        isBusyElsewhere -> "Generating in another chat"
                                        else -> "Message OpenChat"
                                    },
                                    color = colors.subtleText,
                                    fontSize = 16.sp,
                                    lineHeight = 22.sp
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                if (isPreparingAttachment) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .padding(8.dp)
                    ) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                    }
                } else if (isStreamingHere) {
                    RoundComposerButton(
                        icon = Icons.Filled.Stop,
                        contentDescription = "Stop generating",
                        enabled = true,
                        isPrimary = true,
                        onClick = onStop
                    )
                } else if (text.isNotBlank() || attachments.isNotEmpty()) {
                    RoundComposerButton(
                        icon = Icons.Filled.ArrowUpward,
                        contentDescription = "Send message",
                        enabled = canSend,
                        isPrimary = true,
                        onClick = onSend
                    )
                } else {
                    IconButton(
                        onClick = onMic,
                        enabled = hasModel && !isBusyElsewhere,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Mic,
                            contentDescription = "Voice input",
                            tint = colors.subtleText,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }
            }
        }

        Text(
            text = "OpenChat can make mistakes. Check important information.",
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 7.dp),
            style = MaterialTheme.typography.labelSmall,
            color = colors.subtleText,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun RoundComposerButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    isPrimary: Boolean,
    onClick: () -> Unit
) {
    val background = when {
        !isPrimary -> LocalChatColors.current.chipBackground
        enabled -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val iconColor = when {
        !isPrimary -> MaterialTheme.colorScheme.onSurface
        enabled -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    }
    Box(
        modifier = Modifier
            .padding(bottom = 2.dp)
            .size(36.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconColor,
            modifier = Modifier.size(19.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelPickerSheet(
    state: AppUiState,
    onDismiss: () -> Unit,
    onSelect: (String, String) -> Unit,
    onManageModels: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val options = state.enabledOptions()
    val selection = state.resolveSelection()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 18.dp)
        ) {
            Text(
                text = "Choose a model",
                modifier = Modifier.padding(horizontal = 20.dp),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${options.size} model${if (options.size == 1) "" else "s"} ready for chat",
                modifier = Modifier.padding(horizontal = 20.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalChatColors.current.subtleText
            )
            Spacer(Modifier.height(10.dp))

            if (options.isEmpty()) {
                Text(
                    text = "No models are enabled yet. Fetch your provider's models, test one, and enable it.",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalChatColors.current.subtleText
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp)
                ) {
                    val grouped = options
                        .groupBy { it.provider.id }
                        .values
                        .sortedBy { it.first().provider.name.lowercase() }
                    grouped.forEach { providerModels ->
                        val provider = providerModels.first().provider
                        item(key = "provider-${provider.id}") {
                            Text(
                                text = provider.name,
                                modifier = Modifier.padding(
                                    start = 20.dp,
                                    end = 20.dp,
                                    top = 10.dp,
                                    bottom = 4.dp
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = LocalChatColors.current.subtleText
                            )
                        }
                        items(
                            items = providerModels,
                            key = { it.provider.id + "::" + it.model.modelId }
                        ) { option ->
                            val selected = selection != null &&
                                selection.provider.id == option.provider.id &&
                                selection.modelId == option.model.modelId
                            ModelOptionRow(
                                option = option,
                                selected = selected,
                                onClick = {
                                    onSelect(option.provider.id, option.model.modelId)
                                }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onManageModels)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Manage models",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowDown,
                    contentDescription = null,
                    tint = LocalChatColors.current.subtleText,
                    modifier = Modifier
                        .size(18.dp)
                        .padding(top = 1.dp)
                )
            }
        }
    }
}

@Composable
private fun ModelOptionRow(
    option: ModelOption,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                else Color.Transparent
            )
            .padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = option.model.modelId,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val testLabel = option.model.latencyMs?.let { "Tested · $it ms" } ?: "Enabled"
            Text(
                text = testLabel,
                style = MaterialTheme.typography.labelSmall,
                color = LocalChatColors.current.subtleText
            )
        }
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun EditMessageDialog(
    message: ChatMessage,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember(message.id) { mutableStateOf(message.content) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit message") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 110.dp),
                    minLines = 3,
                    maxLines = 8
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Saving will regenerate the response below this message.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalChatColors.current.subtleText
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(text) },
                enabled = text.trim().isNotEmpty() || message.attachments.isNotEmpty()
            ) {
                Text("Save & send")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
