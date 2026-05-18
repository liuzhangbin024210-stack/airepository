package com.majiang.counter.vision

import com.majiang.counter.domain.Tile
import com.majiang.counter.vision.yolo.TileDetection

/** 与 `Tile.allTypes()` 顺序一致，避免每次分配列表。 */
private val TILE_ORDER_27: List<Tile> = Tile.allTypes()

/**
 * 将整桌检测输出的类别索引映射为领域 [Tile]。
 *
 * 须与训练、`tile_schema_v1`、`TfliteTileClassifier` 的 27 类顺序一致。
 */
fun TileDetection.toDomainTile(): Tile = TILE_ORDER_27[classIndex]
