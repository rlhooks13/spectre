# OpenStreetMap plate-reader snapshot

Mapped plate-reader (ALPR) cameras from OpenStreetMap, bundled into the app so the camera layer needs
no download (ADR-0018). © OpenStreetMap contributors, under the Open Database License 1.0
(`LICENSE-ODbL.txt` at the repo root). This data is not MIT.

| File | Contents |
|---|---|
| `alpr.jsonl` | One camera per line, sorted by key: `[type, id, lat5, lon5, tagIndex, [facings]]`. `type` is 0 node, 1 way, 2 relation (ways and relations by their center). `lat5`/`lon5` are degrees × 1e5. `tagIndex` points into the meta file's `tags`, or is -1. Facings are degrees 0–359, at most 4. |
| `alpr-meta.json` | `format` (`spectre-alpr-1`), `osmBase` (the data's timestamp), `querySha256`, `count`, `tagKeys` and the interned `tags` (manufacturer, manufacturer:wikidata, brand, brand:wikidata, operator). |
| `query.overpassql` | The Overpass query. |

## Refresh

`python -I scripts/build_alpr.py --download-dir <a new empty directory>`

- **The query:** one Overpass query per data build. It never runs on a phone, in Gradle or in CI.
- **The raw response:** saved in the new directory, treated as data, and deleted after conversion.
- **Refusals:** the script writes nothing on an Overpass `remark` (a partial result), a count under 1,000, more than 400,000 cameras, a gzipped size over 4 MiB, or a drop of more than 10% from the committed count without `--allow-drop "reason"`.
- **Cadence:** refresh before a release, at most monthly, each in its own PR. The PR and the release notes give the new `osmBase` and the count change.
