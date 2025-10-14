package com.github.deanhowe.intellijplatformbubbleunitsplugin

import com.github.deanhowe.intellijplatformbubbleunitsplugin.services.BubbleSettingsService
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.openapi.vfs.VfsUtil
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class BubbleSettingsServiceCacheAndListenersTest : BasePlatformTestCase() {

    private fun settings(project: Project): BubbleSettingsService = BubbleSettingsService.getInstance(project)

    fun testCacheHitWhenSignatureUnchanged() {
        val svc = settings(project)
        // Ensure content root has a junit file to include in signature
        myFixture.tempDirFixture.createFile("junit-report.xml", "<testsuite name='a' tests='1' failures='0'/>")

        // First compute to populate cache
        val first = svc.refreshBlockingForTest(5000)
        assertNotNull(first)
        assertFalse("Computation must not run on EDT", svc.wasComputedOnEdtForTest())

        // Subscribe to settings changed; expecting NO event on unchanged signature
        val latch = CountDownLatch(1)
        val conn = project.messageBus.connect(testRootDisposable)
        conn.subscribe(BubbleSettingsService.SETTINGS_CHANGED, object : BubbleSettingsService.Companion.SettingsListener {
            override fun bubbleSettingsChanged() {
                latch.countDown()
            }
        })

        // Trigger another refresh; signature should be unchanged → no event published
        val second = svc.refreshBlockingForTest(5000)
        assertNotNull(second)
        assertEquals(first, second)
        assertFalse("No settings change should be published for cache hit", latch.await(1, TimeUnit.SECONDS))
    }

    fun testCacheInvalidatedOnVfsContentChange() {
        val svc = settings(project)
        val vf = myFixture.tempDirFixture.createFile("junit-report.xml", "<testsuite name='a' tests='1' failures='0'/>")
        val first = svc.refreshBlockingForTest(5000)
        assertNotNull(first)

        // Modify the report content
        ApplicationManager.getApplication().runWriteAction {
            VfsUtil.saveText(vf, "<testsuite name='a' tests='2' failures='1'/>")
        }

        // Force refresh; the URL should change due to different embedded payload
        val second = svc.refreshBlockingForTest(5000)
        assertNotNull(second)
        assertNotSame("URL should change when junit XML content changes", first, second)
    }

    fun testUrlUpdatedOnThemeChangeListener() {
        val svc = settings(project)
        myFixture.tempDirFixture.createFile("junit-report.xml", "<testsuite name='a' tests='1' failures='0'/>")
        // Warm up cache
        assertNotNull(svc.refreshBlockingForTest(5000))

        // Fire LafManagerListener via application bus; service will invalidate cache and schedule recompute
        val laf = LafManager.getInstance()
        ApplicationManager.getApplication().messageBus.syncPublisher(LafManagerListener.TOPIC).lookAndFeelChanged(laf)

        // Deterministically recompute and ensure success off-EDT
        val after = svc.refreshBlockingForTest(5000)
        assertNotNull(after)
        assertFalse("Computation must not run on EDT", svc.wasComputedOnEdtForTest())
    }
}
