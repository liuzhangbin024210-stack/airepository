package com.majiang.counter.analysis

import com.google.common.truth.Truth.assertThat
import com.majiang.counter.domain.GameState
import com.majiang.counter.domain.Seat
import com.majiang.counter.domain.Suit
import com.majiang.counter.domain.Tile
import com.majiang.counter.rules.RulesConfig
import com.majiang.counter.rules.SichuanRulesEngine
import kotlin.random.Random
import org.junit.Test

class SituationAnalyzerTest {

    @Test
    fun recommendedDiscardOrder_prefersLowerStudentRonAmongLegalDiscards() {
        val t1 = Tile(Suit.TONG, 1)
        val t2 = Tile(Suit.TONG, 2)
        val ron = FloatArray(27) { 0f }
        ron[t1.suit.ordinal * 9 + (t1.rank - 1)] = 0.95f
        ron[t2.suit.ordinal * 9 + (t2.rank - 1)] = 0.05f
        val kong = FloatArray(27) { 0f }
        val hu = FloatArray(27) { 1f / 27f }
        val engine = SichuanRulesEngine(RulesConfig())
        val an = SituationAnalyzer(
            engine,
            object : PolicyStudentInterpreter {
                override fun infer(featureVector: FloatArray): StudentPolicyV1 =
                    StudentPolicyV1(hu27 = hu, ron27 = ron, kong27 = kong)
            },
        )
        val st = GameState(
            mySeat = Seat.SOUTH,
            myHand = buildList {
                add(t1)
                add(t2)
                repeat(3) { add(Tile(Suit.WAN, 1)) }
                repeat(3) { add(Tile(Suit.WAN, 2)) }
                repeat(3) { add(Tile(Suit.WAN, 3)) }
                add(Tile(Suit.WAN, 4))
                add(Tile(Suit.WAN, 5))
            },
            dingQue = Seat.entries.associateWith { if (it == Seat.SOUTH) Suit.TONG else Suit.WAN },
        )
        val (_, risk) = an.analyze(st, iterations = 40, random = Random(2))
        assertThat(risk.recommendedDiscard).isEqualTo(t2)
        assertThat(risk.recommendedDiscardOrder.first()).isEqualTo(t2)
        assertThat(risk.recommendedDiscardOrder.last()).isEqualTo(t1)
    }

    @Test
    fun analyze_runs_without_crash() {
        val engine = SichuanRulesEngine(RulesConfig())
        val an = SituationAnalyzer(
            engine,
            object : PolicyStudentInterpreter {
                override fun infer(featureVector: FloatArray): StudentPolicyV1? = null
            },
        )
        val st = GameState(
            mySeat = Seat.SOUTH,
            myHand = buildList {
                repeat(3) { add(Tile(Suit.TONG, 1)) }
                repeat(3) { add(Tile(Suit.TONG, 2)) }
                repeat(3) { add(Tile(Suit.TONG, 3)) }
                repeat(3) { add(Tile(Suit.TONG, 4)) }
                add(Tile(Suit.TONG, 5))
            },
            dingQue = Seat.entries.associateWith { Suit.WAN },
        )
        val (ten, risk) = an.analyze(st, iterations = 30, random = Random(1))
        assertThat(ten.sampleCount).isAtLeast(0)
        assertThat(risk.sampleCount).isAtLeast(0)
    }
}
