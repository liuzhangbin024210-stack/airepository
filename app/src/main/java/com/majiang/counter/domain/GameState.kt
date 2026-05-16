package com.majiang.counter.domain

/**
 * 一局游戏的公开/半公开状态（手录或视觉门禁写入后的真值）。
 *
 * @property mySeat 本家座位（默认南：屏幕下方）。
 * @property myHand 本家手牌（分析听牌需恰好 13 张）。
 * @property discards 各家牌河（顺序即打出先后）。
 * @property dingQue 各家定缺；null 表示未定缺或未录入。
 * @property openMelds 明示副露（v1 可先空，仅影响剩余张数统计）。
 * @property dealerSeat 庄家座位，可选。
 * @property phase 对局阶段；换三张等阶段视觉管线不得误计河牌。
 * @property wonSeats 血战已胡离场座位，MC 对手抽样时排除。
 * @property hudRemainingTiles 画面 HUD「剩 NN 张」，可选，用于后续守恒对账。
 * @property opponentHandCount 三家背面手数 UI，可选。
 */
data class GameState(
    val mySeat: Seat = Seat.SOUTH,
    val myHand: List<Tile> = emptyList(),
    val discards: Map<Seat, List<Tile>> = Seat.entries.associateWith { emptyList() },
    val dingQue: Map<Seat, Suit?> = Seat.entries.associateWith { null },
    val openMelds: Map<Seat, List<OpenMeld>> = Seat.entries.associateWith { emptyList() },
    val dealerSeat: Seat? = null,
    val phase: GamePhase = GamePhase.PLAYING,
    val wonSeats: Set<Seat> = emptySet(),
    val hudRemainingTiles: Int? = null,
    val opponentHandCount: Map<Seat, Int?> = Seat.entries.associateWith { null },
) {
    /**
     * 墙中各牌型剩余张数（长度 27，下标与 [Tile] 遍历顺序一致：花色 ordinal*9 + rank-1）。
     * 从 108 张减去本家手牌、各河、各副露中已出现的张数。
     */
    fun remainingCounts(): IntArray {
        val counts = IntArray(27) { 4 }
        fun dec(t: Tile) {
            val idx = t.suit.ordinal * 9 + (t.rank - 1)
            if (counts[idx] > 0) counts[idx]--
        }
        myHand.forEach(::dec)
        discards.values.flatten().forEach(::dec)
        openMelds.values.flatten().forEach { meld -> meld.tiles.forEach(::dec) }
        return counts
    }
}
