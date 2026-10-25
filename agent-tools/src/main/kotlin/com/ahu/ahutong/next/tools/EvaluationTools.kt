package com.ahu.ahutong.next.tools

import com.ahu.ahutong.legacy.LegacyCampusApi
import com.ahu.ahutong.legacy.toChinese
import com.ahu.ahutong.next.agent.Tool
import com.ahu.ahutong.next.agent.ToolError
import com.ahu.ahutong.next.agent.ToolKind
import com.ahu.ahutong.next.agent.ToolOutput
import com.ahu.ahutong.next.agent.ToolSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class GetEvaluationTasksTool : Tool {
    override val spec = ToolSpec(
        name = "get_evaluation_tasks",
        displayName = "查询待评任务",
        description = "查询评教待办。不传 semester 时使用最近一个学期；返回学期、问卷和待评价教师。",
        parameters = schema(
            properties = buildJsonObject {
                put("semester", stringProp("学期名称或 ID，例如 2025-2026 学年第二学期；不传用最近学期"))
            },
        ),
        timeoutMs = 60_000,
        kind = ToolKind.READ,
        source = "评教系统",
    )

    override suspend fun execute(arguments: JsonObject): ToolOutput = withContext(Dispatchers.IO) {
        if (!LegacyCampusApi.isLoggedIn()) {
            return@withContext ToolOutput(false, error = ToolError("not_logged_in", "尚未登录安大通账号"))
        }
        val semesters = when (val result = LegacyCampusApi.evaluationSemesters()) {
            is com.ahu.ahutong.core.common.AhuResult.Failure ->
                return@withContext ToolOutput(false, error = ToolError("evaluation_failed", result.error.toChinese()))
            is com.ahu.ahutong.core.common.AhuResult.Success -> result.value
        }
        val keyword = arguments.stringOrNull("semester")
        val selected = semesters.firstOrNull { it.id == keyword }
            ?: semesters.firstOrNull { keyword != null && (it.nameZh.contains(keyword) || it.schoolYear.contains(keyword)) }
            ?: semesters.firstOrNull()
            ?: return@withContext ToolOutput(false, error = ToolError("evaluation_empty", "没有可评教学期"))

        val tasks = when (val result = LegacyCampusApi.evaluationTasks(selected.id, evaluated = false)) {
            is com.ahu.ahutong.core.common.AhuResult.Failure ->
                return@withContext ToolOutput(false, error = ToolError("evaluation_failed", result.error.toChinese()))
            is com.ahu.ahutong.core.common.AhuResult.Success -> result.value
        }
        ToolOutput(
            ok = true,
            data = buildJsonObject {
                put("semesters", buildJsonArray {
                    semesters.forEach { semester ->
                        add(buildJsonObject {
                            put("id", semester.id)
                            put("name", semester.nameZh.ifBlank { semester.schoolYear })
                        })
                    }
                })
                put("selectedSemester", selected.nameZh.ifBlank { selected.schoolYear })
                put("tasks", buildJsonArray {
                    tasks.forEach { task ->
                        add(buildJsonObject {
                            put("questionnaire", task.evaluationQuestionnaireName)
                            put("questionnaireId", task.evaluationQuestionnaireId)
                            put("daysLeft", task.days)
                            put("teachers", buildJsonArray {
                                task.teachers.forEach { teacher ->
                                    add(buildJsonObject {
                                        put("name", teacher.teacherName)
                                        put("status", teacher.status)
                                        put("stdSumTaskId", teacher.stdSumTaskId.ifBlank { task.stdSumTaskId })
                                    })
                                }
                            })
                        })
                    }
                })
            },
        )
    }
}

class SubmitEvaluationTool : Tool {
    override val spec = ToolSpec(
        name = "submit_evaluation",
        displayName = "按预设提交评教",
        description = "按每个问卷的第一项和通用评语代填评教，提交后不可撤回。只有用户明确要求提交评教时才能调用。" +
            "teacher 指定教师姓名；all=true 表示提交该学期全部待评教师。",
        parameters = schema(
            properties = buildJsonObject {
                put("semester", stringProp("学期名称或 ID；不传用最近学期"))
                put("teacher", stringProp("教师姓名；与 all 二选一"))
                put("all", boolProp("是否提交该学期全部待评教师，默认 false"))
            },
        ),
        timeoutMs = 180_000,
        kind = ToolKind.WRITE,
        source = "评教系统",
        requiresConfirmation = true,
    )

    override suspend fun execute(arguments: JsonObject): ToolOutput = withContext(Dispatchers.IO) {
        if (!LegacyCampusApi.isLoggedIn()) {
            return@withContext ToolOutput(false, error = ToolError("not_logged_in", "尚未登录安大通账号"))
        }
        val semesters = when (val result = LegacyCampusApi.evaluationSemesters()) {
            is com.ahu.ahutong.core.common.AhuResult.Failure ->
                return@withContext ToolOutput(false, error = ToolError("evaluation_failed", result.error.toChinese()))
            is com.ahu.ahutong.core.common.AhuResult.Success -> result.value
        }
        val keyword = arguments.stringOrNull("semester")
        val selected = semesters.firstOrNull { it.id == keyword }
            ?: semesters.firstOrNull { keyword != null && (it.nameZh.contains(keyword) || it.schoolYear.contains(keyword)) }
            ?: semesters.firstOrNull()
            ?: return@withContext ToolOutput(false, error = ToolError("evaluation_empty", "没有可评教学期"))
        val tasks = when (val result = LegacyCampusApi.evaluationTasks(selected.id, evaluated = false)) {
            is com.ahu.ahutong.core.common.AhuResult.Failure ->
                return@withContext ToolOutput(false, error = ToolError("evaluation_failed", result.error.toChinese()))
            is com.ahu.ahutong.core.common.AhuResult.Success -> result.value
        }
        val teacherName = arguments.stringOrNull("teacher")
        val all = arguments.boolOr("all", false)
        if (!all && teacherName == null) {
            return@withContext ToolOutput(
                false,
                error = ToolError("invalid_arguments", "请指定 teacher，或把 all 设为 true 提交全部"),
            )
        }
        val targets = tasks.flatMap { task ->
            task.teachers.filter { teacherName == null || it.teacherName.contains(teacherName) }
                .map { task to it }
        }
        if (targets.isEmpty()) {
            return@withContext ToolOutput(false, error = ToolError("evaluation_empty", "没有匹配的待评教师"))
        }
        val failures = mutableListOf<String>()
        var submitted = 0
        targets.forEach { (task, teacher) ->
            when (val result = LegacyCampusApi.submitDefaultEvaluation(task, teacher)) {
                is com.ahu.ahutong.core.common.AhuResult.Success -> submitted++
                is com.ahu.ahutong.core.common.AhuResult.Failure ->
                    failures += "${teacher.teacherName}: ${result.error.toChinese()}"
            }
        }
        ToolOutput(
            ok = failures.isEmpty(),
            data = buildJsonObject {
                put("submitted", submitted)
                put("failed", failures.size)
                put("failures", buildJsonArray { failures.forEach { add(JsonPrimitive(it)) } })
                put("semester", selected.nameZh.ifBlank { selected.schoolYear })
            },
            error = if (failures.isEmpty()) null else ToolError("evaluation_partial", failures.joinToString("；")),
        )
    }
}
