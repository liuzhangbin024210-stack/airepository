package com.majiang.counter.domain

/**
 * 对局阶段：影响视觉差分是否写入河牌等（换三张期间禁止误计河牌）。
 */
enum class GamePhase {
    /** 正常摸打 */
    PLAYING,

    /** 换三张选牌阶段 */
    EXCHANGE_THREE,

    /** 碰/杠/胡响应窗口（中央提示牌与河去重逻辑见映射表） */
    CLAIM_WINDOW,
}
