package com.majiang.counter.vision

import com.majiang.counter.domain.Tile
import com.majiang.counter.domain.Suit
import kotlinx.coroutines.delay
import javax.inject.Inject

/**
 * 占位分类器：固定返回「一筒」低置信度，供管线联调。
 */
class PlaceholderTileClassifier @Inject constructor() : TileClassifier {
    override fun isModelAvailable(): Boolean = false

    override suspend fun classify(crop: ByteArray?): Pair<Tile, Float> {
        delay(1)
        return Tile(Suit.TONG, 1) to 0.2f
    }
}
