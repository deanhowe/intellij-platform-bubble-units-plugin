package com.github.deanhowe.intellijplatformbubbleunitsplugin

import com.github.deanhowe.intellijplatformbubbleunitsplugin.services.BubbleSettingsService
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class BubbleSettingsServiceEnvResolutionTest : BasePlatformTestCase() {

    fun testEnvResolutionPrefersBubbleUnitsUrl() {
        val svc = BubbleSettingsService(myFixture.project)
        val map = mapOf(
            "APP_URL" to "https://app.example",
            "BUBBLE_UNITS_URL" to "https://bubble.example"
        )
        val resolved = svc.resolveUrlFromEnvMap(map)
        assertEquals("https://bubble.example", resolved)
    }

    fun testEnvResolutionFallsBackToAppUrl() {
        val svc = BubbleSettingsService(myFixture.project)
        val map = mapOf(
            "APP_URL" to "http://fallback.local"
        )
        val resolved = svc.resolveUrlFromEnvMap(map)
        assertEquals("http://fallback.local", resolved)
    }

    fun testEnvResolutionNullWhenNoKeys() {
        val svc = BubbleSettingsService(myFixture.project)
        val map = emptyMap<String, String>()
        val resolved = svc.resolveUrlFromEnvMap(map)
        assertNull(resolved)
    }
}
