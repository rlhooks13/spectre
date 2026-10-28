package app.fieldwatch.data

import android.content.Context
import app.fieldwatch.domain.AppSettings
import app.fieldwatch.domain.CatalogDecodes
import app.fieldwatch.domain.NetworkPolicy
import app.fieldwatch.domain.withFeatureGates
import app.fieldwatch.domain.DefaultCatalog
import app.fieldwatch.domain.FilterEngine
import app.fieldwatch.domain.LIVE_TOUR_REV
import app.fieldwatch.domain.offerLiveTourOnce
import app.fieldwatch.domain.FilterPreset
import app.fieldwatch.domain.FilterState
import app.fieldwatch.domain.Fleet
import app.fieldwatch.domain.MatchRule
import app.fieldwatch.domain.PersistedConfig
import app.fieldwatch.domain.RadioKind
import app.fieldwatch.domain.RetiredRules
import app.fieldwatch.domain.matchKey
import app.fieldwatch.domain.withoutDuplicates
import app.fieldwatch.domain.RuleKind
import app.fieldwatch.domain.SignatureCandidates
import app.fieldwatch.domain.SignatureClass
import app.fieldwatch.domain.SettingsExchange
import app.fieldwatch.domain.SettingsImportResult
import app.fieldwatch.domain.SettingsPack
import app.fieldwatch.domain.SignatureExchange
import app.fieldwatch.domain.SignatureImportResult
import app.fieldwatch.domain.SignaturePack
import app.fieldwatch.domain.StockCatalogUpdateResult
import app.fieldwatch.domain.TakInstall
import app.fieldwatch.domain.WatchTarget
import app.fieldwatch.domain.withOverlayBasemaps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class ConfigStore internal constructor(private val file: File) {
    constructor(context: Context) : this(File(context.filesDir, "config.json"))

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val mutex = Mutex()
    private val _config = MutableStateFlow(seed())
    val config: StateFlow<PersistedConfig> = _config.asStateFlow()

    /** Every config change goes through here, so NetworkPolicy always checks the settings in force. */
    private fun publish(next: PersistedConfig) {
        // Policy first: anything that reacts to the new config must find the policy already updated.
        NetworkPolicy.settings = next.settings
        _config.value = next
    }

    val fleets: List<Fleet> get() = _config.value.fleets
    val filter: FilterState get() = _config.value.filter
    val settings: AppSettings get() = _config.value.settings
    val takInstallTag: String get() = _config.value.takInstallTag
    val presets: List<FilterPreset> get() = _config.value.presets
    val watchlist: List<WatchTarget> get() = _config.value.watchlist

    suspend fun load() = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (!file.exists()) {
                val seeded = seed()
                file.writeText(json.encodeToString(seeded))
                publish(seeded)
                return@withContext
            }
            runCatching {
                json.decodeFromString<PersistedConfig>(file.readText())
            }.onSuccess { loaded ->
                if (loaded.fleets.isEmpty()) {
                    val seeded = seed()
                    file.writeText(json.encodeToString(seeded))
                    publish(seeded)
                } else {
                    val patched = TakInstall.ensure(patchBuiltIn(loaded)).let {
                        it.copy(settings = it.settings.offerLiveTourOnce().withOverlayBasemaps().withFeatureGates())
                    }
                    publish(patched)
                    if (patched != loaded) {
                        file.writeText(json.encodeToString(patched))
                    }
                }
            }.onFailure {
                val seeded = seed()
                file.writeText(json.encodeToString(seeded))
                publish(seeded)
            }
        }
    }

    suspend fun update(transform: (PersistedConfig) -> PersistedConfig) = mutex.withLock {
        val next = transform(_config.value).let { it.copy(settings = it.settings.withFeatureGates()) }
        publish(next)
        withContext(Dispatchers.IO) {
            val tmp = File(file.parentFile, "config.tmp")
            tmp.writeText(json.encodeToString(next))
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        }
    }

    suspend fun restoreDefaults() {
        val cur = _config.value
        val accepted = cur.settings.disclaimerAccepted
        val rev = cur.settings.disclaimerRev
        val tag = cur.takInstallTag
        val retired = cur.takLegacySelfRetired
        update {
            val fresh = seed()
            fresh.copy(
                settings = fresh.settings.copy(
                    disclaimerAccepted = accepted,
                    disclaimerRev = rev,
                ),
                takInstallTag = TakInstall.clean(tag).ifEmpty { fresh.takInstallTag },
                takLegacySelfRetired = retired,
            )
        }
    }

    suspend fun importFleets(incoming: List<Fleet>): SignatureImportResult = mutex.withLock {
        val (merged, result) = SignatureExchange.merge(_config.value.fleets, incoming)
        if (result.error != null || (result.added == 0 && result.merged == 0)) {
            return@withLock result
        }
        val next = _config.value.copy(fleets = merged)
        publish(next)
        withContext(Dispatchers.IO) {
            val tmp = File(file.parentFile, "config.tmp")
            tmp.writeText(json.encodeToString(next))
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        }
        result
    }

    suspend fun importSettings(pack: SettingsPack): SettingsImportResult {
        var result = SettingsImportResult()
        update { local ->
            val (next, applied) = SettingsExchange.apply(local, pack)
            result = applied
            if (applied.error != null) local else next
        }
        return result
    }

    suspend fun overlayStockCatalog(pack: SignaturePack): StockCatalogUpdateResult = mutex.withLock {
        if (pack.catalogVersion <= 0) {
            return@withLock StockCatalogUpdateResult(error = "This pack has no catalog version.")
        }
        val local = _config.value
        if (pack.catalogVersion <= local.version) {
            return@withLock StockCatalogUpdateResult(
                alreadyLatest = true,
                catalogVersion = local.version,
            )
        }
        val (fleets, overlay) = SignatureExchange.overlayStock(local.fleets, pack.fleets)
        if (overlay.error != null) {
            return@withLock StockCatalogUpdateResult(error = overlay.error)
        }
        // A pack sets the version past the ladder, so rows (custom ones too) get the v104 duplicate cleanup here.
        val next = local.copy(fleets = fleets.map(::withoutDuplicateRules), version = pack.catalogVersion)
        publish(next)
        withContext(Dispatchers.IO) {
            val tmp = File(file.parentFile, "config.tmp")
            tmp.writeText(json.encodeToString(next))
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        }
        overlay.copy(catalogVersion = pack.catalogVersion)
    }

    private fun patchBuiltIn(cfg: PersistedConfig): PersistedConfig {
        val catalog = DefaultCatalog.fleets().associateBy { it.id }
        val next = cfg.fleets.map { loaded ->
            if (!loaded.builtIn) return@map loaded
            // Retired rules go on every load, whatever version the file says (an overlay or import can bring one back).
            val fleet = RetiredRules.strip(loaded)
            val stock = catalog[fleet.id] ?: return@map fleet
            val have = fleet.rules.map { ruleKey(it) }.toSet()
            val missing = stock.rules.filter { ruleKey(it) !in have }
            val renamed = when {
                fleet.id == "fleet-unknown" && fleet.name == "Unknown Fleet" -> stock.name
                fleet.id == "fleet-seos" && fleet.name == "Seos" -> stock.name
                fleet.id == "fleet-target-atrius" && fleet.name == "Target Atrius basket" -> stock.name
                else -> fleet.name
            }
            val notes = if (
                fleet.id == "fleet-target-atrius" &&
                fleet.notes == "Target shopping-basket tag. Dual-labels with generic iBeacon; this row is the store basket."
            ) {
                stock.notes
            } else {
                fleet.notes
            }
            if (missing.isEmpty() && renamed == fleet.name && notes == fleet.notes) fleet
            else fleet.copy(name = renamed, notes = notes, rules = fleet.rules + missing)
        }
        var presets = cfg.presets.map { preset ->
            if (preset.id == "named" && preset.name == "Named only") {
                preset.copy(name = "Signatures only")
            } else {
                preset
            }
        }
        var fleets = next
        var version = cfg.version
        var filter = cfg.filter
        val hiddenPresetIds = cfg.hiddenPresetIds
        if (version < CATALOG_V2) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V2.mapNotNull { catalog[it] }.filter { it.id !in have }
            if (extras.isNotEmpty()) fleets = fleets + extras
            version = CATALOG_V2
        }
        if (version < CATALOG_V3) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V3.mapNotNull { catalog[it] }.filter { it.id !in have }
            if (extras.isNotEmpty()) fleets = fleets + extras
            version = CATALOG_V3
        }
        if (version < CATALOG_V4) {
            val s = cfg.settings
            fleets = fleets.map { fleet ->
                if (fleet.id !in POLICY_FLEET_IDS) return@map fleet
                fleet.copy(
                    rules = fleet.rules.map { rule ->
                        val on = when (rule.kind) {
                            RuleKind.NAME_CONTAINS, RuleKind.NAME_GLOB -> s.detectSsidKeywords
                            RuleKind.OUI, RuleKind.MAC_PREFIX -> s.detectKnownOuis
                            RuleKind.VENDOR_IE_OUI -> s.detectVendorIes
                            RuleKind.SERVICE_UUID, RuleKind.SERVICE_DATA, RuleKind.MANUFACTURER_ID, RuleKind.MANUFACTURER_DATA ->
                                if (fleet.id == "fleet-raven") s.detectBleRaven else true
                            else -> true
                        }
                        if (rule.enabled == on) rule else rule.copy(enabled = on)
                    },
                )
            }
            version = CATALOG_V4
        }
        if (version < CATALOG_V5) {
            if (cfg.settings.arrivalsOnly && !filter.arrivalsOnly) {
                filter = filter.copy(arrivalsOnly = true)
            }
            version = CATALOG_V5
        }
        if (version < CATALOG_V6) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V6.mapNotNull { catalog[it] }.filter { it.id !in have }
            if (extras.isNotEmpty()) fleets = fleets + extras
            version = CATALOG_V6
        }
        if (version < CATALOG_V7) {
            fleets = fleets.map { fleet ->
                if (fleet.id == "fleet-hobby-ble" && fleet.builtIn && !fleet.enabled) {
                    fleet.copy(enabled = true)
                } else {
                    fleet
                }
            }
            version = CATALOG_V7
        }
        if (version < CATALOG_V8) {
            fleets = fleets.map { fleet ->
                val stock = catalog[fleet.id] ?: return@map fleet
                if (!fleet.builtIn) return@map fleet
                val enable = if (fleet.id == "fleet-axon") true else fleet.enabled
                val note = if (fleet.attentionNote.isBlank() && stock.attentionNote.isNotBlank()) {
                    stock.attentionNote
                } else {
                    fleet.attentionNote
                }
                if (enable == fleet.enabled && note == fleet.attentionNote) fleet
                else fleet.copy(enabled = enable, attentionNote = note)
            }
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V8.mapNotNull { catalog[it] }.filter { it.id !in have }
            if (extras.isNotEmpty()) fleets = fleets + extras
            version = CATALOG_V8
        }
        if (version < CATALOG_V9) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V9.mapNotNull { catalog[it] }.filter { it.id !in have }
            if (extras.isNotEmpty()) fleets = fleets + extras
            version = CATALOG_V9
        }
        if (version < CATALOG_V10) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V10.mapNotNull { catalog[it] }.filter { it.id !in have }
            if (extras.isNotEmpty()) fleets = fleets + extras
            version = CATALOG_V10
        }
        if (version < CATALOG_V11) {
            fleets = fleets.map { fleet ->
                val stock = catalog[fleet.id] ?: return@map fleet
                if (!fleet.builtIn) return@map fleet
                if (fleet.colorIndex == stock.colorIndex) fleet
                else fleet.copy(colorIndex = stock.colorIndex)
            }
            version = CATALOG_V11
        }
        if (version < CATALOG_V12) {
            fleets = fleets.map { fleet ->
                val stock = catalog[fleet.id]
                when {
                    fleet.id == "fleet-unknown" && fleet.builtIn && stock != null ->
                        fleet.copy(
                            enabled = false,
                            minPeers = 0,
                            clusterByOui = false,
                            sequentialMac = false,
                            notes = stock.notes,
                            rules = stock.rules,
                        )
                    fleet.id == "fleet-airtag" && fleet.builtIn ->
                        fleet.copy(
                            rules = fleet.rules.filterNot {
                                it.kind == RuleKind.MANUFACTURER_DATA &&
                                    it.companyId == 0x004C &&
                                    it.dataPrefixHex.filter { ch -> ch.isLetterOrDigit() }
                                        .equals("07", ignoreCase = true)
                            },
                            notes = stock?.notes ?: fleet.notes,
                        )
                    else -> fleet
                }
            }
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V12.mapNotNull { catalog[it] }.filter { it.id !in have }
            if (extras.isNotEmpty()) fleets = fleets + extras
            version = CATALOG_V12
        }
        if (version < CATALOG_V13) {
            fleets = fleets.sortedBy { it.name.lowercase() }
            version = CATALOG_V13
        }
        if (version < CATALOG_V14) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V14.mapNotNull { catalog[it] }.filter { it.id !in have }
            fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            version = CATALOG_V14
        }
        if (version < CATALOG_V15) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V15.mapNotNull { catalog[it] }.filter { it.id !in have }
            fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            version = CATALOG_V15
        }
        if (version < CATALOG_V16) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V16.mapNotNull { catalog[it] }.filter { it.id !in have }
            fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            version = CATALOG_V16
        }
        // V17 used to turn every built-in signature the user had switched off back on; V19 and V20 did the
        // same for tagLocation, keepScreenOn and onlineLookup (network). P1-A7 removed them: an upgrade keeps
        // the user's choices. The versions still advance.
        if (version < CATALOG_V17) version = CATALOG_V17
        var watchlist = cfg.watchlist
        if (version < CATALOG_V18) {
            val have = watchlist.mapNotNull { it.fleetId }.toSet()
            val extras = DefaultCatalog.defaultWatchlist().filter { it.fleetId !in have }
            if (extras.isNotEmpty()) watchlist = watchlist + extras
            version = CATALOG_V18
        }
        var settings = cfg.settings
        if (version < CATALOG_V20) version = CATALOG_V20
        if (version < CATALOG_V21) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V21.mapNotNull { catalog[it] }.filter { it.id !in have }
            fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            version = CATALOG_V21
        }
        if (version < CATALOG_V22) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V22.mapNotNull { catalog[it] }.filter { it.id !in have }
            fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            version = CATALOG_V22
        }
        if (version < CATALOG_V23) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V23.mapNotNull { catalog[it] }.filter { it.id !in have }
            fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            version = CATALOG_V23
        }
        if (version < CATALOG_V24) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V24.mapNotNull { catalog[it] }.filter { it.id !in have }
            fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            version = CATALOG_V24
        }
        if (version < CATALOG_V25) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V25.mapNotNull { catalog[it] }.filter { it.id !in have }
            fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            version = CATALOG_V25
        }
        if (version < CATALOG_V26) {
            fleets = fleets.map { fleet ->
                val stock = catalog[fleet.id] ?: return@map fleet
                if (!fleet.builtIn) fleet else fleet.copy(kind = stock.kind)
            }
            val stockPresets = FilterEngine().defaultPresets(fleets)
            val custom = presets.filterNot { it.isBuiltIn() }
            presets = stockPresets + custom
            version = CATALOG_V26
        }
        if (version < CATALOG_V27) {
            val stockPresets = FilterEngine().defaultPresets(fleets)
            val custom = presets.filterNot { it.isBuiltIn() }
            presets = stockPresets + custom
            if (filter.classIncludeActive() && filter.namedOnly) {
                filter = filter.copy(namedOnly = false)
            }
            version = CATALOG_V27
        }
        if (version < CATALOG_V28) {
            fleets = fleets.map { fleet ->
                val stock = catalog[fleet.id] ?: return@map fleet
                if (!fleet.builtIn) fleet else fleet.copy(kind = stock.kind)
            }
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V28.mapNotNull { catalog[it] }.filter { it.id !in have }
            fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            val stockPresets = FilterEngine().defaultPresets(fleets)
            val custom = presets.filterNot { it.isBuiltIn() }
            presets = stockPresets + custom
            version = CATALOG_V28
        }
        if (version < CATALOG_V29) {
            fleets = fleets.map { fleet ->
                val stock = catalog[fleet.id] ?: return@map fleet
                if (!fleet.builtIn) fleet else fleet.copy(kind = stock.kind)
            }
            val stockPresets = FilterEngine().defaultPresets(fleets)
            val custom = presets.filterNot { it.isBuiltIn() }
            presets = stockPresets + custom
            version = CATALOG_V29
        }
        if (version < CATALOG_V30) {
            fleets = fleets.map { fleet ->
                if (fleet.enabled) fleet else fleet.copy(enabled = true)
            }
            if (filter.useFleetFilter && !filter.excludeSignatures && filter.fleetIds.isNotEmpty()) {
                filter = filter.copy(
                    includeSignatures = true,
                    includeFleetIds = filter.fleetIds,
                    useFleetFilter = false,
                    fleetIds = emptySet(),
                )
            }
            version = CATALOG_V30
        }
        if (version < CATALOG_V31) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V31.mapNotNull { catalog[it] }.filter { it.id !in have }
            fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            val stockPresets = FilterEngine().defaultPresets(fleets)
            val custom = presets.filterNot { it.isBuiltIn() }
            presets = stockPresets + custom
            val watched = watchlist.mapNotNull { it.fleetId }.toSet()
            val watchExtras = DefaultCatalog.defaultWatchlist().filter { it.fleetId !in watched }
            if (watchExtras.isNotEmpty()) watchlist = watchlist + watchExtras
            version = CATALOG_V31
        }
        if (version < CATALOG_V32) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V32.mapNotNull { catalog[it] }.filter { it.id !in have }
            fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            val stockPresets = FilterEngine().defaultPresets(fleets)
            val custom = presets.filterNot { it.isBuiltIn() }
            presets = stockPresets + custom
            version = CATALOG_V32
        }
        if (version < CATALOG_V33) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V33.mapNotNull { catalog[it] }.filter { it.id !in have }
            fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            val stockPresets = FilterEngine().defaultPresets(fleets)
            val custom = presets.filterNot { it.isBuiltIn() }
            presets = stockPresets + custom
            version = CATALOG_V33
        }
        if (version < CATALOG_V34) {
            fleets = fleets.map { fleet ->
                val stock = catalog[fleet.id] ?: return@map fleet
                if (!fleet.builtIn) fleet else fleet.copy(kind = stock.kind)
            }
            val stockPresets = FilterEngine().defaultPresets(fleets)
            val custom = presets.filterNot { it.isBuiltIn() }
            presets = stockPresets + custom
            version = CATALOG_V34
        }
        if (version < CATALOG_V35) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V35.mapNotNull { catalog[it] }.filter { it.id !in have }
            fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            val stockPresets = FilterEngine().defaultPresets(fleets)
            val custom = presets.filterNot { it.isBuiltIn() }
            presets = stockPresets + custom
            version = CATALOG_V35
        }
        if (version < CATALOG_V36) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V36.mapNotNull { catalog[it] }.filter { it.id !in have }
            fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            val stockPresets = FilterEngine().defaultPresets(fleets)
            val custom = presets.filterNot { it.isBuiltIn() }
            presets = stockPresets + custom
            version = CATALOG_V36
        }
        if (version < CATALOG_V37) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V37.mapNotNull { catalog[it] }.filter { it.id !in have }
            fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            val stockPresets = FilterEngine().defaultPresets(fleets)
            val custom = presets.filterNot { it.isBuiltIn() }
            presets = stockPresets + custom
            version = CATALOG_V37
        }
        if (version < CATALOG_V38) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V38.mapNotNull { catalog[it] }.filter { it.id !in have }
            fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            fleets = fleets.map { fleet ->
                val stock = catalog[fleet.id] ?: return@map fleet
                if (!fleet.builtIn) return@map fleet
                val note = if (fleet.attentionNote.isBlank() && stock.attentionNote.isNotBlank()) {
                    stock.attentionNote
                } else {
                    fleet.attentionNote
                }
                if (note == fleet.attentionNote) fleet else fleet.copy(attentionNote = note)
            }
            val stockPresets = FilterEngine().defaultPresets(fleets)
            val custom = presets.filterNot { it.isBuiltIn() }
            presets = stockPresets + custom
            val watched = watchlist.mapNotNull { it.fleetId }.toSet()
            val watchExtras = DefaultCatalog.defaultWatchlist().filter { it.fleetId !in watched }
            if (watchExtras.isNotEmpty()) watchlist = watchlist + watchExtras
            version = CATALOG_V38
        }
        if (version < CATALOG_V39) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V39.mapNotNull { catalog[it] }.filter { it.id !in have }
            fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            version = CATALOG_V39
        }
        if (version < CATALOG_V40) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V40.mapNotNull { catalog[it] }.filter { it.id !in have }
            fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            val refresh = setOf("fleet-tesla", "fleet-mbux", "fleet-audi-mmi", "fleet-gm-hotspot")
            fleets = fleets.map { fleet ->
                if (fleet.id !in refresh || !fleet.builtIn) return@map fleet
                val stock = catalog[fleet.id] ?: return@map fleet
                val keys = fleet.rules.map { ruleKey(it) }.toSet()
                val extraRules = stock.rules.filter { ruleKey(it) !in keys }
                fleet.copy(rules = fleet.rules + extraRules, notes = stock.notes)
            }
            val stockPresets = FilterEngine().defaultPresets(fleets)
            val custom = presets.filterNot { it.isBuiltIn() }
            presets = stockPresets + custom
            version = CATALOG_V40
        }
        if (version < CATALOG_V41) {
            fleets = fleets.map { fleet ->
                if (fleet.id != "fleet-tesla" && fleet.id != "fleet-ibeacon") return@map fleet
                if (!fleet.builtIn) return@map fleet
                val stock = catalog[fleet.id] ?: return@map fleet
                val keys = fleet.rules.map { ruleKey(it) }.toSet()
                val extraRules = stock.rules.filter { ruleKey(it) !in keys }
                fleet.copy(rules = fleet.rules + extraRules, notes = stock.notes)
            }
            version = CATALOG_V41
        }
        if (version < CATALOG_V42) {
            fleets = fleets.map { fleet ->
                val stock = catalog[fleet.id] ?: return@map fleet
                if (!fleet.builtIn) return@map fleet
                val color = if (fleet.id == "fleet-gopro") stock.colorIndex else fleet.colorIndex
                fleet.copy(kind = stock.kind, colorIndex = color)
            }
            val stockPresets = FilterEngine().defaultPresets(fleets)
            val custom = presets.filterNot { it.isBuiltIn() }
            presets = stockPresets + custom
            version = CATALOG_V42
        }
        if (version < CATALOG_V43) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V43.mapNotNull { catalog[it] }.filter { it.id !in have }
            fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            fleets = fleets.map { fleet ->
                if (fleet.id != "fleet-dji" || !fleet.builtIn) return@map fleet
                val stock = catalog[fleet.id] ?: return@map fleet
                fleet.copy(notes = stock.notes)
            }
            val stockPresets = FilterEngine().defaultPresets(fleets)
            val custom = presets.filterNot { it.isBuiltIn() }
            presets = stockPresets + custom
            version = CATALOG_V43
        }
        if (version < CATALOG_V44) {
            val stockPresets = FilterEngine().defaultPresets(fleets)
            val custom = presets.filterNot { it.isBuiltIn() }
            presets = stockPresets + custom
            version = CATALOG_V44
        }
        if (version < CATALOG_V45) {
            fleets = fleets.map { fleet ->
                if (!fleet.builtIn) return@map fleet
                if (fleet.id != "fleet-airtag" && fleet.id != "fleet-apple-device") return@map fleet
                val stock = catalog[fleet.id] ?: return@map fleet
                fleet.copy(notes = stock.notes)
            }
            version = CATALOG_V45
        }
        if (version < CATALOG_V46) {
            fleets = fleets.map { fleet ->
                if (!fleet.builtIn) return@map fleet
                if (fleet.id != "fleet-unifi-ap" && fleet.id != "fleet-unifi") return@map fleet
                val stock = catalog[fleet.id] ?: return@map fleet
                fleet.copy(kind = stock.kind, colorIndex = stock.colorIndex, notes = stock.notes)
            }
            version = CATALOG_V46
        }
        if (version < CATALOG_V47) {
            val stockPresets = FilterEngine().defaultPresets(fleets)
            val custom = presets.filterNot { it.isBuiltIn() }
            presets = stockPresets + custom
            version = CATALOG_V47
        }
        if (version < CATALOG_V48) {
            val leIds = setOf(
                "fleet-axon",
                "fleet-watchguard",
                "fleet-cradlepoint",
                "fleet-airlink",
                "fleet-compex",
                "fleet-novatel",
                "fleet-utility-inc",
            )
            fleets = fleets.map { fleet ->
                if (!fleet.builtIn || fleet.id !in leIds) return@map fleet
                val stock = catalog[fleet.id] ?: return@map fleet
                fleet.copy(kind = stock.kind, colorIndex = stock.colorIndex)
            }
            if (SignatureClass.BODYWORN in filter.classes) {
                filter = filter.copy(classes = filter.classes + SignatureClass.LAW_ENFORCEMENT)
            }
            version = CATALOG_V48
        }
        if (version < CATALOG_V49) {
            val campusApIds = setOf(
                "fleet-meraki",
                "fleet-cisco",
                "fleet-aruba",
                "fleet-ruckus",
                "fleet-fortinet",
                "fleet-mist",
                "fleet-sophos",
                "fleet-extreme",
                "fleet-edgecore",
                "fleet-watchguard-ap",
                "fleet-mojo",
            )
            fleets = fleets.map { fleet ->
                if (!fleet.builtIn || fleet.id !in campusApIds) return@map fleet
                val stock = catalog[fleet.id] ?: return@map fleet
                fleet.copy(kind = stock.kind, colorIndex = stock.colorIndex, notes = stock.notes)
            }
            version = CATALOG_V49
        }
        if (version < CATALOG_V50) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V50.mapNotNull { catalog[it] }.filter { it.id !in have }
            fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            val mergeIds = setOf(
                "fleet-glinet",
                "fleet-toyota",
                "fleet-volkswagen",
                "fleet-porsche",
                "fleet-motive",
                "fleet-hp",
                "fleet-att",
            )
            fleets = fleets.map { fleet ->
                if (!fleet.builtIn || fleet.id !in mergeIds) return@map fleet
                val stock = catalog[fleet.id] ?: return@map fleet
                val keys = fleet.rules.map { ruleKey(it) }.toSet()
                val extraRules = stock.rules.filter { ruleKey(it) !in keys }
                fleet.copy(rules = fleet.rules + extraRules, notes = stock.notes)
            }
            version = CATALOG_V50
        }
        if (version < CATALOG_V51) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V51.mapNotNull { catalog[it] }.filter { it.id !in have }
            fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            version = CATALOG_V51
        }
        if (version < CATALOG_V52) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V52.mapNotNull { catalog[it] }.filter { it.id !in have }
            fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            version = CATALOG_V52
        }
        if (version < CATALOG_V53) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V53.mapNotNull { catalog[it] }.filter { it.id !in have }
            fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            version = CATALOG_V53
        }
        if (version < CATALOG_V54) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V54.mapNotNull { catalog[it] }.filter { it.id !in have }
            fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            version = CATALOG_V54
        }
        if (version < CATALOG_V55) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V55.mapNotNull { catalog[it] }.filter { it.id !in have }
            fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            version = CATALOG_V55
        }
        if (version < CATALOG_V56) {
            fleets = fleets.map { fleet ->
                val stock = catalog[fleet.id] ?: return@map fleet
                if (!fleet.builtIn) return@map fleet
                if (stock.decode != null && fleet.decode == null) {
                    fleet.copy(decode = stock.decode, notes = stock.notes)
                } else {
                    fleet
                }
            }
            version = CATALOG_V56
        }
        if (version < CATALOG_V57) {
            fleets = fleets.map { fleet ->
                val stock = catalog[fleet.id] ?: return@map fleet
                if (!fleet.builtIn || stock.decode == null) return@map fleet
                fleet.copy(decode = stock.decode, notes = stock.notes)
            }
            version = CATALOG_V57
        }
        if (version < CATALOG_V58) {
            fleets = fleets.map { fleet ->
                if (fleet.kind != SignatureClass.BODYWORN) return@map fleet
                fleet.copy(kind = SignatureClass.WEARABLE)
            }
            fun foldBodyworn(state: FilterState): FilterState {
                if (SignatureClass.BODYWORN !in state.classes) return state
                return state.copy(classes = state.classes - SignatureClass.BODYWORN + SignatureClass.WEARABLE)
            }
            filter = foldBodyworn(filter)
            presets = presets.map { preset ->
                val next = foldBodyworn(preset.filter)
                if (next === preset.filter) preset else preset.copy(filter = next)
            }
            version = CATALOG_V58
        }
        if (version < CATALOG_V59) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V59.mapNotNull { catalog[it] }.filter { it.id !in have }
            fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            version = CATALOG_V59
        }
        if (version < CATALOG_V60) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V60.mapNotNull { catalog[it] }.filter { it.id !in have }
            fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            version = CATALOG_V60
        }
        if (version < CATALOG_V61) {
            fleets = fleets.map { fleet ->
                if (fleet.id != "fleet-govee" || !fleet.builtIn) return@map fleet
                val stock = catalog[fleet.id] ?: return@map fleet
                fleet.copy(decode = stock.decode, notes = stock.notes)
            }
            version = CATALOG_V61
        }
        if (version < CATALOG_V62) {
            fleets = fleets.map { fleet ->
                if (fleet.id != "fleet-nest-weave" || !fleet.builtIn) return@map fleet
                val stock = catalog[fleet.id] ?: return@map fleet
                fleet.copy(decode = stock.decode, notes = stock.notes)
            }
            version = CATALOG_V62
        }
        if (version < CATALOG_V63) {
            fleets = fleets.map { fleet ->
                if (!fleet.builtIn) return@map fleet
                if (fleet.id != "fleet-fs-ext-battery" && fleet.id != "fleet-flock-cameras") return@map fleet
                val stock = catalog[fleet.id] ?: return@map fleet
                val note = if (fleet.attentionNote.isBlank() && stock.attentionNote.isNotBlank()) {
                    stock.attentionNote
                } else {
                    fleet.attentionNote
                }
                fleet.copy(
                    kind = stock.kind,
                    colorIndex = stock.colorIndex,
                    notes = stock.notes,
                    attentionNote = note,
                )
            }
            val watched = watchlist.mapNotNull { it.fleetId }.toSet()
            val watchExtras = DefaultCatalog.defaultWatchlist().filter { it.fleetId !in watched }
            if (watchExtras.isNotEmpty()) watchlist = watchlist + watchExtras
            version = CATALOG_V63
        }
        if (version < CATALOG_V64) {
            fleets = fleets.map { fleet ->
                if (!fleet.builtIn) return@map fleet
                val stock = catalog[fleet.id] ?: return@map fleet
                if (stock.attentionNote.isBlank()) return@map fleet
                val note = if (fleet.attentionNote.isBlank()) stock.attentionNote else fleet.attentionNote
                if (note == fleet.attentionNote) fleet else fleet.copy(attentionNote = note)
            }
            val watched = watchlist.mapNotNull { it.fleetId }.toSet()
            val watchExtras = DefaultCatalog.defaultWatchlist().filter { it.fleetId !in watched }
            if (watchExtras.isNotEmpty()) watchlist = watchlist + watchExtras
            version = CATALOG_V64
        }
        if (version < CATALOG_V65) {
            fleets = fleets.map { fleet ->
                if (fleet.builtIn) return@map fleet
                val rules = fleet.rules.map { rule ->
                    if (!rule.enabled) return@map rule
                    if (rule.kind != RuleKind.NAME_GLOB && rule.kind != RuleKind.NAME_CONTAINS) return@map rule
                    if (SignatureCandidates.isOverbroadCreateName(rule.text)) rule.copy(enabled = false)
                    else rule
                }
                if (rules == fleet.rules) fleet else fleet.copy(rules = rules)
            }
            version = CATALOG_V65
        }
        if (version < CATALOG_V66) {
            fleets = fleets.map { fleet ->
                if (fleet.builtIn) return@map fleet
                val rules = fleet.rules.map { rule ->
                    if (!rule.enabled || rule.kind != RuleKind.SERVICE_UUID) return@map rule
                    val hex = rule.text.filter { it.isLetterOrDigit() }.uppercase()
                    if (hex in GENERIC_GATT_UUIDS) rule.copy(enabled = false) else rule
                }
                if (rules == fleet.rules) fleet else fleet.copy(rules = rules)
            }
            version = CATALOG_V66
        }
        if (version < CATALOG_V67) {
            fleets = fleets.map { fleet ->
                if (!fleet.builtIn) return@map fleet
                val stock = catalog[fleet.id] ?: return@map fleet
                if (stock.notes == fleet.notes) fleet else fleet.copy(notes = stock.notes)
            }
            version = CATALOG_V67
        }
        if (version < CATALOG_V68) {
            fleets = fleets.map { fleet ->
                if (!fleet.builtIn) return@map fleet
                val stock = catalog[fleet.id] ?: return@map fleet
                if (stock.notes == fleet.notes) fleet else fleet.copy(notes = stock.notes)
            }
            version = CATALOG_V68
        }
        if (version < CATALOG_V69) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V69.mapNotNull { catalog[it] }.filter { it.id !in have }
            fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            version = CATALOG_V69
        }
        if (version < CATALOG_V70) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V70.mapNotNull { catalog[it] }.filter { it.id !in have }
            fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            val watched = watchlist.mapNotNull { it.fleetId }.toSet()
            val watchExtras = DefaultCatalog.defaultWatchlist().filter { it.fleetId !in watched }
            if (watchExtras.isNotEmpty()) watchlist = watchlist + watchExtras
            version = CATALOG_V70
        }
        if (version < CATALOG_V71) {
            val watched = watchlist.mapNotNull { it.fleetId }.toSet()
            val watchExtras = DefaultCatalog.defaultWatchlist().filter { it.fleetId !in watched }
            if (watchExtras.isNotEmpty()) watchlist = watchlist + watchExtras
            version = CATALOG_V71
        }
        if (version < CATALOG_V72) {
            val accessControl = setOf(
                "fleet-seos", "fleet-salto", "fleet-dormakaba", "fleet-paxton",
            )
            fleets = fleets.map { fleet ->
                if (!fleet.builtIn || fleet.id !in accessControl) return@map fleet
                val stock = catalog[fleet.id] ?: return@map fleet
                fleet.copy(kind = stock.kind, colorIndex = stock.colorIndex)
            }
            version = CATALOG_V72
        }
        if (version < CATALOG_V73) {
            fleets = fleets.filterNot { it.builtIn && it.id == "fleet-unknown" }
            watchlist = watchlist.filterNot { it.fleetId == "fleet-unknown" }
            version = CATALOG_V73
        }
        if (version < CATALOG_V74) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V74.mapNotNull { catalog[it] }.filter { it.id !in have }
            fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            val watched = watchlist.mapNotNull { it.fleetId }.toSet()
            val watchExtras = DefaultCatalog.defaultWatchlist().filter { it.fleetId !in watched }
            if (watchExtras.isNotEmpty()) watchlist = watchlist + watchExtras
            version = CATALOG_V74
        }
        if (version < CATALOG_V75) {
            val stockPresets = FilterEngine().defaultPresets(fleets)
            val stockNames = stockPresets.map { it.name.lowercase() }.toSet()
            val stockFilters = stockPresets.map { it.filter }.toSet()
            val custom = presets.filterNot { it.isBuiltIn() }.filterNot { saved ->
                saved.name.lowercase() in stockNames || saved.filter in stockFilters
            }
            presets = stockPresets + custom
            version = CATALOG_V75
        }
        if (version < CATALOG_V76) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V76.mapNotNull { catalog[it] }.filter { it.id !in have }
            fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            fleets = fleets.map { fleet ->
                if (!fleet.builtIn || fleet.id !in TPMS_V76_REFRESH) return@map fleet
                val stock = catalog[fleet.id] ?: return@map fleet
                fleet.copy(decode = stock.decode, notes = stock.notes)
            }
            version = CATALOG_V76
        }
        if (version < CATALOG_V77) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V77.mapNotNull { catalog[it] }.filter { it.id !in have }
            fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            fleets = fleets.map { fleet ->
                if (!fleet.builtIn || fleet.id !in CATALOG_V77_REFRESH) return@map fleet
                val stock = catalog[fleet.id] ?: return@map fleet
                fleet.copy(
                    decode = stock.decode,
                    notes = stock.notes,
                    attentionNote = stock.attentionNote,
                    rules = stock.rules,
                )
            }
            val watched = watchlist.map { it.fleetId }.toSet()
            val watchExtras = DefaultCatalog.defaultWatchlist().filter { it.fleetId !in watched }
            if (watchExtras.isNotEmpty()) watchlist = watchlist + watchExtras
            version = CATALOG_V77
        }
        if (version < CATALOG_V78) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V78.mapNotNull { catalog[it] }.filter { it.id !in have }
            fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            version = CATALOG_V78
        }
        if (version < CATALOG_V79) {
            fleets = fleets.map { fleet ->
                if (!fleet.builtIn || fleet.id != "fleet-remote-id") return@map fleet
                val stock = catalog[fleet.id] ?: return@map fleet
                fleet.copy(decode = stock.decode)
            }
            version = CATALOG_V79
        }
        if (version < CATALOG_V80) {
            fleets = fleets.map { fleet ->
                if (!fleet.builtIn || fleet.id !in setOf("fleet-raven", "fleet-penguin")) return@map fleet
                val stock = catalog[fleet.id] ?: return@map fleet
                fleet.copy(rules = stock.rules, notes = stock.notes, attentionNote = stock.attentionNote)
            }
            version = CATALOG_V80
        }
        if (version < CATALOG_V81) {
            fleets = fleets.map { fleet ->
                if (!fleet.builtIn || fleet.id != "fleet-axon") return@map fleet
                val stock = catalog[fleet.id] ?: return@map fleet
                fleet.copy(rules = stock.rules)
            }
            version = CATALOG_V81
        }
        if (version < CATALOG_V82) {
            fleets = fleets.map { fleet ->
                if (!fleet.builtIn || fleet.id != "fleet-flock-cameras") return@map fleet
                val stock = catalog[fleet.id] ?: return@map fleet
                fleet.copy(
                    rules = stock.rules,
                    notes = stock.notes,
                    attentionNote = stock.attentionNote,
                )
            }
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V82.mapNotNull { catalog[it] }.filter { it.id !in have }
            if (extras.isNotEmpty()) fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            version = CATALOG_V82
        }
        if (version < CATALOG_V83) {
            fleets = fleets.map { fleet ->
                if (!fleet.builtIn || fleet.id != "fleet-penguin") return@map fleet
                val stock = catalog[fleet.id] ?: return@map fleet
                fleet.copy(decode = stock.decode, notes = stock.notes)
            }
            version = CATALOG_V83
        }
        if (version < CATALOG_V84) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V84.mapNotNull { catalog[it] }.filter { it.id !in have }
            if (extras.isNotEmpty()) fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            version = CATALOG_V84
        }
        if (version < CATALOG_V85) {
            fleets = fleets.map { fleet ->
                if (!fleet.builtIn || fleet.id != "fleet-dult") return@map fleet
                val stock = catalog[fleet.id] ?: return@map fleet
                fleet.copy(decode = stock.decode, notes = stock.notes)
            }
            version = CATALOG_V85
        }
        if (version < CATALOG_V86) {
            fleets = fleets.map { fleet ->
                if (!fleet.builtIn || fleet.id != "fleet-dult") return@map fleet
                val stock = catalog[fleet.id] ?: return@map fleet
                fleet.copy(decode = stock.decode, notes = stock.notes)
            }
            version = CATALOG_V86
        }
        if (version < CATALOG_V87) {
            fleets = fleets.map { fleet ->
                if (!fleet.builtIn || fleet.id != "fleet-find-hub") return@map fleet
                val stock = catalog[fleet.id] ?: return@map fleet
                fleet.copy(decode = stock.decode, notes = stock.notes)
            }
            version = CATALOG_V87
        }
        if (version < CATALOG_V88) {
            fleets = fleets.map { fleet ->
                if (!fleet.builtIn || fleet.id != "fleet-remote-id") return@map fleet
                val stock = catalog[fleet.id] ?: return@map fleet
                fleet.copy(decode = stock.decode, notes = stock.notes)
            }
            version = CATALOG_V88
        }
        if (version < CATALOG_V89) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V89.mapNotNull { catalog[it] }.filter { it.id !in have }
            if (extras.isNotEmpty()) fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            fleets = fleets.map { fleet ->
                if (!fleet.builtIn) return@map fleet
                val stock = catalog[fleet.id] ?: return@map fleet
                when (fleet.id) {
                    "fleet-even-g1" -> {
                        val keys = fleet.rules.map { ruleKey(it) }.toSet()
                        val extraRules = stock.rules.filter { ruleKey(it) !in keys }
                        fleet.copy(
                            rules = if (extraRules.isEmpty()) fleet.rules else fleet.rules + extraRules,
                            notes = stock.notes,
                            attentionNote = stock.attentionNote,
                        )
                    }
                    "fleet-liteon-camera-radio" -> {
                        val keys = fleet.rules.map { ruleKey(it) }.toSet()
                        val extraRules = stock.rules.filter { ruleKey(it) !in keys }
                        if (extraRules.isEmpty()) fleet else fleet.copy(rules = fleet.rules + extraRules)
                    }
                    else -> fleet
                }
            }
            if (watchlist.none { it.fleetId == "fleet-rayneo" }) {
                DefaultCatalog.defaultWatchlist().firstOrNull { it.fleetId == "fleet-rayneo" }?.let {
                    watchlist = watchlist + it
                }
            }
            version = CATALOG_V89
        }
        if (version < CATALOG_V90) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V90.mapNotNull { catalog[it] }.filter { it.id !in have }
            if (extras.isNotEmpty()) fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            fleets = fleets.map { fleet ->
                if (!fleet.builtIn) return@map fleet
                val stock = catalog[fleet.id] ?: return@map fleet
                when (fleet.id) {
                    "fleet-remote-id", "fleet-parrot" -> {
                        val keys = fleet.rules.map { ruleKey(it) }.toSet()
                        val extraRules = stock.rules.filter { ruleKey(it) !in keys }
                        fleet.copy(
                            rules = if (extraRules.isEmpty()) fleet.rules else fleet.rules + extraRules,
                            notes = stock.notes,
                        )
                    }
                    else -> fleet
                }
            }
            val watched = watchlist.mapNotNull { it.fleetId }.toSet()
            val addWatch = DefaultCatalog.defaultWatchlist().filter {
                it.fleetId in ADDED_IN_V90 && it.fleetId !in watched
            }
            if (addWatch.isNotEmpty()) watchlist = watchlist + addWatch
            version = CATALOG_V90
        }
        if (version < CATALOG_V91) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V91.mapNotNull { catalog[it] }.filter { it.id !in have }
            if (extras.isNotEmpty()) fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            fleets = fleets.map { fleet ->
                if (!fleet.builtIn) return@map fleet
                val stock = catalog[fleet.id] ?: return@map fleet
                when (fleet.id) {
                    "fleet-dji", "fleet-osmo" -> fleet.copy(decode = stock.decode, notes = stock.notes)
                    else -> fleet
                }
            }
            version = CATALOG_V91
        }
        if (version < CATALOG_V92) {
            fleets = fleets.map { fleet ->
                if (!fleet.builtIn) return@map fleet
                val stock = catalog[fleet.id] ?: return@map fleet
                when (fleet.id) {
                    "fleet-liteon-camera-radio" -> fleet.copy(
                        rules = fleet.rules.filterNot { rule ->
                            rule.kind == RuleKind.OUI &&
                                (rule.text.equals("48:27:EA", ignoreCase = true) ||
                                    rule.text.equals("82:6B:F2", ignoreCase = true))
                        },
                    )
                    "fleet-meta-glasses" -> {
                        val keys = fleet.rules.map { ruleKey(it) }.toSet()
                        val extraRules = stock.rules.filter { ruleKey(it) !in keys }
                        fleet.copy(
                            rules = if (extraRules.isEmpty()) fleet.rules else fleet.rules + extraRules,
                            notes = stock.notes,
                            attentionNote = stock.attentionNote,
                        )
                    }
                    "fleet-axon" -> fleet.copy(notes = stock.notes, attentionNote = stock.attentionNote)
                    else -> fleet
                }
            }
            version = CATALOG_V92
        }
        if (version < CATALOG_V93) {
            fleets = fleets.map { fleet ->
                val stock = catalog[fleet.id] ?: return@map fleet
                catalog93Fleet(fleet, stock)
            }
            version = CATALOG_V93
        }
        if (version < CATALOG_V94) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V94.mapNotNull { catalog[it] }.filter { it.id !in have }
            if (extras.isNotEmpty()) fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            version = CATALOG_V94
        }
        if (version < CATALOG_V95) {
            fleets = fleets.map { fleet ->
                if (!fleet.builtIn || fleet.id != "fleet-remote-id") return@map fleet
                val stock = catalog[fleet.id] ?: return@map fleet
                fleet.copy(decode = stock.decode, notes = stock.notes)
            }
            version = CATALOG_V95
        }
        if (version < CATALOG_V96) {
            fleets = fleets.map { fleet ->
                if (!fleet.builtIn || fleet.id != "fleet-chipolo") return@map fleet
                val stock = catalog[fleet.id] ?: return@map fleet
                if (fleet.notes != "Chipolo item finder (Find Hub / Find My). A tag you clip to keys or a bag.") {
                    return@map fleet
                }
                fleet.copy(notes = stock.notes)
            }
            version = CATALOG_V96
        }
        if (version < CATALOG_V97) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V97.mapNotNull { catalog[it] }.filter { it.id !in have }
            if (extras.isNotEmpty()) fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            version = CATALOG_V97
        }
        if (version < CATALOG_V98) {
            // P1-B1: Samsung's company id is on every Galaxy phone and Buds, so SmartTag keeps only its own
            // signals (FD5A and names). The six notes that named Fieldwatch change only where still stock.
            fleets = fleets.map { fleet ->
                if (!fleet.builtIn) return@map fleet
                val stock = catalog[fleet.id] ?: return@map fleet
                when (fleet.id) {
                    // Already stripped on load (RetiredRules); kept here so the step reads as the change it is.
                    "fleet-smarttag" -> RetiredRules.strip(fleet)
                    in V97_NOTES -> fleet.copy(
                        notes = if (fleet.notes == V97_NOTES.getValue(fleet.id)) stock.notes else fleet.notes,
                        attentionNote = if (fleet.attentionNote == V97_ATTENTION[fleet.id]) stock.attentionNote else fleet.attentionNote,
                    )
                    else -> fleet
                }
            }
            version = CATALOG_V98
        }
        if (version < CATALOG_V99) {
            // P1-B2: Tile's three old rules are retired (RetiredRules strips them on load) and its new stock rules
            // (0x067C, the BLE-only name "Tile") are added by patchBuiltIn above, so this step only moves the version.
            version = CATALOG_V99
        }
        if (version < CATALOG_V100) {
            // P1-B3: AirTags' bare Offline Finding rule is retired (RetiredRules strips it on load); a new "Find My device"
            // row takes that record and the engine routes it by its status byte. That rule labeled these records until
            // now, so what the user set on it carries over: switched off, the new row's rule starts off; watched or
            // filtered on AirTags, the new row is watched or filtered too.
            val oldAirTags = cfg.fleets.firstOrNull { it.id == AIRTAGS && it.builtIn }
            // Only the rule v100 moved: the row today also holds what v107 moved (FD44, "Find My"), which v107 carries.
            val ruleOff = oldAirTags?.rules?.any { RetiredRules.retiredIn(AIRTAGS, it) == CATALOG_V100 && !it.enabled } == true
            val movedKey = ruleKey(OFFLINE_FINDING_RULE_V100)
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V100.mapNotNull { catalog[it] }.filter { it.id !in have }
                .map { if (ruleOff) it.copy(rules = it.rules.map { r -> if (ruleKey(r) == movedKey) r.copy(enabled = false) else r }) else it }
            if (extras.isNotEmpty()) fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            if (extras.isNotEmpty()) {
                val findMy = extras.single()
                val watched = watchlist.firstOrNull { it.fleetId == AIRTAGS }
                if (watched != null && watchlist.none { it.fleetId == findMy.id }) {
                    watchlist = watchlist + watched.copy(id = "watch-${findMy.id}", fleetId = findMy.id, label = findMy.name)
                }
                filter = filter.withFleetAlongside(AIRTAGS, findMy.id)
                presets = presets.map { p -> if (p.isBuiltIn()) p else p.copy(filter = p.filter.withFleetAlongside(AIRTAGS, findMy.id)) }
            }
            fleets = fleets.map { fleet ->
                if (fleet.id == AIRTAGS && fleet.builtIn && fleet.notes == V99_AIRTAG_NOTES) fleet.copy(notes = catalog.getValue(AIRTAGS).notes) else fleet
            }
            version = CATALOG_V100
        }
        if (version < CATALOG_V101) {
            // P1-B5a: RAVEN, Vigilant and IBR* are retired (RetiredRules strips them on load), and patchBuiltIn above
            // appends their replacements switched on (ADDED_RULES_V101). A user who had switched the old rule off,
            // or had SSID keywords off (the v4 policy), gets the replacements off too.
            fleets = carryRetiredOff(fleets, cfg.fleets, CATALOG_V101, ADDED_RULES_V101)
            version = CATALOG_V101
        }
        if (version < CATALOG_V102) {
            // P1-B5b: 18 short "contains" names are retired (RetiredRules strips them on load). Each row keeps the
            // anchored glob of the same brand and radio it already had; HP and MyQ also gain a glob (ADDED_RULES_V102)
            // that patchBuiltIn appends switched on, and that carries an "off" from the retired name.
            fleets = carryRetiredOff(fleets, cfg.fleets, CATALOG_V102, ADDED_RULES_V102)
            version = CATALOG_V102
        }
        if (version < CATALOG_V103) {
            // P1-B5: the last short "contains" names are retired (RetiredRules strips them on load); patchBuiltIn appends
            // the brand-as-a-word globs that replace them, which carry an "off" from the retired name.
            fleets = carryRetiredOff(fleets, cfg.fleets, CATALOG_V103, ADDED_RULES_V103)
            version = CATALOG_V103
        }
        if (version < CATALOG_V104) {
            // P1-B6: the overlay and the import used to count a rule's switch as part of the rule, so a row could end
            // up holding a rule twice, once on and once off (the "on" copy won); stock also held case variants of one
            // rule ("Eufy*", "eufy*"). Keep one copy of each, switched as mergedSwitch says.
            fleets = fleets.map(::withoutDuplicateRules)
            version = CATALOG_V104
        }
        if (version < CATALOG_V105) {
            // P1-B11a: Find Hub's 0x40 and DULT's near-owner notes no longer suggest "often your own tag" (a timer or a
            // lingering bit, not ownership), and Find Hub reads a 32-byte EID too. A decode still carrying the old stock
            // sentence gets the new stock decode; one the user edited keeps their edit. Notes are untouched.
            fleets = fleets.map { fleet ->
                val old = fleet.decode?.fields.orEmpty().any { field ->
                    field.enumNotes?.values?.any { it.contains(OLD_OWN_TAG_NOTE_V104) } == true
                }
                val stock = catalog[fleet.id]
                when {
                    !old -> fleet
                    // Unedited stock: the whole new decode (wording and the 32-byte EID).
                    fleet.builtIn && fleet.id in REFRESHED_DECODES_V105 && stock != null -> fleet.copy(decode = stock.decode)
                    // A user copy or edited decode: only the retired sentences are swapped.
                    else -> fleet.copy(decode = CatalogDecodes.withoutOwnTagNotes(fleet.decode))
                }
            }
            // The Find Hub row note said "Nearby vs separated frames"; replace it only where it's still stock.
            fleets = fleets.map { fleet ->
                if (fleet.id != "fleet-find-hub" || !fleet.notes.contains(OLD_FIND_HUB_NOTE_V104)) return@map fleet
                fleet.copy(notes = fleet.notes.replace(OLD_FIND_HUB_NOTE_V104, NEW_FIND_HUB_NOTE_V105))
            }
            version = CATALOG_V105
        }
        if (version < CATALOG_V106) {
            // P1-B11b: the Find Hub accessory row, which SignatureEngine routes dual-mode or audio/phone 0x40 frames to.
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V106.mapNotNull { catalog[it] }.filter { it.id !in have }
            if (extras.isNotEmpty()) fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            version = CATALOG_V106
        }
        if (version < CATALOG_V107) {
            // P1-B11c: AirTags' FD44 and "Find My" are retired (RetiredRules strips them on load), and patchBuiltIn above
            // appends their replacements to "Find My device" switched on. What the user set on them carries over:
            // - a moved rule switched off on AirTags, or all of them when the AirTags row was off or gone, starts off;
            // - a Find My device row the user had switched off comes back on with only its old rules off, so FD44
            //   radios AirTags labeled stay labeled while what the user silenced stays silent;
            // - watched or filtered on AirTags, Find My device is watched or filtered too (as v100 did).
            // The Find My device note changes only where still stock.
            val oldAirTags = cfg.fleets.firstOrNull { it.id == AIRTAGS && it.builtIn }
            val movedKeys = MOVED_TO_FIND_MY_V107.map { ruleKey(it.second) }.toSet()
            val off = MOVED_TO_FIND_MY_V107.filter { (retired, _) ->
                oldAirTags == null || !oldAirTags.enabled ||
                    oldAirTags.rules.any { !it.enabled && ruleKey(it) == ruleKey(retired) }
            }.map { ruleKey(it.second) }.toSet()
            fleets = fleets.map { fleet ->
                if (fleet.id != FIND_MY || !fleet.builtIn) return@map fleet
                val rowOff = !fleet.enabled && off.size < movedKeys.size
                fleet.copy(
                    enabled = fleet.enabled || rowOff,
                    notes = if (fleet.notes == V106_FIND_MY_NOTES) catalog.getValue(FIND_MY).notes else fleet.notes,
                    rules = fleet.rules.map { r ->
                        val key = ruleKey(r)
                        when {
                            key in off -> r.copy(enabled = false)
                            rowOff && key !in movedKeys -> r.copy(enabled = false)
                            else -> r
                        }
                    },
                )
            }
            val findMy = fleets.firstOrNull { it.id == FIND_MY }
            if (findMy != null && oldAirTags != null) {
                val watched = watchlist.firstOrNull { it.fleetId == AIRTAGS }
                if (watched != null && watchlist.none { it.fleetId == FIND_MY }) {
                    watchlist = watchlist + watched.copy(id = "watch-$FIND_MY", fleetId = FIND_MY, label = findMy.name)
                }
                filter = filter.withFleetAlongside(AIRTAGS, FIND_MY)
                presets = presets.map { p -> if (p.isBuiltIn()) p else p.copy(filter = p.filter.withFleetAlongside(AIRTAGS, FIND_MY)) }
            }
            version = CATALOG_V107
        }
        if (version < CATALOG_V108) {
            // P1-B11d: SmartTag's "contains" names are retired (RetiredRules strips them on load); patchBuiltIn appends
            // the whole BLE names. A retired name the user had switched off switches off its own replacements. The stock
            // row gains its FD5A decode where it has none; a decode the user wrote stays.
            val oldTag = cfg.fleets.firstOrNull { it.id == SMART_TAG && it.builtIn }
            val off = REPLACED_NAMES_V108.filter { (retired, _) ->
                oldTag?.rules?.any { !it.enabled && ruleKey(it) == ruleKey(retired) } == true
            }.values.flatten().map { ruleKey(it) }.toSet()
            fleets = fleets.map { fleet ->
                if (fleet.id != SMART_TAG || !fleet.builtIn) return@map fleet
                fleet.copy(
                    rules = fleet.rules.map { r -> if (ruleKey(r) in off) r.copy(enabled = false) else r },
                    decode = fleet.decode ?: catalog.getValue(SMART_TAG).decode,
                )
            }
            version = CATALOG_V108
        }
        if (version < CATALOG_V109) {
            // P1-B12a: Novatel, Compex and AirLink aren't law enforcement (research §1 items 7–9). Each part changes only
            // where it's still v108 stock: the class, the color (Hue.LAW), the note, the attention note (now none). The
            // default watch goes where the row ends up with no attention note; a watch the user changed stays.
            fleets = fleets.map { fleet ->
                val old = LAW_ROWS_V108[fleet.id]
                if (old == null || !fleet.builtIn) return@map fleet
                val stock = catalog.getValue(fleet.id)
                fleet.copy(
                    kind = if (fleet.kind == SignatureClass.LAW_ENFORCEMENT) stock.kind else fleet.kind,
                    colorIndex = if (fleet.colorIndex == V108_LAW_HUE) stock.colorIndex else fleet.colorIndex,
                    notes = if (fleet.notes == old.first) stock.notes else fleet.notes,
                    attentionNote = if (fleet.attentionNote == old.second) stock.attentionNote else fleet.attentionNote,
                )
            }
            val quiet = fleets.filter { it.id in LAW_ROWS_V108 && it.builtIn && it.attentionNote.isBlank() }.map { it.id }.toSet()
            watchlist = watchlist.filterNot { w ->
                val id = w.fleetId ?: return@filterNot false
                // A default watch's label is the stock name (the same in v108), whatever the user renamed the row to.
                id in quiet && w == WatchTarget(id = "watch-$id", fleetId = id, label = catalog.getValue(id).name)
            }
            version = CATALOG_V109
        }
        if (!settings.darkTheme) settings = settings.copy(darkTheme = true)
        if (settings.scanControlsExpanded) settings = settings.copy(scanControlsExpanded = false)
        presets = presets.filterNot { it.isBuiltIn() && it.id in hiddenPresetIds }
            .distinctBy { it.id }
        val fleetsChanged = fleets != cfg.fleets
        val presetsChanged = presets != cfg.presets
        val versionChanged = version != cfg.version
        val filterChanged = filter != cfg.filter
        val watchChanged = watchlist != cfg.watchlist
        val settingsChanged = settings != cfg.settings
        val hiddenChanged = hiddenPresetIds != cfg.hiddenPresetIds
        return when {
            !fleetsChanged && !presetsChanged && !versionChanged && !filterChanged &&
                !watchChanged && !settingsChanged && !hiddenChanged -> cfg
            else -> cfg.copy(
                version = version,
                fleets = fleets,
                presets = presets,
                filter = filter,
                watchlist = watchlist,
                settings = settings,
                hiddenPresetIds = hiddenPresetIds,
            )
        }
    }

    /**
     * [fleets] with each built-in row's newly [added] name globs switched off where that row in [before] (the config
     * as loaded, before RetiredRules stripped it) had a retired rule switched off: the user's "off" on the rule a
     * catalog step replaced carries to its replacements.
     */
    private fun carryRetiredOff(fleets: List<Fleet>, before: List<Fleet>, version: Int, added: Map<String, List<MatchRule>>): List<Fleet> =
        fleets.map { fleet ->
            val rules = added[fleet.id]
            if (rules == null || !fleet.builtIn) return@map fleet
            val old = before.firstOrNull { it.id == fleet.id } ?: return@map fleet
            // Only a rule this version retired: an older one switched off ("Yale", v102) says nothing about "Seos" (v103).
            if (old.rules.none { !it.enabled && RetiredRules.retiredIn(fleet.id, it) == version }) return@map fleet
            // By kind, text and radio: BMW's Wi-Fi "BMW_*" isn't the BLE "BMW_*" v103 added.
            val keys = rules.map { ruleKey(it) }.toSet()
            fleet.copy(rules = fleet.rules.map { r -> if (ruleKey(r) in keys) r.copy(enabled = false) else r })
        }

    private fun ruleKey(rule: MatchRule): String = rule.matchKey()

    /** [fleet] with each rule held once ([withoutDuplicates]); the same instance when it has no duplicate. */
    private fun withoutDuplicateRules(fleet: Fleet): Fleet {
        val rules = fleet.rules.withoutDuplicates()
        return if (rules === fleet.rules) fleet else fleet.copy(rules = rules)
    }

    private fun seed(): PersistedConfig {
        val fleets = DefaultCatalog.fleets()
        return PersistedConfig(
            version = CATALOG_VERSION,
            fleets = fleets,
            filter = FilterState(),
            presets = FilterEngine().defaultPresets(fleets),
            watchlist = DefaultCatalog.defaultWatchlist(),
            settings = AppSettings(liveTourRev = LIVE_TOUR_REV),
            takInstallTag = TakInstall.newTag(),
            takLegacySelfRetired = true,
        )
    }

    companion object {
        /** Stock catalog generation. Settings footer and the GitHub pack use this. */
        const val CATALOG_VERSION = 109
        private const val CATALOG_V2 = 2
        private const val CATALOG_V3 = 3
        private const val CATALOG_V4 = 4
        private const val CATALOG_V5 = 5
        private const val CATALOG_V6 = 6
        private const val CATALOG_V7 = 7
        private const val CATALOG_V8 = 8
        private const val CATALOG_V9 = 9
        private const val CATALOG_V10 = 10
        private const val CATALOG_V11 = 11
        private const val CATALOG_V12 = 12
        private const val CATALOG_V13 = 13
        private const val CATALOG_V14 = 14
        private const val CATALOG_V15 = 15
        private const val CATALOG_V16 = 16
        private const val CATALOG_V17 = 17
        private const val CATALOG_V18 = 18
        private const val CATALOG_V20 = 20
        private const val CATALOG_V21 = 21
        private const val CATALOG_V22 = 22
        private const val CATALOG_V23 = 23
        private const val CATALOG_V24 = 24
        private const val CATALOG_V25 = 25
        private const val CATALOG_V26 = 26
        private const val CATALOG_V27 = 27
        private const val CATALOG_V28 = 28
        private const val CATALOG_V29 = 29
        private const val CATALOG_V30 = 30
        private const val CATALOG_V31 = 31
        private const val CATALOG_V32 = 32
        private const val CATALOG_V33 = 33
        private const val CATALOG_V34 = 34
        private const val CATALOG_V35 = 35
        private const val CATALOG_V36 = 36
        private const val CATALOG_V37 = 37
        private const val CATALOG_V38 = 38
        private const val CATALOG_V39 = 39
        private const val CATALOG_V40 = 40
        private const val CATALOG_V41 = 41
        private const val CATALOG_V42 = 42
        private const val CATALOG_V43 = 43
        private const val CATALOG_V44 = 44
        private const val CATALOG_V45 = 45
        private const val CATALOG_V46 = 46
        private const val CATALOG_V47 = 47
        private const val CATALOG_V48 = 48
        private const val CATALOG_V49 = 49
        private const val CATALOG_V50 = 50
        private const val CATALOG_V51 = 51
        private const val CATALOG_V52 = 52
        private const val CATALOG_V53 = 53
        private const val CATALOG_V54 = 54
        private const val CATALOG_V55 = 55
        private const val CATALOG_V56 = 56
        private const val CATALOG_V57 = 57
        private const val CATALOG_V58 = 58
        private const val CATALOG_V59 = 59
        private const val CATALOG_V60 = 60
        private const val CATALOG_V61 = 61
        private const val CATALOG_V62 = 62
        private const val CATALOG_V63 = 63
        private const val CATALOG_V64 = 64
        private const val CATALOG_V65 = 65
        private const val CATALOG_V66 = 66
        private const val CATALOG_V67 = 67
        private const val CATALOG_V68 = 68
        private const val CATALOG_V69 = 69
        private const val CATALOG_V70 = 70
        private const val CATALOG_V71 = 71
        private const val CATALOG_V72 = 72
        private const val CATALOG_V73 = 73
        private const val CATALOG_V74 = 74
        private const val CATALOG_V75 = 75
        private const val CATALOG_V76 = 76
        private const val CATALOG_V77 = 77
        private const val CATALOG_V78 = 78
        private const val CATALOG_V79 = 79
        private const val CATALOG_V80 = 80
        private const val CATALOG_V81 = 81
        private const val CATALOG_V82 = 82
        private const val CATALOG_V83 = 83
        private const val CATALOG_V84 = 84
        private const val CATALOG_V85 = 85
        private const val CATALOG_V86 = 86
        private const val CATALOG_V87 = 87
        private const val CATALOG_V88 = 88
        private const val CATALOG_V89 = 89
        private const val CATALOG_V90 = 90
        private const val CATALOG_V91 = 91
        private const val CATALOG_V92 = 92
        private const val CATALOG_V93 = 93
        private const val CATALOG_V94 = 94
        private const val CATALOG_V95 = 95
        private const val CATALOG_V96 = 96
        private const val CATALOG_V97 = 97
        private const val CATALOG_V98 = 98
        private const val CATALOG_V99 = 99
        private const val CATALOG_V100 = 100
        private const val CATALOG_V101 = 101
        private const val CATALOG_V102 = 102
        private const val CATALOG_V103 = 103
        private const val CATALOG_V104 = 104
        private const val CATALOG_V105 = 105
        private const val CATALOG_V106 = 106
        private const val CATALOG_V107 = 107
        private const val CATALOG_V108 = 108
        private const val CATALOG_V109 = CATALOG_VERSION

        /**
         * P1-B12a: the rows v109 takes out of law enforcement, with their exact v108 stock note and attention note
         * (catalog/snapshots/v108.json). v109 replaces a text only where it's still equal.
         */
        /** Their v108 stock color, Hue.LAW (teal), written out. */
        private const val V108_LAW_HUE = 7

        private val LAW_ROWS_V108 = mapOf(
            "fleet-airlink" to (
                "Sierra Wireless AirLink vehicle / fleet gateway. Common in public-safety and commercial fleets. Hidden SSIDs still hit on the board vendor." to
                    "Sierra Wireless AirLink vehicle gateway. Common in US police and public-safety fleets; also commercial fleet. Hidden or renamed SSIDs still hit on the Sierra Wireless IEEE OUI. Pattern match, not that agency or that car. Look with your eyes."
                ),
            "fleet-compex" to (
                "Compex vehicle or agency Wi-Fi access point. Same boards appear on other Compex radios." to
                    "Compex Wi-Fi AP. Some US public-safety agencies use these in vehicles. The same IEEE OUIs appear on other Compex radios. Pattern match, not that agency. Look with your eyes."
                ),
            "fleet-novatel" to (
                "Novatel Wireless / Inseego vehicle radio. The same prefix is also on some consumer MiFi hotspots." to
                    "Novatel Wireless / Inseego OUI 28:80:A2. Reported in public-safety vehicle AP work. Same prefix is also on some Inseego consumer MiFi radios. Pattern match, not that agency. Look with your eyes."
                ),
        )

        /**
         * SmartTag's "contains" names v108 retired (P1-B11d), each with the whole BLE names that replace it: an "off" on
         * one carries only to its own replacements. Written out.
         */
        private val REPLACED_NAMES_V108 = mapOf(
            MatchRule(RuleKind.NAME_CONTAINS, text = "SmartTag") to globs(RadioKind.BLE, "Galaxy SmartTag*"),
            MatchRule(RuleKind.NAME_CONTAINS, text = "Smart Tag") to globs(RadioKind.BLE, "Smart Tag", "Smart Tag2"),
            MatchRule(RuleKind.NAME_CONTAINS, text = "Galaxy SmartTag") to globs(RadioKind.BLE, "Galaxy SmartTag*"),
        )

        /** P1-B11b: Find Hub headphones and phones get their own row, so their 0x40 frames stop reading as a tag. */
        private val ADDED_IN_V106 = listOf("fleet-find-hub-accessory")

        /**
         * P1-B11c: the AirTags rules v107 retired and the "Find My device" rule that replaces each. Written out, not read
         * from DefaultCatalog, so a later catalog change can't rewrite what v107 moved.
         */
        private val MOVED_TO_FIND_MY_V107 = listOf(
            MatchRule(RuleKind.NAME_CONTAINS, text = "Find My") to MatchRule(RuleKind.NAME_CONTAINS, text = "Find My", radio = RadioKind.BLE),
            MatchRule(RuleKind.SERVICE_UUID, text = "FD44") to MatchRule(RuleKind.SERVICE_UUID, text = "FD44"),
        )

        /** The exact v106 stock "Find My device" note (catalog/snapshots/v106.json). v107 rewords it only where still equal. */
        private const val V106_FIND_MY_NOTES = "Apple Find My network tag or accessory (Offline Finding) whose type isn't " +
            "AirTag, iPhone, Mac or AirPods: often a third-party Find My tag. This row's rule also finds nameless AirTags, " +
            "iPhones and AirPods for their own rows; switching it off stops those too. Addresses rotate."

        /** Rows whose stock decode v105 refreshes (P1-B11a). */
        private val REFRESHED_DECODES_V105 = setOf("fleet-find-hub", "fleet-dult")

        /** The sentence the v104 stock Find Hub and DULT notes shared; still present means the decode is unedited stock. */
        private const val OLD_OWN_TAG_NOTE_V104 = "With you the whole sit, this is often your own tag."

        private const val OLD_FIND_HUB_NOTE_V104 = "Nearby vs separated frames."
        private const val NEW_FIND_HUB_NOTE_V105 =
            "Frames show whether unwanted-tracking protection is on (separated) or not yet on."

        /**
         * The exact v97 stock wording of the notes v98 reworded (P1-B1), from catalog/snapshots/v97.json. A note still
         * equal to it is replaced; anything else is the user's and stays. Fixed text, so a later rewording can't move it.
         */
        private val V97_NOTES = mapOf(
            "fleet-sony" to "Sony headphones, TV, or camera. Bravia TVs also send a generic iBeacon for Cast — Fieldwatch keeps Sony.",
            "fleet-gotenna" to "goTenna Mesh or Pro companion radio. Pairs over BLE; the mesh itself is UHF and Fieldwatch cannot hear it. Pro is sold to agencies. Pattern match, not that operator.",
            "fleet-bluetoad" to "Iteris roadside Bluetooth travel-time reader (Vantage Velocity, now BlueTOAD Spectra / Spectra CV). Samples phones, headsets, and in-car Bluetooth as vehicles pass; matching the same ID at two points gives speed. About 100 m. A sample, not a full count. Quiet or Ethernet-only cabinets may not advertise. Spectra CV also uses 5.9 GHz C-V2X, which Fieldwatch cannot hear. IEEE Iteris OUI can also hit other Iteris roadside kit. Pattern match, not that cabinet.",
            "fleet-hobby-ble" to "Cheap hobby UART module advertised under a default name (HM-10, JDY, CC41, ESP32 BLE). Common on printers, cars, and DIY. Fieldwatch does not see Classic HC-05/HC-06.",
            "fleet-cisco" to "Cisco Aironet / Catalyst / Business access point. Fieldwatch sees AP beacons, not phones or switches. Meraki has its own row.",
        )
        private val V97_ATTENTION = mapOf(
            "fleet-hobby-ble" to "These default BLE serial names are cheap hobby modules. Some pump/ATM overlays have used boards like this so someone nearby can pull data over Bluetooth. The same modules show up on printers, cars, and DIY. If this radio is loud next to a card reader, treat it with caution and look with your eyes. Not proof of a skimmer. A miss is not a clean bill (name changed, Classic HC-05/HC-06, or cellular). Fieldwatch does not connect and does not try default PINs. Fieldwatch does not see Classic HC-05/HC-06.",
        )
        private val GENERIC_GATT_UUIDS = setOf("180A", "180D", "180F")
        private val POLICY_FLEET_IDS = setOf(
            "fleet-flock-cameras",
            "fleet-liteon-camera-radio",
            "fleet-raven",
            "fleet-fs-ext-battery",
            "fleet-penguin",
            "fleet-pigvision",
        )
        private val ADDED_IN_V2 = listOf(
            "fleet-chipolo",
            "fleet-pebblebee",
            "fleet-verkada",
            "fleet-vigilant",
            "fleet-eufy",
            "fleet-wyze",
            "fleet-ring",
            "fleet-arlo",
            "fleet-nest",
            "fleet-tapo",
            "fleet-reolink",
            "fleet-hikvision",
            "fleet-dahua",
        )
        private val ADDED_IN_V3 = listOf(
            "fleet-meshtastic",
            "fleet-helium",
            "fleet-genetec",
            "fleet-rekor",
            "fleet-axon",
            "fleet-avigilon",
            "fleet-axis",
            "fleet-unifi",
        )
        private val ADDED_IN_V6 = listOf(
            "fleet-hobby-ble",
        )
        private val ADDED_IN_V8 = listOf(
            "fleet-watchguard",
            "fleet-meta-glasses",
            "fleet-snap-spectacles",
        )
        private val ADDED_IN_V9 = listOf(
            "fleet-hak5-pineapple",
            "fleet-flipper",
            "fleet-pwnagotchi",
            "fleet-marauder",
        )
        private val ADDED_IN_V10 = listOf(
            "fleet-porkchop",
        )
        private val ADDED_IN_V12 = listOf(
            "fleet-apple-device",
            "fleet-apple-audio",
            "fleet-microsoft",
        )
        private val ADDED_IN_V14 = listOf(
            "fleet-tesla",
            "fleet-google",
            "fleet-sony",
            "fleet-bose",
            "fleet-garmin",
            "fleet-amazon",
            "fleet-fitbit",
            "fleet-oura",
            "fleet-logitech",
            "fleet-jbl",
            "fleet-sonos",
            "fleet-gopro",
            "fleet-dji",
        )
        private val ADDED_IN_V15 = listOf(
            "fleet-netgear",
            "fleet-tplink",
            "fleet-asus",
            "fleet-linksys",
            "fleet-eero",
            "fleet-google-wifi",
            "fleet-dlink",
            "fleet-belkin",
            "fleet-xfinity",
            "fleet-spectrum",
            "fleet-att",
            "fleet-verizon",
            "fleet-starlink",
            "fleet-meraki",
            "fleet-glinet",
        )
        private val ADDED_IN_V16 = listOf(
            "fleet-unifi-ap",
        )
        private val ADDED_IN_V21 = listOf(
            "fleet-nest-thermostat",
            "fleet-ecobee",
            "fleet-sensi",
            "fleet-honeywell-home",
        )
        private val ADDED_IN_V22 = listOf(
            "fleet-nest-weave",
        )
        private val ADDED_IN_V23 = listOf(
            "fleet-haiku",
            "fleet-tuya",
            "fleet-seos",
            "fleet-myq",
            "fleet-chevrolet",
            "fleet-rivian",
            "fleet-govee",
            "fleet-hp",
            "fleet-mbux",
            "fleet-motive",
        )
        private val ADDED_IN_V24 = listOf(
            "fleet-august",
            "fleet-schlage",
            "fleet-nuki",
            "fleet-salto",
            "fleet-dormakaba",
            "fleet-lockly",
            "fleet-kevo",
            "fleet-master-lock",
            "fleet-igloohome",
            "fleet-tedee",
            "fleet-paxton",
            "fleet-kwikset",
        )
        private val ADDED_IN_V25 = listOf(
            "fleet-ibeacon",
            "fleet-minew",
            "fleet-estimote",
            "fleet-kontakt",
        )
        private val ADDED_IN_V28 = listOf(
            "fleet-goodyear",
            "fleet-schrader",
            "fleet-pacific-tpms",
            "fleet-huf",
            "fleet-fobo",
            "fleet-ruuvi",
            "fleet-bluemaestro",
            "fleet-sensorpush",
            "fleet-samsara",
        )
        private val ADDED_IN_V31 = listOf(
            "fleet-pokemon-go-plus",
            "fleet-hatch",
            "fleet-bhyve",
            "fleet-fieldy",
            "fleet-plaud",
            "fleet-retail-led-sign",
            "fleet-esl",
        )
        private val ADDED_IN_V32 = listOf(
            "fleet-unifi-protect",
            "fleet-tesla-tstpms",
            "fleet-radiacode",
            "fleet-lg-webos",
            "fleet-nespresso",
            "fleet-epson",
            "fleet-shokz",
        )
        private val ADDED_IN_V33 = listOf(
            "fleet-remote-id",
            "fleet-skydio",
            "fleet-autel",
            "fleet-parrot",
            "fleet-hoverair",
            "fleet-cradlepoint",
            "fleet-airlink",
        )
        private val ADDED_IN_V35 = listOf(
            "fleet-cisco",
            "fleet-aruba",
            "fleet-ruckus",
            "fleet-fortinet",
            "fleet-mikrotik",
            "fleet-engenius",
            "fleet-zyxel",
            "fleet-peplink",
            "fleet-openwrt",
            "fleet-arris",
        )
        private val ADDED_IN_V36 = listOf(
            "fleet-mist",
            "fleet-tmobile",
            "fleet-humax",
            "fleet-sagemcom",
            "fleet-arcadyan",
            "fleet-askey",
            "fleet-calix",
            "fleet-nokia",
            "fleet-airties",
            "fleet-tenda",
            "fleet-sercomm",
            "fleet-luxul",
            "fleet-sophos",
            "fleet-aumovio",
            "fleet-centurylink",
            "fleet-gm-hotspot",
            "fleet-audi-mmi",
        )
        private val ADDED_IN_V37 = listOf(
            "fleet-extreme",
            "fleet-adtran",
            "fleet-cambium",
            "fleet-trendnet",
            "fleet-cudy",
            "fleet-snapav",
            "fleet-vantiva",
            "fleet-hitron",
            "fleet-actiontec",
            "fleet-buffalo",
            "fleet-grandstream",
            "fleet-edgecore",
            "fleet-watchguard-ap",
            "fleet-mojo",
            "fleet-winegard",
            "fleet-inseego",
            "fleet-synology",
        )
        private val ADDED_IN_V38 = listOf(
            "fleet-compex",
            "fleet-novatel",
            "fleet-utility-inc",
        )
        private val ADDED_IN_V39 = listOf(
            "fleet-fast-pair",
        )
        private val ADDED_IN_V40 = listOf(
            "fleet-ford",
            "fleet-honda",
            "fleet-hyundai",
            "fleet-toyota",
            "fleet-nissan",
            "fleet-subaru",
            "fleet-bmw",
            "fleet-volkswagen",
            "fleet-porsche",
            "fleet-jlr",
            "fleet-byd",
        )
        private val ADDED_IN_V43 = listOf(
            "fleet-osmo",
            "fleet-insta360",
        )
        private val ADDED_IN_V50 = listOf(
            "fleet-ruijie",
            "fleet-dwnet",
            "fleet-wavlink",
            "fleet-peoplenet",
            "fleet-uconnect",
            "fleet-carplay",
        )
        private val ADDED_IN_V51 = listOf(
            "fleet-roku",
        )
        private val ADDED_IN_V52 = listOf(
            "fleet-franklin",
        )
        private val ADDED_IN_V53 = listOf(
            "fleet-samsung-appliance",
            "fleet-ecowater",
        )
        private val ADDED_IN_V54 = listOf(
            "fleet-carlink",
        )
        private val ADDED_IN_V55 = listOf(
            "fleet-target-atrius",
        )
        private val ADDED_IN_V59 = listOf(
            "fleet-phone-hotspot",
            "fleet-huawei",
            "fleet-plume",
        )
        private val ADDED_IN_V60 = listOf(
            "fleet-honeywell-xenon-hc",
            "fleet-omron",
            "fleet-withings",
            "fleet-dexcom",
        )
        private val ADDED_IN_V69 = listOf(
            "fleet-bluetoad",
        )
        private val ADDED_IN_V70 = listOf(
            "fleet-bliptrack",
            "fleet-hanwha-wisenet",
            "fleet-uniview",
            "fleet-rhombus",
            "fleet-meshcore",
            "fleet-gotenna",
            "fleet-sensecap",
            "fleet-rak-wisgate",
            "fleet-ghostesp",
            "fleet-bruce",
        )
        private val ADDED_IN_V74 = listOf(
            "fleet-digital-ally",
            "fleet-reveal-media",
            "fleet-wolfcom",
            "fleet-panasonic-ipro",
            "fleet-limitless",
            "fleet-bee",
            "fleet-omi",
            "fleet-friend-pendant",
            "fleet-brilliant-frame",
            "fleet-even-g1",
            "fleet-hayden-ai",
            "fleet-miovision",
            "fleet-tattile",
            "fleet-lvt",
        )
        private val ADDED_IN_V76 = listOf(
            "fleet-tpms-ble",
            "fleet-sytpms",
            "fleet-tirecheck",
            "fleet-tpms-service",
        )
        private val TPMS_V76_REFRESH = setOf(
            "fleet-goodyear",
            "fleet-schrader",
            "fleet-pacific-tpms",
            "fleet-huf",
            "fleet-fobo",
            "fleet-tesla-tstpms",
        )
        private val ADDED_IN_V77 = listOf(
            "fleet-vuzix",
        )
        private val CATALOG_V77_REFRESH = setOf(
            "fleet-flock-cameras",
            "fleet-fs-ext-battery",
            "fleet-penguin",
            "fleet-raven",
            "fleet-remote-id",
            "fleet-meta-glasses",
            "fleet-snap-spectacles",
            "fleet-axon",
        )
        private val ADDED_IN_V82 = listOf(
            "fleet-liteon-camera-radio",
        )
        private val ADDED_IN_V78 = listOf(
            "fleet-find-hub",
        )
        private val ADDED_IN_V84 = listOf(
            "fleet-dult",
        )
        private val ADDED_IN_V89 = listOf(
            "fleet-rayneo",
        )
        private val ADDED_IN_V90 = listOf(
            "fleet-tello",
            "fleet-potensic",
            "fleet-holystone",
            "fleet-hubsan",
            "fleet-yuneec",
            "fleet-swellpro",
            "fleet-crazyflie",
        )
        private val ADDED_IN_V91 = listOf(
            "fleet-dji-power",
        )
        private val ADDED_IN_V94 = listOf(
            "fleet-polar",
        )
        private val ADDED_IN_V97 = listOf(
            "fleet-bitchat",
        )
        private val ADDED_IN_V100 = listOf(
            "fleet-findmy",
        )
        private const val AIRTAGS = "fleet-airtag"
        private const val FIND_MY = "fleet-findmy"
        private const val SMART_TAG = "fleet-smarttag"

        /** The bare Offline Finding rule v100 moved from AirTags to Find My device (P1-B3), written out. */
        private val OFFLINE_FINDING_RULE_V100 = MatchRule(RuleKind.MANUFACTURER_DATA, companyId = 0x004C, dataPrefixHex = "12")

        private fun globs(radio: RadioKind?, vararg texts: String): List<MatchRule> =
            texts.map { MatchRule(RuleKind.NAME_GLOB, text = it, radio = radio) }

        /** The name globs v101 added in place of each retired name rule (P1-B5a), by fleet. */
        private val ADDED_RULES_V101 = mapOf(
            "fleet-raven" to globs(null, "RAVEN", "RAVEN-*", "RAVEN_*") + globs(RadioKind.WIFI, "DIRECT-??-Raven-*"),
            "fleet-vigilant" to globs(null, "Vigilant", "Vigilant-*", "Vigilant_*"),
            "fleet-cradlepoint" to globs(RadioKind.WIFI, "IBR350*", "IBR1150*"),
        )

        /** The name globs v102 added beside the anchored globs that replace a retired short name (P1-B5b). */
        private val ADDED_RULES_V102 = mapOf(
            "fleet-hp" to globs(RadioKind.BLE, "HP ENVY*"),
            "fleet-myq" to globs(RadioKind.BLE, "MyQ"),
        )

        /**
         * The brand-as-a-word globs v103 added in place of each retired short name (P1-B5), all BLE. Written out, not
         * built from DefaultCatalog.bleWordStart, so a later change to that helper can't rewrite what v103 added.
         */
        private val ADDED_RULES_V103 = mapOf(
            "fleet-amazon" to globs(RadioKind.BLE, "Echo", "Echo *", "Echo-*", "Echo_*"),
            "fleet-audi-mmi" to globs(RadioKind.BLE, "Audi", "Audi *", "Audi-*", "Audi_*"),
            "fleet-bmw" to globs(RadioKind.BLE, "BMW", "BMW *", "BMW-*", "BMW_*"),
            "fleet-bose" to globs(RadioKind.BLE, "Bose", "Bose *", "Bose-*", "Bose_*", "LE-Bose*"),
            "fleet-byd" to globs(RadioKind.BLE, "BYD", "BYD *", "BYD-*", "BYD_*"),
            "fleet-ford" to globs(RadioKind.BLE, "Ford", "Ford *", "Ford-*", "Ford_*"),
            "fleet-hobby-ble" to globs(RadioKind.BLE, "BT05", "BT05 *", "BT05-*", "BT05_*"),
            "fleet-jbl" to globs(RadioKind.BLE, "JBL", "JBL *", "JBL-*", "JBL_*"),
            "fleet-logitech" to globs(RadioKind.BLE, "Logi", "Logi *", "Logi-*", "Logi_*"),
            "fleet-microsoft" to globs(RadioKind.BLE, "Xbox", "Xbox *", "Xbox-*", "Xbox_*"),
            "fleet-oura" to globs(RadioKind.BLE, "Oura", "Oura *", "Oura-*", "Oura_*"),
            "fleet-seos" to globs(RadioKind.BLE, "Seos", "Seos *", "Seos-*", "Seos_*"),
            "fleet-sony" to globs(RadioKind.BLE, "Sony", "Sony *", "Sony-*", "Sony_*"),
            "fleet-volkswagen" to globs(RadioKind.BLE, "VW", "VW *", "VW-*", "VW_*"),
        )

        /** The exact v99 stock AirTags note (catalog/snapshots/v99.json). v100 rewords it only where still equal. */
        private const val V99_AIRTAG_NOTES = "Apple AirTag or Find My accessory. iPhones also send Find My so they can be " +
            "located — that stays on Apple Device unless the name is AirTag. Addresses rotate."

        /** Where a filter names [old] in a fleet set, it names [added] too (a row that takes over part of [old]'s radios). */
        private fun FilterState.withFleetAlongside(old: String, added: String): FilterState = copy(
            fleetIds = if (old in fleetIds) fleetIds + added else fleetIds,
            includeFleetIds = if (old in includeFleetIds) includeFleetIds + added else includeFleetIds,
        )
    }
}

/**
 * Catalog 93 drops loose Flock and Penguin names, Silicon Labs prefixes on
 * FS Ext Battery, and the unassigned LiteOn prefix. A rule the operator added
 * with different text stays. Stock notes are copied so the editor matches.
 */
internal fun catalog93Fleet(fleet: Fleet, stock: Fleet): Fleet {
    if (!fleet.builtIn || fleet.id != stock.id) return fleet
    val rules = fleet.rules.filterNot { rule ->
        when (fleet.id) {
            "fleet-flock-cameras" ->
                (rule.kind == RuleKind.NAME_CONTAINS && rule.text.uppercase() in FLOCK_LOOSE_NAMES) ||
                    (rule.kind == RuleKind.NAME_GLOB && rule.text == "Flock-??????")
            "fleet-penguin" ->
                rule.kind == RuleKind.NAME_CONTAINS && rule.text.equals("Penguin", true) ||
                    rule.kind == RuleKind.NAME_GLOB && rule.text.equals("Penguin*", true)
            "fleet-fs-ext-battery" ->
                (rule.kind == RuleKind.OUI && rule.text.uppercase() in FS_EXT_SILABS_OUIS) ||
                    (rule.kind == RuleKind.NAME_GLOB && rule.text.equals("FS_*", true))
            "fleet-liteon-camera-radio" ->
                rule.kind == RuleKind.OUI && rule.text.equals("B8:35:32", true)
            else -> false
        }
    }
    return when (fleet.id) {
        "fleet-flock-cameras", "fleet-penguin", "fleet-fs-ext-battery", "fleet-liteon-camera-radio" ->
            fleet.copy(
                rules = rules,
                notes = stock.notes,
                attentionNote = stock.attentionNote,
            )
        else -> fleet
    }
}

private val FS_EXT_SILABS_OUIS = setOf(
    "04:0D:84", "1C:34:F1", "38:5B:44", "94:34:69", "B4:E3:F9", "F0:82:C0",
)

private val FLOCK_LOOSE_NAMES = setOf("FLOCK", "FLCK", "CONDOR", "FALCON", "SPARROW")
