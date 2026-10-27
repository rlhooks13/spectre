<!-- generated: begin -->
<!-- generated: catalog v97 -> v100 -->
### Signatures (stock catalog v97 → v100)

- **New:** Find My device (finder) matches on Bluetooth company id 0x004C data starting 12.
- **Apple AirTags** (finder): stopped matching on Bluetooth company id 0x004C data starting 12.
- **BlueTOAD Spectra** (surveillance): notes reworded.
- **Cisco** (isp): notes reworded.
- **goTenna** (mesh): notes reworded.
- **Hobby BLE serial** (hacking): attention note reworded; notes reworded.
- **Samsung SmartTags** (finder): stopped matching on Bluetooth company id 0x0075.
- **Sony** (audio): notes reworded.
- **Tile Trackers** (finder): stopped matching on name contains "Tile"; service uuid "FEDD"; Bluetooth company id 0x00C7; now also matches on name glob "Tile" (BLE); Bluetooth company id 0x067C.
<!-- generated: catalog end -->

### Verdict changes in the test corpus

- Target: Find My accessory, status 0x20: was Apple AirTags (finder), now Find My device (finder).
- Target: Offline Finding record too short to read: was Apple AirTags (finder), now Find My device (finder).
- Target: Tile on its own company id 0x067C: was no signature, now Tile Trackers (finder).
- Target: Tattile camera name: was Tattile (surveillance, extra attention), Tile Trackers (finder), now Tattile (surveillance, extra attention).
- Near miss: Galaxy phone advertising Samsung 0x0075: was Samsung SmartTags (finder), now no signature.
- Near miss: Galaxy Buds advertising Samsung 0x0075: was Samsung SmartTags (finder), now no signature.
- Near miss: Separated iPhone, Offline Finding only (its signature is right, but it is still read as a tracker until P1-B4): was Apple AirTags (finder), now Apple Device (phone).
- Near miss: Powered-off AirPods, Offline Finding only (its signature is right, but it is still read as a tracker until P1-B4): was Apple AirTags (finder), now Apple audio (audio).
- Near miss: Tattile camera name (its signature is right, but it is still read as a tracker until P1-B4): was Tattile (surveillance, extra attention), Tile Trackers (finder), now Tattile (surveillance, extra attention).
- Near miss: Quuppa tag on 0x00C7: was Tile Trackers (finder), now no signature.
- Near miss: Wi-Fi network named Tile: was Tile Trackers (finder), now no signature.

### Feature switches

| Switch | State |
|---|---|
| `IN_APP_CATALOG_UPDATE` | off |
| `OTHER_SENSORS_LAYER` | off |
<!-- generated: end -->
