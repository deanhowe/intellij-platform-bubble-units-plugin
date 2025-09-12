# BubbleUnits Plugin for IntelliJ Platform

> [!CAUTION]
> This plugin is not ready for public consumption – yet.

![Build](https://github.com/deanhowe/intellij-platform-bubble-units-plugin/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/28293-bubble-units.svg)](https://plugins.jetbrains.com/plugin/28293-bubble-units)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/28293-bubble-units.svg)](https://plugins.jetbrains.com/plugin/28293-bubble-units)

## Overview

The BubbleUnits plugin is a simple tool that renders a webview from a URL in a dedicated tool window/panel within your IDE.

The URL can be specified in the plugin's preferences, from the `BUBBLE_UNITS_URL=` variable in a `.env` file in your projects root directory, defaults to your `APP_URL=` or loads a BubbleUnits graph when a `junit-reports.xml` file is detected in your projects root directory.

<!-- Plugin description -->
BubbleUnits embeds a dedicated web panel in your IDE to visualize test results as interactive bubble charts or display any web content you choose.

Designed for PHP developers using PHPUnit (or any testing framework that produces JUnit XML), it gives you immediate visual feedback on test results without leaving your IDE.

**Key Features:**
* Interactive bubble chart visualization of test results from junit-report.xml
* Configurable to display any URL of your choice
* Supports project-specific URLs via .env configuration
* Toolbar with refresh and browser launch actions
* Lightweight with minimal UI footprint

**URL Configuration (in order of precedence):**
1. Custom URL in Settings/Preferences | Tools | BubbleUnits
2. Development panel (if enabled): render selected/bundled HTML (data URL)
3. BUBBLE_UNITS_URL in your project's .env file
4. APP_URL in your project's .env file
5. Default bundled bubble.html (data URL) as fallback

To enable test visualization, add this to your test command:
```
--log-junit junit-report.xml
```

No navigation controls, no browser tabs - just your test results or chosen web content, beautifully displayed in your IDE.
<!-- Plugin description end -->

## Features

- Embeds a web page directly in your IDE
- Simple configuration through the settings panel
- Supports project-specific URLs via `.env` file
- Lightweight and minimal design
- No navigation controls – just displays the specified URL

Enable JUnit logging in your app i.e for Laravel PHP in your composer scripts like `@php artisan test --log-junit junit-report.xml`

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
- Open the Bubble Units tool window to view the web content or BubbleUnits graph

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

# Clera the cache
./gradlew cleanBuildCache

```

The `./src/main/resources/bubble.html` file is sourced/derived from my repo [PHPUnitBubble Report](https://github.com/deanhowe/phpunit-d3-report) and if you want to edit the BubbleUnit graph, you should probably check that out.

## Contributing

Contributions are welcome! This plugin is intentionally kept simple and minimal, so please keep that in mind when submitting changes.

---
Plugin based on the [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template).

---

## Run with different IDEs for development

By default, `./gradlew runIde` launches IntelliJ IDEA Community (IC) version 2024.3.6, as configured in `gradle.properties`.
You can switch the IDE (and version) at runtime via Gradle properties without changing files:

- IntelliJ IDEA Ultimate:
  ./gradlew runIde -PplatformType=IU -PplatformVersion=2024.3.6

- PhpStorm:
  ./gradlew runIde -PplatformType=PS -PplatformVersion=2024.3.4

Notes:
- The first run will download the selected IDE into Gradle caches; subsequent runs are faster.
- Ensure your plugin dependencies are compatible with the chosen IDE and version. If you need IDE-specific bundled plugins, set `platformBundledPlugins` in `gradle.properties` accordingly.
- Supported `platformType` codes are documented in `gradle.properties`.

[^6] https://

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
