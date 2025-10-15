<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# intellij-platform-bubble-units-plugin Changelog

## [0.0.7]
### Added
- Privacy statement in README (no telemetry; reads .env and junit XML locally)
- In-IDE Help page (resources/web/bubble-unit-help.html)
- Expanded documentation with clear URL precedence explained
- Screenshots

### Changed
- Improved README plugin description to be more concise and user-facing
- Enhanced CI workflow with build, tests, and coverage reporting
- Configured release workflow with secrets placeholders for deployment

### Security
- Navigation policy implemented to keep tool window on configured URLs
- External links now open in system browser for better security

## [0.0.6]
### Added
- CI workflow: build and tests on push/PR with Gradle cache (GitHub Actions)
- CONTRIBUTING.md with coding and testing guidelines
- Pre-release checklist (docs/RELEASE_CHECKLIST.md)
- Optional blob URL rendering path behind system property `-DbubbleUnits.useBlobUrl=true` (experimental)
- Development panel in Settings (enable/disable, directory chooser, HTML dropdown with bundled and discovered files); applying settings triggers immediate Tool Window reload via message bus
- Inline URL validation with error text, “Reset to default” button, and “Test URL” action; added context help link explaining URL precedence
- User-visible error page for data URL failures with root cause logged; theme-aware CSS variables exposed to HTML (text/bg/error/warning/success/info)
- Persist last-loaded content across IDE restarts where appropriate; toolbar actions: Reload and Open in Browser
- Additional i18n bundle keys and tests verifying message formatting and presence
- Unit/integration tests for settings URL precedence, .env parsing, file watcher events, and Tool Window behavior

### Changed
- Standardized logging messages (added helper with project context) and reduced string concatenation in hot paths
- URL precedence clarified and implemented: custom URL (non-blank) > dev panel selection > .env (BUBBLE_UNITS_URL or APP_URL) > default
- Data URL generation optimized with signature-based cache; very long data: URLs are elided in Settings field
- Migrated deprecated listeners: use BulkFileListener via MessageBus; removed ToolWindowManagerListener.stateChanged()
- JCEF usage hardened: guard browser creation for headless/disabled JCEF, ensure correct disposal, and prefer content reload over full browser rebuild
- Virtual file watching improved: monitor common JUnit report filenames and nested modules; debounce rapid changes (~750ms)

### Fixed
- Moved blocking I/O off the EDT; lazy-loaded expensive data to avoid UI stalls
- Prevented double-disposal of JCEF browser with thread-safe checks
- .env parsing robustness: supports comments, blank lines, and quoted values with escaping
- URL validation rejects invalid schemes (including javascript:), avoiding unsafe inputs

### Security
- Sanitize/size-limit junit-report.xml before embedding; strip or escape script tags when loading arbitrary HTML

### Documentation
- Added docs/architecture.md and docs/troubleshooting.md; updated README with setup, URL precedence, and test logging configuration

## [0.0.1]
### Added
- Kicked the tyres and set things up? 

## [Unreleased]
### Added
- Initial scaffold created from [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template)
