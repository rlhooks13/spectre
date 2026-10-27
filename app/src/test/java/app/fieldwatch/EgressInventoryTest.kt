package app.fieldwatch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Network traffic leaves the app from exactly these files, and each asks NetworkPolicy first (P1-A7,
 * ADR-0014). A new egress point fails here until it gets an Egress kind and an entry below.
 */
class EgressInventoryTest {
    private val egress = mapOf(
        "data/PathTiles.kt" to "Egress.MAP_TILES",
        "data/PlaceLookup.kt" to "Egress.PLACE_NAMES",
        "data/CatalogRemote.kt" to "Egress.CATALOG_UPDATE",
        "data/FlockFiles.kt" to "Egress.SENSOR_LIST",
        "radio/TakPublisher.kt" to "Egress.TAK_PUBLISH",
    )

    private val network = Regex(
        listOf(
            "openConnection\\s*\\(", "openStream\\s*\\(", "URL\\([^)]*\\)\\s*\\.\\s*read(Text|Bytes)\\s*\\(",
            "\\b(Server)?Socket\\s*\\(", "DatagramSocket\\s*\\(", "MulticastSocket\\s*\\(", "createSocket\\s*\\(",
            "SocketChannel", "InetAddress\\s*\\.\\s*get(All)?ByName", "getFromLocation\\w*\\s*\\(",
            "WebView", "DownloadManager", "HttpClient", "OkHttp", "Retrofit",
        ).joinToString("|"),
    )

    /** Every non-test source set (main, debug, staging, release, ...), Kotlin and Java. */
    private fun sources(): List<Pair<String, File>> {
        val src = repoFile("src")
        return src.listFiles().orEmpty()
            .filter { it.isDirectory && !it.name.startsWith("test") && !it.name.startsWith("androidTest") }
            .flatMap { set ->
                set.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
                    .map { file ->
                        val path = file.relativeTo(set).invariantSeparatorsPath
                            .removePrefix("java/app/fieldwatch/").removePrefix("kotlin/app/fieldwatch/")
                        path to file
                    }
            }
    }

    private fun isComment(line: String): Boolean = line.trimStart().let { it.startsWith("//") || it.startsWith("*") || it.startsWith("/*") }

    @Test
    fun onlyTheListedFilesTouchTheNetwork() {
        val found = sources()
            .filter { (_, file) -> file.readLines().any { line -> !isComment(line) && network.containsMatchIn(line) } }
            .map { it.first }
            .toSortedSet()
        assertEquals(egress.keys.toSortedSet(), found)
    }

    @Test
    fun eachEgressFileAsksThePolicy() {
        val byPath = sources().toMap()
        for ((path, kind) in egress) {
            val code = byPath.getValue(path).readLines().filterNot(::isComment).joinToString("\n")
            assertTrue(path, code.contains("NetworkPolicy.allows("))
            assertTrue(path, code.contains(kind))
        }
    }

    @Test
    fun noCallerHardCodesOnlineLookup() {
        val hits = sources().flatMap { (path, file) ->
            file.readLines().filter { "onlineLookup = true" in it }.map { "$path: ${it.trim()}" }
        }
        assertEquals(emptyList<String>(), hits)
    }

    private fun repoFile(path: String): File {
        val candidates = listOf(File(path), File("app/$path"))
        return candidates.firstOrNull { it.exists() } ?: candidates.last()
    }
}
