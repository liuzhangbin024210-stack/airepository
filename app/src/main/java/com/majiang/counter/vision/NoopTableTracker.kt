package com.majiang.counter.vision

import androidx.camera.core.ImageProxy
import com.majiang.counter.domain.DiscardEvent
import com.majiang.counter.profile.AppProfile
import com.majiang.counter.profile.RoiCalibrationPack

/**
 * 占位跟踪器：不解析帧，不产生弃牌事件（单测或关闭视觉时可替换注入）。
 */
class NoopTableTracker : TableTracker {
    override suspend fun processFrame(
        image: ImageProxy,
        roiPack: RoiCalibrationPack,
        profile: AppProfile,
    ): DiscardEvent? = null
}
