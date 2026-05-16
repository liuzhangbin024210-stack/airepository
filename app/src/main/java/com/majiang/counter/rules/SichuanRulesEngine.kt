package com.majiang.counter.rules

import com.majiang.counter.domain.Seat
import com.majiang.counter.domain.Suit
import com.majiang.counter.domain.Tile

/**
 * 四川血战规则子集：定缺、无吃、胡/听/点炮/明杠判定；一炮多响返回和牌家集合。
 *
 * **动作优先级（桌规提示）**：多家同时可胡/碰/杠时，产品层与目标 App 一致采用「胡优先于碰杠」；
 * 本类分别给出荣和家集与可明杠家集，由上层合并决策。
 */
class SichuanRulesEngine(
    private val config: RulesConfig = RulesConfig(),
) {

    /**
     * 血战：仍未胡牌的座位（未胡者仍可参与摸打与荣和判定上下文）。
     */
    fun seatsStillPlaying(wonSeats: Set<Seat>): Set<Seat> =
        Seat.entries.filter { it !in wonSeats }.toSet()

    /**
     * 胡牌型判定：标准 4 面子 + 1 雀头，且手牌 14 张中 **不得含该家定缺花色**。
     */
    fun isWinningHand14(hand14: List<Tile>, que: Suit?): Boolean {
        if (que != null && hand14.any { it.suit == que }) return false
        return MahjongWinChecker.isWinningHand14(hand14)
    }

    /**
     * 13 张听牌集合（不含定缺门进张；与 [MahjongWinChecker.waitingTiles] 一致但加定缺）。
     */
    fun waitingTiles(hand13: List<Tile>, que: Suit?): Set<Tile> {
        if (hand13.size != 13) return emptySet()
        val out = mutableSetOf<Tile>()
        for (t in Tile.allTypes()) {
            if (que != null && t.suit == que) continue
            val h = hand13 + t
            if (isWinningHand14(h, que)) out.add(t)
        }
        return out
    }

    /**
     * 点炮：某家 13 张 + 打出牌是否可胡（定缺约束）。
     */
    fun canRon(opponentHand13: List<Tile>, discard: Tile, que: Suit?): Boolean {
        if (opponentHand13.size != 13) return false
        return isWinningHand14(opponentHand13 + discard, que)
    }

    /**
     * 一炮多响：返回所有可对 [discard] 荣和的座位集合（不含 [discarder]）。
     *
     * @param inactiveSeats 已胡离场等不再参与荣和的座位（血战）；缺省为空。
     */
    fun ronSeats(
        discarder: Seat,
        discard: Tile,
        opponentHands13: Map<Seat, List<Tile>>,
        dingQue: Map<Seat, Suit?>,
        inactiveSeats: Set<Seat> = emptySet(),
    ): Set<Seat> {
        val winners = mutableSetOf<Seat>()
        for ((seat, hand) in opponentHands13) {
            if (seat == discarder) continue
            if (seat in inactiveSeats) continue
            val q = dingQue[seat]
            if (canRon(hand, discard, q)) winners.add(seat)
        }
        if (!config.allowMultiRon && winners.size > 1) {
            // 极少数房规仅一家和：取最小座位枚举序第一家（实现可配置时再细化）
            val first = winners.minByOrNull { it.ordinal }!!
            return setOf(first)
        }
        return winners
    }

    fun canMingGangOnDiscard(opponentHand13: List<Tile>, discard: Tile): Boolean =
        MahjongWinChecker.canMingGangOnDiscard(opponentHand13, discard)

    /**
     * 「打缺」：手牌仍含定缺花色时，只能打出该花色；未定缺或未持缺门则任意手牌可打。
     */
    fun dingQueLegalDiscardsInHand(hand: List<Tile>, que: Suit?): Set<Tile> {
        if (que == null) return hand.toSet()
        if (!hand.any { it.suit == que }) return hand.toSet()
        return hand.filter { it.suit == que }.toSet()
    }

    /**
     * 是否存在至少一家可点杠（明杠）该弃牌。
     *
     * @param inactiveSeats 已胡离场等不再抢杠的座位（血战常见口径：和牌家不再操作）。
     */
    fun kongSeats(
        discarder: Seat,
        discard: Tile,
        opponentHands13: Map<Seat, List<Tile>>,
        inactiveSeats: Set<Seat> = emptySet(),
    ): Set<Seat> {
        val s = mutableSetOf<Seat>()
        for ((seat, hand) in opponentHands13) {
            if (seat == discarder) continue
            if (seat in inactiveSeats) continue
            if (canMingGangOnDiscard(hand, discard)) s.add(seat)
        }
        return s
    }

    fun rulesConfig(): RulesConfig = config
}
