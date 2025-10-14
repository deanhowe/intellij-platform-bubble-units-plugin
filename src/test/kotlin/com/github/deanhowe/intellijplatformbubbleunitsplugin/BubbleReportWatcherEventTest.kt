package com.github.deanhowe.intellijplatformbubbleunitsplugin

import com.github.deanhowe.intellijplatformbubbleunitsplugin.services.BubbleReportWatcher
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class BubbleReportWatcherEventTest : BasePlatformTestCase() {

    private fun subscribeAndAwait(action: () -> Unit): Boolean {
        val latch = CountDownLatch(1)
        val connection = project.messageBus.connect(testRootDisposable)
        connection.subscribe(BubbleReportWatcher.TOPIC, object : BubbleReportWatcher.Listener {
            override fun junitReportChanged() {
                latch.countDown()
            }
        })
        // Ensure service is initialized
        BubbleReportWatcher.getInstance(project)

        action()

        // Dispatch EDT events just in case
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        return latch.await(5, TimeUnit.SECONDS)
    }

    fun testPublishesOnCreate() {
        val ok = subscribeAndAwait {
            ApplicationManager.getApplication().runWriteAction {
                myFixture.tempDirFixture.createFile("junit-report.xml", "<testsuite name='c'/>")
            }
        }
        assertTrue("Expected watcher to publish event on create", ok)
    }

    fun testPublishesOnChange() {
        // Pre-create file as VirtualFile in temp project dir
        var vf: com.intellij.openapi.vfs.VirtualFile? = null
        ApplicationManager.getApplication().runWriteAction {
            vf = myFixture.tempDirFixture.createFile("TESTS-TestSuites.xml", "<testsuite name='a'/>")
        }

        val ok = subscribeAndAwait {
            ApplicationManager.getApplication().runWriteAction {
                VfsUtil.saveText(vf!!, "<testsuite name='b'/>")
            }
        }
        assertTrue("Expected watcher to publish event on change", ok)
    }

    fun testIgnoresNonMatchingFiles() {
        val latch = CountDownLatch(1)
        val connection = project.messageBus.connect(testRootDisposable)
        connection.subscribe(BubbleReportWatcher.TOPIC, object : BubbleReportWatcher.Listener {
            override fun junitReportChanged() {
                latch.countDown()
            }
        })
        BubbleReportWatcher.getInstance(project)

        ApplicationManager.getApplication().runWriteAction {
            myFixture.tempDirFixture.createFile("not-a-report.txt", "hello")
        }
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        // Expect no event within a short timeout
        val published = latch.await(1, TimeUnit.SECONDS)
        assertFalse("Did not expect watcher to publish for non-matching file", published)
    }
}
