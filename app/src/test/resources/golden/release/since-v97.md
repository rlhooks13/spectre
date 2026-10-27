<!-- generated: begin -->
<!-- generated: catalog v97 -> v101 -->
### Signatures (stock catalog v97 → v101)

- **New:** Find My device (finder) matches on Bluetooth company id 0x004C data starting 12.
- **Apple AirTags** (finder): stopped matching on Bluetooth company id 0x004C data starting 12; notes reworded.
- **BlueTOAD Spectra** (surveillance): notes reworded.
- **Cisco** (isp): notes reworded.
- **Cradlepoint** (law enforcement): stopped matching on name glob "IBR*" (WIFI).
- **goTenna** (mesh): notes reworded.
- **Hobby BLE serial** (hacking): attention note reworded; notes reworded.
- **Raven / ShotSpotter** (surveillance): stopped matching on name contains "RAVEN"; now also matches on name glob "RAVEN-*"; name glob "RAVEN_*"; name glob "DIRECT-*-Raven-*" (WIFI).
- **Samsung SmartTags** (finder): stopped matching on Bluetooth company id 0x0075.
- **Sony** (audio): notes reworded.
- **Tile Trackers** (finder): stopped matching on name contains "Tile"; service uuid "FEDD"; Bluetooth company id 0x00C7; now also matches on name glob "Tile" (BLE); Bluetooth company id 0x067C.
- **Motorola Vigilant** (surveillance): stopped matching on name contains "Vigilant"; now also matches on name glob "Vigilant-*"; name glob "Vigilant_*".
<!-- generated: catalog end -->

### Verdict changes in the test corpus

- Target: Find My accessory, status 0x20: was Apple AirTags (finder), now Find My device (finder).
- Target: Offline Finding record too short to read: was Apple AirTags (finder), now Find My device (finder).
- Target: Tile on its own company id 0x067C: was no signature, now Tile Trackers (finder).
- Target: Tattile camera name: was Tattile (surveillance, extra attention), Tile Trackers (finder), now Tattile (surveillance, extra attention).
- Near miss: Galaxy phone advertising Samsung 0x0075: was Samsung SmartTags (finder), now no signature.
- Near miss: Galaxy Buds advertising Samsung 0x0075: was Samsung SmartTags (finder), now no signature.
- Near miss: Separated iPhone, Offline Finding only: was Apple AirTags (finder), now Apple Device (phone).
- Near miss: Powered-off AirPods, Offline Finding only: was Apple AirTags (finder), now Apple audio (audio).
- Near miss: Tattile camera name: was Tattile (surveillance, extra attention), Tile Trackers (finder), now Tattile (surveillance, extra attention).
- Near miss: Quuppa tag on 0x00C7: was Tile Trackers (finder), now no signature.
- Near miss: Wi-Fi network named Tile: was Tile Trackers (finder), now no signature.
- Near miss: Speaker named Ravenclaw: was Raven / ShotSpotter (surveillance), now no signature.
- Near miss: Home alarm named Vigilant: was Motorola Vigilant (surveillance, extra attention), now no signature.
- Near miss: Phone hotspot named Ibrahim: was Apple Device (phone), Cradlepoint (law enforcement, extra attention), now Apple Device (phone).

### Feature switches

| Switch | State |
|---|---|
| `IN_APP_CATALOG_UPDATE` | off |
| `OTHER_SENSORS_LAYER` | off |
<!-- generated: end -->
