package com.github.deanhowe.intellijplatformbubbleunitsplugin.settings

import com.github.deanhowe.intellijplatformbubbleunitsplugin.MyBundle
import com.github.deanhowe.intellijplatformbubbleunitsplugin.services.BubbleSettingsService
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

class BubbleSettingsConfigurable(private val project: Project) : Configurable {
    private var urlField: JBTextField? = null
    private val settings = BubbleSettingsService.getInstance(project)

    override fun getDisplayName(): String = MyBundle.message("bubbleSettingsTitle")

    override fun createComponent(): JComponent {
        // Initialize with the current URL (which might be from settings, .env, or default)
        urlField = JBTextField(settings.url)

        val panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel(MyBundle.message("bubbleSettingsUrlLabel")), urlField!!, 1, false)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        return panel
    }

    override fun isModified(): Boolean {
        // Check if the URL in the text field is different from the current custom URL
        return urlField?.text != settings.url
    }

    override fun apply() {
        // Update the custom URL in settings
        settings.url = urlField?.text ?: ""
    }

    override fun reset() {
        // Reset to the current URL (which might be from settings, .env, or default)
        urlField?.text = settings.url
    }

    override fun disposeUIResources() {
        urlField = null
    }
}
