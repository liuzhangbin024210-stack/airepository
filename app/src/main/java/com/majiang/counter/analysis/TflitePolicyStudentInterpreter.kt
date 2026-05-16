package com.majiang.counter.analysis

import android.content.Context
import com.majiang.counter.profile.AppProfile
import com.majiang.counter.rules.RulesConfig
import com.majiang.counter.rules.sha256FingerprintHex
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
 * 从 `assets/ml/{appId}/model_manifest.json` 加载策略 TFLite（`policyFile`），并与 `appId`、`rulesHash`、`featureSchemaVersion` 绑定校验。
 *
 * **输出张量**（与 [PolicyFeatureV1] 文档一致）：
 * - **27**：仅听牌头，内部 softmax；
 * - **81**：听牌 softmax + RonAny sigmoid + 点杠 sigmoid 三段拼接。
 */
@Singleton
class TflitePolicyStudentInterpreter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appProfile: AppProfile,
    private val rulesConfig: RulesConfig,
) : PolicyStudentInterpreter {

    private val loadLock = Any()

    @Volatile
    private var interpreter: Interpreter? = null

    @Volatile
    private var modelByteBuffer: ByteBuffer? = null

    @Volatile
    private var loadFinished: Boolean = false

    override fun infer(featureVector: FloatArray): StudentPolicyV1? {
        if (featureVector.size != PolicyFeatureV1.FEATURE_DIM) return null
        val interp = interpreterOrNull() ?: return null
        val raw = synchronized(loadLock) {
            runCatching {
                val outTensor = interp.getOutputTensor(0)
                val outElements = outTensor.numElements()
                val inBuf = ByteBuffer.allocateDirect(4 * PolicyFeatureV1.FEATURE_DIM)
                    .order(ByteOrder.nativeOrder())
                inBuf.asFloatBuffer().put(featureVector)
                inBuf.rewind()
                val outBuf = ByteBuffer.allocateDirect(4 * outElements).order(ByteOrder.nativeOrder())
                interp.run(inBuf, outBuf)
                outBuf.rewind()
                FloatArray(outElements) { outBuf.float }
            }.getOrNull()
        } ?: return null

        return when (raw.size) {
            PolicyFeatureV1.POLICY_HEAD_HU -> {
                val hu = softmaxIfNeeded(raw)
                StudentPolicyV1(hu, null, null)
            }
            PolicyFeatureV1.POLICY_OUTPUT_FULL_DIM -> {
                val hu = softmaxIfNeeded(raw.copyOfRange(0, PolicyFeatureV1.POLICY_HEAD_HU))
                val ron = toProbHead(raw, PolicyFeatureV1.POLICY_HEAD_HU, PolicyFeatureV1.POLICY_HEAD_RON)
                val kong = toProbHead(
                    raw,
                    PolicyFeatureV1.POLICY_HEAD_HU + PolicyFeatureV1.POLICY_HEAD_RON,
                    PolicyFeatureV1.POLICY_HEAD_KONG,
                )
                StudentPolicyV1(hu, ron, kong)
            }
            else -> null
        }
    }

    /**
     * Ron/杠头：若整段已在 \[0,1\] 内则视为概率输出，否则按 logits 做 sigmoid。
     */
    private fun toProbHead(raw: FloatArray, offset: Int, len: Int): FloatArray {
        var allUnit = true
        for (i in 0 until len) {
            val v = raw[offset + i]
            if (v < 0f || v > 1f) {
                allUnit = false
                break
            }
        }
        return FloatArray(len) { j ->
            val v = raw[offset + j]
            if (allUnit) v.coerceIn(0f, 1f) else sigmoid(v)
        }
    }

    private fun sigmoid(x: Float): Float =
        (1.0 / (1.0 + exp(-x.toDouble().coerceIn(-30.0, 30.0)))).toFloat()

    private fun softmaxIfNeeded(raw: FloatArray): FloatArray {
        val m = raw.maxOrNull() ?: 0f
        val exps = FloatArray(raw.size) { j ->
            exp((raw[j] - m).toDouble()).toFloat().coerceIn(1e-9f, 1e9f)
        }
        val s = exps.sum().takeIf { it > 1e-9f } ?: 1f
        return FloatArray(raw.size) { j -> exps[j] / s }
    }

    private fun interpreterOrNull(): Interpreter? {
        interpreter?.let { return it }
        synchronized(loadLock) {
            if (loadFinished) return interpreter
            loadFinished = true
            val manifest = readManifestJson() ?: return null
            if (manifest.appId != appProfile.appId) return null
            if (manifest.featureSchemaVersion != PolicyFeatureV1.FEATURE_SCHEMA_VERSION) return null
            val expectedRules = rulesConfig.sha256FingerprintHex()
            if (!manifest.rulesHash.equals(expectedRules, ignoreCase = true)) return null
            val name = manifest.policyFile?.trim().orEmpty()
            if (name.isEmpty()) return null
            val path = "ml/${appProfile.appId}/$name"
            val model = runCatching {
                context.assets.open(path).use { it.readBytes() }
            }.getOrNull() ?: return null
            val buf = ByteBuffer.allocateDirect(model.size).order(ByteOrder.nativeOrder())
            buf.put(model)
            buf.rewind()
            modelByteBuffer = buf
            interpreter = runCatching {
                Interpreter(buf, Interpreter.Options().apply { setNumThreads(2) })
            }.getOrNull()
            val ok = interpreter != null && verifyTensorShapes(interpreter!!)
            if (!ok) {
                interpreter?.close()
                interpreter = null
                modelByteBuffer = null
            }
            return interpreter
        }
    }

    private fun verifyTensorShapes(interp: Interpreter): Boolean {
        val inElements = interp.getInputTensor(0).numElements()
        if (inElements != PolicyFeatureV1.FEATURE_DIM) return false
        val outElements = interp.getOutputTensor(0).numElements()
        return outElements == PolicyFeatureV1.POLICY_HEAD_HU ||
            outElements == PolicyFeatureV1.POLICY_OUTPUT_FULL_DIM
    }

    private data class ManifestFields(
        val appId: String,
        val rulesHash: String,
        val featureSchemaVersion: Int,
        val policyFile: String?,
    )

    private fun readManifestJson(): ManifestFields? {
        val path = "ml/${appProfile.appId}/model_manifest.json"
        val text = runCatching {
            context.assets.open(path).use { ins ->
                BufferedReader(InputStreamReader(ins, Charsets.UTF_8)).readText()
            }
        }.getOrNull() ?: return null
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return null
        return ManifestFields(
            appId = root.optString("appId", "").trim(),
            rulesHash = root.optString("rulesHash", "").trim(),
            featureSchemaVersion = root.optInt("featureSchemaVersion", -1),
            policyFile = if (root.isNull("policyFile")) null else root.optString("policyFile", "").trim().ifEmpty { null },
        )
    }
}
