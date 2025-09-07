package com.github.deanhowe.intellijplatformbubbleunitsplugin.startup

import com.github.deanhowe.intellijplatformbubbleunitsplugin.services.BubbleSettingsService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.messages.MessageBusConnection

class MyProjectActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        // Initialize the settings service
        project.service<BubbleSettingsService>()
    }
}
