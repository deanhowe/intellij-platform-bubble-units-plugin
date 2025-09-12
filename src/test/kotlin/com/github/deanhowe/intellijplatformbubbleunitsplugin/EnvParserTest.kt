package com.github.deanhowe.intellijplatformbubbleunitsplugin

import com.github.deanhowe.intellijplatformbubbleunitsplugin.services.EnvParser
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class EnvParserTest : BasePlatformTestCase() {

    fun testParseCommentsAndWhitespace() {
        val lines = listOf(
            "   # comment line",
            "",
            " BUBBLE_UNITS_URL = https://example.com  ",
            "  APP_URL= http://fallback.local "
        )
        val map = EnvParser.parseEnvLines(lines)
        assertEquals("https://example.com", map["BUBBLE_UNITS_URL"])
        assertEquals("http://fallback.local", map["APP_URL"])
    }

    fun testParseQuotedAndEscaped() {
        val lines = listOf(
            "BUBBLE_UNITS_URL=\"https://exa\\\"mple.com/path\"",
            "APP_URL='http:\\/\\/fallback.local'"
        )
        val map = EnvParser.parseEnvLines(lines)
        assertEquals("https://exa\"mple.com/path", map["BUBBLE_UNITS_URL"])
        assertEquals("http:\\/\\/fallback.local", map["APP_URL"]) // backslashes preserved except \\" and \\' cases
    }
}
