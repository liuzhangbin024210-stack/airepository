package com.majiang.counter.ui.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.majiang.counter.ui.AuthViewModel
import com.majiang.counter.ui.PlayerStrings

/**
 * 管理员在已登录状态下添加新用户；完成后不切换当前登录身份。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminAddUserScreen(
    authVm: AuthViewModel,
    adminUsername: String,
    onClose: () -> Unit,
) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    val formState by authVm.formState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        authVm.resetFormFeedback()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(PlayerStrings.AUTH_ADD_USER_TITLE) },
                navigationIcon = {
                    IconButton(onClick = onClose, enabled = !formState.busy) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = PlayerStrings.AUTH_BACK,
                        )
                    }
                },
            )
        },
    ) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
        ) {
            AuthScaffold(
                title = "",
                subtitle = PlayerStrings.AUTH_ADD_USER_SUBTITLE,
                showHeader = false,
                username = username,
                onUsernameChange = { username = it },
                password = password,
                onPasswordChange = { password = it },
                confirmPassword = confirm,
                onConfirmPasswordChange = { confirm = it },
                busy = formState.busy,
                errorMessage = formState.errorMessage,
                primaryLabel = PlayerStrings.AUTH_ADD_USER_BUTTON,
                onPrimary = {
                    authVm.registerByAdmin(
                        adminUsername = adminUsername,
                        username = username,
                        password = password,
                        confirmPassword = confirm,
                        onSuccess = {
                            username = ""
                            password = ""
                            confirm = ""
                            onClose()
                        },
                    )
                },
                secondaryLabel = PlayerStrings.AUTH_CANCEL,
                onSecondary = onClose,
            )
        }
    }
}
