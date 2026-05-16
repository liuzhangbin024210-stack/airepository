package com.majiang.counter.auth

/**
 * 本地管理员策略：固定用户名为 [ADMIN_USERNAME] 的账号视为管理员。
 *
 * 说明：无服务端角色同步，仅用于本机「添加用户」等权限控制。
 */
object AdminPolicy {
    const val ADMIN_USERNAME: String = "admin"

    /** 内置管理员默认密码（首次安装写入数据库；正式环境请登录后在设置中修改）。 */
    const val DEFAULT_ADMIN_PASSWORD: String = "admin123"

    fun isAdmin(username: String): Boolean = username == ADMIN_USERNAME
}
