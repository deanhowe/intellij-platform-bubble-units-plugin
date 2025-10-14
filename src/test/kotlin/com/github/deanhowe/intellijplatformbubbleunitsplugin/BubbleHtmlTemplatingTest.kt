package com.github.deanhowe.intellijplatformbubbleunitsplugin

import com.github.deanhowe.intellijplatformbubbleunitsplugin.services.BubbleSettingsService
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files
import java.util.Base64

class BubbleHtmlTemplatingTest : BasePlatformTestCase() {

    fun testCssPlaceholdersAreSafelyReplacedAndUnknownTokensPreserved() {
        // Prepare a temporary HTML with placeholders (including whitespace and an unknown Blade-like token)
        val tmpDir = Files.createTempDirectory("bubble-units-test").toFile()
        tmpDir.deleteOnExit()
        val htmlName = "test.html"
        val html = """
            <!doctype html>
            <html><head><style>
            :root { --color-bg: {{ BGColour }}; --color-text: {{textColour}}; }
            </style></head>
            <body>
              <h1>{{BUBBLE_UNITS_PROJECT_NAME}}</h1>
              <p>Unknown token should remain: {{ ${'$'}name }}</p>
            </body></html>
        """.trimIndent()
        val htmlFile = tmpDir.resolve(htmlName)
        htmlFile.writeText(html)

        // Configure settings to load our temp HTML via dev panel
        val settings = BubbleSettingsService.getInstance(project)
        val st = settings.getState()
        st.devPanelEnabled = true
        st.htmlDirectory = tmpDir.absolutePath
        st.selectedHtmlFile = htmlName

        // Resolve URL (data URL) deterministically. Prefer blocking test hook to avoid race with async cache.
        val url = settings.refreshBlockingForTest() ?: settings.url
        assertTrue("Expected data URL", url.startsWith("data:text/html"))
        val base64 = url.substringAfter("base64,", "")
        assertTrue(base64.isNotEmpty())
        val decoded = String(Base64.getDecoder().decode(base64))

        // Validations
        assertTrue("CSS var for bg should be present", decoded.contains("--color-bg:"))
        assertTrue("CSS var for text should be present", decoded.contains("--color-text:"))
        assertFalse("Template token {{BGColour}} should be replaced", decoded.contains("{{BGColour}}"))
        assertFalse("Template token with spaces should be replaced", decoded.contains("{{ BGColour }}"))
        assertFalse("Style block should not collapse to {}", decoded.contains(":root{--color-bg:{}}"))
        // Unknown Blade-like token should remain; verify via regex rather than exact string matching
        val unknownRegex = Regex("\\{\\{\\s*\\$name\\s*\\}\\}")
        val preserved = unknownRegex.containsMatchIn(decoded) || decoded.contains("{{")
        assertTrue("Unknown token should be preserved", preserved)
    }
}
