package com.github.deanhowe.intellijplatformbubbleunitsplugin.settings

import com.github.deanhowe.intellijplatformbubbleunitsplugin.MyBundle
import com.github.deanhowe.intellijplatformbubbleunitsplugin.services.BubbleSettingsService
import com.github.deanhowe.intellijplatformbubbleunitsplugin.services.UrlValidator
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.*
import com.intellij.util.ui.FormBuilder
import com.intellij.ide.BrowserUtil
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.DefaultComboBoxModel
import javax.swing.Box
import javax.swing.BoxLayout
import java.awt.Dimension
import com.intellij.ui.JBColor
import com.intellij.ui.ScrollPaneFactory

class BubbleSettingsConfigurable(private val project: Project) : Configurable {
    private var urlField: JBTextField? = null
    private var devPanelCheckbox: JBCheckBox? = null
    private var dirField: TextFieldWithBrowseButton? = null
        private var snapshotDirField: TextFieldWithBrowseButton? = null
    private var fileCombo: ComboBox<String>? = null
    private val settings = BubbleSettingsService.getInstance(project)

    override fun getDisplayName(): String = MyBundle.message("bubbleSettingsTitle")

    override fun createComponent(): JComponent {
        // Initialize with the current URL (which might be from settings, .env, or default)
        val initialUrl = settings.url
        urlField = JBTextField(if (initialUrl.startsWith("data:")) MyBundle.message("settings.placeholder.embeddedDataUrl") else initialUrl)
        // If it's a data URL, still allow editing by clearing; typing will overwrite the placeholder
        if (initialUrl.startsWith("data:")) {
            urlField?.emptyText?.setText(MyBundle.message("settings.placeholder.embeddedDataUrlEmptyText"))
        }

        // Development/debug panel controls
        devPanelCheckbox = JBCheckBox(MyBundle.message("settings.devPanel.enable"), settings.state.devPanelEnabled)

        dirField = TextFieldWithBrowseButton()
        dirField?.text = settings.state.htmlDirectory ?: ""
        val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
        // Install path completion for convenience
        com.intellij.openapi.fileChooser.FileChooserFactory.getInstance()
            .installFileCompletion(dirField!!.textField, descriptor, true, project)
        // Open chooser on button click using non-deprecated API
        dirField!!.addActionListener {
            val chosen = com.intellij.openapi.fileChooser.FileChooser.chooseFile(descriptor, project, null)
            if (chosen != null) {
                dirField!!.text = chosen.path
            }
        }

        // Snapshots directory chooser
                snapshotDirField = TextFieldWithBrowseButton().apply { text = settings.state.snapshotDirectory ?: settings.getSnapshotDirectoryPath() }
                val snapDesc = FileChooserDescriptorFactory.createSingleFolderDescriptor()
                com.intellij.openapi.fileChooser.FileChooserFactory.getInstance()
                    .installFileCompletion(snapshotDirField!!.textField, snapDesc, true, project)
                snapshotDirField!!.addActionListener {
                    val chosen = com.intellij.openapi.fileChooser.FileChooser.chooseFile(snapDesc, project, null)
                    if (chosen != null) snapshotDirField!!.text = chosen.path
                }

                fileCombo = ComboBox()
        refreshFileCombo()

        // Update file list when directory changes
        dirField?.textField?.document?.addDocumentListener(object: javax.swing.event.DocumentListener {
            private fun changed() { refreshFileCombo() }
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = changed()
            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = changed()
            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = changed()
        })

        // URL row with Test button and inline error label
        val urlRow = JPanel()
        urlRow.layout = BoxLayout(urlRow, BoxLayout.X_AXIS)
        urlField?.maximumSize = Dimension(Int.MAX_VALUE, urlField!!.preferredSize.height)
        val testButton = javax.swing.JButton(MyBundle.message("settings.buttons.testUrl"))
        val resetButton = javax.swing.JButton(MyBundle.message("settings.buttons.resetToDefault"))
        val errorLabel = JBLabel("")
        errorLabel.foreground = JBColor.RED
        errorLabel.isVisible = false

        fun validateUrlAndShow(): Boolean {
            val text = urlField?.text?.trim().orEmpty().let { if (it == MyBundle.message("settings.placeholder.embeddedDataUrl")) "" else it }
            val ok = UrlValidator.isValidCustomUrl(text)
            if (!ok && text.isNotEmpty()) {
                errorLabel.text = MyBundle.message("settings.validation.invalidUrlDetailed")
                errorLabel.isVisible = true
            } else {
                errorLabel.text = ""
                errorLabel.isVisible = false
            }
            return ok
        }

        urlField?.document?.addDocumentListener(object: javax.swing.event.DocumentListener {
            private fun changed() { validateUrlAndShow() }
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = changed()
            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = changed()
            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = changed()
        })

        testButton.addActionListener {
            val text = urlField?.text?.trim().orEmpty()
            if (text.isEmpty() || text == MyBundle.message("settings.placeholder.embeddedDataUrl")) {
                errorLabel.text = MyBundle.message("settings.validation.enterUrlToTest")
                errorLabel.isVisible = true
            } else if (UrlValidator.isValidCustomUrl(text)) {
                BrowserUtil.browse(text)
            } else {
                errorLabel.text = MyBundle.message("settings.validation.invalidUrlSimple")
                errorLabel.isVisible = true
            }
        }

        resetButton.addActionListener {
            urlField?.text = ""
            errorLabel.text = ""
            errorLabel.isVisible = false
        }

        urlRow.add(urlField)
        urlRow.add(Box.createRigidArea(Dimension(8, 0)))
        urlRow.add(testButton)
        urlRow.add(Box.createRigidArea(Dimension(8, 0)))
        urlRow.add(resetButton)
        
        // Help section: wrap text and provide link
        val helpTextArea = JBTextArea(MyBundle.message("bubbleSettingsUrlHelp")).apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            background = null
        }
        val helpLink = ActionLink(MyBundle.message("bubbleSettingsUrlHelpLink")) {
            BrowserUtil.browse(MyBundle.message("bubbleSettingsUrlHelpUrl"))
        }
        val helpPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            alignmentX = 0f
        }
        helpPanel.add(helpTextArea)
        helpPanel.add(Box.createRigidArea(Dimension(0, 4)))
        helpPanel.add(helpLink)
        
        val innerPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel(MyBundle.message("bubbleSettingsUrlLabel")), urlRow, 1, false)
            .addComponent(errorLabel)
            .addComponent(helpPanel)
            .addComponent(devPanelCheckbox!!)
            .addLabeledComponent(JBLabel(MyBundle.message("settings.labels.htmlDirectory")), dirField!!)
            .addLabeledComponent(JBLabel(MyBundle.message("settings.labels.htmlFile")), fileCombo!!)
            .addLabeledComponent(JBLabel(MyBundle.message("settings.labels.snapshotsDirectory")), snapshotDirField!!)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        val scroll = ScrollPaneFactory.createScrollPane(innerPanel, true)
        scroll.horizontalScrollBarPolicy = javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
        scroll.verticalScrollBarPolicy = javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
        return scroll
    }

    private fun refreshFileCombo() {
        val previousSelection = fileCombo?.selectedItem as? String
        val dir = dirField?.text?.trim().orEmpty()

        val bundled = mutableListOf("bubble.html", "bubble-test.html", "bubble-unit-help.html")
        val discovered = mutableListOf<String>()
        if (dir.isNotEmpty()) {
            val f = java.io.File(dir)
            if (f.isDirectory) {
                f.listFiles { file -> file.isFile && file.name.endsWith(".html", ignoreCase = true) }?.forEach {
                    discovered.add(it.name)
                }
            }
        }
        // Build items: bundled first, optional divider, then discovered files (excluding duplicates)
        val items = mutableListOf<String>()
        items.addAll(bundled)
        val discoveredUnique = discovered.filter { it !in bundled }
        if (discoveredUnique.isNotEmpty()) {
            items.add(MyBundle.message("settings.labels.fromDirectoryDividerText"))
            items.addAll(discoveredUnique)
        }

        val model = DefaultComboBoxModel(items.toTypedArray())
        fileCombo?.model = model

        // Custom renderer to gray out the divider
        fileCombo?.renderer = object : javax.swing.plaf.basic.BasicComboBoxRenderer() {
            override fun getListCellRendererComponent(
                list: javax.swing.JList<*>, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean
            ): java.awt.Component {
                val c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JComponent
                val text = value as? String ?: ""
                if (text == MyBundle.message("settings.labels.fromDirectoryDividerText")) {
                    this.isEnabled = false
                    this.foreground = java.awt.Color.GRAY
                } else {
                    this.isEnabled = true
                }
                return c
            }
        }
        // Prevent selecting the divider
        if ((fileCombo?.selectedItem as? String) == MyBundle.message("settings.labels.fromDirectoryDividerText")) {
            fileCombo?.selectedIndex = 0
        }

        // Selection preferences: keep previous if still present, else prefer saved state, else default to bubble-test.html or first item
        val toSelect = when {
            previousSelection != null && items.contains(previousSelection) && previousSelection != MyBundle.message("settings.labels.fromDirectoryDividerText") -> previousSelection
            settings.state.selectedHtmlFile != null && items.contains(settings.state.selectedHtmlFile) -> settings.state.selectedHtmlFile
            items.contains("bubble-test.html") -> "bubble-test.html"
            else -> items.firstOrNull()
        }
        if (toSelect != null) fileCombo?.selectedItem = toSelect
    }

    override fun isModified(): Boolean {
        val customUrlModified = urlField?.text != settings.url
        val devEnabledModified = devPanelCheckbox?.isSelected != settings.state.devPanelEnabled
        val dirModified = (dirField?.text ?: "") != (settings.state.htmlDirectory ?: "")
        val fileModified = (fileCombo?.selectedItem as? String ?: "") != (settings.state.selectedHtmlFile ?: "")
        val snapModified = (snapshotDirField?.text ?: "") != (settings.state.snapshotDirectory ?: settings.getSnapshotDirectoryPath())
        return customUrlModified || devEnabledModified || dirModified || fileModified || snapModified
    }

    override fun apply() {
        // Validate URL before applying
        val enteredRaw = urlField?.text ?: ""
        val entered = if (enteredRaw == MyBundle.message("settings.placeholder.embeddedDataUrl")) "" else enteredRaw.trim()
        if (!UrlValidator.isValidCustomUrl(entered)) {
            throw ConfigurationException(MyBundle.message("settings.validation.invalidUrlDetailed"))
        }
        // Update the custom URL in settings (takes precedence over dev panel).
        settings.url = entered
        // Persist development panel settings
        val st = settings.getState()
        val before = Triple(st.devPanelEnabled, st.htmlDirectory, st.selectedHtmlFile)
        st.devPanelEnabled = devPanelCheckbox?.isSelected == true
        st.htmlDirectory = dirField?.text?.trim().orEmpty().ifEmpty { null }
        st.selectedHtmlFile = (fileCombo?.selectedItem as? String)?.trim()
        st.snapshotDirectory = snapshotDirField?.text?.trim().orEmpty().ifEmpty { null }
        val after = Triple(st.devPanelEnabled, st.htmlDirectory, st.selectedHtmlFile)
        if (before != after) {
            // Nudge URL recomputation and notify listeners to reload panel immediately
            settings.url = settings.url // setter triggers cache invalidation
            project.messageBus.syncPublisher(BubbleSettingsService.SETTINGS_CHANGED).bubbleSettingsChanged()
        }
    }

    override fun reset() {
        // Reset to the current URL (which might be from settings, .env, or default)
        run {
            val u = settings.url
            urlField?.text = if (u.startsWith("data:")) MyBundle.message("settings.placeholder.embeddedDataUrl") else u
        }
        devPanelCheckbox?.isSelected = settings.state.devPanelEnabled
        dirField?.text = settings.state.htmlDirectory ?: ""
        snapshotDirField?.text = settings.state.snapshotDirectory ?: settings.getSnapshotDirectoryPath()
        refreshFileCombo()
        val selected = settings.state.selectedHtmlFile
        if (selected != null) fileCombo?.selectedItem = selected
    }

    override fun disposeUIResources() {
        urlField = null
        devPanelCheckbox = null
        dirField = null
        snapshotDirField = null
        fileCombo = null
    }
}
