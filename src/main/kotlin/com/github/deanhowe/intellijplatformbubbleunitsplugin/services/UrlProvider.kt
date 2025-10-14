package com.github.deanhowe.intellijplatformbubbleunitsplugin.services

/**
 * UrlProvider centralizes the decision logic for determining which URL/content
 * should be used by the Bubble Units tool window.
 *
 * Precedence (highest to lowest):
 * 1) Non-blank custom URL explicitly set by the user in settings.
 * 2) When the development panel is enabled, use the tool's generated/default content (data URL).
 * 3) Project .env-derived URL (e.g., BUBBLE_UNITS_URL, then APP_URL) when available.
 * 4) Fallback to the generated/default content (data URL).
 *
 * This class is intentionally stateless and purely functional to keep
 * BubbleSettingsService focused on persistence while keeping URL resolution
 * easy to test in isolation.
 */
public class UrlProvider {
    /**
     * Resolve the effective URL to use.
     *
     * @param customUrl user-provided URL from settings; wins if non-blank.
     * @param devPanelEnabled whether dev panel is enabled; if true, prefer default content.
     * @param envUrlProvider supplier for URL resolved from project .env files; may return null.
     * @param defaultUrlProvider supplier for the tool's default content (typically a data URL).
     */
    public fun resolve(
        customUrl: String?,
        devPanelEnabled: Boolean,
        envUrlProvider: () -> String?,
        defaultUrlProvider: () -> String,
    ): String {
        // 1) Explicit custom URL from settings always wins if non-blank
        if (!customUrl.isNullOrBlank()) return customUrl

        // 2) If development panel is enabled, force rendering of the selected/bundled HTML
        if (devPanelEnabled) return defaultUrlProvider()

        // 3) Otherwise, try .env URL
        val envUrl = envUrlProvider()
        if (!envUrl.isNullOrBlank()) return envUrl

        // 4) Fallback to default
        return defaultUrlProvider()
    }
}
