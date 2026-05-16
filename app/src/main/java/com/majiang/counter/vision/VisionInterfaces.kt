package com.majiang.counter.vision

import androidx.camera.core.ImageProxy
import com.majiang.counter.domain.DiscardEvent
import com.majiang.counter.domain.Tile
import com.majiang.counter.profile.AppProfile
import com.majiang.counter.profile.RoiCalibrationPack

/**
 * 从牌面小图识别牌种（占位 TFLite 可替换实现）。
 */
interface TileClassifier {
    suspend fun classify(crop: ByteArray?): Pair<Tile, Float>

    /** 是否已加载可用牌面模型（占位实现恒为 false）。 */
    fun isModelAvailable(): Boolean
}

/**
 * 单帧视觉管线：输入 [ImageProxy]（建议 RGBA 输出）与当前 ROI 包，可选产出 [DiscardEvent]。
 */
interface TableTracker {
    suspend fun processFrame(
        image: ImageProxy,
        roiPack: RoiCalibrationPack,
        profile: AppProfile,
    ): DiscardEvent?
}
