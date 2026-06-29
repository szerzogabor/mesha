package com.mesha.mobile.ui.screens.createissue

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mesha.mobile.data.remote.dto.ProjectDto
import com.mesha.mobile.data.repository.DraftRepository
import com.mesha.mobile.data.repository.MeshaRepository
import com.mesha.mobile.data.repository.SelectionStore
import com.mesha.mobile.data.sync.DraftSyncWorker
import com.mesha.mobile.domain.ai.GenerateIssueRequest
import com.mesha.mobile.domain.ai.IssueDraft
import com.mesha.mobile.domain.ai.IssuePriority
import com.mesha.mobile.domain.ai.LocalAiException
import com.mesha.mobile.domain.ai.LocalAiProvider
import com.mesha.mobile.domain.speech.SpeechEvent
import com.mesha.mobile.domain.speech.SpeechInputProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class CreateStep { INPUT, GENERATING, REVIEW, DONE }

data class CreateIssueUiState(
    val step: CreateStep = CreateStep.INPUT,
    val prompt: String = "",
    val listening: Boolean = false,
    val modelAvailable: Boolean = true,
    val speechAvailable: Boolean = true,
    val projects: List<ProjectDto> = emptyList(),
    val selectedProjectId: String? = null,
    // Editable review fields
    val title: String = "",
    val description: String = "",
    val acceptanceCriteria: List<String> = emptyList(),
    val priority: IssuePriority = IssuePriority.MEDIUM,
    val labels: List<String> = emptyList(),
    val error: String? = null,
    val resultMessage: String? = null,
    val queuedOffline: Boolean = false,
)

/**
 * Drives the "Create Issue with AI" flow end to end:
 * prompt (typed or spoken) → on-device Gemma draft → editable review → create/queue.
 *
 * Draft generation is fully local; issue creation is offline-tolerant — the draft is
 * always persisted first, then synced, so a failed network call queues rather than loses
 * the user's work.
 */
@HiltViewModel
class CreateIssueAiViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val localAi: LocalAiProvider,
    private val speech: SpeechInputProvider,
    private val meshaRepository: MeshaRepository,
    private val draftRepository: DraftRepository,
    private val selectionStore: SelectionStore,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateIssueUiState())
    val state: StateFlow<CreateIssueUiState> = _state.asStateFlow()

    private var listenJob: Job? = null

    init {
        _state.update { it.copy(speechAvailable = speech.isAvailable()) }
        viewModelScope.launch {
            _state.update { it.copy(modelAvailable = localAi.isAvailable()) }
        }
        loadProjects()
    }

    private fun loadProjects() {
        val workspaceId = selectionStore.workspaceId.value
        viewModelScope.launch {
            val wsId = workspaceId ?: meshaRepository.getWorkspaces().getOrNull()
                ?.firstOrNull()?.id?.also { selectionStore.selectWorkspace(it) }
            if (wsId == null) {
                _state.update { it.copy(error = "No workspace available") }
                return@launch
            }
            meshaRepository.getProjects(wsId).onSuccess { projects ->
                val selected = selectionStore.projectId.value
                    ?: projects.firstOrNull()?.id
                _state.update { it.copy(projects = projects, selectedProjectId = selected) }
            }
        }
    }

    fun onPromptChange(value: String) = _state.update { it.copy(prompt = value, error = null) }

    fun selectProject(id: String) {
        selectionStore.selectProject(id)
        _state.update { it.copy(selectedProjectId = id) }
    }

    // ---- Voice input ----

    fun toggleListening() {
        if (_state.value.listening) stopListening() else startListening()
    }

    private fun startListening() {
        if (!speech.isAvailable()) {
            _state.update { it.copy(error = "Speech recognition unavailable") }
            return
        }
        _state.update { it.copy(listening = true, error = null) }
        listenJob = viewModelScope.launch {
            speech.listen().collect { event ->
                when (event) {
                    is SpeechEvent.Partial ->
                        _state.update { it.copy(prompt = event.text) }
                    is SpeechEvent.Result ->
                        _state.update { it.copy(prompt = event.text, listening = false) }
                    is SpeechEvent.Error ->
                        _state.update { it.copy(listening = false, error = event.message) }
                    SpeechEvent.ReadyForSpeech -> Unit
                }
            }
            _state.update { it.copy(listening = false) }
        }
    }

    private fun stopListening() {
        listenJob?.cancel()
        _state.update { it.copy(listening = false) }
    }

    // ---- Generation ----

    fun generate() {
        val prompt = _state.value.prompt.trim()
        if (prompt.length < 5) {
            _state.update { it.copy(error = "Describe the task in a little more detail") }
            return
        }
        _state.update { it.copy(step = CreateStep.GENERATING, error = null) }
        viewModelScope.launch {
            try {
                val draft = localAi.generateIssueDraft(GenerateIssueRequest(prompt))
                applyDraft(draft)
            } catch (e: LocalAiException) {
                _state.update {
                    it.copy(step = CreateStep.INPUT, error = friendlyMessage(e))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Defensive: any provider failure that doesn't go through LocalAiException
                // should still surface as an error rather than leave the UI stuck or crash.
                _state.update {
                    it.copy(step = CreateStep.INPUT, error = "On-device generation failed. ${e.message ?: ""}".trim())
                }
            }
        }
    }

    private fun applyDraft(draft: IssueDraft) {
        _state.update {
            it.copy(
                step = CreateStep.REVIEW,
                title = draft.title,
                description = draft.description,
                acceptanceCriteria = draft.acceptanceCriteria,
                priority = draft.priority,
                labels = draft.labels,
            )
        }
    }

    // ---- Review edits ----

    fun onTitleChange(v: String) = _state.update { it.copy(title = v) }
    fun onDescriptionChange(v: String) = _state.update { it.copy(description = v) }
    fun onPriorityChange(p: IssuePriority) = _state.update { it.copy(priority = p) }
    fun updateCriterion(index: Int, value: String) = _state.update {
        it.copy(acceptanceCriteria = it.acceptanceCriteria.toMutableList().apply { set(index, value) })
    }
    fun addCriterion() = _state.update { it.copy(acceptanceCriteria = it.acceptanceCriteria + "") }
    fun removeCriterion(index: Int) = _state.update {
        it.copy(acceptanceCriteria = it.acceptanceCriteria.filterIndexed { i, _ -> i != index })
    }

    fun backToInput() = _state.update { it.copy(step = CreateStep.INPUT, error = null) }

    // ---- Create / queue ----

    fun createIssue() {
        val s = _state.value
        val projectId = s.selectedProjectId
        val workspaceId = selectionStore.workspaceId.value
        if (projectId == null || workspaceId == null) {
            _state.update { it.copy(error = "Select a project first") }
            return
        }
        val draft = IssueDraft(
            title = s.title.trim(),
            description = s.description.trim(),
            acceptanceCriteria = s.acceptanceCriteria.map { it.trim() }.filter { it.isNotBlank() },
            priority = s.priority,
            labels = s.labels,
        )
        if (draft.title.isBlank()) {
            _state.update { it.copy(error = "Title is required") }
            return
        }

        viewModelScope.launch {
            // Always persist locally first so the work is never lost.
            draftRepository.saveDraft(draft, projectId, workspaceId, s.prompt)
            val created = draftRepository.syncPending()
            if (created > 0) {
                _state.update {
                    it.copy(step = CreateStep.DONE, queuedOffline = false,
                        resultMessage = "Issue created in Mesha")
                }
            } else {
                // Offline / failed — leave queued and schedule a retry.
                DraftSyncWorker.enqueue(appContext)
                _state.update {
                    it.copy(step = CreateStep.DONE, queuedOffline = true,
                        resultMessage = "Saved offline — will sync when connected")
                }
            }
        }
    }

    private fun friendlyMessage(e: LocalAiException): String = when (e) {
        is LocalAiException.ModelNotAvailable ->
            "On-device Gemma model isn't installed yet. Add it in Settings to generate drafts."
        is LocalAiException.InvalidOutput ->
            "The model returned an unexpected response. Try rephrasing your request."
        is LocalAiException.InferenceFailed ->
            "On-device generation failed. ${e.message ?: ""}".trim()
    }

    override fun onCleared() {
        super.onCleared()
        listenJob?.cancel()
    }
}
