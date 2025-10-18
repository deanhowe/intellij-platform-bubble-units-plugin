package com.github.deanhowe.intellijplatformbubbleunitsplugin.startup

import com.github.deanhowe.intellijplatformbubbleunitsplugin.services.BubbleSettingsService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class MyProjectActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        // Initialize the settings service
        project.service<BubbleSettingsService>()
    }
}
