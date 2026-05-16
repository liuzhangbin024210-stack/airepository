package com.majiang.counter.domain

/**
 * 明示副露（v1 数据结构；完整识别后续接皮肤包）。
 */
enum class OpenMeldKind {
    PONG,
    MING_GANG,
}

/**
 * 一组明副露（牌面列表 + 类型）。
 */
data class OpenMeld(
    val kind: OpenMeldKind,
    val tiles: List<Tile>,
)
