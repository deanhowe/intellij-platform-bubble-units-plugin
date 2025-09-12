package com.github.deanhowe.intellijplatformbubbleunitsplugin.toolWindow

import com.github.deanhowe.intellijplatformbubbleunitsplugin.services.BubbleSettingsService
import com.github.deanhowe.intellijplatformbubbleunitsplugin.services.BubbleReportWatcher
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
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

            // Removed deprecated ToolWindowManagerListener.stateChanged usage.
            // Browser is created once and remains; rely on BubbleReportWatcher to trigger reloads as needed.

            // Subscribe to settings changes and junit-report.xml changes and reload the panel when it changes
            val connection = project.messageBus.connect(project)
            connection.subscribe(
                BubbleSettingsService.SETTINGS_CHANGED,
                object : BubbleSettingsService.Companion.SettingsListener {
                    override fun bubbleSettingsChanged() {
                        loadUrl()
                    }
                }
            )
            connection.subscribe(
                BubbleReportWatcher.TOPIC,
                object : BubbleReportWatcher.Listener {
                    private var pending = false
                    private val debounceMs = 750L
                    private val scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
                    override fun junitReportChanged() {
                        // Debounce rapid sequences of file changes to avoid janky reloads
                        synchronized(this) {
                            if (pending) return
                            pending = true
                        }
                        scheduler.schedule({
                            try {
                                loadUrl()
                            } finally {
                                synchronized(this) { pending = false }
                            }
                        }, debounceMs, java.util.concurrent.TimeUnit.MILLISECONDS)
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
