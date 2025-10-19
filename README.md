# BubbleUnits Browser Plugin for Jetbrains IDE/Platform/s

![Build](https://github.com/deanhowe/intellij-platform-bubble-units-plugin/workflows/Build/badge.svg) [![Version](https://img.shields.io/jetbrains/plugin/v/28293-bubble-units.svg)](https://plugins.jetbrains.com/plugin/28293-bubble-units) [![Downloads](https://img.shields.io/jetbrains/plugin/d/28293-bubble-units.svg)](https://plugins.jetbrains.com/plugin/28293-bubble-units) <div id="jetbrainsMarketplacePanel"></div>

## Overview

The BubbleUnits[^1] Browser plugin is a simple tool that renders a single webview within a dedicated tab inside your JetBrains IDE of choice.

The rendered URL can be specified in the plugin's preferences, or via either a `BUBBLE_UNITS_URL` or `APP_URL` variable in your projects root `.env` file, if neither of these options is specified and a `junit-reports.xml` file is detected in your projects root directory a [BubbleUnits](https://deanhowe.github.io/php-bubbleunit-reports/) graph is shown.

---

<!-- Plugin description -->
BubbleUnits embeds a dedicated web panel in your IDE to visualize test results as interactive bubble charts or display any web content you choose.

No navigation controls, no browser tabs - just your test results or chosen web content, beautifully displayed in your IDE.

Designed for with PHP developers using TDD in mind (or any testing framework that produces JUnit XML), it gives you immediate visual feedback on test results without leaving your IDE.

---

<table border="0"><tr cellborder="2"><td width="66%" border="2">

**Key Features:**
* Interactive bubble chart visualization of test results from junit-report.xml
* Configurable to display any URL of your choice
* Supports project-specific URLs via .env configuration
* Toolbar with refresh and browser launch actions
* Lightweight with minimal UI footprint

</td><td valign="top">

![Screenshot](https://github.com/deanhowe/intellij-platform-bubble-units-plugin/raw/main/art/bubble-units.gif)

</td></tr></table>

---

**URL Configuration (in order of precedence):**
1. Custom __URL__ in `Settings/Preferences | Tools | BubbleUnits`
2. Development panel (if enabled): render selected/bundled HTML (data URL)
3. `BUBBLE_UNITS_URL` or `APP_URL` in your project's .env file
4. Default bundled bubble.html (data URL) as fallback

---

To enable the BubbleUnit visualization, add this to your test command:

```
--log-junit junit-report.xml 
```

<!-- Plugin description end -->

---

## Features

<img src="https://github.com/deanhowe/intellij-platform-bubble-units-plugin/raw/main/art/bubble-units-screenshot-dark.png" width="400" align="right" alt="Screenshot of BubbleUnits Plugin"/>

- Embeds a web page directly in your IDE
- Simple configuration through the settings panel
- Supports project-specific URLs via `.env` file
- Lightweight and minimal design
- No navigation controls – just displays the specified URL

Enable JUnit logging in your app i.e for Laravel PHP in your composer scripts like `@php artisan test --log-junit junit-report.xml`

---

## Installation

https://github.com/deanhowe/intellij-platform-bubble-units-plugin

- Using JetBrains Marketplace:

  Go to [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/28293-bubble-units) and install it by clicking the <kbd>Install to ...</kbd> button in case your IDE is running.

- Using the IDE built-in plugin system:

  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "Bubble Units"</kbd> >
  <kbd>Install</kbd>

- Manually:

  Download the [latest release](https://github.com/deanhowe/intellij-platform-bubble-units-plugin/releases/latest) and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

---

## Usage

- After installation, you'll find a new tool window/panel labeled "BubbleUnits" with a globe icon on the right side of your IDE.
- The plugin determines the URL to display in the following order of priority:
  1. User-configured URL from settings (if set)
  2. If the project has a `./junit-report.xml` file in the root (and the checkbox is ticked), a BubbleUnit graph is shown/rendered
  3. URL from `.env` file in the project directory (if it exists)
     1. `BUBBLE_UNITS_URL=<URL>`
     2. `APP_URL=<URL>`
  4. Default URL: "https://bubbles.laravel.cloud"
- To configure the URL via settings:
   - Go to <kbd>Settings/Preferences</kbd> > <kbd>Tools</kbd> > <kbd>BubbleUnits Settings</kbd>
   - Enter the URL you want to display
   - Click <kbd>Apply</kbd> or <kbd>OK</kbd>
- To configure the URL via `.env` file:
   - Create a file named `.env` in the root directory of your project
   - Add a line with the format: `BUBBLE_UNITS_URL=https://<your-url-here>`
   - The plugin will automatically detect and use this URL if no custom URL is set in the settings
- To override the JUnit XML report location via `.env` (useful for dogfooding this plugin in this repo or custom setups):
   - Add `BUBBLE_UNITS_JUNIT_PATH=build/test-results/test/TEST-*.xml` (use a concrete file path) or an alias key `JUNIT_XML_PATH=...`
   - Relative paths are resolved against the project root; absolute paths are supported
   - The specified file is monitored for changes even if it’s outside the project content roots/excluded folders
- Open the Bubble Units tool window to view the web content or BubbleUnits graph

---

## Development

For developers who want to modify or extend this plugin:

```bash
# Run tests
./gradlew check

# Build the project
./gradlew build

# Create a distributable plugin zip in build/distributions
./gradlew buildPlugin

# Run the plugin in a development IDE instance
./gradlew runIde

# Clear the cache
./gradlew cleanBuildCache

```

The `./src/main/resources/web/bubble.html` file is sourced/derived from my repo [PHPUnitBubble Report](https://github.com/deanhowe/phpunit-d3-report) and if you want to edit the BubbleUnit graph, you should probably check that out.


---

## Run with different IDEs for development

By default, `./gradlew runIde` launches PhpStorm (PS) version 2025.2, as configured in `gradle.properties`.
You can switch the IDE (and version) at runtime via Gradle properties without changing files:

- IntelliJ IDEA Ultimate:
  ./gradlew runIde -PplatformType=IU -PplatformVersion=2024.3.6

- PhpStorm:
  ./gradlew runIde -PplatformType=PS -PplatformVersion=2024.3.4

---

> [!NOTE]
> - The first run will download the selected IDE into Gradle caches; subsequent runs are faster.
> - Ensure your plugin dependencies are compatible with the chosen IDE and version. If you need IDE-specific bundled plugins, set `platformBundledPlugins` in `gradle.properties` accordingly.
> - Supported `platformType` codes are documented in `gradle.properties`.

---

## Development panel

The plugin provides a lightweight development/debug panel to iterate on HTML directly inside the IDE tool window.

How to use
- Open Settings/Preferences > Tools > BubbleUnits
- Check “Enable development panel”
- Choose an HTML directory (browse button supports path completion)
- Pick an HTML file from the dropdown:
  - Bundled defaults: bubble.html, bubble-test.html, bubble-unit-help.html
  - A divider “— from directory —” separates any discovered .html files in your chosen directory
- Click Apply — the BubbleUnits tool window reloads immediately (live reload on settings change)

Notes
- If the resolved URL is a data: URL, the settings field displays “<embedded data url>” instead of the very long string. Clear it to enter a real custom URL.
- HTML lookup order when the development panel is enabled:
  1) Selected directory (if provided)
  2) Project root (./<file>)
  3) Bundled resource (from the plugin)
- URL precedence (effective order):
  1) Custom URL in Settings (non-blank)
  2) Development panel (renders selected/bundled HTML as a data URL)
  3) .env — BUBBLE_UNITS_URL, otherwise APP_URL
  4) Default bundled bubble.html as a data URL

Tips
- Use bubble-test.html to verify JetBrains theme variables (text, background, error, warning, success, info) are applied.
- bubble-unit-help.html contains a similar style-guide style page for quick visual checks.

---

## Test logging (for running tests)

Tests in this project use JUL (java.util.logging). Gradle is configured to point tests at src/test/resources/test-log.properties automatically:

- In build.gradle.kts: tasks.test { systemProperty("java.util.logging.config.file", "${project.projectDir}/src/test/resources/test-log.properties") }

You don’t need to copy any files; just run:

- ./gradlew test
- ./gradlew test --tests "com.github.deanhowe.intellijplatformbubbleunitsplugin.MyBundleTest.testMessageFormatting"

See .junie/guidelines.md for details.

---

## JUnit XML test reports

Gradle is configured to export test results in JUnit XML format.

- Run tests: `./gradlew test`
- XML results: `build/test-results/test/*.xml`
- HTML report: `build/reports/tests/test/index.html`

These reports are useful for CI systems that consume JUnit XML artifacts.

---

### Snapshot exports (SVG/PNG/JSON)

- Where files are saved: By default, exports are written into a hidden folder in your project root: `./.bubble-unit-snapshots/`.
- After you click one of the export buttons, the IDE shows a notification with the absolute path to the saved file.
- If the embedded page can’t export (e.g., the bridge isn’t ready or there’s no SVG yet), BubbleUnits now shows a balloon with guidance instead of failing silently.
- You can change the snapshot directory in Settings/Preferences | Tools | BubbleUnits.
---

## Troubleshooting

- JCEF not available: If you’re running in a headless environment or JCEF is disabled, the tool window will display a message and won’t render the embedded page. Use the toolbar action “Open in Browser” to open the resolved URL in your default browser.
- URL resolution order: Custom URL (Settings) > Development panel (data URL) > .env (BUBBLE_UNITS_URL, then APP_URL) > default bundled bubble.html. Use the “Reset to default” and “Test URL” buttons in Settings to verify inputs.
- Errors in Event Log: When content cannot be resolved, an error notification is posted to the IDE Event Log (Notification group: “BubbleUnits”). Also check IDEA logs for messages containing “BubbleUnits”.

---

## Links

- JetBrains Marketplace: https://plugins.jetbrains.com/plugin/28293-bubble-units
- Issue tracker: https://github.com/deanhowe/intellij-platform-bubble-units-plugin/issues
- Documentation:
  - Troubleshooting: docs/troubleshooting.md
  - Using as a template: docs/template-usage.md

---

### Known benign IDE warnings during runIde

These messages can appear in the IDE log when launching the development IDE via `./gradlew runIde`. They are not caused by BubbleUnits and can be safely ignored in development:

- No URL bundle (CFBundleURLTypes) is defined in the main bundle. To be able to open external links, specify protocols in the app layout section of the build file. Example: args.urlSchemes = ["your-protocol"]
  - Explanation: This applies to packaging a standalone macOS app and cannot be configured by an IntelliJ plugin. BubbleUnits opens external links with standard http/https/file URLs using the IDE’s BrowserUtil; no custom URL scheme is required.
- resource not found: colorSchemes/DqlAddonsDefault.xml and colorSchemes/DqlAddonsDarcula.xml
  - Explanation: Emitted by the IDE’s EditorColorsManager when a different installed plugin (e.g., DQL Addons) declares schemes that are not present for the current IDE. BubbleUnits does not register any color schemes.
- `preload=NOT_HEADLESS`/`preload=TRUE` must be used only for core services (Code With Me related)
  - Explanation: Logged by bundled JetBrains plugins in development IDE builds; unrelated to BubbleUnits functionality.

---

## Privacy

- No telemetry is collected or sent. The plugin operates locally within your IDE.
- The plugin may read the following files from your project when resolving content:
  - `.env` (keys: `BUBBLE_UNITS_URL`, `APP_URL`, optional `BUBBLE_UNITS_JUNIT_PATH` / `JUNIT_XML_PATH`)
  - `junit-report.xml` (or another configured JUnit XML path) for visualization
- Network requests are only made to the configured URL when the tool window is active; the plugin does not fetch remote content at IDE startup.

---

## Contributing

Contributions are welcome! This plugin is intentionally kept simple and minimal, so please keep that in mind when submitting changes.

---

Plugin based on the [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template).

<div id="jetbrainsMarketplacePanel"></div>
<img src="https://resources.jetbrains.com/storage/products/company/brand/logos/jb_beam.svg" width="200" align="left" alt="JetBrainsIcon"/>
<div id="jetbrainsMarketplaceButton"></div>
<script src="https://plugins.jetbrains.com/assets/scripts/mp-widget.js"></script>
<script>
  MarketplaceWidget.setupMarketplaceWidget('install', 28293, "#jetbrainsMarketplaceButton");
  MarketplaceWidget.setupMarketplaceWidget('card', 28293, "#jetbrainsMarketplacePanel");
</script>

---

[^1]: BubbleUnits - a diagram showing the run time of tests in a test suite
