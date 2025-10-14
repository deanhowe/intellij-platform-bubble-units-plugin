package com.github.deanhowe.intellijplatformbubbleunitsplugin

import com.github.deanhowe.intellijplatformbubbleunitsplugin.services.UrlProvider
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class UrlProviderTest : BasePlatformTestCase() {

    fun testCustomUrlWins() {
        val provider = UrlProvider()
        val result = provider.resolve(
            customUrl = "https://custom.example",
            devPanelEnabled = true,
            envUrlProvider = { "https://env.example" },
            defaultUrlProvider = { "data:default" },
        )
        assertEquals("https://custom.example", result)
    }

    fun testDevPanelWhenEnabled() {
        val provider = UrlProvider()
        val result = provider.resolve(
            customUrl = "  ",
            devPanelEnabled = true,
            envUrlProvider = { "https://env.example" },
            defaultUrlProvider = { "data:default" },
        )
        assertEquals("data:default", result)
    }

    fun testEnvUrlWhenNoCustomAndDevDisabled() {
        val provider = UrlProvider()
        val result = provider.resolve(
            customUrl = null,
            devPanelEnabled = false,
            envUrlProvider = { "https://env.example" },
            defaultUrlProvider = { "data:default" },
        )
        assertEquals("https://env.example", result)
    }

    fun testDefaultWhenNothingElse() {
        val provider = UrlProvider()
        val result = provider.resolve(
            customUrl = null,
            devPanelEnabled = false,
            envUrlProvider = { null },
            defaultUrlProvider = { "data:default" },
        )
        assertEquals("data:default", result)
    }
}
