package com.majiang.counter.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.majiang.counter.domain.Suit
import com.majiang.counter.domain.Tile
import com.majiang.counter.profile.AppProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.exp

/**
 * 牌面小图分类：从 assets 加载可选牌面 TFLite；无模型或推理失败时返回 **低置信度** 占位结果，
 * 与修订计划中「占位识别不通过门禁」一致，避免误写 [com.majiang.counter.domain.GameState]。
 *
 * **输入管线**：传入 **PNG 或 JPEG 字节**（如手牌条为 PNG 以减轻压缩伪影）；此处解码后按模型张量形状缩放并归一化到 \[0,1\]。
 *
 * **模型契约**（训练脚本须与 `Tile.allTypes()` 的 27 门牌顺序一致）：
 * - 输入：float32，`[1,H,W,3]`（NHWC）或 `[1,3,H,W]`（NCHW），由张量维自动判断。
 * - 输出：float32，连续 27 个 float（如 `[1,27]` 或 `[27]`）；可为 logits（内部 softmax）或概率。
 *
 * 资源：`assets/ml/{appId}/{tileClassifierFile}`，文件名来自同目录 `model_manifest.json` 的 `tileClassifierFile` 字段。
 */
@Singleton
class TfliteTileClassifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appProfile: AppProfile,
) : TileClassifier {

    private val tileOrder: List<Tile> = Tile.allTypes()
    private val loadLock = Any()

    @Volatile
    private var interpreter: Interpreter? = null

    /** 须与 [interpreter] 同生命周期，避免 DirectByteBuffer 被回收后 native 侧悬空。 */
    @Volatile
    private var modelByteBuffer: ByteBuffer? = null

    @Volatile
    private var loadAttempted: Boolean = false

    @Volatile
    private var skipInterpreter: Boolean = false

    override fun isModelAvailable(): Boolean = interpreterOrNull() != null

    override suspend fun classify(crop: ByteArray?): Pair<Tile, Float> {
        val jpeg = crop ?: return fallback()
        val interp = interpreterOrNull() ?: return fallback()
        return synchronized(loadLock) {
            runCatching {
                val bmp = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
                    ?: return@synchronized fallback()
                try {
                    val inShape = interp.getInputTensor(0).shape()
                    val outShape = interp.getOutputTensor(0).shape()
                    val outLen = outShape.fold(1) { a, b -> a * b }
                    require(outLen == 27) { "输出长度须为 27，实为 $outLen" }

                    val inputBuffer = buildInputBuffer(bmp, inShape)
                    val outBuf = ByteBuffer.allocateDirect(4 * 27).order(ByteOrder.nativeOrder())
                    interp.run(inputBuffer, outBuf)
                    outBuf.rewind()
                    val raw = FloatArray(27) { outBuf.float }
                    val probs = softmax(raw)
                    val bestIdx = probs.indices.maxByOrNull { probs[it] } ?: 0
                    tileOrder[bestIdx] to probs[bestIdx].coerceIn(0f, 1f)
                } finally {
                    bmp.recycle()
                }
            }.getOrElse { fallback() }
        }
    }

    private fun interpreterOrNull(): Interpreter? {
        if (skipInterpreter) return null
        interpreter?.let { return it }
        synchronized(loadLock) {
            if (loadAttempted) return interpreter
            loadAttempted = true
            val path = readTileModelAssetPath() ?: run {
                skipInterpreter = true
                return null
            }
            val model = runCatching {
                context.assets.open(path).use { it.readBytes() }
            }.getOrNull() ?: run {
                skipInterpreter = true
                return null
            }
            val buf = ByteBuffer.allocateDirect(model.size).order(ByteOrder.nativeOrder())
            buf.put(model)
            buf.rewind()
            modelByteBuffer = buf
            interpreter = runCatching {
                Interpreter(buf, Interpreter.Options().apply { setNumThreads(2) })
            }.getOrNull()
            if (interpreter == null) {
                skipInterpreter = true
            }
            return interpreter
        }
    }

    /**
     * 从 `assets/ml/{appId}/model_manifest.json` 读取 `tileClassifierFile`。
     */
    private fun readTileModelAssetPath(): String? {
        val base = "ml/${appProfile.appId}"
        val manifestPath = "$base/model_manifest.json"
        val text = runCatching {
            context.assets.open(manifestPath).use { ins ->
                BufferedReader(InputStreamReader(ins, Charsets.UTF_8)).readText()
            }
        }.getOrNull() ?: return null
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return null
        if (!root.has("tileClassifierFile") || root.isNull("tileClassifierFile")) {
            return null
        }
        val name = root.optString("tileClassifierFile", "").trim()
        if (name.isEmpty()) return null
        return "$base/$name"
    }

    private fun buildInputBuffer(bitmap: Bitmap, shape: IntArray): ByteBuffer {
        require(shape.size == 4) { "仅支持 4 维输入，形状=${shape.contentToString()}" }
        require(shape[0] == 1) { "batch 须为 1" }
        val nchw = shape[1] == 3 && shape[2] >= 8 && shape[3] >= 8
        val buffer = if (nchw) {
            fillBufferNchw(bitmap, shape[2], shape[3])
        } else {
            require(shape[3] == 3) { "NHWC 末维须为 3" }
            fillBufferNhwc(bitmap, shape[1], shape[2])
        }
        buffer.rewind()
        return buffer
    }

    private fun fillBufferNhwc(src: Bitmap, h: Int, w: Int): ByteBuffer {
        val scaled = Bitmap.createScaledBitmap(src, w, h, true)
        try {
            val buf = ByteBuffer.allocateDirect(4 * h * w * 3).order(ByteOrder.nativeOrder())
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val p = scaled.getPixel(x, y)
                    buf.putFloat(((p shr 16) and 0xff) / 255f)
                    buf.putFloat(((p shr 8) and 0xff) / 255f)
                    buf.putFloat((p and 0xff) / 255f)
                }
            }
            return buf
        } finally {
            if (scaled != src) scaled.recycle()
        }
    }

    /** NCHW：按平面 R、G、B 连续写入（与常见 Keras 导出一致）。 */
    private fun fillBufferNchw(src: Bitmap, h: Int, w: Int): ByteBuffer {
        val scaled = Bitmap.createScaledBitmap(src, w, h, true)
        try {
            val buf = ByteBuffer.allocateDirect(4 * 3 * h * w).order(ByteOrder.nativeOrder())
            val fb = buf.asFloatBuffer()
            val r = FloatArray(h * w)
            val g = FloatArray(h * w)
            val bPlane = FloatArray(h * w)
            var i = 0
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val p = scaled.getPixel(x, y)
                    r[i] = ((p shr 16) and 0xff) / 255f
                    g[i] = ((p shr 8) and 0xff) / 255f
                    bPlane[i] = (p and 0xff) / 255f
                    i++
                }
            }
            fb.put(r)
            fb.put(g)
            fb.put(bPlane)
            return buf
        } finally {
            if (scaled != src) scaled.recycle()
        }
    }

    private fun softmax(logits: FloatArray): FloatArray {
        val m = logits.maxOrNull() ?: 0f
        val exps = FloatArray(logits.size) { j ->
            exp((logits[j] - m).toDouble()).toFloat().coerceIn(1e-9f, 1e9f)
        }
        val s = exps.sum().takeIf { it > 1e-9f } ?: 1f
        return FloatArray(logits.size) { j -> exps[j] / s }
    }

    private fun fallback(): Pair<Tile, Float> = Tile(Suit.TONG, 1) to 0.08f
}
