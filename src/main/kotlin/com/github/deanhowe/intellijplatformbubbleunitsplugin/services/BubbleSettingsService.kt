package com.github.deanhowe.intellijplatformbubbleunitsplugin.services

import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.ui.JBColor
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.xmlb.XmlSerializerUtil
import java.awt.Color
import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Paths
import java.util.Base64
import java.util.concurrent.atomic.AtomicReference
import com.github.deanhowe.intellijplatformbubbleunitsplugin.util.Logging
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.TimeUnit

/**
 * BubbleSettingsService
 *
 * Responsibilities:
 * - Persist user settings (custom URL, dev tools options, last loaded URL, snapshot directory).
 * - Provide a non-blocking, cached default data URL that renders the bundled web UI (bubble.html).
 * - Use VFS to discover and read JUnit XML with size limits; embed it Base64-encoded in the data URL.
 * - Invalidate the cached URL when inputs change:
 *   - IDE Look & Feel (theme) updates via LafManagerListener.
 *   - JUnit report file changes via BubbleReportWatcher (VFS listener).
 * - Expose a fast getter that returns the last computed value immediately and schedules background refresh.
 *
 * Threading:
 * - Heavy work (file I/O, Base64, large string building) is performed off the EDT using ReadAction.nonBlocking
 *   and AppExecutorUtil. Results are published on the UI thread; callers should not block the EDT.
 * - Tests can call refreshBlockingForTest() to perform a synchronous recompute off the EDT and await the result.
 *
 * Privacy and logging:
 * - Avoids logging file contents/paths at info/warn/error; uses minimal messages.
 */
@Service(Service.Level.PROJECT)
@State(
    name = "com.github.deanhowe.intellijplatformbubbleunitsplugin.services.BubbleSettingsService",
    storages = [Storage("BubbleSettingsService.xml")]
)
class BubbleSettingsService(private val project: Project) : PersistentStateComponent<BubbleSettingsService.State> {

    @Volatile
    private var lastComputationOnEdt: Boolean = true

    // Async cached computation of default data URL
    private data class Computed(val signature: Int, val url: String)
    private val computedRef = AtomicReference<Computed?>(null)

    // Debounced refresh to avoid thrashing on rapid changes
    private val refreshAlarm = com.intellij.util.Alarm(com.intellij.util.Alarm.ThreadToUse.POOLED_THREAD, project)

    init {
        // Listen for theme changes and refresh in background
        ApplicationManager.getApplication().messageBus.connect(project).subscribe(
            LafManagerListener.TOPIC,
            LafManagerListener {
                invalidateCache()
                scheduleRefresh()
            }
        )
        // Listen for junit file changes via watcher service
        val connection = project.messageBus.connect(project)
        connection.subscribe(
            BubbleReportWatcher.TOPIC,
            object : BubbleReportWatcher.Listener {
                override fun junitReportChanged() {
                    invalidateCache()
                    scheduleRefresh()
                }
            }
        )
    }

    private var customUrl: String? = null

    data class State(
        var customUrl: String? = null,
        var devPanelEnabled: Boolean = false,
        var htmlDirectory: String? = null,
        var selectedHtmlFile: String? = null,
        var lastLoadedUrl: String? = null,
        var snapshotDirectory: String? = null
    )

    private val state = State()

    private val urlProvider = UrlProvider()

    /**
     * Resolve an absolute path to the JUnit XML file from the project's .env, if provided.
     * Supported keys (checked in order):
     * - BUBBLE_UNITS_JUNIT_PATH
     * - JUNIT_XML_PATH (alias)
     * Relative paths are resolved against the project base path.
     */
    fun resolveJunitPathFromEnv(): String? {
        val projectPath = project.basePath ?: return null
        val envPath = try {
            val envFile = File(Paths.get(projectPath, ".env").toString())
            if (!envFile.exists()) return null
            val map = EnvParser.parseEnvLines(envFile.readLines())
            resolveJunitPathFromEnvMap(map)
        } catch (e: Exception) {
            thisLogger().warn("Error reading .env for junit path: ${e.message}")
            null
        }
        if (envPath.isNullOrBlank()) return null
        val file = File(envPath).let { f -> if (f.isAbsolute) f else Paths.get(projectPath, envPath).toFile() }
        return try { file.canonicalPath } catch (_: Exception) { file.absolutePath }
    }

    fun resolveJunitPathFromEnvMap(map: Map<String, String>): String? {
        val keys = listOf("BUBBLE_UNITS_JUNIT_PATH", "JUNIT_XML_PATH")
        for (k in keys) {
            val v = map[k]?.trim().orEmpty()
            if (v.isNotEmpty()) return v
        }
        return null
    }

    var url: String
        get() {
            // Non-blocking: return cached value (or a small placeholder) and trigger background refresh
            return urlProvider.resolve(
                customUrl = customUrl,
                devPanelEnabled = state.devPanelEnabled,
                envUrlProvider = { getUrlFromEnvFile() },
                defaultUrlProvider = { getCachedDefaultUrlOrTrigger() }
            )
        }
        set(value) {
            val normalized = value.trim()
            // Treat blank as null to allow dev panel/.env to take effect
            customUrl = normalized.ifEmpty { null }
            state.customUrl = customUrl
            invalidateCache()
            scheduleRefresh()
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

    fun invalidateCache() {
        computedRef.set(null)
    }

    private fun scheduleRefresh(delayMs: Long = 300) {
        refreshAlarm.cancelAllRequests()
        refreshAlarm.addRequest({ refreshUrlAsync() }, delayMs)
    }

    private fun getCachedDefaultUrlOrTrigger(): String {
        scheduleRefresh()
        return computedRef.get()?.url ?: fallbackDataUrl()
    }

    private fun fallbackDataUrl(): String {
        val html = """
            <!doctype html><meta charset='utf-8'>
            <title>Bubble Units</title>
            <body><p>Loading Bubble Units…</p></body>
        """.trimIndent()
        val enc = Base64.getEncoder().encodeToString(html.toByteArray(UTF_8))
        return "data:text/html;charset=utf-8;base64,$enc"
    }

    private fun refreshUrlAsync() {
        val startNs = System.nanoTime()
        ReadAction.nonBlocking<Computed> {
            if (ApplicationManager.getApplication().isDispatchThread) {
                throw IllegalStateException("URL computation must not run on EDT")
            }
            computeDefaultUrl()
        }
            .expireWith(project)
            .coalesceBy(this)
            .finishOnUiThread(com.intellij.openapi.application.ModalityState.any()) { computed ->
                val prev = computedRef.get()
                if (prev == null || prev.signature != computed.signature || prev.url != computed.url) {
                    computedRef.set(computed)
                    val ms = (System.nanoTime() - startNs) / 1_000_000
                    Logging.debug(project, BubbleSettingsService::class.java, "computed default URL in ${'$'}ms ms")
                    project.messageBus.syncPublisher(SETTINGS_CHANGED).bubbleSettingsChanged()
                }
            }
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    private fun computeDefaultUrl(): Computed {
        val bg: Color = JBColor.namedColor("Panel.background", JBColor.PanelBackground)
        val fg: Color = JBColor.namedColor("Label.foreground", JBColor.foreground())
        val alert: Color = JBColor.namedColor("Label.alertForeground", JBColor.RED)
        val err: Color = JBColor.namedColor("Label.errorForeground", JBColor.RED)
        val warn: Color = JBColor.namedColor("Label.warningForeground", Color(0xFFC107))
        val info: Color = JBColor.namedColor("Label.infoForeground", Color(0x2196F3))
        val success: Color = JBColor.namedColor("Label.successForeground", Color(0x2E7D32))
        val debugc: Color = JBColor.namedColor("Label.disabledForeground", JBColor.GRAY)

        val projectName = project.name + " BubbleUnits"
        val projectPath = project.basePath ?: ""

        val junitVf = findBestJunitVirtualFile()
        val junitPath = junitVf?.path ?: ""
        val junitXml = try { if (junitVf != null) readJunitXmlLimited(junitVf) else "" } catch (_: Exception) { "" }
        val junitXmlB64 = Base64.getEncoder().encodeToString(junitXml.toByteArray(UTF_8))

        val replacements = mapOf(
            "BGColour" to toCssColor(bg),
            "textColour" to toCssColor(fg),
            "errorColour" to toCssColor(err),
            "failedColour" to toCssColor(err),
            "warningColour" to toCssColor(warn),
            "successColour" to toCssColor(success),
            "infoColour" to toCssColor(info),
            "psr3Emergency" to toCssColor(err),
            "psr3Alert" to toCssColor(alert),
            "psr3Critical" to toCssColor(err),
            "psr3Error" to toCssColor(err),
            "psr3Warning" to toCssColor(warn),
            "psr3Notice" to toCssColor(info),
            "psr3Info" to toCssColor(info),
            "psr3Debug" to toCssColor(debugc),
            "BUBBLE_UNITS_PROJECT_NAME" to projectName,
            "BUBBLE_UNITS_INTRO" to "",
            "JUNIT_XML_BASE64" to junitXmlB64,
            "JUNIT_PATH" to junitPath,
            "PROJECT_BASE_PATH" to projectPath
        )
        val html = loadBubbleHtmlWithReplacements(replacements)
        val encodedHtml = Base64.getEncoder().encodeToString(html.toByteArray(UTF_8))
        val url = "data:text/html;charset=utf-8;base64,$encodedHtml"

        val signature = arrayOf(
            projectName,
            projectPath,
            junitPath,
            junitXml.hashCode().toString(),
            toCssColor(bg),
            toCssColor(fg),
            toCssColor(err),
            toCssColor(warn),
            toCssColor(success),
            toCssColor(info),
            toCssColor(alert),
            toCssColor(debugc),
            resolveHtmlToLoad(),
            state.htmlDirectory ?: ""
        ).contentHashCode()
        return Computed(signature, url)
    }

    // Unused function
    // @TestOnly
    // fun getCachedDefaultUrlForTest(): String? = computedRef.get()?.url

    @TestOnly
    fun wasComputedOnEdtForTest(): Boolean = lastComputationOnEdt

    @TestOnly
    fun refreshBlockingForTest(timeoutMs: Long = 5000): String? {
        val future = AppExecutorUtil.getAppExecutorService().submit<String?> {
            lastComputationOnEdt = ApplicationManager.getApplication().isDispatchThread
            val computed = computeDefaultUrl()
            computedRef.set(computed)
            computed.url
        }
        return try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: Exception) {
            null
        }
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

    private fun findBestJunitVirtualFile(): VirtualFile? {
        val envAbs = resolveJunitPathFromEnv()
        if (!envAbs.isNullOrBlank()) {
            try {
                val url = VfsUtilCore.pathToUrl(envAbs)
                VirtualFileManager.getInstance().findFileByUrl(url)?.let { vf ->
                    if (!vf.isDirectory) return vf
                }
            } catch (_: Exception) { /* ignore */ }
        }
        val index = ProjectFileIndex.getInstance(project)
        var best: VirtualFile? = null
        index.iterateContent { vf ->
            if (vf.isDirectory) return@iterateContent true
            val n = vf.name
            val isMatch = (n.startsWith("TEST-") && n.endsWith(".xml", true)) ||
                n.equals("junit.xml", true) ||
                n.equals("junit-report.xml", true) ||
                n.equals("report.junit.xml", true) ||
                n.equals("junit.report.xml", true) ||
                n.equals("TESTS-TestSuites.xml", true) ||
                n.equals("TEST-results.xml", true)
            if (isMatch) {
                if (best == null || vf.timeStamp > (best?.timeStamp ?: 0L)) {
                    best = vf
                }
            }
            true
        }
        return best
    }

    /** Read junit XML content via VFS with a conservative size limit to avoid huge data URLs. */
    private fun readJunitXmlLimited(vf: VirtualFile): String {
        return try {
            val max = 512 * 1024 // 512 KiB limit
            vf.inputStream.use { input ->
                val buf = ByteArray(max)
                var readTotal = 0
                while (readTotal < max) {
                    val r = input.read(buf, readTotal, max - readTotal)
                    if (r <= 0) break
                    readTotal += r
                }
                val data = if (readTotal < buf.size) buf.copyOf(readTotal) else buf
                String(data, UTF_8)
            }
        } catch (e: Exception) {
            thisLogger().warn("Failed reading junit XML: ${'$'}{e.message}")
            ""
        }
    }

    private fun loadBubbleHtmlWithReplacements(replacements: Map<String, String>): String {
        val bubble = resolveHtmlToLoad()
        val fromSpecifiedDir = state.htmlDirectory
            ?.let { dir -> Paths.get(dir, bubble).toFile() }
            ?.takeIf { it.isFile && it.canRead() }

        val fromProjectFile = project.basePath
            ?.let { base -> Paths.get(base, bubble).toFile() }
            ?.takeIf { it.isFile && it.canRead() }

        val loadedFromLocal = fromSpecifiedDir != null || fromProjectFile != null

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
                    val resourcePath = "/web/$bubble"
                    javaClass.getResourceAsStream(resourcePath)?.use { input ->
                        input.reader(Charsets.UTF_8).readText()
                    }
                }
            }
        } catch (e: Exception) {
            thisLogger().warn("Failed to load $bubble", e)
            null
        }

        val baseHtml = rawHtml ?: """
            <!doctype html>
            <meta charset="utf-8">
            <title>Bubble Units</title>
            <body>
              <p>Could not load $bubble</p>
            </body>
        """.trimIndent()

        // Security hardening: strip <script> tags when loading arbitrary local HTML
        val html = if (loadedFromLocal) sanitizeLocalHtml(baseHtml) else baseHtml

        // Apply safe placeholder replacements with an explicit matcher.
        // Matches tokens like {{KEY}} or {{ KEY }} composed of [A-Za-z0-9_].
        // Unknown tokens (including Blade-like {{ $name }}) are preserved as-is.
        val tokenPattern = Regex("""[{][{]\s*([A-Za-z0-9_]+)\s*[}][}]""")

        // Log missing keys present in the template but absent in provided replacements
        val tokensInTemplate = tokenPattern.findAll(html).map { it.groupValues[1] }.toSet()
        val missing = tokensInTemplate.filter { it !in replacements.keys }
        if (missing.isNotEmpty()) {
            thisLogger().warn("Template contains placeholders without values: ${missing.joinToString(", ")}")
        }
        val fallbacks = defaultPlaceholderFallbacks()

        return tokenPattern.replace(html) { m ->
            val name = m.groupValues[1]
            replacements[name] ?: fallbacks[name] ?: m.value
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

    /**
     * Remove inline <script> tags from locally loaded HTML to reduce risk when
     * rendering arbitrary project files. Bundled resources are trusted and not sanitized.
     */
    private fun sanitizeLocalHtml(html: String): String {
        // Strip only inline <script> tags; keep external scripts with src= so the page can function.
        // This provides a balance between safety and usability when developers load local bubble.html.
        val inlineScriptPattern = Regex("(?is)<script(?![^>]*\\bsrc=)[^>]*>.*?</script>")
        val count = inlineScriptPattern.findAll(html).count()
        if (count > 0) {
            thisLogger().warn("Stripped $count inline <script> tag(s) from local HTML; external scripts preserved")
        }
        return inlineScriptPattern.replace(html, "")
    }

    /** Default safe fallbacks for known placeholders. */
    private fun defaultPlaceholderFallbacks(): Map<String, String> = mapOf(
        // Core theme colors
        "BGColour" to "#ffffff",
        "textColour" to "#000000",
        "errorColour" to "#ff5252",
        "warningColour" to "#ffc107",
        "failedColour" to "#ef5350",
        "successColour" to "#2e7d32",
        "infoColour" to "#2196f3",
        // PSR-3 levels
        "psr3Emergency" to "#ff5252",
        "psr3Alert" to "#ff5252",
        "psr3Critical" to "#ff5252",
        "psr3Error" to "#ff5252",
        "psr3Warning" to "#ffc107",
        "psr3Notice" to "#2196f3",
        "psr3Info" to "#2196f3",
        "psr3Debug" to "#9e9e9e",
        // Meta
        "BUBBLE_UNITS_PROJECT_NAME" to "Unknown project",
        "BUBBLE_UNITS_INTRO" to "",
        "JUNIT_XML_BASE64" to "",
        "JUNIT_PATH" to "",
        "PROJECT_BASE_PATH" to ""
    )


    fun setLastLoadedUrl(url: String?) {
        state.lastLoadedUrl = url?.trim()?.ifEmpty { null }
    }

    fun getOrCreateSnapshotDir(): File {
            // Prefer project base for project-scoped snapshots; otherwise fall back to a safe user home location.
            val defaultBase = project.basePath ?: System.getProperty("user.home", ".")
            val defaultPath = Paths.get(defaultBase, ".bubble-unit-snapshots").toString()
            val configured = state.snapshotDirectory?.trim().orEmpty()
            val path = configured.ifEmpty { defaultPath }
            val dir = File(path)
            if (!dir.exists()) {
                try { dir.mkdirs() } catch (_: Exception) {}
            }
            return dir
        }

        // Unused function
    // fun setSnapshotDirectory(path: String?) {
    //     state.snapshotDirectory = path?.trim()?.ifEmpty { null }
    // }

        fun getSnapshotDirectoryPath(): String {
            return getOrCreateSnapshotDir().absolutePath
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
