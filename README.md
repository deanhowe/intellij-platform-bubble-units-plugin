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

This plugin renders a webview of your choice in a dedicated panel.

No navigation, not a mini browser, just one URL rendered (and fully functional).

The URL can be configured in three ways:
1. Via the plugin preferences
2. Via a `BUBBLE_UNITS_URL` variable in a `.env` file in your project directory
3. Default fallback is to the `APP_URL` or if a `junit-report.xml` file is found in the root of the project a BubbleUnits graph is displayed. 

When showing the BubbleUnits graph
The text content of the window/panel is editable – click on it and type away!

At the moment clicking on a bubble 

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