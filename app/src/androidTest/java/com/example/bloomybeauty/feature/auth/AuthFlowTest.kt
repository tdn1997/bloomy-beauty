package com.example.bloomybeauty.feature.auth

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bloomybeauty.data.auth.AuthDatabaseHelper
import com.example.bloomybeauty.data.auth.AuthRepository
import com.example.bloomybeauty.ui.theme.BloomyBeautyTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertNotNull

class AuthFlowTest {
    @get:Rule
    val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val helper = AuthDatabaseHelper(context, "auth_flow_test.db")
    private val store = ViewModelStore()

    @After
    fun tearDown() {
        compose.runOnUiThread { store.clear() }
        helper.close()
        context.deleteDatabase("auth_flow_test.db")
    }

    @Test
    fun registerLogoutAndLoginThroughUi() {
        context.deleteDatabase("auth_flow_test.db")
        lateinit var viewModel: AuthViewModel
        compose.runOnUiThread {
            viewModel = AuthViewModel(AuthRepository(helper))
            store.put("auth", viewModel)
        }
        compose.setContent {
            val state by viewModel.state.collectAsState()
            BloomyBeautyTheme {
                AuthScreen(state, viewModel::submit, viewModel::switchMode, viewModel::clearError, viewModel::logout)
            }
        }
        compose.waitUntil(10_000) { !viewModel.state.value.initializing }
        compose.onNodeWithText("Chưa có tài khoản? Đăng ký").performClick()
        compose.onNodeWithText("Họ và tên").performTextInput("Khách hàng")
        compose.onNodeWithText("Email").performTextInput("customer@example.com")
        compose.onNodeWithText("Mật khẩu").performTextInput("password123")
        compose.onNodeWithText("Nhập lại mật khẩu").performTextInput("password123")
        compose.onNodeWithText("Đăng ký").performScrollTo().assertIsDisplayed().performClick()
        compose.waitUntil(10_000) { !viewModel.state.value.busy }
        assertNotNull("Registration failed: ${viewModel.state.value.error}", viewModel.state.value.user)
        compose.onNodeWithText("Xin chào, Khách hàng!").assertIsDisplayed()
        compose.onNodeWithText("Đăng xuất").performClick()
        compose.waitUntil(10_000) { viewModel.state.value.user == null && !viewModel.state.value.busy }
        compose.onNodeWithText("Email").performTextInput("customer@example.com")
        compose.onNodeWithText("Mật khẩu").performTextInput("password123")
        compose.onNodeWithText("Đăng nhập").performScrollTo().assertIsDisplayed().performClick()
        compose.waitUntil(10_000) { viewModel.state.value.user != null }
        compose.onNodeWithText("customer@example.com").assertIsDisplayed()
    }
}
