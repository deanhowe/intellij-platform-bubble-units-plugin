package com.github.deanhowe.intellijplatformbubbleunitsplugin.toolWindow

import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.jcef.JBCefBrowser
import org.cef.browser.CefBrowser
import org.cef.handler.CefDisplayHandlerAdapter
import org.cef.CefSettings

/**
 * Attaches a console logger to the provided JBCefBrowser so that messages from
 * the embedded Chromium console are forwarded to the IntelliJ log. This helps
 * diagnose issues when content (e.g., XML/HTML) does not load as expected.
 */
fun attachConsoleLogger(browser: JBCefBrowser, log: Logger) {
    val client = browser.jbCefClient
    client.addDisplayHandler(object : CefDisplayHandlerAdapter() {
        override fun onConsoleMessage(
            browser: CefBrowser?,
            level: CefSettings.LogSeverity?,
            message: String?,
            source: String?,
            line: Int
        ): Boolean {
            val lvlEnum = level ?: CefSettings.LogSeverity.LOGSEVERITY_INFO
            val lvl = lvlEnum.toString()
            val safeMsg = run {
                val m = (message ?: "").trim()
                val limit = 2000
                if (m.length > limit) m.substring(0, limit) + "…" else m
            }
            val safeSrc = run {
                val s = source ?: ""
                if (s.startsWith("data:")) "data:(...)" else s
            }
            val text = "[JCEF Console][$lvl] $safeMsg ($safeSrc:$line)"
            when (lvlEnum) {
                CefSettings.LogSeverity.LOGSEVERITY_ERROR,
                CefSettings.LogSeverity.LOGSEVERITY_FATAL -> log.warn(text) // avoid Throwable-backed IDE SEVERE logs
                CefSettings.LogSeverity.LOGSEVERITY_WARNING -> log.warn(text)
                else -> log.info(text) // Log all console messages to help diagnose bridge/export issues
            }
            return false // allow default handling too
        }
    }, (browser.cefBrowser))
}
