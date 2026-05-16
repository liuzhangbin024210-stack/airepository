package com.majiang.counter.auth

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PasswordHasherTest {

    @Test
    fun hashAndVerify_match() {
        val salt = PasswordHasher.generateSalt()
        val encoded = PasswordHasher.encodeSalt(salt)
        val hash = PasswordHasher.hash("secret123", salt)
        assertThat(PasswordHasher.verify("secret123", encoded, hash)).isTrue()
    }

    @Test
    fun verify_wrongPassword_fails() {
        val salt = PasswordHasher.generateSalt()
        val encoded = PasswordHasher.encodeSalt(salt)
        val hash = PasswordHasher.hash("secret123", salt)
        assertThat(PasswordHasher.verify("wrong", encoded, hash)).isFalse()
    }
}
