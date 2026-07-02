package com.mesha.mobile.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.ui.graphics.vector.ImageVector

/** Route constants for the app's navigation graph. */
object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val ISSUES = "issues"
    const val SESSIONS = "sessions"
    const val PROJECTS = "projects"
    const val SETTINGS = "settings"

    const val CREATE_ISSUE_AI = "create_issue_ai"
    const val LOCAL_AI = "local_ai"
    const val LOCAL_LLM_CHAT = "local_llm_chat"
    const val SESSION_DETAIL = "session_detail/{sessionId}"
    const val ISSUE_DETAIL = "issue_detail/{projectId}/{issueId}"

    fun sessionDetail(sessionId: String) = "session_detail/$sessionId"
    fun issueDetail(projectId: String, issueId: String) = "issue_detail/$projectId/$issueId"
}

/** Bottom-navigation destinations, in display order. */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    HOME(Routes.HOME, "Home", Icons.Filled.Home),
    ISSUES(Routes.ISSUES, "Issues", Icons.AutoMirrored.Filled.ListAlt),
    SESSIONS(Routes.SESSIONS, "Sessions", Icons.Filled.SmartToy),
    PROJECTS(Routes.PROJECTS, "Projects", Icons.Filled.Folder),
    SETTINGS(Routes.SETTINGS, "Settings", Icons.Filled.Settings),
}
