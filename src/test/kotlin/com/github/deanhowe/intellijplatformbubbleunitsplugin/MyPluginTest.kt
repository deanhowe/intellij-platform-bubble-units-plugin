package com.github.deanhowe.intellijplatformbubbleunitsplugin

import com.github.deanhowe.intellijplatformbubbleunitsplugin.services.BubbleSettingsService
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.components.service
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.PsiErrorElementUtil

@TestDataPath("\$CONTENT_ROOT/src/test/testData")
class MyPluginTest : BasePlatformTestCase() {

    fun testXMLFile() {
        val psiFile = myFixture.configureByText(XmlFileType.INSTANCE, "<foo>bar</foo>")
        val xmlFile = assertInstanceOf(psiFile, XmlFile::class.java)

        assertFalse(PsiErrorElementUtil.hasErrors(project, xmlFile.virtualFile))

        assertNotNull(xmlFile.rootTag)

        xmlFile.rootTag?.let {
            assertEquals("foo", it.name)
            assertEquals("bar", it.value.text)
        }
    }

    fun testRename() {
        myFixture.testRename("foo.xml", "foo_after.xml", "a2")
    }

    fun testBubbleSettingsDefaultUrl() {
        // Test that the default URL is used when no custom URL is set
        val settingsService = project.service<BubbleSettingsService>()

        // Reset any custom URL that might have been set by other tests
        settingsService.url = ""

        // The default URL should be a data URL
        assertTrue(settingsService.url.startsWith("data:text/html"))
    }

    fun testBubbleSettingsCustomUrl() {
        // Test that a custom URL is used when set
        val settingsService = project.service<BubbleSettingsService>()

        // Set a custom URL
        val customUrl = "https://example.com/custom"
        settingsService.url = customUrl

        // The custom URL should be returned
        assertEquals(customUrl, settingsService.url)

        // Reset the URL for other tests
        settingsService.url = ""
    }

    fun testBubbleSettingsUrlPriority() {
        // Test the URL priority logic: custom URL > default URL
        val settingsService = project.service<BubbleSettingsService>()

        // First, verify the default URL is used initially
        settingsService.url = ""
        assertTrue(settingsService.url.startsWith("data:text/html"))

        // Then set a custom URL and verify it takes precedence
        val customUrl = "https://example.com/custom"
        settingsService.url = customUrl
        assertEquals(customUrl, settingsService.url)

        // Reset the custom URL to empty and verify we go back to the default
        settingsService.url = ""
        assertTrue(settingsService.url.startsWith("data:text/html"))
    }

    override fun getTestDataPath() = "src/test/testData/rename"
}
