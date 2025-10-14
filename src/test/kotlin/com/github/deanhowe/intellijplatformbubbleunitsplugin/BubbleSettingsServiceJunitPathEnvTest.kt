package com.github.deanhowe.intellijplatformbubbleunitsplugin

import com.github.deanhowe.intellijplatformbubbleunitsplugin.services.BubbleSettingsService
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class BubbleSettingsServiceJunitPathEnvTest : BasePlatformTestCase() {

    fun testEnvJunitPathPrefersBubbleUnitsKey() {
        val svc = BubbleSettingsService(myFixture.project)
        val map = mapOf(
            "JUNIT_XML_PATH" to "reports/junit.xml",
            "BUBBLE_UNITS_JUNIT_PATH" to "build/test-results/test/TEST-foo.xml"
        )
        val resolved = svc.resolveJunitPathFromEnvMap(map)
        assertEquals("build/test-results/test/TEST-foo.xml", resolved)
    }

    fun testEnvJunitPathFallsBackToAlias() {
        val svc = BubbleSettingsService(myFixture.project)
        val map = mapOf(
            "JUNIT_XML_PATH" to "reports/alt.xml"
        )
        val resolved = svc.resolveJunitPathFromEnvMap(map)
        assertEquals("reports/alt.xml", resolved)
    }

    fun testEnvJunitPathNullWhenMissing() {
        val svc = BubbleSettingsService(myFixture.project)
        val map = emptyMap<String, String>()
        val resolved = svc.resolveJunitPathFromEnvMap(map)
        assertNull(resolved)
    }
}
