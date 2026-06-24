package com.mesha.mobile.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the user's active workspace and project selection in memory for the session.
 * Screens read from here so the choice persists across bottom-nav tabs without
 * threading ids through navigation arguments everywhere.
 */
@Singleton
class SelectionStore @Inject constructor() {
    private val _workspaceId = MutableStateFlow<String?>(null)
    val workspaceId: StateFlow<String?> = _workspaceId.asStateFlow()

    private val _projectId = MutableStateFlow<String?>(null)
    val projectId: StateFlow<String?> = _projectId.asStateFlow()

    fun selectWorkspace(id: String?) {
        if (_workspaceId.value != id) {
            _workspaceId.value = id
            _projectId.value = null // reset project when workspace changes
        }
    }

    fun selectProject(id: String?) {
        _projectId.value = id
    }
}
