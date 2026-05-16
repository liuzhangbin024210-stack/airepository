package com.majiang.counter.auth

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AdminPolicyTest {

    @Test
    fun isAdmin_onlyLowercaseAdmin() {
        assertThat(AdminPolicy.isAdmin("admin")).isTrue()
        assertThat(AdminPolicy.isAdmin("Admin")).isFalse()
        assertThat(AdminPolicy.isAdmin("admin2")).isFalse()
    }
}
