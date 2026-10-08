package com.example.bloomybeauty.feature.auth

import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.ExtractedTextRequest
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bloomybeauty.ui.theme.BloomyBeautyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.io.FileInputStream

class VietnameseInputTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation = instrumentation.uiAutomation

    @Test
    fun composingVietnameseKeepsAccentsAndCursorInRegistration() {
        // Use platform InputConnection with an isolated IME. A second active keyboard
        // would also send edits, making the synthetic composition sequence unreliable.
        val originalIme = shell("settings get secure default_input_method").trim()
        require(originalIme.matches(Regex("[A-Za-z0-9._/]+")))
        val testIme = "com.example.bloomybeauty.test/com.example.bloomybeauty.feature.auth.RegressionInputMethodService"
        shell("ime enable $testIme")
        shell("ime set $testIme")
        try {
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                lateinit var activity: ComponentActivity
                scenario.onActivity {
                    activity = it
                    it.setContent {
                        BloomyBeautyTheme {
                            AuthScreen(AuthUiState(initializing = false, registering = true),
                                { _, _, _, _ -> },
                                {}, {}, {})
                        }
                    }
                }
                awaitFrame(activity)
                lateinit var nameConnection: InputConnection
                instrumentation.runOnMainSync {
                    nameConnection = checkNotNull(findComposeView(activity.window.decorView)?.onCreateInputConnection(EditorInfo()))
                }
                composeText(activity, nameConnection, listOf("Ngu", "Nguy", "Nguyễn", "Nguyễn Thị Mỹ"))
                instrumentation.runOnMainSync {
                    assertEquals("Nguyễn Thị Mỹ", nameConnection.getExtractedText(ExtractedTextRequest(), 0).text.toString())
                }
                instrumentation.runOnMainSync { nameConnection.setSelection(0, 6) }
                awaitFrame(activity)
                composeText(activity, nameConnection, listOf("Tra", "Trần"))
                instrumentation.runOnMainSync {
                    assertEquals("Trần Thị Mỹ", nameConnection.getExtractedText(ExtractedTextRequest(), 0).text.toString())
                }
            }
        } finally {
            shell("ime set $originalIme")
            shell("ime disable $testIme")
        }
    }

    private fun composeText(activity: ComponentActivity, connection: InputConnection, values: List<String>) {
        values.forEach { value ->
            instrumentation.runOnMainSync { connection.setComposingText(value, 1) }
            awaitFrame(activity)
        }
        instrumentation.runOnMainSync { connection.finishComposingText() }
        awaitFrame(activity)
    }

    private fun shell(command: String): String = automation.executeShellCommand(command).use { descriptor ->
        FileInputStream(descriptor.fileDescriptor).bufferedReader().use { it.readText() }
    }

    private fun awaitFrame(activity: ComponentActivity) {
        val rendered = CountDownLatch(1)
        instrumentation.runOnMainSync {
            activity.window.decorView.postOnAnimation {
                activity.window.decorView.postOnAnimation { rendered.countDown() }
            }
        }
        assertTrue("UI did not render", rendered.await(5, TimeUnit.SECONDS))
        instrumentation.waitForIdleSync()
    }

    private fun findComposeView(view: View): View? {
        if (view.javaClass.simpleName == "AndroidComposeView") return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findComposeView(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }
}
