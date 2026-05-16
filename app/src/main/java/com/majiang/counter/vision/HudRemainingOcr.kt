package com.majiang.counter.vision

import android.graphics.Bitmap

/**
 * 从 HUD 小图识别「剩张」数字（实现可为 ML Kit；单测可替换桩）。
 */
fun interface HudRemainingOcr {

    /**
     * @param hudCrop 已按 `extras.hud` 裁好的位图；调用方负责回收。
     * @return 0..108 的剩张数，失败时 null。
     */
    suspend fun recognizeRemainingTiles(hudCrop: Bitmap): Int?
}
