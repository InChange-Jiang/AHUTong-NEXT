package com.ahu.ahutong.next.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.next.agent.AgentEvent
import com.ahu.ahutong.next.agent.AgentRuntime
import com.ahu.ahutong.next.agent.ApprovalGate
import com.ahu.ahutong.next.agent.ChatModel
import com.ahu.ahutong.next.agent.LlmMessage
import com.ahu.ahutong.next.agent.ToolRegistry
import com.ahu.ahutong.next.app.data.HistoryStore
import com.ahu.ahutong.next.app.data.SettingsRepository
import com.ahu.ahutong.next.app.data.SoulRepository
import com.ahu.ahutong.next.data.CampusRepository
import com.ahu.ahutong.next.app.data.NextCampusRepository
import com.ahu.ahutong.next.app.data.modelLabel as buildModelLabel
import com.ahu.ahutong.next.app.data.providerFor
import com.ahu.ahutong.next.model.FakeChatModel
import com.ahu.ahutong.next.model.OpenAiCompatibleChatModel
import com.ahu.ahutong.next.tools.DeepSeekSearchClient
import com.ahu.ahutong.next.tools.WebSearchTool
import com.ahu.ahutong.next.tools.allCampusTools
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = SettingsRepository(application)
    private val soul = SoulRepository(application)
    private val historyStore = HistoryStore(application)
    private val repository: CampusRepository = NextCampusRepository()
    private val gate = ApprovalGate()
    private var runJob: Job? = null
    private var deltaFlushJob: Job? = null
    private val pendingDelta = StringBuilder()
    private var persistJob: Job? = null
    private var historyGeneration = 0
    private var streamingItemId: String? = null

    private val _ui = MutableStateFlow(
        ChatUiState(
            items = historyStore.load(),
            hasApiKey = !settings.apiKey().isNullOrBlank(),
            modelLabel = modelLabel(),
            sessions = historyStore.summaries(),
            currentSessionId = historyStore.currentId(),
        ),
    )
    val ui: StateFlow<ChatUiState> = _ui.asStateFlow()

    private fun buildRegistry(): ToolRegistry {
        val tools = allCampusTools(repository).toMutableList()
        val key = settings.apiKey()
        if (!key.isNullOrBlank() &&
            settings.searchEnabled() &&
            providerFor(settings.baseUrl()).supportsWebSearch
        ) {
            tools += WebSearchTool(DeepSeekSearchClient(settings.baseUrl(), key))
        }
        return ToolRegistry(tools)
    }

    fun onInputChange(value: String) {
        _ui.update { it.copy(input = value) }
    }

    fun send(text: String = _ui.value.input) {
        val trimmed = text.trim()
        val state = _ui.value
        if (trimmed.isEmpty() || state.isRunning || state.pendingApprovalId != null) return

        val history = buildHistory(state.items)
        val userItem = ChatItem.User(UUID.randomUUID().toString(), trimmed)
        streamingItemId = null
        _ui.update {
            it.copy(
                items = it.items + userItem,
                input = "",
                isRunning = true,
                status = "正在理解你的问题…",
                pendingApprovalId = null,
            )
        }
        persist()

        val runtime = AgentRuntime(
            model = buildModel(),
            registry = buildRegistry(),
            systemPrompt = buildSystemPrompt(),
        )
        runJob = viewModelScope.launch {
            try {
                runtime.run(history, trimmed, gate).collect { event -> handle(event) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handle(AgentEvent.Failed(e.message ?: "执行失败，请稍后再试"))
            } finally {
                finalizeStreamingItem(removeIfEmpty = true)
                _ui.update { it.copy(isRunning = false, status = null, pendingApprovalId = null) }
                persist()
            }
        }
    }

    fun approve(paymentPassword: String? = null) {
        val pending = _ui.value.pendingApprovalId ?: return
        _ui.update { state ->
            state.copy(
                items = state.items.map {
                    if (it is ChatItem.Approval && it.id == pending) {
                        it.copy(decision = ApprovalDecision.APPROVED)
                    } else {
                        it
                    }
                },
                pendingApprovalId = null,
                status = "正在执行…",
            )
        }
        gate.approve(paymentPassword)
    }

    fun reject() {
        val pending = _ui.value.pendingApprovalId ?: return
        _ui.update { state ->
            state.copy(
                items = state.items.map {
                    if (it is ChatItem.Approval && it.id == pending) {
                        it.copy(decision = ApprovalDecision.REJECTED)
                    } else {
                        it
                    }
                },
                pendingApprovalId = null,
                status = "已取消，正在收尾…",
            )
        }
        gate.reject()
    }

    fun cancelRun() {
        gate.cancel()
        runJob?.cancel()
        runJob = null
        deltaFlushJob?.cancel()
        deltaFlushJob = null
        pendingDelta.setLength(0)
        finalizeStreamingItem(removeIfEmpty = true)
        _ui.update { state ->
            state.copy(
                isRunning = false,
                status = null,
                pendingApprovalId = null,
                items = state.items.map {
                    if (it is ChatItem.Approval && it.decision == ApprovalDecision.PENDING) {
                        it.copy(decision = ApprovalDecision.REJECTED)
                    } else {
                        it
                    }
                },
            )
        }
        persist()
    }

    fun newChat() {
        cancelRun()
        historyGeneration++
        persistJob?.cancel()
        val sessionId = historyStore.startNewSession()
        _ui.update {
            it.copy(
                items = emptyList(),
                input = "",
                status = null,
                pendingApprovalId = null,
                sessions = historyStore.summaries(),
                currentSessionId = sessionId,
            )
        }
    }

    fun clearAllHistory() {
        cancelRun()
        historyGeneration++
        persistJob?.cancel()
        historyStore.clearAll()
        val sessionId = historyStore.startNewSession()
        _ui.update {
            it.copy(
                items = emptyList(),
                input = "",
                status = null,
                pendingApprovalId = null,
                sessions = historyStore.summaries(),
                currentSessionId = sessionId,
            )
        }
    }

    fun openSession(sessionId: String) {
        if (sessionId == _ui.value.currentSessionId) return
        cancelRun()
        historyGeneration++
        persistJob?.cancel()
        val items = historyStore.switchTo(sessionId)
        _ui.update {
            it.copy(
                items = items,
                input = "",
                status = null,
                pendingApprovalId = null,
                sessions = historyStore.summaries(),
                currentSessionId = historyStore.currentId(),
            )
        }
    }

    fun deleteSession(sessionId: String) {
        cancelRun()
        historyGeneration++
        persistJob?.cancel()
        historyStore.delete(sessionId)
        _ui.update {
            it.copy(
                items = historyStore.load(),
                input = "",
                status = null,
                pendingApprovalId = null,
                sessions = historyStore.summaries(),
                currentSessionId = historyStore.currentId(),
            )
        }
    }

    fun refreshSessions() {
        _ui.update {
            it.copy(
                sessions = historyStore.summaries(),
                currentSessionId = historyStore.currentId(),
            )
        }
    }

    fun refreshSettings() {
        _ui.update {
            it.copy(
                hasApiKey = !settings.apiKey().isNullOrBlank(),
                modelLabel = modelLabel(),
            )
        }
    }

    override fun onCleared() {
        gate.cancel()
        runJob?.cancel()
        deltaFlushJob?.cancel()
        super.onCleared()
    }

    private fun handle(event: AgentEvent) {
        when (event) {
            is AgentEvent.Status -> _ui.update { it.copy(status = event.text) }

            AgentEvent.AssistantStart -> startStreamingItem()

            is AgentEvent.AssistantDelta -> enqueueDelta(event.text)

            is AgentEvent.AssistantMessage -> {
                flushPendingDeltas()
                finishStreamingItem(event.text)
            }

            is AgentEvent.ToolResultEvent -> {
                flushPendingDeltas()
                finalizeStreamingItem(removeIfEmpty = true)
                val item = ChatItem.Tool(UUID.randomUUID().toString(), event.result)
                _ui.update { it.copy(items = it.items + item, status = null) }
                persist()
            }

            is AgentEvent.ApprovalRequired -> {
                flushPendingDeltas()
                finalizeStreamingItem(removeIfEmpty = true)
                val item = ChatItem.Approval(UUID.randomUUID().toString(), event.request)
                _ui.update {
                    it.copy(
                        items = it.items + item,
                        status = null,
                        pendingApprovalId = item.id,
                    )
                }
            }

            is AgentEvent.Failed -> {
                flushPendingDeltas()
                finalizeStreamingItem(removeIfEmpty = true)
                val item = ChatItem.Error(UUID.randomUUID().toString(), event.message)
                _ui.update { it.copy(items = it.items + item, status = null) }
                persist()
            }

            AgentEvent.Done -> {
                flushPendingDeltas()
                finalizeStreamingItem(removeIfEmpty = true)
            }
        }
    }

    private fun startStreamingItem() {
        pendingDelta.setLength(0)
        if (streamingItemId != null) return
        val item = ChatItem.Assistant(
            id = UUID.randomUUID().toString(),
            text = "",
            streaming = true,
        )
        streamingItemId = item.id
        _ui.update { it.copy(items = it.items + item, status = null) }
    }

    /**
     * SSE 的 delta 很密（几十毫秒一次）。逐条 setState 会让 Markdown 反复整段重排，
     * 看起来像“卡跳”。这里把 33ms 内的增量合并成一次 UI 更新，文本仍是连续长出来的。
     */
    private fun enqueueDelta(delta: String) {
        pendingDelta.append(delta)
        if (deltaFlushJob?.isActive == true) return
        deltaFlushJob = viewModelScope.launch {
            while (true) {
                delay(DELTA_FLUSH_INTERVAL_MS)
                if (pendingDelta.isEmpty()) {
                    deltaFlushJob = null
                    return@launch
                }
                val chunk = pendingDelta.toString()
                pendingDelta.setLength(0)
                appendStreamingDeltaNow(chunk)
            }
        }
    }

    private fun flushPendingDeltas() {
        if (pendingDelta.isEmpty()) return
        val chunk = pendingDelta.toString()
        pendingDelta.setLength(0)
        appendStreamingDeltaNow(chunk)
    }

    private fun appendStreamingDeltaNow(delta: String) {
        if (streamingItemId == null) startStreamingItem()
        val id = streamingItemId ?: return
        _ui.update { state ->
            state.copy(
                items = state.items.map {
                    if (it is ChatItem.Assistant && it.id == id) {
                        it.copy(text = it.text + delta)
                    } else {
                        it
                    }
                },
            )
        }
    }

    private fun finishStreamingItem(finalText: String) {
        flushPendingDeltas()
        val id = streamingItemId
        if (id == null) {
            val item = ChatItem.Assistant(UUID.randomUUID().toString(), finalText)
            _ui.update { it.copy(items = it.items + item, status = null) }
        } else {
            _ui.update { state ->
                state.copy(
                    items = state.items.map {
                        if (it is ChatItem.Assistant && it.id == id) {
                            it.copy(text = finalText, streaming = false)
                        } else {
                            it
                        }
                    },
                    status = null,
                )
            }
            streamingItemId = null
        }
        persist()
    }

    private fun finalizeStreamingItem(removeIfEmpty: Boolean) {
        val id = streamingItemId ?: return
        streamingItemId = null
        _ui.update { state ->
            state.copy(
                items = state.items.mapNotNull {
                    if (it is ChatItem.Assistant && it.id == id) {
                        if (it.text.isBlank() && removeIfEmpty) null else it.copy(streaming = false)
                    } else {
                        it
                    }
                },
            )
        }
    }

    private fun buildSystemPrompt(): String {
        val personality = soul.soulFor(settings.personality())
        return buildString {
            append(AgentRuntime.DEFAULT_SYSTEM_PROMPT.trimEnd())
            if (personality.isNotBlank()) {
                append("\n\n")
                append(personality)
            }
        }
    }

    private fun buildModel(): ChatModel {
        val key = settings.apiKey()
        return if (key.isNullOrBlank()) {
            FakeChatModel()
        } else {
            OpenAiCompatibleChatModel(
                baseUrl = settings.baseUrl(),
                apiKey = key,
                model = settings.model(),
            )
        }
    }

    private fun buildHistory(items: List<ChatItem>): List<LlmMessage> = items.mapNotNull { item ->
        when (item) {
            is ChatItem.User -> LlmMessage(role = "user", content = item.text)
            is ChatItem.Assistant -> LlmMessage(role = "assistant", content = item.text)
            else -> null
        }
    }.takeLast(24)

    private fun modelLabel(): String {
        val key = settings.apiKey()
        return buildModelLabel(
            baseUrl = settings.baseUrl(),
            model = settings.model(),
            hasKey = !key.isNullOrBlank(),
        )
    }

    private companion object {
        const val DELTA_FLUSH_INTERVAL_MS = 33L
    }

    private fun persist() {
        val generation = historyGeneration
        val snapshot = _ui.value.items
        persistJob?.cancel()
        persistJob = viewModelScope.launch(Dispatchers.IO) {
            if (generation == historyGeneration) {
                historyStore.save(snapshot)
                val sessions = historyStore.summaries()
                val currentId = historyStore.currentId()
                _ui.update {
                    it.copy(sessions = sessions, currentSessionId = currentId)
                }
            }
        }
    }
}
