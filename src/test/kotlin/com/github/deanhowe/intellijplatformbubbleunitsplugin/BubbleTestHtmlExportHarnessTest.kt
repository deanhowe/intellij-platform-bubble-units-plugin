package com.github.deanhowe.intellijplatformbubbleunitsplugin

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class BubbleTestHtmlExportHarnessTest : BasePlatformTestCase() {

    fun testBubbleTestHtmlContainsExportButtonsAndSvg() {
        val url = javaClass.getResource("/web/bubble-test.html")
        assertNotNull("/web/bubble-test.html should be on the classpath", url)
        val text = url!!.readText()
        // Ensure the dev harness includes the three export buttons with the expected IDs our bridge binds to
        assertTrue(text.contains("id=\"svg_download_link\""))
        assertTrue(text.contains("id=\"json_report_download_link\""))
        assertTrue(text.contains("id=\"png_download_link\""))
        // Ensure there's a #bubbles container and an inline SVG so exports have something to serialize
        assertTrue(text.contains("id=\"bubbles\""))
        assertTrue(text.contains("<svg"))
    }
}
