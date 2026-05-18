package com.majiang.counter.vision.yolo

/**
 * 归一化轴对齐框：相对整帧图像，各边坐标 ∈ \[0, 1\]，与 Android 像素坐标系一致（左上为原点）。
 *
 * @property left 左边界
 * @property top 上边界
 * @property right 右边界（须 ≥ [left]）
 * @property bottom 下边界（须 ≥ [top]）
 */
data class NormalizedBBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    init {
        require(left in 0f..1f && top in 0f..1f && right in 0f..1f && bottom in 0f..1f) {
            "归一化框坐标须在 0..1 内"
        }
        require(right >= left && bottom >= top) { "right≥left 且 bottom≥top" }
    }
}
