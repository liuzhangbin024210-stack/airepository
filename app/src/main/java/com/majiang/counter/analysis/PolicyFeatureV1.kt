package com.majiang.counter.analysis

import com.majiang.counter.domain.GamePhase
import com.majiang.counter.domain.GameState
import com.majiang.counter.domain.Seat
import com.majiang.counter.domain.Suit
import com.majiang.counter.domain.Tile

/**
 * v1 策略蒸馏用离散特征向量（与 `model_manifest.json` 的 `featureSchemaVersion == 1` 绑定）。
 *
 * **策略输出（与 Python `policy_schema` 一致）**：
 * - **27 维**：仅听牌/进张头，对 `Tile.allTypes()` 做 softmax；
 * - **81 维**：`[0,27)` 听牌 softmax；`[27,54)` RonAny 按牌型索引 sigmoid（或已为概率则钳制）；`[54,81)` 点杠 sigmoid。
 *
 * 布局（共 [FEATURE_DIM] 维 float32，顺序固定，训练脚本须一致）：
 * - [0,27)：墙余各门牌张数 /4（与 [GameState.remainingCounts] 顺序一致）；
 * - [27,54)：本家手牌直方图 /4；
 * - [54,66)：四家定缺各 3 维 one-hot（座位顺序 [Seat] 枚举，花色顺序 [Suit]）；未定缺为全 0；
 * - [66,69)：[GamePhase] 三态 one-hot；
 * - [69,73)：`mySeat` 四向 one-hot；
 * - [73,77)：`wonSeats` 各座是否已胡（0/1）；
 * - [77,81)：`dealerSeat` 四向 one-hot，无庄家则全 0。
 */
object PolicyFeatureV1 {
    const val FEATURE_SCHEMA_VERSION: Int = 1
    const val FEATURE_DIM: Int = 81

    /** 听牌头输出维度。 */
    const val POLICY_HEAD_HU: Int = 27

    /** 打出侧：RonAny + 点杠，各 27 维（与牌型索引对齐）。 */
    const val POLICY_HEAD_RON: Int = 27
    const val POLICY_HEAD_KONG: Int = 27

    /** 全头输出：听牌 + 打出 Ron + 打出杠。 */
    const val POLICY_OUTPUT_FULL_DIM: Int =
        POLICY_HEAD_HU + POLICY_HEAD_RON + POLICY_HEAD_KONG

    private fun Tile.index27(): Int = suit.ordinal * 9 + (rank - 1)

    /**
     * 由当前公开状态构造特征向量；不修改 [state]。
     */
    fun build(state: GameState): FloatArray {
        val out = FloatArray(FEATURE_DIM)
        val rem = state.remainingCounts()
        for (i in 0 until 27) {
            out[i] = (rem[i].coerceIn(0, 4)) / 4f
        }
        val handHist = IntArray(27)
        for (t in state.myHand) {
            handHist[t.index27()]++
        }
        for (i in 0 until 27) {
            out[27 + i] = (handHist[i].coerceIn(0, 4)) / 4f
        }
        var o = 54
        for (seat in Seat.entries) {
            val q = state.dingQue[seat]
            when (q) {
                null -> {
                    o += 3
                }
                else -> {
                    out[o + q.ordinal] = 1f
                    o += 3
                }
            }
        }
        when (state.phase) {
            GamePhase.PLAYING -> out[66] = 1f
            GamePhase.EXCHANGE_THREE -> out[67] = 1f
            GamePhase.CLAIM_WINDOW -> out[68] = 1f
        }
        out[69 + state.mySeat.ordinal] = 1f
        for (seat in Seat.entries) {
            out[73 + seat.ordinal] = if (seat in state.wonSeats) 1f else 0f
        }
        val d = state.dealerSeat
        if (d != null) {
            out[77 + d.ordinal] = 1f
        }
        return out
    }

    /**
     * 将 27 维模型输出按听牌集合裁剪并归一化为概率（仅用于 UI 展示；非法则返回 null）。
     */
    fun studentHuOnWaitingTiles(full27: FloatArray, waiting: Set<Tile>): Map<Tile, Float>? {
        if (full27.size != 27) return null
        if (waiting.isEmpty()) return null
        if (full27.any { it.isNaN() || it.isInfinite() }) return null
        val m = full27.maxOrNull() ?: return null
        if (m <= 0f) return null
        val raw = waiting.associateWith { t -> full27[t.index27()].coerceAtLeast(0f) }
        val s = raw.values.sum()
        if (s <= 1e-9f) return null
        return raw.mapValues { (_, v) -> v / s }
    }

    /**
     * 将打出侧 27 维 Ron / 杠头映射到当前合法打出候选（按牌型取索引）。
     * 非法（NaN、长度不对）时返回 null。
     */
    fun studentRonKongOnCandidates(
        ron27: FloatArray,
        kong27: FloatArray,
        candidates: Set<Tile>,
    ): Pair<Map<Tile, Float>, Map<Tile, Float>>? {
        if (ron27.size != POLICY_HEAD_RON || kong27.size != POLICY_HEAD_KONG) return null
        if (ron27.any { it.isNaN() || it.isInfinite() }) return null
        if (kong27.any { it.isNaN() || it.isInfinite() }) return null
        if (candidates.isEmpty()) {
            return Pair(emptyMap<Tile, Float>(), emptyMap<Tile, Float>())
        }
        val ron = candidates.associateWith { t -> ron27[t.index27()].coerceIn(0f, 1f) }
        val kong = candidates.associateWith { t -> kong27[t.index27()].coerceIn(0f, 1f) }
        return ron to kong
    }
}
