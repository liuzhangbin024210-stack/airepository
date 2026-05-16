package com.majiang.counter.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.majiang.counter.auth.AdminPolicy
import com.majiang.counter.ui.auth.AdminAddUserScreen
import com.majiang.counter.ui.auth.LoginScreen

/**
 * 应用根导航：未登录显示登录；已登录进入主界面；管理员可从设置进入「添加用户」全屏页。
 */
@Composable
fun AppRoot(authVm: AuthViewModel = hiltViewModel()) {
    val authState by authVm.authState.collectAsStateWithLifecycle()
    var showAdminAddUser by rememberSaveable { mutableStateOf(false) }

    when (val state = authState) {
        AuthUiState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        AuthUiState.LoggedOut -> {
            LoginScreen(authVm = authVm)
        }
        is AuthUiState.LoggedIn -> {
            if (showAdminAddUser && AdminPolicy.isAdmin(state.username)) {
                Surface(Modifier.fillMaxSize()) {
                    AdminAddUserScreen(
                        authVm = authVm,
                        adminUsername = state.username,
                        onClose = { showAdminAddUser = false },
                    )
                }
            } else {
                MainScreen(
                    username = state.username,
                    isAdmin = AdminPolicy.isAdmin(state.username),
                    onLogout = { authVm.logout() },
                    onAddUser = {
                        if (AdminPolicy.isAdmin(state.username)) {
                            showAdminAddUser = true
                        }
                    },
                )
            }
        }
    }
}
