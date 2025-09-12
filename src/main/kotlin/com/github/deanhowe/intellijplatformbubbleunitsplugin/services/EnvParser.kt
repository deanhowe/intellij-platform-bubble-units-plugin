package com.github.deanhowe.intellijplatformbubbleunitsplugin.services

/**
 * Minimal .env parser supporting:
 * - Comments starting with '#'
 * - Blank lines
 * - Whitespace around keys and values
 * - Single or double quoted values with support for simple escaping (\\' and \" and \\ for backslash)
 */
object EnvParser {
    fun parseEnvLines(lines: List<String>): Map<String, String> {
        val map = linkedMapOf<String, String>()
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#")) continue
            val eq = line.indexOf('=')
            if (eq <= 0) continue
            val key = line.substring(0, eq).trim()
            var value = line.substring(eq + 1).trim()
            if (value.isEmpty()) {
                map[key] = ""
                continue
            }
            // Handle quotes
            if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
                value = value.substring(1, value.length - 1)
                value = value
                    .replace("\\\"", "\"")
                    .replace("\\'", "'")
                    .replace("\\\\", "\\")
            }
            map[key] = value
        }
        return map
    }
}
