package com.majiang.counter.ml

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.max

/**
 * 从 `assets/ml/{appId}/model_manifest.json` 读取整桌检测模型配置（字段均可选）。
 *
 * @property assetPath 相对 assets 的模型路径，如 `ml/xuezhan_mahjong_default/yolo-tiles-v1.tflite`；未配置时为 null
 * @property inputWidth 模型输入宽（默认 640）
 * @property inputHeight 模型输入高（默认 640）
 */
data class TileDetectorAssetSpec(
    val assetPath: String?,
    val inputWidth: Int,
    val inputHeight: Int,
)

/**
 * 解析 manifest 中与整桌 YOLO TFLite 相关的键。
 *
 * 可选键：`tileDetectorFile`、`tileDetectorInputWidth`、`tileDetectorInputHeight`。
 */
fun readTileDetectorAssetSpec(context: Context, appId: String): TileDetectorAssetSpec {
    val manifestPath = "ml/$appId/model_manifest.json"
    val text = runCatching {
        context.assets.open(manifestPath).use { ins ->
            BufferedReader(InputStreamReader(ins, Charsets.UTF_8)).readText()
        }
    }.getOrNull() ?: return TileDetectorAssetSpec(null, 640, 640)
    val root = runCatching { JSONObject(text) }.getOrNull()
        ?: return TileDetectorAssetSpec(null, 640, 640)
    val name = root.optString("tileDetectorFile", "").trim().takeIf { it.isNotEmpty() }
    val path = name?.let { "ml/$appId/$it" }
    val w = max(32, root.optInt("tileDetectorInputWidth", 640))
    val h = max(32, root.optInt("tileDetectorInputHeight", 640))
    return TileDetectorAssetSpec(path, w, h)
}
