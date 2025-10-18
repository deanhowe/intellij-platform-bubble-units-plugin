package com.github.deanhowe.intellijplatformbubbleunitsplugin.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.messages.Topic

/**
 * Watches the project's junit-report.xml and notifies listeners when it changes.
 * Properly unregisters on dispose to support dynamic plugin loading.
 */
@Service(Service.Level.PROJECT)
class BubbleReportWatcher(private val project: Project) : DisposableEx {

    interface Listener {
        fun junitReportChanged()
    }

    companion object {
        val TOPIC: Topic<Listener> = Topic.create(
            "BubbleUnits JUnit report change",
            Listener::class.java
        )
        fun getInstance(project: Project): BubbleReportWatcher = project.service()
    }

    init {
        // Subscribe once; the connection is bound to this service's Disposable (no manual flags needed)
        val connection = project.messageBus.connect(this)
        connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: MutableList<out VFileEvent>) {
                val envPathAbs = try {
                    BubbleSettingsService.getInstance(project).resolveJunitPathFromEnv()
                } catch (_: Exception) { null }
                val changed = events.any { e ->
                    val vf = e.file
                    if (vf != null) {
                        matchesJunit(vf, envPathAbs)
                    } else {
                        val p = e.path
                        if (envPathAbs != null && p.equals(envPathAbs, ignoreCase = false)) return@any true
                        val name = p.substringAfterLast('/')
                        isJunitName(name)
                    }
                }
                if (changed) {
                    com.github.deanhowe.intellijplatformbubbleunitsplugin.util.Logging.info(
                        project,
                        BubbleReportWatcher::class.java,
                        "JUnit report change detected; notifying listeners"
                    )
                    project.messageBus.syncPublisher(TOPIC).junitReportChanged()
                }
            }
        })
    }

    private fun matchesJunit(file: VirtualFile, envPathAbs: String?): Boolean {
        if (file.isDirectory) return false
        // If explicit .env path is configured, accept exact path match regardless of content root/exclusion
        if (!envPathAbs.isNullOrEmpty() && file.path == envPathAbs) return true
        if (!isJunitName(file.name)) return false
        val index = com.intellij.openapi.roots.ProjectFileIndex.getInstance(project)
        return index.isInContent(file)
    }

    private fun isJunitName(name: String): Boolean {
        if (name.equals("report.junit.xml", ignoreCase = true)) return true
        if (name.equals("junit-report.xml", ignoreCase = true)) return true
        if (name.equals("junit.xml", ignoreCase = true)) return true
        if (name.equals("TESTS-TestSuites.xml", ignoreCase = true)) return true
        if (name.equals("TEST-results.xml", ignoreCase = true)) return true
        if (Regex("""TEST-.*\.xml""").matches(name)) return true
        return false
    }

    override fun dispose() {
        // No-op: the message bus connection is tied to this Disposable
    }
}

/** Lightweight Disposable to avoid importing intellij.platform.util.concurrent Disposable directly here. */
interface DisposableEx : com.intellij.openapi.Disposable
