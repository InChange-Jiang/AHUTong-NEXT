package com.ahu.ahutong.next.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
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
import com.ahu.ahutong.next.ui.components.AgentAvatar
import com.ahu.ahutong.next.ui.markdown.AppMarkdown
import com.ahu.ahutong.next.ui.markdown.KatexMarkdown
import com.ahu.ahutong.next.ui.markdown.StreamingAppMarkdown
import com.ahu.ahutong.next.ui.markdown.containsMath
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
    val streamingTextLength = (state.items.lastOrNull() as? ChatItem.Assistant)?.text?.length ?: 0
    val lastAssistantStreaming = (state.items.lastOrNull() as? ChatItem.Assistant)?.streaming == true

    // 新增消息、状态变化、流式结束都直接贴底，不用 animateScrollToItem：
    // 后者在长回复里会被不断重排打断，看起来就是反复跳。
    LaunchedEffect(state.items.size, state.status, lastAssistantStreaming) {
        val target = state.items.lastIndex + if (state.status != null) 1 else 0
        if (target < 0) return@LaunchedEffect
        withFrameNanos { }
        listState.scrollToItem(target)
        // 历史里的超长工具卡是惰性测量的：先跳到末条时列表还没算上它们的高度，
        // 随后几帧内容会继续变高。这里连续补滚到真正稳定，同时避开用户的拖动。
        var stableFrames = 0
        repeat(BOTTOM_SETTLE_MAX_FRAMES) {
            if (stableFrames >= BOTTOM_SETTLE_STABLE_FRAMES) return@LaunchedEffect
            withFrameNanos { }
            if (!listState.isScrollInProgress && listState.canScrollForward) {
                listState.dispatchRawDelta(BOTTOM_JUMP_PX)
                stableFrames = 0
            } else {
                stableFrames++
            }
        }
    }

    // 流式文本每长高一段就贴底。用非挂起的 dispatchRawDelta，避免滚动任务被下一次
    // 文本更新取消；这里不做“是否在底部”的判断，否则长回复超过一屏后就再也追不上。
    LaunchedEffect(streamingTextLength) {
        if (streamingTextLength == 0) return@LaunchedEffect
        withFrameNanos { }
        listState.dispatchRawDelta(BOTTOM_JUMP_PX)
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
                    verticalArrangement = if (state.items.isEmpty()) {
                        Arrangement.Top
                    } else {
                        Arrangement.spacedBy(14.dp, Alignment.Bottom)
                    },
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
    val accountName = com.ahu.ahutong.legacy.LegacyCampusApi.currentUserName()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AgentAvatar(size = 42.dp)
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
                    "安大通NEXT",
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
                    accountName ?: if (online) "在线" else "本地",
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
        AgentAvatar(size = 104.dp)
        Spacer(Modifier.height(28.dp))
        Text(
            "嗨，今天想做什么",
            style = MaterialTheme.typography.displaySmall,
            color = NextPalette.Ink,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "我是安筱桐。课表、作业、成绩、考勤、空闲教室，一句话就能查。",
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
            Suggestion("空闲教室", "今天有哪些空闲教室", R.drawable.ic_iconpark_calendar),
            Suggestion("天气", "合肥今天天气怎么样", R.drawable.ic_iconpark_weather),
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
            modifier = Modifier.padding(start = 6.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = NextPalette.Ink),
                placeholder = {
                    Text(
                        "问课表、作业、成绩、考勤…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = NextPalette.InkSecondary,
                    )
                },
                maxLines = 4,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    disabledBorderColor = Color.Transparent,
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

            Spacer(Modifier.width(8.dp))

            FilledIconButton(
                onClick = onSend,
                enabled = canSend,
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = NextPalette.Accent,
                    contentColor = Color.White,
                    disabledContainerColor = NextPalette.AccentSoft,
                    disabledContentColor = NextPalette.Accent.copy(alpha = 0.45f),
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
        is ChatItem.Assistant -> AssistantMessage(item.text, item.streaming)
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
                .background(Brush.linearGradient(listOf(Color(0xFFF6A47A), NextPalette.Accent)))
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
private fun AssistantMessage(text: String, streaming: Boolean) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AgentAvatar(size = 24.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                "安筱桐",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = NextPalette.Accent,
            )
        }
        Spacer(Modifier.height(7.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = NextPalette.Surface,
                    shape = RoundedCornerShape(
                        topStart = 8.dp,
                        topEnd = 22.dp,
                        bottomEnd = 22.dp,
                        bottomStart = 22.dp,
                    ),
                )
                .padding(horizontal = 15.dp, vertical = 13.dp),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (streaming) {
                    StreamingAppMarkdown(
                        markdown = text,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else if (text.containsMath()) {
                    KatexMarkdown(
                        markdown = text,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    AppMarkdown(
                        markdown = text,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
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
            elevation = 4.dp,
        ) {
            Row(
                modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.8.dp,
                    color = NextPalette.Accent,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text,
                    style = MaterialTheme.typography.labelMedium,
                    color = NextPalette.InkSecondary,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "取消",
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onCancel)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = NextPalette.Accent,
                )
            }
        }
    }
}

private const val BOTTOM_JUMP_PX = 100_000f
private const val BOTTOM_SETTLE_MAX_FRAMES = 60
private const val BOTTOM_SETTLE_STABLE_FRAMES = 5

private data class Suggestion(
    val label: String,
    val query: String,
    val iconRes: Int,
)
