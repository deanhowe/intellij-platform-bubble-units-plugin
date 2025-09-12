package com.github.deanhowe.intellijplatformbubbleunitsplugin

import com.github.deanhowe.intellijplatformbubbleunitsplugin.services.BubbleSettingsService
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class BubbleSettingsServiceEnvResolutionTest : BasePlatformTestCase() {

    fun testBubbleUnitsUrlWinsOverAppUrl() {
        val svc = BubbleSettingsService.getInstance(project)
        val map = mapOf(
            "APP_URL" to "http://app.local",
            "BUBBLE_UNITS_URL" to "https://units.example"
        )
        assertEquals("https://units.example", svc.resolveUrlFromEnvMap(map))
    }

    fun testAppUrlUsedWhenBubbleUnitsMissing() {
        val svc = BubbleSettingsService.getInstance(project)
        val map = mapOf(
            "APP_URL" to "http://app.local"
        )
        assertEquals("http://app.local", svc.resolveUrlFromEnvMap(map))
    }

    fun testNullWhenNoRelevantKeysOrBlank() {
        val svc = BubbleSettingsService.getInstance(project)
        val map1 = emptyMap<String, String>()
        assertNull(svc.resolveUrlFromEnvMap(map1))

        val map2 = mapOf(
            "BUBBLE_UNITS_URL" to "   ",
            "APP_URL" to "  "
        )
        assertNull(svc.resolveUrlFromEnvMap(map2))
    }
}
