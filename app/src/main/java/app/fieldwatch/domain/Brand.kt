package app.fieldwatch.domain

/**
 * The app's display identity, in one place. Stable identifiers are not here on purpose: format
 * ids (`fieldwatch-settings`, …), TAK UIDs (`FIELDWATCH-…`), notification channels and internal
 * log file names keep their legacy values so old files, feeds and settings still match.
 */
object Brand {
    const val NAME = "Spectre"
    const val NAME_UPPER = "SPECTRE"

    /** Prefix for files the user exports or shares. */
    const val FILE_PREFIX = "spectre"

    const val REPO_URL = "https://github.com/rlhooks13/spectre"

    /** Prefix for Logcat tags. */
    const val LOG_TAG = "Spectre"

    /** HTTP User-Agent for the few network calls the app makes. */
    /** Drops SemVer build metadata (a dev build's commit), so servers see only the version. */
    fun userAgent(version: String): String = "$NAME/${version.substringBefore('+')} (+$REPO_URL)"
}
