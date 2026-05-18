package com.majiang.counter.vision.yolo

import android.graphics.Bitmap

/**
 * 整桌（或整帧 ROI）麻将牌 **检测** 抽象：与 `com.majiang.counter.vision.TileClassifier` 的「单张小图分类」互补。
 *
 * 设计约束（与修订计划 / `tile_schema_v1` 对齐）：
 * - 仅输出 **27** 类数牌索引；字牌 / 花牌不得出现在 `TileDetection.classIndex`。
 * - 具体 TFLite 输入尺寸、letterbox、NMS 由各实现类封装；调用方只传 **ARGB_8888** 或兼容的 `Bitmap`。
 *
 * 实现类可放在本模块；`app` 通过 Hilt 装配具体实现（当前默认 `NoOpTileDetector`）。
 */
interface TileDetector {
    /** 是否已在端上成功加载检测用 TFLite（或其它运行时）。 */
    fun isModelAvailable(): Boolean

    /**
     * 对整帧图像执行检测。
     *
     * @param frame 建议为分析用分辨率下的 RGB 位图；实现内不应长期持有引用以免泄漏。
     * @return 检测结果列表；无检出时返回空列表。
     */
    suspend fun detectTiles(frame: Bitmap): List<TileDetection>
}
