# -*- coding: utf-8 -*-
"""
牌面 27 类标签顺序：须与 Kotlin `Tile.allTypes()` 完全一致。

Kotlin：`Suit.entries` 为 WAN, TONG, TIAO；每花色 1..9。
"""

from __future__ import annotations

# 与 com.majiang.counter.domain.Tile.allTypes() 顺序一致（万 1–9，筒 1–9，条 1–9）
TILE_ORDER_27: list[tuple[str, int]] = []
for suit in ("WAN", "TONG", "TIAO"):
    for rank in range(1, 10):
        TILE_ORDER_27.append((suit, rank))

assert len(TILE_ORDER_27) == 27

NUM_CLASSES = 27
