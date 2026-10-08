package com.example.bloomybeauty.feature.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.bloomybeauty.R
import com.example.bloomybeauty.data.auth.AuthError
import com.example.bloomybeauty.data.auth.AuthUser
import com.example.bloomybeauty.ui.theme.BloomyBeautyTheme
import kotlinx.coroutines.flow.drop

@Composable
fun AuthScreen(
    state: AuthUiState,
    onSubmit: (String, String, String, String) -> Unit,
    onSwitchMode: () -> Unit,
    onClearError: () -> Unit,
    onLogout: () -> Unit,
) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        if (state.initializing) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).imePadding()
                    .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                BrandHeader()
                Spacer(Modifier.height(28.dp))
                Surface(
                    modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                ) {
                    if (state.user != null) {
                        AccountContent(state.user, state.busy, state.error, onLogout)
                    } else {
                        AuthForm(state, onSubmit, onSwitchMode, onClearError)
                    }
                }
            }
        }
    }
}

@Composable
private fun BrandHeader() {
    Box(
        Modifier.size(64.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text("B", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
    Spacer(Modifier.height(14.dp))
    Text(stringResource(R.string.brand_name), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
    Text(
        stringResource(R.string.brand_tagline), style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
    )
}

@Composable
private fun AuthForm(
    state: AuthUiState,
    onSubmit: (String, String, String, String) -> Unit,
    onSwitchMode: () -> Unit,
    onClearError: () -> Unit,
) {
    val name = rememberTextFieldState()
    var email by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    // Passwords stay in memory only, outside saved instance state and the saved session.
    var password by remember { mutableStateOf(TextFieldValue()) }
    var confirmation by remember { mutableStateOf(TextFieldValue()) }
    var passwordVisible by remember { mutableStateOf(false) }
    val nameFocus = remember { FocusRequester() }
    val clearError by rememberUpdatedState(onClearError)
    LaunchedEffect(name) {
        snapshotFlow { name.text.toString() }.drop(1).collect { clearError() }
    }
    LaunchedEffect(state.registering) {
        if (state.registering) nameFocus.requestFocus()
    }
    val focusManager = LocalFocusManager.current
    val submit = {
        focusManager.clearFocus()
        onSubmit(name.text.toString(), email.text, password.text, confirmation.text)
    }
    val switchMode = {
        password = TextFieldValue()
        confirmation = TextFieldValue()
        passwordVisible = false
        onSwitchMode()
    }
    BackHandler(enabled = state.registering && !state.busy) { switchMode() }

    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            stringResource(if (state.registering) R.string.register_title else R.string.login_title),
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold,
        )
        Text(
            stringResource(if (state.registering) R.string.register_subtitle else R.string.login_subtitle),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.registering) {
            ComposingNameField(name, !state.busy, state.error == AuthError.INVALID_NAME, nameFocus)
        }
        OutlinedTextField(
            value = email, onValueChange = { email = it; onClearError() },
            modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.email)) },
            singleLine = true, enabled = !state.busy,
            isError = state.error == AuthError.INVALID_EMAIL || state.error == AuthError.DUPLICATE_EMAIL,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            shape = RoundedCornerShape(14.dp),
        )
        PasswordField(
            value = password, onValueChange = { password = it; onClearError() },
            label = stringResource(R.string.password), visible = passwordVisible,
            onToggleVisibility = { passwordVisible = !passwordVisible }, enabled = !state.busy,
            isError = state.error == AuthError.INVALID_PASSWORD,
            imeAction = if (state.registering) ImeAction.Next else ImeAction.Done, onDone = submit,
        )
        if (state.registering) {
            Text(
                stringResource(R.string.password_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PasswordField(
                value = confirmation, onValueChange = { confirmation = it; onClearError() },
                label = stringResource(R.string.confirm_password), visible = passwordVisible,
                onToggleVisibility = { passwordVisible = !passwordVisible }, enabled = !state.busy,
                isError = state.error == AuthError.PASSWORD_MISMATCH,
                imeAction = ImeAction.Done, onDone = submit,
            )
        }
        state.error?.let { AuthErrorMessage(it) }
        Button(
            onClick = { submit() }, enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(16.dp),
        ) {
            if (state.busy) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(if (state.registering) R.string.register_action else R.string.login_action))
            }
        }
        TextButton(onClick = { switchMode() }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(if (state.registering) R.string.switch_to_login else R.string.switch_to_register),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComposingNameField(state: TextFieldState, enabled: Boolean, isError: Boolean, focus: FocusRequester) {
    val interactions = remember { MutableInteractionSource() }
    BasicTextField(
        state = state, modifier = Modifier.fillMaxWidth().focusRequester(focus),
        enabled = enabled, lineLimits = TextFieldLineLimits.SingleLine,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        interactionSource = interactions,
        decorator = { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = state.text.toString(), innerTextField = innerTextField,
                enabled = enabled, singleLine = true, isError = isError,
                visualTransformation = VisualTransformation.None, interactionSource = interactions,
                label = { Text(stringResource(R.string.full_name)) },
                container = {
                    OutlinedTextFieldDefaults.Container(enabled, isError, interactions, shape = RoundedCornerShape(14.dp))
                },
            )
        },
    )
}

@Composable
private fun PasswordField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    label: String,
    visible: Boolean,
    onToggleVisibility: () -> Unit,
    enabled: Boolean,
    isError: Boolean,
    imeAction: ImeAction,
    onDone: () -> Unit,
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(),
        label = { Text(label) }, singleLine = true, enabled = enabled, isError = isError,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        trailingIcon = {
            TextButton(onClick = onToggleVisibility, enabled = enabled) {
                Text(stringResource(if (visible) R.string.hide_password else R.string.show_password))
            }
        },
        shape = RoundedCornerShape(14.dp),
    )
}

@Composable
private fun AccountContent(user: AuthUser, busy: Boolean, error: AuthError?, onLogout: () -> Unit) {
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.login_success), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.welcome_user, user.name), style = MaterialTheme.typography.bodyLarge)
        Text(user.email, color = MaterialTheme.colorScheme.onSurfaceVariant)
        error?.let { AuthErrorMessage(it) }
        Button(onClick = onLogout, enabled = !busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Text(stringResource(if (busy) R.string.logging_out else R.string.logout_action))
        }
    }
}

@Composable
private fun AuthErrorMessage(error: AuthError) {
    val resource = when (error) {
        AuthError.INVALID_NAME -> R.string.error_name
        AuthError.INVALID_EMAIL -> R.string.error_email
        AuthError.INVALID_PASSWORD -> R.string.error_password
        AuthError.PASSWORD_MISMATCH -> R.string.error_confirmation
        AuthError.DUPLICATE_EMAIL -> R.string.error_duplicate_email
        AuthError.INVALID_CREDENTIALS -> R.string.error_credentials
        AuthError.STORAGE -> R.string.error_storage
    }
    Text(
        stringResource(resource), color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Preview(showBackground = true)
@Composable
private fun LoginPreview() {
    BloomyBeautyTheme {
        AuthScreen(AuthUiState(initializing = false), { _, _, _, _ -> }, {}, {}, {})
    }
}
