package com.majiang.counter.data

import com.majiang.counter.auth.AdminPolicy
import com.majiang.counter.auth.PasswordHasher
import javax.inject.Inject
import javax.inject.Singleton

sealed class AuthError(val message: String) {
    data object EmptyUsername : AuthError("请输入用户名")
    data object UsernameTooShort : AuthError("用户名至少 2 个字符")
    data object EmptyPassword : AuthError("请输入密码")
    data object PasswordTooShort : AuthError("密码至少 6 位")
    data object PasswordMismatch : AuthError("两次密码不一致")
    data object UsernameTaken : AuthError("该用户名已存在")
    data object InvalidCredentials : AuthError("用户名或密码错误")
    data object AdminOnlyRegister : AuthError("仅管理员可在设置中添加用户")
    data object WrongOldPassword : AuthError("当前密码不正确")
    data object SamePassword : AuthError("新密码不能与当前密码相同")
}

@Singleton
class UserRepository @Inject constructor(
    private val userDao: UserDao,
) {
    /**
     * 确保内置管理员存在（不存在则创建，默认密码见 [AdminPolicy.DEFAULT_ADMIN_PASSWORD]）。
     */
    suspend fun ensureDefaultAdminExists() {
        if (userDao.findByUsername(AdminPolicy.ADMIN_USERNAME) != null) return
        val salt = PasswordHasher.generateSalt()
        val entity = UserEntity(
            username = AdminPolicy.ADMIN_USERNAME,
            passwordHash = PasswordHasher.hash(AdminPolicy.DEFAULT_ADMIN_PASSWORD, salt),
            salt = PasswordHasher.encodeSalt(salt),
            createdAtMillis = System.currentTimeMillis(),
        )
        userDao.insert(entity)
    }

    /**
     * 由管理员创建新用户；不会校验「当前密码」，但会校验 [actorUsername] 是否为管理员。
     */
    suspend fun registerByAdmin(
        actorUsername: String,
        newUsername: String,
        password: String,
        confirmPassword: String,
    ): Result<Unit> {
        if (!AdminPolicy.isAdmin(actorUsername)) {
            return Result.failure(AuthException(AuthError.AdminOnlyRegister))
        }
        validateRegister(newUsername, password, confirmPassword)?.let { return Result.failure(it) }
        val normalized = newUsername.trim()
        if (userDao.findByUsername(normalized) != null) {
            return Result.failure(AuthException(AuthError.UsernameTaken))
        }
        val salt = PasswordHasher.generateSalt()
        val entity = UserEntity(
            username = normalized,
            passwordHash = PasswordHasher.hash(password, salt),
            salt = PasswordHasher.encodeSalt(salt),
            createdAtMillis = System.currentTimeMillis(),
        )
        userDao.insert(entity)
        return Result.success(Unit)
    }

    /**
     * 已登录用户修改自己的密码。
     */
    suspend fun changePassword(
        username: String,
        oldPassword: String,
        newPassword: String,
        confirmPassword: String,
    ): Result<Unit> {
        if (newPassword != confirmPassword) {
            return Result.failure(AuthException(AuthError.PasswordMismatch))
        }
        if (newPassword.length < 6) {
            return Result.failure(AuthException(AuthError.PasswordTooShort))
        }
        if (newPassword == oldPassword) {
            return Result.failure(AuthException(AuthError.SamePassword))
        }
        val user = userDao.findByUsername(username.trim())
            ?: return Result.failure(AuthException(AuthError.InvalidCredentials))
        if (!PasswordHasher.verify(oldPassword, user.salt, user.passwordHash)) {
            return Result.failure(AuthException(AuthError.WrongOldPassword))
        }
        val salt = PasswordHasher.generateSalt()
        val hash = PasswordHasher.hash(newPassword, salt)
        userDao.updateCredentials(
            username = user.username,
            passwordHash = hash,
            salt = PasswordHasher.encodeSalt(salt),
        )
        return Result.success(Unit)
    }

    suspend fun authenticate(username: String, password: String): Result<UserEntity> {
        if (username.isBlank()) return Result.failure(AuthException(AuthError.EmptyUsername))
        if (password.isEmpty()) return Result.failure(AuthException(AuthError.EmptyPassword))
        val user = userDao.findByUsername(username.trim())
            ?: return Result.failure(AuthException(AuthError.InvalidCredentials))
        if (!PasswordHasher.verify(password, user.salt, user.passwordHash)) {
            return Result.failure(AuthException(AuthError.InvalidCredentials))
        }
        return Result.success(user)
    }

    private fun validateRegister(
        username: String,
        password: String,
        confirmPassword: String,
    ): AuthException? {
        val trimmed = username.trim()
        when {
            trimmed.isEmpty() -> return AuthException(AuthError.EmptyUsername)
            trimmed.length < 2 -> return AuthException(AuthError.UsernameTooShort)
            password.isEmpty() -> return AuthException(AuthError.EmptyPassword)
            password.length < 6 -> return AuthException(AuthError.PasswordTooShort)
            password != confirmPassword -> return AuthException(AuthError.PasswordMismatch)
        }
        return null
    }
}

class AuthException(val authError: AuthError) : Exception(authError.message)
