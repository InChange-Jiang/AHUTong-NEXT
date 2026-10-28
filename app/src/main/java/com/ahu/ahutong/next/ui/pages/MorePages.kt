package com.ahu.ahutong.next.ui.pages

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.data.xuexiaotong.CourseProgress
import com.ahu.ahutong.legacy.LegacyCampusApi
import com.ahu.ahutong.next.ui.components.AsyncContent
import com.ahu.ahutong.next.ui.components.EmptyState
import com.ahu.ahutong.next.ui.components.FeatureScaffold
import com.ahu.ahutong.next.ui.components.FeatureScrollColumn
import com.ahu.ahutong.data.phonebook.PhoneBookDirectory
import com.ahu.ahutong.next.ui.components.LoadState
import com.ahu.ahutong.next.ui.components.LocalNextToast
import com.ahu.ahutong.next.ui.components.SoftIconButton
import com.ahu.ahutong.next.ui.components.SoftChip
import com.ahu.ahutong.next.ui.components.SoftChipStrip
import com.ahu.ahutong.next.ui.components.SoftPrimaryButton
import com.ahu.ahutong.next.ui.components.toLoadFailure
import com.ahu.ahutong.next.ui.theme.NextPalette
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import com.ahu.ahutong.next.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private fun formatCourseSyncTime(timestamp: Long): String =
    DateTimeFormatter.ofPattern("MM-dd HH:mm").format(
        Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()),
    )

@Composable
fun CourseTasksPage(onBack: () -> Unit) {
    var progress by remember { mutableStateOf(LegacyCampusApi.cachedCourseProgress()) }
    var state by remember {
        mutableStateOf<LoadState<Unit>>(
            if (progress.isEmpty()) LoadState.Loading else LoadState.Ready(Unit),
        )
    }
    var syncing by remember { mutableStateOf(false) }
    var lastSync by remember {
        mutableStateOf(
            LegacyCampusApi.courseProgressLastSync()
                .takeIf { it > 0 }
                ?: progress.maxOfOrNull { it.updatedAt } ?: 0L,
        )
    }
    var showEmptyCourses by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val showToast = LocalNextToast.current

    suspend fun sync() {
        syncing = true
        if (progress.isEmpty()) state = LoadState.Loading
        runCatching {
            withContext(Dispatchers.IO) { LegacyCampusApi.syncCourseProgress().requireValue() }
        }.fold(
            onSuccess = {
                progress = it
                lastSync = LegacyCampusApi.courseProgressLastSync()
                    .takeIf { it > 0 }
                    ?: it.maxOfOrNull { course -> course.updatedAt } ?: 0L
                state = LoadState.Ready(Unit)
            },
            onFailure = {
                val failure = it.toLoadFailure()
                if (progress.isEmpty()) state = failure else showToast(failure.message)
            },
        )
        syncing = false
    }

    LaunchedEffect(Unit) {
        if (progress.isEmpty()) {
            sync()
        } else {
            lastSync = LegacyCampusApi.courseProgressLastSync()
                .takeIf { it > 0 }
                ?: progress.maxOfOrNull { it.updatedAt } ?: 0L
            state = LoadState.Ready(Unit)
        }
    }

    val subtitle = when {
        syncing -> "正在同步课程任务…"
        lastSync > 0 -> "上次更新 ${formatCourseSyncTime(lastSync)}"
        else -> ""
    }

    FeatureScaffold(
        title = "课程任务",
        subtitle = subtitle,
        onBack = onBack,
        actions = {
            SoftIconButton(onClick = { if (!syncing) scope.launch { sync() } }) {
                if (syncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = NextPalette.Accent,
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_iconpark_refresh),
                        contentDescription = "同步课程任务",
                        tint = NextPalette.Ink,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        },
    ) {
        AsyncContent(state, onRetry = { scope.launch { sync() } }) {
            val visible = progress.filter { showEmptyCourses || it.totalCount > 0 }
            val totalDone = progress.sumOf { it.doneCount }
            val totalTasks = progress.sumOf { it.totalCount }
            val average = if (progress.isEmpty()) 0 else progress.sumOf { it.percent } / progress.size
            FeatureScrollColumn {
                PageCard {
                    Row {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("课程", style = MaterialTheme.typography.labelSmall, color = NextPalette.InkSecondary)
                            Text("${progress.size}", style = MaterialTheme.typography.headlineMedium, color = NextPalette.AccentDeep)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("总进度", style = MaterialTheme.typography.labelSmall, color = NextPalette.InkSecondary)
                            Text("$average%", style = MaterialTheme.typography.headlineMedium, color = NextPalette.AccentDeep)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("任务点", style = MaterialTheme.typography.labelSmall, color = NextPalette.InkSecondary)
                            Text("$totalDone/$totalTasks", style = MaterialTheme.typography.titleMedium, color = NextPalette.Ink)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SoftChip(
                            "显示无任务课程",
                            onClick = { showEmptyCourses = !showEmptyCourses },
                            selected = showEmptyCourses,
                        )
                    }
                }
                if (visible.isEmpty()) EmptyState("暂无任务点数据，点右上角同步。")
                visible.forEach { course ->
                    PageCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                course.name,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = NextPalette.Ink,
                            )
                            Text(
                                if (course.totalCount > 0) "${course.percent}%" else "暂无任务点",
                                style = MaterialTheme.typography.labelMedium,
                                color = NextPalette.AccentDeep,
                            )
                        }
                        LinearProgressIndicator(
                            progress = { course.percent / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = NextPalette.Accent,
                            trackColor = NextPalette.AccentSoft,
                        )
                        Text(
                            if (course.totalCount > 0) "已完成任务点 ${course.doneCount}/${course.totalCount}" else "暂无任务点",
                            style = MaterialTheme.typography.labelSmall,
                            color = NextPalette.InkSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PhoneBookPage(onBack: () -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var department by remember { mutableStateOf<String?>(null) }
    val departments = PhoneBookDirectory.groups.keys.toList()

    FeatureScaffold("电话本", onBack) {
        FeatureScrollColumn {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("搜索部门或号码") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            SoftChipStrip {
                SoftChip("全部", onClick = { department = null }, selected = department == null)
                departments.forEach { name ->
                    SoftChip(name, onClick = { department = name }, selected = department == name)
                }
            }
            PhoneBookDirectory.groups.forEach { (group, entries) ->
                if (department != null && department != group) return@forEach
                val visible = entries.filter {
                    query.isBlank() ||
                        group.contains(query, true) ||
                        it.name.contains(query, true) ||
                        it.numbers.any { number -> number.contains(query) }
                }
                if (visible.isEmpty()) return@forEach
                PageCard {
                    Text(group, style = MaterialTheme.typography.titleSmall, color = NextPalette.Ink)
                    visible.forEachIndexed { index, entry ->
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text(entry.name, style = MaterialTheme.typography.bodyMedium, color = NextPalette.Ink)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                entry.numbers.forEach { number ->
                                    Text(
                                        number,
                                        modifier = Modifier.clickable {
                                            runCatching {
                                                context.startActivity(
                                                    Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")),
                                                )
                                            }
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = NextPalette.AccentDeep,
                                    )
                                }
                            }
                        }
                        if (index != visible.lastIndex) HorizontalDivider(color = NextPalette.Line)
                    }
                }
            }
        }
    }
}

@Composable
fun PreferencesPage(
    suggestionsEnabled: Boolean,
    onSuggestionsChange: (Boolean) -> Unit,
    remindersEnabled: Boolean,
    onOpenReminders: () -> Unit,
    onOpenAppearance: () -> Unit,
    onBack: () -> Unit,
) {
    FeatureScaffold("偏好设置", onBack) {
        FeatureScrollColumn {
            PageCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("显示快捷建议", style = MaterialTheme.typography.titleSmall, color = NextPalette.Ink)
                        Text(
                            "空会话时展示作业、课表、成绩等推荐问句。",
                            style = MaterialTheme.typography.labelSmall,
                            color = NextPalette.InkSecondary,
                        )
                    }
                    Switch(checked = suggestionsEnabled, onCheckedChange = onSuggestionsChange)
                }
            }
            PageCard {
                Text("提醒", style = MaterialTheme.typography.titleSmall, color = NextPalette.Ink)
                Text(
                    if (remindersEnabled) "上课提醒已开启" else "上课提醒未开启",
                    style = MaterialTheme.typography.labelSmall,
                    color = NextPalette.InkSecondary,
                )
                SoftPrimaryButton("打开提醒设置", onClick = onOpenReminders, modifier = Modifier.fillMaxWidth())
            }
            PageCard {
                Text("显示", style = MaterialTheme.typography.titleSmall, color = NextPalette.Ink)
                Text(
                    "统一调整聊天、卡片和页面的字号与间距。",
                    style = MaterialTheme.typography.labelSmall,
                    color = NextPalette.InkSecondary,
                )
                SoftPrimaryButton("外观与显示", onClick = onOpenAppearance, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
