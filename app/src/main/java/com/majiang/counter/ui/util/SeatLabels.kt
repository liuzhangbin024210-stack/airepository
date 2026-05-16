package com.majiang.counter.ui.util

import com.majiang.counter.domain.Seat

fun seatLabel(seat: Seat): String = when (seat) {
    Seat.EAST -> "东"
    Seat.SOUTH -> "南（本家默认）"
    Seat.WEST -> "西"
    Seat.NORTH -> "北"
}
