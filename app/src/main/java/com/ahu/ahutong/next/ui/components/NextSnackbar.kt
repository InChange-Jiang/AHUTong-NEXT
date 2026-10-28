package com.ahu.ahutong.next.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 全局 Snackbar 控制器。连续操作时先 dismiss 上一条，避免像系统 Toast 那样排队等待。
 */
class NextSnackbarController internal constructor(
    val hostState: SnackbarHostState,
    private val scope: CoroutineScope,
) {
    fun show(message: String) {
        if (message.isBlank()) return
        hostState.currentSnackbarData?.dismiss()
        scope.launch {
            hostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short,
            )
        }
    }
}

val LocalNextSnackbar = staticCompositionLocalOf<NextSnackbarController?> { null }

@Composable
fun rememberNextSnackbarController(): NextSnackbarController {
    val hostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    return remember(hostState, scope) {
        NextSnackbarController(hostState, scope)
    }
}

@Composable
fun NextSnackbarHost(
    controller: NextSnackbarController,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(
        hostState = controller.hostState,
        modifier = modifier,
    ) { data ->
        Snackbar(
            snackbarData = data,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(horizontal = 6.dp),
        )
    }
}
