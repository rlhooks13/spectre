# ADR-0012: Camera-alert region notice is always shown

- **Status:** Accepted, 2026-10-27
- **Context:**
  - Spectre can speak an alert when the user nears a camera on a sensor layer: plate readers now, and speed, red-light and other cameras when the Other-sensors layer returns (P1-E5).
  - Some countries restrict this. Germany forbids a driver from using such a warning app while driving. Switzerland and France restrict the warning itself.
  - The plan (P0-7d) asks for a region notice.

## Decision
- **Always show the notice,** in Settings, under each sensor layer's "Voice Alerts" heading and above its switches (`settings.layer.alert.region`). It shows whenever the layer is on.
- **Not keyed to region.**
  - The phone's locale says nothing about where it is driven.
  - Keying to location would mean reading location for a legal notice.
  - A traveller needs the notice before crossing a border, not after.
- **Name the countries, not the statutes.** Laws change and differ in scope. The notice tells the user to check the law where they drive. It doesn't claim to state that law.
- **Translated in the bundled German, French and Italian packs,** which cover the three countries' languages. The other packs show it in English until translated.

## Consequences
- Every user with a layer on sees the notice, including people where alerts are legal.
- A user whose alert was already on from Fieldwatch sees the notice only when they open the layer settings. A one-time prompt for those users is not done here. Revisit it if Spectre ever turns an alert on by default.
- A new sensor layer gets the notice automatically, because `SensorLayerBlock` draws it.
