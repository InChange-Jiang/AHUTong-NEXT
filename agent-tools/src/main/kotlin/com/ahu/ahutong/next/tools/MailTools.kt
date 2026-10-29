package com.ahu.ahutong.next.tools

import com.ahu.ahutong.data.mail.MailDraft
import com.ahu.ahutong.data.mail.StudentMailRepository
import com.ahu.ahutong.next.agent.Tool
import com.ahu.ahutong.next.agent.ToolError
import com.ahu.ahutong.next.agent.ToolKind
import com.ahu.ahutong.next.agent.ToolOutput
import com.ahu.ahutong.next.agent.ToolSpec
import com.ahu.ahutong.next.agent.ToolTrust
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class GetMailTool : Tool {
    private val mail = StudentMailRepository()

    override val spec = ToolSpec(
        name = "get_mail",
        displayName = "查询学生邮箱",
        description = "查询或搜索学生邮箱。不传 message_id 时返回文件夹和邮件列表；传 message_id 时返回邮件正文与附件。" +
            "folder 可传文件夹名（如 收件箱）或 ID；query 会在主题和发件人中搜索。",
        parameters = schema(
            properties = buildJsonObject {
                put("folder", stringProp("文件夹名称或 ID，默认 收件箱"))
                put("query", stringProp("搜索关键词，匹配主题或发件人"))
                put("unread_only", boolProp("只看未读，默认 false"))
                put("message_id", stringProp("邮件 ID；传入时返回正文"))
                put("limit", intProp("列表条数，默认 20，最多 30", minimum = 1, maximum = 30))
            },
        ),
        timeoutMs = 60_000,
        kind = ToolKind.READ,
        source = "学生邮箱",
    )

    override suspend fun execute(arguments: JsonObject): ToolOutput = withContext(Dispatchers.IO) {
        runCatching {
            mail.connect()
            val messageId = arguments.stringOrNull("message_id")
            if (messageId != null) {
                val detail = mail.detail(messageId)
                return@withContext ToolOutput(
                    ok = true,
                    data = buildJsonObject {
                        put("subject", detail.subject)
                        put("from", detail.from.joinToString(", "))
                        put("to", detail.to.joinToString(", "))
                        put("cc", detail.cc.joinToString(", "))
                        put("text", detail.text.ifBlank { detail.html })
                        put("attachments", buildJsonArray {
                            detail.attachments.forEach { attachment ->
                                add(buildJsonObject {
                                    put("name", attachment.name)
                                    put("size", attachment.size)
                                })
                            }
                        })
                    },
                )
            }

            val folders = mail.folders()
            val folderKey = arguments.stringOrNull("folder") ?: "收件箱"
            val folder = folders.firstOrNull { it.id.toString() == folderKey }
                ?: folders.firstOrNull { it.name.contains(folderKey) }
                ?: folders.firstOrNull()
                ?: return@withContext ToolOutput(false, error = ToolError("mail_empty", "邮箱没有可用文件夹"))
            val unreadOnly = arguments.boolOr("unread_only", false)
            val page = mail.messages(folder.id, 0, unreadOnly)
            val query = arguments.stringOrNull("query")
            val limit = arguments.intOr("limit", 20).coerceIn(1, 30)
            val messages = page.messages.filter { message ->
                query == null ||
                    message.subject.contains(query, ignoreCase = true) ||
                    message.from.contains(query, ignoreCase = true)
            }.take(limit)
            ToolOutput(
                ok = true,
                data = buildJsonObject {
                    put("folder", folder.name)
                    put("total", page.total)
                    put("folders", buildJsonArray {
                        folders.forEach { item ->
                            add(buildJsonObject {
                                put("id", item.id)
                                put("name", item.name)
                                put("unread", item.unread)
                            })
                        }
                    })
                    put("messages", buildJsonArray {
                        messages.forEach { message ->
                            add(buildJsonObject {
                                put("id", message.id)
                                put("subject", message.subject.ifBlank { "(无主题)" })
                                put("from", message.from)
                                put("date", message.date)
                                put("summary", message.summary)
                                put("read", message.read)
                            })
                        }
                    })
                },
                trust = ToolTrust.UNTRUSTED,
            )
        }.getOrElse { error ->
            ToolOutput(false, error = ToolError("mail_failed", error.message ?: "邮箱查询失败"))
        }
    }
}

class SendMailTool : Tool {
    private val mail = StudentMailRepository()

    override val spec = ToolSpec(
        name = "send_mail",
        displayName = "发送学生邮箱邮件",
        description = "使用学生邮箱发送一封邮件。只有用户明确给出收件人、主题和正文并要求发送时才能调用；发送后不可撤回。",
        parameters = schema(
            properties = buildJsonObject {
                put("to", stringProp("收件人邮箱，多个用逗号分隔"))
                put("cc", stringProp("抄送邮箱，可选"))
                put("subject", stringProp("主题"))
                put("body", stringProp("正文"))
            },
            required = listOf("to", "subject", "body"),
        ),
        timeoutMs = 120_000,
        kind = ToolKind.WRITE,
        source = "学生邮箱",
        requiresConfirmation = true,
    )

    override suspend fun execute(arguments: JsonObject): ToolOutput = withContext(Dispatchers.IO) {
        val to = arguments.stringOrNull("to")
            ?: return@withContext ToolOutput(false, error = ToolError("invalid_arguments", "缺少收件人"))
        val subject = arguments.stringOrNull("subject")
            ?: return@withContext ToolOutput(false, error = ToolError("invalid_arguments", "缺少主题"))
        val body = arguments.stringOrNull("body")
            ?: return@withContext ToolOutput(false, error = ToolError("invalid_arguments", "缺少正文"))
        val cc = arguments.stringOrNull("cc").orEmpty()
        runCatching {
            mail.connect()
            val draft = mail.beginDraft(MailDraft(to = to, cc = cc, subject = subject, body = body))
            mail.send(draft)
        }.fold(
            onSuccess = {
                ToolOutput(true, data = buildJsonObject { put("sent", true) })
            },
            onFailure = { error ->
                ToolOutput(false, error = ToolError("mail_send_failed", error.message ?: "邮件发送失败"))
            },
        )
    }
}
