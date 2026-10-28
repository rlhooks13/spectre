# ADR-0014: One network policy, checked where traffic leaves

- **Status:** Accepted, 2026-10-27
- **Context:**
  - The app sends traffic from five places: map tiles (`PathTiles`), reverse geocoding (`PlaceLookup`), the stock catalog (`CatalogRemote`), sensor lists (`FlockFiles`) and TAK Cursor-on-Target (`TakPublisher`).
  - The guards were spread across screens and ViewModel callers.
    - **Stale snapshots:** five tile paths passed a literal `onlineLookup = true` and relied on an `online` value the screen captured when it composed. Queued tile windows and pan prefetches also ran without re-checking.
  - The settings ladder forced choices back on during upgrades:
    - V17 re-enabled every built-in signature the user had turned off;
    - V19 turned `tagLocation` back on;
    - V20 turned `keepScreenOn` and `onlineLookup` back on.

## Decision
- **`NetworkPolicy.allows(egress)`** is checked inside each egress function, right before it connects, against the settings in force.
  - `ConfigStore` publishes every config change to it.
  - Before settings load, nothing is allowed.
- **Rules per egress:**

  | Egress | Allowed when |
  |---|---|
  | `MAP_TILES` | `onlineLookup` |
  | `PLACE_NAMES` | `onlineLookup` and not Privacy (`demoMode`), as the callers already required |
  | `CATALOG_UPDATE` | `Features.IN_APP_CATALOG_UPDATE` (off until P4-6) |
  | `SENSOR_LIST` | always. The plate-reader list makes no request: it is a bundled OpenStreetMap snapshot (ADR-0018, which supersedes the P0-7c dialog here). For the "Other sensors" list, every request follows a user action in the layer's Settings block, which names the source host. Download follows a confirmation dialog with the source and its terms. The update check (a HEAD request) follows the user's Update tap. That list stays off through its blank URL and feature flag until P1-E5. |
  | `TAK_PUBLISH` | `takEnabled` and not Privacy. This covers the DNS lookup of the TAK host as well as the packets. |
  | `TAK_RETRACT` | always. When the feed or Privacy turns off, tombstones withdraw the spots the feed already published, so teammates' maps stop showing them. Blocking them would leave the user's spots visible. |

- **`PathTiles` asks the policy itself.** Its `onlineLookup` parameter is gone, so no caller can pass a stale or hard-coded value.
- **`ConfigStore` sets the policy before it publishes a config,** so nothing reacting to new settings finds the policy behind. Only `ConfigStore` can set it.
- **`EgressInventoryTest`** scans every non-test source set, Kotlin and Java. It fails when network code appears outside these five files, when one of them stops asking the policy, or when a caller hard-codes `onlineLookup = true`. It looks for:
  - connections and streams: `openConnection`, `openStream`, `URL(...).readText`, sockets, `SocketChannel`;
  - DNS: `InetAddress.get…ByName`;
  - geocoding: `getFromLocation…`;
  - web clients: WebView, DownloadManager, HTTP client libraries.
- **The V17, V19 and V20 ladder bodies are deleted;** their version bumps stay. `SettingsLadderTest` upgrades a V16 config and checks that `onlineLookup`, `tagLocation` and `keepScreenOn` stay off.

## Consequences
- **Settings changes take effect at once:** turning "Online place names and maps" off stops tile and place traffic on the next request, even for work a screen already started.
- **Old installs keep their settings:** configs older than V20 no longer get location tags, screen-on or online lookups forced back on.
- **Signatures are still re-enabled once:** V30 re-enables every fleet once, as part of its move from the fleet filter to the include filter. So a config older than V30 still gets its signatures back on. It isn't a network setting, and changing it is a detection-behavior decision for a later catalog step (follow-up).
- **Not covered here:**
  - **The OS network location provider** (`LocationManager.NETWORK_PROVIDER`) may contact Google. It is a platform service, not app traffic, and it is outside this policy.
  - **Redirects:** `CatalogRemote` and `FlockFiles` follow redirects. The policy still applies, because the egress kind doesn't change, but the final host is checked only after the request: `FlockFiles.servedFromSource` refuses to install a list from elsewhere. Hop-by-hop host checks like `PathTiles`' are a follow-up.
  - **Where tombstones go:** they go to the TAK host configured now. If the user changes the host and then turns the feed off, they go to the new host. Remembering each spot's destination is a follow-up.
  - **A mid-request switch-off:** a refusal returns the egress function's normal failure value. A tile export or a place lookup interrupted by turning the setting off can report it as a failed map or "no name returned". The next request is correct.
- **New network code** must add an `Egress` kind, a rule here, and an entry in `EgressInventoryTest`.
