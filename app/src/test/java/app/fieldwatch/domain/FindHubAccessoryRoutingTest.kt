package app.fieldwatch.domain

import app.fieldwatch.testing.Sightings.sighting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * P1-B11b: SignatureEngine.routeFindHubAccessory, one test per branch. A Find Hub 0x40 frame reads as headphones or a
 * phone only with an identifying audio or phone product match; anything weaker keeps it a tag (a missed tracker is worse).
 */
class FindHubAccessoryRoutingTest {
    private val stock = DefaultCatalog.fleets()
    private val hub = stock.single { it.id == SignatureEngine.FIND_HUB }
    private val accessory = stock.single { it.id == SignatureEngine.FIND_HUB_ACCESSORY }
    private val fastPair = stock.single { it.id == "fleet-fast-pair" }

    private fun frame(f: String) = listOf(ServiceDataRecord("FEAA", f + "11".repeat(20) + "00"))

    private fun hits(s: Sighting, fleets: List<Fleet>): Set<String> = SignatureEngine().match(listOf(s), fleets).getValue(s.key)

    private fun custom(id: String, kind: SignatureClass, rule: MatchRule) =
        Fleet(id = id, name = id, enabled = true, colorIndex = 0, kind = kind, matchAny = true, builtIn = false, rules = listOf(rule))

    private val headphones = custom("my-headphones", SignatureClass.AUDIO, MatchRule(RuleKind.NAME_GLOB, text = "WH-1000*"))

    @Test
    fun aProductAudioMatchMakesItAnAccessoryWithOrWithoutFlags() {
        val s = sighting("A", name = "WH-1000XM5", serviceData = frame("40"))
        assertEquals(setOf(SignatureEngine.FIND_HUB_ACCESSORY, "my-headphones"), hits(s, listOf(hub, accessory, headphones)))
        val flagged = s.copy(key = "B", facts = s.facts.copy(advFlags = 0x06))
        assertEquals(setOf(SignatureEngine.FIND_HUB_ACCESSORY, "my-headphones"), hits(flagged, listOf(hub, accessory, headphones)))
    }

    @Test
    fun withoutAProductMatchItStaysATag() {
        for ((key, flags) in listOf("N" to null, "L" to 0x06, "D" to 0x02, "Z" to 0x00, "C" to 0x04)) {
            val s = sighting(key, serviceData = frame("40")).let { it.copy(facts = it.facts.copy(advFlags = flags)) }
            assertEquals("flags $flags", setOf(SignatureEngine.FIND_HUB), hits(s, listOf(hub, accessory)))
        }
    }

    /** Fast Pair (a protocol Find Hub tags speak too) and a vendor-prefix-only hit are no headphone match. */
    @Test
    fun weakCoLabelsKeepItATag() {
        val tagWithFastPair = sighting("F", serviceData = frame("40") + ServiceDataRecord("FE2C", "00112233"))
        assertEquals(setOf(SignatureEngine.FIND_HUB, "fleet-fast-pair"), hits(tagWithFastPair, listOf(hub, accessory, fastPair)))
        val ouiAudio = custom("oui-audio", SignatureClass.AUDIO, MatchRule(RuleKind.OUI, text = "48:62:64"))
        val randomAddress = sighting("O", mac = "48:62:64:4A:1B:2C", serviceData = frame("40"))
        assertEquals(setOf(SignatureEngine.FIND_HUB, "oui-audio"), hits(randomAddress, listOf(hub, accessory, ouiAudio)))
    }

    /** Another finder row on the same radio keeps it a tag, whatever else matched. */
    @Test
    fun anotherFinderKeepsItATag() {
        val chipolo = custom("my-chipolo", SignatureClass.FINDER, MatchRule(RuleKind.NAME_GLOB, text = "Chipolo*"))
        val s = sighting("C", name = "Chipolo ONE WH-1000", serviceData = frame("40"))
        val both = custom("my-headphones", SignatureClass.AUDIO, MatchRule(RuleKind.NAME_GLOB, text = "*WH-1000*"))
        assertEquals(setOf(SignatureEngine.FIND_HUB, "my-chipolo", "my-headphones"), hits(s, listOf(hub, accessory, chipolo, both)))
    }

    /** Separated (0x41) is always a tag: the accessory row doesn't match it. */
    @Test
    fun separatedIsAlwaysATag() {
        val s = sighting("S", name = "WH-1000XM5", serviceData = frame("41"))
        assertEquals(setOf(SignatureEngine.FIND_HUB, "my-headphones"), hits(s, listOf(hub, accessory, headphones)))
    }

    /** With the tag row deleted, a tag is left unlabeled, never called headphones; with the accessory row gone, a tag. */
    @Test
    fun aMissingRowNeverMakesATagHeadphones() {
        val s = sighting("M", serviceData = frame("40"))
        assertEquals(emptySet<String>(), hits(s, listOf(accessory)))
        assertEquals(setOf(SignatureEngine.FIND_HUB), hits(s, listOf(hub)))
        assertFalse(SignatureEngine.FIND_HUB_ACCESSORY in hits(s, listOf(accessory)))
    }

    /** The headline for routed headphones says headphones, not a Find Hub tag. */
    @Test
    fun theGuessForAnAccessoryIsNotATag() {
        val s = sighting("G", name = "WH-1000XM5", serviceData = frame("40"))
        val guess = DeviceExplain.guess(s, listOf(accessory.name, "Sony"))
        assertFalse(guess.headline, guess.headline.contains("Find Hub tag"))
    }
}
