package com.openchat.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openchat.app.AppUiState
import com.openchat.app.ChatViewModel
import com.openchat.app.data.Conversation
import com.openchat.app.ui.components.AppLogo
import com.openchat.app.ui.theme.LocalChatColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun ChatDrawer(
    uiState: AppUiState,
    viewModel: ChatViewModel,
    onClose: () -> Unit,
    onNewChat: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenModels: () -> Unit
) {
    val colors = LocalChatColors.current
    var query by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<Conversation?>(null) }
    var deleteTarget by remember { mutableStateOf<Conversation?>(null) }

    val conversations = uiState.data.conversations
    val filtered = remember(conversations, query) {
        val needle = query.trim()
        if (needle.isBlank()) {
            conversations
        } else {
            conversations.filter { it.title.contains(needle, ignoreCase = true) }
        }
    }
    val pinned = remember(filtered) {
        filtered.filter { it.pinned }.sortedByDescending { it.updatedAt }
    }
    val regular = remember(filtered) {
        filtered.filterNot { it.pinned }.sortedByDescending { it.updatedAt }
    }

    ModalDrawerSheet(
        modifier = Modifier.width(330.dp),
        drawerContainerColor = colors.sidebar,
        windowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 8.dp, top = 10.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppLogo(size = 30.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "OpenChat",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close sidebar",
                        modifier = Modifier.size(21.dp),
                        tint = colors.subtleText
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable {
                        onClose()
                        onNewChat()
                    }
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(27.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.width(11.dp))
                Text(
                    text = "New chat",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            }

            BasicTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                decorationBox = { innerTextField ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.chipBackground)
                            .padding(horizontal = 11.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp),
                            tint = colors.subtleText
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (query.isBlank()) {
                                Text(
                                    text = "Search chats",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.subtleText
                                )
                            }
                            innerTextField()
                        }
                    }
                }
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 8.dp,
                    end = 8.dp,
                    top = 2.dp,
                    bottom = 8.dp
                )
            ) {
                if (filtered.isEmpty()) {
                    item(key = "empty-chats") {
                        Text(
                            text = if (conversations.isEmpty()) {
                                "No chats yet. Start a conversation."
                            } else {
                                "No chats match your search."
                            },
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.subtleText
                        )
                    }
                }

                if (pinned.isNotEmpty()) {
                    item(key = "pinned-header") {
                        DrawerSectionLabel("Pinned")
                    }
                    items(pinned, key = { "pinned-${it.id}" }) { conversation ->
                        DrawerConversationRow(
                            conversation = conversation,
                            selected = conversation.id == uiState.activeConversationId,
                            onClick = {
                                onClose()
                                onOpenConversation(conversation.id)
                            },
                            onRename = { renameTarget = conversation },
                            onTogglePin = { viewModel.togglePinned(conversation.id) },
                            onDelete = { deleteTarget = conversation }
                        )
                    }
                }

                var lastGroup: String? = null
                regular.forEach { conversation ->
                    val group = conversationGroup(conversation.updatedAt)
                    if (group != lastGroup) {
                        item(key = "group-$group") {
                            DrawerSectionLabel(group)
                        }
                        lastGroup = group
                    }
                    item(key = "conversation-${conversation.id}") {
                        DrawerConversationRow(
                            conversation = conversation,
                            selected = conversation.id == uiState.activeConversationId,
                            onClick = {
                                onClose()
                                onOpenConversation(conversation.id)
                            },
                            onRename = { renameTarget = conversation },
                            onTogglePin = { viewModel.togglePinned(conversation.id) },
                            onDelete = { deleteTarget = conversation }
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                DrawerFooterRow(
                    icon = Icons.Outlined.Tune,
                    title = "Manage models",
                    subtitle = "${uiState.enabledOptions().size} enabled",
                    onClick = {
                        onClose()
                        onOpenModels()
                    }
                )
                DrawerFooterRow(
                    icon = Icons.Outlined.Settings,
                    title = "Settings",
                    subtitle = "${uiState.data.providers.size} provider${if (uiState.data.providers.size == 1) "" else "s"}",
                    onClick = {
                        onClose()
                        onOpenSettings()
                    }
                )
            }
        }
    }

    val renaming = renameTarget
    if (renaming != null) {
        RenameConversationDialog(
            initialTitle = renaming.title,
            onDismiss = { renameTarget = null },
            onRename = { newTitle ->
                viewModel.renameConversation(renaming.id, newTitle)
                renameTarget = null
            }
        )
    }

    val deleting = deleteTarget
    if (deleting != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete chat?") },
            text = {
                Text("\"${deleting.title}\" will be permanently removed from this device.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteConversation(deleting.id)
                        deleteTarget = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
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
private fun DrawerConversationRow(
    conversation: Conversation,
    selected: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalChatColors.current
    var menuExpanded by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (selected) MaterialTheme.colorScheme.surfaceVariant
                    else androidx.compose.ui.graphics.Color.Transparent
                )
                .clickable(onClick = onClick)
                .padding(start = 11.dp, end = 2.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = conversation.title,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 2.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.size(31.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = "Chat options",
                    modifier = Modifier.size(17.dp),
                    tint = colors.subtleText
                )
            }
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Rename") },
                leadingIcon = {
                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                onClick = {
                    menuExpanded = false
                    onRename()
                }
            )
            DropdownMenuItem(
                text = { Text(if (conversation.pinned) "Unpin" else "Pin") },
                leadingIcon = {
                    Icon(Icons.Outlined.PushPin, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                onClick = {
                    menuExpanded = false
                    onTogglePin()
                }
            )
            DropdownMenuItem(
                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                onClick = {
                    menuExpanded = false
                    onDelete()
                }
            )
        }
    }
}

@Composable
private fun DrawerSectionLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 11.dp, top = 12.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = LocalChatColors.current.subtleText
    )
}

@Composable
private fun DrawerFooterRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(19.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = LocalChatColors.current.subtleText
            )
        }
    }
}

@Composable
private fun RenameConversationDialog(
    initialTitle: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename chat") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onRename(title) },
                enabled = title.trim().isNotEmpty()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun conversationGroup(timestamp: Long): String {
    val date = Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
    val today = LocalDate.now()
    return when {
        date == today -> "Today"
        date == today.minusDays(1) -> "Yesterday"
        date.isAfter(today.minusDays(7)) -> "Previous 7 days"
        date.isAfter(today.minusDays(30)) -> "Previous 30 days"
        else -> "Older"
    }
}
