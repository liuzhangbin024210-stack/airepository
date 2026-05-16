package com.majiang.counter.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HudRemainingTextParserTest {

    @Test
    fun parse_afterSheng_withSpaces() {
        assertEquals(55, HudRemainingTextParser.parseRemainingTiles("血战麻将 剩 55 张"))
    }

    @Test
    fun parse_afterSheng_compact() {
        assertEquals(55, HudRemainingTextParser.parseRemainingTiles("剩55张"))
    }

    @Test
    fun parse_zhangSuffix() {
        assertEquals(12, HudRemainingTextParser.parseRemainingTiles("第 1/4 局 12张"))
    }

    @Test
    fun parse_rejectsOutOfRange() {
        assertNull(HudRemainingTextParser.parseRemainingTiles("剩 500 张"))
    }

    @Test
    fun parse_multiline() {
        val t = "标题\n剩 44 张\n其它"
        assertEquals(44, HudRemainingTextParser.parseRemainingTiles(t))
    }
}
