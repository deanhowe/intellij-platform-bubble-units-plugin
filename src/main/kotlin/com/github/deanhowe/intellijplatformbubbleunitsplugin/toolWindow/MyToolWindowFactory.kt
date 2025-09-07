package com.github.deanhowe.intellijplatformbubbleunitsplugin.toolWindow

import com.github.deanhowe.intellijplatformbubbleunitsplugin.services.BubbleSettingsService
import com.github.deanhowe.intellijplatformbubbleunitsplugin.services.BubbleReportWatcher
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManager
import com.intellij.ui.jcef.JBCefBrowser
import com.github.deanhowe.intellijplatformbubbleunitsplugin.toolWindow.attachConsoleLogger
import java.awt.BorderLayout
import javax.swing.JPanel

class MyToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val myToolWindow = MyToolWindow(project, toolWindow)
        val contentManager = toolWindow.contentManager
        val content = contentManager.factory.createContent(myToolWindow.getContent(), null, false)
        contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true

    class MyToolWindow(private val project: Project, private val toolWindow: ToolWindow) {

        private val settings = BubbleSettingsService.getInstance(project)
        private var browser: JBCefBrowser? = null
        private val panel = JPanel(BorderLayout())

        init {
            // Initialize the browser
            createBrowser()

            // Add a listener to detect when the tool window is shown or hidden
            project.messageBus.connect(project).subscribe(
                ToolWindowManagerListener.TOPIC,
                object : ToolWindowManagerListener {
                    override fun stateChanged() {
                        if (toolWindow.isVisible) {
                            // Tool window is shown, reload the browser
                            if (browser == null) {
                                createBrowser()
                            } else {
                                loadUrl()
                            }
                        } else {
                            // Tool window is hidden, dispose the browser
                            disposeBrowser()
                        }
                    }
                }
            )

            // Subscribe to junit-report.xml changes and reload the panel when it changes
            project.messageBus.connect(project).subscribe(
                BubbleReportWatcher.TOPIC,
                object : BubbleReportWatcher.Listener {
                    override fun junitReportChanged() {
                        // Rebuild URL (it depends on the XML content) and reload
                        loadUrl()
                    }
                }
            )

            // Ensure the watcher service is initialized
            BubbleReportWatcher.getInstance(project)
        }

        fun getContent(): JPanel {
            return panel
        }

        private fun createBrowser() {
            browser = JBCefBrowser()
            // Attach JCEF console logger to help debug loading issues
            val log = Logger.getInstance(MyToolWindowFactory::class.java)
            browser?.let { attachConsoleLogger(it, log) }

            panel.removeAll()
            panel.add(browser!!.component, BorderLayout.CENTER)
            panel.revalidate()
            panel.repaint()
            loadUrl()
        }

        private fun disposeBrowser() {
            browser?.let {
                panel.remove(it.component)
                it.dispose()
                browser = null
                panel.revalidate()
                panel.repaint()
            }
        }

        private fun loadUrl() {
            browser?.loadURL(settings.url)
        }
    }
}
