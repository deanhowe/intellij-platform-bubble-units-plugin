package com.github.deanhowe.intellijplatformbubbleunitsplugin.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileEvent
import com.intellij.openapi.vfs.VirtualFileListener
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.messages.Topic

/**
 * Watches the project's junit-report.xml and notifies listeners when it changes.
 * Properly unregisters on dispose to support dynamic plugin loading.
 */
@Service(Service.Level.PROJECT)
class BubbleReportWatcher(private val project: Project) : VirtualFileListener, DisposableEx {

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

    private var connection: Any? = null
    private var registered = false

    init {
        register()
    }

    private fun register() {
        if (registered) return
        VirtualFileManager.getInstance().addVirtualFileListener(this, this)
        registered = true
    }

    private fun matchesJunit(file: VirtualFile?): Boolean {
        val base = project.basePath ?: return false
        if (file == null || file.isDirectory) return false
        if (!file.name.equals("junit-report.xml", ignoreCase = true)) return false
        val path = file.path
        return path.startsWith(base)
    }

    override fun contentsChanged(event: VirtualFileEvent) {
        if (matchesJunit(event.file)) {
            thisLogger().info("junit-report.xml changed; notifying listeners")
            project.messageBus.syncPublisher(TOPIC).junitReportChanged()
        }
    }

    override fun fileCreated(event: VirtualFileEvent) {
        if (matchesJunit(event.file)) {
            project.messageBus.syncPublisher(TOPIC).junitReportChanged()
        }
    }

    override fun fileDeleted(event: VirtualFileEvent) {
        if (matchesJunit(event.file)) {
            project.messageBus.syncPublisher(TOPIC).junitReportChanged()
        }
    }

    override fun dispose() {
        // Listener is registered with this as parent disposable; nothing else to do
        registered = false
    }
}

/** Lightweight Disposable to avoid importing intellij.platform.util.concurrent Disposable directly here. */
interface DisposableEx : com.intellij.openapi.Disposable
