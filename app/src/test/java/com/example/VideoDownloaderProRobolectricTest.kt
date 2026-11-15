package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VideoDownloaderProRobolectricTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `empty url shows validation error and clear resets it`() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("analyze_video_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("analyze_error_card").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("url_input_field")
            .performScrollTo()
            .performTextInput("https://youtube.com/shorts/aqz-KE-bpKQ")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("YouTube Short").assertIsDisplayed()

        composeRule.onNodeWithTag("clear_url_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("analyze_error_card").assertDoesNotExist()
    }

    @Test
    fun `navigation tabs and settings dialogs work cleanly`() {
        composeRule.waitForIdle()

        // Navigate to Downloads tab
        composeRule.onNodeWithTag("nav_tab_downloads").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("No Active Downloads").assertIsDisplayed()

        // Navigate to History tab
        composeRule.onNodeWithTag("nav_tab_history").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Your Vault is Empty").assertIsDisplayed()

        // Navigate to Settings tab & toggle theme + open Privacy Policy
        composeRule.onNodeWithTag("nav_tab_settings").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("theme_selector_light").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("privacy_policy_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Close").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("help_support_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Got It").performClick()
    }
}
