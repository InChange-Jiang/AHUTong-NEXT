package com.ahu.ahutong.next.ui.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.data.mail.MailDetail
import com.ahu.ahutong.data.mail.MailDraft
import com.ahu.ahutong.data.mail.MailFolder
import com.ahu.ahutong.data.mail.MailMessage
import com.ahu.ahutong.data.mail.StudentMailRepository
import com.ahu.ahutong.data.repository.DownloadedFile
import com.ahu.ahutong.data.repository.GitHubContentItem
import com.ahu.ahutong.data.repository.RepositoryManager
import com.ahu.ahutong.next.ui.components.AsyncContent
import com.ahu.ahutong.next.ui.components.EmptyState
import com.ahu.ahutong.next.ui.components.FeatureScaffold
import com.ahu.ahutong.next.ui.components.FeatureScrollColumn
import com.ahu.ahutong.next.ui.components.LoadState
import com.ahu.ahutong.next.ui.components.SoftChip
import com.ahu.ahutong.next.ui.components.SoftPrimaryButton
import com.ahu.ahutong.next.ui.components.SoftSecondaryButton
import com.ahu.ahutong.next.ui.components.toLoadFailure
import com.ahu.ahutong.next.ui.markdown.AppMarkdown
import com.ahu.ahutong.next.ui.theme.NextPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MailPage(onBack: () -> Unit) {
    val mail = remember { StudentMailRepository() }
    var folders by remember { mutableStateOf<List<MailFolder>>(emptyList()) }
    var selectedFolder by remember { mutableStateOf<MailFolder?>(null) }
    var messages by remember { mutableStateOf<List<MailMessage>>(emptyList()) }
    var unreadOnly by remember { mutableStateOf(false) }
    var selectedMessage by remember { mutableStateOf<MailDetail?>(null) }
    var state by remember { mutableStateOf<LoadState<Unit>>(LoadState.Loading) }
    var error by remember { mutableStateOf<String?>(null) }
    var showCompose by remember { mutableStateOf(false) }
    var showContacts by remember { mutableStateOf(false) }
    var contacts by remember { mutableStateOf<List<com.ahu.ahutong.data.mail.MailContact>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var reload by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    fun loadFolder(folder: MailFolder, unread: Boolean) {
        scope.launch {
            state = LoadState.Loading
            runCatching {
                withContext(Dispatchers.IO) {
                    mail.messages(folder.id, 0, unread).messages
                }
            }.fold(
                onSuccess = { messages = it; state = LoadState.Ready(Unit) },
                onFailure = { state = it.toLoadFailure() },
            )
        }
    }

    LaunchedEffect(reload) {
        state = LoadState.Loading
        runCatching {
            withContext(Dispatchers.IO) {
                mail.connect()
                mail.folders()
            }
        }.fold(
            onSuccess = { list ->
                folders = list
                val inbox = list.firstOrNull { it.name.contains("收件") } ?: list.firstOrNull()
                selectedFolder = inbox
                if (inbox != null) loadFolder(inbox, unreadOnly) else state = LoadState.Ready(Unit)
            },
            onFailure = {
                error = it.message ?: "邮箱连接失败"
                state = LoadState.Failed(error!!)
            },
        )
    }

    FeatureScaffold(
        title = "学生邮箱",
        onBack = onBack,
        actions = {
            TextButton(onClick = {
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) { mail.contacts() }
                    }.onSuccess { contacts = it; showContacts = true }
                        .onFailure { error = it.message ?: "联系人加载失败" }
                }
            }) { Text("联系人") }
            TextButton(onClick = { showCompose = true }) { Text("写信") }
        },
    ) {
        androidx.compose.material3.OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("搜索邮件") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
        )
        if (folders.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                folders.forEach { folder ->
                    SoftChip(
                        "${folder.name}${if (folder.unread > 0) " (${folder.unread})" else ""}",
                        onClick = {
                            selectedFolder = folder
                            loadFolder(folder, unreadOnly)
                        },
                        selected = selectedFolder?.id == folder.id,
                    )
                }
                SoftChip("仅未读", onClick = {
                    unreadOnly = !unreadOnly
                    selectedFolder?.let { loadFolder(it, unreadOnly) }
                }, selected = unreadOnly)
            }
        }
        AsyncContent(state, onRetry = { reload++ }) {
            when {
                selectedMessage != null -> {
                    Column(modifier = Modifier.fillMaxSize().padding(18.dp)) {
                        TextButton(onClick = { selectedMessage = null }) { Text("返回列表") }
                        FeatureScrollColumn {
                            PageCard {
                                Text(
                                    selectedMessage!!.subject,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = NextPalette.Ink,
                                )
                                Text(
                                    "${selectedMessage!!.from.joinToString()} · ${selectedMessage!!.to.joinToString()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NextPalette.InkSecondary,
                                )
                                HorizontalDivider(color = NextPalette.Line)
                                Text(
                                    mailBody(selectedMessage!!.text, selectedMessage!!.html),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = NextPalette.Ink,
                                )
                                selectedMessage!!.attachments.forEach {
                                    Text(
                                        "附件：${it.name} (${it.size / 1024} KB)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NextPalette.InkSecondary,
                                    )
                                }
                            }
                        }
                    }
                }
                messages.isEmpty() -> EmptyState("这个文件夹没有邮件。")
                else -> {
                    val visibleMessages = messages.filter {
                        query.isBlank() ||
                            it.subject.contains(query, true) ||
                            it.from.contains(query, true)
                    }
                    if (visibleMessages.isEmpty()) {
                        EmptyState("没有匹配的邮件。")
                    } else FeatureScrollColumn {
                    PageCard {
                        visibleMessages.forEachIndexed { index, message ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        scope.launch {
                                            runCatching {
                                                withContext(Dispatchers.IO) { mail.detail(message.id) }
                                            }.onSuccess { selectedMessage = it }
                                                .onFailure { error = it.message ?: "邮件加载失败" }
                                        }
                                    },
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        message.subject.ifBlank { "(无主题)" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (message.read) FontWeight.Normal else FontWeight.SemiBold,
                                        color = NextPalette.Ink,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "${message.from} · ${message.date}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NextPalette.InkSecondary,
                                        maxLines = 1,
                                    )
                                }
                                if (!message.read) {
                                    Text("未读", style = MaterialTheme.typography.labelSmall, color = NextPalette.AccentDeep)
                                }
                            }
                            if (index != visibleMessages.lastIndex) HorizontalDivider(color = NextPalette.Line)
                        }
                    }
                    }
                }
            }
        }
    }

    if (showCompose) {
        ComposeMailDialog(
            onDismiss = { showCompose = false },
            onSend = { draft ->
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            val begun = mail.beginDraft(draft)
                            mail.send(begun)
                        }
                    }.fold(
                        onSuccess = { showCompose = false; reload++ },
                        onFailure = { error = it.message ?: "发送失败" },
                    )
                }
            },
        )
    }

    if (showContacts) {
        AlertDialog(
            onDismissRequest = { showContacts = false },
            title = { Text("联系人") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (contacts.isEmpty()) {
                        Text("暂无联系人", color = NextPalette.InkSecondary)
                    }
                    contacts.forEach { contact ->
                        Text(
                            "${contact.name}  ${contact.email}",
                            style = MaterialTheme.typography.bodySmall,
                            color = NextPalette.Ink,
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showContacts = false }) { Text("关闭") } },
        )
    }

    error?.let {
        AlertDialog(
            onDismissRequest = { error = null },
            title = { Text("邮箱") },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = { error = null }) { Text("知道了") } },
        )
    }
}

@Composable
private fun ComposeMailDialog(
    onDismiss: () -> Unit,
    onSend: (MailDraft) -> Unit,
) {
    var to by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("写邮件") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = to,
                    onValueChange = { to = it },
                    label = { Text("收件人") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("主题") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("正文") },
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = to.isNotBlank() && subject.isNotBlank(),
                onClick = { onSend(MailDraft(to = to.trim(), subject = subject.trim(), body = body)) },
            ) { Text("发送") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun MaterialsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    var path by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<GitHubContentItem>>(emptyList()) }
    var state by remember { mutableStateOf<LoadState<Unit>>(LoadState.Loading) }
    var document by remember { mutableStateOf<com.ahu.ahutong.data.repository.RepositoryMarkdownDocument?>(null) }
    var downloaded by remember { mutableStateOf<DownloadedFile?>(null) }
    var downloads by remember { mutableStateOf<List<DownloadedFile>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(path, reload) {
        state = LoadState.Loading
        runCatching {
            withContext(Dispatchers.IO) { RepositoryManager.getContents(path) }
        }.fold(
            onSuccess = { items = it; state = LoadState.Ready(Unit) },
            onFailure = { state = it.toLoadFailure() },
        )
    }

    LaunchedEffect(Unit) {
        downloads = runCatching { RepositoryManager.getDownloadedFiles(context) }.getOrDefault(emptyList())
    }

    FeatureScaffold(
        title = "学习资料",
        onBack = onBack,
        actions = { TextButton(onClick = { reload++ }) { Text("刷新") } },
    ) {
        if (path.isNotBlank()) {
            TextButton(
                onClick = {
                    path = path.substringBeforeLast('/', "")
                    document = null
                },
                modifier = Modifier.padding(horizontal = 12.dp),
            ) { Text("返回上级") }
        }
        if (document != null) {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
                TextButton(onClick = { document = null }) { Text("返回目录") }
                FeatureScrollColumn {
                    PageCard {
                        AppMarkdown(document!!.content, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        } else {
            AsyncContent(state, onRetry = { reload++ }) {
                if (items.isEmpty()) {
                    EmptyState("这个目录没有内容。")
                } else {
                    FeatureScrollColumn {
                        PageCard {
                            val summaries = RepositoryManager.getDirectorySummaries(items)
                            items.forEachIndexed { index, item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (item.type == "dir") {
                                                path = item.path
                                            } else if (item.name.endsWith(".md", ignoreCase = true)) {
                                                scope.launch {
                                                    runCatching {
                                                        withContext(Dispatchers.IO) {
                                                            RepositoryManager.getMarkdownDocument(item.path)
                                                        }
                                                    }.onSuccess { document = it }
                                                        .onFailure { error = it.message ?: "文档加载失败" }
                                                }
                                            }
                                        },
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            item.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = NextPalette.Ink,
                                        )
                                        Text(
                                            if (item.type == "dir") {
                                                val summary = summaries[item.path]
                                                "${summary?.directoryCount ?: 0} 个目录 · ${summary?.fileCount ?: 0} 个文件"
                                            } else {
                                                "${item.size / 1024} KB"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = NextPalette.InkSecondary,
                                        )
                                    }
                                    if (item.type != "dir" && !item.name.endsWith(".md", true)) {
                                        TextButton(onClick = {
                                            scope.launch {
                                                runCatching {
                                                    withContext(Dispatchers.IO) {
                                                        RepositoryManager.downloadFile(item.path, context)
                                                    }
                                                }.onSuccess {
                                                    downloaded = it
                                                    downloads = RepositoryManager.getDownloadedFiles(context)
                                                }.onFailure { error = it.message ?: "下载失败" }
                                            }
                                        }) { Text("下载") }
                                    }
                                }
                                if (index != items.lastIndex) HorizontalDivider(color = NextPalette.Line)
                            }
                        }
                        if (downloads.isNotEmpty()) {
                            PageCard {
                                PageTitle("已下载")
                                downloads.take(10).forEach {
                                    Text(
                                        "${it.name} · ${it.size / 1024} KB",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NextPalette.InkSecondary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    error?.let {
        AlertDialog(
            onDismissRequest = { error = null },
            title = { Text("学习资料") },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = { error = null }) { Text("知道了") } },
        )
    }
    downloaded?.let {
        AlertDialog(
            onDismissRequest = { downloaded = null },
            title = { Text("下载完成") },
            text = { Text("${it.name}\n保存路径：${it.localPath}") },
            confirmButton = { TextButton(onClick = { downloaded = null }) { Text("知道了") } },
        )
    }
}


private fun mailBody(text: String, html: String): String {
    if (text.isNotBlank()) return text
    return html
        .replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("(?i)</p>"), "\n\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}
