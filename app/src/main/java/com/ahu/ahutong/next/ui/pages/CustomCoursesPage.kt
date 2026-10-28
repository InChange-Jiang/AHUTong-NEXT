package com.ahu.ahutong.next.ui.pages

import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.schedule.ScheduleSemester
import com.ahu.ahutong.legacy.LegacyCampusApi
import com.ahu.ahutong.legacy.toChinese
import com.ahu.ahutong.next.R
import com.ahu.ahutong.next.app.data.CustomCourseRecord
import com.ahu.ahutong.next.app.data.CustomCourseSlot
import com.ahu.ahutong.next.app.data.CustomCourseStore
import com.ahu.ahutong.next.app.data.clockText
import com.ahu.ahutong.next.app.data.spanForDisplay
import com.ahu.ahutong.next.app.data.clockRangeForDisplay
import com.ahu.ahutong.next.ui.components.FeatureScaffold
import com.ahu.ahutong.next.ui.components.SoftChip
import com.ahu.ahutong.next.ui.components.SoftChipStrip
import com.ahu.ahutong.next.ui.components.SoftIconButton
import com.ahu.ahutong.next.ui.components.SoftPrimaryButton
import com.ahu.ahutong.next.ui.components.SoftSecondaryButton
import com.ahu.ahutong.next.ui.components.SoftTextField
import com.ahu.ahutong.next.ui.theme.NextPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val customCoursePalette = listOf(
    0xFF82ADF7,
    0xFF7AE3D2,
    0xFF77B6EF,
    0xFFE19BB0,
    0xFFE38874,
    0xFF679ACD,
    0xFFE87897,
    0xFFEBB877,
    0xFFC8A2C8,
    0xFFA8E4A0,
    0xFFFF8A80,
    0xFFB39DDB,
).map { it.toInt() }

private const val MAX_WEEK = 20
private const val MAX_SECTION = 13

@Composable
fun CustomCoursesPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { CustomCourseStore(context) }
    val accountId = remember {
        LegacyCampusApi.currentStudentId().orEmpty().ifBlank { "anon" }
    }
    var records by remember { mutableStateOf(store.load(accountId)) }
    var semesters by remember { mutableStateOf<List<ScheduleSemester>>(emptyList()) }
    var semesterId by remember { mutableStateOf(0) }
    var semesterName by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf<CustomCourseRecord?>(null) }
    var deleting by remember { mutableStateOf<CustomCourseRecord?>(null) }

    LaunchedEffect(Unit) {
        loading = true
        val result = withContext(Dispatchers.IO) {
            runCatching { LegacyCampusApi.scheduleSemesters() }.getOrNull()
        }
        when (result) {
            is AhuResult.Success -> {
                semesters = result.value
                val current = result.value.firstOrNull { it.isCurrent } ?: result.value.firstOrNull()
                semesterId = current?.id ?: 0
                semesterName = current?.name.orEmpty()
                loadError = null
            }
            is AhuResult.Failure -> loadError = result.error.toChinese()
            null -> loadError = "学期信息加载失败，请返回课表刷新后重试"
        }
        loading = false
    }

    BackHandler(enabled = draft != null) { draft = null }

    val editing = draft
    if (editing != null) {
        val isNew = records.none { it.id == editing.id }
        CustomCourseEditor(
            draft = editing,
            semesterName = semesterName,
            isNew = isNew,
            onDraftChange = { draft = it },
            onBack = { draft = null },
            onSave = {
                records = store.add(accountId, editing)
                draft = null
            },
            onDelete = {
                deleting = editing
            },
        )
    } else {
        FeatureScaffold(
            title = "自定义课程",
            onBack = onBack,
            actions = {
                if (!loading && semesterId != 0) {
                    SoftIconButton(onClick = {
                        draft = CustomCourseRecord(
                            semesterId = semesterId,
                            slots = listOf(CustomCourseSlot()),
                        )
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_iconpark_plus),
                            contentDescription = "添加课程",
                            tint = NextPalette.Ink,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            },
        ) {
            val semesterRecords = records.filter { it.semesterId == semesterId }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (semesterName.isNotBlank()) {
                    Text(
                        semesterName,
                        style = MaterialTheme.typography.labelMedium,
                        color = NextPalette.InkSecondary,
                    )
                }
                when {
                    loading -> Text(
                        "正在读取学期…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NextPalette.InkSecondary,
                    )

                    loadError != null -> Text(
                        loadError.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = NextPalette.Danger,
                    )

                    semesterId == 0 -> Text(
                        "还没有确定当前学期，请先登录并打开一次课表后再添加。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NextPalette.InkSecondary,
                    )

                    semesterRecords.isEmpty() -> PageCard {
                        Text(
                            "还没有自定义课程。",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = NextPalette.Ink,
                        )
                        Text(
                            "教务课表里的课会自动同步；自己加的课、晚自习、培训等可以在这里手动添加。",
                            style = MaterialTheme.typography.bodySmall,
                            color = NextPalette.InkSecondary,
                        )
                    }

                    else -> semesterRecords.forEach { record ->
                        CustomCourseListItem(
                            record = record,
                            onEdit = { draft = record },
                            onDelete = { deleting = record },
                        )
                    }
                }

                if (!loading && semesterId != 0) {
                    SoftPrimaryButton(
                        text = "添加课程",
                        onClick = {
                            draft = CustomCourseRecord(
                                semesterId = semesterId,
                                slots = listOf(CustomCourseSlot()),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.size(76.dp))
            }
        }
    }

    deleting?.let { record ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除自定义课程") },
            text = { Text("将从本机移除「${record.name}」，不影响教务课表。") },
            confirmButton = {
                TextButton(onClick = {
                    records = store.delete(accountId, record.id)
                    if (draft?.id == record.id) draft = null
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

@Composable
private fun CustomCourseListItem(
    record: CustomCourseRecord,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    PageCard {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(
                        record.colorArgb?.let { Color(it) } ?: NextPalette.Accent,
                    ),
            )
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    record.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = NextPalette.Ink,
                )
                Text(
                    slotSummary(record),
                    style = MaterialTheme.typography.bodySmall,
                    color = NextPalette.InkSecondary,
                )
                if (record.note.isNotBlank()) {
                    Text(
                        "备注：${record.note}",
                        style = MaterialTheme.typography.bodySmall,
                        color = NextPalette.InkSecondary,
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onEdit) { Text("编辑") }
            TextButton(onClick = onDelete) { Text("删除") }
        }
    }
}

@Composable
private fun CustomCourseEditor(
    draft: CustomCourseRecord,
    semesterName: String,
    isNew: Boolean,
    onDraftChange: (CustomCourseRecord) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    val valid = draft.name.isNotBlank() && draft.slots.isNotEmpty() &&
        draft.slots.all { it.weeks.isNotEmpty() }

    fun updateSlot(index: Int, transform: (CustomCourseSlot) -> CustomCourseSlot) {
        onDraftChange(
            draft.copy(
                slots = draft.slots.mapIndexed { i, slot ->
                    if (i == index) transform(slot) else slot
                },
            ),
        )
    }

    FeatureScaffold(
        title = if (isNew) "添加课程" else "编辑课程",
        onBack = onBack,
        actions = {
            TextButton(onClick = { if (valid) onSave() }, enabled = valid) {
                Text(
                    "保存",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (valid) NextPalette.AccentDeep else NextPalette.InkSecondary,
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
            if (semesterName.isNotBlank()) {
                Text(
                    semesterName,
                    style = MaterialTheme.typography.labelMedium,
                    color = NextPalette.InkSecondary,
                )
            }
            PageCard {
                SoftTextField(
                    value = draft.name,
                    onValueChange = { onDraftChange(draft.copy(name = it.take(40))) },
                    label = "课程名称",
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "颜色",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = NextPalette.Ink,
                )
                CourseColorRow(
                    selected = draft.colorArgb,
                    onSelect = { onDraftChange(draft.copy(colorArgb = it)) },
                )
            }

            draft.slots.forEachIndexed { index, slot ->
                CustomCourseSlotEditor(
                    index = index,
                    slotCount = draft.slots.size,
                    slot = slot,
                    onChange = { updated -> updateSlot(index) { updated } },
                    onCopy = {
                        onDraftChange(draft.copy(slots = draft.slots + slot))
                    },
                    onRemove = {
                        onDraftChange(
                            draft.copy(
                                slots = draft.slots.filterIndexed { i, _ -> i != index },
                            ),
                        )
                    },
                )
            }

            SoftSecondaryButton(
                text = "添加上课时间",
                onClick = {
                    onDraftChange(
                        draft.copy(
                            slots = draft.slots + (draft.slots.lastOrNull() ?: CustomCourseSlot()),
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )

            PageCard {
                SoftTextField(
                    value = draft.teacher,
                    onValueChange = { onDraftChange(draft.copy(teacher = it.take(30))) },
                    label = "教师（可选）",
                    modifier = Modifier.fillMaxWidth(),
                )
                SoftTextField(
                    value = draft.location,
                    onValueChange = { onDraftChange(draft.copy(location = it.take(40))) },
                    label = "地点（可选）",
                    modifier = Modifier.fillMaxWidth(),
                )
                SoftTextField(
                    value = draft.note,
                    onValueChange = { onDraftChange(draft.copy(note = it.take(120))) },
                    label = "备注（可选）",
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SoftPrimaryButton(
                text = "保存课程",
                onClick = { if (valid) onSave() },
                enabled = valid,
                modifier = Modifier.fillMaxWidth(),
            )
            if (!isNew) {
                SoftSecondaryButton(
                    text = "删除此课程",
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.size(76.dp))
        }
    }
}

@Composable
private fun CourseColorRow(selected: Int?, onSelect: (Int?) -> Unit) {
    SoftChipStrip {
        SoftChip("自动", onClick = { onSelect(null) }, selected = selected == null)
        customCoursePalette.forEach { argb ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(argb))
                    .then(
                        if (selected == argb) {
                            Modifier.border(2.dp, NextPalette.Ink, CircleShape)
                        } else {
                            Modifier
                        },
                    )
                    .clickable { onSelect(argb) },
            )
        }
    }
}

@Composable
private fun CustomCourseSlotEditor(
    index: Int,
    slotCount: Int,
    slot: CustomCourseSlot,
    onChange: (CustomCourseSlot) -> Unit,
    onCopy: () -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    var weekDialogVisible by remember { mutableStateOf(false) }
    val span = slot.spanForDisplay()

    PageCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "上课时间 ${index + 1}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = NextPalette.Ink,
            )
            TextButton(onClick = onCopy) { Text("复制") }
            if (slotCount > 1) {
                TextButton(onClick = onRemove) { Text("删除") }
            }
        }

        Text(
            "星期",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = NextPalette.Ink,
        )
        SoftChipStrip {
            (1..7).forEach { day ->
                SoftChip(
                    weekdayShort(day),
                    onClick = { onChange(slot.copy(weekday = day)) },
                    selected = slot.weekday == day,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { weekDialogVisible = true }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "上课周次",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = NextPalette.Ink,
            )
            Text(
                weekSelectionLabel(slot.weeks),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = NextPalette.AccentDeep,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "使用自定义时间",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = NextPalette.Ink,
                )
                Text(
                    "适用于时间不固定的课；课表按最近节次吸附显示",
                    style = MaterialTheme.typography.labelSmall,
                    color = NextPalette.InkSecondary,
                )
            }
            Switch(
                checked = slot.useCustomTime,
                onCheckedChange = { enabled ->
                    onChange(slot.copy(useCustomTime = enabled))
                },
            )
        }

        if (slot.useCustomTime) {
            TimeRow(
                label = "开始时间",
                minutes = slot.startMinutes,
                onPick = {
                    TimePickerDialog(
                        context,
                        { _, hour, minute ->
                            onChange(slot.copy(startMinutes = hour * 60 + minute))
                        },
                        slot.startMinutes / 60,
                        slot.startMinutes % 60,
                        true,
                    ).show()
                },
            )
            TimeRow(
                label = "结束时间",
                minutes = slot.endMinutes,
                onPick = {
                    TimePickerDialog(
                        context,
                        { _, hour, minute ->
                            onChange(slot.copy(endMinutes = hour * 60 + minute))
                        },
                        slot.endMinutes / 60,
                        slot.endMinutes % 60,
                        true,
                    ).show()
                },
            )
            Text(
                "将显示在第 ${span.first}-${span.last} 节",
                style = MaterialTheme.typography.labelMedium,
                color = NextPalette.AccentDeep,
            )
        } else {
            Text(
                "开始节次",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = NextPalette.Ink,
            )
            SoftChipStrip {
                (1..MAX_SECTION).forEach { section ->
                    SoftChip(
                        "第${section}节",
                        onClick = {
                            onChange(
                                slot.copy(
                                    startSection = section,
                                    endSection = slot.endSection.coerceAtLeast(section),
                                ),
                            )
                        },
                        selected = slot.startSection == section,
                    )
                }
            }
            Text(
                "结束节次",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = NextPalette.Ink,
            )
            SoftChipStrip {
                (slot.startSection..MAX_SECTION).forEach { section ->
                    SoftChip(
                        "第${section}节",
                        onClick = { onChange(slot.copy(endSection = section)) },
                        selected = slot.endSection == section,
                    )
                }
            }
        }
    }

    if (weekDialogVisible) {
        WeekSelectionDialog(
            initial = slot.weeks,
            onDismiss = { weekDialogVisible = false },
            onConfirm = { weeks ->
                weekDialogVisible = false
                onChange(slot.copy(weeks = weeks))
            },
        )
    }
}

@Composable
private fun TimeRow(label: String, minutes: Int, onPick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onPick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = NextPalette.Ink,
        )
        Text(
            minutes.clockText(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = NextPalette.AccentDeep,
        )
    }
}

@Composable
private fun WeekSelectionDialog(
    initial: List<Int>,
    onDismiss: () -> Unit,
    onConfirm: (List<Int>) -> Unit,
) {
    var weeks by remember { mutableStateOf(initial.distinct().sorted().toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择上课周次") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "已选 ${weeks.size} 周",
                    style = MaterialTheme.typography.labelMedium,
                    color = NextPalette.InkSecondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    listOf(
                        "全选" to { (1..MAX_WEEK).toSet() },
                        "单周" to { (1..MAX_WEEK).filter { it % 2 == 1 }.toSet() },
                        "双周" to { (1..MAX_WEEK).filter { it % 2 == 0 }.toSet() },
                        "清空" to { emptySet<Int>() },
                    ).forEach { (label, action) ->
                        TextButton(onClick = { weeks = action() }) {
                            Text(label, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                (1..MAX_WEEK).chunked(5).forEach { rowWeeks ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        rowWeeks.forEach { week ->
                            SoftChip(
                                "$week",
                                onClick = {
                                    weeks = if (week in weeks) weeks - week else weeks + week
                                },
                                selected = week in weeks,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(weeks.sorted()) }) { Text("完成") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

private fun slotSummary(record: CustomCourseRecord): String {
    if (record.slots.isEmpty()) return "时间待完善"
    return record.slots.joinToString("；") { slot ->
        val span = slot.spanForDisplay()
        val section = if (span.first == span.last) {
            "第${span.first}节"
        } else {
            "第${span.first}-${span.last}节"
        }
        val clock = slot.clockRangeForDisplay()?.let { " $it" }.orEmpty()
        "${weekdayShort(slot.weekday)} $section$clock ${weekSelectionLabel(slot.weeks)}"
    }
}

private fun weekSelectionLabel(weeks: List<Int>): String {
    val sorted = weeks.filter { it in 1..MAX_WEEK }.distinct().sorted()
    if (sorted.isEmpty()) return "未选择"
    if (sorted.size == MAX_WEEK) return "全部 $MAX_WEEK 周"
    val first = sorted.first()
    val last = sorted.last()
    if (sorted.size == 1) return "第${first}周"
    if (sorted.zipWithNext().all { (a, b) -> b - a == 1 }) {
        return "第${first}-${last}周"
    }
    if (sorted.zipWithNext().all { (a, b) -> b - a == 2 }) {
        return "第${first}-${last}${if (first % 2 == 0) "双" else "单"}周"
    }
    return sorted.joinToString("/") + "周"
}

private fun weekdayShort(day: Int): String = when (day) {
    1 -> "周一"
    2 -> "周二"
    3 -> "周三"
    4 -> "周四"
    5 -> "周五"
    6 -> "周六"
    else -> "周日"
}
