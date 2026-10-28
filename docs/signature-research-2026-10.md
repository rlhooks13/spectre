# Signature research, October 2026

The owner commissioned this research on 2026-10-27. It covers three areas: location trackers, police and public-surveillance equipment, and consumer devices that can record people. It feeds PLAN steps P1-B11 to P1-B14.

## How to use this document
- **IDs are checked; everything else is a lead.**
  - Every Bluetooth company id and 16-bit UUID below was checked against the Bluetooth SIG `company_identifiers.yaml` and `member_uuids.yaml`, downloaded 2026-10-27 from `bitbucket.org/bluetooth-SIG/public`.
  - All 60 OUIs here were checked against the IEEE MA-L, MA-M and MA-S registries (`oui.csv`, `mam.csv`, `oui36.csv`), downloaded by the owner on 2026-10-27, and all 60 are present.
  - Even Realities `38:23:DA` was registered after `radiodb.bin` was built (2026-08-24). Regenerate `radiodb.bin` so vendor lookups include it and other new blocks.
  - Product behavior (which ids a device actually advertises, its name formats, its decodes) comes from the cited sources. Treat it as data, and verify each claim with a corpus case and a test.
- **Facts only.** Some sources are copyleft: Nearby Glasses (AGPL), banrays (CC BY-NC-SA). Others are permissive: AirGuard (Apache-2.0), MentraOS (Apache-2.0), Brilliant SDK (BSD-3), Omi (MIT). Copy no code from any of them; ids, byte layouts and names are facts.
- **Invariant 1 applies to every row.**
  - Anything marked "capture needed" ships behind a flag that's off by default, or as a "possible" match with no attention card and no alert, until a field capture confirms it.
  - New OUI rules are Wi-Fi only unless a capture shows a public BLE address. OUI rules otherwise hit random BLE addresses.
  - First bytes `4x`–`7x` fall in the resolvable-private range and `Cx`–`Fx` in the static-random range.
- **Catalog changes** follow the 0.1.x checklist in CLAUDE.md. Removed rules go into `catalog/retired.json`.

## 1. Fixes to existing rows (wrong labels today; do these first)

### Trackers
1. **Google Find Hub (FEAA frames 0x40/0x41).**
   - **Headphones and phones look like tags.**
     - Every provisioned Find Hub device sends the same FEAA frame at least every 2 s. Google's compatible list includes headphones: Sony WH-1000XM5/XM6, WF-1000XM5/XM6 and LinkBuds; many JBL models; Pixel Buds Pro 2 and 2a; Jabra Evolve3; Moto Buds. Today all of these become FINDER "Google Find Hub".
     - AirGuard separates phones from tags using the advertising Flags byte (0x02 is a phone, 0x06 is a tag), and Spectre already stores `advFlags`.
     - **Fix:** call a 0x40 frame a tag only when the Flags byte has "BR/EDR not supported" (0x04) set. Dual-mode radios, and radios co-labeled by an audio or phone row, read "Find Hub accessory (headphones or phone)". Keep 0x41 as a finder.
     - Sources: https://www.android.com/learn-find-hub/compatible-devices/ and AirGuard `GoogleFindMyNetwork.kt` / `GoogleFindMyNetworkType.kt` (https://github.com/seemoo-lab/AirGuard).
   - **Wording.**
     - 0x40 only means unwanted-tracking protection isn't active yet. A server-side timer starts it, and AirGuard's comment says the owner connected within about 4 h. So a tag planted hours ago still reads "nearby".
     - Relabel 0x40 "not yet in separated mode". Drop "often your own tag" from `CatalogDecodes.findHub` and the same idea from `AdvPayloadDecoder` ("nearby / with owner").
     - Sources: https://developers.google.com/nearby/fast-pair/specifications/extensions/fmdn and AirCatch, https://arxiv.org/abs/2602.07656.
   - **EID length.** `CatalogDecodes.findHub` reads a fixed 20-byte EID. The spec allows 20 (SECP160R1) or 32 (SECP256R1) bytes, and `AdvPayloadDecoder` already handles both.
2. **Apple AirTags row.**
   - `uuid("FD44")` (SIG: Apple Inc.) is Apple's Find My accessory service. Third-party tags and AirPods use it too (AirGuard `AppleFindMy.kt`; https://github.com/parawanderer/OpenTagViewer/issues/193).
   - On `fleet-airtag` it labels those devices "Apple AirTags", and `dropAirTagsWhenAppleDevice` keeps that label.
   - **Fix:** move `uuid("FD44")` and `name("Find My")` (BLE-only) to `fleet-findmy`, where the status byte routes them: `(status & 0x30) >> 4` gives 1 = AirTag, 2 = third-party, 3 = AirPods, 0 = Apple device.
   - Update `SignatureExchangeTest`'s FD44 expectation.
   - Apple itself alerts on separated AirPods moving with you (https://support.apple.com/en-us/119874), so check whether the PHONE treatment of `12 19` type 3 under-warns.
3. **Samsung SmartTags (FD5A).**
   - **Names.** `name("Smart Tag")` is a contains rule on any radio. It gives a Samsung label to Find Hub tags sold as "Kogan Smart Tag", "Amine Smart Tag", "NOVOO Smart Tag" or "Maginon Smart Tag Duo", and to clones. **Fix:** use BLE-only anchored globs `Smart Tag`, `Smart Tag2` and `Galaxy SmartTag*`. A name-only hit with no FD5A is "possible".
   - **Decode (new).**
     - Byte 0: the high 5 bits are 0x10 (`byte0 & 0xF8 == 0x10`), and the low 3 bits are the state:
       - 1 = premature offline
       - 2 = offline (more than 15 min)
       - 3 = overmature offline (more than 24 h); emphasize this one
       - 4–6 = paired or connected
     - Bytes 1–3: aging counter.
     - Bytes 4–11: privacy ID.
     - Byte 12: UWB and battery flags. Confirm the bit order on a capture.
   - **Following detection.** In overmature offline the privacy ID holds for 24 h while the address changes about every 15 min, so a following tag splits into many short-lived radios. Key co-travel on the privacy ID.
   - Sources: Yu et al., USENIX Security 2024, https://arxiv.org/abs/2210.14702; AirGuard `SamsungTracker.kt`; Kismet PR 679 (SmartTag2 capture), https://github.com/kismetwireless/kismet/pull/679.
4. **Tile.**
   - FEED means activated and FEEC means shipping mode (SIG: Tile, Inc.). FEEC alone means an unactivated Tile with no owner, so it must not drive a finder alert.
   - **Likely decode bug:** AirGuard requires FEED service data to start with `02 00` (version 2), and the private id is 64 bits. `CatalogDecodes.tile` reads 8 bytes from offset 0, which takes in the header. It should probably read offset 2, length 8, gated on `0200`. Confirm on a capture.
   - Tile addresses are static (Kumar, Raymaker and Specter, USENIX Security 2026, https://arxiv.org/abs/2510.00350).
   - Nordic Tile library: https://docs.nordicsemi.com/r/bundle/nrf5_sdk_v17.0.2/page/group_tile_lib.html
5. **DULT (FCB2).**
   - The rule and decode match draft-ietf-dult-accessory-protocol (https://datatracker.ietf.org/doc/draft-ietf-dult-accessory-protocol/).
   - The near-owner bit can persist 30+ minutes after separation, so "near owner" must not clear a newly planted tag. The same wording fix as Find Hub applies to the DULT note in `CatalogDecodes` ("often your own tag").
   - Don't hard-code Network IDs; the draft removed the registry.
6. **Chipolo (optional).** Add `uuid("FE65")` (SIG: CHIPOLO d.o.o.; seen with FE33, https://github.com/sandeepmistry/node-chipolo/issues/8). FE33 byte 1 bit 0 means the owner hasn't been seen for more than 30 min, on firmware D8 and older (AirGuard `Chipolo.kt`).

### Police and surveillance
7. **Novatel row (LAW_ENFORCEMENT on `28:80:A2`): severity-1 risk.** IEEE lists `28:80:A2` as "Inseego Wireless, Inc", maker of consumer MiFi hotspots. ADR-0015 already records the double label with the Inseego row. Retire the row, or make it ISP with no attention note.
8. **Compex row.** `04:F0:21` is "Compex Systems Pte Ltd", a maker of Wi-Fi modules and embedded AP/CPE boards. `00:40:29` and `00:80:48` are 1990s Compex NIC blocks. No source ties any of them to police vehicles. Drop the LAW_ENFORCEMENT class, or keep only `114K-*` if a capture supports it.
9. **AirLink row.** Six of its seven OUIs are generic "Sierra Wireless, ULC" blocks; Sierra Wireless is a module maker that also sold consumer AirCard hotspots. Only `00:14:3E` is "AirLink Communications, Inc.". Downgrade it to VEHICLE or fleet router.
10. **Reveal Media row.** `name("BodyWorn")` and `glob("BodyWorn*")` are Utility's body camera (Utility became Coreforce in October 2025). Move both to the Utility Inc row, through `retired.json` plus additions.
    - Sources: https://officer.com/on-the-street/body-cameras/company/12006088/bodyworn-by-utility-utility-associates-inc and https://www.coreforcetech.com/resources/yardarm
    - Reveal's current lines are R1, K, KS, D and Calla (https://www.revealmedia.com/products). `RS2-*` targets a 2015 model and is a generic prefix, so consider retiring it.
11. **Raven / ShotSpotter.** `oui("D4:11:D6")` (ShotSpotter, Inc.) starts with 0xD4, which is in the static-random range. Make it Wi-Fi only. The same applies to Rhombus `CC:47:BD`.
12. **LiteOn camera radio.**
    - A WiGLE audit found about 8,000 non-Flock SSIDs on each of `74:4C:A1`, `F4:6A:DD` and `D0:39:57`, including Barco ClickShare (50,278 rows), `SMARTGATE_*` and an Audi HUD (https://simeononsecurity.com/articles/flock-finder-oui-false-positive-audit-2026/). Review what this row claims.
    - `vendorIe("00:80:19")` is registered to Dayna Communications. Find the capture that justified it.
13. **Verkada: don't add its member UUIDs** (FD3A, FD3B, FC2C). Verkada Pass phones advertise them to unlock doors, so they would label employees' phones as cameras (https://help.verkada.com/access-control/configuration-and-setup/bluetooth-unlock). The OUI `E0:A7:00` (Verkada Inc) is fine as a Wi-Fi-only addition.
14. **Avigilon.** `00:18:85` and `00:1F:92` are now "Motorola Solutions Inc." in IEEE; a 2018 IPVM list ties them to Avigilon (https://ipvm.com/discussions/is-there-a-list-of-the-top-camera-manufacturer-s-mac-ouis). Add them Wi-Fi only after a capture. Don't add `70:1A:D5` ("Avigilon Alta", ex-Openpath access control).

### Recording devices
15. **Meta glasses (`fleet-meta-glasses`).**
    - Bare `mfg(0x01AB)` (Meta Platforms, Inc.) matches Quest and other Meta gear.
    - **Payload layout (legacy):** bytes 0–1 are the product id (LE), and byte 2 bit 0 is "pairing open". Product ids: 258 Ray-Ban Meta (2023), 259 Meta Ray-Ban Display, 262 Ray-Ban Meta Optics, 263 "Meta glasses", 264 Oakley Meta Vanguard, 265 Oakley Meta HSTN, 266 Ray-Ban Meta Gen 2 (https://github.com/macedonga/visor/wiki/Glasses-hardware, /wiki/Radios-and-links).
    - **Independent capture:** a Ray-Ban Meta at power-on sent `020102102716e4` with FD5F (https://github.com/NullPxl/banrays). That decodes as product 258, so the two sources agree.
    - **Fix:** replace bare `mfg(0x01AB)` with `mfgData(0x01AB, …)` prefixes `0201`, `0301`, `0601`, `0701`, `0801`, `0901` and `0A01`. Move bare 0x01AB and 0x058E (Meta Platforms Technologies, LLC) to a new non-attention "Meta device" row.
    - **v2 layout** (byte 0 ≥ 0x80 is a version): capture a Gen 2 before adding prefixes.
    - **FD5F** (Meta Platforms Technologies) is a second signal only after a Quest capture.
    - 0x0D53 (Luxottica Group S.p.A) and FEB7/FEB8 (Meta Platforms, Inc.) have no glasses capture, so keep them flagged.
    - Candidate glasses SoftAP BSSIDs, capture first: Luxottica `38:47:12`, `80:AA:1C`, `98:59:49`.
    - Don't copy NoPeek's table (https://github.com/getnopeek/nopeek-android): its 0x07D7 is Nanjing Qinheng (a chip vendor), its 0x0BA7 is hearX Group, and its 0x00E0 is Google.
16. **Brilliant Frame.** Both of its rules miss today.
    - The UUID `7A230001-5475-A6A4-654C-576174636800` is wrong. The official one is `7A230001-5475-A6A4-654C-8431F6AD49C4` (https://docs.brilliant.xyz/frame/frame-sdk-bluetooth-specs/; SDK https://github.com/brilliantlabsAR/brilliant_sdk).
    - The firmware name is "Frame XX", not "Brilliant Frame".
    - **Fix:** add the correct UUID and `bleGlob("Frame ??")`, and retire the bad UUID. Halo is in section 2.
17. **Omi.** `uuid("23BA7924-…")` is Omi's GATT button service, and it is never advertised. The advert has only the Arduino-default `19B10000-…` (rightly excluded) and the name "Omi" (https://github.com/BasedHardware/omi, `transport.c`, `omi.conf`). Retire or annotate the UUID rule.
18. **Plaud.**
    - Add `mfg(0x0FB2)` (PLAUD Inc.) and Wi-Fi `oui("9C:06:CF")` (PLAUD Inc.). It's a single-product-type company, so confuser risk is low. Capture to confirm.
    - The NotePin is a Find My accessory, so away from its phone it can show as `fleet-findmy` instead.
19. **Even G1 row.** Add `bleGlob("Even G2_*")`. The G2 name is "Even G2_XX_L_YYYYYY" (https://github.com/i-soxi/even-g2-protocol/blob/main/docs/ble-uuids.md). Add `oui("38:23:DA")` (IEEE: Even Realities Ltd.) only after a capture shows a public address.
20. **HOVERAir.** Add `mfg(0x09F3)` (Beijing Zero Zero Infinity Technology) and Wi-Fi `oui("84:83:19")` (Hangzhou Zero Zero Technology). Both belong to a drone-only company. Capture to confirm.
21. **Unverified existing UUIDs.** No public source found for Limitless (`632DE001-…`), Bee (`03D5D5C4-…`) or Friend (`1A3FD0E7-…`). Confirm them against captures.

## 2. New signatures, well sourced (add after section 1)

| # | Signature | Class | Rules | Confidence | Confusers and notes |
|---|---|---|---|---|---|
| N1 | **Hubble Network** (Bluetooth-to-satellite location network) | BEACON for now; FINDER once a consumer tag ships | `uuid("FCA6")` (SIG: Hubble Network Inc.; FCA7 is also Hubble) | High that FCA6 means Hubble | Nothing outside Hubble uses it. Hubble sensors and dev kits use it for data, so not FINDER yet. The address is a rotating NRPA, so per-address co-travel is weak. Life360 plans Tile Pro and Slim on it. Docs: https://hubble.com/docs/device-sdk/terrestrial/advertising-packet; https://www.life360.com/press/life360-partners-with-hubble-network-to-build-global-location-tracking-network-aiming-to-leapfrog-apple-and-google |
| N2 | **Cheap P2P ("PPPP") Wi-Fi cameras in hotspot mode** (A9 / X5 mini cams, LookCam, FtyCamPro) | CAMERA | Wi-Fi globs `<PFX>-??????-?????` and `<PFX>` + 11 × `?`, for tier A prefixes FTYC, FTYA, FTZW, FTY, FTZ. Tier B (LookCam BHCC, FHBB, GHBB; HDWiFiCam DGOA, DGOC, DGOE, DGOK, DGON, DGB, DBG, DGO, NMSA, PIXA, PIZ; YsxLite PTZ, PTZA, PTZB, BATC, BATE, TBAT) needs a capture to settle dashed versus undashed. Tier C (ACCQ, BCCA, BCCQ, CAMA) is capture first. | High (A), medium (B), low (C) | Label it "cheap P2P Wi-Fi camera (family sold as mini / hidden cams)", never "hidden camera": the same apps run normal PTZ, bulb, baby and doorbell cams. Only cameras in hotspot mode show up. Don't use module OUIs (Beken, Bilian, Sigmastar, Ingenic, `FC:58:4A`), which appear in Tuya plugs. Sources: https://palant.info/2025/11/05/an-overview-of-the-pppp-protocol-for-iot-cameras/; https://palant.info/2025/09/08/a-look-at-a-p2p-camera-lookcam-app/; https://arxiv.org/abs/2306.00610; https://github.com/DavidVentura/cam-reverse; https://cdn4.sharperimage.com/si/pdf/manuals/209441.pdf; https://github.com/devbis/aiopppp |
| N3 | **VIOFO dash cams** | CAMERA | Wi-Fi `NAME_CONTAINS "VIOFO"` (5 characters) | High | None. SSIDs are `5G_VIOFO_<model>_xxxx` and `VIOFO_<model>_XXXX` (https://www.viofo.com/blogs/viofo-car-dash-camera-guide-faq-and-news/get-to-know-the-new-viofo-app-smart-control-for-your-dash-cam) |
| N4 | **70mai dash cams** | CAMERA | Wi-Fi `glob("70mai_*")`; Wi-Fi OUIs `44:27:F3` and `B8:59:C8` (70mai Co.,Ltd.). BLE `uuid("FD4D")` and `uuid("FD4E")` (70mai) are capture first. Never bare 0x0909. | High (SSID), medium (OUI), low (BLE) | 70mai also sells non-camera car gear, so no bare company id. Manuals: https://new-us-dc-cdn.70mai.com/instructions/70maiDashCam_M300_UserManual.pdf; https://70mai.store/cdn/shop/files/70mai_Dash_Cam_App_Connection_Guide.pdf |
| N5 | **BlackVue dash cams** | CAMERA | Wi-Fi `glob("BlackVue*")` | Medium-high | Owners can rename it. Pittasoft `00:25:42` only after a capture. Source: https://forum.blackvue.com/hc/en-us/articles/49128391868057 |
| N6 | **EZVIZ in hotspot mode** | CAMERA | Wi-Fi `glob("EZVIZ_*")`. The 15 "Hangzhou Ezviz Software" OUIs (`0C:A6:4C`, `20:BB:BC`, `34:C6:DD`, `38:F2:5D`, `54:D6:0D`, `58:8F:CF`, `64:24:4D`, `64:F2:FB`, `78:A6:A0`, `78:C1:AE`, `94:EC:13`, `AC:1C:26`, `EC:97:E0`, `F4:70:18`, `FC:24:22`) only after a capture. | High (SSID) | EZVIZ locks, plugs and vacuums in setup mode. Label it "EZVIZ device in hotspot mode, usually a camera". Source: https://support.ezviz.com/faq/category/Security-Cameras?page=2 |
| N7 | **Mentra Live camera glasses** | GLASSES | `bleGlob("MENTRA_LIVE*")`, `bleGlob("XyBLE_*")` | Medium-high / medium | `XyBLE_` is the K900 ODM platform, so other K900 camera glasses may match; they're still camera glasses. Company 0xB822 isn't SIG-assigned, so don't use it. Source: MentraOS `MentraLive.kt` (Apache-2.0), https://github.com/Mentra-Community/MentraOS |
| N8 | **Brilliant Halo camera glasses** | GLASSES | `uuid("7A230001-5475-A6A4-654C-8431F6AD49C4")` (shared with Frame), `bleGlob("Halo ??")` | High (UUID), medium (name) | "Halo XX" pet collars and the old Amazon Halo; the UUID is the strong rule. Halo firmware has a camera module (https://github.com/brilliantlabsAR/halo-firmware) |
| N9 | **Yardarm Holster Aware** (starts the body camera when the gun is drawn; sold with Motorola V300/V700/VB400, Kustom Vantage and Coreforce BodyWorn) | BODYWORN | BLE `mfg(0x025F)` (Yardarm Technologies) | Medium | Armed private security and the military; the company says it doesn't sell to consumers. Capture needed: is 0x025F in the advert, does it advertise while bonded, and what address type. Sources: https://www.motorolasolutions.com/content/dam/msi/docs/products/body-worn-cameras/v300-holster-aware-brochure.pdf; https://kustomsignals.com/blog/yardarm; https://hypepotamus.com/companies/yardarm/ |
| N10 | **Police and surveillance OUIs (Wi-Fi only, capture first)** | as listed | Kustom Signals `58:E8:76:C` (MA-M); Decatur Electronics `6C:18:11`; Zepcam `48:46:8D`; Safe Fleet / L-3 Mobile-Vision `38:73:EA:0` (MA-M); ELSAG `70:B3:D5:1C:5`, Selex ES `70:B3:D5:52:1` and `70:B3:D5:F5:E` (MA-S); Fusus `7C:83:34:4` and `84:B3:86:5` (MA-M); Ubicquia `94:7B:BE` (SURVEILLANCE without attention: "may host plate readers or cameras") | Medium that a hit is that vendor; low that the unit beacons | Kustom and Decatur speed trailers are often public works. Zepcam also sells to private security and transit. Many UbiHubs are plain city Wi-Fi (Flock on UbiHub: https://govtech.com/biz/flock-safety-ubicquia-offer-streetlight-crime-solving-tool) |
| N11 | **Camera and drone vendor OUIs (Wi-Fi only)** | existing rows | Skydio `38:1D:14`; Miovision `84:F6:FA`; i-PRO `D4:2D:C5`; Hanwha `44:B4:23` and `E4:30:22`; Verkada `E0:A7:00` | High (registry) | None beyond each row's existing confusers |
| N12 | **Flock SSID variants** | existing Flock row | Wi-Fi exact `glob("Flock")`; `glob("Flock Camera net.")` | Low-medium, and the yield is falling | Any network named exactly "Flock" (a church or a business). Flock reportedly turned off the management AP around December 2025. Sources: https://simeononsecurity.com/articles/flock-camera-detection-arms-race-2026/; https://github.com/colonelpanichacks/flock-you/issues/43 |

## 3. Capture first (open `field-capture` issues; no label until a capture confirms)
- **Motorola Solutions BLE:** `uuid("FE04")`, `uuid("FD8E")` and company 0x04EC, all Motorola Solutions. They could be body cameras (V300/VB400/V500) or APX radios, but MOTOTRBO business radios share them. Non-alerting OTHER row only.
- **Axis body-worn:** `mfgData(0x0D5B, <prefix from capture>)` for the W110/W120 cameras and the W401 activation kit (https://axis.com/dam/public/69/e5/91/datasheet-axis-w401-body-worn-activation-kit-en-US-462887.pdf). Never bare 0x0D5B: the AXIS A4612 office door reader shares it.
- **HeyCyan white-label camera glasses** (for example Nilox at ALDI / Hofer): `uuid("7905FFF0-B5CE-4E99-A40F-4B1E122D00D0")`. Low confidence, because the vendor's watches may share it. Never bare 0x05D6 (Zhuhai Jieli, a chip vendor).
- **Rokid glasses:** `oui("28:E8:88")` (Lingban Technology, the Rokid parent). Low confidence.
- **Teltonika vehicle GPS trackers:** `bleGlob("FMB???_???????")` and `bleGlob("FMB???_???????_LE")` (https://wiki.teltonika-gps.com/view/FMC920_Bluetooth%C2%AE_4.0_settings). The name may appear only over Classic Bluetooth. Don't use Teltonika OUIs (Teltonika Networks makes routers), and never bare 0x089A.
- **Pebblebee name:** `bleGlob("PB - ????")` (AirGuard `PebbleBee.kt`). Power banks abbreviate to "PB". FA25 is unassigned, so don't use it.
- **soundcore Work recorder:** `bleGlob("soundcore Work*")`. Never bare 0x0CC2 (Anker).
- **Notta Memo:** `oui("E0:83:0D")` (NOTTA PTE. LTD.); it only fires on a public BLE address.
- **AirTag 2nd generation (Jan 2026):** confirm its status byte still says AirTag (type 1).
- **Tile FD84** (SIG: Tile, Inc.): no observed use yet.

## Rejected
| Item | Why |
|---|---|
| Eye Spy's "00:0E:58 = Motorola / Vigilant plate reader" | IEEE lists `00:0E:58` as Sonos, Inc. |
| Applied Concepts / Stalker radar `1C:82:59:D` | Stalker sports radar guns pair over Bluetooth, so ballparks would read as police |
| Verkada member UUIDs FD3A, FD3B, FC2C | They label employees' phones as cameras |
| Mentra company id 0xB822 | Not SIG-assigned (the SIG list ends at 0x113C) |
| Wi-Fi module and chip vendor OUIs and company ids (Beken, Espressif, Silicon Labs, Jieli, Quectel, Sierra Wireless) | They are in thousands of consumer devices |
| `8C:1F:64:57:4` "Flock Audio Inc." | Unrelated to Flock Safety |
| Samsung FD59 and FD69 | FD59 is an unregistered SmartTag with no owner. FD69 is Galaxy phone offline finding, not a tag |
| No passive id found | Huawei Tag, Jiobit, Tracki, LandAirSea, Tractive, Cube, Nut, Getac, WCCTV, BRINC (Remote ID only), Kapsch, Jenoptik, Neology, V380, iCSee, CamHi, Nextbase, Garmin Dash Cam, Thinkware, Vantrue, Xreal, Solos, Halliday, Rabbit, Android XR glasses |
| Already covered by Find My or Find Hub | Xiaomi Tag, eufy SmartTrack, UGREEN FineTrack, JioTag Go, Kogan / Amine / NOVOO Smart Tag, Nutale, Orbitkey, Moto Tag 2, Pebblebee Universal |
