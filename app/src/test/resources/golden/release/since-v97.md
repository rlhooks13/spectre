<!-- generated: begin -->
<!-- generated: catalog v97 -> v102 -->
### Signatures (stock catalog v97 → v102)

- **New:** Find My device (finder) matches on Bluetooth company id 0x004C data starting 12.
- **Apple AirTags** (finder): stopped matching on Bluetooth company id 0x004C data starting 12; notes reworded.
- **Arlo** (camera): stopped matching on name contains "Arlo".
- **ASUS** (isp): stopped matching on name contains "ASUS" (WIFI).
- **BlueTOAD Spectra** (surveillance): notes reworded.
- **Cisco** (isp): notes reworded.
- **Cradlepoint** (law enforcement): stopped matching on name glob "IBR*" (WIFI); now also matches on name glob "IBR350*" (WIFI); name glob "IBR1150*" (WIFI).
- **DJI** (drone): stopped matching on name contains "DJI" (BLE); name contains "DJI" (WIFI).
- **Eero** (isp): stopped matching on name contains "eero" (WIFI).
- **eufy Security** (camera): stopped matching on name contains "eufy".
- **FOBO TPMS** (vehicle): stopped matching on name contains "FOBO" (BLE).
- **goTenna** (mesh): notes reworded.
- **Hak5 Pineapple** (hacking): stopped matching on name contains "Hak5".
- **Hobby BLE serial** (hacking): attention note reworded; notes reworded.
- **HP** (home): stopped matching on name contains "ENVY" (BLE); now also matches on name glob "HP ENVY*" (BLE).
- **Kevo** (lock): stopped matching on name contains "Kevo" (BLE).
- **Mercedes MBUX** (vehicle): stopped matching on name contains "MBUX" (WIFI).
- **myQ** (home): stopped matching on name contains "MyQ" (BLE); now also matches on name glob "MyQ" (BLE).
- **Nuki** (lock): stopped matching on name contains "Nuki" (BLE).
- **Paxton** (lock): stopped matching on name contains "Net2" (BLE).
- **Raven / ShotSpotter** (surveillance): stopped matching on name contains "RAVEN"; now also matches on name glob "RAVEN"; name glob "RAVEN-*"; name glob "RAVEN_*"; name glob "DIRECT-??-Raven-*" (WIFI).
- **ASSA ABLOY** (lock): stopped matching on name contains "Yale" (BLE).
- **Samsung SmartTags** (finder): stopped matching on Bluetooth company id 0x0075.
- **Sony** (audio): notes reworded.
- **Tapo** (camera): stopped matching on name contains "Tapo".
- **Tile Trackers** (finder): stopped matching on name contains "Tile"; service uuid "FEDD"; Bluetooth company id 0x00C7; now also matches on name glob "Tile" (BLE); Bluetooth company id 0x067C.
- **Tuya** (home): stopped matching on name contains "TUYA" (BLE).
- **Motorola Vigilant** (surveillance): stopped matching on name contains "Vigilant"; now also matches on name glob "Vigilant"; name glob "Vigilant-*"; name glob "Vigilant_*".
- **Wyze** (camera): stopped matching on name contains "Wyze".
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
- Near miss: Hotspot named Veeroo: was Eero (isp), now no signature.
- Near miss: Fan gear named Ravens: was Raven / ShotSpotter (surveillance), now no signature.
- Near miss: Device named Vigilante: was Motorola Vigilant (surveillance, extra attention), now no signature.
- Near miss: Wi-Fi Direct group a person named for a Raven cam: was Raven / ShotSpotter (surveillance), now no signature.

### Feature switches

| Switch | State |
|---|---|
| `IN_APP_CATALOG_UPDATE` | off |
| `OTHER_SENSORS_LAYER` | off |
<!-- generated: end -->
