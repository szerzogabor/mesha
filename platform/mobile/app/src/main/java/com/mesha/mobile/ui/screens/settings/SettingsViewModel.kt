package com.mesha.mobile.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mesha.mobile.BuildConfig
import com.mesha.mobile.domain.ai.GemmaModelManager
import com.mesha.mobile.domain.ai.LocalAiProvider
import com.mesha.mobile.update.ApkInstaller
import com.mesha.mobile.update.UpdateChecker
import com.mesha.mobile.update.UpdateStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val versionName: String = BuildConfig.VERSION_NAME,
    val versionCode: Int = BuildConfig.VERSION_CODE,
    val modelInstalled: Boolean = false,
    val modelDirectory: String = "",
    val updateStatus: UpdateStatus = UpdateStatus.UpToDate,
    val checkingUpdate: Boolean = false,
    val downloadingUpdate: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val localAi: LocalAiProvider,
    private val modelManager: GemmaModelManager,
    private val updateChecker: UpdateChecker,
    private val apkInstaller: ApkInstaller,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init { refreshModelStatus() }

    fun refreshModelStatus() {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    modelInstalled = localAi.isAvailable(),
                    modelDirectory = modelManager.expectedModelDirectory().absolutePath,
                )
            }
        }
    }

    fun checkForUpdate() {
        _state.update { it.copy(checkingUpdate = true, message = null) }
        viewModelScope.launch {
            val status = updateChecker.check()
            _state.update {
                it.copy(
                    checkingUpdate = false,
                    updateStatus = status,
                    message = if (status is UpdateStatus.UpToDate) "You're on the latest version" else null,
                )
            }
        }
    }

    fun downloadAndInstallUpdate() {
        val status = _state.value.updateStatus
        if (status !is UpdateStatus.UpdateAvailable) return
        _state.update { it.copy(downloadingUpdate = true, message = null) }
        viewModelScope.launch {
            runCatching {
                val url = updateChecker.downloadUrl(status.release)
                val apk = apkInstaller.download(url, status.release.fileName)
                apkInstaller.install(apk)
            }.onFailure { e ->
                _state.update { it.copy(downloadingUpdate = false, message = "Update failed: ${e.message}") }
            }.onSuccess {
                _state.update { it.copy(downloadingUpdate = false) }
            }
        }
    }
}
