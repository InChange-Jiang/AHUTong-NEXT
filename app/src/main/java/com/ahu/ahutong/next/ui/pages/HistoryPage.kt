package com.ahu.ahutong.next.ui.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.next.R
import com.ahu.ahutong.next.app.data.ChatSessionSummary
import com.ahu.ahutong.next.ui.components.FeatureScaffold
import com.ahu.ahutong.next.ui.components.SoftIconButton
import com.ahu.ahutong.next.ui.theme.NextPalette
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HistoryPage(
    sessions: List<ChatSessionSummary>,
    currentSessionId: String?,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onNewChat: () -> Unit,
) {
    var deleting by remember { mutableStateOf<ChatSessionSummary?>(null) }

    FeatureScaffold(
        title = "历史对话",
        subtitle = "共 ${sessions.size} 个会话",
        onBack = onBack,
        actions = {
            SoftIconButton(onClick = onNewChat) {
                Icon(
                    painter = painterResource(R.drawable.ic_iconpark_plus),
                    contentDescription = "新建对话",
                    tint = NextPalette.Ink,
                    modifier = Modifier.size(20.dp),
                )
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (sessions.isEmpty()) {
                PageCard {
                    Text(
                        "还没有历史对话。",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = NextPalette.Ink,
                    )
                    Text(
                        "每次点「新建对话」都会保留上一段对话，可以在这里切回来。",
                        style = MaterialTheme.typography.bodySmall,
                        color = NextPalette.InkSecondary,
                    )
                }
            } else {
                sessions.forEach { session ->
                    val isCurrent = session.id == currentSessionId
                    PageCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                                .clickable { onOpen(session.id) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        session.title,
                                        modifier = Modifier.weight(1f, fill = false),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = NextPalette.Ink,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (isCurrent) {
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "当前",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = NextPalette.AccentDeep,
                                        )
                                    }
                                }
                                if (session.preview.isNotBlank()) {
                                    Text(
                                        session.preview,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = NextPalette.InkSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text(
                                    "${session.messageCount} 条消息 · ${formatSessionTime(session.updatedAt)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NextPalette.InkSecondary,
                                )
                            }
                        }
                        HorizontalDivider(color = NextPalette.Line)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(onClick = { onOpen(session.id) }) { Text("打开") }
                            TextButton(onClick = { deleting = session }) { Text("删除") }
                        }
                    }
                }
            }
            Spacer(Modifier.size(76.dp))
        }
    }

    deleting?.let { session ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除这段对话？") },
            text = {
                Text(
                    "「${session.title}」会从本机移除，无法恢复。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(session.id)
                    deleting = null
                }) {
                    Text("删除", color = NextPalette.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("取消") }
            },
        )
    }
}

private fun formatSessionTime(timestamp: Long): String {
    if (timestamp <= 0L) return "未知时间"
    return DateTimeFormatter.ofPattern("MM-dd HH:mm").format(
        Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()),
    )
}
