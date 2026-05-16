package com.majiang.counter.profile

import com.majiang.counter.domain.Seat
import org.json.JSONArray
import org.json.JSONObject

/**
 * 与 `docs/画面字段-GameState-映射表.md` 对齐的可标定区域键（非河牌区域写入 JSON `extras`）。
 */
object VisualRoiKeys {
    const val HAND_BOTTOM = "hand_bottom"
    const val HUD = "hud"
    const val CENTER_LAST_DISCARD = "center_last_discard"
    const val HAND_COUNT_EAST = "hand_count_EAST"
    const val HAND_COUNT_NORTH = "hand_count_NORTH"
    const val HAND_COUNT_WEST = "hand_count_WEST"
    const val DEALER_BADGE = "dealer_badge"

    /** 标定页展示的扩展项顺序（河牌四区仍用 [Seat] 单独编辑）。 */
    fun extraCalibrationOrder(): List<Pair<String, String>> = listOf(
        HAND_BOTTOM to "本家手牌区",
        HUD to "HUD（剩张/局数）",
        CENTER_LAST_DISCARD to "中央最近打出",
        HAND_COUNT_EAST to "东（左）家手数",
        HAND_COUNT_NORTH to "北（上）家手数",
        HAND_COUNT_WEST to "西（右）家手数",
        DEALER_BADGE to "庄标（示意框）",
    )
}

/**
 * 一款皮肤下的完整 ROI 包：四牌河 + 扩展区域（相对预览 0..1）。
 */
data class RoiCalibrationPack(
    val rivers: Map<Seat, NormRect>,
    val extras: Map<String, NormRect>,
) {
    fun rectForExtra(key: String): NormRect? = extras[key]

    companion object {
        fun defaultFromProfile(profile: AppProfile): RoiCalibrationPack {
            val riverList = profile.defaultRiverRects
                ?: AppProfile.xuezhanDefault().defaultRiverRects
                ?: error("血战默认皮肤须配置 defaultRiverRects")
            val rivers = Seat.entries.zip(riverList).toMap()
            return RoiCalibrationPack(rivers, defaultExtrasFallback(profile))
        }

        /**
         * 与血战默认布局一致的扩展 ROI 初值（占位几何，仍以用户标定为准）。
         */
        fun defaultExtrasFallback(profile: AppProfile): Map<String, NormRect> {
            profile.defaultExtraRects?.let { return it }
            val hud = profile.hudRect ?: NormRect(0.38f, 0.38f, 0.62f, 0.52f)
            return mapOf(
                VisualRoiKeys.HAND_BOTTOM to NormRect(0.08f, 0.88f, 0.92f, 0.98f),
                VisualRoiKeys.HUD to hud,
                VisualRoiKeys.CENTER_LAST_DISCARD to NormRect(0.43f, 0.40f, 0.57f, 0.52f),
                VisualRoiKeys.HAND_COUNT_EAST to NormRect(0.02f, 0.18f, 0.12f, 0.32f),
                VisualRoiKeys.HAND_COUNT_NORTH to NormRect(0.40f, 0.02f, 0.60f, 0.12f),
                VisualRoiKeys.HAND_COUNT_WEST to NormRect(0.88f, 0.18f, 0.98f, 0.32f),
                VisualRoiKeys.DEALER_BADGE to NormRect(0.40f, 0.72f, 0.60f, 0.82f),
            )
        }
    }
}

/**
 * ROI 包 JSON：v2 含 `rivers` + `extras`；兼容旧版仅顶层 `EAST`…`NORTH` 四数组。
 */
object RoiCalibrationCodec {
    private const val VERSION = 2
    private const val KEY_VERSION = "version"
    private const val KEY_RIVERS = "rivers"
    private const val KEY_EXTRAS = "extras"

    fun toJson(pack: RoiCalibrationPack): String {
        val root = JSONObject()
        root.put(KEY_VERSION, VERSION)
        val riversObj = JSONObject()
        for (seat in Seat.entries) {
            val r = pack.rivers[seat] ?: continue
            riversObj.put(seat.name, rectToJsonArray(r))
        }
        root.put(KEY_RIVERS, riversObj)
        val extrasObj = JSONObject()
        for ((k, r) in pack.extras) {
            extrasObj.put(k, rectToJsonArray(r))
        }
        root.put(KEY_EXTRAS, extrasObj)
        return root.toString()
    }

    /**
     * 解析存储 JSON；无法识别时退回 [RoiCalibrationPack.defaultFromProfile]。
     */
    fun fromJsonOrLegacy(json: String?, profile: AppProfile): RoiCalibrationPack {
        val fallback = RoiCalibrationPack.defaultFromProfile(profile)
        if (json.isNullOrBlank()) return fallback
        return runCatching {
            val root = JSONObject(json)
            if (root.optInt(KEY_VERSION) == VERSION && root.has(KEY_RIVERS)) {
                val riversObj = root.getJSONObject(KEY_RIVERS)
                val rivers = Seat.entries.associateWith { seat ->
                    if (riversObj.has(seat.name)) {
                        parseRect(riversObj.getJSONArray(seat.name))
                    } else {
                        fallback.rivers[seat] ?: NormRect(0.05f, 0.05f, 0.95f, 0.95f)
                    }
                }
                val extras = if (root.has(KEY_EXTRAS)) {
                    val ex = root.getJSONObject(KEY_EXTRAS)
                    val parsed = ex.keys().asSequence().associateWith { k -> parseRect(ex.getJSONArray(k)) }
                    mergeExtras(fallback.extras, parsed)
                } else {
                    fallback.extras
                }
                RoiCalibrationPack(rivers, extras)
            } else {
                legacyTopLevelSeatArrays(root, profile)
            }
        }.getOrElse { RoiCalibrationPack.defaultFromProfile(profile) }
    }

    private fun mergeExtras(base: Map<String, NormRect>, saved: Map<String, NormRect>): Map<String, NormRect> {
        val out = base.toMutableMap()
        out.putAll(saved)
        return out
    }

    /** 旧版：根对象直接含 EAST、SOUTH、WEST、NORTH 四个 JSONArray。 */
    private fun legacyTopLevelSeatArrays(root: JSONObject, profile: AppProfile): RoiCalibrationPack {
        val fallback = RoiCalibrationPack.defaultFromProfile(profile)
        val rivers = mutableMapOf<Seat, NormRect>()
        for (seat in Seat.entries) {
            if (!root.has(seat.name)) return fallback
            rivers[seat] = parseRect(root.getJSONArray(seat.name))
        }
        return RoiCalibrationPack(rivers, fallback.extras)
    }

    private fun rectToJsonArray(r: NormRect): JSONArray =
        JSONArray(listOf(r.left.toDouble(), r.top.toDouble(), r.right.toDouble(), r.bottom.toDouble()))

    private fun parseRect(a: JSONArray): NormRect =
        NormRect(
            a.getDouble(0).toFloat(),
            a.getDouble(1).toFloat(),
            a.getDouble(2).toFloat(),
            a.getDouble(3).toFloat(),
        ).sanitized()
}
