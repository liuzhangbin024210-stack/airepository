package com.majiang.counter.profile

import com.google.common.truth.Truth.assertThat
import com.majiang.counter.domain.Seat
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test

class RoiCalibrationCodecTest {

    private val profile = AppProfile.xuezhanDefault()

    @Test
    fun roundTrip_v2_preserves_rivers_and_extras() {
        val original = RoiCalibrationPack.defaultFromProfile(profile)
        val json = RoiCalibrationCodec.toJson(original)
        val back = RoiCalibrationCodec.fromJsonOrLegacy(json, profile)
        assertThat(back.rivers).isEqualTo(original.rivers)
        assertThat(back.extras).isEqualTo(original.extras)
    }

    @Test
    fun legacy_topLevel_seat_arrays_migrate_to_rivers_and_keep_default_extras() {
        val fallback = RoiCalibrationPack.defaultFromProfile(profile)
        val legacy = """
            {"EAST":[0.01,0.02,0.03,0.04],"SOUTH":[0.1,0.2,0.3,0.4],"WEST":[0.5,0.5,0.6,0.6],"NORTH":[0.7,0.7,0.8,0.8]}
        """.trimIndent()
        val pack = RoiCalibrationCodec.fromJsonOrLegacy(legacy, profile)
        assertThat(pack.rivers[Seat.EAST]).isEqualTo(NormRect(0.01f, 0.02f, 0.03f, 0.04f).sanitized())
        assertThat(pack.extras).isEqualTo(fallback.extras)
    }

    @Test
    fun v2_partial_extras_merge_with_fallback_keys() {
        val base = RoiCalibrationPack.defaultFromProfile(profile)
        val root = JSONObject()
        root.put("version", 2)
        val rivers = JSONObject()
        for (seat in Seat.entries) {
            val rr = base.rivers.getValue(seat)
            rivers.put(
                seat.name,
                JSONArray(listOf(rr.left.toDouble(), rr.top.toDouble(), rr.right.toDouble(), rr.bottom.toDouble())),
            )
        }
        root.put("rivers", rivers)
        val extras = JSONObject()
        extras.put(VisualRoiKeys.HUD, JSONArray(listOf(0.4, 0.4, 0.5, 0.5)))
        root.put("extras", extras)
        val parsed = RoiCalibrationCodec.fromJsonOrLegacy(root.toString(), profile)
        assertThat(parsed.extras[VisualRoiKeys.HUD]).isEqualTo(NormRect(0.4f, 0.4f, 0.5f, 0.5f).sanitized())
        assertThat(parsed.extras[VisualRoiKeys.HAND_BOTTOM]).isEqualTo(base.extras[VisualRoiKeys.HAND_BOTTOM])
    }
}
