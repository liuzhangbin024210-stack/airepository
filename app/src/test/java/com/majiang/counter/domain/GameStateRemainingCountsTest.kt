package com.majiang.counter.domain

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [GameState.remainingCounts]：从 108 张牌墙扣减本家手牌、各河、各副露后的剩余张数向量（长度 27）。
 */
class GameStateRemainingCountsTest {

    private fun emptyDiscards(): Map<Seat, List<Tile>> =
        Seat.entries.associateWith { emptyList() }

    private fun emptyMelds(): Map<Seat, List<OpenMeld>> =
        Seat.entries.associateWith { emptyList() }

    @Test
    fun remainingCounts_emptyState_allFours() {
        val gs = GameState(
            myHand = emptyList(),
            discards = emptyDiscards(),
            openMelds = emptyMelds(),
        )
        val c = gs.remainingCounts()
        assertEquals(27, c.size)
        assertTrueAll(c, 4)
    }

    @Test
    fun remainingCounts_myHandDecrements() {
        val hand = listOf(
            Tile(Suit.WAN, 1),
            Tile(Suit.WAN, 1),
            Tile(Suit.TIAO, 9),
        )
        val gs = GameState(
            myHand = hand,
            discards = emptyDiscards(),
            openMelds = emptyMelds(),
        )
        val c = gs.remainingCounts()
        assertEquals(2, c[idx(Suit.WAN, 1)])
        assertEquals(3, c[idx(Suit.TIAO, 9)])
    }

    @Test
    fun remainingCounts_discardsAndMeldsCombined() {
        val meld = OpenMeld(
            OpenMeldKind.PONG,
            listOf(Tile(Suit.TONG, 5), Tile(Suit.TONG, 5), Tile(Suit.TONG, 5)),
        )
        val gs = GameState(
            myHand = listOf(Tile(Suit.WAN, 3)),
            discards = mapOf(
                Seat.EAST to listOf(Tile(Suit.WAN, 3)),
                Seat.SOUTH to emptyList(),
                Seat.WEST to emptyList(),
                Seat.NORTH to emptyList(),
            ),
            openMelds = mapOf(
                Seat.WEST to listOf(meld),
                Seat.EAST to emptyList(),
                Seat.SOUTH to emptyList(),
                Seat.NORTH to emptyList(),
            ),
        )
        val c = gs.remainingCounts()
        // WAN 3：手 1 + 河 1 → 剩 2
        assertEquals(2, c[idx(Suit.WAN, 3)])
        // TONG 5：副露 3 → 剩 1
        assertEquals(1, c[idx(Suit.TONG, 5)])
    }

    @Test
    fun remainingCounts_matchesFullDeckMinusVisible() {
        val all = Tile.allTypes()
        val visible = all.flatMap { t -> List(4) { t } } // 108 张全在场上
        val gs = GameState(
            myHand = visible.take(13),
            discards = mapOf(
                Seat.EAST to visible.drop(13).take(30),
                Seat.SOUTH to visible.drop(43).take(30),
                Seat.WEST to visible.drop(73).take(20),
                Seat.NORTH to visible.drop(93).take(15),
            ),
            openMelds = emptyMelds(),
        )
        val c = gs.remainingCounts()
        assertArrayEquals(IntArray(27) { 0 }, c)
    }

    private fun idx(suit: Suit, rank: Int): Int = suit.ordinal * 9 + (rank - 1)

    private fun assertTrueAll(arr: IntArray, v: Int) {
        for (x in arr) assertEquals(v, x)
    }
}
