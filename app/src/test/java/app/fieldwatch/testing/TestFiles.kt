package app.fieldwatch.testing

import app.fieldwatch.domain.LanguagePack
import app.fieldwatch.domain.LanguageParse
import java.io.File

/** Repo files from a unit test, whether Gradle runs it from `app/` or from the repo root. */
object TestFiles {
    /** [path] is relative to the app module, e.g. `src/main/assets/lang/de.json`. */
    fun repoFile(path: String): File =
        listOf(File(path), File("app/$path")).firstOrNull { it.exists() } ?: File(path)

    /** A bundled language pack's accepted lines, ready for `LanguageText.apply`. */
    fun bundledPack(tag: String): Map<String, String> {
        val parsed = LanguagePack.parse(repoFile("src/main/assets/lang/$tag.json").readBytes())
        check(parsed is LanguageParse.Ok) { "Bundled pack $tag doesn't parse: $parsed" }
        return parsed.preview.accepted
    }
}
