import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import java.io.File
import org.w3c.dom.Element

val projectDirPath = project.projectDir.absolutePath

plugins {
    id("java") // Java support
    alias(libs.plugins.kotlin) // Kotlin support
    alias(libs.plugins.intelliJPlatform) // IntelliJ Platform Gradle Plugin
    alias(libs.plugins.changelog) // Gradle Changelog Plugin
    alias(libs.plugins.qodana) // Gradle Qodana Plugin
    alias(libs.plugins.kover) // Gradle Kover Plugin
    id("io.gitlab.arturbosch.detekt") version "1.23.8" // Kotlin static analysis (Detekt)
    id("com.github.node-gradle.node") version "7.0.1" // Node Support
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

// Set the JVM language level used to build the project.
kotlin {
    jvmToolchain(21)
}

node {
    // Ensures CI/local builds don’t rely on a preinstalled Node
    download.set(true)
    version.set("22.12.0") // pick a specific, tested Node.js version
    // npmVersion can be set too if needed
}

// Configure project's dependencies
repositories {
    mavenCentral()

    // IntelliJ Platform Gradle Plugin Repositories Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-repositories-extension.html
    intellijPlatform {
        defaultRepositories()
    }
}

// Dependencies are managed with Gradle version catalog - read more: https://docs.gradle.org/current/userguide/platforms.html#sub:version-catalog
dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.opentest4j)

    // Detekt formatting rules
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.8")

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        create(providers.gradleProperty("platformType"), providers.gradleProperty("platformVersion"))

        // Plugin Dependencies. Uses `platformBundledPlugins` property from the gradle.properties file for bundled IntelliJ Platform plugins.
        bundledPlugins(providers.gradleProperty("platformBundledPlugins").map { it.split(',') })

        // Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file for plugin from JetBrains Marketplace.
        plugins(providers.gradleProperty("platformPlugins").map { it.split(',') })

        // Module Dependencies. Uses `platformBundledModules` property from the gradle.properties file for bundled IntelliJ Platform modules.
        bundledModules(providers.gradleProperty("platformBundledModules").map { it.split(',') })

        testFramework(TestFrameworkType.Platform)
    }
}

// Configure IntelliJ Platform Gradle Plugin - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html
intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")

        // Extract the <!-- Plugin description --> section from README.md and provide for the plugin's manifest
        description = providers.fileContents(layout.projectDirectory.file("README.md")).asText.map {
            val start = "<!-- Plugin description -->"
            val end = "<!-- Plugin description end -->"

            with(it.lines()) {
                if (!containsAll(listOf(start, end))) {
                    throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
                }
                subList(indexOf(start) + 1, indexOf(end)).joinToString("\n").let(::markdownToHTML)
            }
        }

        val changelog = project.changelog // local variable for configuration cache compatibility
        // Get the latest available change notes from the changelog file
        changeNotes = providers.gradleProperty("pluginVersion").map { pluginVersion ->
            with(changelog) {
                renderItem(
                    (getOrNull(pluginVersion) ?: getUnreleased())
                        .withHeader(false)
                        .withEmptySections(false),
                    Changelog.OutputType.HTML,
                )
            }
        }

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        // The pluginVersion is based on the SemVer (https://semver.org) and supports pre-release labels, like 2.1.7-alpha.3
        // Specify pre-release label to publish the plugin in a custom Release Channel automatically. Read more:
        // https://plugins.jetbrains.com/docs/intellij/deployment.html#specifying-a-release-channel
        channels = providers.gradleProperty("pluginVersion").map { listOf(it.substringAfter('-', "").substringBefore('.').ifEmpty { "default" }) }
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}

// Configure Gradle Changelog Plugin - read more: https://github.com/JetBrains/gradle-changelog-plugin
changelog {
    groups.empty()
    repositoryUrl = providers.gradleProperty("pluginRepositoryUrl")
}

// Configure Gradle Kover Plugin - read more: https://github.com/Kotlin/kotlinx-kover#configuration
kover {
    reports {
        total {
            xml {
                onCheck = true
            }
        }
    }
}

// Configure Detekt - Kotlin static analysis
detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("detekt.yml"))
    baseline = file("detekt-baseline.xml")
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt> {
    jvmTarget = "21"
    // Adopt gradually: report but don't fail the build initially
    ignoreFailures = true
}

// Make detekt part of the standard check lifecycle
tasks.named("check") { dependsOn("detekt") }

tasks {
    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").get()
    }

    publishPlugin {
        dependsOn(patchChangelog)
    }

    // Merge Gradle's per-class JUnit XML reports into a single junit-report.xml at project root
    val mergeJUnitReports by registering {
        group = "verification"
        description = "Merge Gradle test XML reports into a single junit-report.xml at project root for BubbleUnits"
        notCompatibleWithConfigurationCache("Simple XML merge script uses non-CC-safe references")
        inputs.dir(layout.buildDirectory.dir("test-results"))
        outputs.file(layout.projectDirectory.file("junit-report.xml"))

        doLast {
            val resultsRoot = File("$projectDirPath/build/test-results")
            val targetFile = File("$projectDirPath/junit-report.xml")

            val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = false
            val builder = factory.newDocumentBuilder()
            val master = builder.newDocument()
            val root = master.createElement("testsuites")
            master.appendChild(root)

            var totalTests = 0
            var totalFailures = 0
            var totalErrors = 0
            var totalSkipped = 0
            var totalTime = 0.0

            val xmlFiles = if (resultsRoot.exists()) {
                resultsRoot.walkTopDown().filter { it.isFile && it.name.endsWith(".xml") }.toList()
            } else {
                emptyList()
            }

            xmlFiles.forEach { f ->
                try {
                    val doc = builder.parse(f)
                    val elem = doc.documentElement

                    fun importSuite(suiteElem: Element) {
                        fun getInt(name: String) = suiteElem.getAttribute(name).toIntOrNull() ?: 0
                        fun getDouble(name: String) = suiteElem.getAttribute(name).toDoubleOrNull() ?: 0.0
                        totalTests += getInt("tests")
                        totalFailures += getInt("failures")
                        totalErrors += getInt("errors")
                        totalSkipped += (getInt("skipped") + getInt("ignored"))
                        totalTime += getDouble("time")
                        val imported = master.importNode(suiteElem, true)
                        root.appendChild(imported)
                    }

                    when (elem.tagName) {
                        "testsuite" -> importSuite(elem)
                        "testsuites" -> {
                            val children = elem.getElementsByTagName("testsuite")
                            for (i in 0 until children.length) {
                                importSuite(children.item(i) as Element)
                            }
                        }
                        else -> {
                            // ignore other roots
                        }
                    }
                } catch (e: Exception) {
                    logger.warn("Failed to merge test report ${f.name}: ${e.message}")
                }
            }

            root.setAttribute("tests", totalTests.toString())
            root.setAttribute("failures", totalFailures.toString())
            root.setAttribute("errors", totalErrors.toString())
            root.setAttribute("skipped", totalSkipped.toString())
            root.setAttribute("time", "%.3f".format(totalTime))

            val tf = javax.xml.transform.TransformerFactory.newInstance().newTransformer()
            tf.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes")
            tf.transform(javax.xml.transform.dom.DOMSource(master), javax.xml.transform.stream.StreamResult(targetFile))

            if (xmlFiles.isEmpty()) {
                logger.lifecycle("No test result XMLs found under $resultsRoot; wrote empty ${targetFile.path}")
            } else {
                logger.lifecycle("Merged ${xmlFiles.size} test result file(s) into ${targetFile.path}")
            }
        }
    }

    test {
        systemProperty("java.util.logging.config.file", "${project.projectDir}/src/test/resources/test-log.properties")
        // Ensure JUnit XML and HTML reports are generated (useful for CI/test result export)
        reports {
            junitXml.required.set(true)
            html.required.set(true)
        }
        finalizedBy(named("mergeJUnitReports"))
    }
}

val npmCi by tasks.registering(com.github.gradle.node.npm.task.NpmTask::class) {
    description = "Install frontend dependencies using npm ci"
    workingDir.set(file("web"))
    args.set(listOf("ci"))
}

val npmBuild by tasks.registering(com.github.gradle.node.npm.task.NpmTask::class) {
    description = "Build frontend assets"
    dependsOn(npmCi)
    workingDir.set(file("web"))
    args.set(listOf("run", "build"))
}

// Copy the Vite-produced PHPUnitBubbleReport.html into plugin resources as web/bubble.html
val renderPhpUnitBubbleReportToResources by tasks.registering(Copy::class) {
    description = "Export Vite output (PHPUnitBubbleReport.html) into resources/web as bubble.html"
    dependsOn(npmBuild)
    from("web/dist/PHPUnitBubbleReport.html")
    rename { "bubble.html" }
    into("src/main/resources/web")
}

tasks.named<ProcessResources>("processResources") {
    // Ensure the HTML is generated and copied into resources during builds
    dependsOn(renderPhpUnitBubbleReportToResources)
}

// Optional: ensure assemble/buildPlugin also trigger frontend build
tasks.named("assemble") { dependsOn("processResources") }

// Ensure runIde generates and copies the HTML before launching the IDE
tasks.matching { it.name == "runIde" }.configureEach {
    dependsOn(renderPhpUnitBubbleReportToResources)
}

intellijPlatformTesting {
    runIde {
        register("runIdeForUiTests") {
            task {
                jvmArgumentProviders += CommandLineArgumentProvider {
                    listOf(
                        "-Drobot-server.port=8082",
                        "-Dide.mac.message.dialogs.as.sheets=false",
                        "-Djb.privacy.policy.text=<!--999.999-->",
                        "-Djb.consents.confirmation.enabled=false",
                    )
                }
            }

            plugins {
                robotServerPlugin()
            }
        }
    }
}
