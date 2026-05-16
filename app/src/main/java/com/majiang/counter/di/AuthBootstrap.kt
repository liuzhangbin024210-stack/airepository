package com.majiang.counter.di

import com.majiang.counter.auth.AdminPolicy
import com.majiang.counter.auth.AuthSessionStore
import com.majiang.counter.data.UserRepository
import com.majiang.counter.BuildConfig
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * 在 [android.app.Application] 中访问 Hilt 单例（早于首个 Activity / ViewModel）。
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AuthBootstrapEntryPoint {
    fun userRepository(): UserRepository
    fun authSessionStore(): AuthSessionStore
}

/**
 * 启动时同步完成：内置管理员账号、可选的默认管理员会话（见 [BuildConfig.AUTO_ADMIN_LOGIN]）。
 */
fun runAuthBootstrap(application: android.app.Application) {
    val entry = EntryPointAccessors.fromApplication(application, AuthBootstrapEntryPoint::class.java)
    runBlocking {
        entry.userRepository().ensureDefaultAdminExists()
        if (BuildConfig.AUTO_ADMIN_LOGIN) {
            val session = entry.authSessionStore().session.first()
            if (session == null) {
                entry.authSessionStore().save(AdminPolicy.ADMIN_USERNAME)
            }
        }
    }
}
