package com.majiang.counter.vision

import androidx.camera.core.ImageProxy
import com.majiang.counter.domain.DiscardEvent
import com.majiang.counter.domain.Seat
import com.majiang.counter.domain.Tile
import com.majiang.counter.profile.AppProfile
import com.majiang.counter.profile.RoiCalibrationPack

/**
 * 门禁：**置信度阈值** + **连续稳定帧**（同一座位、同一识别牌）后才向下游返回事件，避免低质量分类结果污染 [com.majiang.counter.domain.GameState]。
 * 阈值取自 [AppProfile]（皮肤包元数据），便于首款标定后写入配置。
 */
class GatedTableTracker(
    private val inner: TableTracker,
) : TableTracker {

    private var stableKey: Pair<Seat, Tile>? = null
    private var stableCount: Int = 0

    override suspend fun processFrame(
        image: ImageProxy,
        roiPack: RoiCalibrationPack,
        profile: AppProfile,
    ): DiscardEvent? {
        val minConfidence = profile.visionMinClassifierConfidence
        val stableFramesRequired = profile.visionStableFramesRequired
        val raw = inner.processFrame(image, roiPack, profile)
        if (raw == null) {
            resetStable()
            return null
        }
        if (raw.confidence < minConfidence) {
            resetStable()
            return null
        }
        val key = raw.seat to raw.tile
        if (stableKey == key) {
            stableCount++
        } else {
            stableKey = key
            stableCount = 1
        }
        if (stableCount < stableFramesRequired) {
            return null
        }
        resetStable()
        return raw
    }

    private fun resetStable() {
        stableKey = null
        stableCount = 0
    }
}
