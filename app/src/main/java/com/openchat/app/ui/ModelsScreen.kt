package com.openchat.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openchat.app.AppUiState
import com.openchat.app.ChatViewModel
import com.openchat.app.data.ModelInfo
import com.openchat.app.data.Provider
import com.openchat.app.ui.components.ScreenTopBar
import com.openchat.app.ui.theme.LocalChatColors

@Composable
fun ModelsScreen(
    uiState: AppUiState,
    viewModel: ChatViewModel,
    initialProviderId: String?,
    onBack: () -> Unit,
    onOpenProviders: () -> Unit
) {
    val providers = uiState.data.providers
    var selectedProviderId by remember(initialProviderId) {
        mutableStateOf(initialProviderId?.takeIf { id -> providers.any { it.id == id } })
    }
    var query by remember { mutableStateOf("") }
    var showAddModel by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ModelInfo?>(null) }

    LaunchedEffect(initialProviderId) {
        val target = initialProviderId ?: return@LaunchedEffect
        val provider = uiState.data.providers.firstOrNull { it.id == target }
            ?: return@LaunchedEffect
        val hasModels = uiState.data.models.any { it.providerId == provider.id }
        if (!hasModels) {
            viewModel.fetchModels(provider.id)
        }
    }

    LaunchedEffect(providers) {
        val selected = selectedProviderId ?: return@LaunchedEffect
        if (providers.none { it.id == selected }) {
            selectedProviderId = null
        }
    }

    val selectedProvider = selectedProviderId?.let { id ->
        providers.firstOrNull { it.id == id }
    }
    val searchQuery = query.trim()
    val visibleModels = uiState.data.models.filter { model ->
        val matchesProvider = selectedProvider == null || model.providerId == selectedProvider.id
        val matchesQuery = searchQuery.isBlank() ||
            model.modelId.contains(searchQuery, ignoreCase = true)
        matchesProvider && matchesQuery
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
    ) {
        ScreenTopBar(
            title = "Models",
            onBack = onBack,
            actions = {
                IconButton(
                    onClick = {
                        if (selectedProvider == null) {
                            viewModel.notify("Choose a provider first, then add its model ID.")
                        } else {
                            showAddModel = true
                        }
                    }
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add model manually")
                }
            }
        )

        if (providers.isEmpty()) {
            NoProvidersState(
                onOpenProviders = onOpenProviders,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            )
        } else {
            if (providers.size > 1) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item(key = "all") {
                        FilterChip(
                            selected = selectedProviderId == null,
                            onClick = { selectedProviderId = null },
                            label = { Text("All providers") }
                        )
                    }
                    items(items = providers, key = { it.id }) { provider ->
                        FilterChip(
                            selected = selectedProviderId == provider.id,
                            onClick = { selectedProviderId = provider.id },
                            label = { Text(provider.name) }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("Search models") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Clear search",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (selectedProvider != null) {
                    item(key = "selected-provider") {
                        SelectedProviderPanel(
                            provider = selectedProvider,
                            modelCount = uiState.data.models.count {
                                it.providerId == selectedProvider.id
                            },
                            enabledCount = uiState.data.models.count {
                                it.providerId == selectedProvider.id && it.enabled
                            },
                            isFetching = selectedProvider.id in uiState.fetchingProviderIds,
                            onFetch = { viewModel.fetchModels(selectedProvider.id) },
                            onAddManual = { showAddModel = true }
                        )
                    }

                    if (visibleModels.isEmpty()) {
                        item(key = "empty-models") {
                            EmptyModelsCard(
                                query = searchQuery,
                                isFetching = selectedProvider.id in uiState.fetchingProviderIds,
                                onFetch = { viewModel.fetchModels(selectedProvider.id) },
                                onAddManual = { showAddModel = true }
                            )
                        }
                    } else {
                        items(items = visibleModels, key = { it.key }) { model ->
                            ModelRow(
                                model = model,
                                isTesting = model.key in uiState.testingModelKeys,
                                onTest = {
                                    viewModel.testModel(model.providerId, model.modelId)
                                },
                                onEnabledChange = { enabled ->
                                    viewModel.setModelEnabled(
                                        model.providerId,
                                        model.modelId,
                                        enabled
                                    )
                                },
                                onDelete = { deleteTarget = model }
                            )
                        }
                    }

                    item(key = "tip") {
                        Text(
                            text = "Testing a model sends a tiny message and enables it if the provider answers. " +
                                "Enabled models appear in the chat model picker.",
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalChatColors.current.subtleText
                        )
                    }
                } else {
                    var shownAnySection = false
                    providers.forEach { provider ->
                        val providerModels = visibleModels.filter {
                            it.providerId == provider.id
                        }
                        val shouldShow = searchQuery.isBlank() || providerModels.isNotEmpty()
                        if (shouldShow) {
                            shownAnySection = true
                            item(key = "header-${provider.id}") {
                                ProviderModelsHeader(
                                    provider = provider,
                                    total = uiState.data.models.count {
                                        it.providerId == provider.id
                                    },
                                    enabled = uiState.data.models.count {
                                        it.providerId == provider.id && it.enabled
                                    },
                                    isFetching = provider.id in uiState.fetchingProviderIds,
                                    onFetch = { viewModel.fetchModels(provider.id) },
                                    onOpenProvider = { selectedProviderId = provider.id }
                                )
                            }
                            if (providerModels.isEmpty()) {
                                item(key = "empty-models-${provider.id}") {
                                    EmptyModelsCard(
                                        query = "",
                                        isFetching = provider.id in uiState.fetchingProviderIds,
                                        onFetch = { viewModel.fetchModels(provider.id) },
                                        onAddManual = {
                                            selectedProviderId = provider.id
                                            showAddModel = true
                                        }
                                    )
                                }
                            } else {
                                items(items = providerModels, key = { it.key }) { model ->
                                    ModelRow(
                                        model = model,
                                        isTesting = model.key in uiState.testingModelKeys,
                                        onTest = {
                                            viewModel.testModel(
                                                model.providerId,
                                                model.modelId
                                            )
                                        },
                                        onEnabledChange = { enabled ->
                                            viewModel.setModelEnabled(
                                                model.providerId,
                                                model.modelId,
                                                enabled
                                            )
                                        },
                                        onDelete = { deleteTarget = model }
                                    )
                                }
                            }
                        }
                    }

                    if (!shownAnySection) {
                        item(key = "no-results") {
                            Text(
                                text = "No models match \"$searchQuery\".",
                                modifier = Modifier.padding(vertical = 24.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = LocalChatColors.current.subtleText
                            )
                        }
                    } else if (searchQuery.isBlank() && uiState.data.models.isEmpty()) {
                        item(key = "tip-all") {
                            Text(
                                text = "Fetch a provider's list above, or open a single provider and add a model ID manually. " +
                                    "Enabled models appear in the chat model picker.",
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = LocalChatColors.current.subtleText
                            )
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(showAddModel, selectedProviderId) {
        if (showAddModel && selectedProvider == null) {
            showAddModel = false
        }
    }

    if (showAddModel && selectedProvider != null) {
        val provider = selectedProvider
        AddModelDialog(
            provider = provider,
            onDismiss = { showAddModel = false },
            onAdd = { modelId ->
                val added = viewModel.addManualModel(provider.id, modelId)
                if (added) {
                    showAddModel = false
                }
            }
        )
    }

    deleteTarget?.let { model ->
        val providerName = providers.firstOrNull { it.id == model.providerId }?.name
            ?: "this provider"
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Remove ${model.modelId}?") },
            text = {
                Text(
                    "The model will be removed from $providerName. " +
                        "If it was fetched from the provider, it can return the next time you fetch the list."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteModel(model.providerId, model.modelId)
                        deleteTarget = null
                        viewModel.notify("Model removed.")
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
private fun SelectedProviderPanel(
    provider: Provider,
    modelCount: Int,
    enabledCount: Int,
    isFetching: Boolean,
    onFetch: () -> Unit,
    onAddManual: () -> Unit
) {
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
                Column(Modifier.weight(1f)) {
                    Text(
                        text = provider.name,
                        style = MaterialTheme.typography.titleMedium,
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
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "$enabledCount/$modelCount",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onFetch,
                    enabled = !isFetching,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    if (isFetching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Fetching…")
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (modelCount == 0) "Fetch models" else "Refresh list")
                    }
                }
                TextButton(onClick = onAddManual) {
                    Text("Add manually")
                }
            }
        }
    }
}

@Composable
private fun ProviderModelsHeader(
    provider: Provider,
    total: Int,
    enabled: Int,
    isFetching: Boolean,
    onFetch: () -> Unit,
    onOpenProvider: () -> Unit
) {
    val colors = LocalChatColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 0.dp, top = 14.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 2.dp)
        ) {
            Text(
                text = provider.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "$total model${if (total == 1) "" else "s"} • $enabled enabled",
                style = MaterialTheme.typography.labelSmall,
                color = colors.subtleText
            )
        }
        if (isFetching) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(horizontal = 10.dp)
                    .size(18.dp),
                strokeWidth = 2.dp
            )
        } else {
            IconButton(onClick = onFetch) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = "Fetch models for ${provider.name}",
                    tint = colors.subtleText,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
        TextButton(onClick = onOpenProvider) {
            Text(
                text = "Filter",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun ModelRow(
    model: ModelInfo,
    isTesting: Boolean,
    onTest: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalChatColors.current
    val statusText = when {
        isTesting -> "Testing the connection…"
        model.tested -> buildString {
            append("Passed")
            model.latencyMs?.let { append(" • $it ms") }
        }

        else -> "Not tested"
    }
    val statusColor = when {
        isTesting -> MaterialTheme.colorScheme.primary
        model.tested -> MaterialTheme.colorScheme.primary
        else -> colors.subtleText
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = model.modelId,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (model.isManual) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = colors.chipBackground
                        ) {
                            Text(
                                text = "manual",
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                fontSize = 9.sp,
                                color = colors.subtleText
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(17.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    IconButton(onClick = onTest) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Test ${model.modelId}",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
            Switch(
                checked = model.enabled,
                onCheckedChange = onEnabledChange
            )
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Remove ${model.modelId}",
                    tint = colors.subtleText,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyModelsCard(
    query: String,
    isFetching: Boolean,
    onFetch: () -> Unit,
    onAddManual: () -> Unit
) {
    val colors = LocalChatColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            if (query.isNotBlank()) {
                Text(
                    text = "No models match \"$query\" in this provider.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.subtleText
                )
            } else if (isFetching) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Asking the provider for its model list…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.subtleText
                    )
                }
            } else {
                Text(
                    text = "No models loaded yet. Fetch the provider's list, or add a model ID manually if the provider does not support listing.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.subtleText
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onFetch) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Fetch models")
                    }
                    TextButton(onClick = onAddManual) {
                        Text("Add manually")
                    }
                }
            }
        }
    }
}

@Composable
private fun NoProvidersState(
    onOpenProviders: () -> Unit,
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
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = "No providers yet",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Models belong to providers. Add a Base URL and API key first, then come back here to test and enable models.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.subtleText
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onOpenProviders) {
            Text("Open providers")
        }
    }
}

@Composable
private fun AddModelDialog(
    provider: Provider,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    var modelId by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a model") },
        text = {
            Column {
                Text(
                    text = "Enter the exact model ID that ${provider.name} expects, for example gpt-4o-mini.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalChatColors.current.subtleText
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = modelId,
                    onValueChange = { modelId = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Model ID") },
                    placeholder = { Text("gpt-4o-mini") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done
                    )
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "The model is added enabled. Use the play button to test it at any time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalChatColors.current.subtleText
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(modelId) },
                enabled = modelId.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
