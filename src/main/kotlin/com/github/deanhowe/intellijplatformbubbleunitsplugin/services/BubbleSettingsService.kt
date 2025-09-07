package com.github.deanhowe.intellijplatformbubbleunitsplugin.services

import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.util.xmlb.XmlSerializerUtil
import java.awt.Color
import java.io.File
import java.util.Base64
import java.nio.file.Paths
import java.nio.charset.StandardCharsets.UTF_8

@Service(Service.Level.PROJECT)
@State(
    name = "com.github.deanhowe.intellijplatformbubbleunitsplugin.services.BubbleSettingsService",
    storages = [Storage("BubbleSettingsService.xml")]
)
class BubbleSettingsService(private val project: Project) : PersistentStateComponent<BubbleSettingsService.State> {


    private val DEFAULT_URL: String
        get() {

            val BGColour: Color = JBColor.namedColor("Panel.background", JBColor.PanelBackground)

            val projectName = project.name + " BubbleUnits"
            val projectPath = project.basePath ?: ""
            val junitFile = File(Paths.get(projectPath, "junit-report.xml").toString())
            val junitPath = Paths.get(projectPath, "junit-report.xml").toString()

            val junitXml = runCatching { File(junitPath).readText(UTF_8) }.getOrDefault("")
            //val dataUrl = "data:text/xml;charset=utf-8;base64:" +
            val dataUrl = Base64.getEncoder().encodeToString(junitXml.toByteArray(UTF_8))

            val junitHtmlFile = loadBubbleHtmlWithReplacements(mapOf(
                "BGColour" to BGColour.toString(),
                "BUBBLE_UNITS_PROJECT_NAME" to projectName,
                "BUBBLE_UNITS_INTRO" to "",
                "JUNIT_XML_BASE64" to dataUrl,
                "JUNIT_PATH" to junitPath,
                "PROJECT_BASE_PATH" to projectPath
            )
            )



//                // Use a data URL instead of loading from JAR to avoid sandboxing issues
//                val htmlContent = """
//                    <!DOCTYPE html>
//                    <html>
//                    <head>
//                        <meta http-equiv="refresh" content="0;url=https://local.test/bubbles">
//                        <title>Redirecting to Bubble Units</title>
//                    </head>
//                    <body>
//                        <p>Redirecting to Bubble Units...</p>
//                    </body>
//                    </html>
//                """.trimIndent()

                    // Use a data URL instead of loading from JAR to avoid sandboxing issues
                    //val htmlContent = junitFile.readText(Charsets.UTF_8)
                    val htmlContent = junitHtmlFile
//            if (junitFile.exists()) {
//            }


            // Encode the HTML content as a base64 data URL without compression
            val encodedHtml = java.util.Base64.getEncoder().encodeToString(htmlContent.toByteArray(Charsets.UTF_8))
            return "data:text/html;charset=utf-8;base64,$encodedHtml"
        }
    private var customUrl: String? = null

    data class State(
        var customUrl: String? = null
    )

    private val state = State()

    var url: String
        get() {
            // First priority: User-configured URL from settings
            if (!customUrl.isNullOrBlank()) {
                return customUrl!!
            }

            // Second priority: URL from .env file
            val envUrl = getUrlFromEnvFile()
            if (envUrl != null) {
                return envUrl
            }

            // Third priority: Default URL
            return DEFAULT_URL
        }
        set(value) {
            customUrl = value
            state.customUrl = value
        }

    private fun getUrlFromEnvFile(): String? {
        try {
            val projectPath = project.basePath ?: return null
            val envFile = File(Paths.get(projectPath, ".env").toString())

            if (!envFile.exists()) {
                return null
            }

            var appUrl: String? = null

            // First pass: look for BUBBLE_UNITS_URL
            envFile.readLines().forEach { line ->
                val trimmedLine = line.trim()
                if (trimmedLine.startsWith("BUBBLE_UNITS_URL=")) {
                    val url = trimmedLine.substring("BUBBLE_UNITS_URL=".length).trim()
                    // Remove quotes if present
                    return url.removeSurrounding("\"").removeSurrounding("'")
                }
                else if (trimmedLine.startsWith("APP_URL=")) {
                    val url = trimmedLine.substring("APP_URL=".length).trim()
                    // Store APP_URL for later if BUBBLE_UNITS_URL is not found
                    appUrl = url.removeSurrounding("\"").removeSurrounding("'")
                }
            }

            // Return APP_URL if found and BUBBLE_UNITS_URL was not found
            if (appUrl != null) {
                return appUrl
            }

            return null
        } catch (e: Exception) {
            thisLogger().warn("Error reading .env file: ${e.message}")
            return null
        }
    }

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.customUrl = state.customUrl
        this.state.customUrl = state.customUrl
    }

    /**
     * Loads bubble.html as text and applies simple {{PLACEHOLDER}} replacements.
     *
     * Order of lookup:
     * 1) <projectRoot>/bubble.html (useful during development or if users keep a copy in the project)
     * 2) /bubble.html from plugin resources (place under src/main/resources)
     *
     * If neither is found, it returns a tiny fallback HTML so the UI doesn't break.
     */
    private fun loadBubbleHtmlWithReplacements(replacements: Map<String, String>): String {
        val fromProjectFile = project.basePath
            ?.let { base -> Paths.get(base, "bubble.html").toFile() }
            ?.takeIf { it.isFile && it.canRead() }

        val rawHtml: String? = try {
            when {
                // 1) Try project-local file
                fromProjectFile != null -> {
                    // Note: this is simple blocking I/O. If you call this on the EDT, consider caching the result.
                    fromProjectFile.readText(Charsets.UTF_8)
                }
                else -> {
                    // 2) Try bundled resource (ensure bubble.html is placed under src/main/resources)
                    val resourcePath = "/bubble.html"
                    javaClass.getResourceAsStream(resourcePath)?.use { input ->
                        input.reader(Charsets.UTF_8).readText()
                    }
                }
            }
        } catch (e: Exception) {
            thisLogger().warn("Failed to load bubble.html", e)
            null
        }

        val html = rawHtml ?: """
            <!doctype html>
            <meta charset="utf-8">
            <title>Bubble Units</title>
            <body>
              <p>Could not load bubble.html</p>
            </body>
        """.trimIndent()

        // Apply very simple placeholder replacements, e.g. {{JUNIT_PATH}}
        // This is a straightforward String replace; for complex templating use a proper engine.
        return replacements.entries.fold(html) { acc, (key, value) ->
            acc.replace("{{$key}}", value)
        }
    }



    companion object {
        @JvmStatic
        fun getInstance(project: Project): BubbleSettingsService {
            return project.service<BubbleSettingsService>()
        }
    }
}
