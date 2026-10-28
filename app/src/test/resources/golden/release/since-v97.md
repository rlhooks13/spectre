<!-- generated: begin -->
<!-- generated: catalog v97 -> v105 -->
### Signatures (stock catalog v97 → v105)

- **New:** Find My device (finder) matches on Bluetooth company id 0x004C data starting 12.
- **Apple AirTags** (finder): stopped matching on Bluetooth company id 0x004C data starting 12; notes reworded.
- **Amazon** (home): stopped matching on name contains "Echo" (BLE); now also matches on name glob "Echo" (BLE); name glob "Echo *" (BLE); name glob "Echo-*" (BLE); name glob "Echo_*" (BLE).
- **Arlo** (camera): stopped matching on name contains "Arlo".
- **ASUS** (isp): stopped matching on name contains "ASUS" (WIFI).
- **Audi MMI** (vehicle): stopped matching on name contains "Audi" (BLE); now also matches on name glob "Audi" (BLE); name glob "Audi *" (BLE); name glob "Audi-*" (BLE); name glob "Audi_*" (BLE).
- **BlueTOAD Spectra** (surveillance): notes reworded.
- **BMW** (vehicle): stopped matching on name contains "BMW" (BLE); now also matches on name glob "BMW" (BLE); name glob "BMW *" (BLE); name glob "BMW-*" (BLE); name glob "BMW_*" (BLE).
- **Bose** (audio): stopped matching on name contains "Bose" (BLE); now also matches on name glob "Bose" (BLE); name glob "Bose *" (BLE); name glob "Bose-*" (BLE); name glob "Bose_*" (BLE); name glob "LE-Bose*" (BLE).
- **BYD** (vehicle): stopped matching on name contains "BYD" (BLE); now also matches on name glob "BYD" (BLE); name glob "BYD *" (BLE); name glob "BYD-*" (BLE); name glob "BYD_*" (BLE).
- **Cisco** (isp): notes reworded.
- **Cradlepoint** (law enforcement): stopped matching on name glob "IBR*" (WIFI); now also matches on name glob "IBR350*" (WIFI); name glob "IBR1150*" (WIFI).
- **DJI** (drone): stopped matching on name contains "DJI" (BLE); name contains "DJI" (WIFI).
- **DULT tracker** (finder): decoded fields changed.
- **Eero** (isp): stopped matching on name contains "eero" (WIFI).
- **eufy Security** (camera): stopped matching on name contains "eufy".
- **Google Find Hub** (finder): decoded fields changed; notes reworded.
- **FOBO TPMS** (vehicle): stopped matching on name contains "FOBO" (BLE).
- **Ford** (vehicle): stopped matching on name contains "Ford" (BLE); now also matches on name glob "Ford" (BLE); name glob "Ford *" (BLE); name glob "Ford-*" (BLE); name glob "Ford_*" (BLE).
- **goTenna** (mesh): notes reworded.
- **Hak5 Pineapple** (hacking): stopped matching on name contains "Hak5".
- **Hobby BLE serial** (hacking): attention note reworded; stopped matching on name contains "BT05" (BLE); now also matches on name glob "BT05" (BLE); name glob "BT05 *" (BLE); name glob "BT05-*" (BLE); name glob "BT05_*" (BLE); notes reworded.
- **HP** (home): stopped matching on name contains "ENVY" (BLE); now also matches on name glob "HP ENVY*" (BLE).
- **JBL / Harman** (audio): stopped matching on name contains "JBL" (BLE); now also matches on name glob "JBL" (BLE); name glob "JBL *" (BLE); name glob "JBL-*" (BLE); name glob "JBL_*" (BLE).
- **Kevo** (lock): stopped matching on name contains "Kevo" (BLE).
- **Logitech** (home): stopped matching on name contains "Logi" (BLE); now also matches on name glob "Logi" (BLE); name glob "Logi *" (BLE); name glob "Logi-*" (BLE); name glob "Logi_*" (BLE).
- **Mercedes MBUX** (vehicle): stopped matching on name contains "MBUX" (WIFI).
- **Microsoft Device** (phone): stopped matching on name contains "Xbox" (BLE); now also matches on name glob "Xbox" (BLE); name glob "Xbox *" (BLE); name glob "Xbox-*" (BLE); name glob "Xbox_*" (BLE).
- **myQ** (home): stopped matching on name contains "MyQ" (BLE); now also matches on name glob "MyQ" (BLE).
- **Nuki** (lock): stopped matching on name contains "Nuki" (BLE).
- **Oura** (wearable): stopped matching on name contains "Oura" (BLE); now also matches on name glob "Oura" (BLE); name glob "Oura *" (BLE); name glob "Oura-*" (BLE); name glob "Oura_*" (BLE).
- **Paxton** (lock): stopped matching on name contains "Net2" (BLE).
- **Raven / ShotSpotter** (surveillance): stopped matching on name contains "RAVEN"; now also matches on name glob "RAVEN"; name glob "RAVEN-*"; name glob "RAVEN_*"; name glob "DIRECT-??-Raven-*" (WIFI).
- **ASSA ABLOY** (lock): stopped matching on name contains "Seos" (BLE); name contains "Yale" (BLE); now also matches on name glob "Seos" (BLE); name glob "Seos *" (BLE); name glob "Seos-*" (BLE); name glob "Seos_*" (BLE).
- **Samsung SmartTags** (finder): stopped matching on Bluetooth company id 0x0075.
- **Sony** (audio): stopped matching on name contains "Sony" (BLE); now also matches on name glob "Sony" (BLE); name glob "Sony *" (BLE); name glob "Sony-*" (BLE); name glob "Sony_*" (BLE); notes reworded.
- **Tapo** (camera): stopped matching on name contains "Tapo".
- **Tile Trackers** (finder): stopped matching on name contains "Tile"; service uuid "FEDD"; Bluetooth company id 0x00C7; now also matches on name glob "Tile" (BLE); Bluetooth company id 0x067C.
- **Tuya** (home): stopped matching on name contains "TUYA" (BLE).
- **UniFi AP** (isp): stopped matching on name contains "UBNT" (WIFI).
- **Motorola Vigilant** (surveillance): stopped matching on name contains "Vigilant"; now also matches on name glob "Vigilant"; name glob "Vigilant-*"; name glob "Vigilant_*".
- **Volkswagen** (vehicle): stopped matching on name contains "VW" (BLE); now also matches on name glob "VW" (BLE); name glob "VW *" (BLE); name glob "VW-*" (BLE); name glob "VW_*" (BLE).
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
- Near miss: Car stereo named Audio: was Audi MMI (vehicle), now no signature.
- Near miss: BLE speaker named Bradford: was Ford (vehicle), now no signature.
- Near miss: Speaker named Echoes: was Amazon (home), now no signature.
- Near miss: Phone named for a Sonya: was Sony (audio), now no signature.
- Near miss: TV wall mount with vw in its name: was Volkswagen (vehicle), now no signature.
- Near miss: Scanner named Logistics: was Logitech (home), now no signature.
- Near miss: Speaker named Entourage: was Oura (wearable), now no signature.
- Near miss: Hotspot named Veeroo: was Eero (isp), now no signature.
- Near miss: Fan gear named Ravens: was Raven / ShotSpotter (surveillance), now no signature.
- Near miss: Device named Vigilante: was Motorola Vigilant (surveillance, extra attention), now no signature.
- Near miss: Wi-Fi Direct group a person named for a Raven cam: was Raven / ShotSpotter (surveillance), now no signature.

### Feature switches

| Switch | State |
|---|---|
| `BLE_ADDRESS_CLASSIFIER` | off |
| `IN_APP_CATALOG_UPDATE` | off |
| `LATE_TAIL_FIX` | off |
| `OTHER_SENSORS_LAYER` | off |
| `PLATE_LAYER` | on |
<!-- generated: end -->
