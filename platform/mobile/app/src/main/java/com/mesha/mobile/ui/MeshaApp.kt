package com.mesha.mobile.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mesha.mobile.ClerkBootstrap
import com.mesha.mobile.R
import com.mesha.mobile.data.repository.AuthState
import com.mesha.mobile.ui.navigation.Routes
import com.mesha.mobile.ui.navigation.TopLevelDestination
import com.mesha.mobile.ui.screens.agents.AgentsScreen
import com.mesha.mobile.ui.screens.createissue.CreateIssueAiScreen
import com.mesha.mobile.ui.screens.dashboard.DashboardScreen
import com.mesha.mobile.localai.ui.LocalAiScreen
import com.mesha.mobile.ui.screens.issues.IssuesScreen
import com.mesha.mobile.ui.screens.login.LoginScreen
import com.mesha.mobile.ui.screens.projects.ProjectsScreen
import com.mesha.mobile.ui.screens.chat.LocalLlmChatScreen
import com.mesha.mobile.ui.screens.sessions.SessionDetailScreen
import com.mesha.mobile.ui.screens.sessions.SessionsScreen
import com.mesha.mobile.ui.screens.settings.SettingsScreen

/**
 * Root composable. Gates on auth state: unauthenticated users see the login screen,
 * authenticated users get the bottom-nav scaffold and the full navigation graph.
 *
 * Also gates on [ClerkBootstrap.isReady] first: [AppViewModel] pulls in
 * [com.mesha.mobile.data.repository.AuthRepository], which touches the Clerk SDK as
 * soon as it's constructed, so a build with a missing/invalid publishable key must
 * never reach that — it shows a configuration error instead of crashing.
 */
@Composable
fun MeshaApp() {
    if (!ClerkBootstrap.isReady) {
        ConfigurationErrorScreen()
        return
    }

    val appViewModel: AppViewModel = hiltViewModel()
    val authState by appViewModel.authState.collectAsStateWithLifecycle()

    if (authState == AuthState.Loading) {
        AuthLoadingScreen()
        return
    }

    if (authState == AuthState.Unauthenticated) {
        LoginScreen()
        return
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination

    val topLevelRoutes = TopLevelDestination.entries.map { it.route }
    val showBottomBar = topLevelRoutes.any { route ->
        currentRoute?.hierarchy?.any { it.route == route } == true
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { dest ->
                        val selected =
                            currentRoute?.hierarchy?.any { it.route == dest.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.HOME) {
                DashboardScreen(
                    onCreateIssueWithAi = { navController.navigate(Routes.CREATE_ISSUE_AI) },
                    onOpenSessions = { navController.navigate(Routes.SESSIONS) },
                    onOpenIssues = { navController.navigate(Routes.ISSUES) },
                    onOpenChat = { navController.navigate(Routes.LOCAL_LLM_CHAT) },
                )
            }
            composable(Routes.ISSUES) {
                IssuesScreen(
                    onCreateIssueWithAi = { navController.navigate(Routes.CREATE_ISSUE_AI) },
                )
            }
            composable(Routes.SESSIONS) {
                SessionsScreen(
                    onOpenSession = { id -> navController.navigate(Routes.sessionDetail(id)) },
                )
            }
            composable(Routes.PROJECTS) { ProjectsScreen() }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onOpenAgents = { navController.navigate("agents") },
                    onOpenLocalAi = { navController.navigate(Routes.LOCAL_AI) },
                    onSignOut = { appViewModel.signOut() },
                )
            }
            composable("agents") { AgentsScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.LOCAL_AI) {
                LocalAiScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.LOCAL_LLM_CHAT) {
                LocalLlmChatScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.CREATE_ISSUE_AI) {
                CreateIssueAiScreen(onClose = { navController.popBackStack() })
            }
            composable(Routes.SESSION_DETAIL) { entry ->
                val sessionId = entry.arguments?.getString("sessionId").orEmpty()
                SessionDetailScreen(
                    sessionId = sessionId,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

@Composable
private fun ConfigurationErrorScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.clerk_configuration_error))
    }
}

@Composable
private fun AuthLoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
