package com.ahu.ahutong.next.ui.pages

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.ahu.ahutong.next.ui.components.LoadState
import com.ahu.ahutong.next.ui.components.SoftChip
import com.ahu.ahutong.next.ui.components.SoftChipStrip
import com.ahu.ahutong.next.ui.components.SoftPrimaryButton
import com.ahu.ahutong.next.ui.components.toLoadFailure
import com.ahu.ahutong.next.ui.theme.NextPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun CourseTasksPage(onBack: () -> Unit) {
    var progress by remember { mutableStateOf(LegacyCampusApi.cachedCourseProgress()) }
    var state by remember { mutableStateOf<LoadState<Unit>>(LoadState.Loading) }
    var syncing by remember { mutableStateOf(false) }
    var showEmptyCourses by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun sync() {
        syncing = true
        state = LoadState.Loading
        runCatching {
            withContext(Dispatchers.IO) { LegacyCampusApi.syncCourseProgress().requireValue() }
        }.fold(
            onSuccess = { progress = it; state = LoadState.Ready(Unit) },
            onFailure = { state = it.toLoadFailure() },
        )
        syncing = false
    }

    LaunchedEffect(Unit) {
        if (progress.isEmpty()) sync() else state = LoadState.Ready(Unit)
    }

    FeatureScaffold(
        title = "课程任务",
        onBack = onBack,
        actions = {
            TextButton(enabled = !syncing, onClick = { scope.launch { sync() } }) {
                Text(if (syncing) "同步中…" else "同步")
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

private data class PhoneEntry(val name: String, val numbers: List<String>)

private val phoneBook: Map<String, List<PhoneEntry>> = mapOf(
    "师生综合服务大厅" to listOf(
        PhoneEntry("咨询台", listOf("63861400")),
        PhoneEntry("财务处", listOf("63861322", "65107303")),
        PhoneEntry("研究生院", listOf("63861455", "65107332")),
        PhoneEntry("教务处", listOf("63861055", "65107135")),
        PhoneEntry("双创学院", listOf("65106065")),
        PhoneEntry("学生处（学生发展中心）", listOf("63861686", "65107232")),
        PhoneEntry("国际合作与交流处", listOf("63861848")),
        PhoneEntry("学校办公室", listOf("63861659", "65107020")),
        PhoneEntry("网络信息中心", listOf("63861855", "65107421")),
        PhoneEntry("保卫处", listOf("63861918")),
        PhoneEntry("人事处", listOf("63861922")),
        PhoneEntry("国有资产管理与实验室管理处", listOf("63861949", "65107310")),
        PhoneEntry("人文社会科学处", listOf("63861958")),
        PhoneEntry("科学技术处", listOf("63861983")),
        PhoneEntry("校团委", listOf("65107537")),
    ),
    "学生处（心理健康教育中心）" to listOf(
        PhoneEntry("学生思想教育科", listOf("63861900")),
        PhoneEntry("学生管理科", listOf("63861054")),
        PhoneEntry("学生资助管理中心", listOf("63861900")),
        PhoneEntry("学生就业指导中心", listOf("63861355", "63861383")),
        PhoneEntry("心理健康教育中心", listOf("63861590")),
    ),
    "校团委" to listOf(PhoneEntry("团委办公室", listOf("63861121", "63861196"))),
    "图书馆" to listOf(PhoneEntry("图书馆", listOf("63861109"))),
    "校医院（医保办）" to listOf(
        PhoneEntry("24 小时值班电话", listOf("63861120")),
        PhoneEntry("校医疗保障委员会办公室", listOf("63861715")),
    ),
    "后勤服务" to listOf(
        PhoneEntry("学校物业监管办公室", listOf("63861358")),
        PhoneEntry("餐饮监管办公室", listOf("63861398")),
    ),
    "报警电话" to listOf(
        PhoneEntry("保卫处（优先联系）", listOf("63861110", "65108229")),
        PhoneEntry("三里庵派出所", listOf("63636594")),
        PhoneEntry("芙蓉派出所", listOf("63822505")),
    ),
    "磬苑后勤" to listOf(
        PhoneEntry("校园报修电话", listOf("63861118")),
        PhoneEntry("服务监督电话", listOf("63861722")),
        PhoneEntry("北区 24 小时报修电话", listOf("62950090")),
        PhoneEntry("南区 24 小时报修电话", listOf("63861333")),
        PhoneEntry("桃园", listOf("63861034")),
        PhoneEntry("李园", listOf("63861037")),
        PhoneEntry("桔园", listOf("63861036")),
        PhoneEntry("枣园", listOf("63861218")),
        PhoneEntry("榴园", listOf("63861217")),
        PhoneEntry("杏园", listOf("63861219")),
        PhoneEntry("蕙8", listOf("63861654")),
        PhoneEntry("蕙9", listOf("63861617")),
        PhoneEntry("蕙10", listOf("63861697")),
        PhoneEntry("松园", listOf("63861160")),
        PhoneEntry("竹园", listOf("63861115")),
        PhoneEntry("梅园", listOf("63861113")),
        PhoneEntry("桂园", listOf("63861097")),
        PhoneEntry("枫园", listOf("63861096")),
        PhoneEntry("槐园", listOf("63861081")),
        PhoneEntry("留学生公寓", listOf("63861096")),
    ),
    "龙河后勤" to listOf(
        PhoneEntry("宿舍管理办公室", listOf("65107014")),
        PhoneEntry("校园报修电话", listOf("63861118")),
        PhoneEntry("服务监督电话", listOf("63861722")),
        PhoneEntry("206楼", listOf("65107064")),
        PhoneEntry("207-208楼", listOf("65108150")),
        PhoneEntry("209楼", listOf("65107940")),
        PhoneEntry("301楼", listOf("65108004")),
        PhoneEntry("304-306楼", listOf("65108004")),
    ),
)

@Composable
fun PhoneBookPage(onBack: () -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var department by remember { mutableStateOf<String?>(null) }
    val departments = phoneBook.keys.toList()

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
            phoneBook.forEach { (group, entries) ->
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
