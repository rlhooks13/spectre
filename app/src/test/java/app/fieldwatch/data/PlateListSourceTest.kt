package app.fieldwatch.data

import app.fieldwatch.testing.TestFiles.repoFile
import app.fieldwatch.ui.LayerToggle
import app.fieldwatch.ui.layerToggle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.URL

/**
 * P0-7c (ADR-0018): the plate-reader list is never downloaded. Ported from the earlier tests of its download dialog
 * (which named the source host) and its download guards, which the "Other sensors" download still uses.
 */
class PlateListSourceTest {
    @get:Rule
    val tmp = TemporaryFolder()

    /** No URL and no host, so the probe and the download fail before opening a connection. */
    @Test
    fun thePlateListHasNoSourceAndFailsClosed() {
        assertEquals("", SensorList.PLATES.url)
        assertEquals("", FlockFiles.sourceHost(SensorList.PLATES))
        assertEquals("", FlockFiles.sourceHost(SensorList.OTHER))
        val dir = tmp.newFolder()
        assertEquals(FlockFiles.Probe.FAILED, FlockFiles.probe(dir, SensorList.PLATES))
        assertEquals(FlockFiles.Fetch.Failed, FlockFiles.download(dir, SensorList.PLATES) {})
        assertTrue(!FlockFiles.servedFromSource(URL("https://example.org/x"), SensorList.PLATES))
    }

    /** Turning the plates layer on never asks for a file, the network, or a download: it just turns on. */
    @Test
    fun thePlatesSwitchNeverDownloads() {
        val asked = mutableListOf<String>()
        for (online in listOf(true, false)) {
            for (hasFile in listOf(true, false)) {
                val action = layerToggle(
                    SensorList.PLATES,
                    on = true,
                    available = true,
                    hasFile = { asked += "file"; hasFile },
                    online = { asked += "network"; online },
                )
                assertEquals(LayerToggle.ON, action)
            }
        }
        assertEquals(emptyList<String>(), asked)
        assertEquals(LayerToggle.OFF, layerToggle(SensorList.PLATES, false, true, { true }, { true }))
        // Hidden until P0-7e: turning it on does nothing.
        assertEquals(LayerToggle.NONE, layerToggle(SensorList.PLATES, true, false, { true }, { true }))
        // "Other sensors" still asks before it downloads.
        assertEquals(LayerToggle.CONFIRM_DOWNLOAD, layerToggle(SensorList.OTHER, true, true, { false }, { true }))
        assertEquals(LayerToggle.NEEDS_NETWORK, layerToggle(SensorList.OTHER, true, true, { false }, { false }))
        assertEquals(LayerToggle.ON, layerToggle(SensorList.OTHER, true, true, { true }, { false }))
    }

    /** "Never contact dontgetflocked": no app source, resource, R8 rule or build script names it. */
    @Test
    fun noAppSourceNamesTheOldHost() {
        val main = repoFile("src/main")
        assertTrue("found the app module's sources", main.isDirectory)
        val binary = setOf("png", "webp", "jpg", "gz", "bin", "ttf", "otf", "jar", "so", "keystore")
        val files = main.walkTopDown().filter { it.isFile && it.extension.lowercase() !in binary } +
            sequenceOf(repoFile("proguard-rules.pro"), repoFile("build.gradle.kts")).filter { it.isFile }
        val hits = files.filter { it.readText(Charsets.UTF_8).contains("dontgetflocked", ignoreCase = true) }.map { it.path }.toList()
        assertEquals(emptyList<String>(), hits)
    }

    /** The host guard the "Other sensors" download keeps: the named host or a subdomain, over HTTPS only. */
    @Test
    fun onlyTheNamedHostOverHttpsCountsAsTheSource() {
        val source = "data.example.org"
        listOf("https://data.example.org/list.geojson.gz", "https://cdn.data.example.org/x")
            .forEach { assertTrue(it, FlockFiles.servedFromHost(URL(it), source)) }
        listOf(
            "https://evil.example/list.geojson.gz",
            "https://data.example.org.evil.example/x",
            "http://data.example.org/list.geojson.gz",
        ).forEach { assertTrue(it, !FlockFiles.servedFromHost(URL(it), source)) }
        assertTrue(!FlockFiles.servedFromHost(URL("https://data.example.org/x"), ""))
    }

    /** The download stops reading past a cap that still fits a real list. */
    @Test
    fun theDownloadIsCapped() {
        assertTrue(FlockFiles.MAX_BYTES >= SensorList.OTHER.expectedBytes)
        assertTrue(FlockFiles.MAX_BYTES <= 100L * 1024L * 1024L)
    }

    /** A list an older version downloaded, and its temp files, are deleted at start; a second run finds nothing. */
    @Test
    fun anOldDownloadIsDeleted() {
        val dir = tmp.newFolder()
        val name = SensorList.PLATES.fileName
        listOf(name, "$name.write", "$name.download", SensorList.OTHER.fileName).forEach { File(dir, it).writeText("x") }
        assertEquals(3, FlockFiles.retirePlatesDownload(dir))
        assertEquals(0, FlockFiles.retirePlatesDownload(dir))
        assertTrue("Other sensors is left alone", File(dir, SensorList.OTHER.fileName).exists())
    }
}
