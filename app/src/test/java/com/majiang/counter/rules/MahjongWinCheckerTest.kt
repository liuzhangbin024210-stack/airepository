package com.majiang.counter.rules

import com.google.common.truth.Truth.assertThat
import com.majiang.counter.domain.Suit
import com.majiang.counter.domain.Tile
import org.junit.Test

class MahjongWinCheckerTest {

    @Test
    fun winning_four_triplets_and_pair() {
        val hand = buildList {
            repeat(3) { add(Tile(Suit.WAN, 1)) }
            repeat(3) { add(Tile(Suit.WAN, 2)) }
            repeat(3) { add(Tile(Suit.WAN, 3)) }
            repeat(3) { add(Tile(Suit.WAN, 4)) }
            add(Tile(Suit.WAN, 5))
            add(Tile(Suit.WAN, 5))
        }
        assertThat(hand.size).isEqualTo(14)
        assertThat(MahjongWinChecker.isWinningHand14(hand)).isTrue()
    }

    @Test
    fun waiting_contains_missing_tile() {
        val hand13 = buildList {
            repeat(3) { add(Tile(Suit.WAN, 1)) }
            repeat(3) { add(Tile(Suit.WAN, 2)) }
            repeat(3) { add(Tile(Suit.WAN, 3)) }
            repeat(3) { add(Tile(Suit.WAN, 4)) }
            add(Tile(Suit.WAN, 5))
        }
        val w = MahjongWinChecker.waitingTiles(hand13)
        assertThat(w).contains(Tile(Suit.WAN, 5))
    }
}
