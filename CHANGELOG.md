<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# intellij-platform-bubble-units-plugin Changelog

## [0.0.8] - 2025-10-19
### Added
- In-IDE Help page and bundled web assets pipeline (Vite) wired into Gradle; HTML exported to resources at build/run time.
- Export buttons become disabled after successful export, with clear visual indication (dimmed, grayscale, added checkmark) across IDE and standalone web builds.
- Consolidated export notifications with debounce and deduplication by file path.
- Robust JCEF bridge with primary JSQuery channel and a reliable hash-based fallback; early no-op stubs to prevent ReferenceErrors.
- Toolbar action to re-inject the bridge at runtime.
- Monochrome toolbar icons (Refresh, Web, Bolt) consistent with IDE tinting.

### Changed
- Plugin display name updated to "BubbleUnits Browser" for IDE and Marketplace.
- Tool window icon handling aligned with JetBrains best practices: icon declared in plugin.xml and stored under resources/icons; removed programmatic icon setting and icon mapper.
- Avoided forcing tool window placement; layout is user-controlled.
- Notification handler now appends snapshot directory only for export-related messages.
- Delegated click listener is the single source of truth for export buttons to prevent multiple handlers and duplicate exports.
- Extracted injected JS to a resource file (src/main/resources/js/bridge-inject.js) groundwork for readability; inline injection kept in sync.
- Detekt configuration tuned: disabled ktlint MaximumLineLength to reduce noise from long JS literals.

### Fixed
- Eliminated duplicate "BubbleUnits bridge ready" messages and doubled notifications by making injection idempotent and gating one-time notifications.
- Removed console errors about undefined bridge symbols by providing early, detectable stubs and dynamic bridge lookup per call.
- Ensured exports save reliably when JSQuery is unavailable: URL-encode Base64 in hash, add timestamp to force onAddressChange, and robust Kotlin-side parsing/decoding.
- Resolved cases of "no errors, but also no files" via stub-aware fallback installation and direct hash fallback inside sender.
- Consolidated notification no longer lists the same file multiple times; unique paths only.
- Gradle configuration cache error fixed by replacing ad-hoc doLast with a typed, cacheable MergeJUnitReportsTask using proper inputs/outputs.

### Security
- Navigation policy and external link handling to keep content on configured URLs and open external links in system browser.

### Build/CI
- Kover XML reports on check; Detekt integrated into check lifecycle with baseline; configuration cache enabled and working.
- Frontend (Node/Vite) build integrated into processResources, assemble, and runIde.

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
