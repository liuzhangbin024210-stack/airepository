package com.majiang.counter.rules

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RulesFingerprintTest {

    @Test
    fun defaultRules_sha256_matches_manifest_placeholder() {
        val hex = RulesConfig().sha256FingerprintHex()
        assertThat(hex).isEqualTo("c87de682f376157574fe79d7380e736d744e7e92e06f41e840284c2af56bae6b")
    }
}
