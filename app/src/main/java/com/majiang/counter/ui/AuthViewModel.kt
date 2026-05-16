package com.majiang.counter.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.majiang.counter.auth.AuthSessionStore
import com.majiang.counter.data.AuthException
import com.majiang.counter.data.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AuthUiState {
    data object Loading : AuthUiState
    data object LoggedOut : AuthUiState
    data class LoggedIn(val username: String) : AuthUiState
}

data class AuthFormState(
    val busy: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val sessionStore: AuthSessionStore,
) : ViewModel() {

    val authState: StateFlow<AuthUiState> = sessionStore.session
        .map { session ->
            if (session != null) AuthUiState.LoggedIn(session.username)
            else AuthUiState.LoggedOut
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuthUiState.Loading)

    private val _formState = MutableStateFlow(AuthFormState())
    val formState: StateFlow<AuthFormState> = _formState.asStateFlow()

    private fun updateForm(busy: Boolean, error: String?) {
        _formState.value = AuthFormState(busy = busy, errorMessage = error)
    }

    fun login(username: String, password: String) {
        if (_formState.value.busy) return
        updateForm(busy = true, error = null)
        viewModelScope.launch {
            val result = userRepository.authenticate(username, password)
            result.fold(
                onSuccess = { user ->
                    sessionStore.save(user.username)
                    updateForm(busy = false, error = null)
                },
                onFailure = { err ->
                    val msg = (err as? AuthException)?.authError?.message ?: err.message ?: "登录失败"
                    updateForm(busy = false, error = msg)
                },
            )
        }
    }

    fun registerByAdmin(
        adminUsername: String,
        username: String,
        password: String,
        confirmPassword: String,
        onSuccess: () -> Unit,
    ) {
        if (_formState.value.busy) return
        updateForm(busy = true, error = null)
        viewModelScope.launch {
            val result = userRepository.registerByAdmin(adminUsername, username, password, confirmPassword)
            result.fold(
                onSuccess = {
                    updateForm(busy = false, error = null)
                    onSuccess()
                },
                onFailure = { err ->
                    val msg = (err as? AuthException)?.authError?.message ?: err.message ?: "添加用户失败"
                    updateForm(busy = false, error = msg)
                },
            )
        }
    }

    fun changePassword(
        username: String,
        oldPassword: String,
        newPassword: String,
        confirmPassword: String,
        onSuccess: () -> Unit,
    ) {
        if (_formState.value.busy) return
        updateForm(busy = true, error = null)
        viewModelScope.launch {
            val result = userRepository.changePassword(username, oldPassword, newPassword, confirmPassword)
            result.fold(
                onSuccess = {
                    updateForm(busy = false, error = null)
                    onSuccess()
                },
                onFailure = { err ->
                    val msg = (err as? AuthException)?.authError?.message ?: err.message ?: "修改密码失败"
                    updateForm(busy = false, error = msg)
                },
            )
        }
    }

    fun logout() {
        viewModelScope.launch { sessionStore.clear() }
    }

    /** 清除表单上的错误与忙碌态（打开子界面时避免沿用上一次提示）。 */
    fun resetFormFeedback() {
        _formState.value = AuthFormState(busy = false, errorMessage = null)
    }
}
