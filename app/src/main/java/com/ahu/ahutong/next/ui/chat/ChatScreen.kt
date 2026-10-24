package com.ahu.ahutong.next.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.next.R
import com.ahu.ahutong.next.ui.components.AhtorusMark
import com.ahu.ahutong.next.ui.components.SoftBackground
import com.ahu.ahutong.next.ui.components.SoftChip
import com.ahu.ahutong.next.ui.components.SoftIconButton
import com.ahu.ahutong.next.ui.components.SoftSurface
import com.ahu.ahutong.next.ui.theme.NextPalette

@Composable
fun ChatScreen(
    state: ChatUiState,
    onInputChange: (String) -> Unit,
    onSend: (String) -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onCancel: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(state.items.size, state.status) {
        val target = state.items.lastIndex + if (state.status != null) 1 else 0
        if (target >= 0) listState.animateScrollToItem(target)
    }

    SoftBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            FloatingTopBar(
                online = state.hasApiKey,
                onOpenSettings = onOpenSettings,
            )

            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 18.dp,
                        end = 18.dp,
                        top = 10.dp,
                        bottom = 150.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (state.items.isEmpty()) {
                        item {
                            EmptyHero(onSend = onSend)
                        }
                    } else {
                        items(state.items, key = { it.id }) { item ->
                            MessageItem(
                                item = item,
                                onApprove = onApprove,
                                onReject = onReject,
                            )
                        }
                    }

                    if (state.status != null) {
                        item {
                            StatusPill(text = state.status, onCancel = onCancel)
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            InputPill(
                value = state.input,
                enabled = !state.isRunning,
                onValueChange = onInputChange,
                onSend = { onSend(state.input) },
            )
        }
    }
}

@Composable
private fun FloatingTopBar(
    online: Boolean,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AhtorusMark(size = 42.dp)
        Spacer(Modifier.width(12.dp))

        SoftSurface(
            shape = RoundedCornerShape(50),
            elevation = 8.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "NEXT",
                    style = MaterialTheme.typography.titleSmall,
                    color = NextPalette.Ink,
                )
                Spacer(Modifier.width(9.dp))
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (online) NextPalette.Success else NextPalette.Warning),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (online) "在线" else "本地",
                    style = MaterialTheme.typography.labelSmall,
                    color = NextPalette.InkSecondary,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        SoftIconButton(onClick = onOpenSettings) {
            Icon(
                painter = painterResource(R.drawable.ic_iconpark_setting),
                contentDescription = "设置",
                tint = NextPalette.Ink,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun EmptyHero(onSend: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 26.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AhtorusMark(size = 104.dp)
        Spacer(Modifier.height(28.dp))
        Text(
            "嗨，今天想做什么",
            style = MaterialTheme.typography.displaySmall,
            color = NextPalette.Ink,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "我是 Ahtorus。查课表、看作业、问成绩，或者让我加一条日程。",
            style = MaterialTheme.typography.bodyMedium,
            color = NextPalette.InkSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(26.dp))

        val suggestions = listOf(
            Suggestion("作业", "这周还有哪些作业？", R.drawable.ic_iconpark_assignment),
            Suggestion("课表", "我今天的课表", R.drawable.ic_iconpark_calendar),
            Suggestion("成绩", "查一下我的成绩", R.drawable.ic_iconpark_grades),
            Suggestion("日程", "把小组会议加到日程", R.drawable.ic_iconpark_plus),
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 2.dp),
        ) {
            items(suggestions, key = { it.label }) { suggestion ->
                SoftChip(
                    label = suggestion.label,
                    icon = painterResource(suggestion.iconRes),
                    onClick = { onSend(suggestion.query) },
                )
            }
        }
    }
}

@Composable
private fun InputPill(
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    val canSend = enabled && value.isNotBlank()

    SoftSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        elevation = 18.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 6.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = NextPalette.Ink),
                placeholder = {
                    Text(
                        "问课表、作业、成绩…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = NextPalette.InkSecondary,
                    )
                },
                maxLines = 4,
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    cursorColor = NextPalette.Accent,
                    focusedTextColor = NextPalette.Ink,
                    unfocusedTextColor = NextPalette.Ink,
                    disabledTextColor = NextPalette.InkSecondary,
                    focusedPlaceholderColor = NextPalette.InkSecondary,
                    unfocusedPlaceholderColor = NextPalette.InkSecondary,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
            )

            Spacer(Modifier.width(6.dp))

            FilledIconButton(
                onClick = onSend,
                enabled = canSend,
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = NextPalette.Accent,
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFFE7E5DF),
                    disabledContentColor = NextPalette.InkSecondary,
                ),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_iconpark_send),
                    contentDescription = "发送",
                    modifier = Modifier.size(19.dp),
                )
            }
        }
    }
}

@Composable
private fun MessageItem(
    item: ChatItem,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    when (item) {
        is ChatItem.User -> UserBubble(item.text)
        is ChatItem.Assistant -> AssistantMessage(item.text)
        is ChatItem.Tool -> ToolResultCard(item.result)
        is ChatItem.Approval -> ApprovalCard(item, onApprove, onReject)
        is ChatItem.Error -> ErrorCard(item.text)
        is ChatItem.Notice -> Text(
            item.text,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelSmall,
            color = NextPalette.InkSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun UserBubble(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Box(
            modifier = Modifier
                .shadow(
                    elevation = 10.dp,
                    shape = RoundedCornerShape(22.dp, 22.dp, 8.dp, 22.dp),
                    clip = false,
                    ambientColor = Color(0x332F6BFF),
                    spotColor = Color(0x332F6BFF),
                )
                .clip(RoundedCornerShape(22.dp, 22.dp, 8.dp, 22.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF4A82FF), NextPalette.Accent)))
                .padding(horizontal = 16.dp, vertical = 11.dp),
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun AssistantMessage(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        AhtorusMark(size = 30.dp)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Ahtorus",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = NextPalette.Accent,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                color = NextPalette.Ink,
            )
        }
    }
}

@Composable
private fun StatusPill(text: String, onCancel: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        SoftSurface(
            shape = RoundedCornerShape(50),
            elevation = 6.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = NextPalette.Accent,
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    color = NextPalette.InkSecondary,
                )
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = onCancel,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Text(
                        "取消",
                        style = MaterialTheme.typography.labelSmall,
                        color = NextPalette.Accent,
                    )
                }
            }
        }
    }
}

private data class Suggestion(
    val label: String,
    val query: String,
    val iconRes: Int,
)
