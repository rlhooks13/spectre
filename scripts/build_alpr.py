#!/usr/bin/env python3
"""Build the bundled OpenStreetMap plate-reader snapshot (P0-7e, ADR-0018).

One Overpass query per data build, never per phone and never in Gradle or CI. The response is
downloaded into a new empty directory, treated as data (parsed with limits, never executed), and
deleted after conversion. The result is committed under catalog/osm/:

  alpr.jsonl         one camera per line, sorted by key: [type, id, lat5, lon5, tagIndex, [facings]]
  alpr-meta.json     format, osmBase, query hash, count, and the interned tag table
  query.overpassql   the query text

The data is © OpenStreetMap contributors, under the Open Database License 1.0 (LICENSE-ODbL.txt).

Run with:  python -I scripts/build_alpr.py [--download-dir NEW_EMPTY_DIR] [--allow-drop "reason"]
"""
from __future__ import annotations

import argparse
import gzip
import hashlib
import http.client
import json
import math
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT_DIR = ROOT / "catalog" / "osm"
ENDPOINT = "https://overpass-api.de/api/interpreter"
USER_AGENT = "spectre-build-alpr/1 (+https://github.com/rlhooks13/spectre)"
FORMAT = "spectre-alpr-1"
QUERY = """[out:json][timeout:300];
nwr["man_made"="surveillance"]["surveillance:type"="ALPR"];
out center tags;
"""

# Limits (ADR-0018 §2). MIN_COUNT matches FlockApproach.MIN_COUNT in the app.
MIN_COUNT = 1_000
MAX_COUNT = 400_000
MAX_GZIP_BYTES = 4 * 1024 * 1024
MAX_RESPONSE_BYTES = 512 * 1024 * 1024
MAX_DROP = 0.10
MAX_FACINGS = 4
MAX_TAG_CHARS = 80
# Overpass's busy and gateway answers get one retry after a backoff; anything else fails at once.
RETRY_CODES = (429, 502, 503, 504)
TYPES = {"node": 0, "way": 1, "relation": 2}
TAG_KEYS = ("manufacturer", "manufacturer:wikidata", "brand", "brand:wikidata", "operator")
COMPASS = {
    "N": 0, "NNE": 22.5, "NE": 45, "ENE": 67.5, "E": 90, "ESE": 112.5, "SE": 135, "SSE": 157.5,
    "S": 180, "SSW": 202.5, "SW": 225, "WSW": 247.5, "W": 270, "WNW": 292.5, "NW": 315, "NNW": 337.5,
}


class BuildError(Exception):
    pass


def sensor_key(osm_id: int, osm_type: str) -> int:
    """Same as the app's sensorKey: a way is the negative id; a relation uses another negative range."""
    if osm_id == 0:
        return 0
    if osm_id > 0 and osm_type == "way":
        return -osm_id
    if osm_id > 0 and osm_type == "relation":
        return -(1 << 62) - osm_id
    return osm_id


def norm(deg: float) -> int | None:
    """The app's FlockApproach.norm: 0-359, halves rounded up."""
    if not math.isfinite(deg):
        return None
    return int(math.floor(deg % 360.0 + 0.5)) % 360


def bearings(raw: str) -> list[int]:
    """The app's bearingsFrom: parts split on ';' or ','. Also reads compass points (N, NNE, ...)."""
    out: list[int] = []
    for part in str(raw).replace(",", ";").split(";"):
        part = part.strip().upper()
        if not part:
            continue
        if part in COMPASS:
            deg = norm(COMPASS[part])
        else:
            try:
                deg = norm(float(part))
            except ValueError:
                continue
        if deg is not None and deg not in out:
            out.append(deg)
    return out


def facings(tags: dict) -> list[int]:
    """direction then camera:direction, merged in order without repeats, at most 4 (as the app's reader does)."""
    out: list[int] = []
    for deg in bearings(tags.get("direction") or "") + bearings(tags.get("camera:direction") or ""):
        if deg not in out:
            out.append(deg)
        if len(out) == MAX_FACINGS:
            break
    return out


def tag_tuple(tags: dict) -> tuple[str, ...]:
    return tuple(" ".join(str(tags.get(k, "")).split())[:MAX_TAG_CHARS].rstrip() for k in TAG_KEYS)


def fetch(download_dir: Path) -> Path:
    """POST the query once (one backed-off retry on a busy or gateway error) and save the response in download_dir.

    A failed or oversized download is deleted before the error is raised, so no partial response is left behind.
    """
    body = urllib.parse.urlencode({"data": QUERY}).encode("utf-8")
    target = download_dir / "overpass-response.json"
    for attempt in (1, 2):
        request = urllib.request.Request(ENDPOINT, data=body, headers={"User-Agent": USER_AGENT})
        try:
            with urllib.request.urlopen(request, timeout=420) as response, open(target, "wb") as out:
                total = 0
                while True:
                    chunk = response.read(1 << 20)
                    if not chunk:
                        break
                    total += len(chunk)
                    if total > MAX_RESPONSE_BYTES:
                        raise BuildError(f"response over {MAX_RESPONSE_BYTES} bytes")
                    out.write(chunk)
            return target
        except urllib.error.HTTPError as err:
            target.unlink(missing_ok=True)
            if err.code in RETRY_CODES and attempt == 1:
                print(f"Overpass returned {err.code}; retrying once in 60 s", file=sys.stderr)
                time.sleep(60)
                continue
            raise BuildError(f"Overpass returned HTTP {err.code}") from err
        except BuildError:
            target.unlink(missing_ok=True)
            raise
        except (urllib.error.URLError, OSError, http.client.HTTPException) as err:
            target.unlink(missing_ok=True)
            raise BuildError(f"download failed: {err}") from err
    raise BuildError("unreachable")


def _dict(value) -> dict:
    return value if isinstance(value, dict) else {}


def convert(response: Path, known_tags: list[tuple[str, ...]]) -> tuple[list[list], list[tuple[str, ...]], str]:
    """Parse the response as data: a size-capped read, a bounded nesting depth, and at most MAX_COUNT elements."""
    with open(response, "rb") as f:
        raw = f.read(MAX_RESPONSE_BYTES + 1)
    if len(raw) > MAX_RESPONSE_BYTES:
        raise BuildError(f"response over {MAX_RESPONSE_BYTES} bytes")
    try:
        data = json.loads(raw.decode("utf-8"))
    except (ValueError, RecursionError) as err:
        raise BuildError(f"response is not JSON this script reads: {str(err)[:120]}") from err
    if not isinstance(data, dict):
        raise BuildError("response is not a JSON object")
    if "remark" in data:
        # Overpass reports a timeout or out-of-memory this way, with HTTP 200 and truncated elements.
        raise BuildError(f"Overpass remark: {str(data['remark'])[:200]}")
    osm_base = str(_dict(data.get("osm3s")).get("timestamp_osm_base", ""))
    if not osm_base:
        raise BuildError("no osm_base timestamp")
    elements = data.get("elements")
    if not isinstance(elements, list):
        raise BuildError("no elements list")
    if len(elements) > MAX_COUNT:
        raise BuildError(f"{len(elements)} elements, over the maximum {MAX_COUNT}")
    # The committed tag table keeps its indices, and new tuples are appended, so a refresh doesn't renumber every row.
    table: dict[tuple[str, ...], int] = {t: i for i, t in enumerate(known_tags)}
    rows: dict[int, list] = {}
    for el in elements:
        if not isinstance(el, dict):
            continue
        osm_type = el.get("type")
        if osm_type not in TYPES:
            continue
        osm_id = el.get("id")
        if not isinstance(osm_id, int) or isinstance(osm_id, bool) or osm_id <= 0:
            continue
        where = el if osm_type == "node" else _dict(el.get("center"))
        lat, lon = where.get("lat"), where.get("lon")
        if not isinstance(lat, (int, float)) or not isinstance(lon, (int, float)):
            continue
        if not (-90 <= lat <= 90 and -180 <= lon <= 180):
            continue
        tags = {k: v for k, v in _dict(el.get("tags")).items() if isinstance(k, str) and isinstance(v, str)}
        if tags.get("man_made") != "surveillance" or tags.get("surveillance:type") != "ALPR":
            continue
        tup = tag_tuple(tags)
        index = -1
        if any(tup):
            index = table.setdefault(tup, len(table))
        key = sensor_key(osm_id, osm_type)
        rows[key] = [TYPES[osm_type], osm_id, round(lat * 1e5), round(lon * 1e5), index, facings(tags)]
    ordered = [rows[k] for k in sorted(rows)]
    # Tuples no row uses any more are kept (as empty slots would shift indices); a refresh PR can note the count.
    tag_list = [t for t, _ in sorted(table.items(), key=lambda kv: kv[1])]
    return ordered, tag_list, osm_base


def check(cameras: list[list], previous: int | None, allow_drop: str | None) -> None:
    count = len(cameras)
    if count < MIN_COUNT:
        raise BuildError(f"{count} cameras, under the minimum {MIN_COUNT}")
    if count > MAX_COUNT:
        raise BuildError(f"{count} cameras, over the maximum {MAX_COUNT}")
    if previous and count < previous * (1 - MAX_DROP) and not allow_drop:
        raise BuildError(f"{count} cameras is more than 10% under the committed {previous}; pass --allow-drop with a reason")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--download-dir", type=Path, help="a new empty directory for the raw response")
    parser.add_argument("--allow-drop", help="reason a drop of more than 10% is expected (record it in the PR)")
    args = parser.parse_args()

    download_dir = args.download_dir or Path(tempfile.mkdtemp(prefix="spectre-alpr-"))
    download_dir.mkdir(parents=True, exist_ok=True)
    if any(download_dir.iterdir()):
        raise BuildError(f"{download_dir} is not empty")

    meta_path = OUT_DIR / "alpr-meta.json"
    previous = None
    known_tags: list[tuple[str, ...]] = []
    if meta_path.is_file():
        try:
            old = json.loads(meta_path.read_text(encoding="utf-8"))
            previous = old.get("count")
            known_tags = [tuple(t) for t in old.get("tags", [])]
        except (ValueError, OSError, TypeError) as err:
            raise BuildError(f"committed alpr-meta.json is unreadable: {err}") from err

    response = download_dir / "overpass-response.json"
    try:
        response = fetch(download_dir)
        cameras, tags, osm_base = convert(response, known_tags)
    finally:
        response.unlink(missing_ok=True)
    check(cameras, previous, args.allow_drop)

    lines = "".join(json.dumps(c, separators=(",", ":")) + "\n" for c in cameras)
    meta = {
        "format": FORMAT,
        "osmBase": osm_base,
        "querySha256": hashlib.sha256(QUERY.encode("utf-8")).hexdigest(),
        "count": len(cameras),
        "tagKeys": list(TAG_KEYS),
        "tags": [list(t) for t in tags],
        "license": "ODbL-1.0, © OpenStreetMap contributors",
    }
    if previous and len(cameras) < previous * (1 - MAX_DROP):
        # Only reached with --allow-drop (check() refuses otherwise): the override is recorded in the data itself.
        meta["dropOverride"] = {"from": previous, "to": len(cameras), "reason": args.allow_drop}
    meta_text = json.dumps(meta, ensure_ascii=False, indent=1) + "\n"
    # The APK asset (P0-7e2) is the meta on one line, then the rows: measure exactly that, at gzip's default level.
    asset = json.dumps(meta, ensure_ascii=False, separators=(",", ":")) + "\n" + lines
    packed = gzip.compress(asset.encode("utf-8"), compresslevel=6, mtime=0)
    if len(packed) > MAX_GZIP_BYTES:
        raise BuildError(f"snapshot is {len(packed)} bytes gzipped, over {MAX_GZIP_BYTES}")

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    outputs = (("alpr.jsonl", lines), ("alpr-meta.json", meta_text), ("query.overpassql", QUERY))
    temps = [OUT_DIR / f"{name}.tmp" for name, _ in outputs]
    try:
        # Every file is written in full before any is replaced, so a write error leaves the old snapshot whole.
        for (_, text), tmp in zip(outputs, temps):
            with open(tmp, "w", encoding="utf-8", newline="\n") as f:
                f.write(text)
        for (name, _), tmp in zip(outputs, temps):
            tmp.replace(OUT_DIR / name)
    finally:
        for tmp in temps:
            tmp.unlink(missing_ok=True)
    change = "" if previous is None else f" ({len(cameras) - previous:+d} from {previous})"
    print(f"{len(cameras)} cameras{change}, osmBase {osm_base}, {len(packed)} bytes gzipped, {len(tags)} tag sets")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except BuildError as err:
        print(f"build_alpr: {err}; nothing written", file=sys.stderr)
        sys.exit(1)
