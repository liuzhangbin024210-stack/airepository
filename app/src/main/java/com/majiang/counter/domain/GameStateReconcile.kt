package com.majiang.counter.domain

import kotlin.math.abs

/**
 * 与画面 HUD「剩 NN 张」对账用的纯函数（可单测）。
 *
 * 守恒式（108 张血战）：牌墙剩余 = 108 − 四家手牌张数之和 − 河牌总张数 − 明示副露牌张数。
 * 本家手牌用 [GameState.myHand]；他家用 [GameState.opponentHandCount]（仅统计 ``seat != mySeat``）。
 * 若 HUD 或任一家对手手数未录入，返回 null，表示**跳过**硬对账（与映射表 v1 约定一致）。
 */
fun GameState.expectedWallRemainingOrNull(): Int? {
    val oppSeats = Seat.entries.filter { it != mySeat }
    if (oppSeats.any { opponentHandCount[it] == null }) return null
    val concealed =
        myHand.size + oppSeats.sumOf { opponentHandCount[it]!! }
    val river = discards.values.sumOf { it.size }
    val meld = openMelds.values.flatten().sumOf { it.tiles.size }
    return (108 - concealed - river - meld).coerceAtLeast(0)
}

/**
 * 判断当前 [GameState] 是否与 HUD 剩张在允许误差内一致。
 *
 * @param toleranceTiles 允许差的张数（动画/识别抖动）。
 * @return 二者均齐全且 ``|expected - hud| <= tolerance`` 时为 true；缺字段时 true（不判失败）。
 */
fun GameState.hudMatchesReconciledWall(hud: Int?, toleranceTiles: Int): Boolean {
    if (hud == null) return true
    val expected = expectedWallRemainingOrNull() ?: return true
    return abs(expected - hud) <= toleranceTiles
}
