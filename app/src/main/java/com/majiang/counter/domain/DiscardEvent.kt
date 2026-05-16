package com.majiang.counter.domain

/**
 * 识别或用户确认后的一条弃牌事件。
 *
 * @property confidence 分类器置信度 0..1；手录/真值注入可为 1。
 */
data class DiscardEvent(
    val seat: Seat,
    val tile: Tile,
    val confidence: Float = 1f,
)
