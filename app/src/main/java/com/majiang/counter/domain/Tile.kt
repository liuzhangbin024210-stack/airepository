package com.majiang.counter.domain

/**
 * 一种牌（点数 1–9）。
 *
 * @property suit 花色
 * @property rank 点数 1–9
 */
data class Tile(val suit: Suit, val rank: Int) : Comparable<Tile> {
    init {
        require(rank in 1..9) { "rank 必须在 1..9" }
    }

    override fun compareTo(other: Tile): Int {
        val c = suit.compareTo(other.suit)
        return if (c != 0) c else rank.compareTo(other.rank)
    }

    companion object {
        /** 全部 27 种牌型（每种牌墙 4 张，此处仅类型）。 */
        fun allTypes(): List<Tile> =
            Suit.entries.flatMap { s -> (1..9).map { r -> Tile(s, r) } }
    }
}
