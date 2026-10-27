# Capture runbook (P1-F1 to P1-F4)

A capture is a recording of what the radios and GPS reported, made by a **debug build** (ADR-0016). Raw captures hold bystander data, so they're scrubbed (ADR-0017) before they're kept. Only scrubbed parts are stored, in the private repo `rlhooks13/spectre-corpus`. Raw files are deleted.

## Record (on the dedicated phone, debug build)
```
adb -s $SERIAL shell run-as app.fieldwatch.debug mkdir -p files/capture
adb -s $SERIAL shell run-as app.fieldwatch.debug touch files/capture/ENABLED    # start
#   ... scan: walk, drive, or leave reference tags beside the phone ...
adb -s $SERIAL shell run-as app.fieldwatch.debug rm files/capture/ENABLED       # stop
```
The recorder flushes every second. It writes `files/capture/capture-<wallMs>-<part>.ndjson`, in parts of up to 8 MB, 64 MB in all.

## Pull into a new empty folder, outside the repo
```
mkdir -p ~/captures/raw-$(date +%s) && cd "$_"
adb -s $SERIAL shell run-as app.fieldwatch.debug ls files/capture
adb -s $SERIAL exec-out run-as app.fieldwatch.debug cat files/capture/<part>.ndjson > <part>.ndjson   # each part
```

## Scrub, leak-check and store
```
git clone https://github.com/rlhooks13/spectre-corpus ~/spectre-corpus   # once
cd ~/spectre
./gradlew testDebugUnitTest --tests '*CaptureScrubTool*' -PscrubIn=$HOME/captures/raw-<ts> -PscrubOut=$HOME/spectre-corpus/captures
```
- **Each part** is scrubbed with its own secret and leak-checked.
- **If any part leaks,** the tool fails, writes nothing, and keeps the raw files. Fix the scrubber (a P1-F2 follow-up), then run it again. Don't hand-edit a raw capture.
- **If every part passes,** the tool writes them to `captures/<random id>/part-NNNN.ndjson` and deletes the raw files from the folder.

## Delete what's left of the raw capture
```
adb -s $SERIAL shell run-as app.fieldwatch.debug rm -rf files/capture
rmdir ~/captures/raw-<ts>
```

## Commit to the corpus
```
cd ~/spectre-corpus && git add captures && git commit -m "Add a scrubbed capture." && git push
```
- **Scenario notes** go next to the parts as `captures/<id>/notes.md`. Examples: "no-tail commute", or "AirTag, SmartTag and Tile beside the phone for 24 h".
- **Ground truth** for field-capture issues is written by the owner, never inferred.
- **Notes stay generic:** never note a place, a street, a time of day or a person.

## Rules
- **Raw only on the phone or in `~/captures/raw-*`:** a raw capture never goes into either git repo, never into a PR or issue, and never into a chat.
- **The corpus repo stays private.** Even scrubbed, a route's shape can be map-matched (ADR-0017, Residual risk).
- **The agent doesn't read capture contents.** It only counts records by type, as in the P1-F1 device checks.
