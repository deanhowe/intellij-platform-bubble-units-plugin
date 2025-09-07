package com.github.deanhowe.intellijplatformbubbleunitsplugin.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.project.Project

class BubbleSettingsConfigurableProvider(private val project: Project) : ConfigurableProvider() {
    override fun createConfigurable(): Configurable {
        return BubbleSettingsConfigurable(project)
    }
}