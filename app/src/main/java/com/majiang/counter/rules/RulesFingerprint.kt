package com.majiang.counter.rules

import java.security.MessageDigest

/**
 * 与 `assets/ml/.../model_manifest.json` 中 `rulesHash` 对齐的运行时指纹，用于策略 TFLite 与规则版本绑定。
 */
fun RulesConfig.sha256FingerprintHex(): String {
    val md = MessageDigest.getInstance("SHA-256")
    val bytes = md.digest(fingerprintString().toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { b -> "%02x".format(b) }
}
