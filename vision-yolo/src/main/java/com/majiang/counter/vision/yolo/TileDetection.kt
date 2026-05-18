package com.majiang.counter.vision.yolo

/**
 * 单张牌的检测结果，与领域层 `Tile.allTypes()` 下标一致（0=万1 … 26=条9）。
 *
 * @property classIndex 0..26，须与训练 / manifest 约定一致
 * @property confidence 置信度 ∈ \[0,1\]（由上层再做 softmax / sigmoid 归一化约定）
 * @property bboxNorm 相对整帧的归一化框
 */
data class TileDetection(
    val classIndex: Int,
    val confidence: Float,
    val bboxNorm: NormalizedBBox,
) {
    init {
        require(classIndex in 0..26) { "classIndex 须在 0..26（四川 108 张 27 门）" }
        require(confidence in 0f..1f) { "confidence 须在 0..1" }
    }
}
