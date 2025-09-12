package com.github.deanhowe.intellijplatformbubbleunitsplugin

import com.github.deanhowe.intellijplatformbubbleunitsplugin.services.UrlValidator
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class UrlValidatorTest : BasePlatformTestCase() {

    fun testValidSchemes() {
        assertTrue(UrlValidator.isValidCustomUrl("http://example.com"))
        assertTrue(UrlValidator.isValidCustomUrl("https://example.com/path?q=1"))
        assertTrue(UrlValidator.isValidCustomUrl("file:///tmp/index.html"))
        assertTrue(UrlValidator.isValidCustomUrl("data:text/plain;base64,SGk="))
    }

    fun testInvalidSchemes() {
        assertFalse(UrlValidator.isValidCustomUrl("javascript:alert(1)"))
        assertFalse(UrlValidator.isValidCustomUrl("://missing-scheme"))
        assertFalse(UrlValidator.isValidCustomUrl("ht!tp://bad"))
    }

    fun testBlankAllowed() {
        // Blank is allowed -> means use fallback precedence
        assertTrue(UrlValidator.isValidCustomUrl(""))
        assertTrue(UrlValidator.isValidCustomUrl("   "))
    }
}
