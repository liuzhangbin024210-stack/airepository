package com.majiang.counter.rules

import com.majiang.counter.domain.Tile

/**
 * 四川 108 张麻将胡牌判定（万筒条，无字牌）：4 面子 + 1 雀头。
 * 面子可为刻子或顺子（同花色连续三张）。
 */
object MahjongWinChecker {

    private fun Tile.index(): Int = suit.ordinal * 9 + (rank - 1)

    /**
     * 将手牌转为 27 维张数向量。
     */
    fun counts(hand: List<Tile>): IntArray {
        val c = IntArray(27)
        for (t in hand) c[t.index()]++
        return c
    }

    /**
     * 14 张是否成胡牌型。
     */
    fun isWinningHand14(hand: List<Tile>): Boolean {
        if (hand.size != 14) return false
        return isWinningHand14(counts(hand))
    }

    /**
     * @param counts 长度 27，总和须为 14
     */
    fun isWinningHand14(counts: IntArray): Boolean {
        require(counts.size == 27)
        if (counts.sum() != 14) return false
        for (p in 0 until 27) {
            if (counts[p] < 2) continue
            val rest = counts.clone()
            rest[p] -= 2
            if (composeFourMelds(rest)) return true
        }
        return false
    }

    /**
     * 13 张听牌：是否存在某张进张后成胡（不考虑牌墙是否还有该张）。
     */
    fun waitingTiles(hand13: List<Tile>): Set<Tile> {
        if (hand13.size != 13) return emptySet()
        val result = mutableSetOf<Tile>()
        for (t in Tile.allTypes()) {
            val h = hand13 + t
            if (isWinningHand14(h)) result.add(t)
        }
        return result
    }

    /**
     * 点炮：对手 13 张 + 打出的牌 1 张是否构成胡牌。
     */
    fun canRonOnDiscard(opponentHand13: List<Tile>, discard: Tile): Boolean {
        if (opponentHand13.size != 13) return false
        return isWinningHand14(opponentHand13 + discard)
    }

    /**
     * 点杠（简化）：对手手中已有 **3** 张与打出牌相同，打出该牌后构成明杠。
     */
    fun canMingGangOnDiscard(opponentHand13: List<Tile>, discard: Tile): Boolean =
        opponentHand13.count { it == discard } == 3

    private fun composeFourMelds(c: IntArray): Boolean {
        val i = c.indexOfFirst { it > 0 }
        if (i == -1) return true

        val rank = i % 9
        // 顺子（同花色内，且不从 8、9 万/筒/条开头跨花色）
        if (rank <= 6 && c[i] >= 1 && c[i + 1] >= 1 && c[i + 2] >= 1) {
            val copyChi = c.clone()
            copyChi[i]--
            copyChi[i + 1]--
            copyChi[i + 2]--
            if (composeFourMelds(copyChi)) return true
        }

        // 刻子（放在顺子之后尝试，避免 333444 等牌型被错误只吃顺）
        if (c[i] >= 3) {
            val copy = c.clone()
            copy[i] -= 3
            if (composeFourMelds(copy)) return true
        }

        return false
    }
}
