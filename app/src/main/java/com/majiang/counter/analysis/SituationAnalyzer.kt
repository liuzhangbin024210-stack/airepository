package com.majiang.counter.analysis

import com.majiang.counter.domain.Seat
import com.majiang.counter.domain.Suit
import com.majiang.counter.domain.Tile
import com.majiang.counter.domain.GameState
import com.majiang.counter.rules.SichuanRulesEngine
import kotlin.random.Random

/**
 * 听牌侧：听牌列表 + 各听牌张的 MC 胡牌进张估计。
 *
 * @property studentHuProbByWaiting 学生模型在听牌子集上归一化后的参考分布；无模型或校验失败时为 null。
 */
data class TenpaiInsight(
    val waitingTiles: Set<Tile>,
    /** 各听牌张在 MC 下「下一摸为 w 且可胡」的相对频率（0..1，总和不必为 1）。 */
    val huProbByWaiting: Map<Tile, Float>,
    val sampleCount: Int,
    val studentHuProbByWaiting: Map<Tile, Float>? = null,
)

/**
 * 打出风险：对每种打出的牌 d，「∃ 至少一家点炮 / 点杠」的 MC 估计（v1 固定口径）。
 *
 * @property studentRonAnyByDiscard 学生模型对 RonAny 的估计；无打出头或未加载模型时为 null。
 * @property studentKongAnyByDiscard 学生模型对点杠存在性的估计；null 同上。
 * @property recommendedDiscardOrder v1 打法建议：合法打出候选按「先压 RonAny、再压点杠」升序；学生两头齐全时优先用 TFLite，否则用 MC。
 * @property recommendedDiscard 首推打出（[recommendedDiscardOrder] 首项）；无合法打出候选时为 null。
 */
data class DiscardRiskInsight(
    val ronAnyByDiscard: Map<Tile, Float>,
    val kongAnyByDiscard: Map<Tile, Float>,
    val sampleCount: Int,
    val studentRonAnyByDiscard: Map<Tile, Float>? = null,
    val studentKongAnyByDiscard: Map<Tile, Float>? = null,
    val recommendedDiscardOrder: List<Tile> = emptyList(),
    val recommendedDiscard: Tile? = recommendedDiscardOrder.firstOrNull(),
)

/**
 * 局势分析：L1 MC 教师；[PolicyStudentInterpreter] 成功时并行展示听牌与打出侧学生估计（模型仅 27 维输出时无打出头）。
 */
class SituationAnalyzer(
    private val engine: SichuanRulesEngine,
    private val policyStudent: PolicyStudentInterpreter,
) {

    /**
     * @param iterations MC 次数（默认 300）。
     */
    fun analyze(
        state: GameState,
        iterations: Int = 300,
        random: Random = Random.Default,
    ): Pair<TenpaiInsight, DiscardRiskInsight> {
        val mySeat = state.mySeat
        val que = state.dingQue[mySeat]
        val hand = state.myHand
        val opponents = Seat.entries.filter { it != mySeat && it !in state.wonSeats }

        val waiting = if (hand.size == 13) {
            engine.waitingTiles(hand, que)
        } else {
            emptySet()
        }

        val legalDiscards = engine.dingQueLegalDiscardsInHand(hand, que)
        val discardCandidates = hand.filter { it in legalDiscards }.groupingBy { it }.eachCount().keys

        val huCounts = waiting.associateWith { 0 }.toMutableMap()
        val ronCounts = mutableMapOf<Tile, Int>()
        val kongCounts = mutableMapOf<Tile, Int>()
        for (d in discardCandidates) {
            ronCounts[d] = 0
            kongCounts[d] = 0
        }

        var validSamples = 0
        repeat(iterations) {
            val pool = buildUnknownPool(state, random)
            val requiredPool = (opponents.size * 13 + 1).coerceAtLeast(1)
            if (pool.size < requiredPool) return@repeat
            pool.shuffle(random)
            var idx = 0
            val oppHands = mutableMapOf<Seat, MutableList<Tile>>()
            for (s in opponents) {
                val h = mutableListOf<Tile>()
                repeat(13) {
                    if (idx < pool.size) h.add(pool[idx++])
                }
                oppHands[s] = h
            }
            if (idx >= pool.size) return@repeat
            val nextTile = pool[idx]
            validSamples++

            if (hand.size == 13) {
                for (w in waiting) {
                    if (nextTile == w && engine.isWinningHand14(hand + w, que)) {
                        huCounts[w] = huCounts.getValue(w) + 1
                    }
                }
            }

            for (d in discardCandidates) {
                val handAfter = hand.toMutableList()
                val removed = handAfter.indexOfFirst { it == d }
                if (removed < 0) continue
                handAfter.removeAt(removed)
                if (handAfter.size != 13) continue
                val ron = engine.ronSeats(
                    mySeat,
                    d,
                    oppHands.mapValues { it.value },
                    state.dingQue,
                    inactiveSeats = state.wonSeats,
                )
                if (ron.isNotEmpty()) ronCounts[d] = ronCounts.getValue(d) + 1
                val kong = engine.kongSeats(
                    mySeat,
                    d,
                    oppHands.mapValues { it.value },
                    inactiveSeats = state.wonSeats,
                )
                if (kong.isNotEmpty()) kongCounts[d] = kongCounts.getValue(d) + 1
            }
        }

        val denom = validSamples.coerceAtLeast(1).toFloat()
        val huProb = waiting.associateWith { w -> huCounts.getValue(w) / denom }
        val ronProb = discardCandidates.associateWith { d -> ronCounts.getValue(d) / denom }
        val kongProb = discardCandidates.associateWith { d -> kongCounts.getValue(d) / denom }

        val vec = PolicyFeatureV1.build(state)
        val pol = policyStudent.infer(vec)
        val studentHu = if (pol != null && waiting.isNotEmpty()) {
            PolicyFeatureV1.studentHuOnWaitingTiles(pol.hu27, waiting)
        } else {
            null
        }
        val discardStudent = if (
            pol != null &&
            pol.ron27 != null &&
            pol.kong27 != null &&
            discardCandidates.isNotEmpty()
        ) {
            PolicyFeatureV1.studentRonKongOnCandidates(pol.ron27, pol.kong27, discardCandidates)
        } else {
            null
        }

        val recOrder = buildRecommendedDiscardOrder(
            discardCandidates,
            ronProb,
            kongProb,
            discardStudent?.first,
            discardStudent?.second,
        )

        return TenpaiInsight(waiting, huProb, validSamples, studentHu) to
            DiscardRiskInsight(
                ronProb,
                kongProb,
                validSamples,
                studentRonAnyByDiscard = discardStudent?.first,
                studentKongAnyByDiscard = discardStudent?.second,
                recommendedDiscardOrder = recOrder,
            )
    }

    /**
     * v1 推荐打出排序：在合法候选上最小化 RonAny，其次最小化点杠存在性估计；TFLite 的 Ron+杠头齐全时优先用学生，否则用 MC。
     */
    private fun buildRecommendedDiscardOrder(
        candidates: Set<Tile>,
        mcRon: Map<Tile, Float>,
        mcKong: Map<Tile, Float>,
        stuRon: Map<Tile, Float>?,
        stuKong: Map<Tile, Float>?,
    ): List<Tile> {
        if (candidates.isEmpty()) return emptyList()
        val useStudent =
            stuRon != null &&
                stuKong != null &&
                candidates.all { it in stuRon && it in stuKong }
        fun ronOf(t: Tile): Float =
            if (useStudent) stuRon!![t] ?: 1f else mcRon[t] ?: 1f
        fun kongOf(t: Tile): Float =
            if (useStudent) stuKong!![t] ?: 1f else mcKong[t] ?: 1f
        return candidates.sortedWith(
            compareBy<Tile>({ ronOf(it) }, { kongOf(it) }, { it.suit.ordinal * 9 + it.rank }),
        )
    }

    /**
     * 将「未知牌」展开为列表：墙中尚未出现的张（108 - 已知）。
     */
    private fun buildUnknownPool(state: GameState, random: Random): MutableList<Tile> {
        val rem = state.remainingCounts()
        val list = mutableListOf<Tile>()
        for (i in 0 until 27) {
            val suit = Suit.entries[i / 9]
            val rank = i % 9 + 1
            repeat(rem[i]) { list.add(Tile(suit, rank)) }
        }
        return list
    }
}
