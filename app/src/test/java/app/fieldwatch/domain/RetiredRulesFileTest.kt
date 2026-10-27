package app.fieldwatch.domain

import app.fieldwatch.data.ConfigStore
import app.fieldwatch.testing.TestFiles
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `catalog/retired.json` (CLAUDE.md catalog checklist, step 5) lists stock rules a catalog version removed, so the
 * ladder and import can learn removal (P1-B6 wires that up). Each entry must be a rule today's stock row no longer
 * has, and that the snapshot just before its version still had.
 */
class RetiredRulesFileTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun everyRetiredRuleIsGoneFromStockAndWasInThePreviousSnapshot() {
        val root = json.parseToJsonElement(TestFiles.repoFile("../catalog/retired.json").readText(Charsets.UTF_8)).jsonObject
        assertEquals("spectre-retired-rules", root.getValue("format").jsonPrimitive.content)
        val entries = root.getValue("rules").jsonArray
        assertTrue(entries.isNotEmpty())
        val stock = DefaultCatalog.fleets().associateBy { it.id }
        // The app's copy (RetiredRules) lists exactly these.
        assertEquals(
            RetiredRules.ALL.map { Triple(it.fleetId, SignatureExchange.ruleKey(it.rule), it.retiredIn) }.toSet(),
            entries.map { it.jsonObject }.map { e ->
                Triple(
                    e.getValue("fleetId").jsonPrimitive.content,
                    SignatureExchange.ruleKey(json.decodeFromJsonElement(MatchRule.serializer(), e.getValue("rule"))),
                    e.getValue("retiredIn").jsonPrimitive.int,
                )
            }.toSet(),
        )
        for (entry in entries.map { it.jsonObject }) {
            val fleetId = entry.getValue("fleetId").jsonPrimitive.content
            val retiredIn = entry.getValue("retiredIn").jsonPrimitive.int
            val rule = json.decodeFromJsonElement(MatchRule.serializer(), entry.getValue("rule"))
            assertTrue("$fleetId: retiredIn $retiredIn is after the current catalog", retiredIn <= ConfigStore.CATALOG_VERSION)
            assertTrue("$fleetId: a retired rule names its step", entry.getValue("step").jsonPrimitive.content.matches(Regex("""P\d-[A-Z]?\d+[a-z]?""")))
            val key = SignatureExchange.ruleKey(rule)
            assertFalse("$fleetId still has the retired rule $key", stock.getValue(fleetId).rules.any { SignatureExchange.ruleKey(it) == key })
            val before = SignatureExchange.parse(TestFiles.repoFile("../catalog/snapshots/v${retiredIn - 1}.json").readText(Charsets.UTF_8))
            assertTrue(
                "$fleetId: v${retiredIn - 1} never had $key",
                before.fleets.single { it.id == fleetId }.rules.any { SignatureExchange.ruleKey(it) == key },
            )
        }
    }
}
