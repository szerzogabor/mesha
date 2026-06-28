package com.mesha.mobile.localai.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mesha.mobile.localai.model.CatalogEntry
import com.mesha.mobile.localai.model.DownloadState
import com.mesha.mobile.localai.model.LocalAiModel
import com.mesha.mobile.localai.repository.ModelRepository
import com.mesha.mobile.localai.storage.ModelStorageManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LocalAiUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val entries: List<CatalogEntry> = emptyList(),
    /** In-flight download state keyed by model id. */
    val downloads: Map<String, DownloadState> = emptyMap(),
    val availableStorageBytes: Long = 0,
    val totalUsageBytes: Long = 0,
    val error: String? = null,
    val message: String? = null,
)

/**
 * Drives the Local AI screen: loads the catalog, runs/cancels downloads, deletes installed
 * models and surfaces update detection. Per-model download jobs are tracked so a download can
 * be cancelled (which pauses it — the partial file is kept for resume).
 */
@HiltViewModel
class LocalAiViewModel @Inject constructor(
    private val repository: ModelRepository,
    private val storage: ModelStorageManager,
) : ViewModel() {

    private val _state = MutableStateFlow(LocalAiUiState())
    val state: StateFlow<LocalAiUiState> = _state.asStateFlow()

    private val downloadJobs = mutableMapOf<String, Job>()

    init {
        load(forceRefresh = false)
    }

    /** Loads (or refreshes) the catalog and recomputes install state + storage figures. */
    fun load(forceRefresh: Boolean = true) {
        _state.update {
            it.copy(
                loading = it.entries.isEmpty(),
                refreshing = it.entries.isNotEmpty() && forceRefresh,
                error = null,
            )
        }
        viewModelScope.launch {
            val result = repository.availableModels(forceRefresh)
            _state.update { current ->
                result.fold(
                    onSuccess = { entries ->
                        current.copy(
                            loading = false,
                            refreshing = false,
                            entries = entries,
                            availableStorageBytes = storage.availableStorageBytes(),
                            totalUsageBytes = storage.totalDiskUsageBytes(),
                        )
                    },
                    onFailure = { e ->
                        current.copy(
                            loading = false,
                            refreshing = false,
                            error = e.message ?: "Couldn't load the model catalog.",
                        )
                    },
                )
            }
        }
    }

    /** Starts (or resumes) downloading and installing [model]. */
    fun download(model: LocalAiModel) {
        if (downloadJobs[model.id]?.isActive == true) return
        val job = viewModelScope.launch {
            repository.installModel(model)
                .onCompletion { downloadJobs.remove(model.id) }
                .collect { downloadState ->
                    _state.update { it.copy(downloads = it.downloads + (model.id to downloadState)) }
                    if (downloadState is DownloadState.Completed) {
                        clearDownload(model.id)
                        load(forceRefresh = false)
                    }
                }
        }
        downloadJobs[model.id] = job
    }

    /** Cancels an in-flight download, pausing it (the partial file is kept for resume). */
    fun cancel(modelId: String) {
        downloadJobs.remove(modelId)?.cancel()
        clearDownload(modelId)
    }

    /** Dismisses a failed download's error banner for [modelId]. */
    fun dismissDownload(modelId: String) = clearDownload(modelId)

    /** Deletes an installed model and refreshes the list. */
    fun delete(modelId: String) {
        viewModelScope.launch {
            val removed = repository.removeModel(modelId)
            _state.update {
                it.copy(message = if (removed) "Model deleted" else "Nothing to delete")
            }
            load(forceRefresh = false)
        }
    }

    /** Re-checks the catalog for newer versions of installed models. */
    fun checkForUpdates() {
        viewModelScope.launch {
            val result = repository.checkForUpdates()
            val message = result.fold(
                onSuccess = { updates ->
                    if (updates.isEmpty()) "All models are up to date"
                    else "${updates.size} update(s) available"
                },
                onFailure = { "Update check failed: ${it.message}" },
            )
            _state.update { it.copy(message = message) }
            load(forceRefresh = false)
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun clearDownload(modelId: String) =
        _state.update { it.copy(downloads = it.downloads - modelId) }
}
