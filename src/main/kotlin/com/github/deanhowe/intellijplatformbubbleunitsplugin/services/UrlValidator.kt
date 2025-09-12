package com.github.deanhowe.intellijplatformbubbleunitsplugin.services

import java.net.URI

object UrlValidator {
    private val allowedSchemes = setOf("http", "https", "file", "data")

    fun isValidCustomUrl(input: String): Boolean {
        val url = input.trim()
        if (url.isEmpty()) return true // blank is allowed (means use precedence fallbacks)
        if (url.startsWith("javascript:", ignoreCase = true)) return false
        // Allow simple data: and file: without strict parsing
        if (url.startsWith("data:") || url.startsWith("file:")) return true
        return try {
            val uri = URI(url)
            val scheme = uri.scheme?.lowercase()
            scheme != null && allowedSchemes.contains(scheme)
        } catch (_: Exception) {
            false
        }
    }
}
