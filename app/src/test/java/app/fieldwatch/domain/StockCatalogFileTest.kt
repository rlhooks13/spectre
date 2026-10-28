package app.fieldwatch.domain

import app.fieldwatch.data.ConfigStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StockCatalogFileTest {
    @Test
    fun distPackMatchesDefaultCatalog() {
        val dest = distPackFile()
        val encoded = SignatureExchange.encode(
            SignatureExchange.pack(
                fleets = DefaultCatalog.fleets(),
                catalogVersion = ConfigStore.CATALOG_VERSION,
                appVersion = "stock",
                exportedAt = "",
            ),
        )
        if (writing()) {
            dest.parentFile?.mkdirs()
            dest.writeText(encoded)
        }
        assertTrue("Missing ${dest.absolutePath}. Run tests with WRITE_STOCK_CATALOG=1.", dest.isFile)
        val pack = SignatureExchange.parse(dest.readText())
        assertEquals("fieldwatch-signatures", pack.format)
        assertEquals(ConfigStore.CATALOG_VERSION, pack.catalogVersion)
        val stock = DefaultCatalog.fleets().associateBy { it.id }
        assertEquals(stock.keys, pack.fleets.map { it.id }.toSet())
        for (row in pack.fleets) {
            val expect = stock.getValue(row.id)
            assertEquals(row.id, expect.name, row.name)
            assertEquals(row.id, expect.kind, row.kind)
            assertEquals(row.id, expect.attentionNote, row.attentionNote)
            assertEquals(row.id, expect.notes, row.notes)
            assertEquals(row.id, expect.matchAny, row.matchAny)
            assertTrue(row.builtIn)
        }
        val ray = pack.fleets.single { it.id == "fleet-rayneo" }
        assertFalse(ray.matchAny)
        assertEquals(0x0BC6, ray.rules.single { it.kind == RuleKind.MANUFACTURER_ID }.companyId)
        assertEquals("RayNeo*", ray.rules.single { it.kind == RuleKind.NAME_GLOB }.text)
        assertEquals(2, ray.rules.size)
        val even = pack.fleets.single { it.id == "fleet-even-g1" }
        assertTrue(even.rules.any { it.kind == RuleKind.MANUFACTURER_ID && it.companyId == 0x10F9 })
        val lite = pack.fleets.single { it.id == "fleet-liteon-camera-radio" }
        val ouis = lite.rules.filter { it.kind == RuleKind.OUI }.map { it.text.uppercase() }.toSet()
        assertTrue(ouis.contains("E0:0A:F6"))
        assertTrue(ouis.contains("14:B5:CD"))
        assertTrue(ouis.contains("F8:A2:D6"))
        assertFalse(ouis.contains("48:27:EA"))
        assertFalse(ouis.contains("82:6B:F2"))
        assertTrue(lite.attentionNote.isBlank())
        val meta = pack.fleets.single { it.id == "fleet-meta-glasses" }
        assertTrue(meta.rules.any { it.kind == RuleKind.NAME_GLOB && it.text == "Meta RB Display*" })
        assertFalse(meta.rules.any { it.text.equals("FD5F", true) })
        assertTrue(pack.fleets.single { it.id == "fleet-axon" }.notes.contains("fixed ALPR"))
        val remote = pack.fleets.single { it.id == "fleet-remote-id" }
        assertTrue(remote.rules.any { it.kind == RuleKind.VENDOR_IE_OUI && it.text.equals("6A:5C:35", true) })
        assertTrue(remote.rules.any { it.kind == RuleKind.VENDOR_IE_OUI && it.text.equals("FA:0B:BC", true) })
        val tello = pack.fleets.single { it.id == "fleet-tello" }
        assertEquals(
            setOf("TELLO*", "RMTT*"),
            tello.rules.map { it.text }.toSet(),
        )
        val crazy = pack.fleets.single { it.id == "fleet-crazyflie" }
        assertFalse(crazy.rules.any { it.kind == RuleKind.MANUFACTURER_ID })
        assertTrue(pack.fleets.single { it.id == "fleet-parrot" }.rules.any {
            it.kind == RuleKind.NAME_GLOB && it.text == "Skycontroller*"
        })
    }

    /** Set by the build for testDebugUnitTest only (`WRITE_STOCK_CATALOG=1` or `-PwriteStockCatalog`), so variants can't race. */
    private fun writing(): Boolean = System.getProperty("spectre.writeStockCatalog") == "true"

    /**
     * One stock snapshot per catalog version from v97, the first (`catalog/snapshots/v<N>.json`, PLAN key decisions),
     * for the importer's three-way diff and the release notes. The current version's is today's stock catalog, byte
     * for byte; it's written with the dist pack. Older ones are frozen: [FROZEN] pins their hashes, and a catalog bump
     * adds the outgoing version's hash there.
     */
    @Test
    fun snapshotForEveryVersionAndCurrentMatchesStock() {
        val dir = app.fieldwatch.testing.TestFiles.repoFile("../catalog/snapshots")
        val current = File(dir, "v${ConfigStore.CATALOG_VERSION}.json")
        val encoded = SignatureExchange.encode(
            SignatureExchange.pack(DefaultCatalog.fleets(), ConfigStore.CATALOG_VERSION, appVersion = "stock", exportedAt = ""),
        )
        if (writing()) {
            dir.mkdirs()
            current.writeText(encoded)
        }
        assertTrue("Missing ${current.path}. Run tests with WRITE_STOCK_CATALOG=1.", current.isFile)
        assertEquals("The v${ConfigStore.CATALOG_VERSION} snapshot isn't today's stock catalog", encoded, current.readText())
        for (n in FIRST_SNAPSHOT..ConfigStore.CATALOG_VERSION) {
            val file = File(dir, "v$n.json")
            assertTrue("Missing snapshot ${file.name}", file.isFile)
            assertEquals(file.name, n, SignatureExchange.parse(file.readText()).catalogVersion)
        }
        val extra = dir.listFiles().orEmpty().map { it.name } - (FIRST_SNAPSHOT..ConfigStore.CATALOG_VERSION).map { "v$it.json" }.toSet()
        assertEquals("Unexpected files in catalog/snapshots", emptyList<String>(), extra.sorted())
        for ((n, sha) in FROZEN) {
            val bytes = File(dir, "v$n.json").readBytes()
            val actual = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            assertEquals("Snapshot v$n changed; snapshots are frozen once their version ships", sha, actual)
        }
        assertEquals("Every version below the current one is frozen", (FIRST_SNAPSHOT until ConfigStore.CATALOG_VERSION).toSet(), FROZEN.keys)
    }

    private companion object {
        const val FIRST_SNAPSHOT = 97
        val FROZEN = mapOf(
            97 to "dd932a77b805e9e04cfac29c1120544fed5e4ae7e4443280c432a0db90a78ac3",
            98 to "23e200b04b8e5aea0b52b537ce64f5e63c3ab04ae3976c3fc2f254a8d8f18944",
            99 to "b78b7ed45c7ab64878662a9bd5250d5b78d2d34507673c53433d0a2c80582811",
            100 to "e513de558a6d9961c360af3138f187fcd024f6baa0c3f6ecbf92e056b0ec96b3",
            101 to "74a3d664dcc088325447951ab0ccf4d888fbc8be23f3842cf0481747e2658ab4",
            102 to "b869c8bd9c98bfbc68c7c88f35ed28228fdb46d7382cc86e310d95d5e96c6784",
            103 to "ac51b32c4e00b883e8a63fbd44e0d0d6ff31ed925996e82389b5200980a6d7ec",
            104 to "5fe002cb1c8171daec1256ae69f4e517072e25a84f7a0dc0a97047b06d375c91",
            105 to "771f66e76642ffbde832266d98d268e85d04b8fbdeffba05f60308dae10e11ed",
            106 to "52262b3200d9c3d0364736c5ab44991a1b88877ac748a3a84a3759bfd5ca1ab9",
            107 to "30bb497ea928e43462b4af10b0e287bc76a00712d0f51c32ed219ce685f5f006",
            108 to "0e40a49a968f0af154a0b1da0913bf0c9fa2d12e055a950064d6e854c7cb4cb8",
            109 to "913c57555e7c8a537c7d4a46d1b01c698c70897564353cf067fa5d9efd43e611",
            110 to "3c2f289b56901469d2f25b8b266ad0a8e11d5fb102639c85326c8eec3eb2960d",
            111 to "da0ce111e72a0f83daeed0e88fc0bc072c0a94403c392ee99e309fab97100cee",
            112 to "dbee7da1df9c6e542d792cc36deb498f4a229e9ff88fa118c2db395eba99626b",
            113 to "88aff689187d55c2ce685709efebb4cffba258e30d73e46868b44e0836c0734c",
        )
    }

    private fun distPackFile(): File {
        val cwd = File(System.getProperty("user.dir")!!)
        val candidates = listOf(
            File(cwd, "dist/fieldwatch-signatures-v2.json"),
            File(cwd.parentFile, "dist/fieldwatch-signatures-v2.json"),
        )
        return candidates.first { it.parentFile?.exists() == true }
    }

    @Test
    fun remoteIdPinFieldsMatchWhenLiveKeysAreDropped() {
        val raw = distPackFile().readText()
        val full = SignatureExchange.parse(raw).fleets.single { it.id == "fleet-remote-id" }
        val older = SignatureExchange.parse(dropLiveKeys(raw)).fleets.single { it.id == "fleet-remote-id" }
        assertEquals(
            full.decode!!.fields.map { it.copy(live = false, liveEmphasis = emptyList(), enumNotes = null) },
            older.decode!!.fields,
        )
        val location = "0D0012200000000084D717007FE4D3000098083408000000000000"
        val ble = ridBle(location)
        val pinned = PayloadLocation.applySticky(ble.copy(fleetIds = setOf(full.id)), listOf(full))
        assertEquals(40.0, pinned.payloadLat!!, 1e-6)
        assertEquals(-74.0, pinned.payloadLon!!, 1e-6)
        assertEquals(0.0, pinned.payloadHeading!!, 1e-6)
        assertEquals(0.0, pinned.payloadSpeed!!, 1e-6)
        val fullRows = SignatureFieldDecoder.decodeSighting(ble.copy(fleetIds = setOf(full.id)), listOf(full))
        val oldRows = SignatureFieldDecoder.decodeSighting(ble.copy(fleetIds = setOf(older.id)), listOf(older))
        for (id in listOf("latitude", "longitude", "heading", "hspeed", "alt_geo", "status")) {
            assertEquals(id, row(fullRows, id).display, row(oldRows, id).display)
            assertEquals(id, row(fullRows, id).number, row(oldRows, id).number)
        }
        val pack = "D9F2190302123135383146335954444A3144303033315A353330000000" +
            "1220820A00864228110CFF80CF0000F508B2083A022E310A00" +
            "420176E42711B5FF81CF010000000000000005088B02900E00"
        val wifiPin = PayloadLocation.applySticky(ridWifi(pack).copy(fleetIds = setOf(full.id)), listOf(full))
        assertEquals("1581F3YTDJ1D0031Z530", wifiPin.payloadUasId)
        assertEquals(28.7851142, wifiPin.payloadLat!!, 1e-6)
        assertEquals(-81.3629684, wifiPin.payloadLon!!, 1e-6)
        assertEquals(130.0, wifiPin.payloadHeading!!, 1e-6)
        assertEquals(2.5, wifiPin.payloadSpeed!!, 1e-6)
        assertEquals(28.7827062, wifiPin.payloadOpLat!!, 1e-6)
        assertEquals(-81.3563979, wifiPin.payloadOpLon!!, 1e-6)
        assertEquals(OperatorLoc.LIVE, wifiPin.payloadOpKind)
        assertEquals(26.5, wifiPin.payloadOpAlt!!, 1e-6)
    }

    private fun row(rows: List<DecodedFieldValue>, id: String) = rows.first { it.id == id }

    private fun dropLiveKeys(raw: String): String {
        fun strip(el: JsonElement): JsonElement = when (el) {
            is JsonObject -> JsonObject(
                el.filterKeys { it != "live" && it != "liveEmphasis" && it != "enumNotes" }
                    .mapValues { (_, value) -> strip(value) },
            )
            is JsonArray -> JsonArray(el.map { strip(it) })
            else -> el
        }
        return strip(Json.parseToJsonElement(raw)).toString()
    }

    private fun ridBle(dataHex: String) = Sighting(
        key = "BLE:AA:BB:CC:DD:EE:01",
        kind = RadioKind.BLE,
        mac = "AA:BB:CC:DD:EE:01",
        name = "",
        rssi = -50,
        rssiMin = -50,
        rssiMax = -50,
        channel = 0,
        frequencyMhz = 2402,
        vendor = null,
        randomized = true,
        hiddenSsid = false,
        serviceUuids = listOf("FFFA"),
        manufacturerId = null,
        manufacturerDataHex = "",
        rawHex = "",
        extras = "",
        firstSeen = 1L,
        lastSeen = 1L,
        hitCount = 1,
        fleetIds = setOf("fleet-remote-id"),
        rssiHistory = emptyList(),
        presence = emptyList(),
        facts = RadioFacts(serviceData = listOf(ServiceDataRecord("FFFA", dataHex))),
    )

    private fun ridWifi(dataHex: String) = Sighting(
        key = "WIFI:60:60:1F:06:31:08",
        kind = RadioKind.WIFI,
        mac = "60:60:1F:06:31:08",
        name = "RID",
        rssi = -80,
        rssiMin = -80,
        rssiMax = -80,
        channel = 6,
        frequencyMhz = 2437,
        vendor = null,
        randomized = false,
        hiddenSsid = false,
        serviceUuids = emptyList(),
        manufacturerId = null,
        manufacturerDataHex = "",
        rawHex = "",
        extras = "",
        firstSeen = 1L,
        lastSeen = 1L,
        hitCount = 1,
        fleetIds = setOf("fleet-remote-id"),
        rssiHistory = emptyList(),
        presence = emptyList(),
        facts = RadioFacts(vendorIes = listOf(VendorIeRecord("FA:0B:BC", 0x0D, dataHex))),
    )

    @Test
    fun legacyPackStaysOnCatalog77() {
        val cwd = File(System.getProperty("user.dir")!!)
        val legacy = listOf(
            File(cwd, "dist/fieldwatch-signatures.json"),
            File(cwd.parentFile, "dist/fieldwatch-signatures.json"),
        ).first { it.parentFile?.exists() == true }
        assertTrue("Keep dist/fieldwatch-signatures.json for 1.1.11 GitHub updates.", legacy.isFile)
        val pack = SignatureExchange.parse(legacy.readText())
        assertEquals(77, pack.catalogVersion)
        assertFalse(pack.fleets.any { fleet ->
            fleet.rules.any { it.kind == RuleKind.SERVICE_DATA }
        })
    }
}
