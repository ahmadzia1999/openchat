package com.openchat.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openchat.app.ui.ChatDrawer
import com.openchat.app.ui.ChatScreen
import com.openchat.app.ui.ModelsScreen
import com.openchat.app.ui.ProvidersScreen
import com.openchat.app.ui.SettingsScreen
import com.openchat.app.ui.components.AppLogo
import com.openchat.app.ui.theme.OpenChatTheme
import kotlinx.coroutines.launch

private enum class AppScreen {
    CHAT,
    SETTINGS,
    PROVIDERS,
    MODELS
}

@Composable
fun AppRoot(viewModel: ChatViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    OpenChatTheme(themeMode = uiState.data.settings.theme) {
        val snackbarHostState = remember { SnackbarHostState() }

        LaunchedEffect(viewModel) {
            viewModel.events.collect { message ->
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(
                    message = message,
                    duration = SnackbarDuration.Short
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (!uiState.ready) {
                LoadingScreen()
            } else {
                AppNavigation(uiState = uiState, viewModel = viewModel)
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun AppNavigation(
    uiState: AppUiState,
    viewModel: ChatViewModel
) {
    val backStack = remember { mutableStateListOf(AppScreen.CHAT) }
    val scope = rememberCoroutineScope()
    var modelsProviderId by remember { mutableStateOf<String?>(null) }

    val currentScreen = backStack.last()
    val navigate: (AppScreen) -> Unit = { screen ->
        if (backStack.lastOrNull() != screen) {
            backStack.add(screen)
        }
    }
    val goBack: () -> Unit = {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
        }
    }
    val openModels: (String?) -> Unit = { providerId ->
        modelsProviderId = providerId
        navigate(AppScreen.MODELS)
    }

    BackHandler(enabled = currentScreen != AppScreen.CHAT) {
        goBack()
    }

    when (currentScreen) {
        AppScreen.CHAT -> {
            val drawerState = rememberDrawerState(DrawerValue.Closed)
            BackHandler(enabled = drawerState.isOpen) {
                scope.launch { drawerState.close() }
            }

            val currentProviderId = uiState.resolveSelection()?.provider?.id
                ?: uiState.data.settings.lastProviderId.takeIf { it.isNotBlank() }

            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ChatDrawer(
                        uiState = uiState,
                        viewModel = viewModel,
                        onClose = { scope.launch { drawerState.close() } },
                        onNewChat = {
                            scope.launch { drawerState.close() }
                            if (uiState.isStreaming) {
                                viewModel.stopStreaming()
                            }
                            viewModel.startNewChat()
                        },
                        onOpenConversation = { conversationId ->
                            scope.launch { drawerState.close() }
                            viewModel.openConversation(conversationId)
                        },
                        onOpenSettings = {
                            scope.launch { drawerState.close() }
                            navigate(AppScreen.SETTINGS)
                        },
                        onOpenModels = {
                            scope.launch { drawerState.close() }
                            openModels(null)
                        }
                    )
                }
            ) {
                ChatScreen(
                    uiState = uiState,
                    viewModel = viewModel,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onManageProviders = { navigate(AppScreen.PROVIDERS) },
                    onManageModels = { openModels(currentProviderId) }
                )
            }
        }

        AppScreen.SETTINGS -> {
            SettingsScreen(
                uiState = uiState,
                viewModel = viewModel,
                onBack = goBack,
                onOpenProviders = { navigate(AppScreen.PROVIDERS) },
                onOpenModels = { openModels(null) }
            )
        }

        AppScreen.PROVIDERS -> {
            ProvidersScreen(
                uiState = uiState,
                viewModel = viewModel,
                onBack = goBack,
                onManageModels = { providerId -> openModels(providerId) }
            )
        }

        AppScreen.MODELS -> {
            ModelsScreen(
                uiState = uiState,
                viewModel = viewModel,
                initialProviderId = modelsProviderId,
                onBack = goBack,
                onOpenProviders = { navigate(AppScreen.PROVIDERS) }
            )
        }
    }
}

@Composable
private fun LoadingScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AppLogo(size = 60.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            text = "OpenChat",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(22.dp))
        CircularProgressIndicator(
            modifier = Modifier.size(22.dp),
            strokeWidth = 2.dp
        )
    }
}
