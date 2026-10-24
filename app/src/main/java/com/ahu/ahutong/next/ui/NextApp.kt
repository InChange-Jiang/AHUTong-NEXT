package com.ahu.ahutong.next.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ahu.ahutong.next.ui.chat.ChatScreen
import com.ahu.ahutong.next.ui.chat.ChatViewModel
import com.ahu.ahutong.next.ui.lab.ToolLabScreen
import com.ahu.ahutong.next.ui.settings.SettingsScreen

private enum class Screen {
    CHAT,
    SETTINGS,
    LAB,
}

@Composable
fun NextApp(viewModel: ChatViewModel = viewModel()) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(Screen.CHAT) }

    when (screen) {
        Screen.CHAT -> ChatScreen(
            state = state,
            onInputChange = viewModel::onInputChange,
            onSend = viewModel::send,
            onApprove = viewModel::approve,
            onReject = viewModel::reject,
            onCancel = viewModel::cancelRun,
            onOpenSettings = { screen = Screen.SETTINGS },
        )

        Screen.SETTINGS -> SettingsScreen(
            modelLabel = state.modelLabel,
            historyCount = state.items.size,
            onBack = { screen = Screen.CHAT },
            onSaved = viewModel::refreshSettings,
            onClearHistory = viewModel::clearHistory,
            onOpenLab = { screen = Screen.LAB },
        )

        Screen.LAB -> ToolLabScreen(
            onBack = { screen = Screen.SETTINGS },
        )
    }
}
