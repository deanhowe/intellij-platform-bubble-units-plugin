package com.github.deanhowe.intellijplatformbubbleunitsplugin

import com.github.deanhowe.intellijplatformbubbleunitsplugin.services.EnvParser
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class EnvParserMalformedTest : BasePlatformTestCase() {

    fun testMalformedLinesAreSkippedOrHandled() {
        val lines = listOf(
            "NOEQUALS",
            "=novalue",
            "KEY_ONLY=",
            " # comment after spaces",
            "  KEY_WITH_SPACES = value with spaces  "
        )
        val map = EnvParser.parseEnvLines(lines)
        assertFalse(map.containsKey("NOEQUALS"))
        assertFalse(map.containsKey("")) // '=novalue' should be ignored
        assertTrue(map.containsKey("KEY_ONLY"))
        assertEquals("", map["KEY_ONLY"])
        assertEquals("value with spaces", map["KEY_WITH_SPACES"]) // value trimmed
    }
}
