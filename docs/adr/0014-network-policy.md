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
  | `SENSOR_LIST` | always. Every download follows a dialog that names the source and its terms (P0-7c); that confirmation is the consent. The "Other sensors" list stays off through its blank URL and feature flag. |
  | `TAK_PUBLISH` | `takEnabled` and not Privacy |
  | `TAK_RETRACT` | always. When the feed or Privacy turns off, tombstones withdraw the spots the feed already published, so teammates' maps stop showing them. Blocking them would leave the user's spots visible. |

- **`EgressInventoryTest`** fails when network code appears outside these five files, when one of them stops asking the policy, or when a caller hard-codes `onlineLookup = true`.
- **The V17, V19 and V20 ladder bodies are deleted;** their version bumps stay. Upgrades keep the user's choices.

## Consequences
- **Settings changes take effect at once:** turning "Online place names and maps" off stops tile and place traffic on the next request, even for work a screen already started.
- **Old installs keep their choices:** configs older than V17 or V20 no longer get signatures, location tags, screen-on or online lookups forced back on.
- **Not covered here:**
  - V30 still re-enables every fleet once. It isn't a network setting; a later catalog step can revisit it.
  - The OS network location provider (`LocationManager.NETWORK_PROVIDER`) may contact Google. It is a platform service, not app traffic, and it is outside this policy.
- **New network code** must add an `Egress` kind, a rule here, and an entry in `EgressInventoryTest`.
