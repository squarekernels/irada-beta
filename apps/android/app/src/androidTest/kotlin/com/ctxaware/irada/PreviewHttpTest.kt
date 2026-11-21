package com.ctxaware.irada

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Opt-in against api/test-support/preview-server.js, via adb reverse. */
class PreviewHttpTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun mode(value: String) {
        val connection = URL("http://127.0.0.1:3000/__validation").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write("{\"mode\":\"$value\"}".toByteArray()) }
            check(connection.responseCode == 200)
        } finally { connection.disconnect() }
    }
    private fun enabled() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("runLivePreviewTests") == "true")
        mode("normal")
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Go to tasks").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithContentDescription("Add tasks").fetchSemanticsNodes().isNotEmpty()
        }
        if (compose.onAllNodesWithText("Go to tasks").fetchSemanticsNodes().isNotEmpty())
            compose.onNodeWithText("Go to tasks").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Add tasks").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Add tasks").performClick()
        if (compose.onAllNodesWithText("Discard and start over").fetchSemanticsNodes().isNotEmpty())
            compose.onNodeWithText("Discard and start over").performClick()
    }
    private fun enter(input: String) {
        compose.onNode(hasSetTextAction()).performTextReplacement(input)
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Draft saved on this device").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Make a plan").performScrollTo().performClick()
    }
    private fun editOriginal() {
        compose.onNodeWithTag("review_list").performScrollToNode(hasText("Edit original input"))
        compose.onNodeWithText("Edit original input").performClick()
        compose.onNodeWithText("Discard proposals").performClick()
    }
    private fun ready() {
        compose.waitUntil(25_000) { compose.onAllNodesWithText("Review your plan").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun threeExamplesUseRealFastifyWithoutSavingTasks() {
        enabled()
        enter("Buy groceries")
        ready()
        compose.onNodeWithText("Anytime").assertExists()
        compose.onNodeWithText("No reminder").assertExists()
        editOriginal()
        val tomorrow = LocalDate.now().plusDays(1)
        enter("Buy groceries tomorrow")
        ready()
        val due = compose.activity.getString(R.string.due_value, tomorrow.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)))
        compose.onNodeWithTag("review_list").performScrollToNode(hasText(due))
        compose.onNodeWithText(due).assertExists()
        compose.onNodeWithText("No reminder").assertExists()
        editOriginal()
        enter("Remind me tomorrow at 9 to buy groceries")
        ready()
        val reminder = compose.activity.getString(R.string.reminder_value,
            LocalDateTime.of(tomorrow, LocalTime.of(9, 0)).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)))
        compose.onNodeWithTag("review_list").performScrollToNode(hasText(reminder))
        compose.onNodeWithText(reminder).assertExists()
        editOriginal()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("A little space to think").assertIsDisplayed()
        val connection = URL("http://127.0.0.1:3000/api/tasks").openConnection() as HttpURLConnection
        try { assertEquals("{\"tasks\":[]}", connection.inputStream.bufferedReader().use { it.readText() }) }
        finally { connection.disconnect() }
    }

    @Test fun malformedResponseAndServiceFailureRequireManualRetry() {
        enabled()
        mode("invalid")
        val input = "Buy groceries"
        enter(input)
        compose.waitUntil(25_000) { compose.onAllNodesWithText("Could not make a plan").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("The proposed plan could not be read safely. Please retry or edit your input.").assertIsDisplayed()
        mode("failure")
        compose.onNodeWithText("Retry").performClick()
        compose.waitUntil(25_000) { compose.onAllNodesWithText("Needs internet to make a plan. If you are online, please try again in a moment.").fetchSemanticsNodes().isNotEmpty() }
        mode("normal")
        // No connectivity observer or auto-retry: state remains failed until a tap.
        compose.onNodeWithText("Could not make a plan").assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()
        ready()
        editOriginal()
        compose.onNode(hasSetTextAction()).assertTextContains(input)
    }
}
