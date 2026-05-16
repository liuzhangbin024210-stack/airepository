package com.majiang.counter.ui.util

import com.majiang.counter.domain.Suit
import com.majiang.counter.domain.Tile

/** 简短牌面文字，用于列表。 */
fun tileShortLabel(t: Tile): String {
    val suit = when (t.suit) {
        Suit.WAN -> "万"
        Suit.TONG -> "筒"
        Suit.TIAO -> "条"
    }
    return "${t.rank}$suit"
}
