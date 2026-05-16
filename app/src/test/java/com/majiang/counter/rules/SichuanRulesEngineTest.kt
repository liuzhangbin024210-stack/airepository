package com.majiang.counter.rules

import com.google.common.truth.Truth.assertThat
import com.majiang.counter.domain.Seat
import com.majiang.counter.domain.Suit
import com.majiang.counter.domain.Tile
import org.junit.Test

/**
 * 四川血战子集：定缺、一炮多响、明杠、已胡离场等牌例与边界。
 */
class SichuanRulesEngineTest {

    private val defaultEngine = SichuanRulesEngine(RulesConfig())

    /** 听 5 万的标准 13 张（由已知 14 张胡牌型去掉一张 5 万得到）。 */
    private fun hand13Tenpai5Man(): List<Tile> {
        val win14 = buildList {
            repeat(3) { add(Tile(Suit.WAN, 1)) }
            repeat(3) { add(Tile(Suit.WAN, 2)) }
            repeat(3) { add(Tile(Suit.WAN, 3)) }
            repeat(3) { add(Tile(Suit.WAN, 4)) }
            add(Tile(Suit.WAN, 5))
            add(Tile(Suit.WAN, 5))
        }
        return win14.dropLast(1)
    }

    /** 与 5 万荣和无关的 13 张（全筒 + 刻子条，不含听 5 万）。 */
    private fun hand13NoRonOn5Man(): List<Tile> = buildList {
        for (r in 1..9) add(Tile(Suit.TONG, r))
        repeat(4) { add(Tile(Suit.TIAO, 1)) }
    }

    @Test
    fun waitingTiles_contains_5_man_for_sample_tenpai() {
        val h = hand13Tenpai5Man()
        val w = defaultEngine.waitingTiles(h, que = null)
        assertThat(w).contains(Tile(Suit.WAN, 5))
    }

    @Test
    fun ronSeats_multiRon_two_winners_default_config() {
        val h = hand13Tenpai5Man()
        val discard = Tile(Suit.WAN, 5)
        val opp = mapOf(
            Seat.EAST to h,
            Seat.NORTH to hand13NoRonOn5Man(),
            Seat.WEST to h,
        )
        val ding = Seat.entries.associateWith { null as Suit? }
        val winners = defaultEngine.ronSeats(
            discarder = Seat.SOUTH,
            discard = discard,
            opponentHands13 = opp,
            dingQue = ding,
        )
        assertThat(winners).containsExactly(Seat.EAST, Seat.WEST)
    }

    @Test
    fun ronSeats_singleWinner_when_multiRon_disabled() {
        val engine = SichuanRulesEngine(RulesConfig(allowMultiRon = false))
        val h = hand13Tenpai5Man()
        val discard = Tile(Suit.WAN, 5)
        val opp = mapOf(
            Seat.EAST to h,
            Seat.NORTH to hand13NoRonOn5Man(),
            Seat.WEST to h,
        )
        val ding = Seat.entries.associateWith { null as Suit? }
        val winners = engine.ronSeats(
            discarder = Seat.SOUTH,
            discard = discard,
            opponentHands13 = opp,
            dingQue = ding,
        )
        assertThat(winners).containsExactly(Seat.EAST)
    }

    @Test
    fun ronSeats_skips_inactive_even_if_map_contains_hand() {
        val h = hand13Tenpai5Man()
        val discard = Tile(Suit.WAN, 5)
        val opp = mapOf(
            Seat.EAST to h,
            Seat.WEST to h,
        )
        val winners = defaultEngine.ronSeats(
            discarder = Seat.SOUTH,
            discard = discard,
            opponentHands13 = opp,
            dingQue = Seat.entries.associateWith { null },
            inactiveSeats = setOf(Seat.WEST),
        )
        assertThat(winners).containsExactly(Seat.EAST)
    }

    @Test
    fun canRon_false_when_que_blocks_win() {
        val h = hand13Tenpai5Man()
        assertThat(defaultEngine.canRon(h, Tile(Suit.WAN, 5), que = Suit.WAN)).isFalse()
    }

    @Test
    fun dingQueLegalDiscards_only_que_suit_while_holding() {
        val hand = listOf(
            Tile(Suit.WAN, 1),
            Tile(Suit.WAN, 2),
            Tile(Suit.TONG, 1),
        )
        val legal = defaultEngine.dingQueLegalDiscardsInHand(hand, que = Suit.TONG)
        assertThat(legal).containsExactly(Tile(Suit.TONG, 1))
    }

    @Test
    fun kongSeats_when_three_in_hand() {
        val hand = buildList {
            repeat(3) { add(Tile(Suit.WAN, 1)) }
            repeat(10) { add(Tile(Suit.TONG, (it % 9) + 1)) }
        }
        assertThat(hand.size).isEqualTo(13)
        val opp = mapOf(Seat.EAST to hand)
        val seats = defaultEngine.kongSeats(
            discarder = Seat.SOUTH,
            discard = Tile(Suit.WAN, 1),
            opponentHands13 = opp,
        )
        assertThat(seats).containsExactly(Seat.EAST)
    }

    @Test
    fun seatsStillPlaying_excludes_won() {
        assertThat(defaultEngine.seatsStillPlaying(setOf(Seat.EAST, Seat.NORTH)))
            .containsExactly(Seat.SOUTH, Seat.WEST)
    }
}
