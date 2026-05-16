package com.majiang.counter.rules

/**
 * 四川血战子集开关（未确认项默认 false，与修订计划一致）。
 *
 * @property allowMultiRon 一炮多响（四川既定规则，默认 true）。
 * @property lastWallMustWin 牌墙末张必胡等（默认 false）。
 * @property checkFlowerPig 查花猪（默认 false）。
 * @property checkDaJiao 查大叫（默认 false）。
 */
data class RulesConfig(
    val allowMultiRon: Boolean = true,
    val lastWallMustWin: Boolean = false,
    val checkFlowerPig: Boolean = false,
    val checkDaJiao: Boolean = false,
) {
    /**
     * 用于与 TFLite `model_manifest` 等绑定的稳定哈希种子（实现侧可序列化后 SHA-256）。
     */
    fun fingerprintString(): String =
        listOf(
            allowMultiRon,
            lastWallMustWin,
            checkFlowerPig,
            checkDaJiao,
        ).joinToString(",")
}
