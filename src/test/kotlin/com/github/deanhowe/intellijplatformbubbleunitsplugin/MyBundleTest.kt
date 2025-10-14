package com.github.deanhowe.intellijplatformbubbleunitsplugin

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class MyBundleTest : BasePlatformTestCase() {

    fun testProjectServiceMessage() {
        // Test that the bundle correctly formats messages with parameters
        val message = MyBundle.message("projectService", "TestProject")
        assertEquals("Project service: TestProject", message)
    }

    fun testBubbleToolWindowTitle() {
        // Test that the bundle correctly retrieves the tool window title
        val message = MyBundle.message("bubbleToolWindowTitle")
        assertEquals("BubbleUnits", message)
    }

    fun testBubbleSettingsTitle() {
        // Test that the bundle correctly retrieves the settings title
        val message = MyBundle.message("bubbleSettingsTitle")
        assertEquals("BubbleUnits Settings", message)
    }

    fun testBubbleSettingsUrlLabel() {
        // Test that the bundle correctly retrieves the URL label
        val message = MyBundle.message("bubbleSettingsUrlLabel")
        assertEquals("URL", message)
    }

    fun testMessageFormatting() {
        // Test that the bundle correctly formats messages with multiple parameters
        val message = MyBundle.message("projectService", "CustomProject")
        assertEquals("Project service: CustomProject", message)

        // Verify with a different parameter
        val anotherMessage = MyBundle.message("projectService", "AnotherProject")
        assertEquals("Project service: AnotherProject", anotherMessage)
    }

    fun testBubbleSettingsUrlHelpMessages() {
        val help = MyBundle.message("bubbleSettingsUrlHelp")
        assertEquals(
            "URL precedence: Custom URL > Development panel > .env (BUBBLE_UNITS_URL then APP_URL) > Default bundled bubble.html",
            help
        )
        val linkLabel = MyBundle.message("bubbleSettingsUrlHelpLink")
        assertEquals("Learn about URL precedence", linkLabel)
        val url = MyBundle.message("bubbleSettingsUrlHelpUrl")
        assertEquals("https://github.com/deanhowe/intellij-platform-bubble-units-plugin#development-panel", url)
    }
    fun testBubbleToolWindowLoadErrorKey() {
        val msg = MyBundle.message("bubbleToolWindow.loadError")
        assertEquals("Failed to resolve content.", msg)
    }

    fun testJcefNotSupportedKey() {
        val msg = MyBundle.message("bubbleToolWindow.jcefNotSupported")
        assertTrue(msg.contains("JCEF is not available"))
    }

    fun testToolbarMessagesExist() {
        assertEquals("Reload", MyBundle.message("toolbar.reload"))
        assertTrue(MyBundle.message("toolbar.reload.description").contains("Reload"))
        assertEquals("Open in Browser", MyBundle.message("toolbar.openInBrowser"))
        assertTrue(MyBundle.message("toolbar.openInBrowser.description").contains("Open"))
        // Error message presence
        assertTrue(MyBundle.message("bubbleToolWindow.openInBrowser.error").contains("Failed to open URL"))
    }

    fun testSettingsI18nKeys() {
        assertEquals("Enable development panel (render bubble-test.html or selected)", MyBundle.message("settings.devPanel.enable"))
        assertEquals("Test URL", MyBundle.message("settings.buttons.testUrl"))
        assertEquals("Reset to default", MyBundle.message("settings.buttons.resetToDefault"))
        assertEquals("HTML directory", MyBundle.message("settings.labels.htmlDirectory"))
        assertEquals("HTML file", MyBundle.message("settings.labels.htmlFile"))
    }

    fun testSettingsPlaceholders() {
        assertEquals("<embedded data url>", MyBundle.message("settings.placeholder.embeddedDataUrl"))
        assertTrue(MyBundle.message("settings.placeholder.embeddedDataUrlEmptyText").contains("embedded data url hidden"))
    }

    fun testSettingsValidationMessages() {
        assertTrue(MyBundle.message("settings.validation.invalidUrlDetailed").contains("Invalid URL"))
        assertTrue(MyBundle.message("settings.validation.invalidUrlSimple").contains("Invalid URL"))
        assertTrue(MyBundle.message("settings.validation.enterUrlToTest").contains("Enter a URL"))
    }
}
