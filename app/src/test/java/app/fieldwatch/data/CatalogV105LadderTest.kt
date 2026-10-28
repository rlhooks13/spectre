package app.fieldwatch.data

import app.fieldwatch.domain.DefaultCatalog
import app.fieldwatch.domain.FleetDecode
import app.fieldwatch.domain.NetworkPolicy
import app.fieldwatch.testing.ConfigUpgrade
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * P1-B11a, catalog v105: the Find Hub and DULT stock decodes no longer say "often your own tag". A decode still
 * carrying the old stock sentence gets the new stock decode; an edited decode and the rows' notes are left alone.
 */
class CatalogV105LadderTest {
    @After
    fun forgetSettings() {
        NetworkPolicy.settings = null
    }

    private val oldNote = "Nearby its owner. With you the whole sit, this is often your own tag. First heard after the sit started, " +
        "it is more often someone who joined with their own keys."

    /** The v104 stock decode, as it was: the old sentence on the "not separated" value. */
    private fun asV104(decode: FleetDecode, key: String): FleetDecode = decode.copy(
        fields = decode.fields.filter { it.id != "eid32" }.map { f ->
            if (f.id != "mode") f else f.copy(enumNotes = f.enumNotes.orEmpty() + (key to oldNote))
        },
    )

    private fun stock(id: String) = DefaultCatalog.fleets().single { it.id == id }

    @Test
    fun anUneditedDecodeGetsTheNewStockDecodeAndNotesStay() {
        val old = ConfigUpgrade.fieldwatch1132().let { c ->
            c.copy(
                version = 104,
                fleets = c.fleets.map { f ->
                    when (f.id) {
                        "fleet-find-hub" -> f.copy(decode = asV104(stock(f.id).decode!!, "64"), notes = "my own note")
                        "fleet-dult" -> f.copy(decode = asV104(stock(f.id).decode!!, "1"))
                        else -> f
                    }
                },
            )
        }
        val fleets = ConfigUpgrade.load(old).fleets.associateBy { it.id }
        assertEquals(stock("fleet-find-hub").decode, fleets.getValue("fleet-find-hub").decode)
        assertEquals(stock("fleet-dult").decode, fleets.getValue("fleet-dult").decode)
        assertEquals("my own note", fleets.getValue("fleet-find-hub").notes)
    }

    /** A user's copy of the row (not built in) keeps its decode, with only the retired sentence swapped. */
    @Test
    fun aUserCopyLosesTheRetiredSentence() {
        val copy = stock("fleet-find-hub").copy(id = "user-hub", name = "My hub", builtIn = false, decode = asV104(stock("fleet-find-hub").decode!!, "64"))
        val old = ConfigUpgrade.fieldwatch1132().let { c -> c.copy(version = 104, fleets = c.fleets + copy) }
        val mode = ConfigUpgrade.load(old).fleets.single { it.id == "user-hub" }.decode!!.fields.single { it.id == "mode" }
        assertEquals(stock("fleet-find-hub").decode!!.fields.single { it.id == "mode" }.enumNotes!!["64"], mode.enumNotes!!["64"])
    }

    /** Importing an older pack can't bring the retired sentence back. */
    @Test
    fun anOlderPackDoesNotBringItBack() {
        val stockHub = stock("fleet-find-hub")
        val incoming = stockHub.copy(decode = asV104(stockHub.decode!!, "64"))
        val (merged, _) = app.fieldwatch.domain.SignatureExchange.merge(DefaultCatalog.fleets(), listOf(incoming))
        val note = merged.single { it.id == "fleet-find-hub" }.decode!!.fields.single { it.id == "mode" }.enumNotes!!["64"]!!
        assertFalse(note.contains("own tag"))
    }

    /** The row note's "Nearby vs separated frames" is replaced where it's still stock, and left where the user wrote over it. */
    @Test
    fun theRowNoteIsRewordedOnlyWhereStock() {
        val v104Note = stock("fleet-find-hub").notes.replace(
            "Frames show whether unwanted-tracking protection is on (separated) or not yet on.",
            "Nearby vs separated frames.",
        )
        val old = ConfigUpgrade.fieldwatch1132().let { c ->
            c.copy(version = 104, fleets = c.fleets.map { if (it.id == "fleet-find-hub") it.copy(notes = v104Note) else it })
        }
        assertEquals(stock("fleet-find-hub").notes, ConfigUpgrade.load(old).fleets.single { it.id == "fleet-find-hub" }.notes)
    }

    @Test
    fun anEditedDecodeIsKept() {
        val edited = stock("fleet-find-hub").decode!!.let { d ->
            d.copy(fields = d.fields.map { if (it.id == "mode") it.copy(label = "My mode") else it })
        }
        val old = ConfigUpgrade.fieldwatch1132().let { c ->
            c.copy(version = 104, fleets = c.fleets.map { if (it.id == "fleet-find-hub") it.copy(decode = edited) else it })
        }
        assertEquals(edited, ConfigUpgrade.load(old).fleets.single { it.id == "fleet-find-hub" }.decode)
    }
}
