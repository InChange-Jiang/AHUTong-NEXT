package com.ahu.ahutong.next.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.ahu.ahutong.next.app.data.SettingsRepository
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

    val context = LocalContext.current
    val settings = remember { SettingsRepository(context) }
    var displayScale by remember { mutableFloatStateOf(settings.displayScale()) }
    val baseDensity = LocalDensity.current

    CompositionLocalProvider(
        LocalDensity provides Density(
            density = baseDensity.density * displayScale,
            fontScale = baseDensity.fontScale,
        ),
    ) {
        when (screen) {
        Screen.CHAT -> ChatScreen(
            state = state,
            onInputChange = viewModel::onInputChange,
            onSend = viewModel::send,
            onApprove = { viewModel.approve(it) },
            onReject = viewModel::reject,
            onCancel = viewModel::cancelRun,
            onOpenSettings = { screen = Screen.SETTINGS },
        )

        Screen.SETTINGS -> SettingsScreen(
            modelLabel = state.modelLabel,
            historyCount = state.items.size,
            displayScale = displayScale,
            onDisplayScaleChange = {
                displayScale = it
                settings.setDisplayScale(it)
            },
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
}
