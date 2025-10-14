package com.github.deanhowe.intellijplatformbubbleunitsplugin

import com.github.deanhowe.intellijplatformbubbleunitsplugin.services.BubbleReportWatcher
import com.github.deanhowe.intellijplatformbubbleunitsplugin.services.BubbleSettingsService
import com.github.deanhowe.intellijplatformbubbleunitsplugin.toolWindow.MyToolWindowFactory
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ToolWindowMessageBusIntegrationTest : BasePlatformTestCase() {

    fun testReloadOnJUnitReportChange() {
        val project = project
        val manager = ToolWindowManager.getInstance(project)

        // Create an isolated tool window for the test to avoid depending on plugin.xml registration
        val tw = manager.registerToolWindow(
            RegisterToolWindowTask(
                id = "BubbleUnitsTest",
                anchor = ToolWindowAnchor.RIGHT,
                canCloseContent = true
            )
        )

        // Populate tool window content using our factory (headless path: no JCEF)
        MyToolWindowFactory().createToolWindowContent(project, tw)

        val settings = BubbleSettingsService.getInstance(project)
        // Ensure baseline
        settings.setLastLoadedUrl(null)

        // Publish change event (debounced ~750ms in implementation)
        project.messageBus.syncPublisher(BubbleReportWatcher.TOPIC).junitReportChanged()

        // Wait up to 5 seconds for lastLoadedUrl to be set by loadUrl() path
        val deadline = System.currentTimeMillis() + 5000
        while (settings.state.lastLoadedUrl.isNullOrBlank() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
        }

        assertNotNull("Expected lastLoadedUrl to be set after report change", settings.state.lastLoadedUrl)
        assertTrue(
            "Expected a data: or http(s) URL to be computed",
            settings.state.lastLoadedUrl!!.startsWith("data:") ||
                settings.state.lastLoadedUrl!!.startsWith("http://") ||
                settings.state.lastLoadedUrl!!.startsWith("https://")
        )
    }
}
