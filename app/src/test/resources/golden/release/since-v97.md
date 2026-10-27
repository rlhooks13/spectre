<!-- generated: begin -->
<!-- generated: catalog v97 -> v98 -->
### Signatures (stock catalog v97 → v98)

- **BlueTOAD Spectra** (surveillance): notes reworded.
- **Cisco** (isp): notes reworded.
- **goTenna** (mesh): notes reworded.
- **Hobby BLE serial** (hacking): attention note reworded; notes reworded.
- **Samsung SmartTags** (finder): stopped matching on Bluetooth company id 0x0075.
- **Sony** (audio): notes reworded.
<!-- generated: catalog end -->

### Verdict changes in the test corpus

- Galaxy phone advertising Samsung 0x0075: was Samsung SmartTags (finder), now no signature.
- Galaxy Buds advertising Samsung 0x0075: was Samsung SmartTags (finder), now no signature.

### Feature switches

| Switch | State |
|---|---|
| `IN_APP_CATALOG_UPDATE` | off |
| `OTHER_SENSORS_LAYER` | off |
<!-- generated: end -->
