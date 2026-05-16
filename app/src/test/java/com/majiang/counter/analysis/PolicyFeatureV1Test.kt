package com.majiang.counter.analysis

import com.google.common.truth.Truth.assertThat
import com.majiang.counter.domain.GamePhase
import com.majiang.counter.domain.GameState
import com.majiang.counter.domain.Seat
import com.majiang.counter.domain.Suit
import com.majiang.counter.domain.Tile
import org.junit.Test

class PolicyFeatureV1Test {

    @Test
    fun build_featureLength_and_phase_oneHot() {
        val st = GameState(
            mySeat = Seat.SOUTH,
            myHand = List(13) { Tile(Suit.TONG, 1) },
            phase = GamePhase.EXCHANGE_THREE,
            dingQue = Seat.entries.associateWith { Suit.WAN },
            dealerSeat = Seat.EAST,
            wonSeats = setOf(Seat.NORTH),
        )
        val v = PolicyFeatureV1.build(st)
        assertThat(v.size).isEqualTo(PolicyFeatureV1.FEATURE_DIM)
        assertThat(v[66]).isEqualTo(0f)
        assertThat(v[67]).isEqualTo(1f)
        assertThat(v[68]).isEqualTo(0f)
        assertThat(v[69 + Seat.SOUTH.ordinal]).isEqualTo(1f)
        assertThat(v[73 + Seat.NORTH.ordinal]).isEqualTo(1f)
        assertThat(v[77 + Seat.EAST.ordinal]).isEqualTo(1f)
    }

    @Test
    fun studentRonKongOnCandidates_maps_by_tile_type() {
        val ron = FloatArray(27) { 0f }
        val kong = FloatArray(27) { 0f }
        ron[0] = 0.8f
        kong[0] = 0.1f
        val c = setOf(Tile(Suit.WAN, 1), Tile(Suit.TONG, 5))
        val (rm, km) = PolicyFeatureV1.studentRonKongOnCandidates(ron, kong, c)!!
        assertThat(rm[Tile(Suit.WAN, 1)]).isEqualTo(0.8f)
        assertThat(km[Tile(Suit.WAN, 1)]).isEqualTo(0.1f)
        assertThat(rm[Tile(Suit.TONG, 5)]).isEqualTo(0f)
    }

    @Test
    fun studentHuOnWaitingTiles_renormalizes() {
        val full = FloatArray(27) { 0f }
        val idxT9 = Suit.TONG.ordinal * 9 + (9 - 1)
        val idxW1 = Suit.WAN.ordinal * 9 + (1 - 1)
        full[idxT9] = 2f
        full[idxW1] = 1f
        val w = setOf(Tile(Suit.TONG, 9), Tile(Suit.WAN, 1))
        val m = PolicyFeatureV1.studentHuOnWaitingTiles(full, w)!!
        assertThat(m.values.sum()).isWithin(1e-5f).of(1f)
        assertThat(m[Tile(Suit.TONG, 9)]).isGreaterThan(m[Tile(Suit.WAN, 1)]!!)
    }
}
