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


    private var cachedDefaultUrl: String? = null
    private var cachedSignature: Int = 0

    private val DEFAULT_URL: String
        get() {
            val BGColour: Color = JBColor.namedColor("Panel.background", JBColor.PanelBackground)
            val textColour: Color = JBColor.namedColor("Label.foreground", JBColor.foreground())

            val projectName = project.name + " BubbleUnits"
            val projectPath = project.basePath ?: ""
            val junitPath = Paths.get(projectPath, "junit-report.xml").toString()

            val junitXml = runCatching { File(junitPath).readText(UTF_8) }.getOrDefault("")
            val dataUrl = Base64.getEncoder().encodeToString(junitXml.toByteArray(UTF_8))

            val errorColor: Color = JBColor.namedColor("Label.errorForeground", JBColor.RED)
            val warningColor: Color = JBColor.namedColor("Label.warningForeground", Color(0xFFC107)) // amber fallback
            val successColor: Color = JBColor.namedColor("Label.successForeground", Color(0x2E7D32))
            val infoColor: Color = JBColor.namedColor("Label.infoForeground", Color(0x2196F3))

            // Build a lightweight signature to detect changes without re-encoding every time
            val signature = arrayOf(
                projectName,
                projectPath,
                junitXml.hashCode().toString(),
                toCssColor(BGColour),
                toCssColor(textColour),
                toCssColor(errorColor),
                toCssColor(warningColor),
                toCssColor(successColor),
                toCssColor(infoColor),
                resolveHtmlToLoad(),
                state.htmlDirectory ?: ""
            ).contentHashCode()

            if (cachedDefaultUrl != null && cachedSignature == signature) {
                return cachedDefaultUrl!!
            }

            val junitHtmlFile = loadBubbleHtmlWithReplacements(mapOf(
                "BGColour" to toCssColor(BGColour),
                "textColour" to toCssColor(textColour),
                "errorColour" to toCssColor(errorColor),
                "warningColour" to toCssColor(warningColor),
                "successColour" to toCssColor(successColor),
                "infoColour" to toCssColor(infoColor),
                "BUBBLE_UNITS_PROJECT_NAME" to projectName,
                "BUBBLE_UNITS_INTRO" to "",
                "JUNIT_XML_BASE64" to dataUrl,
                "JUNIT_PATH" to junitPath,
                "PROJECT_BASE_PATH" to projectPath
            ))

            val encodedHtml = java.util.Base64.getEncoder().encodeToString(junitHtmlFile.toByteArray(Charsets.UTF_8))
            val result = "data:text/html;charset=utf-8;base64,$encodedHtml"
            cachedDefaultUrl = result
            cachedSignature = signature
            return result
        }
    private var customUrl: String? = null

    data class State(
        var customUrl: String? = null,
        var devPanelEnabled: Boolean = false,
        var htmlDirectory: String? = null,
        var selectedHtmlFile: String? = null
    )

    private val state = State()

    var url: String
        get() {
            // 1) Explicit custom URL from settings always wins if non-blank
            if (!customUrl.isNullOrBlank()) {
                return customUrl!!
            }

            // 2) If development panel is enabled, force rendering of the selected/bundled HTML
            if (state.devPanelEnabled) {
                return DEFAULT_URL
            }

            // 3) Otherwise, try .env URL
            val envUrl = getUrlFromEnvFile()
            if (envUrl != null) {
                return envUrl
            }

            // 4) Fallback to default
            return DEFAULT_URL
        }
        set(value) {
            val normalized = value?.trim().orEmpty()
            // Treat blank as null to allow dev panel/.env to take effect
            customUrl = if (normalized.isEmpty()) null else normalized
            state.customUrl = customUrl
            invalidateCache()
        }

    fun resolveUrlFromEnvMap(map: Map<String, String>): String? {
        val bubble = map["BUBBLE_UNITS_URL"]?.trim().orEmpty()
        if (bubble.isNotEmpty()) return bubble
        val app = map["APP_URL"]?.trim().orEmpty()
        if (app.isNotEmpty()) return app
        return null
    }

    private fun getUrlFromEnvFile(): String? {
        return try {
            val projectPath = project.basePath ?: return null
            val envFile = File(Paths.get(projectPath, ".env").toString())
            if (!envFile.exists()) return null

            val map = EnvParser.parseEnvLines(envFile.readLines())
            resolveUrlFromEnvMap(map)
        } catch (e: Exception) {
            thisLogger().warn("Error reading .env file: ${e.message}")
            null
        }
    }

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.customUrl = state.customUrl
        XmlSerializerUtil.copyBean(state, this.state)
        invalidateCache()
    }

    private fun invalidateCache() {
        cachedDefaultUrl = null
        cachedSignature = 0
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
    private fun toCssColor(c: Color): String = String.format("#%02x%02x%02x", c.red, c.green, c.blue)

    private fun loadBubbleHtmlWithReplacements(replacements: Map<String, String>): String {
        val bubble = resolveHtmlToLoad()
        val fromSpecifiedDir = state.htmlDirectory
            ?.let { dir -> Paths.get(dir, bubble).toFile() }
            ?.takeIf { it.isFile && it.canRead() }

        val fromProjectFile = project.basePath
            ?.let { base -> Paths.get(base, bubble).toFile() }
            ?.takeIf { it.isFile && it.canRead() }

        val rawHtml: String? = try {
            when {
                // 1) Try specified directory
                fromSpecifiedDir != null -> {
                    fromSpecifiedDir.readText(Charsets.UTF_8)
                }
                // 2) Try project-local file
                fromProjectFile != null -> {
                    fromProjectFile.readText(Charsets.UTF_8)
                }
                else -> {
                    // 3) Try bundled resource (ensure bubble.html is placed under src/main/resources)
                    val resourcePath = "/$bubble"
                    javaClass.getResourceAsStream(resourcePath)?.use { input ->
                        input.reader(Charsets.UTF_8).readText()
                    }
                }
            }
        } catch (e: Exception) {
            thisLogger().warn("Failed to load $bubble", e)
            null
        }

        val html = rawHtml ?: """
            <!doctype html>
            <meta charset="utf-8">
            <title>Bubble Units</title>
            <body>
              <p>Could not load $bubble</p>
            </body>
        """.trimIndent()

        // Apply very simple placeholder replacements, e.g. {{JUNIT_PATH}}
        // This is a straightforward String replace; for complex templating use a proper engine.
        return replacements.entries.fold(html) { acc, (key, value) ->
            acc.replace("{{$key}}", value)
        }
    }

    private fun resolveHtmlToLoad(): String {
        // If development panel is enabled, prefer selected file or bubble-test.html
        if (state.devPanelEnabled) {
            state.selectedHtmlFile?.let { sel ->
                return sel
            }
            return "bubble-test.html"
        }
        // Default production file
        return "bubble.html"
    }



    companion object {
        interface SettingsListener { fun bubbleSettingsChanged() }
        val SETTINGS_CHANGED = com.intellij.util.messages.Topic.create(
            "BubbleUnits Settings changed",
            SettingsListener::class.java
        )
        @JvmStatic
        fun getInstance(project: Project): BubbleSettingsService {
            return project.service<BubbleSettingsService>()
        }
    }
}
