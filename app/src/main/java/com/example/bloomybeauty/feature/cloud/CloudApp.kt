package com.example.bloomybeauty.feature.cloud

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bloomybeauty.data.auth.UserRole
import com.example.bloomybeauty.feature.auth.AuthScreen
import com.example.bloomybeauty.feature.auth.AuthUiState

@Composable
fun CloudApp(vm: CloudAccountViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle,vm) {
        val observer=LifecycleEventObserver {_,event -> if(event==Lifecycle.Event.ON_RESUME) vm.refresh()}
        lifecycle.addObserver(observer)
        onDispose {lifecycle.removeObserver(observer)}
    }
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Surface(color=MaterialTheme.colorScheme.secondaryContainer) {
            Text("Firebase — môi trường kiểm thử",Modifier.fillMaxWidth().padding(16.dp),style=MaterialTheme.typography.labelLarge)
        }
        state.notice?.let {Text(it,Modifier.padding(16.dp),color=MaterialTheme.colorScheme.error)}
        if(state.session==null) {
            Text("Tài khoản Firebase dùng riêng cho dữ liệu Cloud. Lịch sử demo được giữ trong bản local.",Modifier.padding(horizontal=16.dp,vertical=8.dp))
            Box(Modifier.weight(1f)) {
                AuthScreen(AuthUiState(initializing=state.initializing,busy=state.busy,registering=state.registering,error=state.authError),
                    vm::submit,vm::switchMode,vm::clearError,vm::logout)
            }
        } else {
            Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Text("Tài khoản Firebase",style=MaterialTheme.typography.headlineSmall)
                if(state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.account?.let {account ->
                    Text(account.name,style=MaterialTheme.typography.titleLarge)
                    Text(account.email)
                    Text("Vai trò: ${if(account.role==UserRole.ADMIN) "Quản trị viên" else "Khách hàng"}")
                    Text(if(account.emailVerified) "Email đã xác minh" else "Email chưa xác minh")
                }
                OutlinedButton(vm::refresh,enabled=!state.busy) {Text("Tải lại tài khoản")}
                Button(vm::logout,enabled=!state.busy) {Text("Đăng xuất")}
            }
        }
    }
}
