package com.majiang.counter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [expectedWallRemainingOrNull] / [hudMatchesReconciledWall] 的守恒与容差单测。
 */
class GameStateReconcileTest {

    private fun emptyDiscards(): Map<Seat, List<Tile>> =
        Seat.entries.associateWith { emptyList() }

    private fun emptyMelds(): Map<Seat, List<OpenMeld>> =
        Seat.entries.associateWith { emptyList() }

    @Test
    fun expectedWall_nullWhenAnyOpponentCountMissing() {
        val gs = GameState(
            myHand = List(13) { Tile(Suit.WAN, 1) },
            discards = emptyDiscards(),
            openMelds = emptyMelds(),
            mySeat = Seat.SOUTH,
            opponentHandCount = Seat.entries.associateWith { null },
        )
        assertNull(gs.expectedWallRemainingOrNull())
    }

    @Test
    fun expectedWall_balancedMidGame() {
        // 本家 13 + 三家各 13 = 52；河共 10 张；无副露 → 墙 = 108 - 52 - 10 = 46
        val disc = buildMap {
            put(Seat.EAST, listOf(Tile(Suit.WAN, 2)))
            put(Seat.SOUTH, List(5) { Tile(Suit.TIAO, 3) })
            put(Seat.WEST, List(4) { Tile(Suit.TONG, 4) })
        }
        val gs = GameState(
            myHand = List(13) { Tile(Suit.WAN, 1) },
            discards = Seat.entries.associateWith { disc[it].orEmpty() },
            openMelds = emptyMelds(),
            mySeat = Seat.SOUTH,
            opponentHandCount = mapOf(
                Seat.EAST to 13,
                Seat.NORTH to 13,
                Seat.WEST to 13,
                Seat.SOUTH to null,
            ),
        )
        assertEquals(46, gs.expectedWallRemainingOrNull())
    }

    @Test
    fun expectedWall_subtractsOpenMelds() {
        val meld = OpenMeld(OpenMeldKind.PONG, listOf(Tile(Suit.WAN, 5), Tile(Suit.WAN, 5), Tile(Suit.WAN, 5)))
        val gs = GameState(
            myHand = List(13) { Tile(Suit.WAN, 1) },
            discards = emptyDiscards(),
            openMelds = mapOf(
                Seat.EAST to listOf(meld),
                Seat.SOUTH to emptyList(),
                Seat.WEST to emptyList(),
                Seat.NORTH to emptyList(),
            ),
            mySeat = Seat.SOUTH,
            opponentHandCount = mapOf(
                Seat.EAST to 10,
                Seat.NORTH to 13,
                Seat.WEST to 13,
                Seat.SOUTH to null,
            ),
        )
        // 108 - 13 - 10 - 13 - 13 - 3(meld) = 56
        assertEquals(56, gs.expectedWallRemainingOrNull())
    }

    @Test
    fun hudMatches_withinTolerance() {
        val gs = GameState(
            myHand = List(13) { Tile(Suit.WAN, 1) },
            discards = emptyDiscards(),
            openMelds = emptyMelds(),
            mySeat = Seat.SOUTH,
            opponentHandCount = mapOf(
                Seat.EAST to 13,
                Seat.NORTH to 13,
                Seat.WEST to 13,
                Seat.SOUTH to null,
            ),
        )
        val exp = gs.expectedWallRemainingOrNull()!!
        assertTrue(gs.hudMatchesReconciledWall(exp, toleranceTiles = 2))
        assertTrue(gs.hudMatchesReconciledWall(exp + 2, toleranceTiles = 2))
        assertFalse(gs.hudMatchesReconciledWall(exp + 3, toleranceTiles = 2))
    }

    @Test
    fun hudMatches_skipsWhenHudOrCountsIncomplete() {
        val gs = GameState(
            myHand = List(13) { Tile(Suit.WAN, 1) },
            discards = emptyDiscards(),
            openMelds = emptyMelds(),
            opponentHandCount = Seat.entries.associateWith { null },
        )
        assertTrue(gs.hudMatchesReconciledWall(hud = 99, toleranceTiles = 0))
        val gs2 = gs.copy(
            opponentHandCount = mapOf(
                Seat.EAST to 13,
                Seat.NORTH to 13,
                Seat.WEST to 13,
                Seat.SOUTH to null,
            ),
        )
        assertTrue(gs2.hudMatchesReconciledWall(hud = null, toleranceTiles = 0))
    }
}
