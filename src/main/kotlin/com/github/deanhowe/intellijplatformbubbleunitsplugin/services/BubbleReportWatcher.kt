package com.github.deanhowe.intellijplatformbubbleunitsplugin.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
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

    private var registered = false

    init {
        register()
    }

    private fun register() {
        if (registered) return
        val connection = project.messageBus.connect(this)
        connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: MutableList<out VFileEvent>) {
                val base = project.basePath ?: return
                val changed = events.any { e ->
                    val vf = e.file
                    if (vf != null) matchesJunit(vf) else e.path?.let { it.startsWith(base) && it.endsWith("/junit-report.xml") } == true
                }
                if (changed) {
                    thisLogger().info("junit-report.xml changed; notifying listeners")
                    project.messageBus.syncPublisher(TOPIC).junitReportChanged()
                }
            }
        })
        registered = true
    }

    private fun matchesJunit(file: VirtualFile): Boolean {
        val base = project.basePath ?: return false
        if (file.isDirectory) return false
        if (!file.name.equals("junit-report.xml", ignoreCase = true)) return false
        val path = file.path
        return path.startsWith(base)
    }

    override fun dispose() {
        // Connection is bound to this disposable
        registered = false
    }
}

/** Lightweight Disposable to avoid importing intellij.platform.util.concurrent Disposable directly here. */
interface DisposableEx : com.intellij.openapi.Disposable
