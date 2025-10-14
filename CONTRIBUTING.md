# Contributing to IntelliJ Platform Bubble Units Plugin

Thanks for your interest in contributing! This project follows a reliability-first improvement plan — please prioritize safety, tests, and documentation.

Guidelines overview:
- Keep scope minimal; prefer hardening to new features.
- Follow Kotlin coding conventions and keep classes small and focused.
- Use IntelliJ logging (thisLogger or the provided Logging helper) with structured context.
- Avoid blocking the EDT; run I/O on background threads and tie resources to Disposables.
- Centralize user-facing strings in MyBundle.properties.
- Place tests under `src/test/kotlin`, prefer `BasePlatformTestCase` for IDE-integrated tests.

How to build and test:
- JDK 21 required.
- Use the Gradle wrapper (`./gradlew`).
- Run all checks: `./gradlew build`.
- Run tests only: `./gradlew test`.
- Launch sandbox IDE: `./gradlew runIde`.

Coding standards and references:
- See `.junie/guidelines.md` for project-specific practices and commands.
- See `docs/plan.md` for the current improvement plan and constraints.
- See `docs/release-checklist.md` for the current release/marketplace checklist. The completed historical checklist is archived in `docs/tasks-archive-2025-09.md`.

Commit hygiene:
- Keep commits focused and descriptive.
- Reference the task you’re addressing (e.g., docs/tasks.md item).
- Include tests when changing behavior.

Security and safety:
- Validate inputs (e.g., URLs), avoid risky schemes.
- Keep sandbox/headless/JCEF-disabled scenarios in mind.

Thank you for helping make Bubble Units reliable and well-documented!
