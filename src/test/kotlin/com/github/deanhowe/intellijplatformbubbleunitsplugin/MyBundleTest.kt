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
}
