package com.ahu.ahutong.next.ui.pages

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.data.EvalQuestionnaireForm
import com.ahu.ahutong.data.model.EvalAnswerOption
import com.ahu.ahutong.data.model.EvalQuestion
import com.ahu.ahutong.data.model.EvalQuestionAnswer
import com.ahu.ahutong.data.model.EvalQuestionnaireRes
import com.ahu.ahutong.data.model.EvalSemester
import com.ahu.ahutong.data.model.EvalSubmitRequest
import com.ahu.ahutong.data.model.EvalTask
import com.ahu.ahutong.data.model.EvalTeacher
import com.ahu.ahutong.legacy.LegacyCampusApi
import com.ahu.ahutong.next.ui.components.AsyncContent
import com.ahu.ahutong.next.ui.components.EmptyState
import com.ahu.ahutong.next.ui.components.FeatureScaffold
import com.ahu.ahutong.next.ui.components.FeatureScrollColumn
import com.ahu.ahutong.next.ui.components.LoadState
import com.ahu.ahutong.next.ui.components.RefreshIconButton
import com.ahu.ahutong.next.ui.components.SoftChip
import com.ahu.ahutong.next.ui.components.SoftChipStrip
import com.ahu.ahutong.next.ui.components.SoftPrimaryButton
import com.ahu.ahutong.next.ui.components.toLoadFailure
import com.ahu.ahutong.next.ui.theme.NextPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import java.util.Locale

@Composable
fun EvaluationPage(onBack: () -> Unit) {
    var semesters by remember { mutableStateOf<List<EvalSemester>>(emptyList()) }
    var selected by remember { mutableStateOf<EvalSemester?>(null) }
    var tasks by remember { mutableStateOf<List<EvalTask>>(emptyList()) }
    var state by remember { mutableStateOf<LoadState<Unit>>(LoadState.Loading) }
    var pending by remember { mutableStateOf<Pair<EvalTask, EvalTeacher>?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var bulkRunning by remember { mutableStateOf(false) }
    var showBulkConfirm by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(reload) {
        state = LoadState.Loading
        val result = runCatching {
            withContext(Dispatchers.IO) {
                val list = LegacyCampusApi.evaluationSemesters().requireValue()
                val current = selected ?: list.firstOrNull { it.id.isNotBlank() }
                val tasks = current?.let { LegacyCampusApi.evaluationTasks(it.id).requireValue() }.orEmpty()
                Triple(list, current, tasks)
            }
        }
        result.fold(
            onSuccess = { (list, current, listTasks) ->
                semesters = list
                selected = current
                tasks = listTasks
                state = LoadState.Ready(Unit)
            },
            onFailure = { state = it.toLoadFailure() },
        )
    }

    fun selectSemester(semester: EvalSemester) {
        selected = semester
        scope.launch {
            state = LoadState.Loading
            runCatching {
                withContext(Dispatchers.IO) { LegacyCampusApi.evaluationTasks(semester.id).requireValue() }
            }.fold(
                onSuccess = { tasks = it; state = LoadState.Ready(Unit) },
                onFailure = { state = it.toLoadFailure() },
            )
        }
    }

    FeatureScaffold(
        title = "评教",
        onBack = onBack,
        actions = { RefreshIconButton { reload++ } },
    ) {
        if (semesters.isNotEmpty()) {
            SoftChipStrip(modifier = Modifier.padding(horizontal = 18.dp)) {
                semesters.forEach { semester ->
                    SoftChip(
                        semester.nameZh.ifBlank { semester.schoolYear },
                        onClick = { selectSemester(semester) },
                        selected = selected?.id == semester.id,
                    )
                }
            }
        }
        AsyncContent(state, onRetry = { reload++ }) {
            if (tasks.isEmpty()) {
                EmptyState("这个学期没有待评教任务。")
            } else {
                FeatureScrollColumn {
                    if (tasks.isNotEmpty()) {
                        SoftPrimaryButton(
                            if (bulkRunning) "正在提交…" else "按预设完成全部",
                            enabled = !bulkRunning,
                            onClick = { showBulkConfirm = true },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Text(
                        "共 ${tasks.sumOf { it.teachers.size }} 个待评价教师",
                        style = MaterialTheme.typography.labelSmall,
                        color = NextPalette.InkSecondary,
                    )
                    tasks.forEach { task ->
                        PageCard {
                            Text(task.evaluationQuestionnaireName, style = MaterialTheme.typography.titleSmall, color = NextPalette.Ink)
                            Text(
                                "${task.teachers.firstOrNull()?.teacherName.orEmpty()} · ${task.days}",
                                style = MaterialTheme.typography.labelSmall,
                                color = NextPalette.InkSecondary,
                            )
                            task.teachers.forEach { teacher ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            teacher.teacherName.ifBlank { "教师 ${teacher.num}" },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = NextPalette.Ink,
                                        )
                                        Text(
                                            if (teacher.status.isBlank()) "未评价" else teacher.status,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = NextPalette.InkSecondary,
                                        )
                                    }
                                    TextButton(onClick = { pending = task to teacher }) { Text("一键评价") }
                                }
                            }
                        }
                    }
                    Text(
                        "一键评价会用每个问题的第一个选项和一段通用评语代填，提交前会再次确认。",
                        style = MaterialTheme.typography.labelSmall,
                        color = NextPalette.InkSecondary,
                    )
                }
            }
        }
    }

    if (showBulkConfirm) {
        val total = tasks.sumOf { it.teachers.size }
        AlertDialog(
            onDismissRequest = { showBulkConfirm = false },
            title = { Text("完成全部评教？") },
            text = { Text("将按默认选项和通用评语提交 $total 位教师的评教，提交后不可撤回。") },
            confirmButton = {
                TextButton(onClick = {
                    showBulkConfirm = false
                    bulkRunning = true
                    scope.launch {
                        var done = 0
                        var failed = 0
                        for (task in tasks) {
                            for (teacher in task.teachers) {
                                runCatching {
                                    withContext(Dispatchers.IO) { submitDefaultEvaluation(task, teacher) }
                                }.onSuccess { done++ }.onFailure { failed++ }
                            }
                        }
                        bulkRunning = false
                        message = "已完成 $done 项" + if (failed > 0) "，$failed 项失败，请稍后重试" else ""
                        reload++
                    }
                }) { Text("确认提交") }
            },
            dismissButton = { TextButton(onClick = { showBulkConfirm = false }) { Text("取消") } },
        )
    }

    pending?.let { (task, teacher) ->
        AlertDialog(
            onDismissRequest = { if (!submitting) pending = null },
            title = { Text("确认提交评教") },
            text = {
                Text(
                    "将为「${teacher.teacherName.ifBlank { task.evaluationQuestionnaireName }}」按默认选项提交评教。提交后不可撤回。",
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !submitting,
                    onClick = {
                        submitting = true
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) { submitDefaultEvaluation(task, teacher) }
                            }.fold(
                                onSuccess = {
                                    message = "评教已提交"
                                    pending = null
                                    reload++
                                },
                                onFailure = { message = it.message ?: "评教提交失败" },
                            )
                            submitting = false
                        }
                    },
                ) { Text(if (submitting) "提交中…" else "确认提交") }
            },
            dismissButton = { TextButton(enabled = !submitting, onClick = { pending = null }) { Text("取消") } },
        )
    }

    message?.let {
        AlertDialog(
            onDismissRequest = { message = null },
            title = { Text("评教") },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("知道了") } },
        )
    }
}

private suspend fun submitDefaultEvaluation(task: EvalTask, teacher: EvalTeacher) {
    val form = LegacyCampusApi.evaluationForm(task.evaluationQuestionnaireId).requireValue()
    val request = buildDefaultRequest(task, teacher, form)
    LegacyCampusApi.submitEvaluation(request).requireValue()
}

private fun buildDefaultRequest(
    task: EvalTask,
    teacher: EvalTeacher,
    form: EvalQuestionnaireForm,
): EvalSubmitRequest {
    val answers = form.questions.map { question ->
        val attr = question.attribute
        val questionId = attr.id.toString()
        when (attr.typeId) {
            1 -> {
                val option = question.options.firstOrNull()
                EvalQuestionAnswer(
                    questionId = questionId,
                    questionnaireId = form.questionnaire.id,
                    type = "1",
                    score = if (attr.enableScore) option?.optionScore ?: 0.0 else 0.0,
                    answer = null,
                    questionAnsExpSaveList = listOf(
                        EvalAnswerOption(
                            optionId = option?.optionId?.toString().orEmpty(),
                            questionnaireId = form.questionnaire.id,
                            value = option?.value,
                            score = option?.optionScore,
                        ),
                    ),
                )
            }
            4 -> EvalQuestionAnswer(
                questionId = questionId,
                questionnaireId = form.questionnaire.id,
                type = "4",
                score = 0.0,
                answer = "老师授课认真负责，课堂内容清晰，学习收获较多。",
                questionAnsExpSaveList = emptyList(),
            )
            else -> EvalQuestionAnswer(
                questionId = questionId,
                questionnaireId = form.questionnaire.id,
                type = attr.typeId.toString(),
                score = 0.0,
                answer = null,
                questionAnsExpSaveList = emptyList(),
            )
        }
    }
    return EvalSubmitRequest(
        stdSumTaskId = teacher.stdSumTaskId.ifBlank { task.stdSumTaskId },
        anonymous = false,
        evaluationQuestionnaireRes = EvalQuestionnaireRes(
            questionnaireId = form.questionnaire.id,
            questionnaireName = form.questionnaire.nameZh.ifBlank { task.evaluationQuestionnaireName },
            enable = form.questionnaire.enable,
            answer = "[]",
            score = 0.0,
            questionAnsSaveList = answers,
        ),
    )
}

@Composable
fun SchoolCalendarPage(onBack: () -> Unit) {
    var years by remember { mutableStateOf<List<String>>(emptyList()) }
    var selected by remember { mutableStateOf<String?>(null) }
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var state by remember { mutableStateOf<LoadState<Unit>>(LoadState.Loading) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(reload) {
        state = LoadState.Loading
        runCatching {
            withContext(Dispatchers.IO) {
                val response = LegacyCampusApi.schoolCalendarYears().requireValue()
                val list = response.years.ifEmpty { listOfNotNull(response.latestYear) }
                val year = selected ?: response.latestYear ?: list.firstOrNull()
                val bytes = if (year != null) LegacyCampusApi.schoolCalendar(year).requireValue() else null
                Triple(list, year, bytes)
            }
        }.fold(
            onSuccess = { (list, year, bytes) ->
                years = list
                selected = year
                bitmap = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                state = LoadState.Ready(Unit)
            },
            onFailure = { state = it.toLoadFailure() },
        )
    }

    FeatureScaffold(
        title = "校历",
        onBack = onBack,
        actions = { RefreshIconButton { reload++ } },
    ) {
        if (years.isNotEmpty()) {
            SoftChipStrip(modifier = Modifier.padding(horizontal = 18.dp)) {
                years.forEach { year ->
                    SoftChip(
                        year,
                        onClick = {
                            selected = year
                            reload++
                        },
                        selected = selected == year,
                    )
                }
            }
        }
        AsyncContent(state, onRetry = { reload++ }) {
            val image = bitmap
            if (image == null) {
                EmptyState("暂未获取到校历图片。")
            } else {
                Image(
                    bitmap = image.asImageBitmap(),
                    contentDescription = "校历",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(image.width.toFloat() / image.height.toFloat()),
                )
            }
        }
    }
}
