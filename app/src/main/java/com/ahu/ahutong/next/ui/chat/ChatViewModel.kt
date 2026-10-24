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
import com.ahu.ahutong.next.data.CampusRepository
import com.ahu.ahutong.next.app.data.NextCampusRepository
import com.ahu.ahutong.next.model.FakeChatModel
import com.ahu.ahutong.next.model.OpenAiCompatibleChatModel
import com.ahu.ahutong.next.tools.DeepSeekSearchClient
import com.ahu.ahutong.next.tools.WebSearchTool
import com.ahu.ahutong.next.tools.GetAttendanceTool
import com.ahu.ahutong.next.tools.GetLostFoundTool
import com.ahu.ahutong.next.tools.GetFreeRoomsTool
import com.ahu.ahutong.next.tools.GetNoticesTool
import com.ahu.ahutong.next.tools.GetProgramCompletionTool
import com.ahu.ahutong.next.tools.GetWeatherTool
import com.ahu.ahutong.next.tools.campusTools
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = SettingsRepository(application)
    private val historyStore = HistoryStore(application)
    private val repository: CampusRepository = NextCampusRepository()
    private val gate = ApprovalGate()
    private var runJob: Job? = null
    private var persistJob: Job? = null
    private var historyGeneration = 0
    private var streamingItemId: String? = null

    private val _ui = MutableStateFlow(
        ChatUiState(
            items = historyStore.load(),
            hasApiKey = !settings.apiKey().isNullOrBlank(),
            modelLabel = modelLabel(),
        ),
    )
    val ui: StateFlow<ChatUiState> = _ui.asStateFlow()

    private fun buildRegistry(): ToolRegistry {
        val tools = campusTools(repository).toMutableList()
        tools += GetWeatherTool()
        tools += GetAttendanceTool()
        tools += GetProgramCompletionTool()
        tools += GetLostFoundTool()
        tools += GetNoticesTool()
        tools += GetFreeRoomsTool()
        val key = settings.apiKey()
        if (!key.isNullOrBlank() && settings.searchEnabled()) {
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

        val runtime = AgentRuntime(buildModel(), buildRegistry())
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

    fun approve() {
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
        gate.approve()
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

    fun clearHistory() {
        cancelRun()
        historyGeneration++
        persistJob?.cancel()
        historyStore.clear()
        historyStore.save(emptyList())
        _ui.update { it.copy(items = emptyList(), input = "", status = null) }
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
        super.onCleared()
    }

    private fun handle(event: AgentEvent) {
        when (event) {
            is AgentEvent.Status -> _ui.update { it.copy(status = event.text) }

            AgentEvent.AssistantStart -> startStreamingItem()

            is AgentEvent.AssistantDelta -> appendStreamingDelta(event.text)

            is AgentEvent.AssistantMessage -> finishStreamingItem(event.text)

            is AgentEvent.ToolResultEvent -> {
                finalizeStreamingItem(removeIfEmpty = true)
                val item = ChatItem.Tool(UUID.randomUUID().toString(), event.result)
                _ui.update { it.copy(items = it.items + item, status = null) }
                persist()
            }

            is AgentEvent.ApprovalRequired -> {
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
                finalizeStreamingItem(removeIfEmpty = true)
                val item = ChatItem.Error(UUID.randomUUID().toString(), event.message)
                _ui.update { it.copy(items = it.items + item, status = null) }
                persist()
            }

            AgentEvent.Done -> finalizeStreamingItem(removeIfEmpty = true)
        }
    }

    private fun startStreamingItem() {
        if (streamingItemId != null) return
        val item = ChatItem.Assistant(
            id = UUID.randomUUID().toString(),
            text = "",
            streaming = true,
        )
        streamingItemId = item.id
        _ui.update { it.copy(items = it.items + item, status = null) }
    }

    private fun appendStreamingDelta(delta: String) {
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

    private fun modelLabel(): String =
        if (settings.apiKey().isNullOrBlank()) "本地演示" else "DeepSeek · ${settings.model()}"

    private fun persist() {
        val generation = historyGeneration
        val snapshot = _ui.value.items
        persistJob?.cancel()
        persistJob = viewModelScope.launch(Dispatchers.IO) {
            if (generation == historyGeneration) historyStore.save(snapshot)
        }
    }
}
