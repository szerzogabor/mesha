package com.mesha.mobile.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mesha.mobile.ui.components.EmptyState
import com.mesha.mobile.ui.components.ErrorState
import com.mesha.mobile.ui.theme.MeshaTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI smoke tests for the shared state views. Runs on device/emulator.
 */
class StateViewsUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyState_showsMessage() {
        composeRule.setContent { MeshaTheme { EmptyState("Nothing here") } }
        composeRule.onNodeWithText("Nothing here").assertIsDisplayed()
    }

    @Test
    fun errorState_retryInvokesCallback() {
        var retried = false
        composeRule.setContent {
            MeshaTheme { ErrorState("Boom", onRetry = { retried = true }) }
        }
        composeRule.onNodeWithText("Retry").performClick()
        assertTrue(retried)
    }
}
