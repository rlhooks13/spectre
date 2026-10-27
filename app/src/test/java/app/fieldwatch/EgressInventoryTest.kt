package app.fieldwatch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Network traffic leaves the app from exactly these files, and each asks NetworkPolicy first (P1-A7).
 * A new egress point fails here until it is added to the list and to the policy.
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
        "openConnection\\(|\\bSocket\\(|DatagramSocket\\(|MulticastSocket\\(|SocketChannel|" +
            "getFromLocation\\(|WebView|DownloadManager|HttpClient|OkHttp|Retrofit",
    )

    @Test
    fun onlyTheListedFilesTouchTheNetwork() {
        val root = repoFile("src/main/java/app/fieldwatch")
        val found = root.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .filter { file -> file.readLines().any { line -> !line.trimStart().startsWith("//") && network.containsMatchIn(line) } }
            .map { it.relativeTo(root).invariantSeparatorsPath }
            .toSortedSet()
        assertEquals(egress.keys.toSortedSet(), found)
    }

    @Test
    fun eachEgressFileAsksThePolicy() {
        val root = repoFile("src/main/java/app/fieldwatch")
        for ((path, kind) in egress) {
            val text = File(root, path).readText()
            assertTrue(path, text.contains("NetworkPolicy.allows($kind)"))
        }
    }

    @Test
    fun noCallerHardCodesOnlineLookup() {
        val root = repoFile("src/main/java/app/fieldwatch")
        val hits = root.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .flatMap { file -> file.readLines().filter { "onlineLookup = true" in it }.map { "${file.name}: ${it.trim()}" } }
            .toList()
        assertEquals(emptyList<String>(), hits)
    }

    private fun repoFile(path: String): File {
        val candidates = listOf(File(path), File("app/$path"))
        return candidates.firstOrNull { it.exists() } ?: candidates.last()
    }
}
