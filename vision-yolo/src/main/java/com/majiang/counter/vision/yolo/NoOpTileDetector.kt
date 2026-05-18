package com.majiang.counter.vision.yolo

import android.graphics.Bitmap
import kotlinx.coroutines.delay

/**
 * 占位实现：不加载模型，恒返回空列表；用于在未导出整桌 YOLO TFLite 前保持 DI 与编译通过。
 *
 * 后续由 `TfliteYoloTileDetector`（或等价类）替换，仍须满足 `TileDetector` 契约。
 */
class NoOpTileDetector : TileDetector {
    override fun isModelAvailable(): Boolean = false

    override suspend fun detectTiles(frame: Bitmap): List<TileDetection> {
        // 与 app 内 PlaceholderTileClassifier 类似：极短延迟便于协程调度联调
        delay(1)
        return emptyList()
    }
}
