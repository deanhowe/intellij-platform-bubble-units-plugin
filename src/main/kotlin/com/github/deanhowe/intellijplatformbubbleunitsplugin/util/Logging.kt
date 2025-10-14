package com.github.deanhowe.intellijplatformbubbleunitsplugin.util

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project

/**
 * Simple logging helpers to standardize message formats and include project context.
 * Avoid building heavy strings in hot paths; prefer passing plain messages.
 */
object Logging {
    private fun prefix(project: Project?): String {
        if (project == null) return "[BubbleUnits]"
        val name = project.name
        val base = project.basePath ?: ""
        return "[BubbleUnits][project='$name'][basePath='$base']"
    }

    fun info(project: Project?, category: Class<*>, message: String) {
        Logger.getInstance(category).info("${'$'}{prefix(project)} ${'$'}message")
    }

    fun warn(project: Project?, category: Class<*>, message: String, t: Throwable? = null) {
        val logger = Logger.getInstance(category)
        if (t != null) logger.warn("${'$'}{prefix(project)} ${'$'}message", t) else logger.warn("${'$'}{prefix(project)} ${'$'}message")
    }

    fun error(project: Project?, category: Class<*>, message: String, t: Throwable? = null) {
        val logger = Logger.getInstance(category)
        if (t != null) logger.error("${'$'}{prefix(project)} ${'$'}message", t) else logger.error("${'$'}{prefix(project)} ${'$'}message")
    }

    fun debug(project: Project?, category: Class<*>, message: String) {
        Logger.getInstance(category).debug("${'$'}{prefix(project)} ${'$'}message")
    }
}
